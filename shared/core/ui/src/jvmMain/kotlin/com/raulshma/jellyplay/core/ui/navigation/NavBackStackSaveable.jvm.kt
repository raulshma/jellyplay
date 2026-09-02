package com.raulshma.jellyplay.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration

/**
 * JVM actual of [rememberNavBackStackSaveable]: the commonMain
 * configuration-taking overload, unchanged from the pre-seam behavior. The
 * desktop shell passes `desktopNavSavedStateConfiguration()` (polymorphic
 * NavKey module enumerating the sealed Route leaves); a null configuration
 * falls back to `SavedStateConfiguration.DEFAULT`, which the library rejects
 * at composition — the loud failure that keeps JVM callers honest.
 */
@Composable
internal actual fun rememberNavBackStackSaveable(
    savedStateConfiguration: SavedStateConfiguration?,
    initialKey: NavKey,
): NavBackStack<NavKey> =
    rememberNavBackStack(savedStateConfiguration ?: SavedStateConfiguration.DEFAULT, initialKey)
