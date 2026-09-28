package com.raulshma.jellyplay.feature.arrqueue

import com.raulshma.jellyplay.core.data.repository.ArrReleaseOperations
import com.raulshma.jellyplay.core.model.arr.ArrDownloadStatus
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrRelease
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ReleaseSearch ViewModel coverage (the ArrQueueViewModelTest style): the
 * RESTART single-flight guard on [ReleaseSearchViewModel.runSearch] — a new
 * search cancels and supersedes the in-flight one, and a superseded search's
 * late outcome (success or failure) never lands over the newer run's rows
 * (the ConnectionProbe discipline this guard copies).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReleaseSearchViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (requests/downloads conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var arrReleaseOperations: ArrReleaseOperations

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        arrReleaseOperations = mockk()
        coEvery { arrReleaseOperations.releaseHistoryStatuses(any()) } returns Result.success(emptyMap())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun item() = ArrQueueItem(
        queueId = 1,
        tmdbId = 1,
        title = "Item 1",
        status = ArrDownloadStatus.DOWNLOADING,
        serverKind = ArrServiceKind.RADARR,
        serverId = "server-RADARR",
    )

    private fun release(guid: String) = ArrRelease(
        guid = guid,
        indexerId = 1,
        title = "Release $guid",
        customFormatScore = 0,
        seeders = 10,
        ageHours = 1.0,
        approved = true,
    )

    @Test
    fun `a superseded search's late success never lands over the newer rows`() = runTest {
        // Each attempt parks on its own gate and answers with its own rows —
        // the ConnectionProbeTest restart-test shape (the stale outcome is
        // released only after the fresh one has landed).
        val staleGate = CompletableDeferred<Unit>()
        val freshGate = CompletableDeferred<Unit>()
        var attempt = 0
        coEvery { arrReleaseOperations.searchReleases(any()) } coAnswers { _ ->
            val gate = if (attempt++ == 0) staleGate else freshGate
            val rows = if (gate === staleGate) listOf(release("stale")) else listOf(release("fresh-a"), release("fresh-b"))
            gate.await()
            Result.success(rows)
        }
        val viewModel = ReleaseSearchViewModel(arrReleaseOperations)

        viewModel.open(item())
        runCurrent()
        // "Search again" supersedes the in-flight search.
        viewModel.searchAgain()
        runCurrent()

        // The newer run lands first...
        freshGate.complete(Unit)
        advanceUntilIdle()
        val fresh = listOf(release("fresh-a"), release("fresh-b"))
        assertEquals(ReleaseSearchPhase.Results, viewModel.state.value.sheet.phase)
        assertEquals(fresh, viewModel.state.value.sheet.releases)

        // ...then the superseded run settles — its stale rows must never land.
        staleGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(fresh, viewModel.state.value.sheet.releases)
    }

    @Test
    fun `a superseded search's late failure never lands over the newer rows`() = runTest {
        val staleGate = CompletableDeferred<Unit>()
        var attempt = 0
        coEvery { arrReleaseOperations.searchReleases(any()) } coAnswers { _ ->
            // Attempt 1 parks then fails late; attempt 2 answers immediately.
            if (attempt++ == 0) {
                staleGate.await()
                Result.failure(IllegalStateException("late boom"))
            } else {
                Result.success(listOf(release("fresh")))
            }
        }
        val viewModel = ReleaseSearchViewModel(arrReleaseOperations)

        viewModel.open(item())
        runCurrent()
        viewModel.searchAgain()
        advanceUntilIdle()
        assertEquals(ReleaseSearchPhase.Results, viewModel.state.value.sheet.phase)

        // The superseded run's late failure must not flip the sheet to Error.
        staleGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(ReleaseSearchPhase.Results, viewModel.state.value.sheet.phase)
        assertEquals(listOf(release("fresh")), viewModel.state.value.sheet.releases)
    }

    @Test
    fun `an unsuperseded search still lands its outcome`() = runTest {
        // Guard sanity: the identity check only discards superseded runs — a
        // plain open's rows land (the ConnectionProbe inline-start precedent).
        coEvery { arrReleaseOperations.searchReleases(any()) } returns Result.success(listOf(release("only")))
        val viewModel = ReleaseSearchViewModel(arrReleaseOperations)

        viewModel.open(item())
        advanceUntilIdle()

        assertEquals(ReleaseSearchPhase.Results, viewModel.state.value.sheet.phase)
        assertEquals(listOf(release("only")), viewModel.state.value.sheet.releases)
    }
}
