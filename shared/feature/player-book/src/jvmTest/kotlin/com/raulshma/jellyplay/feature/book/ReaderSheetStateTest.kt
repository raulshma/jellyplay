package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.datastore.reader.ReaderFontFamily
import com.raulshma.jellyplay.core.datastore.reader.ReaderSlice
import com.raulshma.jellyplay.core.datastore.reader.ReaderStore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the settings sheets' edit bundles (ReaderSheetState.kt): the
 * snapshot → bundle projections, and the pure commit diff the
 * ReaderPreferences apply* commands execute (unchanged bundles write
 * nothing; every moved axis is detected exactly once).
 */
class ReaderSheetStateTest {

    private val typography = ReaderTypographyState(
        fontFamily = ReaderFontFamily.SYSTEM,
        lineHeightPct = 160,
        marginPct = 20,
        justify = false,
        scrollMode = false,
    )

    private val behavior = ReaderBehaviorState(
        volumeKeyPaging = false,
        animatedPageTurns = true,
        readingSpeedWpm = 220,
        tocRailVisible = false,
    )

    @Test
    fun `projections read the snapshot's global axes`() {
        val snapshot = ReaderPrefsSnapshot(
            global = ReaderSlice(
                fontFamily = ReaderFontFamily.SERIF,
                lineHeightPct = 150,
                marginPct = 10,
                justify = true,
                scrollMode = true,
                volumeKeyPaging = true,
                animatedPageTurns = false,
                readingSpeedWpm = 300,
                tocRailVisible = true,
            ),
        )

        assertEquals(
            ReaderTypographyState(
                fontFamily = ReaderFontFamily.SERIF,
                lineHeightPct = 150,
                marginPct = 10,
                justify = true,
                scrollMode = true,
            ),
            snapshot.typographyState(),
        )
        assertEquals(
            ReaderBehaviorState(
                volumeKeyPaging = true,
                animatedPageTurns = false,
                readingSpeedWpm = 300,
                tocRailVisible = true,
            ),
            snapshot.behaviorState(),
        )
    }

    @Test
    fun `an unchanged bundle diffs to no writes`() {
        assertEquals(emptyList(), typography.changedAxes(typography))
        assertEquals(emptyList(), behavior.changedAxes(behavior))
    }

    @Test
    fun `each moved typography axis is detected alone`() {
        assertEquals(
            listOf(ReaderTypographyAxis.FONT_FAMILY),
            typography.copy(fontFamily = ReaderFontFamily.MONO).changedAxes(typography),
        )
        assertEquals(
            listOf(ReaderTypographyAxis.LINE_HEIGHT_PCT),
            typography.copy(lineHeightPct = 161).changedAxes(typography),
        )
        assertEquals(
            listOf(ReaderTypographyAxis.MARGIN_PCT),
            typography.copy(marginPct = 19).changedAxes(typography),
        )
        assertEquals(
            listOf(ReaderTypographyAxis.JUSTIFY),
            typography.copy(justify = true).changedAxes(typography),
        )
        assertEquals(
            listOf(ReaderTypographyAxis.SCROLL_MODE),
            typography.copy(scrollMode = true).changedAxes(typography),
        )
    }

    @Test
    fun `each moved behavior axis is detected alone`() {
        assertEquals(
            listOf(ReaderBehaviorAxis.VOLUME_KEY_PAGING),
            behavior.copy(volumeKeyPaging = true).changedAxes(behavior),
        )
        assertEquals(
            listOf(ReaderBehaviorAxis.ANIMATED_PAGE_TURNS),
            behavior.copy(animatedPageTurns = false).changedAxes(behavior),
        )
        assertEquals(
            listOf(ReaderBehaviorAxis.READING_SPEED_WPM),
            behavior.copy(readingSpeedWpm = 221).changedAxes(behavior),
        )
        assertEquals(
            listOf(ReaderBehaviorAxis.TOC_RAIL_VISIBLE),
            behavior.copy(tocRailVisible = true).changedAxes(behavior),
        )
    }

    @Test
    fun `an all-axes change reports the full write set`() {
        assertEquals(
            ReaderTypographyAxis.entries.toList(),
            typography.copy(
                fontFamily = ReaderFontFamily.SANS,
                lineHeightPct = typography.lineHeightPct + 1,
                marginPct = typography.marginPct + 1,
                justify = true,
                scrollMode = true,
            ).changedAxes(typography),
        )
        assertEquals(
            ReaderBehaviorAxis.entries.toList(),
            behavior.copy(
                volumeKeyPaging = true,
                animatedPageTurns = false,
                readingSpeedWpm = behavior.readingSpeedWpm + 10,
                tocRailVisible = true,
            ).changedAxes(behavior),
        )
    }

    @Test
    fun `the slider bands mirror the store bands`() {
        assertEquals(
            ReaderStore.MIN_LINE_HEIGHT_PCT..ReaderStore.MAX_LINE_HEIGHT_PCT,
            READER_LINE_HEIGHT_RANGE,
        )
        assertEquals(ReaderStore.MIN_MARGIN_PCT..ReaderStore.MAX_MARGIN_PCT, READER_MARGIN_RANGE)
        assertEquals(
            ReaderStore.MIN_AUTO_SCROLL_SPEED_PX_PER_SEC..ReaderStore.MAX_AUTO_SCROLL_SPEED_PX_PER_SEC,
            READER_AUTO_SCROLL_SPEED_RANGE,
        )
    }
}
