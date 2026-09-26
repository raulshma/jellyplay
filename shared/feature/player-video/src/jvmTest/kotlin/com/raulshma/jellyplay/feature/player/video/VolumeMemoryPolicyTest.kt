package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.PlatformKind
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackVolumePolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Matrix tests for the volume-memory ladder: the apply gates
 * (platform / toggle / stored level / engine presence) and the capture gates
 * (user vs programmatic changes at the PlaybackVolumePolicy seam).
 */
class VolumeMemoryPolicyTest {

    // ── restoreLevel (apply at item start) ─────────────────────────────────

    @Test
    fun `desktop restores the remembered level when enabled`() {
        assertEquals(
            0.7f,
            VolumeMemoryPolicy.restoreLevel(
                platform = PlatformKind.DESKTOP,
                rememberEnabled = true,
                storedLevel = 0.7f,
                enginePresent = true,
            ),
        )
    }

    @Test
    fun `android never restores - the system stream owns video volume`() {
        assertNull(
            VolumeMemoryPolicy.restoreLevel(
                platform = PlatformKind.ANDROID,
                rememberEnabled = true,
                storedLevel = 0.7f,
                enginePresent = true,
            ),
        )
    }

    @Test
    fun `toggle off never restores`() {
        assertNull(
            VolumeMemoryPolicy.restoreLevel(
                platform = PlatformKind.DESKTOP,
                rememberEnabled = false,
                storedLevel = 0.7f,
                enginePresent = true,
            ),
        )
    }

    @Test
    fun `no stored level restores nothing - the engine default stands`() {
        assertNull(
            VolumeMemoryPolicy.restoreLevel(
                platform = PlatformKind.DESKTOP,
                rememberEnabled = true,
                storedLevel = null,
                enginePresent = true,
            ),
        )
    }

    @Test
    fun `no engine restores nothing`() {
        assertNull(
            VolumeMemoryPolicy.restoreLevel(
                platform = PlatformKind.DESKTOP,
                rememberEnabled = true,
                storedLevel = 0.7f,
                enginePresent = false,
            ),
        )
    }

    // ── shouldCapture (write on user changes) ──────────────────────────────

    @Test
    fun `capture is desktop-only and toggle-gated`() {
        assertTrue(VolumeMemoryPolicy.shouldCapture(PlatformKind.DESKTOP, rememberEnabled = true))
        assertFalse(VolumeMemoryPolicy.shouldCapture(PlatformKind.DESKTOP, rememberEnabled = false))
        assertFalse(VolumeMemoryPolicy.shouldCapture(PlatformKind.ANDROID, rememberEnabled = true))
    }

    // ── the user-vs-programmatic seam (PlaybackVolumePolicy) ────────────────

    @Test
    fun `user changes carry isUserChange true through the plan`() {
        val plan = PlaybackVolumePolicy.planLevel(0.6f, PlaybackVolumePolicy.MAX_BOOST_NOMINAL)
        assertTrue(plan.isUserChange, "default call sites are user-shaped")
        assertEquals(0.6f, plan.normalized)
    }

    @Test
    fun `programmatic fades carry isUserChange false - they must not overwrite memory`() {
        val fade = PlaybackVolumePolicy.planLevel(
            0.1f,
            PlaybackVolumePolicy.MAX_BOOST_NOMINAL,
            isUserChange = false,
        )
        assertFalse(fade.isUserChange)
        assertEquals(0.1f, fade.normalized)
    }

    @Test
    fun `the flag never alters the clamping decisions`() {
        val user = PlaybackVolumePolicy.planLevel(1.5f, PlaybackVolumePolicy.MAX_BOOST_NOMINAL)
        val programmatic = PlaybackVolumePolicy.planLevel(
            1.5f,
            PlaybackVolumePolicy.MAX_BOOST_NOMINAL,
            isUserChange = false,
        )
        assertEquals(user.normalized, programmatic.normalized)
        assertEquals(user.systemStream, programmatic.systemStream)
    }
}
