package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.error.UserErrorMessages
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.repository.DownloadProgress
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaDetailProvider
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.AutoDownloadMode
import com.raulshma.jellyplay.core.model.DownloadFileInventory
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadQuality
import com.raulshma.jellyplay.core.model.maxBitrate
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_error_details_not_loaded
import com.raulshma.jellyplay.feature.details.generated.resources.detail_error_download_failed
import com.raulshma.jellyplay.feature.details.generated.resources.detail_error_no_source
import com.raulshma.jellyplay.feature.details.generated.resources.detail_error_not_a_series
import com.raulshma.jellyplay.feature.details.generated.resources.detail_error_queue_failed

/**
 * Observable slice of the download-lifecycle concern owned by
 * [DownloadLifecycleActions]. Mirrors the fields the detail screen reads to
 * drive the download button spinner, the cellular-size confirmation dialog,
 * the series-download spinner, the download sheet's per-season cache, and the
 * download-details sheet's on-disk file inventory.
 */
@Immutable
internal data class DownloadLifecycleState(
    val isDownloading: Boolean = false,
    val cellularDownloadWarningMb: Int? = null,
    val isDownloadingSeries: Boolean = false,
    val downloadSheetEpisodes: Map<String, List<MediaItem>> = emptyMap(),
    val downloadSheetLoadingSeasons: Set<String> = emptySet(),
    val downloadedEpisodeIds: Set<String> = emptySet(),
    // Per-download picker (quality + external-subtitle selection). Folded into
    // [DownloadPickerState] so sheet visibility, quality, and subtitle selection
    // travel as one unit through the holder instead of three loose fields.
    val downloadPicker: DownloadPickerState = DownloadPickerState(),
    // Download-details bottom sheet: on-disk file inventory (media + sidecars)
    // with live byte sizes. Null until the sheet is opened and the inventory
    // is loaded; empty once loaded if no files resolved on disk.
    val downloadFileInventory: DownloadFileInventory? = null,
    val isLoadingDownloadFiles: Boolean = false,
    // Live in-flight download fraction per EPISODE id (0..1) — the
    // seasons section's per-row determinate bar + percent chip. Re-keyed from
    // the repository's download-id-keyed [DownloadProgress] map via the
    // mediaItemId join; rows drop out on their own when a download leaves the
    // in-flight status window (completes/fails/cancelled), so the map is a
    // live global projection that needs no per-navigation reset.
    val episodeDownloadProgress: Map<String, Float> = emptyMap(),
)

/**
 * The slim, low-frequency slice of [DownloadLifecycleState] the
 * ALWAYS-composed detail screen root collects — spinner booleans, picker,
 * downloaded-ids and the cellular warning. Deliberately excludes
 * [DownloadLifecycleState.episodeDownloadProgress] (the ~2 s transfer-tick
 * map) and the sheet-scoped caches: without this projection every tick's
 * `copy(...)` recomposed the ~1,300-line root scope and re-ran the ~60-field
 * `DetailContentState` construction. The per-tick map reaches the rows
 * through the narrow [EpisodeDownloadProgressProvider] scope instead; the
 * sheets collect the full [DownloadLifecycleActions.state] themselves while
 * open.
 */
@Immutable
internal data class DownloadRootState(
    val isDownloading: Boolean = false,
    val isDownloadingSeries: Boolean = false,
    val downloadedEpisodeIds: Set<String> = emptySet(),
    val downloadPicker: DownloadPickerState = DownloadPickerState(),
    val cellularDownloadWarningMb: Int? = null,
)

/**
 * Owns the download-lifecycle concern extracted from [DetailViewModel]:
 * single-item download (with the cellular size warning), series download,
 * the download sheet's per-season on-demand cache, and the download-details
 * sheet's file inventory.
 *
 * Plain class constructed by the VM via [Factory], mirroring the
 * [com.raulshma.jellyplay.core.data.seerr.SeerrRequestStateHolder] template:
 * takes [scope], owns its coroutines via [scope.launch], and publishes
 * [state]. Item context arrives through the [DetailSession] flow; one-shot
 * messages flow through the shared [messages] channel; localized strings
 * arrive via [strings]; per-season expansion rides the deep
 * [MediaDetailProvider] seam instead of a bare function pointer.
 */
internal class DownloadLifecycleActions(
    private val scope: CoroutineScope,
    private val session: StateFlow<DetailSession?>,
    private val messages: MutableSharedFlow<DetailMessage>,
    private val strings: DetailStrings,
    private val downloadIntake: DownloadIntake,
    private val downloadsStore: DownloadsStore,
    private val adaptiveBitrateManager: AdaptiveBitrateManager,
    private val downloadRepository: DownloadRepository,
    private val mediaDetailProvider: MediaDetailProvider,
) {
    /**
     * Hilt factory bundling this helper's exclusive collaborators so they
     * never appear in the [DetailViewModel] constructor. The VM supplies only
     * the screen-scoped inputs in [create].
     */
    class Factory constructor(
        private val downloadIntake: DownloadIntake,
        private val downloadsStore: DownloadsStore,
        private val adaptiveBitrateManager: AdaptiveBitrateManager,
        private val downloadRepository: DownloadRepository,
    ) {
        fun create(
            scope: CoroutineScope,
            session: StateFlow<DetailSession?>,
            messages: MutableSharedFlow<DetailMessage>,
            strings: DetailStrings,
            mediaDetailProvider: MediaDetailProvider,
        ): DownloadLifecycleActions = DownloadLifecycleActions(
            scope = scope,
            session = session,
            messages = messages,
            strings = strings,
            downloadIntake = downloadIntake,
            downloadsStore = downloadsStore,
            adaptiveBitrateManager = adaptiveBitrateManager,
            downloadRepository = downloadRepository,
            mediaDetailProvider = mediaDetailProvider,
        )
    }

    private val _state = MutableStateFlow(DownloadLifecycleState())
    val state: StateFlow<DownloadLifecycleState> = _state.asStateFlow()

    /**
     * The [DownloadRootState] projection, deduped so only an actual
     * root-field change reaches the screen root. Eagerly (not
     * WhileSubscribed): the mapping is five scalar fields per emission —
     * trivial next to a recomposition — and the always-live contract mirrors
     * [state]'s `asStateFlow`, so direct `.value` reads (tests) never see a
     * stale initial.
     */
    val rootState: StateFlow<DownloadRootState> = _state
        .map {
            DownloadRootState(
                isDownloading = it.isDownloading,
                isDownloadingSeries = it.isDownloadingSeries,
                downloadedEpisodeIds = it.downloadedEpisodeIds,
                downloadPicker = it.downloadPicker,
                cellularDownloadWarningMb = it.cellularDownloadWarningMb,
            )
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, DownloadRootState())

    init {
        // Live in-flight download progress: one collector for the screen's
        // lifetime, re-keying the repository's download-id-keyed projection onto
        // episode ids so the seasons section renders bars without a per-row
        // subscription. The upstream map is structurally deduped by the
        // repository (Room re-emits per table write; equal maps collapse), and
        // rows self-remove when their status leaves the in-flight window, so
        // no navigation reset is needed below.
        scope.launch {
            downloadRepository.getActiveDownloadProgress().collect { progressById ->
                _state.update {
                    it.copy(episodeDownloadProgress = episodeDownloadProgress(progressById.values))
                }
            }
        }
    }

    /**
     * Opens the pre-download picker, seeding the pending quality from the user's
     * stored preference and resetting the subtitle selection to "all" so a prior
     * session's pick never leaks into a new one.
     */
    fun openDownloadPicker() {
        val prefs = downloadsStore.downloads.value
        _state.update {
            it.copy(
                downloadPicker = DownloadPickerState(
                    visible = true,
                    quality = prefs.downloadQuality,
                ),
            )
        }
    }

    fun dismissDownloadPicker() {
        _state.update { it.copy(downloadPicker = it.downloadPicker.copy(visible = false)) }
    }

    fun setPendingQuality(quality: DownloadQuality) {
        _state.update { it.copy(downloadPicker = it.downloadPicker.copy(quality = quality)) }
    }

    /**
     * Replaces the external-subtitle selection for the next download. The picker
     * UI hands the toggled [SubtitleSelection] here; [SubtitleSelection.All]
     * bundles every deliverable subtitle, [SubtitleSelection.Subset] an explicit
     * set (possibly empty — a valid "no subtitles" choice).
     */
    fun setPendingSubtitleSelection(selection: SubtitleSelection) {
        _state.update { it.copy(downloadPicker = it.downloadPicker.copy(subtitleSelection = selection)) }
    }

    // Per-season on-demand cache for the download sheet (the sheet fetches
    // seasons lazily, independent of the main display). These are internal
    // caches, not observable state — they are projected into
    // [DownloadLifecycleState.downloadSheetEpisodes] /
    // [DownloadLifecycleState.downloadSheetLoadingSeasons].
    private val downloadSheetEpisodesMap = mutableMapOf<String, List<MediaItem>>()
    private var downloadSheetFetchedSeasonIds: Set<String> = emptySet()

    fun startDownload() {
        val detail = session.value?.detail ?: run {
            scope.launch { messages.tryEmit(DetailMessage.Text(strings.get(Res.string.detail_error_details_not_loaded))) }
            return
        }
        val source = detail.mediaSources.firstOrNull() ?: run {
            scope.launch { messages.tryEmit(DetailMessage.Text(strings.get(Res.string.detail_error_no_source))) }
            return
        }

        // The picker (if open) has handed off to the download; close it so the
        // cellular-warning dialog / spinner can take over. The pending quality
        // + subtitle selection persist in state so [performDownload] and a
        // follow-up [confirmCellularDownload] resolve identically.
        _state.update { it.copy(downloadPicker = it.downloadPicker.copy(visible = false)) }

        // Cellular download size warning: when on a metered network and the
        // user has configured a warning threshold (MB), surface a
        // confirmation dialog instead of silently consuming data.
        val prefs = downloadsStore.downloads.value
        val thresholdMb = prefs.cellularDownloadSizeWarningMb
        if (thresholdMb > 0 && !adaptiveBitrateManager.isUnmeteredConnection()) {
            val sizeBytes = source.size ?: 0L
            val sizeMb = (sizeBytes / (1024L * 1024L)).toInt()
            if (sizeMb >= thresholdMb) {
                _state.update { it.copy(cellularDownloadWarningMb = sizeMb) }
                return
            }
        }

        performDownload(detail.item, source)
    }

    /**
     * Called from the UI after the user explicitly confirms a cellular
     * download that exceeded the cellular warning threshold. Clears the
     * warning state and proceeds with the download.
     */
    fun confirmCellularDownload() {
        val detail = session.value?.detail ?: return
        val source = detail.mediaSources.firstOrNull() ?: return
        _state.update { it.copy(cellularDownloadWarningMb = null) }
        performDownload(detail.item, source)
    }

    fun dismissCellularDownloadWarning() {
        _state.update { it.copy(cellularDownloadWarningMb = null) }
    }

    private fun performDownload(
        item: MediaItem,
        source: MediaSource,
    ) {
        val detail = session.value?.detail ?: return
        scope.launch {
            _state.update { it.copy(isDownloading = true) }
            try {
                // Apply the pending download quality (seeded from the user's
                // preference when the picker opens) when building the stream URL
                // so the server transcodes to the requested ceiling, and narrow
                // the bundled subtitles to the picker's selection
                // (SubtitleSelection.All = every deliverable subtitle). The intake
                // seam owns the full bundle (local images, trickplay, subtitles,
                // segments, offline metadata row).
                val picker = _state.value.downloadPicker
                val maxBitrate = qualityToMaxBitrate(picker.quality)
                val result = downloadIntake.start(detail, maxBitrate, picker.subtitleSelection.toIndexSet())
                if (result.downloadItem == null) {
                    val message = result.error
                        ?: strings.get(Res.string.detail_error_download_failed)
                    messages.tryEmit(DetailMessage.Text(message))
                }
            } catch (e: Exception) {
                messages.tryEmit(
                    DetailMessage.Text(UserErrorMessages.resolve(e, strings.get(Res.string.detail_error_download_failed))),
                )
            }
            _state.update { it.copy(isDownloading = false) }
        }
    }

    fun downloadSeries(
        episodeIds: Map<String, List<String>>? = null,
        policyChoice: DownloadIntake.SeriesPolicyChoice = DownloadIntake.SeriesPolicyChoice.LeaveUnchanged,
    ) {
        val detail = session.value?.detail ?: run {
            scope.launch { messages.tryEmit(DetailMessage.SeriesDownload(queuedCount = 0, error = strings.get(Res.string.detail_error_details_not_loaded))) }
            return
        }
        val item = detail.item
        if (item.mediaType != MediaType.SERIES) {
            scope.launch { messages.tryEmit(DetailMessage.SeriesDownload(queuedCount = 0, error = strings.get(Res.string.detail_error_not_a_series))) }
            return
        }

        scope.launch {
            _state.update { it.copy(isDownloadingSeries = true) }
            downloadIntake.startSeries(item.id, episodeIds, policyChoice)
                .onSuccess { downloadIds ->
                    messages.tryEmit(DetailMessage.SeriesDownload(queuedCount = downloadIds.size, error = null))
                }
                .onFailure { error ->
                    messages.tryEmit(
                        DetailMessage.SeriesDownload(
                            queuedCount = 0,
                            error = UserErrorMessages.resolve(error, strings.get(Res.string.detail_error_queue_failed)),
                        ),
                    )
                }
            _state.update { it.copy(isDownloadingSeries = false) }
        }
    }

    /**
     * The session series' stored policy override (null = Default) — the
     * download sheet's pre-load read so its chips open on the stored choice.
     */
    suspend fun seriesPolicy(): AutoDownloadMode? {
        val seriesId = session.value?.seriesId ?: return null
        return downloadIntake.seriesPolicy(seriesId)
    }

    fun prepareDownloadSheetEpisodes() {
        val seriesId = session.value?.seriesId ?: return
        val itemId = session.value?.itemId ?: return
        val seasons = session.value?.seasons ?: emptyList()
        if (seasons.isEmpty()) return
        val seasonIds = seasons.map { it.id }.toSet()

        downloadSheetEpisodesMap.clear()
        downloadSheetFetchedSeasonIds = emptySet()
        _state.update { it.copy(downloadSheetLoadingSeasons = seasonIds) }

        scope.launch {
            // MediaDetailProvider.expandSeason is idempotent (returns the
            // cached episodes without re-emitting when the season is already
            // present + fetched) and only fetches seasons not yet in the
            // snapshot (e.g. the mismatched-season-key edge).
            if (session.value?.seriesId != seriesId) return@launch
            seasons.forEach { season ->
                downloadSheetEpisodesMap[season.id] = mediaDetailProvider.expandSeason(itemId, season.id)
            }
            if (session.value?.seriesId != seriesId) return@launch

            downloadSheetFetchedSeasonIds = seasonIds
            _state.update {
                it.copy(
                    downloadSheetEpisodes = downloadSheetEpisodesMap.toMap(),
                    downloadSheetLoadingSeasons = emptySet(),
                )
            }
        }
    }

    fun loadDownloadSheetEpisodes(seasonId: String) {
        if (seasonId in downloadSheetFetchedSeasonIds) return
        val itemId = session.value?.itemId ?: return
        _state.update { it.copy(downloadSheetLoadingSeasons = it.downloadSheetLoadingSeasons + seasonId) }
        scope.launch {
            // expandSeason serves from the cached snapshot when present, else
            // fetches the one season and merges it in. Avoids a duplicate
            // round-trip and a second in-memory copy.
            val episodes = mediaDetailProvider.expandSeason(itemId, seasonId)
            downloadSheetEpisodesMap[seasonId] = episodes
            _state.update { it.copy(downloadSheetEpisodes = downloadSheetEpisodesMap.toMap()) }
            downloadSheetFetchedSeasonIds = downloadSheetFetchedSeasonIds + seasonId
            _state.update { it.copy(downloadSheetLoadingSeasons = it.downloadSheetLoadingSeasons - seasonId) }
        }
    }

    fun loadDownloadedEpisodeIds() {
        val seriesId = session.value?.seriesId ?: return
        scope.launch {
            val ids = downloadRepository.episodeIdsForSeries(seriesId)
            _state.update { it.copy(downloadedEpisodeIds = ids) }
        }
    }

    fun resetDownloadSheetState() {
        downloadSheetEpisodesMap.clear()
        downloadSheetFetchedSeasonIds = emptySet()
        _state.update {
            it.copy(
                downloadSheetEpisodes = emptyMap(),
                downloadSheetLoadingSeasons = emptySet(),
                downloadedEpisodeIds = emptySet(),
            )
        }
    }

    /**
     * Loads the on-disk file inventory (media + sidecar artifacts) for the
     * download-details sheet. Runs only when the sheet opens — sidecar sizes
     * aren't persisted, so this is the one place the filesystem walk executes.
     * Resolves the item id from the current session (detail snapshot first,
     * bare item id fallback so a not-yet-snapshotted download still
     * inventories).
     */
    fun loadDownloadFileInventory() {
        val itemId = session.value?.detail?.item?.id ?: session.value?.itemId ?: return
        _state.update { it.copy(isLoadingDownloadFiles = true) }
        scope.launch {
            val inventory = downloadRepository.getDownloadFileInventory(itemId)
            _state.update {
                it.copy(downloadFileInventory = inventory, isLoadingDownloadFiles = false)
            }
        }
    }

    /** Clears the loaded inventory so the next sheet open re-reads fresh sizes. */
    fun clearDownloadFileInventory() {
        _state.update {
            it.copy(downloadFileInventory = null, isLoadingDownloadFiles = false)
        }
    }

    /** Reactive download row for the current item (drives the download-info card). */
    fun downloadFlow(itemId: String): Flow<DownloadItem?> =
        downloadRepository.getDownloadByMediaItemIdFlow(itemId)

    /** Clears isDownloading/isDownloadingSeries + the sheet caches. Called by the
     *  VM on screen-entry reset (loadItemInternal). */
    fun resetForNavigation() {
        downloadSheetEpisodesMap.clear()
        downloadSheetFetchedSeasonIds = emptySet()
        _state.update {
            it.copy(
                isDownloading = false,
                isDownloadingSeries = false,
                cellularDownloadWarningMb = null,
                downloadPicker = DownloadPickerState(),
                downloadSheetEpisodes = emptyMap(),
                downloadSheetLoadingSeasons = emptySet(),
                downloadedEpisodeIds = emptySet(),
                downloadFileInventory = null,
                isLoadingDownloadFiles = false,
            )
        }
    }

    private fun qualityToMaxBitrate(quality: DownloadQuality): Int? = quality.maxBitrate
}

/**
 * Re-keys the repository's download-id-keyed live [DownloadProgress] rows onto
 * episode (item) ids as 0..1 fractions — the [DownloadLifecycleState.episodeDownloadProgress]
 * shape the seasons section's rows render. Rows without an item join or
 * with an unknown total size (a PENDING row before its size probe — the same
 * `totalSizeBytes > 0` guard the ⋮ menu's percent label applies) drop out;
 * two in-flight rows for one item resolve to their latest fraction (last wins).
 * Pure so the in-flight/absent/unknown-total merge has a direct test surface.
 */
internal fun episodeDownloadProgress(
    progress: Collection<DownloadProgress>,
): Map<String, Float> = buildMap {
    for (row in progress) {
        val itemId = row.mediaItemId?.takeIf(String::isNotEmpty) ?: continue
        val fraction = row.progressFraction ?: continue
        put(itemId, fraction)
    }
}
