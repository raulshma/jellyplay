package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.DeinterlaceMode
import com.raulshma.jellyplay.core.model.ItemPlaybackPreference
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.MpvRenderQuality
import com.raulshma.jellyplay.core.model.MpvRenderOverrides

/**
 * The SESSION-SCOPED render state: what the "Rendering"
 * sheet and the deinterlace cycle toggle mean for the CURRENT playback
 * session, and how it folds into every [MpvEngineConfig] the config builder
 * produces. Pure state + pure folds — the ViewModel owns the repository /
 * DataStore writes and the engine re-apply, this class owns the semantics:
 *
 *  - [override] — the ACTIVE rendering override (shader pack + tone mapping):
 *    re-resolved per item from the stored item/series rows (item wins) and
 *    overwritten synchronously by sheet edits. `null` = the global settings
 *    stand.
 *  - [pending] — sheet picks of GLOBAL-only fields (render quality, `tscale`,
 *    custom shader files) ahead of the async DataStore round-trip, so the
 *    engine config reflects the pick immediately instead of one aggregate
 *    refresh later.
 *  - [deinterlace] — the gear-menu cycle (AUTO→ON→OFF→AUTO). Held
 *    ONLY for the session's lifetime: it survives a next-episode advance
 *    (the session lives on) and reverts on player exit ([onReleased]) —
 *    never a persisted preference.
 *
 * "Inherit" semantics fall out of the derived-config shape: clearing
 * [override] simply makes the global fields authoritative again — no
 * imperative snapshot is taken or restored anywhere.
 */
internal class SessionRenderState {

    /** Global-only sheet picks not yet reflected in the cached aggregate. */
    data class PendingGlobalEdits(
        val renderQuality: MpvRenderQuality? = null,
        val interpolationTscale: com.raulshma.jellyplay.core.model.MpvInterpolationTscale? = null,
        val customShaderFiles: List<String>? = null,
    )

    @Volatile var override: MpvRenderOverrides? = null
        private set

    @Volatile var pending: PendingGlobalEdits = PendingGlobalEdits()
        private set

    @Volatile var deinterlace: DeinterlaceMode = DeinterlaceMode.AUTO
        private set

    /**
     * The effective [MpvEngineConfig] slice: [global] with the session's
     * override folded through [RenderProfileResolver.foldInto] (the single
     * home of the two-field fold) and the pending global picks over it.
     */
    fun effectiveMpvConfig(global: MpvEngineConfig): MpvEngineConfig {
        var effective = RenderProfileResolver.foldInto(global, override)
        pending.renderQuality?.let { effective = effective.copy(renderQuality = it) }
        pending.interpolationTscale?.let { effective = effective.copy(interpolationTscale = it) }
        pending.customShaderFiles?.let { effective = effective.copy(customShaderFiles = it) }
        return effective
    }

    /**
     * Re-resolves the override for a NEW item: the stored item/series rows
     * win (item first). The deinterlace cycle deliberately PERSISTS across
     * this call — the shim's per-session semantics ("survives next-episode
     * advance").
     */
    fun onItemChanged(item: ItemPlaybackPreference?, series: ItemPlaybackPreference?) {
        override = RenderProfileResolver.overrides(item, series)
    }

    /** Sheet edit: applies the given override for the session immediately. */
    fun applyOverride(overrides: MpvRenderOverrides?) {
        override = overrides
    }

    /** Sheet pick of a global-only field: session-lens value until the store round-trips. */
    fun applyPending(transform: (PendingGlobalEdits) -> PendingGlobalEdits) {
        pending = transform(pending)
    }

    /**
     * The gear-menu cycle: AUTO→ON→OFF→AUTO. Returns the NEW mode so
     * the caller can surface it without a second read.
     */
    fun cycleDeinterlace(): DeinterlaceMode {
        deinterlace = when (deinterlace) {
            DeinterlaceMode.AUTO -> DeinterlaceMode.ON
            DeinterlaceMode.ON -> DeinterlaceMode.OFF
            DeinterlaceMode.OFF -> DeinterlaceMode.AUTO
        }
        return deinterlace
    }

    /** Player exit: everything session-scoped reverts (persisted rows don't). */
    fun onReleased() {
        override = null
        pending = PendingGlobalEdits()
        deinterlace = DeinterlaceMode.AUTO
    }
}
