package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
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

/**
 * A stable [ImageVector] for each [HomeSectionType], used by the home section
 * rows, the inline section-config sheet and the settings screens. Centralised
 * here so every surface shares a single mapping — the settings reorder list
 * previously spelled out its own partial `when` (with a `Folder` fallback)
 * that had already drifted from this one.
 *
 * Returns a sensible icon for every section type, falling back to a folder.
 */
@Composable
fun rememberHomeSectionIcon(type: HomeSectionType): ImageVector =
    remember(type) { homeSectionIcon(type) }

/** Non-composable variant for use outside composition (e.g. settings lists). */
fun homeSectionIcon(type: HomeSectionType): ImageVector = when (type) {
    HomeSectionType.CONTINUE_WATCHING -> Tabler.Outline.PlayerPlay
    HomeSectionType.NEXT_UP -> Tabler.Outline.PlayerSkipForward
    HomeSectionType.RECENTLY_ADDED -> Tabler.Outline.Clock
    HomeSectionType.LATEST_MEDIA -> Tabler.Outline.LayersLinked
    HomeSectionType.FAVORITES -> Tabler.Outline.Bookmark
    HomeSectionType.LIVE_TV -> Tabler.Outline.DeviceTv
    HomeSectionType.DOWNLOADED -> Tabler.Outline.Download
    HomeSectionType.RECOMMENDATIONS -> Tabler.Outline.Wand
    HomeSectionType.PINNED -> Tabler.Outline.Pinned
}
