package com.raulshma.jellyplay.feature.music.smartplaylist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.CriterionOperator
import com.raulshma.jellyplay.core.model.CriterionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.PlaylistCriterion
import com.raulshma.jellyplay.core.model.SmartPlaylist
import com.raulshma.jellyplay.core.model.SmartPlaylistSort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import javax.inject.Inject

@HiltViewModel
class SmartPlaylistsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : ViewModel() {

    var playlists by mutableStateOf(defaultPlaylists)
        private set

    var generatedItems by mutableStateOf<List<MediaItem>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    fun generatePlaylist(playlist: SmartPlaylist) {
        viewModelScope.launch {
            isLoading = true
            error = null
            mediaRepository.getMediaItems(
                mediaTypes = listOf(com.raulshma.jellyplay.core.model.MediaType.AUDIO),
                limit = playlist.maxItems,
            ).onSuccess { result ->
                var items = result.items
                items = applyCriteria(items, playlist.criteria)
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
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)

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
                    CriterionType.PLAY_COUNT -> true // Not available in basic MediaItem
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
            SmartPlaylistSort.PLAY_COUNT -> items // Not available
            SmartPlaylistSort.DATE_ADDED -> items // Not available
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
