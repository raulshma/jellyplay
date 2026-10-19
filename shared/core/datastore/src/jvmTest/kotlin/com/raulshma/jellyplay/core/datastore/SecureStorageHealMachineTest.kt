package com.raulshma.jellyplay.core.datastore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the self-heal arms of [SecureStorageHealMachine] — the two secure-storage
 * crash fixes: 370354c49 (undecryptable state heals by wipe-and-restart instead
 * of crashing the launch) and f5b667b6c (reopen only when the delete actually
 * succeeded; the fallback is a process-local in-memory store). Fake operations
 * only; the androidMain adapter supplies the real Context /
 * EncryptedSharedPreferences operations through this exact machine contract.
 */
class SecureStorageHealMachineTest {

    /**
     * String-seam fake of the encrypted backend: one instance per open().
     * Poisoning makes reads/writes throw the way a restored-corrupt-keyset
     * file does on every access.
     */
    private class FakeEncryptedStore {
        var poisoned = false
        val values = HashMap<String, String?>()

        private fun checkReadable() {
            check(!poisoned) { "Signature/MAC verification failed" }
        }

        fun getString(key: String, defValue: String?): String? {
            checkReadable()
            return values[key] ?: defValue
        }

        fun putString(key: String, value: String?) {
            checkReadable()
            if (value == null) values.remove(key) else values[key] = value
        }

        fun remove(key: String) {
            checkReadable()
            values.remove(key)
        }
    }

    /**
     * The adapter mapping under test, mirroring androidMain's
     * AndroidSecureKeyValueStorage 1:1 — machine + hostable stores behind
     * [SecureKeyValueStorage] — so every arm asserts what the interface
     * reports, not machine internals. `null` entries in [openOutcomes] make
     * that open() throw (keystore unavailable).
     */
    private class Harness(
        openOutcomes: List<FakeEncryptedStore?> = listOf(FakeEncryptedStore()),
        initialDeleteSucceeds: Boolean = true,
    ) : SecureKeyValueStorage {

        var deleteSucceeds = initialDeleteSucceeds
        var deleteThrows = false

        private val outcomes = ArrayDeque(openOutcomes)
        var openAttempts = 0
            private set
        var deletes = 0
            private set
        val opened = mutableListOf<FakeEncryptedStore>()
        val fallback = FakeEncryptedStore()

        private val machine = SecureStorageHealMachine(
            fallback = fallback,
            open = {
                openAttempts++
                val next = outcomes.removeFirstOrNull()
                    ?: throw IllegalStateException("keystore unavailable")
                opened += next
                next
            },
            delete = {
                deletes++
                if (deleteThrows) throw IllegalStateException("delete failed")
                deleteSucceeds
            },
        )

        override fun getString(key: String, defValue: String?): String? =
            machine.guarded { store -> store.getString(key, defValue) }

        override fun putString(key: String, value: String?) {
            machine.guarded { store -> store.putString(key, value) }
        }

        override fun remove(key: String) {
            machine.guarded { store -> store.remove(key) }
        }
    }

    @Test
    fun `clean open serves reads and writes on the encrypted store`() {
        val harness = Harness()
        harness.putString("k", "v")
        assertEquals("v", harness.getString("k"))
        // No heal was ever attempted.
        assertEquals(0, harness.deletes)
        assertEquals(1, harness.openAttempts)
        assertEquals(mapOf<String, String?>("k" to "v"), harness.opened.single().values)
        assertTrue(harness.fallback.values.isEmpty())
    }

    @Test
    fun `poisoned constructor open resets and reopens when the delete succeeds`() {
        val harness = Harness(listOf<FakeEncryptedStore?>(null, FakeEncryptedStore()))

        // The constructor heal already ran: the reopened store serves.
        harness.putString("token", "v2")
        assertEquals("v2", harness.getString("token"))
        assertEquals(1, harness.deletes)
        assertEquals(2, harness.openAttempts)
        assertEquals(mapOf<String, String?>("token" to "v2"), harness.opened.single().values)
        assertTrue(harness.fallback.values.isEmpty())
    }

    @Test
    fun `a backing that opens clean but reads poisoned heals on the first access`() {
        val poisoned = FakeEncryptedStore()
        val reopened = FakeEncryptedStore()
        // Open succeeds, but every access on the opened store throws.
        poisoned.poisoned = true
        val harness = Harness(listOf(poisoned, reopened))

        // Constructor open succeeded; the first write throws and heals through
        // the guard: delete succeeds → reopen → the retry lands there.
        harness.putString("token", "v2")
        assertEquals("v2", harness.getString("token"))
        assertEquals(1, harness.deletes)
        assertEquals(2, harness.openAttempts)
        assertEquals(mapOf<String, String?>("token" to "v2"), reopened.values)
        assertTrue(poisoned.values.isEmpty())
        assertTrue(harness.fallback.values.isEmpty())
    }

    @Test
    fun `poisoned constructor open with a failing delete falls back in memory`() {
        val harness = Harness(listOf<FakeEncryptedStore?>(null), initialDeleteSucceeds = false)

        // The interface still reports — from the in-memory fallback, never crashing.
        harness.putString("token", "v2")
        assertEquals("v2", harness.getString("token"))
        assertEquals("v2", harness.fallback.values["token"])
        // Deletion-checked: a failed delete must NOT trigger a reopen.
        assertEquals(1, harness.deletes)
        assertEquals(1, harness.openAttempts)
        // The fallback keeps serving; repeated traffic stays crash-free.
        repeat(3) { harness.putString("k$it", "$it") }
        assertEquals("2", harness.getString("k2"))
    }

    @Test
    fun `delete success with a failing reopen falls back in memory`() {
        val harness = Harness(listOf(FakeEncryptedStore(), null))
        harness.opened.single().poisoned = true

        // The read guard healed: delete succeeded, reopen threw → fallback
        // serves (key absent → the default comes back).
        assertEquals("def", harness.getString("token", "def"))
        assertEquals(2, harness.openAttempts)
        assertEquals(1, harness.deletes)
        // Subsequent writes land in the fallback.
        harness.putString("token", "v2")
        assertEquals("v2", harness.fallback.values["token"])
    }

    @Test
    fun `a mid-life write that throws heals through the write guard`() {
        val harness = Harness()
        harness.deleteSucceeds = false
        harness.opened.single().poisoned = true

        harness.putString("token", "v2")
        assertEquals("v2", harness.getString("token"))
        assertEquals("v2", harness.fallback.values["token"])
        // The failed delete again barred the reopen.
        assertEquals(1, harness.deletes)
        assertEquals(1, harness.openAttempts)
    }

    @Test
    fun `a throwing delete counts as not-deleted and falls back in memory`() {
        val harness = Harness()
        harness.deleteThrows = true
        harness.opened.single().poisoned = true

        assertEquals("def", harness.getString("k", "def"))
        assertEquals(1, harness.deletes)
        assertEquals(1, harness.openAttempts)
    }

    @Test
    fun `post-fallback writes and reads route only to the in-memory fallback`() {
        val harness = Harness(listOf<FakeEncryptedStore?>(null), initialDeleteSucceeds = false)

        harness.putString("a", "1")
        harness.putString("b", "2")
        assertEquals("1", harness.getString("a"))
        harness.remove("a")
        assertNull(harness.getString("a"))
        assertEquals("2", harness.getString("b"))
        // The fallback is process-local: nothing ever reached the encrypted seam.
        assertTrue(harness.opened.isEmpty())
        assertEquals(mapOf<String, String?>("b" to "2"), harness.fallback.values)
    }

    @Test
    fun `a guard retries once after healing and lets a second throw propagate`() {
        val first = FakeEncryptedStore()
        val reopened = FakeEncryptedStore()
        val harness = Harness(listOf(first, reopened))
        first.poisoned = true
        // The reopened store is itself poisoned.
        reopened.poisoned = true

        assertFailsWith<IllegalStateException> { harness.getString("k") }
        // Exactly one heal ran; the retried read threw through instead of looping.
        assertEquals(1, harness.deletes)
        assertEquals(2, harness.openAttempts)
    }
}
