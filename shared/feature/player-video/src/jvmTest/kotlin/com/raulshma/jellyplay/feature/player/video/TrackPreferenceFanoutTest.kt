package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.focus.FocusClaimState
import com.raulshma.jellyplay.core.data.playback.focus.PlaybackSurfaceId
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ItemPlaybackPreference
import com.raulshma.jellyplay.core.model.PlaybackPrefScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the resolved-preference fold the arm-phase collector delegates to
 * [TrackPreferenceFanout] — previously the inline body in the
 * `PlayerWiring` builder with ZERO direct pins (the composition suite can
 * only see that the collector exists, not what it decides). With plain
 * recording fakes:
 *
 *  - the boost default fold: a null / boost-less resolution lands the NONE
 *    default, and the mirror write carries strength + enabled in ONE copy;
 *  - the fold ORDER: mirror write → series-pref reflection → engine-config
 *    rebuild → the language re-apply;
 *  - the re-run condition (the load-bearing one): ONLY a language directive
 *    (audio language, subtitle language, or explicit subtitle-off) re-runs
 *    `updateTracksFromEngine` — the autoplay race's fix — while null and
 *    boost-only resolutions churn nothing;
 *  - the whole fanout driven end-to-end through a FAKE RESOLVER FLOW, the
 *    same StateFlow-collect registration arm() performs.
 *
 * [DisplacedHolderSelfPause]'s claim-state decision (our held VIDEO floor is
 * the ONLY non-pausing state) is pinned in the same suite — it moved out of
 * the builder in the same cut.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackPreferenceFanoutTest {

    /** Records every collaborator call in invocation order. */
    private class RecordingFanout {
        val log = mutableListOf<String>()

        val fanout = TrackPreferenceFanout(
            applyDialogueBoostResolution = { strength ->
                log += "boost:$strength"
            },
            onSeriesPreferenceResolved = { pref -> log += "series:${pref?.key}" },
            rebuildEngineConfig = { log += "rebuildConfig" },
            reapplyTracksFromEngine = { log += "reapplyTracks" },
        )
    }

    private fun pref(
        key: String = "item-1",
        audioLanguage: String? = null,
        subtitleLanguage: String? = null,
        subtitleDisabled: Boolean? = null,
        dialogueBoostStrength: EffectStrength? = null,
    ): ItemPlaybackPreference = ItemPlaybackPreference(
        scope = PlaybackPrefScope.ITEM,
        key = key,
        audioLanguage = audioLanguage,
        subtitleLanguage = subtitleLanguage,
        subtitleDisabled = subtitleDisabled,
        dialogueBoostStrength = dialogueBoostStrength,
    )

    // ── the boost default fold + the fold order ─────────────────────────────

    @Test
    fun nullResolutionFoldsTheNoneBoostDefault_andNeverReappliesTracks() {
        val fakes = RecordingFanout()
        fakes.fanout.onPreferenceResolved(null)
        assertEquals(
            listOf("boost:NONE", "series:null", "rebuildConfig"),
            fakes.log,
            "a null resolution mirrors the OFF default and re-runs nothing",
        )
    }

    @Test
    fun boostOnlyRulePinsTheStrength_withoutTheLanguageReapply() {
        val fakes = RecordingFanout()
        fakes.fanout.onPreferenceResolved(pref(dialogueBoostStrength = EffectStrength.MODERATE))
        assertEquals(
            listOf("boost:MODERATE", "series:item-1", "rebuildConfig"),
            fakes.log,
            "a boost-only rule must not churn the track re-apply",
        )
    }

    @Test
    fun theFoldRunsInRegistrationOrder_mirrorThenSeriesThenRebuildThenReapply() {
        val fakes = RecordingFanout()
        fakes.fanout.onPreferenceResolved(pref(audioLanguage = "ja"))
        assertEquals(
            listOf("boost:NONE", "series:item-1", "rebuildConfig", "reapplyTracks"),
            fakes.log,
            "mirror write → series reflection → config rebuild → language re-apply",
        )
    }

    // ── the re-run condition (the autoplay-race fix) ─────────────────────────

    @Test
    fun everyLanguageDirectiveAloneTriggersTheReapply() {
        val fakes = RecordingFanout()

        fakes.fanout.onPreferenceResolved(pref(audioLanguage = "ja"))
        assertTrue("reapplyTracks" in fakes.log, "audio language re-runs: ${fakes.log}")

        fakes.log.clear()
        fakes.fanout.onPreferenceResolved(pref(subtitleLanguage = "en"))
        assertTrue("reapplyTracks" in fakes.log, "subtitle language re-runs: ${fakes.log}")

        fakes.log.clear()
        fakes.fanout.onPreferenceResolved(pref(subtitleDisabled = true))
        assertTrue("reapplyTracks" in fakes.log, "subtitle-off re-runs: ${fakes.log}")
    }

    @Test
    fun explicitSubtitleFalseDoesNotTriggerTheReapply() {
        val fakes = RecordingFanout()
        // `subtitleDisabled == true` is the directive; an explicit FALSE is
        // "no preference" (the former inline ladder's exact comparison).
        fakes.fanout.onPreferenceResolved(pref(subtitleDisabled = false))
        assertFalse("reapplyTracks" in fakes.log, fakes.log.toString())
    }

    @Test
    fun hasLanguagePreference_isTheExactFormerLadder() {
        assertFalse(TrackPreferenceFanout.hasLanguagePreference(null))
        assertFalse(TrackPreferenceFanout.hasLanguagePreference(pref()))
        assertFalse(TrackPreferenceFanout.hasLanguagePreference(pref(dialogueBoostStrength = EffectStrength.HIGH)))
        assertTrue(TrackPreferenceFanout.hasLanguagePreference(pref(audioLanguage = "ja")))
        assertTrue(TrackPreferenceFanout.hasLanguagePreference(pref(subtitleLanguage = "en")))
        assertTrue(TrackPreferenceFanout.hasLanguagePreference(pref(subtitleDisabled = true)))
    }

    // ── the end-to-end resolver-flow registration (the arm() shape) ─────────

    @Test
    fun resolverFlowEmissionsFoldExactlyLikeDirectCalls() {
        val fakes = RecordingFanout()
        val resolved = MutableStateFlow<ItemPlaybackPreference?>(null)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
        try {
            // The exact registration arm() performs over the real resolver.
            scope.launch {
                resolved.collect { pref -> fakes.fanout.onPreferenceResolved(pref) }
            }
            fakes.log.clear()

            // The autoplay race: the engine's track list lands first, THEN the
            // DAO read resolves — the re-apply rides the late emission.
            resolved.value = pref(audioLanguage = "ja", dialogueBoostStrength = EffectStrength.LOW)
            assertEquals(
                listOf("boost:LOW", "series:item-1", "rebuildConfig", "reapplyTracks"),
                fakes.log,
            )

            // A null re-resolution (item teardown) folds the OFF default and
            // re-runs nothing.
            fakes.log.clear()
            resolved.value = null
            assertEquals(listOf("boost:NONE", "series:null", "rebuildConfig"), fakes.log)
        } finally {
            scope.cancel()
        }
    }

    // ── the displaced-holder self-pause decision ─────────────────────────────

    @Test
    fun shouldPauseFor_everyStateExceptOurHeldVideoFloor() {
        // Our held VIDEO floor: the ONLY state that skips the pause.
        assertFalse(
            DisplacedHolderSelfPause.shouldPauseFor(FocusClaimState.Held(PlaybackSurfaceId.VIDEO)),
            "our own held VIDEO floor must not self-pause",
        )
        // A MUSIC-held floor: the desktop's load-bearing row (a MUSIC eviction
        // of a held VIDEO claim arrives as no surface command there).
        assertTrue(DisplacedHolderSelfPause.shouldPauseFor(FocusClaimState.Held(PlaybackSurfaceId.MUSIC)))
        // Every unheld state falls through and pauses.
        assertTrue(DisplacedHolderSelfPause.shouldPauseFor(FocusClaimState.Idle))
    }

    @Test
    fun displacedHolderSelfPause_pausesOnlyWhenPlaying() {
        var playing = false
        var pauses = 0
        val selfPause = DisplacedHolderSelfPause(
            isEnginePlaying = { playing },
            pauseEngine = { pauses++ },
        )

        selfPause.onClaimStateChanged(FocusClaimState.Held(PlaybackSurfaceId.VIDEO))
        assertEquals(0, pauses, "our held VIDEO floor never pauses")
        selfPause.onClaimStateChanged(FocusClaimState.Idle)
        assertEquals(0, pauses, "an idle engine is not paused")
        playing = true
        selfPause.onClaimStateChanged(FocusClaimState.Held(PlaybackSurfaceId.MUSIC))
        assertEquals(1, pauses, "a displaced holder pauses the still-playing engine")
        selfPause.onClaimStateChanged(FocusClaimState.Held(PlaybackSurfaceId.VIDEO))
        assertEquals(1, pauses, "a re-held VIDEO floor does not pause again")
    }
}
