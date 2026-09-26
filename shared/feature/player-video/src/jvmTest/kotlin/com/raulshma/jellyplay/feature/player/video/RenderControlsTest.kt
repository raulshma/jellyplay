package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.DeinterlaceMode
import com.raulshma.jellyplay.core.model.ItemPlaybackPreference
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.MpvInterpolationTscale
import com.raulshma.jellyplay.core.model.MpvRenderOverrides
import com.raulshma.jellyplay.core.model.MpvRenderQuality
import com.raulshma.jellyplay.core.model.MpvShaderPack
import com.raulshma.jellyplay.core.model.MpvToneMapping
import com.raulshma.jellyplay.core.model.PlaybackPrefScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [RenderControls] — the "Rendering" sheet + deinterlace write
 * choreography extracted from the ViewModel (the SubtitleStyleController
 * shape: pure state composed with the writes, no ViewModel, no uiState).
 * Pins the load-bearing invariants: a session override pick beats the
 * persisted global immediately and only persists when the sheet's save
 * toggle is on (the FULL resolved row is written); with no session override
 * the persisted global stands; a global-only pick mirrors into the pending
 * lens before the store round-trip and the persisted slice is computed from
 * the pre-write mirror; the pending mirror is invalidated on session
 * release; the deinterlace cycle is never persisted; every write reports a
 * dirty config exactly once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RenderControlsTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    // Mirror fakes (the VM-side state the lambdas read/write).
    private var globalConfig = MpvEngineConfig()
    private var storedRows: Map<PlaybackPrefScope, ItemPlaybackPreference> = emptyMap()

    // Recording sinks.
    private val savedGlobalConfigs = mutableListOf<MpvEngineConfig>()
    private val savedProfiles = mutableListOf<MpvRenderOverrides>()
    private var clearedProfiles = 0
    private var configDirties = 0

    private lateinit var controller: RenderControls

    @BeforeTest
    fun setUp() {
        controller = RenderControls(
            scope = testScope,
            getGlobalMpvConfig = { globalConfig },
            saveGlobalMpvConfig = { savedGlobalConfigs.add(it) },
            loadStoredRow = { prefScope, _ -> storedRows[prefScope] },
            saveRenderProfile = { savedProfiles.add(it) },
            clearStoredRenderProfile = { clearedProfiles++ },
            onConfigDirty = { configDirties++ },
        )
    }

    private fun pref(scope: PlaybackPrefScope, overrides: MpvRenderOverrides?) = ItemPlaybackPreference(
        scope = scope,
        key = if (scope == PlaybackPrefScope.ITEM) "item-1" else "series-1",
        renderProfile = overrides,
    )

    // ── session override vs persist ────────────────────────────────────────

    @Test
    fun sessionOverride_beatsThePersistedGlobalImmediately_withoutPersistingUnlessAsked() = testScope.runTest {
        globalConfig = MpvEngineConfig(
            shaderPack = MpvShaderPack.ANIME4K_B,
            toneMapping = MpvToneMapping.HABLE,
            renderQuality = MpvRenderQuality.HIGH,
        )

        controller.setRenderShaderPack(MpvShaderPack.ANIME4K_A, persist = false)

        // The session lens folds the pick over the persisted global. The
        // fresh override starts from DEFAULTS (the override is a self-
        // contained two-field row), so BOTH override fields replace the
        // global slice; global-only fields pass through untouched.
        val effective = controller.state.effectiveMpvConfig(globalConfig)
        assertEquals(MpvShaderPack.ANIME4K_A, effective.shaderPack)
        assertEquals(MpvToneMapping.AUTO, effective.toneMapping, "a fresh override starts from defaults, not the global slice")
        assertEquals(MpvRenderQuality.HIGH, effective.renderQuality, "global-only fields pass through")
        // …and the save toggle off means nothing is persisted.
        assertTrue(savedProfiles.isEmpty(), "a session-only pick must not write the preference rows")
        assertEquals(0, clearedProfiles)
        assertEquals(1, configDirties)
    }

    @Test
    fun sessionOverride_persistedWhenAsked_writesTheFullResolvedRow() = testScope.runTest {
        globalConfig = MpvEngineConfig(toneMapping = MpvToneMapping.HABLE)

        controller.setRenderToneMapping(MpvToneMapping.BT2390, persist = true)

        // The stored row is the FULL resolved override (defaults + the pick),
        // never a single-field patch — the next item re-resolves from it.
        assertEquals(
            listOf(MpvRenderOverrides(shaderPack = MpvShaderPack.OFF, toneMapping = MpvToneMapping.BT2390)),
            savedProfiles,
        )
        assertEquals(1, configDirties)
    }

    @Test
    fun sessionOverride_accumulatesFieldPicksOntoTheCurrentLens() = testScope.runTest {
        controller.setRenderShaderPack(MpvShaderPack.ANIME4K_C, persist = false)
        controller.setRenderToneMapping(MpvToneMapping.GAMMA, persist = true)

        // The LENS is the accumulator: the second pick builds on the first…
        assertEquals(
            MpvRenderOverrides(shaderPack = MpvShaderPack.ANIME4K_C, toneMapping = MpvToneMapping.GAMMA),
            controller.state.override,
        )
        // …and only the persist=true pick wrote a row (the full resolved one).
        assertEquals(
            listOf(MpvRenderOverrides(shaderPack = MpvShaderPack.ANIME4K_C, toneMapping = MpvToneMapping.GAMMA)),
            savedProfiles,
        )
    }

    @Test
    fun noSessionOverride_thePersistedGlobalStands() = testScope.runTest {
        globalConfig = MpvEngineConfig(shaderPack = MpvShaderPack.ANIME4K_B, toneMapping = MpvToneMapping.REINHARD)

        assertNull(controller.state.override)
        assertEquals(globalConfig, controller.state.effectiveMpvConfig(globalConfig))
        assertEquals(0, configDirties)
    }

    // ── clearRenderOverride ("Inherit") ────────────────────────────────────

    @Test
    fun clearRenderOverride_dropsTheSessionLensAndClearsThePersistedRows() = testScope.runTest {
        globalConfig = MpvEngineConfig(shaderPack = MpvShaderPack.ANIME4K_B)
        controller.setRenderShaderPack(MpvShaderPack.ANIME4K_A, persist = true)

        controller.clearRenderOverride()

        assertNull(controller.state.override)
        // Derived "inherit": the global settings stand again — no snapshot.
        assertEquals(MpvShaderPack.ANIME4K_B, controller.state.effectiveMpvConfig(globalConfig).shaderPack)
        assertEquals(1, clearedProfiles, "the persisted override clears in BOTH scopes")
        assertEquals(2, configDirties, "one dirty per write (the pick + the clear)")
    }

    // ── global-only picks: pending mirror + global persist ─────────────────

    @Test
    fun globalPick_mirrorsThroughPendingThenPersistsTheTransformedGlobalSlice() = testScope.runTest {
        globalConfig = MpvEngineConfig(renderQuality = MpvRenderQuality.BALANCED)

        controller.setRenderQuality(MpvRenderQuality.HIGH)

        // The session lens reflects the pick immediately (ahead of the
        // DataStore round-trip)…
        assertEquals(MpvRenderQuality.HIGH, controller.state.effectiveMpvConfig(globalConfig).renderQuality)
        // …and the persisted slice is the pick over the PRE-write mirror —
        // the async write never double-lands into itself.
        assertEquals(listOf(globalConfig.copy(renderQuality = MpvRenderQuality.HIGH)), savedGlobalConfigs)
        assertEquals(1, configDirties)
    }

    @Test
    fun globalPicks_eachPersistsItsOwnFieldOverTheCurrentGlobalSlice() = testScope.runTest {
        globalConfig = MpvEngineConfig(interpolation = true)

        controller.setInterpolationTscale(MpvInterpolationTscale.OVERSAMPLE)
        controller.setCustomShaderFiles(listOf("/u/a.glsl", "/u/b.glsl"))

        assertEquals(
            MpvEngineConfig(interpolation = true, interpolationTscale = MpvInterpolationTscale.OVERSAMPLE),
            savedGlobalConfigs[0],
        )
        assertEquals(
            MpvEngineConfig(interpolation = true, customShaderFiles = listOf("/u/a.glsl", "/u/b.glsl")),
            savedGlobalConfigs[1],
        )
        assertEquals(2, configDirties)
    }

    @Test
    fun globalPick_pendingMirror_isInvalidatedOnSessionRelease() = testScope.runTest {
        globalConfig = MpvEngineConfig(renderQuality = MpvRenderQuality.BALANCED)
        controller.setRenderQuality(MpvRenderQuality.HIGH)
        controller.setInterpolationTscale(MpvInterpolationTscale.LINEAR)
        controller.setCustomShaderFiles(listOf("/u/x.glsl"))
        controller.setRenderShaderPack(MpvShaderPack.ANIME4K_A, persist = false)
        controller.cycleDeinterlace()
        // The picks above legitimately persisted the global picks; the
        // release itself must not write anything new.
        val savesBeforeRelease = savedGlobalConfigs.size
        val profileSavesBeforeRelease = savedProfiles.size

        controller.onReleased()

        // Everything session-scoped reverts — the pending global mirror
        // included — so a fresh session reads the persisted values again.
        assertEquals(globalConfig, controller.state.effectiveMpvConfig(globalConfig))
        assertNull(controller.state.override)
        assertEquals(DeinterlaceMode.AUTO, controller.state.deinterlace)
        // The release never touches the persisted rows.
        assertEquals(savesBeforeRelease, savedGlobalConfigs.size)
        assertEquals(profileSavesBeforeRelease, savedProfiles.size)
        assertEquals(0, clearedProfiles)
    }

    // ── deinterlace: session-scoped, never persisted ───────────────────────

    @Test
    fun deinterlace_cyclesAndIsNeverPersisted() = testScope.runTest {
        controller.cycleDeinterlace()
        controller.cycleDeinterlace()
        controller.cycleDeinterlace()

        assertEquals(DeinterlaceMode.AUTO, controller.state.deinterlace, "AUTO→ON→OFF→AUTO")
        assertTrue(savedGlobalConfigs.isEmpty(), "the cycle must never write the global slice")
        assertTrue(savedProfiles.isEmpty(), "the cycle must never write the override rows")
        assertEquals(0, clearedProfiles)
        assertEquals(3, configDirties, "each cycle rebuilds the config (the engines apply the delta)")
    }

    // ── session item change: stored-override re-resolution ─────────────────

    @Test
    fun onSessionItemChanged_storedItemRowBeatsTheSeriesRow() = testScope.runTest {
        val packA = MpvRenderOverrides(shaderPack = MpvShaderPack.ANIME4K_A, toneMapping = MpvToneMapping.AUTO)
        val packB = MpvRenderOverrides(shaderPack = MpvShaderPack.ANIME4K_B, toneMapping = MpvToneMapping.BT2390)
        storedRows = mapOf(
            PlaybackPrefScope.ITEM to pref(PlaybackPrefScope.ITEM, packA),
            PlaybackPrefScope.SERIES to pref(PlaybackPrefScope.SERIES, packB),
        )

        controller.onSessionItemChanged(itemId = "item-1", seriesId = "series-1")

        assertEquals(packA, controller.state.override, "item row wins over series row")
        assertEquals(1, configDirties)
    }

    @Test
    fun onSessionItemChanged_withoutStoredRows_thePreviousLensDropsToGlobal() = testScope.runTest {
        controller.setRenderShaderPack(MpvShaderPack.ANIME4K_A, persist = false)
        configDirties = 0

        controller.onSessionItemChanged(itemId = null, seriesId = null)

        assertNull(controller.state.override, "no stored rows: the previous item's lens must not bleed")
        assertEquals(globalConfig, controller.state.effectiveMpvConfig(globalConfig))
        assertEquals(1, configDirties)
    }
}
