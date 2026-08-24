package com.raulshma.jellyplay.core.network

import com.raulshma.jellyplay.core.model.NetworkTimeoutPreset
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The one SDK-lenient Json instance for the wasm wire stack — both the
 * ContentNegotiation plugin below and the auth client's hand-rolled
 * encode/decode helpers read from this, so the two paths cannot drift apart.
 */
internal val wasmWireJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Phase W chunk 1: the Ktor HTTP stack for the wasmJs target. The Js engine
 * is fetch-backed on wasmJs and ships a wasmJs variant since Ktor 3.0 —
 * `libs.ktor.client.js` resolves directly for this target (no separate wasm
 * engine artifact exists).
 *
 * Configuration mirrors the jvmShared stack where the platforms overlap:
 *  - JSON: `ignoreUnknownKeys` + `isLenient`, the SDK-lenient decoding the
 *    Jellyfin SDK's own client uses (unknown server fields never fail a
 *    decode; unquoted scalars tolerated).
 *  - Timeouts: the same [NetworkTimeoutPreset] seconds that drive the OkHttp
 *    base client's connect/read/write timeouts. Ktor has no separate write
 *    timeout — `socketTimeoutMillis` covers socket read+write inactivity —
 *    and the whole-request cap is disabled (`requestTimeoutMillis = null`)
 *    because the OkHttp base client deliberately sets no call timeout.
 *    CAVEAT (wasm gotcha): the fetch-backed Js engine has limited native
 *    timeout support; the HttpTimeout plugin installs cleanly but treat the
 *    millisecond windows as best-effort on wasm v1.
 */
fun createWasmHttpClient(
    timeoutPreset: NetworkTimeoutPreset = NetworkTimeoutPreset.DEFAULT,
): HttpClient = HttpClient(Js) {
    // Non-2xx statuses are handled by the client code (mapped onto
    // ApiException.fromHttp) — do not let Ktor's default expectSuccess throw
    // its own exception types first.
    expectSuccess = false

    install(ContentNegotiation) {
        json(wasmWireJson)
    }

    install(HttpTimeout) {
        connectTimeoutMillis = timeoutPreset.connectSec * 1_000
        socketTimeoutMillis = timeoutPreset.readSec * 1_000
        requestTimeoutMillis = null
    }
}

/**
 * Dedicated probe client for reachability checks, mirroring the jvmShared
 * `ServerAddressRouter.probeClient` (deliberately NOT derived from the app
 * client; short 2s connect / 3s read / 5s call windows so probing a
 * black-holed address stays fast). No ContentNegotiation — probe bodies are
 * decoded by hand.
 */
fun createWasmProbeHttpClient(): HttpClient = HttpClient(Js) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 2_000
        socketTimeoutMillis = 3_000
        requestTimeoutMillis = 5_000
    }
}
