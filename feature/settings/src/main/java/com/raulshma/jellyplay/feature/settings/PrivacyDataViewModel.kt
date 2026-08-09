package com.raulshma.jellyplay.feature.settings

import android.content.Context
import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.ImageCache
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import com.raulshma.jellyplay.core.ui.feedback.uiTextOf
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Backs the Privacy & Data hub ([PrivacyDataScreen]). Re-exposes the same
 * destructive data actions that already live on dedicated screens
 * ([StorageSettingsViewModel] for cache/image-cache, [FactoryResetViewModel]
 * for the full-preferences reset, [SearchViewModel] for search-history clear)
 * so the user finds them in one place. The implementations are duplicated
 * here intentionally — the hub does not navigate into each screen, it fires
 * the action directly and emits a single [UserMessageBus] confirmation.
 *
 * State is fire-and-forget: each action runs on [Dispatchers.IO] and posts a
 * localized confirmation via [userMessageBus]. No persistent UI state is held.
 *
 * Sign-out is intentionally NOT handled here: it is an app-level concern
 * (`MainViewModel.logout`) and is invoked from the screen via the `onLogout`
 * callback threaded through the nav graph, mirroring [SettingsScreen].
 */
@HiltViewModel
class PrivacyDataViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val editor: PreferencesEditor,
    private val serverIdentityStore: ServerIdentityStore,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val userMessageBus: UserMessageBus,
) : JellyPlayViewModel() {

    /**
     * Wipes `context.cacheDir` (and the external cache dir) except the Coil
     * image cache. Mirrors [StorageSettingsViewModel.clearCache] — wiping
     * image_cache mid-session causes every visible image to re-decode.
     */
    fun clearCache() {
        launch {
            try {
                withContext(Dispatchers.IO) {
                    context.cacheDir.listFiles()?.forEach { child ->
                        if (child.name != ImageCache.DIR) {
                            child.deleteRecursively()
                        }
                    }
                    val externalCache = context.externalCacheDir
                    if (externalCache != null && externalCache.exists()) {
                        externalCache.listFiles()?.forEach { child ->
                            if (child.name != ImageCache.DIR) {
                                child.deleteRecursively()
                            }
                        }
                    }
                }
            } finally {
                userMessageBus.info(uiTextOf(R.string.settings_cache_cleared))
            }
        }
    }

    /**
     * Clears only the Coil image cache ([ImageCache.DIR]). Mirrors
     * [StorageSettingsViewModel.clearImageCache].
     */
    fun clearImageCache() {
        launch {
            try {
                withContext(Dispatchers.IO) {
                    val imageDir = File(context.cacheDir, ImageCache.DIR)
                    if (imageDir.exists()) {
                        imageDir.deleteRecursively()
                    }
                }
            } finally {
                userMessageBus.info(uiTextOf(R.string.settings_image_cache_cleared))
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
            userMessageBus.info(uiTextOf(R.string.settings_search_history_cleared))
        }
    }

    /**
     * Resets the entire preferences DataStore to factory defaults. Mirrors
     * [FactoryResetViewModel.resetAll]. Settings-only — does not sign out or
     * delete downloads/cache/DB.
     */
    fun factoryReset() {
        editor.clearAllPreferences()
        userMessageBus.info(uiTextOf(R.string.settings_factory_reset_all_done))
    }
}
