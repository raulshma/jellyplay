package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.datastore.reader.ReaderFontFamily
import com.raulshma.jellyplay.core.datastore.reader.ReaderStore

/**
 * The settings sheets' edit bundles and their commit-diff logic — the state
 * half of the former ReaderSheets.kt (the sheets themselves live one file per
 * sheet beside this one). All state is caller-owned; every sheet is pure
 * presentation + callbacks.
 */

/**
 * The reflowable typography bundle the settings sheet edits as one copy-on-
 * change value (chips/switches commit immediately; sliders commit on settle).
 * [ReaderPreferences.applyTypography] owns the which-axis-changed diff; the
 * individual setters remain the write surface.
 */
internal data class ReaderTypographyState(
    val fontFamily: ReaderFontFamily,
    val lineHeightPct: Int,
    val marginPct: Int,
    val justify: Boolean,
    val scrollMode: Boolean,
)

/**
 * The behavior bundle (volume-key paging, animated page turns, the chapter
 * tick rail, reading speed) — same copy-on-change edit contract as
 * [ReaderTypographyState]; [ReaderPreferences.applyBehavior] owns the diff.
 */
internal data class ReaderBehaviorState(
    val volumeKeyPaging: Boolean,
    val animatedPageTurns: Boolean,
    val readingSpeedWpm: Int,
    val tocRailVisible: Boolean = false,
)

/** Projects the snapshot's global axes into the typography sheet bundle. */
internal fun ReaderPrefsSnapshot.typographyState() = ReaderTypographyState(
    fontFamily = global.fontFamily,
    lineHeightPct = global.lineHeightPct,
    marginPct = global.marginPct,
    justify = global.justify,
    scrollMode = global.scrollMode,
)

/** Projects the snapshot's global axes into the behavior sheet bundle. */
internal fun ReaderPrefsSnapshot.behaviorState() = ReaderBehaviorState(
    volumeKeyPaging = global.volumeKeyPaging,
    animatedPageTurns = global.animatedPageTurns,
    readingSpeedWpm = global.readingSpeedWpm,
    tocRailVisible = global.tocRailVisible,
)

/** One typography axis a whole-bundle commit can move. */
internal enum class ReaderTypographyAxis {
    FONT_FAMILY, LINE_HEIGHT_PCT, MARGIN_PCT, JUSTIFY, SCROLL_MODE,
}

/** One behavior axis a whole-bundle commit can move. */
internal enum class ReaderBehaviorAxis {
    VOLUME_KEY_PAGING, ANIMATED_PAGE_TURNS, READING_SPEED_WPM, TOC_RAIL_VISIBLE,
}

/**
 * The typography commit diff, pure: which axes [next] moves relative to the
 * CURRENT bundle (the snapshot's projection). ReaderPreferences.applyTypography
 * executes this — one changed axis, one setter write — so an unchanged axis
 * never fires a persist. Declaration order is the write order; nothing
 * depends on it beyond determinism.
 */
internal fun ReaderTypographyState.changedAxes(current: ReaderTypographyState): List<ReaderTypographyAxis> =
    buildList {
        if (fontFamily != current.fontFamily) add(ReaderTypographyAxis.FONT_FAMILY)
        if (lineHeightPct != current.lineHeightPct) add(ReaderTypographyAxis.LINE_HEIGHT_PCT)
        if (marginPct != current.marginPct) add(ReaderTypographyAxis.MARGIN_PCT)
        if (justify != current.justify) add(ReaderTypographyAxis.JUSTIFY)
        if (scrollMode != current.scrollMode) add(ReaderTypographyAxis.SCROLL_MODE)
    }

/** The behavior twin of [ReaderTypographyState.changedAxes] (see its KDoc). */
internal fun ReaderBehaviorState.changedAxes(current: ReaderBehaviorState): List<ReaderBehaviorAxis> =
    buildList {
        if (volumeKeyPaging != current.volumeKeyPaging) add(ReaderBehaviorAxis.VOLUME_KEY_PAGING)
        if (animatedPageTurns != current.animatedPageTurns) add(ReaderBehaviorAxis.ANIMATED_PAGE_TURNS)
        if (readingSpeedWpm != current.readingSpeedWpm) add(ReaderBehaviorAxis.READING_SPEED_WPM)
        if (tocRailVisible != current.tocRailVisible) add(ReaderBehaviorAxis.TOC_RAIL_VISIBLE)
    }

/** The typography bundle's line-height slider band (the store band, verbatim). */
internal val READER_LINE_HEIGHT_RANGE =
    ReaderStore.MIN_LINE_HEIGHT_PCT..ReaderStore.MAX_LINE_HEIGHT_PCT

/** The typography bundle's margin slider band (the store band, verbatim). */
internal val READER_MARGIN_RANGE = ReaderStore.MIN_MARGIN_PCT..ReaderStore.MAX_MARGIN_PCT

/** The auto-scroll speed slider band (the store band, verbatim). */
internal val READER_AUTO_SCROLL_SPEED_RANGE =
    ReaderStore.MIN_AUTO_SCROLL_SPEED_PX_PER_SEC..ReaderStore.MAX_AUTO_SCROLL_SPEED_PX_PER_SEC
