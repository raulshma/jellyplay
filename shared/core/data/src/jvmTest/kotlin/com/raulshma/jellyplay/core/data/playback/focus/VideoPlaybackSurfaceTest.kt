package com.raulshma.jellyplay.core.data.playback.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the [VideoPlaybackSurface] duck/restore mechanics — the deleted
 * `PlayerAudioLifecycle` transient-loss body, moved with the behavior. The
 * executor tests above pin WHEN the commands fire; this suite pins WHAT the
 * surface does to its target (volume capture, muted guards, programmatic
 * setVolume flag, the restore hook).
 */
class VideoPlaybackSurfaceTest {

    /** Recording [FocusCommandTarget] — the legacy PlaybackControl shape. */
    private class FakeTarget : FocusCommandTarget {
        private var muted = false
        override var isPlaying: Boolean = true
        override var volume: Float = 1f
        override val isMuted: Boolean get() = muted
        var pauses = 0
        var lastVolumeSet: Pair<Float, Boolean>? = null
        var muteAssertions = 0
        override fun pause() {
            pauses++
            isPlaying = false
        }
        override fun setMuted(muted: Boolean) {
            if (muted) muteAssertions++
            this.muted = muted
        }
        override fun setVolume(volume: Float, isUserChange: Boolean) {
            lastVolumeSet = volume to isUserChange
            this.volume = volume
        }
    }

    @Test
    fun `duck captures the pre-duck volume and sets the duck level programmatically`() {
        val surface = VideoPlaybackSurface()
        val target = FakeTarget().apply { volume = 0.8f }
        surface.bind(target = { target })

        surface.duck(0.2f)

        assertEquals(0.2f to false, target.lastVolumeSet, "ducking is not a user volume choice — volume memory must not capture it")
        assertEquals(0.2f, target.volume, "the target sits at the duck level; the pre-duck 0.8 is the surface's bookkeeping")
    }

    @Test
    fun `duck while muted touches nothing - muted stays at zero`() {
        val surface = VideoPlaybackSurface()
        val target = FakeTarget().apply { setMuted(true); volume = 0f }
        surface.bind(target = { target })

        surface.duck(0.2f)

        assertNull(target.lastVolumeSet, "no volume write while muted: the capture would clobber the real level on restore")
        assertEquals(0f, target.volume)
    }

    @Test
    fun `restore puts the pre-duck volume back and fires the restore hook`() {
        val surface = VideoPlaybackSurface()
        val target = FakeTarget().apply { volume = 0.8f }
        var restores = 0
        surface.bind(target = { target }, onRestore = { restores++ })

        surface.duck(0.2f)
        surface.restore()

        assertEquals(0.8f to false, target.lastVolumeSet)
        assertEquals(1, restores, "the VOD wiring's resume-skip rides exactly here (the legacy focus-regain hook)")
    }

    @Test
    fun `restore while muted re-asserts mute instead of leaking the duck level`() {
        val surface = VideoPlaybackSurface()
        val target = FakeTarget().apply { volume = 0.8f }
        surface.bind(target = { target })
        surface.duck(0.2f)
        target.setMuted(true) // muted while ducked (the phone-call case)
        val muteAssertionsBeforeRestore = target.muteAssertions

        surface.restore()

        assertEquals(0.2f to false, target.lastVolumeSet, "no restore write happened — the last volume write is still the duck's")
        assertEquals(
            muteAssertionsBeforeRestore + 1,
            target.muteAssertions,
            "mute is re-asserted so a duck-while-muted cycle never leaks audio",
        )
    }

    @Test
    fun `pause closes the duck round-trip before pausing`() {
        val surface = VideoPlaybackSurface()
        val target = FakeTarget().apply { volume = 0.8f }
        surface.bind(target = { target })
        surface.duck(0.2f)

        surface.pause()

        assertEquals(0.8f to false, target.lastVolumeSet, "the user's resume after the pause must not play at the duck level")
        assertEquals(1, target.pauses)
        assertFalse(target.isPlaying)
    }

    @Test
    fun `commands are no-ops when unbound or the target is gone`() {
        val surface = VideoPlaybackSurface()
        surface.pause()
        surface.duck(0.2f)
        surface.restore()

        var reads = 0
        surface.bind(target = { reads++; null })
        surface.pause()
        surface.duck(0.2f)
        surface.restore()
        assertEquals(3, reads, "the provider is consulted per command (re-read-on-every-callback contract)")

        surface.unbind()
        reads = 0
        surface.pause()
        surface.duck(0.2f)
        surface.restore()
        assertEquals(0, reads, "after unbind nothing is consulted — screen teardown")
        assertTrue(true)
    }
}
