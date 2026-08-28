package com.raulshma.jellyplay.web

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import coil3.EventListener
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.fetch.Fetcher
import coil3.key.Keyer
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.request.crossfade
import com.raulshma.jellyplay.core.data.di.dataWasmModule
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.datastore.di.datastoreCommonModule
import com.raulshma.jellyplay.core.datastore.di.webDatastoreModule
import com.raulshma.jellyplay.core.designsystem.theme.JellyPlayTheme
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.core.network.auth.AtomicSessionState
import com.raulshma.jellyplay.core.network.di.networkWasmModule
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.calendar.di.calendarModule
import com.raulshma.jellyplay.feature.details.detailsModule
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
 * credentials come from SeerrSecureCredentialsStore. Wave 16B closes the
 * biggest honesty gap: a credentials UI now EXISTS ([WebSeerrPane] via
 * WebAppRoot's `entry<WebSeerr>`, opened from the connected card's "Seerr"
 * button), and the API key PERSISTS across reloads — webDatastoreModule
 * binds SeerrSecureCredentialsStore to
 * [com.raulshma.jellyplay.core.datastore.LocalStorageSecureKeyValueStorage]
 * (localStorage, keys `jellyplay/secure/seerr/<key>`; XSS-readable by
 * design-consequence, accepted for this config-tier secret — see that
 * class's KDoc). And of the two Seerr auth methods, the session-cookie one
 * is BROWSER-IMPOSSIBLE by platform rule: a browser tab cannot set the
 * `Cookie` request header (forbidden header) nor read `Set-Cookie`
 * responses, so the cookie-based Seerr login the Android/desktop apps
 * perform can never function here — only the API-key credential mode can
 * work, and the pane offers exactly that mode and nothing else. The OTHER
 * credential stores (Jellyfin token, *arr keys, subtitle-provider
 * credentials) remain SESSION-MEMORY ONLY
 * ([com.raulshma.jellyplay.core.datastore.WasmSecureKeyValueStorage], empty
 * every boot) — the wave-16B persistence carve-out covers Seerr alone.
 * NET RESULT: until credentials are entered in the Seerr pane, the requests
 * screen on web shows its honest not-configured error state ("Seerr not
 * configured" + Retry); after a successful Save/Test they flip live with no
 * restart (the repository re-reads the stores per call).
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
            // Wave 16A: the second feature slice on web — the calendar VM
            // (its ctor deps ArrRepository/SeerrRepository/ExperimentalStore
            // all resolve from the modules above, calendarModule registers
            // nothing new). KoinModuleRegistrationGuardTest's web allowlist
            // pins this registration in the same change.
            calendarModule,
            // Wave 16C: the SeerrDetail slice. detailsModule is now the
            // wasm-clean module (the MediaDetail cluster's VM/factory defs
            // moved to the jvm platform modules the android/desktop apps
            // register); its only def the browser ever resolves is
            // SeerrDetailViewModel, whose ctor deps — SeerrRepository +
            // SeerrRequestDelegate (dataWasmModule), PreferenceProjections +
            // SeerrPreferencesStore (datastoreCommonModule), and the narrow
            // MediaRepository (webDetailsPlatformModule below, over
            // networkWasmModule's LibraryApiClient) — all resolve on web.
            detailsModule,
            webDetailsPlatformModule(),
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
        // fine against that server.
        //
        // CACHE/LONG-SESSION: NOW VERIFIED TOO (2026-08-28, wave 18A, tools/
        // e2e/web-soak.mjs, 50 Back→Diagnostics cycles + 3 reloads): cold
        // load = 2 network fetches (the pane's two concurrent same-URL loads
        // — raw painter + MediaImage — are NOT coalesced; each fetches, both
        // then share one memory entry keyed by the URL); every warm cycle
        // after that = 2 memory-cache hits and ZERO refetches for the whole
        // soak (final counters hits=98 misses=2 net=2 fail=0); GC'd
        // JSHeapUsedSize stayed flat across all 10 GC'd samples (28.7→28.9MB,
        // final/first = 1.006, slope ≈ -0.4KB/cycle); GC'd Nodes/Listeners
        // returned to baseline every time (231/55 → 231/55 — VideoCheck's
        // video-host div create/remove does not leak); ms-to-OK p50 4ms /
        // max 18ms; zero console errors end to end. The first
        // instrumentation draft ALSO caught a real bug in itself (counters
        // nobody read reported hits=0 while Coil's DEBUG log proved
        // MEMORY_CACHE hits) — recorded in CountingMemoryCache's KDoc.
        // STILL UNVERIFIED on web: large libraries (the fixture has exactly
        // one poster — LRU eviction beyond ~130 decoded entries at the 80MB
        // cap is arithmetic, not measurement), non-Jellyfin image hosts, and
        // browser HTTP-cache reuse across reloads (measured
        // fromDiskCache=false on every reload against this server's headers
        // — informational; the wasm singleton has no Coil disk cache to
        // persist anything itself).
        setSingletonImageLoaderFactory { context ->
            ImageLoader.Builder(context)
                .components {
                    // KtorNetworkFetcherFactory is a public FACTORY FUNCTION in
                    // coil-network-ktor3 returning a NetworkFetcher.Factory
                    // component. The lazy () -> HttpClient overload keeps
                    // engine construction out of composition until the first
                    // request.
                    add(KtorNetworkFetcherFactory(httpClient = { HttpClient(Js) }))
                    // WAVE 18A MEASURED FINDING (the Coil long-session soak):
                    // without a Keyer for String, the memory cache NEVER
                    // engages on this target — MemoryCacheService.newCacheKey
                    // consults ComponentRegistry.keyers, and coil-core 3.4.0's
                    // wasmJs klib ships only UriKeyer/FileUriKeyer (checked
                    // the klib symbols; StringKeyer does not exist upstream at
                    // this pin at all). The first soak run measured exactly
                    // that: hits=0 misses=0 while net=2 fired on EVERY pane
                    // entry — the cache was allocated (15% of Coil's hardcoded
                    // wasm memory ceiling) but never read nor written. Same
                    // explicit-registration mandate as the fetcher above: the
                    // JVM side self-populates its String keyer through
                    // ServiceLoader, and the nonJvm registry starts empty.
                    // Keying by the raw URL string (what upstream later added
                    // as StringKeyer) makes the cache live: same URL ⇒ same
                    // key ⇒ warm pane entries resolve from memory; size
                    // differences stay safe because a non-transformed request
                    // bakes no size into the key and isCacheValueValid still
                    // rejects undersized sampled results.
                    add(Keyer<String> { data, _ -> data })
                }
                // Wave 18A: cache/long-session observability for the web-soak
                // lane (tools/e2e/web-soak.mjs). Both hooks are behavior-
                // preserving (see CoilStats KDoc for what is counted and why
                // the counting cache mirrors, not replaces, the default).
                .memoryCache {
                    CountingMemoryCache(
                        // EXACTLY the cache ImageLoader.Builder.build() would
                        // create on its own (same maxSizePercent(application)
                        // default — 15% of Coil's wasm totalAvailableMemoryBytes),
                        // so cache sizing is unchanged; only get() is observed.
                        delegate = MemoryCache.Builder().maxSizePercent(context).build(),
                    ).also { CoilStats.cache = it }
                }
                .eventListener(CoilStatsEventListener)
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
                    // Wave 16B: the WebSeerrController deps (same pattern as
                    // userPrefs above — resolved here, passed down). All
                    // three bindings are UNNAMED singles (DatastoreQualifiers
                    // qualify only the raw DataStores):
                    // datastoreCommonModule → SeerrPreferencesStore,
                    // webDatastoreModule → SeerrSecureCredentialsStore
                    // (localStorage-backed since wave 16B),
                    // dataWasmModule → SeerrRepository.
                    seerrPreferencesStore = koinApp.koin.get(),
                    seerrSecureCredentialsStore = koinApp.koin.get(),
                    seerrRepository = koinApp.koin.get(),
                    // GATED E2E BOOT ROUTE (see WebAppRoot's backStack note):
                    // parsed once here from the boot URL; real navigation
                    // never sets the param, so this is null for every human
                    // load. The `variant` param only qualifies the
                    // inputprobe route (scrollable lattice); null otherwise.
                    bootRoute = parseE2eBootRoute(),
                    bootVariant = e2eVariantParam(),
                )
            }
        }
    }
}

/**
 * GATED E2E BOOT ROUTE (desktop `jellyplay.harness.*` prop precedent):
 * `?e2eRoute=seerrdetail/<tmdbId>/<mediaType>` seeds the shell's back stack
 * with that shared route at boot so the CDP verification lane can reach it
 * without depending on synthetic mouse-click geometry (wave 17A's
 * clean-room probe found NO Compose input dead region — the wave-16 report
 * was that wave's SeerrDetailViewModel construction crash freezing
 * composition after the demo-button click landed;
 * docs/e2e/web-input-dead-region.md has the measured evidence, WebAppRoot's
 * backStack note the lane rationale). Wave 17A adds
 * `?e2eRoute=inputprobe` for that investigation: that route bypasses the
 * shell entirely (see [WebInputProbe]) — no NavDisplay, no session gate, no
 * server. Parsed ONCE at boot; no user-facing surface sets the parameter,
 * so every human load parses null and boots on the landing pane. Unknown
 * values are ignored silently — this is a lane hook, not a router.
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun parseE2eBootRoute(): NavKey? {
    val raw = e2eRouteParam() ?: return null
    if (raw == "inputprobe") return WebInputProbe
    val parts = raw.split('/')
    if (parts.size != 3 || parts[0] != "seerrdetail") return null
    val tmdbId = parts[1].toIntOrNull() ?: return null
    return Route.SeerrDetail(tmdbId = tmdbId, mediaType = parts[2])
}

/**
 * `?variant=` boot param — qualifies the `inputprobe` route ONLY
 * (`variant=scroll` renders the probe lattice inside a verticalScroll;
 * see [WebInputProbePane]). Parsed with the same lane-hook rules: null for
 * every human load, ignored for every other route.
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun e2eVariantParam(): String? = js("new URLSearchParams(window.location.search).get('variant')")

/** Single-expression `js()` body (the WasmClock rule: wasm `js()` may only
 *  be a function's whole body, never embedded in larger expressions). */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun e2eRouteParam(): String? = js("new URLSearchParams(window.location.search).get('e2eRoute')")

/**
 * Wave 18A: process-lifetime counters for the web Coil singleton, surfaced in
 * the WebDiagnostics pane as the load-bearing `COIL_STATS:` / `COIL_CACHE:`
 * lines (see that pane's strings-contract note) and read by the long-session
 * soak lane (tools/e2e/web-soak.mjs). Deliberately dumb totals — no reset, no
 * history: the soak takes its own per-cycle deltas from these monotonics.
 *
 * WHAT IS COUNTED (measured against coil-core 3.4.0 sources, not inferred):
 *  - hits/misses: Coil 3.4.0's [EventListener] has NO memory-cache events
 *    (the memoryCacheHit/Miss/Set callbacks are a later-3.x addition), so the
 *    counts are taken at the [MemoryCache.get] boundary by
 *    [CountingMemoryCache] instead. EngineInterceptor executes exactly one
 *    memory-cache lookup per request run (its getCacheValue call), so one
 *    request ⇒ one hit OR one miss — PROVIDED a cache key exists: with no
 *    matching Keyer the lookup is skipped entirely (hits=misses=0, the
 *    pre-fix state the first soak run measured; see the Keyer registration
 *    in main()). Weak-reference re-hits count as hits too (they return
 *    non-null through RealMemoryCache.get — from Coil's and our perspective
 *    a hit is a hit).
 *  - net: EventListener.fetchStart fires before EVERY Fetcher.fetch attempt.
 *    On this config the only registered fetcher for URL data is the Ktor
 *    network fetcher and wasm has no disk cache (singletonDiskCache() is
 *    null), so every fetchStart is a real network fetch execution — and every
 *    miss resolves to exactly one net (miss ⇒ fetch; there is no second
 *    tier). Measured net==misses after cold loads is therefore EXPECTED and
 *    itself documents the no-disk-cache reality.
 *  - fail: request-level onError — a non-2xx body, transport failure, or
 *    decode error all funnel here on this config.
 *
 * Wasm/JS is single-threaded in this shell (no Kotlin workers — the compose
 * web entry runs every coroutine on the one JS event loop), so the counters
 * are plain Ints. Note this is a measured platform constraint, not a choice:
 * the stdlib common atomics (kotlin.concurrent.atomics) do not resolve their
 * documented surface on the wasmJs actual at the 2.3.21 pin (.value private,
 * addAndGet unresolved), and the JVM-only kotlin.concurrent.AtomicInt does
 * not exist on this target at all.
 */
internal object CoilStats {
    var requests = 0
        internal set
    var hits = 0
        internal set
    var misses = 0
        internal set
    var net = 0
        internal set
    var fail = 0
        internal set
    var success = 0
        internal set

    /** The counting cache registered in main()'s loader factory; null until
     *  the singleton first resolves (it is built lazily). Written exactly
     *  once from the factory initializer in main(). */
    var cache: CountingMemoryCache? = null
        internal set

    /** The AX-visible stats line — exact format is load-bearing
     *  (WebDiagnostics strings contract; tools/e2e/web-soak.mjs parses it). */
    fun axStatsLine(): String =
        "COIL_STATS: hits=$hits misses=$misses net=$net fail=$fail"

    /** Coil-counted bytes of decoded bitmaps currently held vs the LRU cap
     *  (the default 15% of Coil's hardcoded wasm totalAvailableMemoryBytes —
     *  the runtime value is measured, not assumed). */
    fun axCacheLine(): String {
        val counting = cache ?: return "COIL_CACHE: none"
        return "COIL_CACHE: size=${counting.size} maxSize=${counting.maxSize}"
    }
}

/**
 * The one [EventListener] for the web singleton. Counts request starts
 * (so the soak can sanity-check hits+misses against them), fetch attempts
 * ([net]), completions, and failures. See [CoilStats] for the exact
 * 3.4.0 event semantics.
 */
internal object CoilStatsEventListener : EventListener() {
    override fun onStart(request: ImageRequest) {
        CoilStats.requests += 1
    }

    override fun fetchStart(request: ImageRequest, fetcher: Fetcher, options: Options) {
        CoilStats.net += 1
    }

    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        CoilStats.success += 1
    }

    override fun onError(request: ImageRequest, result: ErrorResult) {
        CoilStats.fail += 1
    }
}

/**
 * Behavior-preserving [MemoryCache] decorator: everything delegates to the
 * real cache except [get], which tallies hit/miss STRAIGHT INTO
 * [CoilStats] on its way through (single source of truth for the pane line —
 * the first wired version incremented counters nobody read, and the soak
 * faithfully reported hits=0 misses=0 while Coil's own DEBUG logger proved
 * warm entries resolving from MEMORY_CACHE; the soak caught the bug). The
 * delegate is constructed with the identical default sizing call
 * (MemoryCache.Builder().maxSizePercent(context).build()) that
 * ImageLoader.Builder.build() uses when no cache is supplied — so eviction
 * bounds, weak-reference behavior, and cache keys are all unchanged; only the
 * lookup outcome is observed. initialMaxSize is @ExperimentalCoilApi in the
 * interface (the one opt-in this wave needs; a decorator must implement it).
 */
@OptIn(ExperimentalCoilApi::class)
internal class CountingMemoryCache(private val delegate: MemoryCache) : MemoryCache by delegate {
    override fun get(key: MemoryCache.Key): MemoryCache.Value? {
        val value = delegate[key]
        if (value != null) CoilStats.hits += 1 else CoilStats.misses += 1
        return value
    }
}
