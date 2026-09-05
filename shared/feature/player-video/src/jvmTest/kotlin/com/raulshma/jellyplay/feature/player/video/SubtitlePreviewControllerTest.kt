package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import com.raulshma.jellyplay.feature.player.video.engine.TimedCue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [SubtitlePreviewController] after the cue-preview trio
 * (`subtitlePreviewCues` / `subtitlePreviewSource` / `previewSheetVisible`)
 * moved out of [VideoPlayerUiState]: the test surface is the controller's
 * [SubtitlePreviewState] flow + its commands — no ViewModel, no uiState.
 * All dependencies are plain constructor-lambda fakes (the seams the VM wires).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubtitlePreviewControllerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var controller: SubtitlePreviewController

    private var externalSubs: List<SubtitleSource>? = null
    private var selectedTrack: TrackOption? = null
    private var engineCues: List<TimedCue>? = null
    private var playbackHeaders: Map<String, String>? = null
    private var cacheCleared = false

    /** Per-source cue results the load seam hands out; a deferred makes a source's load suspend until released. */
    private val loadResults = mutableMapOf<String, List<TimedCue>?>()
    private val loadGates = mutableMapOf<String, CompletableDeferred<Unit>>()
    private val loadCalls = mutableListOf<String>()

    private fun cue(text: String) = TimedCue(startTimeUs = 0L, endTimeUs = 1_000_000L, text = text)

    private fun source(id: String, label: String = id) = SubtitleSource(
        url = "https://example.invalid/$id.srt",
        label = label,
        language = "eng",
        mimeType = "application/x-subrip",
        id = id,
    )

    private fun selected(id: String? = null, label: String? = null, index: Int = 3) = TrackOption(
        index = index,
        label = label ?: "whatever",
        language = "eng",
        isSelected = true,
        id = id,
    )

    @BeforeTest
    fun setUp() {
        externalSubs = null
        selectedTrack = null
        engineCues = null
        playbackHeaders = mapOf("X-Token" to "t")
        cacheCleared = false
        loadResults.clear()
        loadGates.clear()
        loadCalls.clear()

        controller = SubtitlePreviewController(
            scope = testScope,
            loadCues = { source, _ ->
                loadCalls.add(source.id)
                loadGates[source.id]?.await()
                loadResults[source.id]
            },
            clearCuesCache = { cacheCleared = true },
            getExternalSubtitles = { externalSubs },
            getPlaybackHeaders = { playbackHeaders },
            getSelectedSubtitleTrack = { selectedTrack },
            getEngineCues = { engineCues },
        )
    }

    // ── onTrackSelectionChanged: source resolution ───────────────────────────

    @Test
    fun `selected track resolves external source by exact id first`() {
        val byId = source(id = "external-a", label = "Label A")
        val byLabel = source(id = "external-b", label = "Shared Label")
        val decoy = source(id = "external-c", label = "Shared Label")
        externalSubs = listOf(byId, byLabel, decoy)
        loadResults["external-a"] = listOf(cue("id-match"))
        // Label matches two sources but the id matches external-a — id wins.
        selectedTrack = selected(id = "external-a", label = "Shared Label")

        controller.onTrackSelectionChanged()

        assertEquals(listOf("external-a"), loadCalls)
        assertEquals(SubtitlePreviewSource.EXTERNAL, controller.state.value.source)
        assertEquals(listOf(cue("id-match")), controller.state.value.cues)
    }

    @Test
    fun `without id match the label match resolves the source`() {
        val track = source(id = "external-b", label = "English")
        externalSubs = listOf(source(id = "external-a", label = "Spanish"), track)
        loadResults["external-b"] = listOf(cue("label-match"))
        selectedTrack = selected(id = null, label = "English")

        controller.onTrackSelectionChanged()

        assertEquals(listOf("external-b"), loadCalls)
        assertEquals(SubtitlePreviewSource.EXTERNAL, controller.state.value.source)
        assertEquals(listOf(cue("label-match")), controller.state.value.cues)
    }

    @Test
    fun `selected track with no resolvable external source clears the preview`() {
        externalSubs = listOf(source(id = "external-a", label = "Spanish"))
        selectedTrack = selected(id = null, label = "German")
        controller.onTrackSelectionChanged()

        assertEquals(emptyList(), loadCalls)
        assertEquals(SubtitlePreviewSource.NONE, controller.state.value.source)
        assertEquals(null, controller.state.value.cues)
    }

    @Test
    fun `no external subtitles clears the preview so embedded cues can take over`() {
        externalSubs = emptyList()
        controller.setSheetVisible(true)
        engineCues = listOf(cue("embedded"))
        controller.onEngineCues(listOf(cue("embedded")))

        controller.onTrackSelectionChanged()

        assertEquals(SubtitlePreviewSource.NONE, controller.state.value.source)
        assertEquals(null, controller.state.value.cues)
    }

    @Test
    fun `unparseable external source clears instead of guessing another track`() {
        externalSubs = listOf(source(id = "external-a"))
        loadResults["external-a"] = null // image subs / unsupported codec
        selectedTrack = selected(id = "external-a")

        controller.onTrackSelectionChanged()

        assertEquals(SubtitlePreviewSource.NONE, controller.state.value.source)
        assertEquals(null, controller.state.value.cues)
    }

    // ── EXTERNAL vs EMBEDDED precedence ──────────────────────────────────────

    @Test
    fun `external source wins over the engine cue pump`() {
        externalSubs = listOf(source(id = "external-a"))
        loadResults["external-a"] = listOf(cue("external"))
        selectedTrack = selected(id = "external-a")
        controller.onTrackSelectionChanged()
        controller.setSheetVisible(true)

        controller.onEngineCues(listOf(cue("embedded")))

        assertEquals(SubtitlePreviewSource.EXTERNAL, controller.state.value.source)
        assertEquals(listOf(cue("external")), controller.state.value.cues)
    }

    @Test
    fun `engine cues populate EMBEDDED only when no external source is active`() {
        controller.setSheetVisible(true)
        val cues = listOf(cue("embedded"))
        controller.onEngineCues(cues)

        assertEquals(SubtitlePreviewSource.EMBEDDED, controller.state.value.source)
        assertEquals(cues, controller.state.value.cues)

        // Empty cue lists clear back to NONE (the engine fires empty between cues).
        controller.onEngineCues(emptyList())
        assertEquals(SubtitlePreviewSource.NONE, controller.state.value.source)
        assertEquals(null, controller.state.value.cues)
    }

    // ── Sheet-visible gating ─────────────────────────────────────────────────

    @Test
    fun `engine cue pump is gated while the sheet is closed`() {
        // No external source active; sheet closed (default).
        controller.onEngineCues(listOf(cue("played-range")))
        assertEquals(SubtitlePreviewSource.NONE, controller.state.value.source)
        assertEquals(null, controller.state.value.cues)
    }

    @Test
    fun `opening the sheet re-syncs the embedded preview from the engine`() {
        engineCues = listOf(cue("engine-now"))
        controller.setSheetVisible(true)

        assertEquals(true, controller.state.value.sheetVisible)
        assertEquals(SubtitlePreviewSource.EMBEDDED, controller.state.value.source)
        assertEquals(listOf(cue("engine-now")), controller.state.value.cues)
    }

    @Test
    fun `opening the sheet with no engine cues and no external source stays NONE`() {
        controller.setSheetVisible(true)
        assertEquals(SubtitlePreviewSource.NONE, controller.state.value.source)
        assertEquals(null, controller.state.value.cues)
    }

    // ── Stale-load cancellation ──────────────────────────────────────────────

    @Test
    fun `fast track switch wins - a slow stale load never overwrites the newer result`() {
        externalSubs = listOf(source(id = "slow"), source(id = "fast"))
        loadResults["slow"] = listOf(cue("stale"))
        loadResults["fast"] = listOf(cue("fresh"))
        val slowGate = CompletableDeferred<Unit>()
        loadGates["slow"] = slowGate

        selectedTrack = selected(id = "slow")
        controller.onTrackSelectionChanged()
        assertEquals(listOf("slow"), loadCalls)

        // Switch before the slow load finishes; the fast load must complete first.
        selectedTrack = selected(id = "fast")
        controller.onTrackSelectionChanged()
        assertEquals(listOf("slow", "fast"), loadCalls)
        assertEquals(listOf(cue("fresh")), controller.state.value.cues)

        // Release the stale load: its result must be dropped (cancelled job).
        slowGate.complete(Unit)
        testScope.testScheduler.advanceUntilIdle()
        assertEquals(listOf(cue("fresh")), controller.state.value.cues)
        assertEquals(SubtitlePreviewSource.EXTERNAL, controller.state.value.source)
    }

    // ── clear / reset ────────────────────────────────────────────────────────

    @Test
    fun `clearCues drops the preview and the repository cache`() {
        externalSubs = listOf(source(id = "external-a"))
        loadResults["external-a"] = listOf(cue("external"))
        selectedTrack = selected(id = "external-a")
        controller.onTrackSelectionChanged()
        assertTrue(cacheCleared.not()) // loads do not clear the cache

        controller.clearCues()

        assertTrue(cacheCleared)
        assertEquals(null, controller.state.value.cues)
        assertEquals(SubtitlePreviewSource.NONE, controller.state.value.source)
    }

    @Test
    fun `resetForItem clears preview, cache and the sheet flag`() {
        controller.setSheetVisible(true)

        controller.resetForItem()

        assertTrue(cacheCleared)
        assertEquals(false, controller.state.value.sheetVisible)
        assertEquals(null, controller.state.value.cues)
        assertEquals(SubtitlePreviewSource.NONE, controller.state.value.source)
    }

    @Test
    fun `resetForItem cancels an in-flight external load`() {
        externalSubs = listOf(source(id = "slow"))
        loadResults["slow"] = listOf(cue("stale"))
        val gate = CompletableDeferred<Unit>()
        loadGates["slow"] = gate
        selectedTrack = selected(id = "slow")
        controller.onTrackSelectionChanged()

        controller.resetForItem()
        gate.complete(Unit)
        testScope.testScheduler.advanceUntilIdle()

        assertEquals(null, controller.state.value.cues)
        assertEquals(SubtitlePreviewSource.NONE, controller.state.value.source)
    }
}
