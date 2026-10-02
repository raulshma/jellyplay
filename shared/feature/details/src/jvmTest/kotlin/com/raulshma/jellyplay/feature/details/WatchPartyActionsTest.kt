package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.SyncPlayRepository
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_watch_party_failed
import com.raulshma.jellyplay.feature.details.generated.resources.detail_watch_party_default_name

/**
 * The bootstrap is a TWO-step sequence since the createGroup deepening: one
 * [SyncPlayManager.createGroup] call owns create→join-MY-group (the old
 * snapshot/recover/join steps — and their duplicate-name disambiguation —
 * live there now, pinned in SyncPlayManagerTest), then the queue push.
 */
class WatchPartyActionsTest {

    private val mediaRepository: SyncPlayRepository = mockk(relaxed = true)
    private val syncPlayManager: SyncPlayManager = mockk(relaxed = true)

    private val strings = fakeDetailStrings()
    private val messages = RecordingMessages()

    private val joinedGroup = SyncPlayGroup(
        groupId = "g1",
        groupName = "My Movie",
        participantCount = 1,
    )

    @BeforeTest
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
        syncPlayRepository = mediaRepository,
        syncPlayManager = syncPlayManager,
    )

    private fun stubCreateSuccess(group: SyncPlayGroup = joinedGroup) {
        coEvery { syncPlayManager.createGroup(group.groupName) } returns Result.success(group)
        coEvery {
            mediaRepository.syncPlaySetNewQueue(any(), any(), any(), any())
        } returns Result.success(Unit)
    }

    // ── Screen-item entry point (title/source resolution moved here) ─────

    @Test
    fun `startScreenItem resolves title and default media source from the session and seeds the queue`() = runTest {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "My Movie", mediaType = MediaType.MOVIE),
            mediaSources = listOf(MediaSource(id = "src1", name = "Source")),
        )
        stubCreateSuccess()

        actions(this, MutableStateFlow(DetailSession(itemId = "m1", detail = detail))).startScreenItem()
        advanceUntilIdle()

        // Group titled from the item name; queue seeded with the default source.
        coVerify(exactly = 1) { syncPlayManager.createGroup("My Movie") }
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
        coEvery {
            syncPlayManager.createGroup(strings.get(Res.string.detail_watch_party_default_name))
        } returns Result.success(joinedGroup.copy(groupName = strings.get(Res.string.detail_watch_party_default_name)))
        coEvery {
            mediaRepository.syncPlaySetNewQueue(any(), any(), any(), any())
        } returns Result.success(Unit)

        actions(this, MutableStateFlow(DetailSession(itemId = "m1", detail = detail))).startScreenItem()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            syncPlayManager.createGroup(strings.get(Res.string.detail_watch_party_default_name))
        }
    }

    @Test
    fun `startScreenItem with no loaded detail is a no-op`() = runTest {
        actions(this).startScreenItem()
        advanceUntilIdle()

        coVerify(exactly = 0) { syncPlayManager.createGroup(any()) }
        coVerify(exactly = 0) { mediaRepository.createSyncPlayGroup(any()) }
        assertTrue(messages.recorded.isEmpty())
    }

    // ── Happy path ──────────────────────────────────────────────────────

    @Test
    fun `start success creates and joins via one manager call, seeds queue and emits WatchPartyStarted`() = runTest {
        stubCreateSuccess()

        val result = actions(this).start(
            itemId = "m1",
            title = "My Movie",
            mediaSourceId = "src1",
        )

        assertTrue(result.isSuccess)
        assertTrue(messages.recorded.contains(DetailMessage.WatchPartyStarted("m1")))
        coVerify(exactly = 1) { syncPlayManager.createGroup("My Movie") }
        // The manager owns the join — the bootstrap never re-joins.
        coVerify(exactly = 0) { syncPlayManager.joinGroup(any()) }
        coVerify(exactly = 0) { mediaRepository.getSyncPlayGroups() }
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
    fun `start success invokes the two steps in order`() = runTest {
        val calls = mutableListOf<String>()
        coEvery { syncPlayManager.createGroup(any()) } answers { calls += "createJoin"; Result.success(joinedGroup) }
        coEvery {
            mediaRepository.syncPlaySetNewQueue(any(), any(), any(), any())
        } answers { calls += "setQueue"; Result.success(Unit) }

        actions(this).start("m1", "My Movie", null)

        assertEquals(listOf("createJoin", "setQueue"), calls)
    }

    // ── Failure short-circuits ─────────────────────────────────────────

    @Test
    fun `start when the deepened createGroup fails returns failure, emits message and skips setNewQueue`() = runTest {
        coEvery { syncPlayManager.createGroup(any()) } returns Result.failure(RuntimeException("server"))

        val result = actions(this).start("m1", "My Movie", null)

        assertTrue(result.isFailure)
        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(Res.string.detail_msg_watch_party_failed))
            )
        )
        coVerify(exactly = 0) { mediaRepository.syncPlaySetNewQueue(any(), any(), any(), any()) }
    }

    @Test
    fun `start when setNewQueue fails returns failure`() = runTest {
        stubCreateSuccess()
        coEvery {
            mediaRepository.syncPlaySetNewQueue(any(), any(), any(), any())
        } returns Result.failure(RuntimeException("queue"))

        val result = actions(this).start("m1", "My Movie", null)

        assertTrue(result.isFailure)
        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(Res.string.detail_msg_watch_party_failed))
            )
        )
        // No WatchPartyStarted may be emitted on any failure path.
        assertTrue(messages.recorded.none { it is DetailMessage.WatchPartyStarted })
    }
}
