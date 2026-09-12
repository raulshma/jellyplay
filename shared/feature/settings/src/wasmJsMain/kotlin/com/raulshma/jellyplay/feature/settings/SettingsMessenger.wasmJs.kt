package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable

/**
 * Web actual of the [SettingsMessenger] seam: null, like the desktop actual —
 * the browser shell has no UserMessageBus host yet, so one-shot row feedback
 * drops (messages are already resolved [String]s at the call site).
 */
@Composable
internal actual fun rememberSettingsMessenger(): SettingsMessenger? = null
