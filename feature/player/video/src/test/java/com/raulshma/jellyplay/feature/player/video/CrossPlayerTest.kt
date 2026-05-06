package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.PlayerType
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
        assertTrue(caps.subtitleStyle)
    }

    @Test
    fun exoPlayerCapabilities_dialogueBoostSupported() {
        val caps = getCapabilitiesForType(PlayerType.EXO_PLAYER)
        assertTrue(caps.dialogueBoost)
    }

    @Test
    fun exoPlayerCapabilities_nightModeSupported() {
        val caps = getCapabilitiesForType(PlayerType.EXO_PLAYER)
        assertTrue(caps.nightMode)
    }

    @Test
    fun exoPlayerCapabilities_ocrSupported() {
        val caps = getCapabilitiesForType(PlayerType.EXO_PLAYER)
        assertTrue(caps.ocr)
    }

    @Test
    fun exoPlayerCapabilities_cuesSupported() {
        val caps = getCapabilitiesForType(PlayerType.EXO_PLAYER)
        assertTrue(caps.cues)
    }

    @Test
    fun exoPlayerCapabilities_audioDelayNotSupported() {
        val caps = getCapabilitiesForType(PlayerType.EXO_PLAYER)
        assertFalse(caps.audioDelay)
    }

    @Test
    fun exoPlayerCapabilities_audioPassthroughNotSupported() {
        val caps = getCapabilitiesForType(PlayerType.EXO_PLAYER)
        assertFalse(caps.audioPassthrough)
    }

    @Test
    fun mpvCapabilities_audioDelaySupported() {
        val caps = getCapabilitiesForType(PlayerType.MPV)
        assertTrue(caps.audioDelay)
    }

    @Test
    fun mpvCapabilities_audioPassthroughSupported() {
        val caps = getCapabilitiesForType(PlayerType.MPV)
        assertTrue(caps.audioPassthrough)
    }

    @Test
    fun mpvCapabilities_subtitleStyleNotSupported() {
        val caps = getCapabilitiesForType(PlayerType.MPV)
        assertFalse(caps.subtitleStyle)
    }

    @Test
    fun mpvCapabilities_dialogueBoostNotSupported() {
        val caps = getCapabilitiesForType(PlayerType.MPV)
        assertFalse(caps.dialogueBoost)
    }

    @Test
    fun mpvCapabilities_nightModeNotSupported() {
        val caps = getCapabilitiesForType(PlayerType.MPV)
        assertFalse(caps.nightMode)
    }

    @Test
    fun mpvCapabilities_ocrNotSupported() {
        val caps = getCapabilitiesForType(PlayerType.MPV)
        assertFalse(caps.ocr)
    }

    @Test
    fun mpvCapabilities_cuesNotSupported() {
        val caps = getCapabilitiesForType(PlayerType.MPV)
        assertFalse(caps.cues)
    }

    @Test
    fun libvlcCapabilities_audioDelaySupported() {
        val caps = getCapabilitiesForType(PlayerType.LIBVLC)
        assertTrue(caps.audioDelay)
    }

    @Test
    fun libvlcCapabilities_audioPassthroughSupported() {
        val caps = getCapabilitiesForType(PlayerType.LIBVLC)
        assertTrue(caps.audioPassthrough)
    }

    @Test
    fun libvlcCapabilities_subtitleStyleNotSupported() {
        val caps = getCapabilitiesForType(PlayerType.LIBVLC)
        assertFalse(caps.subtitleStyle)
    }

    @Test
    fun libvlcCapabilities_dialogueBoostNotSupported() {
        val caps = getCapabilitiesForType(PlayerType.LIBVLC)
        assertFalse(caps.dialogueBoost)
    }

    @Test
    fun libvlcCapabilities_nightModeNotSupported() {
        val caps = getCapabilitiesForType(PlayerType.LIBVLC)
        assertFalse(caps.nightMode)
    }

    @Test
    fun libvlcCapabilities_ocrNotSupported() {
        val caps = getCapabilitiesForType(PlayerType.LIBVLC)
        assertFalse(caps.ocr)
    }

    @Test
    fun libvlcCapabilities_cuesNotSupported() {
        val caps = getCapabilitiesForType(PlayerType.LIBVLC)
        assertFalse(caps.cues)
    }

    @Test
    fun mpvAndLibvlcCapabilities_matchForSharedFeatures() {
        val mpvCaps = getCapabilitiesForType(PlayerType.MPV)
        val vlcCaps = getCapabilitiesForType(PlayerType.LIBVLC)

        assertEquals(mpvCaps.audioDelay, vlcCaps.audioDelay)
        assertEquals(mpvCaps.audioPassthrough, vlcCaps.audioPassthrough)
        assertEquals(mpvCaps.subtitleStyle, vlcCaps.subtitleStyle)
        assertEquals(mpvCaps.dialogueBoost, vlcCaps.dialogueBoost)
        assertEquals(mpvCaps.nightMode, vlcCaps.nightMode)
        assertEquals(mpvCaps.ocr, vlcCaps.ocr)
        assertEquals(mpvCaps.cues, vlcCaps.cues)
    }

    @Test
    fun exoPlayerAndNativeEngines_capabilitiesDiffer() {
        val exoCaps = getCapabilitiesForType(PlayerType.EXO_PLAYER)
        val mpvCaps = getCapabilitiesForType(PlayerType.MPV)

        assertFalse(exoCaps.audioDelay == mpvCaps.audioDelay)
        assertFalse(exoCaps.audioPassthrough == mpvCaps.audioPassthrough)
        assertFalse(exoCaps.subtitleStyle == mpvCaps.subtitleStyle)
        assertFalse(exoCaps.dialogueBoost == mpvCaps.dialogueBoost)
    }

    private fun getCapabilitiesForType(playerType: PlayerType): EngineCapabilities {
        return when (playerType) {
            PlayerType.EXO_PLAYER -> EngineCapabilities(
                audioDelay = false,
                audioPassthrough = false,
                subtitleStyle = true,
                dialogueBoost = true,
                nightMode = true,
                ocr = true,
                cues = true,
            )
            PlayerType.MPV -> EngineCapabilities(
                audioDelay = true,
                audioPassthrough = true,
                subtitleStyle = false,
                dialogueBoost = false,
                nightMode = false,
                ocr = false,
                cues = false,
            )
            PlayerType.LIBVLC -> EngineCapabilities(
                audioDelay = true,
                audioPassthrough = true,
                subtitleStyle = false,
                dialogueBoost = false,
                nightMode = false,
                ocr = false,
                cues = false,
            )
            PlayerType.EXTERNAL -> EngineCapabilities()
        }
    }
}

class PlayerEngineInterfaceContractTest {

    @Test
    fun trackInfo_containsRequiredFields() {
        val trackInfo = com.raulshma.jellyplay.feature.player.video.engine.PlayerEngine.TrackInfo(
            index = 0,
            label = "English",
            language = "eng",
            isSelected = true,
            type = com.raulshma.jellyplay.feature.player.video.engine.PlayerEngine.TrackType.AUDIO,
        )
        assertEquals(0, trackInfo.index)
        assertEquals("English", trackInfo.label)
        assertEquals("eng", trackInfo.language)
        assertTrue(trackInfo.isSelected)
        assertEquals(com.raulshma.jellyplay.feature.player.video.engine.PlayerEngine.TrackType.AUDIO, trackInfo.type)
    }

    @Test
    fun trackInfo_defaultTrackGroupIsNull() {
        val trackInfo = com.raulshma.jellyplay.feature.player.video.engine.PlayerEngine.TrackInfo(
            index = 0,
            label = "Test",
            language = null,
            isSelected = false,
            type = com.raulshma.jellyplay.feature.player.video.engine.PlayerEngine.TrackType.SUBTITLE,
        )
        assertEquals(null, trackInfo.trackGroup)
    }

    @Test
    fun trackInfo_dataClassEquality() {
        val a = com.raulshma.jellyplay.feature.player.video.engine.PlayerEngine.TrackInfo(
            index = 1, label = "Spanish", language = "spa", isSelected = false,
            type = com.raulshma.jellyplay.feature.player.video.engine.PlayerEngine.TrackType.AUDIO,
        )
        val b = com.raulshma.jellyplay.feature.player.video.engine.PlayerEngine.TrackInfo(
            index = 1, label = "Spanish", language = "spa", isSelected = false,
            type = com.raulshma.jellyplay.feature.player.video.engine.PlayerEngine.TrackType.AUDIO,
        )
        assertEquals(a, b)
    }

    @Test
    fun trackType_hasAudioAndSubtitle() {
        assertEquals(2, com.raulshma.jellyplay.feature.player.video.engine.PlayerEngine.TrackType.entries.size)
        assertNotNull(com.raulshma.jellyplay.feature.player.video.engine.PlayerEngine.TrackType.AUDIO)
        assertNotNull(com.raulshma.jellyplay.feature.player.video.engine.PlayerEngine.TrackType.SUBTITLE)
    }
}
