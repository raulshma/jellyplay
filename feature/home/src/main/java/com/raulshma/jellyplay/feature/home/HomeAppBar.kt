package com.raulshma.jellyplay.feature.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.ui.components.HeaderStatus
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.ModeSwitch
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowLeft
import com.composables.icons.tabler.outline.DotsVertical
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.Settings
import com.composables.icons.tabler.outline.Users
import com.composables.icons.tabler.outline.Wand
import com.composables.icons.tabler.outline.Wifi
import com.composables.icons.tabler.outline.WifiOff
import com.composables.icons.tabler.outline.X

@Composable
fun HomeTopDock(
    listState: LazyListState,
    transitionRangePx: Float,
    baseIconColor: Color,
    isSearchFocused: Boolean,
    searchQuery: String,
    offlineMode: OfflineMode,
    homeMode: HomeMode,
    headerStatus: HeaderStatus,
    activeDownloadCount: Int,
    onModeChange: (HomeMode) -> Unit,
    onSearchExpanded: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onToggleOffline: () -> Unit,
    searchResultsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollFraction by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset.toFloat() / transitionRangePx).coerceIn(0f, 1f)
        }
    }
    val appBarIconColor = lerp(baseIconColor, MaterialTheme.colorScheme.onSurface, scrollFraction)
    val appBarIconColorFaded = appBarIconColor.copy(alpha = 0.9f)
    val focusManager = LocalFocusManager.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                horizontal = 16.dp,
                vertical = 8.dp
            )
            .onPreviewKeyEvent { keyEvent ->
                if (isSearchFocused && keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyDown) {
                    onClearSearch()
                    focusManager.clearFocus()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier
                .then(if (isSearchFocused) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
                .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
                .graphicsLayer {
                    shadowElevation = if (isSearchFocused) 4f else 0f
                    shape = RoundedCornerShape(24.dp)
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
                    .padding(horizontal = 8.dp),
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
                        onToggleOffline = onToggleOffline,
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
                contentDescription = "Back",
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
                "Search movies, shows, music...",
                color = appBarIconColor.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        colors = searchTextFieldColors,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = appBarIconColor,
        ),
        singleLine = true,
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

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
                    contentDescription = "Clear search",
                    tint = appBarIconColorFaded,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun CollapsedDockContent(
    offlineMode: OfflineMode,
    homeMode: HomeMode,
    headerStatus: HeaderStatus,
    appBarIconColorFaded: Color,
    onToggleOffline: () -> Unit,
    onModeChange: (HomeMode) -> Unit,
    onSearchExpand: () -> Unit,
) {
    if (offlineMode != OfflineMode.ONLINE) {
        val onlineFocusState = rememberTvFocusState()
        Box(
            modifier = Modifier
                .then(onlineFocusState.focusModifier)
                .tvFocusIndicator(onlineFocusState, CircleShape)
        ) {
            IconButton(
                onClick = onToggleOffline,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Tabler.Outline.Download,
                    contentDescription = "Go online",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
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
                contentDescription = "Search",
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
                        contentDescription = if (isExpanded) "Close menu" else "More options",
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
            text = { Text("Surprise Me") },
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
            text = { Text("SyncPlay") },
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
                    Text("Downloads")
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
                onToggleOffline()
            },
            text = {
                Text(if (offlineMode != OfflineMode.ONLINE) "Go Online" else "Go Offline")
            },
            icon = {
                Icon(
                    if (offlineMode != OfflineMode.ONLINE) Tabler.Outline.Wifi else Tabler.Outline.WifiOff,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
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
                onSettingsClick()
            },
            text = { Text("Settings") },
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
