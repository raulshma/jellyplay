package com.raulshma.jellyplay.core.network.config

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okhttp3.tls.HeldCertificate

/**
 * Round-trip + fail-closed coverage for [ClientCertificateManager]:
 * every accepted import shape is normalized to the on-disk PEM pair and
 * re-parses into usable key managers; every broken-material shape fails
 * CLOSED (throws) instead of silently presenting no certificate.
 *
 * Test identities are minted with okhttp-tls [HeldCertificate] (already the
 * SelfSignedTrustHandshakeTest fixture source) — no BouncyCastle on the
 * classpath, which is exactly the constraint the PKCS#1 wrapping exists for;
 * the traditional `BEGIN RSA PRIVATE KEY` fixture is assembled from the CRT
 * key components with a local DER writer.
 */
class ClientCertificateManagerTest {

    private lateinit var certsDir: java.nio.file.Path
    private lateinit var storage: RecordingSecureStorage
    private lateinit var manager: ClientCertificateManager

    // Fixtures minted once per class (RSA keygen is the slow part).
    private object Fixtures {
        val clientCert: HeldCertificate = HeldCertificate.Builder()
            .commonName("JellyPlay Client")
            .rsa2048()
            .serialNumber(1001L)
            .build()

        val clientKeyPair: KeyPair = clientCert.keyPair
        val clientX509: X509Certificate = clientCert.certificate

        val serverCa: HeldCertificate = HeldCertificate.Builder()
            .commonName("JellyPlay Test Server CA")
            .serialNumber(2001L)
            .build()
    }

    @BeforeTest
    fun setUp() {
        certsDir = Files.createTempDirectory("jellyplay-certs")
        storage = RecordingSecureStorage()
        manager = ClientCertificateManager(certsDir.toFile(), storage)
    }

    @AfterTest
    fun tearDown() {
        certsDir.toFile().deleteRecursively()
    }

    // ── PKCS#12 import → normalization ────────────────────────────────────

    @Test
    fun `pkcs12 import normalizes to a PEM pair on disk and enables the certificate`() = runTest {
        val p12 = pkcs12Bundle(Fixtures.clientKeyPair, Fixtures.clientX509, passphrase = "secret")

        val status = manager.import(ClientCertificateImport(pkcs12Bytes = p12, passphrase = "secret".toCharArray()))
            .getOrThrow()

        assertTrue(status.enabled, "import enables the certificate")
        assertTrue(status.materialPresent)
        assertTrue(status.subject?.contains("JellyPlay Client") == true, "subject parsed, was ${status.subject}")
        assertEquals(Fixtures.clientX509.issuerX500Principal.name, status.issuer)
        assertEquals(Fixtures.clientX509.notBefore.time, status.notValidBeforeMs)
        assertEquals(Fixtures.clientX509.notAfter.time, status.notValidAfterMs)

        val crt = certsDir.resolve("client.crt").toFile().readText()
        val key = certsDir.resolve("client.key").toFile().readText()
        assertTrue(crt.contains("-----BEGIN CERTIFICATE-----"))
        assertTrue(key.contains("-----BEGIN PRIVATE KEY-----"), "normalized key is unencrypted PKCS#8 PEM")

        assertEquals("secret", storage.map["client_certificate.passphrase"])
        assertEquals("true", storage.map["client_certificate.enabled"])
    }

    @Test
    fun `imported key managers present the imported certificate`() = runTest {
        importPkcs12()

        val managers = assertNotNull(manager.keyManagers())
        val x509 = managers.filterIsInstance<javax.net.ssl.X509KeyManager>()
            .first { it.getPrivateKey("client") != null }
        val chain = assertNotNull(x509.getCertificateChain("client"))
        assertEquals(Fixtures.clientX509.encoded.toList(), chain.first().encoded.toList())
    }

    @Test
    fun `round trip - the normalized pair re-parses through a fresh manager`() = runTest {
        importPkcs12()

        val second = ClientCertificateManager(certsDir.toFile(), storage)
        // The enabled flag lives in secure storage, so the fresh manager
        // sees the same active certificate — proving the on-disk pair is
        // self-sufficient material, not manager-local state.
        assertTrue(second.status.value.enabled)
        assertTrue(second.keyManagers() != null)
    }

    @Test
    fun `wrong passphrase fails with a user-presentable error and writes nothing`() = runTest {
        val p12 = pkcs12Bundle(Fixtures.clientKeyPair, Fixtures.clientX509, passphrase = "right")

        val error = manager.import(
            ClientCertificateImport(pkcs12Bytes = p12, passphrase = "wrong".toCharArray()),
        ).exceptionOrNull()

        assertTrue(
            error is IllegalArgumentException && error.message!!.contains("passphrase", ignoreCase = true),
            "expected a passphrase-shaped error, got $error",
        )
        assertFalse(certsDir.resolve("client.crt").toFile().exists(), "nothing written on failure")
        assertFalse(manager.status.value.enabled)
    }

    // ── PEM pair import shapes ────────────────────────────────────────────

    @Test
    fun `pem pair import with pkcs8 key normalizes and enables`() = runTest {
        val status = manager.import(
            ClientCertificateImport(
                certificatePemBytes = Fixtures.clientCert.certificatePem().toByteArray(),
                privateKeyPemBytes = pkcs8Pem(Fixtures.clientKeyPair.private).toByteArray(),
            ),
        ).getOrThrow()

        assertTrue(status.enabled)
        assertTrue(manager.keyManagers() != null)
    }

    @Test
    fun `pem pair import with traditional rsa key is wrapped and accepted`() = runTest {
        val status = manager.import(
            ClientCertificateImport(
                certificatePemBytes = Fixtures.clientCert.certificatePem().toByteArray(),
                privateKeyPemBytes = pkcs1Pem(Fixtures.clientKeyPair.private as RSAPrivateCrtKey).toByteArray(),
            ),
        ).getOrThrow()

        assertTrue(status.enabled)
        val managers = assertNotNull(manager.keyManagers())
        val key = managers.filterIsInstance<javax.net.ssl.X509KeyManager>()
            .first { it.getPrivateKey("client") != null }
            .getPrivateKey("client")!!
        // The wrapped PKCS#1 key must round back to the SAME private key.
        val factory = java.security.KeyFactory.getInstance("RSA")
        val roundTrip = factory.generatePrivate(PKCS8EncodedKeySpec(key.encoded))
        assertEquals(Fixtures.clientKeyPair.private.encoded.toList(), roundTrip.encoded.toList())
    }

    @Test
    fun `encrypted pem key is rejected with a conversion pointer`() = runTest {
        val encryptedPem = "-----BEGIN ENCRYPTED PRIVATE KEY-----\nAAAA\n-----END ENCRYPTED PRIVATE KEY-----\n"

        val error = manager.import(
            ClientCertificateImport(
                certificatePemBytes = Fixtures.clientCert.certificatePem().toByteArray(),
                privateKeyPemBytes = encryptedPem.toByteArray(),
            ),
        ).exceptionOrNull()

        assertTrue(
            error is IllegalArgumentException && error.message!!.contains("PKCS#12"),
            "expected the re-export pointer, got $error",
        )
        assertFalse(manager.status.value.enabled)
    }

    @Test
    fun `mismatched key and certificate fail the import`() = runTest {
        val otherKey = HeldCertificate.Builder().commonName("Other").rsa2048().build().keyPair

        val error = manager.import(
            ClientCertificateImport(
                certificatePemBytes = Fixtures.clientCert.certificatePem().toByteArray(),
                privateKeyPemBytes = pkcs8Pem(otherKey.private).toByteArray(),
            ),
        ).exceptionOrNull()

        assertTrue(
            error is IllegalArgumentException && error.message!!.contains("does not match"),
            "expected the pairing failure, got $error",
        )
    }

    // ── toggle / remove / playback paths ─────────────────────────────────

    @Test
    fun `disabling withholds key managers, enabling restores them`() = runTest {
        importPkcs12()
        assertTrue(manager.keyManagers() != null)

        manager.setEnabled(false)
        assertEquals(null, manager.keyManagers())
        assertFalse(manager.status.value.enabled)
        // Material survives a disable (re-enable keeps working).
        manager.setEnabled(true)
        assertTrue(manager.keyManagers() != null)
    }

    @Test
    fun `remove deletes material, passphrase, and flag`() = runTest {
        importPkcs12()
        manager.remove()

        assertFalse(certsDir.resolve("client.crt").toFile().exists())
        assertFalse(certsDir.resolve("client.key").toFile().exists())
        assertEquals(null, manager.keyManagers())
        assertEquals(null, storage.map["client_certificate.passphrase"])
        assertFalse(manager.status.value.enabled)
    }

    @Test
    fun `playbackTlsPaths exposes the normalized paths only while enabled`() = runTest {
        assertEquals(null, manager.playbackTlsPaths())

        importPkcs12()
        val paths = assertNotNull(manager.playbackTlsPaths())
        assertEquals(certsDir.resolve("client.crt").toFile().absolutePath, paths.certificatePath)
        assertEquals(certsDir.resolve("client.key").toFile().absolutePath, paths.keyPath)
        assertEquals(null, paths.caPath, "no CA installed")

        manager.setEnabled(false)
        assertEquals(null, manager.playbackTlsPaths())
    }

    // ── CA override ───────────────────────────────────────────────────────

    @Test
    fun `ca override installs and parses, corrupt installed override fails closed`() = runTest {
        assertEquals(null, manager.customCa())

        importPkcs12(withCa = true)
        val ca = assertNotNull(manager.customCa())
        assertEquals(Fixtures.serverCa.certificate.subjectX500Principal, ca.subjectX500Principal)
        assertTrue(manager.status.value.customCaConfigured)
        assertNotNull(manager.playbackTlsPaths()?.caPath)

        // Corrupt the INSTALLED override: reading it must throw — never
        // silently fall back to platform trust.
        certsDir.resolve("server-ca.pem").toFile().writeText("not a certificate")
        assertFailsWith<ClientCertificateMaterialException> { manager.customCa() }
    }

    // ── fail-closed on broken enabled material ───────────────────────────

    @Test
    fun `enabled certificate with missing key file fails closed`() = runTest {
        importPkcs12()
        certsDir.resolve("client.key").toFile().delete()

        assertFailsWith<ClientCertificateMaterialException> {
            manager.keyManagers()
        }
    }

    @Test
    fun `enabled certificate with corrupt certificate file fails closed`() = runTest {
        importPkcs12()
        certsDir.resolve("client.crt").toFile().writeText("garbage")

        val failure = assertFailsWith<ClientCertificateMaterialException> { manager.keyManagers() }
        // The status read stays non-throwing so the UI can still Remove.
        assertTrue(manager.status.value.materialPresent)
        assertTrue(failure.message!!.contains("corrupt"))
    }

    @Test
    fun `broken material surfaces as a TLS trust failure through isTlsTrustFailure`() = runTest {
        importPkcs12()
        certsDir.resolve("client.key").toFile().delete()

        val thrown = assertFailsWith<ClientCertificateMaterialException> { manager.keyManagers() }
        // KeyStoreException is the CertificateException/SSL-adjacent family
        // the classifier walks — the handshake must read as a TLS failure,
        // never as an opaque crash or a silent no-cert connection.
        assertTrue(isTlsTrustFailure(thrown), "material failure must classify as a TLS failure")
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private suspend fun importPkcs12(withCa: Boolean = false) {
        val p12 = pkcs12Bundle(Fixtures.clientKeyPair, Fixtures.clientX509, passphrase = null)
        manager.import(
            ClientCertificateImport(
                pkcs12Bytes = p12,
                serverCaPemBytes = if (withCa) Fixtures.serverCa.certificatePem().toByteArray() else null,
            ),
        ).getOrThrow()
    }

    private fun pkcs12Bundle(keyPair: KeyPair, certificate: X509Certificate, passphrase: String?): ByteArray {
        val store = KeyStore.getInstance("PKCS12")
        store.load(null, null)
        store.setKeyEntry("client", keyPair.private, passphrase?.toCharArray() ?: CharArray(0), arrayOf(certificate))
        val bytes = ByteArrayOutputStream()
        store.store(bytes, passphrase?.toCharArray() ?: CharArray(0))
        return bytes.toByteArray()
    }

    private fun pkcs8Pem(key: PrivateKey): String =
        "-----BEGIN PRIVATE KEY-----\n" +
            Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(key.encoded) +
            "\n-----END PRIVATE KEY-----\n"

    /**
     * Traditional `BEGIN RSA PRIVATE KEY` (PKCS#1 RSAPrivateKey DER) from the
     * CRT key components — assembled with a local DER INTEGER/SEQUENCE
     * writer (version, n, e, d, p, q, d mod p-1, d mod q-1, qInv).
     */
    private fun pkcs1Pem(key: RSAPrivateCrtKey): String {
        fun integer(value: BigInteger): ByteArray {
            var bytes = value.toByteArray()
            // DER INTEGER: minimal-length, non-negative needs a leading 0x00
            // only when the high bit is set — toByteArray() already emits the
            // minimal signed form, so only strip redundant zero padding.
            if (bytes.size > 1 && bytes[0] == 0.toByte() && bytes[1] >= 0) {
                bytes = bytes.copyOfRange(1, bytes.size)
            }
            return byteArrayOf(0x02) + derLength(bytes.size) + bytes
        }
        val content = integer(BigInteger.ZERO) +
            integer(key.modulus) +
            integer(key.publicExponent) +
            integer(key.privateExponent) +
            integer(key.primeP) +
            integer(key.primeQ) +
            integer(key.primeExponentP) +
            integer(key.primeExponentQ) +
            integer(key.crtCoefficient)
        val der = byteArrayOf(0x30) + derLength(content.size) + content
        return "-----BEGIN RSA PRIVATE KEY-----\n" +
            Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der) +
            "\n-----END RSA PRIVATE KEY-----\n"
    }

    private fun derLength(length: Int): ByteArray =
        if (length < 0x80) {
            byteArrayOf(length.toByte())
        } else {
            var needed = 0
            var value = length
            while (value > 0) {
                needed++
                value = value ushr 8
            }
            byteArrayOf((0x80 or needed).toByte()) + ByteArray(needed) { i ->
                (length ushr (8 * (needed - 1 - i))).toByte()
            }
        }

    /** In-memory SecureKeyValueStorage (the interface has no bulk clear by design). */
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

    /** Sanity for the fixture minting itself: the p12/PEM bodies are real X.509. */
    @Test
    fun `fixtures mint parseable certificates`() {
        val factory = CertificateFactory.getInstance("X.509")
        val parsed = factory.generateCertificate(
            ByteArrayInputStream(Fixtures.clientX509.encoded),
        ) as X509Certificate
        assertEquals(Fixtures.clientX509.subjectX500Principal, parsed.subjectX500Principal)
    }
}
