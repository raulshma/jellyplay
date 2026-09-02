package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Backs the Privacy & Data hub ([PrivacyDataScreen]). Re-exposes the same
 * destructive data actions that already live on dedicated screens
 * ([StorageSettingsViewModel] for cache/image-cache, [FactoryResetViewModel]
 * for the full-preferences reset, [SearchViewModel] for search-history clear)
 * so the user finds them in one place. The implementations are duplicated
 * here intentionally — the hub does not navigate into each screen, it fires
 * the action directly and emits a single [PrivacyUserMessage] confirmation.
 *
 * State is fire-and-forget: each action runs in a VM coroutine and posts a
 * confirmation to [messages] (buffered Channel, single collector, never
 * replayed — same semantics as the legacy UserMessageBus). The FS cache wipes
 * delegate to the [StorageAreas] platform seam (the legacy bodies read
 * `Context` directly); Wave 2 binds the actuals at the Koin edge.
 *
 * Sign-out is intentionally NOT handled here: it is an app-level concern
 * (`MainViewModel.logout`) and is invoked from the screen via the `onLogout`
 * callback threaded through the nav graph, mirroring [SettingsScreen].
 */
class PrivacyDataViewModel(
    private val editor: PreferencesEditor,
    private val serverIdentityStore: ServerIdentityStore,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val storageAreas: StorageAreas,
) : JellyPlayViewModel() {

    private val messageChannel = Channel<PrivacyUserMessage>(Channel.BUFFERED)
    val messages: Flow<PrivacyUserMessage> = messageChannel.receiveAsFlow()

    /**
     * Wipes the cache dirs except the Coil image cache. Mirrors
     * [StorageSettingsViewModel.clearCache] — wiping image_cache mid-session
     * causes every visible image to re-decode.
     */
    fun clearCache() {
        launch {
            try {
                storageAreas.clearCache()
            } finally {
                messageChannel.trySend(PrivacyUserMessage.CacheCleared)
            }
        }
    }

    /**
     * Clears only the Coil image cache. Mirrors
     * [StorageSettingsViewModel.clearImageCache].
     */
    fun clearImageCache() {
        launch {
            try {
                storageAreas.clearImageCache()
            } finally {
                messageChannel.trySend(PrivacyUserMessage.ImageCacheCleared)
            }
        }
    }

    /**
     * Clears the current user's search history. The userId is read once from
     * [ServerIdentityStore.activeUserId] (the same source [SearchViewModel]
     * uses); if there is no active user the action is a no-op.
     */
    fun clearSearchHistory() {
        launch {
            val userId = serverIdentityStore.activeUserId.first()
            if (userId != null) {
                searchHistoryRepository.clearAll(userId)
            }
            messageChannel.trySend(PrivacyUserMessage.SearchHistoryCleared)
        }
    }

    /**
     * Resets the entire preferences DataStore to factory defaults. Mirrors
     * [FactoryResetViewModel.resetAll]. Settings-only — does not sign out or
     * delete downloads/cache/DB.
     */
    fun factoryReset() {
        editor.clearAllPreferences()
        messageChannel.trySend(PrivacyUserMessage.FactoryResetDone)
    }
}
