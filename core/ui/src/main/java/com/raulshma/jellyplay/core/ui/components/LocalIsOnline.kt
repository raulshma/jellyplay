package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.ServerHealth
import kotlinx.coroutines.flow.StateFlow

/**
 * CompositionLocal that provides a [StateFlow] of [NetworkStatus]
 * representing the device's current connectivity state.
 *
 * Provided at the app level in [JellyPlayApp].
 */
val LocalNetworkStatus: ProvidableCompositionLocal<StateFlow<NetworkStatus>> =
    staticCompositionLocalOf { error("LocalNetworkStatus not provided") }

/**
 * CompositionLocal that provides a [StateFlow] of [ServerHealth]
 * representing the health status of the connected Jellyfin server.
 *
 * Provided at the app level in [JellyPlayApp].
 */
val LocalServerHealth: ProvidableCompositionLocal<StateFlow<ServerHealth>> =
    staticCompositionLocalOf { error("LocalServerHealth not provided") }
