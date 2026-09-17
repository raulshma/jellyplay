package com.raulshma.jellyplay.feature.music.moodplaylist

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.MoodPlaylistRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MoodPlaylist
import com.raulshma.jellyplay.core.model.MoodPlaylistSort
import com.raulshma.jellyplay.core.model.MoodPlaylistsPreset
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.music.GeneratedPlaylistState
import com.raulshma.jellyplay.feature.music.MusicQueuePlayer
import kotlinx.coroutines.flow.collectLatest

class MoodPlaylistsViewModel(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
    audioQueueFacade: MusicQueuePlayer,
    private val moodPlaylistRepository: MoodPlaylistRepository,
) : JellyPlayViewModel() {

    /**
     * The generate pipeline (loading/error/selection/items lifecycle), the
     * `"custom-"` id convention + delete guard, clearGenerated and playAll
     * live in the shared [GeneratedPlaylistState]; what stays here is the
     * mood-kind adapters — the fetch (random 300-audio pull) and the client
     * filter/exclusion/rating + sort + take pipeline — plus the
     * repository-derived playlist/favorite state the holder doesn't own.
     */
    private val generated = GeneratedPlaylistState<MoodPlaylist>(scope, audioQueueFacade)

    private val _playlists = composeState<List<MoodPlaylist>>(MoodPlaylistsPreset.all)
    val playlists: List<MoodPlaylist> get() = _playlists.value

    private val _favoritePlaylistIds = composeState<Set<String>>(emptySet())
    val favoritePlaylistIds: Set<String> get() = _favoritePlaylistIds.value

    val selectedPlaylist: MoodPlaylist? get() = generated.selectedPlaylist

    val generatedItems: List<MediaItem> get() = generated.generatedItems

    val isLoading: Boolean get() = generated.isLoading

    val error: String? get() = generated.error

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
            generated.clearError()
            val playlist = MoodPlaylist(
                id = GeneratedPlaylistState.newCustomId(),
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
        if (!GeneratedPlaylistState.isCustomId(playlist.id)) return
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
        generated.generate(
            playlist = playlist,
            fetch = {
                mediaRepository.getMediaItems(
                    filters = LibraryFilters(
                        mediaTypes = listOf(MediaType.AUDIO),
                        sortBy = SortOption.RANDOM,
                    ),
                    limit = 300,
                )
            },
            process = { items ->
                val filtered = applyMoodFilter(items, playlist)
                val sorted = applySort(filtered, playlist.sortBy)
                sorted.take(playlist.maxItems)
            },
        )
    }

    fun clearGenerated() {
        generated.clearGenerated()
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    fun playAll(startIndex: Int = 0) {
        generated.playAll(startIndex)
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
