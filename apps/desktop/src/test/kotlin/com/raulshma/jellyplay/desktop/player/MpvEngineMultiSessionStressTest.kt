package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.desktop.player.mpv.MpvLib
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Multi-session reuse stress for the desktop engine lifecycle — the spike doc's
 * "factory reusing engines across navigation" case
 * (docs/spikes/x-desktop-video-surface-story.md, sw-render surface story):
 * each navigation creates a fresh engine through the factory and the old one
 * must release fully enough that the next context initializes cleanly. N
 * sequential create → load → READY → stop → release cycles over BOTH engine
 * variants (plain headless + the sw render path with its real `vo=libmpv`
 * render backend, ao=null for CI silence) — a leaked context, waiter thread,
 * or half-destroyed handle surfaces as a later session failing to reach READY
 * or a native crash, which is exactly the failure class this pin exists for.
 * Bounded: 6 sessions per variant, short tone, bounded waits. Skips on
 * machines without libmpv (real-engine test discipline).
 */
class MpvEngineMultiSessionStressTest {

    private fun libmpvAvailable(): Boolean = try {
        MpvLib.mpv
        true
    } catch (_: Throwable) {
        false
    }

    private fun swRendererAvailable(): Boolean = try {
        val probe = MpvSoftwareRenderEngine(extraOptions = mapOf("ao" to "null"))
        probe.release()
        true
    } catch (_: Throwable) {
        false
    }

    @Test
    fun sequentialSessions_reachReadyAndReleaseCleanly_headlessEngine() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        runSessions(engineFactory = {
            MpvDesktopEngine(
                extraOptions = mapOf("vo" to "null", "ao" to "null"),
            )
        })
    }

    @Test
    fun sequentialSessions_reachReadyAndReleaseCleanly_swRenderEngine() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        assumeTrue(swRendererAvailable(), { "libmpv has no usable 'sw' render backend" })
        runSessions(engineFactory = {
            MpvSoftwareRenderEngine(extraOptions = mapOf("ao" to "null"))
        })
    }

    private fun runSessions(engineFactory: () -> MpvDesktopEngine, sessions: Int = 6) {
        repeat(sessions) { index ->
            val engine = engineFactory()
            try {
                engine.load(
                    PlaybackRequest(
                        uri = testMedia("test-tone.wav"),
                        title = "multi-session-$index",
                    ),
                )
                waitUntil(15_000, { "session $index READY (state=${engine.playbackState.value})" }) {
                    engine.playbackState.value == EnginePlaybackState.READY
                }
                engine.play()
                waitUntil(10_000) { engine.isPlaying.value }
                engine.stop()
                waitUntil(10_000) { engine.playbackState.value == EnginePlaybackState.IDLE }
            } finally {
                engine.release()
            }
        }
        // A context built AFTER the stress loop proves the last release did
        // not wedge the library (the leak class the spike doc called out).
        val postEngine = engineFactory()
        try {
            postEngine.load(
                PlaybackRequest(uri = testMedia("test-tone.wav"), title = "post-stress"),
            )
            waitUntil(15_000) { postEngine.playbackState.value == EnginePlaybackState.READY }
            assertTrue(postEngine.durationMs > 0)
        } finally {
            postEngine.release()
        }
        assertEquals(
            EnginePlaybackState.IDLE,
            postEngine.playbackState.value,
            "released post-stress engine must land back in IDLE",
        )
    }

    private fun waitUntil(timeoutMs: Long, describe: () -> String = { "condition" }, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        if (!condition()) throw AssertionError("${describe()} not met within ${timeoutMs}ms")
    }
}

private fun testMedia(name: String): String =
    java.io.File(
        java.util.Objects.requireNonNull(
            MpvEngineMultiSessionStressTest::class.java.classLoader.getResource("media/$name"),
        ) { "missing test media resource media/$name" }.toURI(),
    ).absolutePath
