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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.res.stringResource
import com.composables.icons.tabler.outline.Clock
import com.composables.icons.tabler.outline.Refresh

/** Translucency of the home app-bar capsules and the expanded-search surface scrim. */
private const val AppBarScrimAlpha = 0.85f

/**
 * The dock's whole data surface, bundled — the former 15 flat params (plus
 * the callbacks' 7 lambdas) meant every dock feature was a 3-signature
 * lockstep edit (screen → scrim → dock); same disease [HomeContentState]
 * cured for the content list. Constructed inline at the call site:
 * `@Immutable` + value equality keep the dock skippable.
 */
@Immutable
internal data class HomeDockState(
    val offlineMode: OfflineMode,
    val homeMode: HomeMode,
    val headerStatus: HeaderStatus,
    val pendingSyncCount: Int,
    val showClock: Boolean,
    val currentUser: UserInfo?,
    val currentServerUsers: List<UserInfo>,
    /** True while the search field is focused/expanded (branch + dpad routing). */
    val isSearchFocused: Boolean,
    val isGoingOnline: Boolean = false,
    /** Scrim-side D-pad-down-to-hero gate; not read by the dock body itself. */
    val homeHeroEnabled: Boolean = true,
    /** Scrim-side D-pad-down-to-hero gate; not read by the dock body itself. */
    val hasFeaturedItem: Boolean = false,
    /** Scrim-side auto-hide gate; not read by the dock body itself. */
    val hideTopHeaderOnScroll: Boolean = false,
)

/** The dock's whole interaction surface — see [HomeDockState]. */
@Immutable
internal data class HomeDockCallbacks(
    val onUserSwitch: (String) -> Unit,
    val onModeChange: (HomeMode) -> Unit,
    val onSearchExpanded: (Boolean) -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onClearSearch: () -> Unit,
    val onToggleOffline: () -> Unit,
    val onShowSyncDetails: () -> Unit = {},
)

@Composable
internal fun HomeTopDock(
    appBarIconColor: Color,
    appBarIconColorFaded: Color,
    searchQuery: String,
    state: HomeDockState,
    callbacks: HomeDockCallbacks,
    searchResultsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTv = LocalTvMode.current

    val hasStatusIndicators = state.headerStatus !is HeaderStatus.None || state.showClock ||
        state.pendingSyncCount > 0 || state.offlineMode != OfflineMode.ONLINE

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
            // Back while the search field is focused collapses it. The
            // teardown ordering (collapse → clear query → drop keyboard
            // focus) lives in the caller's HomeSearchSession — the dock only
            // forwards, it never re-implements parts of the sequence.
            .onDpadKey(
                onBack = {
                    if (state.isSearchFocused) {
                        callbacks.onClearSearch()
                        true
                    } else false
                },
            ),
        contentAlignment = Alignment.TopStart
    ) {
        if (state.isSearchFocused) {
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
                            // Both forward to the caller's HomeSearchSession
                            // close (collapse + clear + defocus as ONE
                            // ordered sequence) — formerly each path here
                            // hand-assembled a partial copy of that triple.
                            onBack = { callbacks.onSearchExpanded(false) },
                            onQueryChange = callbacks.onSearchQueryChange,
                            onClear = callbacks.onClearSearch,
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
                    state = state,
                    callbacks = callbacks,
                    appBarIconColorFaded = appBarIconColorFaded,
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
                contentDescription = stringResource(R.string.home_back),
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
                        text = stringResource(R.string.home_search_placeholder),
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
                    contentDescription = stringResource(R.string.home_clear_search),
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
                contentDescription = stringResource(R.string.home_go_online),
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
    state: HomeDockState,
    callbacks: HomeDockCallbacks,
    appBarIconColorFaded: Color,
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
                if (state.headerStatus !is HeaderStatus.None) {
                    HeaderStatusIndicator(
                        status = state.headerStatus,
                        tint = appBarIconColorFaded,
                    )
                }
                if (state.showClock) {
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
                if (state.pendingSyncCount > 0) {
                    SyncStatusIcon(
                        pendingCount = state.pendingSyncCount,
                        isDraining = state.offlineMode == OfflineMode.ONLINE,
                        tint = appBarIconColorFaded,
                        onClick = callbacks.onShowSyncDetails,
                    )
                }
                if (state.offlineMode != OfflineMode.ONLINE) {
                    val onlineFocusState = rememberTvFocusState()
                    Box(
                        modifier = Modifier
                            .then(onlineFocusState.focusModifier)
                            .tvFocusIndicator(onlineFocusState, CircleShape)
                    ) {
                        OfflineToggleIcon(
                            isGoingOnline = state.isGoingOnline,
                            onToggleOffline = callbacks.onToggleOffline,
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
            if (state.currentUser != null && state.currentServerUsers.size >= 2) {
                UserSwitcherChip(
                    currentUser = state.currentUser,
                    users = state.currentServerUsers,
                    onUserSwitch = callbacks.onUserSwitch,
                )
            }
            ModeSwitch(
                currentMode = state.homeMode,
                onModeChange = callbacks.onModeChange,
            )
            val searchFocusState = rememberTvFocusState()
            Box(
                modifier = Modifier
                    .then(searchFocusState.focusModifier)
                    .tvFocusIndicator(searchFocusState, CircleShape)
            ) {
                IconButton(
                    onClick = { callbacks.onSearchExpanded(true) },
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        Tabler.Outline.Search,
                        contentDescription = stringResource(R.string.home_search),
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
        avatarColorPair(colorScheme)
    }
    val (avatarColor, onAvatarColor) = remember(currentUser.name, containerTriplet, onContainerTriplet) {
        avatarColorsFor(currentUser.name, containerTriplet, onContainerTriplet)
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
            title = stringResource(R.string.home_switch_user),
        ) {
            options.forEach { option -> UserSwitchTvRow(option) }
        }
    }
}

// The avatar palette helpers live in HomeUserSwitchMenu.kt (avatarColorPair /
// avatarColorsFor) — the single home of that logic for both surfaces.
