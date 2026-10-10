package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.GestureMode
import com.raulshma.jellyplay.core.model.InputPattern
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.PlayerAction
import com.raulshma.jellyplay.core.model.PlayerBinding
import com.raulshma.jellyplay.core.model.PlayerBindingIds
import com.raulshma.jellyplay.core.model.PlayerInputDefaults
import com.raulshma.jellyplay.core.model.PlayerInputMap
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Everything the binding editor screen renders, in one slice: the persisted
 * map plus the three behavior flags a reset (whole or per-row) honors. The
 * screen recomposes only when one of THESE fields moves — unrelated
 * playback-preference writes stay invisible.
 */
@Immutable
internal data class InputBindingsUiState(
    val map: PlayerInputMap,
    val gestureMode: GestureMode,
    val holdSpeedEnabled: Boolean,
    val doubleTapHoldSeekEnabled: Boolean,
)

/** What [InputBindingsViewModel.addKeyBinding] did with a captured combo. */
internal data class InputBindingsAddResult(
    /** The row that now owns the pattern (existing row keeps its id). */
    val bindingId: String,
    /** False when the pattern was already bound — the row was NOT touched. */
    val created: Boolean,
)

/** The editor's slice off one [PlaybackPreferences] emission — the single place the four fields are read. */
private fun inputBindingsUiState(prefs: PlaybackPreferences): InputBindingsUiState = InputBindingsUiState(
    map = prefs.videoInputBindings,
    gestureMode = prefs.videoGestureMode,
    holdSpeedEnabled = prefs.videoHoldSpeedEnabled,
    doubleTapHoldSeekEnabled = prefs.videoDoubleTapHoldSeekEnabled,
)

/**
 * The input-binding editor's ViewModel: reads the persisted [PlayerInputMap]
 * through the playback slice, writes whole-map edits through
 * [PreferencesEditor]. Every write replaces the full binding list — the
 * editor is the only writer of the blob besides the store's atomic
 * GestureMode preset (which the editor's rows observe as plain flag
 * changes).
 */
class InputBindingsViewModel(
    projections: PreferenceProjections,
    editor: PreferencesEditor,
) : SettingsEditorViewModel(editor) {

    /**
     * The screen's slice. `distinctUntilChanged` on the mapped data class:
     * PlayerInputMap is @Immutable and structural, so identical blobs
     * (store re-emissions) do not recompose the editor. EAGERLY, not the
     * sibling VMs' WhileSubscribed: [addKeyBinding] and [resetBinding]
     * read [uiState].value synchronously as their write pre-check — an
     * idle flow would give them a stale or empty map.
     */
    internal val uiState: StateFlow<InputBindingsUiState> = stateIn(
        initial = inputBindingsUiState(projections.playbackPreferences.value),
        started = SharingStarted.Eagerly,
        flow = projections.playbackPreferences
            .map(::inputBindingsUiState)
            .distinctUntilChanged(),
    )

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

    /**
     * The GestureMode preset chips: the same atomic preset the playback
     * settings row writes — a mass flip of the touch rows' enabled flags,
     * never a second gate.
     */
    fun setGestureMode(mode: GestureMode) {
        edit { scope -> scope.videoPlayer.setVideoGestureMode(mode) }
    }

    /**
     * The add-shortcut flow's single entry: a captured [pattern] either
     * creates a new unbound row (id from [PlayerBindingIds.customKeyId],
     * ready for the action picker) or already belongs to a row — then
     * NOTHING is written and the screen scrolls to that row instead. A
     * blind [PlayerInputMap.withBindingAdded] here would stamp Unbound
     * over a rebound row's action, which is exactly what the flash avoids.
     * The synchronous pre-read is safe: the editor is the only PATTERN
     * writer — the player's quick toggle flips enabled flags only, and
     * [updateMap] reads inside the edit regardless.
     */
    internal fun addKeyBinding(pattern: InputPattern.Key): InputBindingsAddResult {
        val existing = uiState.value.map.bindings.firstOrNull { it.pattern == pattern }
        if (existing != null) return InputBindingsAddResult(existing.id, created = false)
        val id = PlayerBindingIds.customKeyId(pattern)
        updateMap {
            it.withBindingAdded(PlayerBinding(id, pattern, PlayerAction.NONE, enabled = true))
        }
        return InputBindingsAddResult(id, created = true)
    }

    /** Swipe-delete on a user-captured row. Default rows never reach this. */
    fun removeBinding(bindingId: String) {
        updateMap { it.withoutBinding(bindingId) }
    }

    /**
     * Swipe-reset on one row: restore the row [bindingId] to exactly what
     * Reset-all would give it (same parameterized default map). Unknown or
     * default-equal rows no-op.
     */
    fun resetBinding(bindingId: String) {
        val state = uiState.value
        val default = PlayerInputDefaults.defaultBindingsById(
            gestureMode = state.gestureMode,
            holdSpeedEnabled = state.holdSpeedEnabled,
            doubleTapHoldSeekEnabled = state.doubleTapHoldSeekEnabled,
        )[bindingId] ?: return
        updateMap { it.withBindingAdded(default) }
    }

    /**
     * Factory reset for the whole mapping (custom rows included — the whole
     * blob is replaced), honoring the stored GestureMode preset AND the two
     * per-behavior flags, so a reset and a fresh read of the current
     * preference set agree row for row.
     */
    fun resetToDefaults() {
        edit { scope ->
            val prefs = uiState.value
            // defaultMap(...) seeds the touch rows' enabled flags from the
            // mode + behavior flags directly — the same map the absent-blob
            // legacy read produces, so a reset and a fresh read agree.
            scope.videoPlayer.setVideoInputBindings(
                PlayerInputDefaults.defaultMap(
                    gestureMode = prefs.gestureMode,
                    holdSpeedEnabled = prefs.holdSpeedEnabled,
                    doubleTapHoldSeekEnabled = prefs.doubleTapHoldSeekEnabled,
                ),
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
