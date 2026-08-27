package com.raulshma.jellyplay.core.network.arr

import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.network.seerr.arrSeerrWireJson

/**
 * Pure, commonMain wire helpers for the Phase W wasm Radarr/Sonarr clients —
 * the byte-level conventions of the jvmShared `RadarrApiClientImpl` /
 * `SonarrApiClientImpl` (OkHttp) extracted for wasm + commonTest. The
 * jvmShared impls keep their own private copies; the two MUST stay in sync.
 */

/** `RadarrApiClientImpl`/`SonarrApiClientImpl.buildUrl`: `$base/api/v3$path` with the base trailing slash trimmed. */
internal fun arrApiUrl(baseUrl: String, path: String): String {
    val base = baseUrl.trimEnd('/')
    return "$base/api/v3$path"
}

/**
 * The Radarr AND Sonarr `parseErrorMessage`, verbatim (the two jvmShared
 * impls are character-identical here): the body is re-parsed to a JSON
 * element and re-serialized COMPACTLY (element `.toString()`), so raw
 * whitespace/newlines never reach the error string; a blank result degrades
 * to a bare `HTTP $code`; non-JSON bodies fall back to the first 200 chars.
 */
internal fun arrHttpErrorMessage(code: Int, body: String): String = try {
    val msg = arrSeerrWireJson.parseToJsonElement(body).toString()
    if (msg.isNotBlank()) "HTTP $code: ${msg.take(200)}" else "HTTP $code"
} catch (_: Exception) {
    "HTTP $code: ${body.take(200)}"
}

/**
 * The `X-Api-Key` header both *arr v3 APIs authenticate with — the same
 * header name Seerr uses for API-key credentials (see
 * `SeerrApiClientImpl.withAuth`).
 */
internal fun arrApiKeyHeaders(apiKey: String): List<Pair<String, String>> =
    listOf("X-Api-Key" to apiKey)

/**
 * The `withDeleteOptions` query pairs both *arr impls attach to
 * `DELETE /queue/{id}` and `DELETE /queue/bulk`
 * (`removeFromClient` / `blocklist` / `skipRedownload`, in that order,
 * rendered via `Boolean.toString()`).
 */
internal fun ArrQueueDeleteOptions.toQueryPairs(): List<Pair<String, String>> = listOf(
    "removeFromClient" to removeFromClient.toString(),
    "blocklist" to blocklist.toString(),
    "skipRedownload" to skipRedownload.toString(),
)
