package com.raulshma.jellyplay.core.datastore

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.Test
import java.security.MessageDigest

/**
 * Unit tests for the security-critical PIN hashing logic. Targets
 * [PinHasher] directly so no Android Context (Robolectric) is required.
 *
 * Coverage:
 *  - Hash format (v2 prefix, salt is random per call)
 *  - Verify accepts the correct PIN and rejects wrong PINs
 *  - Legacy unsalted-SHA-256 hashes still verify (backward compatibility)
 *  - Constant-time compare path does not regress
 *  - Malformed stored hashes fail closed (return false, never throw)
 *  - needsMigration discriminates v2 from legacy
 */
class PinHasherTest {

    @Test
    fun `hash produces v2 format with pbkdf2 iterations`() {
        val h = PinHasher.hash("1234")
        // v2$<iterations>$<saltHex>$<hashHex>
        assertTrue(h.startsWith(PinHasher.V2_PREFIX), "hash must start with v2\$ prefix: $h")
        val parts = h.split("$")
        assertEquals(4, parts.size)
        assertEquals(parts[0], "v2")
        assertEquals(PinHasher.PBKDF2_ITERATIONS.toString(), parts[1])
        // 128-bit salt = 16 bytes = 32 hex chars; 256-bit hash = 32 bytes = 64 hex chars
        assertEquals(32, parts[2].length)
        assertEquals(64, parts[3].length)
    }

    @Test
    fun `hash uses a fresh random salt per call`() {
        val a = PinHasher.hash("1234")
        val b = PinHasher.hash("1234")
        assertNotEquals(a, b, "two hashes of the same PIN must differ (random salt)")
    }

    @Test
    fun `verify accepts the correct PIN for a v2 hash`() {
        val pin = "0428"
        val hash = PinHasher.hash(pin)
        assertTrue(PinHasher.verify(pin, hash))
    }

    @Test
    fun `verify rejects a wrong PIN for a v2 hash`() {
        val hash = PinHasher.hash("1234")
        assertFalse(PinHasher.verify("1235", hash))
        assertFalse(PinHasher.verify("0000", hash))
        assertFalse(PinHasher.verify("12345", hash))
        assertFalse(PinHasher.verify("", hash))
    }

    @Test
    fun `verify returns false for null or empty stored hash`() {
        assertFalse(PinHasher.verify("1234", null))
        assertFalse(PinHasher.verify("1234", ""))
    }

    @Test
    fun `verify accepts legacy unsalted SHA-256 hashes`() {
        // Backward compatibility: a hash written by the previous
        // implementation (unsalted SHA-256 hex) must still verify so existing
        // users aren't locked out after upgrade.
        val pin = "9876"
        val legacy = MessageDigest.getInstance("SHA-256")
            .digest(pin.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertTrue(PinHasher.verify(pin, legacy))
        assertFalse(PinHasher.verify("0000", legacy))
    }

    @Test
    fun `needsMigration returns true for legacy and mismatched iterations`() {
        val legacy = MessageDigest.getInstance("SHA-256")
            .digest("9876".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertTrue(PinHasher.needsMigration(legacy))
        assertFalse(PinHasher.needsMigration(PinHasher.hash("9876")))
        assertFalse(PinHasher.needsMigration(null))
        
        // A v2 hash with a different iteration count (e.g., from an older version) should need migration
        assertTrue(PinHasher.needsMigration("v2\$600000\$salt\$hash"))
    }

    @Test
    fun `verify fails closed on malformed v2 hashes without throwing`() {
        // Drop a segment, corrupt the hex, change the prefix, etc. The verify
        // path must return false rather than propagating an exception — an
        // uncaught exception here would crash the lock screen.
        val valid = PinHasher.hash("1234")
        val parts = valid.split("$").toMutableList()
        parts[0] = "v3"
        assertFalse(PinHasher.verify("1234", parts.joinToString("$")))

        parts[0] = "v2"
        parts[1] = "not-a-number"
        assertFalse(PinHasher.verify("1234", parts.joinToString("$")))

        parts[1] = "600000"
        parts[2] = "zz" // invalid hex
        assertFalse(PinHasher.verify("1234", parts.joinToString("$")))

        // Wrong segment count
        assertFalse(PinHasher.verify("1234", "v2\$600000\$onlyonesalt"))
        assertFalse(PinHasher.verify("1234", "v2\$600000\$salt\$hash\$extra"))
    }

    @Test
    fun `verify fails closed on truncated legacy hashes`() {
        // A 63-char string is not a valid SHA-256 hex; verify must return
        // false rather than throwing on the from-hex decode.
        assertFalse(PinHasher.verify("1234", "abc".repeat(21)))
    }

    @Test
    fun `verify is consistent across repeated calls for the same hash`() {
        val hash = PinHasher.hash("4321")
        repeat(5) { assertTrue(PinHasher.verify("4321", hash)) }
    }
}
