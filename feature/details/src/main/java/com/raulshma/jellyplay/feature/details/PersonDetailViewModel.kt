package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.network.RetryPolicy
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : JellyPlayViewModel() {

    private val _name = composeState("")
    val name: String get() = _name.value

    private val _filmography = composeState<List<MediaItem>>(emptyList())
    val filmography: List<MediaItem> get() = _filmography.value

    private val _isLoading = composeState(true)
    val isLoading: Boolean get() = _isLoading.value

    private val _error = composeState<String?>(null)
    val error: String? get() = _error.value

    fun loadPerson(personId: String) {
        launch {
            _isLoading.value = true
            _error.value = null
            _name.value = ""
            _filmography.value = emptyList()
            coroutineScope {
                val detailDeferred = async {
                    RetryPolicy.executeWithRetry { mediaRepository.getMediaDetail(personId) }
                }
                val itemsDeferred = async {
                    RetryPolicy.executeWithRetry { mediaRepository.getItemsByPerson(personId) }
                }

                val detailResult = detailDeferred.await()
                val itemsResult = itemsDeferred.await()

                if (detailResult.isSuccess && itemsResult.isSuccess) {
                    _name.value = detailResult.getOrThrow().item.name
                    _filmography.value = itemsResult.getOrThrow()
                } else {
                    val detailError = detailResult.exceptionOrNull()?.message
                    val itemsError = itemsResult.exceptionOrNull()?.message
                    _error.value = itemsError ?: detailError ?: "Failed to load"
                }
            }
            _isLoading.value = false
        }
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)
}
