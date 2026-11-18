package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the volume-command chassis ([VolumeCommandChassis]) over a fake native
 * seam — no libmpv, no Android, a plain JVM construction of the arms the
 * desktop `MpvDesktopEngine` and Android's `ReloadablePlayerEngine` run:
 * containment wrapping (a dead engine runs nothing), the chassis-owned
 * unmute memory (audible-only remember, pre-mute snapshot semantics, the
 * release-time reset), the snapshot SOURCE divergence (Android's system
 * stream vs the desktop's own native level — the same chassis, different seam
 * data), and the full desktop mpv shape (alive-guard + LEAVE_UNCHANGED mute /
 * REMEMBERED_LEVEL unmute + cached-percent read + user capture) driven
 * end-to-end.
 *
 * The template choreography itself is pinned by [VolumeCommandTemplatesTest];
 * this suite pins what the chassis adds around it and how the hosts compose
 * it.
 */
class VolumeCommandChassisTest {

    /**
     * Records every seam call in order; applies change the fake native level.
     * The chassis-owned memory is deliberately INVISIBLE here — tests read it
     * back through `chassis.rememberedUnmuteLevel`, exactly what the mute
     * template does.
     */
    private class RecordingSeam(
        var nativeLevel: Float? = 0.5f,
        /**
         * The pre-mute snapshot SOURCE, set independently of the handle to
         * pin the Android shape (its snapshot reads the system stream, not
         * the native handle). `null` mirrors [nativeLevel] — the desktop
         * shape.
         */
        var snapshotLevel: Float? = null,
        override val volumeBoostCeiling: Float = PlaybackVolumePolicy.MAX_BOOST_NOMINAL,
        override val unmuteRestoreFloor: Float = PlaybackVolumePolicy.UNMUTE_FLOOR,
        override val muteTemplateEnabled: Boolean = true,
        var restoreOnMute: PlaybackVolumePolicy.NativeVolumeRestore =
            PlaybackVolumePolicy.NativeVolumeRestore.LEAVE_UNCHANGED,
        var restoreOnUnmute: PlaybackVolumePolicy.NativeVolumeRestore =
            PlaybackVolumePolicy.NativeVolumeRestore.REMEMBERED_LEVEL,
    ) : NativeVolumeSeam {
        val log = mutableListOf<String>()

        override fun readNativeVolume(): Float? = nativeLevel

        override fun preMuteSnapshotLevel(): Float? = snapshotLevel ?: nativeLevel

        override fun applyNativeVolume(normalized: Float) {
            log += "apply:$normalized"
            nativeLevel = normalized
        }

        override fun applyNativeMuteFlag(muted: Boolean) {
            log += "flag:$muted"
        }

        override fun syncSystemStream(normalized: Float) {
            log += "sync:$normalized"
        }

        override fun onUserVolumeChanged(level: Float) {
            log += "capture:$level"
        }

        override fun nativeVolumeRestore(muted: Boolean): PlaybackVolumePolicy.NativeVolumeRestore =
            if (muted) restoreOnMute else restoreOnUnmute
    }

    /**
     * The chassis as a real host wires it: an alive-guard dispatch whose
     * entries interleave with the seam log, proving the shell wraps the WHOLE
     * template run.
     */
    private fun chassis(seam: RecordingSeam, alive: Boolean = true): VolumeCommandChassis =
        VolumeCommandChassis(seam, dispatch = { command ->
            if (alive) {
                seam.log += "dispatch"
                command()
                seam.log += "released"
            }
        })

    // ── containment ──

    @Test
    fun setVolume_runsTheWholeTemplate_insideTheDispatchShell() {
        val seam = RecordingSeam()
        val chassis = chassis(seam)
        chassis.setVolume(0.7f, isUserChange = true)
        assertEquals(
            listOf("dispatch", "capture:0.7", "apply:0.7", "sync:0.7", "released"),
            seam.log,
        )
        assertEquals(0.7f, chassis.rememberedUnmuteLevel)
    }

    @Test
    fun deadEngine_runsNothing_onAllFourCommands() {
        val seam = RecordingSeam()
        val chassis = chassis(seam, alive = false)
        chassis.setVolume(0.7f, isUserChange = true)
        chassis.increaseVolume(0.1f)
        chassis.decreaseVolume(0.1f)
        chassis.setMuted(true)
        assertEquals(emptyList(), seam.log, "an aborted containment must skip every seam call")
        assertEquals(1f, chassis.rememberedUnmuteLevel, "the memory must not move on a dead engine")
        assertEquals(0.5f, seam.nativeLevel, "the fake handle must not move either")
    }

    @Test
    fun defaultDispatch_runsInline() {
        val seam = RecordingSeam()
        val chassis = VolumeCommandChassis(seam)
        chassis.setVolume(0.7f, isUserChange = true)
        assertEquals(listOf("capture:0.7", "apply:0.7", "sync:0.7"), seam.log)
    }

    // ── the unmute memory (chassis-owned) ──

    @Test
    fun memory_remembersAudibleLevelsOnly_andSurvivesAZeroingDelta() {
        val seam = RecordingSeam(nativeLevel = 0.5f)
        val chassis = chassis(seam)
        chassis.decreaseVolume(0.5f)
        assertEquals(1f, chassis.rememberedUnmuteLevel, "a zeroing delta must not overwrite the memory with silence")
    }

    @Test
    fun mute_snapshotsFromTheSeamSource_notFromTheNativeHandle() {
        // The Android shape: the snapshot source (the system stream) is data
        // on the seam and differs from the native handle — the chassis must
        // remember the SOURCE, not the handle level.
        val seam = RecordingSeam(nativeLevel = 0.4f, snapshotLevel = 0.6f)
        seam.restoreOnMute = PlaybackVolumePolicy.NativeVolumeRestore.ZERO
        val chassis = chassis(seam)
        chassis.setMuted(true)
        assertEquals(
            listOf("dispatch", "flag:true", "apply:0.0", "sync:0.0", "released"),
            seam.log,
        )
        assertEquals(0.6f, chassis.rememberedUnmuteLevel, "the snapshot source is the restore target")
    }

    @Test
    fun mute_snapshotsBeforeTheWrite_soTheMuteItselfIsNeverRemembered() {
        // The desktop shape: the snapshot mirrors the native level; with a
        // zeroing mute the pre-write level must win.
        val seam = RecordingSeam(nativeLevel = 0.4f)
        seam.restoreOnMute = PlaybackVolumePolicy.NativeVolumeRestore.ZERO
        val chassis = chassis(seam)
        chassis.setMuted(true)
        assertEquals(0.4f, chassis.rememberedUnmuteLevel)
    }

    @Test
    fun mute_anInaudibleSnapshot_overwritesNothing() {
        val seam = RecordingSeam(nativeLevel = 0.4f, snapshotLevel = 0f)
        val chassis = chassis(seam)
        chassis.setVolume(0.8f, isUserChange = true)
        chassis.setMuted(true)
        assertEquals(0.8f, chassis.rememberedUnmuteLevel)
    }

    @Test
    fun unmute_restoresTheLevelTheSnapshotRemembered() {
        val seam = RecordingSeam(nativeLevel = 0.6f)
        val chassis = chassis(seam)
        chassis.setVolume(0.6f, isUserChange = true)
        chassis.setMuted(true)
        chassis.setMuted(false)
        assertEquals(
            listOf(
                "dispatch", "capture:0.6", "apply:0.6", "sync:0.6", "released",
                "dispatch", "flag:true", "sync:0.0", "released",
                "dispatch", "flag:false", "apply:0.6", "sync:0.6", "released",
            ),
            seam.log,
        )
    }

    @Test
    fun resetUnmuteMemory_restoresNominal_afterARelease() {
        val seam = RecordingSeam()
        val chassis = chassis(seam)
        chassis.setVolume(0.3f, isUserChange = true)
        chassis.resetUnmuteMemory()
        chassis.setMuted(false)
        assertEquals(
            listOf(
                "dispatch", "capture:0.3", "apply:0.3", "sync:0.3", "released",
                "dispatch", "flag:false", "apply:1.0", "sync:1.0", "released",
            ),
            seam.log,
            "the reset must serve nominal loudness, not the released item's last level",
        )
    }

    // ── forwarded seam divergence data ──

    @Test
    fun boostCeiling_forwardedFromTheSeam() {
        val seam = RecordingSeam(volumeBoostCeiling = PlaybackVolumePolicy.MAX_BOOST_VLC)
        val chassis = chassis(seam)
        chassis.setVolume(2.5f, isUserChange = true)
        assertEquals(
            listOf("dispatch", "capture:2.0", "apply:2.0", "sync:1.0", "released"),
            seam.log,
        )
        assertEquals(2.0f, chassis.rememberedUnmuteLevel)
    }

    @Test
    fun unmuteFloor_forwardedFromTheSeam() {
        // The VOD default floors a silenced memory at the audible floor.
        val floored = RecordingSeam()
        val flooredChassis = chassis(floored)
        flooredChassis.setVolume(0.02f, isUserChange = true)
        flooredChassis.setMuted(false)
        assertTrue("apply:0.05" in floored.log, "the default floor applies: ${floored.log}")

        // A host restoring the EXACT pre-mute level declares 0f (the live
        // knob) — the same chassis, declared divergence data.
        val exact = RecordingSeam(unmuteRestoreFloor = 0f)
        val exactChassis = chassis(exact)
        exactChassis.setVolume(0.02f, isUserChange = true)
        exactChassis.setMuted(false)
        assertTrue("apply:0.02" in exact.log, "the declared floor applies: ${exact.log}")
    }

    @Test
    fun muteTemplateGate_forwardedFromTheSeam_flagRunsEitherWay() {
        val seam = RecordingSeam(muteTemplateEnabled = false)
        val chassis = chassis(seam)
        chassis.setMuted(true)
        assertEquals(listOf("dispatch", "flag:true", "released"), seam.log)
    }

    @Test
    fun deltaCommands_abortWithoutANativeHandle() {
        val seam = RecordingSeam(nativeLevel = null)
        val chassis = chassis(seam)
        chassis.increaseVolume(0.1f)
        chassis.decreaseVolume(0.1f)
        // The shell wraps each run; the null-handle abort happens INSIDE the
        // template, so no seam call fires.
        assertEquals(
            listOf("dispatch", "released", "dispatch", "released"),
            seam.log,
        )
    }

    @Test
    fun programmaticPlans_rememberButNeverCapture() {
        val seam = RecordingSeam()
        val chassis = chassis(seam)
        chassis.setVolume(0.7f, isUserChange = false)
        assertEquals(
            listOf("dispatch", "apply:0.7", "sync:0.7", "released"),
            seam.log,
            "a fade must move the level and the memory but never fire the capture",
        )
        assertEquals(0.7f, chassis.rememberedUnmuteLevel)
    }

    // ── the desktop mpv shape, constructible without libmpv ──

    /**
     * The [VolumeCommandChassis] wired exactly as `MpvDesktopEngine` wires it
     * — alive-guard over a released flag, the cached percent as the native
     * level, the real mute flag, LEAVE_UNCHANGED / REMEMBERED_LEVEL, snapshot
     * = own native level, user capture through the hook — driven through the
     * four commands like the engine is.
     */
    @Test
    fun desktopVolumeArms_constructibleAndBehaving_withoutLibmpv() {
        var alive = true
        var released = false
        var muteFlag: Boolean? = null
        var percent = 100.0
        val captures = mutableListOf<Float>()
        val seam = object : NativeVolumeSeam {
            override fun readNativeVolume(): Float? =
                if (alive) (percent / 100.0).toFloat() else null

            override fun applyNativeVolume(normalized: Float) {
                percent = normalized * 100.0
            }

            override fun applyNativeMuteFlag(muted: Boolean) {
                if (alive) muteFlag = muted
            }

            override fun preMuteSnapshotLevel(): Float? = readNativeVolume()

            override fun onUserVolumeChanged(level: Float) {
                captures += level
            }

            override fun nativeVolumeRestore(muted: Boolean): PlaybackVolumePolicy.NativeVolumeRestore =
                if (muted) PlaybackVolumePolicy.NativeVolumeRestore.LEAVE_UNCHANGED
                else PlaybackVolumePolicy.NativeVolumeRestore.REMEMBERED_LEVEL
        }
        val engine = VolumeCommandChassis(
            native = seam,
            dispatch = { command -> if (!released) command() },
        )

        // A dead engine runs nothing — the former `aliveCtx() ?: return`
        // guards, generalized as the dispatch shell.
        released = true
        engine.setVolume(0.5f, isUserChange = true)
        engine.increaseVolume(0.1f)
        engine.decreaseVolume(0.1f)
        engine.setMuted(true)
        assertEquals(100.0, percent, "dead engine: the handle never moves")
        assertEquals(null, muteFlag, "dead engine: the flag never moves")
        assertEquals(emptyList(), captures, "dead engine: the capture never fires")

        // Alive: a user set remembers + captures + writes the percent.
        released = false
        engine.setVolume(0.5f, isUserChange = true)
        assertEquals(50.0, percent, "the cached percent IS the native level")
        assertEquals(listOf(0.5f), captures, "user change captures through the hook")

        // Mute: the FLAG silences; the property stays (LEAVE_UNCHANGED) and
        // the memory is the pre-mute native level.
        engine.setMuted(true)
        assertEquals(true, muteFlag)
        assertEquals(50.0, percent, "mute must not touch the volume property")
        assertEquals(0.5f, engine.rememberedUnmuteLevel, "the snapshot remembered the live level")

        // Unmute: the remembered level is written straight into the property
        // (REMEMBERED_LEVEL), programmatic — never a capture.
        engine.setMuted(false)
        assertEquals(false, muteFlag)
        assertEquals(50.0, percent, "unmute restores the remembered level")
        assertEquals(listOf(0.5f), captures, "the restore is programmatic: no capture")

        // A programmatic fade moves the level without touching the memory
        // surface's capture.
        engine.setVolume(0.2f, isUserChange = false)
        assertEquals(20.0, percent, 1e-6, "the fade writes the percent (0.2f carries float noise)")
        assertEquals(listOf(0.5f), captures)
    }
}
