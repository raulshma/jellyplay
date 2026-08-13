package com.raulshma.jellyplay.feature.details

import android.content.Context
import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.DownloadQuality
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Observable slice of the download-lifecycle concern owned by
 * [DownloadLifecycleActions]. Mirrors the fields the detail screen reads to
 * drive the download button spinner, the cellular-size confirmation dialog,
 * the series-download spinner, and the download sheet's per-season cache.
 */
@Immutable
internal data class DownloadLifecycleState(
    val isDownloading: Boolean = false,
    val cellularDownloadWarningMb: Int? = null,
    val isDownloadingSeries: Boolean = false,
    val downloadSheetEpisodes: Map<String, List<MediaItem>> = emptyMap(),
    val downloadSheetLoadingSeasons: Set<String> = emptySet(),
    val downloadedEpisodeIds: Set<String> = emptySet(),
)

/**
 * Owns the download-lifecycle concern extracted from [DetailViewModel]:
 * single-item download (with the cellular size warning), series download,
 * and the download sheet's per-season on-demand cache.
 *
 * Plain class (no Hilt/DI) constructed by the VM, mirroring the
 * [com.raulshma.jellyplay.core.data.seerr.SeerrRequestStateHolder] template:
 * takes [scope], owns its coroutines via [scope.launch], and exposes
 * [state]. All VM-internal references are injected as provider lambdas / a
 * message sink so this class has no dependency on the VM's state container.
 *
 * - [detailProvider] / [seasonsProvider] read the current detail/seasons.
 * - [currentSeriesIdProvider] / [itemIdProvider] read the current navigation ids.
 * - [expandSeason] is the provider's idempotent per-season expand.
 * - [messageSink] forwards one-shot [DetailMessage]s to the VM's shared flow.
 */
internal class DownloadLifecycleActions(
    private val scope: CoroutineScope,
    private val downloadIntake: DownloadIntake,
    private val downloadsStore: DownloadsStore,
    private val adaptiveBitrateManager: AdaptiveBitrateManager,
    private val downloadRepository: DownloadRepository,
    private val context: Context,
    private val detailProvider: () -> MediaDetail?,
    private val seasonsProvider: () -> List<MediaItem>,
    private val currentSeriesIdProvider: () -> String?,
    private val itemIdProvider: () -> String?,
    private val expandSeason: suspend (itemId: String, seasonId: String) -> List<MediaItem>,
    private val messageSink: (DetailMessage) -> Unit,
) {
    private val _state = MutableStateFlow(DownloadLifecycleState())
    val state: StateFlow<DownloadLifecycleState> = _state.asStateFlow()

    // Per-season on-demand cache for the download sheet (the sheet fetches
    // seasons lazily, independent of the main display). These are internal
    // caches, not observable state — they are projected into
    // [DownloadLifecycleState.downloadSheetEpisodes] /
    // [DownloadLifecycleState.downloadSheetLoadingSeasons].
    private val downloadSheetEpisodesMap = mutableMapOf<String, List<MediaItem>>()
    private var downloadSheetFetchedSeasonIds: Set<String> = emptySet()

    fun startDownload() {
        val detail = detailProvider() ?: run {
            scope.launch { messageSink(DetailMessage.Text(context.getString(R.string.detail_error_details_not_loaded))) }
            return
        }
        val source = detail.mediaSources.firstOrNull() ?: run {
            scope.launch { messageSink(DetailMessage.Text(context.getString(R.string.detail_error_no_source))) }
            return
        }

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
        val detail = detailProvider() ?: return
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
        val detail = detailProvider() ?: return
        scope.launch {
            _state.update { it.copy(isDownloading = true) }
            try {
                // Apply the user's download quality preference when building the
                // stream URL so the server transcodes to the requested ceiling.
                // The intake seam owns the full bundle (local images, trickplay,
                // subtitles, segments, offline metadata row).
                val prefs = downloadsStore.downloads.value
                val maxBitrate = qualityToMaxBitrate(prefs.downloadQuality)
                val result = downloadIntake.start(detail, maxBitrate)
                if (result.downloadItem == null) {
                    val message = result.error
                        ?: context.getString(R.string.detail_error_download_failed)
                    messageSink(DetailMessage.Text(message))
                }
            } catch (e: Exception) {
                messageSink(DetailMessage.Text(e.message ?: context.getString(R.string.detail_error_download_failed)))
            }
            _state.update { it.copy(isDownloading = false) }
        }
    }

    fun downloadSeries(episodeIds: Map<String, List<String>>? = null) {
        val detail = detailProvider() ?: run {
            scope.launch { messageSink(DetailMessage.SeriesDownload(queuedCount = 0, error = context.getString(R.string.detail_error_details_not_loaded))) }
            return
        }
        val item = detail.item
        if (item.mediaType != MediaType.SERIES) {
            scope.launch { messageSink(DetailMessage.SeriesDownload(queuedCount = 0, error = context.getString(R.string.detail_error_not_a_series))) }
            return
        }

        scope.launch {
            _state.update { it.copy(isDownloadingSeries = true) }
            downloadIntake.startSeries(item.id, episodeIds)
                .onSuccess { downloadIds ->
                    messageSink(DetailMessage.SeriesDownload(queuedCount = downloadIds.size, error = null))
                }
                .onFailure { error ->
                    messageSink(DetailMessage.SeriesDownload(queuedCount = 0, error = error.message ?: context.getString(R.string.detail_error_queue_failed)))
                }
            _state.update { it.copy(isDownloadingSeries = false) }
        }
    }

    fun prepareDownloadSheetEpisodes() {
        val seriesId = currentSeriesIdProvider() ?: return
        val itemId = itemIdProvider() ?: return
        val seasons = seasonsProvider()
        if (seasons.isEmpty()) return
        val seasonIds = seasons.map { it.id }.toSet()

        downloadSheetEpisodesMap.clear()
        downloadSheetFetchedSeasonIds = emptySet()
        _state.update { it.copy(downloadSheetLoadingSeasons = seasonIds) }

        scope.launch {
            // expandSeason is idempotent (returns the cached episodes without
            // re-emitting when the season is already present + fetched) and only
            // fetches seasons not yet in the snapshot (e.g. the mismatched-season-key
            // edge).
            if (currentSeriesIdProvider() != seriesId) return@launch
            seasons.forEach { season ->
                downloadSheetEpisodesMap[season.id] = expandSeason(itemId, season.id)
            }
            if (currentSeriesIdProvider() != seriesId) return@launch

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
        val itemId = itemIdProvider() ?: return
        _state.update { it.copy(downloadSheetLoadingSeasons = it.downloadSheetLoadingSeasons + seasonId) }
        scope.launch {
            // expandSeason serves from the cached snapshot when present, else
            // fetches the one season and merges it in. Avoids a duplicate
            // round-trip and a second in-memory copy.
            val episodes = expandSeason(itemId, seasonId)
            downloadSheetEpisodesMap[seasonId] = episodes
            _state.update { it.copy(downloadSheetEpisodes = downloadSheetEpisodesMap.toMap()) }
            downloadSheetFetchedSeasonIds = downloadSheetFetchedSeasonIds + seasonId
            _state.update { it.copy(downloadSheetLoadingSeasons = it.downloadSheetLoadingSeasons - seasonId) }
        }
    }

    fun loadDownloadedEpisodeIds() {
        val seriesId = currentSeriesIdProvider() ?: return
        scope.launch {
            val ids = downloadRepository.getDownloadedEpisodeIdsForSeries(seriesId)
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
                downloadSheetEpisodes = emptyMap(),
                downloadSheetLoadingSeasons = emptySet(),
                downloadedEpisodeIds = emptySet(),
            )
        }
    }

    private fun qualityToMaxBitrate(quality: DownloadQuality): Int? = when (quality) {
        DownloadQuality.ORIGINAL -> null
        DownloadQuality.HIGH_1080P -> 8_000_000
        DownloadQuality.MEDIUM_720P -> 3_000_000
        DownloadQuality.LOW_480P -> 1_500_000
    }
}
