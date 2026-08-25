package com.raulshma.jellyplay.feature.home

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Android actual of the home process-lifecycle seam: a `DefaultLifecycleObserver`
 * registered against [ProcessLifecycleOwner], field-for-field the legacy
 * feature:home behavior (observer added in the VM's init, removed in
 * onCleared; both `runCatching`-guarded so an uninitialised process
 * LifecycleOwner degrades to a no-op instead of crashing).
 */
internal actual fun registerHomeProcessLifecycle(
    onStart: () -> Unit,
    onStop: () -> Unit,
): (() -> Unit)? = runCatching {
    val lifecycleOwner = ProcessLifecycleOwner.get()
    val observer = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            super.onStart(owner)
            onStart()
        }

        override fun onStop(owner: LifecycleOwner) {
            super.onStop(owner)
            onStop()
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    // Removal handle (symmetric with the registration guard above).
    val removal: () -> Unit = {
        runCatching { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    removal
}.getOrNull()
