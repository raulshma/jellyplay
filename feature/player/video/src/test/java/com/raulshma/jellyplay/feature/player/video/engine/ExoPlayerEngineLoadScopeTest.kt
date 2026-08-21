package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression: 247e2715c restructured [ExoPlayerEngine.load] so the rebuild path
 * calls `release()` AFTER `recreateEngineScopeIfInactive()` — and `release()`
 * cancels `engineScope`. load() then returned with a cancelled scope, so the
 * `EnginePositionTicker` launched by `positionFlow` (on `engineScope`) never
 * executed its loop: the seek bar froze at the resume position while playback
 * ran (one seed emission, then silence). load() must leave the scope live.
 */
@RunWith(RobolectricTestRunner::class)
class ExoPlayerEngineLoadScopeTest {

    @Test
    fun load_rebuildPath_leavesEngineScopeActive() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = ExoPlayerEngine(
            context = context,
            streamingOkHttpClient = OkHttpClient(),
            fontProvider = FontProvider(context),
        )

        // First load on a fresh engine: `player == null` skips the reuse fast
        // path and takes the release()+rebuild path — the exact path that
        // returned with a cancelled scope.
        engine.load(
            PlaybackRequest(
                uri = "https://example.com/video.mp4",
                title = "scope-regression",
                serverUrl = "https://example.com",
            )
        )

        val scopeField = BasePlayerEngine::class.java.getDeclaredField("engineScope")
        scopeField.isAccessible = true
        val scope = scopeField.get(engine) as CoroutineScope
        assertTrue(
            "load() rebuild path must leave engineScope active — a cancelled scope " +
                "kills the positionFlow ticker and freezes the seek bar",
            scope.isActive,
        )
    }
}
