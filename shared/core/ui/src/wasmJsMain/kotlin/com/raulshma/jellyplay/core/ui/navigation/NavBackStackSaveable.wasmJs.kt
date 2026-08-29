package com.raulshma.jellyplay.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration

/**
 * wasmJs actual of [rememberNavBackStackSaveable]: mirrors the JVM path (the
 * commonMain configuration-taking overload). Theoretical only today — the web
 * shell (WebAppRoot) deliberately avoids [rememberNavigationState] and keeps a
 * memory-only back stack (no rememberNavBackStack/SavedState on wasm) — but
 * the expect demands an actual, and should the web shell ever adopt saveable
 * navigation, the explicit-configuration contract is the correct one here:
 * wasm has no reflection fallback.
 */
@Composable
internal actual fun rememberNavBackStackSaveable(
    savedStateConfiguration: SavedStateConfiguration?,
    initialKey: NavKey,
): NavBackStack<NavKey> =
    rememberNavBackStack(savedStateConfiguration ?: SavedStateConfiguration.DEFAULT, initialKey)
