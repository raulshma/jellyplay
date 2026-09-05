package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Platform seam behind [NotificationSettingsViewModel]'s reschedule poke: the
 * legacy NotificationScheduler (WorkManager enqueue/cancel over the
 * notification store) lives in the Hilt-owned Android-only :core:notification,
 * which this module cannot reach. The Android composition root provides the
 * wrapping impl at the Koin edge (downloads-conveyor interop pattern); desktop
 * has no notification worker and binds a no-op. Suspend + no-args, matching
 * the legacy `NotificationScheduler.scheduleOrUpdate()` signature exactly.
 */
fun interface NotificationSync {
    suspend fun scheduleOrUpdate()
}

class NotificationSettingsViewModel(
    private val store: UserPreferencesStore,
    private val projections: com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections,
    private val appearanceStore: com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore,
    private val editor: PreferencesEditor,
    private val mediaRepository: MediaRepository,
    private val notificationSync: NotificationSync,
) : JellyPlayViewModel() {

    /** Notification-screen slice — recomposes this screen only on notification-field writes. */
    val preferences: StateFlow<NotificationPreferences> = projections.notificationPreferences

    private val advancedSettings = AdvancedSettingsGate(appearanceStore, editor)

    val showAdvancedSettings: StateFlow<Boolean> = advancedSettings.showAdvancedSettings

    private val _libraryFolders = MutableStateFlow<List<LibraryFolder>>(emptyList())
    val libraryFolders: StateFlow<List<LibraryFolder>> = _libraryFolders.asStateFlow()

    var libraryError by composeState<String?>(null)
        private set

    init {
        loadLibraryFolders()
    }

    fun setShowAdvancedSettings(enabled: Boolean) = advancedSettings.setShowAdvancedSettings(enabled)

    /**
     * Applies a transform to the notification preferences and reschedules the
     * notification worker against the updated config.
     */
    fun updateNotificationPreferences(transform: (NotificationPreferences) -> NotificationPreferences) {
        editor.edit {
            notification.updateNotificationPreferences(transform)
            notificationSync.scheduleOrUpdate()
        }
    }

    private fun loadLibraryFolders() {
        launch {
            libraryError = null
            mediaRepository.getLibraryFolders()
                .onSuccess { folders ->
                    _libraryFolders.value = folders.filter { it.collectionType != "music" }
                }
                .onFailure { error -> libraryError = error.message ?: error::class.simpleName }
        }
    }
}
