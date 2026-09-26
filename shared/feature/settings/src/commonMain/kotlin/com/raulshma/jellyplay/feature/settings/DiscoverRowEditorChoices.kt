package com.raulshma.jellyplay.feature.settings

/**
 * The discover-row editor's selectable chip ladders, hoisted out of the
 * composable bodies as plain data so the exact rungs are pinnable in unit
 * tests (DiscoverRowEditorChoicesTest). The values ARE the shipped editor
 * behavior — changing a rung is a product decision, not a refactor. Label
 * FORMATTING (the "★ 7.5", "2020–2026", "30 d" texts) stays in the screen;
 * these are the raw selectable values only.
 */
object DiscoverRowEditorChoices {

    /** Min-rating / min-vote chip steps, shared by the Jellyfin and Seerr groups; 0f renders as "Any". */
    val ratingChipSteps: List<Float> = listOf(0f, 6f, 7f, 7.5f, 8f)

    /** Year-bucket chips, newest first (frozen decades plus the shipped 2020–2026 "recent" bucket). */
    val yearRanges: List<IntRange> = listOf(2020..2026, 2010..2019, 2000..2009, 1990..1999)

    /** "Added within" day chips; null renders as "Any". */
    val addedWithinDays: List<Int?> = listOf(null, 7, 30, 90, 365)

    /** "Premiered within" year chips; null renders as "Any". */
    val premieredWithinYears: List<Int?> = listOf(null, 1, 5, 10, 25)
}
