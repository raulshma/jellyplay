package com.raulshma.jellyplay.feature.auth

import com.raulshma.jellyplay.feature.auth.generated.resources.Res
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_cleartext
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_connection_failed
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_connection_timeout
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_could_not_connect
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_local_network_denied
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_ssl
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.io.IOException

/**
 * wasmJs actuals of the add-server failure classification, modeled on the
 * web slice's taxonomy ([apps/web WebConnectFailurePolicy.isLikelyCorsOrTransport]
 * plus core/network's `NetworkErrorClassifier.wasmJs.kt`): on the fetch-backed
 * Js engine there are no `java.net`/`javax.net.ssl` types — transport
 * failures arrive as ktor-io [IOException] subclasses (the engine wraps DNS,
 * refused connections, TLS and network changes into them), the HttpTimeout
 * plugin fires [HttpRequestTimeoutException], and engines whose wrapping
 * differs still surface the raw browser rejection strings ("Failed to fetch"
 * on Chromium, "NetworkError" on Firefox, "Load failed" on WebKit).
 */

/**
 * Web actual: never prompts. Browsers refuse untrusted certificates at the
 * fetch layer with opaque transport errors — indistinguishable from CORS
 * refusals — and name no certificate-exception type a klib could key on;
 * a message-content sniff would be far too loose for a *security consent
 * dialog* whose JVM counterpart requires a typed root-cause SSLException.
 * A grant could not be honored anyway: the browser itself rejects the
 * handshake before ktor sees the failure. The unmappable case returns null,
 * exactly like the JVM actual for non-TLS/non-https failures.
 */
internal actual fun tlsTrustPromptFor(address: String, throwable: Throwable): String? = null

internal actual fun getConnectionErrorMessage(
    address: String,
    throwable: Throwable,
    localNetworkStatus: LocalNetworkStatus,
): AuthMessage {
    val root = getRootCause(throwable)
    // Typed transport signals, checked on the ROOT cause like the JVM
    // classifier. HttpRequestTimeoutException (the SocketTimeoutException
    // analog) first: on ktor 3.5.2 it already extends IOException, so the
    // ordering is what keeps timeout distinct from refused.
    val isTypedTransport = root is HttpRequestTimeoutException || root is IOException
    // Message fragments walked down the chain — WebConnectFailurePolicy's
    // rationale verbatim: survive an engine whose wrapping differs from
    // ktor's IOException assumption (CORS blocks surface here too).
    var refusalMessage = false
    var cause: Throwable? = root
    while (cause != null && !refusalMessage) {
        val message = cause.message?.lowercase() ?: ""
        refusalMessage =
            "failed to fetch" in message || "networkerror" in message || "load failed" in message
        cause = cause.cause
    }
    // Local-network blame branch, restricted to the transport-refusal shape —
    // the wasm analog of the JVM actual's typed-exception restriction. No web
    // platform enforces the permission today (a registered status never
    // blames), but the seam keeps its contract.
    if ((isTypedTransport || refusalMessage) && localNetworkStatus.blamesFailureOnPermission(address)) {
        return AuthMessage.Resource(Res.string.auth_error_local_network_denied)
    }
    return when {
        root is HttpRequestTimeoutException -> AuthMessage.Resource(Res.string.auth_error_connection_timeout)
        // DNS failures are NOT distinctly mappable on web (they arrive inside
        // the same opaque fetch refusal), so the JVM's dedicated
        // auth_error_resolve_address line is unreachable there — refused and
        // unresolvable both read "could not connect".
        root is IOException -> AuthMessage.Resource(Res.string.auth_error_could_not_connect)
        refusalMessage -> AuthMessage.Resource(Res.string.auth_error_could_not_connect)
        root.message?.contains("cleartext", ignoreCase = true) == true ->
            AuthMessage.Resource(Res.string.auth_error_cleartext)
        root.message?.contains("ssl", ignoreCase = true) == true ->
            AuthMessage.Resource(Res.string.auth_error_ssl)
        else -> root.message?.takeIf {
            it.isNotBlank() && !it.startsWith("org.") && it.length < 100
        }?.let { AuthMessage.Raw(it) } ?: AuthMessage.Resource(Res.string.auth_error_connection_failed)
    }
}
