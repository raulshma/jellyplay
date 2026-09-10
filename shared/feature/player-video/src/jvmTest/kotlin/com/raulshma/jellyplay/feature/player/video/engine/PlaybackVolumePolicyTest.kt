package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the shared volume/mute choreography decisions extracted from the three
 * engine adapters — the clamp bounds, the boost-ceiling divergence (declared
 * data, not copy variance), the snapshot-before-zeroing mute order, and the
 * per-engine native-restore vocabulary (including the libVLC unmute fix: it
 * must restore the remembered level, not snap to full loudness).
 */
class PlaybackVolumePolicyTest {

    // ── declared boost ceilings ──

    @Test
    fun maxBoost_declaresVlcAmplificationDivergence() {
        assertEquals(1.0f, PlaybackVolumePolicy.MAX_BOOST_NOMINAL)
        assertEquals(2.0f, PlaybackVolumePolicy.MAX_BOOST_VLC)
    }

    // ── planLevel ──

    @Test
    fun planLevel_passesThroughInRangeValues() {
        val plan = PlaybackVolumePolicy.planLevel(0.5f, PlaybackVolumePolicy.MAX_BOOST_NOMINAL)
        assertEquals(0.5f, plan.normalized)
        assertEquals(0.5f, plan.systemStream)
    }

    @Test
    fun planLevel_clampsToDeclaredMaxBoost() {
        // Exo/mpv ceiling: nominal 1.0.
        assertEquals(
            1.0f,
            PlaybackVolumePolicy.planLevel(1.5f, PlaybackVolumePolicy.MAX_BOOST_NOMINAL).normalized,
        )
        // libVLC amplification ceiling: 2.0 — the boost must survive the policy.
        val boosted = PlaybackVolumePolicy.planLevel(2.5f, PlaybackVolumePolicy.MAX_BOOST_VLC)
        assertEquals(2.0f, boosted.normalized)
    }

    @Test
    fun planLevel_clampsNegativesToZero() {
        val plan = PlaybackVolumePolicy.planLevel(-0.3f, PlaybackVolumePolicy.MAX_BOOST_NOMINAL)
        assertEquals(0.0f, plan.normalized)
        assertEquals(0.0f, plan.systemStream)
    }

    @Test
    fun planLevel_systemStreamCappedAtOne_evenWhenBoosted() {
        // The system music stream has no amplification: a >1.0 boost saturates
        // the stream sync at 1.0 while the native handle keeps the boost.
        val plan = PlaybackVolumePolicy.planLevel(1.7f, PlaybackVolumePolicy.MAX_BOOST_VLC)
        assertEquals(1.7f, plan.normalized)
        assertEquals(1.0f, plan.systemStream)
    }

    // ── planMute ──

    @Test
    fun planMute_zeroesSystemStream_snapshotsFirst_andZeroesNativeVolume() {
        val plan = PlaybackVolumePolicy.planMute(PlaybackVolumePolicy.NativeVolumeRestore.ZERO)
        assertTrue(plan.snapshotSystemVolume)
        assertEquals(0f, plan.nativeVolume)
        assertEquals(0f, plan.systemStream)
    }

    @Test
    fun planMute_leavesNativeVolumeAlone_forMuteFlagEngines() {
        // mpv toggles a real mute flag; its volume property must not move.
        val plan = PlaybackVolumePolicy.planMute(PlaybackVolumePolicy.NativeVolumeRestore.LEAVE_UNCHANGED)
        assertNull(plan.nativeVolume)
        assertEquals(0f, plan.systemStream)
    }

    // ── planUnmute ──

    @Test
    fun planUnmute_restoresRememberedLevel_withinWindow() {
        val plan = PlaybackVolumePolicy.planUnmute(0.4f, PlaybackVolumePolicy.NativeVolumeRestore.REMEMBERED_LEVEL)
        assertEquals(0.4f, plan.nativeVolume)
        assertEquals(0.4f, plan.systemStream)
    }

    @Test
    fun planUnmute_floorsSilencedMemory_toAudibleLevel() {
        // A mute at volume 0 must not unmute into permanent silence.
        val plan = PlaybackVolumePolicy.planUnmute(0f, PlaybackVolumePolicy.NativeVolumeRestore.REMEMBERED_LEVEL)
        assertEquals(0.05f, plan.nativeVolume)
        assertEquals(0.05f, plan.systemStream)
    }

    @Test
    fun planUnmute_capsBoostedMemory_atNominal() {
        val plan = PlaybackVolumePolicy.planUnmute(1.7f, PlaybackVolumePolicy.NativeVolumeRestore.REMEMBERED_LEVEL)
        assertEquals(1.0f, plan.nativeVolume)
        assertEquals(1.0f, plan.systemStream)
    }

    @Test
    fun planUnmute_nativeRestoreVocabulary_matchesEachEngine() {
        // Exo: no mute flag — the handle returns to full, the system stream
        // carries the remembered level.
        val exo = PlaybackVolumePolicy.planUnmute(0.4f, PlaybackVolumePolicy.NativeVolumeRestore.FULL)
        assertEquals(1f, exo.nativeVolume)
        assertEquals(0.4f, exo.systemStream)
        // mpv: native volume untouched (its mute flag toggles).
        val mpv = PlaybackVolumePolicy.planUnmute(0.4f, PlaybackVolumePolicy.NativeVolumeRestore.LEAVE_UNCHANGED)
        assertNull(mpv.nativeVolume)
        assertEquals(0.4f, mpv.systemStream)
        // libVLC: mutes by zeroing volume — unmute must write the remembered
        // level back (the fixed bug: it hardcoded full loudness instead).
        val vlc = PlaybackVolumePolicy.planUnmute(0.4f, PlaybackVolumePolicy.NativeVolumeRestore.REMEMBERED_LEVEL)
        assertEquals(0.4f, vlc.nativeVolume)
        assertEquals(0.4f, vlc.systemStream)
    }
}
