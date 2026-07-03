package com.raulshma.jellyplay.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.ui.focus.onFocusEvent
import com.raulshma.jellyplay.core.ui.components.TopBarStyle
import com.raulshma.jellyplay.core.ui.feedback.LocalUserMessageBus
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import com.raulshma.jellyplay.core.ui.navigation.Route
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.DreamImageCategory
import com.raulshma.jellyplay.core.model.DreamTransitionStyle
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.ThemeMode
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
import com.raulshma.jellyplay.feature.settings.R
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

private val LocalAnimateSettingsEntrance = staticCompositionLocalOf { false }

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: (Boolean) -> Unit,
    onServerManagement: (String?) -> Unit = {},
    onUserManagement: (String?) -> Unit = {},
    onSeerrSettings: (String?) -> Unit = {},
    onAdminDashboard: () -> Unit = {},
    onSetupWizard: () -> Unit = {},
    onNewsletterClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onWatchProgressHeatmapClick: () -> Unit = {},
    onAppearanceSettings: (String?) -> Unit = {},
    onPinnedHomeSections: (String?) -> Unit = {},
    onHomeLayoutPresets: (String?) -> Unit = {},
    onPlaybackSettings: (String?) -> Unit = {},
    onAudioSettings: (String?) -> Unit = {},
    onLanguageSettings: (String?) -> Unit = {},
    onNotificationSettings: (String?) -> Unit = {},
    onStorageSettings: (String?) -> Unit = {},
    onSecuritySettings: (String?) -> Unit = {},
    onBackupSettings: (String?) -> Unit = {},
    onExperimentalSettings: (String?) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences = viewModel.preferences
    val userName = viewModel.currentUserName
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current

    val listFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val leadingFocusRequester = remember { FocusRequester() }
    val trailingFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    var animateEntrance by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animateEntrance = true
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

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }

    val filteredItems = remember(searchQuery) {
        // Ranked fuzzy match: tolerates typos, merged/split terms and synonyms so advanced
        // settings with jargon-heavy titles/thin keyword lists stay findable. See
        // SettingsSearchMatcher for scoring details.
        SettingsSearchMatcher.search(searchQuery, SettingsSearchRegistry.items)
    }

    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        searchQuery = ""
    }

    com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold(
        title = "Settings",
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
                            userName = userName,
                            showAdvanced = preferences.showAdvancedSettings,
                            onNewsletterClick = onNewsletterClick,
                            onToggleAdvanced = { viewModel.setShowAdvancedSettings(!preferences.showAdvancedSettings) },
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
                                        "Search settings...",
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
                                        contentDescription = "Search / Back",
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        if (searchQuery.isNotBlank() || isSearchActive) {
                                            SettingsIconButton(
                                                onClick = { searchQuery = "" },
                                                icon = Tabler.Outline.X,
                                                contentDescription = "Clear search",
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
                                        } else {
                                            SettingsIconButton(
                                                onClick = onNewsletterClick,
                                                icon = Tabler.Outline.Mail,
                                                contentDescription = "Newsletter",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                iconSize = 20.dp,
                                                modifier = Modifier
                                                    .focusRequester(trailingFocusRequester)
                                                    .onDpadKey(
                                                        onLeft = {
                                                            searchFocusRequester.tryRequestFocus()
                                                            true
                                                        },
                                                    )
                                            )
                                            AdvancedSettingsToggleButton(
                                                showAdvanced = preferences.showAdvancedSettings,
                                                onToggle = { viewModel.setShowAdvancedSettings(!preferences.showAdvancedSettings) },
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = if (userName.isNotBlank()) userName.take(1).uppercase() else "U",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                            Spacer(Modifier.width(4.dp))
                                        }
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
                                                            text = "Adv",
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
                                                     onLogout(false)
                                                 } else {
                                                    when (item.route) {
                                                        is Route.ServerManagement -> onServerManagement(item.id)
                                                        is Route.UserManagement -> onUserManagement(item.id)
                                                        is Route.SeerrSettings -> onSeerrSettings(item.id)
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
                                    text = "No matching settings found",
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
                        state = rememberLazyListState(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(
                            start = adaptiveInfo.contentPadding(LocalTvMode.current),
                            end = adaptiveInfo.contentPadding(LocalTvMode.current),
                            bottom = adaptiveInfo.bottomPadding(LocalTvMode.current),
                        ),
                    ) {
                        item {
                            AnimatedSettingsEntrance(0) {
                                if (userName.isNotBlank()) {
                                    SettingsProfileBanner(
                                userName = userName,
                            )
                        }
                    }
                }

                item {
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

                item {
                    AnimatedSettingsEntrance(1) {
                        SettingsGroup(
                            icon = Tabler.Outline.User,
                            title = "Account",
                            summary = { "Signed in as $userName" },
                            initiallyExpanded = true,
                        ) {
                            SettingListItem(
                                icon = Tabler.Outline.Logout,
                                title = "Sign Out",
                                subtitle = "Log out of current account",
                                index = 0, count = 2,
                                isDestructive = true,
                                onClick = {
                                    onLogout(false)
                                },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Logout,
                                title = "Sign Out from Server",
                                subtitle = "Revoke this device's session on the server",
                                index = 1, count = 2,
                                isDestructive = true,
                                onClick = {
                                    onLogout(true)
                                },
                            )
                        }
                    }
                }

                item {
                    AnimatedSettingsEntrance(2) {
                        SettingListItem(
                            icon = Tabler.Outline.Palette,
                            title = "Appearance",
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

                item {
                    AnimatedSettingsEntrance(3) {
                        SettingListItem(
                            icon = Tabler.Outline.PlayerPlay,
                            title = "Playback",
                            subtitle = "Player Engine: ${preferences.preferredPlayer.displayName}",
                            index = 0, count = 1,
                            highlighted = lastClickedSettingId == "playback",
                            onClick = {
                                lastClickedSettingId = "playback"
                                onPlaybackSettings(null)
                            },
                        )
                    }
                }

                item {
                    AnimatedSettingsEntrance(4) {
                        SettingListItem(
                            icon = Tabler.Outline.Music,
                            title = "Audio Player",
                            subtitle = "Default speed: ${if (preferences.audioDefaultSpeed == 1.0f) "1x" else "${preferences.audioDefaultSpeed}x"}",
                            index = 0, count = 1,
                            highlighted = lastClickedSettingId == "audio",
                            onClick = {
                                lastClickedSettingId = "audio"
                                onAudioSettings(null)
                            },
                        )
                    }
                }

                item {
                    AnimatedSettingsEntrance(5) {
                        SettingListItem(
                            icon = Tabler.Outline.Language,
                            title = "Language & Subtitles",
                            subtitle = "Audio: ${preferences.preferredAudioLanguage ?: "Default"}",
                            index = 0, count = 1,
                            highlighted = lastClickedSettingId == "language",
                            onClick = {
                                lastClickedSettingId = "language"
                                onLanguageSettings(null)
                            },
                        )
                    }
                }

                item {
                    AnimatedSettingsEntrance(6) {
                        val notifPrefs = preferences.notificationPreferences
                        SettingListItem(
                            icon = Tabler.Outline.Bell,
                            title = "Notifications",
                            subtitle = if (notifPrefs.enabled) "Checking ${notifPrefs.checkFrequency.displayName.lowercase()}" else "Disabled",
                            index = 0, count = 1,
                            highlighted = lastClickedSettingId == "notifications",
                            onClick = {
                                lastClickedSettingId = "notifications"
                                onNotificationSettings(null)
                            },
                        )
                    }
                }

                item {
                    AnimatedSettingsEntrance(7) {
                        SettingListItem(
                            icon = Tabler.Outline.Database,
                            title = "Downloads & Storage",
                            subtitle = "Cache: ${viewModel.cacheSizeMb} MB",
                            index = 0, count = 1,
                            highlighted = lastClickedSettingId == "storage",
                            onClick = {
                                lastClickedSettingId = "storage"
                                onStorageSettings(null)
                            },
                        )
                    }
                }

                item {
                    AnimatedSettingsEntrance(8) {
                        SettingListItem(
                            icon = Tabler.Outline.Lock,
                            title = "Security",
                            subtitle = when {
                                preferences.pinLockEnabled && preferences.biometricLockEnabled -> "PIN + Biometric lock: On"
                                preferences.biometricLockEnabled -> "Biometric lock: On"
                                preferences.pinLockEnabled -> "PIN lock: On"
                                else -> "Lock: Off"
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

                item {
                    AnimatedSettingsEntrance(9) {
                        SettingListItem(
                            icon = Tabler.Outline.DatabaseExport,
                            title = "Backup & Restore",
                            subtitle = "Export or import app settings",
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
                    item {
                        AnimatedSettingsEntrance(10) {
                            SettingsGroup(
                                icon = Tabler.Outline.Moon,
                                title = "Screensaver",
                                summary = {
                                    val cats = preferences.dreamImageCategories.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
                                    cats.joinToString(", ")
                                },
                            ) {
                                val dreamTotal = 5
                                SettingToggleItem(
                                    icon = Tabler.Outline.Typography,
                                    title = "Show Title",
                                    subtitle = if (preferences.dreamShowTitle) "Display media title" else "Hide media title",
                                    checked = preferences.dreamShowTitle,
                                    index = 0, count = dreamTotal,
                                    onCheckedChange = { viewModel.setDreamShowTitle(it) },
                                )
                                SettingListItem(
                                    icon = Tabler.Outline.Movie,
                                    title = "Categories",
                                    subtitle = "Choose which library types appear",
                                    trailingText = preferences.dreamImageCategories.joinToString(", ") {
                                        when (it) {
                                            DreamImageCategory.MOVIES -> "Movies"
                                            DreamImageCategory.SERIES -> "TV"
                                            DreamImageCategory.MUSIC -> "Music"
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
                                    title = "Slideshow Interval",
                                    subtitle = "Time between image changes",
                                    trailingText = "${preferences.dreamSlideshowIntervalMs / 1000}s",
                                    index = 2, count = dreamTotal,
                                    onClick = {
                                        val intervals = listOf(5_000L, 10_000L, 15_000L, 30_000L, 60_000L)
                                        val currentIndex = intervals.indexOf(preferences.dreamSlideshowIntervalMs)
                                        val nextIndex = (currentIndex + 1) % intervals.size
                                        viewModel.setDreamSlideshowIntervalMs(intervals[nextIndex])
                                    },
                                )
                                SettingToggleItem(
                                    icon = Tabler.Outline.Wand,
                                    title = "Ken Burns Effect",
                                    subtitle = if (preferences.dreamKenBurnsEnabled) "Pan and zoom animation" else "Static display",
                                    checked = preferences.dreamKenBurnsEnabled,
                                    index = 3, count = dreamTotal,
                                    onCheckedChange = { viewModel.setDreamKenBurnsEnabled(it) },
                                )
                                SettingListItem(
                                    icon = Tabler.Outline.ArrowRight,
                                    title = "Transition Style",
                                    subtitle = preferences.dreamTransitionStyle.name,
                                    trailingText = preferences.dreamTransitionStyle.name,
                                    index = 4, count = dreamTotal,
                                    onClick = {
                                        val styles = DreamTransitionStyle.entries
                                        val currentIndex = styles.indexOf(preferences.dreamTransitionStyle)
                                        val nextIndex = (currentIndex + 1) % styles.size
                                        viewModel.setDreamTransitionStyle(styles[nextIndex])
                                    },
                                )
                            }
                        }
                    }
                }

                item {
                    AnimatedSettingsEntrance(if (isTv) 11 else 10) {
                        SettingListItem(
                            icon = Tabler.Outline.Flask,
                            title = "Experimental",
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

                item {
                    AnimatedSettingsEntrance(if (isTv) 12 else 11) {
                        SettingListItem(
                            icon = Tabler.Outline.InfoCircle,
                            title = "About",
                            subtitle = "App version and licenses",
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
}
}

private fun buildAppearanceSummary(preferences: com.raulshma.jellyplay.core.model.UserPreferences): String {
    val parts = mutableListOf<String>()
    parts.add(preferences.themeMode.name.lowercase().replaceFirstChar { it.uppercase() })
    if (preferences.dynamicTheming) parts.add("Dynamic")
    if (preferences.oledMode) parts.add("OLED")
    if (preferences.contrastLevel != ContrastLevel.DEFAULT) parts.add("${preferences.contrastLevel.name.lowercase().replaceFirstChar { it.uppercase() }} contrast")
    if (preferences.performanceMode) parts.add("Performance")
    return parts.joinToString(", ")
}

private fun buildExperimentalSummary(preferences: com.raulshma.jellyplay.core.model.UserPreferences): String {
    val count = preferences.enabledExperimentalFeatures.size
    return if (count == 0) "Early-access features" else "$count feature${if (count != 1) "s" else ""} enabled"
}

@Composable
private fun SettingsProfileBanner(
    userName: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth24)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Tabler.Outline.User,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                "Signed in as",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Text(
                userName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
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
    userName: String,
    showAdvanced: Boolean,
    onNewsletterClick: () -> Unit,
    onToggleAdvanced: () -> Unit,
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
                .weight(1f)
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
                text = "Search settings...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SettingsIconButton(
            onClick = onNewsletterClick,
            icon = Tabler.Outline.Mail,
            contentDescription = "Newsletter",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            iconSize = 20.dp,
        )

        AdvancedSettingsToggleButton(
            showAdvanced = showAdvanced,
            onToggle = onToggleAdvanced,
        )

        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (userName.isNotBlank()) userName.take(1).uppercase() else "U",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(8.dp))
    }
}
