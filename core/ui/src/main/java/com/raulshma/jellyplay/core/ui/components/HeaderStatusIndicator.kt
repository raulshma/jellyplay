package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

/**
 * Represents the status to display in the header indicator area.
 */
sealed class HeaderStatus {
    /** No status indicator to show. */
    data object None : HeaderStatus()

    /** Content is currently loading (refresh). */
    data object Loading : HeaderStatus()

    /** An error occurred. */
    data object Error : HeaderStatus()

    /** Device is on the local LAN but has no internet (server may be reachable). */
    data object Local : HeaderStatus()

    /** Device is completely offline — no network at all. */
    data object Offline : HeaderStatus()
}

/**
 * A compact, animated status indicator for use in app headers / TopAppBars.
 *
 * Displays a small spinner for loading, an error icon for errors, a cloud-off icon
 * when offline, or a wifi-off icon when on a local network without internet.
 *
 * Uses [AnimatedContent] for smooth transitions between states.
 *
 * Priority order: **Offline** > **Local** > **Error** > **Loading** > **None**
 *
 * @param status The current [HeaderStatus] to display.
 * @param modifier Modifier applied to the indicator container.
 * @param tint Optional color override for the icon/indicator. Defaults to
 *   [MaterialTheme.colorScheme.onSurfaceVariant].
 */
@Composable
fun HeaderStatusIndicator(
    status: HeaderStatus,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = status,
        transitionSpec = {
            (fadeIn(animationSpec = fadeSpec) togetherWith fadeOut(animationSpec = fadeSpec))
        },
        label = "headerStatus",
        modifier = modifier.semantics {
            contentDescription = when (status) {
                is HeaderStatus.Loading -> "Loading"
                is HeaderStatus.Error -> "Error"
                is HeaderStatus.Local -> "Local network, no internet"
                is HeaderStatus.Offline -> "Offline"
                is HeaderStatus.None -> ""
            }
        },
    ) { currentStatus ->
        when (currentStatus) {
            is HeaderStatus.None -> {
                Box(modifier = Modifier.size(20.dp))
            }
            is HeaderStatus.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = tint,
                )
            }
            is HeaderStatus.Error -> {
                Icon(
                    imageVector = Tabler.Outline.AlertCircle,
                    contentDescription = "Error",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            is HeaderStatus.Local -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(20.dp),
                ) {
                    Icon(
                        imageVector = Tabler.Outline.WifiOff,
                        contentDescription = "Local network, no internet",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            is HeaderStatus.Offline -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(20.dp),
                ) {
                    Icon(
                        imageVector = Tabler.Outline.CloudOff,
                        contentDescription = "Offline",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/**
 * Resolves the effective [HeaderStatus] from individual state booleans
 * and the current [NetworkStatus].
 *
 * Priority: **offline** > **local** > **error** > **loading** > **none**
 *
 * @param isLoading Whether content is currently loading/refreshing.
 * @param hasError Whether an error has occurred.
 * @param networkStatus The current network connectivity state.
 */
fun resolveHeaderStatus(
    isLoading: Boolean,
    hasError: Boolean,
    networkStatus: NetworkStatus,
): HeaderStatus = when {
    networkStatus == NetworkStatus.Offline -> HeaderStatus.Offline
    networkStatus == NetworkStatus.Local -> HeaderStatus.Local
    hasError -> HeaderStatus.Error
    isLoading -> HeaderStatus.Loading
    else -> HeaderStatus.None
}
