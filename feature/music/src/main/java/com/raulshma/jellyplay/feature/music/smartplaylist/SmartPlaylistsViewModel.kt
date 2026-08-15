package com.raulshma.jellyplay.feature.music.smartplaylist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.SmartPlaylistRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.CriterionOperator
import com.raulshma.jellyplay.core.model.CriterionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.PlaylistCriterion
import com.raulshma.jellyplay.core.model.SmartPlaylist
import com.raulshma.jellyplay.core.model.SmartPlaylistSort
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.viewmodel.MutableComposeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SmartPlaylistsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val audioQueueFacade: AudioQueueFacade,
    private val smartPlaylistRepository: SmartPlaylistRepository,
) : JellyPlayViewModel() {

    private val _playlists: MutableComposeState<List<SmartPlaylist>> = composeState(emptyList())
    val playlists: List<SmartPlaylist> get() = _playlists.value

    private val _generatedItems: MutableComposeState<List<MediaItem>> = composeState(emptyList())
    val generatedItems: List<MediaItem> get() = _generatedItems.value

    private val _isLoading: MutableComposeState<Boolean> = composeState(false)
    val isLoading: Boolean get() = _isLoading.value

    private val _error: MutableComposeState<String?> = composeState<String?>(null)
    val error: String? get() = _error.value

    init {
        launch {
            smartPlaylistRepository.observeSmartPlaylists().collectLatest { custom ->
                _playlists.value = defaultPlaylists + custom
            }
        }
    }

    fun createCustomPlaylist(
        name: String,
        criteria: List<PlaylistCriterion>,
        maxItems: Int = 50,
        sortBy: SmartPlaylistSort = SmartPlaylistSort.RANDOM,
    ) {
        if (name.isBlank()) return
        launch {
            _error.value = null
            val playlist = SmartPlaylist(
                id = "custom-${UUID.randomUUID()}",
                name = name.trim(),
                criteria = criteria,
                maxItems = maxItems,
                sortBy = sortBy,
            )
            smartPlaylistRepository.upsert(playlist)
        }
    }

    fun deleteCustomPlaylist(playlist: SmartPlaylist) {
        if (!playlist.id.startsWith("custom-")) return
        launch {
            smartPlaylistRepository.delete(playlist.id)
        }
    }

    fun generatePlaylist(playlist: SmartPlaylist) {
        launch {
            _isLoading.value = true
            _error.value = null

            val hasPlayCountFilter = playlist.criteria.any { it.type == CriterionType.PLAY_COUNT }
            val hasDateAddedSort = playlist.sortBy == SmartPlaylistSort.DATE_ADDED
            val hasPlayCountSort = playlist.sortBy == SmartPlaylistSort.PLAY_COUNT

            val sortOption = when {
                hasDateAddedSort -> com.raulshma.jellyplay.core.model.SortOption.DATE_ADDED
                hasPlayCountSort -> com.raulshma.jellyplay.core.model.SortOption.DATE_PLAYED
                playlist.sortBy == SmartPlaylistSort.RATING -> com.raulshma.jellyplay.core.model.SortOption.RATING
                playlist.sortBy == SmartPlaylistSort.TITLE -> com.raulshma.jellyplay.core.model.SortOption.SORT_NAME
                playlist.sortBy == SmartPlaylistSort.ARTIST -> com.raulshma.jellyplay.core.model.SortOption.ALBUM_ARTIST
                playlist.sortBy == SmartPlaylistSort.ALBUM -> com.raulshma.jellyplay.core.model.SortOption.ALBUM
                playlist.sortBy == SmartPlaylistSort.YEAR -> com.raulshma.jellyplay.core.model.SortOption.YEAR_DESC
                else -> com.raulshma.jellyplay.core.model.SortOption.SORT_NAME
            }

            val unplayedOnly = hasPlayCountFilter && playlist.criteria
                .filter { it.type == CriterionType.PLAY_COUNT }
                .any { it.operator == CriterionOperator.EQUALS && it.value == "0" }

            val nonPlayCountCriteria = playlist.criteria.filter { it.type != CriterionType.PLAY_COUNT }

            val favoriteOnly = playlist.criteria.isEmpty() && playlist.id == "favorites"

            val result = if (favoriteOnly) {
                mediaRepository.getFavorites(
                    mediaTypes = listOf(com.raulshma.jellyplay.core.model.MediaType.AUDIO),
                    limit = playlist.maxItems,
                )
            } else {
                val genreFilters = nonPlayCountCriteria.filter { it.type == CriterionType.GENRE }
                    .mapNotNull { it.value }
                mediaRepository.getMediaItems(
                    filters = com.raulshma.jellyplay.core.model.LibraryFilters(
                        mediaTypes = listOf(com.raulshma.jellyplay.core.model.MediaType.AUDIO),
                        genres = genreFilters.takeIf { it.isNotEmpty() }.orEmpty(),
                        sortBy = sortOption,
                    ),
                    limit = playlist.maxItems,
                )
            }

            result.onSuccess { searchResult ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    var items = searchResult.items
                    if (unplayedOnly) {
                        items = items.filter { !it.isPlayed }
                    }
                    items = applyCriteria(items, nonPlayCountCriteria)
                    if (!hasDateAddedSort && !hasPlayCountSort) {
                        items = applySort(items, playlist.sortBy)
                    }
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
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    fun playAll(startIndex: Int = 0) {
        launch {
            audioQueueFacade.playTracks(generatedItems, startIndex = startIndex)
        }
    }

    private fun applyCriteria(
        items: List<MediaItem>,
        criteria: List<PlaylistCriterion>,
    ): List<MediaItem> {
        return items.filter { item ->
            criteria.all { criterion ->
                when (criterion.type) {
                    CriterionType.GENRE -> when (criterion.operator) {
                        CriterionOperator.EQUALS -> item.genres.any { it.equals(criterion.value, ignoreCase = true) }
                        CriterionOperator.CONTAINS -> item.genres.any { it.contains(criterion.value, ignoreCase = true) }
                        else -> true
                    }
                    CriterionType.ARTIST -> when (criterion.operator) {
                        CriterionOperator.EQUALS -> item.albumArtist?.equals(criterion.value, ignoreCase = true) == true
                            || item.artistItems.any { it.name.equals(criterion.value, ignoreCase = true) }
                        CriterionOperator.CONTAINS -> item.albumArtist?.contains(criterion.value, ignoreCase = true) == true
                            || item.artistItems.any { it.name.contains(criterion.value, ignoreCase = true) }
                        else -> true
                    }
                    CriterionType.ALBUM -> when (criterion.operator) {
                        CriterionOperator.EQUALS -> item.album?.equals(criterion.value, ignoreCase = true) == true
                        CriterionOperator.CONTAINS -> item.album?.contains(criterion.value, ignoreCase = true) == true
                        else -> true
                    }
                    CriterionType.YEAR -> when (criterion.operator) {
                        CriterionOperator.EQUALS -> item.year?.toString() == criterion.value
                        CriterionOperator.GREATER_THAN -> item.year?.let { it > criterion.value.toIntOrNull() ?: 0 } == true
                        CriterionOperator.LESS_THAN -> item.year?.let { it < criterion.value.toIntOrNull() ?: Int.MAX_VALUE } == true
                        else -> true
                    }
                    CriterionType.RATING -> when (criterion.operator) {
                        CriterionOperator.GREATER_THAN -> item.communityRating?.let { it > criterion.value.toFloatOrNull() ?: 0f } == true
                        CriterionOperator.LESS_THAN -> item.communityRating?.let { it < criterion.value.toFloatOrNull() ?: 10f } == true
                        CriterionOperator.EQUALS -> item.communityRating?.let { it >= (criterion.value.toFloatOrNull() ?: 0f) } == true
                        else -> true
                    }
                    CriterionType.PLAY_COUNT -> when (criterion.operator) {
                        CriterionOperator.EQUALS -> if (criterion.value == "0") !item.isPlayed else item.isPlayed
                        CriterionOperator.GREATER_THAN -> item.isPlayed
                        CriterionOperator.LESS_THAN -> !item.isPlayed
                        else -> true
                    }
                    CriterionType.TAG -> when (criterion.operator) {
                        CriterionOperator.EQUALS -> item.tags.any { it.equals(criterion.value, ignoreCase = true) }
                        CriterionOperator.CONTAINS -> item.tags.any { it.contains(criterion.value, ignoreCase = true) }
                        else -> true
                    }
                }
            }
        }
    }

    private fun applySort(items: List<MediaItem>, sortBy: SmartPlaylistSort): List<MediaItem> {
        return when (sortBy) {
            SmartPlaylistSort.RANDOM -> items.shuffled()
            SmartPlaylistSort.TITLE -> items.sortedBy { it.name }
            SmartPlaylistSort.ARTIST -> items.sortedBy { it.albumArtist ?: it.artistItems.firstOrNull()?.name ?: it.name }
            SmartPlaylistSort.ALBUM -> items.sortedBy { it.album ?: "" }
            SmartPlaylistSort.YEAR -> items.sortedByDescending { it.year ?: 0 }
            SmartPlaylistSort.RATING -> items.sortedByDescending { it.communityRating ?: 0f }
            SmartPlaylistSort.PLAY_COUNT -> items.sortedByDescending { if (it.isPlayed) 1 else 0 }
            SmartPlaylistSort.DATE_ADDED -> items // Handled server-side via DateCreated sort
        }
    }

    companion object {
        val defaultPlaylists = listOf(
            SmartPlaylist(
                id = "top_rated",
                name = "Top Rated",
                criteria = listOf(
                    PlaylistCriterion(CriterionType.RATING, "4.0", CriterionOperator.GREATER_THAN),
                ),
                sortBy = SmartPlaylistSort.RATING,
                maxItems = 50,
            ),
            SmartPlaylist(
                id = "recently_added",
                name = "Recently Added",
                criteria = emptyList(),
                sortBy = SmartPlaylistSort.DATE_ADDED,
                maxItems = 50,
            ),
            SmartPlaylist(
                id = "unplayed",
                name = "Unplayed Tracks",
                criteria = listOf(
                    PlaylistCriterion(CriterionType.PLAY_COUNT, "0", CriterionOperator.EQUALS),
                ),
                sortBy = SmartPlaylistSort.RANDOM,
                maxItems = 50,
            ),
            SmartPlaylist(
                id = "favorites",
                name = "Favorites",
                criteria = emptyList(),
                sortBy = SmartPlaylistSort.RATING,
                maxItems = 50,
            ),
        )
    }
}
