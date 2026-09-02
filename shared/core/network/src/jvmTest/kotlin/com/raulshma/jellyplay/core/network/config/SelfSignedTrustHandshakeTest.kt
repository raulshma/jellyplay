package com.raulshma.jellyplay.core.network.config

import com.raulshma.jellyplay.core.model.NetworkTimeoutPreset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate

/**
 * REAL handshake integration tests for the self-signed trust layer: a
 * MockWebServer answering over TLS with a self-signed certificate, and an
 * OkHttpClient built with [applySelfSignedTrust] whose granted set is backed
 * by a [MutableStateFlow] — standing in for the Eagerly-shared
 * `OkHttpConfigProvider.config` StateFlow the production wiring reads at
 * handshake time.
 *
 * Asserts the four contract points:
 *  (a) ungranted → SSLHandshakeException;
 *  (b) host granted via the StateFlow → request succeeds;
 *  (c) revoke (update the StateFlow) + evictAll → fails again — including the
 *      honest caveat that a POOLED (or TLS-session-resumed) connection stays
 *      trusted, so the strict refusal is asserted on a fresh client (fresh
 *      SSLContext = no resumable session);
 *  (d) a different https endpoint is never trusted by another's grant.
 */
class SelfSignedTrustHandshakeTest {

    private lateinit var serverA: MockWebServer
    private lateinit var serverB: MockWebServer
    private lateinit var client: OkHttpClient
    private val grantedConfig = MutableStateFlow(defaultConfig())

    private fun defaultConfig() = OkHttpConfig(
        maxCacheSizeMb = 0,
        networkTimeoutPreset = NetworkTimeoutPreset.DEFAULT,
        verboseNetworkLogging = false,
        selfSignedTrustHosts = emptySet(),
    )

    @BeforeTest
    fun setUp() {
        val localhostCert = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(localhostCert)
            .build()

        // Bound explicitly to the IPv4 loopback: on dual-stack dev machines
        // "localhost" resolves to [::1] + 127.0.0.1 and OkHttp's route
        // fallback turns a single TLS failure into a confusing
        // ConnectException for the OTHER route.
        serverA = MockWebServer()
        serverA.useHttps(serverCertificates.sslContext().socketFactory, false)
        serverA.start(java.net.InetAddress.getByName("127.0.0.1"), 0)

        // A DIFFERENT self-signed identity so cross-endpoint trust leaks are
        // distinguishable, not just port mismatches.
        val otherCert = HeldCertificate.Builder()
            .commonName("other.local")
            .addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1")
            .build()
        serverB = MockWebServer()
        serverB.useHttps(
            HandshakeCertificates.Builder().heldCertificate(otherCert).build().sslContext().socketFactory,
            false,
        )
        serverB.start(java.net.InetAddress.getByName("127.0.0.1"), 0)

        client = OkHttpClient.Builder()
            .applySelfSignedTrust { grantedConfig.value.selfSignedTrustHosts }
            .build()
    }

    @AfterTest
    fun tearDown() {
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
        serverA.shutdown()
        serverB.shutdown()
    }

    private fun canonicalAddress(server: MockWebServer): String =
        "https://127.0.0.1:${server.port}"

    /**
     * The request URL with the host pinned to the IPv4 literal the server is
     * bound to. MockWebServer's own `url()` builds from `hostName`, which for
     * an InetAddress-bound server REVERSE-RESOLVES: "127.0.0.1" on Windows,
     * but "localhost" on Linux/macOS — and OkHttp then races dual-stack
     * localhost resolution instead of dialing the bound address, surfacing
     * `ConnectException: Failed to connect to localhost/[::1]:port` (the
     * deterministic CI failure that hid the trust assertions behind a
     * transport error). Grants ([canonicalAddress]) pin the same literal so
     * the handshake peer ("127.0.0.1") matches the entry.
     */
    private fun v4Url(server: MockWebServer, path: String = "/System/Info/Public"): okhttp3.HttpUrl =
        server.url(path).newBuilder().host("127.0.0.1").build()

    private fun get(server: MockWebServer, path: String = "/System/Info/Public") =
        client.newCall(Request.Builder().url(v4Url(server, path)).build()).execute()

    // ----------------------------------------------------------------- (a)

    @Test
    fun `ungranted self-signed server fails the handshake`() {
        grantedConfig.value = defaultConfig()

        val error = runCatching { get(serverA).use {} }.exceptionOrNull()

        assertTrue(
            isTlsTrustFailure(error),
            "expected an SSL trust failure, got $error",
        )
    }

    // ----------------------------------------------------------------- (b)

    @Test
    fun `granting the host through the state flow makes the request succeed`() {
        serverA.enqueue(MockResponse().setBody("""{"Id":"abc","ServerName":"Test"}"""))
        grantedConfig.value = defaultConfig().copy(
            selfSignedTrustHosts = setOf(canonicalAddress(serverA)),
        )

        get(serverA).use { response ->
            assertEquals(200, response.code)
        }
        // The grant ALSO covers the same host on other ports (documented
        // portless-entry semantics are the mirror image: this entry pins the
        // port; the portless variant is covered by the matcher unit tests).
    }

    @Test
    fun `portless grant for the host covers the server's non-default port`() {
        serverA.enqueue(MockResponse().setBody("ok"))
        grantedConfig.value = defaultConfig().copy(
            selfSignedTrustHosts = setOf("https://127.0.0.1"),
        )

        get(serverA).use { response ->
            assertEquals(200, response.code)
        }
    }

    // ----------------------------------------------------------------- (c)

    @Test
    fun `revocation bites once the pooled connection is gone`() {
        serverA.enqueue(MockResponse().setBody("one"))
        grantedConfig.value = defaultConfig().copy(
            selfSignedTrustHosts = setOf(canonicalAddress(serverA)),
        )
        get(serverA).use { response ->
            assertEquals(200, response.code)
            response.body.string() // fully drain so the connection returns to the pool
        }

        // Revoke via the same live StateFlow the production provider exposes.
        grantedConfig.value = defaultConfig()

        // Documented limitation made observable: a POOLED (or TLS-session-
        // resumed — the JVM's session cache may skip re-presenting the
        // certificate) connection opened while the grant was live keeps
        // working...
        serverA.enqueue(MockResponse().setBody("two"))
        get(serverA).use { response ->
            assertEquals(200, response.code, "pooled/resumed connection stays trusted until evicted")
            response.body.string()
        }

        // ...and a NEW handshake is refused again. The fresh client carries a
        // fresh SSLContext, so no cached TLS session can resume past the
        // trust manager — this is the assertion that proves the revoke was
        // read at handshake time.
        client.connectionPool.evictAll()
        val freshClient = OkHttpClient.Builder()
            .applySelfSignedTrust { grantedConfig.value.selfSignedTrustHosts }
            .build()
        try {
            val error = runCatching {
                freshClient.newCall(Request.Builder().url(v4Url(serverA, "/x")).build()).execute().use {}
            }.exceptionOrNull()
            assertTrue(
                isTlsTrustFailure(error),
                "expected an SSL trust failure after revoke on a fresh client, got $error",
            )
        } finally {
            freshClient.connectionPool.evictAll()
            freshClient.dispatcher.executorService.shutdown()
        }
    }

    // ----------------------------------------------------------------- (d)

    @Test
    fun `another https endpoint is never trusted by a foreign grant`() {
        grantedConfig.value = defaultConfig().copy(
            selfSignedTrustHosts = setOf(canonicalAddress(serverA)),
        )

        val error = runCatching { get(serverB).use {} }.exceptionOrNull()

        assertTrue(
            isTlsTrustFailure(error),
            "a grant for one endpoint must not trust a different one, got $error",
        )
    }

    @Test
    fun `router-shaped probe against a granted self-signed server is reachable`() {
        // The router's probe client is the reason the trust layer had to be
        // installed there too: `connectToServer` classifies servers through
        // it, and an untrusted handshake would read "unreachable", hiding the
        // grant dialog. Simulate the probe path against the same TLS server.
        val provider = object : OkHttpConfigProvider {
            override val config = grantedConfig
        }
        val probeClient = OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(2))
            .readTimeout(java.time.Duration.ofSeconds(3))
            .callTimeout(java.time.Duration.ofSeconds(5))
            .applySelfSignedTrust(selfSignedTrustHostsReader(provider))
            .build()
        try {
            serverA.enqueue(MockResponse().setBody("""{"Id":"abc","ServerName":"Test"}"""))
            grantedConfig.value = defaultConfig().copy(
                selfSignedTrustHosts = setOf(canonicalAddress(serverA)),
            )
            probeClient.newCall(
                Request.Builder().url(v4Url(serverA)).build(),
            ).execute().use { response ->
                assertEquals(200, response.code)
            }
        } finally {
            probeClient.connectionPool.evictAll()
            probeClient.dispatcher.executorService.shutdown()
        }
    }
}
