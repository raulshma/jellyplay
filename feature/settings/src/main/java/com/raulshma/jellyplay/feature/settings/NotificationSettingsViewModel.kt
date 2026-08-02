package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.notification.scheduler.NotificationScheduler
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val store: UserPreferencesStore,
    private val projections: com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections,
    private val appearanceStore: com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore,
    private val editor: PreferencesEditor,
    private val mediaRepository: MediaRepository,
    private val notificationScheduler: NotificationScheduler,
) : JellyPlayViewModel() {

    /** Notification-screen slice — recomposes this screen only on notification-field writes. */
    val preferences: StateFlow<NotificationPreferences> = projections.notificationPreferences

    val showAdvancedSettings: StateFlow<Boolean> = appearanceStore.showAdvancedSettings

    private val _libraryFolders = MutableStateFlow<List<LibraryFolder>>(emptyList())
    val libraryFolders: StateFlow<List<LibraryFolder>> = _libraryFolders.asStateFlow()

    var libraryError by composeState<String?>(null)
        private set

    init {
        loadLibraryFolders()
    }

    fun setShowAdvancedSettings(enabled: Boolean) =
        editor.edit { appearance.setShowAdvancedSettings(enabled) }

    /**
     * Applies a transform to the notification preferences and reschedules the
     * notification worker against the updated config.
     */
    fun updateNotificationPreferences(transform: (NotificationPreferences) -> NotificationPreferences) {
        editor.edit {
            notification.updateNotificationPreferences(transform)
            notificationScheduler.scheduleOrUpdate()
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
