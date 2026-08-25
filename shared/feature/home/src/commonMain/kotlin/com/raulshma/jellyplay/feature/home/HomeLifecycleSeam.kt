package com.raulshma.jellyplay.feature.home

/**
 * Process-lifecycle seam for [HomeViewModel] (V3 conveyor transform:
 * `androidx.lifecycle.ProcessLifecycleOwner` is Android-only).
 *
 * Registers app-foreground/background callbacks with the platform's process
 * lifecycle and returns a removal handle, or `null` where no process
 * lifecycle exists. The Android actual registers a `DefaultLifecycleObserver`
 * against ProcessLifecycleOwner — `runCatching`-guarded exactly like the
 * legacy inline registration, so environments without an initialised process
 * LifecycleOwner silently no-op instead of crashing construction. The JVM
 * actual has no process lifecycle at all and always returns `null`: neither
 * callback fires, which preserves the legacy JVM-test semantics (the
 * refresher's start/stop is driven by tests directly).
 */
internal expect fun registerHomeProcessLifecycle(
    onStart: () -> Unit,
    onStop: () -> Unit,
): (() -> Unit)?
