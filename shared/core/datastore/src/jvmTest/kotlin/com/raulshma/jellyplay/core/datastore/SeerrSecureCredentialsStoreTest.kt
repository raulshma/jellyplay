package com.raulshma.jellyplay.core.datastore

import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the [SeerrSecureCredentialsStore] contract: empty-string defaults on a
 * fresh encrypted file, per-secret read/write round-trips, and the api-key /
 * session-cookie memoisation invariants — the cache is only valid because this
 * class is the sole owner of the file, so a write through the setters must
 * refresh the memo, [clearAll] must drop it, and a hot read must keep serving
 * the memoised value even if the underlying storage is mutated out-of-band.
 * The password field is intentionally uncached — every read hits storage.
 */
class SeerrSecureCredentialsStoreTest {

    private lateinit var storage: FakeSecureKeyValueStorage
    private lateinit var store: SeerrSecureCredentialsStore

    @BeforeTest
    fun setup() {
        storage = FakeSecureKeyValueStorage()
        store = SeerrSecureCredentialsStore(storage)
    }

    @Test
    fun `fresh storage reads empty strings for every secret`() = runTest {
        assertEquals("", store.getApiKey())
        assertEquals("", store.getPassword())
        assertEquals("", store.getSessionCookie())
    }

    @Test
    fun `password round-trips without caching`() = runTest {
        store.setPassword("hunter2")
        assertEquals("hunter2", store.getPassword())

        // Uncached: a behind-the-back storage mutation is observed immediately.
        storage.raw["password"] = "rotated"
        assertEquals("rotated", store.getPassword())
    }

    @Test
    fun `api key round-trips through the setter`() = runTest {
        store.setApiKey("key-1")
        assertEquals("key-1", store.getApiKey())
    }

    @Test
    fun `api key memo survives an out-of-band storage mutation`() = runTest {
        store.setApiKey("key-1")
        assertEquals("key-1", store.getApiKey())

        // Sole-owner contract: out-of-band writes are NOT part of the contract,
        // so the memoised value keeps being served.
        storage.raw["api_key"] = "out-of-band"
        assertEquals("key-1", store.getApiKey())
    }

    @Test
    fun `setting the api key refreshes the memo`() = runTest {
        store.setApiKey("key-1")
        assertEquals("key-1", store.getApiKey())

        store.setApiKey("key-2")
        assertEquals("key-2", store.getApiKey())
    }

    @Test
    fun `session cookie memo is refreshed by the setter and served hot`() = runTest {
        store.setSessionCookie("cookie-1")
        assertEquals("cookie-1", store.getSessionCookie())

        storage.raw["session_cookie"] = "out-of-band"
        assertEquals("cookie-1", store.getSessionCookie())

        store.setSessionCookie("cookie-2")
        assertEquals("cookie-2", store.getSessionCookie())
    }

    @Test
    fun `clearAll removes every secret`() = runTest {
        store.setApiKey("key-1")
        store.setPassword("hunter2")
        store.setSessionCookie("cookie-1")
        assertEquals("key-1", store.getApiKey())
        assertEquals("cookie-1", store.getSessionCookie())

        store.clearAll()

        // Storage entries are gone and hot reads fall back to storage (empty),
        // not a stale memo.
        assertEquals("", store.getApiKey())
        assertEquals("", store.getPassword())
        assertEquals("", store.getSessionCookie())
        assertEquals(null, storage.raw["api_key"])
        assertEquals(null, storage.raw["password"])
        assertEquals(null, storage.raw["session_cookie"])
    }

    @Test
    fun `clearAll drops the memos so a behind-the-back write is observed again`() = runTest {
        store.setApiKey("key-1")
        assertEquals("key-1", store.getApiKey())

        store.clearAll()
        // No read between clearAll and the re-seed: the memo must still be
        // dropped, so this read goes to storage and observes the new value.
        storage.raw["api_key"] = "re-seeded"

        assertEquals("re-seeded", store.getApiKey())
    }
}
