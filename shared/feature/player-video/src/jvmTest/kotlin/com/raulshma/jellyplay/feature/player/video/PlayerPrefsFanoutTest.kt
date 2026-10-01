package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the pref-diff choreography the aggregate collector delegates to
 * [PlayerPrefsFanout] (the SettingsProjector's side-effecting half): which
 * seed fires on which diff, that the engine-config rebuild trigger stays
 * guarded, that the autoplay flip respects the applied mirror, and that the
 * video focus-policy push tracks the two focus prefs — with plain recording
 * fakes, no ViewModel involved.
 */
class PlayerPrefsFanoutTest {

    /** Records every collaborator call in invocation order. */
    private class RecordingFanout(
        var projectResult: Boolean = false,
        var itemId: String? = "item-1",
        var autoplayMirror: Boolean = true,
    ) {
        val log = mutableListOf<String>()

        /** The (osLeg, duck) pairs the fanout pushed, in order. */
        val focusPolicies = mutableListOf<Pair<Boolean, Boolean>>()

        val fanout = PlayerPrefsFanout(
            projectPrefs = { agg ->
                log += "project:${agg.videoPlayer.videoAutoplayNext}"
                projectResult
            },
            getCurrentItemId = { itemId },
            seedSleepTimerLastUsedMs = { ms -> log += "seedSleepTimer:$ms" },
            onStoredSelectionChanged = { selection -> log += "seedStoredSelection:$selection" },
            seedDefaultSearchLanguage = { language -> log += "seedSearchLanguage:$language" },
            isAutoplayNextApplied = { applied -> applied == autoplayMirror },
            applyAutoplayNextPref = { enabled ->
                log += "applyAutoplayNext:$enabled"
                autoplayMirror = enabled
            },
            rebuildEngineConfigIfRunning = { log += "rebuildConfig" },
            applyVideoFocusPolicy = { osLeg, duck -> focusPolicies += osLeg to duck },
        )
    }

    // ─── Subtitle-style rebuild trigger (A) ─────────────────────────────────

    @Test
    fun `subtitle style change projects then rebuilds the engine config`() {
        val fakes = RecordingFanout(projectResult = true)
        fakes.fanout.onAggregateChanged(VideoPlayerAggregate(), VideoPlayerAggregate())
        assertEquals(listOf("project:true", "rebuildConfig"), fakes.log)
    }

    @Test
    fun `no subtitle style change means no rebuild from trigger A`() {
        val fakes = RecordingFanout(projectResult = false)
        fakes.fanout.onAggregateChanged(VideoPlayerAggregate(), VideoPlayerAggregate())
        assertFalse(fakes.log.contains("rebuildConfig"))
    }

    // ─── Volume-boost / equalizer rebuild trigger (B) ───────────────────────

    @Test
    fun `volume boost flip rebuilds the engine config once`() {
        val fakes = RecordingFanout(projectResult = false)
        val old = VideoPlayerAggregate()
        val new = old.copy(audioEffects = old.audioEffects.copy(volumeBoostEnabled = true))
        fakes.fanout.onAggregateChanged(old, new)
        assertEquals(1, fakes.log.count { it == "rebuildConfig" })
    }

    @Test
    fun `equalizer settings change rebuilds the engine config`() {
        val fakes = RecordingFanout(projectResult = false)
        val old = VideoPlayerAggregate()
        val new = old.copy(audioEffects = old.audioEffects.copy(volumeBoostGain = 150))
        fakes.fanout.onAggregateChanged(old, new)
        assertTrue(fakes.log.contains("rebuildConfig"))
    }

    @Test
    fun `unrelated aggregate emission fires no rebuild`() {
        val fakes = RecordingFanout(projectResult = false)
        val old = VideoPlayerAggregate()
        val new = old.copy(videoPlayer = old.videoPlayer.copy(showClockInPlayer = true))
        fakes.fanout.onAggregateChanged(old, new)
        assertFalse(fakes.log.contains("rebuildConfig"))
    }

    @Test
    fun `focus pref flip does not rebuild the engine config`() {
        // The video focus slice: no engine consumes pauseOnAudioFocusLoss
        // anymore (ExoPlayer's built-in handleAudioFocus is forced off; the
        // OS seat belongs to the PlaybackFocus module). The pref rides the
        // policy push instead.
        val fakes = RecordingFanout(projectResult = false)
        val old = VideoPlayerAggregate()
        // Default is `true` (PlaybackStore) — flip OFF so the diff is real.
        val new = old.copy(playback = old.playback.copy(pauseOnAudioFocusLoss = false))
        fakes.fanout.onAggregateChanged(old, new)
        assertFalse(fakes.log.contains("rebuildConfig"))
        assertEquals(listOf(false to false), fakes.focusPolicies)
    }

    // ─── Controller seeds fire only on relevant diffs ───────────────────────

    @Test
    fun `sleep timer seed fires only when the stored duration changed`() {
        val fakes = RecordingFanout()
        val old = VideoPlayerAggregate()
        val changed = old.copy(audio = old.audio.copy(sleepTimerDurationMs = 1_800_000L))
        fakes.fanout.onAggregateChanged(old, changed)
        assertTrue(fakes.log.contains("seedSleepTimer:1800000"))

        fakes.log.clear()
        // Same diff again → still fires (the old aggregate advanced too).
        fakes.fanout.onAggregateChanged(old, changed)
        assertTrue(fakes.log.contains("seedSleepTimer:1800000"))

        fakes.log.clear()
        // Unrelated pref write → no re-seed.
        fakes.fanout.onAggregateChanged(changed, changed.copy(videoPlayer = changed.videoPlayer.copy(showClockInPlayer = true)))
        assertFalse(fakes.log.any { it.startsWith("seedSleepTimer") })
    }

    @Test
    fun `stored selection seed fires only when the current item's selection changed`() {
        val fakes = RecordingFanout()
        val old = VideoPlayerAggregate()
        val selection = MediaStreamSelection(audioStreamIndex = 1, subtitleStreamIndex = null)
        val new = old.copy(
            engine = old.engine.copy(mediaStreamSelections = mapOf("item-1" to selection)),
        )
        fakes.fanout.onAggregateChanged(old, new)
        assertEquals(listOf("seedStoredSelection:$selection"), fakes.log.filter { it.startsWith("seedStoredSelection") })

        fakes.log.clear()
        // A different item's row changes → the current item's selection did
        // not → no re-seed.
        val other = new.copy(
            engine = new.engine.copy(mediaStreamSelections = new.engine.mediaStreamSelections + ("item-2" to selection)),
        )
        fakes.fanout.onAggregateChanged(new, other)
        assertFalse(fakes.log.any { it.startsWith("seedStoredSelection") })
    }

    @Test
    fun `default search language seeds on change and defaults to eng`() {
        val fakes = RecordingFanout()
        val old = VideoPlayerAggregate()
        val new = old.copy(subtitle = old.subtitle.copy(preferredSubtitleLanguage = "deu"))
        fakes.fanout.onAggregateChanged(old, new)
        assertTrue(fakes.log.contains("seedSearchLanguage:deu"))

        fakes.log.clear()
        // Null language on BOTH sides resolves to "eng" on each — no diff, no seed.
        fakes.fanout.onAggregateChanged(VideoPlayerAggregate(), VideoPlayerAggregate())
        assertFalse(fakes.log.any { it.startsWith("seedSearchLanguage") })
    }

    // ─── Autoplay-controller flip ───────────────────────────────────────────

    @Test
    fun `autoplay flip applies when the mirror lags the pref`() {
        val fakes = RecordingFanout(autoplayMirror = true)
        val old = VideoPlayerAggregate()
        val new = old.copy(videoPlayer = old.videoPlayer.copy(videoAutoplayNext = false))
        fakes.fanout.onAggregateChanged(old, new)
        assertEquals(listOf("applyAutoplayNext:false"), fakes.log.filter { it.startsWith("applyAutoplayNext") })
    }

    @Test
    fun `autoplay flip is skipped when the mirror already matches`() {
        val fakes = RecordingFanout(autoplayMirror = false)
        val old = VideoPlayerAggregate()
        val new = old.copy(videoPlayer = old.videoPlayer.copy(videoAutoplayNext = false))
        fakes.fanout.onAggregateChanged(old, new)
        assertFalse(fakes.log.any { it.startsWith("applyAutoplayNext") })
    }

    // ─── Video focus-policy push ─────────────────────────────────────────────

    @Test
    fun `focus prefs ride every emission into the policy push`() {
        // The push is unconditional (the module-side setter is idempotent) —
        // unlike the seeds, it is NOT diff-guarded.
        val fakes = RecordingFanout()
        val old = VideoPlayerAggregate()
        val new = old.copy(videoPlayer = old.videoPlayer.copy(showClockInPlayer = true))
        fakes.fanout.onAggregateChanged(old, new)
        assertEquals(listOf(true to false), fakes.focusPolicies, "defaults: pause-on-loss on, duck off")
    }

    @Test
    fun `duck pref on keeps the OS seat even with pause off`() {
        // The legacy duck seat was registered on the duck pref ALONE (git
        // HEAD's `if (duckOnTransientFocusLoss) registerAudioFocus()`), so a
        // pause-off/duck-on user still ducked through phone calls. The seat
        // gate is the prefs' OR; the loss DIRECTIVE stays the duck pref
        // (Duck + regain-restore).
        val fakes = RecordingFanout()
        val new = VideoPlayerAggregate(playback = VideoPlayerAggregate().playback.copy(pauseOnAudioFocusLoss = false, duckOnTransientFocusLoss = true))
        fakes.fanout.onAggregateChanged(VideoPlayerAggregate(), new)
        assertEquals(listOf(true to true), fakes.focusPolicies)
    }

    @Test
    fun `both prefs on pushes the duck policy`() {
        // The legacy dual-seat race (ExoPlayer-builtin + manual lifecycle)
        // collapsed onto one module-owned seat with the duck pref winning —
        // the more specific intent.
        val fakes = RecordingFanout()
        val new = VideoPlayerAggregate(playback = VideoPlayerAggregate().playback.copy(pauseOnAudioFocusLoss = true, duckOnTransientFocusLoss = true))
        fakes.fanout.onAggregateChanged(VideoPlayerAggregate(), new)
        assertEquals(listOf(true to true), fakes.focusPolicies)
    }

    @Test
    fun `policy push tracks a pref transition across emissions`() {
        val fakes = RecordingFanout()
        val duckOn = VideoPlayerAggregate(playback = VideoPlayerAggregate().playback.copy(duckOnTransientFocusLoss = true))
        fakes.fanout.onAggregateChanged(VideoPlayerAggregate(), duckOn)
        fakes.fanout.onAggregateChanged(duckOn, VideoPlayerAggregate())
        assertEquals(listOf(true to true, true to false), fakes.focusPolicies)
    }

    // ─── Choreography order ─────────────────────────────────────────────────

    @Test
    fun `projection runs before the seeds and the rebuild`() {
        val fakes = RecordingFanout(projectResult = true)
        val old = VideoPlayerAggregate()
        val new = old.copy(audio = old.audio.copy(sleepTimerDurationMs = 60_000L))
        fakes.fanout.onAggregateChanged(old, new)
        assertEquals(
            listOf("project:true", "seedSleepTimer:60000", "rebuildConfig"),
            fakes.log,
        )
    }
}
