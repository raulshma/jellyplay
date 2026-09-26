package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.ItemPlaybackPreference
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.MpvRenderOverrides
import com.raulshma.jellyplay.core.model.MpvShaderPack
import com.raulshma.jellyplay.core.model.MpvToneMapping
import com.raulshma.jellyplay.core.model.PlaybackPrefScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins for the session render semantics:
 *  - [RenderProfileResolver]: the item > series > global precedence and the
 *    derived-config fold ("Inherit" clears — no imperative snapshot).
 *  - [SessionRenderState]: the session lifecycle — a sheet edit applies
 *    immediately, a next-episode item change re-resolves the stored override
 *    while the deinterlace cycle SURVIVES, and release reverts everything.
 */
class RenderProfileResolverTest {

    private fun pref(
        scope: PlaybackPrefScope,
        overrides: MpvRenderOverrides?,
    ) = ItemPlaybackPreference(
        scope = scope,
        key = if (scope == PlaybackPrefScope.ITEM) "item-1" else "series-1",
        renderProfile = overrides,
    )

    private val packA = MpvRenderOverrides(shaderPack = MpvShaderPack.ANIME4K_A, toneMapping = MpvToneMapping.AUTO)
    private val packB = MpvRenderOverrides(shaderPack = MpvShaderPack.ANIME4K_B, toneMapping = MpvToneMapping.BT2390)

    // ── precedence ──────────────────────────────────────────────────────────

    @Test
    fun itemOverride_winsOverSeriesOverGlobal() {
        val item = pref(PlaybackPrefScope.ITEM, packA)
        val series = pref(PlaybackPrefScope.SERIES, packB)
        assertEquals(packA, RenderProfileResolver.overrides(item, series))

        // No item row → series stands.
        assertEquals(packB, RenderProfileResolver.overrides(null, series))

        // Item row without a profile → the series profile resolves (the
        // resolution is on the renderProfile FIELD, not row existence).
        val itemWithout = pref(PlaybackPrefScope.ITEM, null)
        assertEquals(packB, RenderProfileResolver.overrides(itemWithout, series))

        // Neither → null (global stands).
        assertNull(RenderProfileResolver.overrides(null, null))
        assertNull(RenderProfileResolver.overrides(itemWithout, pref(PlaybackPrefScope.SERIES, null)))
    }

    @Test
    fun foldInto_overrideReplacesOnlyPackAndToneMapping() {
        val global = MpvEngineConfig(
            shaderPack = MpvShaderPack.OFF,
            toneMapping = MpvToneMapping.HABLE,
            renderQuality = com.raulshma.jellyplay.core.model.MpvRenderQuality.HIGH,
        )
        val folded = RenderProfileResolver.foldInto(
            global = global,
            item = pref(PlaybackPrefScope.ITEM, packB),
            series = null,
        )
        assertEquals(MpvShaderPack.ANIME4K_B, folded.shaderPack)
        assertEquals(MpvToneMapping.BT2390, folded.toneMapping)
        // Global-only surfaces pass through untouched.
        assertEquals(com.raulshma.jellyplay.core.model.MpvRenderQuality.HIGH, folded.renderQuality)
    }

    @Test
    fun foldInto_nullOverride_isTheGlobalConfig() {
        val global = MpvEngineConfig(toneMapping = MpvToneMapping.REINHARD)
        assertEquals(
            global,
            RenderProfileResolver.foldInto(global, item = null, series = null),
        )
    }

    // ── "Inherit (off)" clears: an explicit OFF pack override is real, null ─

    @Test
    fun inherit_clearsToGlobal_anExplicitOffPackIsStillMeaningful() {
        val global = MpvEngineConfig(shaderPack = MpvShaderPack.ANIME4K_B)
        // Stored "OFF" override → the pack is explicitly OFF, NOT "follow global".
        val offOverride = MpvRenderOverrides(shaderPack = MpvShaderPack.OFF, toneMapping = MpvToneMapping.AUTO)
        assertEquals(
            MpvShaderPack.OFF,
            RenderProfileResolver.foldInto(global, item = pref(PlaybackPrefScope.ITEM, offOverride), series = null).shaderPack,
        )
        // null override (inherit) → the global pack stands again.
        assertEquals(
            MpvShaderPack.ANIME4K_B,
            RenderProfileResolver.foldInto(global, item = null, series = null).shaderPack,
        )
    }
}

class SessionRenderStateTest {

    private val global = MpvEngineConfig(shaderPack = MpvShaderPack.OFF, toneMapping = MpvToneMapping.AUTO)
    private val seriesOverride = MpvRenderOverrides(shaderPack = MpvShaderPack.ANIME4K_C, toneMapping = MpvToneMapping.MOBIUS)

    private fun seriesPref() = ItemPlaybackPreference(
        scope = PlaybackPrefScope.SERIES,
        key = "series-1",
        renderProfile = seriesOverride,
    )

    @Test
    fun noOverrideAndNoCycle_theGlobalConfigPassesThrough() {
        val state = SessionRenderState()
        assertEquals(global, state.effectiveMpvConfig(global))
        assertEquals(com.raulshma.jellyplay.core.model.DeinterlaceMode.AUTO, state.deinterlace)
    }

    @Test
    fun sheetEdit_appliesImmediately() {
        val state = SessionRenderState()
        state.applyOverride(MpvRenderOverrides(shaderPack = MpvShaderPack.ANIME4K_A, toneMapping = MpvToneMapping.GAMMA))
        val effective = state.effectiveMpvConfig(global)
        assertEquals(MpvShaderPack.ANIME4K_A, effective.shaderPack)
        assertEquals(MpvToneMapping.GAMMA, effective.toneMapping)
    }

    @Test
    fun pendingGlobalEdits_foldAheadOfTheStoreRoundTrip() {
        val state = SessionRenderState()
        state.applyPending {
            it.copy(
                renderQuality = com.raulshma.jellyplay.core.model.MpvRenderQuality.HIGH,
                interpolationTscale = com.raulshma.jellyplay.core.model.MpvInterpolationTscale.LINEAR,
                customShaderFiles = listOf("/u/x.glsl"),
            )
        }
        val effective = state.effectiveMpvConfig(global)
        assertEquals(com.raulshma.jellyplay.core.model.MpvRenderQuality.HIGH, effective.renderQuality)
        assertEquals(com.raulshma.jellyplay.core.model.MpvInterpolationTscale.LINEAR, effective.interpolationTscale)
        assertEquals(listOf("/u/x.glsl"), effective.customShaderFiles)
    }

    @Test
    fun itemChange_reResolvesTheOverride_butTheDeinterlaceCycleSurvives() {
        val state = SessionRenderState()
        // Next-episode advance: the session (and the cycle) lives on...
        assertEquals(com.raulshma.jellyplay.core.model.DeinterlaceMode.ON, state.cycleDeinterlace())
        state.onItemChanged(item = null, series = seriesPref())
        assertEquals(seriesOverride, state.effectiveMpvConfig(global).let { MpvRenderOverrides(it.shaderPack, it.toneMapping) })
        // ...the cycle survived the item switch.
        assertEquals(com.raulshma.jellyplay.core.model.DeinterlaceMode.ON, state.deinterlace)
    }

    @Test
    fun itemChangeWithoutStoredOverride_fallsBackToGlobal() {
        val state = SessionRenderState()
        state.applyOverride(MpvRenderOverrides(shaderPack = MpvShaderPack.ANIME4K_A, toneMapping = MpvToneMapping.AUTO))
        state.onItemChanged(item = null, series = null)
        assertNull(state.override)
        assertEquals(MpvShaderPack.OFF, state.effectiveMpvConfig(global).shaderPack)
    }

    @Test
    fun deinterlace_cyclesAutoOnOffAuto() {
        val state = SessionRenderState()
        assertEquals(com.raulshma.jellyplay.core.model.DeinterlaceMode.ON, state.cycleDeinterlace())
        assertEquals(com.raulshma.jellyplay.core.model.DeinterlaceMode.OFF, state.cycleDeinterlace())
        assertEquals(com.raulshma.jellyplay.core.model.DeinterlaceMode.AUTO, state.cycleDeinterlace())
    }

    @Test
    fun release_revertsEverythingSessionScoped() {
        val state = SessionRenderState()
        state.applyOverride(seriesOverride)
        state.applyPending { it.copy(renderQuality = com.raulshma.jellyplay.core.model.MpvRenderQuality.PERFORMANCE) }
        state.cycleDeinterlace()
        state.onReleased()
        assertEquals(global, state.effectiveMpvConfig(global))
        assertNull(state.override)
        assertEquals(com.raulshma.jellyplay.core.model.DeinterlaceMode.AUTO, state.deinterlace)
        // The cycle still works after a fresh session starts.
        assertEquals(com.raulshma.jellyplay.core.model.DeinterlaceMode.ON, state.cycleDeinterlace())
    }

    @Test
    fun mpvRenderOverrides_neutrality_onlyDefaultsAreNeutral() {
        assertTrue(MpvRenderOverrides().isNeutral)
        assertFalse(MpvRenderOverrides(shaderPack = MpvShaderPack.ANIME4K_B).isNeutral)
        assertFalse(MpvRenderOverrides(toneMapping = MpvToneMapping.CLIP).isNeutral)
    }
}
