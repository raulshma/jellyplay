package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.BookTocCacheRepository
import com.raulshma.jellyplay.core.data.repository.NoopBookTocCacheRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.hasPlaybackPosition
import com.raulshma.jellyplay.core.model.toMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * "When does the home render downloads?" as ONE module: the offline
 * collection gate, both gated collectors (library + episodes), and the
 * render-source fold that used to span five pieces across the VM — a combine,
 * a mutable `offlineGateState` mirror that existed only to dodge the uiState
 * one-hop lag, two `flatMapLatest` collectors in init, and a
 * [computeHomeRenderSource] call inside one of them.
 *
 * Interface: [state] ([OfflineHomeState] carries the render source plus both
 * offline lists). Inputs: the offline mode and the refresher's
 * `fetchFailed`. The fold keys on the SAME gate emission that opened the
 * collection (the gate value is paired into every library emission inside
 * `flatMapLatest`), so the lag race the old mirror worked around is
 * structurally impossible here.
 *
 * The underlying repository flows re-emit on every download-progress write,
 * so they are collected ONLY while the gate is open (any offline mode, or an
 * online fetch that failed — the stale cached sections cannot refresh, so
 * the offline rows take over); the upstream collection
 * is cancelled while the online home renders.
 *
 * [homeLayoutProvider] supplies the cached online home layout (see
 * [OfflineHomeState.cachedLayout]) — one suspend read per gate open, not per
 * library emission.
 */
internal class OfflineHomeGate(
    scope: CoroutineScope,
    offlineMode: Flow<OfflineMode>,
    offlineRepository: OfflineRepository,
    fetchFailed: Flow<Boolean>,
    homeLayoutProvider: suspend () -> List<HomeSection> = { emptyList() },
    /**
     * Local TOC cache backing the offline Continue Reading row's progress
     * bars — the offline twin of the refresher's online decode: a paged
     * book's page count lives only here, so without this lookup an offline
     * book card falls back to the percent reading of page-encoded ticks (a
     * visibly wrong bar). The cache is local Room, available offline.
     * Best-effort per item; defaults to the neutral no-op (tests).
     */
    private val bookTocCacheRepository: BookTocCacheRepository = NoopBookTocCacheRepository(),
) {
    private val _state = MutableStateFlow(OfflineHomeState())
    val state: StateFlow<OfflineHomeState> = _state.asStateFlow()

    /** The gate value both collectors key on — see [OfflineGate.isCollecting]. */
    private val gate: Flow<OfflineGate> = combine(
        offlineMode,
        fetchFailed,
    ) { mode, failedEmpty -> OfflineGate(mode, failedEmpty) }
        .distinctUntilChanged()

    init {
        // Offline library under the gate. First emission after the gate opens
        // carries pending=true — the window where the implicit-offline
        // fallback is still deciding whether any downloads exist.
        @OptIn(ExperimentalCoroutinesApi::class)
        scope.launch {
            gate
                .flatMapLatest { gate ->
                    if (gate.isCollecting) {
                        offlineRepository.getOfflineLibrary()
                            .map { gate to OfflineLibraryEmission(items = it) }
                            .onStart { emit(gate to OfflineLibraryEmission(pending = true)) }
                    } else {
                        flowOf(gate to OfflineLibraryEmission())
                    }
                }
                // The repository flow re-emits on every download-progress
                // write; identical (gate, items) pairs — Room re-emissions
                // with nothing changed — are dropped here so the suspend
                // TOC decode below only runs for a real list change.
                .distinctUntilChanged()
                .collect { (gate, emission) ->
                    // Book progress decodes ride the library emission (the
                    // TOC cache is local Room — readable offline, and the
                    // offline ids ARE the server ids). Computed before the
                    // state write so the update lands as one emission, and
                    // only while the gate is actually collecting — a closed
                    // gate emits empty items, and decoding them would be a
                    // wasted repository round-trip on every online emission.
                    val bookFractions = if (gate.isCollecting) {
                        offlineBookProgressFractions(emission.items)
                    } else {
                        emptyMap()
                    }
                    _state.update { state ->
                        state.copy(
                            offlineLibrary = emission.items,
                            bookProgressFractions = bookFractions,
                            renderSource = computeHomeRenderSource(
                                offlineMode = gate.mode,
                                fetchFailed = gate.fetchFailed,
                                offlineLibrary = emission.items,
                                fallbackPending = emission.pending,
                            ),
                        )
                    }
                }
        }

        // Downloaded episodes ride the SAME gate but are collected
        // independently — they feed only the offline CW/Next Up rows, so
        // their (potentially large, artwork-resolving) emissions must not
        // delay the library's pending→loaded transition.
        @OptIn(ExperimentalCoroutinesApi::class)
        scope.launch {
            gate
                .flatMapLatest { gate ->
                    if (gate.isCollecting) {
                        offlineRepository.getOfflineEpisodes()
                    } else {
                        flowOf(emptyList())
                    }
                }
                .collect { episodes ->
                    _state.update { it.copy(offlineEpisodes = episodes) }
                }
        }

        // Cached online layout under the SAME gate: one provider read per
        // gate open (a single indexed Room row — far cheaper than a library
        // emission, so it lands before the first library item in practice).
        // Cleared when the gate closes so a return online never renders a
        // stale offline layout.
        @OptIn(ExperimentalCoroutinesApi::class)
        scope.launch {
            gate
                .flatMapLatest { gate ->
                    if (gate.isCollecting) {
                        flow { emit(homeLayoutProvider()) }
                    } else {
                        flowOf(emptyList())
                    }
                }
                .distinctUntilChanged()
                .collect { layout ->
                    _state.update { it.copy(cachedLayout = layout) }
                }
        }
    }

    /**
     * Continue Reading progress bars for the offline library's BOOK items,
     * keyed by item id — the offline twin of the refresher's online decode,
     * through the shared [decodeBookProgressFractions]: exact page fractions
     * where the TOC cache knows the page count, percent fallback otherwise
     * (EPUB percent ticks decode correctly there). Empty when the gate is
     * closed (no offline books to decode).
     */
    private suspend fun offlineBookProgressFractions(items: List<OfflineMediaItem>): Map<String, Float> =
        bookTocCacheRepository.decodeBookProgressFractions(
            items
                .filter { it.mediaType == MediaType.BOOK && it.hasPlaybackPosition }
                .map { it.toMediaItem() },
        )
}

/** The module's whole output: the render decision plus both offline lists and the cached layout. */
@Immutable
internal data class OfflineHomeState(
    val renderSource: HomeRenderSource = HomeRenderSource.Online,
    val offlineLibrary: List<OfflineMediaItem> = emptyList(),
    val offlineEpisodes: List<OfflineMediaItem> = emptyList(),
    /**
     * Continue Reading progress bars keyed by item id, decoded from the local
     * TOC cache (the collector inside [OfflineHomeGate]) — the offline twin of
     * [com.raulshma.jellyplay.feature.home.HomeRefreshState.bookProgressFractions].
     * A book card missing from the map falls back to the percent reading.
     */
    val bookProgressFractions: Map<String, Float> = emptyMap(),
    /**
     * The cached online home layout (issue #147): section types, titles,
     * per-library rows and order from the last successful online fetch. Empty
     * when no snapshot exists — the offline home then falls back to its
     * generic derived rows.
     */
    val cachedLayout: List<HomeSection> = emptyList(),
)

/**
 * The offline collection gate value: [isCollecting] is the predicate both
 * collectors key on, and [mode]/[fetchFailed] feed
 * [computeHomeRenderSource] so the render-source fold reads the same gate
 * emission that opened/closed the collection.
 */
internal data class OfflineGate(
    val mode: OfflineMode,
    val fetchFailed: Boolean,
) {
    val isCollecting: Boolean get() = mode != OfflineMode.ONLINE || fetchFailed
}

/**
 * Emission envelope for the offline-library collection gate: [pending] marks
 * the window after the gate opens but before the first real library emission,
 * while the implicit-offline fallback is still deciding whether any downloads
 * exist. Maps onto [OfflineHomeState.offlineLibrary] +
 * [HomeRenderSource.FallbackPending].
 */
internal data class OfflineLibraryEmission(
    val items: List<OfflineMediaItem> = emptyList(),
    val pending: Boolean = false,
)
