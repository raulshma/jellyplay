package com.raulshma.jellyplay.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.DiscoveredServer
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.tv.isTvDevice
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Server") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val adaptiveInfo = LocalAdaptiveInfo.current
        val isTv = isTvDevice()
        val contentPad = adaptiveInfo.contentPadding(isTv)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                verticalAlignment = CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    if (uiState.isDiscovering) Icons.Default.Wifi else Icons.Default.Cast,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (uiState.isDiscovering) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        uiState.isDiscovering -> "Scanning for servers\u2026"
                        uiState.discoveredServers.isNotEmpty() -> "Found ${uiState.discoveredServers.size} server${if (uiState.discoveredServers.size != 1) "s" else ""}"
                        uiState.discoveryFailed -> "Discovery unavailable"
                        else -> "No servers found"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.weight(1f))
                when {
                    uiState.isDiscovering -> {
                        TextButton(onClick = onStop) {
                            Text("Stop")
                        }
                    }
                    uiState.discoveryFailed || uiState.discoveredServers.isEmpty() -> {
                        TextButton(onClick = onRetry) {
                            Text("Retry")
                        }
                    }
                }
            }

            // Progress indicator while scanning
            if (uiState.isDiscovering) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Discovery failure message
            if (uiState.discoveryFailed) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = CenterVertically) {
                    Icon(
                        Icons.Default.WifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Could not scan for servers. Enter the address manually below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Discovered servers list
            if (uiState.discoveredServers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.discoveredServers.forEach { server ->
                        DiscoveredServerItem(
                            server = server,
                            onClick = { onServerSelected(server) },
                            isConnecting = uiState.isConnecting,
                        )
                    }
                }
            } else if (!uiState.isDiscovering && !uiState.discoveryFailed) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Make sure your server is on the same network and try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DiscoveredServerItem(
    server: DiscoveredServer,
    onClick: () -> Unit,
    isConnecting: Boolean,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusable()
            .clickable(onClick = onClick, enabled = !isConnecting),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = CenterVertically,
        ) {
            Icon(
                Icons.Default.Dns,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
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
