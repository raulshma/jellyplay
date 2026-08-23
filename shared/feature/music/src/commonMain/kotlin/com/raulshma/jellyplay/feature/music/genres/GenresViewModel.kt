package com.raulshma.jellyplay.feature.music.genres

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel

class GenresViewModel(
    private val mediaRepository: MediaRepository,
) : JellyPlayViewModel() {

    private val _genres = stateFlow<List<Genre>>(emptyList())
    val genres = _genres.flow

    private val _isLoading = stateFlow(true)
    val isLoading = _isLoading.flow

    private val _error = stateFlow<String?>(null)
    val error = _error.flow

    init {
        loadGenres()
    }

    private fun loadGenres(force: Boolean = false) {
        launch {
            _isLoading.set(true)
            mediaRepository.getGenres(force = force)
                .onSuccess { _genres.set(it) }
                .onFailure { _error.set(it.message ?: "Failed to load genres") }
            _isLoading.set(false)
        }
    }

    fun refresh() {
        launch {
            loadGenres(force = true)
        }
    }
}
