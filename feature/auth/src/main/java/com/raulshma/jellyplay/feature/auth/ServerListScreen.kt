package com.raulshma.jellyplay.feature.auth

import androidx.compose.animation.AnimatedVisibility
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServerListScreen(
    onAddServer: () -> Unit,
    onServerSelected: (ServerInfo) -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    val isSynthwave = LocalIsSynthwave.current
    val backgroundColor = rememberScreenBackgroundColor()

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("JellyPlay") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isSynthwave) Color.Transparent else MaterialTheme.colorScheme.surface,
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddServer,
                icon = { Icon(Tabler.Outline.Plus, contentDescription = null) },
                text = { Text("Add Server") },
            )
        },
    ) { padding ->
        val adaptiveInfo = LocalAdaptiveInfo.current
        val isTv = LocalTvMode.current
        val contentPad = adaptiveInfo.contentPadding(isTv)
        val spacing = adaptiveInfo.itemSpacing(isTv)

        if (isLoading) {
            ScreenLoadingState(
                message = "Loading...",
                modifier = Modifier.padding(padding),
            )
        } else if (servers.isEmpty()) {
            ScreenEmptyState(
                icon = Tabler.Outline.Server,
                title = "No servers added",
                description = "Add your Jellyfin server to get started",
                actionLabel = "Add Server",
                onAction = onAddServer,
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(contentPad),
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
                            onClick = { onServerSelected(server) },
                            onDelete = { viewModel.removeServer(server.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerItem(
    server: ServerInfo,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusable().clickable(onClick = onClick),
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
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Tabler.Outline.Trash,
                    contentDescription = "Remove server",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
