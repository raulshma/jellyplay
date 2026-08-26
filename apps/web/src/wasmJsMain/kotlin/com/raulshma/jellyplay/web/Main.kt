package com.raulshma.jellyplay.web

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.raulshma.jellyplay.core.datastore.di.datastoreCommonModule
import com.raulshma.jellyplay.core.datastore.di.webDatastoreModule
import com.raulshma.jellyplay.core.designsystem.theme.JellyPlayTheme
import com.raulshma.jellyplay.core.network.auth.AtomicSessionState
import com.raulshma.jellyplay.core.network.di.networkWasmModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import kotlinx.browser.document
import org.koin.core.context.startKoin

/**
 * Phase W web shell entry (docs/kmp-migration-plan.md §Phase W): boots the
 * shared datastore + wasm network DI stacks and renders the navigation
 * shell. The W.1/W.4 boot-proof placeholder grew up with wave 11B:
 * [WebAppRoot] renders real shared UI through core/ui's wasm-visible
 * primitives over the JB fork's NavDisplay.
 *
 * W.1 chunk 3: `networkWasmModule` registers the Ktor wasm clients
 * (auth/library/playback over ONE shared [AtomicSessionState]). No screen
 * drives them yet — the shell only OBSERVES the session state the auth
 * client publishes, proving the wiring is live end-to-end.
 *
 * W.4: boots the Coil image singleton (see main() below). Wired against the
 * repo-wide coil 3.4.0 pin — the last release line whose wasmJs klibs are
 * built with a Kotlin 2.3 compiler (see the version note in
 * gradle/libs.versions.toml).
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
        // Phase W.4 image engine: one app-wide Coil ImageLoader. This MUST be
        // the first thing in the composition root — setSingletonImageLoader-
        // Factory delegates to SingletonImageLoader.setSafe, which throws if
        // the singleton was already resolved by an earlier AsyncImage call.
        //
        // The ktor3 fetcher is registered EXPLICITLY because on wasmJs there
        // is no alternative: in coil-core 3.4.0 sources,
        // ServiceLoaderComponentRegistry's jvmCommon actual is the only one
        // reading java.util.ServiceLoader, and the nonJvmCommon (js/wasm)
        // actual starts EMPTY with a manual register() API. The desktop
        // okhttp flavor self-populates through META-INF/services on the JVM;
        // coil-network-ktor3 3.4.0 ships its @EagerInitialization initHook in
        // nativeMain ONLY — the wasm artifact contains no initHook at all —
        // so the registry can never be populated on this target and explicit
        // registration is mandatory, not merely deterministic.
        //
        // RUNTIME HONESTY (mirrors HtmlVideoEngine): this slice proves the
        // pipeline COMPILES under wasmJs only (:apps:web:compileKotlinWasmJs;
        // karma/browser testing is disabled by policy in this repo). No
        // browser has yet rendered a real AsyncImage through this loader —
        // a later real-server browser pass must verify actual artwork
        // fetches work end-to-end (engine selection, CORS against the
        // Jellyfin host, cache behaviour).
        setSingletonImageLoaderFactory { context ->
            ImageLoader.Builder(context)
                .components {
                    // KtorNetworkFetcherFactory is a public FACTORY FUNCTION in
                    // coil-network-ktor3 returning a NetworkFetcher.Factory
                    // component. The lazy () -> HttpClient overload keeps
                    // engine construction out of composition until the first
                    // request.
                    add(KtorNetworkFetcherFactory(httpClient = { HttpClient(Js) }))
                }
                .crossfade(true)
                .build()
        }

        JellyPlayTheme(darkTheme = isSystemInDarkTheme(), dynamicColor = false) {
            // The one shared session state the three wasm API clients are
            // built around — passed in directly rather than via a compose
            // Koin scope (koin-compose is not a web-shell dep yet). WebAppRoot
            // provisions the core/ui composition locals around its NavDisplay
            // and renders the web-only landing/status panes.
            WebAppRoot(sessionState = koinApp.koin.get())
        }
    }
}
