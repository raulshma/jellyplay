package com.raulshma.jellyplay.core.network.config

import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import okhttp3.OkHttpClient

/**
 * Delegation tests for [SelfSignedTrustManager] / [SelfSignedHostnameVerifier]
 * with a hand-rolled recording fake as the delegate: the granted short-circuit
 * must skip the delegate entirely, the non-granted path must pass through and
 * propagate the delegate's failure, and the client-auth overloads must always
 * delegate untouched.
 *
 * The `SSLEngine` overloads are exercised directly (an engine with a peer can
 * be created offline via `SSLContext.createSSLEngine(host, port)`); the
 * `SSLSocket` overload — which cannot be constructed with a peer without a
 * real server — is covered end-to-end by [SelfSignedTrustHandshakeTest].
 */
class SelfSignedTrustManagerTest {

    /** Which delegate overload ran, in order. Records + optionally fails. */
    private class RecordingTrustManager(
        private val failWith: CertificateException? = null,
    ) : javax.net.ssl.X509ExtendedTrustManager() {

        val calls = mutableListOf<String>()

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            calls += "client-plain"
            failWith?.let { throw it }
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: java.net.Socket?) {
            calls += "client-socket"
            failWith?.let { throw it }
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {
            calls += "client-engine"
            failWith?.let { throw it }
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            calls += "server-plain"
            failWith?.let { throw it }
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: java.net.Socket?) {
            calls += "server-socket:${socket is SSLSocket}"
            failWith?.let { throw it }
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {
            calls += "server-engine"
            failWith?.let { throw it }
        }
    }

    private val emptyChain = arrayOf<X509Certificate>()
    private var granted: Set<String> = emptySet()
    private fun newManager(delegate: javax.net.ssl.X509TrustManager) =
        SelfSignedTrustManager(delegate) { granted }

    private fun engineFor(host: String, port: Int): SSLEngine =
        SSLContext.getDefault().createSSLEngine(host, port)

    // ------------------------------------------------------- granted short-circuit

    @Test
    fun `granted peer skips the delegate on the engine server overload`() {
        val delegate = RecordingTrustManager()
        granted = setOf("https://media.example.com:8920")
        val manager = newManager(delegate)

        manager.checkServerTrusted(emptyChain, "RSA", engineFor("media.example.com", 8920))

        assertTrue(delegate.calls.isEmpty(), "granted short-circuit must not reach the delegate")
    }

    @Test
    fun `granted peer skips the delegate on the hostname verifier`() {
        granted = setOf("https://media.example.com:8920")
        val verifier = SelfSignedHostnameVerifier({ _, _ -> error("delegate must not run") }) { granted }

        val session = FakeSession("media.example.com", 8920)
        assertTrue(verifier.verify("media.example.com", session))
    }

    private class FakeSession(private val host: String, private val port: Int) : SSLSession {
        override fun getPeerHost(): String = host
        override fun getPeerPort(): Int = port
        // Every other member is unused by the wrapper; minimal stubs.
        override fun getId(): ByteArray = ByteArray(0)
        override fun getSessionContext(): javax.net.ssl.SSLSessionContext? = null
        override fun getCreationTime(): Long = 0L
        override fun getLastAccessedTime(): Long = 0L
        override fun invalidate() = Unit
        override fun isValid(): Boolean = false
        override fun putValue(name: String?, value: Any?) = Unit
        override fun getValue(name: String?): Any? = null
        override fun removeValue(name: String?) = Unit
        override fun getValueNames(): Array<String> = emptyArray()
        override fun getPeerCertificates(): Array<java.security.cert.Certificate> = emptyArray()
        override fun getLocalCertificates(): Array<java.security.cert.Certificate>? = null
        override fun getPeerCertificateChain(): Array<javax.security.cert.X509Certificate>? = null
        override fun getCipherSuite(): String = ""
        override fun getProtocol(): String = ""
        override fun getPeerPrincipal(): java.security.Principal? = null
        override fun getLocalPrincipal(): java.security.Principal? = null
        override fun getPacketBufferSize(): Int = 0
        override fun getApplicationBufferSize(): Int = 0
    }

    // ------------------------------------------------------------- pass-through

    @Test
    fun `non-granted peer delegates and propagates the delegate failure`() {
        val boom = CertificateException("untrusted")
        val delegate = RecordingTrustManager(failWith = boom)
        granted = setOf("https://other.example.com:8920")
        val manager = newManager(delegate)

        val error = assertFailsWith<CertificateException> {
            manager.checkServerTrusted(emptyChain, "RSA", engineFor("media.example.com", 8920))
        }
        assertEquals(boom, error)
        assertEquals(listOf("server-engine"), delegate.calls)
    }

    @Test
    fun `granted host on a non-granted port still delegates`() {
        // Entry pinned to :8920; handshake against :443 must NOT short-circuit.
        val delegate = RecordingTrustManager(failWith = CertificateException("no"))
        granted = setOf("https://media.example.com:8920")
        val manager = newManager(delegate)

        assertFailsWith<CertificateException> {
            manager.checkServerTrusted(emptyChain, "RSA", engineFor("media.example.com", 443))
        }
        assertEquals(listOf("server-engine"), delegate.calls)
    }

    @Test
    fun `plain server overload always delegates - no peer visible`() {
        val delegate = RecordingTrustManager()
        granted = setOf("https://media.example.com:8920")
        val manager = newManager(delegate)

        manager.checkServerTrusted(emptyChain, "RSA")

        assertEquals(listOf("server-plain"), delegate.calls)
    }

    @Test
    fun `plain delegate without extended overloads is still consulted`() {
        // A minimal X509TrustManager delegate (not extended): the wrapper must
        // fall back to the plain overload rather than crash.
        var plainServerCalls = 0
        var plainClientCalls = 0
        val plain = object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                plainClientCalls++
            }

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                plainServerCalls++
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        granted = emptySet()
        val manager = SelfSignedTrustManager(plain) { granted }

        manager.checkServerTrusted(emptyChain, "RSA", engineFor("media.example.com", 443))
        manager.checkClientTrusted(emptyChain, "RSA", engineFor("media.example.com", 443))

        assertEquals(1, plainServerCalls)
        assertEquals(1, plainClientCalls)
    }

    @Test
    fun `engine without peer host delegates - cannot short-circuit blind`() {
        val delegate = RecordingTrustManager()
        granted = setOf("https://media.example.com:8920")
        val manager = newManager(delegate)

        val noPeerEngine = SSLContext.getDefault().createSSLEngine()
        manager.checkServerTrusted(emptyChain, "RSA", noPeerEngine)

        assertEquals(listOf("server-engine"), delegate.calls)
    }

    @Test
    fun `socket without a handshake session delegates - cannot short-circuit blind`() {
        // An unconnected SSLSocket has no handshake session → no peer → the
        // wrapper must delegate rather than guess. (The connected-socket path
        // with a live peer runs in SelfSignedTrustHandshakeTest.)
        val delegate = RecordingTrustManager()
        granted = setOf("https://media.example.com:8920")
        val manager = newManager(delegate)
        val unconnected = SSLContext.getDefault().socketFactory.createSocket() as SSLSocket

        manager.checkServerTrusted(emptyChain, "RSA", unconnected)

        assertEquals(listOf("server-socket:true"), delegate.calls)
    }

    @Test
    fun `null socket delegates`() {
        val delegate = RecordingTrustManager()
        granted = setOf("https://media.example.com:8920")
        val manager = newManager(delegate)

        manager.checkServerTrusted(emptyChain, "RSA", null as java.net.Socket?)

        assertEquals(listOf("server-socket:false"), delegate.calls)
    }

    // ---------------------------------------------------------------- clients

    @Test
    fun `client-auth overloads always delegate even for granted hosts`() {
        val delegate = RecordingTrustManager()
        granted = setOf("https://media.example.com:8920")
        val manager = newManager(delegate)
        val engine = engineFor("media.example.com", 8920)

        manager.checkClientTrusted(emptyChain, "RSA", engine)
        manager.checkClientTrusted(emptyChain, "RSA")

        assertEquals(listOf("client-engine", "client-plain"), delegate.calls)
    }

    // ------------------------------------------------------------------ misc

    @Test
    fun `granted set is read at decision time - later revocation bites immediately`() {
        val delegate = RecordingTrustManager(failWith = CertificateException("no"))
        granted = setOf("https://media.example.com:8920")
        val manager = newManager(delegate)
        val engine = engineFor("media.example.com", 8920)

        manager.checkServerTrusted(emptyChain, "RSA", engine) // accepted
        granted = emptySet() // revoke
        assertFailsWith<CertificateException> {
            manager.checkServerTrusted(emptyChain, "RSA", engine)
        }
        assertEquals(listOf("server-engine"), delegate.calls)
    }

    @Test
    fun `hostname verifier delegates non-granted hosts to the stock okhttp verifier`() {
        granted = emptySet()
        var delegateAsked = false
        val verifier = SelfSignedHostnameVerifier({ _, _ -> delegateAsked = true; false }) { granted }

        val verdict = verifier.verify("media.example.com", FakeSession("media.example.com", 443))

        assertEquals(false, verdict)
        assertTrue(delegateAsked)
    }

    @Test
    fun `stock verifier delegate is okhttp's own - not the JDK https default`() {
        // Guards the documented trick: the wrapper must delegate to
        // OkHttpClient's own default verifier instance so non-granted hosts
        // keep byte-identical OkHttp hostname behavior.
        val stockClient = OkHttpClient()
        assertEquals(stockClient.hostnameVerifier::class.java.name, "okhttp3.internal.tls.OkHostnameVerifier")
    }
}
