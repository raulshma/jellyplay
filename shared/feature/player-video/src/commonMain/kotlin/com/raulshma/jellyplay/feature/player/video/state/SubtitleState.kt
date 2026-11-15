package com.raulshma.jellyplay.feature.player.video.state

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.CultureInfo
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import com.raulshma.jellyplay.feature.player.video.SubtitleDownloadStatus

/**
 * The legacy Jellyfin-only subtitle search machine's phase
 * ([com.raulshma.jellyplay.feature.player.video.SubtitleManager.searchRemoteSubtitles],
 * OpenSubtitles via the server) — one mutually-exclusive state replacing the
 * former `searchedSubtitles` / `isSearchingSubtitles` / `hasSearchedSubtitles`
 * / `subtitleSearchError` boolean-field quartet.
 *
 * The migration exists because that quartet was SHARED with the multi-provider
 * machine ([ProviderSubtitleSearchState]): one machine's start or completion
 * rewrote the other's spinner / "searched" flags mid-flight (a legacy
 * completion dropped the sheet's spinner while provider results were still
 * streaming, and a slow legacy search flipped the pair under a settled
 * provider flow). With one state per machine the interleavings are
 * structurally independent — the same late-result class of bug the book
 * reader's `ReaderSearchSession` fixed with a stamped token; the player's two
 * machines cannot share a token because their jobs deliberately do not cancel
 * each other (see
 * [com.raulshma.jellyplay.feature.player.video.SubtitleManager.searchAllProviders]).
 *
 * The Search tab renders a FOLD of both machines (the `searchTab*` helpers in
 * [com.raulshma.jellyplay.feature.player.video.SubtitleHubSheetModule]); the
 * tab's pre-migration no-results / empty / error render rules are unchanged.
 */
sealed interface RemoteSubtitleSearchState {
    /** No search yet — the Search tab shows its language-picker hint. */
    data object Idle : RemoteSubtitleSearchState

    /** A search is in flight — the Search tab's spinner. */
    data object Searching : RemoteSubtitleSearchState

    /**
     * The search completed with [results] — possibly empty, which renders as
     * "no results found" (a settled outcome, deliberately distinct from
     * [Failed]).
     */
    data class Done(val results: List<RemoteSubtitleInfo>) : RemoteSubtitleSearchState

    /**
     * The search failed — the Search tab's "search failed" retry invitation
     * with [message] (the former `subtitleSearchError`). A failure is NOT a
     * settled "searched" outcome: the old flag pair was cleared here and only
     * the error block rendered, so the tab fold counts only [Done] as searched.
     */
    data class Failed(val message: String) : RemoteSubtitleSearchState
}

/**
 * The multi-provider subtitle search machine's phase
 * ([com.raulshma.jellyplay.feature.player.video.SubtitleManager.searchAllProviders]:
 * Jellyfin + Wyzie + OpenSubtitles via `searchAllStreaming`) — one
 * mutually-exclusive state replacing the former `providerSearchResults` /
 * `providerSearchErrors` pair plus that machine's writes to the then-shared
 * `isSearchingSubtitles` / `hasSearchedSubtitles` flags (see
 * [RemoteSubtitleSearchState] for the bug the split fixes).
 *
 * There is deliberately NO Failed arm: `searchAllStreaming` isolates every
 * provider failure into the per-provider error map (one bad key surfaces as a
 * chip rather than blanking the whole list), so a total failure IS a
 * [Results] with empty results and every provider errored — the Search tab
 * renders its "search failed" block from that map, byte-identical to the
 * pre-migration behavior.
 */
sealed interface ProviderSubtitleSearchState {
    /** No provider search yet — the Search tab shows its language-picker hint. */
    data object Idle : ProviderSubtitleSearchState

    /**
     * A search is in flight, carrying the streaming partials landed so far.
     * Each provider's rows/errors are pushed onto this arm the instant that
     * provider resolves (`searchAllStreaming`'s partial snapshots), so a
     * slow/retrying provider can no longer gate its siblings' — the partials
     * live ON the arm, with no parallel buffer a machine write could drift
     * from. Rows keep their provider provenance so the Search tab can render
     * provider filter chips and per-row badges.
     */
    data class Searching(
        val partialResults: List<SubtitleSearchResult> = emptyList(),
        val partialErrors: Map<SubtitleProviderKind, String> = emptyMap(),
    ) : ProviderSubtitleSearchState

    /**
     * The search settled: [results] is the stable-ordered cross-provider merge
     * and [errors] the per-provider failure messages rendered under the list
     * (and as error-tinted filter chips).
     */
    data class Results(
        val results: List<SubtitleSearchResult>,
        val errors: Map<SubtitleProviderKind, String>,
    ) : ProviderSubtitleSearchState
}

/**
 * The SubtitleManager sheet's download/search state — the slice owned by
 * [com.raulshma.jellyplay.feature.player.video.SubtitleManager]. The download
 * list ([remoteSubtitles]) and the search machines are kept separate so tab
 * switches never clobber each other.
 *
 * The TWO search machines are deliberately independent state values:
 * [remoteSubtitleSearch] (the legacy Jellyfin-only search) and
 * [providerSubtitleSearch] (the merged cross-provider search). They used to
 * share one `isSearchingSubtitles` / `hasSearchedSubtitles` boolean pair, and
 * each machine's start/completion rewrote the other's flags mid-flight; the
 * Search tab now folds the two (the `searchTab*` helpers in
 * [com.raulshma.jellyplay.feature.player.video.SubtitleHubSheetModule]). The
 * states being disjoint is also what now carries the jobs' deliberate
 * "cannot cancel each other" property — see
 * [com.raulshma.jellyplay.feature.player.video.SubtitleManager.searchAllProviders].
 *
 * `subtitleStyle` deliberately does NOT live here: it has three writers
 * (SettingsProjector + the ViewModel's subtitle-delay/style setters), so it
 * stays a session/prefs-mirror field on [com.raulshma.jellyplay.feature.player.video.VideoPlayerUiState].
 *
 * [configuredSubtitleProviders] drives whether the provider chips are shown at
 * all (hidden when only Jellyfin is configured) — and with it, which machine
 * the Search tab's Search button drives.
 */
@Immutable
data class SubtitleState(
    val remoteSubtitles: List<RemoteSubtitleInfo> = emptyList(),
    val subtitleCultures: List<CultureInfo> = emptyList(),
    /**
     * The legacy Jellyfin-only search machine's phase — replaces the former
     * `searchedSubtitles` / `isSearchingSubtitles` / `hasSearchedSubtitles` /
     * `subtitleSearchError` quartet (per-arm payloads carry what each flat
     * field used to).
     */
    val remoteSubtitleSearch: RemoteSubtitleSearchState = RemoteSubtitleSearchState.Idle,
    val isUploadingSubtitle: Boolean = false,
    val isLoadingRemoteSubtitles: Boolean = false,
    /**
     * Failure message from the last [remoteSubtitles] fetch, rendered inline on
     * the Get tab's Download section. Deliberately not a global toast: the
     * fetch's retry chain can outlive the hub (even the player), and a late
     * global toast over an unrelated screen reads as an error with no context.
     */
    val remoteSubtitlesError: String? = null,
    val defaultSearchLanguage: String = "eng",
    /** Per-subtitle-id download status for the "Get Subtitles" sheet. */
    val downloadingSubtitles: Map<String, SubtitleDownloadStatus> = emptyMap(),
    /**
     * Row key → where a download has surfaced and is usable right now, backing
     * the sheet's "Use" action so it can activate the exact track without
     * guessing by label.
     */
    val readySubtitles: Map<String, ReadySubtitleHint> = emptyMap(),
    /**
     * The multi-provider streaming search machine's phase — replaces the
     * former `providerSearchResults` / `providerSearchErrors` pair (results
     * and per-provider error chips ride the arm payloads now).
     */
    val providerSubtitleSearch: ProviderSubtitleSearchState = ProviderSubtitleSearchState.Idle,
    /** Providers the user has configured (drives chip visibility). */
    val configuredSubtitleProviders: Set<SubtitleProviderKind> = emptySet(),
)

/**
 * Where a downloaded subtitle has surfaced, recorded per sheet row so the
 * "Use" action can activate it exactly. [trackId] is the side-loaded
 * [com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource.id]
 * (`external:{index}` for server downloads, `provider:{kind}:{id}` for
 * external-provider side-loads); [serverStreamIndex] is the server
 * `MediaStream.index` the download surfaced as — the picker lists synthetic
 * server rows keyed by that index on transcoded playback.
 */
@Immutable
data class ReadySubtitleHint(
    val trackId: String,
    val serverStreamIndex: Int? = null,
)

/**
 * The Subtitle Manager's per-row key namespaces — the single home for how
 * status and ready-hint map entries are keyed. Jellyfin rows key on the plain
 * remote-subtitle id; external-provider rows on this composite. Producer
 * ([com.raulshma.jellyplay.feature.player.video.SubtitleManager]) and consumer
 * (the sheets' "Use" / status lookups) must both go through these builders so
 * a format change cannot silently desynchronize them.
 */
fun providerSubtitleRowKey(provider: SubtitleProviderKind, providerSubtitleId: String): String =
    "${provider}:$providerSubtitleId"

/**
 * The engine-side [com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource.id]
 * namespace for a provider row's side-loaded local copy — deliberately distinct
 * from the row key (which keys sheet maps) so the two id spaces can't collide.
 */
fun providerSubtitleEngineId(rowKey: String): String = "provider:$rowKey"
