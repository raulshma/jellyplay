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
import kotlinx.coroutines.delay
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
            name = ""
            filmography = emptyList()
            coroutineScope {
                val detailDeferred = async {
                    retryIO { mediaRepository.getMediaDetail(personId) }
                }
                val itemsDeferred = async {
                    retryIO { mediaRepository.getItemsByPerson(personId) }
                }

                val detailResult = detailDeferred.await()
                val itemsResult = itemsDeferred.await()

                if (detailResult.isSuccess && itemsResult.isSuccess) {
                    name = detailResult.getOrThrow().item.name
                    filmography = itemsResult.getOrThrow()
                } else {
                    val detailError = detailResult.exceptionOrNull()?.message
                    val itemsError = itemsResult.exceptionOrNull()?.message
                    error = itemsError ?: detailError ?: "Failed to load"
                }
            }
            isLoading = false
        }
    }

    private suspend fun <T> retryIO(
        times: Int = 3,
        initialDelay: Long = 1000,
        maxDelay: Long = 4000,
        factor: Double = 2.0,
        block: suspend () -> Result<T>
    ): Result<T> {
        var currentDelay = initialDelay
        repeat(times - 1) {
            val result = block()
            if (result.isSuccess) return result
            kotlinx.coroutines.delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
        return block()
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)
}
