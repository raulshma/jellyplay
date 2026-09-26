package com.raulshma.jellyplay.core.network.config

import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.nio.file.Files
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate

/**
 * REAL handshake integration tests for the `applyTls` layer: a
 * MockWebServer over TLS that REQUIRES client authentication (needClientAuth
 * — wired by wrapping the server SSLContext's socket factory), backed by the
 * [ClientCertificateManager] the production Koin graph binds.
 *
 * Asserts the four contract points:
 *  (a) with the provider's key managers (an imported, enabled certificate)
 *      the mTLS handshake succeeds — through the SAME live-read wrapper the
 *      base client installs;
 *  (b) without a certificate (the legacy applySelfSignedTrust shape) the
 *      server that requires client auth refuses the handshake;
 *  (c) fail-closed: an ENABLED certificate whose material is broken fails
 *      the handshake as a TLS failure — never a silent certificate-less
 *      connection that some server might accept;
 *  (d) the custom CA override anchors server trust: a server presenting a
 *      certificate signed by the override CA is trusted with the override
 *      installed and refused without it (no platform-trust fallback).
 */
class SelfSignedTrustClientAuthTest {

    private lateinit var certsDir: File
    private lateinit var storage: RecordingSecureStorage
    private lateinit var manager: ClientCertificateManager
    private lateinit var requireCertServer: MockWebServer
    private lateinit var caSignedServer: MockWebServer

    private val grantedConfig = MutableStateFlow(defaultConfig())

    private object Fixtures {
        val clientCert: HeldCertificate = HeldCertificate.Builder()
            .commonName("JellyPlay Client")
            .rsa2048()
            .serialNumber(3001L)
            .build()

        val serverCert: HeldCertificate = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1")
            .serialNumber(3002L)
            .build()

        val ca: HeldCertificate = HeldCertificate.Builder()
            .commonName("JellyPlay Test CA")
            .certificateAuthority(1)
            .serialNumber(3003L)
            .build()

        val caSignedServerCert: HeldCertificate = HeldCertificate.Builder()
            .commonName("ca-server")
            .addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1")
            .signedBy(ca)
            .serialNumber(3004L)
            .build()
    }

    @BeforeTest
    fun setUp() {
        certsDir = Files.createTempDirectory("jellyplay-mtls").toFile()
        storage = RecordingSecureStorage()
        manager = ClientCertificateManager(certsDir, storage)

        // Server that REQUIRES the client certificate and trusts exactly the
        // fixture client cert (server-side mirror of the mTLS requirement).
        val serverTls = HandshakeCertificates.Builder()
            .heldCertificate(Fixtures.serverCert)
            .addTrustedCertificate(Fixtures.clientCert.certificate)
            .build()
        requireCertServer = MockWebServer()
        requireCertServer.useHttps(needClientAuth(serverTls.sslContext().socketFactory), false)
        requireCertServer.start(InetAddress.getByName("127.0.0.1"), 0)

        // Server presenting a certificate SIGNED BY the custom CA — trusted
        // by the client only while the CA override is installed.
        val caServerTls = HandshakeCertificates.Builder()
            .heldCertificate(Fixtures.caSignedServerCert)
            .build()
        caSignedServer = MockWebServer()
        caSignedServer.useHttps(caServerTls.sslContext().socketFactory, false)
        caSignedServer.start(InetAddress.getByName("127.0.0.1"), 0)
    }

    @AfterTest
    fun tearDown() {
        requireCertServer.shutdown()
        caSignedServer.shutdown()
        certsDir.deleteRecursively()
    }

    private fun defaultConfig() = OkHttpConfig(
        maxCacheSizeMb = 0,
        networkTimeoutPreset = com.raulshma.jellyplay.core.model.NetworkTimeoutPreset.DEFAULT,
        verboseNetworkLogging = false,
        selfSignedTrustHosts = emptySet(),
    )

    /** The applyTls-built client over the real manager (live provider read). */
    private fun tlsClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .applyTls(
                ServerTrustConfig(
                    grantedHosts = { grantedConfig.value.selfSignedTrustHosts },
                    clientCertificate = manager,
                ),
            )
            .build()

    private fun v4Url(server: MockWebServer, path: String = "/System/Info/Public") =
        server.url(path).newBuilder().host("127.0.0.1").build()

    // ----------------------------------------------------------------- (a)

    @Test
    fun `server requiring client auth accepts a client with the imported certificate`() = runTest {
        importClientCertificate()
        // The server cert is self-signed for the probe — grant the host so
        // server-side trust is not the variable under test here.
        grantedConfig.value = defaultConfig().copy(
            selfSignedTrustHosts = setOf("https://127.0.0.1:${requireCertServer.port}"),
        )
        requireCertServer.enqueue(MockResponse().setBody("""{"Id":"abc","ServerName":"Test"}"""))

        val client = tlsClient()
        try {
            client.newCall(Request.Builder().url(v4Url(requireCertServer)).build()).execute().use { response ->
                assertEquals(200, response.code)
            }
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    // ----------------------------------------------------------------- (b)

    @Test
    fun `server requiring client auth refuses a certificate-less client`() {
        grantedConfig.value = defaultConfig().copy(
            selfSignedTrustHosts = setOf("https://127.0.0.1:${requireCertServer.port}"),
        )
        val client = OkHttpClient.Builder()
            .applySelfSignedTrust { grantedConfig.value.selfSignedTrustHosts }
            .build()
        try {
            val error = runCatching {
                client.newCall(Request.Builder().url(v4Url(requireCertServer)).build()).execute().use {}
            }.exceptionOrNull()
            // TLS 1.3 certificate_required/bad_certificate may reach the JDK
            // client as a raw socket abort (Windows especially) instead of a
            // parsed alert — the contract under test is that the connection
            // does NOT succeed without the certificate; any shape of
            // transport/TLS failure qualifies.
            assertTrue(
                error is javax.net.ssl.SSLException ||
                    error is java.security.cert.CertificateException ||
                    error is java.io.IOException,
                "expected the certificate-less handshake to fail, got $error",
            )
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    // ----------------------------------------------------------------- (c)

    @Test
    fun `enabled certificate with broken material fails closed as a TLS failure`() = runTest {
        importClientCertificate()
        // Break the material UNDER the enabled flag.
        File(certsDir, "client.key").writeText("corrupted")
        grantedConfig.value = defaultConfig().copy(
            selfSignedTrustHosts = setOf("https://127.0.0.1:${requireCertServer.port}"),
        )

        val client = tlsClient()
        try {
            val error = runCatching {
                client.newCall(Request.Builder().url(v4Url(requireCertServer)).build()).execute().use {}
            }.exceptionOrNull()
            assertTrue(
                isTlsTrustFailure(error),
                "broken enabled material must surface as a TLS failure, got $error",
            )
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    // ----------------------------------------------------------------- (d)

    @Test
    fun `custom CA override anchors server trust and its absence refuses the server`() = runTest {
        // No grant, no platform trust for the CA-signed server: refused...
        val noOverride = tlsClient()
        try {
            val error = runCatching {
                noOverride.newCall(Request.Builder().url(v4Url(caSignedServer)).build()).execute().use {}
            }.exceptionOrNull()
            assertTrue(isTlsTrustFailure(error), "expected refusal without the override, got $error")
        } finally {
            noOverride.connectionPool.evictAll()
            noOverride.dispatcher.executorService.shutdown()
        }

        // ...and accepted once the override is installed (live read: the
        // client above was already built — a fresh connection on a NEW
        // client still reads the CA at handshake time, but reuse proves the
        // value even harder: the trust manager consults the provider per
        // handshake).
        importServerCa()
        caSignedServer.enqueue(MockResponse().setBody("ok"))
        val withOverride = tlsClient()
        try {
            withOverride.newCall(Request.Builder().url(v4Url(caSignedServer)).build()).execute().use { response ->
                assertEquals(200, response.code)
            }
        } finally {
            withOverride.connectionPool.evictAll()
            withOverride.dispatcher.executorService.shutdown()
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private suspend fun importClientCertificate() {
        val p12 = java.io.ByteArrayOutputStream().also { out ->
            val store = KeyStore.getInstance("PKCS12")
            store.load(null, null)
            store.setKeyEntry(
                "client",
                Fixtures.clientCert.keyPair.private,
                CharArray(0),
                arrayOf(Fixtures.clientCert.certificate),
            )
            store.store(out, CharArray(0))
        }.toByteArray()
        manager.import(ClientCertificateImport(pkcs12Bytes = p12)).getOrThrow()
    }

    private fun importServerCa() {
        File(certsDir, "server-ca.pem").writeText(Fixtures.ca.certificatePem())
    }

    /**
     * Wraps [delegate] so every CONNECTED socket it produces demands client
     * auth (server mode + needClientAuth) — the mockwebserver equivalent of
     * an nginx `ssl_verify_client on`.
     */
    private fun needClientAuth(delegate: SSLSocketFactory): SSLSocketFactory =
        object : SSLSocketFactory() {
            private fun Socket.demand() = apply { (this as? SSLSocket)?.needClientAuth = true }

            override fun createSocket(p0: String?, p1: Int) = delegate.createSocket(p0, p1).demand()
            override fun createSocket(p0: String?, p1: Int, p2: InetAddress?, p3: Int) =
                delegate.createSocket(p0, p1, p2, p3).demand()
            override fun createSocket(p0: InetAddress?, p1: Int) = delegate.createSocket(p0, p1).demand()
            override fun createSocket(p0: InetAddress?, p1: Int, p2: InetAddress?, p3: Int) =
                delegate.createSocket(p0, p1, p2, p3).demand()
            override fun createSocket(p0: Socket?, p1: String?, p2: Int, p3: Boolean) =
                delegate.createSocket(p0, p1, p2, p3).demand()

            override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
            override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites
        }

    private class RecordingSecureStorage : com.raulshma.jellyplay.core.datastore.SecureKeyValueStorage {
        val map = mutableMapOf<String, String>()
        override fun getString(key: String, defValue: String?): String? = map[key] ?: defValue
        override fun putString(key: String, value: String?) {
            if (value == null) map.remove(key) else map[key] = value
        }
        override fun remove(key: String) {
            map.remove(key)
        }
    }
}
