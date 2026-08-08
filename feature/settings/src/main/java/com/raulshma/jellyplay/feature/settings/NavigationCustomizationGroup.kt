package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.model.NavigationStyle
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Apps
import com.composables.icons.tabler.outline.ArrowBarToDown
import com.composables.icons.tabler.outline.DeviceTv
import com.composables.icons.tabler.outline.Disc
import com.composables.icons.tabler.outline.Home
import com.composables.icons.tabler.outline.LayoutList
import com.composables.icons.tabler.outline.Menu2
import com.composables.icons.tabler.outline.Search
import com.raulshma.jellyplay.core.model.NavigationCustomizationPreferences
import com.raulshma.jellyplay.feature.settings.R

import com.raulshma.jellyplay.core.ui.tv.LocalTvMode

/**
 * Describes a single floating-navigation-bar item for the customization UI.
 *
 * Keys are the [Route] simple class names that [UserPreferences.hiddenNavItems]
 * and [UserPreferences.navItemOrder] operate on — these are the exact strings
 * used by the nav-bar composition in `JellyPlayApp`.
 */
private data class NavItemDescriptor(
    val key: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    @StringRes val subtitleRes: Int,
)

// The union of video + music top-level routes, in their default display order.
// Mirrors VIDEO_TOP_LEVEL_ROUTES / MUSIC_TOP_LEVEL_ROUTES in core:ui.
private val NAV_ITEMS: List<NavItemDescriptor> = listOf(
    NavItemDescriptor("Home", R.string.settings_nav_item_home, Tabler.Outline.Home, R.string.settings_nav_item_home_subtitle),
    NavItemDescriptor("Library", R.string.settings_nav_item_library, Tabler.Outline.LayoutList, R.string.settings_nav_item_library_subtitle),
    NavItemDescriptor("Search", R.string.settings_nav_item_search, Tabler.Outline.Search, R.string.settings_nav_item_search_subtitle),
    NavItemDescriptor("LiveTv", R.string.settings_nav_item_live_tv, Tabler.Outline.DeviceTv, R.string.settings_nav_item_live_tv_subtitle),
    NavItemDescriptor("MusicBrowse", R.string.settings_nav_item_browse, Tabler.Outline.Disc, R.string.settings_nav_item_browse_subtitle),
    NavItemDescriptor("Shortcuts", R.string.settings_nav_item_shortcuts, Tabler.Outline.Apps, R.string.settings_nav_item_shortcuts_subtitle),
)

/**
 * A settings group that lets the user customize the floating navigation bar:
 * toggle individual items on/off and reorder them by drag, toggle navigation style
 * (Expressive M3 vs Classic), plus toggle the hide-on-scroll behavior.
 *
 * Backed by [UserPreferences.hiddenNavItems] / [UserPreferences.navItemOrder] /
 * [UserPreferences.hideBottomNavOnScroll] / [UserPreferences.navigationStyle],
 * which the nav-bar composition already reads — so no further wiring is needed.
 */
@Composable
fun NavigationCustomizationGroup(
    preferences: NavigationCustomizationPreferences,
    viewModel: AppearanceSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val isTv = LocalTvMode.current
    val navItems = remember(isTv) {
        if (isTv) NAV_ITEMS else NAV_ITEMS.filter { it.key != "Shortcuts" }
    }

    SettingsGroup(
        icon = Tabler.Outline.Menu2,
        title = stringResource(R.string.settings_nav_bar_title),
        summary = {
            val visible = navItems.count { it.key !in preferences.hiddenNavItems }
            val scroll = stringResource(
                if (preferences.hideBottomNavOnScroll) R.string.settings_nav_auto_hide else R.string.settings_nav_pinned,
            )
            stringResource(R.string.settings_nav_items_summary_with_scroll, visible, navItems.size, scroll)
        },
        initiallyExpanded = true,
        modifier = modifier,
    ) {
        val total = navItems.size + 2 // items + hide-on-scroll + nav-style toggle
        var idx = 0

        SettingListItem(
            icon = Tabler.Outline.Menu2,
            title = stringResource(R.string.settings_nav_style),
            subtitle = stringResource(
                if (preferences.navigationStyle == NavigationStyle.EXPRESSIVE)
                    R.string.settings_nav_style_expressive_subtitle
                else
                    R.string.settings_nav_style_classic_subtitle
            ),
            trailingText = stringResource(
                if (preferences.navigationStyle == NavigationStyle.EXPRESSIVE)
                    R.string.settings_nav_style_expressive
                else
                    R.string.settings_nav_style_classic
            ),
            index = idx++, count = total,
            onClick = {
                val nextStyle = if (preferences.navigationStyle == NavigationStyle.EXPRESSIVE)
                    NavigationStyle.CLASSIC else NavigationStyle.EXPRESSIVE
                viewModel.setNavigationStyle(nextStyle)
            },
        )

        SettingToggleItem(
            icon = Tabler.Outline.ArrowBarToDown,
            title = stringResource(R.string.settings_nav_hide_on_scroll),
            subtitle = stringResource(
                if (preferences.hideBottomNavOnScroll) R.string.settings_nav_hide_on_scroll_on else R.string.settings_nav_hide_on_scroll_off,
            ),
            checked = preferences.hideBottomNavOnScroll,
            index = idx++, count = total,
            onCheckedChange = { viewModel.setHideBottomNavOnScroll(it) },
        )

        // ── Reorderable item list ──
        // Order is driven by a local mutable list seeded from the stored order,
        // reconciled whenever the stored order changes and no drag is in flight
        // (mirrors the Home Screen Layout reorder pattern).
        val navItemOrder = remember(preferences.navItemOrder, navItems) {
            resolveOrder(preferences.navItemOrder, navItems).toMutableStateList()
        }
        val itemHeights = remember { mutableStateMapOf<String, Int>() }
        var draggingKey by remember { mutableStateOf<String?>(null) }
        var dragOffsetY by remember { mutableFloatStateOf(0f) }

        fun persistOrder() {
            val currentOrder = navItemOrder.toList()
            if (currentOrder != preferences.navItemOrder) {
                viewModel.setNavItemOrder(currentOrder)
            }
        }

        fun moveItem(key: String, deltaY: Float) {
            if (draggingKey != key) return
            dragOffsetY += deltaY
            while (true) {
                val currentIndex = navItemOrder.indexOf(key)
                if (currentIndex == -1) return
                val draggedHeight = itemHeights[key] ?: return

                if (dragOffsetY > 0f && currentIndex < navItemOrder.lastIndex) {
                    val nextKey = navItemOrder[currentIndex + 1]
                    val nextHeight = itemHeights[nextKey] ?: draggedHeight
                    val threshold = (draggedHeight + nextHeight) / 2f
                    if (dragOffsetY > threshold) {
                        navItemOrder.removeAt(currentIndex)
                        navItemOrder.add(currentIndex + 1, key)
                        dragOffsetY -= nextHeight.toFloat()
                        continue
                    }
                }
                if (dragOffsetY < 0f && currentIndex > 0) {
                    val prevKey = navItemOrder[currentIndex - 1]
                    val prevHeight = itemHeights[prevKey] ?: draggedHeight
                    val threshold = (draggedHeight + prevHeight) / 2f
                    if (-dragOffsetY > threshold) {
                        navItemOrder.removeAt(currentIndex)
                        navItemOrder.add(currentIndex - 1, key)
                        dragOffsetY += prevHeight.toFloat()
                        continue
                    }
                }
                break
            }
        }

        Spacer(Modifier.height(8.dp))

        navItemOrder.forEachIndexed { index, key ->
            val descriptor = navItems.first { it.key == key }
            val enabled = key !in preferences.hiddenNavItems
            SettingReorderableToggleItem(
                icon = descriptor.icon,
                title = stringResource(descriptor.labelRes),
                subtitle = stringResource(descriptor.subtitleRes),
                checked = enabled,
                index = index,
                count = navItemOrder.size,
                modifier = Modifier.onSizeChanged { itemHeights[key] = it.height },
                onCheckedChange = { checked ->
                    val current = preferences.hiddenNavItems.toMutableSet()
                    if (checked) current.remove(key) else current.add(key)
                    viewModel.setHiddenNavItems(current)
                },
                onDrag = { delta -> moveItem(key, delta) },
                onDragStart = { draggingKey = key; dragOffsetY = 0f },
                onDragEnd = { draggingKey = null; persistOrder() },
            )
            idx++
        }
    }
}

/**
 * Resolves the stored nav-item order against the available [navItems]: known items
 * in their stored position, then any known items missing from the stored order
 * in their default order. Unknown stored keys are dropped.
 */
private fun resolveOrder(storedOrder: List<String>, navItems: List<NavItemDescriptor>): List<String> {
    val known = navItems.map { it.key }
    val ordered = storedOrder.filter { it in known }
    val missing = known.filter { it !in ordered }
    return ordered + missing
}
