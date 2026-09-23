package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.ItemPlaybackPreference
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.MpvInterpolationTscale
import com.raulshma.jellyplay.core.model.MpvRenderOverrides
import com.raulshma.jellyplay.core.model.MpvRenderQuality
import com.raulshma.jellyplay.core.model.MpvShaderPack
import com.raulshma.jellyplay.core.model.MpvToneMapping
import com.raulshma.jellyplay.core.model.PlaybackPrefScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Owns the "Rendering" sheet + gear-menu deinterlace write choreography
 * extracted from [VideoPlayerViewModel] (the [SubtitleStyleController]
 * shape): it composes the pure [SessionRenderState] semantics with the
 * writes around them — the global mpv slice persist (the global-only picks:
 * render quality / `tscale` / custom shader files), the per-item/series
 * render-profile rows (shader pack + tone mapping, through the
 * [ItemPlaybackPreferenceWriter] render commands), and the session
 * item-change re-resolution of the stored override.
 *
 * Like [SubtitleStyleController], this class never references
 * [VideoPlayerUiState] — the state lives in [state] ([SessionRenderState])
 * and every write flows through the narrow constructor lambdas below, so
 * the god-count ratchet stays at its baseline. The engine re-apply stays
 * VM-side: this controller reports a dirty config through [onConfigDirty]
 * (the VM's `updateConfigWithUiState`), and the engines' diff caches apply
 * the delta.
 *
 * Load-bearing invariants (pinned by [RenderControlsTest]):
 *  - a sheet override pick (shader pack / tone mapping) applies to the
 *    SESSION immediately and only persists when the sheet's "save" toggle
 *    is on — and when it persists, the FULL resolved override row is
 *    written, never a single-field patch (the stored row must stay
 *    self-contained for the next item's re-resolution);
 *  - a global-only pick mirrors into the [SessionRenderState.pending] lens
 *    BEFORE the async DataStore round-trip (the engine reflects the pick on
 *    the same config rebuild) and then persists the transformed GLOBAL
 *    slice — computed from the pre-write mirror, the pick never double-lands;
 *  - the deinterlace cycle is NEVER persisted (session-scoped only), and a
 *    session release ([onReleased]) reverts override + pending mirror +
 *    cycle while the persisted rows survive.
 */
internal class RenderControls(
    private val scope: CoroutineScope,
    /** The persisted GLOBAL mpv slice (the sheet pickers' global lens). */
    private val getGlobalMpvConfig: () -> MpvEngineConfig,
    /** Persists the global mpv slice (`stores.engine.setMpvConfig`). */
    private val saveGlobalMpvConfig: suspend (MpvEngineConfig) -> Unit,
    /** Reads a stored per-item/series preference row (the playback-pref repository `get`). */
    private val loadStoredRow: suspend (prefScope: PlaybackPrefScope, id: String) -> ItemPlaybackPreference?,
    /** Persists the per-item/series override ([ItemPlaybackPreferenceWriter.setRenderProfile]). */
    private val saveRenderProfile: (MpvRenderOverrides) -> Unit,
    /** Clears the persisted override in BOTH scopes ([ItemPlaybackPreferenceWriter.clearRenderProfile]). */
    private val clearStoredRenderProfile: () -> Unit,
    /** Immediate engine-config rebuild (`updateConfigWithUiState`). */
    private val onConfigDirty: () -> Unit,
) {

    /** The session-scoped render state this controller drives (the sheet + config builder read it). */
    val state: SessionRenderState = SessionRenderState()

    /** Pass-through of [getGlobalMpvConfig] — the sheet's pickers read the global slice through it. */
    val globalMpvConfig: MpvEngineConfig
        get() = getGlobalMpvConfig()

    /**
     * The "Rendering" sheet's shader-pack pick. Applies to the session
     * immediately (folded into every config build via [state]) and, when
     * [persist] is on (the "save for this series" toggle), pins the override
     * to the series row (or the item row for standalone movies).
     */
    fun setRenderShaderPack(pack: MpvShaderPack, persist: Boolean) =
        setSessionOverride(persist) { it.copy(shaderPack = pack) }

    /** The sheet's tone-mapping pick — same session/persist choreography as [setRenderShaderPack]. */
    fun setRenderToneMapping(mapping: MpvToneMapping, persist: Boolean) =
        setSessionOverride(persist) { it.copy(toneMapping = mapping) }

    /** The shared choreography of the sheet's override-field picks (see the two callers above). */
    private fun setSessionOverride(
        persist: Boolean,
        field: (MpvRenderOverrides) -> MpvRenderOverrides,
    ) {
        val next = field(state.override ?: MpvRenderOverrides())
        state.applyOverride(next)
        onConfigDirty()
        if (persist) saveRenderProfile(next)
    }

    /**
     * "Inherit (follow global)": clears the persisted override (both scopes)
     * and drops the session lens — the effective config is derived from the
     * global settings again.
     */
    fun clearRenderOverride() {
        state.applyOverride(null)
        onConfigDirty()
        clearStoredRenderProfile()
    }

    /**
     * The sheet's render-quality pick: a GLOBAL preference (part of the mpv
     * config slice, not the per-item override). Written through the store —
     * the session lens mirrors it so the engine reflects the pick before the
     * DataStore round-trip lands.
     */
    fun setRenderQuality(quality: MpvRenderQuality) = setGlobalMpvField(
        pending = { it.copy(renderQuality = quality) },
        config = { it.copy(renderQuality = quality) },
    )

    /** the interpolation `tscale` preset — global, beside the interpolation toggle. */
    fun setInterpolationTscale(tscale: MpvInterpolationTscale) = setGlobalMpvField(
        pending = { it.copy(interpolationTscale = tscale) },
        config = { it.copy(interpolationTscale = tscale) },
    )

    /** the CUSTOM pack's user `*.glsl` selection (absolute paths) — global. */
    fun setCustomShaderFiles(files: List<String>) = setGlobalMpvField(
        pending = { it.copy(customShaderFiles = files) },
        config = { it.copy(customShaderFiles = files) },
    )

    /**
     * The shared choreography of the sheet's global-only picks: mirror into
     * the session lens (so the engine reflects the pick before the DataStore
     * round-trip lands), rebuild the config, then write the global slice.
     */
    private fun setGlobalMpvField(
        pending: (SessionRenderState.PendingGlobalEdits) -> SessionRenderState.PendingGlobalEdits,
        config: (MpvEngineConfig) -> MpvEngineConfig,
    ) {
        state.applyPending(pending)
        onConfigDirty()
        val next = config(getGlobalMpvConfig())
        scope.launch {
            saveGlobalMpvConfig(next)
        }
    }

    /**
     * cycle the session-scoped deinterlace override AUTO→ON→OFF→AUTO.
     * Held in [state] (survives next-episode advance, reverts on player
     * exit) — deliberately NOT persisted.
     */
    fun cycleDeinterlace() {
        state.cycleDeinterlace()
        onConfigDirty()
    }

    /**
     * Re-resolves the session's render override for a NEW item (next-episode
     * advance / session settle): the stored item/series rows win (item row
     * first), null rows fall back to "the global settings stand". The
     * deinterlace cycle deliberately SURVIVES this — the session lives on
     * across an item switch ([SessionRenderState.onItemChanged]).
     */
    suspend fun onSessionItemChanged(itemId: String?, seriesId: String?) {
        val itemRow = itemId?.let { loadStoredRow(PlaybackPrefScope.ITEM, it) }
        val seriesRow = seriesId?.let { loadStoredRow(PlaybackPrefScope.SERIES, it) }
        state.onItemChanged(itemRow, seriesRow)
        onConfigDirty()
    }

    /**
     * Player exit: everything session-scoped reverts ([SessionRenderState.onReleased])
     * while the persisted override rows survive for the next playback.
     */
    fun onReleased() {
        state.onReleased()
    }
}
