package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.JellyPlayBackHandler
import com.raulshma.jellyplay.core.ui.components.TopBarStyle
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.withHighlightSettingId
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.message.LocalUserMessageBus
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusDefaults
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKeyEvent
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.groupedItemContainerColor
import com.raulshma.jellyplay.core.designsystem.theme.hairlineBorderColor
import com.raulshma.jellyplay.core.ui.settingssearch.ResolvedSettingsItem
import com.raulshma.jellyplay.core.ui.settingssearch.settingsSearchResults
import androidx.compose.runtime.Immutable
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_advanced_badge
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_advanced_enabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cancel
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_clear_search_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_search_back_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_search_placeholder
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out_confirm_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out_confirm_message_server
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out_confirm_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out_confirm_title_server
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_title

// Search-result ids that are destructive *actions* rather than settings (open a
// confirm dialog instead of navigating). These are deliberately excluded from the
// "recent settings" list — recents track navigable settings the user revisits, not
// one-off sign-out actions. Kept as a hand list on purpose: what makes these two
// ids actions is semantics (a destructive confirm), not a derivable structural
// property of their catalog declarations.
private val ACTION_ONLY_IDS = setOf(AccountRows.Logout.id, AccountRows.SignOutFromServer.id)

// Dream-screen pickers (slideshow interval, transition style) flow through the shared
// `PickerState` dispatcher rather than a screen-local sealed dialog enum.

/**
 * What a settings-search result tap does — the effect vocabulary
 * [settingsResultClickAction] decides between and the composable performs.
 */
internal sealed class SettingsSearchResultAction {
    /**
     * Navigate into a sub-screen; [route] carries the tapped id already baked
     * in as the deep-link highlight target.
     */
    class NavigateToScreen(val route: Route) : SettingsSearchResultAction()

    /**
     * Open the sign-out confirm dialog; [fromServer] selects the title,
     * message and the eventual log-out variant.
     */
    class OpenSignOutDialog(val fromServer: Boolean) : SettingsSearchResultAction()

    /** Launch the host-indirected setup wizard. */
    object OpenSetupWizard : SettingsSearchResultAction()

    /**
     * No navigation — an on-screen target (the screensaver rows) reveals
     * itself through the pending highlight alone.
     */
    object NoOp : SettingsSearchResultAction()
}

/**
 * The pure decision behind a search-result (or recent-setting) tap on this
 * screen: [action] is the effect to perform, [pendingHighlightId] the id to
 * mark for the TV re-entry focus policy (`null` for the management
 * exemptions and the destructive actions), [enableAdvanced] whether the
 * advanced toggle must flip on first, and [recordRecent] whether the id
 * enters the recent-settings list (pure actions like logout never do).
 */
internal data class SettingsSearchResultClick(
    val action: SettingsSearchResultAction,
    val pendingHighlightId: String? = null,
    val enableAdvanced: Boolean = false,
    val recordRecent: Boolean = true,
)

/**
 * Decides [SettingsSearchResultClick] for the tapped result. The destructive
 * account actions open their confirm dialogs (`logout` directly,
 * `sign_out_from_server` through the bare-`Route.Settings` branch); the other
 * bare-Settings targets are this screen's own rows (the screensaver group)
 * and only reveal themselves via the pending highlight; the setup wizard
 * keeps its host indirection; everything else navigates with the id baked
 * into the route, marking the pending highlight except for Server/User
 * Management, which the old per-route dispatch never marked (unknown
 * highlight ids are no-ops downstream — `rememberHighlightScrollIndex`
 * resolves them to -1). The destructive [ACTION_ONLY_IDS] never enter the
 * recent-settings list, and an advanced result auto-enables advanced
 * settings when they are off.
 */
internal fun settingsResultClickAction(
    id: String,
    route: Route,
    isAdvanced: Boolean,
    showAdvancedSettings: Boolean,
): SettingsSearchResultClick {
    val click = when {
        id == AccountRows.Logout.id -> SettingsSearchResultClick(
            action = SettingsSearchResultAction.OpenSignOutDialog(fromServer = false),
        )
        route == Route.Settings -> {
            if (id == AccountRows.SignOutFromServer.id) {
                SettingsSearchResultClick(
                    action = SettingsSearchResultAction.OpenSignOutDialog(fromServer = true),
                )
            } else {
                SettingsSearchResultClick(
                    action = SettingsSearchResultAction.NoOp,
                    pendingHighlightId = id,
                )
            }
        }
        route == Route.Onboarding -> SettingsSearchResultClick(
            action = SettingsSearchResultAction.OpenSetupWizard,
            pendingHighlightId = SystemRows.SetupWizard.id,
        )
        else -> SettingsSearchResultClick(
            action = SettingsSearchResultAction.NavigateToScreen(route.withHighlightSettingId(id)),
            pendingHighlightId = if (route is Route.ServerManagement || route is Route.UserManagement) {
                null
            } else {
                id
            },
        )
    }
    return click.copy(
        enableAdvanced = isAdvanced && !showAdvancedSettings,
        recordRecent = id !in ACTION_ONLY_IDS,
    )
}

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
            delay(TV_INITIAL_FOCUS_DELAY_MS)
            if (isFirstTvEntry) {
                searchFocusRequester.tryRequestFocus()
                isFirstTvEntry = false
            } else {
                if (lastClickedSettingId != null) {
                    delay(TV_HIGHLIGHT_REFOCUS_DELAY_MS)
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

    var isSearchFocused by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var signOutFromServer by remember { mutableStateOf(false) }
    // The screen-level dialog state: the screensaver/idle sections write
    // through this holder (activeDialog.value = ...) so one
    // SettingsPickerDialog at the screen root still owns dismissal.
    val activeDialogState = remember { mutableStateOf<PickerState<*>?>(null) }
    var activeDialog by activeDialogState

    // The search panel's five loose state pieces (query / active / category
    // filter / display list / recents) and their interactions — open → type →
    // filter → tap-through → dismiss, recents add/dedupe/clear — live on the
    // JVM-testable holder ([SettingsSearchPanelState]); this composable only
    // performs the effects (focus requests, VM persistence).
    val searchPanel = remember {
        SettingsSearchPanelState(
            recordRecentSink = viewModel::recordSettingUsed,
            clearRecentsSink = viewModel::clearRecentSettings,
        )
    }

    // Shared search-exit path: dismiss the panel and hand focus back to the
    // main list (TV focus policy depends on the list regaining focus).
    fun dismissSearchAndRefocus() {
        searchPanel.dismiss()
        listFocusRequester.tryRequestFocus()
    }

    // Highlight-then-navigate choreography for this screen's rows — the same
    // dispatch onResultClick runs for search results: mark the pending TV
    // re-entry highlight, then inject the id as the route's deep-link target.
    val openSetting: (String, (String) -> Route) -> Unit = { id, buildRoute ->
        lastClickedSettingId = id
        onNavigate(buildRoute(id).withHighlightSettingId(id))
    }


    // Shared core/ui settings-search pipeline over this module's catalog
    // (the same `settingsSearchResults` feature/home consumes through the
    // provider seam): debounced, distinct-until-changed, matched off the main
    // thread against the platform-filtered, locale-resolved catalog — and
    // short-circuited on blank queries, so an empty search bar never pays the
    // 258-item resolve.
    val filteredItems by produceState(
        initialValue = emptyList<ResolvedSettingsItem>(),
        searchPanel.searchQuery,
    ) {
        settingsSearchResults(snapshotFlow { searchPanel.searchQuery }, SettingsSearchCatalog)
            .collect { value = it }
    }

    val availableCategories = remember(filteredItems) {
        filteredItems.map { it.category }.distinct()
    }

    val displayItems = remember(filteredItems, searchPanel.selectedCategory) {
        searchPanel.displayItems(filteredItems)
    }

    // The last-used setting ids (most-recent first), resolved back to renderable
    // items against the catalog. Stale ids — a recorded setting whose catalog
    // entry no longer exists — drop out via mapNotNull and naturally age out as
    // new ids displace them. Only re-resolved when the persisted id list
    // changes; the catalog-wide resolve stays off the main thread even though
    // this producer itself runs on the composition dispatcher
    // (SettingsSearchCatalog.recentItems owns the Default hop).
    val recentIds by viewModel.recentSettingIds.collectAsStateWithLifecycle()
    // Re-seed the holder's recents mirror whenever the store emits — the
    // core.ui.reorder.ReorderState re-sync shape (the store owns persistence;
    // the mirror is display state).
    LaunchedEffect(recentIds) { searchPanel.submitRecents(recentIds) }
    val recentItems by produceState(
        initialValue = emptyList<ResolvedSettingsItem>(),
        searchPanel.recentIds,
    ) {
        value = SettingsSearchCatalog.recentItems(searchPanel.recentIds)
    }

    JellyPlayBackHandler(enabled = searchPanel.isSearchActive) {
        dismissSearchAndRefocus()
    }

    com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold(
        title = stringResource(Res.string.settings_title),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
        topBarStyle = TopBarStyle.None,
    ) { paddingValues ->
        val bus = LocalUserMessageBus.current

        LaunchedEffect(viewModel.messageSentEvent) {
            viewModel.messageSentEvent?.let { msg ->
                bus.info(msg)
                viewModel.clearMessageEvent()
            }
        }

        // Shared tap handler for both the live search results and the recent
        // settings list: the branching lives in the pure
        // [settingsResultClickAction] (pinned by jvmTest); this reduction only
        // performs the decided effects, records the setting as recently used
        // when the decision says so, then collapses the search panel.
        val onResultClick: (ResolvedSettingsItem) -> Unit = { item ->
            val click = settingsResultClickAction(item.id, item.route, item.isAdvanced, preferences.showAdvancedSettings)
            if (click.enableAdvanced) {
                viewModel.edit { scope -> scope.appearance.setShowAdvancedSettings(true) }
                bus.info(advancedEnabledMessage)
            }
            click.pendingHighlightId?.let { lastClickedSettingId = it }
            when (val action = click.action) {
                is SettingsSearchResultAction.OpenSignOutDialog -> {
                    signOutFromServer = action.fromServer
                    showSignOutConfirm = true
                }
                is SettingsSearchResultAction.NavigateToScreen -> onNavigate(action.route)
                SettingsSearchResultAction.OpenSetupWizard -> onSetupWizard()
                SettingsSearchResultAction.NoOp -> {}
            }
            if (click.recordRecent) searchPanel.recordRecent(item.id)
            // Dismiss search after navigation has been dispatched so the main
            // settings list doesn't briefly reveal during the transition.
            searchPanel.dismiss()
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
                if (!searchPanel.isSearchActive) {
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
                                    searchPanel.open()
                                    coroutineScope.launch {
                                        delay(SEARCH_FIELD_FOCUS_DELAY_MS)
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
                                    searchPanel.open()
                                    coroutineScope.launch {
                                        delay(SEARCH_FIELD_FOCUS_DELAY_MS)
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
                                    if (searchPanel.searchQuery.isEmpty()) {
                                        Text(
                                            text = stringResource(Res.string.settings_search_placeholder),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                    BasicTextField(
                                        value = searchPanel.searchQuery,
                                        onValueChange = { searchPanel.onQueryChange(it) },
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
                                                    if (searchPanel.searchQuery.isNotEmpty()) {
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

                                if (searchPanel.searchQuery.isNotEmpty()) {
                                    SettingsIconButton(
                                        onClick = { searchPanel.clearQuery() },
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

                if (searchPanel.isSearchActive) {
                    SettingsSearchResultsPane(
                        searchPanel = searchPanel,
                        availableCategories = availableCategories,
                        displayItems = displayItems,
                        recentItems = recentItems,
                        advancedBadgeLabel = advLabel,
                        onResultClick = onResultClick,
                        onDismissSearch = { dismissSearchAndRefocus() },
                        onNavigate = onNavigate,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    SettingsBrowsePane(
                        viewModel = viewModel,
                        preferences = preferences,
                        userName = userName,
                        currentServerAddress = currentServerAddress,
                        isTv = isTv,
                        animateEntrance = animateEntrance,
                        lazyListState = lazyListState,
                        listFocusRequester = listFocusRequester,
                        onBack = onBack,
                        openSetting = openSetting,
                        onNavigate = onNavigate,
                        onSetupWizard = onSetupWizard,
                        onNewsletterClick = onNewsletterClick,
                        lastClickedSettingId = lastClickedSettingId,
                        onLastClickedSettingIdChange = { lastClickedSettingId = it },
                        onSignOut = { fromServer ->
                            signOutFromServer = fromServer
                            showSignOutConfirm = true
                        },
                        activeDialogState = activeDialogState,
                    )
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
