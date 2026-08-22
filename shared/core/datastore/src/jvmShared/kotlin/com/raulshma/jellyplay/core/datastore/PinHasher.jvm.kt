package com.raulshma.jellyplay.core.datastore

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * JVM/Android actual (java.security PBKDF2 + SHA-256) for the [PinHasher]
 * contract. Extracted from [UserPreferencesStore] so the security-critical
 * PBKDF2 + constant-time comparison logic can be unit tested without an
 * Android `Context`.
 *
 * Current hash format: `v2$<iterations>$<saltHex>$<hashHex>` where the hash
 * is derived via PBKDF2-HMAC-SHA256 (310k iterations, 128-bit salt, 256-bit
 * key). 310k iterations balances security with faster UI response, as 600k is too slow
 * for quick PIN unlocks on some devices.
 *
 * Legacy format (unsalted SHA-256, 64 lowercase hex chars) is still accepted
 * by [verify] so existing users aren't locked out after upgrade. Callers can
 * detect legacy hashes via [needsMigration] and silently re-hash with [hash]
 * on the next successful unlock.
 *
 * Comparison is constant-time via [MessageDigest.isEqual] to avoid timing
 * side-channels even for legacy hashes.
 */
internal actual object PinHasher {
    internal actual val V2_PREFIX = "v2\$"
    internal actual val PBKDF2_ITERATIONS = 310_000
    internal actual val PBKDF2_KEY_BITS = 256
    internal actual val SALT_BYTES = 16

    private val secureRandom = SecureRandom()
    private val sha256 by lazy { MessageDigest.getInstance("SHA-256") }

    actual fun hash(pin: String): String {
        require(pin.isNotEmpty()) { "PIN must not be empty" }
        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val hashBytes = pbkdf2(pin, salt, PBKDF2_ITERATIONS)
        return "$V2_PREFIX$PBKDF2_ITERATIONS\$${salt.toHex()}\$${hashBytes.toHex()}"
    }

    actual fun verify(input: String, storedHash: String?): Boolean {
        if (storedHash == null || input.isEmpty()) return false
        return when {
            storedHash.startsWith(V2_PREFIX) -> verifyPbkdf2(input, storedHash)
            else -> verifyLegacySha256(input, storedHash)
        }
    }

    actual fun needsMigration(storedHash: String?): Boolean {
        if (storedHash == null) return false
        if (!storedHash.startsWith(V2_PREFIX)) return true
        val parts = storedHash.split("$")
        if (parts.size == 4) {
            val iterations = parts[1].toIntOrNull()
            if (iterations != null && iterations != PBKDF2_ITERATIONS) {
                return true
            }
        }
        return false
    }

    private fun verifyPbkdf2(input: String, stored: String): Boolean {
        val parts = stored.split("$")
        if (parts.size != 4 || parts[0] != "v2") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = parts[2].fromHex() ?: return false
        val expected = parts[3].fromHex() ?: return false
        val actual = pbkdf2(input, salt, iterations)
        return constantTimeEquals(expected, actual)
    }

    private fun verifyLegacySha256(input: String, storedHex: String): Boolean {
        val expected = storedHex.fromHex() ?: return false
        val digest = (sha256.clone() as MessageDigest)
        val actual = digest.digest(input.toByteArray())
        return constantTimeEquals(expected, actual)
    }

    private fun pbkdf2(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, PBKDF2_KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray? {
        if (length % 2 != 0) return null
        return try {
            ByteArray(length / 2) { i ->
                val hi = Character.digit(this[i * 2], 16)
                val lo = Character.digit(this[i * 2 + 1], 16)
                if (hi < 0 || lo < 0) return null
                ((hi shl 4) + lo).toByte()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        // MessageDigest.isEqual is specified to run in constant time relative
        // to the byte length, avoiding early-exit timing leaks.
        return MessageDigest.isEqual(a, b)
    }
}
