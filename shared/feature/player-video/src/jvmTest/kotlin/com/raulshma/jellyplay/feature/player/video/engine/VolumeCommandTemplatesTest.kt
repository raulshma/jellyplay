package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the shared volume/mute command choreography ([VolumeCommandTemplates])
 * over a recording surface: remember-before-write ordering, the
 * LEAVE_UNCHANGED mute (flag written, native level untouched, pre-mute level
 * snapshotted), the REMEMBERED_LEVEL unmute restore, the user-change capture
 * (user plans only — a programmatic restore never fires it), the null-handle
 * delta abort, the boost ceiling, and the mute-template gate.
 *
 * The surface mirrors the desktop mpv adapter's divergences (mute flag +
 * LEAVE_UNCHANGED / REMEMBERED_LEVEL, native-level snapshot, user capture,
 * system sync on) — the shape [ReloadablePlayerEngine] and the desktop engine
 * bridge to.
 */
class VolumeCommandTemplatesTest {

    /** Records every seam call in order; applies change the fake native level. */
    private class RecordingSurface(
        var nativeLevel: Float? = 0.5f,
        override val volumeBoostCeiling: Float = PlaybackVolumePolicy.MAX_BOOST_NOMINAL,
        override val muteTemplateEnabled: Boolean = true,
        var restoreOnMute: PlaybackVolumePolicy.NativeVolumeRestore =
            PlaybackVolumePolicy.NativeVolumeRestore.LEAVE_UNCHANGED,
        var restoreOnUnmute: PlaybackVolumePolicy.NativeVolumeRestore =
            PlaybackVolumePolicy.NativeVolumeRestore.REMEMBERED_LEVEL,
    ) : VolumeCommandTemplates.NativeVolumeSurface {
        override var rememberedUnmuteLevel: Float = 1f
        val log = mutableListOf<String>()

        override fun readNativeVolume(): Float? = nativeLevel

        override fun applyNativeVolume(normalized: Float) {
            log += "apply:$normalized"
            nativeLevel = normalized
        }

        override fun applyNativeMuteFlag(muted: Boolean) {
            log += "flag:$muted"
        }

        override fun rememberUnmuteVolume(level: Float) {
            log += "remember:$level"
            if (level > 0f) rememberedUnmuteLevel = level
        }

        override fun snapshotVolumeForMute() {
            log += "snapshot"
            nativeLevel?.let { if (it > 0f) rememberedUnmuteLevel = it }
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

    // ── setVolume / delta commands ──

    @Test
    fun setVolume_plansRemembersCapturesWritesAndSyncs_inOrder() {
        val surface = RecordingSurface()
        VolumeCommandTemplates.setVolume(surface, 0.7f, isUserChange = true)
        assertEquals(
            listOf("remember:0.7", "capture:0.7", "apply:0.7", "sync:0.7"),
            surface.log,
        )
        assertEquals(0.7f, surface.rememberedUnmuteLevel)
    }

    @Test
    fun setVolume_programmaticPlan_neverCaptures() {
        val surface = RecordingSurface()
        VolumeCommandTemplates.setVolume(surface, 0.7f, isUserChange = false)
        assertEquals(listOf("remember:0.7", "apply:0.7", "sync:0.7"), surface.log)
    }

    @Test
    fun setVolume_clampsToTheDeclaredBoostCeiling() {
        // Exo/mpv ceiling: nominal 1.0 — the system sync caps there too.
        val nominal = RecordingSurface()
        VolumeCommandTemplates.setVolume(nominal, 1.5f, isUserChange = true)
        assertEquals(listOf("remember:1.0", "capture:1.0", "apply:1.0", "sync:1.0"), nominal.log)

        // libVLC amplification ceiling: 2.0 survives the plan; the stream caps at 1.0.
        val boosted = RecordingSurface(volumeBoostCeiling = PlaybackVolumePolicy.MAX_BOOST_VLC)
        VolumeCommandTemplates.setVolume(boosted, 2.5f, isUserChange = true)
        assertEquals(listOf("remember:2.0", "capture:2.0", "apply:2.0", "sync:1.0"), boosted.log)
    }

    @Test
    fun increaseVolume_basesTheDeltaOnTheNativeLevel_andCaptures() {
        val surface = RecordingSurface(nativeLevel = 0.5f)
        VolumeCommandTemplates.increaseVolume(surface, delta = 0.1f)
        assertEquals(
            listOf("remember:0.6", "capture:0.6", "apply:0.6", "sync:0.6"),
            surface.log,
        )
    }

    @Test
    fun decreaseVolume_intoSilence_remembersNothingButStillApplies() {
        val surface = RecordingSurface(nativeLevel = 0.5f)
        VolumeCommandTemplates.decreaseVolume(surface, delta = 0.5f)
        assertEquals(
            listOf("remember:0.0", "capture:0.0", "apply:0.0", "sync:0.0"),
            surface.log,
        )
        // The remembered level must survive a zeroing nudge (audible-only rule).
        assertEquals(1f, surface.rememberedUnmuteLevel)
    }

    @Test
    fun deltaCommands_abortWithoutANativeHandle() {
        val surface = RecordingSurface(nativeLevel = null)
        VolumeCommandTemplates.increaseVolume(surface, delta = 0.1f)
        VolumeCommandTemplates.decreaseVolume(surface, delta = 0.1f)
        assertEquals(emptyList(), surface.log)
    }

    // ── mute / unmute ──

    @Test
    fun mute_leaveUnchanged_writesTheFlagOnly_andSnapshotsThePreMuteLevel() {
        val surface = RecordingSurface(nativeLevel = 0.4f, restoreOnMute = PlaybackVolumePolicy.NativeVolumeRestore.LEAVE_UNCHANGED)
        surface.rememberedUnmuteLevel = 0.8f
        VolumeCommandTemplates.setMuted(surface, muted = true)
        // The native volume is NOT written (mpv's flag silences) and the
        // snapshot replaces the remembered level with the live pre-mute level.
        assertEquals(listOf("flag:true", "snapshot", "sync:0.0"), surface.log)
        assertEquals(0.4f, surface.rememberedUnmuteLevel)
    }

    @Test
    fun mute_zeroRestore_writesTheZeroAfterTheSnapshot() {
        val surface = RecordingSurface(nativeLevel = 0.4f, restoreOnMute = PlaybackVolumePolicy.NativeVolumeRestore.ZERO)
        VolumeCommandTemplates.setMuted(surface, muted = true)
        assertEquals(listOf("flag:true", "snapshot", "apply:0.0", "sync:0.0"), surface.log)
    }

    @Test
    fun unmute_rememberedLevel_restoresItIntoTheNativeWrite() {
        val surface = RecordingSurface(restoreOnUnmute = PlaybackVolumePolicy.NativeVolumeRestore.REMEMBERED_LEVEL)
        surface.rememberedUnmuteLevel = 0.6f
        VolumeCommandTemplates.setMuted(surface, muted = false)
        // The restore is programmatic: no capture, ever.
        assertEquals(listOf("flag:false", "apply:0.6", "sync:0.6"), surface.log)
    }

    @Test
    fun unmute_fullRestore_writesNominalLoudness() {
        val surface = RecordingSurface(restoreOnUnmute = PlaybackVolumePolicy.NativeVolumeRestore.FULL)
        surface.rememberedUnmuteLevel = 0.6f
        VolumeCommandTemplates.setMuted(surface, muted = false)
        assertEquals(listOf("flag:false", "apply:1.0", "sync:0.6"), surface.log)
    }

    @Test
    fun muteTemplateDisabled_runsTheFlagOnly() {
        val surface = RecordingSurface(muteTemplateEnabled = false)
        VolumeCommandTemplates.setMuted(surface, muted = true)
        assertEquals(listOf("flag:true"), surface.log)
    }
}
