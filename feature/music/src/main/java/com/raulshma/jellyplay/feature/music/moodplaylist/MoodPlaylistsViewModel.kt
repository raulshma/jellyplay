package com.raulshma.jellyplay.feature.music.moodplaylist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MoodPlaylist
import com.raulshma.jellyplay.core.model.MoodPlaylistSort
import com.raulshma.jellyplay.core.model.MoodPlaylistsPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoodPlaylistsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val audioPlaybackManager: AudioPlaybackManager,
) : ViewModel() {

    val playlists = MoodPlaylistsPreset.all

    var selectedPlaylist by mutableStateOf<MoodPlaylist?>(null)
        private set

    var generatedItems by mutableStateOf<List<MediaItem>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    fun generatePlaylist(playlist: MoodPlaylist) {
        viewModelScope.launch {
            isLoading = true
            error = null
            selectedPlaylist = playlist
            mediaRepository.getMediaItems(
                mediaTypes = listOf(MediaType.AUDIO),
                limit = 300,
                sortBy = "Random",
            ).onSuccess { result ->
                var items = result.items
                items = applyMoodFilter(items, playlist)
                items = applySort(items, playlist.sortBy)
                generatedItems = items.take(playlist.maxItems)
            }.onFailure {
                error = it.message ?: "Failed to generate playlist"
            }
            isLoading = false
        }
    }

    fun clearGenerated() {
        generatedItems = emptyList()
        selectedPlaylist = null
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)

    fun playAll(startIndex: Int = 0) {
        val queueItems = generatedItems.map { track ->
            AudioQueueItem(
                id = track.id,
                name = track.name,
                artist = track.albumArtist ?: track.artistItems.firstOrNull()?.name ?: "",
                album = track.album,
                imageUrl = getImageUrl(track.id),
                mediaSourceId = null,
                durationMs = track.runTimeTicks?.let { it / 10_000 } ?: 0L,
                normalizationGain = track.normalizationGain,
            )
        }
        audioPlaybackManager.playQueue(queueItems, startIndex)
    }

    private fun applyMoodFilter(items: List<MediaItem>, playlist: MoodPlaylist): List<MediaItem> {
        return items.filter { item ->
            val hasMatchingGenre = playlist.genreKeywords.any { keyword ->
                item.genres.any { genre ->
                    genre.contains(keyword, ignoreCase = true)
                }
            }
            val hasExcludedGenre = playlist.excludedGenres.any { excluded ->
                item.genres.any { genre ->
                    genre.contains(excluded, ignoreCase = true)
                }
            }
            val meetsRating = playlist.minRating?.let { min ->
                (item.communityRating ?: 0f) >= min
            } ?: true

            hasMatchingGenre && !hasExcludedGenre && meetsRating
        }
    }

    private fun applySort(items: List<MediaItem>, sortBy: MoodPlaylistSort): List<MediaItem> {
        return when (sortBy) {
            MoodPlaylistSort.RANDOM -> items.shuffled()
            MoodPlaylistSort.RATING -> items.sortedByDescending { it.communityRating ?: 0f }
            MoodPlaylistSort.YEAR_DESC -> items.sortedByDescending { it.year ?: 0 }
            MoodPlaylistSort.TITLE -> items.sortedBy { it.name }
        }
    }
}
