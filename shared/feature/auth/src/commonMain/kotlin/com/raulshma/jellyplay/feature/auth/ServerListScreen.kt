package com.raulshma.jellyplay.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.raulshma.jellyplay.core.model.ServerHealth
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSynthwave
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSoothingTheme
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.auth.generated.resources.Res
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_add_server
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_app_name
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_cancel
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_loading
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_no_servers_added_desc
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_no_servers_added_title
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_remove
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_remove_server
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_remove_server_message
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_remove_server_title
import com.raulshma.jellyplay.feature.auth.generated.resources.server_health_checking
import com.raulshma.jellyplay.feature.auth.generated.resources.server_health_healthy
import com.raulshma.jellyplay.feature.auth.generated.resources.server_health_unreachable
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.core.model.ServerInfo
import androidx.compose.ui.unit.IntOffset
import com.raulshma.jellyplay.core.designsystem.theme.Dimensions
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.AddListRow
import com.raulshma.jellyplay.core.ui.components.LocalFloatingNavOffset
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServerListScreen(
    onAddServer: () -> Unit,
    onServerSelected: (ServerInfo) -> Unit,
    viewModel: AuthViewModel = koinViewModel(),
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val serverHealth by viewModel.serverHealth.collectAsStateWithLifecycle()

    val isSynthwave = LocalIsSynthwave.current
    val backgroundColor = rememberScreenBackgroundColor()

    val navOffsetPx = LocalFloatingNavOffset.current

    // One-shot reachability ping per saved server on screen entry.
    LaunchedEffect(servers.map { it.address }) {
        viewModel.checkServersHealth(servers)
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.auth_app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isSynthwave) Color.Transparent else MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = if (isSynthwave) Color.Transparent else MaterialTheme.colorScheme.surface,
                )
            )
        },
    ) { padding ->
        val adaptiveInfo = LocalAdaptiveInfo.current
        val isTv = LocalTvMode.current
        val contentPad = adaptiveInfo.contentPadding(isTv)
        val spacing = adaptiveInfo.itemSpacing(isTv)

        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                ScreenLoadingState(message = stringResource(Res.string.auth_loading))
            } else if (servers.isEmpty()) {
                ScreenEmptyState(
                    icon = Tabler.Outline.Server,
                    title = stringResource(Res.string.auth_no_servers_added_title),
                    description = stringResource(Res.string.auth_no_servers_added_desc),
                    actionLabel = stringResource(Res.string.auth_add_server),
                    onAction = onAddServer,
                )
            } else {
                val firstItemFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    if (isTv) firstItemFocusRequester.tryRequestFocus("server_first")
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().tvFocusRestorer(),
                    contentPadding = PaddingValues(
                        start = contentPad,
                        end = contentPad,
                        top = contentPad,
                        bottom = contentPad + Dimensions.floatingNavHeight,
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    itemsIndexed(servers, key = { _, it -> it.id }, contentType = { _, _ -> "server" }) { index, server ->
                        val visible = remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { visible.value = true }
                        AnimatedVisibility(
                            visible = visible.value,
                            enter = fadeIn(
                                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
                            ) + slideInVertically(
                                initialOffsetY = { it / 10 },
                                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                            ),
                        ) {
                            ServerItem(
                                server = server,
                                health = serverHealth[server.address],
                                firstFocusModifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                                onClick = { onServerSelected(server) },
                                onDelete = { viewModel.removeServer(server.id) },
                            )
                        }
                    }

                    // TV has no FAB, so the add action lives in the list.
                    if (isTv) {
                        item(key = "add_server", contentType = "add_server") {
                            AddListRow(
                                label = stringResource(Res.string.auth_add_server),
                                onClick = onAddServer,
                            )
                        }
                    }
                }
            }

            if (!isTv) {
                val fabFocusState = rememberTvFocusState(focusedScale = 1.05f)
                ExtendedFloatingActionButton(
                    onClick = onAddServer,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .then(fabFocusState.focusModifier)
                        .tvFocusIndicator(fabFocusState, ShapeCache.smooth16)
                        .padding(
                            end = 16.dp,
                            bottom = 16.dp + Dimensions.floatingNavHeight,
                        )
                        .offset {
                            val maxOffset = Dimensions.floatingNavHeight.toPx()
                            val yOffset = (-navOffsetPx()).coerceAtMost(maxOffset)
                            IntOffset(x = 0, y = yOffset.toInt())
                        },
                    icon = { Icon(Tabler.Outline.Plus, contentDescription = null) },
                    text = { Text(stringResource(Res.string.auth_add_server)) },
                )
            }
        }
    }
}

@Composable
private fun ServerItem(
    server: ServerInfo,
    health: ServerHealth?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    firstFocusModifier: Modifier = Modifier,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val isSynthwave = LocalIsSynthwave.current
    val isSoothing = LocalIsSoothingTheme.current
    val border = when {
        isSynthwave -> {
            androidx.compose.foundation.BorderStroke(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                )
            )
        }
        isSoothing -> {
            androidx.compose.foundation.BorderStroke(
                width = 0.8.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )
        }
        else -> null
    }

    val shape = when {
        isSynthwave -> RoundedCornerShape(0.dp)
        isSoothing -> ShapeCache.smooth16
        else -> ShapeCache.smooth12
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(firstFocusModifier)
            .focusIndicator(shape)
            .clickable(onClick = onClick),
        shape = shape,
        border = border,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = server.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (health != null) {
                    Spacer(modifier = Modifier.size(6.dp))
                    ServerHealthBadge(health = health)
                }
            }
            val deleteFocusState = rememberTvFocusState()
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.then(deleteFocusState.focusModifier).tvFocusIndicator(deleteFocusState, CircleShape),
            ) {
                Icon(
                    Tabler.Outline.Trash,
                    contentDescription = stringResource(Res.string.auth_remove_server),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showDeleteConfirm) {
        com.raulshma.jellyplay.core.ui.components.ConfirmDialog(
            title = stringResource(Res.string.auth_remove_server_title),
            message = stringResource(Res.string.auth_remove_server_message, server.name),
            confirmText = stringResource(Res.string.auth_remove),
            dismissText = stringResource(Res.string.auth_cancel),
            onConfirm = onDelete,
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun ServerHealthBadge(
    health: ServerHealth,
) {
    val (dotColor, label) = when (health) {
        is ServerHealth.Healthy -> MaterialTheme.colorScheme.tertiary to
            stringResource(Res.string.server_health_healthy)
        is ServerHealth.Unreachable -> MaterialTheme.colorScheme.error to
            stringResource(Res.string.server_health_unreachable)
        is ServerHealth.Checking -> MaterialTheme.colorScheme.outline to
            stringResource(Res.string.server_health_checking)
        is ServerHealth.Unknown -> MaterialTheme.colorScheme.outline to
            stringResource(Res.string.server_health_checking)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
