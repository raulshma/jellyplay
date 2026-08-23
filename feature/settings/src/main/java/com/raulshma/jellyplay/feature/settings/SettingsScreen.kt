package com.raulshma.jellyplay.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.focus.onFocusEvent
import com.raulshma.jellyplay.core.ui.components.TopBarStyle
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.feedback.LocalUserMessageBus
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.withHighlightSettingId
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.groupedItemContainerColor
import com.raulshma.jellyplay.core.designsystem.theme.hairlineBorderColor
import com.raulshma.jellyplay.core.designsystem.theme.lightModeHairlineBorder
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme
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
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.TvFocusDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import com.raulshma.jellyplay.core.ui.feedback.uiTextOf
import com.raulshma.jellyplay.core.ui.settingssearch.ResolvedSettingsItem
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchMatcher
import com.raulshma.jellyplay.core.ui.settingssearch.resolve
import com.raulshma.jellyplay.core.ui.components.ExpressiveChipContainer
import androidx.compose.ui.graphics.Brush
import com.raulshma.jellyplay.feature.settings.R
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

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

/**
 * A single resolved settings-search result row, shared by the live search results
 * and the recent-settings list so both render identically (leading icon, title,
 * subtitle, category/advanced pills, chevron, expressive list shape, TV focus) and
 * share one tap handler. Extracted from the inline result row so the two lists can
 * not drift in appearance or click behavior.
 */
@Composable
private fun SettingsSearchResultRow(
    item: ResolvedSettingsItem,
    index: Int,
    count: Int,
    advancedBadgeLabel: String,
    onClick: () -> Unit,
) {
    val shape = com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape(index, count, innerRadius = 0.dp)
    val itemTvFocusState = rememberTvFocusState(focusedScale = 1.01f)
    ListItem(
        headlineContent = {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = item.subtitle,
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: (Boolean) -> Unit,
    navActions: SettingsNavActions,
    viewModel: SettingsViewModel = hiltViewModel(),
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

    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()

    val searchBackCd = stringResource(R.string.settings_search_back_cd)
    val clearSearchCd = stringResource(R.string.settings_clear_search_cd)
    val newsletterCd = stringResource(R.string.settings_newsletter_cd)
    val advLabel = stringResource(R.string.settings_advanced_badge)

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var signOutFromServer by remember { mutableStateOf(false) }
    var activeDialog by remember { mutableStateOf<PickerState<*>?>(null) }

    // Debounced + off-main-thread fuzzy search. Each keystroke only re-runs the
    // matcher after a short quiet period, and the Damerau-Levenshtein work happens
    // on Dispatchers.Default so typing stays smooth on low-end devices. The catalog's
    // @StringRes ids are resolved to the current locale once per query, so matching and
    // the rendered results both reflect the user's language.
    val context = LocalContext.current
    val filteredItems by produceState(
        initialValue = emptyList<ResolvedSettingsItem>(),
        searchQuery,
    ) {
        snapshotFlow { searchQuery }
            .debounce(120)
            .distinctUntilChanged()
            .map { SettingsSearchMatcher.search(it, SettingsSearchCatalog.items.resolve(context::getString)) }
            .flowOn(Dispatchers.Default)
            .collect { value = it }
    }

    // The last-used setting ids (most-recent first), resolved back to renderable
    // items against the catalog. Stale ids — a recorded setting whose catalog
    // entry no longer exists — drop out via mapNotNull and naturally age out as new
    // ids displace them. Only re-resolved when the persisted id list changes.
    val recentIds by viewModel.recentSettingIds.collectAsStateWithLifecycle()
    val recentItems = remember(recentIds) {
        if (recentIds.isEmpty()) emptyList()
        else {
            val byId = SettingsSearchCatalog.items.resolve(context::getString).associateBy { it.id }
            recentIds.mapNotNull { byId[it] }
        }
    }

    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        searchQuery = ""
    }

    com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        backgroundColor = backgroundColor,
        topBarStyle = TopBarStyle.None,
    ) { paddingValues ->
        val userMessageBus = LocalUserMessageBus.current

        LaunchedEffect(viewModel.messageSentEvent) {
            viewModel.messageSentEvent?.let { msg ->
                userMessageBus.info(msg)
                viewModel.clearMessageEvent()
            }
        }

        // Shared tap handler for both the live search results and the recent
        // settings list: flips the advanced toggle on if needed, dispatches the
        // navigation, records the setting as recently used (skipping pure
        // actions like logout), then collapses the search panel. Extracted so
        // the two lists never drift in click behavior. Navigation is the same
        // one-liner as the home header search: inject the matched id as the
        // route's deep-link highlight target. Only the non-navigate special
        // cases (sign-out dialogs, the on-screen screensaver group, the
        // host-indirected setup wizard) keep bespoke branches.
        val onResultClick: (ResolvedSettingsItem) -> Unit = { item ->
            if (item.isAdvanced && !preferences.showAdvancedSettings) {
                viewModel.setShowAdvancedSettings(true)
                userMessageBus.info(uiTextOf(R.string.settings_advanced_enabled))
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
                // Floating Toolbar / Search Bar Section (MD3 expressive DockedSearchBar)
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
                    if (isTv && !isSearchActive) {
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
                    } else {
                        DockedSearchBar(
                            inputField = {
                            SearchBarDefaults.InputField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                onSearch = { },
                                expanded = isSearchActive,
                                onExpandedChange = { expanded ->
                                    if (!isTv) {
                                        isSearchActive = expanded
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocusRequester)
                                    .onFocusEvent { isSearchFocused = it.isFocused }
                                    
                                    .onDpadKeyEvent(
                                        onSelect = { e ->
                                            if (!isSearchActive && e.isKeyUp) {
                                                isSearchActive = true
                                                true
                                            } else false
                                        },
                                        onLeft = {
                                            leadingFocusRequester.tryRequestFocus()
                                            true
                                        },
                                        onRight = {
                                            trailingFocusRequester.tryRequestFocus()
                                            true
                                        },
                                        onBack = { e ->
                                            if (e.isKeyUp) {
                                                isSearchActive = false
                                                searchQuery = ""
                                                listFocusRequester.tryRequestFocus()
                                            }
                                            true
                                        },
                                    ),
                                placeholder = {
                                    Text(
                                        stringResource(R.string.settings_search_placeholder),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                leadingIcon = {
                                    SettingsIconButton(
                                        onClick = {
                                            if (isSearchActive) {
                                                isSearchActive = false
                                                searchQuery = ""
                                            } else {
                                                onBack()
                                            }
                                        },
                                        icon = if (isSearchActive) Tabler.Outline.ArrowLeft else Tabler.Outline.Search,
                                        contentDescription = searchBackCd,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        iconSize = 20.dp,
                                        modifier = Modifier
                                            .focusRequester(leadingFocusRequester)
                                            .onDpadKey(
                                                onRight = {
                                                    searchFocusRequester.tryRequestFocus()
                                                    true
                                                },
                                            )
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotBlank() || isSearchActive) {
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
                                                    },
                                                )
                                        )
                                    }
                                },
                            )
                        },
                        expanded = isSearchActive,
                        onExpandedChange = { expanded ->
                            if (!isTv) {
                                isSearchActive = expanded
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isSearchFocused && isTv) {
                                    Modifier.shadow(
                                        elevation = TvFocusDefaults.GlowElevation,
                                        shape = ShapeCache.smooth16,
                                        clip = false,
                                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = TvFocusDefaults.GlowAmbientAlpha),
                                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = TvFocusDefaults.GlowSpotAlpha),
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .border(
                                width = if (isSearchFocused && isTv) TvFocusDefaults.BorderWidth else 1.dp,
                                color = if (isSearchFocused && isTv) MaterialTheme.colorScheme.primary else hairlineBorderColor(),
                                shape = ShapeCache.smooth16
                            ),
                        shape = ShapeCache.smooth16,
                        colors = SearchBarDefaults.colors(
                            containerColor = groupedItemContainerColor(darkAlpha = 0.4f),
                        ),
                    ) {
                        when {
                            filteredItems.isNotEmpty() -> {
                                SearchResultsColumn(
                                    onBack = {
                                        isSearchActive = false
                                        searchQuery = ""
                                        listFocusRequester.tryRequestFocus()
                                    },
                                ) {
                                    itemsIndexed(filteredItems, key = { _, item -> item.id }) { index, item ->
                                        SettingsSearchResultRow(
                                            item = item,
                                            index = index,
                                            count = filteredItems.size,
                                            advancedBadgeLabel = advLabel,
                                            onClick = { onResultClick(item) },
                                        )
                                    }
                                }
                            }
                            // Empty query: surface the last-used settings instead of a dead-end
                            // "no matches" message. Same row rendering and click behavior as live
                            // results, plus a header row with a Clear affordance.
                            searchQuery.isBlank() && recentItems.isNotEmpty() -> {
                                SearchResultsColumn(
                                    onBack = {
                                        isSearchActive = false
                                        searchQuery = ""
                                        listFocusRequester.tryRequestFocus()
                                    },
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
                                                text = stringResource(R.string.settings_recents_title),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            TextButton(onClick = { viewModel.clearRecentSettings() }) {
                                                Text(stringResource(R.string.settings_clear_recents))
                                            }
                                        }
                                    }
                                    itemsIndexed(recentItems, key = { _, item -> item.id }) { index, item ->
                                        SettingsSearchResultRow(
                                            item = item,
                                            index = index,
                                            count = recentItems.size,
                                            advancedBadgeLabel = advLabel,
                                            onClick = { onResultClick(item) },
                                        )
                                    }
                                }
                            }
                            else -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(
                                            if (searchQuery.isBlank()) R.string.settings_search_hint
                                            else R.string.settings_no_matches
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    }
                }

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
                        item(key = "profile") {
                            AnimatedSettingsEntrance(0) {
                                if (userName.isNotBlank()) {
                                    SettingsProfileBanner(
                                        userName = userName,
                                        serverAddress = currentServerAddress,
                                        isAdmin = viewModel.currentUser?.isAdmin == true,
                                        showAdvanced = preferences.showAdvancedSettings,
                                        onToggleAdvanced = { viewModel.setShowAdvancedSettings(!preferences.showAdvancedSettings) },
                                        onNewsletterClick = onNewsletterClick,
                                        onUserManagementClick = { onNavigate(Route.UserManagement(null)) },
                                        onServerManagementClick = { onNavigate(Route.ServerManagement(null)) },
                                    )
                                }
                            }
                        }

                        item(key = "active_devices") {
                            AnimatedSettingsEntrance(1) {
                                if (viewModel.currentUser?.isAdmin == true && viewModel.activeSessions.isNotEmpty()) {
                                    ActiveDevicesRow(
                                        sessions = viewModel.activeSessions,
                                        serverAddress = currentServerAddress,
                                        onSendMessage = viewModel::sendMessageToSession,
                                    )
                                }
                            }
                        }

                        item(key = "account") {
                            AnimatedSettingsEntrance(2) {
                                SettingsGroup(
                                    icon = Tabler.Outline.User,
                                    title = stringResource(R.string.settings_account),
                                    summary = { stringResource(R.string.settings_signed_in_as_name, userName) },
                                    badge = {
                                        RoleBadge(isAdmin = viewModel.currentUser?.isAdmin == true)
                                    },
                                    initiallyExpanded = false,
                                ) {
                                    SettingListItem(
                                        icon = Tabler.Outline.Server,
                                        title = stringResource(R.string.settings_server_management),
                                        subtitle = stringResource(R.string.settings_server_management_subtitle),
                                        index = 0, count = 4,
                                        onClick = {
                                            lastClickedSettingId = "server_management"
                                            onNavigate(Route.ServerManagement(lastClickedSettingId))
                                        },
                                    )
                                    SettingListItem(
                                        icon = Tabler.Outline.Users,
                                        title = stringResource(R.string.settings_switch_user),
                                        subtitle = stringResource(R.string.settings_switch_user_subtitle),
                                        index = 1, count = 4,
                                        onClick = {
                                            lastClickedSettingId = "user_management"
                                            onNavigate(Route.UserManagement(lastClickedSettingId))
                                        },
                                    )
                                    SettingListItem(
                                        icon = Tabler.Outline.Logout,
                                        title = stringResource(R.string.settings_sign_out),
                                        subtitle = stringResource(R.string.settings_sign_out_subtitle),
                                        index = 2, count = 4,
                                        isDestructive = true,
                                        onClick = {
                                            signOutFromServer = false
                                            showSignOutConfirm = true
                                        },
                                    )
                                    SettingListItem(
                                        icon = Tabler.Outline.Logout,
                                        title = stringResource(R.string.settings_sign_out_from_server),
                                        subtitle = stringResource(R.string.settings_sign_out_from_server_subtitle),
                                        index = 3, count = 4,
                                        isDestructive = true,
                                        onClick = {
                                            signOutFromServer = true
                                            showSignOutConfirm = true
                                        },
                                    )
                                }
                            }
                        }

                        item(key = "activity") {
                            AnimatedSettingsEntrance(3) {
                                val pendingCount = viewModel.pendingRequestCount.collectAsStateWithLifecycle().value
                                SettingsGroup(
                                    icon = Tabler.Outline.Activity,
                                    title = stringResource(R.string.settings_activity_insights),
                                    summary = { stringResource(R.string.settings_activity_insights_subtitle) },
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
                                        title = stringResource(R.string.settings_browse_favorites),
                                        subtitle = stringResource(R.string.settings_browse_favorites_subtitle),
                                        index = 0, count = insightsCount,
                                        onClick = {
                                            lastClickedSettingId = "favorites"
                                            onNavigate(Route.Favorites)
                                        },
                                    )
                                    SettingListItem(
                                        icon = Tabler.Outline.ChartBar,
                                        title = stringResource(R.string.settings_watch_history_heatmap),
                                        subtitle = stringResource(R.string.settings_watch_history_heatmap_subtitle),
                                        index = 1, count = insightsCount,
                                        onClick = {
                                            lastClickedSettingId = "watch_progress_heatmap"
                                            onNavigate(Route.WatchProgressHeatmap)
                                        },
                                    )
                                    SettingListItem(
                                        icon = Tabler.Outline.Database,
                                        title = stringResource(R.string.settings_activity_queue),
                                        subtitle = stringResource(R.string.settings_activity_queue_subtitle),
                                        index = 2, count = insightsCount,
                                        onClick = {
                                            lastClickedSettingId = "activity_queue"
                                            onNavigate(Route.ArrQueue)
                                        },
                                    )
                                    SettingListItem(
                                        icon = Tabler.Outline.CalendarEvent,
                                        title = stringResource(R.string.settings_upcoming),
                                        subtitle = stringResource(R.string.settings_upcoming_subtitle),
                                        index = 3, count = insightsCount,
                                        onClick = {
                                            lastClickedSettingId = "upcoming"
                                            onNavigate(Route.UpcomingCalendar)
                                        },
                                    )
                                    SettingListItem(
                                        icon = Tabler.Outline.Inbox,
                                        title = stringResource(R.string.settings_requests),
                                        subtitle = stringResource(R.string.settings_requests_subtitle),
                                        index = 4, count = insightsCount,
                                        trailingText = pendingCount.takeIf { it > 0 }?.toString(),
                                        onClick = {
                                            lastClickedSettingId = "requests"
                                            onNavigate(Route.Requests)
                                        },
                                    )
                                }
                            }
                        }

                        item(key = "system") {
                            AnimatedSettingsEntrance(4) {
                                val activeSessionCount = viewModel.activeSessions.size
                                SettingsGroup(
                                    icon = Tabler.Outline.Adjustments,
                                    title = stringResource(R.string.settings_system),
                                    summary = { stringResource(R.string.settings_system_subtitle) },
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
                                            title = stringResource(R.string.settings_admin_dashboard),
                                            subtitle = stringResource(R.string.settings_admin_dashboard_subtitle),
                                            index = systemIndex++, count = systemCount,
                                            onClick = {
                                                lastClickedSettingId = "admin_dashboard"
                                                onNavigate(Route.AdminDashboard)
                                            },
                                        )
                                    }
                                    SettingListItem(
                                        icon = Tabler.Outline.Wand,
                                        title = stringResource(R.string.settings_setup_wizard),
                                        subtitle = stringResource(R.string.settings_setup_wizard_subtitle),
                                        index = systemIndex++, count = systemCount,
                                        onClick = {
                                            lastClickedSettingId = "setup_wizard"
                                            onSetupWizard()
                                        },
                                    )
                                }
                            }
                        }

                        item(key = "item_appearance") {
                            AnimatedSettingsEntrance(4) {
                                SettingListItem(
                                    icon = Tabler.Outline.Palette,
                                    title = stringResource(R.string.settings_appearance),
                                    subtitle = buildAppearanceSummary(preferences),
                                    index = 0, count = 1,
                                    onClick = {
                                        lastClickedSettingId = "appearance"
                                        onNavigate(Route.AppearanceSettings(lastClickedSettingId))
                                    },
                                )
                            }
                        }

                        item(key = "item_playback") {
                            AnimatedSettingsEntrance(5) {
                                SettingListItem(
                                    icon = Tabler.Outline.PlayerPlay,
                                    title = stringResource(R.string.settings_playback),
                                    subtitle = stringResource(R.string.settings_playback_subtitle, preferences.preferredPlayer.displayName),
                                    index = 0, count = 1,
                                    onClick = {
                                        lastClickedSettingId = "playback"
                                        onNavigate(Route.PlaybackSettings(lastClickedSettingId))
                                    },
                                )
                            }
                        }

                        item(key = "item_audio") {
                            AnimatedSettingsEntrance(6) {
                                SettingListItem(
                                    icon = Tabler.Outline.Music,
                                    title = stringResource(R.string.settings_audio_player),
                                    subtitle = stringResource(R.string.settings_default_speed_value, if (preferences.audioDefaultSpeed == 1.0f) "1x" else "${preferences.audioDefaultSpeed}x"),
                                    index = 0, count = 1,
                                    onClick = {
                                        lastClickedSettingId = "audio"
                                        onNavigate(Route.AudioSettings(lastClickedSettingId))
                                    },
                                )
                            }
                        }

                        item(key = "item_language") {
                            AnimatedSettingsEntrance(7) {
                                SettingListItem(
                                    icon = Tabler.Outline.Language,
                                    title = stringResource(R.string.settings_language_subtitles),
                                    subtitle = stringResource(R.string.settings_language_subtitle, preferences.preferredAudioLanguage ?: stringResource(R.string.settings_lang_default)),
                                    index = 0, count = 1,
                                    onClick = {
                                        lastClickedSettingId = "language"
                                        onNavigate(Route.LanguageSettings(lastClickedSettingId))
                                    },
                                )
                            }
                        }

                        item(key = "item_notifications") {
                            AnimatedSettingsEntrance(8) {
                                val notifPrefs = preferences.notificationPreferences
                                SettingListItem(
                                    icon = Tabler.Outline.Bell,
                                    title = stringResource(R.string.settings_notifications),
                                    subtitle = if (notifPrefs.enabled) stringResource(R.string.settings_notifications_checking, notifPrefs.checkFrequency.displayName.lowercase()) else stringResource(R.string.settings_disabled),
                                    index = 0, count = 1,
                                    onClick = {
                                        lastClickedSettingId = "notifications"
                                        onNavigate(Route.NotificationSettings(lastClickedSettingId))
                                    },
                                )
                            }
                        }

                        item(key = "item_storage") {
                            AnimatedSettingsEntrance(9) {
                                SettingListItem(
                                    icon = Tabler.Outline.Database,
                                    title = stringResource(R.string.settings_downloads_storage),
                                    subtitle = stringResource(R.string.settings_cache_subtitle, viewModel.cacheSizeMb),
                                    index = 0, count = 1,
                                    onClick = {
                                        lastClickedSettingId = "storage"
                                        onNavigate(Route.StorageSettings(lastClickedSettingId))
                                    },
                                )
                            }
                        }
                                        item(key = "item_security") {
                            AnimatedSettingsEntrance(10) {
                                SettingListItem(
                                    icon = Tabler.Outline.Lock,
                                    title = stringResource(R.string.settings_security),
                                    subtitle = when {
                                        preferences.pinLockEnabled && preferences.biometricLockEnabled -> stringResource(R.string.settings_pin_biometric_on)
                                        preferences.biometricLockEnabled -> stringResource(R.string.settings_biometric_on)
                                        preferences.pinLockEnabled -> stringResource(R.string.settings_pin_on)
                                        else -> stringResource(R.string.settings_lock_off)
                                    },
                                    index = 0, count = 1,
                                    onClick = {
                                        lastClickedSettingId = "security"
                                        onNavigate(Route.SecuritySettings(lastClickedSettingId))
                                    },
                                )
                            }
                        }

                        item(key = "item_privacy_data") {
                            AnimatedSettingsEntrance(11) {
                                SettingListItem(
                                    icon = Tabler.Outline.ShieldLock,
                                    title = stringResource(R.string.settings_privacy_data),
                                    subtitle = stringResource(R.string.settings_privacy_data_subtitle),
                                    index = 0, count = 1,
                                    onClick = {
                                        lastClickedSettingId = "privacy_data"
                                        onNavigate(Route.PrivacyData(lastClickedSettingId))
                                    },
                                )
                            }
                        }

                        item(key = "item_backup") {
                            AnimatedSettingsEntrance(12) {
                                SettingListItem(
                                    icon = Tabler.Outline.DatabaseExport,
                                    title = stringResource(R.string.settings_backup_restore),
                                    subtitle = stringResource(R.string.settings_backup_restore_subtitle),
                                    index = 0, count = 1,
                                    onClick = {
                                        lastClickedSettingId = "backup"
                                        onNavigate(Route.BackupSettings(lastClickedSettingId))
                                    },
                                )
                            }
                        }

                        if (isTv) {
                            item(key = "group_screensaver") {
                                AnimatedSettingsEntrance(13) {
                                    SettingsGroup(
                                        icon = Tabler.Outline.Moon,
                                        title = stringResource(R.string.settings_screensaver),
                                        summary = {
                                            val cats = preferences.dreamImageCategories
                                            remember(cats) {
                                                cats.joinToString(", ") { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
                                            }
                                        },
                                        initiallyExpanded = lastClickedSettingId in SCREENSAVER_GROUP_IDS,
                                    ) {
                                        val dreamTotal = 5
                                        val slideshowIntervalTitle = stringResource(R.string.settings_slideshow_interval)
                                        val transitionStyleTitle = stringResource(R.string.settings_transition_style)
                                        val transitionCrossfadeLabel = stringResource(R.string.settings_transition_crossfade)
                                        val transitionSlideLabel = stringResource(R.string.settings_transition_slide)
                                        val transitionNoneLabel = stringResource(R.string.settings_transition_none)
                                        SettingToggleItem(
                                            icon = Tabler.Outline.Typography,
                                            title = stringResource(R.string.settings_show_title),
                                            subtitle = if (preferences.dreamShowTitle) stringResource(R.string.settings_display_media_title) else stringResource(R.string.settings_hide_media_title),
                                            checked = preferences.dreamShowTitle,
                                            index = 0, count = dreamTotal,
                                            highlighted = lastClickedSettingId == "screensaver_show_title",
                                            onCheckedChange = { viewModel.setDreamShowTitle(it) },
                                        )
                                        val categoryMovies = stringResource(R.string.settings_category_movies)
                                        val categoryTv = stringResource(R.string.settings_category_tv)
                                        val categoryMusic = stringResource(R.string.settings_category_music)
                                        SettingListItem(
                                            icon = Tabler.Outline.Movie,
                                            title = stringResource(R.string.settings_categories),
                                            subtitle = stringResource(R.string.settings_categories_subtitle),
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
                                            title = stringResource(R.string.settings_slideshow_interval),
                                            subtitle = stringResource(R.string.settings_slideshow_interval_subtitle),
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
                                            title = stringResource(R.string.settings_ken_burns),
                                            subtitle = if (preferences.dreamKenBurnsEnabled) stringResource(R.string.settings_ken_burns_on) else stringResource(R.string.settings_ken_burns_off),
                                            checked = preferences.dreamKenBurnsEnabled,
                                            index = 3, count = dreamTotal,
                                            highlighted = lastClickedSettingId == "screensaver_ken_burns",
                                            onCheckedChange = { viewModel.setDreamKenBurnsEnabled(it) },
                                        )
                                        SettingListItem(
                                            icon = Tabler.Outline.ArrowRight,
                                            title = stringResource(R.string.settings_transition_style),
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
                        }

                        item(key = "item_experimental") {
                            AnimatedSettingsEntrance(if (isTv) 14 else 13) {
                                SettingListItem(
                                    icon = Tabler.Outline.Flask,
                                    title = stringResource(R.string.settings_experimental),
                                    subtitle = buildExperimentalSummary(preferences),
                                    index = 0, count = 1,
                                    onClick = {
                                        lastClickedSettingId = "experimental"
                                        onNavigate(Route.ExperimentalSettings(lastClickedSettingId))
                                    },
                                )
                            }
                        }

                        item(key = "item_integrations") {
                            AnimatedSettingsEntrance(if (isTv) 15 else 14) {
                                SettingListItem(
                                    icon = Tabler.Outline.PlugConnected,
                                    title = stringResource(R.string.settings_integrations),
                                    subtitle = stringResource(R.string.settings_integrations_subtitle),
                                    index = 0, count = 1,
                                    onClick = {
                                        lastClickedSettingId = "integrations"
                                        onNavigate(Route.Integrations(lastClickedSettingId))
                                    },
                                )
                            }
                        }

                        item(key = "item_about") {
                            AnimatedSettingsEntrance(if (isTv) 16 else 15) {
                                SettingListItem(
                                    icon = Tabler.Outline.InfoCircle,
                                    title = stringResource(R.string.settings_about),
                                    subtitle = stringResource(R.string.settings_about_subtitle),
                                    index = 0, count = 1,
                                    onClick = {
                                        lastClickedSettingId = "about"
                                        onNavigate(Route.About)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showSignOutConfirm) {
            ConfirmDialog(
                title = if (signOutFromServer) stringResource(R.string.settings_sign_out_confirm_title_server) else stringResource(R.string.settings_sign_out_confirm_title),
                message = if (signOutFromServer) {
                    stringResource(R.string.settings_sign_out_confirm_message_server)
                } else {
                    stringResource(R.string.settings_sign_out_confirm_message)
                },
                confirmText = stringResource(R.string.settings_sign_out),
                onConfirm = {
                    val fromServer = signOutFromServer
                    showSignOutConfirm = false
                    onLogout(fromServer)
                },
                onDismiss = { showSignOutConfirm = false },
                dismissText = stringResource(R.string.settings_cancel),
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
    if (preferences.dynamicTheming) parts.add(stringResource(R.string.settings_dynamic_token))
    if (preferences.oledMode) parts.add(stringResource(R.string.settings_oled_token))
    if (preferences.contrastLevel != ContrastLevel.DEFAULT) parts.add(stringResource(R.string.settings_contrast_suffix, preferences.contrastLevel.name.lowercase().replaceFirstChar { it.uppercase() }))
    if (preferences.performanceMode) parts.add(stringResource(R.string.settings_performance_token))
    return parts.joinToString(", ")
}

@Composable
private fun buildExperimentalSummary(preferences: SettingsScreenPreferences): String {
    val count = preferences.enabledExperimentalFeatures.size
    return if (count == 0) stringResource(R.string.settings_early_access_features)
    else pluralStringResource(R.plurals.settings_features_enabled, count, count)
}

@Composable
private fun SettingsProfileBanner(
    userName: String,
    serverAddress: String?,
    isAdmin: Boolean,
    showAdvanced: Boolean,
    onToggleAdvanced: () -> Unit,
    onNewsletterClick: () -> Unit,
    onUserManagementClick: () -> Unit,
    onServerManagementClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = ShapeCache.smooth24,
        color = if (LocalIsLightTheme.current) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        border = BorderStroke(
            width = 1.dp,
            color = hairlineBorderColor()
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        )
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (userName.isNotBlank()) userName.take(1).uppercase() else "U",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        RoleBadge(
                            isAdmin = isAdmin,
                            horizontalPadding = 7.dp,
                            verticalPadding = 1.dp,
                        )
                    }

                    Spacer(Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Text(
                            text = serverAddress?.takeIf { it.isNotBlank() } ?: stringResource(R.string.settings_connected_server),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingsIconButton(
                        onClick = onUserManagementClick,
                        icon = Tabler.Outline.Users,
                        contentDescription = stringResource(R.string.settings_switch_user),
                    )
                    SettingsIconButton(
                        onClick = onNewsletterClick,
                        icon = Tabler.Outline.Mail,
                        contentDescription = stringResource(R.string.settings_newsletter_cd),
                    )
                }
            }

            ExpressiveChipContainer(
                onClick = onToggleAdvanced,
                containerColor = if (showAdvanced) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f) else MaterialTheme.colorScheme.surfaceContainer,
                forceActive = showAdvanced,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Tabler.Outline.AdjustmentsHorizontal,
                    contentDescription = null,
                    tint = if (showAdvanced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.settings_power_user_mode),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (showAdvanced) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Switch(
                        checked = showAdvanced,
                        onCheckedChange = { onToggleAdvanced() },
                        modifier = Modifier.scale(0.75f),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    )
                }
            }
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
    val container = if (isAdmin) MaterialTheme.colorScheme.tertiaryContainer
    else MaterialTheme.colorScheme.secondaryContainer
    val onContainer = if (isAdmin) MaterialTheme.colorScheme.onTertiaryContainer
    else MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    ) {
        Text(
            text = stringResource(if (isAdmin) R.string.settings_admin_badge else R.string.settings_member_badge),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
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
                text = stringResource(R.string.settings_search_placeholder),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
