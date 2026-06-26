package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.PlayerType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileProviderTest {

    private val provider = DeviceProfileProvider()

    @Test
    fun `mpv profile advertises a permissive direct play profile`() {
        val profile = provider.forPlayer(PlayerType.MPV)
        assertNotNull(profile.name)
        assertTrue("MPV must offer at least one direct-play profile", profile.directPlayProfiles.isNotEmpty())
        val videoDirectPlay = profile.directPlayProfiles.first()
        assertTrue(videoDirectPlay.container?.isNotEmpty() == true)
        assertTrue(videoDirectPlay.videoCodec?.contains("h264") == true)
        assertTrue(videoDirectPlay.videoCodec?.contains("hevc") == true)
    }

    @Test
    fun `hardware profile advertises direct play and a transcode fallback`() {
        val profile = provider.forPlayer(PlayerType.EXO_PLAYER)
        assertTrue(profile.directPlayProfiles.isNotEmpty())
        assertTrue("ExoPlayer needs an HLS transcode fallback", profile.transcodingProfiles.isNotEmpty())
        val transcode = profile.transcodingProfiles.first()
        assertTrue(transcode.videoCodec?.contains("h264") == true)
        // Subtitle delivery must be declared so the server hands back
        // external subs rather than always burning them in.
        assertTrue(profile.subtitleProfiles.isNotEmpty())
    }

    @Test
    fun `libvlc uses the same hardware codec set as exoplayer`() {
        val libvlc = provider.forPlayer(PlayerType.LIBVLC)
        val exo = provider.forPlayer(PlayerType.EXO_PLAYER)
        assertTrue(libvlc.directPlayProfiles.isNotEmpty())
        assertEquals(exo.directPlayProfiles.first().videoCodec, libvlc.directPlayProfiles.first().videoCodec)
    }

    private fun assertEquals(expected: String?, actual: String?) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
