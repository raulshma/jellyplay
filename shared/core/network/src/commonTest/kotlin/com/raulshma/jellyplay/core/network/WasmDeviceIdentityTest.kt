package com.raulshma.jellyplay.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the pure core of the persistent wasm device identity (wave 21C):
 * the canonical-UUID-v4 format gate, the stored/generate/persist decision
 * table, and the localStorage key literal the headless-Edge CDP lane
 * (tools/e2e/web-verify.mjs's DEVICE_ID step) hard-codes — a key rename here
 * must fail this test before it silently breaks the lane. Runs from
 * commonTest (via jvmTest, the only enabled test target); the actual
 * localStorage seam (`persistedOrRandomDeviceId`) lives in wasmJsMain and is
 * browser-verified, not unit-tested (the Seerr-credential-store precedent).
 */
class WasmDeviceIdentityTest {

    // Index map for hand-built fixtures: 8-4-4-4-12 hex, version nibble at
    // 14, variant nibble at 19 — every fixture below is annotated against it.
    private val validV4 = "01234567-89ab-4cde-8f01-2345678901ab"

    @Test
    fun `storage key literal is the e2e lane contract`() {
        assertEquals("jellyplay/device-id", WASM_DEVICE_ID_STORAGE_KEY)
    }

    @Test
    fun `format gate accepts a canonical lowercase v4`() {
        assertTrue(isCanonicalUuidV4Text(validV4), "canonical v4 must pass")
        // Variant nibble covers all four RFC 4122 values (8/9/a/b).
        for (variant in listOf("8", "9", "a", "b")) {
            val id = validV4.replaceRange(19, 20, variant)
            assertTrue(isCanonicalUuidV4Text(id), "variant nibble '$variant' must pass")
        }
    }

    @Test
    fun `format gate rejects non-v4 and non-canonical shapes`() {
        val cases = mapOf(
            "wrong version nibble (v1)" to "01234567-89ab-1cde-8f01-2345678901ab",
            "wrong variant nibble (0)" to "01234567-89ab-4cde-0f01-2345678901ab",
            "uppercase hex" to "01234567-89AB-4cde-8f01-2345678901ab",
            "hyphen misplaced" to "01234567-89ab-4cde8-f01-2345678901ab",
            "one char short" to "0123456-89ab-4cde-8f01-2345678901ab",
            "non-hex letter g" to "01234567-89ab-4cde-8f01-2345678901ag",
            "braced GUID form" to "{01234567-89ab-4cde-8f01-2345678901ab}",
            "urn prefix form" to "urn:uuid:01234567-89ab-4cde-8f01-2345678901ab",
            "empty" to "",
            "null literal from a broken storage" to "null",
            "base64-ish garbage" to "c2VycmVyLXNoaW0=",
        )
        for ((why, value) in cases) {
            assertFalse(isCanonicalUuidV4Text(value), "$why must fail: \"$value\"")
        }
    }

    @Test
    fun `valid stored id is returned verbatim with no generate and no write`() {
        var generated = 0
        var persisted: String? = null
        val resolved = resolveWasmDeviceId(
            stored = validV4,
            generate = { generated += 1; "11111111-2222-4333-8444-555555555555" },
            persist = { persisted = it },
        )
        assertEquals(validV4, resolved, "stored id must be reused verbatim (stable across boots)")
        assertEquals(0, generated, "no generation for a valid stored id")
        assertNull(persisted, "a valid stored id must not be rewritten (write only on first boot)")
    }

    @Test
    fun `absent stored id generates, persists once and returns the generated value`() {
        val fresh = "fedcba98-7654-4321-a098-112233445566"
        val persisted = mutableListOf<String>()
        val resolved = resolveWasmDeviceId(
            stored = null,
            generate = { fresh },
            persist = { persisted += it },
        )
        assertEquals(fresh, resolved, "first boot resolves the generated id")
        assertEquals(listOf(fresh), persisted, "persist invoked exactly once with the generated id")
    }

    @Test
    fun `corrupt stored value is regenerated and overwritten`() {
        val fresh = "abcdef01-2345-4a67-b89c-def012345678"
        val persisted = mutableListOf<String>()
        val resolved = resolveWasmDeviceId(
            stored = "not-a-uuid",
            generate = { fresh },
            persist = { persisted += it },
        )
        assertEquals(fresh, resolved, "foreign text under the key reads as absent")
        assertEquals(listOf(fresh), persisted, "the corrupt entry is overwritten with the fresh id")
    }

    @Test
    fun `throwing storage degrades to a session-only generated id`() {
        val fresh = "01020304-0506-4708-990a-0b0c0d0e0f10"
        val resolved = resolveWasmDeviceId(
            stored = null,
            generate = { fresh },
            persist = { throw IllegalStateException("storage disabled (private mode)") },
        )
        assertEquals(fresh, resolved, "a failed write never disturbs the resolved id")
    }
}
