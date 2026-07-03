package com.raulshma.jellyplay.feature.downloads

import androidx.lifecycle.SavedStateHandle
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OfflineDetailViewModel @Inject constructor(
    private val offlineRepository: OfflineRepository,
    private val playbackRepository: PlaybackRepository,
    @Suppress("unused") savedStateHandle: SavedStateHandle,
) : JellyPlayViewModel() {

    private val _itemId = MutableStateFlow<String?>(null)

    val item: StateFlow<OfflineMediaItem?> =
        _itemId.flatMapLatest { id -> if (id == null) flowOf(null) else offlineRepository.getOfflineDetail(id) }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** Children (e.g. album tracks) for the item, with download rows joined. */
    val children: StateFlow<List<OfflineMediaItem>> =
        _itemId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else offlineRepository.getChildren(id)
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Drives the screen's data. Called once from a LaunchedEffect(itemId). */
    fun load(itemId: String) {
        if (_itemId.value == itemId) return
        _itemId.value = itemId
    }

    /**
     * Builds the primary-image URL for a cast member. The matching image is
     * preloaded into Coil's cache at download time, so this is a cache hit when
     * offline. Used by the cast row in the offline detail screen.
     */
    fun personImageUrl(personId: String): String =
        playbackRepository.getImageUrl(personId, maxWidth = 200)

    fun delete(onDone: () -> Unit) {
        val id = _itemId.value ?: return
        launch {
            offlineRepository.deleteOfflineItem(id)
            onDone()
        }
    }
}
