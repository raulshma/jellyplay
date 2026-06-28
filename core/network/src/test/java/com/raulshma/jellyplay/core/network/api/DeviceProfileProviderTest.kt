package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.PlayerType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileProviderTest {

    private val provider = DeviceProfileProvider(DeviceCodecCapabilities())

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
    fun `hardware profile always offers an HLS transcode fallback and subtitle delivery`() {
        // On the JVM there is no MediaCodecList, so DeviceCodecCapabilities
        // falls back to empty sets — the hardware direct-play codec list is
        // therefore empty here, but the profile must still advertise a
        // transcode fallback so the server has somewhere to go.
        val profile = provider.forPlayer(PlayerType.EXO_PLAYER)
        assertTrue("ExoPlayer needs an HLS transcode fallback", profile.transcodingProfiles.isNotEmpty())
        val transcode = profile.transcodingProfiles.first()
        assertTrue(transcode.videoCodec.contains("h264"))
        assertTrue(profile.subtitleProfiles.isNotEmpty())
    }

    @Test
    fun `hardware direct play only advertises detected video codecs but forces common audio codecs`() {
        // Fake device that decodes only h264 + aac.
        val fakeCapabilities = object : DeviceCodecCapabilities() {
            override val supportedVideoCodecs get() = setOf("h264")
            override val supportedAudioCodecs get() = setOf("aac")
        }
        val profile = DeviceProfileProvider(fakeCapabilities).forPlayer(PlayerType.EXO_PLAYER)
        val videoDirectPlay = profile.directPlayProfiles.first()
        assertTrue(videoDirectPlay.videoCodec?.contains("h264") == true)
        assertTrue("must not claim hevc the fake device lacks", videoDirectPlay.videoCodec?.contains("hevc") != true)
        assertTrue(videoDirectPlay.audioCodec?.contains("aac") == true)
        // DTS/TrueHD have no MediaFormat mime and are therefore never reported
        // by MediaCodecList, yet they are common in MKV rips and must be
        // advertised so the server does not transcode them away.
        assertTrue("forced audio codecs must always be advertised", videoDirectPlay.audioCodec?.contains("dts") == true)
        assertTrue(videoDirectPlay.audioCodec?.contains("truehd") == true)
    }
}
