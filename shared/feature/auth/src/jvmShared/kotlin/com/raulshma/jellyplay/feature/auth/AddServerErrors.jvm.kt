package com.raulshma.jellyplay.feature.auth

import com.raulshma.jellyplay.core.model.normalizeServerAddress
import com.raulshma.jellyplay.feature.auth.generated.resources.Res
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_cleartext
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_could_not_connect
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_connection_failed
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_connection_timeout
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_local_network_denied
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_resolve_address
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_ssl

/**
 * JVM/android actuals of the add-server failure classification (moved
 * verbatim out of AddServerViewModel.kt's commonMain — the javax/java.net
 * types have no web counterpart), so android + desktop behavior is
 * byte-identical — pinned by jvmTest.
 */
internal actual fun tlsTrustPromptFor(address: String, throwable: Throwable): String? {
    val normalized = normalizeServerAddress(address)
    if (!normalized.startsWith("https://")) return null
    // SSLHandshakeException / SSLPeerUnverifiedException are both SSLException
    // subclasses. The network probe wraps TLS-trust failures in a plain
    // RuntimeException marker (non-retryable, review round) —
    // getRootCause walks past the wrapper to the underlying SSLException.
    return if (getRootCause(throwable) is javax.net.ssl.SSLException) normalized else null
}

internal actual fun getConnectionErrorMessage(
    address: String,
    throwable: Throwable,
    localNetworkStatus: LocalNetworkStatus,
): AuthMessage {
    val root = getRootCause(throwable)
    // Android 17+: when local network access is denied, attempts to reach a
    // LAN host fail as a timeout / connect error / unresolved host. Surface a
    // single actionable message instead of a cryptic generic failure, but only
    // when the target is actually local (public hosts are unaffected by the
    // permission, so blaming it there would be misleading).
    if (localNetworkStatus.blamesFailureOnPermission(address) &&
        (root is java.net.UnknownHostException ||
            root is java.net.ConnectException ||
            root is java.net.SocketTimeoutException)
    ) {
        return AuthMessage.Resource(Res.string.auth_error_local_network_denied)
    }
    return when {
        root is java.net.UnknownHostException -> AuthMessage.Resource(Res.string.auth_error_resolve_address)
        root is java.net.ConnectException -> AuthMessage.Resource(Res.string.auth_error_could_not_connect)
        root is java.net.SocketTimeoutException -> AuthMessage.Resource(Res.string.auth_error_connection_timeout)
        root is javax.net.ssl.SSLException -> AuthMessage.Resource(Res.string.auth_error_ssl)
        root.message?.contains("cleartext", ignoreCase = true) == true ->
            AuthMessage.Resource(Res.string.auth_error_cleartext)
        root.message?.contains("ssl", ignoreCase = true) == true ->
            AuthMessage.Resource(Res.string.auth_error_ssl)
        else -> root.message?.takeIf {
            it.isNotBlank() && !it.startsWith("org.") && it.length < 100
        }?.let { AuthMessage.Raw(it) } ?: AuthMessage.Resource(Res.string.auth_error_connection_failed)
    }
}
