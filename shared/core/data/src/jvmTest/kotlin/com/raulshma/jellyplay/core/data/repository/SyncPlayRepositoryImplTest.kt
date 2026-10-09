package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayGroupInfo
import com.raulshma.jellyplay.core.network.api.SyncPlayApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Delegation pins for [SyncPlayRepositoryImpl] (the stateless-forward
 * shape): the family is four stateless members with no cache state, so the
 * only behavior to pin is the argument-exact forward per member — the two
 * reads and the set-new-queue push ride [SyncPlayApiClient], and
 * createSyncPlayGroup delegates to [SyncPlayManager.createGroup] (the
 * create→join-MY-group owner), mapping the joined group to the
 * [SyncPlayGroupInfo] identifying slice.
 */
class SyncPlayRepositoryImplTest {

    private val apiClient: SyncPlayApiClient = mockk(relaxed = true)
    private val syncPlayManager: SyncPlayManager = mockk(relaxed = true)

    private lateinit var repository: SyncPlayRepositoryImpl

    @BeforeTest
    fun setup() {
        repository = SyncPlayRepositoryImpl(
            syncPlayApiClient = apiClient,
            syncPlayManager = syncPlayManager,
        )
    }

    @Test
    fun `getSyncPlayGroups delegates to apiClient`() = runTest {
        val groups = listOf(
            SyncPlayGroup(groupId = "group-1", groupName = "Movie Night", participantCount = 2),
        )
        coEvery { apiClient.getSyncPlayGroups() } returns Result.success(groups)

        val result = repository.getSyncPlayGroups()

        assertTrue(result.isSuccess)
        assertEquals(groups, result.getOrNull())
        coVerify(exactly = 1) { apiClient.getSyncPlayGroups() }
    }

    @Test
    fun `createSyncPlayGroup delegates to the manager and maps to the joined group's identifying slice`() = runTest {
        val joined = SyncPlayGroup(groupId = "group-7", groupName = "Watch Party", participantCount = 1)
        coEvery { syncPlayManager.createGroup("Watch Party") } returns Result.success(joined)

        val result = repository.createSyncPlayGroup("Watch Party")

        assertEquals(
            SyncPlayGroupInfo(groupId = "group-7", groupName = "Watch Party"),
            result.getOrNull(),
        )
        coVerify(exactly = 1) { syncPlayManager.createGroup("Watch Party") }
        // The wire client's bare create verb is NOT the seam's path — the
        // manager owns the create→join choreography.
        coVerify(exactly = 0) { apiClient.createSyncPlayGroup(any()) }
    }

    @Test
    fun `createSyncPlayGroup surfaces the manager's failure`() = runTest {
        coEvery { syncPlayManager.createGroup(any()) } returns Result.failure(IllegalStateException("no session"))

        val result = repository.createSyncPlayGroup("Watch Party")

        assertTrue(result.isFailure)
    }

    @Test
    fun `getSyncPlayInfo delegates groupId through`() = runTest {
        val info = SyncPlayGroupInfo(groupId = "group-7", groupName = "Watch Party")
        coEvery { apiClient.getSyncPlayInfo("group-7") } returns Result.success(info)

        val result = repository.getSyncPlayInfo("group-7")

        assertEquals(info, result.getOrNull())
        coVerify(exactly = 1) { apiClient.getSyncPlayInfo("group-7") }
    }

    @Test
    fun `getSyncPlayInfo default resolves the current group`() = runTest {
        val info = SyncPlayGroupInfo(groupId = "group-7", groupName = "Watch Party")
        coEvery { apiClient.getSyncPlayInfo(null) } returns Result.success(info)

        val result = repository.getSyncPlayInfo()

        assertEquals(info, result.getOrNull())
        coVerify(exactly = 1) { apiClient.getSyncPlayInfo(null) }
    }

    @Test
    fun `syncPlaySetNewQueue delegates every argument`() = runTest {
        coEvery {
            apiClient.syncPlaySetNewQueue(listOf("item-1", "item-2"), "item-1", "media-source-1", 500L)
        } returns Result.success(Unit)

        val result = repository.syncPlaySetNewQueue(
            itemIds = listOf("item-1", "item-2"),
            playingItemId = "item-1",
            mediaSourceId = "media-source-1",
            startPositionTicks = 500L,
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            apiClient.syncPlaySetNewQueue(listOf("item-1", "item-2"), "item-1", "media-source-1", 500L)
        }
    }
}
