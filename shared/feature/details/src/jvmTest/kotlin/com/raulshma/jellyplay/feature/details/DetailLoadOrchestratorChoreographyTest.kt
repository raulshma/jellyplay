package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.HomeFreshness
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.UserDataChange
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.testfixtures.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule

/**
 * Direct [DetailLoadOrchestrator] tests: the load graph's concurrency
 * CHOREOGRAPHY half in isolation — the debounced user-data burst drain (the
 * init-block collector), the staleness epoch's stale-flight rejection, and
 * the content-generation clobber latch — driven through the orchestrator's
 * own surface (load / update / state), with NO VM construction (the
 * HomeRefresherTest / HomeUserDataReactorTest precedent; DetailViewModelTest
 * exercises the VM fold and never reaches these arms). The graph is
 * [DetailLoadOrchestratorTestGraph]; the debounce is a REAL operator over
 * the shared test scheduler — runCurrent / advanceTimeBy, never
 * advanceUntilIdle-then-emit.
 *
 * The three pins document current truth (behavior unchanged — these tests
 * are the first surface that bites when the choreography regresses):
 *
 * 1. **Burst collapse**: user-data changes arriving within one debounce
 *    window collapse into exactly ONE silent refresh load — the
 *    pull-to-refresh shape (detail stays visible under Refreshing), not one
 *    reload per change.
 * 2. **Stale-flight rejection**: the epoch bumped at load start supersedes
 *    the previous item's in-flight remote-discovery flight — its late
 *    landing drops instead of painting the old item's companion content
 *    onto the new screen.
 * 3. **Clobber latch**: an attachment-only tick (same contentGeneration)
 *    adopts the attachment fields but leaves the consumer's optimistic
 *    content untouched; a genuinely new generation is adopted wholesale.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetailLoadOrchestratorChoreographyTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private var graph: DetailLoadOrchestratorTestGraph? = null

    @AfterTest
    fun tearDown() {
        graph?.cancel()
    }

    /** Builds the graph on the test scheduler and lets the init-block collector subscribe. */
    private fun TestScope.buildGraph(): DetailLoadOrchestratorTestGraph =
        DetailLoadOrchestratorTestGraph(this).also {
            graph = it
            runCurrent() // the init-block burst collector subscribes
        }

    // ── Pin 1: the debounced user-data burst drain ─────────────────────────
    //
    // The init-block collector accumulates item ids across the debounce
    // window (onEach into burstIds) and drains ONCE per window into
    // load(itemId, refresh = true) — the silent refresh (pull-to-refresh
    // shape), never one reload per change. DetailViewModelTest pins the
    // merged-drain-set edge through the VM's fold; this pin drives the
    // orchestrator's collector itself.

    @Test
    fun userDataChangeBurst_withinOneDebounceWindow_collapsesIntoOneSilentRefresh() =
        runTest(mainDispatcherRule.testDispatcher) {
            val g = buildGraph()
            g.stubProvider("m1", g.remoteSnapshot(g.movieDetail("m1")))
            g.orchestrator.load("m1", refresh = false)
            advanceUntilIdle()
            assertEquals("m1", g.orchestrator.state.value.detail?.item?.id)

            // Park the provider's refresh so the in-flight silent refresh is
            // observable between the drain and the refreshed resolution.
            val gate = CompletableDeferred<Unit>()
            g.parkRefresh("m1", gate)

            // Two user-data changes naming the on-screen item inside one
            // debounce window (the debounce restarts on every emission).
            g.userDataEvents.tryEmit(UserDataChange(userId = "u1", itemIds = listOf("m1")))
            advanceTimeBy(500)
            g.userDataEvents.tryEmit(UserDataChange(userId = "u1", itemIds = listOf("m1")))
            advanceTimeBy(HomeFreshness.USER_DATA_CHANGE_REFRESH_DEBOUNCE_MS + 100)
            runCurrent()

            // Exactly ONE silent refresh fired for the whole burst...
            coVerify(exactly = 1) { g.mediaDetailProvider.refresh("m1") }
            // ...and it is the pull-to-refresh shape: the detail stays
            // visible under Refreshing (clearedForReload(keepDetail = true)).
            val inFlight = g.orchestrator.state.value
            assertEquals("m1", inFlight.detail?.item?.id)
            assertTrue(inFlight.loadState is DetailUiLoadState.Refreshing)

            gate.complete(Unit)
            advanceUntilIdle()
            // The refreshed resolution adopted (the flow replays its Loaded
            // snapshot into the new collector) and the indicator cleared.
            assertEquals("m1", g.orchestrator.state.value.detail?.item?.id)
            assertFalse(g.orchestrator.state.value.loadState is DetailUiLoadState.Refreshing)
        }

    // ── Pin 2: the staleness epoch's stale-flight rejection ────────────────
    //
    // load() bumps the guard's epoch SYNCHRONOUSLY with the identity write
    // (DetailLoadGuard.enter), so a flight path captured under the previous
    // item's epoch (the TMDB-companion legs capture loadGuard.bump() at the
    // Seerr-data claim) is rejected at its landing re-check even though the
    // flight itself runs on the orchestrator's scope — NOT the per-item load
    // job the next load cancels.

    @Test
    fun loadBSupersedingInFlightA_priorItemCompanionLandingWritesNothing() =
        runTest(mainDispatcherRule.testDispatcher) {
            val g = buildGraph()

            // m1: a remote movie with a tmdb id whose companion videos fetch
            // parks mid-flight (the disconnected-Seerr TMDB fallback leg).
            g.stubProvider("m1", g.remoteSnapshot(g.movieDetail("m1", tmdbId = 123)))
            val staleVideo = SeerrRelatedVideo(key = "stale")
            val gate = CompletableDeferred<Unit>()
            coEvery { g.seerrRepository.getTmdbVideos(123, MediaType.MOVIE) } coAnswers {
                gate.await()
                Result.success(listOf(staleVideo))
            }

            g.orchestrator.load("m1", refresh = false)
            advanceUntilIdle()
            assertEquals("m1", g.orchestrator.state.value.detail?.item?.id)
            // The flight is parked — captured against m1's epoch.
            coVerify(exactly = 1) { g.seerrRepository.getTmdbVideos(123, MediaType.MOVIE) }

            // Navigate to m2: the identity write + epoch bump happen
            // synchronously in load(); m2's snapshot never resolves (the flow
            // stays Loading), so nothing bumps the epoch again after this.
            g.stubProvider("m2")
            g.orchestrator.load("m2", refresh = false)
            advanceUntilIdle()
            assertNull(g.orchestrator.state.value.detail, "m2 never resolves")

            // The stale fetch resolves now — the epoch it captured is no
            // longer current, so its landing must drop instead of painting
            // m1's videos onto m2's screen.
            gate.complete(Unit)
            advanceUntilIdle()

            assertTrue(
                g.orchestrator.state.value.relatedVideos.isEmpty(),
                "the superseded flight's late landing must write nothing",
            )
        }

    // ── Pin 3: the content-generation clobber latch ────────────────────────
    //
    // reduceLoaded keys on MediaDetailSnapshot.contentGeneration vs
    // lastAppliedGeneration: a SAME-generation emission is an attachment-only
    // tick (download/sync re-emission) and updates ONLY detailContext /
    // capabilities / assets / localSubtitles — the consumer's optimistic
    // content (written through the update seam, the VM-side UI write path)
    // survives. A NEW generation is adopted wholesale, deliberately
    // replacing the optimistic content with the server truth.

    @Test
    fun attachmentTick_sameGeneration_preservesConsumerPatch_andAdoptsAttachmentFields() =
        runTest(mainDispatcherRule.testDispatcher) {
            val g = buildGraph()
            val flow = g.stubProvider("m1", g.remoteSnapshot(g.movieDetail("m1")))
            g.orchestrator.load("m1", refresh = false)
            advanceUntilIdle()
            assertFalse(g.orchestrator.state.value.detail!!.item.isFavorite)

            // The consumer's optimistic write through the narrow update seam —
            // the VM's optimistic user-data patch lands exactly this shape
            // after a successful mutation.
            g.orchestrator.update { state ->
                state.copy(
                    detail = state.detail?.copy(item = state.detail.item.copy(isFavorite = true)),
                )
            }
            assertTrue(g.orchestrator.state.value.detail!!.item.isFavorite)

            // Attachment-only tick: SAME contentGeneration, the capability
            // slice refreshed (a download/sync attachment re-emission).
            val tickCapabilities = g.remoteSnapshot(g.movieDetail("m1"))
                .snapshot
                .capabilities
                .copy(localStreamInfo = true)
            flow.value = g.remoteSnapshot(
                g.movieDetail("m1"),
                contentGeneration = 0L,
                capabilities = tickCapabilities,
            )
            advanceUntilIdle()

            val afterTick = g.orchestrator.state.value
            assertTrue(
                afterTick.detail!!.item.isFavorite,
                "the attachment tick must not clobber the consumer's optimistic content",
            )
            assertTrue(
                afterTick.capabilities.localStreamInfo,
                "the attachment fields themselves still adopt",
            )

            // A genuinely NEW generation is adopted wholesale — the server
            // truth (favorite=false) replaces the optimistic flip on purpose.
            flow.value = g.remoteSnapshot(g.movieDetail("m1"), contentGeneration = 1L)
            advanceUntilIdle()
            assertFalse(
                g.orchestrator.state.value.detail!!.item.isFavorite,
                "a new resolution adopts server truth over the optimistic patch",
            )
        }
}
