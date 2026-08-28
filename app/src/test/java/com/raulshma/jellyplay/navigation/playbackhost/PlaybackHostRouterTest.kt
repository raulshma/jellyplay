package com.raulshma.jellyplay.navigation.playbackhost

import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization test of [PlaybackHostRouter.decide] — pins every
 * (route x preferredPlayer) cell of the CURRENT host table so any future
 * answer change is a deliberate, visible edit rather than silent drift
 * (wave 19C flipped Live TV from InNav to DedicatedActivity exactly that way).
 *
 * Pure JVM: the router is a pure function of (NavKey, PlayerType) with no
 * Android types. Its single consumer is the navigateFilter adapter in
 * JellyPlayApp.kt.
 */
class PlaybackHostRouterTest {

    // Every PlayerType the preference can resolve to.
    private val allPlayerTypes = PlayerType.entries.toList()

    private val videoAllArgs = Route.VideoPlayer(
        itemId = "item-1",
        mediaSourceId = "source-1",
        startPositionTicks = 10_000_000L,
        subtitleStreamIndex = 3,
        audioStreamIndex = 1,
    )

    private val videoMinimalArgs = Route.VideoPlayer(itemId = "item-min")

    private val liveTv = Route.LiveTvChannelPlayer(
        channelId = "chan-1",
        channelName = "Channel One",
        audioStreamIndex = 2,
        subtitleStreamIndex = 4,
    )

    // ── Route.VideoPlayer ──────────────────────────────────────────────────

    @Test
    fun `video with EXTERNAL preferred answers ExternalPlayer with the route's fields`() {
        val decision = PlaybackHostRouter.decide(videoAllArgs, PlayerType.EXTERNAL)

        assertEquals(
            HostDecision.ExternalPlayer(
                itemId = "item-1",
                mediaSourceId = "source-1",
                startPositionTicks = 10_000_000L,
            ),
            decision,
        )
    }

    @Test
    fun `minimal video with EXTERNAL preferred answers ExternalPlayer with null source and zero ticks`() {
        val decision = PlaybackHostRouter.decide(videoMinimalArgs, PlayerType.EXTERNAL)

        assertEquals(
            HostDecision.ExternalPlayer(itemId = "item-min", mediaSourceId = null, startPositionTicks = 0L),
            decision,
        )
    }

    @Test
    fun `video with any non-EXTERNAL engine answers DedicatedActivity carrying the full args`() {
        // The dedicated PlayerActivity hosts video for every embedded engine;
        // engine choice happens inside VideoPlayerScreen, not at the host seam.
        listOf(PlayerType.EXO_PLAYER, PlayerType.MPV, PlayerType.LIBVLC).forEach { pref ->
            val decision = PlaybackHostRouter.decide(videoAllArgs, pref)

            assertEquals(
                HostDecision.DedicatedActivity(
                    PlayerActivityArgs.Video(
                        itemId = "item-1",
                        mediaSourceId = "source-1",
                        startPositionTicks = 10_000_000L,
                        subtitleStreamIndex = 3,
                        audioStreamIndex = 1,
                    ),
                ),
                decision,
            )
        }
    }

    @Test
    fun `minimal video with embedded engine answers DedicatedActivity with defaulted args`() {
        val decision = PlaybackHostRouter.decide(videoMinimalArgs, PlayerType.EXO_PLAYER)

        assertEquals(
            HostDecision.DedicatedActivity(PlayerActivityArgs.Video(itemId = "item-min")),
            decision,
        )
    }

    // ── Route.LiveTvChannelPlayer ──────────────────────────────────────────

    @Test
    fun `live tv with EXTERNAL preferred answers ExternalPlayer with null source and zero ticks`() {
        // Pinned quirk: Live TV hands off externally only for EXTERNAL; the
        // channel id plays the role of item id, and there is no resume
        // position for a live stream.
        val decision = PlaybackHostRouter.decide(liveTv, PlayerType.EXTERNAL)

        assertEquals(
            HostDecision.ExternalPlayer(itemId = "chan-1", mediaSourceId = null, startPositionTicks = 0L),
            decision,
        )
    }

    @Test
    fun `live tv with any non-EXTERNAL engine answers DedicatedActivity carrying the channel args`() {
        // Wave 19C: the dedicated PlayerActivity hosts live for every embedded
        // engine so system PiP serves live TV (the former in-nav answer died
        // with MainActivity's supportsPictureInPicture). The channel fields
        // map onto PlayerActivityArgs.Live verbatim; no resume position for a
        // live stream.
        listOf(PlayerType.EXO_PLAYER, PlayerType.MPV, PlayerType.LIBVLC).forEach { pref ->
            assertEquals(
                "pref=$pref",
                HostDecision.DedicatedActivity(
                    PlayerActivityArgs.Live(
                        channelId = "chan-1",
                        channelName = "Channel One",
                        subtitleStreamIndex = 4,
                        audioStreamIndex = 2,
                    ),
                ),
                PlaybackHostRouter.decide(liveTv, pref),
            )
        }
    }

    // ── Route.AudioPlayer ──────────────────────────────────────────────────

    @Test
    fun `audio never goes external regardless of preferred player`() {
        // Pinned quirk: EXTERNAL is a video-only handoff; audio always
        // composes in-nav.
        allPlayerTypes.forEach { pref ->
            assertEquals(
                "pref=$pref",
                HostDecision.InNav,
                PlaybackHostRouter.decide(Route.AudioPlayer("track-1"), pref),
            )
        }
    }

    // ── Non-playback routes ────────────────────────────────────────────────

    @Test
    fun `non-playback routes answer NotPlayback for every preferred player`() {
        listOf(Route.MediaDetail("item-1"), Route.Home).forEach { route ->
            allPlayerTypes.forEach { pref ->
                assertEquals(
                    "route=$route pref=$pref",
                    HostDecision.NotPlayback,
                    PlaybackHostRouter.decide(route, pref),
                )
            }
        }
    }

    // ── Adapter contract pins ──────────────────────────────────────────────

    @Test
    fun `every route x preferredPlayer cell produces a decision`() {
        // Sanity: the table is total — no (route, pref) pair can fall through
        // without an explicit HostDecision. The navigateFilter adapter
        // branches on exactly these four variants.
        val routes: List<NavKey> = listOf(
            videoAllArgs,
            videoMinimalArgs,
            liveTv,
            Route.AudioPlayer("track-1"),
            Route.MediaDetail("item-1"),
            Route.Home,
        )
        routes.forEach { route ->
            allPlayerTypes.forEach { pref ->
                val decision = PlaybackHostRouter.decide(route, pref)
                assertTrue(
                    "route=$route pref=$pref",
                    decision is HostDecision.ExternalPlayer ||
                        decision is HostDecision.DedicatedActivity ||
                        decision is HostDecision.InNav ||
                        decision is HostDecision.NotPlayback,
                )
            }
        }
    }

    @Test
    fun `only video and live tv can answer ExternalPlayer`() {
        // The external-player result flow (reportExternalPlaybackStopped)
        // credits watched progress; it must only ever run for these routes.
        // For live, every non-EXTERNAL engine now answers DedicatedActivity
        // (wave 19C live PiP) — the ExternalPlayer handoff survives only for
        // PlayerType.EXTERNAL.
        allPlayerTypes.forEach { pref ->
            val videoDecision = PlaybackHostRouter.decide(videoAllArgs, pref)
            assertTrue(
                "pref=$pref",
                videoDecision is HostDecision.ExternalPlayer ||
                    videoDecision is HostDecision.DedicatedActivity,
            )
            val liveDecision = PlaybackHostRouter.decide(liveTv, pref)
            assertTrue(
                "pref=$pref",
                liveDecision is HostDecision.ExternalPlayer || liveDecision is HostDecision.DedicatedActivity,
            )
            assertFalse(
                "pref=$pref",
                PlaybackHostRouter.decide(Route.AudioPlayer("track-1"), pref)
                    is HostDecision.ExternalPlayer,
            )
        }
    }
}
