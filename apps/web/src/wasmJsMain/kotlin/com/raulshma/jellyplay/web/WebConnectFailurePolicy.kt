package com.raulshma.jellyplay.web

import com.raulshma.jellyplay.core.network.api.ApiException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.io.IOException

/**
 * The web connect flow's failure taxonomy, extracted out of WebConnectFlow.kt
 * (slice 2) so the wasmJs unit lane can pin it — the functions were
 * `private` in a UI file, which left the classification "statically
 * unverifiable" until a real-server browser pass (their original KDoc's own
 * words). The logic below is byte-for-byte the code that shipped there; only
 * visibility changed (private → internal) and the wasmJsTest source set now
 * covers it ([WebConnectFailurePolicyTest] — still no browser: hand-built
 * throwables stand in for the engine-specific failures). The real-server
 * browser pass remains the authority for whether the MESSAGE fragments below
 * match what engines actually surface; the tests pin the taxonomy, not the
 * browsers' wording.
 */

/**
 * True when [failure] looks like a transport-layer refusal rather than a
 * server verdict. Two signals, either suffices:
 *  - TYPE: Ktor Js/fetch IO errors (which CORS blocks surface as) or the
 *    timeout plugin. The assumption that the Js engine wraps fetch failures
 *    in [IOException] is statically unverifiable from this repo's lanes.
 *    (Redundant-but-harmless: on ktor 3.5.2 `HttpRequestTimeoutException`
 *    already extends `IOException`, so the first check is subsumed by the
 *    second — kept so the intent survives a ktor change.)
 *  - MESSAGE: the raw browser rejection strings ("Failed to fetch" on
 *    Chromium, "NetworkError" on Firefox, "Load failed" on WebKit) matched
 *    case-insensitively down the cause chain, so the friendly line + CORS
 *    hint survive an engine whose wrapping differs; the coordinator's
 *    real-server browser pass will confirm the actual taxonomy.
 *
 * Used only to decide whether the CORS doc pointer shows alongside the error
 * line — never to replace the typed message itself.
 */
internal fun isLikelyCorsOrTransport(failure: Throwable): Boolean {
    var cause: Throwable? = failure
    while (cause != null) {
        if (cause is HttpRequestTimeoutException || cause is IOException) return true
        val message = cause.message?.lowercase() ?: ""
        if (
            "failed to fetch" in message ||
            "networkerror" in message ||
            "load failed" in message
        ) {
            return true
        }
        cause = cause.cause
    }
    return false
}

/**
 * Probe-stage error mapping: transport refusals get a diagnosable line (with
 * the CORS doc hint added separately when the browser still reports
 * connectivity); anything else falls back to whatever the failure carries.
 */
internal fun friendlyProbeFailure(failure: Throwable, browserOnline: Boolean): String {
    if (isLikelyCorsOrTransport(failure)) {
        return if (browserOnline) {
            "Could not reach the server (request refused or timed out)."
        } else {
            "The browser reports no connectivity."
        }
    }
    return failure.message ?: "Could not reach the server."
}

/**
 * Sign-in-stage error mapping, invalid-credentials vs unreachable kept
 * distinct: Jellyfin answers wrong credentials with HTTP 401, which the
 * client surfaces as an access-denied ApiException; transport failures ride
 * the client's classified retryable messages ("Connection timed out…", etc.).
 */
internal fun friendlySignInFailure(failure: Throwable): String {
    val message = failure.message
    return when {
        failure is ApiException && failure.httpCode == 401 -> "Incorrect username or password."
        message != null -> message
        else -> "Sign-in failed."
    }
}
