package com.raulshma.jellyplay.feature.book

import kotlin.math.roundToInt

/**
 * "≈ N min left" from remaining epub.js location pages. Location pages are
 * [EPUB_LOCATION_PAGE_CHARS]-char units; the chapter label feeds off the
 * `relocated` event's per-chapter `displayed` pages while the book label
 * feeds off the whole-book location list (`total − current`) — the same
 * chars-per-page / words-per-minute math either way. Words per location page
 * = chars / [CHARS_PER_WORD] (the ~5.5 average English word length); minutes
 * = pages × words-per-page ÷ the user's words-per-minute setting.
 * Round-half-up keeps the label stable across recompositions of equal input.
 */
internal const val EPUB_LOCATION_PAGE_CHARS = 1024
internal const val CHARS_PER_WORD = 5.5

internal fun locationPagesMinutesRemaining(remainingPages: Int, wpm: Int): Int {
    if (remainingPages <= 0) return 0
    val wordsPerPage = EPUB_LOCATION_PAGE_CHARS / CHARS_PER_WORD
    return (remainingPages * wordsPerPage / wpm.coerceAtLeast(1)).roundToInt()
}
