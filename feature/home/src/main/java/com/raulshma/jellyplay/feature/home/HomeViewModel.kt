package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.HomeSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@Stable
class HomeState {
    var sections by mutableStateOf<List<HomeSection>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun updateSections(sections: List<HomeSection>) { this.sections = sections }
    fun setLoading(loading: Boolean) { isLoading = loading }
    fun setError(error: String?) { this.error = error }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : ViewModel() {

    private val _state = HomeState()
    val sections get() = _state.sections
    val isLoading get() = _state.isLoading
    val error get() = _state.error

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.setLoading(true)
            _state.setError(null)
            mediaRepository.getHomeSections()
                .onSuccess { sections ->
                    _state.updateSections(sections)
                }
                .onFailure {
                    _state.setError(it.message ?: "Failed to load home sections")
                }
            _state.setLoading(false)
        }
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)
}
