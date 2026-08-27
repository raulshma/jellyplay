package com.raulshma.jellyplay.web

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.raulshma.jellyplay.core.data.di.dataWasmModule
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.datastore.di.datastoreCommonModule
import com.raulshma.jellyplay.core.datastore.di.webDatastoreModule
import com.raulshma.jellyplay.core.designsystem.theme.JellyPlayTheme
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.core.network.auth.AtomicSessionState
import com.raulshma.jellyplay.core.network.di.networkWasmModule
import com.raulshma.jellyplay.feature.requests.di.requestsModule
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
 * (auth/library/playback over ONE shared [AtomicSessionState]). Wave 12C:
 * [WebAppRoot] now DRIVES the auth client — connect probe, sign-in,
 * capabilities, logout — through WebConnectController, so the shell observes
 * published sessions it actually created end-to-end.
 *
 * W.4: boots the Coil image singleton (see main() below). Wired against the
 * repo-wide coil 3.4.0 pin — the last release line whose wasmJs klibs are
 * built with a Kotlin 2.3 compiler (see the version note in
 * gradle/libs.versions.toml).
 *
 * `ComposeViewport` is the current CMP web entry (it replaced the deprecated
 * `CanvasBasedWindow`); it renders into the document body, taking the full
 * viewport.
 *
 * Wave 15C — the first FEATURE screen renders here: `requestsModule` (the
 * shared RequestsViewModel registration, same `module {}` the desktop shell
 * lists) + `dataWasmModule` (15B's requests repository slice:
 * SeerrRepository/ArrRepository over 15A's Seerr/TMDB/Radarr/Sonarr wasm
 * clients) join the startKoin list, and [WebAppRoot] gains an
 * `entry<Route.Requests>` composing the shared RequestsScreen. The shell
 * also provisions the ViewModelStoreOwner/LifecycleOwner pair at this root
 * ([ProvideWebShellViewModelOwners] — ComposeViewport provisions none; the
 * requests module's own fallback wrapper is pass-through since this exists).
 *
 * SEERR-ON-WEB HONESTY (the wiring-site place a reader hits it): the
 * requests feature talks to a separate Overseerr/Jellyseerr server whose
 * credentials come from SeerrSecureCredentialsStore — on web that store is
 * [com.raulshma.jellyplay.core.datastore.WasmSecureKeyValueStorage],
 * SESSION-MEMORY ONLY (empty every boot; no UI to enter credentials exists
 * in the web shell this wave). And of the two Seerr auth methods, the
 * session-cookie one is BROWSER-IMPOSSIBLE by platform rule: a browser tab
 * cannot set the `Cookie` request header (forbidden header) nor read
 * `Set-Cookie` responses, so the cookie-based Seerr login the Android/desktop
 * apps perform can never function here — only the API-key credential mode
 * can work, and only once a settings/credentials UI lands (see
 * networkWasmModule's ArrSeerrApiSupport note for the same limitation at the
 * client layer). NET RESULT: until then the requests screen on web shows
 * its honest not-configured error state ("Seerr not configured" + Retry)
 * — the state the E2E lane asserts as v1 truth.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // DI first: everything composable resolves lazily through Koin, so the
    // container must exist before the first composition. Same module shape
    // as the desktop shell's startKoin, minus the jvm-only stacks. Wave 15C
    // adds the requests slice (feature VM + data repositories) — exactly the
    // set the KoinModuleRegistrationGuardTest's web allowlist pins.
    val koinApp = startKoin {
        modules(
            datastoreCommonModule,
            webDatastoreModule(),
            networkWasmModule,
            dataWasmModule,
            requestsModule,
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
        // RUNTIME HONESTY: VERIFIED IN A REAL BROWSER (2026-08-27, wave 13C)
        // — the headless-Edge CDP lane (tools/e2e/web-verify.mjs) drove the
        // connect/sign-in flow and the gated WebDiagnosticsPane against a
        // live Jellyfin 10.11.11 server: a Primary artwork request through
        // THIS loader (KtorNetworkFetcherFactory + HttpClient(Js)) decoded
        // and rendered (painter State.Success; screenshot evidence kept out
        // of the repo), with zero console errors and zero uncaught
        // exceptions. Bearer-less image URLs (SDK parity — no api_key) load
        // fine against that server. Still unverified: cache behaviour over
        // time, large libraries, and non-Jellyfin image hosts.
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
            // Wave 15C: the ONE ViewModelStoreOwner/LifecycleOwner path (see
            // WebShellPlatformOwners.kt) — wraps the whole shell so the
            // requests entry's koinViewModel() resolves, desktop-style.
            ProvideWebShellViewModelOwners {
                // The one shared session state the three wasm API clients are
                // built around — passed in directly rather than via a compose
                // Koin scope (koin-compose is not a web-shell dep yet). Wave 12C
                // adds the auth client (the session's writer) and the shared
                // "user_prefs" DataStore (last-server-url persistence for the
                // connect form); WebAppRoot provisions the core/ui composition
                // locals around its NavDisplay and renders the web-only panes.
                WebAppRoot(
                    sessionState = koinApp.koin.get(),
                    authApiClient = koinApp.koin.get(),
                    userPrefs = koinApp.koin.get(DatastoreQualifiers.userPreferencesDataStore),
                )
            }
        }
    }
}
