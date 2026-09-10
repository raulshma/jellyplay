package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.Visualizer
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * Pins [AudioVisualizerHelper]'s data-capture invariants:
 *
 * - `attach(UNSET)` never opens a Visualizer; attaching the same session twice
 *   is idempotent (one listener registration); `detach` releases the effect,
 *   clears the flows, and the helper re-attaches cleanly afterwards.
 * - Capture callbacks are TIME-THROTTLED first (33 ms window): frames arriving
 *   inside the window never reach the flows even when their content differs —
 *   the ordering regression the throttle-first fix pinned; after the window a
 *   changed frame is published, per stream (waveform / FFT independent).
 * - `setEnabled(false)` publishes empty arrays to both flows; `detach` does the
 *   same and releases the underlying Visualizer exactly once.
 *
 * The `Visualizer` is supplied through the helper's `visualizerFactory`
 * constructor seam as a plain mock: `mockkConstructor` on Robolectric-shadowed
 * framework classes intercepts unreliably (shadowed native methods such as
 * `release()`/`setEnabled` bypass the mock, and `int`-returning setters clash
 * with `just runs` stubs).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioVisualizerHelperTest {

    private var captureListener: Visualizer.OnDataCaptureListener? = null

    private fun visualizer(): Visualizer {
        val fx = mockk<Visualizer>(relaxed = true)
        every { fx.setCaptureSize(any()) } answers { 0 }
        every { fx.setDataCaptureListener(any(), any(), any(), any()) } answers {
            captureListener = firstArg()
            0
        }
        every { fx.setEnabled(any()) } answers { 0 }
        every { fx.release() } just runs
        return fx
    }

    @Before
    fun setUp() {
        mockkStatic(Visualizer::class)
        every { Visualizer.getCaptureSizeRange() } returns intArrayOf(128, 1024)
        every { Visualizer.getMaxCaptureRate() } returns 20_000
    }

    @After
    fun tearDown() {
        unmockkAll()
        captureListener = null
    }

    private fun helper(fx: Visualizer) = AudioVisualizerHelper(visualizerFactory = { fx })

    private fun waveform(bytes: ByteArray) =
        captureListener?.onWaveFormDataCapture(null, bytes, 1)

    private fun fft(bytes: ByteArray) =
        captureListener?.onFftDataCapture(null, bytes, 1)

    private fun advanceMs(ms: Long) = ShadowSystemClock.advanceBy(Duration.ofMillis(ms))

    @Test
    fun `attach with UNSET session never registers a listener`() {
        val fx = visualizer()
        val h = helper(fx)

        h.attach(androidx.media3.common.C.AUDIO_SESSION_ID_UNSET)

        assertTrue(captureListener == null)
    }

    @Test
    fun `attach registers one listener at the minimum capture size`() {
        val fx = visualizer()
        val h = helper(fx)

        h.attach(audioSessionId = 42)

        assertNotNull(captureListener)
        verify(exactly = 1) { fx.setCaptureSize(128) }
        verify(exactly = 1) { fx.setDataCaptureListener(any(), any(), true, true) }

        h.attach(audioSessionId = 42)
        verify(exactly = 1) { fx.setDataCaptureListener(any(), any(), true, true) }
    }

    @Test
    fun `waveform frames inside the throttle window are dropped even when changed`() {
        val fx = visualizer()
        val h = helper(fx)
        h.attach(audioSessionId = 42)
        advanceMs(40)

        val first = byteArrayOf(1, 2, 3)
        waveform(first)
        assertArrayEquals(first, h.waveformData.value)

        // 10 ms later, different content — still inside the 33 ms window.
        advanceMs(10)
        val second = byteArrayOf(9, 9, 9)
        waveform(second)

        assertArrayEquals("throttled frame must not reach the flow", first, h.waveformData.value)

        // Past the window, changed content is published.
        advanceMs(40)
        waveform(second)
        assertArrayEquals(second, h.waveformData.value)
    }

    @Test
    fun `fft frames throttle independently of waveform frames`() {
        val fx = visualizer()
        val h = helper(fx)
        h.attach(audioSessionId = 42)
        advanceMs(40)

        val wave = byteArrayOf(1)
        waveform(wave)

        // Fresh stream: the FFT throttle window has not started yet.
        val fftFirst = byteArrayOf(4, 5)
        fft(fftFirst)
        assertArrayEquals(fftFirst, h.fftData.value)
        assertArrayEquals(wave, h.waveformData.value)

        advanceMs(10)
        val fftSecond = byteArrayOf(7)
        fft(fftSecond)
        assertArrayEquals(fftFirst, h.fftData.value)

        advanceMs(40)
        fft(fftSecond)
        assertArrayEquals(fftSecond, h.fftData.value)
    }

    @Test
    fun `null capture payloads are ignored`() {
        val fx = visualizer()
        val h = helper(fx)
        h.attach(audioSessionId = 42)
        advanceMs(40)

        captureListener?.onWaveFormDataCapture(null, null, 1)
        captureListener?.onFftDataCapture(null, null, 1)

        assertTrue(h.waveformData.value.isEmpty())
        assertTrue(h.fftData.value.isEmpty())
    }

    @Test
    fun `setEnabled false clears both flows`() {
        val fx = visualizer()
        val h = helper(fx)
        h.attach(audioSessionId = 42)
        advanceMs(40)
        waveform(byteArrayOf(1))
        fft(byteArrayOf(2))

        h.setEnabled(false)

        assertTrue(h.waveformData.value.isEmpty())
        assertTrue(h.fftData.value.isEmpty())
        // attach applied isEnabled=false, then the explicit disable — 2 total.
        verify(exactly = 2) { fx.setEnabled(false) }
    }

    @Test
    fun `detach releases the visualizer and clears the flows, then re-attach works`() {
        val fx = visualizer()
        val h = helper(fx)
        h.attach(audioSessionId = 42)
        advanceMs(40)
        waveform(byteArrayOf(1))

        h.detach()

        verify(exactly = 1) { fx.release() }
        assertTrue(h.waveformData.value.isEmpty())
        assertTrue(h.fftData.value.isEmpty())

        h.attach(audioSessionId = 43)
        advanceMs(40)
        val fresh = byteArrayOf(8)
        waveform(fresh)
        assertArrayEquals(fresh, h.waveformData.value)
    }
}
