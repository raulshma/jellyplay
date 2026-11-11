package com.raulshma.jellyplay.feature.editor

import com.raulshma.jellyplay.core.ui.viewmodel.loadInto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Coverage for the editor feature's one load ladder ([loadInto]) — the
 * suspend-guard choreography behind [EditorViewModel]'s editor-data load (the
 * LiveTvLoad pattern, riding the core:ui ladder). Pins the dispatch contract
 * the load site relies on: start raises the load phase to Loading and clears
 * the banner error BEFORE the fetch, exactly one arm fires, the success arm
 * publishes the fetched payload and settles the phase, the failure arm
 * preserves the previously-loaded detail while surfacing the error, the fetch
 * suspends the ladder so the raised phase is observable mid-flight, and the
 * fetch Result is returned to the caller after its arm ran.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorLoadIntoTest {

    /** Mirrors the EditorUiState fields the load ladder touches — the
     *  v0.11.10 sealed-phase shape ([EditorLoadPhase] + the actionError
     *  banner channel), not the former `isLoading`/`error` boolean pair. */
    private sealed interface FakeLoadPhase {
        data object Loading : FakeLoadPhase
        data object Ready : FakeLoadPhase
        data class Failed(val message: String?) : FakeLoadPhase
    }

    private data class FakeEditorState(
        val detail: String? = null,
        val loadPhase: FakeLoadPhase = FakeLoadPhase.Loading,
        val actionError: String? = null,
    )

    /** The StateFlowHandle.update idiom the ViewModel writes through. */
    private class FakeEditor(initial: FakeEditorState = FakeEditorState()) {
        var state: FakeEditorState = initial
            private set

        fun update(transform: (FakeEditorState) -> FakeEditorState) {
            state = transform(state)
        }
    }

    @Test
    fun `start raises the phase and clears the error before the fetch, success arm publishes and settles`() = runTest {
        val editor = FakeEditor(FakeEditorState(detail = "stale", loadPhase = FakeLoadPhase.Ready, actionError = "old failure"))
        val events = mutableListOf<String>()
        var phaseAtFetch: FakeLoadPhase? = null

        val result = loadInto(
            start = {
                events += "start"
                editor.update { it.copy(loadPhase = FakeLoadPhase.Loading, actionError = null) }
            },
            fetch = {
                events += "fetch"
                phaseAtFetch = editor.state.loadPhase
                // The ladder's whole point: the previous error is gone while
                // the new fetch is in flight.
                assertEquals(null, editor.state.actionError)
                Result.success("fresh detail")
            },
            onSuccess = { detail ->
                events += "onSuccess"
                editor.update { it.copy(detail = detail, loadPhase = FakeLoadPhase.Ready) }
            },
            onFailure = { e ->
                events += "onFailure"
                editor.update { it.copy(loadPhase = FakeLoadPhase.Failed(e.message)) }
            },
        )

        // start raised the phase BEFORE the fetch ran, and only the success arm fired.
        assertEquals(listOf("start", "fetch", "onSuccess"), events)
        assertEquals(FakeLoadPhase.Loading, phaseAtFetch)
        // The success arm settled the phase and published the payload.
        assertEquals(FakeEditorState(detail = "fresh detail", loadPhase = FakeLoadPhase.Ready), editor.state)
        assertTrue(result.isSuccess)
        assertEquals("fresh detail", result.getOrNull())
    }

    @Test
    fun `failure runs only the failure arm and preserves the previously loaded detail`() = runTest {
        val editor = FakeEditor(FakeEditorState(detail = "prior", loadPhase = FakeLoadPhase.Ready))
        val events = mutableListOf<String>()

        val result = loadInto(
            start = {
                events += "start"
                editor.update { it.copy(loadPhase = FakeLoadPhase.Loading, actionError = null) }
            },
            fetch = {
                events += "fetch"
                Result.failure<String>(RuntimeException("detail boom"))
            },
            onSuccess = { detail ->
                events += "onSuccess"
                editor.update { it.copy(detail = detail, loadPhase = FakeLoadPhase.Ready) }
            },
            onFailure = { e ->
                events += "onFailure"
                editor.update { it.copy(loadPhase = FakeLoadPhase.Failed(e.message)) }
            },
        )

        // The failure arm saw the exception and settled the phase; the success
        // arm never ran, so the previously-loaded detail survives the error —
        // the editor's reload-after-subtitle-op path depends on this.
        assertEquals(listOf("start", "fetch", "onFailure"), events)
        assertEquals(FakeLoadPhase.Failed("detail boom"), editor.state.loadPhase)
        assertEquals("prior", editor.state.detail)
        assertEquals("detail boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun `the fetch suspends the ladder so the raised phase is observable mid-flight`() = runTest {
        val editor = FakeEditor(FakeEditorState(loadPhase = FakeLoadPhase.Ready, actionError = "stale"))
        val fetchGate = CompletableDeferred<Unit>()
        val loadJob = launch {
            loadInto(
                start = { editor.update { it.copy(loadPhase = FakeLoadPhase.Loading, actionError = null) } },
                fetch = {
                    fetchGate.await()
                    Result.success("detail")
                },
                onSuccess = { detail -> editor.update { it.copy(detail = detail, loadPhase = FakeLoadPhase.Ready) } },
                onFailure = { e -> editor.update { it.copy(loadPhase = FakeLoadPhase.Failed(e.message)) } },
            )
        }
        runCurrent() // runs the ladder up to the parked fetch

        assertEquals(FakeLoadPhase.Loading, editor.state.loadPhase)
        assertEquals(null, editor.state.actionError)
        assertEquals(null, editor.state.detail)

        fetchGate.complete(Unit)
        loadJob.join()
        assertEquals(FakeEditorState(detail = "detail", loadPhase = FakeLoadPhase.Ready), editor.state)
    }
}
