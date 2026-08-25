package com.raulshma.jellyplay.feature.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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
import com.raulshma.jellyplay.core.designsystem.theme.hairlineBorderColor
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.ui.components.HeaderStatus
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.LocalReducedMotion
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
import org.jetbrains.compose.resources.stringResource
import com.composables.icons.tabler.outline.Clock
import com.composables.icons.tabler.outline.Refresh
import com.raulshma.jellyplay.feature.home.generated.resources.sync_icon_content_description
import com.raulshma.jellyplay.feature.home.generated.resources.home_switch_user
import com.raulshma.jellyplay.feature.home.generated.resources.home_search_placeholder
import com.raulshma.jellyplay.feature.home.generated.resources.home_search
import com.raulshma.jellyplay.feature.home.generated.resources.home_go_online
import com.raulshma.jellyplay.feature.home.generated.resources.home_clear_search
import com.raulshma.jellyplay.feature.home.generated.resources.home_back
import com.raulshma.jellyplay.feature.home.generated.resources.Res

/** Translucency of the home app-bar capsules and the expanded-search surface scrim. */
private const val AppBarScrimAlpha = 0.85f

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
    val focusManager = LocalFocusManager.current
    val isTv = LocalTvMode.current

    val hasStatusIndicators = headerStatus !is HeaderStatus.None || showClock || pendingSyncCount > 0 || offlineMode != OfflineMode.ONLINE

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (!isTv) Modifier.statusBarsPadding() else Modifier)
            // Lift the whole dock (field + results) above the soft keyboard.
            // Must sit OUTSIDE the results box's heightIn cap: applying
            // imePadding below that cap lets the keyboard consume the results
            // budget (400dp − ~368dp inset = a 32dp sliver that only fits the
            // section header).
            .imePadding()
            .padding(
                start = 16.dp,
                top = 4.dp,
                end = 16.dp,
                bottom = 0.dp,
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
        contentAlignment = Alignment.TopStart
    ) {
        if (isSearchFocused) {
            Surface(
                shape = ShapeCache.smooth24,
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AppBarScrimAlpha),
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(
                    width = 1.dp,
                    color = hairlineBorderColor(),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(
                                start = 4.dp,
                                top = 0.dp,
                                end = 4.dp,
                                bottom = 0.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
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
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AppBarScrimAlpha)
                            ),
                    ) {
                        searchResultsContent()
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (!hasStatusIndicators) Arrangement.End else Arrangement.SpaceBetween,
            ) {
                CollapsedDockContent(
                    hasStatusIndicators = hasStatusIndicators,
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
            modifier = Modifier.size(38.dp),
        ) {
            Icon(
                Tabler.Outline.ArrowLeft,
                contentDescription = stringResource(Res.string.home_back),
                tint = appBarIconColorFaded,
                modifier = Modifier.size(22.dp),
            )
        }
    }

    BasicTextField(
        value = searchQuery,
        onValueChange = onQueryChange,
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 8.dp)
            .focusRequester(focusRequester),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = appBarIconColor,
        ),
        singleLine = true,
        cursorBrush = SolidColor(appBarIconColor),
        decorationBox = { innerTextField ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.home_search_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                innerTextField()
            }
        },
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
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    Tabler.Outline.X,
                    contentDescription = stringResource(Res.string.home_clear_search),
                    tint = appBarIconColorFaded,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OfflineToggleIcon(
    isGoingOnline: Boolean,
    onToggleOffline: () -> Unit,
) {
    IconButton(
        onClick = onToggleOffline,
        enabled = !isGoingOnline,
        modifier = Modifier.size(38.dp),
    ) {
        if (isGoingOnline) {
            LoadingIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                Tabler.Outline.Download,
                contentDescription = stringResource(Res.string.home_go_online),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
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
    val reducedMotion = LocalReducedMotion.current
    val infiniteTransition = rememberInfiniteTransition(label = "sync_draining")
    val rotation by if (isDraining && !reducedMotion) {
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
        Res.string.sync_icon_content_description,
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
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    imageVector = Tabler.Outline.Refresh,
                    contentDescription = contentDescription,
                    tint = if (isDraining) MaterialTheme.colorScheme.primary else tint,
                    modifier = Modifier
                        .size(22.dp)
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
    hasStatusIndicators: Boolean,
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
    // ── Start Side: Connectivity & Status Expressive Capsule (Only shown when indicators exist) ──
    if (hasStatusIndicators) {
        Surface(
            shape = ShapeCache.smoothPill,
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AppBarScrimAlpha),
            tonalElevation = 2.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(
                width = 1.dp,
                color = hairlineBorderColor(),
            ),
            modifier = Modifier.height(52.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (headerStatus !is HeaderStatus.None) {
                    HeaderStatusIndicator(
                        status = headerStatus,
                        tint = appBarIconColorFaded,
                    )
                }
                if (showClock) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        Icon(
                            imageVector = Tabler.Outline.Clock,
                            contentDescription = null,
                            tint = appBarIconColorFaded,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = rememberWallClockTimeString(),
                            color = appBarIconColorFaded,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }
                if (pendingSyncCount > 0) {
                    SyncStatusIcon(
                        pendingCount = pendingSyncCount,
                        isDraining = offlineMode == OfflineMode.ONLINE,
                        tint = appBarIconColorFaded,
                        onClick = onShowSyncDetails,
                    )
                }
                if (offlineMode != OfflineMode.ONLINE) {
                    val onlineFocusState = rememberTvFocusState()
                    Box(
                        modifier = Modifier
                            .then(onlineFocusState.focusModifier)
                            .tvFocusIndicator(onlineFocusState, CircleShape)
                    ) {
                        OfflineToggleIcon(
                            isGoingOnline = isGoingOnline,
                            onToggleOffline = onToggleOffline,
                        )
                    }
                }
            }
        }
    }

    // ── End Side: Navigation, Actions & User Expressive Capsule ──
    Surface(
        shape = ShapeCache.smoothPill,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AppBarScrimAlpha),
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(
            width = 1.dp,
            color = hairlineBorderColor(),
        ),
        modifier = Modifier.height(52.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (currentUser != null && currentServerUsers.size >= 2) {
                UserSwitcherChip(
                    currentUser = currentUser,
                    users = currentServerUsers,
                    onUserSwitch = onUserSwitch,
                )
            }
            ModeSwitch(
                currentMode = homeMode,
                onModeChange = onModeChange,
            )
            val searchFocusState = rememberTvFocusState()
            Box(
                modifier = Modifier
                    .then(searchFocusState.focusModifier)
                    .tvFocusIndicator(searchFocusState, CircleShape)
            ) {
                IconButton(
                    onClick = onSearchExpand,
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        Tabler.Outline.Search,
                        contentDescription = stringResource(Res.string.home_search),
                        tint = appBarIconColorFaded,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
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
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            UserAvatar(
                name = currentUser.name,
                size = 20.dp,
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
            title = stringResource(Res.string.home_switch_user),
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
