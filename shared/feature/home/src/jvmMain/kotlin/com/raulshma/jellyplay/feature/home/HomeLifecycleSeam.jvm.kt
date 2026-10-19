package com.raulshma.jellyplay.feature.home

/**
 * JVM actual of the home process-lifecycle seam: desktop has no process
 * LifecycleOwner, so registration is a no-op returning `null` — neither
 * callback ever fires, and the refresher stays unstarted until something
 * else drives it.
 *
 * Desktop note: when desktop nav wiring reaches home,
 * the refresher's start/stop must be driven some other way (e.g. call
 * `onStart` eagerly here once the VM is actually instantiated on desktop) —
 * until then no desktop code constructs this VM.
 */
internal actual fun registerHomeProcessLifecycle(
    onStart: () -> Unit,
    onStop: () -> Unit,
): (() -> Unit)? = null
