package com.raulshma.jellyplay.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Stable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.Text as TvText
import androidx.tv.material3.rememberDrawerState
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.focusIndicator


// ──────────────────────────────────────────────────────────────────────────────
// Constants
// ──────────────────────────────────────────────────────────────────────────────

private val CollapsedDrawerWidth = 72.dp
private val ExpandedDrawerWidth = 240.dp
private val DrawerIconSize = 24.dp
private val DrawerItemSpacing = 4.dp
private const val ExitConfirmationTimeoutMs = 2000L

/**
 * Frame budget for the content-focus guard. The content requester often isn't attached to a
 * placed focusable until the incoming screen's NavDisplay entry transition (~300ms) settles,
 * so a short retry loop gives up while focus is still orphaned — and orphaned focus falls to
 * the drawer rail, which snaps the drawer open.
 */
private const val ContentGuardRetryFrames = 24

// ──────────────────────────────────────────────────────────────────────────────
// Data + icon mapping
// ──────────────────────────────────────────────────────────────────────────────

// @Stable (not @Immutable): route is a NavKey marker interface whose impls
// are not guaranteed immutable — matches the NavigationState precedent for
// NavKey-holding types.
@Stable
data class TvNavItem(
    val route: NavKey,
    val label: String,
    val icon: ImageVector,
)

/** Library collection types excluded from the TV drawer — already accessible via the Library tab. */
private val EXCLUDED_DRAWER_TYPES = setOf(
    "movies", "tvshows", "music", "boxsets", "playlists", "homevideos", "anime",
)

private fun libraryIcon(collectionType: String?): ImageVector = when (collectionType?.lowercase()) {
    "movies" -> Tabler.Outline.Movie
    "tvshows" -> Tabler.Outline.DeviceTv
    "music" -> Tabler.Outline.Music
    "boxsets" -> Tabler.Outline.Stack2
    "playlists" -> Tabler.Outline.List
    "livetv" -> Tabler.Outline.DeviceTv
    "photos" -> Tabler.Outline.Photo
    "homevideos" -> Tabler.Outline.Video
    "folders", "books" -> Tabler.Outline.Folder
    else -> Tabler.Outline.Folder
}

// ──────────────────────────────────────────────────────────────────────────────
// Main drawer composable
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TvNavigationDrawer(
    primaryItems: List<TvNavItem>,
    libraryFolders: List<LibraryFolder>,
    currentTopLevel: NavKey,
    isSubPage: Boolean,
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed),
    drawerListState: LazyListState = rememberLazyListState(),
    currentRoute: NavKey? = null,
    nowPlayingTitle: String? = null,
    nowPlayingEnabled: Boolean = false,
    onNowPlayingClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // Filter out standard library types — those are already reachable via the Library tab.
    val drawerFolders = remember(libraryFolders) {
        libraryFolders.filter { it.collectionType?.lowercase() !in EXCLUDED_DRAWER_TYPES }
    }

    val selectedItemFocusRequester = remember { FocusRequester() }
    val drawerSheetFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }

    var contentHasFocus by remember { mutableStateOf(false) }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    // ── Item layout: [primaryItems...] [drawerFolders...] [Settings] ───────────
    val primaryCount = primaryItems.size
    val libraryStartIndex = primaryCount
    val settingsIndex = libraryStartIndex + drawerFolders.size
    val totalItemCount = settingsIndex + 1

    // ── Selected-index computation ────────────────────────────────────────────
    val selectedIndex: Int = remember(currentRoute, currentTopLevel, primaryItems, drawerFolders) {
        val route = currentRoute ?: currentTopLevel
        // Check primary items first
        val primaryIdx = primaryItems.indexOfFirst { it.route == route || it.route == currentTopLevel }
        if (primaryIdx >= 0) return@remember primaryIdx

        when (route) {
            is Route.LibraryBrowse -> {
                val idx = drawerFolders.indexOfFirst { it.id == route.folderId }
                if (idx >= 0) libraryStartIndex + idx else -1
            }
            is Route.Settings -> settingsIndex
            else -> {
                // Fall back to matching currentTopLevel against primary items
                val topLevelIdx = primaryItems.indexOfFirst { it.route == currentTopLevel }
                if (topLevelIdx >= 0) topLevelIdx else 0
            }
        }
    }

    fun requestSelectedRailFocus() {
        selectedItemFocusRequester.tryRequestFocus("tv_drawer_selected")
    }

    fun closeDrawerAndMoveToContent() {
        drawerState.setValue(DrawerValue.Closed)
        if (!focusManager.moveFocus(FocusDirection.Right)) {
            focusManager.clearFocus(force = true)
        }
    }

    fun navigateFromDrawer(route: NavKey) {
        closeDrawerAndMoveToContent()
        onNavigate(route)
    }

    BackHandler(enabled = drawerState.currentValue == DrawerValue.Open) {
        lastBackPressTime = 0L
        closeDrawerAndMoveToContent()
    }

    BackHandler(enabled = drawerState.currentValue == DrawerValue.Closed) {
        if (isSubPage) {
            onBack()
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPressTime < ExitConfirmationTimeoutMs) {
                lastBackPressTime = 0L
                (context as? android.app.Activity)?.moveTaskToBack(true)
            } else {
                lastBackPressTime = now
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.press_back_again_to_exit),
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    LaunchedEffect(drawerState.currentValue, selectedIndex, primaryItems.size, drawerFolders.size) {
        if (drawerState.currentValue == DrawerValue.Open && totalItemCount > 0) {
            drawerListState.scrollToItem(selectedIndex.coerceIn(0, totalItemCount - 1))
            requestSelectedRailFocus()
        }
    }

    LaunchedEffect(currentRoute, drawerState.currentValue, contentHasFocus) {
        if (drawerState.currentValue == DrawerValue.Closed && !contentHasFocus) {
            repeat(ContentGuardRetryFrames) {
                androidx.compose.runtime.withFrameNanos { }
                if (contentHasFocus || drawerState.currentValue != DrawerValue.Closed) return@LaunchedEffect
                if (contentFocusRequester.tryRequestFocus("tv_content_guard")) return@LaunchedEffect
            }
        }
    }

    val isClosed = drawerState.currentValue == DrawerValue.Closed
    val animatedDrawerWidth by animateDpAsState(
        targetValue = if (isClosed) CollapsedDrawerWidth else ExpandedDrawerWidth,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "drawerWidth",
    )

    NavigationDrawer(
        drawerState = drawerState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        drawerContent = { drawerValue ->
            val showLabels = drawerValue == DrawerValue.Open

            LazyColumn(
                state = drawerListState,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(animatedDrawerWidth)
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = 16.dp,
                        bottom = 16.dp,
                    )
                    .focusRequester(drawerSheetFocusRequester)
                    .focusGroup()
                    .tvFocusRestorer(selectedItemFocusRequester)
                    .focusProperties {
                        onEnter = { requestSelectedRailFocus() }
                    }
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(DrawerItemSpacing),
            ) {
                // ── Primary nav items (Home, Library, Search, Live TV, Shortcuts…) ─
                items(primaryCount, key = { idx -> "primary_$idx" }) { index ->
                    val item = primaryItems[index]
                    TvDrawerRow(
                        label = item.label,
                        icon = item.icon,
                        showLabel = showLabels,
                        selected = selectedIndex == index,
                        onClick = { navigateFromDrawer(item.route) },
                        modifier = Modifier.ifElse(
                            selectedIndex == index,
                            Modifier.focusRequester(selectedItemFocusRequester),
                        ),
                    )
                }

                // ── Now Playing (dynamic) ────────────────────────────────────────
                if (nowPlayingEnabled) {
                    item(key = "now_playing") {
                        TvDrawerRow(
                            label = stringResource(R.string.nav_now_playing),
                            subtext = nowPlayingTitle,
                            icon = Tabler.Outline.PlayerPlay,
                            showLabel = showLabels,
                            selected = false,
                            onClick = {
                                closeDrawerAndMoveToContent()
                                onNowPlayingClick()
                            },
                        )
                    }
                }

                // ── Server library folders (filtered — standard types excluded) ────
                items(drawerFolders.size, key = { idx ->
                    "lib_${drawerFolders[idx].id}"
                }) { index ->
                    val folder = drawerFolders[index]
                    val itemIndex = libraryStartIndex + index
                    TvDrawerRow(
                        label = folder.name,
                        icon = libraryIcon(folder.collectionType),
                        showLabel = showLabels,
                        selected = selectedIndex == itemIndex,
                        onClick = {
                            navigateFromDrawer(
                                Route.LibraryBrowse(
                                    folder.id,
                                    folder.name,
                                    folder.collectionType,
                                ),
                            )
                        },
                        modifier = Modifier.ifElse(
                            selectedIndex == itemIndex,
                            Modifier.focusRequester(selectedItemFocusRequester),
                        ),
                    )
                }

                // ── Settings ─────────────────────────────────────────────────────
                item(key = "settings") {
                    TvDrawerRow(
                        label = stringResource(R.string.nav_settings),
                        icon = Tabler.Outline.Settings,
                        showLabel = showLabels,
                        selected = selectedIndex == settingsIndex,
                        onClick = { navigateFromDrawer(Route.Settings) },
                        modifier = Modifier.ifElse(
                            selectedIndex == settingsIndex,
                            Modifier.focusRequester(selectedItemFocusRequester),
                        ),
                    )
                }
            }
        },
        content = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(contentFocusRequester)
                    .onFocusChanged { contentHasFocus = it.hasFocus }
                    .focusGroup()
                    .tvFocusRestorer(),
            ) {
                content()
            }
        },
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Drawer row — TV-focused clickable item with scale, focus highlight, and
// selection background
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TvDrawerRow(
    label: String,
    icon: ImageVector,
    showLabel: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtext: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val iconScale by animateFloatAsState(
        targetValue = when {
            isFocused -> 1.25f
            selected -> 1.1f
            else -> 1.0f
        },
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "iconScale",
    )

    val rowScale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "rowScale",
    )

    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onSecondaryContainer
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val backgroundColor = when {
        selected -> MaterialTheme.colorScheme.secondaryContainer
        isFocused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        else -> Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(rowScale)
            .clip(ShapeCache.smooth28)
            .background(backgroundColor)
            .focusIndicator(ShapeCache.smooth28)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier
                .size(DrawerIconSize)
                .scale(iconScale),
        )
        if (showLabel) {
            if (subtext != null) {
                Column {
                    TvText(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor,
                        maxLines = 1,
                    )
                    TvText(
                        text = subtext,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            } else {
                TvText(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    maxLines = 1,
                )
            }
        }
    }
}
