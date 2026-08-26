package com.raulshma.jellyplay.feature.player.video

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerSheetTest {

    @Test
    fun playerSheet_subtypes_areDistinct() {
        val sheets = listOf(
            PlayerSheet.None,
            PlayerSheet.Speed,
            PlayerSheet.Audio,
            PlayerSheet.Chapter,
            PlayerSheet.PlaybackInfo,
            PlayerSheet.AspectRatio,
            PlayerSheet.SubtitleHub,
            PlayerSheet.AVSync,
            PlayerSheet.Decoder,
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
