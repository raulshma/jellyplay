package com.raulshma.jellyplay.core.network.di

import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.core.network.api.KtorWasmAuthApiClient
import com.raulshma.jellyplay.core.network.api.KtorWasmLibraryApiClient
import com.raulshma.jellyplay.core.network.api.KtorWasmPlaybackApiClient
import com.raulshma.jellyplay.core.network.api.KtorWasmRadarrApiClient
import com.raulshma.jellyplay.core.network.api.KtorWasmSeerrApiClient
import com.raulshma.jellyplay.core.network.api.KtorWasmSonarrApiClient
import com.raulshma.jellyplay.core.network.api.KtorWasmTmdbApiClient
import com.raulshma.jellyplay.core.network.api.KtorWasmUserApiClient
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.network.api.PlaybackApiClient
import com.raulshma.jellyplay.core.network.api.TmdbApiClient
import com.raulshma.jellyplay.core.network.api.UserApiClient
import com.raulshma.jellyplay.core.network.api.WasmClientIdentity
import com.raulshma.jellyplay.core.network.arr.RadarrApiClient
import com.raulshma.jellyplay.core.network.arr.SonarrApiClient
import com.raulshma.jellyplay.core.network.auth.AtomicSessionState
import com.raulshma.jellyplay.core.network.createWasmHttpClient
import com.raulshma.jellyplay.core.network.createWasmProbeHttpClient
import com.raulshma.jellyplay.core.network.randomUuidV4
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClient
import org.koin.core.module.Module
import org.koin.dsl.module

// Web counterpart of the desktop module's DESKTOP_APP_VERSION /
// DESKTOP_DEVICE_NAME constants: the web build has no package manager either,
// so the client version mirrors the desktop fallback until a shared version
// source lands, and the device name identifies the client in the server's
// session/device lists.
private const val WASM_APP_VERSION = "1.0"
private const val WASM_DEVICE_NAME = "JellyPlay Web"

/**
 * Koin construction owner for the wasmJs network stack (Phase W chunks 1-2;
 * chunk 4 adds the stateless Seerr / Radarr / Sonarr / TMDB clients) —
 * the counterpart of [networkJvmModule] for the web target. Nothing
 * consumes it yet (apps/web wiring lands in a later Phase W chunk); it
 * exists so the module keeps compiling and the wiring is reviewable in
 * isolation.
 *
 * Device identity: the device id is a random UUID v4 PER BOOT — there is no
 * persisted device identity on wasm v1 (browser storage adapter arrives with
 * the Phase W persistence chunk). Consequence, documented: the server lists
 * each browser reload as a new device until persistence lands.
 *
 * Session sharing (chunk 2): ONE [AtomicSessionState] is constructed here and
 * injected into the auth, library and playback clients — the auth client
 * publishes into it, the library/playback clients derive per-request base
 * URL + token from it (the wasm stand-in for the engine-held SDK client).
 */
val networkWasmModule: Module = module {
    single { createWasmHttpClient() }
    single { createWasmProbeHttpClient() }
    single { AtomicSessionState() }
    single {
        WasmClientIdentity(
            clientName = "JellyPlay",
            clientVersion = WASM_APP_VERSION,
            deviceName = WASM_DEVICE_NAME,
            deviceId = randomUuidV4(),
        )
    }
    single {
        KtorWasmAuthApiClient(
            httpClient = get(),
            probeHttpClient = get(),
            identity = get(),
            sharedSessionState = get(),
        )
    }
    single {
        KtorWasmLibraryApiClient(
            httpClient = get(),
            sessionState = get(),
            identity = get(),
        )
    }
    single {
        KtorWasmPlaybackApiClient(
            httpClient = get(),
            sessionState = get(),
            identity = get(),
        )
    }
    single {
        KtorWasmUserApiClient(
            httpClient = get(),
            sessionState = get(),
            identity = get(),
        )
    }
    single<AuthApiClient> { get<KtorWasmAuthApiClient>() }
    single<LibraryApiClient> { get<KtorWasmLibraryApiClient>() }
    single<PlaybackApiClient> { get<KtorWasmPlaybackApiClient>() }
    single<UserApiClient> { get<KtorWasmUserApiClient>() }
    // Seerr / *arr / TMDB (Phase W chunk 4): stateless per-call
    // (baseUrl, credentials) clients over the shared app HttpClient — they
    // hold NO session state and take their own `ArrSeerrApiSupport` base.
    // DELTA vs the JVM graph: networkJvmModule binds each interface through a
    // DI-level `Resilient*` wrapper (OkHttp-side jvmShared classes); on wasm
    // the clients fold the identical retry budget in themselves
    // (`apiResultWithRetry`, max 4 = the wrappers' MAX_RETRIES), so the
    // interface binds straight to the Ktor client with no wrapper.
    single {
        KtorWasmSeerrApiClient(
            httpClient = get(),
        )
    }
    single {
        KtorWasmTmdbApiClient(
            httpClient = get(),
        )
    }
    single {
        KtorWasmRadarrApiClient(
            httpClient = get(),
        )
    }
    single {
        KtorWasmSonarrApiClient(
            httpClient = get(),
        )
    }
    single<SeerrApiClient> { get<KtorWasmSeerrApiClient>() }
    single<TmdbApiClient> { get<KtorWasmTmdbApiClient>() }
    single<RadarrApiClient> { get<KtorWasmRadarrApiClient>() }
    single<SonarrApiClient> { get<KtorWasmSonarrApiClient>() }
}
