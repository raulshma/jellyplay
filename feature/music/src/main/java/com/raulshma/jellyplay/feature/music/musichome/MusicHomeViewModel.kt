package com.raulshma.jellyplay.feature.music.musichome

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MusicHomeSection(
    val title: String,
    val items: List<MediaItem>,
)

@HiltViewModel
class MusicHomeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val downloadRepository: com.raulshma.jellyplay.core.data.repository.DownloadRepository,
    private val preferencesStore: com.raulshma.jellyplay.core.datastore.UserPreferencesStore,
) : ViewModel() {

    var sections by mutableStateOf<List<MusicHomeSection>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var homeMode by mutableStateOf(HomeMode.VIDEO)
        private set

    val activeDownloadCount = downloadRepository.getActiveDownloadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch {
            preferencesStore.preferences.collect { prefs ->
                homeMode = prefs.homeMode
            }
        }
        loadSections()
    }

    fun loadSections() {
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                val sectionsList = mutableListOf<MusicHomeSection>()

                mediaRepository.getFavorites(
                    mediaTypes = listOf(MediaType.ARTIST),
                    limit = 20,
                ).getOrNull()?.items?.takeIf { it.isNotEmpty() }?.let {
                    sectionsList.add(MusicHomeSection("Favorite Artists", it))
                }

                mediaRepository.getMediaItems(
                    mediaTypes = listOf(MediaType.ALBUM),
                    sortBy = "DateCreated",
                    sortOrder = "Descending",
                    limit = 20,
                ).getOrNull()?.items?.takeIf { it.isNotEmpty() }?.let {
                    sectionsList.add(MusicHomeSection("Latest Albums", it))
                }

                mediaRepository.getMediaItems(
                    mediaTypes = listOf(MediaType.AUDIO),
                    sortBy = "DatePlayed",
                    sortOrder = "Descending",
                    limit = 20,
                ).getOrNull()?.items?.takeIf { it.isNotEmpty() }?.let {
                    sectionsList.add(MusicHomeSection("Recently Played", it))
                }

                mediaRepository.getMediaItems(
                    mediaTypes = listOf(MediaType.ALBUM),
                    sortBy = "CommunityRating",
                    sortOrder = "Descending",
                    limit = 20,
                ).getOrNull()?.items?.takeIf { it.isNotEmpty() }?.let {
                    sectionsList.add(MusicHomeSection("Top Rated Albums", it))
                }

                mediaRepository.getFavorites(
                    mediaTypes = listOf(MediaType.AUDIO),
                    limit = 20,
                ).getOrNull()?.items?.takeIf { it.isNotEmpty() }?.let {
                    sectionsList.add(MusicHomeSection("Favorite Tracks", it))
                }

                sections = sectionsList
            } catch (e: Exception) {
                error = e.message ?: "Failed to load music"
            }
            isLoading = false
        }
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)

    fun getBackdropUrl(itemId: String): String =
        playbackRepository.getBackdropUrl(itemId, maxWidth = 1280)
}
