package com.raulshma.jellyplay.feature.book

import kotlin.math.roundToInt

/**
 * "≈ N min left in chapter". epub.js location pages are
 * [EPUB_LOCATION_PAGE_CHARS]-char units and the `relocated` event reports
 * them per CHAPTER (`displayed.total - displayed.page`), so the estimate is
 * chapter-scoped by construction — the label says so. Words per location
 * page = chars / [CHARS_PER_WORD] (the ~5.5 average English word length);
 * minutes = pages × words-per-page ÷ the user's words-per-minute setting.
 * Round-half-up keeps the label stable across recompositions of equal input.
 */
internal const val EPUB_LOCATION_PAGE_CHARS = 1024
internal const val CHARS_PER_WORD = 5.5

internal fun chapterMinutesRemaining(remainingPages: Int, wpm: Int): Int {
    if (remainingPages <= 0) return 0
    val wordsPerPage = EPUB_LOCATION_PAGE_CHARS / CHARS_PER_WORD
    return (remainingPages * wordsPerPage / wpm.coerceAtLeast(1)).roundToInt()
}
