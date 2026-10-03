package com.raulshma.jellyplay.feature.player.video.engine.mpv

import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the shared mpv property-intake table ([MpvPropertyIntake]) — the
 * decision rows both mpv hosts funnel their observer callbacks through
 * (the `MpvEventFold`/`MpvFoldApplier` precedent, intake half). Exhaustive
 * per the table's doctrine: every row with a representative value, BOTH
 * declared buffered formulas (Android position + demuxer-cache-duration;
 * the shared absolute demuxer-cache-time), the per-host gating
 * (speed-drop, sub-start, sub-visibility, channel-count), unknown- and
 * wrong-shape nulls, the full observed-property sets of both hosts, and
 * round-trips of the produced events through [MpvEventFold].
 */
class MpvPropertyIntakeTest {

    private fun intake(
        host: MpvIntakeHost,
        property: String,
        value: MpvIntakeValue,
        positionMs: Long = 0L,
        livePaused: Boolean = true,
        previousChannelCount: Int? = null,
    ): MpvPropertyIntakeResult? =
        MpvPropertyIntake.dispatch(host, property, value, positionMs, livePaused, previousChannelCount)

    // ─── Event rows (shared by both hosts) ────────────────────────────────────

    @Test
    fun pauseFlag_foldsToPauseChanged_bothHosts() {
        for (host in MpvIntakeHost.entries) {
            assertEquals(
                MpvPropertyIntakeResult.Event(MpvPlaybackEvent.PauseChanged(true)),
                intake(host, "pause", MpvIntakeValue.Flag(true)),
            )
            assertEquals(
                MpvPropertyIntakeResult.Event(MpvPlaybackEvent.PauseChanged(false)),
                intake(host, "pause", MpvIntakeValue.Flag(false)),
            )
        }
    }

    @Test
    fun pausedForCacheFlag_foldsToBufferingLatchEvent_bothHosts() {
        for (host in MpvIntakeHost.entries) {
            assertEquals(
                MpvPropertyIntakeResult.Event(MpvPlaybackEvent.PausedForCacheChanged(buffering = true)),
                intake(host, "paused-for-cache", MpvIntakeValue.Flag(true)),
            )
        }
    }

    @Test
    fun eofReachedRow_carriesTheLivePauseIntoTheEvent_bothHosts() {
        for (host in MpvIntakeHost.entries) {
            assertEquals(
                MpvPropertyIntakeResult.Event(MpvPlaybackEvent.EofReachedChanged(eof = true, pausedNow = false)),
                intake(host, "eof-reached", MpvIntakeValue.Flag(true), livePaused = false),
            )
            // The default is the readers' no-handle fallback (paused).
            assertEquals(
                MpvPropertyIntakeResult.Event(MpvPlaybackEvent.EofReachedChanged(eof = false, pausedNow = true)),
                intake(host, "eof-reached", MpvIntakeValue.Flag(false)),
            )
        }
    }

    @Test
    fun sidAndAid_mapToTrackSwitchKinds_overTextAndUnreadPayloads() {
        // Android reads the string (it logs it)…
        assertEquals(
            MpvPropertyIntakeResult.Event(MpvPlaybackEvent.TrackSwitch(MpvPlaybackEvent.TrackKind.SUBTITLE)),
            intake(MpvIntakeHost.ANDROID, "sid", MpvIntakeValue.Text("2")),
        )
        assertEquals(
            MpvPropertyIntakeResult.Event(MpvPlaybackEvent.TrackSwitch(MpvPlaybackEvent.TrackKind.AUDIO)),
            intake(MpvIntakeHost.ANDROID, "aid", MpvIntakeValue.Text("1")),
        )
        // …the desktop ships the payload unread (the fold keys on the name).
        assertEquals(
            MpvPropertyIntakeResult.Event(MpvPlaybackEvent.TrackSwitch(MpvPlaybackEvent.TrackKind.SUBTITLE)),
            intake(MpvIntakeHost.DESKTOP, "sid", MpvIntakeValue.Unread),
        )
        assertEquals(
            MpvPropertyIntakeResult.Event(MpvPlaybackEvent.TrackSwitch(MpvPlaybackEvent.TrackKind.AUDIO)),
            intake(MpvIntakeHost.DESKTOP, "aid", MpvIntakeValue.Unread),
        )
    }

    @Test
    fun subText_mapsToSubTextChanged_includingTheBlankClearLine() {
        for (host in MpvIntakeHost.entries) {
            assertEquals(
                MpvPropertyIntakeResult.Event(MpvPlaybackEvent.SubTextChanged("Hello there")),
                intake(host, "sub-text", MpvIntakeValue.Text("Hello there")),
            )
            // The blank clear-line is a real event — the FOLD decides the clear.
            assertEquals(
                MpvPropertyIntakeResult.Event(MpvPlaybackEvent.SubTextChanged("")),
                intake(host, "sub-text", MpvIntakeValue.Text("")),
            )
        }
    }

    // ─── Numeric cache rows ───────────────────────────────────────────────────

    @Test
    fun timePos_truncatesToMillisAndClampsNegative_bothHosts() {
        for (host in MpvIntakeHost.entries) {
            assertEquals(
                MpvPropertyIntakeResult.CachedPositionMs(12_500L),
                intake(host, "time-pos", MpvIntakeValue.Decimal(12.5)),
            )
            // Truncation, not rounding — the shipped (v * 1000).toLong() shape.
            assertEquals(
                MpvPropertyIntakeResult.CachedPositionMs(12_345L),
                intake(host, "time-pos", MpvIntakeValue.Decimal(12.3456)),
            )
            assertEquals(
                MpvPropertyIntakeResult.CachedPositionMs(0L),
                intake(host, "time-pos", MpvIntakeValue.Decimal(-0.5)),
            )
        }
    }

    @Test
    fun duration_sharedScaling_withTheAndroidOnlyNegativeClamp() {
        for (host in MpvIntakeHost.entries) {
            assertEquals(
                MpvPropertyIntakeResult.CachedDurationMs(91_250L),
                intake(host, "duration", MpvIntakeValue.Decimal(91.25)),
            )
        }
        // Declared per-host landing (practically unreachable, pinned anyway):
        assertEquals(
            MpvPropertyIntakeResult.CachedDurationMs(0L),
            intake(MpvIntakeHost.ANDROID, "duration", MpvIntakeValue.Decimal(-0.5)),
        )
        assertEquals(
            MpvPropertyIntakeResult.CachedDurationMs(-500L),
            intake(MpvIntakeHost.DESKTOP, "duration", MpvIntakeValue.Decimal(-0.5)),
        )
    }

    @Test
    fun demuxerCacheTime_absoluteBufferedFormula_sharedByBothHosts() {
        for (host in MpvIntakeHost.entries) {
            assertEquals(
                MpvPropertyIntakeResult.CachedBufferedMs(45_000L),
                intake(host, "demuxer-cache-time", MpvIntakeValue.Whole(45L)),
            )
        }
    }

    @Test
    fun demuxerCacheDuration_androidAddsCachedPosition_desktopRowIsUnobserved() {
        // THE ANDROID BUFFERED FORMULA (relative): cached position + cache-duration.
        assertEquals(
            MpvPropertyIntakeResult.CachedBufferedMs(62_500L),
            intake(MpvIntakeHost.ANDROID, "demuxer-cache-duration", MpvIntakeValue.Decimal(2.5), positionMs = 60_000L),
        )
        // Fractional seconds truncate (the shipped (value * 1000L).toLong() shape).
        assertEquals(
            MpvPropertyIntakeResult.CachedBufferedMs(61_234L),
            intake(MpvIntakeHost.ANDROID, "demuxer-cache-duration", MpvIntakeValue.Decimal(1.234), positionMs = 60_000L),
        )
        // THE DESKTOP SIDE OF THE DECLARED DIVERGENCE: it does not observe
        // this property — never a silently-adopted formula.
        assertNull(intake(MpvIntakeHost.DESKTOP, "demuxer-cache-duration", MpvIntakeValue.Decimal(2.5), positionMs = 60_000L))
    }

    @Test
    fun subStart_androidOnly() {
        assertEquals(
            MpvPropertyIntakeResult.CachedSubStartSec(7.25),
            intake(MpvIntakeHost.ANDROID, "sub-start", MpvIntakeValue.Decimal(7.25)),
        )
        assertNull(intake(MpvIntakeHost.DESKTOP, "sub-start", MpvIntakeValue.Decimal(7.25)))
    }

    @Test
    fun speed_desktopCaches_androidDropsTheObservation() {
        assertEquals(
            MpvPropertyIntakeResult.CachedSpeed(1.25f),
            intake(MpvIntakeHost.DESKTOP, "speed", MpvIntakeValue.Decimal(1.25)),
        )
        // Android registers the observation and drops the event (its getter
        // live-reads) — the declared shipped shape.
        assertNull(intake(MpvIntakeHost.ANDROID, "speed", MpvIntakeValue.Decimal(1.25)))
    }

    @Test
    fun subVisibility_androidDebugRow_desktopUnobserved() {
        assertEquals(
            MpvPropertyIntakeResult.SubVisibilityObserved(true),
            intake(MpvIntakeHost.ANDROID, "sub-visibility", MpvIntakeValue.Flag(true)),
        )
        assertNull(intake(MpvIntakeHost.DESKTOP, "sub-visibility", MpvIntakeValue.Flag(true)))
    }

    @Test
    fun channelCount_desktopOnly_withChangeGuardAgainstThePreviousCount() {
        assertEquals(
            MpvPropertyIntakeResult.ChannelLayoutChanged(6),
            intake(MpvIntakeHost.DESKTOP, "audio-params/channel-count", MpvIntakeValue.Whole(6L), previousChannelCount = null),
        )
        assertEquals(
            MpvPropertyIntakeResult.ChannelLayoutChanged(2),
            intake(MpvIntakeHost.DESKTOP, "audio-params/channel-count", MpvIntakeValue.Whole(2L), previousChannelCount = 6),
        )
        // Unchanged layout: no re-init of the audio chain.
        assertNull(
            intake(MpvIntakeHost.DESKTOP, "audio-params/channel-count", MpvIntakeValue.Whole(6L), previousChannelCount = 6),
        )
        assertNull(intake(MpvIntakeHost.ANDROID, "audio-params/channel-count", MpvIntakeValue.Whole(6L)))
    }

    // ─── Node rows (host seam names) ──────────────────────────────────────────

    @Test
    fun trackListAndCacheState_routeToTheHostSeams_bothHosts() {
        for (host in MpvIntakeHost.entries) {
            assertEquals(
                MpvPropertyIntakeResult.RefreshTracks,
                intake(host, "track-list", MpvIntakeValue.Node),
            )
            assertEquals(
                MpvPropertyIntakeResult.DecodeBufferedRanges,
                intake(host, "demuxer-cache-state", MpvIntakeValue.Node),
            )
        }
    }

    // ─── The null outcome: unknown, unobserved, wrong shape ──────────────────

    @Test
    fun unknownProperty_yieldsNull_bothHosts() {
        for (host in MpvIntakeHost.entries) {
            assertNull(intake(host, "cache-buffering-state", MpvIntakeValue.Whole(50L)))
            assertNull(intake(host, "", MpvIntakeValue.Text("")))
        }
    }

    @Test
    fun wrongPayloadShape_yieldsNull() {
        assertNull(intake(MpvIntakeHost.ANDROID, "time-pos", MpvIntakeValue.Flag(true)))
        assertNull(intake(MpvIntakeHost.ANDROID, "pause", MpvIntakeValue.Decimal(1.0)))
        assertNull(intake(MpvIntakeHost.DESKTOP, "demuxer-cache-time", MpvIntakeValue.Text("45")))
        assertNull(intake(MpvIntakeHost.ANDROID, "sub-text", MpvIntakeValue.Node))
        assertNull(intake(MpvIntakeHost.DESKTOP, "sid", MpvIntakeValue.Flag(true)))
        assertNull(intake(MpvIntakeHost.ANDROID, "track-list", MpvIntakeValue.Text("[]")))
    }

    // ─── Observed-set ratchets: no observed property loses its intake ────────

    /** Android's `postInitOptions` observe list with representative payloads. */
    @Test
    fun everyAndroidObservedProperty_yieldsAnIntake_exceptTheDroppedSpeedRow() {
        val observed: Map<String, MpvIntakeValue> = mapOf(
            "pause" to MpvIntakeValue.Flag(true),
            "speed" to MpvIntakeValue.Decimal(1.25), // observed AND dropped — the declared row
            "paused-for-cache" to MpvIntakeValue.Flag(false),
            "eof-reached" to MpvIntakeValue.Flag(false),
            "time-pos" to MpvIntakeValue.Decimal(60.0),
            "duration" to MpvIntakeValue.Decimal(1800.0),
            "demuxer-cache-duration" to MpvIntakeValue.Decimal(2.5),
            "demuxer-cache-time" to MpvIntakeValue.Whole(62L),
            "demuxer-cache-state" to MpvIntakeValue.Node,
            "sid" to MpvIntakeValue.Text("2"),
            "aid" to MpvIntakeValue.Text("1"),
            "track-list" to MpvIntakeValue.Node,
            "sub-visibility" to MpvIntakeValue.Flag(true),
            "sub-text" to MpvIntakeValue.Text("Hi"),
            "sub-start" to MpvIntakeValue.Decimal(7.25),
        )
        observed.forEach { (property, value) ->
            val result = intake(MpvIntakeHost.ANDROID, property, value, positionMs = 60_000L)
            if (property == "speed") {
                assertNull(result, "Android's speed row is declared drop-only; a decision here is a behavior change")
            } else {
                assertTrue(result != null, "Android observes '$property' but the table has no row for it")
            }
        }
    }

    /** The desktop's `registerObservers` observe list with representative payloads. */
    @Test
    fun everyDesktopObservedProperty_yieldsAnIntake() {
        val observed: Map<String, MpvIntakeValue> = mapOf(
            "pause" to MpvIntakeValue.Flag(true),
            "speed" to MpvIntakeValue.Decimal(1.25),
            "paused-for-cache" to MpvIntakeValue.Flag(false),
            "eof-reached" to MpvIntakeValue.Flag(false),
            "time-pos" to MpvIntakeValue.Decimal(60.0),
            "duration" to MpvIntakeValue.Decimal(1800.0),
            "demuxer-cache-time" to MpvIntakeValue.Whole(62L),
            "demuxer-cache-state" to MpvIntakeValue.Node,
            "sub-text" to MpvIntakeValue.Text("Hi"),
            "sid" to MpvIntakeValue.Unread,
            "aid" to MpvIntakeValue.Unread,
            "track-list" to MpvIntakeValue.Node,
            "audio-params/channel-count" to MpvIntakeValue.Whole(6),
        )
        observed.forEach { (property, value) ->
            assertTrue(
                intake(MpvIntakeHost.DESKTOP, property, value, previousChannelCount = null) != null,
                "Desktop observes '$property' but the table has no row for it",
            )
        }
    }

    // ─── Round-trips: table row → MpvEventFold ───────────────────────────────

    @Test
    fun foldRoundTrip_pauseEvent_releasesTheGuardedIsPlayingLatch() {
        val loaded = MpvEventFold.fold(MpvPlaybackLatches(), MpvPlaybackEvent.FileLoaded(pausedNow = true))
        val event = (intake(MpvIntakeHost.ANDROID, "pause", MpvIntakeValue.Flag(false)) as MpvPropertyIntakeResult.Event).event
        val result = MpvEventFold.fold(loaded.latches, event)
        assertEquals(true, result.isPlaying)
        assertTrue(result.latches.fileLoaded)
    }

    @Test
    fun foldRoundTrip_sidSwitch_declaresTheCueClearAndTrackRefresh() {
        val event = (intake(MpvIntakeHost.DESKTOP, "sid", MpvIntakeValue.Unread) as MpvPropertyIntakeResult.Event).event
        val result = MpvEventFold.fold(MpvPlaybackLatches(), event)
        assertTrue(result.refreshTracks)
        assertTrue(result.clearCueHistory)
        assertTrue(result.clearLiveCue)
    }

    @Test
    fun foldRoundTrip_subText_blankClearsTheLiveCue_textMirrorsTheLine() {
        val blank = (intake(MpvIntakeHost.DESKTOP, "sub-text", MpvIntakeValue.Text("")) as MpvPropertyIntakeResult.Event).event
        val cleared = MpvEventFold.fold(MpvPlaybackLatches(), blank)
        assertTrue(cleared.clearLiveCue)
        assertNull(cleared.liveSubtitleText)

        val line = (intake(MpvIntakeHost.ANDROID, "sub-text", MpvIntakeValue.Text("Hi")) as MpvPropertyIntakeResult.Event).event
        val mirrored = MpvEventFold.fold(MpvPlaybackLatches(), line)
        assertEquals("Hi", mirrored.liveSubtitleText)
    }

    @Test
    fun foldRoundTrip_eofReached_true_parksAtEndedWithTheLivePause() {
        val loaded = MpvEventFold.fold(MpvPlaybackLatches(), MpvPlaybackEvent.FileLoaded(pausedNow = false))
        val event = (
            intake(MpvIntakeHost.DESKTOP, "eof-reached", MpvIntakeValue.Flag(true), livePaused = true)
                as MpvPropertyIntakeResult.Event
            ).event
        val result = MpvEventFold.fold(loaded.latches, event)
        assertEquals(EnginePlaybackState.ENDED, result.playbackState)
        assertEquals(false, result.isPlaying)
        assertTrue(result.clearLiveCue)
    }
}
