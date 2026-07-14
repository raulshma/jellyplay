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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val store: UserPreferencesStore,
    private val editor: PreferencesEditor,
    private val mediaRepository: MediaRepository,
    private val notificationScheduler: NotificationScheduler,
) : JellyPlayViewModel() {

    /** Notification-screen slice — recomposes this screen only on notification-field writes. */
    val preferences: StateFlow<NotificationPreferences> = store.notificationPreferences

    val showAdvancedSettings: StateFlow<Boolean> = store.preferences
        .map { it.showAdvancedSettings }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    private val _libraryFolders = MutableStateFlow<List<LibraryFolder>>(emptyList())
    val libraryFolders: StateFlow<List<LibraryFolder>> = _libraryFolders.asStateFlow()

    var libraryError by composeState<String?>(null)
        private set

    init {
        loadLibraryFolders()
    }

    fun setShowAdvancedSettings(enabled: Boolean) =
        editor.edit { setShowAdvancedSettings(enabled) }

    /**
     * Applies a transform to the notification preferences and reschedules the
     * notification worker against the updated config.
     */
    fun updateNotificationPreferences(transform: (NotificationPreferences) -> NotificationPreferences) {
        editor.edit {
            updateNotificationPreferences(transform)
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
