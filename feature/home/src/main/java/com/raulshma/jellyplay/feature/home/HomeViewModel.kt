package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.HomeSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val preferencesStore: com.raulshma.jellyplay.core.datastore.UserPreferencesStore,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {

    var sections by mutableStateOf<List<HomeSection>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var kidsModeEnabled by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            preferencesStore.preferences.collect { prefs ->
                kidsModeEnabled = prefs.kidsModeEnabled
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            isLoading = true
            error = null
            val prefs = preferencesStore.preferences.first()
            mediaRepository.getHomeSections()
                .onSuccess { sections ->
                    val filteredSections = if (prefs.kidsModeEnabled) {
                        sections.map { section ->
                            section.copy(items = section.items.filter { isAllowedForKids(it, prefs.kidsModeMaxRating) })
                        }.filter { it.items.isNotEmpty() }
                    } else sections
                    this@HomeViewModel.sections = filteredSections
                    val continueWatching = filteredSections
                        .find { it.type == com.raulshma.jellyplay.core.model.HomeSectionType.CONTINUE_WATCHING }
                        ?.items ?: emptyList()
                    preferencesStore.setContinueWatching(continueWatching)
                    val intent = android.content.Intent("com.raulshma.jellyplay.widget.ACTION_REFRESH_CONTINUE_WATCHING")
                    intent.setPackage(context.packageName)
                    context.sendBroadcast(intent)
                }
                .onFailure {
                    error = it.message ?: "Failed to load home sections"
                }
            isLoading = false
        }
    }

    private fun isAllowedForKids(item: com.raulshma.jellyplay.core.model.MediaItem, maxRating: String): Boolean {
        if (item.officialRating == null) return true
        val kidRatings = listOf("G", "TV-Y", "TV-Y7", "TV-G", "PG", "TV-PG")
        val maxIndex = kidRatings.indexOf(maxRating)
        val itemIndex = kidRatings.indexOf(item.officialRating)
        return if (itemIndex >= 0 && maxIndex >= 0) itemIndex <= maxIndex else true
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)
}
