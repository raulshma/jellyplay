package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleStyle
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Covers the reload-decision predicate extracted from [LibVlcPlayerEngine.onConfigChanged].
 *
 * The whole point: a subtitle *delay* change (offsetMs) must NOT be reported as
 * a style change, because libVLC applies delay live via setSpuDelay and must not
 * rebuild the media for it — only genuine font/color/position changes require a
 * reload.
 */
class SubtitleStyleDiffTest {

    @Test
    fun identicalStyles_reportNoChange() {
        val style = SubtitleStyle(applyCustomStyle = true, fontSize = 28)
        assertFalse(styleChangedExcludingDelay(style, style))
    }

    @Test
    fun delayOnlyChange_reportsNoChange() {
        val base = SubtitleStyle(applyCustomStyle = true, fontSize = 28)
        // Only offsetMs differs — the case EngineConfigBuilder mirrors into both
        // subtitleDelayMs and subtitleStyle.offsetMs.
        val onlyDelay = base.copy(offsetMs = 750L)
        assertFalse(styleChangedExcludingDelay(base, onlyDelay))
    }

    @Test
    fun delayChangeInBothDirections_reportsNoChange() {
        val base = SubtitleStyle(applyCustomStyle = true, offsetMs = 500L)
        assertFalse(styleChangedExcludingDelay(base, base.copy(offsetMs = -500L)))
    }

    @Test
    fun fontSizeChange_reportsChange() {
        val base = SubtitleStyle(applyCustomStyle = true, fontSize = 24)
        assertTrue(styleChangedExcludingDelay(base, base.copy(fontSize = 36)))
    }

    @Test
    fun fontColorChange_reportsChange() {
        val base = SubtitleStyle(applyCustomStyle = true, fontColor = SubtitleColor.WHITE)
        assertTrue(styleChangedExcludingDelay(base, base.copy(fontColor = SubtitleColor.YELLOW)))
    }

    @Test
    fun verticalPositionChange_reportsChange() {
        val base = SubtitleStyle(applyCustomStyle = true, verticalPosition = 0.05f)
        assertTrue(styleChangedExcludingDelay(base, base.copy(verticalPosition = 0.12f)))
    }

    @Test
    fun fontFamilyChange_reportsChange() {
        val base = SubtitleStyle(applyCustomStyle = true, fontFamilyPath = null)
        assertTrue(styleChangedExcludingDelay(base, base.copy(fontFamilyPath = "/fonts/x.ttf")))
    }

    @Test
    fun delayPlusFontChange_reportsChange() {
        // A combined delay + real style change must still reload for the style part.
        val base = SubtitleStyle(applyCustomStyle = true, fontSize = 24)
        val both = base.copy(offsetMs = 1000L, fontSize = 40)
        assertTrue(styleChangedExcludingDelay(base, both))
    }
}
