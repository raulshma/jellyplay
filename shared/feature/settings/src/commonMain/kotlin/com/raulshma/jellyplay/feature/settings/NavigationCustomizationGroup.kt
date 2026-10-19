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
import com.composables.icons.tabler.outline.ArrowBarToDown
import com.composables.icons.tabler.outline.Menu2
import com.raulshma.jellyplay.core.model.NavigationCustomizationPreferences
import com.raulshma.jellyplay.core.ui.navigation.NAV_DESTINATIONS
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.SHORTCUTS_NAV_KEY
import com.raulshma.jellyplay.core.ui.reorder.rememberReorderableOrderedList
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

// The customizable floating-bar rows, DERIVED from core:ui's NAV_DESTINATIONS
// registry: the customizable-route subset (the video/music top-level union —
// Home, Library, Search, LiveTv, MusicBrowse — plus Shortcuts, i.e. exactly
// the routes the floating bar renders) in registry row order, each row's
// persisted key and icon read from its registry entry.
// The genuinely local facts are the faces the registry does not carry:
// localized label/subtitle resources per route (the "Browse" wording the
// customization row shares with TOP_LEVEL_LABEL_OVERRIDES' music-bar
// divergence). The label/subtitle maps' keys ARE the customizable-route
// enumeration; a route registered in core:ui but absent here simply gets no
// customization row.
private val NAV_ITEM_LABEL_RES: Map<Route, StringResource> = mapOf(
    Route.Home to Res.string.settings_nav_item_home,
    Route.Library to Res.string.settings_nav_item_library,
    Route.Search to Res.string.settings_nav_item_search,
    Route.LiveTv to Res.string.settings_nav_item_live_tv,
    Route.MusicBrowse to Res.string.settings_nav_item_browse,
    Route.Shortcuts to Res.string.settings_nav_item_shortcuts,
)

private val NAV_ITEM_SUBTITLE_RES: Map<Route, StringResource> = mapOf(
    Route.Home to Res.string.settings_nav_item_home_subtitle,
    Route.Library to Res.string.settings_nav_item_library_subtitle,
    Route.Search to Res.string.settings_nav_item_search_subtitle,
    Route.LiveTv to Res.string.settings_nav_item_live_tv_subtitle,
    Route.MusicBrowse to Res.string.settings_nav_item_browse_subtitle,
    Route.Shortcuts to Res.string.settings_nav_item_shortcuts_subtitle,
)

private val NAV_ITEMS: List<NavItemDescriptor> = NAV_DESTINATIONS
    .filter { it.route in NAV_ITEM_LABEL_RES }
    .map { row ->
        NavItemDescriptor(
            // row.key is the same string Route.navKey resolves to
            // (NAV_KEYS_BY_ROUTE derives from these rows) — the persisted
            // vocabulary is unchanged.
            key = row.key,
            labelRes = NAV_ITEM_LABEL_RES.getValue(row.route),
            icon = row.icon,
            subtitleRes = NAV_ITEM_SUBTITLE_RES.getValue(row.route),
        )
    }

/**
 * A settings group that lets the user customize the floating navigation bar:
 * toggle individual items on/off and reorder them by drag, plus toggle the
 * hide-on-scroll behavior.
 *
 * Backed by [com.raulshma.jellyplay.core.model.UserPreferences.hiddenNavItems] /
 * [com.raulshma.jellyplay.core.model.UserPreferences.navItemOrder] /
 * hideBottomNavOnScroll, which every nav shell applies through
 * [com.raulshma.jellyplay.core.ui.navigation.applyNavCustomization] — Android
 * floating bar and desktop rail alike.
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
        title = rowTitle(AppearanceRows.NavBarCustomization),
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
            title = rowTitle(AppearanceRows.NavHideOnScroll),
            subtitle = stringResource(
                if (preferences.hideBottomNavOnScroll) Res.string.settings_nav_hide_on_scroll_on else Res.string.settings_nav_hide_on_scroll_off,
            ),
            checked = preferences.hideBottomNavOnScroll,
            onCheckedChange = { viewModel.edit { scope -> scope.navigation.setHideBottomNavOnScroll(it) } },
        )

        // ── Reorderable item list ──
        // The shared reorderable-list holder owns the mirror/resync/persist
        // choreography around ReorderState (same shape as the Appearance
        // reorder lists); this site is just content: known nav items for the
        // resolveOrder seed and the persist write.
        val navOrder = rememberReorderableOrderedList(
            storedOrder = preferences.navItemOrder,
            knownOrder = navItems.map { it.key },
            onPersist = { order -> viewModel.edit { it.navigation.setNavItemOrder(order) } },
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
                    viewModel.edit { it.navigation.setHiddenNavItems(current) }
                },
                onDrag = { delta -> navOrder.onDrag(key, delta) },
                onDragStart = { navOrder.onDragStart(key) },
                onDragEnd = navOrder::onDragEnd,
            )
        }
        }
    }
}
