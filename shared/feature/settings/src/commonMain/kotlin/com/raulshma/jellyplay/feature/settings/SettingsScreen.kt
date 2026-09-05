package com.raulshma.jellyplay.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import com.raulshma.jellyplay.core.ui.animation.pressScaleValue
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.raulshma.jellyplay.core.network.library.buildUserImageUrl
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.ui.components.TopBarStyle
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.JellyPlayBackHandler
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.withHighlightSettingId
import androidx.compose.foundation.layout.statusBarsPadding
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.groupedItemContainerColor
import com.raulshma.jellyplay.core.designsystem.theme.hairlineBorderColor
import com.raulshma.jellyplay.core.designsystem.theme.lightModeHairlineBorder
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme
import com.raulshma.jellyplay.core.designsystem.theme.settingsGroupContainerColor
import com.raulshma.jellyplay.core.model.SettingsScreenPreferences
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.DreamImageCategory
import com.raulshma.jellyplay.core.model.DreamTransitionStyle
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.ThemeMode
import androidx.compose.foundation.lazy.itemsIndexed
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.feedback.rememberConfirmHaptic
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.TvFocusDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import com.raulshma.jellyplay.core.ui.settingssearch.ResolvedSettingsItem
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchMatcher
import com.raulshma.jellyplay.core.ui.components.ExpressiveChipContainer
import androidx.compose.ui.graphics.Brush
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_about
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_server_management
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_server_management_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_about_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_account
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_activity_insights
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_activity_insights_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_activity_queue
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_activity_queue_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_admin_badge
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_admin_dashboard
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_admin_dashboard_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_advanced_badge
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_advanced_enabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_appearance
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_player
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_backup_restore
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_backup_restore_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_biometric_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_browse_favorites
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_browse_favorites_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cache_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cancel
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_categories
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_categories_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_category_movies
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_category_music
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_category_tv
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_clear_recents
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_clear_search_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_connected_server
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_contrast_suffix
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_default_speed_value
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_disabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_display_media_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_downloads_storage
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dynamic_token
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_early_access_features
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_experimental
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_features_enabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_media_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_integrations
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_integrations_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_ken_burns
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_ken_burns_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_ken_burns_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_lang_default
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_language_subtitles
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_language_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_lock_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_member_badge
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_no_matches
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_notifications
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_notifications_checking
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_oled_token
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_performance_token
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pin_biometric_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pin_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_power_user_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_power_user_mode_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_no_matches_hint
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_clear_query
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_filter_all
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_browse_categories
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quick_actions
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_server_settings
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_switch_user_action
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_whats_new
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_privacy_data
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_privacy_data_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_recents_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_requests
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_requests_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_search_back_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_search_hint
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_search_placeholder
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_security
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_setup_wizard
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_setup_wizard_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_screensaver
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_signed_in_as_name
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out_confirm_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out_confirm_message_server
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out_confirm_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out_confirm_title_server
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out_from_server
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out_from_server_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_slideshow_interval
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_slideshow_interval_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_switch_user
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_switch_user_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_system
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_system_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_transition_crossfade
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_transition_none
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_transition_slide
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_transition_style
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_upcoming
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_upcoming_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_watch_history_heatmap
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_watch_history_heatmap_subtitle

private val LocalAnimateSettingsEntrance = staticCompositionLocalOf { false }

// Registry ids of the screensaver (dream) group rendered on the main Settings screen. When a
// settings-search result for one of these is tapped, the click sets lastClickedSettingId so the
// group expands and highlights the matching row (there is no dedicated screensaver screen).
private val SCREENSAVER_GROUP_IDS = setOf(
    "screensaver_show_title",
    "screensaver_categories",
    "screensaver_slideshow_interval",
    "screensaver_ken_burns",
    "screensaver_transition_style",
)

// Search-result ids that are destructive *actions* rather than settings (open a
// confirm dialog instead of navigating). These are deliberately excluded from the
// "recent settings" list — recents track navigable settings the user revisits, not
// one-off sign-out actions.
private val ACTION_ONLY_IDS = setOf("logout", "sign_out_from_server")

// Dream-screen pickers (slideshow interval, transition style) flow through the shared
// `PickerState` dispatcher rather than a screen-local sealed dialog enum.

/**
 * Bundles the navigation actions passed into [SettingsScreen] (and
 * [AppearanceSettingsScreen]'s drill-ins).
 *
 * Grouping them into a single `@Immutable` value lets the navigation call site
 * `remember` one instance, so the screen subtree is treated as skip-worthy by
 * the Compose compiler instead of recomposing on every parent state change
 * (each unstable lambda parameter would otherwise be a distinct stability
 * key). Mirrors the [com.raulshma.jellyplay.feature.home.HomeCallbacks]
 * pattern.
 *
 * [onNavigate] is the single seam for every sub-screen drill-in: the caller
 * passes the target [Route] with its `highlightSettingId` already set — e.g.
 * `Route.AppearanceSettings("theme_mode")` — so screens never grow a per-route
 * lambda again (this facade replaced a 28-lambda `SettingsCallbacks`).
 * [onSetupWizard] keeps its host-level indirection; [onLogout] and
 * [onCheckForUpdates] complete the host-provided action surface.
 *
 * Callers should construct via `remember(...) { SettingsNavActions(...) }` so
 * the same instance is reused across recompositions.
 */
@Immutable
data class SettingsNavActions(
    val onNavigate: (Route) -> Unit = {},
    val onLogout: () -> Unit = {},
    val onSetupWizard: () -> Unit = {},
    val onCheckForUpdates: () -> Unit = {},
)

/**
 * The shared column container for settings-search results (live matches and the
 * recents list). Both lists share the same padding, spacing, and TV back-key
 * handling (dismiss search + refocus the settings list); only the row content
 * differs, passed as [content]. Extracted so the container wiring can't drift
 * between the two branches the way the row rendering already can't.
 */
@Composable
private fun SearchResultsColumn(
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .then(Modifier.onDpadKeyEvent(
                onBack = { e ->
                    if (e.isKeyUp) { onBack() }
                    true
                },
            )),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        content = content,
    )
}

private fun highlightText(
    text: String,
    query: String,
    highlightColor: Color,
): androidx.compose.ui.text.AnnotatedString {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return androidx.compose.ui.text.AnnotatedString(text)
    val index = text.indexOf(trimmed, ignoreCase = true)
    if (index < 0) return androidx.compose.ui.text.AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, index))
        withStyle(
            SpanStyle(
                color = highlightColor,
                fontWeight = FontWeight.Bold,
            )
        ) {
            append(text.substring(index, index + trimmed.length))
        }
        append(text.substring(index + trimmed.length))
    }
}

/**
 * A single resolved settings-search result row, shared by the live search results
 * and the recent-settings list so both render identically (leading icon, title,
 * subtitle, category/advanced pills, chevron, expressive list shape, TV focus) and
 * share one tap handler.
 */
@Composable
private fun SettingsSearchResultRow(
    item: ResolvedSettingsItem,
    index: Int,
    count: Int,
    advancedBadgeLabel: String,
    query: String = "",
    onClick: () -> Unit,
) {
    val shape = com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape(index, count, innerRadius = 0.dp)
    val itemTvFocusState = rememberTvFocusState(focusedScale = 1.01f)
    ListItem(
        headlineContent = {
            Text(
                text = highlightText(item.title, query, MaterialTheme.colorScheme.primary),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = highlightText(item.subtitle, query, MaterialTheme.colorScheme.primary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(ShapeCache.smooth8)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = item.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (item.isAdvanced) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = advancedBadgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Icon(
                    imageVector = Tabler.Outline.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = groupedItemContainerColor(),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .lightModeHairlineBorder(shape)
            .then(itemTvFocusState.focusModifier)
            .tvFocusIndicator(itemTvFocusState, shape)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun SettingsCategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.05f)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else groupedItemContainerColor(darkAlpha = 0.4f),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else hairlineBorderColor(),
        ),
        modifier = modifier
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, CircleShape),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private data class QuickCategory(
    val title: String,
    val icon: ImageVector,
    val route: Route,
)

@Composable
private fun SettingsQuickCategoriesGrid(
    onNavigate: (Route) -> Unit,
    onDismissSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val categories = listOf(
        QuickCategory(stringResource(Res.string.settings_appearance), Tabler.Outline.Palette, Route.AppearanceSettings()),
        QuickCategory(stringResource(Res.string.settings_playback), Tabler.Outline.PlayerPlay, Route.PlaybackSettings()),
        QuickCategory(stringResource(Res.string.settings_audio_player), Tabler.Outline.Headphones, Route.AudioSettings()),
        QuickCategory(stringResource(Res.string.settings_language_subtitles), Tabler.Outline.Subtitles, Route.LanguageSettings()),
        QuickCategory(stringResource(Res.string.settings_downloads_storage), Tabler.Outline.Download, Route.StorageSettings()),
        QuickCategory(stringResource(Res.string.settings_security), Tabler.Outline.ShieldLock, Route.SecuritySettings()),
        QuickCategory(stringResource(Res.string.settings_server_management), Tabler.Outline.Server, Route.ServerManagement(null)),
        QuickCategory(stringResource(Res.string.settings_browse_favorites), Tabler.Outline.Heart, Route.Favorites),
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.chunked(2).forEach { rowPair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowPair.forEach { cat ->
                    val catFocusState = rememberTvFocusState(focusedScale = 1.02f)
                    Surface(
                        onClick = {
                            onDismissSearch()
                            onNavigate(cat.route)
                        },
                        shape = ShapeCache.smooth16,
                        color = groupedItemContainerColor(darkAlpha = 0.45f),
                        border = BorderStroke(1.dp, hairlineBorderColor().copy(alpha = 0.6f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .then(catFocusState.focusModifier)
                            .tvFocusIndicator(catFocusState, ShapeCache.smooth16)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(ShapeCache.smooth10)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = cat.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = cat.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                if (rowPair.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: (Boolean) -> Unit,
    navActions: SettingsNavActions,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val onNavigate = navActions.onNavigate
    val onSetupWizard = navActions.onSetupWizard
    val onNewsletterClick: () -> Unit = { onNavigate(Route.Newsletter) }
    val preferences = viewModel.preferences
    val userName = viewModel.currentUserName
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current

    val listFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val leadingFocusRequester = remember { FocusRequester() }
    val trailingFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    var animateEntrance by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animateEntrance = true
        viewModel.refreshCacheSize()
    }

    // On first TV entry, focus the search bar so the user can quickly type. On re-entry from a
    // sub-settings screen, focus the list instead — the restored scroll position puts the user
    // near where they left off, and tvFocusRestorer() on the LazyColumn restores the last-focused
    // child. Without the saveable flag, the search bar steals focus on every return, which the
    // user perceives as "focus reset to the top."
    var isFirstTvEntry by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    var lastClickedSettingId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (isTv) {
            kotlinx.coroutines.delay(150)
            if (isFirstTvEntry) {
                searchFocusRequester.tryRequestFocus()
                isFirstTvEntry = false
            } else {
                if (lastClickedSettingId != null) {
                    kotlinx.coroutines.delay(1000)
                    lastClickedSettingId = null
                } else {
                    listFocusRequester.tryRequestFocus()
                }
            }
        }
    }

    val currentServerAddress by viewModel.currentServerAddress.collectAsStateWithLifecycle()

    val backgroundColorState = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState()

    val searchBackCd = stringResource(Res.string.settings_search_back_cd)
    val clearSearchCd = stringResource(Res.string.settings_clear_search_cd)
    val newsletterCd = stringResource(Res.string.settings_newsletter_cd)
    val advLabel = stringResource(Res.string.settings_advanced_badge)
    val advancedEnabledMessage = stringResource(Res.string.settings_advanced_enabled)

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var signOutFromServer by remember { mutableStateOf(false) }
    var activeDialog by remember { mutableStateOf<PickerState<*>?>(null) }

    // Shared search-exit path: dismiss the panel and hand focus back to the
    // main list (TV focus policy depends on the list regaining focus).
    fun dismissSearchAndRefocus() {
        isSearchActive = false
        searchQuery = ""
        selectedCategory = null
        listFocusRequester.tryRequestFocus()
    }

    // Highlight-then-navigate choreography for this screen's rows — the same
    // dispatch onResultClick runs for search results: mark the pending TV
    // re-entry highlight, then inject the id as the route's deep-link target.
    val openSetting: (String, (String) -> Route) -> Unit = { id, buildRoute ->
        lastClickedSettingId = id
        onNavigate(buildRoute(id).withHighlightSettingId(id))
    }

    // Section scaffold: one lazy item wrapped in the staggered entrance, with
    // the TV step offset (the TV-only screensaver group shifts later rows).
    fun LazyListScope.settingsSection(
        key: String,
        phoneStep: Int,
        tvStep: Int = phoneStep,
        content: @Composable () -> Unit,
    ) = item(key = key) {
        AnimatedSettingsEntrance(if (isTv) tvStep else phoneStep) { content() }
    }

    // Debounced + off-main-thread fuzzy search. Each keystroke only re-runs the
    // matcher after a short quiet period, and the whole pipeline — the catalog
    // resolve included — runs on Dispatchers.Default so typing stays smooth on
    // low-end devices (the resolve is one blocking compose-resources read per
    // catalog entry when cold; see SettingsSearchCatalog.resolved). Matching
    // and the rendered results both reflect the user's language.
    val filteredItems by produceState(
        initialValue = emptyList<ResolvedSettingsItem>(),
        searchQuery,
    ) {
        snapshotFlow { searchQuery }
            .debounce(120)
            .distinctUntilChanged()
            .map { SettingsSearchMatcher.search(it, SettingsSearchCatalog.resolved()) }
            .flowOn(Dispatchers.Default)
            .collect { value = it }
    }

    val availableCategories = remember(filteredItems) {
        filteredItems.map { it.category }.distinct()
    }

    val displayItems = remember(filteredItems, selectedCategory) {
        if (selectedCategory != null) {
            filteredItems.filter { it.category == selectedCategory }
        } else {
            filteredItems
        }
    }

    // The last-used setting ids (most-recent first), resolved back to renderable
    // items against the catalog. Stale ids — a recorded setting whose catalog
    // entry no longer exists — drop out via mapNotNull and naturally age out as
    // new ids displace them. Only re-resolved when the persisted id list
    // changes; the catalog-wide resolve stays off the main thread even though
    // this producer itself runs on the composition dispatcher
    // (SettingsSearchCatalog.recentItems owns the Default hop).
    val recentIds by viewModel.recentSettingIds.collectAsStateWithLifecycle()
    val recentItems by produceState(
        initialValue = emptyList<ResolvedSettingsItem>(),
        recentIds,
    ) {
        value = SettingsSearchCatalog.recentItems(recentIds)
    }

    JellyPlayBackHandler(enabled = isSearchActive) {
        dismissSearchAndRefocus()
    }

    com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold(
        title = stringResource(Res.string.settings_title),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
        topBarStyle = TopBarStyle.None,
    ) { paddingValues ->
        val messenger = rememberSettingsMessenger()

        LaunchedEffect(viewModel.messageSentEvent) {
            viewModel.messageSentEvent?.let { msg ->
                messenger?.info(msg)
                viewModel.clearMessageEvent()
            }
        }

        // Shared tap handler for both the live search results and the recent
        // settings list: flips the advanced toggle on if needed, dispatches the
        // navigation, records the setting as recently used (skipping pure
        // actions like logout), then collapses the search panel. Extracted so the
        // two lists never drift in click behavior. Navigation is the same
        // one-liner as the home header search: inject the matched id as the
        // route's deep-link highlight target. Only the non-navigate special
        // cases (sign-out dialogs, the on-screen screensaver group, the
        // host-indirected setup wizard) keep bespoke branches.
        val onResultClick: (ResolvedSettingsItem) -> Unit = { item ->
            if (item.isAdvanced && !preferences.showAdvancedSettings) {
                viewModel.setShowAdvancedSettings(true)
                messenger?.info(advancedEnabledMessage)
            }
            if (item.id == "logout") {
                signOutFromServer = false
                showSignOutConfirm = true
            } else {
                when (item.route) {
                    Route.Settings -> {
                        // Bare-settings targets live on this screen: the
                        // sign-out-from-server action opens the confirm dialog,
                        // screensaver rows reveal their group.
                        if (item.id == "sign_out_from_server") {
                            signOutFromServer = true
                            showSignOutConfirm = true
                        } else {
                            lastClickedSettingId = item.id
                        }
                    }
                    Route.Onboarding -> {
                        lastClickedSettingId = "setup_wizard"
                        onSetupWizard()
                    }
                    else -> {
                        // Entries into sub-screens mark a pending highlight for
                        // the TV re-entry focus policy — except Server/User
                        // Management, which the old per-route dispatch never
                        // marked. Unknown highlight ids are no-ops downstream
                        // (rememberHighlightScrollIndex resolves them to -1).
                        if (item.route !is Route.ServerManagement && item.route !is Route.UserManagement) {
                            lastClickedSettingId = item.id
                        }
                        onNavigate(item.route.withHighlightSettingId(item.id))
                    }
                }
            }
            if (item.id !in ACTION_ONLY_IDS) viewModel.recordSettingUsed(item.id)
            // Dismiss search after navigation has been dispatched so the main
            // settings list doesn't briefly reveal during the transition.
            isSearchActive = false
            searchQuery = ""
            selectedCategory = null
        }

        // Admin session polling is tied to screen visibility so it only runs
        // while settings is in the foreground, not for the VM's whole lifetime.
        // Key on the user id so the effect re-runs once the async `currentUser`
        // load resolves — on first entry currentUser is still null, so keying on
        // Unit would never start polling for an admin who stays on the screen.
        val currentUserId = viewModel.currentUser?.id
        androidx.lifecycle.compose.LifecycleStartEffect(currentUserId) {
            if (viewModel.currentUser?.isAdmin == true) {
                viewModel.startSessionAutoRefresh()
            }
            onStopOrDispose { viewModel.stopSessionAutoRefresh() }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Search Bar / Navigation Header
                if (!isSearchActive) {
                    if (isTv) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = adaptiveInfo.contentPadding(LocalTvMode.current),
                                    end = adaptiveInfo.contentPadding(LocalTvMode.current),
                                    top = 16.dp,
                                    bottom = 8.dp
                                )
                        ) {
                            SettingsTvCollapsedSearchRow(
                                onSearchClicked = {
                                    isSearchActive = true
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(100)
                                        searchFocusRequester.tryRequestFocus()
                                    }
                                },
                                searchBoxFocusRequester = searchFocusRequester
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = adaptiveInfo.contentPadding(LocalTvMode.current),
                                    end = adaptiveInfo.contentPadding(LocalTvMode.current),
                                    top = 16.dp,
                                    bottom = 8.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SettingsIconButton(
                                onClick = onBack,
                                icon = Tabler.Outline.ArrowLeft,
                                contentDescription = searchBackCd,
                                modifier = Modifier.size(44.dp),
                            )
                            Surface(
                                onClick = {
                                    isSearchActive = true
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(100)
                                        searchFocusRequester.tryRequestFocus()
                                    }
                                },
                                shape = CircleShape,
                                color = groupedItemContainerColor(darkAlpha = 0.4f),
                                border = BorderStroke(1.dp, hairlineBorderColor()),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Tabler.Outline.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(Res.string.settings_search_placeholder),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = adaptiveInfo.contentPadding(LocalTvMode.current),
                                end = adaptiveInfo.contentPadding(LocalTvMode.current),
                                top = 16.dp,
                                bottom = 8.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsIconButton(
                            onClick = { dismissSearchAndRefocus() },
                            icon = Tabler.Outline.ArrowLeft,
                            contentDescription = searchBackCd,
                            iconSize = 20.dp,
                            modifier = Modifier
                                .focusRequester(leadingFocusRequester)
                                .onDpadKey(
                                    onRight = {
                                        searchFocusRequester.tryRequestFocus()
                                        true
                                    }
                                )
                        )

                        Surface(
                            shape = ShapeCache.smooth16,
                            color = groupedItemContainerColor(darkAlpha = 0.4f),
                            border = BorderStroke(
                                width = if (isSearchFocused && isTv) TvFocusDefaults.BorderWidth else 1.dp,
                                color = if (isSearchFocused && isTv) MaterialTheme.colorScheme.primary else hairlineBorderColor()
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .then(
                                    if (isSearchFocused && isTv) {
                                        Modifier.shadow(
                                            elevation = TvFocusDefaults.GlowElevation,
                                            shape = ShapeCache.smooth16,
                                            clip = false,
                                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = TvFocusDefaults.GlowAmbientAlpha),
                                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = TvFocusDefaults.GlowSpotAlpha),
                                        )
                                    } else Modifier
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Tabler.Outline.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )

                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = stringResource(Res.string.settings_search_placeholder),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(searchFocusRequester)
                                            .onFocusEvent { isSearchFocused = it.isFocused }
                                            .onDpadKeyEvent(
                                                onLeft = {
                                                    leadingFocusRequester.tryRequestFocus()
                                                    true
                                                },
                                                onRight = {
                                                    if (searchQuery.isNotEmpty()) {
                                                        trailingFocusRequester.tryRequestFocus()
                                                        true
                                                    } else false
                                                },
                                                onBack = { e ->
                                                    if (e.isKeyUp) {
                                                        dismissSearchAndRefocus()
                                                    }
                                                    true
                                                }
                                            )
                                    )
                                }

                                if (searchQuery.isNotEmpty()) {
                                    SettingsIconButton(
                                        onClick = { searchQuery = "" },
                                        icon = Tabler.Outline.X,
                                        contentDescription = clearSearchCd,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        iconSize = 18.dp,
                                        modifier = Modifier
                                            .focusRequester(trailingFocusRequester)
                                            .onDpadKey(
                                                onLeft = {
                                                    searchFocusRequester.tryRequestFocus()
                                                    true
                                                }
                                            )
                                    )
                                }
                            }
                        }
                    }
                }

                if (isSearchActive) {
                    if (availableCategories.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            contentPadding = PaddingValues(horizontal = adaptiveInfo.contentPadding(LocalTvMode.current)),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            item(key = "cat_all") {
                                SettingsCategoryChip(
                                    label = stringResource(Res.string.settings_filter_all),
                                    selected = selectedCategory == null,
                                    onClick = { selectedCategory = null },
                                )
                            }
                            items(availableCategories, key = { it }) { cat ->
                                SettingsCategoryChip(
                                    label = cat,
                                    selected = selectedCategory == cat,
                                    onClick = {
                                        selectedCategory = if (selectedCategory == cat) null else cat
                                    },
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        when {
                            searchQuery.isNotBlank() && displayItems.isNotEmpty() -> {
                                SearchResultsColumn(
                                    onBack = { dismissSearchAndRefocus() }
                                ) {
                                    itemsIndexed(displayItems, key = { _, item -> item.id }) { index, item ->
                                        SettingsSearchResultRow(
                                            item = item,
                                            query = searchQuery,
                                            index = index,
                                            count = displayItems.size,
                                            advancedBadgeLabel = advLabel,
                                            onClick = { onResultClick(item) },
                                        )
                                    }
                                }
                            }
                            searchQuery.isNotBlank() && displayItems.isEmpty() -> {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = adaptiveInfo.contentPadding(LocalTvMode.current)),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(vertical = 24.dp)
                                ) {
                                    item(key = "no_matches_banner") {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                modifier = Modifier.size(56.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Tabler.Outline.Search,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(16.dp))
                                            Text(
                                                text = stringResource(Res.string.settings_no_matches),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = stringResource(Res.string.settings_no_matches_hint),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                            if (selectedCategory != null) {
                                                Spacer(Modifier.height(12.dp))
                                                TextButton(onClick = { selectedCategory = null }) {
                                                    Text(stringResource(Res.string.settings_filter_all))
                                                }
                                            }
                                        }
                                    }
                                    item(key = "categories_header") {
                                        Text(
                                            text = stringResource(Res.string.settings_browse_categories),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                        )
                                    }
                                    item(key = "categories_grid") {
                                        SettingsQuickCategoriesGrid(
                                            onNavigate = onNavigate,
                                            onDismissSearch = { dismissSearchAndRefocus() }
                                        )
                                    }
                                }
                            }
                            searchQuery.isBlank() && recentItems.isNotEmpty() -> {
                                SearchResultsColumn(
                                    onBack = { dismissSearchAndRefocus() }
                                ) {
                                    item(key = "recents_header") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = stringResource(Res.string.settings_recents_title),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            TextButton(onClick = { viewModel.clearRecentSettings() }) {
                                                Text(stringResource(Res.string.settings_clear_recents))
                                            }
                                        }
                                    }
                                    itemsIndexed(recentItems, key = { _, item -> item.id }) { index, item ->
                                        SettingsSearchResultRow(
                                            item = item,
                                            query = "",
                                            index = index,
                                            count = recentItems.size,
                                            advancedBadgeLabel = advLabel,
                                            onClick = { onResultClick(item) },
                                        )
                                    }
                                    item(key = "browse_cats_header") {
                                        Text(
                                            text = stringResource(Res.string.settings_browse_categories),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp)
                                        )
                                    }
                                    item(key = "browse_cats_grid") {
                                        SettingsQuickCategoriesGrid(
                                            onNavigate = onNavigate,
                                            onDismissSearch = { dismissSearchAndRefocus() }
                                        )
                                    }
                                }
                            }
                            else -> {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = adaptiveInfo.contentPadding(LocalTvMode.current)),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(vertical = 16.dp)
                                ) {
                                    item(key = "browse_header") {
                                        Text(
                                            text = stringResource(Res.string.settings_browse_categories),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                        )
                                    }
                                    item(key = "browse_grid") {
                                        SettingsQuickCategoriesGrid(
                                            onNavigate = onNavigate,
                                            onDismissSearch = { dismissSearchAndRefocus() }
                                        )
                                    }
                                    item(key = "hint") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = stringResource(Res.string.settings_search_hint),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {

                CompositionLocalProvider(LocalAnimateSettingsEntrance provides animateEntrance) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding()
                            .then(Modifier
                                .tvFocusRestorer()
                                .focusRequester(listFocusRequester)
                                .onDpadKeyEvent(
                                    onBack = { e ->
                                        if (e.isKeyUp) { onBack() }
                                        true
                                    },
                                )
                            ),
                        state = lazyListState,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(
                            start = adaptiveInfo.contentPadding(LocalTvMode.current),
                            end = adaptiveInfo.contentPadding(LocalTvMode.current),
                            bottom = adaptiveInfo.bottomPadding(LocalTvMode.current),
                        ),
                    ) {
                        settingsSection("profile", 0) {
                            if (userName.isNotBlank()) {
                                SettingsProfileBanner(
                                    userName = userName,
                                    currentUser = viewModel.currentUser,
                                    serverAddress = currentServerAddress,
                                    isAdmin = viewModel.currentUser?.isAdmin == true,
                                    onNewsletterClick = onNewsletterClick,
                                    onUserManagementClick = { onNavigate(Route.UserManagement(null)) },
                                    onServerManagementClick = { onNavigate(Route.ServerManagement(null)) },
                                )
                            }
                        }

                        settingsSection("power_user_mode", 1) {
                            PowerUserModeCard(
                                checked = preferences.showAdvancedSettings,
                                onCheckedChange = { viewModel.setShowAdvancedSettings(it) },
                            )
                        }

                        settingsSection("active_devices", 2) {
                            if (viewModel.currentUser?.isAdmin == true && viewModel.activeSessions.isNotEmpty()) {
                                ActiveDevicesRow(
                                    sessions = viewModel.activeSessions,
                                    serverAddress = currentServerAddress,
                                    onSendMessage = viewModel::sendMessageToSession,
                                )
                            }
                        }

                        settingsSection("account", 3) {
                            SettingsGroup(
                                icon = Tabler.Outline.User,
                                title = stringResource(Res.string.settings_account),
                                summary = { stringResource(Res.string.settings_signed_in_as_name, userName) },
                                badge = {
                                    RoleBadge(isAdmin = viewModel.currentUser?.isAdmin == true)
                                },
                                initiallyExpanded = false,
                            ) {
                                SettingListItem(
                                    icon = Tabler.Outline.Server,
                                    title = stringResource(Res.string.settings_server_management),
                                    subtitle = stringResource(Res.string.settings_server_management_subtitle),
                                    index = 0, count = 4,
                                    onClick = { openSetting("server_management") { Route.ServerManagement(it) } },
                                )
                                SettingListItem(
                                    icon = Tabler.Outline.Users,
                                    title = stringResource(Res.string.settings_switch_user),
                                    subtitle = stringResource(Res.string.settings_switch_user_subtitle),
                                    index = 1, count = 4,
                                    onClick = { openSetting("user_management") { Route.UserManagement(it) } },
                                )
                                SettingListItem(
                                    icon = Tabler.Outline.Logout,
                                    title = stringResource(Res.string.settings_sign_out),
                                    subtitle = stringResource(Res.string.settings_sign_out_subtitle),
                                    index = 2, count = 4,
                                    isDestructive = true,
                                    onClick = {
                                        signOutFromServer = false
                                        showSignOutConfirm = true
                                    },
                                )
                                SettingListItem(
                                    icon = Tabler.Outline.Logout,
                                    title = stringResource(Res.string.settings_sign_out_from_server),
                                    subtitle = stringResource(Res.string.settings_sign_out_from_server_subtitle),
                                    index = 3, count = 4,
                                    isDestructive = true,
                                    onClick = {
                                        signOutFromServer = true
                                        showSignOutConfirm = true
                                    },
                                )
                            }
                        }

                        settingsSection("activity", 4) {
                            val pendingCount = viewModel.pendingRequestCount.collectAsStateWithLifecycle().value
                            SettingsGroup(
                                icon = Tabler.Outline.Activity,
                                title = stringResource(Res.string.settings_activity_insights),
                                summary = { stringResource(Res.string.settings_activity_insights_subtitle) },
                                badge = if (pendingCount > 0) {
                                    {
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "$pendingCount pending",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        }
                                    }
                                } else null,
                                initiallyExpanded = false,
                            ) {
                                val insightsCount = 5
                                SettingListItem(
                                    icon = Tabler.Outline.Heart,
                                    title = stringResource(Res.string.settings_browse_favorites),
                                    subtitle = stringResource(Res.string.settings_browse_favorites_subtitle),
                                    index = 0, count = insightsCount,
                                    onClick = { openSetting("favorites") { Route.Favorites } },
                                )
                                SettingListItem(
                                    icon = Tabler.Outline.ChartBar,
                                    title = stringResource(Res.string.settings_watch_history_heatmap),
                                    subtitle = stringResource(Res.string.settings_watch_history_heatmap_subtitle),
                                    index = 1, count = insightsCount,
                                    onClick = { openSetting("watch_progress_heatmap") { Route.WatchProgressHeatmap } },
                                )
                                SettingListItem(
                                    icon = Tabler.Outline.Database,
                                    title = stringResource(Res.string.settings_activity_queue),
                                    subtitle = stringResource(Res.string.settings_activity_queue_subtitle),
                                    index = 2, count = insightsCount,
                                    onClick = { openSetting("activity_queue") { Route.ArrQueue } },
                                )
                                SettingListItem(
                                    icon = Tabler.Outline.CalendarEvent,
                                    title = stringResource(Res.string.settings_upcoming),
                                    subtitle = stringResource(Res.string.settings_upcoming_subtitle),
                                    index = 3, count = insightsCount,
                                    onClick = { openSetting("upcoming") { Route.UpcomingCalendar } },
                                )
                                SettingListItem(
                                    icon = Tabler.Outline.Inbox,
                                    title = stringResource(Res.string.settings_requests),
                                    subtitle = stringResource(Res.string.settings_requests_subtitle),
                                    index = 4, count = insightsCount,
                                    trailingText = pendingCount.takeIf { it > 0 }?.toString(),
                                    onClick = { openSetting("requests") { Route.Requests } },
                                )
                            }
                        }

                        settingsSection("system", 5) {
                            val activeSessionCount = viewModel.activeSessions.size
                            SettingsGroup(
                                icon = Tabler.Outline.Adjustments,
                                title = stringResource(Res.string.settings_system),
                                summary = { stringResource(Res.string.settings_system_subtitle) },
                                badge = if (viewModel.currentUser?.isAdmin == true && activeSessionCount > 0) {
                                    {
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "$activeSessionCount active",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        }
                                    }
                                } else null,
                                initiallyExpanded = false,
                            ) {
                                val systemCount = if (viewModel.currentUser?.isAdmin == true) 3 else 2
                                var systemIndex = 0
                                if (viewModel.currentUser?.isAdmin == true) {
                                    SettingListItem(
                                        icon = Tabler.Outline.Shield,
                                        title = stringResource(Res.string.settings_admin_dashboard),
                                        subtitle = stringResource(Res.string.settings_admin_dashboard_subtitle),
                                        index = systemIndex++, count = systemCount,
                                        onClick = { openSetting("admin_dashboard") { Route.AdminDashboard } },
                                    )
                                }
                                SettingListItem(
                                    icon = Tabler.Outline.Wand,
                                    title = stringResource(Res.string.settings_setup_wizard),
                                    subtitle = stringResource(Res.string.settings_setup_wizard_subtitle),
                                    index = systemIndex++, count = systemCount,
                                    onClick = {
                                        lastClickedSettingId = "setup_wizard"
                                        onSetupWizard()
                                    },
                                )
                            }
                        }

                        settingsSection("item_appearance", 6) {
                            SettingListItem(
                                icon = Tabler.Outline.Palette,
                                title = stringResource(Res.string.settings_appearance),
                                subtitle = buildAppearanceSummary(preferences),
                                index = 0, count = 1,
                                onClick = { openSetting("appearance") { Route.AppearanceSettings(it) } },
                            )
                        }

                        settingsSection("item_playback", 7) {
                            SettingListItem(
                                icon = Tabler.Outline.PlayerPlay,
                                title = stringResource(Res.string.settings_playback),
                                subtitle = stringResource(Res.string.settings_playback_subtitle, preferences.preferredPlayer.displayName),
                                index = 0, count = 1,
                                onClick = { openSetting("playback") { Route.PlaybackSettings(it) } },
                            )
                        }

                        settingsSection("item_audio", 8) {
                            SettingListItem(
                                icon = Tabler.Outline.Music,
                                title = stringResource(Res.string.settings_audio_player),
                                subtitle = stringResource(Res.string.settings_default_speed_value, if (preferences.audioDefaultSpeed == 1.0f) "1x" else "${preferences.audioDefaultSpeed}x"),
                                index = 0, count = 1,
                                onClick = { openSetting("audio") { Route.AudioSettings(it) } },
                            )
                        }

                        settingsSection("item_language", 9) {
                            SettingListItem(
                                icon = Tabler.Outline.Language,
                                title = stringResource(Res.string.settings_language_subtitles),
                                subtitle = stringResource(Res.string.settings_language_subtitle, preferences.preferredAudioLanguage ?: stringResource(Res.string.settings_lang_default)),
                                index = 0, count = 1,
                                onClick = { openSetting("language") { Route.LanguageSettings(it) } },
                            )
                        }

                        settingsSection("item_notifications", 10) {
                            val notifPrefs = preferences.notificationPreferences
                            SettingListItem(
                                icon = Tabler.Outline.Bell,
                                title = stringResource(Res.string.settings_notifications),
                                subtitle = if (notifPrefs.enabled) stringResource(Res.string.settings_notifications_checking, notifPrefs.checkFrequency.displayName.lowercase()) else stringResource(Res.string.settings_disabled),
                                index = 0, count = 1,
                                onClick = { openSetting("notifications") { Route.NotificationSettings(it) } },
                            )
                        }

                        settingsSection("item_storage", 11) {
                            SettingListItem(
                                icon = Tabler.Outline.Database,
                                title = stringResource(Res.string.settings_downloads_storage),
                                subtitle = stringResource(Res.string.settings_cache_subtitle, viewModel.cacheSizeMb),
                                index = 0, count = 1,
                                onClick = { openSetting("storage") { Route.StorageSettings(it) } },
                            )
                        }

                        settingsSection("item_security", 12) {
                            SettingListItem(
                                icon = Tabler.Outline.Lock,
                                title = stringResource(Res.string.settings_security),
                                subtitle = when {
                                    preferences.pinLockEnabled && preferences.biometricLockEnabled -> stringResource(Res.string.settings_pin_biometric_on)
                                    preferences.biometricLockEnabled -> stringResource(Res.string.settings_biometric_on)
                                    preferences.pinLockEnabled -> stringResource(Res.string.settings_pin_on)
                                    else -> stringResource(Res.string.settings_lock_off)
                                },
                                index = 0, count = 1,
                                onClick = { openSetting("security") { Route.SecuritySettings(it) } },
                            )
                        }

                        settingsSection("item_privacy_data", 13) {
                            SettingListItem(
                                icon = Tabler.Outline.ShieldLock,
                                title = stringResource(Res.string.settings_privacy_data),
                                subtitle = stringResource(Res.string.settings_privacy_data_subtitle),
                                index = 0, count = 1,
                                onClick = { openSetting("privacy_data") { Route.PrivacyData(it) } },
                            )
                        }

                        settingsSection("item_backup", 14) {
                            SettingListItem(
                                icon = Tabler.Outline.DatabaseExport,
                                title = stringResource(Res.string.settings_backup_restore),
                                subtitle = stringResource(Res.string.settings_backup_restore_subtitle),
                                index = 0, count = 1,
                                onClick = { openSetting("backup") { Route.BackupSettings(it) } },
                            )
                        }

                        if (isTv) {
                            settingsSection("group_screensaver", 15) {
                                SettingsGroup(
                                    icon = Tabler.Outline.Moon,
                                    title = stringResource(Res.string.settings_screensaver),
                                    summary = {
                                        val cats = preferences.dreamImageCategories
                                        remember(cats) {
                                            cats.joinToString(", ") { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
                                        }
                                    },
                                    initiallyExpanded = lastClickedSettingId in SCREENSAVER_GROUP_IDS,
                                ) {
                                    val dreamTotal = 5
                                    val slideshowIntervalTitle = stringResource(Res.string.settings_slideshow_interval)
                                    val transitionStyleTitle = stringResource(Res.string.settings_transition_style)
                                    val transitionCrossfadeLabel = stringResource(Res.string.settings_transition_crossfade)
                                    val transitionSlideLabel = stringResource(Res.string.settings_transition_slide)
                                    val transitionNoneLabel = stringResource(Res.string.settings_transition_none)
                                    SettingToggleItem(
                                        icon = Tabler.Outline.Typography,
                                        title = stringResource(Res.string.settings_show_title),
                                        subtitle = if (preferences.dreamShowTitle) stringResource(Res.string.settings_display_media_title) else stringResource(Res.string.settings_hide_media_title),
                                        checked = preferences.dreamShowTitle,
                                        index = 0, count = dreamTotal,
                                        highlighted = lastClickedSettingId == "screensaver_show_title",
                                        onCheckedChange = { viewModel.setDreamShowTitle(it) },
                                    )
                                    val categoryMovies = stringResource(Res.string.settings_category_movies)
                                    val categoryTv = stringResource(Res.string.settings_category_tv)
                                    val categoryMusic = stringResource(Res.string.settings_category_music)
                                    SettingListItem(
                                        icon = Tabler.Outline.Movie,
                                        title = stringResource(Res.string.settings_categories),
                                        subtitle = stringResource(Res.string.settings_categories_subtitle),
                                        trailingText = remember(preferences.dreamImageCategories, categoryMovies, categoryTv, categoryMusic) {
                                            preferences.dreamImageCategories.joinToString(", ") {
                                                when (it) {
                                                    DreamImageCategory.MOVIES -> categoryMovies
                                                    DreamImageCategory.SERIES -> categoryTv
                                                    DreamImageCategory.MUSIC -> categoryMusic
                                                }
                                            }
                                        },
                                        index = 1, count = dreamTotal,
                                        highlighted = lastClickedSettingId == "screensaver_categories",
                                        onClick = {
                                            val allCats = DreamImageCategory.entries.toSet()
                                            val current = preferences.dreamImageCategories
                                            val next = if (current.size == allCats.size) {
                                                setOf(DreamImageCategory.MOVIES)
                                            } else {
                                                val cycle = allCats.toList()
                                                val nextIndex = current.size
                                                cycle.take(nextIndex + 1).toSet()
                                            }
                                            viewModel.setDreamImageCategories(next)
                                        },
                                    )
                                    SettingListItem(
                                        icon = Tabler.Outline.Stopwatch,
                                        title = stringResource(Res.string.settings_slideshow_interval),
                                        subtitle = stringResource(Res.string.settings_slideshow_interval_subtitle),
                                        trailingText = "${preferences.dreamSlideshowIntervalMs / 1000}s",
                                        index = 2, count = dreamTotal,
                                        highlighted = lastClickedSettingId == "screensaver_slideshow_interval",
                                        onClick = {
                                            activeDialog = PickerState.List(
                                                title = slideshowIntervalTitle,
                                                items = listOf(5_000L, 10_000L, 15_000L, 30_000L, 60_000L),
                                                label = { "${it / 1000}s" },
                                                isSelected = { it == preferences.dreamSlideshowIntervalMs },
                                                onSelect = { viewModel.setDreamSlideshowIntervalMs(it) },
                                            )
                                        },
                                    )
                                    SettingToggleItem(
                                        icon = Tabler.Outline.Wand,
                                        title = stringResource(Res.string.settings_ken_burns),
                                        subtitle = if (preferences.dreamKenBurnsEnabled) stringResource(Res.string.settings_ken_burns_on) else stringResource(Res.string.settings_ken_burns_off),
                                        checked = preferences.dreamKenBurnsEnabled,
                                        index = 3, count = dreamTotal,
                                        highlighted = lastClickedSettingId == "screensaver_ken_burns",
                                        onCheckedChange = { viewModel.setDreamKenBurnsEnabled(it) },
                                    )
                                    SettingListItem(
                                        icon = Tabler.Outline.ArrowRight,
                                        title = stringResource(Res.string.settings_transition_style),
                                        subtitle = preferences.dreamTransitionStyle.name,
                                        trailingText = preferences.dreamTransitionStyle.name,
                                        index = 4, count = dreamTotal,
                                        highlighted = lastClickedSettingId == "screensaver_transition_style",
                                        onClick = {
                                            val labels = mapOf(
                                                DreamTransitionStyle.CROSSFADE to transitionCrossfadeLabel,
                                                DreamTransitionStyle.SLIDE to transitionSlideLabel,
                                                DreamTransitionStyle.NONE to transitionNoneLabel,
                                            )
                                            activeDialog = PickerState.List(
                                                title = transitionStyleTitle,
                                                items = DreamTransitionStyle.entries,
                                                label = { labels[it] ?: it.name },
                                                isSelected = { it == preferences.dreamTransitionStyle },
                                                onSelect = { viewModel.setDreamTransitionStyle(it) },
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        settingsSection("item_experimental", phoneStep = 15, tvStep = 16) {
                            SettingListItem(
                                icon = Tabler.Outline.Flask,
                                title = stringResource(Res.string.settings_experimental),
                                subtitle = buildExperimentalSummary(preferences),
                                index = 0, count = 1,
                                onClick = { openSetting("experimental") { Route.ExperimentalSettings(it) } },
                            )
                        }

                        settingsSection("item_integrations", phoneStep = 16, tvStep = 17) {
                            SettingListItem(
                                icon = Tabler.Outline.PlugConnected,
                                title = stringResource(Res.string.settings_integrations),
                                subtitle = stringResource(Res.string.settings_integrations_subtitle),
                                index = 0, count = 1,
                                onClick = { openSetting("integrations") { Route.Integrations(it) } },
                            )
                        }

                        settingsSection("item_about", phoneStep = 17, tvStep = 18) {
                            SettingListItem(
                                icon = Tabler.Outline.InfoCircle,
                                title = stringResource(Res.string.settings_about),
                                subtitle = stringResource(Res.string.settings_about_subtitle),
                                index = 0, count = 1,
                                onClick = { openSetting("about") { Route.About } },
                            )
                        }
                    }
                }
                }
            }
        }

        if (showSignOutConfirm) {
            ConfirmDialog(
                title = if (signOutFromServer) stringResource(Res.string.settings_sign_out_confirm_title_server) else stringResource(Res.string.settings_sign_out_confirm_title),
                message = if (signOutFromServer) {
                    stringResource(Res.string.settings_sign_out_confirm_message_server)
                } else {
                    stringResource(Res.string.settings_sign_out_confirm_message)
                },
                confirmText = stringResource(Res.string.settings_sign_out),
                onConfirm = {
                    val fromServer = signOutFromServer
                    showSignOutConfirm = false
                    onLogout(fromServer)
                },
                onDismiss = { showSignOutConfirm = false },
                dismissText = stringResource(Res.string.settings_cancel),
            )
        }

        SettingsPickerDialog(
            state = activeDialog,
            onDismiss = { activeDialog = null },
        )
    }
}

@Composable
private fun buildAppearanceSummary(preferences: SettingsScreenPreferences): String {
    val parts = mutableListOf<String>()
    parts.add(preferences.themeMode.name.lowercase().replaceFirstChar { it.uppercase() })
    if (preferences.dynamicTheming) parts.add(stringResource(Res.string.settings_dynamic_token))
    if (preferences.oledMode) parts.add(stringResource(Res.string.settings_oled_token))
    if (preferences.contrastLevel != ContrastLevel.DEFAULT) parts.add(stringResource(Res.string.settings_contrast_suffix, preferences.contrastLevel.name.lowercase().replaceFirstChar { it.uppercase() }))
    if (preferences.performanceMode) parts.add(stringResource(Res.string.settings_performance_token))
    return parts.joinToString(", ")
}

@Composable
private fun buildExperimentalSummary(preferences: SettingsScreenPreferences): String {
    val count = preferences.enabledExperimentalFeatures.size
    return if (count == 0) stringResource(Res.string.settings_early_access_features)
    else pluralStringResource(Res.plurals.settings_features_enabled, count, count)
}

@Composable
private fun SettingsProfileBanner(
    userName: String,
    currentUser: UserInfo?,
    serverAddress: String?,
    isAdmin: Boolean,
    onNewsletterClick: () -> Unit,
    onUserManagementClick: () -> Unit,
    onServerManagementClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val avatarUrl = remember(serverAddress, currentUser) {
        if (!serverAddress.isNullOrBlank() && currentUser != null) {
            val url = buildUserImageUrl(
                baseUrl = serverAddress,
                userId = currentUser.id,
                imageType = "Primary",
                maxWidth = 160,
                tag = currentUser.primaryImageTag,
            )
            url.ifBlank { null }
        } else null
    }

    val isLight = LocalIsLightTheme.current
    val isTv = LocalTvMode.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isLight) Modifier.shadow(2.dp, ShapeCache.smooth24) else Modifier)
            .clip(ShapeCache.smooth24)
            .background(settingsGroupContainerColor())
            .lightModeHairlineBorder(ShapeCache.smooth24)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // Profile Info Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar (Clean circle without flashy border)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = userName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = if (userName.isNotBlank()) userName.take(1).uppercase() else "U",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Name, Role & Server Status
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = userName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    RoleBadge(
                        isAdmin = isAdmin,
                        horizontalPadding = 6.dp,
                        verticalPadding = 1.dp,
                    )
                }

                Spacer(Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(CircleShape)
                        .then(
                            if (!isTv) Modifier.clickable(onClick = onServerManagementClick)
                            else Modifier,
                        )
                        .padding(vertical = 1.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50)),
                    )
                    Text(
                        text = serverAddress?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.settings_connected_server),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Trailing Action Icons
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsIconButton(
                    onClick = onUserManagementClick,
                    icon = Tabler.Outline.Users,
                    contentDescription = stringResource(Res.string.settings_switch_user),
                    iconSize = 19.dp,
                )
                SettingsIconButton(
                    onClick = onNewsletterClick,
                    icon = Tabler.Outline.News,
                    contentDescription = stringResource(Res.string.settings_newsletter_cd),
                    iconSize = 19.dp,
                )
            }
        }
    }
}

/**
 * Compact hero-styled toggle card for Power User Mode. Visually contiguous with the profile
 * banner above it and the [SettingsGroup] cards below — same smooth24 container,
 * [settingsGroupContainerColor] fill, hairline border and light-mode shadow — but a single
 * compact row: the group-header icon tile (tints primary while enabled) and a shrunken switch
 * instead of a full-height ListItem, so the toggle reads as part of the hero cluster rather
 * than a detached list row.
 */
@Composable
private fun PowerUserModeCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = LocalIsLightTheme.current
    val tvFocusState = rememberTvFocusState(focusedScale = 1.02f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = pressScaleValue(isPressed, 0.98f),
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "powerUserPressScale",
    )
    val confirmHaptic = rememberConfirmHaptic()

    val iconTint by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "powerUserIconTint",
    )
    val iconTileColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "powerUserIconTile",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isLight) Modifier.shadow(2.dp, ShapeCache.smooth24) else Modifier)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(ShapeCache.smooth24)
            .background(settingsGroupContainerColor())
            .lightModeHairlineBorder(ShapeCache.smooth24)
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, ShapeCache.smooth24)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                confirmHaptic()
                onCheckedChange(!checked)
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(ShapeCache.smooth12)
                .background(iconTileColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Tabler.Outline.AdjustmentsHorizontal,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(17.dp),
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.settings_power_user_mode),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = stringResource(Res.string.settings_power_user_mode_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))

        // The row itself is the tap target; the switch is a display-only affordance shrunk
        // below M3's 48dp minimum so the card stays one compact row tall.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.scale(0.8f),
            )
        }
    }
}

/**
 * Pill-shaped admin/member role badge. Used in the account [SettingsGroup] header and the
 * [SettingsProfileBanner]. Defaults mirror the group-header padding; the banner passes tighter
 * padding via [horizontalPadding]/[verticalPadding].
 */
@Composable
private fun RoleBadge(
    isAdmin: Boolean,
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 8.dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 2.dp,
) {
    val container = if (isAdmin) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.surfaceContainerHighest
    val onContainer = if (isAdmin) MaterialTheme.colorScheme.onTertiaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = if (isAdmin) Tabler.Outline.Shield else Tabler.Outline.User,
            contentDescription = null,
            tint = onContainer,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = stringResource(if (isAdmin) Res.string.settings_admin_badge else Res.string.settings_member_badge),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = onContainer,
        )
    }
}

@Composable
private fun AnimatedSettingsEntrance(
    index: Int,
    content: @Composable () -> Unit,
) {
    val animate = LocalAnimateSettingsEntrance.current
    var visible by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(animate) }

    LaunchedEffect(animate) {
        if (animate && !visible) {
            kotlinx.coroutines.delay(index * 20L)
            visible = true
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        ) + expandVertically(
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        ),
    ) {
        content()
    }
}

@Composable
private fun SettingsIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
) {
    val isTv = LocalTvMode.current
    if (isTv) {
        val focusState = rememberTvFocusState(focusedScale = 1.15f)
        Box(
            modifier = modifier
                .size(36.dp)
                .then(focusState.focusModifier)
                .tvFocusIndicator(focusState, ShapeCache.smooth10)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = tint,
            )
        }
    } else {
        IconButton(onClick = onClick, modifier = modifier) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = tint,
            )
        }
    }
}

@Composable
private fun SettingsTvCollapsedSearchRow(
    onSearchClicked: () -> Unit,
    searchBoxFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(
                width = 1.dp,
                color = hairlineBorderColor(),
                shape = ShapeCache.smooth16
            )
            .background(
                color = groupedItemContainerColor(darkAlpha = 0.4f),
                shape = ShapeCache.smooth16
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val searchBoxFocusState = rememberTvFocusState(focusedScale = 1.02f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .focusRequester(searchBoxFocusRequester)
                .then(searchBoxFocusState.focusModifier)
                .tvFocusIndicator(searchBoxFocusState, ShapeCache.smooth12)
                .clickable(onClick = onSearchClicked)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Tabler.Outline.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(Res.string.settings_search_placeholder),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
