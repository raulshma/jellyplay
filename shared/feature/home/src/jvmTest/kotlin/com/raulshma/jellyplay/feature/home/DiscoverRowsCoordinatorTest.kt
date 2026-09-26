package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.descriptor
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Direct [DiscoverRowsCoordinator] tests — the cheap end of the dice-roll
 * machinery's coverage (the refresher suites pin the invariant end-to-end at
 * its two riskiest suspension points; these pin the coordinator's OWN
 * contract without building a refresher):
 *  - a successful roll patches the row in place and registers the entry, and
 *    the next drain re-applies it over whatever payloads the raced fetch
 *    captured (the generation invariant's happy path), draining whole;
 *  - a drained registry stays drained — entries whose row the fetch doesn't
 *    carry are dropped with the drain, not carried to a later one;
 *  - [DiscoverRowsCoordinator.cancelForIdentityChange] both clears the
 *    registry (a previous identity's rolled items can never be re-applied by
 *    the incoming identity's first fetch) and cancels an in-flight roll,
 *    whose [NonCancellable] finally still clears the rolling flag.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverRowsCoordinatorTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var state: MutableStateFlow<HomeRefreshState>
    private lateinit var coordinator: DiscoverRowsCoordinator
    private lateinit var scope: CoroutineScope

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk(relaxed = true)
        state = MutableStateFlow(HomeRefreshState(sections = listOf(discoverSection("dr_x", item("m1")))))
    }

    @AfterTest
    fun tearDown() {
        if (this::scope.isInitialized) scope.cancel()
        Dispatchers.resetMain()
    }

    private fun TestScope.buildCoordinator(): DiscoverRowsCoordinator {
        scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        coordinator = DiscoverRowsCoordinator(scope, mediaRepository, state)
        return coordinator
    }

    @Test
    fun roll_patchesTheRowInPlace_andTheNextFetchsDrainReAppliesItOverThePreRollPayloads() = runTest {
        val rolled = listOf(item("r2"), item("r7"))
        coEvery { mediaRepository.rerollDiscoverRow(any()) } returns Result.success(rolled)
        val c = buildCoordinator()

        c.roll(discoverRow("dr_x"))
        runCurrent()

        assertEquals(
            rolled,
            state.value.sections.single().items,
            "the roll patches the on-screen row immediately, siblings untouched",
        )
        assertTrue("dr_x" in state.value.rollingDiscoverRowIds, "the spin flag is up until the min-spin floor")

        // The raced fetch lands carrying the PRE-roll payload: the drain must
        // re-apply the registered roll — its own last word on sections.
        val preRoll = listOf(discoverSection("dr_x", item("m1")))
        assertEquals(
            rolled,
            c.drainRolls(preRoll).single().items,
            "a roll registered before the drain point survives the fetch's sections write",
        )

        // Drained whole: the NEXT fetch's payloads pass through untouched.
        assertEquals(
            listOf(item("m1")),
            c.drainRolls(preRoll).single().items,
            "the registry drains whole — a later fetch must not re-apply the old roll",
        )

        advanceTimeBy(DiscoverRowsCoordinator.ROLL_MIN_SPIN_FOR_TEST + 1)
        runCurrent()
        assertTrue("dr_x" !in state.value.rollingDiscoverRowIds)
    }

    @Test
    fun drainRolls_dropsEntriesWhoseRowTheFetchDoesNotCarry() = runTest {
        val rolled = listOf(item("r2"))
        coEvery { mediaRepository.rerollDiscoverRow(any()) } returns Result.success(rolled)
        val c = buildCoordinator()

        c.roll(discoverRow("dr_x"))
        runCurrent()

        // A fetch that doesn't carry the row at all (disabled / absent):
        // its drain drops the entry with it — a later fetch carrying the row
        // must not resurrect the stale rolled items.
        assertEquals(emptyList(), c.drainRolls(emptyList()))
        val laterFetch = listOf(discoverSection("dr_x", item("m1")))
        assertEquals(
            listOf(item("m1")),
            c.drainRolls(laterFetch).single().items,
            "the entry was dropped with the drain that didn't carry the row",
        )
    }

    @Test
    fun cancelForIdentityChange_clearsTheRegistry_soTheIncomingIdentitysFetchCannotReApply() = runTest {
        val rolled = listOf(item("r2"))
        coEvery { mediaRepository.rerollDiscoverRow(any()) } returns Result.success(rolled)
        val c = buildCoordinator()

        c.roll(discoverRow("dr_x"))
        runCurrent()
        c.cancelForIdentityChange()

        assertEquals(
            listOf(item("m1")),
            c.drainRolls(listOf(discoverSection("dr_x", item("m1")))).single().items,
            "the previous identity's registered roll is void — the incoming identity's first fetch paints its own payloads",
        )
    }

    @Test
    fun cancelForIdentityChange_cancelsAnInFlightRoll_andItsFlagStillClears() = runTest {
        val gate = CompletableDeferred<Result<List<MediaItem>>>()
        coEvery { mediaRepository.rerollDiscoverRow(any()) } coAnswers { gate.await() }
        val c = buildCoordinator()

        c.roll(discoverRow("dr_x"))
        runCurrent()
        assertTrue("dr_x" in state.value.rollingDiscoverRowIds, "the flag is up while the roll runs")

        c.cancelForIdentityChange()
        advanceTimeBy(DiscoverRowsCoordinator.ROLL_MIN_SPIN_FOR_TEST + 1)
        runCurrent()

        assertTrue(
            "dr_x" !in state.value.rollingDiscoverRowIds,
            "the cancelled roll's NonCancellable finally must still clear the flag — a stuck flag disables the dice forever",
        )
        assertEquals(
            listOf(item("m1")),
            state.value.sections.single().items,
            "a cancelled roll never patches the row",
        )
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private fun discoverRow(id: String) = DiscoverRowConfig(id = id, title = "Row $id")

    private fun discoverSection(rowId: String, vararg items: MediaItem) = HomeSection(
        id = HomeSectionType.DISCOVER.descriptor.idFor(rowId),
        title = "Row $rowId",
        type = HomeSectionType.DISCOVER,
        items = items.toList(),
    )

    private fun item(id: String) = MediaItem(id = id, name = id, mediaType = MediaType.MOVIE)
}
