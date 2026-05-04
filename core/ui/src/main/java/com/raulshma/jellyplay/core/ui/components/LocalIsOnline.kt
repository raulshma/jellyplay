package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.raulshma.jellyplay.core.model.NetworkStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * CompositionLocal that provides a [StateFlow] of [NetworkStatus]
 * representing the device's current connectivity state.
 *
 * Provided at the app level in [JellyPlayApp].
 */
val LocalNetworkStatus: ProvidableCompositionLocal<StateFlow<NetworkStatus>> =
    staticCompositionLocalOf { error("LocalNetworkStatus not provided") }
