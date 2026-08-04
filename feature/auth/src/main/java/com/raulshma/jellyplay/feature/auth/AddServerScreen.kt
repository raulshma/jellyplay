package com.raulshma.jellyplay.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.raulshma.jellyplay.feature.auth.R
import com.raulshma.jellyplay.core.model.DiscoveredServer
import com.raulshma.jellyplay.core.network.LocalNetworkAccess
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddServerScreen(
    onServerAdded: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: AddServerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var contentVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Android 17+: local network access is required for SSDP discovery. Track
    // the grant state so we can (a) gate auto-discovery on it and (b) show a
    // rationale banner + re-request affordance when denied. On non-enforcing
    // platforms this short-circuits to granted and the banner never appears.
    var localNetworkGranted by remember {
        mutableStateOf(LocalNetworkAccess.isGranted(context))
    }
    val localNetworkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Just record the result; discovery is kicked off by the
        // LaunchedEffect below keyed on localNetworkGranted, which is the
        // single trigger for both initial and post-grant scans.
        localNetworkGranted = granted
    }

    // Auto-start discovery when screen appears, but only if local network
    // access is already available; otherwise the scan silently finds nothing.
    // The rationale banner's "Allow access" button starts discovery after grant.
    LaunchedEffect(localNetworkGranted) {
        contentVisible = true
        if (localNetworkGranted) viewModel.startDiscovery()
    }

    JellyPlayScreenScaffold(
        title = stringResource(R.string.auth_add_server_title),
        onBack = onBack,
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
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + slideInVertically(
                        initialOffsetY = { it / 20 },
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    ),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Tabler.Outline.Server,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            stringResource(R.string.auth_connect_to_jellyfin),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.auth_add_server_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Local network permission rationale (Android 17+). Shown when
            // discovery can't work yet; grants kick off discovery via the launcher.
            item {
                AnimatedVisibility(
                    visible = contentVisible && LocalNetworkAccess.enforced && !localNetworkGranted,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + slideInVertically(
                        initialOffsetY = { it / 20 },
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    ),
                ) {
                    LocalNetworkRationaleBanner(
                        onAllow = {
                            localNetworkLauncher.launch(LocalNetworkAccess.PERMISSION)
                        },
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Discovery Section
            item {
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + slideInVertically(
                        initialOffsetY = { it / 20 },
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
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
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
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
                                stringResource(R.string.auth_or_enter_manually),
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
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + slideInVertically(
                        initialOffsetY = { it / 20 },
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
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
            .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
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
        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
        LoadingIndicator(
            modifier = Modifier.size(16.dp),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.auth_scanning_for_servers),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        val stopFocusState = rememberTvFocusState()
        TextButton(
            onClick = onStop,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(),
            modifier = Modifier.then(stopFocusState.focusModifier).tvFocusIndicator(stopFocusState, ShapeCache.smooth12),
        ) {
            Text(stringResource(R.string.auth_stop))
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
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "chevron",
    )

    Column {
        Row(
            verticalAlignment = CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(ShapeCache.smooth16)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .focusIndicator(ShapeCache.smooth16)
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (isScanning) {
                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                LoadingIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.width(12.dp))
            } else {
                Icon(
                    Tabler.Outline.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            val serversFoundTemplate = if (isScanning) {
                stringResource(if (serverCount == 1) R.string.auth_server_one_found_scanning else R.string.auth_servers_found_scanning)
            } else {
                stringResource(if (serverCount == 1) R.string.auth_server_one_found else R.string.auth_servers_found)
            }
            Text(
                text = serversFoundTemplate.format(serverCount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Tabler.Outline.ChevronDown,
                contentDescription = stringResource(if (expanded) R.string.auth_collapse else R.string.auth_expand),
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronRotation),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                expandFrom = Alignment.Top,
            ) + fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
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
            Tabler.Outline.WifiOff,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.auth_discovery_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        val retryFocusState = rememberTvFocusState()
        TextButton(
            onClick = onRetry,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(),
            modifier = Modifier.then(retryFocusState.focusModifier).tvFocusIndicator(retryFocusState, ShapeCache.smooth12),
        ) {
            Text(stringResource(R.string.auth_retry))
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
            text = stringResource(R.string.auth_no_servers_found),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        val noServersRetryFocusState = rememberTvFocusState()
        TextButton(
            onClick = onRetry,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(),
            modifier = Modifier.then(noServersRetryFocusState.focusModifier).tvFocusIndicator(noServersRetryFocusState, ShapeCache.smooth12),
        ) {
            Text(stringResource(R.string.auth_retry))
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
            .focusIndicator(shape)
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
                Tabler.Outline.Server,
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
            Tabler.Outline.CircleCheck,
            contentDescription = stringResource(R.string.auth_connect),
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
    val isTv = LocalTvMode.current
    val addressFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (isTv) addressFocusRequester.tryRequestFocus("manual_address")
    }

    Column {
        OutlinedTextField(
            value = uiState.manualAddress,
            onValueChange = onAddressChange,
            label = { Text(stringResource(R.string.auth_server_address)) },
            placeholder = { Text(stringResource(R.string.auth_server_address_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(addressFocusRequester),
            isError = uiState.connectError != null,
            supportingText = uiState.connectError?.let { { Text(it) } },
            enabled = !uiState.isConnecting,
        )

        Spacer(modifier = Modifier.height(16.dp))

        val connectFocusState = rememberTvFocusState(focusedScale = 1.04f)
        Button(
            onClick = onConnect,
            enabled = !uiState.isConnecting && uiState.manualAddress.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
                .then(connectFocusState.focusModifier)
                .tvFocusIndicator(connectFocusState, ShapeCache.smooth12),
        ) {
            if (uiState.isConnecting) {
                JellyPlayLoadingIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (uiState.isConnecting) stringResource(R.string.auth_connecting) else stringResource(R.string.auth_connect))
        }
    }
}

/**
 * Rationale banner shown on Android 17+ when local network access has not been
 * granted. Discovery and LAN connections can't work without it, so rather than
 * spinning a scan that will silently return nothing, we explain why and offer a
 * one-tap grant. "Allow access" launches the system permission prompt; the
 * caller starts discovery on a successful grant.
 */
@Composable
private fun LocalNetworkRationaleBanner(onAllow: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            verticalAlignment = CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Icon(
                Tabler.Outline.Wifi,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.auth_local_network_rationale_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.auth_local_network_rationale_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            val allowFocusState = rememberTvFocusState(focusedScale = 1.04f)
            Button(
                onClick = onAllow,
                modifier = Modifier
                    .then(allowFocusState.focusModifier)
                    .tvFocusIndicator(allowFocusState, ShapeCache.smooth12),
            ) {
                Text(stringResource(R.string.auth_local_network_allow))
            }
        }
    }
}
