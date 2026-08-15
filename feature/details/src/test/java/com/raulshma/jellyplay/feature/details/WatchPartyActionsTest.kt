package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WatchPartyActionsTest {

    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val syncPlayManager: SyncPlayManager = mockk(relaxed = true)

    private val strings = fakeDetailStrings()
    private val messages = RecordingMessages()

    private val group = SyncPlayGroup(
        groupId = "g1",
        groupName = "My Movie",
        participantCount = 1,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        messages.reset()
    }

    private fun actions(
        scope: kotlinx.coroutines.CoroutineScope? = null,
        session: MutableStateFlow<DetailSession?> = MutableStateFlow(null),
    ): WatchPartyActions = WatchPartyActions(
        scope = scope ?: kotlinx.coroutines.test.TestScope(),
        session = session,
        messages = messages.flow,
        strings = strings,
        mediaRepository = mediaRepository,
        syncPlayManager = syncPlayManager,
    )

    // ── Screen-item entry point (title/source resolution moved here) ─────

    @Test
    fun `startScreenItem resolves title and default media source from the session and seeds the queue`() = runTest {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "My Movie", mediaType = MediaType.MOVIE),
            mediaSources = listOf(MediaSource(id = "src1", name = "Source")),
        )
        coEvery { mediaRepository.createSyncPlayGroup(any()) } returns Result.success(Unit)
        coEvery { mediaRepository.getSyncPlayGroups() } returns Result.success(listOf(group))
        coEvery { syncPlayManager.joinGroup(any()) } returns Result.success(Unit)
        coEvery {
            mediaRepository.syncPlaySetNewQueue(any(), any(), any(), any())
        } returns Result.success(Unit)

        actions(this, MutableStateFlow(DetailSession(itemId = "m1", detail = detail))).startScreenItem()
        advanceUntilIdle()

        // Group titled from the item name; queue seeded with the default source.
        coVerify(exactly = 1) { mediaRepository.createSyncPlayGroup("My Movie") }
        coVerify(exactly = 1) {
            mediaRepository.syncPlaySetNewQueue(
                itemIds = listOf("m1"),
                playingItemId = "m1",
                mediaSourceId = "src1",
                startPositionTicks = 0L,
            )
        }
        assertTrue(messages.recorded.contains(DetailMessage.WatchPartyStarted("m1")))
    }

    @Test
    fun `startScreenItem falls back to the localized default title for a blank item name`() = runTest {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "", mediaType = MediaType.MOVIE),
        )
        coEvery { mediaRepository.createSyncPlayGroup(any()) } returns Result.success(Unit)
        coEvery { mediaRepository.getSyncPlayGroups() } returns Result.success(
            listOf(group.copy(groupName = strings.get(R.string.detail_watch_party_default_name)))
        )
        coEvery { syncPlayManager.joinGroup(any()) } returns Result.success(Unit)
        coEvery {
            mediaRepository.syncPlaySetNewQueue(any(), any(), any(), any())
        } returns Result.success(Unit)

        actions(this, MutableStateFlow(DetailSession(itemId = "m1", detail = detail))).startScreenItem()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            mediaRepository.createSyncPlayGroup(strings.get(R.string.detail_watch_party_default_name))
        }
    }

    @Test
    fun `startScreenItem with no loaded detail is a no-op`() = runTest {
        actions(this).startScreenItem()
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaRepository.createSyncPlayGroup(any()) }
        assertTrue(messages.recorded.isEmpty())
    }

    // ── Happy path ──────────────────────────────────────────────────────

    @Test
    fun `start success creates group, recovers it by name, joins, seeds queue and emits WatchPartyStarted`() = runTest {
        coEvery { mediaRepository.createSyncPlayGroup(any()) } returns Result.success(Unit)
        coEvery { mediaRepository.getSyncPlayGroups() } returns Result.success(listOf(group))
        coEvery { syncPlayManager.joinGroup(any()) } returns Result.success(Unit)
        coEvery {
            mediaRepository.syncPlaySetNewQueue(any(), any(), any(), any())
        } returns Result.success(Unit)

        val result = actions(this).start(
            itemId = "m1",
            title = "My Movie",
            mediaSourceId = "src1",
        )

        assertTrue(result.isSuccess)
        assertTrue(messages.recorded.contains(DetailMessage.WatchPartyStarted("m1")))
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

        actions(this).start("m1", "My Movie", null)

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

        val result = actions(this).start("m1", "My Movie", null)

        assertTrue(result.isFailure)
        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(R.string.detail_msg_watch_party_failed))
            )
        )
        coVerify(exactly = 0) { syncPlayManager.joinGroup(any()) }
        coVerify(exactly = 0) { mediaRepository.syncPlaySetNewQueue(any(), any(), any(), any()) }
    }

    @Test
    fun `start when the created group is not found returns failure and skips join and setNewQueue`() = runTest {
        coEvery { mediaRepository.createSyncPlayGroup(any()) } returns Result.success(Unit)
        // The group list does not contain a group matching the title.
        coEvery { mediaRepository.getSyncPlayGroups() } returns Result.success(emptyList())

        val result = actions(this).start("m1", "My Movie", null)

        assertTrue(result.isFailure)
        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(R.string.detail_msg_watch_party_failed))
            )
        )
        coVerify(exactly = 0) { syncPlayManager.joinGroup(any()) }
        coVerify(exactly = 0) { mediaRepository.syncPlaySetNewQueue(any(), any(), any(), any()) }
    }

    @Test
    fun `start when joinGroup fails returns failure and skips setNewQueue`() = runTest {
        coEvery { mediaRepository.createSyncPlayGroup(any()) } returns Result.success(Unit)
        coEvery { mediaRepository.getSyncPlayGroups() } returns Result.success(listOf(group))
        coEvery { syncPlayManager.joinGroup(any()) } returns Result.failure(RuntimeException("ws"))

        val result = actions(this).start("m1", "My Movie", null)

        assertTrue(result.isFailure)
        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(R.string.detail_msg_watch_party_failed))
            )
        )
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

        val result = actions(this).start("m1", "My Movie", null)

        assertTrue(result.isFailure)
        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(R.string.detail_msg_watch_party_failed))
            )
        )
        // No WatchPartyStarted may be emitted on any failure path.
        assertTrue(messages.recorded.none { it is DetailMessage.WatchPartyStarted })
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

        val result = actions(this).start("m1", "My Movie", null)

        assertTrue(result.isSuccess)
        coVerify { syncPlayManager.joinGroup("fresh") }
        coVerify(exactly = 0) { syncPlayManager.joinGroup("stale") }
    }
}
