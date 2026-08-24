package com.raulshma.jellyplay.core.network.di

import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.core.network.api.KtorWasmAuthApiClient
import com.raulshma.jellyplay.core.network.createWasmHttpClient
import com.raulshma.jellyplay.core.network.createWasmProbeHttpClient
import com.raulshma.jellyplay.core.network.randomUuidV4
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
 * Koin construction owner for the wasmJs network stack (Phase W chunk 1) —
 * the counterpart of [networkJvmModule] for the web target. Nothing consumes
 * it yet (apps/web lands in a later Phase W chunk); it exists so the module
 * keeps compiling and the wiring is reviewable in isolation.
 *
 * Device identity: the device id is a random UUID v4 PER BOOT — there is no
 * persisted device identity on wasm v1 (browser storage adapter arrives with
 * the Phase W persistence chunk). Consequence, documented: the server lists
 * each browser reload as a new device until persistence lands.
 */
val networkWasmModule: Module = module {
    single { createWasmHttpClient() }
    single { createWasmProbeHttpClient() }
    single {
        KtorWasmAuthApiClient(
            httpClient = get(),
            probeHttpClient = get(),
            clientName = "JellyPlay",
            clientVersion = WASM_APP_VERSION,
            deviceName = WASM_DEVICE_NAME,
            deviceId = randomUuidV4(),
        )
    }
    single<AuthApiClient> { get<KtorWasmAuthApiClient>() }
}
