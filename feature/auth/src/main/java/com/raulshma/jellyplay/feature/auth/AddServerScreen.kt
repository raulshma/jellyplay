package com.raulshma.jellyplay.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.DiscoveredServer
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import com.raulshma.jellyplay.core.ui.components.TooltipIconButton
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServerScreen(
    onServerAdded: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: AddServerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var contentVisible by remember { mutableStateOf(false) }

    // Auto-start discovery when screen appears
    LaunchedEffect(Unit) {
        contentVisible = true
        viewModel.startDiscovery()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text("Add Server") },
                navigationIcon = {
                    TooltipIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tooltipText = "Back",
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val adaptiveInfo = LocalAdaptiveInfo.current
        val isTv = LocalTvMode.current
        val contentPad = adaptiveInfo.contentPadding(isTv)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = contentPad),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // Header
            item {
                Spacer(modifier = Modifier.height(24.dp))
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(AnimationTokens.StandardDuration, easing = AlphaEasing)) + slideInVertically(
                        initialOffsetY = { it / 20 },
                        animationSpec = tween(AnimationTokens.StandardDuration, easing = FancyTransitionEasing),
                    ),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Default.Dns,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            "Connect to Jellyfin",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Find a server on your network or enter an address manually",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Discovery Section
            item {
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(AnimationTokens.StandardDuration, delayMillis = 100, easing = AlphaEasing)) + slideInVertically(
                        initialOffsetY = { it / 20 },
                        animationSpec = tween(AnimationTokens.StandardDuration, delayMillis = 100, easing = FancyTransitionEasing),
                    ),
                ) {
                    DiscoverySection(
                        uiState = uiState,
                        onRetry = { viewModel.startDiscovery() },
                        onStop = { viewModel.stopDiscovery() },
                        onServerSelected = { server ->
                            viewModel.connectToServer(server.address) { result ->
                                result.onSuccess { onServerAdded(server.address) }
                            }
                        },
                    )
                }
            }

            // Divider between discovery and manual entry
            item {
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(AnimationTokens.StandardDuration, delayMillis = 150, easing = AlphaEasing)),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f))
                            Text(
                                "or enter manually",
                                modifier = Modifier.padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // Manual Entry Section
            item {
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(AnimationTokens.StandardDuration, delayMillis = 200, easing = AlphaEasing)) + slideInVertically(
                        initialOffsetY = { it / 20 },
                        animationSpec = tween(AnimationTokens.StandardDuration, delayMillis = 200, easing = FancyTransitionEasing),
                    ),
                ) {
                    ManualEntrySection(
                        uiState = uiState,
                        onAddressChange = { viewModel.updateManualAddress(it) },
                        onConnect = {
                            viewModel.connectToServer(uiState.manualAddress) { result ->
                                result.onSuccess { onServerAdded(uiState.manualAddress.trim()) }
                            }
                        },
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun DiscoverySection(
    uiState: AddServerUiState,
    onRetry: () -> Unit,
    onStop: () -> Unit,
    onServerSelected: (DiscoveredServer) -> Unit,
) {
    val hasServers = uiState.discoveredServers.isNotEmpty()
    val serverCount = uiState.discoveredServers.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring()),
    ) {
        when {
            uiState.discoveryFailed -> {
                DiscoveryFailedRow(onRetry = onRetry)
            }
            uiState.isDiscovering && !hasServers -> {
                ScanningRow(onStop = onStop)
            }
            hasServers -> {
                ServerCountRow(
                    serverCount = serverCount,
                    isScanning = uiState.isDiscovering,
                    servers = uiState.discoveredServers,
                    isConnecting = uiState.isConnecting,
                    onServerSelected = onServerSelected,
                )
            }
            else -> {
                NoServersRow(onRetry = onRetry)
            }
        }
    }
}

@Composable
private fun ScanningRow(onStop: () -> Unit) {
    Row(
        verticalAlignment = CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Scanning for servers\u2026",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onStop, contentPadding = androidx.compose.foundation.layout.PaddingValues()) {
            Text("Stop")
        }
    }
}

@Composable
private fun ServerCountRow(
    serverCount: Int,
    isScanning: Boolean,
    servers: List<DiscoveredServer>,
    isConnecting: Boolean,
    onServerSelected: (DiscoveredServer) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(300, easing = FancyTransitionEasing),
        label = "chevron",
    )

    Column {
        Row(
            verticalAlignment = CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(ShapeCache.smooth16)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { expanded = !expanded }
                .tvFocusable()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.width(12.dp))
            } else {
                Icon(
                    Icons.Default.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = if (isScanning) {
                    "$serverCount server${if (serverCount != 1) "s" else ""} found\u2026"
                } else {
                    "$serverCount server${if (serverCount != 1) "s" else ""} found"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronRotation),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = spring(),
                expandFrom = Alignment.Top,
            ) + fadeIn(tween(200, easing = AlphaEasing)),
            exit = shrinkVertically(animationSpec = spring()) + fadeOut(tween(150, easing = AlphaEasing)),
        ) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                servers.forEachIndexed { index, server ->
                    DiscoveredServerItem(
                        server = server,
                        onClick = { onServerSelected(server) },
                        isConnecting = isConnecting,
                        shape = expressiveListShape(
                            index = index,
                            count = servers.size,
                            outerRadius = 16.dp,
                            innerRadius = 10.dp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoveryFailedRow(onRetry: () -> Unit) {
    Row(
        verticalAlignment = CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            Icons.Default.WifiOff,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Discovery unavailable",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry, contentPadding = androidx.compose.foundation.layout.PaddingValues()) {
            Text("Retry")
        }
    }
}

@Composable
private fun NoServersRow(onRetry: () -> Unit) {
    Row(
        verticalAlignment = CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = "No servers found on your network",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry, contentPadding = androidx.compose.foundation.layout.PaddingValues()) {
            Text("Retry")
        }
    }
}

@Composable
private fun DiscoveredServerItem(
    server: DiscoveredServer,
    onClick: () -> Unit,
    isConnecting: Boolean,
    shape: androidx.compose.ui.graphics.Shape = ShapeCache.smooth12,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .tvFocusable()
            .clickable(onClick = onClick, enabled = !isConnecting)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(ShapeCache.smooth12)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Dns,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = server.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = "Connect",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ManualEntrySection(
    uiState: AddServerUiState,
    onAddressChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    Column {
        OutlinedTextField(
            value = uiState.manualAddress,
            onValueChange = onAddressChange,
            label = { Text("Server Address") },
            placeholder = { Text("https://jellyfin.example.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = uiState.connectError != null,
            supportingText = uiState.connectError?.let { { Text(it) } },
            enabled = !uiState.isConnecting,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onConnect,
            enabled = !uiState.isConnecting && uiState.manualAddress.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState.isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (uiState.isConnecting) "Connecting\u2026" else "Connect")
        }
    }
}
