package com.raulshma.jellyplay.feature.requests

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * SPIKE RESULT (evidence in the KDoc of [ProvidePlatformLocalsFallback]):
 * hosts that render [RequestsScreen] without a composition-root owner pair
 * (tests, embedded previews) get NEITHER a `ViewModelStoreOwner` NOR a
 * `LifecycleOwner`, and Koin 4.2.2's `koinViewModel()` hard-errors without
 * the former. This wrapper is the minimal provisioning: a pass-through on
 * android/desktop (whose composition roots already provide both locals,
 * byte-identical behavior) and a remember-scoped root owner otherwise.
 *
 * WHY WRAP HERE AND NOT ONLY AT THE HOST: `koinViewModel()` is evaluated as a
 * default parameter of [RequestsScreen]'s body, so the provider must sit
 * OUTSIDE that call — the requests nav entry is the natural seam for hosts
 * that do not provision owners (the android/desktop entries here; on those
 * two the Activity/window already provide the locals, so this wrapper IS a
 * pass-through).
 */
internal val LocalPlatformLocalsFallbackActive = compositionLocalOf { false }

@Composable
internal fun ProvidePlatformLocalsFallback(content: @Composable () -> Unit) {
    if (LocalViewModelStoreOwner.current != null) {
        // Android (ActivitysetContent owner) and desktop (CMP window-scoped
        // owner via ProvidePlatformCompositionLocals): locals already present.
        content()
    } else {
        // No owner present: provide a root owner for the screen's lifetime.
        val owner = remember { FallbackScreenPlatformOwner() }
        DisposableEffect(owner) {
            onDispose {
                // Fire onCleared() for every VM created in this store (e.g.
                // RequestsViewModel's startPolling/stopPolling battery
                // contract) — the composition leaving is this screen's
                // "cleared" moment.
                owner.viewModelStore.clear()
            }
        }
        CompositionLocalProvider(
            LocalViewModelStoreOwner provides owner,
            LocalLifecycleOwner provides owner,
            LocalPlatformLocalsFallbackActive provides true,
        ) {
            content()
        }
    }
}

/**
 * Root owner pair for the fallback path. [LifecycleRegistry] sits in ON_RESUME
 * for the screen's active span (collectAsStateWithLifecycle requires a
 * LifecycleOwner; without it `LocalLifecycleOwner.current` throws — verified
 * in lifecycle-runtime-compose 2.11.0 commonMain, which defaults the local to
 * an error).
 */
private class FallbackScreenPlatformOwner : ViewModelStoreOwner, LifecycleOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()

    private val registry = LifecycleRegistry(this).apply {
        handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override val lifecycle: Lifecycle
        get() = registry
}
