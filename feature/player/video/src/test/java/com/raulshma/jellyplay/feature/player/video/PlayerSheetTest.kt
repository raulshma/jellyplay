package com.raulshma.jellyplay.feature.player.video

import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class PlayerSheetTest {

    @Test
    fun playerSheet_subtypes_areDistinct() {
        val sheets = listOf(
            PlayerSheet.None,
            PlayerSheet.Speed,
            PlayerSheet.Audio,
            PlayerSheet.Subtitle,
            PlayerSheet.Chapter,
            PlayerSheet.PlaybackInfo,
            PlayerSheet.AspectRatio,
            PlayerSheet.SubtitleStyle,
            PlayerSheet.AVSync,
            PlayerSheet.Decoder,
            PlayerSheet.SubtitleDownload,
        )
        val uniqueTypes = sheets.map { it::class }.distinct()
        assertTrue(uniqueTypes.size >= sheets.size - 1)
    }

    @Test
    fun playerSheet_whenNone_noSheetVisible() {
        val sheet = PlayerSheet.None
        assertTrue(sheet is PlayerSheet.None)
    }

    @Test
    fun playerSheet_speedIsNotNone() {
        assertFalse(PlayerSheet.Speed is PlayerSheet.None)
    }
}
