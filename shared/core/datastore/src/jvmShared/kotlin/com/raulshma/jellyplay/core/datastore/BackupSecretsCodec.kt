package com.raulshma.jellyplay.core.datastore

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json

/**
 * Encrypts/decrypts the [BackupSecrets] payload behind a [SecretsEnvelope] —
 * the Wave-3 passphrase-protected secrets block of the LOCAL FILE settings
 * backup. Lives in `jvmShared` because `javax.crypto` is JVM-only and both
 * JellyPlay targets (Android + desktop) are JVM.
 *
 * ## Envelope wire format
 *
 * The envelope is stored under the `"secrets"` key of the backup document:
 *
 * ```json
 * "secrets": {
 *   "v": 1,
 *   "kdfSaltB64": "<16 random bytes, base64>",
 *   "kdfIterations": 600000,
 *   "nonceB64": "<12 random bytes, base64>",
 *   "ciphertextB64": "<AES-256-GCM ciphertext + 128-bit tag, base64>"
 * }
 * ```
 *
 * ## Crypto
 *
 *  - KDF: PBKDF2-HMAC-SHA256 (`PBKDF2WithHmacSHA256`), [KDF_ITERATIONS]
 *    iterations, random [SALT_BYTES]-byte salt, [KEY_BITS]-bit key. The
 *    iteration count travels in the envelope so a future bump stays
 *    self-describing; decrypt honors the stored value (bounded to a sane
 *    range: a tampered `kdfIterations = 1` cannot cheapen the KDF, and a
 *    hostile multi-billion count cannot turn decode into a minutes-to-hours
 *    hang — see [MAX_ACCEPTED_ITERATIONS]).
 *  - Cipher: AES-256-GCM (`AES/GCM/NoPadding`), random [NONCE_BYTES]-byte
 *    nonce per encryption, [GCM_TAG_BITS]-bit auth tag (the JDK appends the
 *    tag to the ciphertext, so `ciphertextB64` is one blob).
 *
 * The passphrase is a [CharArray] so the caller can zero it after use (the
 * codec clears its `PBEKeySpec` immediately); it is never persisted anywhere.
 *
 * ## Failure model
 *
 * GCM is authenticated: a wrong passphrase (or any ciphertext/salt/nonce
 * tampering) fails the tag check and surfaces as
 * [BackupSecretsWrongPassphraseException] — one user-facing "wrong passphrase"
 * outcome, never a crash. Structurally broken envelopes (bad base64, unknown
 * envelope version) surface as [BackupSecretsFormatException].
 */
object BackupSecretsCodec {

    /** Envelope format version stamped into [SecretsEnvelope.v]. */
    const val ENVELOPE_VERSION = 1

    /** PBKDF2 work factor (the settled Wave-3 decision — 600,000). */
    const val KDF_ITERATIONS = 600_000

    /** Random PBKDF2 salt length in bytes. */
    const val SALT_BYTES = 16

    /** Random GCM nonce length in bytes. */
    const val NONCE_BYTES = 12

    /** Derived AES key size in bits. */
    const val KEY_BITS = 256

    /** GCM authentication tag size in bits. */
    const val GCM_TAG_BITS = 128

    /** Below this the KDF would be cheap enough that tampering pays — reject rather than honor. */
    private const val MIN_ACCEPTED_ITERATIONS = 100_000

    /**
     * Above this a hostile `kdfIterations` is a denial-of-service lever: PBKDF2
     * cost scales linearly with the count, so an attacker-crafted envelope
     * declaring e.g. `kdfIterations = 2_000_000_000` would pin the import
     * flow's CPU for minutes-to-hours per attempt — the import dialog offers no
     * cancel. Decode REJECTS above this ceiling (as [BackupSecretsFormatException])
     * rather than honoring it; the value sits ~3x [KDF_ITERATIONS] (600k) so
     * any plausible future work-factor bump still decodes.
     */
    private const val MAX_ACCEPTED_ITERATIONS = 2_000_000

    private val secureRandom = SecureRandom()

    /**
     * The payload JSON codec: compact (the payload is ciphertext-adjacent, no
     * human reads it) and lenient on decode for forward-compatible field
     * additions, mirroring the slice stores' own JSON configs.
     */
    private val payloadJson = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Encrypts [plain] under [passphrase]. Every call draws a fresh random
     * salt + nonce, so encrypting the same payload twice yields different
     * envelopes (no linkage).
     *
     * Does NOT consume [passphrase] — the caller owns zeroing it (the dialog
     * may keep the entry up for a retry); the internal `PBEKeySpec` is cleared
     * before this returns.
     */
    fun encrypt(plain: BackupSecrets, passphrase: CharArray): SecretsEnvelope {
        require(passphrase.isNotEmpty()) { "Passphrase must not be empty" }
        val plaintext = payloadJson.encodeToString(BackupSecrets.serializer(), plain).encodeToByteArray()

        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val nonce = ByteArray(NONCE_BYTES).also { secureRandom.nextBytes(it) }
        val key = deriveKey(passphrase, salt, KDF_ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(plaintext)

        return SecretsEnvelope(
            v = ENVELOPE_VERSION,
            kdfSaltB64 = java.util.Base64.getEncoder().encodeToString(salt),
            kdfIterations = KDF_ITERATIONS,
            nonceB64 = java.util.Base64.getEncoder().encodeToString(nonce),
            ciphertextB64 = java.util.Base64.getEncoder().encodeToString(ciphertext),
        )
    }

    /**
     * Decrypts [envelope] with [passphrase].
     *
     * @throws BackupSecretsWrongPassphraseException when the GCM tag check
     *   fails — wrong passphrase, or any tampering with the stored
     *   salt/nonce/ciphertext. Intentionally one outcome: the envelope reveals
     *   nothing about which one it was.
     * @throws BackupSecretsFormatException when the envelope is structurally
     *   broken (bad base64, unknown [SecretsEnvelope.v], implausible KDF
     *   parameters, undecodable plaintext).
     */
    fun decrypt(envelope: SecretsEnvelope, passphrase: CharArray): BackupSecrets {
        if (envelope.v != ENVELOPE_VERSION) {
            throw BackupSecretsFormatException("Unsupported secrets envelope version ${envelope.v}")
        }
        if (envelope.kdfIterations < MIN_ACCEPTED_ITERATIONS ||
            envelope.kdfIterations > MAX_ACCEPTED_ITERATIONS
        ) {
            throw BackupSecretsFormatException("Implausible kdfIterations ${envelope.kdfIterations}")
        }
        val salt = envelope.kdfSaltB64.decodeBase64()
            ?: throw BackupSecretsFormatException("kdfSaltB64 is not valid base64")
        val nonce = envelope.nonceB64.decodeBase64()
            ?: throw BackupSecretsFormatException("nonceB64 is not valid base64")
        val ciphertext = envelope.ciphertextB64.decodeBase64()
            ?: throw BackupSecretsFormatException("ciphertextB64 is not valid base64")
        if (salt.size != SALT_BYTES) throw BackupSecretsFormatException("Salt must be $SALT_BYTES bytes")
        if (nonce.size != NONCE_BYTES) throw BackupSecretsFormatException("Nonce must be $NONCE_BYTES bytes")

        val key = deriveKey(passphrase, salt, envelope.kdfIterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        val plaintext = try {
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            // AEADBadTagException is the wrong-passphrase shape; any other
            // crypto failure at this point is equally "cannot decrypt". One
            // typed outcome either way — never the raw JCE exception.
            throw BackupSecretsWrongPassphraseException(cause = e)
        }

        val json = plaintext.decodeToString()
        return try {
            payloadJson.decodeFromString(BackupSecrets.serializer(), json)
        } catch (e: Exception) {
            throw BackupSecretsFormatException("Decrypted payload is not a valid BackupSecrets", e)
        }
    }

    /** PBKDF2-HMAC-SHA256 → [KEY_BITS]-bit AES key. Clears the spec before returning. */
    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return try {
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun String.decodeBase64(): ByteArray? = try {
        java.util.Base64.getDecoder().decode(this)
    } catch (_: IllegalArgumentException) {
        null
    }
}

/**
 * Base type for the two typed failures [BackupSecretsCodec] reports — the
 * import UI maps these to inline retry outcomes instead of crashing.
 */
open class BackupSecretsException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * The GCM auth tag did not verify: the passphrase is wrong, or the stored
 * salt/nonce/ciphertext were tampered with. Deliberately one user-facing
 * outcome ("wrong passphrase") — the envelope cannot distinguish them.
 */
class BackupSecretsWrongPassphraseException(cause: Throwable? = null) :
    BackupSecretsException("Decryption failed — wrong passphrase or corrupted secrets block", cause)

/** The envelope is structurally broken (bad base64, unknown version, bad payload). */
class BackupSecretsFormatException(message: String, cause: Throwable? = null) :
    BackupSecretsException(message, cause)
