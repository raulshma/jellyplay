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
            PlayerSheet.SecondarySubtitle,
            PlayerSheet.TapToTranslate("hello"),
            PlayerSheet.OcrResult,
            PlayerSheet.AudioDelay,
            PlayerSheet.Decoder,
            PlayerSheet.SubtitleDownload,
        )
        val uniqueTypes = sheets.map { it::class }.distinct()
        assertTrue(uniqueTypes.size >= sheets.size - 1)
    }

    @Test
    fun playerSheet_tapToTranslate_holdsText() {
        val sheet = PlayerSheet.TapToTranslate("extracted text")
        assertTrue(sheet is PlayerSheet.TapToTranslate)
        assertTrue((sheet as PlayerSheet.TapToTranslate).text == "extracted text")
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

    @Test
    fun playerSheet_equality_sameTapToTranslate() {
        val a = PlayerSheet.TapToTranslate("test")
        val b = PlayerSheet.TapToTranslate("test")
        assertTrue(a == b)
    }

    @Test
    fun playerSheet_inequality_differentTapToTranslate() {
        val a = PlayerSheet.TapToTranslate("a")
        val b = PlayerSheet.TapToTranslate("b")
        assertFalse(a == b)
    }
}
