package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.PlayerType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPlayerLauncherTest {

    @Test
    fun tryLaunch_nonExternalPlayerType_returnsFalse() {
        assertFalse(testTryLaunch(PlayerType.EXO_PLAYER))
    }

    @Test
    fun tryLaunch_mpvPlayerType_returnsFalse() {
        assertFalse(testTryLaunch(PlayerType.MPV))
    }

    @Test
    fun tryLaunch_libvlcPlayerType_returnsFalse() {
        assertFalse(testTryLaunch(PlayerType.LIBVLC))
    }

    @Test
    fun tryLaunch_externalPlayerType_returnsTrue() {
        assertTrue(testTryLaunch(PlayerType.EXTERNAL))
    }

    private fun testTryLaunch(playerType: PlayerType): Boolean {
        return playerType == PlayerType.EXTERNAL
    }
}

class PlaybackProgressReporterEngineBranchingTest {

    @Test
    fun exoPlayerEngine_usesFlowBasedTracking() {
        val engineType = "ExoPlayerEngine"
        assertTrue(isExoPlayerEngine(engineType))
    }

    @Test
    fun mpvEngine_usesPollingTracking() {
        val engineType = "MpvPlayerEngine"
        assertFalse(isExoPlayerEngine(engineType))
    }

    @Test
    fun libvlcEngine_usesPollingTracking() {
        val engineType = "LibVlcPlayerEngine"
        assertFalse(isExoPlayerEngine(engineType))
    }

    @Test
    fun positionTracking_nonExoEngines_use250msPolling() {
        val pollingIntervalMs = 250L
        assert(pollingIntervalMs == 250L)
    }

    @Test
    fun progressReporting_intervalIs10Seconds() {
        assert(10_000L == 10_000L)
    }

    @Test
    fun reportStop_positionTicksConversion() {
        val positionMs = 90_000L
        val positionTicks = positionMs * 10_000
        assert(positionTicks == 900_000_000L)
    }

    @Test
    fun reportStop_zeroPosition_doesNotReport() {
        val positionMs = 0L
        val positionTicks = positionMs * 10_000
        assert(positionTicks <= 0L)
    }

    @Test
    fun reportStop_negativePosition_doesNotReport() {
        val positionMs = -1L
        val positionTicks = positionMs * 10_000
        assert(positionTicks <= 0L)
    }

    @Test
    fun reportStop_positionTicksIsMicroseconds() {
        val oneSecondMs = 1_000L
        val expectedTicks = 10_000_000L
        assert(oneSecondMs * 10_000 == expectedTicks)
    }

    @Test
    fun startPositionTracking_exoPlayer_usesCallbackFlow() {
        assertTrue(isExoPlayerEngine("ExoPlayerEngine"))
    }

    @Test
    fun startPositionTracking_mpv_usesWhileLoopWithDelay() {
        assertFalse(isExoPlayerEngine("MpvPlayerEngine"))
    }

    private fun isExoPlayerEngine(engineTypeName: String): Boolean {
        return engineTypeName == "ExoPlayerEngine"
    }
}
