package com.raulshma.jellyplay.core.network.config

import com.raulshma.jellyplay.core.datastore.SecureKeyValueStorage
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * JVM implementation of the client-certificate seam: owns the
 * app-level mTLS material, normalizes every import to a PEM pair on disk,
 * and answers the handshake layer through [ClientCertificateProvider].
 *
 * ## On-disk layout (the normalization contract)
 *
 * Import ALWAYS rewrites the material as:
 *  - `certs/client.crt` — the full certificate chain, PEM, leaf first;
 *  - `certs/client.key` — the private key as UNENCRYPTED PKCS#8 PEM;
 *  - `certs/server-ca.pem` — the optional trust-anchor override.
 *
 * The normalized pair is the single source both TLS consumers read: mpv /
 * ffmpeg need literal PEM file paths (`tls-client-cert` / `tls-client-key` /
 * `tls-ca-file`), and the OkHttp handshake layer re-parses the same pair
 * into key managers. Because mpv cannot open encrypted key files, a
 * passphrase-protected PKCS#12 import is decrypted ONCE at import; the
 * passphrase itself is retained in [SecureKeyValueStorage] only so a future
 * re-export/rotation can reuse it. The `certs` directory lives under the
 * app-private storage of each platform (Android `filesDir`, desktop
 * `configDir`); POSIX hosts additionally get 0600 on key material.
 *
 * ## Live-read + fail-closed (see [ClientCertificateProvider])
 *
 * [keyManagers]/[customCa] re-read the enabled flag and re-parse the files,
 * cached only against each file's (lastModified, length) stamp so toggling or
 * swapping material takes effect on the next handshake without rebuilding
 * any OkHttpClient — the same live-config contract the self-signed grants
 * layer has. Enabled-but-broken material THROWS (never a silent no-cert
 * handshake); a corrupt installed CA override THROWS (never a silent fall
 * back to platform trust).
 *
 * ## Accepted import shapes
 *
 *  - PKCS#12 / `.p12` / `.pfx` (key + chain in one bundle, optional
 *    passphrase);
 *  - PEM pair: `-----BEGIN CERTIFICATE-----` chain file + `-----BEGIN PRIVATE
 *    KEY-----` (PKCS#8) or `-----BEGIN RSA PRIVATE KEY-----` (traditional
 *    PKCS#1, wrapped into PKCS#8 at parse time — no BouncyCastle needed).
 *    Encrypted PEM keys (`ENCRYPTED PRIVATE KEY`, SEC1 `EC PRIVATE KEY`) are
 *    rejected with a pointer to convert to PKCS#12 instead.
 */
class ClientCertificateManager(
    certsDir: File,
    private val secureStorage: SecureKeyValueStorage,
) : ClientCertificateFacade, ClientCertificateProvider {

    private val certsDir: File = certsDir.apply { mkdirs() }
    private val certificateFile: File = File(certsDir, CERT_FILE)
    private val keyFile: File = File(certsDir, KEY_FILE)
    private val caFile: File = File(certsDir, CA_FILE)

    private val statusFlow = MutableStateFlow(readStatus())
    override val status: StateFlow<ClientCertificateStatus> = statusFlow.asStateFlow()

    // ── ClientCertificateFacade ─────────────────────────────────────────────

    override suspend fun import(input: ClientCertificateImport): Result<ClientCertificateStatus> =
        runCatching {
            val parsed = parseImport(input)
            val chainPem = parsed.certificateChain.joinToString("") { pemBlock("CERTIFICATE", it.encoded) }
            val keyPem = pemBlock("PRIVATE KEY", parsed.privateKey.encoded)
            writeAtomic(certificateFile, chainPem)
            writeAtomic(keyFile, keyPem)
            restrictToOwner(keyFile)
            restrictToOwner(certificateFile)
            input.serverCaPemBytes?.let { caBytes ->
                // Parse BEFORE writing: a corrupt CA must fail the import,
                // not install a trust override that later fails closed at
                // every handshake.
                parseCaCertificate(caBytes)
                writeAtomic(caFile, String(caBytes, Charsets.UTF_8))
                restrictToOwner(caFile)
            }
            input.passphrase?.let { secureStorage.putString(KEY_PASSPHRASE, String(it)) }
            secureStorage.putString(KEY_ENABLED, true.toString())
            refreshStatus()
        }.recoverCatching { failure ->
            // Parse errors already carry user-presentable text; everything
            // else (wrong passphrase digests, IO) gets a generic shape with
            // the cause attached.
            throw if (failure is IllegalArgumentException) failure else IllegalArgumentException(
                "Could not import the certificate: ${failure.message ?: failure::class.simpleName}",
                failure,
            )
        }

    override fun setEnabled(enabled: Boolean) {
        if (!status.value.isImported) return
        secureStorage.putString(KEY_ENABLED, enabled.toString())
        refreshStatus()
    }

    override fun remove() {
        certificateFile.delete()
        keyFile.delete()
        caFile.delete()
        secureStorage.remove(KEY_ENABLED)
        secureStorage.remove(KEY_PASSPHRASE)
        synchronized(cacheLock) {
            cachedKeyManagers = null
            cachedCa = null
        }
        refreshStatus()
    }

    // ── ClientCertificateProvider (handshake-time reads) ────────────────────

    override fun keyManagers(): Array<KeyManager>? {
        if (!isEnabled()) return null
        val certStamp = stampOf(certificateFile)
        val keyStamp = stampOf(keyFile)
        if (certStamp == null || keyStamp == null) {
            // Enabled but the material is GONE: fail closed (never a silent
            // certificate-less handshake against a server that requires it).
            throw ClientCertificateMaterialException(
                "Client certificate is enabled but ${if (certStamp == null) CERT_FILE else KEY_FILE} is missing",
            )
        }
        synchronized(cacheLock) {
            val cached = cachedKeyManagers
            if (cached != null && cached.certStamp == certStamp && cached.keyStamp == keyStamp) {
                return cached.managers
            }
        }
        val parsed = readNormalizedPair()
        val managers = buildKeyManagers(parsed)
        synchronized(cacheLock) {
            cachedKeyManagers = CachedKeyManagers(certStamp, keyStamp, managers)
        }
        return managers
    }

    override fun customCa(): X509Certificate? {
        if (!caFile.isFile) return null
        val caStamp = stampOf(caFile) ?: throw ClientCertificateMaterialException("server-ca.pem vanished")
        synchronized(cacheLock) {
            val cached = cachedCa
            if (cached != null && cached.stamp == caStamp) return cached.certificate
        }
        val parsed = try {
            parseCaCertificate(caFile.readBytes())
        } catch (e: ClientCertificateMaterialException) {
            throw e
        } catch (e: Exception) {
            // Fail-closed: an INSTALLED override that no longer parses must
            // fail the handshake, never silently fall back to platform trust.
            throw ClientCertificateMaterialException(
                "Installed server CA override is corrupt: ${e.message ?: e::class.simpleName}",
                e,
            )
        }
        synchronized(cacheLock) {
            cachedCa = CachedCa(caStamp, parsed)
        }
        return parsed
    }

    /**
     * The mpv-facing view of the ACTIVE certificate: literal file paths for
     * the `tls-client-cert` / `tls-client-key` / `tls-ca-file` options, or
     * `null` when no certificate is enabled. (The CA override is reported
     * independently of the client cert: mpv should anchor on the custom CA
     * whenever it is installed, mirroring the OkHttp trust side.)
     */
    fun playbackTlsPaths(): ClientCertificatePaths? {
        if (!isEnabled()) return null
        if (!certificateFile.isFile || !keyFile.isFile) return null
        return ClientCertificatePaths(
            certificatePath = certificateFile.absolutePath,
            keyPath = keyFile.absolutePath,
            caPath = if (caFile.isFile) caFile.absolutePath else null,
        )
    }

    /** Path triple handed to the player layer (mapped to its PlaybackTls there). */
    data class ClientCertificatePaths(
        val certificatePath: String,
        val keyPath: String,
        val caPath: String?,
    )

    // ── Parsing ─────────────────────────────────────────────────────────────

    private fun parseImport(input: ClientCertificateImport): ParsedClientCertificate {
        val pkcs12 = input.pkcs12Bytes
        return if (pkcs12 != null) {
            parsePkcs12(pkcs12, input.passphrase)
        } else {
            val certPem = input.certificatePemBytes
                ?: throw IllegalArgumentException("No certificate material supplied")
            val keyPem = input.privateKeyPemBytes
                ?: throw IllegalArgumentException(
                    "A PKCS#12 (.p12/.pfx) bundle or a certificate + private key pair is required",
                )
            ParsedClientCertificate(
                certificateChain = parsePemCertificateChain(certPem),
                privateKey = parsePemPrivateKey(keyPem),
            )
        }.also(::validateKeyMatchesLeaf)
    }

    private fun parsePkcs12(bytes: ByteArray, passphrase: CharArray?): ParsedClientCertificate {
        val store = KeyStore.getInstance("PKCS12")
        try {
            store.load(ByteArrayInputStream(bytes), passphrase ?: CharArray(0))
        } catch (e: Exception) {
            val isPasswordShape = e.message?.contains("password", ignoreCase = true) == true ||
                e.cause?.message?.contains("password", ignoreCase = true) == true ||
                e is java.security.UnrecoverableKeyException
            throw IllegalArgumentException(
                if (isPasswordShape) {
                    "Could not open the PKCS#12 bundle — check the passphrase"
                } else {
                    "Not a readable PKCS#12 (.p12/.pfx) file: ${e.message ?: e::class.simpleName}"
                },
                e,
            )
        }
        val aliases = store.aliases().toList()
        val entryAlias = aliases.firstOrNull { alias ->
            store.isKeyEntry(alias) &&
                store.getCertificateChain(alias)?.any { it is X509Certificate } == true
        } ?: throw IllegalArgumentException(
            "The PKCS#12 bundle contains no private-key entry with a certificate",
        )
        val entry = store.getEntry(entryAlias, KeyStore.PasswordProtection(passphrase ?: CharArray(0)))
            as? KeyStore.PrivateKeyEntry
            ?: throw IllegalArgumentException("The PKCS#12 key entry could not be read")
        val chain = entry.certificateChain.filterIsInstance<X509Certificate>()
        if (chain.isEmpty()) {
            throw IllegalArgumentException("The PKCS#12 entry carries no X.509 certificates")
        }
        return ParsedClientCertificate(chain, entry.privateKey)
    }

    private fun parsePemCertificateChain(bytes: ByteArray): List<X509Certificate> {
        val text = String(bytes, Charsets.UTF_8)
        if (!text.contains("BEGIN CERTIFICATE")) {
            throw IllegalArgumentException(
                "The certificate file is not PEM (no BEGIN CERTIFICATE block) — " +
                    "supply a .crt/.pem certificate or a .p12 bundle",
            )
        }
        val factory = CertificateFactory.getInstance("X.509")
        val chain = factory.generateCertificates(ByteArrayInputStream(bytes))
            .filterIsInstance<X509Certificate>()
        if (chain.isEmpty()) throw IllegalArgumentException("No certificate found in the PEM file")
        return chain
    }

    private fun parsePemPrivateKey(bytes: ByteArray): PrivateKey {
        val text = String(bytes, Charsets.UTF_8)
        val body = pemBody(text, "ENCRYPTED PRIVATE KEY")
        if (body != null) {
            throw IllegalArgumentException(
                "Encrypted PEM private keys are not supported — re-export as PKCS#12 (.p12) with a passphrase",
            )
        }
        val ecBody = pemBody(text, "EC PRIVATE KEY")
        if (ecBody != null) {
            throw IllegalArgumentException(
                "Traditional EC private keys are not supported — re-export as PKCS#8 or PKCS#12",
            )
        }
        val pkcs8 = pemBody(text, "PRIVATE KEY")
        if (pkcs8 != null) {
            return parsePkcs8WithAnyAlgorithm(pkcs8)
        }
        val pkcs1 = pemBody(text, "RSA PRIVATE KEY")
        if (pkcs1 != null) {
            val wrapped = Pkcs1ToPkcs8.wrapRsa(pkcs1)
            return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(wrapped))
        }
        throw IllegalArgumentException(
            "No supported private key block found — expected BEGIN PRIVATE KEY or BEGIN RSA PRIVATE KEY",
        )
    }

    private fun parsePkcs8WithAnyAlgorithm(pkcs8: ByteArray): PrivateKey {
        val algorithms = listOf("EC", "RSA", "Ed25519", "Ed448", "XDH")
        val errors = mutableListOf<String>()
        for (algorithm in algorithms) {
            try {
                return KeyFactory.getInstance(algorithm).generatePrivate(PKCS8EncodedKeySpec(pkcs8))
            } catch (e: Exception) {
                errors += "$algorithm: ${e.message}"
            }
        }
        throw IllegalArgumentException("Unsupported private key: ${errors.joinToString("; ")}")
    }

    private fun parseCaCertificate(bytes: ByteArray): X509Certificate {
        val factory = CertificateFactory.getInstance("X.509")
        val parsed = factory.generateCertificates(ByteArrayInputStream(bytes))
            .filterIsInstance<X509Certificate>()
            .firstOrNull()
            ?: throw IllegalArgumentException("The CA file contains no X.509 certificate")
        return parsed
    }

    /**
     * Best-effort pairing check so a mismatched cert/key pair fails at import
     * instead of at the first handshake. RSA pairs compare modulus +
     * public exponent (both fully derivable from the CRT private key); other
     * families skip the check (the handshake will surface a mismatch anyway).
     */
    private fun validateKeyMatchesLeaf(parsed: ParsedClientCertificate) {
        val rsaKey = parsed.privateKey as? java.security.interfaces.RSAPrivateCrtKey ?: return
        val rsaCert = runCatching {
            parsed.leaf.publicKey as java.security.interfaces.RSAPublicKey
        }.getOrNull() ?: return
        val derivedPublic = KeyFactory.getInstance("RSA")
            .generatePublic(RSAPublicKeySpec(rsaKey.modulus, rsaKey.publicExponent))
        val matches = MessageDigest.isEqual(derivedPublic.encoded, rsaCert.encoded)
        if (!matches) {
            throw IllegalArgumentException("The private key does not match the certificate")
        }
    }

    // ── Status + cache plumbing ─────────────────────────────────────────────

    private fun isEnabled(): Boolean = secureStorage.getString(KEY_ENABLED) == true.toString()

    private fun readStatus(): ClientCertificateStatus {
        val materialPresent = certificateFile.isFile && keyFile.isFile
        if (!materialPresent) {
            return ClientCertificateStatus(
                enabled = false,
                materialPresent = false,
                customCaConfigured = caFile.isFile,
            )
        }
        // Corrupt-on-disk material must still surface a parseable status to
        // the UI (subject/validity stay null), NOT throw here: the settings
        // screen has to stay usable to Remove the broken import.
        val leaf = runCatching { readNormalizedPair().leaf }.getOrNull()
        return ClientCertificateStatus(
            enabled = isEnabled(),
            materialPresent = true,
            subject = leaf?.subjectX500Principal?.name,
            issuer = leaf?.issuerX500Principal?.name,
            notValidBeforeMs = leaf?.notBefore?.time,
            notValidAfterMs = leaf?.notAfter?.time,
            customCaConfigured = caFile.isFile,
        )
    }

    /** Re-reads the normalized pair; THROWS on missing/corrupt material (fail-closed read path). */
    private fun readNormalizedPair(): ParsedClientCertificate = try {
        val chain = parsePemCertificateChain(certificateFile.readBytes())
        val key = parsePemPrivateKey(keyFile.readBytes())
        ParsedClientCertificate(chain, key)
    } catch (e: ClientCertificateMaterialException) {
        throw e
    } catch (e: Exception) {
        throw ClientCertificateMaterialException(
            "Enabled client certificate material is corrupt: ${e.message ?: e::class.simpleName}",
            e,
        )
    }

    private fun buildKeyManagers(parsed: ParsedClientCertificate): Array<KeyManager> {
        val store = KeyStore.getInstance("PKCS12")
        store.load(null, null)
        store.setKeyEntry(
            "client",
            parsed.privateKey,
            CharArray(0),
            parsed.certificateChain.toTypedArray(),
        )
        val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        factory.init(store, CharArray(0))
        return factory.keyManagers
    }

    private fun refreshStatus(): ClientCertificateStatus = readStatus().also {
        synchronized(cacheLock) {
            cachedKeyManagers = null
            cachedCa = null
        }
        statusFlow.value = it
    }

    /**
     * Change stamp for one material file: lastModified + length + a content
     * fold. The metadata pair alone can't tell a same-size rewrite within the
     * filesystem timestamp granularity apart (FAT/exFAT and some network
     * filesystems are 1–2 s coarse; a same-ms rewrite serves a stale key
     * manager at the next handshake), so the bytes ride along — the files are
     * small PEMs, and this runs at handshake frequency, not per frame.
     */
    private fun stampOf(file: File): Long? {
        if (!file.isFile) return null
        var h = file.lastModified()
        h = h * 31L + file.length()
        for (b in file.readBytes()) h = h * 31L + b
        return h
    }

    private fun writeAtomic(target: File, content: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            // Rename can fail across file systems / on Windows when the
            // target is locked — fall back to a plain overwrite.
            target.writeText(content)
            tmp.delete()
        }
    }

    /** 0600 where the platform has POSIX permissions; a no-op elsewhere. */
    private fun restrictToOwner(file: File) {
        runCatching {
            val path: Path = file.toPath()
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
        } // Windows / Android app-private dirs: nothing to do.
    }

    private class CachedKeyManagers(
        val certStamp: Long,
        val keyStamp: Long,
        val managers: Array<KeyManager>,
    )

    private class CachedCa(val stamp: Long, val certificate: X509Certificate)

    private val cacheLock = Any()
    private var cachedKeyManagers: CachedKeyManagers? = null
    private var cachedCa: CachedCa? = null

    companion object {
        internal const val CERT_FILE = "client.crt"
        internal const val KEY_FILE = "client.key"
        internal const val CA_FILE = "server-ca.pem"
        private const val KEY_ENABLED = "client_certificate.enabled"
        private const val KEY_PASSPHRASE = "client_certificate.passphrase"

        internal fun pemBlock(label: String, der: ByteArray): String =
            "-----BEGIN $label-----\n" +
                Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der) +
                "\n-----END $label-----\n"

        /** Base64 body of the FIRST `label` PEM block in [text], or null. */
        internal fun pemBody(text: String, label: String): ByteArray? {
            val begin = "-----BEGIN $label-----"
            val end = "-----END $label-----"
            val start = text.indexOf(begin)
            if (start < 0) return null
            val bodyStart = start + begin.length
            val endIdx = text.indexOf(end, bodyStart)
            if (endIdx < 0) return null
            val base64 = text.substring(bodyStart, endIdx).filterNot { it.isWhitespace() }
            return runCatching { Base64.getDecoder().decode(base64) }.getOrNull()
        }
    }
}

/**
 * Traditional PKCS#1 `RSAPrivateKey` DER → PKCS#8 `PrivateKeyInfo` DER, by
 * pure envelope wrapping:
 *
 * `PrivateKeyInfo ::= SEQUENCE { INTEGER 0, SEQUENCE { OID rsaEncryption, NULL }, OCTET STRING <pkcs1> }`
 *
 * The PKCS#1 body is opaque payload, so no key math is involved — this is
 * what lets the app accept `openssl genrsa`-style keys without BouncyCastle.
 */
internal object Pkcs1ToPkcs8 {

    private val RSA_ENCRYPTION_OID = byteArrayOf(
        0x2A.toByte(), 0x86.toByte(), 0x48.toByte(), 0x86.toByte(), 0xF7.toByte(),
        0x0D.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(), // 1.2.840.113549.1.1.1
    )

    fun wrapRsa(pkcs1Der: ByteArray): ByteArray = derSequence(
        derInteger(byteArrayOf(0.toByte())),
        derSequence(derOid(RSA_ENCRYPTION_OID), DerNull),
        derOctetString(pkcs1Der),
    )

    // ── minimal DER writer ───────────────────────────────────────────────

    private val DerNull = byteArrayOf(0x05, 0x00)

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

    private fun derTagged(tag: Int, content: ByteArray): ByteArray =
        byteArrayOf(tag.toByte()) + derLength(content.size) + content

    private fun derSequence(vararg parts: ByteArray): ByteArray =
        derTagged(0x30, parts.reduce { acc, bytes -> acc + bytes })

    private fun derInteger(value: ByteArray): ByteArray = derTagged(0x02, value)

    private fun derOid(oid: ByteArray): ByteArray = derTagged(0x06, oid)

    private fun derOctetString(value: ByteArray): ByteArray = derTagged(0x04, value)
}
