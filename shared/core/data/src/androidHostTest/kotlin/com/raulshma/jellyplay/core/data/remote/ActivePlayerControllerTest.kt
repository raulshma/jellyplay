package com.raulshma.jellyplay.core.data.remote

import com.raulshma.jellyplay.core.model.TrackType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the [ActivePlayerController] identity invariant: `unbindEngine` clears
 * the active engine **only when handed the same instance that is currently
 * bound** — a stale/unrelated engine instance (e.g. from a recreated player
 * ViewModel) must never clear a newer binding. `clearEngine` clears
 * unconditionally. The registry holds exactly one engine or null, and
 * [ActivePlayerController.engine] always mirrors [ActivePlayerController.activeEngine].
 *
 * Hand-written fake engines (no MockK) so `===` identity semantics are exact.
 */
class ActivePlayerControllerTest {

    private class FakeEngine(
        name: String,
        initialPlaying: Boolean = false,
    ) : RemotePlayableEngine {
        val name = name
        override val currentPositionMs: Long = 0L
        override val isPlaying: MutableStateFlow<Boolean> = MutableStateFlow(initialPlaying)
        override val underlyingPlayer: Any? = null

        // Backing field + val override: a `var` would generate a JVM
        // setVolume(Float) clashing with the interface method.
        private var volumeField: Float = 1f
        override val volume: Float get() = volumeField

        override fun play() { isPlaying.value = true }
        override fun pause() { isPlaying.value = false }
        override fun stop() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun selectTrack(type: TrackType, index: Int) = Unit
        override fun setMaxVideoBitrate(bps: Int?) = Unit
        override fun setVolume(value: Float) { volumeField = value }
        override fun increaseVolume(delta: Float) { volumeField += delta }
        override fun decreaseVolume(delta: Float) { volumeField -= delta }
        override fun setMuted(muted: Boolean) = Unit
        override fun release() = Unit
    }

    private val controller = ActivePlayerController()

    @Test
    fun `starts with no engine bound`() {
        assertNull(controller.engine)
        assertNull(controller.activeEngine.value)
    }

    @Test
    fun `bindEngine exposes engine via property and flow`() {
        val engine = FakeEngine("first")

        controller.bindEngine(engine)

        assertSame(engine, controller.engine)
        assertSame(engine, controller.activeEngine.value)
    }

    @Test
    fun `rebinding replaces the engine`() {
        val first = FakeEngine("first")
        val second = FakeEngine("second")

        controller.bindEngine(first)
        controller.bindEngine(second)

        assertSame(second, controller.engine)
        assertNotSame(first, controller.activeEngine.value)
    }

    @Test
    fun `unbindEngine with the bound instance clears the registry`() {
        val engine = FakeEngine("bound")
        controller.bindEngine(engine)

        controller.unbindEngine(engine)

        assertNull(controller.engine)
        assertNull(controller.activeEngine.value)
    }

    @Test
    fun `unbindEngine with a different instance does not clear the bound engine`() {
        val bound = FakeEngine("bound")
        val stale = FakeEngine("stale")
        controller.bindEngine(bound)

        controller.unbindEngine(stale)

        // Identity invariant: a stale engine instance must never clear a
        // newer binding.
        assertSame(bound, controller.engine)
        assertSame(bound, controller.activeEngine.value)
    }

    @Test
    fun `unbindEngine with no binding is a safe no-op`() {
        val engine = FakeEngine("never-bound")

        controller.unbindEngine(engine)

        assertNull(controller.engine)
    }

    @Test
    fun `clearEngine clears unconditionally even with an engine bound`() {
        val engine = FakeEngine("bound")
        controller.bindEngine(engine)

        controller.clearEngine()

        assertNull(controller.engine)
    }

    @Test
    fun `clearEngine after unbind keeps the registry empty`() {
        val engine = FakeEngine("bound")
        controller.bindEngine(engine)
        controller.unbindEngine(engine)

        controller.clearEngine()

        assertNull(controller.engine)
    }

    @Test
    fun `fake engine behaviour is observable through the bound reference`() {
        val engine = FakeEngine("bound", initialPlaying = false)
        controller.bindEngine(engine)

        engine.play()
        assertTrue(controller.engine!!.isPlaying.value)
        engine.setVolume(0.5f)
        assertEquals(0.5f, controller.engine!!.volume, 0.001f)

        controller.clearEngine()
        assertNotNull(engine)
        assertFalse(controller.activeEngine.value === engine)
    }}
