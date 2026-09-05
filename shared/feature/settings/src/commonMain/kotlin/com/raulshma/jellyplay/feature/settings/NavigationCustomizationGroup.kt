package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.SHORTCUTS_NAV_KEY
import com.raulshma.jellyplay.core.ui.navigation.navKey
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_auto_hide
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_bar_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_hide_on_scroll
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_hide_on_scroll_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_hide_on_scroll_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_item_browse
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_item_browse_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_item_home
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_item_home_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_item_library
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_item_library_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_item_live_tv
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_item_live_tv_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_item_search
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_item_search_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_item_shortcuts
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_item_shortcuts_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_items_summary_with_scroll
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_pinned

import com.raulshma.jellyplay.core.ui.tv.LocalTvMode

/**
 * Describes a single floating-navigation-bar item for the customization UI.
 *
 * Keys are the stable [Route.navKey] strings that
 * [com.raulshma.jellyplay.core.model.UserPreferences.hiddenNavItems]
 * and [com.raulshma.jellyplay.core.model.UserPreferences.navItemOrder] operate
 * on — the same strings every nav shell compares against via
 * [com.raulshma.jellyplay.core.ui.navigation.applyNavCustomization].
 */
private data class NavItemDescriptor(
    val key: String,
    val labelRes: StringResource,
    val icon: ImageVector,
    val subtitleRes: StringResource,
)

// The union of video + music top-level routes, in their default display order.
// Mirrors VIDEO_TOP_LEVEL_ROUTES / MUSIC_TOP_LEVEL_ROUTES in core:ui.
private val NAV_ITEMS: List<NavItemDescriptor> = listOf(
    NavItemDescriptor(Route.Home.navKey, Res.string.settings_nav_item_home, Tabler.Outline.Home, Res.string.settings_nav_item_home_subtitle),
    NavItemDescriptor(Route.Library.navKey, Res.string.settings_nav_item_library, Tabler.Outline.LayoutList, Res.string.settings_nav_item_library_subtitle),
    NavItemDescriptor(Route.Search.navKey, Res.string.settings_nav_item_search, Tabler.Outline.Search, Res.string.settings_nav_item_search_subtitle),
    NavItemDescriptor(Route.LiveTv.navKey, Res.string.settings_nav_item_live_tv, Tabler.Outline.DeviceTv, Res.string.settings_nav_item_live_tv_subtitle),
    NavItemDescriptor(Route.MusicBrowse.navKey, Res.string.settings_nav_item_browse, Tabler.Outline.Disc, Res.string.settings_nav_item_browse_subtitle),
    NavItemDescriptor(SHORTCUTS_NAV_KEY, Res.string.settings_nav_item_shortcuts, Tabler.Outline.Apps, Res.string.settings_nav_item_shortcuts_subtitle),
)

/**
 * A settings group that lets the user customize the floating navigation bar:
 * toggle individual items on/off and reorder them by drag, plus toggle the
 * hide-on-scroll behavior.
 *
 * Backed by [com.raulshma.jellyplay.core.model.UserPreferences.hiddenNavItems] /
 * [com.raulshma.jellyplay.core.model.UserPreferences.navItemOrder] /
 * hideBottomNavOnScroll, which every nav shell applies through
 * [com.raulshma.jellyplay.core.ui.navigation.applyNavCustomization] — Android
 * floating bar and desktop rail alike (#152).
 */
@Composable
fun NavigationCustomizationGroup(
    preferences: NavigationCustomizationPreferences,
    viewModel: AppearanceSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val isTv = LocalTvMode.current
    val navItems = remember(isTv) {
        if (isTv) NAV_ITEMS else NAV_ITEMS.filter { it.key != SHORTCUTS_NAV_KEY }
    }

    SettingsGroup(
        icon = Tabler.Outline.Menu2,
        title = stringResource(Res.string.settings_nav_bar_title),
        summary = {
            val visible = navItems.count { it.key !in preferences.hiddenNavItems }
            val scroll = stringResource(
                if (preferences.hideBottomNavOnScroll) Res.string.settings_nav_auto_hide else Res.string.settings_nav_pinned,
            )
            stringResource(Res.string.settings_nav_items_summary_with_scroll, visible, navItems.size, scroll)
        },
        initiallyExpanded = true,
        modifier = modifier,
    ) {
        SettingsItemList(total = navItems.size + 1) {
        SettingToggleItem(
            icon = Tabler.Outline.ArrowBarToDown,
            title = stringResource(Res.string.settings_nav_hide_on_scroll),
            subtitle = stringResource(
                if (preferences.hideBottomNavOnScroll) Res.string.settings_nav_hide_on_scroll_on else Res.string.settings_nav_hide_on_scroll_off,
            ),
            checked = preferences.hideBottomNavOnScroll,
            onCheckedChange = { viewModel.setHideBottomNavOnScroll(it) },
        )

        // ── Reorderable item list ──
        // The shared reorderable-list holder owns the mirror/resync/persist
        // choreography around ReorderState (same shape as the Appearance
        // reorder lists); this site is just content: known nav items for the
        // resolveOrder seed and the persist write.
        val navOrder = rememberReorderableOrderedList(
            storedOrder = preferences.navItemOrder,
            knownOrder = navItems.map { it.key },
            onPersist = viewModel::setNavItemOrder,
        )

        Spacer(Modifier.height(8.dp))

        navOrder.items.forEachIndexed { index, key ->
            val descriptor = navItems.first { it.key == key }
            val enabled = key !in preferences.hiddenNavItems
            SettingReorderableToggleItem(
                icon = descriptor.icon,
                title = stringResource(descriptor.labelRes),
                subtitle = stringResource(descriptor.subtitleRes),
                checked = enabled,
                index = index,
                count = navOrder.items.size,
                modifier = Modifier.onSizeChanged { navOrder.recordHeight(key, it.height) },
                onCheckedChange = { checked ->
                    val current = preferences.hiddenNavItems.toMutableSet()
                    if (checked) current.remove(key) else current.add(key)
                    viewModel.setHiddenNavItems(current)
                },
                onDrag = { delta -> navOrder.onDrag(key, delta) },
                onDragStart = { navOrder.onDragStart(key) },
                onDragEnd = navOrder::onDragEnd,
            )
        }
        }
    }
}

/**
 * Resolves a stored order against the [knownOrder]: known items in their
 * stored position, then any known items missing from the stored order in
 * their default [knownOrder] position. Unknown stored entries are dropped.
 */
internal fun <T> resolveOrder(storedOrder: List<T>, knownOrder: List<T>): List<T> {
    val ordered = storedOrder.filter { it in knownOrder }
    val missing = knownOrder.filter { it !in ordered }
    return ordered + missing
}
