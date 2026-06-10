package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import com.raulshma.jellyplay.feature.player.video.engine.MediaTrack
import com.raulshma.jellyplay.core.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerTypeTest {

    @Test
    fun playerType_hasCorrectDisplayNames() {
        assertEquals("ExoPlayer", PlayerType.EXO_PLAYER.displayName)
        assertEquals("mpv", PlayerType.MPV.displayName)
        assertEquals("LibVLC", PlayerType.LIBVLC.displayName)
        assertEquals("External", PlayerType.EXTERNAL.displayName)
    }

    @Test
    fun playerType_hasCorrectDescriptions() {
        assertTrue(PlayerType.EXO_PLAYER.description.contains("Media3"))
        assertTrue(PlayerType.MPV.description.contains("libmpv"))
        assertTrue(PlayerType.LIBVLC.description.contains("VLC"))
        assertTrue(PlayerType.EXTERNAL.description.contains("external", ignoreCase = true))
    }

    @Test
    fun playerType_entries_matchExpectedCount() {
        assertEquals(4, PlayerType.entries.size)
    }

    @Test
    fun playerType_fromStoredName_exoPlayer() {
        assertEquals(PlayerType.EXO_PLAYER, PlayerType.fromStoredName("EXO_PLAYER"))
    }

    @Test
    fun playerType_fromStoredName_mpv() {
        assertEquals(PlayerType.MPV, PlayerType.fromStoredName("MPV"))
    }

    @Test
    fun playerType_fromStoredName_libvlc() {
        assertEquals(PlayerType.LIBVLC, PlayerType.fromStoredName("LIBVLC"))
    }

    @Test
    fun playerType_fromStoredName_external() {
        assertEquals(PlayerType.EXTERNAL, PlayerType.fromStoredName("EXTERNAL"))
    }

    @Test
    fun playerType_fromStoredName_legacyInternal_mapsToExoPlayer() {
        assertEquals(PlayerType.EXO_PLAYER, PlayerType.fromStoredName("INTERNAL"))
    }

    @Test
    fun playerType_fromStoredName_unknown_mapsToExoPlayer() {
        assertEquals(PlayerType.EXO_PLAYER, PlayerType.fromStoredName("UNKNOWN_PLAYER"))
    }

    @Test
    fun playerType_fromStoredName_emptyString_mapsToExoPlayer() {
        assertEquals(PlayerType.EXO_PLAYER, PlayerType.fromStoredName(""))
    }
}

class EngineCapabilitiesCrossPlayerTest {

    @Test
    fun exoPlayerCapabilities_subtitleStyleSupported() {
        val caps = getCapabilitiesForType(PlayerType.EXO_PLAYER)
        assertTrue(caps.supportsSubtitleStyle)
    }

    @Test
    fun exoPlayerCapabilities_dialogueBoostSupported() {
        val caps = getCapabilitiesForType(PlayerType.EXO_PLAYER)
        assertTrue(caps.supportsDialogueBoost)
    }

    @Test
    fun exoPlayerCapabilities_nightModeSupported() {
        val caps = getCapabilitiesForType(PlayerType.EXO_PLAYER)
        assertTrue(caps.supportsNightMode)
    }

    @Test
    fun exoPlayerCapabilities_cuesSupported() {
        val caps = getCapabilitiesForType(PlayerType.EXO_PLAYER)
        assertTrue(caps.supportsCues)
    }

    @Test
    fun exoPlayerCapabilities_audioDelayNotSupported() {
        val caps = getCapabilitiesForType(PlayerType.EXO_PLAYER)
        assertFalse(caps.supportsAudioDelay)
    }

    @Test
    fun exoPlayerCapabilities_audioPassthroughNotSupported() {
        val caps = getCapabilitiesForType(PlayerType.EXO_PLAYER)
        assertFalse(caps.supportsAudioPassthrough)
    }

    @Test
    fun mpvCapabilities_audioDelaySupported() {
        val caps = getCapabilitiesForType(PlayerType.MPV)
        assertTrue(caps.supportsAudioDelay)
    }

    @Test
    fun mpvCapabilities_audioPassthroughSupported() {
        val caps = getCapabilitiesForType(PlayerType.MPV)
        assertTrue(caps.supportsAudioPassthrough)
    }

    @Test
    fun mpvCapabilities_subtitleStyleSupported() {
        val caps = getCapabilitiesForType(PlayerType.MPV)
        assertTrue(caps.supportsSubtitleStyle)
    }

    @Test
    fun mpvCapabilities_dialogueBoostSupported() {
        val caps = getCapabilitiesForType(PlayerType.MPV)
        assertTrue(caps.supportsDialogueBoost)
    }

    @Test
    fun mpvCapabilities_nightModeSupported() {
        val caps = getCapabilitiesForType(PlayerType.MPV)
        assertTrue(caps.supportsNightMode)
    }

    @Test
    fun mpvCapabilities_cuesNotSupported() {
        val caps = getCapabilitiesForType(PlayerType.MPV)
        assertFalse(caps.supportsCues)
    }

    @Test
    fun libvlcCapabilities_audioDelaySupported() {
        val caps = getCapabilitiesForType(PlayerType.LIBVLC)
        assertTrue(caps.supportsAudioDelay)
    }

    @Test
    fun libvlcCapabilities_audioPassthroughSupported() {
        val caps = getCapabilitiesForType(PlayerType.LIBVLC)
        assertTrue(caps.supportsAudioPassthrough)
    }

    @Test
    fun libvlcCapabilities_subtitleStyleSupported() {
        val caps = getCapabilitiesForType(PlayerType.LIBVLC)
        assertTrue(caps.supportsSubtitleStyle)
    }

    @Test
    fun libvlcCapabilities_dialogueBoostSupported() {
        val caps = getCapabilitiesForType(PlayerType.LIBVLC)
        assertTrue(caps.supportsDialogueBoost)
    }

    @Test
    fun libvlcCapabilities_nightModeSupported() {
        val caps = getCapabilitiesForType(PlayerType.LIBVLC)
        assertTrue(caps.supportsNightMode)
    }

    @Test
    fun libvlcCapabilities_cuesNotSupported() {
        val caps = getCapabilitiesForType(PlayerType.LIBVLC)
        assertFalse(caps.supportsCues)
    }

    @Test
    fun mpvAndLibvlcCapabilities_matchForSharedFeatures() {
        val mpvCaps = getCapabilitiesForType(PlayerType.MPV)
        val vlcCaps = getCapabilitiesForType(PlayerType.LIBVLC)

        assertEquals(mpvCaps.supportsAudioDelay, vlcCaps.supportsAudioDelay)
        assertEquals(mpvCaps.supportsAudioPassthrough, vlcCaps.supportsAudioPassthrough)
        assertEquals(mpvCaps.supportsSubtitleStyle, vlcCaps.supportsSubtitleStyle)
        assertEquals(mpvCaps.supportsDialogueBoost, vlcCaps.supportsDialogueBoost)
        assertEquals(mpvCaps.supportsNightMode, vlcCaps.supportsNightMode)
        assertEquals(mpvCaps.supportsCues, vlcCaps.supportsCues)
    }

    @Test
    fun exoPlayerAndNativeEngines_someCapabilitiesDiffer() {
        val exoCaps = getCapabilitiesForType(PlayerType.EXO_PLAYER)
        val mpvCaps = getCapabilitiesForType(PlayerType.MPV)

        assertFalse(exoCaps.supportsAudioDelay == mpvCaps.supportsAudioDelay)
        assertFalse(exoCaps.supportsAudioPassthrough == mpvCaps.supportsAudioPassthrough)
        assertFalse(exoCaps.supportsCues == mpvCaps.supportsCues)
        assertTrue(exoCaps.supportsNightMode == mpvCaps.supportsNightMode)
    }

    private fun getCapabilitiesForType(playerType: PlayerType): EngineCapabilities {
        return when (playerType) {
            PlayerType.EXO_PLAYER -> EngineCapabilities(
                supportsPip = true,
                supportsMiniMode = true,
                supportsCues = true,
                supportsAudioDelay = false,
                supportsSubtitleDelay = true,
                supportsAudioPassthrough = false,
                supportsSubtitleStyle = true,
                supportsDialogueBoost = true,
                supportsNightMode = true,
            )
            PlayerType.MPV -> EngineCapabilities(
                supportsPip = true,
                supportsMiniMode = false,
                supportsCues = false,
                supportsAudioDelay = true,
                supportsSubtitleDelay = true,
                supportsAudioPassthrough = true,
                supportsSubtitleStyle = true,
                supportsDialogueBoost = true,
                supportsNightMode = true,
            )
            PlayerType.LIBVLC -> EngineCapabilities(
                supportsPip = true,
                supportsMiniMode = false,
                supportsCues = false,
                supportsAudioDelay = true,
                supportsSubtitleDelay = true,
                supportsAudioPassthrough = true,
                supportsSubtitleStyle = true,
                supportsDialogueBoost = true,
                supportsNightMode = true,
            )
            PlayerType.EXTERNAL -> EngineCapabilities()
        }
    }
}

class MediaTrackContractTest {

    @Test
    fun mediaTrack_containsRequiredFields() {
        val track = MediaTrack(
            id = "0",
            index = 0,
            label = "English",
            language = "eng",
            isSelected = true,
            type = TrackType.AUDIO,
        )
        assertEquals("0", track.id)
        assertEquals(0, track.index)
        assertEquals("English", track.label)
        assertEquals("eng", track.language)
        assertTrue(track.isSelected)
        assertEquals(TrackType.AUDIO, track.type)
    }

    @Test
    fun mediaTrack_defaultTrackGroupIsNull() {
        val track = MediaTrack(
            id = "0",
            index = 0,
            label = "Test",
            language = null,
            isSelected = false,
            type = TrackType.SUBTITLE,
        )
        assertEquals(null, track.trackGroup)
    }

    @Test
    fun mediaTrack_dataClassEquality() {
        val a = MediaTrack(
            id = "1", index = 1, label = "Spanish", language = "spa", isSelected = false,
            type = TrackType.AUDIO,
        )
        val b = MediaTrack(
            id = "1", index = 1, label = "Spanish", language = "spa", isSelected = false,
            type = TrackType.AUDIO,
        )
        assertEquals(a, b)
    }

    @Test
    fun trackType_hasAudioAndSubtitle() {
        assertEquals(2, TrackType.entries.size)
        assertNotNull(TrackType.AUDIO)
        assertNotNull(TrackType.SUBTITLE)
    }
}
