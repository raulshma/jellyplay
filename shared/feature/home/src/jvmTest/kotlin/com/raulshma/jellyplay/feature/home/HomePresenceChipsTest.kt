package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.model.HomeFreshness
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.SessionNowPlayingItem
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Presence chips: the session filter (self-exclusion by user id, the
 * now-playing null/blank filter, the cap at 3), the probe-once /Sessions
 * capability gate (failure hides forever for the session; a transient poll
 * failure does not flip the verdict), the SyncPlay join-availability
 * projection (group present vs absent, and a failed group read degrading to
 * "no Join"), and the per-chip dismiss memory (session-scoped, reset on user
 * switch). The pure projection helpers are pinned beside them.
 */
class HomePresenceChipsTest {

    private val adminRepository: AdminRepository = mockk(relaxed = true)
    private val syncPlayManager: SyncPlayManager = mockk(relaxed = true)

    private fun buildController() = HomePresenceChipsController(
        adminRepository = adminRepository,
        syncPlayManager = syncPlayManager,
    )

    private fun session(
        id: String,
        userId: String,
        userName: String = "User$id",
        itemId: String? = "item_$id",
        itemName: String = "Item $id",
        seriesName: String? = null,
        wireType: String = "Movie",
    ) = SessionInfo(
        id = id,
        userId = userId,
        userName = userName,
        nowPlayingItem = itemId?.let {
            SessionNowPlayingItem(id = it, name = itemName, seriesName = seriesName, type = wireType)
        },
    )

    private fun stubSessions(vararg sessions: SessionInfo, failure: Boolean = false) {
        coEvery { adminRepository.getSessions() } returns
            if (failure) Result.failure(IllegalStateException("forbidden"))
            else Result.success(sessions.toList())
    }

    private fun stubGroups(vararg groups: SyncPlayGroup, failure: Boolean = false) {
        if (failure) {
            coEvery { syncPlayManager.visibleGroups() } throws IllegalStateException("syncplay down")
        } else {
            coEvery { syncPlayManager.visibleGroups() } returns groups.toList()
        }
        // The controller exercises the real matcher end-to-end.
        every { syncPlayManager.groupPlayingItem(any(), any()) } answers { callOriginal() }
    }

    private fun group(groupId: String, playingItemId: String?) =
        SyncPlayGroup(groupId = groupId, groupName = groupId, participantCount = 1, playingItemId = playingItemId)

    // ── Session filter ───────────────────────────────────────────────────

    @Test
    fun ownSessions_excluded_byUserId() = runTest {
        stubSessions(
            session("s1", userId = "me", itemId = "item_mine"),
            session("s2", userId = "alex", itemId = "item_alex"),
        )

        val chips = buildController().refresh(currentUserId = "me")

        assertEquals(listOf("item_alex"), chips.map { it.itemId })
    }

    @Test
    fun sessionsWithoutNowPlaying_filtered_out() = runTest {
        stubSessions(
            session("s1", userId = "alex", itemId = null),
            session("s2", userId = "bob", itemId = "item_bob"),
            // A playing session without a resolvable item id can't be a tap
            // target — filtered with the nulls.
            session("s3", userId = "carol", itemId = ""),
        )

        val chips = buildController().refresh(currentUserId = "me")

        assertEquals(listOf("item_bob"), chips.map { it.itemId })
    }

    @Test
    fun chips_cappedAtThree() = runTest {
        stubSessions(
            session("s1", userId = "u1"),
            session("s2", userId = "u2"),
            session("s3", userId = "u3"),
            session("s4", userId = "u4"),
            session("s5", userId = "u5"),
        )

        val chips = buildController().refresh(currentUserId = "me")

        assertEquals(HomePresenceChipsMath.MAX_CHIPS, chips.size)
        assertEquals(3, HomePresenceChipsMath.MAX_CHIPS)
        assertEquals(listOf("u1", "u2", "u3"), chips.map { it.userId })
    }

    // ── Probe-once capability gate ───────────────────────────────────────

    @Test
    fun probeFailure_hidesForeverForTheSession_singleProbe() = runTest {
        stubSessions(failure = true)
        val controller = buildController()

        assertTrue(controller.refresh(currentUserId = "me").isEmpty())

        // The server changes its mind (a promotion to admin lands): the
        // probe-once contract still hides — and /Sessions is never asked
        // again this session (the short-circuit precedes the network read).
        stubSessions(session("s1", userId = "alex"))
        assertTrue(controller.refresh(currentUserId = "me").isEmpty())
        assertFalse(controller.isAvailableForSession())
        coVerify(exactly = 1) { adminRepository.getSessions() }
    }

    @Test
    fun probeSuccess_thenTransientFailure_hidesOneEmission_only() = runTest {
        stubSessions(session("s1", userId = "alex"))
        val controller = buildController()
        assertEquals(1, controller.refresh(currentUserId = "me").size)

        // A later poll fails (offline blip): the chips hide for THAT
        // emission, but the probe verdict survives — the next successful
        // poll shows the chips again without a re-probe.
        stubSessions(failure = true)
        assertTrue(controller.refresh(currentUserId = "me").isEmpty())
        stubSessions(session("s1", userId = "alex"))
        assertEquals(1, controller.refresh(currentUserId = "me").size)
        assertTrue(controller.isAvailableForSession())
    }

    @Test
    fun userSwitch_reArmsProbe_andResetsDismissMemory() = runTest {
        stubSessions(
            session("s1", userId = "alex"),
            session("s2", userId = "bob"),
        )
        stubGroups()
        val controller = buildController()
        val chips = controller.refresh(currentUserId = "admin_user")
        assertEquals(2, chips.size)
        controller.dismiss(chips.first().sessionId)
        assertTrue(controller.isDismissed(chips.first().sessionId))

        // A different user's session is a fresh home session for this
        // controller: the probe re-arms (a success now — the swap underneath
        // the admin client changed the answer) and the previous user's
        // dismissals are gone.
        stubSessions(session("s9", userId = "alex"))
        val next = controller.refresh(currentUserId = "non_admin_user")
        assertEquals(1, next.size)
        assertFalse(controller.isDismissed(next.first().sessionId))
    }

    // ── Join availability (group present vs absent) ──────────────────────

    @Test
    fun groupPlayingTheItem_setsJoinableGroupId() = runTest {
        stubSessions(session("s1", userId = "alex", itemId = "item_dune"))
        stubGroups(group("g1", playingItemId = "item_dune"))

        val chips = buildController().refresh(currentUserId = "me")

        assertEquals("g1", chips.single().joinableGroupId)
    }

    @Test
    fun noGroupPlayingTheItem_joinableGroupIdNull_chipsStay() = runTest {
        stubSessions(session("s1", userId = "alex", itemId = "item_dune"))
        stubGroups(group("g1", playingItemId = "item_other"))

        val chips = buildController().refresh(currentUserId = "me")

        assertNull(chips.single().joinableGroupId)
    }

    @Test
    fun failedGroupRead_degradesToNoJoin_chipsRemain() = runTest {
        stubSessions(session("s1", userId = "alex", itemId = "item_dune"))
        stubGroups(failure = true)

        val chips = buildController().refresh(currentUserId = "me")

        assertEquals(1, chips.size)
        assertNull(chips.single().joinableGroupId)
    }

    // ── Dismiss memory ───────────────────────────────────────────────────

    @Test
    fun dismissedSession_filteredFromLaterRefreshes_only() = runTest {
        stubSessions(
            session("s1", userId = "alex"),
            session("s2", userId = "bob"),
        )
        stubGroups()
        val controller = buildController()
        assertEquals(2, controller.refresh(currentUserId = "me").size)

        controller.dismiss("s1")

        val chips = controller.refresh(currentUserId = "me")
        assertEquals(listOf("s2"), chips.map { it.sessionId })
        assertFalse(controller.isDismissed("s2"))
    }

    // ── Poll (the cadence loop, moved from the VM) ───────────────────────

    @Test
    fun poll_pollsImmediately_thenOnTheHomeCadence_andStopsWhenTheGateCloses() = runTest {
        stubSessions(session("s1", userId = "alex"))
        stubGroups()
        val gate = MutableStateFlow(
            PresenceGate(enabled = true, userId = "me", online = true, foreground = true),
        )
        val emissions = mutableListOf<List<HomePresenceChip>>()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        try {
            buildController().poll(scope, gate) { emissions.add(it) }

            // The first poll of a session is immediate — and it is the
            // /Sessions capability probe (one network read so far).
            runCurrent()
            assertEquals(1, emissions.size)
            assertEquals(1, emissions.single().size)

            // The cadence tick: one poll per home refresh interval.
            advanceTimeBy(HomeFreshness.REFRESH_INTERVAL_FOREGROUND_MS)
            runCurrent()
            assertEquals(2, emissions.size)

            // The gate closes (pref off / going offline / signed out): an
            // immediate empty emission folds the row away, the parked loop
            // is cancelled, and the cadence stops polling for good.
            gate.value = PresenceGate(enabled = false, userId = "me", online = true, foreground = true)
            runCurrent()
            assertEquals(3, emissions.size)
            assertTrue(emissions.last().isEmpty())
            advanceTimeBy(HomeFreshness.REFRESH_INTERVAL_FOREGROUND_MS * 3)
            assertEquals(3, emissions.size)
            coVerify(exactly = 2) { adminRepository.getSessions() }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun poll_closedGate_writesEmptyOnce_withoutProbing() = runTest {
        // The default-off pref shape: the loop's first (and only) tick is the
        // empty write — /Sessions is never asked, so opting in later (a gate
        // flip) probes and polls afresh from a controller that never ran.
        stubSessions(session("s1", userId = "alex"))
        val gate = MutableStateFlow(
            PresenceGate(enabled = false, userId = "me", online = true, foreground = true),
        )
        val emissions = mutableListOf<List<HomePresenceChip>>()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        try {
            buildController().poll(scope, gate) { emissions.add(it) }

            runCurrent()
            assertEquals(listOf(emptyList<HomePresenceChip>()), emissions)
            advanceTimeBy(HomeFreshness.REFRESH_INTERVAL_FOREGROUND_MS * 2)
            assertEquals(1, emissions.size)
            coVerify(exactly = 0) { adminRepository.getSessions() }
        } finally {
            scope.cancel()
        }
    }

    // ── Pure projection helpers ──────────────────────────────────────────

    @Test
    fun displayItemName_prefersSeriesName_forEpisodes() {
        assertEquals("Dune", HomePresenceChipsMath.displayItemName("Episode 1", "Dune"))
        assertEquals("Dune", HomePresenceChipsMath.displayItemName("Dune", null))
        // A blank series name falls through to the item name, never renders
        // as "watching ".
        assertEquals("Episode 1", HomePresenceChipsMath.displayItemName("Episode 1", "  "))
    }

    @Test
    fun mediaTypeFor_mapsTheWireVocabulary() {
        assertEquals(MediaType.MOVIE, HomePresenceChipsMath.mediaTypeFor("Movie"))
        assertEquals(MediaType.EPISODE, HomePresenceChipsMath.mediaTypeFor("Episode"))
        assertEquals(MediaType.BOOK, HomePresenceChipsMath.mediaTypeFor("Book"))
        assertEquals(MediaType.UNKNOWN, HomePresenceChipsMath.mediaTypeFor("LiveTvProgram"))
        assertEquals(MediaType.UNKNOWN, HomePresenceChipsMath.mediaTypeFor(null))
    }

    @Test
    fun userName_fallsBackToDeviceName_whenBlank() = runTest {
        coEvery { adminRepository.getSessions() } returns Result.success(
            listOf(
                SessionInfo(
                    id = "s1",
                    userId = "alex",
                    userName = "",
                    deviceName = "Living Room TV",
                    nowPlayingItem = SessionNowPlayingItem(id = "item_dune", name = "Dune", type = "Movie"),
                ),
            ),
        )
        stubGroups()

        val chips = buildController().refresh(currentUserId = "me")

        assertEquals("Living Room TV", chips.single().userName)
    }

    @Test
    fun isPhoneLayout_compactNonTv_isTrue() {
        assertTrue(isPhoneLayout(isTv = false, windowSizeClass = WindowSizeClass.Compact))
        assertFalse(isPhoneLayout(isTv = true, windowSizeClass = WindowSizeClass.Compact))
        assertFalse(isPhoneLayout(isTv = false, windowSizeClass = WindowSizeClass.Medium))
        assertFalse(isPhoneLayout(isTv = false, windowSizeClass = WindowSizeClass.Expanded))
    }
}
