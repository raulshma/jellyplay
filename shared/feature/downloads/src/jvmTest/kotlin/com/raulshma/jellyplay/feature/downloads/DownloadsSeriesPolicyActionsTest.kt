package com.raulshma.jellyplay.feature.downloads

import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.repository.SeriesDownloadArm
import com.raulshma.jellyplay.core.data.repository.SeriesDownloadPolicyStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.AutoDownloadMode
import com.raulshma.jellyplay.core.model.AutoDownloadSuggestion
import com.raulshma.jellyplay.core.testfixtures.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule

/**
 * Pins the ADR-0015 armed-series surface — the [DownloadsSeriesPolicyActions]
 * holder the VM's `seriesPolicyActions` accessor exposes — and the pure
 * [armedSeriesRows] fold beside it:
 *
 * 1. Resolution: a FOLLOW_GLOBAL arm resolves through
 *    [com.raulshma.jellyplay.core.model.resolveAgainst] (the one home)
 *    against the LIVE global mode — FOLLOW_GLOBAL × global INSTANT renders
 *    the follows-global story with the resolved INSTANT label, and flipping
 *    the global preference re-resolves without any arm-row write. Explicit
 *    overrides render verbatim; an explicit NEVER row stays LISTED (the
 *    check visits-and-skips it — hiding it would strand the arm).
 * 2. The nameless arm (a sheet-written row before its first episode download
 *    backfills the name) falls back to the series id.
 * 3. Deterministic display order (name case-insensitive, id tiebreaker).
 * 4. `unArmSeries` dispatches THE disarm verb — `setSeriesPolicy(id, null)` —
 *    and nothing else (the sheet's Default chip never writes it).
 * 5. The relocated gh-181 suggestion confirm/dismiss still pass through the
 *    holder (the same behavior the pre-relocation suites reached via the VM,
 *    pinned here now that the members moved).
 *
 * Drives the holder DIRECTLY (the DownloadsResyncActions extraction
 * precedent): the policy/intake/store doubles are enough — no full-VM
 * construction.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsSeriesPolicyActionsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var policyRepository: SeriesDownloadPolicyStore
    private lateinit var intake: DownloadIntake
    private lateinit var downloadsStore: DownloadsStore

    /** Test-owned holder scope (the resync mirror test's mechanics). */
    private var holderScope: CoroutineScope? = null

    /** Backing flow behind observeArmedSeries so tests can push arm rows. */
    private lateinit var armedFlow: MutableStateFlow<List<SeriesDownloadArm>>

    /** Backing flow behind the downloads preference slice (the global mode). */
    private lateinit var storeSliceFlow: MutableStateFlow<DownloadsSlice>

    /** Every message the holder reported through its sink, in order. */
    private val sentMessages = mutableListOf<DownloadsUserMessage>()

    @BeforeTest
    fun setUp() {
        policyRepository = mockk(relaxed = true)
        intake = mockk(relaxed = true)
        armedFlow = MutableStateFlow(emptyList())
        storeSliceFlow = MutableStateFlow(DownloadsSlice())
        every { policyRepository.observeArmedSeries() } returns armedFlow
        every { policyRepository.observeAutoDownloadSuggestions() } returns MutableStateFlow(emptyList())
        downloadsStore = mockk(relaxed = true)
        every { downloadsStore.downloads } returns storeSliceFlow
        sentMessages.clear()
    }

    @AfterTest
    fun tearDown() {
        holderScope?.cancel()
    }

    /**
     * The holder under test, wired to this test's doubles. Call it on a
     * test-owned scope riding [mainDispatcherRule]'s dispatcher (cancelled in
     * [tearDown]) — the resync holder mirror test's mechanics: this scheduler
     * topology does not progress `backgroundScope` launches, and the
     * WhileSubscribed sharing must run on the shared test scheduler.
     */
    private fun CoroutineScope.policyActions() = DownloadsSeriesPolicyActions(
        scope = this,
        policyStore = policyRepository,
        intake = intake,
        downloadsStore = downloadsStore,
        postMessage = { sentMessages += it },
    )

    private fun arm(
        seriesId: String,
        mode: AutoDownloadMode,
        seriesName: String = "Series $seriesId",
    ) = SeriesDownloadArm(seriesId = seriesId, mode = mode, seriesName = seriesName)

    // ── the pure fold ─────────────────────────────────────────────────────

    @Test
    fun follow_global_arm_resolves_against_the_global_mode() {
        val rows = armedSeriesRows(listOf(arm("s1", AutoDownloadMode.FOLLOW_GLOBAL)), AutoDownloadMode.INSTANT)
        val row = rows.single()
        assertTrue(row.followsGlobal)
        assertEquals(AutoDownloadMode.INSTANT, row.resolvedMode)
        // Same row, global flipped: re-resolves — never snapshot into the arm.
        assertEquals(AutoDownloadMode.NEVER, armedSeriesRows(listOf(arm("s1", AutoDownloadMode.FOLLOW_GLOBAL)), AutoDownloadMode.NEVER).single().resolvedMode)
    }

    @Test
    fun explicit_overrides_resolve_verbatim_and_a_never_row_stays_listed() {
        val rows = armedSeriesRows(
            listOf(
                arm("s1", AutoDownloadMode.INSTANT),
                arm("s2", AutoDownloadMode.ASK),
                arm("s3", AutoDownloadMode.NEVER),
            ),
            AutoDownloadMode.ASK,
        )
        assertEquals(
            listOf(AutoDownloadMode.INSTANT, AutoDownloadMode.ASK, AutoDownloadMode.NEVER),
            rows.map { it.resolvedMode },
        )
        // The explicit NEVER is a live arm (visited-and-skipped): rendered,
        // and not dressed up as a follows-global row.
        val never = rows.single { it.seriesId == "s3" }
        assertFalse(never.followsGlobal)
    }

    @Test
    fun nameless_arm_falls_back_to_the_series_id() {
        val rows = armedSeriesRows(listOf(arm("s9", AutoDownloadMode.ASK, seriesName = "")), AutoDownloadMode.NEVER)
        assertEquals("s9", rows.single().displayName)
    }

    @Test
    fun rows_sort_by_display_name_then_series_id() {
        val rows = armedSeriesRows(
            listOf(
                arm("b-late", AutoDownloadMode.ASK, seriesName = "blue"),
                arm("a-late", AutoDownloadMode.ASK, seriesName = "Blue"),
                arm("z", AutoDownloadMode.ASK, seriesName = "Alpha"),
            ),
            AutoDownloadMode.NEVER,
        )
        // Name-first order: "Alpha" leads; the case-insensitive blue/Blue tie
        // falls to the id (a-late before b-late).
        assertEquals(listOf("z", "a-late", "b-late"), rows.map { it.seriesId })
    }

    // ── the holder wiring ─────────────────────────────────────────────────

    @Test
    fun armed_series_mirrors_the_arm_flow_resolved_against_the_live_global_mode() =
        runTest(mainDispatcherRule.testDispatcher) {
            holderScope = CoroutineScope(mainDispatcherRule.testDispatcher)
            val actions = holderScope!!.policyActions()
            // The mirror test's mechanics: collect on the test scope (the
            // WhileSubscribed sharing starts), cancel the collector before the
            // body ends — runTest fails on active children otherwise.
            val warmJob = launch { actions.armedSeries.collect { /* warm */ } }

            armedFlow.value = listOf(
                arm("s1", AutoDownloadMode.FOLLOW_GLOBAL),
                arm("s2", AutoDownloadMode.INSTANT),
            )
            storeSliceFlow.value = DownloadsSlice(autoDownloadMode = AutoDownloadMode.INSTANT)
            advanceUntilIdle()

            val rows = actions.armedSeries.value
            assertEquals(2, rows.size)
            assertEquals(AutoDownloadMode.INSTANT, rows.single { it.seriesId == "s1" }.resolvedMode)
            assertTrue(rows.single { it.seriesId == "s1" }.followsGlobal)

            // Flipping the global preference re-renders the follows-global
            // labels — the arm row itself is untouched.
            storeSliceFlow.value = DownloadsSlice(autoDownloadMode = AutoDownloadMode.ASK)
            advanceUntilIdle()
            val reResolved = actions.armedSeries.value.single { it.seriesId == "s1" }
            assertEquals(AutoDownloadMode.ASK, reResolved.resolvedMode)
            assertTrue(reResolved.followsGlobal)
            warmJob.cancel()
        }

    @Test
    fun unArmSeries_dispatches_the_disarm_verb() = runTest(mainDispatcherRule.testDispatcher) {
        holderScope = CoroutineScope(mainDispatcherRule.testDispatcher)
        val actions = holderScope!!.policyActions()

        actions.unArmSeries("s1")
        advanceUntilIdle()

        coVerify(exactly = 1) { policyRepository.setSeriesPolicy("s1", null) }
    }

    // ── the relocated suggestion family (gh-181) ──────────────────────────

    @Test
    fun downloadSuggestions_confirms_through_the_intake_and_reports_queued() =
        runTest(mainDispatcherRule.testDispatcher) {
            holderScope = CoroutineScope(mainDispatcherRule.testDispatcher)
            val actions = holderScope!!.policyActions()
            val prompt = AutoDownloadSuggestion(
                episodeId = "e2",
                seriesId = "s1",
                seriesName = "Show",
                seasonId = "season1",
                seasonName = "Season 1",
                seasonNumber = 1,
                episodeNumber = 2,
                name = "Episode 2",
            )
            coEvery { policyRepository.autoDownloadSuggestionsByIds(listOf("e2")) } returns listOf(prompt)
            coEvery { intake.startSeries(any(), any(), any()) } returns Result.success(listOf("d1"))

            actions.downloadSuggestions(listOf("e2"))
            advanceUntilIdle()

            // The confirm path enqueues through the normal series intake — no
            // policy write rides it (the sheet owns policy choices).
            coVerify(exactly = 1) { intake.startSeries(seriesId = "s1", episodeIds = any(), policyChoice = any()) }
            coVerify(exactly = 0) { policyRepository.setSeriesPolicy(any<String>(), any<AutoDownloadMode>()) }
            coVerify(exactly = 1) { policyRepository.dismissAutoDownloadSuggestions(listOf("e2")) }
            // Explicit element type: the data object's precise type vs the
            // MutableList's wider element type leaves K2's solver no common
            // supertype without it (ONLY_INPUT_TYPES inference failure).
            assertEquals(listOf<DownloadsUserMessage>(DownloadsUserMessage.SuggestionsQueued), sentMessages)
        }

    @Test
    fun downloadSuggestions_surfaces_a_failed_batch_and_leaves_the_prompt_pending() =
        runTest(mainDispatcherRule.testDispatcher) {
            holderScope = CoroutineScope(mainDispatcherRule.testDispatcher)
            val actions = holderScope!!.policyActions()
            val prompt = AutoDownloadSuggestion(
                episodeId = "e2",
                seriesId = "s1",
                seriesName = "Show",
                seasonId = "season1",
                seasonName = "Season 1",
                seasonNumber = 1,
                episodeNumber = 2,
                name = "Episode 2",
            )
            coEvery { policyRepository.autoDownloadSuggestionsByIds(listOf("e2")) } returns listOf(prompt)
            coEvery { intake.startSeries(any(), any(), any()) } returns Result.failure(IllegalStateException("server offline"))

            actions.downloadSuggestions(listOf("e2"))
            advanceUntilIdle()

            // A failed batch is surfaced as Raw text; its prompt stays pending
            // (no dismiss write) so the user can retry.
            coVerify(exactly = 0) { policyRepository.dismissAutoDownloadSuggestions(any()) }
            assertEquals(1, sentMessages.size)
            assertTrue(sentMessages.single() is DownloadsUserMessage.Raw)
            assertEquals("server offline", (sentMessages.single() as DownloadsUserMessage.Raw).text)
        }

    @Test
    fun dismissSuggestions_tombstones_without_downloading() = runTest(mainDispatcherRule.testDispatcher) {
        holderScope = CoroutineScope(mainDispatcherRule.testDispatcher)
        val actions = holderScope!!.policyActions()

        actions.dismissSuggestions(listOf("e2"))
        advanceUntilIdle()

        coVerify(exactly = 1) { policyRepository.dismissAutoDownloadSuggestions(listOf("e2")) }
        coVerify(exactly = 0) { intake.startSeries(any(), any(), any()) }
    }
}
