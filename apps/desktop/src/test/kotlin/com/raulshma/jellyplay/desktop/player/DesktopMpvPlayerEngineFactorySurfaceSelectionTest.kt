package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.desktop.player.EngineActivitySnapshot.Companion.SURFACE_HWND
import com.raulshma.jellyplay.desktop.player.EngineActivitySnapshot.Companion.SURFACE_NO_OP
import com.raulshma.jellyplay.desktop.player.EngineActivitySnapshot.Companion.SURFACE_SOFTWARE
import com.raulshma.jellyplay.desktop.player.EngineActivitySnapshot.Companion.SURFACE_WID_NULL
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib
import com.raulshma.jellyplay.feature.player.video.DesktopVideoSurfaceBridge
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Surface-selection contract of [DesktopMpvPlayerEngineFactory] (wave 14B):
 * which engine — and which [EngineActivitySnapshot] surface tag — a session
 * gets for each bridge state. The bridge is a global object, so every test
 * installs its provider/probe and removes them again in a finally block.
 *
 * The "HWND never appears" tests cost the factory's full 4 s wait budget on
 * Windows (the budget under test); they are kept to the two fallback branches
 * only. The wid-engine tests need a real libmpv (the engine ctor initializes
 * a core) and skip without tools/mpv — same gating as MpvDesktopEngineTest.
 * The fake HWND value is never dereferenced by these tests: mpv only opens
 * the wid target when media loads, which no test here does.
 */
class DesktopMpvPlayerEngineFactorySurfaceSelectionTest {

    private fun libmpvAvailable(): Boolean = try {
        MpvLib.mpv
        true
    } catch (_: Throwable) {
        false
    }

    /** Installs [probe], restoring the bridge's probe-free state afterwards. */
    private suspend fun withSoftwareProbe(probe: () -> Boolean, block: suspend () -> Unit) {
        DesktopVideoSurfaceBridge.registerSoftwareSurfaceProbe(probe)
        try {
            block()
        } finally {
            DesktopVideoSurfaceBridge.registerSoftwareSurfaceProbe(null)
        }
    }

    @Test
    fun external_selectsNoOp(): Unit = runBlocking {
        val recorder = EngineActivityRecorder()
        try {
            val factory = DesktopMpvPlayerEngineFactory(recorder = recorder)
            val engine = factory.create(PlayerType.EXTERNAL)
            assertEquals(SURFACE_NO_OP, recorder.latest().surface)
            assertIs<MediaEngine>(engine)
        } finally {
            recorder.dispose()
        }
    }

    @Test
    fun hwndAvailable_selectsWidEngine() = runBlocking {
        assumeTrue(DesktopVideoSurfaceBridge.isWindowsVideoSurfaceSupported)
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val recorder = EngineActivityRecorder()
        val provider: () -> Long? = { 0x12345678L }
        DesktopVideoSurfaceBridge.register(provider)
        try {
            val factory = DesktopMpvPlayerEngineFactory(recorder = recorder)
            val engine = factory.create(PlayerType.MPV)
            assertIs<MpvDesktopEngine>(engine)
            assertEquals(SURFACE_HWND, recorder.latestVideoEngine().surface)
        } finally {
            DesktopVideoSurfaceBridge.clear(provider)
            recorder.dispose()
        }
    }

    @Test
    fun hwndPublishedDuringWait_selectsWidEngineBeforeBudgetExpires() = runBlocking {
        assumeTrue(DesktopVideoSurfaceBridge.isWindowsVideoSurfaceSupported)
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val recorder = EngineActivityRecorder()
        // The session pipeline reality this pins: the surface publishes a few
        // frames AFTER the factory started waiting (pre-engine host compose
        // vs the loadMedia resolve work ahead of create()).
        val calls = AtomicInteger(0)
        val provider: () -> Long? = {
            if (calls.incrementAndGet() >= 5) 0x12345678L else null
        }
        DesktopVideoSurfaceBridge.register(provider)
        try {
            val startedAt = System.currentTimeMillis()
            val factory = DesktopMpvPlayerEngineFactory(recorder = recorder)
            val engine = factory.create(PlayerType.MPV)
            val elapsedMs = System.currentTimeMillis() - startedAt
            assertIs<MpvDesktopEngine>(engine)
            assertEquals(SURFACE_HWND, recorder.latestVideoEngine().surface)
            // 5 provider hits at the 16 ms poll cadence — nowhere near the
            // 4 s budget. (Generous ceiling: CI jitter must not flake this.)
            assertTrue(elapsedMs < 3_000, "waited ${elapsedMs}ms — expected a quick hit")
        } finally {
            DesktopVideoSurfaceBridge.clear(provider)
            recorder.dispose()
        }
    }

    @Test
    fun noHwnd_andSoftwareSurfaceSupported_selectsSoftwareEngine() = runBlocking {
        val recorder = EngineActivityRecorder()
        try {
            withSoftwareProbe(probe = { true }) {
                val factory = DesktopMpvPlayerEngineFactory(recorder = recorder)
                val engine = factory.create(PlayerType.MPV)
                assertIs<MpvSoftwareRenderEngine>(engine)
                assertEquals(SURFACE_SOFTWARE, recorder.latestVideoEngine().surface)
            }
        } finally {
            recorder.dispose()
        }
    }

    @Test
    fun noHwnd_andSoftwareProbeFailing_degradesToWidNullEngine() = runBlocking {
        val recorder = EngineActivityRecorder()
        try {
            withSoftwareProbe(probe = { false }) {
                val factory = DesktopMpvPlayerEngineFactory(recorder = recorder)
                val engine = factory.create(PlayerType.MPV)
                // The pre-12B degrade is the same class as the wid engine —
                // the recorder's surface tag is what tells the branches apart.
                assertIs<MpvDesktopEngine>(engine)
                assertEquals(SURFACE_WID_NULL, recorder.latestVideoEngine().surface)
            }
        } finally {
            recorder.dispose()
        }
    }
}
