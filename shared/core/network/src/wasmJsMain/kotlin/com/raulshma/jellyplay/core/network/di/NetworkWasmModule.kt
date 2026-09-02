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
import com.raulshma.jellyplay.core.network.persistedOrRandomDeviceId
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
 * the counterpart of [networkJvmModule] for the web target. Consumed by the
 * web shell's startKoin (apps/web Main.kt registers it alongside
 * datastoreCommonModule/webDatastoreModule/dataWasmModule and the feature
 * modules), where its auth/library/playback singles drive the connect/sign-in
 * flow and the shared feature screens' repositories.
 *
 * Device identity (wave 21C): the device id is PERSISTED across boots —
 * direct localStorage under `jellyplay/device-id` (see
 * [persistedOrRandomDeviceId]): the first boot on an origin generates a UUID
 * v4 and stores it, every later boot re-uses the stored value, so the server
 * stops listing each browser reload as a new device. Storage-disabled/
 * private-mode origins fall back to the old random-per-boot behaviour for
 * that session only.
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
            // Resolved ONCE per boot into this immutable val: stable for the
            // whole session regardless of later storage failures; re-read
            // from localStorage only on the next cold boot.
            deviceId = persistedOrRandomDeviceId(),
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
