package com.raulshma.jellyplay.core.datastore.identity

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Covers the persisted session-identity machine: the sealed [PersistedIdentity]
 * read (defaults, the blank==absent legacy reads, the pure [PersistedIdentity.resolve]
 * decision core), the canonical phase-shaped write commands, idempotent
 * device-id allocation, and the clears.
 */
class ServerIdentityStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: ServerIdentityStore

    @BeforeTest
    fun setup() = kotlinx.coroutines.runBlocking {
        val dataStore = TestDataStoreProvider.get()
        store = ServerIdentityStore(dataStore, scope)
        // Robolectric reuses the same DataStore file across @Test methods in a
        // class; drop any session left over from a prior test so each test
        // starts from a logged-out state (this also removes legacy blank
        // sentinels a previous test may have planted).
        store.clearSession()
    }

    // ── The typed read ──

    @Test
    fun `identity reads NoSession before any session is set`() = runTest {
        assertEquals(PersistedIdentity.NoSession, store.identity.first())
        assertNull(store.activeServerId.first())
        assertNull(store.activeUserId.first())
    }

    @Test
    fun `setActiveSession writes the ActiveSession phase atomically`() = runTest {
        store.setActiveSession(serverId = "srv-1", userId = "user-1")
        assertEquals(PersistedIdentity.ActiveSession("srv-1", "user-1"), store.identity.first())
        assertEquals("srv-1", store.activeServerId.first())
        assertEquals("user-1", store.activeUserId.first())
    }

    @Test
    fun `legacy blank sentinels read as user-absent (blank == absent pin)`() = runTest {
        // The former write idioms persisted "" sentinels on disk
        // (setActiveSession("", "") / setActiveUser("")); existing installs
        // carry them. The typed read must apply blank == absent uniformly
        // for BOTH keys — the old raw user flow leaked a blank id downstream.
        TestDataStoreProvider.get().edit { prefs ->
            prefs[stringPreferencesKey("active_server_id")] = "srv-1"
            prefs[stringPreferencesKey("active_user_id")] = ""
        }
        assertEquals(PersistedIdentity.ServerSelected("srv-1"), store.identity.first())
        assertEquals("srv-1", store.activeServerId.first())
        assertNull(store.activeUserId.first())

        // A blank server key degrades the whole phase: no usable selection
        // (and the user key alone is unrestorable — it reads as signed out).
        TestDataStoreProvider.get().edit { prefs ->
            prefs[stringPreferencesKey("active_server_id")] = ""
            prefs[stringPreferencesKey("active_user_id")] = "user-1"
        }
        assertEquals(PersistedIdentity.NoSession, store.identity.first())
        assertNull(store.activeServerId.first())
        assertNull(store.activeUserId.first())
    }

    @Test
    fun `resolve is total over the raw key combinations`() = runTest {
        // The pure decision core (convention §3) — pinned without DataStore.
        assertEquals(PersistedIdentity.NoSession, PersistedIdentity.resolve(null, null))
        assertEquals(PersistedIdentity.NoSession, PersistedIdentity.resolve(null, "user-1"))
        assertEquals(PersistedIdentity.NoSession, PersistedIdentity.resolve("", ""))
        assertEquals(PersistedIdentity.ServerSelected("srv-1"), PersistedIdentity.resolve("srv-1", null))
        assertEquals(PersistedIdentity.ServerSelected("srv-1"), PersistedIdentity.resolve("srv-1", ""))
        assertEquals(
            PersistedIdentity.ActiveSession("srv-1", "user-1"),
            PersistedIdentity.resolve("srv-1", "user-1"),
        )
    }

    // ── Device id ──

    @Test
    fun `ensureDeviceId allocates and persists a stable id`() = runTest {
        val first = store.ensureDeviceId()
        assertNotNull(first)
        // Subsequent calls must return the same id — allocation is idempotent.
        assertEquals(first, store.ensureDeviceId())
        assertEquals(first, store.deviceId.first())
    }

    // ── Clears ──

    @Test
    fun `clearSession drops server and user but keeps device id and lands NoSession`() = runTest {
        store.ensureDeviceId()
        store.setActiveSession("srv-1", "user-1")

        store.clearSession()

        assertEquals(PersistedIdentity.NoSession, store.identity.first())
        assertNull(store.activeServerId.first())
        assertNull(store.activeUserId.first())
        assertNotNull(store.deviceId.first())
    }

    @Test
    fun `clearUser leaves ServerSelected (the removeUser pin's store half)`() = runTest {
        store.setActiveSession("srv-1", "user-1")

        store.clearUser()

        assertEquals(PersistedIdentity.ServerSelected("srv-1"), store.identity.first())
        assertEquals("srv-1", store.activeServerId.first())
        assertNull(store.activeUserId.first())
    }

    // ── Canonical write commands ──

    @Test
    fun `setSelectedServer persists ServerSelected and drops a stale user key (BUG 1 store half)`() = runTest {
        store.setActiveSession("srv-A", "user-A")

        store.setSelectedServer("srv-B")

        // The stale user key is gone IN THE SAME edit: a remembered user of
        // the previous server must not ride the new selection (an
        // ActiveSession(newServer, oldUser) disk pair would restore a token
        // minted for the other server).
        assertEquals(PersistedIdentity.ServerSelected("srv-B"), store.identity.first())
        assertEquals("srv-B", store.activeServerId.first())
        assertNull(store.activeUserId.first())
    }

    @Test
    fun `setActiveSession requires non-blank ids (the blank sentinel is unrepresentable)`() = runTest {
        assertFailsWith<IllegalArgumentException> { store.setActiveSession("", "user-1") }
        assertFailsWith<IllegalArgumentException> { store.setActiveSession("srv-1", "") }
        assertFailsWith<IllegalArgumentException> { store.setActiveSession("  ", "user-1") }
        // The requires run before the edit — no partial write, identity
        // untouched.
        assertEquals(PersistedIdentity.NoSession, store.identity.first())
        assertFailsWith<IllegalArgumentException> { store.setSelectedServer(" ") }
    }
}
