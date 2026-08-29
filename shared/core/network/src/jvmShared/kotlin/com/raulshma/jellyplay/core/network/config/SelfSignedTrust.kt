package com.raulshma.jellyplay.core.network.config

import java.net.Socket
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

/**
 * Opt-in trust for Jellyfin servers that present a self-signed (or otherwise
 * unverifiable) TLS certificate — desktop + Android. Wasm is deliberately out
 * of scope: the browser owns certificate decisions there.
 *
 * ## Shape
 *
 * The granted set lives in [OkHttpConfig.selfSignedTrustHosts] (fed by the
 * network DataStore preference) and is read via the supplied
 * `() -> Set<String>` **at handshake time** — the same live-config contract as
 * the per-request timeout/logging interceptor in `baseOkHttpClient`. That is
 * why [OkHttpClient] instances never need rebuilding: a grant made in the
 * Add Server dialog is honored by the very next TLS handshake, and a revoke
 * bites on the next handshake a connection is actually established for.
 *
 * ## Matching
 *
 * Entries are canonical `scheme://host[:port]` strings (the
 * `ServerAddressRouter.addressString` form). [SelfSignedTrustHosts.isGranted]
 * matches a handshake peer `(host, port)` when:
 *  - the entry's host equals the peer host, case-insensitively (IPv6 literals
 *    compare bracket-stripped); and
 *  - the entry carries an explicit port → it must equal the peer port; an
 *    entry without a port (the `https://host` form, i.e. port 443 implied)
 *    matches the host on ANY port — a deliberate, documented trade: the user
 *    trusted the *host*, so alternate ports of the same server (the
 *    Server Management "add address" flow) share the grant instead of each
 *    re-prompting. Trust never crosses to a different host.
 *  - when the peer port is unknown (`<= 0` — never happens on the trust
 *    manager path, tolerated for the hostname-verifier fallback), only the
 *    host is compared. This cannot widen trust: the trust manager has already
 *    gated the same handshake with the real port.
 *
 * ## Honesty notes (limitations that are accepted, not bugs)
 *
 *  - **Revocation does not evict pooled connections.** OkHttp keeps idle TLS
 *    connections in its `ConnectionPool` (base client: 15 min idle). A
 *    connection opened while a grant was live stays trusted until it idles
 *    out, the process restarts, or something calls
 *    `client.connectionPool.evictAll()`. The settings toggle and the DataStore
 *    writes only stop NEW handshakes from trusting the host.
 *  - **Revocation also does not purge the JVM's TLS session cache.** A
 *    connection may be re-established by TLS session resumption, and a
 *    resumed handshake can skip re-presenting the server certificate — in
 *    which case no trust manager runs at all. Same blast radius as the pooled
 *    connection above (bounded by the session cache lifetime), and the same
 *    mitigation: new handshakes after the cache expires are gated again.
 *  - A grant accepts ANY certificate the granted host:port presents — the
 *    delegate (platform default) trust manager and hostname verifier are
 *    skipped for that peer. This is the standard "proceed anyway" trade; the
 *    grant is host-scoped and user-visible (Settings → Server Management).
 */

/**
 * Pure matcher between granted trust entries and a handshake peer. No JVM
 * network types — exhaustively unit-tested in
 * `SelfSignedTrustHostsTest` (jvmTest).
 */
object SelfSignedTrustHosts {

    /** Canonical pieces of one granted entry. */
    internal data class ParsedEntry(val host: String, val port: Int?)

    /**
     * Whether `(host, port)` is covered by a granted entry. See the file-level
     * KDoc for the matching rules; `port <= 0` means "unknown" (host-only
     * comparison).
     */
    fun isGranted(entries: Set<String>, host: String?, port: Int): Boolean {
        if (host.isNullOrBlank() || entries.isEmpty()) return false
        val peerHost = normalizeHost(host) ?: return false
        return entries.any { entry ->
            val parsed = parseEntry(entry) ?: return@any false
            parsed.host == peerHost && (parsed.port == null || port <= 0 || parsed.port == port)
        }
    }

    /**
     * Parses `scheme://host[:port]` tolerantly: scheme optional (missing
     * scheme is treated as a bare authority), userinfo/path/query/fragment and
     * trailing slashes stripped, host lowercased, IPv6 brackets stripped.
     * Returns null for anything unparseable — an unparseable entry is never
     * granted (fail closed).
     */
    internal fun parseEntry(entry: String): ParsedEntry? {
        val raw = entry.trim()
        if (raw.isEmpty()) return null
        val afterScheme = if (raw.contains("://")) raw.substringAfter("://") else raw
        val authority = afterScheme
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        val hostPort = authority.substringAfterLast('@')
        if (hostPort.isNotEmpty()) {
            // Bracketed IPv6: [::1] or [::1]:8920
            if (hostPort.startsWith("[")) {
                val close = hostPort.indexOf(']')
                if (close < 0) return null
                val host = hostPort.substring(1, close).lowercase()
                if (host.isEmpty()) return null
                val portPart = hostPort.substring(close + 1)
                if (portPart.isEmpty()) return ParsedEntry(host, null)
                val port = portPart.removePrefix(":").toIntOrNull() ?: return null
                return if (port in 1..65535) ParsedEntry(host, port) else null
            }
            if (hostPort.count { it == ':' } == 1) {
                val host = hostPort.substringBefore(':').lowercase()
                if (host.isEmpty()) return null
                val port = hostPort.substringAfter(':').toIntOrNull() ?: return null
                return if (port in 1..65535) ParsedEntry(host, port) else null
            }
            if (hostPort.count { it == ':' } == 0) {
                return ParsedEntry(hostPort.lowercase(), null)
            }
            // Unbracketed IPv6 or garbage — fail closed.
            return null
        }
        return null
    }

    /** Lowercases + bracket-strips a peer host; null when blank. */
    internal fun normalizeHost(host: String): String? {
        val trimmed = host.trim().lowercase()
        if (trimmed.isEmpty()) return null
        return if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length > 2) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
    }
}

/**
 * [X509ExtendedTrustManager] wrapping the platform default. A plain
 * `X509TrustManager` cannot see WHO it is talking to — the `Socket` /
 * `SSLEngine` overloads of `checkServerTrusted` are the only seam where the
 * peer host:port is visible at decision time, so exactly those overloads
 * short-circuit (accept any certificate) when the peer matches a granted
 * entry. Everything else — client-auth checks, all non-granted peers —
 * delegates untouched, so default platform trust behavior (including the
 * Android network security config, which the default factory incorporates)
 * is preserved byte-for-byte.
 */
class SelfSignedTrustManager(
    private val delegate: X509TrustManager,
    private val grantedHosts: () -> Set<String>,
) : X509ExtendedTrustManager() {

    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        delegate.checkClientTrusted(chain, authType)
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) {
        when (delegate) {
            is X509ExtendedTrustManager -> delegate.checkClientTrusted(chain, authType, socket)
            else -> delegate.checkClientTrusted(chain, authType)
        }
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {
        when (delegate) {
            is X509ExtendedTrustManager -> delegate.checkClientTrusted(chain, authType, engine)
            else -> delegate.checkClientTrusted(chain, authType)
        }
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        // No peer visible on this overload — cannot (and must not) short-circuit.
        delegate.checkServerTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) {
        // SSLSocket (unlike SSLEngine) has no direct peer accessor — the
        // handshake-time session carries the (host, port) OkHttp connected
        // the socket with. Null when the socket isn't mid-handshake / isn't
        // SSL: no peer visible → delegate (never short-circuit blind).
        val handshakeSession = (socket as? SSLSocket)?.handshakeSession
        if (handshakeSession != null &&
            SelfSignedTrustHosts.isGranted(grantedHosts(), handshakeSession.peerHost, handshakeSession.peerPort)
        ) {
            return
        }
        when (delegate) {
            is X509ExtendedTrustManager -> delegate.checkServerTrusted(chain, authType, socket)
            else -> delegate.checkServerTrusted(chain, authType)
        }
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {
        val peerHost = engine?.peerHost
        val peerPort = engine?.peerPort ?: -1
        if (SelfSignedTrustHosts.isGranted(grantedHosts(), peerHost, peerPort)) return
        when (delegate) {
            is X509ExtendedTrustManager -> delegate.checkServerTrusted(chain, authType, engine)
            else -> delegate.checkServerTrusted(chain, authType)
        }
    }

    private fun isPeerGranted(peerHost: String?, peerPort: Int): Boolean =
        SelfSignedTrustHosts.isGranted(grantedHosts(), peerHost, peerPort)
}

/**
 * [HostnameVerifier] wrapping OkHttp's stock verifier. A granted host skips
 * name verification entirely (self-signed certs rarely carry a matching SAN);
 * everything else delegates. The delegate is read off a default-built
 * [OkHttpClient]'s public `hostnameVerifier` property — that instance IS
 * OkHttp's internal `OkHostnameVerifier`, obtained without importing the
 * `okhttp3.internal.tls` package (Kotlin `internal` visibility forbids that
 * import cross-module); it guarantees zero behavior drift for non-granted
 * hosts, unlike `HttpsURLConnection.getDefaultHostnameVerifier()` whose JDK
 * verifier accepts a subtly different SAN set than OkHttp's.
 */
class SelfSignedHostnameVerifier(
    private val delegate: HostnameVerifier,
    private val grantedHosts: () -> Set<String>,
) : HostnameVerifier {
    override fun verify(host: String, session: javax.net.ssl.SSLSession): Boolean {
        val port = runCatching { session.peerPort }.getOrDefault(-1)
        if (SelfSignedTrustHosts.isGranted(grantedHosts(), host, port)) return true
        return delegate.verify(host, session)
    }
}

/** The stock OkHttp hostname verifier instance (see [SelfSignedHostnameVerifier] KDoc). */
private val okhttpDefaultHostnameVerifier: HostnameVerifier by lazy {
    OkHttpClient().hostnameVerifier
}

/** The platform-default trust manager, exactly what an unconfigured OkHttp uses. */
internal fun platformTrustManager(): X509TrustManager {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(null as java.security.KeyStore?)
    return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
}

/**
 * Installs the self-signed trust layer on a client builder: an SSLContext
 * built over a [SelfSignedTrustManager] wrapping the platform default, plus a
 * [SelfSignedHostnameVerifier] wrapping OkHttp's stock verifier. Both read the
 * granted set through [grantedHosts] at handshake time, so the builder is
 * configured ONCE per client and stays live. Used by `baseOkHttpClient` and
 * the `ServerAddressRouter` probe client (a probe against a self-signed
 * server must not be classified "unreachable" once the user granted it).
 */
fun OkHttpClient.Builder.applySelfSignedTrust(
    grantedHosts: () -> Set<String>,
): OkHttpClient.Builder {
    val trustManager = SelfSignedTrustManager(platformTrustManager(), grantedHosts)
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, arrayOf(trustManager), SecureRandom())
    sslSocketFactory(sslContext.socketFactory, trustManager)
    hostnameVerifier(SelfSignedHostnameVerifier(okhttpDefaultHostnameVerifier, grantedHosts))
    return this
}

/** Adapts a config provider into the handshake-time granted-set read. */
fun selfSignedTrustHostsReader(provider: OkHttpConfigProvider): () -> Set<String> =
    { provider.config.value.selfSignedTrustHosts }

/**
 * Whether a failure chain is a TLS-trust failure (unknown/untrusted
 * certificate, failed hostname verification): walks to the root cause and
 * checks for [SSLException] — `SSLHandshakeException` and
 * `SSLPeerUnverifiedException` are its subclasses. Shared by the
 * `ApiException` classifier (jvmShared) and the Add Server grant dialog.
 * Deliberately narrow: a generic `IOException` that merely mentions "ssl" in
 * its message does not qualify (a TLS-trust dialog must never fire on a
 * transport failure).
 */
fun isTlsTrustFailure(throwable: Throwable?): Boolean {
    var cause = throwable ?: return false
    while (cause.cause != null && cause.cause !== cause) {
        if (cause is SSLException || cause is CertificateException) return true
        cause = cause.cause!!
    }
    return cause is SSLException || cause is CertificateException
}
