package com.raulshma.jellyplay.feature.music.moodplaylist

import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.toAudioQueueItem
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.MoodPlaylistRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MoodPlaylist
import com.raulshma.jellyplay.core.model.MoodPlaylistSort
import com.raulshma.jellyplay.core.model.MoodPlaylistsPreset
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MoodPlaylistsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val audioPlaybackManager: AudioPlaybackManager,
    private val moodPlaylistRepository: MoodPlaylistRepository,
) : JellyPlayViewModel() {

    private val _playlists = composeState<List<MoodPlaylist>>(MoodPlaylistsPreset.all)
    val playlists: List<MoodPlaylist> get() = _playlists.value

    private val _favoritePlaylistIds = composeState<Set<String>>(emptySet())
    val favoritePlaylistIds: Set<String> get() = _favoritePlaylistIds.value

    private val _selectedPlaylist = composeState<MoodPlaylist?>(null)
    val selectedPlaylist: MoodPlaylist? get() = _selectedPlaylist.value

    private val _generatedItems = composeState<List<MediaItem>>(emptyList())
    val generatedItems: List<MediaItem> get() = _generatedItems.value

    private val _isLoading = composeState(false)
    val isLoading: Boolean get() = _isLoading.value

    private val _error = composeState<String?>(null)
    val error: String? get() = _error.value

    init {
        launch {
            combinePlaylists().collectLatest { combined ->
                _playlists.value = MoodPlaylistsPreset.all + combined.custom
                _favoritePlaylistIds.value = combined.favorites
            }
        }
    }

    private data class CombinedPlaylists(
        val custom: List<MoodPlaylist>,
        val favorites: Set<String>,
    )

    private fun combinePlaylists(): kotlinx.coroutines.flow.Flow<CombinedPlaylists> =
        kotlinx.coroutines.flow.combine(
            moodPlaylistRepository.observeMoodPlaylists(),
            moodPlaylistRepository.observePreferences(),
        ) { custom, prefs ->
            CombinedPlaylists(
                custom = custom,
                favorites = prefs.filter { it.isFavorite }.map { it.playlistId }.toSet(),
            )
        }

    fun createCustomPlaylist(
        name: String,
        emoji: String,
        description: String,
        genreKeywords: List<String>,
        excludedGenres: List<String> = emptyList(),
        minRating: Float? = null,
        maxItems: Int = 50,
        sortBy: MoodPlaylistSort = MoodPlaylistSort.RANDOM,
        themeColorHex: String? = null,
    ) {
        if (name.isBlank() || genreKeywords.isEmpty()) return
        launch {
            _error.value = null
            val playlist = MoodPlaylist(
                id = "custom-${UUID.randomUUID()}",
                name = name.trim(),
                emoji = emoji.ifBlank { "🎵" },
                description = description.trim(),
                genreKeywords = genreKeywords,
                excludedGenres = excludedGenres,
                minRating = minRating,
                sortBy = sortBy,
                maxItems = maxItems,
                themeColorHex = themeColorHex,
            )
            moodPlaylistRepository.upsert(playlist)
        }
    }

    fun deleteCustomPlaylist(playlist: MoodPlaylist) {
        if (!playlist.id.startsWith("custom-")) return
        launch {
            moodPlaylistRepository.delete(playlist.id)
        }
    }

    fun toggleFavorite(playlist: MoodPlaylist) {
        launch {
            val isFavorite = playlist.id in favoritePlaylistIds
            moodPlaylistRepository.setPreference(
                playlistId = playlist.id,
                isFavorite = !isFavorite,
            )
        }
    }

    fun generatePlaylist(playlist: MoodPlaylist) {
        launch {
            _isLoading.value = true
            _error.value = null
            _selectedPlaylist.value = playlist
            mediaRepository.getMediaItems(
                filters = com.raulshma.jellyplay.core.model.LibraryFilters(
                    mediaTypes = listOf(MediaType.AUDIO),
                    sortBy = com.raulshma.jellyplay.core.model.SortOption.RANDOM,
                ),
                limit = 300,
            ).onSuccess { result ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    var items = result.items
                    items = applyMoodFilter(items, playlist)
                    items = applySort(items, playlist.sortBy)
                    items.take(playlist.maxItems)
                }.also { items ->
                    _generatedItems.value = items
                }
            }.onFailure {
                _error.value = it.message ?: "Failed to generate playlist"
            }
            _isLoading.value = false
        }
    }

    fun clearGenerated() {
        _generatedItems.value = emptyList()
        _selectedPlaylist.value = null
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    fun playAll(startIndex: Int = 0) {
        val queueItems = generatedItems.map { track ->
            track.toAudioQueueItem(imageUrl = getImageUrl(track.id))
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
