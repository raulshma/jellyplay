package com.raulshma.jellyplay.feature.details

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.worker.DownloadWorker
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val downloadRepository: DownloadRepository,
    private val preferencesStore: UserPreferencesStore,
) : ViewModel() {

    val preferences = preferencesStore.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    private val _detail = mutableStateOf<MediaDetail?>(null)
    val detail: androidx.compose.runtime.State<MediaDetail?> get() = _detail
    private val _isLoading = mutableStateOf(false)
    val isLoading: androidx.compose.runtime.State<Boolean> get() = _isLoading
    private val _error = mutableStateOf<String?>(null)
    val error: androidx.compose.runtime.State<String?> get() = _error

    var seasons by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var episodes by mutableStateOf<Map<String, List<MediaItem>>>(emptyMap())
        private set
    // Tracks season IDs where a fetch was attempted (success or failure)
    // so the UI knows when to stop showing the loading skeleton.
    var fetchedSeasonIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var isDownloading by mutableStateOf(false)
        private set

    var smartPlayTarget by mutableStateOf<SmartPlayTarget?>(null)
        private set

    var selectedSubtitleIndex by mutableStateOf<Int?>(null)
        private set
    var selectedAudioIndex by mutableStateOf<Int?>(null)
        private set

    fun selectSubtitle(index: Int?) {
        selectedSubtitleIndex = index
        val itemId = _detail.value?.item?.id ?: return
        viewModelScope.launch {
            preferencesStore.setMediaStreamSelection(
                itemId = itemId,
                subtitleStreamIndex = index,
                audioStreamIndex = selectedAudioIndex,
            )
        }
    }

    fun selectAudio(index: Int?) {
        selectedAudioIndex = index
        val itemId = _detail.value?.item?.id ?: return
        viewModelScope.launch {
            preferencesStore.setMediaStreamSelection(
                itemId = itemId,
                audioStreamIndex = index,
                subtitleStreamIndex = selectedSubtitleIndex,
            )
        }
    }

    data class SmartPlayTarget(
        val episode: MediaItem,
        val label: String,
        val startPositionTicks: Long,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeDownload: Flow<com.raulshma.jellyplay.core.model.DownloadItem?> =
        _detail.let { detailState ->
            detailState.value?.item?.id?.let { itemId ->
                downloadRepository.getDownloadByMediaItemIdFlow(itemId)
            } ?: flowOf(null)
        }

    fun getDownloadFlow(itemId: String): Flow<com.raulshma.jellyplay.core.model.DownloadItem?> =
        downloadRepository.getDownloadByMediaItemIdFlow(itemId)

    fun loadItem(itemId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            // Reset season/episode state on fresh load
            seasons = emptyList()
            episodes = emptyMap()
            fetchedSeasonIds = emptySet()
            smartPlayTarget = null
            selectedSubtitleIndex = null
            selectedAudioIndex = null
            mediaRepository.getMediaDetail(itemId)
                .onSuccess { detail ->
                    _detail.value = detail
                    val streams = detail.mediaSources.firstOrNull()?.mediaStreams.orEmpty()
                    val storedSelections = preferences.value.mediaStreamSelections
                    val storedSelection = storedSelections[itemId]
                    val hasStoredSelection = storedSelections.containsKey(itemId)

                    selectedSubtitleIndex = if (hasStoredSelection) {
                        storedSelection?.subtitleStreamIndex
                    } else {
                        streams.firstOrNull { it.type == com.raulshma.jellyplay.core.model.StreamType.SUBTITLE && it.isDefault }?.index
                    }
                    selectedAudioIndex = if (hasStoredSelection) {
                        storedSelection?.audioStreamIndex
                    } else {
                        streams.firstOrNull { it.type == com.raulshma.jellyplay.core.model.StreamType.AUDIO && it.isDefault }?.index
                    }

                    viewModelScope.launch {
                        preferencesStore.setMediaStreamSelection(
                            itemId = itemId,
                            audioStreamIndex = selectedAudioIndex,
                            subtitleStreamIndex = selectedSubtitleIndex,
                        )
                    }
                    if (detail.item.mediaType == MediaType.SERIES) {
                        loadSeasons(itemId)
                    } else if (detail.item.mediaType == MediaType.EPISODE && detail.item.seriesId != null) {
                        loadSeasons(detail.item.seriesId!!)
                    } else {
                        smartPlayTarget = null
                    }
                }
                .onFailure { _error.value = it.message ?: "Failed to load details" }
            _isLoading.value = false
        }
    }

    private fun loadSeasons(seriesId: String) {
        viewModelScope.launch {
            mediaRepository.getSeasons(seriesId)
                .onSuccess { seasonList ->
                    seasons = seasonList
                    seasonList.forEach { season ->
                        loadEpisodes(seriesId, season.id)
                    }
                }
        }
    }

    private fun loadEpisodes(seriesId: String, seasonId: String) {
        viewModelScope.launch {
            mediaRepository.getEpisodes(seriesId, seasonId)
                .onSuccess { episodeList ->
                    episodes = episodes.toMutableMap().apply {
                        this[seasonId] = episodeList
                    }
                    maybeComputeSmartPlayTarget()
                }
                .onFailure {
                    // On failure store empty list so UI stops showing skeleton
                    if (!episodes.containsKey(seasonId)) {
                        episodes = episodes.toMutableMap().apply {
                            this[seasonId] = emptyList()
                        }
                    }
                    maybeComputeSmartPlayTarget()
                }
            // Always mark as fetched so UI never spins forever
            fetchedSeasonIds = fetchedSeasonIds + seasonId
        }
    }

    private fun maybeComputeSmartPlayTarget() {
        val item = _detail.value?.item ?: return
        when (item.mediaType) {
            MediaType.SERIES -> computeSeriesSmartPlayTarget()
            MediaType.EPISODE -> computeEpisodeSmartPlayTarget(item)
            else -> smartPlayTarget = null
        }
    }

    private fun computeSeriesSmartPlayTarget() {
        val allEpisodes = episodes.values.flatten()
        if (allEpisodes.isEmpty()) {
            smartPlayTarget = null
            return
        }
        val sorted = allEpisodes.sortedByPlaybackOrder()

        // Match Jellyfin's "next thing to watch" behavior: resume in-progress
        // episodes first, then continue with the first unwatched episode.
        val resumeEpisode = sorted.firstOrNull { it.hasResumeProgress() }
        if (resumeEpisode != null) {
            val s = resumeEpisode.seasonNumber ?: 1
            val e = resumeEpisode.episodeNumber ?: resumeEpisode.indexNumber ?: 1
            smartPlayTarget = SmartPlayTarget(
                episode = resumeEpisode,
                label = "Resume S${s}:E${e}",
                startPositionTicks = resumeEpisode.playbackPositionTicks ?: 0,
            )
            return
        }

        val nextEpisode = sorted.firstOrNull { !it.isPlayed }
        if (nextEpisode != null) {
            val s = nextEpisode.seasonNumber ?: 1
            val e = nextEpisode.episodeNumber ?: nextEpisode.indexNumber ?: 1
            val hasWatchedBefore = sorted
                .takeWhile { it.id != nextEpisode.id }
                .any { it.isPlayed || (it.playbackPositionTicks ?: 0L) > 0L }
            val label = if (hasWatchedBefore) {
                "Play Next S${s}:E${e}"
            } else {
                "Play S${s}:E${e}"
            }
            smartPlayTarget = SmartPlayTarget(
                episode = nextEpisode,
                label = label,
                startPositionTicks = 0,
            )
            return
        }

        val first = sorted.first()
        val s = first.seasonNumber ?: 1
        val e = first.episodeNumber ?: first.indexNumber ?: 1
        smartPlayTarget = SmartPlayTarget(
            episode = first,
            label = "Replay S${s}:E${e}",
            startPositionTicks = 0,
        )
    }

    private fun computeEpisodeSmartPlayTarget(currentEpisode: MediaItem) {
        val allEpisodes = episodes.values.flatten().sortedByPlaybackOrder()
        if (allEpisodes.isEmpty()) {
            smartPlayTarget = null
            return
        }
        val currentIndex = allEpisodes.indexOfFirst { it.id == currentEpisode.id }
        if (currentIndex < 0) {
            smartPlayTarget = null
            return
        }
        val s = currentEpisode.seasonNumber ?: 1
        val e = currentEpisode.episodeNumber ?: currentEpisode.indexNumber ?: 1
        val hasProgress = (currentEpisode.playbackPositionTicks ?: 0) > 0 && !currentEpisode.isPlayed
        smartPlayTarget = SmartPlayTarget(
            episode = currentEpisode,
            label = if (hasProgress) "Resume S${s}:E${e}" else "Play S${s}:E${e}",
            startPositionTicks = currentEpisode.playbackPositionTicks ?: 0,
        )
    }

    private fun MediaItem.hasResumeProgress(): Boolean =
        (playbackPositionTicks ?: 0L) > 0L && !isPlayed

    private fun List<MediaItem>.sortedByPlaybackOrder(): List<MediaItem> =
        sortedWith(
            compareBy<MediaItem>(
                { it.seasonNumber ?: Int.MAX_VALUE },
                { it.episodeNumber ?: it.indexNumber ?: Int.MAX_VALUE },
                { it.name },
            )
        )

    fun toggleFavorite() {
        val itemId = _detail.value?.item?.id ?: return
        viewModelScope.launch {
            mediaRepository.toggleFavorite(itemId)
                .onSuccess { loadItem(itemId) }
        }
    }

    fun markPlayed() {
        val itemId = _detail.value?.item?.id ?: return
        viewModelScope.launch {
            mediaRepository.markPlayed(itemId)
            loadItem(itemId)
        }
    }

    fun markUnplayed() {
        val itemId = _detail.value?.item?.id ?: return
        viewModelScope.launch {
            mediaRepository.markUnplayed(itemId)
            loadItem(itemId)
        }
    }

    fun startDownload() {
        val detail = _detail.value ?: return
        val item = detail.item
        val source = detail.mediaSources.firstOrNull() ?: return

        viewModelScope.launch {
            isDownloading = true
            val streamUrl = playbackRepository.getStreamUrl(item.id, source.id)
            val imageUrl = playbackRepository.getImageUrl(item.id, maxWidth = 300)
            val mediaType = when (item.mediaType) {
                MediaType.AUDIO, MediaType.MUSIC -> MediaType.AUDIO.name
                else -> item.mediaType.name
            }

            downloadRepository.startDownload(
                mediaItemId = item.id,
                name = item.name,
                mediaType = mediaType,
                mediaSourceId = source.id,
                downloadUrl = streamUrl,
                imageUrl = imageUrl,
                imageBlurHash = item.blurHashes.primary,
            ).onSuccess { downloadItem ->
                enqueueDownloadWorker(downloadItem.id)
            }
            isDownloading = false
        }
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)

    fun getBackdropUrl(itemId: String): String =
        playbackRepository.getBackdropUrl(itemId, maxWidth = 1280)

    private fun enqueueDownloadWorker(downloadId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(
                Data.Builder()
                    .putString(DownloadWorker.KEY_DOWNLOAD_ID, downloadId)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${DownloadWorker.UNIQUE_WORK_PREFIX}$downloadId",
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )
    }
}
