package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate

/**
 * The change-time strategy over [PLAYER_PREF_LEAVES]: reads the latest
 * [VideoPlayerAggregate] + the current uiState and applies only the PROJECTED
 * rows (those with a state read — the same leaves the seed applies at load
 * time, plus the input map) that actually changed, preserving the
 * distinct-until-changed guards the inline collector previously hand-wrote
 * per field. One uiState copy per changed leaf; an unchanged leaf performs
 * no copy at all.
 *
 * Two projections stay hand-written because they are not table rows (see the
 * table's KDoc): the HDR-aware subtitle-style resolution below, and the PIN
 * pair (two uiPrefs leaves from one pref + one derived flag, written in ONE
 * guarded copy).
 *
 * [project] returns `true` when the resolved subtitle style changed — the VM
 * must then rebuild the engine config (`updateConfigWithUiState`); every
 * other projected field has no downstream side effect (the render-pref
 * rebuild triggers are [PlayerPrefsFanout]'s, derived from the same table's
 * rebuild flags).
 *
 * Engine + session access is via lambdas so this class stays ViewModel-agnostic.
 * The `getItemId` / `getMediaStreams` reads feed the per-item audio/subtitle
 * override + HDR-aware subtitle-style resolution.
 *
 * The *load-time* counterpart strategy is [PlayerPrefsSeed]; both read the
 * same leaf table, so a row moves between them by editing its row, not two
 * files.
 */
internal class SettingsProjector(
    private val getUiState: () -> VideoPlayerUiState,
    private val updateUiState: ((VideoPlayerUiState) -> VideoPlayerUiState) -> Unit,
    private val getItemId: () -> String?,
    private val getMediaStreams: () -> List<com.raulshma.jellyplay.core.model.MediaStream>,
) {

    /**
     * Apply the prefs-derived uiState slice, one guarded write per changed
     * projected row. Returns `true` when the resolved subtitle style changed.
     */
    fun project(agg: VideoPlayerAggregate): Boolean {
        var subtitleStyleChanged = false

        // HDR-aware subtitle style: the resolved style depends on whether the
        // current streams carry HDR, so re-derive on every prefs tick. The VM
        // must rebuild the engine config when this changes.
        //
        // Subtitle delay is per-media and authoritative via the per-item store
        // (written by setSubtitleDelay), NOT the global style bucket. Preserve
        // the resolved per-item delay here so a DataStore re-emission — including
        // the very write that just stored this delay — can't clobber the live
        // offsetMs back to the global default. Without this, writing the delay
        // re-emits the slice, this projector overwrites offsetMs to the global
        // default (often 0), and the resulting config push reloads media with the
        // delay removed. Mirrors the engineFlow collector's resolve step.
        val resolvedSubtitleStyle = resolveSubtitleStyleWithDelay(
            agg.subtitle,
            getItemId(),
            isHdr = isHdrFromStreams(getMediaStreams()),
        )
        if (getUiState().subtitleStyle != resolvedSubtitleStyle) {
            updateUiState { it.copy(subtitleStyle = resolvedSubtitleStyle) }
            subtitleStyleChanged = true
        }

        // The projected rows: the same leaves the seed applies at load time,
        // guarded so an unrelated pref write (dozens emit UserPreferences)
        // does not allocate a fresh uiState copy or re-emit to every
        // collector. The state reads / writes come from the table's rows —
        // adding a change-time leaf is adding (or projecting) a row, not a
        // hand-written syncPref block.
        for (leaf in PLAYER_PREF_LEAVES) {
            val readState = leaf.readStateAny ?: continue
            val newValue = leaf.readAggregateAny(agg)
            if (readState(getUiState()) != newValue) {
                val write = leaf.writeAny
                updateUiState { it.write(newValue) }
            }
        }

        // PIN lock: two uiPrefs leaves driven by one pref + one derived flag,
        // written in ONE guarded copy (a table row pair would write two).
        val hasPin = agg.security.pinHash != null
        if (getUiState().uiPrefs.usePinForPlayerLock != agg.security.usePinForPlayerLock || getUiState().uiPrefs.hasPin != hasPin) {
            updateUiState { it.copy(uiPrefs = it.uiPrefs.copy(usePinForPlayerLock = agg.security.usePinForPlayerLock, hasPin = hasPin)) }
        }

        return subtitleStyleChanged
    }
}
