package com.raulshma.jellyplay.feature.home

/**
 * JVM actual of the home process-lifecycle seam: desktop has no process
 * LifecycleOwner, so registration is a no-op returning `null` — neither
 * callback ever fires. This preserves the legacy JVM behavior exactly (the
 * `runCatching`-guarded `ProcessLifecycleOwner.get()` call threw and was
 * swallowed, leaving the refresher unstarted until something else drove it).
 *
 * Latent-desktop note (coordinator): when desktop nav wiring reaches home,
 * the refresher's start/stop must be driven some other way (e.g. call
 * `onStart` eagerly here once the VM is actually instantiated on desktop) —
 * until then no desktop code constructs this VM.
 */
internal actual fun registerHomeProcessLifecycle(
    onStart: () -> Unit,
    onStop: () -> Unit,
): (() -> Unit)? = null
