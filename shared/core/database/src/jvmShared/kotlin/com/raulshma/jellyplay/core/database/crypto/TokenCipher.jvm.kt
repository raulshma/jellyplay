package com.raulshma.jellyplay.core.database.crypto

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * JVM-shared [TokenCipher] body (javax.crypto AES-256-GCM) for the Android and
 * desktop targets; only the [SecretKey] provisioning differs per platform
 * (Android Keystore vs desktop key file).
 */
open class JvmTokenCipher(
    private val secretKeyProvider: () -> SecretKey,
) : TokenCipher {

    private val secretKey: SecretKey by lazy { secretKeyProvider() }

    override fun encrypt(plaintext: String?): String? {
        if (plaintext == null) return null
        if (plaintext.isEmpty()) return plaintext
        if (isEncrypted(plaintext)) return plaintext
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // A fresh random IV per encryption is read back via cipher.iv below.
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return VERSION_PREFIX +
            Base64.getEncoder().withoutPadding().encodeToString(iv) +
            SEPARATOR +
            Base64.getEncoder().withoutPadding().encodeToString(ct)
    }

    override fun decrypt(ciphertext: String?): String? {
        if (ciphertext == null) return null
        if (ciphertext.isEmpty()) return ciphertext
        if (!isEncrypted(ciphertext)) return ciphertext
        val parts = ciphertext.removePrefix(VERSION_PREFIX).split(SEPARATOR)
        require(parts.size == 2) { "Malformed ciphertext" }
        val iv = Base64.getDecoder().decode(parts[0])
        val ct = Base64.getDecoder().decode(parts[1])
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        }
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    override fun isEncrypted(value: String?): Boolean {
        if (value == null) return false
        return value.startsWith(VERSION_PREFIX)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BITS = 128
        private const val VERSION_PREFIX = "v1\$"
        private const val SEPARATOR = "\$"

        /**
         * Build a [TokenCipher] backed by an explicit [SecretKey] instead of a
         * platform store. Production code should NOT call this — it's intended
         * for unit tests running on the JVM.
         */
        fun forTesting(secretKey: SecretKey): TokenCipher =
            JvmTokenCipher(secretKeyProvider = { secretKey })

        /**
         * Convenience for tests: build a [TokenCipher] whose key is generated in-memory and
         * shared between the returned instance and any subsequent calls in the same test, so
         * encrypt/decrypt round-trip across instances.
         */
        fun forTestingWithPersistentKey(): TokenCipher =
            JvmTokenCipher(secretKeyProvider = { persistentTestKey })

        private val persistentTestKey: SecretKey by lazy {
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            SecretKeySpec(bytes, "AES")
        }
    }
}
