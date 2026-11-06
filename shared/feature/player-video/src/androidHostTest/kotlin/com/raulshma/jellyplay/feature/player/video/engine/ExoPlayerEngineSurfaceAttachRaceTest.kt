package com.raulshma.jellyplay.feature.player.video.engine

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raulshma.jellyplay.feature.player.video.subtitle.AndroidFontProvider
import okhttp3.OkHttpClient
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The createSurfaceView-before-load attach race (issue #186 follow-up: the
 * AUTO-profile black-video report).
 *
 * The session manager publishes the engine object BEFORE load() builds the
 * ExoPlayer — the desktop mpv wid contract needs the embed target mounted
 * first — so the screen's PlayerView factory can run while
 * `ExoPlayerEngine.player` is still null. When that happens, the factory
 * captures player=null, and load()'s "full teardown-as-reset" (which runs the
 * terminal release()) DISCARDED the just-created playerView — so the rebuilt
 * player had no view to attach to, PlayerView stayed player=null for the
 * engine's lifetime, media3 routed the decoded frames to a dummy surface, and
 * playback looked alive while the video was black: audio playing, position
 * advancing, zero frames composited. Any suspension before load() (the AUTO
 * resolve's drift-check probe on Dispatchers.Default, a slow DataStore read,
 * whatever comes next) deterministically widened the window enough for the
 * factory to lose the race; with no suspension at all the player won it and
 * the same device played fine — which is why only AUTO showed black.
 *
 * The invariant under test: whichever order the view and the player are built
 * in, the engine's PlayerView ends up attached to the engine's player —
 * createSurfaceView-before-load attaches the built player to the pre-existing
 * view (the load-time reset preserves the UI-owned view and re-attaches), and
 * load-before-createSurfaceView keeps the historical factory-side attach.
 */
@RunWith(AndroidJUnit4::class)
class ExoPlayerEngineSurfaceAttachRaceTest {

    private fun engine(): ExoPlayerEngine = ExoPlayerEngine(
        context = ApplicationProvider.getApplicationContext(),
        streamingOkHttpClient = OkHttpClient(),
        fontProvider = AndroidFontProvider(ApplicationProvider.getApplicationContext()),
    )

    private fun request() = PlaybackRequest(
        uri = "https://jellyfin.invalid/videos/1/stream.mp4",
        title = "attach-race regression",
    )

    @Test
    fun createSurfaceViewBeforeLoad_builtPlayerAttachesToThePreExistingView() {
        val engine = engine()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // The losing ordering: the screen composes the surface view the moment
        // the engine object is published — the player does not exist yet.
        val view = engine.createSurfaceView(context)
        assertNull("no player yet — the factory captures player=null", (view as? androidx.media3.ui.PlayerView)?.player)

        engine.load(request())

        val playerView = view as? androidx.media3.ui.PlayerView
        assertNotNull("the view must be a PlayerView on Android", playerView)
        assertNotNull(
            "the player load() built must attach to the view the screen already composed — " +
                "a player-less PlayerView renders black with a running decoder",
            playerView?.player,
        )

        engine.release()
        assertNull("release still tears the player off the view", playerView?.player)
    }

    @Test
    fun loadBeforeCreateSurfaceView_factoryAttachesTheLivePlayer() {
        val engine = engine()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // The winning ordering: load() builds the player before the screen
        // composes the view — the historical behavior that always worked.
        engine.load(request())
        val view = engine.createSurfaceView(context) as androidx.media3.ui.PlayerView
        assertNotNull("the factory must capture the already-built player", view.player)

        engine.release()
    }
}
