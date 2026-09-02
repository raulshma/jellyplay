package com.raulshma.jellyplay.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * THE one ViewModelStoreOwner/LifecycleOwner provisioning path for the web
 * shell (wave 15C). `ComposeViewport` provisions NEITHER local (wave 15B
 * spike: CMP 1.11.1's ui-wasm-js klib contains zero LocalViewModelStoreOwner
 * refs and ComposeViewport wires no owners), and `koinViewModel()` —
 * RequestsScreen's default parameter — hard-errors without the former. This
 * provider sits at the composition ROOT in Main.kt, so every screen the
 * shell ever renders resolves ViewModels against ONE page-scoped store,
 * mirroring desktop exactly: CMP's desktop window provisions a
 * window-scoped owner and no desktop screen wrapper exists either.
 *
 * ONE TRUTH (deliberate, documented at both ends): the shell provisions;
 * nobody else does. shared/feature/requests' ProvidePlatformLocalsFallback
 * keeps a provisioning branch for non-shell hosts, but with the shell
 * providing it is a pass-through on ALL shipped surfaces (its check reads
 * the local the shell just provided). Its owner is `internal` to that
 * module anyway — apps/web cannot reach it, which structurally prevents a
 * second provisioning path from being re-introduced here.
 *
 * Lifetime semantics: the owner is `remember`ed at the root, i.e. the page
 * itself. ViewModels created through it (RequestsViewModel on web today)
 * live until the tab closes — a page RELOAD restarts everything (the shell's
 * back stack is memory-only with the same contract), so nothing survives
 * that would need clearing. Consequence accepted and stated: there is no
 * per-screen ViewModel clear on web navigation — RequestsViewModel's
 * Seerr polling (stopPolling in onCleared) keeps running after the user
 * navigates back, exactly like the window-scoped desktop behavior its VM
 * code was written against. The [LifecycleRegistry] sits at ON_RESUME for
 * the page's span, which is what collectAsStateWithLifecycle needs.
 */
@Composable
fun ProvideWebShellViewModelOwners(content: @Composable () -> Unit) {
    val owner = remember { WebShellPlatformOwner() }
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides owner,
        LocalLifecycleOwner provides owner,
    ) {
        content()
    }
}

/**
 * Page-scoped owner pair. The requests module's WebScreenPlatformOwner is
 * structurally identical (15B proved the shape compiles + renders on the
 * wasm klibs); this duplicate is the shell's OWN copy, not a shared one —
 * the seam between "shell owns platform locals" and "feature requests its
 * own fallback" stays visible in the sources.
 */
private class WebShellPlatformOwner : ViewModelStoreOwner, LifecycleOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()

    private val registry = LifecycleRegistry(this).apply {
        handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override val lifecycle: Lifecycle
        get() = registry
}
