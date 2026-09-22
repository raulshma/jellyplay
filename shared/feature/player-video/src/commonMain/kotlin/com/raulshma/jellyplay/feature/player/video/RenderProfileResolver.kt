package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.ItemPlaybackPreference
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.MpvRenderOverrides

/**
 * The pure item → series → global render-profile precedence: folds the
 * per-item/series rendering override (`ItemPlaybackPreference.renderProfile`,
 * persisted by the "Rendering" sheet through `setRenderProfile`) over the
 * global [MpvEngineConfig] slice. The effective config is ALWAYS derived —
 * "Inherit" clears the stored override and the global values stand again; no
 * imperative snapshot exists anywhere.
 *
 * Precedence: a non-null ITEM override wins; else the SERIES override; else
 * the global fields pass through untouched. Only the two overridable fields
 * (shader pack, tone mapping) fold — render quality / tscale / HDR
 * passthrough are global-only surfaces.
 */
internal object RenderProfileResolver {

    /** The active override for the session: item row wins over series row. */
    fun overrides(
        item: ItemPlaybackPreference?,
        series: ItemPlaybackPreference?,
    ): MpvRenderOverrides? = item?.renderProfile ?: series?.renderProfile

    /**
     * The effective [MpvEngineConfig]: [global] with the resolved override's
     * shader pack and tone mapping folded in (a null override is the global
     * config unchanged). Delegates to the override-level [foldInto] — the
     * single home of the two-field fold.
     */
    fun foldInto(
        global: MpvEngineConfig,
        item: ItemPlaybackPreference?,
        series: ItemPlaybackPreference?,
    ): MpvEngineConfig = foldInto(global, overrides(item, series))

    /**
     * The override-level fold: [global] with [override]'s shader pack and
     * tone mapping applied (null = unchanged). The session render state
     * resolves its override once per item change and re-folds through here —
     * the fold cannot drift between the two call shapes.
     */
    fun foldInto(global: MpvEngineConfig, override: MpvRenderOverrides?): MpvEngineConfig {
        if (override == null) return global
        return global.copy(
            shaderPack = override.shaderPack,
            toneMapping = override.toneMapping,
        )
    }
}
