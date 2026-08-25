package com.raulshma.jellyplay.web

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.raulshma.jellyplay.core.datastore.di.datastoreCommonModule
import com.raulshma.jellyplay.core.datastore.di.webDatastoreModule
import com.raulshma.jellyplay.core.designsystem.theme.JellyPlayTheme
import com.raulshma.jellyplay.core.network.auth.AtomicSessionState
import com.raulshma.jellyplay.core.network.di.networkWasmModule
import kotlinx.browser.document
import org.koin.core.context.startKoin

/**
 * Phase W web shell entry (docs/kmp-migration-plan.md §Phase W): boots the
 * shared datastore + wasm network DI stacks and renders a minimal
 * placeholder — this is a boot-proof skeleton, not a UI. Real screens land
 * with the later W slices.
 *
 * W.1 chunk 3: `networkWasmModule` registers the Ktor wasm clients
 * (auth/library/playback over ONE shared [AtomicSessionState]). No screen
 * drives them yet — the shell only OBSERVES the session state the auth
 * client publishes, proving the wiring is live end-to-end.
 *
 * `ComposeViewport` is the current CMP web entry (it replaced the deprecated
 * `CanvasBasedWindow`); it renders into the document body, taking the full
 * viewport.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // DI first: everything composable resolves lazily through Koin, so the
    // container must exist before the first composition. Same module shape
    // as the desktop shell's startKoin, minus the jvm-only stacks.
    val koinApp = startKoin {
        modules(
            datastoreCommonModule,
            webDatastoreModule(),
            networkWasmModule,
        )
    }

    ComposeViewport(document.body!!) {
        // Phase W.4 (Coil wasm image engine) is BLOCKED at the pinned coil
        // 3.5.0: its wasmJs klibs are built with Kotlin 2.4.0 ABI, which this
        // repo's Kotlin 2.3.21 KLIB loader cannot read (klibs resolve but are
        // skipped, leaving coil3 unresolved). When it unblocks (coil 3.4.0
        // pin or Kotlin 2.4 toolchain), the wiring here is a
        // setSingletonImageLoaderFactory { context -> ImageLoader.Builder(
        // context).components { add(KtorNetworkFetcherFactory(httpClient =
        // HttpClient(Js))) }.crossfade(true).build() } — see the dependency
        // note in this module's build.gradle.kts.

        JellyPlayTheme(darkTheme = isSystemInDarkTheme(), dynamicColor = false) {
            // The one shared session state the three wasm API clients are
            // built around — passed in directly rather than via a compose
            // Koin scope (koin-compose is not a web-shell dep yet).
            WebShellScreen(sessionState = koinApp.koin.get())
        }
    }
}
