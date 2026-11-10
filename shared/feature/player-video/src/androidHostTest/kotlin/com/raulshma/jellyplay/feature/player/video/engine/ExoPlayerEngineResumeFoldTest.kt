package com.raulshma.jellyplay.feature.player.video.engine

import android.os.Looper
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raulshma.jellyplay.feature.player.video.subtitle.AndroidFontProvider
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows

/**
 * The resume-position fold in [ExoPlayerEngine.attachMediaItemAndPrepare]:
 * every load — fresh rebuild or reused player, zero or non-zero start —
 * passes the request's position straight into `Player.setMediaItem(item,
 * startPositionMs)` and never issues a post-prepare `seekTo`.
 *
 * Why this shape is load-bearing: preparation starts AT the resume position,
 * so no bytes are fetched at 0 only to be discarded by a correction seek
 * (direct play: the initial HTTP range fetch; HLS/transcode: the extra
 * playlist/segment fetch at 0 plus the position discontinuity the seek
 * fires). Pinned here on what a headless Robolectric environment can observe
 * through the engine's media3 player handle ([Media3PlayerHost.media3Player]):
 * the folded position is client-side masking state, readable synchronously
 * the moment load() returns — Media3 establishes it inside `setMediaItems`,
 * no loader progress needed (the placeholder host never resolves anyway).
 * The mechanism half of the pin: a post-prepare `seekTo` on a non-empty
 * timeline is the only producer of a `DISCONTINUITY_REASON_SEEK` event, so
 * "no SEEK discontinuity fires during a resume load" proves the fold carried
 * the position instead of a trailing seek. The first-rendered-frame check for
 * a >0 resume needs on-device instrumentation and stays out of scope here.
 */
@RunWith(AndroidJUnit4::class)
class ExoPlayerEngineResumeFoldTest {

    private fun engine(): ExoPlayerEngine = ExoPlayerEngine(
        context = ApplicationProvider.getApplicationContext(),
        streamingOkHttpClient = OkHttpClient(),
        fontProvider = AndroidFontProvider(ApplicationProvider.getApplicationContext()),
    )

    private fun request(startPositionMs: Long) = PlaybackRequest(
        uri = "https://jellyfin.invalid/videos/1/stream.mp4",
        title = "resume fold",
        startPositionMs = startPositionMs,
    )

    /** Runs everything ExoPlayer's internal handler (same main looper) has queued. */
    private fun drainMainThread() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun load_withResumePosition_foldsItIntoTheInitialPrepare() {
        val engine = engine()

        engine.load(request(startPositionMs = 90_000))

        val player = checkNotNull(engine.media3Player) { "load() must leave a player bound" }
        assertEquals("the requested resume position must be the player's position off load()", 90_000L, player.currentPosition)
        assertEquals("prepare() runs in the same batch — the player is already buffering", Player.STATE_BUFFERING, player.playbackState)
        assertTrue("load() ends with play requested", player.playWhenReady)

        engine.release()
    }

    @Test
    fun load_zeroResume_startsAtZero_likeTheFormerPlainSetMediaItem() {
        val engine = engine()

        // No-resume load: the raw 0 must ride the same fold and resolve to the
        // same position-0 start the former one-arg setMediaItem + skip-the-seek
        // path produced — never rewritten into "default position"
        // (C.TIME_UNSET) semantics.
        engine.load(request(startPositionMs = 0))

        val player = checkNotNull(engine.media3Player) { "load() must leave a player bound" }
        assertEquals(0L, player.currentPosition)
        assertEquals(Player.STATE_BUFFERING, player.playbackState)
        assertTrue(player.playWhenReady)

        engine.release()
    }

    @Test
    fun reusePath_reLoad_carriesTheNewRequestsResumePosition() {
        val engine = engine()
        engine.load(request(startPositionMs = 30_000))
        val firstPlayer = checkNotNull(engine.media3Player)

        // Identical construction inputs with a different start position: the
        // reuse-vs-rebuild policy keeps the player (position is not a
        // construction input), and the reused player must land on the NEW
        // request's folded position — stale positions from the previous item
        // would resume the binge-watched next episode where this one left off.
        engine.load(request(startPositionMs = 120_000))

        val secondPlayer = checkNotNull(engine.media3Player)
        assertSame("identical construction inputs must reuse the player", firstPlayer, secondPlayer)
        assertEquals(120_000L, secondPlayer.currentPosition)

        engine.release()
    }

    @Test
    fun reusePath_resumeLoad_issuesNoPostPrepareSeekEvent() {
        val engine = engine()
        engine.load(request(startPositionMs = 0))
        drainMainThread() // drain load #1's internal batch before recording

        val reasons = mutableListOf<Int>()
        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                reasons += reason
            }
        }
        val player = checkNotNull(engine.media3Player)
        player.addListener(listener)
        try {
            engine.load(request(startPositionMs = 90_000))

            assertEquals("the fold must have carried the new position", 90_000L, player.currentPosition)
            drainMainThread()
            assertTrue(
                "a post-prepare seekTo is the only producer of DISCONTINUITY_REASON_SEEK — " +
                    "the folded setMediaItem(item, position) must never fire one (observed: $reasons)",
                Player.DISCONTINUITY_REASON_SEEK !in reasons,
            )
        } finally {
            player.removeListener(listener)
            engine.release()
        }
    }
}
