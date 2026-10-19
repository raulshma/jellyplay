package com.raulshma.jellyplay.feature.home

/**
 * Process-lifecycle seam for [HomeViewModel] (conveyor transform:
 * `androidx.lifecycle.ProcessLifecycleOwner` is Android-only).
 *
 * Registers app-foreground/background callbacks with the platform's process
 * lifecycle and returns a removal handle, or `null` where no process
 * lifecycle exists. The Android actual registers a `DefaultLifecycleObserver`
 * against ProcessLifecycleOwner — `runCatching`-guarded, so environments
 * without an initialised process
 * LifecycleOwner silently no-op instead of crashing construction. The JVM
 * actual has no process lifecycle at all and always returns `null`: neither
 * callback fires, and the refresher's start/stop stays test-driven.
 */
internal expect fun registerHomeProcessLifecycle(
    onStart: () -> Unit,
    onStop: () -> Unit,
): (() -> Unit)?
