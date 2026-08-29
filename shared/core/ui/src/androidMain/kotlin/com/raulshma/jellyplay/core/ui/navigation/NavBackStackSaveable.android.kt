package com.raulshma.jellyplay.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration

/**
 * Android actual of [rememberNavBackStackSaveable]: the Android-only
 * reflection overload `rememberNavBackStack(vararg elements)` — serialized
 * through `NavKeySerializer` (reflection over the concrete NavKey classes),
 * which needs NO `SerializersModule` registration. The commonMain
 * configuration-taking overload is deliberately NOT used here: it requires a
 * module registering every NavKey subtype, and passing
 * `SavedStateConfiguration.DEFAULT` to it is the launch crash this seam fixes
 * (see the commonMain KDoc for the full account). [savedStateConfiguration]
 * is accepted for signature parity and ignored — no Android caller of
 * [rememberNavigationState] passes one, and the reflection serializer has no
 * use for it.
 */
@Composable
internal actual fun rememberNavBackStackSaveable(
    savedStateConfiguration: SavedStateConfiguration?,
    initialKey: NavKey,
): NavBackStack<NavKey> = rememberNavBackStack(initialKey)
