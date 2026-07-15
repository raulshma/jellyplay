package com.raulshma.jellyplay.feature.settings

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

/**
 * Describes a single floating-navigation-bar item for the customization UI.
 *
 * Keys are the [Route] simple class names that [UserPreferences.hiddenNavItems]
 * and [UserPreferences.navItemOrder] operate on — these are the exact strings
 * used by the nav-bar composition in `JellyPlayApp`.
 */
private data class NavItemDescriptor(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val subtitle: String,
)

// The union of video + music top-level routes, in their default display order.
// Mirrors VIDEO_TOP_LEVEL_ROUTES / MUSIC_TOP_LEVEL_ROUTES in core:ui.
private val NAV_ITEMS: List<NavItemDescriptor> = listOf(
    NavItemDescriptor("Home", "Home", Tabler.Outline.Home, "Home dashboard"),
    NavItemDescriptor("Library", "Library", Tabler.Outline.LayoutList, "Browse your media libraries"),
    NavItemDescriptor("Search", "Search", Tabler.Outline.Search, "Find movies, shows & more"),
    NavItemDescriptor("LiveTv", "Live TV", Tabler.Outline.DeviceTv, "Live channels & guide"),
    NavItemDescriptor("MusicBrowse", "Browse", Tabler.Outline.Disc, "Music libraries (music mode)"),
    NavItemDescriptor("Shortcuts", "Shortcuts", Tabler.Outline.Apps, "Quick access shortcuts"),
)

/**
 * A settings group that lets the user customize the floating navigation bar:
 * toggle individual items on/off and reorder them by drag, plus toggle the
 * hide-on-scroll behavior ().
 *
 * Backed by [UserPreferences.hiddenNavItems] / [UserPreferences.navItemOrder] /
 * [UserPreferences.hideBottomNavOnScroll], which the nav-bar composition
 * already reads — so no further wiring is needed for changes to take effect.
 */
@Composable
fun NavigationCustomizationGroup(
    preferences: NavigationCustomizationPreferences,
    viewModel: AppearanceSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    SettingsGroup(
        icon = Tabler.Outline.Menu2,
        title = "Navigation Bar",
        summary = {
            val visible = NAV_ITEMS.count { it.key !in preferences.hiddenNavItems }
            val scroll = if (preferences.hideBottomNavOnScroll) "Auto-hide on scroll" else "Pinned"
            "$visible of ${NAV_ITEMS.size} items · $scroll"
        },
        initiallyExpanded = true,
        modifier = modifier,
    ) {
        val total = NAV_ITEMS.size + 1 // items + the hide-on-scroll toggle
        var idx = 0

        SettingToggleItem(
            icon = Tabler.Outline.ArrowBarToDown,
            title = "Hide on Scroll",
            subtitle = if (preferences.hideBottomNavOnScroll) "Bar hides when scrolling down" else "Bar stays visible",
            checked = preferences.hideBottomNavOnScroll,
            index = idx++, count = total,
            onCheckedChange = { viewModel.setHideBottomNavOnScroll(it) },
        )

        // ── Reorderable item list ──
        // Order is driven by a local mutable list seeded from the stored order,
        // reconciled whenever the stored order changes and no drag is in flight
        // (mirrors the Home Screen Layout reorder pattern).
        val navItemOrder = remember(preferences.navItemOrder) {
            resolveOrder(preferences.navItemOrder).toMutableStateList()
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
            val descriptor = NAV_ITEMS.first { it.key == key }
            val enabled = key !in preferences.hiddenNavItems
            SettingReorderableToggleItem(
                icon = descriptor.icon,
                title = descriptor.label,
                subtitle = descriptor.subtitle,
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
 * Resolves the stored nav-item order against the known [NAV_ITEMS]: known items
 * in their stored position, then any known items missing from the stored order
 * in their default order. Unknown stored keys are dropped.
 */
private fun resolveOrder(storedOrder: List<String>): List<String> {
    val known = NAV_ITEMS.map { it.key }
    val ordered = storedOrder.filter { it in known }
    val missing = known.filter { it !in ordered }
    return ordered + missing
}
