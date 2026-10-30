package com.raulshma.jellyplay.core.database.crypto

import com.raulshma.jellyplay.core.database.crypto.JvmTokenCipher

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Pure-JVM tests for [TokenCipher]. Uses [JvmTokenCipher.forTestingWithPersistentKey] to bypass
 * the Android Keystore (which isn't registered in plain JUnit/Robolectric JVM tests).
 *
 * The Keystore-backed production path (`TokenCipher(context)`) is exercised in instrumented
 * tests on real devices; here we test only the encryption logic.
 */
class TokenCipherTest {

    private lateinit var cipher: TokenCipher

    @BeforeTest
    fun setup() {
        cipher = JvmTokenCipher.forTestingWithPersistentKey()
    }

    @Test
    fun `encrypt then decrypt round-trips a non-empty plaintext`() {
        val plaintext = "jellyfin-secret-token-abc123"
        val encrypted = cipher.encrypt(plaintext)!!

        assertNotEquals(plaintext, encrypted)
        assertTrue(encrypted.startsWith("v1\$"), "Ciphertext must be prefixed with v1\$")
        assertEquals(plaintext, cipher.decrypt(encrypted))
    }

    @Test
    fun `encrypt is idempotent`() {
        val plaintext = "token-xyz"
        val once = cipher.encrypt(plaintext)!!
        val twice = cipher.encrypt(once)!!
        // Second call must observe the v1$ prefix and return the input unchanged.
        assertEquals(once, twice)
    }

    @Test
    fun `decrypt returns plaintext input unchanged`() {
        // Pre-migration rows still hold plaintext — decrypt must pass them through.
        val plaintext = "legacy-token"
        assertEquals(plaintext, cipher.decrypt(plaintext))
    }

    @Test
    fun `encrypt returns null for null input`() {
        assertNull(cipher.encrypt(null))
    }

    @Test
    fun `encrypt returns empty for empty input`() {
        assertEquals(cipher.encrypt(""), "")
    }

    @Test
    fun `decrypt returns null for null input`() {
        assertNull(cipher.decrypt(null))
    }

    @Test
    fun `decrypt returns empty for empty input`() {
        assertEquals(cipher.decrypt(""), "")
    }

    @Test
    fun `isEncrypted detects v1 prefix`() {
        assertTrue(cipher.isEncrypted("v1\$abc\$def"))
    }

    @Test
    fun `isEncrypted returns false for plaintext`() {
        assertFalse(cipher.isEncrypted("legacy-token"))
        assertFalse(cipher.isEncrypted(null))
    }

    @Test
    fun `two encryptions of the same plaintext produce different ciphertexts`() {
        // Fresh IV per call → ciphertexts must differ (semantic security).
        val plaintext = "same-token"
        val a = cipher.encrypt(plaintext)!!
        val b = cipher.encrypt(plaintext)!!
        assertNotEquals(a, b)
        assertEquals(plaintext, cipher.decrypt(a))
        assertEquals(plaintext, cipher.decrypt(b))
    }

    @Test
    fun `round-trip works for long tokens`() {
        val longToken = "a".repeat(1024)
        val encrypted = cipher.encrypt(longToken)!!
        assertEquals(longToken, cipher.decrypt(encrypted))
    }

    @Test
    fun `round-trip survives a fresh TokenCipher instance with the same persistent key`() {
        // Simulate process restart: when the key persists (Keystore in prod, persistent test
        // key here), a new TokenCipher instance can decrypt ciphertext from the previous one.
        val plaintext = "persists-across-instances"
        val encrypted = cipher.encrypt(plaintext)!!

        val freshCipher = JvmTokenCipher.forTestingWithPersistentKey()
        assertEquals(plaintext, freshCipher.decrypt(encrypted))
    }

    @Test
    fun `decryption with a different key returns null instead of throwing`() {
        val plaintext = "secret"
        val encrypted = cipher.encrypt(plaintext)!!

        val differentKey = JvmTokenCipher.forTesting(
            javax.crypto.spec.SecretKeySpec(ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }, "AES")
        )
        // Key rotated out from under the row (uninstall + data restore, backup
        // restore on a new device): degrade to "no valid token" — the contract
        // every caller already maps to isConnected=false / signed-out — never
        // throw out of a Room flow.
        assertNull(differentKey.decrypt(encrypted))
    }

    @Test
    fun `decrypt returns null for malformed ciphertext`() {
        // v1$ prefix marks it encrypted, but the payload is not base64.
        assertNull(cipher.decrypt("v1\$!!!not-base64!!!\$also-not"))
    }

    @Test
    fun `decrypt returns null for truncated ciphertext`() {
        // Encrypted-looking but missing the iv/ct separator — a row damaged
        // after the fact must degrade, not throw out of a flow mapping.
        assertNull(cipher.decrypt("v1\$only-one-part"))
    }

    @Test
    fun `decrypt still round-trips after a null-degrading failure`() {
        // A poisoned row must not corrupt the cipher's state for other rows.
        val good = cipher.encrypt("still-works")!!
        assertNull(cipher.decrypt("v1\$!!!not-base64!!!\$also-not"))
        assertEquals("still-works", cipher.decrypt(good))
    }
}
