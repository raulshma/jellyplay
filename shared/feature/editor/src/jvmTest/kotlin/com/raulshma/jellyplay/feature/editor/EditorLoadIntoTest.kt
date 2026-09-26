package com.raulshma.jellyplay.feature.editor

import com.raulshma.jellyplay.core.ui.viewmodel.loadInto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Coverage for the editor feature's one load ladder ([loadInto]) — the
 * `isLoading = true, error = null` suspend-guard choreography behind
 * [EditorViewModel]'s editor-data load (the LiveTvLoad precedent, riding the
 * core:ui ladder per the recorded "can ride loadInto unchanged" note). Pins
 * the dispatch contract the folded site relies on: start raises isLoading and
 * clears the error BEFORE the fetch, exactly one arm fires, the success arm
 * publishes the fetched payload and settles the flag, the failure arm
 * preserves the previously-loaded detail while surfacing the error, the
 * fetch suspends the ladder so the raised flag is observable mid-flight,
 * and the fetch Result is returned to the caller after its arm ran.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorLoadIntoTest {

    /** Mirrors the EditorUiState fields the load ladder touches. */
    private data class FakeEditorState(
        val detail: String? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
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
    fun `start raises the flag and clears the error before the fetch, success arm publishes and settles`() = runTest {
        val editor = FakeEditor(FakeEditorState(detail = "stale", isLoading = false, error = "old failure"))
        val events = mutableListOf<String>()
        var loadingAtFetch: Boolean? = null

        val result = loadInto(
            start = {
                events += "start"
                editor.update { it.copy(isLoading = true, error = null) }
            },
            fetch = {
                events += "fetch"
                loadingAtFetch = editor.state.isLoading
                // The ladder's whole point: the previous error is gone while
                // the new fetch is in flight.
                assertEquals(null, editor.state.error)
                Result.success("fresh detail")
            },
            onSuccess = { detail ->
                events += "onSuccess"
                editor.update { it.copy(detail = detail, isLoading = false) }
            },
            onFailure = { e ->
                events += "onFailure"
                editor.update { it.copy(isLoading = false, error = e.message) }
            },
        )

        // start raised the flag BEFORE the fetch ran, and only the success arm fired.
        assertEquals(listOf("start", "fetch", "onSuccess"), events)
        assertEquals(true, loadingAtFetch)
        // The success arm settled the flag and published the payload.
        assertEquals(FakeEditorState(detail = "fresh detail", isLoading = false), editor.state)
        assertTrue(result.isSuccess)
        assertEquals("fresh detail", result.getOrNull())
    }

    @Test
    fun `failure runs only the failure arm and preserves the previously loaded detail`() = runTest {
        val editor = FakeEditor(FakeEditorState(detail = "prior", isLoading = false))
        val events = mutableListOf<String>()

        val result = loadInto(
            start = {
                events += "start"
                editor.update { it.copy(isLoading = true, error = null) }
            },
            fetch = {
                events += "fetch"
                Result.failure<String>(RuntimeException("detail boom"))
            },
            onSuccess = { detail ->
                events += "onSuccess"
                editor.update { it.copy(detail = detail, isLoading = false) }
            },
            onFailure = { e ->
                events += "onFailure"
                editor.update { it.copy(isLoading = false, error = e.message) }
            },
        )

        // The failure arm saw the exception and settled the flag; the success
        // arm never ran, so the previously-loaded detail survives the error —
        // the editor's reload-after-subtitle-op path depends on this.
        assertEquals(listOf("start", "fetch", "onFailure"), events)
        assertEquals("detail boom", editor.state.error)
        assertFalse(editor.state.isLoading)
        assertEquals("prior", editor.state.detail)
        assertEquals("detail boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun `the fetch suspends the ladder so the raised flag is observable mid-flight`() = runTest {
        val editor = FakeEditor(FakeEditorState(isLoading = false, error = "stale"))
        val fetchGate = CompletableDeferred<Unit>()
        val loadJob = launch {
            loadInto(
                start = { editor.update { it.copy(isLoading = true, error = null) } },
                fetch = {
                    fetchGate.await()
                    Result.success("detail")
                },
                onSuccess = { detail -> editor.update { it.copy(detail = detail, isLoading = false) } },
                onFailure = { e -> editor.update { it.copy(isLoading = false, error = e.message) } },
            )
        }
        runCurrent() // runs the ladder up to the parked fetch

        assertTrue(editor.state.isLoading)
        assertEquals(null, editor.state.error)
        assertEquals(null, editor.state.detail)

        fetchGate.complete(Unit)
        loadJob.join()
        assertEquals(FakeEditorState(detail = "detail", isLoading = false), editor.state)
    }
}
