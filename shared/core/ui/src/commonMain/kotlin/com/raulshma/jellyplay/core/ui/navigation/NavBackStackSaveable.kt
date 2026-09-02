package com.raulshma.jellyplay.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration

/**
 * Platform seam for the saveable Navigation 3 back stack created by
 * [rememberNavigationState] — the fix for the Android launch crash found by
 * the wave-21 device pass (phone app FATAL on first composition:
 * `IllegalArgumentException: You must pass a SavedStateConfiguration.
 * serializersModule configured to handle NavKey open polymorphism`).
 *
 * Background: navigation3 1.1.5 ships TWO saveable back stacks:
 *
 *  - commonMain `rememberNavBackStack(configuration, vararg)` — serialized via
 *    open polymorphism, so the configuration's `SerializersModule` MUST
 *    register every `NavKey` subtype (the library `require`s a non-DEFAULT
 *    module at composition). The desktop shell satisfies this with
 *    `desktopNavSavedStateConfiguration()` (kotlin-reflect enumeration of the
 *    sealed [Route] leaves); Android call sites passed nothing, so the
 *    commonMain call fell through to `SavedStateConfiguration.DEFAULT` and
 *    threw the moment the auth UI composed — the app crashed before any
 *    sign-in screen ever rendered.
 *
 *  - androidMain `rememberNavBackStack(vararg)` — an Android-only overload
 *    that serializes through the reflection-based `NavKeySerializer`, no
 *    registration required (library docs: "That version uses reflection
 *    internally and does not require subtypes to be registered, but it is not
 *    available on other platforms").
 *
 * This expect lets [rememberNavigationState] keep one common body while each
 * target picks the correct overload: Android takes the reflection path and
 * IGNORES [savedStateConfiguration] (no Android caller passes one); JVM/wasm
 * keep the explicit-configuration path, where a forgotten configuration still
 * fails loudly with the library's own message instead of silently degrading.
 */
@Composable
internal expect fun rememberNavBackStackSaveable(
    savedStateConfiguration: SavedStateConfiguration?,
    initialKey: NavKey,
): NavBackStack<NavKey>
