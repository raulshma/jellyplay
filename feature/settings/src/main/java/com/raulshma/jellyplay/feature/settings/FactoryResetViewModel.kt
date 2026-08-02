package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Backs the Factory Reset review screen. Holds the live [preferences] (current)
 * alongside the immutable [factory] baseline (`UserPreferences()` with all
 * default args) so the UI can render a per-category current-vs-default diff and
 * changed-count without duplicating default values.
 *
 * All writes flow through [PreferencesEditor] (the single auditable write seam)
 * — no new mutation path is introduced.
 */
@HiltViewModel
class FactoryResetViewModel @Inject constructor(
    private val preferencesAggregator: com.raulshma.jellyplay.core.datastore.legacy.UserPreferencesAggregator,
    private val editor: PreferencesEditor,
) : JellyPlayViewModel() {

    /** Factory baseline — `UserPreferences` constructed with every default arg. */
    val factory: UserPreferences = UserPreferences()

    var preferences by composeState(UserPreferences())
        private set

    init {
        launch {
            preferencesAggregator.preferences.collect { prefs ->
                preferences = prefs
            }
        }
    }

    /** Resets every preference in [category] to its factory default. */
    fun resetCategory(category: PreferenceResetCategory) {
        editor.resetCategory(category)
    }

    /** Resets the entire preferences DataStore to factory defaults. */
    fun resetAll() {
        editor.clearAllPreferences()
    }
}
