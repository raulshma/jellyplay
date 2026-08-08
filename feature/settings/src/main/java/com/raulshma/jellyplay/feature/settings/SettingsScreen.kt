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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.onFocusEvent
import com.raulshma.jellyplay.core.ui.components.TopBarStyle
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.feedback.LocalUserMessageBus
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import com.raulshma.jellyplay.core.ui.navigation.Route
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
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
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchRegistry
import com.raulshma.jellyplay.core.ui.settingssearch.resolve
import com.raulshma.jellyplay.core.ui.components.ExpressiveChipContainer
import androidx.compose.ui.graphics.Brush
import com.raulshma.jellyplay.feature.settings.R
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

private val LocalAnimateSettingsEntrance = staticCompositionLocalOf { false }

// Dream-screen pickers (slideshow interval, transition style) flow through the shared
// `PickerState` dispatcher rather than a screen-local sealed dialog enum.

/**
 * Bundles the sub-screen navigation callbacks passed into [SettingsScreen].
 *
 * Grouping them into a single `@Immutable` value lets the navigation call site
 * `remember` one instance, so the [SettingsScreen] subtree is treated as
 * skip-worthy by the Compose compiler instead of recomposing on every parent
 * state change (each unstable lambda parameter would otherwise be a distinct
 * stability key). Mirrors the [com.raulshma.jellyplay.feature.home.HomeCallbacks]
 * pattern.
 *
 * Callers should construct via `remember(...) { SettingsCallbacks(...) }` so the
 * same instance is reused across recompositions.
 */
@Immutable
data class SettingsCallbacks(
    val onServerManagement: (String?) -> Unit = {},
    val onUserManagement: (String?) -> Unit = {},
    val onSeerrSettings: (String?) -> Unit = {},
    val onArrSettings: (String?) -> Unit = {},
    val onIntegrations: (String?) -> Unit = {},
    val onAdminDashboard: () -> Unit = {},
    val onSetupWizard: () -> Unit = {},
    val onNewsletterClick: () -> Unit = {},
    val onFavoritesClick: () -> Unit = {},
    val onAboutClick: () -> Unit = {},
    val onWatchProgressHeatmapClick: () -> Unit = {},
    val onActivityQueueClick: () -> Unit = {},
    val onUpcomingClick: () -> Unit = {},
    val onRequestsClick: () -> Unit = {},
    val onAppearanceSettings: (String?) -> Unit = {},
    val onPinnedHomeSections: (String?) -> Unit = {},
    val onHomeLayoutPresets: (String?) -> Unit = {},
    val onConfigureLibraries: (String?) -> Unit = {},
    val onPlaybackSettings: (String?) -> Unit = {},
    val onAudioSettings: (String?) -> Unit = {},
    val onLanguageSettings: (String?) -> Unit = {},
    val onNotificationSettings: (String?) -> Unit = {},
    val onStorageSettings: (String?) -> Unit = {},
    val onSecuritySettings: (String?) -> Unit = {},
    val onBackupSettings: (String?) -> Unit = {},
    val onExperimentalSettings: (String?) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: (Boolean) -> Unit,
    callbacks: SettingsCallbacks,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val onServerManagement = callbacks.onServerManagement
    val onUserManagement = callbacks.onUserManagement
    val onSeerrSettings = callbacks.onSeerrSettings
    val onArrSettings = callbacks.onArrSettings
    val onIntegrations = callbacks.onIntegrations
    val onNewsletterClick = callbacks.onNewsletterClick
    val onAboutClick = callbacks.onAboutClick
    val onActivityQueueClick = callbacks.onActivityQueueClick
    val onUpcomingClick = callbacks.onUpcomingClick
    val onRequestsClick = callbacks.onRequestsClick
    val onFavoritesClick = callbacks.onFavoritesClick
    val onWatchProgressHeatmapClick = callbacks.onWatchProgressHeatmapClick
    val onAdminDashboard = callbacks.onAdminDashboard
    val onSetupWizard = callbacks.onSetupWizard
    val onAppearanceSettings = callbacks.onAppearanceSettings
    val onPinnedHomeSections = callbacks.onPinnedHomeSections
    val onHomeLayoutPresets = callbacks.onHomeLayoutPresets
    val onPlaybackSettings = callbacks.onPlaybackSettings
    val onAudioSettings = callbacks.onAudioSettings
    val onLanguageSettings = callbacks.onLanguageSettings
    val onNotificationSettings = callbacks.onNotificationSettings
    val onStorageSettings = callbacks.onStorageSettings
    val onSecuritySettings = callbacks.onSecuritySettings
    val onBackupSettings = callbacks.onBackupSettings
    val onExperimentalSettings = callbacks.onExperimentalSettings
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
    // on Dispatchers.Default so typing stays smooth on low-end devices. The registry's
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
            .map { SettingsSearchMatcher.search(it, SettingsSearchRegistry.items.resolve(context::getString)) }
            .flowOn(Dispatchers.Default)
            .collect { value = it }
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
                                color = if (isSearchFocused && isTv) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                shape = ShapeCache.smooth16
                            ),
                        shape = ShapeCache.smooth16,
                        colors = SearchBarDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        ),
                    ) {
                        if (filteredItems.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .then(Modifier.onDpadKeyEvent(
                                        onBack = { e ->
                                            if (e.isKeyUp) {
                                                isSearchActive = false
                                                searchQuery = ""
                                                listFocusRequester.tryRequestFocus()
                                            }
                                            true
                                        },
                                    )),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                itemsIndexed(filteredItems, key = { _, item -> item.id }) { index, item ->
                                    val shape = com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape(index, filteredItems.size, innerRadius = 0.dp)
                                    val itemTvFocusState = rememberTvFocusState(focusedScale = 1.01f)
                                    ListItem(
                                        headlineContent = {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        supportingContent = {
                                            Text(
                                                text = item.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        leadingContent = {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(ShapeCache.smooth8)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = item.icon,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        },
                                        trailingContent = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = item.category,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                if (item.isAdvanced) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = advLabel,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                                Icon(
                                                    imageVector = Tabler.Outline.ChevronRight,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        },
                                        colors = ListItemDefaults.colors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(shape)
                                            .then(itemTvFocusState.focusModifier)
                                            .tvFocusIndicator(itemTvFocusState, shape)
                                            .clickable {
                                                if (item.isAdvanced && !preferences.showAdvancedSettings) {
                                                    viewModel.setShowAdvancedSettings(true)
                                                    userMessageBus.info(uiTextOf(R.string.settings_advanced_enabled))
                                                }
                                                  if (item.id == "logout") {
                                                      signOutFromServer = false
                                                     showSignOutConfirm = true
                                                 } else {
                                                    when (item.route) {
                                                        is Route.ServerManagement -> onServerManagement(item.id)
                                                        is Route.UserManagement -> onUserManagement(item.id)
                                                        is Route.SeerrSettings -> {
                                                            lastClickedSettingId = "seerr_settings"
                                                            onSeerrSettings(item.id)
                                                        }
                                                        Route.Favorites -> {
                                                            lastClickedSettingId = "favorites"
                                                            onFavoritesClick()
                                                        }
                                                        Route.WatchProgressHeatmap -> {
                                                            lastClickedSettingId = "watch_progress_heatmap"
                                                            onWatchProgressHeatmapClick()
                                                        }
                                                        Route.ArrQueue -> {
                                                            lastClickedSettingId = "activity_queue"
                                                            onActivityQueueClick()
                                                        }
                                                        Route.UpcomingCalendar -> {
                                                            lastClickedSettingId = "upcoming"
                                                            onUpcomingClick()
                                                        }
                                                        Route.Requests -> {
                                                            lastClickedSettingId = "requests"
                                                            onRequestsClick()
                                                        }
                                                        Route.AdminDashboard -> {
                                                            lastClickedSettingId = "admin_dashboard"
                                                            onAdminDashboard()
                                                        }
                                                        Route.Onboarding -> {
                                                            lastClickedSettingId = "setup_wizard"
                                                            onSetupWizard()
                                                        }
                                                        is Route.ArrSettings -> {
                                                            lastClickedSettingId = "integrations"
                                                            onArrSettings(item.id)
                                                        }
                                                        is Route.Integrations -> {
                                                            lastClickedSettingId = "integrations"
                                                            onIntegrations(item.id)
                                                        }
                                                        is Route.AppearanceSettings -> {
                                                            lastClickedSettingId = "appearance"
                                                            onAppearanceSettings(item.id)
                                                        }
                                                        is Route.PinnedHomeSections -> {
                                                            lastClickedSettingId = "appearance"
                                                            onPinnedHomeSections(item.id)
                                                        }
                                                        is Route.HomeLayoutPresets -> {
                                                            lastClickedSettingId = "appearance"
                                                            onHomeLayoutPresets(item.id)
                                                        }
                                                        is Route.PlaybackSettings -> {
                                                            lastClickedSettingId = "playback"
                                                            onPlaybackSettings(item.id)
                                                        }
                                                        is Route.AudioSettings -> {
                                                            lastClickedSettingId = "audio"
                                                            onAudioSettings(item.id)
                                                        }
                                                        is Route.LanguageSettings -> {
                                                            lastClickedSettingId = "language"
                                                            onLanguageSettings(item.id)
                                                        }
                                                        is Route.NotificationSettings -> {
                                                            lastClickedSettingId = "notifications"
                                                            onNotificationSettings(item.id)
                                                        }
                                                        is Route.StorageSettings -> {
                                                            lastClickedSettingId = "storage"
                                                            onStorageSettings(item.id)
                                                        }
                                                        is Route.SecuritySettings -> {
                                                            lastClickedSettingId = "security"
                                                            onSecuritySettings(item.id)
                                                        }
                                                        is Route.BackupSettings -> {
                                                            lastClickedSettingId = "backup"
                                                            onBackupSettings(item.id)
                                                        }
                                                        is Route.ExperimentalSettings -> {
                                                            lastClickedSettingId = "experimental"
                                                            onExperimentalSettings(item.id)
                                                        }
                                                        Route.About -> {
                                                            lastClickedSettingId = "about"
                                                            onAboutClick()
                                                        }
                                                        else -> {}
                                                    }
                                                }
                                                // Dismiss search after navigation has been dispatched so the
                                                // main settings list doesn't briefly reveal during the transition.
                                                isSearchActive = false
                                                searchQuery = ""
                                            }
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_no_matches),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                                        onUserManagementClick = { onUserManagement(null) },
                                        onServerManagementClick = { onServerManagement(null) },
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
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(
                                                    if (viewModel.currentUser?.isAdmin == true) MaterialTheme.colorScheme.tertiaryContainer
                                                    else MaterialTheme.colorScheme.secondaryContainer
                                                )
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (viewModel.currentUser?.isAdmin == true) stringResource(R.string.settings_admin_badge) else stringResource(R.string.settings_member_badge),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (viewModel.currentUser?.isAdmin == true) MaterialTheme.colorScheme.onTertiaryContainer
                                                else MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        }
                                    },
                                    initiallyExpanded = false,
                                ) {
                                    SettingListItem(
                                        icon = Tabler.Outline.Server,
                                        title = stringResource(R.string.settings_server_management),
                                        subtitle = stringResource(R.string.settings_server_management_subtitle),
                                        index = 0, count = 4,
                                        highlighted = lastClickedSettingId == "server_management",
                                        onClick = {
                                            lastClickedSettingId = "server_management"
                                            onServerManagement(null)
                                        },
                                    )
                                    SettingListItem(
                                        icon = Tabler.Outline.Users,
                                        title = stringResource(R.string.settings_switch_user),
                                        subtitle = stringResource(R.string.settings_switch_user_subtitle),
                                        index = 1, count = 4,
                                        highlighted = lastClickedSettingId == "user_management",
                                        onClick = {
                                            lastClickedSettingId = "user_management"
                                            onUserManagement(null)
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
                                        highlighted = lastClickedSettingId == "favorites",
                                        onClick = {
                                            lastClickedSettingId = "favorites"
                                            onFavoritesClick()
                                        },
                                    )
                                    SettingListItem(
                                        icon = Tabler.Outline.ChartBar,
                                        title = stringResource(R.string.settings_watch_history_heatmap),
                                        subtitle = stringResource(R.string.settings_watch_history_heatmap_subtitle),
                                        index = 1, count = insightsCount,
                                        highlighted = lastClickedSettingId == "watch_progress_heatmap",
                                        onClick = {
                                            lastClickedSettingId = "watch_progress_heatmap"
                                            onWatchProgressHeatmapClick()
                                        },
                                    )
                                    SettingListItem(
                                        icon = Tabler.Outline.Database,
                                        title = stringResource(R.string.settings_activity_queue),
                                        subtitle = stringResource(R.string.settings_activity_queue_subtitle),
                                        index = 2, count = insightsCount,
                                        highlighted = lastClickedSettingId == "activity_queue",
                                        onClick = {
                                            lastClickedSettingId = "activity_queue"
                                            onActivityQueueClick()
                                        },
                                    )
                                    SettingListItem(
                                        icon = Tabler.Outline.CalendarEvent,
                                        title = stringResource(R.string.settings_upcoming),
                                        subtitle = stringResource(R.string.settings_upcoming_subtitle),
                                        index = 3, count = insightsCount,
                                        highlighted = lastClickedSettingId == "upcoming",
                                        onClick = {
                                            lastClickedSettingId = "upcoming"
                                            onUpcomingClick()
                                        },
                                    )
                                    SettingListItem(
                                        icon = Tabler.Outline.Inbox,
                                        title = stringResource(R.string.settings_requests),
                                        subtitle = stringResource(R.string.settings_requests_subtitle),
                                        index = 4, count = insightsCount,
                                        trailingText = pendingCount.takeIf { it > 0 }?.toString(),
                                        highlighted = lastClickedSettingId == "requests",
                                        onClick = {
                                            lastClickedSettingId = "requests"
                                            onRequestsClick()
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
                                            highlighted = lastClickedSettingId == "admin_dashboard",
                                            onClick = {
                                                lastClickedSettingId = "admin_dashboard"
                                                onAdminDashboard()
                                            },
                                        )
                                    }
                                    SettingListItem(
                                        icon = Tabler.Outline.Wand,
                                        title = stringResource(R.string.settings_setup_wizard),
                                        subtitle = stringResource(R.string.settings_setup_wizard_subtitle),
                                        index = systemIndex++, count = systemCount,
                                        highlighted = lastClickedSettingId == "setup_wizard",
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
                                    highlighted = lastClickedSettingId == "appearance",
                                    onClick = {
                                        lastClickedSettingId = "appearance"
                                        onAppearanceSettings(null)
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
                                    highlighted = lastClickedSettingId == "playback",
                                    onClick = {
                                        lastClickedSettingId = "playback"
                                        onPlaybackSettings(null)
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
                                    highlighted = lastClickedSettingId == "audio",
                                    onClick = {
                                        lastClickedSettingId = "audio"
                                        onAudioSettings(null)
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
                                    highlighted = lastClickedSettingId == "language",
                                    onClick = {
                                        lastClickedSettingId = "language"
                                        onLanguageSettings(null)
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
                                    highlighted = lastClickedSettingId == "notifications",
                                    onClick = {
                                        lastClickedSettingId = "notifications"
                                        onNotificationSettings(null)
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
                                    highlighted = lastClickedSettingId == "storage",
                                    onClick = {
                                        lastClickedSettingId = "storage"
                                        onStorageSettings(null)
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
                                    highlighted = lastClickedSettingId == "security",
                                    onClick = {
                                        lastClickedSettingId = "security"
                                        onSecuritySettings(null)
                                    },
                                )
                            }
                        }

                        item(key = "item_backup") {
                            AnimatedSettingsEntrance(11) {
                                SettingListItem(
                                    icon = Tabler.Outline.DatabaseExport,
                                    title = stringResource(R.string.settings_backup_restore),
                                    subtitle = stringResource(R.string.settings_backup_restore_subtitle),
                                    index = 0, count = 1,
                                    highlighted = lastClickedSettingId == "backup",
                                    onClick = {
                                        lastClickedSettingId = "backup"
                                        onBackupSettings(null)
                                    },
                                )
                            }
                        }

                        if (isTv) {
                            item(key = "group_screensaver") {
                                AnimatedSettingsEntrance(12) {
                                    SettingsGroup(
                                        icon = Tabler.Outline.Moon,
                                        title = stringResource(R.string.settings_screensaver),
                                        summary = {
                                            val cats = preferences.dreamImageCategories
                                            remember(cats) {
                                                cats.joinToString(", ") { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
                                            }
                                        },
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
                                            onCheckedChange = { viewModel.setDreamKenBurnsEnabled(it) },
                                        )
                                        SettingListItem(
                                            icon = Tabler.Outline.ArrowRight,
                                            title = stringResource(R.string.settings_transition_style),
                                            subtitle = preferences.dreamTransitionStyle.name,
                                            trailingText = preferences.dreamTransitionStyle.name,
                                            index = 4, count = dreamTotal,
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
                            AnimatedSettingsEntrance(if (isTv) 13 else 12) {
                                SettingListItem(
                                    icon = Tabler.Outline.Flask,
                                    title = stringResource(R.string.settings_experimental),
                                    subtitle = buildExperimentalSummary(preferences),
                                    index = 0, count = 1,
                                    highlighted = lastClickedSettingId == "experimental",
                                    onClick = {
                                        lastClickedSettingId = "experimental"
                                        onExperimentalSettings(null)
                                    },
                                )
                            }
                        }

                        item(key = "item_integrations") {
                            AnimatedSettingsEntrance(if (isTv) 14 else 13) {
                                SettingListItem(
                                    icon = Tabler.Outline.PlugConnected,
                                    title = stringResource(R.string.settings_integrations),
                                    subtitle = stringResource(R.string.settings_integrations_subtitle),
                                    index = 0, count = 1,
                                    highlighted = lastClickedSettingId == "integrations",
                                    onClick = {
                                        lastClickedSettingId = "integrations"
                                        onIntegrations(null)
                                    },
                                )
                            }
                        }

                        item(key = "item_about") {
                            AnimatedSettingsEntrance(if (isTv) 15 else 14) {
                                SettingListItem(
                                    icon = Tabler.Outline.InfoCircle,
                                    title = stringResource(R.string.settings_about),
                                    subtitle = stringResource(R.string.settings_about_subtitle),
                                    index = 0, count = 1,
                                    highlighted = lastClickedSettingId == "about",
                                    onClick = {
                                        lastClickedSettingId = "about"
                                        onAboutClick()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showSignOutConfirm) {
            AlertDialog(
                onDismissRequest = { showSignOutConfirm = false },
                title = { Text(if (signOutFromServer) stringResource(R.string.settings_sign_out_confirm_title_server) else stringResource(R.string.settings_sign_out_confirm_title)) },
                text = {
                    Text(
                        if (signOutFromServer) {
                            stringResource(R.string.settings_sign_out_confirm_message_server)
                        } else {
                            stringResource(R.string.settings_sign_out_confirm_message)
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val fromServer = signOutFromServer
                            showSignOutConfirm = false
                            onLogout(fromServer)
                        },
                    ) { Text(stringResource(R.string.settings_sign_out), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showSignOutConfirm = false }) { Text(stringResource(R.string.settings_cancel)) }
                },
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
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
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (isAdmin) MaterialTheme.colorScheme.tertiaryContainer
                                    else MaterialTheme.colorScheme.secondaryContainer
                                )
                                .padding(horizontal = 7.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = if (isAdmin) stringResource(R.string.settings_admin_badge) else stringResource(R.string.settings_member_badge),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isAdmin) MaterialTheme.colorScheme.onTertiaryContainer
                                else MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                shape = ShapeCache.smooth16
            )
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
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
