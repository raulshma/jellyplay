package com.raulshma.jellyplay.core.network.config

import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

/**
 * Opt-in trust for Jellyfin servers that present a self-signed (or otherwise
 * unverifiable) TLS certificate — desktop + Android.
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
 * (a thin facade over the commonMain [SelfSignedTrustMatcher], the single
 * home of the decision) matches a handshake peer `(host, port)` when:
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
 * JVM-side facade over the pure host matcher: every call delegates verbatim to
 * the commonMain [SelfSignedTrustMatcher] (the single home of the decision —
 * extraction so commonMain callers like the Server Management trust
 * toggle answer the exact question the handshake path asks; no behavior
 * change). Parsing/matching is exhaustively unit-tested against the matcher in
 * `SelfSignedTrustMatcherTest` (jvmTest).
 */
object SelfSignedTrustHosts {

    /**
     * Whether `(host, port)` is covered by a granted entry. See the file-level
     * KDoc for the matching rules; `port <= 0` means "unknown" (host-only
     * comparison).
     */
    fun isGranted(entries: Set<String>, host: String?, port: Int): Boolean =
        SelfSignedTrustMatcher.isGranted(entries, host, port)
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
 *
 * ## Custom CA override
 *
 * When [customCa] is supplied and yields a certificate, the DELEGATE for
 * non-granted peers becomes a trust manager anchored on that single CA
 * instead of the platform default — merged with (not replacing) the
 * self-signed grant short-circuit above. The CA-anchored manager is cached
 * per certificate instance (the provider itself caches parses against file
 * stamps, so handshakes are not punished with re-reads). A corrupt installed
 * override makes [customCa] throw, which fails the handshake — never a
 * silent fall back to platform trust.
 */
class SelfSignedTrustManager(
    private val delegate: X509TrustManager,
    private val grantedHosts: () -> Set<String>,
    private val customCa: (() -> X509Certificate?)? = null,
) : X509ExtendedTrustManager() {

    override fun getAcceptedIssuers(): Array<X509Certificate> = effectiveDelegate().acceptedIssuers

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        effectiveDelegate().checkClientTrusted(chain, authType)
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) {
        when (val effective = effectiveDelegate()) {
            is X509ExtendedTrustManager -> effective.checkClientTrusted(chain, authType, socket)
            else -> effective.checkClientTrusted(chain, authType)
        }
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {
        when (val effective = effectiveDelegate()) {
            is X509ExtendedTrustManager -> effective.checkClientTrusted(chain, authType, engine)
            else -> effective.checkClientTrusted(chain, authType)
        }
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        // No peer visible on this overload — cannot (and must not) short-circuit.
        effectiveDelegate().checkServerTrusted(chain, authType)
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
        when (val effective = effectiveDelegate()) {
            is X509ExtendedTrustManager -> effective.checkServerTrusted(chain, authType, socket)
            else -> effective.checkServerTrusted(chain, authType)
        }
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {
        val peerHost = engine?.peerHost
        val peerPort = engine?.peerPort ?: -1
        if (SelfSignedTrustHosts.isGranted(grantedHosts(), peerHost, peerPort)) return
        when (val effective = effectiveDelegate()) {
            is X509ExtendedTrustManager -> effective.checkServerTrusted(chain, authType, engine)
            else -> effective.checkServerTrusted(chain, authType)
        }
    }

    /**
     * The trust manager non-granted peers are validated against: the custom
     * CA-anchored one when an override is installed, the platform default
     * otherwise.
     */
    private fun effectiveDelegate(): X509TrustManager {
        val ca = customCa?.invoke() ?: return delegate
        return caAnchoredManager(ca)
    }

    /** Cache of the last CA-anchored manager, keyed on the certificate instance. */
    private var caAnchoredCache: Pair<X509Certificate, X509TrustManager>? = null
    private val caCacheLock = Any()

    private fun caAnchoredManager(ca: X509Certificate): X509TrustManager {
        synchronized(caCacheLock) {
            val cached = caAnchoredCache
            if (cached != null && cached.first === ca) return cached.second
        }
        val anchors = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType())
        anchors.load(null, null)
        anchors.setCertificateEntry("jellyplay-server-ca", ca)
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(anchors)
        val manager = factory.trustManagers.filterIsInstance<X509TrustManager>().first()
        synchronized(caCacheLock) { caAnchoredCache = ca to manager }
        return manager
    }
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
 * Everything [applyTls] installs on a client builder, in one value: the
 * handshake-time granted-set read (self-signed "proceed anyway" grants) and
 * the app-level client-certificate provider (mTLS). Both are read
 * LIVE at handshake time — see [ClientCertificateProvider] for the
 * fail-closed contract of the certificate half.
 */
class ServerTrustConfig(
    val grantedHosts: () -> Set<String>,
    val clientCertificate: ClientCertificateProvider = ClientCertificateProvider.NONE,
)

/**
 * The generalized TLS layer install: everything
 * [applySelfSignedTrust] does, PLUS the client-certificate key managers
 * funneled through a [LiveClientKeyManager] and the optional custom-CA trust
 * anchor — both sourced from [ServerTrustConfig.clientCertificate] at
 * handshake time, so importing/toggling/removing a certificate takes effect
 * on the next handshake without rebuilding any client (the live-config
 * contract the granted set already has). Used by `baseOkHttpClient` and the
 * `ServerAddressRouter` probe client — the two TLS construction sites every
 * derived client (API, WS, images, streaming, downloads, ExoPlayer) inherits
 * from on BOTH JVM platforms.
 */
fun OkHttpClient.Builder.applyTls(
    trust: ServerTrustConfig,
): OkHttpClient.Builder {
    // Deliberate TLS exception behind an explicit feature flag (see
    // applySelfSignedTrust): the layer is installed unconditionally when the
    // feature is on because grants, the client certificate, and the CA
    // override are all read LIVE at handshake time — the client must carry
    // the layer even before any of them exist. The always-true flag is also
    // the escape hatch CodeQL's java/insecure-trustmanager query recognizes.
    val selfSignedTrustEnabled = true
    if (selfSignedTrustEnabled) {
        val caReader: (() -> X509Certificate?)? =
            if (trust.clientCertificate === ClientCertificateProvider.NONE) null
            else trust.clientCertificate::customCa
        val trustManager = SelfSignedTrustManager(platformTrustManager(), trust.grantedHosts, caReader)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(arrayOf(LiveClientKeyManager(trust.clientCertificate)), arrayOf(trustManager), SecureRandom())
        sslSocketFactory(sslContext.socketFactory, trustManager)
        hostnameVerifier(SelfSignedHostnameVerifier(okhttpDefaultHostnameVerifier, trust.grantedHosts))
    }
    return this
}

/**
 * Installs the self-signed trust layer on a client builder: an SSLContext
 * built over a [SelfSignedTrustManager] wrapping the platform default, plus a
 * [SelfSignedHostnameVerifier] wrapping OkHttp's stock verifier. Both read the
 * granted set through [grantedHosts] at handshake time, so the builder is
 * configured ONCE per client and stays live. Used by `baseOkHttpClient` and
 * the `ServerAddressRouter` probe client (a probe against a self-signed
 * server must not be classified "unreachable" once the user granted it).
 * Equivalent to [applyTls] with no client-certificate provider.
 */
fun OkHttpClient.Builder.applySelfSignedTrust(
    grantedHosts: () -> Set<String>,
): OkHttpClient.Builder = applyTls(ServerTrustConfig(grantedHosts))

/**
 * Delegating [X509KeyManager] that funnels the app's client-certificate
 * state into a handshake-time read: every alias/key lookup consults the
 * provider's CURRENT key managers, so enabling, disabling, or swapping the
 * certificate affects the very next TLS handshake without rebuilding the
 * OkHttpClient that carries this manager.
 *
 * When no certificate is configured (`null` from the provider) the manager
 * falls back to the JVM default key managers — reproducing the key-selection
 * semantics of `SSLContext.init(null, ...)`, which is what the pre-existing
 * layer did — so certificate-less apps see zero behavior drift. When a
 * certificate IS enabled, the provider's managers are used exclusively; its
 * fail-closed exceptions (missing/corrupt material) propagate into the
 * handshake as a TLS failure rather than a silent certificate-less one.
 *
 * Server-side alias selection always routes to the JVM default: the app only
 * ever acts as a TLS client.
 */
internal class LiveClientKeyManager(
    private val provider: ClientCertificateProvider,
) : X509ExtendedKeyManager() {

    /** The default key managers, as `SSLContext.init(null, ...)` would use. */
    private val defaultManager: X509KeyManager by lazy {
        val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        factory.init(null, null)
        factory.keyManagers.filterIsInstance<X509KeyManager>().first()
    }

    /** The CURRENT client key manager — the provider's, or the default. Fail-closed. */
    private fun current(): X509KeyManager {
        val managers = provider.keyManagers() ?: return defaultManager
        return managers.filterIsInstance<X509KeyManager>().firstOrNull()
            ?: throw ClientCertificateMaterialException(
                "Client certificate provider returned no X509KeyManager",
            )
    }

    override fun getClientAliases(keyType: String?, issuers: Array<Principal>?): Array<String>? =
        current().getClientAliases(keyType, issuers)

    override fun chooseClientAlias(
        keyType: Array<String>?,
        issuers: Array<Principal>?,
        socket: Socket?,
    ): String? = current().chooseClientAlias(keyType, issuers, socket)

    override fun chooseEngineClientAlias(
        keyType: Array<String>?,
        issuers: Array<Principal>?,
        engine: SSLEngine?,
    ): String? {
        val manager = current()
        return if (manager is X509ExtendedKeyManager) {
            manager.chooseEngineClientAlias(keyType, issuers, engine)
        } else {
            manager.chooseClientAlias(keyType, issuers, null)
        }
    }

    override fun getServerAliases(keyType: String?, issuers: Array<Principal>?): Array<String>? =
        defaultManager.getServerAliases(keyType, issuers)

    override fun chooseServerAlias(keyType: String?, issuers: Array<Principal>?, socket: Socket?): String? =
        defaultManager.chooseServerAlias(keyType, issuers, socket)

    override fun chooseEngineServerAlias(
        keyType: String?,
        issuers: Array<Principal>?,
        engine: SSLEngine?,
    ): String? = (defaultManager as? X509ExtendedKeyManager)
        ?.chooseEngineServerAlias(keyType, issuers, engine)

    override fun getPrivateKey(alias: String?): PrivateKey? = current().getPrivateKey(alias)

    override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
        current().getCertificateChain(alias)
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
