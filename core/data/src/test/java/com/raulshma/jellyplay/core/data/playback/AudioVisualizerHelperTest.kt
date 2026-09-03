package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.Visualizer
import io.mockk.every
import io.mockk.just
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioVisualizerHelperTest {

    private var captureListener: Visualizer.OnDataCaptureListener? = null

    @Before
    fun setUp() {
        mockkStatic(Visualizer::class)
        every { Visualizer.getCaptureSizeRange() } returns intArrayOf(128, 1024)
        every { Visualizer.getMaxCaptureRate() } returns 20_000

        mockkConstructor(Visualizer::class)
        every { anyConstructed<Visualizer>().captureSize = any() } just runs
        every { anyConstructed<Visualizer>().setDataCaptureListener(any(), any(), any(), any()) } answers {
            captureListener = firstArg()
            0
        }
        every { anyConstructed<Visualizer>().enabled = any() } just runs
        every { anyConstructed<Visualizer>().release() } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun helper() = AudioVisualizerHelper()

    private fun waveform(bytes: ByteArray) =
        captureListener?.onWaveFormDataCapture(null, bytes, 1)

    private fun fft(bytes: ByteArray) =
        captureListener?.onFftDataCapture(null, bytes, 1)

    private fun advanceMs(ms: Long) = ShadowSystemClock.advanceBy(Duration.ofMillis(ms))

    @Test
    fun `attach with UNSET session never registers a listener`() {
        val h = helper()

        h.attach(androidx.media3.common.C.AUDIO_SESSION_ID_UNSET)

        assertTrue(captureListener == null)
    }

    @Test
    fun `attach registers one listener at the minimum capture size`() {
        val h = helper()

        h.attach(audioSessionId = 42)

        assertTrue(captureListener != null)
        verify(exactly = 1) { anyConstructed<Visualizer>().captureSize = 128 }
        verify(exactly = 1) { anyConstructed<Visualizer>().setDataCaptureListener(any(), any(), true, true) }

        h.attach(audioSessionId = 42)
        verify(exactly = 1) { anyConstructed<Visualizer>().setDataCaptureListener(any(), any(), true, true) }
    }

    @Test
    fun `waveform frames inside the throttle window are dropped even when changed`() {
        val h = helper()
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
        val h = helper()
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
        val h = helper()
        h.attach(audioSessionId = 42)
        advanceMs(40)

        captureListener?.onWaveFormDataCapture(null, null, 1)
        captureListener?.onFftDataCapture(null, null, 1)

        assertTrue(h.waveformData.value.isEmpty())
        assertTrue(h.fftData.value.isEmpty())
    }

    @Test
    fun `setEnabled(false) clears both flows`() {
        val h = helper()
        h.attach(audioSessionId = 42)
        advanceMs(40)
        waveform(byteArrayOf(1))
        fft(byteArrayOf(2))

        h.setEnabled(false)

        assertTrue(h.waveformData.value.isEmpty())
        assertTrue(h.fftData.value.isEmpty())
        verify(exactly = 1) { anyConstructed<Visualizer>().enabled = false }
    }

    @Test
    fun `detach releases the visualizer and clears the flows, then re-attach works`() {
        val h = helper()
        h.attach(audioSessionId = 42)
        advanceMs(40)
        waveform(byteArrayOf(1))

        h.detach()

        verify(exactly = 1) { anyConstructed<Visualizer>().release() }
        assertTrue(h.waveformData.value.isEmpty())
        assertTrue(h.fftData.value.isEmpty())

        h.attach(audioSessionId = 43)
        advanceMs(40)
        val fresh = byteArrayOf(8)
        waveform(fresh)
        assertArrayEquals(fresh, h.waveformData.value)
    }
}
