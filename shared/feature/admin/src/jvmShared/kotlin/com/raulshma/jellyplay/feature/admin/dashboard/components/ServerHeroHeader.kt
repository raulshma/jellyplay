package com.raulshma.jellyplay.feature.admin.dashboard.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Database
import com.composables.icons.tabler.outline.Power
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Server
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.SystemInfo
import com.raulshma.jellyplay.core.ui.components.LocalReducedMotion
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_restart
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_restarting
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_scan_library
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_scan_progress_pct
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_scanning_library
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_shutdown_cd
import com.raulshma.jellyplay.feature.admin.dashboard.LibraryScanState

@Composable
fun ServerHeroHeader(
    systemInfo: SystemInfo,
    isRestarting: Boolean,
    isShuttingDown: Boolean,
    libraryScanState: LibraryScanState,
    onRestart: () -> Unit,
    onShutdown: () -> Unit,
    onScanLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surface

    val restartFocusState = rememberTvFocusState(focusedScale = 1.05f)
    val shutdownFocusState = rememberTvFocusState(focusedScale = 1.04f)
    val scanFocusState = rememberTvFocusState(focusedScale = 1.05f)

    val reducedMotion = LocalReducedMotion.current
    // Server status pulse is a continuous two-channel infinite animation. In
    // performance/reduced-motion mode freeze it at the resting state values.
    val pulseAlpha: Float
    val pulseScale: Float
    if (!reducedMotion) {
        val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
        pulseAlpha = infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = { AlphaEasing.transform(it) }),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseAlpha",
        ).value
        pulseScale = infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = { AlphaEasing.transform(it) }),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseScale",
        ).value
    } else {
        pulseAlpha = 1f
        pulseScale = 1f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth28)
            .background(surface),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                                alpha = pulseAlpha
                            }
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Online",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(ShapeCache.smoothPill)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                ) {
                    Text(
                        "v${systemInfo.version}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Tabler.Outline.Server,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        systemInfo.serverName,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append(systemInfo.operatingSystemDisplayName)
                            append(" \u2022 ")
                            append(systemInfo.productName)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (systemInfo.localAddress.isNotBlank() || systemInfo.wanAddress.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (systemInfo.localAddress.isNotBlank()) {
                        AddressRow("Local", systemInfo.localAddress)
                    }
                    if (systemInfo.wanAddress.isNotBlank()) {
                        AddressRow("WAN", systemInfo.wanAddress)
                    }
                }
            }

            ScanLibraryButton(
                state = libraryScanState,
                enabled = !isRestarting && !isShuttingDown,
                onClick = onScanLibrary,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(scanFocusState.focusModifier)
                    .tvFocusIndicator(scanFocusState, ShapeCache.smooth20),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = onRestart,
                    enabled = !isRestarting && !isShuttingDown && systemInfo.canSelfRestart,
                    modifier = Modifier
                        .weight(1f)
                        .then(restartFocusState.focusModifier)
                        .tvFocusIndicator(restartFocusState, ShapeCache.smooth12),
                ) {
                    // Show loading indicator OR the restart icon — never both simultaneously.
                    if (isRestarting) {
                        com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator(
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Icon(Tabler.Outline.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(if (isRestarting) stringResource(Res.string.admin_restarting) else stringResource(Res.string.admin_restart))
                }
                FilledTonalIconButton(
                    onClick = onShutdown,
                    enabled = !isRestarting && !isShuttingDown,
                    modifier = Modifier
                        .then(shutdownFocusState.focusModifier)
                        .tvFocusIndicator(shutdownFocusState, ShapeCache.smooth12),
                ) {
                    Icon(Tabler.Outline.Power, contentDescription = stringResource(Res.string.admin_shutdown_cd), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * Material 3 Expressive scan-library action. When idle it is a tonal button;
 * while a scan is running it surfaces the live percentage and a determinate
 * progress bar embedded directly beneath the label.
 */
@Composable
private fun ScanLibraryButton(
    state: LibraryScanState,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isScanning = state is LibraryScanState.Running
    // Jellyfin reports this value from the running task. Treat it as untrusted:
    // some server versions can send NaN or a value outside the 0–100 range.
    // Passing either into a progress indicator can cause rendering issues.
    val progress = (state as? LibraryScanState.Running)
        ?.progress
        ?.takeIf(Double::isFinite)
        ?.coerceIn(0.0, 100.0)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled && !isScanning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isScanning) {
                com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator(
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
            } else {
                Icon(Tabler.Outline.Database, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = if (isScanning) {
                    progress?.let { stringResource(Res.string.admin_scan_progress_pct, it.toInt()) } ?: stringResource(Res.string.admin_scanning_library)
                } else {
                    stringResource(Res.string.admin_scan_library)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isScanning) {
            // Match jellyfin-web's TaskProgress: determinate with a percentage
            // when the server reports one, indeterminate otherwise. The server
            // reports currentProgressPercentage = null through most of a library
            // scan (only emits a real value once the progress-reporting phase
            // kicks in), so rendering a determinate bar pinned to % during that
            // window looks frozen — the reported "shows then never updates".
            val progressModifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(ShapeCache.smooth4)
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { (progress / 100.0).toFloat() },
                    modifier = progressModifier,
                )
            } else {
                LinearProgressIndicator(modifier = progressModifier)
            }
        }
    }
}

@Composable
private fun AddressRow(label: String, address: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Tabler.Outline.Server,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        Text(
            address,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
