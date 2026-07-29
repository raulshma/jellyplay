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

    @Test
    fun `MPV profile omits image subtitle codecs when PGS direct play is off`() {
        val profile = provider.forPlayer(PlayerType.MPV, pgsDirectPlay = false)
        val subFormats = profile.subtitleProfiles.map { it.format }
        // Text subs always advertised
        assertTrue("srt should be advertised", subFormats.contains("srt"))
        // Image subs omitted → server burns them in during transcode
        assertTrue("pgs should be omitted", !subFormats.contains("pgs"))
        assertTrue("dvd_subtitle should be omitted", !subFormats.contains("dvd_subtitle"))
    }

    @Test
    fun `MPV profile advertises full image-subtitle family when PGS direct play is on`() {
        val profile = provider.forPlayer(PlayerType.MPV, pgsDirectPlay = true)
        val subFormats = profile.subtitleProfiles.map { it.format }
        // PGS + the rest of the image family so Blu-ray/DVD rips get a deliveryUrl
        assertTrue("pgs should be advertised", subFormats.contains("pgs"))
        assertTrue("pgssub should be advertised", subFormats.contains("pgssub"))
        assertTrue("hdmv_pgs_subtitle should be advertised", subFormats.contains("hdmv_pgs_subtitle"))
        assertTrue("dvd_subtitle should be advertised", subFormats.contains("dvd_subtitle"))
        assertTrue("vobsub should be advertised", subFormats.contains("vobsub"))
        assertTrue("dvb_subtitle should be advertised", subFormats.contains("dvb_subtitle"))
    }
}
