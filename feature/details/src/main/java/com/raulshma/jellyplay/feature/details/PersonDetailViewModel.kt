package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : ViewModel() {

    var name by mutableStateOf("")
        private set
    var filmography by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun loadPerson(personId: String) {
        viewModelScope.launch {
            isLoading = true
            error = null
            coroutineScope {
                val detailDeferred = async { mediaRepository.getMediaDetail(personId) }
                val itemsDeferred = async { mediaRepository.getItemsByPerson(personId) }
                detailDeferred.await()
                    .onSuccess { detail -> name = detail.item.name }
                itemsDeferred.await()
                    .onSuccess { items -> filmography = items }
                    .onFailure { error = it.message ?: "Failed to load" }
            }
            isLoading = false
        }
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)
}
