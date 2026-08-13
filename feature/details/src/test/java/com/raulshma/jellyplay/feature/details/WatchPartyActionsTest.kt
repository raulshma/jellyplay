package com.raulshma.jellyplay.feature.details

import android.content.Context
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WatchPartyActionsTest {

    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val syncPlayManager: SyncPlayManager = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    private val messages = mutableListOf<DetailMessage>()
    private val messageSink: (DetailMessage) -> Unit = { messages += it }

    private val group = SyncPlayGroup(
        groupId = "g1",
        groupName = "My Movie",
        participantCount = 1,
    )

    @Before
    fun setUp() {
        messages.clear()
        every { context.getString(R.string.detail_msg_watch_party_failed) } returns "couldn't start watch party"
    }

    private fun actions(): WatchPartyActions = WatchPartyActions(
        mediaRepository = mediaRepository,
        syncPlayManager = syncPlayManager,
        context = context,
        messageSink = messageSink,
    )

    // ── Happy path ──────────────────────────────────────────────────────

    @Test
    fun `start success creates group, recovers it by name, joins, seeds queue and emits WatchPartyStarted`() = runTest {
        coEvery { mediaRepository.createSyncPlayGroup(any()) } returns Result.success(Unit)
        coEvery { mediaRepository.getSyncPlayGroups() } returns Result.success(listOf(group))
        coEvery { syncPlayManager.joinGroup(any()) } returns Result.success(Unit)
        coEvery {
            mediaRepository.syncPlaySetNewQueue(any(), any(), any(), any())
        } returns Result.success(Unit)

        val result = actions().start(
            itemId = "m1",
            title = "My Movie",
            mediaSourceId = "src1",
        )

        assertTrue(result.isSuccess)
        assertTrue(messages.contains(DetailMessage.WatchPartyStarted("m1")))
        coVerify(exactly = 1) { mediaRepository.createSyncPlayGroup("My Movie") }
        // Snapshot (pre-create) + recover (post-create) = two reads.
        coVerify(exactly = 2) { mediaRepository.getSyncPlayGroups() }
        coVerify(exactly = 1) { syncPlayManager.joinGroup("g1") }
        coVerify(exactly = 1) {
            mediaRepository.syncPlaySetNewQueue(
                itemIds = listOf("m1"),
                playingItemId = "m1",
                mediaSourceId = "src1",
                startPositionTicks = 0L,
            )
        }
    }

    @Test
    fun `start success invokes the four steps in order`() = runTest {
        val calls = mutableListOf<String>()
        coEvery { mediaRepository.createSyncPlayGroup(any()) } answers { calls += "create"; Result.success(Unit) }
        coEvery { mediaRepository.getSyncPlayGroups() } answers { calls += "getGroups"; Result.success(listOf(group)) }
        coEvery { syncPlayManager.joinGroup(any()) } answers { calls += "join"; Result.success(Unit) }
        coEvery {
            mediaRepository.syncPlaySetNewQueue(any(), any(), any(), any())
        } answers { calls += "setQueue"; Result.success(Unit) }

        actions().start("m1", "My Movie", null)

        // Snapshot read precedes create; recover read follows it.
        assertEquals(listOf("getGroups", "create", "getGroups", "join", "setQueue"), calls)
    }

    // ── Failure short-circuits ─────────────────────────────────────────

    @Test
    fun `start when createSyncPlayGroup fails returns failure, emits message and skips join and setNewQueue`() = runTest {
        // The pre-create snapshot read still happens; stub it so the relaxed
        // mock doesn't synthesise a bad Result.
        coEvery { mediaRepository.getSyncPlayGroups() } returns Result.success(emptyList())
        coEvery { mediaRepository.createSyncPlayGroup(any()) } returns Result.failure(RuntimeException("server"))

        val result = actions().start("m1", "My Movie", null)

        assertTrue(result.isFailure)
        assertTrue(messages.contains(DetailMessage.Text("couldn't start watch party")))
        coVerify(exactly = 0) { syncPlayManager.joinGroup(any()) }
        coVerify(exactly = 0) { mediaRepository.syncPlaySetNewQueue(any(), any(), any(), any()) }
    }

    @Test
    fun `start when the created group is not found returns failure and skips join and setNewQueue`() = runTest {
        coEvery { mediaRepository.createSyncPlayGroup(any()) } returns Result.success(Unit)
        // The group list does not contain a group matching the title.
        coEvery { mediaRepository.getSyncPlayGroups() } returns Result.success(emptyList())

        val result = actions().start("m1", "My Movie", null)

        assertTrue(result.isFailure)
        assertTrue(messages.contains(DetailMessage.Text("couldn't start watch party")))
        coVerify(exactly = 0) { syncPlayManager.joinGroup(any()) }
        coVerify(exactly = 0) { mediaRepository.syncPlaySetNewQueue(any(), any(), any(), any()) }
    }

    @Test
    fun `start when joinGroup fails returns failure and skips setNewQueue`() = runTest {
        coEvery { mediaRepository.createSyncPlayGroup(any()) } returns Result.success(Unit)
        coEvery { mediaRepository.getSyncPlayGroups() } returns Result.success(listOf(group))
        coEvery { syncPlayManager.joinGroup(any()) } returns Result.failure(RuntimeException("ws"))

        val result = actions().start("m1", "My Movie", null)

        assertTrue(result.isFailure)
        assertTrue(messages.contains(DetailMessage.Text("couldn't start watch party")))
        coVerify(exactly = 0) { mediaRepository.syncPlaySetNewQueue(any(), any(), any(), any()) }
    }

    @Test
    fun `start when setNewQueue fails returns failure`() = runTest {
        coEvery { mediaRepository.createSyncPlayGroup(any()) } returns Result.success(Unit)
        coEvery { mediaRepository.getSyncPlayGroups() } returns Result.success(listOf(group))
        coEvery { syncPlayManager.joinGroup(any()) } returns Result.success(Unit)
        coEvery {
            mediaRepository.syncPlaySetNewQueue(any(), any(), any(), any())
        } returns Result.failure(RuntimeException("queue"))

        val result = actions().start("m1", "My Movie", null)

        assertTrue(result.isFailure)
        assertTrue(messages.contains(DetailMessage.Text("couldn't start watch party")))
        // No WatchPartyStarted may be emitted on any failure path.
        assertTrue(messages.none { it is DetailMessage.WatchPartyStarted })
    }

    // ── Group disambiguation ───────────────────────────────────────────

    @Test
    fun `start joins the freshly-created group, not a pre-existing same-named one`() = runTest {
        val stale = SyncPlayGroup(groupId = "stale", groupName = "My Movie", participantCount = 5)
        val fresh = SyncPlayGroup(groupId = "fresh", groupName = "My Movie", participantCount = 1)
        coEvery { mediaRepository.createSyncPlayGroup(any()) } returns Result.success(Unit)
        // First read = pre-create snapshot (only the stale group exists);
        // second read = post-create recover (the new group now exists too).
        coEvery { mediaRepository.getSyncPlayGroups() } returnsMany listOf(
            Result.success(listOf(stale)),
            Result.success(listOf(stale, fresh)),
        )
        coEvery { syncPlayManager.joinGroup(any()) } returns Result.success(Unit)
        coEvery {
            mediaRepository.syncPlaySetNewQueue(any(), any(), any(), any())
        } returns Result.success(Unit)

        val result = actions().start("m1", "My Movie", null)

        assertTrue(result.isSuccess)
        coVerify { syncPlayManager.joinGroup("fresh") }
        coVerify(exactly = 0) { syncPlayManager.joinGroup("stale") }
    }
}
