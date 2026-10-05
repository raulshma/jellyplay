package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.PlayerAction
import com.raulshma.jellyplay.core.model.PlayerInputDefaults
import com.raulshma.jellyplay.core.model.PlayerInputMap
import kotlinx.coroutines.flow.StateFlow

/**
 * The input-binding editor's ViewModel (issue #171 generalized): reads the
 * persisted [PlayerInputMap] through the playback slice, writes whole-map
 * edits through [PreferencesEditor]. Every write replaces the full binding
 * list — the editor is the only writer of the blob besides the store's
 * atomic GestureMode preset (which the editor's rows observe as plain flag
 * changes).
 */
class InputBindingsViewModel(
    projections: PreferenceProjections,
    editor: PreferencesEditor,
) : SettingsEditorViewModel(editor) {

    /** Playback-screen slice — recomposes the editor only on playback-field writes. */
    val preferences: StateFlow<PlaybackPreferences> = projections.playbackPreferences

    /**
     * Rebinds one row's action. An NONE action keeps the row (explicit
     * unbind); a rebound row wakes up. Rebinding never touches patterns, so
     * it cannot itself create a duplicate — the whole-map write guard below
     * still runs, for candidates that would preserve a hand-edited blob's
     * pre-existing duplicates.
     */
    fun setBindingAction(bindingId: String, action: PlayerAction) {
        updateMap { it.withBindingAction(bindingId, action) }
    }

    /** The quick-toggle switch: enabled flag only, binding untouched. */
    fun setBindingEnabled(bindingId: String, enabled: Boolean) {
        updateMap { it.withBindingEnabled(bindingId, enabled) }
    }

    /** Factory reset for the whole mapping, honoring the stored GestureMode preset. */
    fun resetToDefaults() {
        edit { scope ->
            // defaultMap(mode) seeds the touch rows' enabled flags from the
            // mode directly — the same map the absent-blob legacy read
            // produces, so a reset and a fresh read agree.
            scope.videoPlayer.setVideoInputBindings(
                PlayerInputDefaults.defaultMap(gestureMode = preferences.value.videoGestureMode),
            )
        }
    }

    private fun updateMap(transform: (PlayerInputMap) -> PlayerInputMap) {
        edit { scope ->
            // Read-modify-write INSIDE the edit (the store decodes the stored
            // blob there), so a quick toggle from the player and an editor
            // write can never interleave two reads and lose one flip. The
            // exact-duplicate write guard (the design's hybrid policy: a
            // pattern can hold one row only) blocks a candidate that would
            // preserve a duplicate — only reachable from a hand-edited or
            // restored blob, never from an edit that started clean.
            scope.videoPlayer.updateVideoInputBindings { current ->
                val candidate = transform(current)
                if (candidate != current && !candidate.hasNoDuplicates()) current else candidate
            }
        }
    }
}
