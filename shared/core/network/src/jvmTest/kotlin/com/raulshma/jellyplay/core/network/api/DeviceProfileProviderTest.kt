package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.AudioPassthroughCodec
import com.raulshma.jellyplay.core.model.MaxAudioChannelsEnum
import com.raulshma.jellyplay.core.model.PlayerType
import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.ProfileConditionType
import org.jellyfin.sdk.model.api.ProfileConditionValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeviceProfileProviderTest {
    private val provider = DeviceProfileProvider(DesktopDeviceCodecCapabilities())

    /** The direct-play audio tokens of [profile]'s first video profile. */
    private fun audioTokens(profile: org.jellyfin.sdk.model.api.DeviceProfile): List<String> =
        profile.directPlayProfiles.first().audioCodec.orEmpty().split(',').map { it.trim() }

    @Test
    fun `mpv profile advertises a permissive direct play profile`() {
        val profile = provider.forPlayer(PlayerType.MPV)
        assertNotNull(profile.name)
        assertTrue(profile.directPlayProfiles.isNotEmpty(), "MPV must offer at least one direct-play profile")
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
        assertTrue(profile.transcodingProfiles.isNotEmpty(), "ExoPlayer needs an HLS transcode fallback")
        val transcode = profile.transcodingProfiles.first()
        assertTrue(transcode.videoCodec.contains("h264"))
        assertTrue(profile.subtitleProfiles.isNotEmpty())
    }

    @Test
    fun `hardware direct play only advertises detected video codecs but forces common audio codecs`() {
        // Fake device that decodes only h264 + aac.
        val fakeCapabilities = object : DeviceCodecCapabilities {
            override val supportedVideoCodecs get() = setOf("h264")
            override val supportedAudioCodecs get() = setOf("aac")
        }
        val profile = DeviceProfileProvider(fakeCapabilities).forPlayer(PlayerType.EXO_PLAYER)
        val videoDirectPlay = profile.directPlayProfiles.first()
        assertTrue(videoDirectPlay.videoCodec?.contains("h264") == true)
        assertTrue(videoDirectPlay.videoCodec?.contains("hevc") != true, "must not claim hevc the fake device lacks")
        assertTrue(videoDirectPlay.audioCodec?.contains("aac") == true)
        // DTS/TrueHD have no MediaFormat mime and are therefore never reported
        // by MediaCodecList, yet they are common in MKV rips and must be
        // advertised so the server does not transcode them away.
        assertTrue(videoDirectPlay.audioCodec?.contains("dts") == true, "forced audio codecs must always be advertised")
        assertTrue(videoDirectPlay.audioCodec?.contains("truehd") == true)
    }

    @Test
    fun `MPV profile omits image subtitle codecs when PGS direct play is off`() {
        val profile = provider.forPlayer(PlayerType.MPV, pgsDirectPlay = false)
        val subFormats = profile.subtitleProfiles.map { it.format }
        // Text subs always advertised
        assertTrue(subFormats.contains("srt"), "srt should be advertised")
        // Image subs omitted → server burns them in during transcode
        assertTrue(!subFormats.contains("pgs"), "pgs should be omitted")
        assertTrue(!subFormats.contains("dvd_subtitle"), "dvd_subtitle should be omitted")
    }

    @Test
    fun `MPV profile advertises full image-subtitle family when PGS direct play is on`() {
        val profile = provider.forPlayer(PlayerType.MPV, pgsDirectPlay = true)
        val subFormats = profile.subtitleProfiles.map { it.format }
        // PGS + the rest of the image family so Blu-ray/DVD rips get a deliveryUrl
        assertTrue(subFormats.contains("pgs"), "pgs should be advertised")
        assertTrue(subFormats.contains("pgssub"), "pgssub should be advertised")
        assertTrue(subFormats.contains("hdmv_pgs_subtitle"), "hdmv_pgs_subtitle should be advertised")
        assertTrue(subFormats.contains("dvd_subtitle"), "dvd_subtitle should be advertised")
        assertTrue(subFormats.contains("vobsub"), "vobsub should be advertised")
        assertTrue(subFormats.contains("dvb_subtitle"), "dvb_subtitle should be advertised")
    }

    // ── per-codec passthrough filter (direct-play audio sets) ──────────────

    @Test
    fun `passthrough filter drops disabled codecs from the mpv direct play list`() {
        val profile = provider.forPlayer(
            playerType = PlayerType.MPV,
            audioPassthrough = true,
            passthroughCodecs = AudioPassthroughCodec.ALL - AudioPassthroughCodec.DTS_HD - AudioPassthroughCodec.TRUEHD,
        )
        val tokens = audioTokens(profile)
        // dts-hd rides the dca decoder; TrueHD is spelled truehd in this list.
        assertFalse("dca" in tokens, "disabled dts-hd must leave the direct-play set")
        assertFalse("truehd" in tokens, "disabled truehd must leave the direct-play set")
        assertTrue("dts" in tokens, "plain DTS stays enabled")
        assertTrue("ac3" in tokens, "enabled codecs stay advertised")
    }

    @Test
    fun `passthrough filter is inert while the master toggle is off`() {
        val filtered = provider.forPlayer(
            playerType = PlayerType.MPV,
            audioPassthrough = false,
            passthroughCodecs = setOf(AudioPassthroughCodec.AC3),
        )
        val unfiltered = provider.forPlayer(PlayerType.MPV)
        assertEquals(unfiltered.directPlayProfiles, filtered.directPlayProfiles)
    }

    @Test
    fun `the full enabled set reproduces the legacy direct play lists`() {
        val all = provider.forPlayer(PlayerType.MPV, audioPassthrough = true, passthroughCodecs = AudioPassthroughCodec.ALL)
        assertEquals(provider.forPlayer(PlayerType.MPV).directPlayProfiles, all.directPlayProfiles)
    }

    @Test
    fun `ExoPlayer ignores the passthrough filter (no bitstream surface)`() {
        val profile = provider.forPlayer(
            playerType = PlayerType.EXO_PLAYER,
            audioPassthrough = true,
            passthroughCodecs = AudioPassthroughCodec.ALL - AudioPassthroughCodec.TRUEHD,
        )
        val tokens = audioTokens(profile)
        // ExoPlayer decodes everything to PCM — filtering its set would make
        // the server transcode codecs the device decodes fine.
        assertTrue("truehd" in tokens, "ExoPlayer's forced TrueHD advertising must survive the filter")
        assertTrue("dts" in tokens)
    }

    // ── channel cap (AudioChannels codec condition) ────────────────────────

    @Test
    fun `the channel cap is advertised as a LessThanEqual AudioChannels condition`() {
        val capped = provider.forPlayer(PlayerType.MPV, maxAudioChannels = MaxAudioChannelsEnum.FIVE_POINT_ONE)
        val condition = capped.codecProfiles
            .filter { it.type == CodecType.VIDEO_AUDIO }
            .flatMap { it.applyConditions }
            .firstOrNull { it.property == ProfileConditionValue.AUDIO_CHANNELS }
        assertNotNull(condition, "the cap must be advertised as an AudioChannels condition")
        assertEquals(ProfileConditionType.LESS_THAN_EQUAL, condition.condition)
        assertEquals("6", condition.value)
    }

    @Test
    fun `no AudioChannels condition while the cap is AUTO`() {
        val uncapped = provider.forPlayer(PlayerType.MPV, maxAudioChannels = MaxAudioChannelsEnum.AUTO)
        val hasCap = uncapped.codecProfiles.any { profile ->
            profile.applyConditions.any { it.property == ProfileConditionValue.AUDIO_CHANNELS }
        }
        assertFalse(hasCap, "AUTO must not advertise a channel cap")
    }
}
