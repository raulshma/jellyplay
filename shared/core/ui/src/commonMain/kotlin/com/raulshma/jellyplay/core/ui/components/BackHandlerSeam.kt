package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable

/**
 * Back-navigation interception seam. Android wires the system back
 * button/gesture through `androidx.activity`; desktop is a no-op for now — the
 * shell handles Escape at the window level.
 */
@Composable
expect fun JellyPlayBackHandler(enabled: Boolean, onBack: () -> Unit)
