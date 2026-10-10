package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.ServerDao
import com.raulshma.jellyplay.core.database.dao.UserDao
import com.raulshma.jellyplay.core.database.entity.ServerEntity
import com.raulshma.jellyplay.core.database.entity.UserEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The settings-backup server-list seam (Wave 3): [ServerBackupMetadataStore]
 * gathers token-free server rows + user names, and upserts rows WITHOUT
 * session material on restore. Regression-critical because this seam is the
 * one path a token could leak into a backup through — the asserts pin that
 * inserted rows carry null accessToken/userId and that the address-collision
 * and blank-field guards hold.
 *
 * DAOs are mockk'd; the Room `withTransaction` extension is stubbed to run its
 * block inline (the AuthRepositoryImplTest pattern).
 */
class ServerBackupMetadataStoreTest {

    private val database: JellyPlayDatabase = mockk(relaxed = true)
    // Relaxed: insert/update answers aren't the assertion target — the
    // captured slots are.
    private val serverDao: ServerDao = mockk(relaxed = true)
    private val userDao: UserDao = mockk(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var store: ServerBackupMetadataStore

    @BeforeTest
    fun setup() {
        mockkStatic("com.raulshma.jellyplay.core.data.repository.RoomTransactionsKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
        store = ServerBackupMetadataStore(database, serverDao, userDao, json)
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic("com.raulshma.jellyplay.core.data.repository.RoomTransactionsKt")
    }

    // ------------------------------------------------------------ gather

    @Test
    fun `gatherServers maps rows and user names without touching tokens`() = runTest {
        every { serverDao.getAllServers() } returns flowOf(
            listOf(
                ServerEntity(
                    id = "srv-1",
                    name = "Home",
                    address = "https://home.local",
                    userId = "user-1",
                    accessToken = "stored-encrypted-token",
                    alternateAddresses = """["https://alt.local","https://lan.local"]""",
                ),
            ),
        )
        coEvery { userDao.getUsersForServerOnce("srv-1") } returns listOf(
            UserEntity(userId = "user-1", serverId = "srv-1", name = "alice", accessToken = "tok"),
            UserEntity(userId = "user-2", serverId = "srv-1", name = "bob", accessToken = "tok"),
        )

        val gathered = store.gatherServers()

        assertEquals(1, gathered.size)
        val server = gathered.single()
        assertEquals("srv-1", server.id)
        assertEquals("Home", server.name)
        assertEquals("https://home.local", server.address)
        assertEquals(listOf("https://alt.local", "https://lan.local"), server.alternateAddresses)
        assertEquals(listOf("alice", "bob"), server.userNames)
        // The seam never reads token columns — nothing to assert beyond the
        // mapping (ServerBackupMetadata has no token field to hold one).
    }

    @Test
    fun `gatherServers tolerates an unparseable alternates column`() = runTest {
        every { serverDao.getAllServers() } returns flowOf(
            listOf(ServerEntity(id = "srv-1", name = "Home", address = "https://home.local", alternateAddresses = "not-json")),
        )
        coEvery { userDao.getUsersForServerOnce(any()) } returns emptyList()

        assertTrue(store.gatherServers().single().alternateAddresses.isEmpty())
    }

    // ------------------------------------------------------------ restore

    @Test
    fun `restore inserts new rows without session material and counts inserts`() = runTest {
        coEvery { serverDao.getServerByAddress(any()) } returns null
        coEvery { serverDao.getServerById(any()) } returns null

        val inserted = store.restoreServerMetadata(
            listOf(
                ServerBackupMetadata(
                    id = "srv-new",
                    name = "New",
                    address = "https://new.local",
                    alternateAddresses = listOf("https://alt.new"),
                    userNames = listOf("alice"),
                ),
            ),
        )

        assertEquals(1, inserted)
        val slot = slot<ServerEntity>()
        coVerify(exactly = 1) { serverDao.insertServer(capture(slot)) }
        val row = slot.captured
        assertEquals("srv-new", row.id)
        assertEquals("https://new.local", row.address)
        assertEquals("""["https://alt.new"]""", row.alternateAddresses)
        assertNull(row.accessToken, "a restored row must never carry a token")
        assertNull(row.userId, "a restored row must never bind a user")
    }

    @Test
    fun `restore updates an existing row but preserves its session state`() = runTest {
        coEvery { serverDao.getServerByAddress("https://home.local") } returns null
        coEvery { serverDao.getServerById("srv-1") } returns ServerEntity(
            id = "srv-1",
            name = "Old Name",
            address = "https://old.local",
            userId = "user-1",
            accessToken = "stored-encrypted-token",
            alternateAddresses = null,
            lastConnected = 1234L,
        )

        val inserted = store.restoreServerMetadata(
            listOf(
                ServerBackupMetadata(
                    id = "srv-1",
                    name = "Home",
                    address = "https://home.local",
                    alternateAddresses = listOf("https://alt.local"),
                ),
            ),
        )

        assertEquals(0, inserted, "an update is a reconciliation, not an insert")
        val slot = slot<ServerEntity>()
        coVerify(exactly = 1) { serverDao.updateServer(capture(slot)) }
        val row = slot.captured
        assertEquals("Home", row.name)
        assertEquals("https://home.local", row.address)
        assertEquals("""["https://alt.local"]""", row.alternateAddresses)
        assertEquals("user-1", row.userId, "the existing session binding is preserved")
        assertEquals("stored-encrypted-token", row.accessToken, "the existing (encrypted) token is preserved")
        assertEquals(1234L, row.lastConnected, "recency is preserved")
    }

    @Test
    fun `restore skips an entry whose address belongs to a different server row`() = runTest {
        coEvery { serverDao.getServerByAddress("https://taken.local") } returns ServerEntity(
            id = "srv-other",
            name = "Other",
            address = "https://taken.local",
        )

        val inserted = store.restoreServerMetadata(
            listOf(ServerBackupMetadata(id = "srv-new", name = "New", address = "https://taken.local")),
        )

        assertEquals(0, inserted)
        coVerify(exactly = 0) { serverDao.insertServer(any()) }
        coVerify(exactly = 0) { serverDao.updateServer(any()) }
    }

    @Test
    fun `restore skips blank ids and addresses`() = runTest {
        val inserted = store.restoreServerMetadata(
            listOf(
                ServerBackupMetadata(id = "", name = "NoId", address = "https://x.local"),
                ServerBackupMetadata(id = "srv-1", name = "NoAddress", address = "  "),
            ),
        )

        assertEquals(0, inserted)
        coVerify(exactly = 0) { serverDao.insertServer(any()) }
    }

    @Test
    fun `restore keeps an existing row's alternates when the backup carries none`() = runTest {
        coEvery { serverDao.getServerByAddress(any()) } returns null
        coEvery { serverDao.getServerById("srv-1") } returns ServerEntity(
            id = "srv-1",
            name = "Home",
            address = "https://home.local",
            alternateAddresses = """["https://keep.local"]""",
        )

        store.restoreServerMetadata(
            listOf(ServerBackupMetadata(id = "srv-1", name = "Home", address = "https://home.local", alternateAddresses = emptyList())),
        )

        val slot = slot<ServerEntity>()
        coVerify(exactly = 1) { serverDao.updateServer(capture(slot)) }
        assertEquals("""["https://keep.local"]""", slot.captured.alternateAddresses)
    }
}
