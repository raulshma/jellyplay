package com.raulshma.jellyplay.core.datastore.identity

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers server / user / device identity persistence: defaults, atomic
 * session writes, idempotent device-id allocation, and clearSession.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ServerIdentityStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: ServerIdentityStore

    @Before
    fun setup() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = com.raulshma.jellyplay.core.datastore.TestDataStoreProvider.get(context)
        store = ServerIdentityStore(dataStore, scope)
        // Robolectric reuses the same DataStore file across @Test methods in a
        // class; drop any session left over from a prior test so each test
        // starts from a logged-out state.
        store.clearSession()
    }

    @Test
    fun `identity flows emit nulls before any session is set`() = runTest {
        assertNull(store.activeServerId.first())
        assertNull(store.activeUserId.first())
    }

    @Test
    fun `setActiveSession writes both ids atomically`() = runTest {
        store.setActiveSession(serverId = "srv-1", userId = "user-1")
        assertEquals("srv-1", store.activeServerId.first())
        assertEquals("user-1", store.activeUserId.first())
    }

    @Test
    fun `identity snapshot reflects both ids`() = runTest {
        store.setActiveSession("srv-1", "user-1")
        val snap = store.identity.first()
        assertEquals("srv-1", snap.activeServerId)
        assertEquals("user-1", snap.activeUserId)
    }

    @Test
    fun `ensureDeviceId allocates and persists a stable id`() = runTest {
        val first = store.ensureDeviceId()
        assertNotNull(first)
        // Subsequent calls must return the same id — allocation is idempotent.
        assertEquals(first, store.ensureDeviceId())
        assertEquals(first, store.deviceId.first())
    }

    @Test
    fun `clearSession drops server and user but keeps device id`() = runTest {
        store.ensureDeviceId()
        store.setActiveSession("srv-1", "user-1")

        store.clearSession()

        assertNull(store.activeServerId.first())
        assertNull(store.activeUserId.first())
        assertNotNull(store.deviceId.first())
    }

    @Test
    fun `setActiveServer and setActiveUser can be set independently`() = runTest {
        store.setActiveServer("srv-2")
        store.setActiveUser("user-2")
        assertEquals("srv-2", store.activeServerId.first())
        assertEquals("user-2", store.activeUserId.first())
    }
}
