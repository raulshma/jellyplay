package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable

/**
 * The desktop seam truth [rememberLiveUpdatesGate] exposes: no Android
 * notification surfaces, ever (the [desktopBiometricGate] pattern).
 */
internal val desktopLiveUpdatesGate: LiveUpdatesGate? = null

@Composable
internal actual fun rememberLiveUpdatesGate(): LiveUpdatesGate? = desktopLiveUpdatesGate
