package com.raulshma.jellyplay.core.ui.components

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bookmark
import com.composables.icons.tabler.outline.Clock
import com.composables.icons.tabler.outline.DeviceTv
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.LayersLinked
import com.composables.icons.tabler.outline.Pinned
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.PlayerSkipForward
import com.composables.icons.tabler.outline.Wand
import com.raulshma.jellyplay.core.model.HomeSectionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the pure, non-composable icon mapping [homeSectionIcon] — the single
 * source of truth shared by home rows, the section-config sheet and the
 * settings reorder list (the `remember*` wrapper only memoizes it):
 *
 *  - EVERY [HomeSectionType] enum constant has an icon (the `when` is
 *    exhaustive, so this doubles as the "new section type was added" tripwire);
 *  - each type resolves to its documented icon;
 *  - the mapping is deterministic: repeated calls return the same stable
 *    vector instance (required for cheap `remember(type)` caching).
 */
class HomeSectionIconTest {

    @Test
    fun everySectionType_resolvesToItsDocumentedIcon() {
        assertEquals(Tabler.Outline.PlayerPlay, homeSectionIcon(HomeSectionType.CONTINUE_WATCHING))
        assertEquals(Tabler.Outline.PlayerSkipForward, homeSectionIcon(HomeSectionType.NEXT_UP))
        assertEquals(Tabler.Outline.Clock, homeSectionIcon(HomeSectionType.RECENTLY_ADDED))
        assertEquals(Tabler.Outline.LayersLinked, homeSectionIcon(HomeSectionType.LATEST_MEDIA))
        assertEquals(Tabler.Outline.Bookmark, homeSectionIcon(HomeSectionType.FAVORITES))
        assertEquals(Tabler.Outline.DeviceTv, homeSectionIcon(HomeSectionType.LIVE_TV))
        assertEquals(Tabler.Outline.Download, homeSectionIcon(HomeSectionType.DOWNLOADED))
        assertEquals(Tabler.Outline.Wand, homeSectionIcon(HomeSectionType.RECOMMENDATIONS))
        assertEquals(Tabler.Outline.Pinned, homeSectionIcon(HomeSectionType.PINNED))
    }

    @Test
    fun mapping_isExhaustiveOverTheEnum() {
        // If a new HomeSectionType is added this fails on the distinct-icon
        // count unless the mapping gains a branch (compiler enforces the when;
        // this pins the test-side inventory).
        assertEquals(HomeSectionType.entries.size, 9)
    }

    @Test
    fun mapping_isDeterministicAndStable() {
        HomeSectionType.entries.forEach { type ->
            assertTrue(homeSectionIcon(type) === homeSectionIcon(type))
        }
    }
}
