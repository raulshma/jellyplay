package com.raulshma.jellyplay.core.data.playback

import android.content.Intent
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Pins [PlayerAudioLifecycle]'s two audio-lifecycle concerns:
 *
 * **Audio focus** (driven through the shadow AudioManager's captured listener):
 * - Permanent loss (LOSS) pauses the engine, resets transient bookkeeping and
 *   abandons focus.
 * - Transient loss ducks the engine to 0.2× but only after capturing the
 *   *unclamped* pre-duck volume (a VLC >1.0 volume must survive the cycle);
 *   when muted nothing is captured and volume stays down.
 * - GAIN restores the pre-duck volume — or re-asserts mute when muted (a
 *   duck-while-muted cycle must never leak audio) — fires the onRegain hook,
 *   resumes if the engine was playing, and clears the transient state.
 * - Registration is idempotent.
 *
 * **Becoming-noisy**: the ACTION_AUDIO_BECOMING_NOISY broadcast pauses the
 * current engine; other broadcasts don't.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerAudioLifecycleTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** The "current engine", swapped like the real ViewModels do. */
    private var engine: PlayerAudioLifecycle.PlaybackControl? = null

    private val recorded = mutableListOf<String>()
    private var enginePlaying = true
    private var engineVolume = 1.5f // >1.0: VLC-style boost that ducking must not clamp away
    private var engineMuted = false
    private var regainHooks = 0

    @Before
    fun setUp() {
        enginePlaying = true
        engineVolume = 1.5f
        engineMuted = false
        regainHooks = 0
        recorded.clear()
        engine = PlayerAudioLifecycle.PlaybackControl(
            isPlaying = { enginePlaying },
            volume = { engineVolume },
            pause = { recorded += "pause"; enginePlaying = false },
            play = { recorded += "play"; enginePlaying = true },
            setVolume = { recorded += "volume:${it}"; engineVolume = it },
            setMuted = { recorded += "mute:${it}"; engineMuted = it },
        )
    }

    private fun lifecycle(onRegain: (() -> Unit)? = null) = PlayerAudioLifecycle(
        context = context,
        control = { engine },
        isMuted = { engineMuted },
        onRegain = onRegain,
    )

    private fun focusListenerOf(lifecycle: PlayerAudioLifecycle): AudioManager.OnAudioFocusChangeListener {
        lifecycle.registerAudioFocus()
        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
        return shadowOf(audioManager).lastAudioFocusRequest.listener
    }

    @Test
    fun `re-registering after a permanent loss re-arms the focus request`() {
        val lifecycle = lifecycle()
        val listener = focusListenerOf(lifecycle)

        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        assertFalse(lifecycle.isAudioFocusActive())

        lifecycle.registerAudioFocus()
        assertTrue(lifecycle.isAudioFocusActive())
        lifecycle.unregisterAudioFocus()
    }

    @Test
    fun `isAudioFocusActive reflects registration and abandonment`() {
        val lifecycle = lifecycle()

        assertFalse(lifecycle.isAudioFocusActive())
        lifecycle.registerAudioFocus()
        assertTrue(lifecycle.isAudioFocusActive())

        lifecycle.unregisterAudioFocus()
        assertFalse(lifecycle.isAudioFocusActive())

        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
        assertNotNull(shadowOf(audioManager).lastAbandonedAudioFocusRequest)
    }

    @Test
    fun `permanent loss pauses and abandons focus`() {
        val lifecycle = lifecycle()
        val listener = focusListenerOf(lifecycle)

        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)

        assertTrue(recorded.contains("pause"))
        assertFalse(lifecycle.isAudioFocusActive())
        assertFalse(enginePlaying)
    }

    @Test
    fun `transient loss ducks to 20 percent and gain restores the unclamped volume and resumes`() {
        var regainCalls = 0
        val lifecycle = lifecycle(onRegain = { regainCalls++ })
        val listener = focusListenerOf(lifecycle)

        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertEquals(0.2f, engineVolume)

        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        assertEquals(1.5f, engineVolume) // unclamped restore
        assertTrue(enginePlaying) // resumed
        assertEquals(1, regainCalls)
    }

    @Test
    fun `can-duck transient loss captures the volume exactly once per cycle`() {
        val lifecycle = lifecycle()
        val listener = focusListenerOf(lifecycle)

        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)

        // The second transient loss must not overwrite the captured pre-duck volume.
        assertEquals(0.2f, engineVolume)

        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(1.5f, engineVolume)
    }

    @Test
    fun `a duck-while-muted cycle never leaks audio past the regain`() {
        engineMuted = true
        engineVolume = 0f
        val lifecycle = lifecycle()
        val listener = focusListenerOf(lifecycle)

        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertEquals(0f, engineVolume) // muted: no duck volume applied

        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        // Mute is re-asserted instead of restoring a volume — no audio leaks.
        assertTrue(recorded.contains("mute:true"))
        assertEquals(0f, engineVolume)
    }

    @Test
    fun `gain without a prior transient loss still fires the regain hook`() {
        var regainCalls = 0
        val lifecycle = lifecycle(onRegain = { regainCalls++ })
        val listener = focusListenerOf(lifecycle)

        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        assertEquals(1, regainCalls)
    }

    @Test
    fun `release before any register is safe`() {
        val lifecycle = lifecycle()

        lifecycle.release()

        assertFalse(lifecycle.isAudioFocusActive())
    }

    @Test
    fun `the becoming-noisy broadcast pauses the current engine`() {
        val lifecycle = lifecycle()
        lifecycle.registerBecomingNoisy()

        context.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue(recorded.contains("pause"))

        // An unrelated broadcast must not pause.
        recorded.clear()
        context.sendBroadcast(Intent("some.other.ACTION"))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(recorded.contains("pause"))

        lifecycle.release()
    }

    @Test
    fun `registerBecomingNoisy is idempotent`() {
        val lifecycle = lifecycle()
        lifecycle.registerBecomingNoisy()
        lifecycle.registerBecomingNoisy()

        context.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        shadowOf(android.os.Looper.getMainLooper()).idle()

        // Exactly one pause: a duplicate receiver would record a second.
        assertEquals(1, recorded.count { it == "pause" })
        lifecycle.release()
    }
}
