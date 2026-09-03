package com.raulshma.jellyplay.core.datastore

import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the [ArrSecureCredentialsStore] contract directly (the *arr preference
 * store tests exercise it only through the [ArrPreferencesStore] bridge):
 * empty-list default on a fresh file, JSON round-trip preserving every field,
 * the non-manual filter on write, per-kind [clearManual] (leaving the other
 * kind untouched), and [clearAll] removing the blob key entirely. Parse
 * failures degrade to an empty list rather than throwing.
 */
class ArrSecureCredentialsStoreTest {

    private lateinit var storage: FakeSecureKeyValueStorage
    private lateinit var store: ArrSecureCredentialsStore

    @BeforeTest
    fun setup() {
        storage = FakeSecureKeyValueStorage()
        store = ArrSecureCredentialsStore(storage)
    }

    private fun server(
        id: String,
        kind: ArrServiceKind,
        isManual: Boolean = true,
    ) = ArrServerConfig(
        id = id,
        baseUrl = "http://${kind.name.lowercase()}.local:7878",
        apiKey = "key-$id",
        name = kind.name.lowercase().replaceFirstChar { it.uppercase() },
        kind = kind,
        isManual = isManual,
    )

    @Test
    fun `fresh storage returns an empty manual list`() = runTest {
        assertEquals(emptyList(), store.getManualServers())
    }

    @Test
    fun `set then get round-trips every config field`() = runTest {
        val radarr = server("radarr-1", ArrServiceKind.RADARR)
        val sonarr = server("sonarr-1", ArrServiceKind.SONARR)

        store.setManualServers(listOf(radarr, sonarr))

        assertEquals(listOf(radarr, sonarr), store.getManualServers())
    }

    @Test
    fun `setManualServers drops auto-discovered entries`() = runTest {
        val manual = server("radarr-1", ArrServiceKind.RADARR, isManual = true)
        val discovered = server("radarr-2", ArrServiceKind.RADARR, isManual = false)

        store.setManualServers(listOf(manual, discovered))

        assertEquals(listOf(manual), store.getManualServers())
    }

    @Test
    fun `clearManual removes only the entries of that kind`() = runTest {
        val radarr1 = server("radarr-1", ArrServiceKind.RADARR)
        val radarr2 = server("radarr-2", ArrServiceKind.RADARR)
        val sonarr1 = server("sonarr-1", ArrServiceKind.SONARR)
        store.setManualServers(listOf(radarr1, sonarr1, radarr2))

        store.clearManual(ArrServiceKind.RADARR)

        assertEquals(listOf(sonarr1), store.getManualServers())
    }

    @Test
    fun `clearManual on an empty store writes an empty array rather than removing the key`() = runTest {
        store.clearManual(ArrServiceKind.SONARR)

        // Implementation contract: a rewritten empty JSON array, not a removed
        // key — the next read still degrades to an empty list either way.
        assertEquals(emptyList(), store.getManualServers())
        assertEquals("[]", storage.raw["arr_manual_servers"])
    }

    @Test
    fun `clearAll removes the blob key entirely`() = runTest {
        store.setManualServers(listOf(server("radarr-1", ArrServiceKind.RADARR)))

        store.clearAll()

        assertNull(storage.raw["arr_manual_servers"])
        assertEquals(emptyList(), store.getManualServers())
    }

    @Test
    fun `corrupt blob degrades to an empty list instead of throwing`() = runTest {
        storage.raw["arr_manual_servers"] = "[{not-a-server"

        val result = store.getManualServers()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `unknown JSON fields in a stored entry are ignored`() = runTest {
        // Forward-compat: a newer build stored an extra field; this build must
        // still decode the entry (ignoreUnknownKeys) and keep the known values.
        storage.raw["arr_manual_servers"] =
            """[{"id":"radarr-1","baseUrl":"http://r.local","apiKey":"k","name":"R","kind":"RADARR","isManual":true,"futureField":42}]"""

        val servers = store.getManualServers()

        assertEquals(1, servers.size)
        assertEquals("radarr-1", servers[0].id)
        assertEquals(ArrServiceKind.RADARR, servers[0].kind)
        assertTrue(servers[0].isManual)
    }
}
