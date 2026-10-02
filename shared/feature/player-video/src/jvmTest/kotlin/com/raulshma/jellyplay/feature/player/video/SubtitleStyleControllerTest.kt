package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import com.raulshma.jellyplay.feature.player.video.subtitle.InstalledFont
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [SubtitleStyleController] — the subtitle-style + dialogue-boost +
 * subtitle-delay choreography extracted from the ViewModel (A7, step 1: the
 * state stays in the VM's mirrors; the controller writes through the narrow
 * constructor lambdas faked here). Pins the three load-bearing invariants:
 * the in-memory offsetMs is the per-item resolved delay and never persists
 * to the global store; a global style write preserves that offset; and the
 * delay apply debounce coalesces a burst of nudges into one engine sync.
 * The user-font install + the direct engine re-apply (folded back from the
 * deleted SubtitleFontController — the old controller was untested, so these
 * pin the folded result) route through the SAME setStyle funnel, which the
 * font tests assert.
 * No ViewModel, no uiState.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleStyleControllerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    // Mirror fakes (the VM-side state the lambdas read/write).
    private var styleMirror = SubtitleStyle()
    private var boostStrengthMirror: EffectStrength = EffectStrength.NONE
    private var boostEnabledMirror = false
    private var currentItemId: String? = "item-1"
    private var globalOffsetMs = 200L

    // Recording sinks.
    private val savedGlobalStyles = mutableListOf<SubtitleStyle>()
    private val savedItemDelays = mutableListOf<Pair<String, Long>>()
    private val savedBoosts = mutableListOf<EffectStrength>()
    private var immediateSyncs = 0
    private var debouncedSyncs = 0

    // The font-install seam's recording fake + the engine re-apply sink.
    private val fontInstallRequests = mutableListOf<String>()
    private var installedFont: InstalledFont? = null
    private val appliedStyles = mutableListOf<SubtitleStyle>()
    private var liveEngine: MediaEngine? = mockk<MediaEngine>(relaxed = true).also {
        every { it.applySubtitleStyle(any()) } answers { appliedStyles += firstArg<SubtitleStyle>() }
    }

    private lateinit var controller: SubtitleStyleController

    @BeforeTest
    fun setUp() {
        controller = SubtitleStyleController(
            scope = testScope,
            getStyle = { styleMirror },
            setStyleMirror = { styleMirror = it },
            setDialogueBoostMirror = { strength, enabled ->
                boostStrengthMirror = strength
                boostEnabledMirror = enabled
            },
            isDialogueBoostEnabled = { boostEnabledMirror },
            getCurrentItemId = { currentItemId },
            getGlobalOffsetMs = { globalOffsetMs },
            saveGlobalStyle = { savedGlobalStyles.add(it) },
            saveItemDelay = { itemId, delayMs -> savedItemDelays.add(itemId to delayMs) },
            saveDialogueBoost = { savedBoosts.add(it) },
            syncEngineConfig = { immediateSyncs++ },
            syncEngineConfigDebounced = { debouncedSyncs++ },
            fontProvider = object : FontProvider {
                override suspend fun installUserFont(uri: String): InstalledFont? {
                    fontInstallRequests += uri
                    return installedFont
                }

                override suspend fun prewarm() = Unit
            },
            getEngine = { liveEngine },
        )
    }

    // ── setStyle: global persist restores the stored global offset ─────────

    @Test
    fun setStyle_persistsGlobalWithTheStoredOffset_neverTheInMemoryOne() = testScope.runTest {
        styleMirror = SubtitleStyle(offsetMs = 1234L, fontSize = 18)

        controller.setStyle(styleMirror)

        // Mirror + immediate engine sync happen synchronously.
        assertEquals(1234L, styleMirror.offsetMs)
        assertEquals(1, immediateSyncs)
        // The invariant: the in-memory 1234 ms (the per-item resolved delay)
        // must NOT leak into the global store — the persisted global default
        // (200 ms) is restored before the write.
        assertEquals(listOf(styleMirror.copy(offsetMs = 200L)), savedGlobalStyles)
        assertTrue(savedItemDelays.isEmpty(), "a style edit is never a per-item delay write")
    }

    // ── setDelay: per-item persist + debounced engine apply ────────────────

    @Test
    fun setDelay_persistsOnlyPerItemAndDebouncesTheEngineApply() = testScope.runTest {
        controller.setDelay(400L)

        // Readout + per-item store reflect the change immediately…
        assertEquals(400L, styleMirror.offsetMs)
        assertEquals(listOf("item-1" to 400L), savedItemDelays)
        assertTrue(savedGlobalStyles.isEmpty(), "a delay correction must never touch the global style")
        // …but the engine apply has NOT run yet (inside the debounce window).
        assertEquals(0, immediateSyncs)

        testScope.advanceTimeBy(SUBTITLE_DELAY_APPLY_DEBOUNCE_MS)
        runCurrent()
        assertEquals(1, immediateSyncs, "exactly one engine apply after the debounce window")
    }

    @Test
    fun setDelay_burstOfNudgesCoalescesIntoOneApply() = testScope.runTest {
        controller.setDelay(100L)
        controller.setDelay(200L)
        controller.setDelay(300L)

        // Every nudged value was mirrored (the overlay readout is live)…
        assertEquals(300L, styleMirror.offsetMs)
        // …and each was persisted per-item (the store write is not debounced —
        // same order as the pre-extraction VM body).
        assertEquals(listOf("item-1" to 100L, "item-1" to 200L, "item-1" to 300L), savedItemDelays)

        testScope.advanceTimeBy(SUBTITLE_DELAY_APPLY_DEBOUNCE_MS)
        runCurrent()
        assertEquals(1, immediateSyncs, "the burst coalesces into ONE engine reload, not three")
    }

    @Test
    fun setDelay_sameValueIsANoOp() = testScope.runTest {
        styleMirror = SubtitleStyle(offsetMs = 500L)

        controller.setDelay(500L)

        assertTrue(savedItemDelays.isEmpty())
        testScope.advanceTimeBy(SUBTITLE_DELAY_APPLY_DEBOUNCE_MS)
        runCurrent()
        assertEquals(0, immediateSyncs)
    }

    @Test
    fun setDelay_withoutCurrentItem_skipsThePerItemPersistButStillApplies() = testScope.runTest {
        currentItemId = null

        controller.setDelay(-250L)

        assertEquals(-250L, styleMirror.offsetMs)
        assertTrue(savedItemDelays.isEmpty())
        testScope.advanceTimeBy(SUBTITLE_DELAY_APPLY_DEBOUNCE_MS)
        runCurrent()
        assertEquals(1, immediateSyncs)
    }

    // ── dialogue boost ─────────────────────────────────────────────────────

    @Test
    fun toggleDialogueBoost_pinsModerateThenClears() = testScope.runTest {
        controller.toggleDialogueBoost()
        assertEquals(EffectStrength.MODERATE, boostStrengthMirror)
        assertTrue(boostEnabledMirror)
        assertEquals(listOf(EffectStrength.MODERATE), savedBoosts)

        controller.toggleDialogueBoost()
        assertEquals(EffectStrength.NONE, boostStrengthMirror)
        assertEquals(false, boostEnabledMirror)
        assertEquals(listOf(EffectStrength.MODERATE, EffectStrength.NONE), savedBoosts)
    }

    @Test
    fun setDialogueBoost_syncsEngineImmediatelyAndPersistsThroughWriter() = testScope.runTest {
        controller.setDialogueBoost(EffectStrength.LOW)

        assertEquals(EffectStrength.LOW, boostStrengthMirror)
        assertTrue(boostEnabledMirror)
        assertEquals(1, immediateSyncs)
        assertEquals(listOf(EffectStrength.LOW), savedBoosts)
    }

    // ── onItemHydrated: per-item delay resolution ──────────────────────────

    @Test
    fun onItemHydrated_storedPerItemCorrectionWins() = testScope.runTest {
        styleMirror = SubtitleStyle(offsetMs = 999L)
        val slice = SubtitleSlice(
            subtitleDelayByItem = mapOf("item-1" to -350L),
            subtitleStyle = SubtitleStyle(offsetMs = 42L),
        )

        controller.onItemHydrated(slice, "item-1")

        assertEquals(-350L, styleMirror.offsetMs, "stored per-item correction beats the global default")
        assertEquals(1, debouncedSyncs)
    }

    @Test
    fun onItemHydrated_withoutStoredCorrectionAppliesTheGlobalDefault() = testScope.runTest {
        styleMirror = SubtitleStyle(offsetMs = 999L)
        val slice = SubtitleSlice(subtitleStyle = SubtitleStyle(offsetMs = 42L))

        controller.onItemHydrated(slice, "item-1")

        assertEquals(42L, styleMirror.offsetMs, "no per-item entry: the global default applies — never the previous item's value")
    }

    @Test
    fun onItemHydrated_equalDelayIsANoOp() = testScope.runTest {
        styleMirror = SubtitleStyle(offsetMs = 42L)
        val slice = SubtitleSlice(subtitleStyle = SubtitleStyle(offsetMs = 42L))

        controller.onItemHydrated(slice, "item-1")

        assertEquals(0, debouncedSyncs)
    }

    // ── onEngineBound: seed with per-item delay + boost reset ──────────────

    @Test
    fun onEngineBound_seedsStyleWithPerItemDelayAndResetsBoost() = testScope.runTest {
        styleMirror = SubtitleStyle(offsetMs = 999L)
        boostStrengthMirror = EffectStrength.MODERATE
        boostEnabledMirror = true
        val slice = SubtitleSlice(
            subtitleDelayByItem = mapOf("item-1" to -350L),
            subtitleStyle = SubtitleStyle(offsetMs = 42L, fontSize = 30),
        )

        controller.onEngineBound(slice, "item-1", isHdr = false)

        // Style re-resolved from the slice WITH the per-item delay — an engine
        // swap never resets a per-item correction to the global default.
        assertEquals(30, styleMirror.fontSize)
        assertEquals(-350L, styleMirror.offsetMs)
        // Dialogue boost resets to OFF until the per-item resolver re-applies.
        assertEquals(EffectStrength.NONE, boostStrengthMirror)
        assertEquals(false, boostEnabledMirror)
        assertTrue(savedBoosts.isEmpty(), "the seed reset must not clear the persisted rule")
    }

    // ── installUserFont: the SubtitleFontController fold ───────────────────

    @Test
    fun installUserFont_appliesTheInstalledFontAsAStyleEdit() = testScope.runTest {
        styleMirror = SubtitleStyle(fontSize = 24, offsetMs = 300L)
        val fontFile = File("/fonts/picked.ttf")
        installedFont = InstalledFont(file = fontFile, familyName = "Picked")

        controller.installUserFont("content://picked")

        // The provider saw the (stringified) pick…
        assertEquals(listOf("content://picked"), fontInstallRequests)
        // …and the resulting family arrived as a FULL style edit: the mirror
        // carries the font fields over the previous style…
        assertEquals(fontFile.absolutePath, styleMirror.fontFamilyPath)
        assertEquals("Picked", styleMirror.fontFamilyName)
        assertEquals(24, styleMirror.fontSize, "the rest of the style rides along (a copy, not a reset)")
        // …which means the SAME funnel as a sheet edit: immediate engine sync
        // + the global persist with the stored offset restored.
        assertEquals(1, immediateSyncs)
        assertEquals(
            listOf(styleMirror.copy(offsetMs = 200L)),
            savedGlobalStyles,
            "a font install is a global style write — the per-item in-memory offset never leaks",
        )
    }

    @Test
    fun installUserFont_copyOrParseFailure_leavesTheStyleUntouched() = testScope.runTest {
        styleMirror = SubtitleStyle(fontSize = 24)
        installedFont = null

        controller.installUserFont("content://broken")

        assertEquals(listOf("content://broken"), fontInstallRequests)
        assertEquals(SubtitleStyle(fontSize = 24), styleMirror, "the bundled fallback font stays")
        assertEquals(0, immediateSyncs)
        assertTrue(savedGlobalStyles.isEmpty())
    }

    // ── applySubtitleStyle: the direct engine re-apply ─────────────────────

    @Test
    fun applySubtitleStyle_reappliesTheCurrentMirrorToTheEngine() = testScope.runTest {
        styleMirror = SubtitleStyle(fontSize = 22, fontFamilyName = "Picked")

        controller.applySubtitleStyle()

        assertEquals(
            listOf(SubtitleStyle(fontSize = 22, fontFamilyName = "Picked")),
            appliedStyles,
            "the engine receives the CURRENT mirror, not a stored copy",
        )
    }

    @Test
    fun applySubtitleStyle_withoutABoundEngine_isANoOp() = testScope.runTest {
        liveEngine = null

        controller.applySubtitleStyle()

        assertTrue(appliedStyles.isEmpty())
    }
}
