package com.raulshma.jellyplay.feature.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Surface
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.RequestOrRestoreFocus
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.ui.components.HeaderStatus
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayCircularProgressIndicator
import com.raulshma.jellyplay.core.ui.components.ModeSwitch
import com.raulshma.jellyplay.core.ui.components.rememberWallClockTimeString
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowLeft
import com.composables.icons.tabler.outline.Cast
import com.composables.icons.tabler.outline.DotsVertical
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.Settings
import com.composables.icons.tabler.outline.Users
import com.composables.icons.tabler.outline.Wand
import com.composables.icons.tabler.outline.Wifi
import com.composables.icons.tabler.outline.WifiOff
import com.composables.icons.tabler.outline.X
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.border
import androidx.compose.ui.res.stringResource
import com.composables.icons.tabler.outline.Clock
import com.composables.icons.tabler.outline.Refresh

@Composable
fun HomeTopDock(
    appBarIconColor: Color,
    appBarIconColorFaded: Color,
    isSearchFocused: Boolean,
    searchQuery: String,
    offlineMode: OfflineMode,
    homeMode: HomeMode,
    headerStatus: HeaderStatus,
    activeDownloadCount: Int,
    pendingSyncCount: Int,
    showClock: Boolean,
    currentUser: UserInfo?,
    currentServerUsers: List<UserInfo>,
    onUserSwitch: (String) -> Unit,
    onModeChange: (HomeMode) -> Unit,
    onSearchExpanded: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onToggleOffline: () -> Unit,
    isGoingOnline: Boolean = false,
    onShowSyncDetails: () -> Unit = {},
    searchResultsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Icon colors are now derived once in MainHomeContent and passed in; this
    // previously recomputed an identical derivedStateOf + lerp on every
    // recompose, duplicating work the caller had already done.
    val focusManager = LocalFocusManager.current

    val isTv = LocalTvMode.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (!isTv) Modifier.statusBarsPadding() else Modifier)
            .padding(
                horizontal = 16.dp,
                vertical = 4.dp
            )
            .onDpadKey(
                onBack = {
                    if (isSearchFocused) {
                        onClearSearch()
                        focusManager.clearFocus()
                        true
                    } else false
                },
            ),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier
                .then(if (isSearchFocused) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
                .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
                .graphicsLayer {
                    shadowElevation = if (isSearchFocused) 4f else 0f
                    shape = ShapeCache.smooth24
                    clip = true
                }
                .background(
                    if (isSearchFocused) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        Color.Transparent
                    }
                )
        ) {
            Row(
                modifier = Modifier
                    .then(if (isSearchFocused) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
                    .height(64.dp)
                    .padding(
                        start = 8.dp,
                        top = 0.dp,
                        end = if (isSearchFocused) 8.dp else 0.dp,
                        bottom = 0.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (isSearchFocused) {
                    SearchExpandedContent(
                        searchQuery = searchQuery,
                        appBarIconColor = appBarIconColor,
                        appBarIconColorFaded = appBarIconColorFaded,
                        onBack = {
                            onSearchExpanded(false)
                            onClearSearch()
                            focusManager.clearFocus()
                        },
                        onQueryChange = onSearchQueryChange,
                        onClear = {
                            onClearSearch()
                            focusManager.clearFocus()
                        },
                    )
                } else {
                    CollapsedDockContent(
                        offlineMode = offlineMode,
                        homeMode = homeMode,
                        headerStatus = headerStatus,
                        appBarIconColorFaded = appBarIconColorFaded,
                        pendingSyncCount = pendingSyncCount,
                        showClock = showClock,
                        currentUser = currentUser,
                        currentServerUsers = currentServerUsers,
                        onUserSwitch = onUserSwitch,
                        onToggleOffline = onToggleOffline,
                        isGoingOnline = isGoingOnline,
                        onShowSyncDetails = onShowSyncDetails,
                        onModeChange = onModeChange,
                        onSearchExpand = { onSearchExpanded(true) },
                    )
                }
            }

            if (isSearchFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f)
                        ),
                ) {
                    searchResultsContent()
                }
            }
        }
    }
}

@Composable
private fun RowScope.SearchExpandedContent(
    searchQuery: String,
    appBarIconColor: Color,
    appBarIconColorFaded: Color,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    val backFocusState = rememberTvFocusState()
    Box(
        modifier = Modifier
            .then(backFocusState.focusModifier)
            .tvFocusIndicator(backFocusState, CircleShape)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                Tabler.Outline.ArrowLeft,
                contentDescription = stringResource(R.string.home_back),
                tint = appBarIconColorFaded,
                modifier = Modifier.size(20.dp),
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    val searchTextFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        cursorColor = appBarIconColor,
    )

    TextField(
        value = searchQuery,
        onValueChange = onQueryChange,
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 4.dp)
            .height(48.dp)
            .focusRequester(focusRequester),
        placeholder = {
            Text(
                stringResource(R.string.home_search_placeholder),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        colors = searchTextFieldColors,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = appBarIconColor,
        ),
        singleLine = true,
    )

    RequestOrRestoreFocus(focusRequester, "home_search")

    if (searchQuery.isNotEmpty()) {
        val clearFocusState = rememberTvFocusState()
        Box(
            modifier = Modifier
                .then(clearFocusState.focusModifier)
                .tvFocusIndicator(clearFocusState, CircleShape)
        ) {
            IconButton(
                onClick = onClear,
                shapes = androidx.compose.material3.IconButtonDefaults.shapes(),
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Tabler.Outline.X,
                    contentDescription = stringResource(R.string.home_clear_search),
                    tint = appBarIconColorFaded,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun OfflineToggleIcon(
    isGoingOnline: Boolean,
    onToggleOffline: () -> Unit,
) {
    IconButton(
        onClick = onToggleOffline,
        enabled = !isGoingOnline,
        modifier = Modifier.size(40.dp),
    ) {
        if (isGoingOnline) {
            JellyPlayCircularProgressIndicator(
                modifier = Modifier.size(20.dp),
            )
        } else {
            Icon(
                Tabler.Outline.Download,
                contentDescription = stringResource(R.string.home_go_online),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Dedicated affordance for pending playback-progress sync. Shown as a sibling
 * of the offline toggle only when there are queued events. Uses the project's
 * established sync icon (Tabler.Outline.Refresh) plus a count badge; rotates
 * while draining to signal active progress. Opens the sync details sheet.
 */
@Composable
private fun SyncStatusIcon(
    pendingCount: Int,
    isDraining: Boolean,
    tint: Color,
    onClick: () -> Unit,
) {
    val syncFocusState = rememberTvFocusState()
    // The draining rotation only applies while isDraining. When the icon is
    // shown offline with queued events (isDraining = false), the icon is
    // visually static, so gate the sole infinite-transition child on it. An
    // InfiniteTransition with no children stops its frame clock, eliminating a
    // continuous ~60Hz recomposition of this composable while idle — the same
    // gating pattern used for the hero pulse animations (HomeHero.kt).
    val infiniteTransition = rememberInfiniteTransition(label = "sync_draining")
    val rotation by if (isDraining) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "sync_draining_rotation",
        )
    } else androidx.compose.runtime.mutableStateOf(0f)
    val contentDescription = stringResource(
        R.string.sync_icon_content_description,
        pendingCount,
    )
    Box(
        modifier = Modifier
            .then(syncFocusState.focusModifier)
            .tvFocusIndicator(syncFocusState, CircleShape)
    ) {
        BadgedBox(
            badge = {
                Badge { Text(pendingCount.coerceAtMost(99).toString()) }
            },
        ) {
            IconButton(
                onClick = onClick,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Tabler.Outline.Refresh,
                    contentDescription = contentDescription,
                    tint = if (isDraining) MaterialTheme.colorScheme.primary else tint,
                    modifier = Modifier
                        .size(20.dp)
                        .then(
                            if (isDraining) Modifier.graphicsLayer { rotationZ = rotation }
                            else Modifier,
                        ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CollapsedDockContent(
    offlineMode: OfflineMode,
    homeMode: HomeMode,
    headerStatus: HeaderStatus,
    appBarIconColorFaded: Color,
    pendingSyncCount: Int,
    showClock: Boolean,
    currentUser: UserInfo?,
    currentServerUsers: List<UserInfo>,
    onUserSwitch: (String) -> Unit,
    onToggleOffline: () -> Unit,
    isGoingOnline: Boolean = false,
    onShowSyncDetails: () -> Unit = {},
    onModeChange: (HomeMode) -> Unit,
    onSearchExpand: () -> Unit,
) {
    // Quick user switcher : tappable avatar chip in the dock, only when the
    // server has ≥2 persisted users. Opens a DropdownMenu (mobile) or a
    // TvSafeSheet (TV) — the project's canonical dual-renderer menu idiom.
    if (currentUser != null && currentServerUsers.size >= 2) {
        UserSwitcherChip(
            currentUser = currentUser,
            users = currentServerUsers,
            onUserSwitch = onUserSwitch,
        )
    }
    if (showClock) {
        Row(
            modifier = Modifier
                .padding(end = 12.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = ShapeCache.smooth12
                )
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    shape = ShapeCache.smooth12
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Tabler.Outline.Clock,
                contentDescription = null,
                tint = appBarIconColorFaded,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = rememberWallClockTimeString(),
                color = appBarIconColorFaded,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
            )
        }
    }
    if (offlineMode != OfflineMode.ONLINE) {
        val onlineFocusState = rememberTvFocusState()
        Box(
            modifier = Modifier
                .then(onlineFocusState.focusModifier)
                .tvFocusIndicator(onlineFocusState, CircleShape)
        ) {
            // The offline toggle does one job: switch offline mode. Pending
            // playback sync is surfaced by a dedicated SyncStatusIcon sibling
            // (added below) so neither affordance obscures the other.
            OfflineToggleIcon(
                isGoingOnline = isGoingOnline,
                onToggleOffline = onToggleOffline,
            )
        }
    }
    // Pending playback-progress events: show a dedicated sync affordance as a
    // sibling of the offline toggle. It conveys purpose (sync icon + count)
    // rather than hiding behind the offline button, and opens the pending
    // sync details sheet on click. Renders both offline (queued, waiting for
    // reconnect) and online (draining) — animated when draining.
    if (pendingSyncCount > 0) {
        SyncStatusIcon(
            pendingCount = pendingSyncCount,
            isDraining = offlineMode == OfflineMode.ONLINE,
            tint = appBarIconColorFaded,
            onClick = onShowSyncDetails,
        )
    }
    ModeSwitch(
        currentMode = homeMode,
        onModeChange = onModeChange,
    )
    HeaderStatusIndicator(
        status = headerStatus,
        tint = appBarIconColorFaded,
    )
    val searchFocusState = rememberTvFocusState()
    Box(
        modifier = Modifier
            .then(searchFocusState.focusModifier)
            .tvFocusIndicator(searchFocusState, CircleShape)
    ) {
        IconButton(
            onClick = onSearchExpand,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                Tabler.Outline.Search,
                contentDescription = stringResource(R.string.home_search),
                tint = appBarIconColorFaded,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeFabMenu(
    isExpanded: Boolean,
    onToggle: (Boolean) -> Unit,
    activeDownloadCount: Int,
    offlineMode: OfflineMode,
    onSurpriseClick: () -> Unit,
    onSyncPlayClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onToggleOffline: () -> Unit,
    isGoingOnline: Boolean = false,
    onPlayOnClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButtonMenu(
        expanded = isExpanded,
        button = {
            androidx.compose.material3.BadgedBox(
                badge = {
                    if (activeDownloadCount > 0 && !isExpanded) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ) {
                            Text(
                                activeDownloadCount.toString(),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            ) {
                ToggleFloatingActionButton(
                    checked = isExpanded,
                    onCheckedChange = onToggle,
                    containerColor = ToggleFloatingActionButtonDefaults.containerColor(
                        initialColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.65f),
                        finalColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    ),
                    containerCornerRadius = { 28.dp },
                ) {
                    Icon(
                        if (isExpanded) Tabler.Outline.X else Tabler.Outline.DotsVertical,
                        contentDescription = if (isExpanded) stringResource(R.string.home_close_menu) else stringResource(R.string.home_more_options),
                        tint = if (isExpanded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    )
                }
            }
        },
        modifier = modifier
            .padding(
                end = 8.dp,
                bottom = 4.dp,
            ),
    ) {
        FloatingActionButtonMenuItem(
            onClick = {
                onToggle(false)
                onSurpriseClick()
            },
            text = { Text(stringResource(R.string.home_surprise_me)) },
            icon = {
                Icon(
                    Tabler.Outline.Wand,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        )
        FloatingActionButtonMenuItem(
            onClick = {
                onToggle(false)
                onSyncPlayClick()
            },
            text = { Text(stringResource(R.string.home_syncplay)) },
            icon = {
                Icon(
                    Tabler.Outline.Users,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        )
        FloatingActionButtonMenuItem(
            onClick = {
                onToggle(false)
                onDownloadsClick()
            },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_downloads))
                    if (activeDownloadCount > 0) {
                        Badge(
                            modifier = Modifier.padding(start = 6.dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                        ) {
                            Text(
                                activeDownloadCount.toString(),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            },
            icon = {
                Icon(
                    Tabler.Outline.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        )
        FloatingActionButtonMenuItem(
            onClick = {
                onToggle(false)
                // Guard re-taps while the offline→online transition is already
                // in flight; FloatingActionButtonMenuItem has no `enabled` arg.
                if (!isGoingOnline) onToggleOffline()
            },
            text = {
                Text(
                    stringResource(
                        when {
                            isGoingOnline -> R.string.home_going_online
                            offlineMode != OfflineMode.ONLINE -> R.string.home_go_online
                            else -> R.string.home_go_offline
                        }
                    )
                )
            },
            icon = {
                if (isGoingOnline) {
                    JellyPlayCircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Icon(
                        if (offlineMode != OfflineMode.ONLINE) Tabler.Outline.Wifi else Tabler.Outline.WifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            containerColor = if (offlineMode != OfflineMode.ONLINE) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        )
        FloatingActionButtonMenuItem(
            onClick = {
                onToggle(false)
                onPlayOnClick()
            },
            text = { Text(stringResource(R.string.home_play_on)) },
            icon = {
                Icon(
                    Tabler.Outline.Cast,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        )
        FloatingActionButtonMenuItem(
            onClick = {
                onToggle(false)
                onSettingsClick()
            },
            text = { Text(stringResource(R.string.home_settings)) },
            icon = {
                Icon(
                    Tabler.Outline.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        )
    }
}

/**
 * The home app-bar quick user switcher chip. Renders the current user's
 * initials avatar + name; tapping opens a [DropdownMenu] (mobile) or a
 * [TvSafeSheet] (TV) listing every persisted user for the server. Mirrors the
 * `DetailTopBar` overflow-menu idiom (single shared option list, dual renderer).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserSwitcherChip(
    currentUser: UserInfo,
    users: List<UserInfo>,
    onUserSwitch: (String) -> Unit,
) {
    val isTv = LocalTvMode.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showTvMenu by remember { mutableStateOf(false) }

    val colorScheme = MaterialTheme.colorScheme
    val (containerTriplet, onContainerTriplet) = remember(colorScheme) {
        avatarColorPairForChip(colorScheme)
    }
    val (avatarColor, onAvatarColor) = remember(currentUser.name, containerTriplet, onContainerTriplet) {
        avatarColorsForName(currentUser.name, containerTriplet, onContainerTriplet)
    }

    val chipFocusState = rememberTvFocusState()
    Box {
        Row(
            modifier = Modifier
                .padding(end = 12.dp)
                .then(chipFocusState.focusModifier)
                .tvFocusIndicator(chipFocusState, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    shape = CircleShape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (isTv) showTvMenu = true else menuExpanded = true
                }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            UserAvatar(
                name = currentUser.name,
                size = 22.dp,
                avatarColor = avatarColor,
                onAvatarColor = onAvatarColor,
            )
            Text(
                text = currentUser.name.ifBlank { "?" },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
        }

        if (!isTv) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                val options = rememberUserSwitchOptions(
                    users = users,
                    currentUserId = currentUser.id,
                    onClose = { menuExpanded = false },
                    onUserSwitch = onUserSwitch,
                )
                options.forEach { option -> UserSwitchDropdownItem(option) }
            }
        }
    }

    if (isTv && showTvMenu) {
        val options = rememberUserSwitchOptions(
            users = users,
            currentUserId = currentUser.id,
            onClose = { showTvMenu = false },
            onUserSwitch = onUserSwitch,
        )
        TvSafeSheet(
            onDismissRequest = { showTvMenu = false },
            title = stringResource(R.string.home_switch_user),
        ) {
            options.forEach { option -> UserSwitchTvRow(option) }
        }
    }
}

/** Palette helper duplicating `HomeUserSwitchMenu`'s private pair (kept local to
 * avoid widening that file's API; the colors are the M3 container triplet). */
private fun avatarColorPairForChip(cs: androidx.compose.material3.ColorScheme) =
    listOf(cs.primaryContainer, cs.secondaryContainer, cs.tertiaryContainer) to
        listOf(cs.onPrimaryContainer, cs.onSecondaryContainer, cs.onTertiaryContainer)

private fun avatarColorsForName(
    name: String,
    containerTriplet: List<Color>,
    onContainerTriplet: List<Color>,
): Pair<Color, Color> {
    val raw = name.hashCode().mod(containerTriplet.size)
    val index = if (raw < 0) -raw else raw
    return containerTriplet[index] to onContainerTriplet[index]
}
