package com.raulshma.jellyplay.feature.shell

import com.raulshma.jellyplay.core.ui.message.UiText
import com.raulshma.jellyplay.core.ui.message.UserMessage
import com.raulshma.jellyplay.core.ui.message.UserMessageBus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the [UserMessageHost] choreography both shells serve their surfaces:
 * the severity→duration table the Android collectors hand-copied, resolution
 * through the injected resolver, the serial merge (each merged source's
 * message presented exactly once, per-source order kept, a message arriving
 * mid-presentation queued — never dropped, never latest-wins), and the
 * [UserMessageHost.hostAdapted] seam a shell feeds a foreign message payload
 * through. The desktop-regression case pins that a host wired desktop-style
 * (shared bus + shell-local sources merged into one collector) actually
 * presents shared-bus messages — the test the pre-seam desktop shell, which
 * never collected the shared bus, would have failed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserMessageHostTest {

    /**
     * The host-collector launch every test uses: the ShellSessionControllerTest
     * pattern — an eager UnconfinedTestDispatcher on the test scheduler, so
     * the host's merge/collect machinery runs to its first suspension without
     * extra pump steps, and [advanceUntilIdle] drives the rest.
     */
    private fun kotlinx.coroutines.test.TestScope.launchHost(block: suspend () -> Unit) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { block() }

    // ── severity → duration policy ──────────────────────────────────────

    @Test
    fun `the severity table covers every severity`() {
        assertEquals(UserMessageDuration.Short, UserMessageHost.durationFor(UserMessage.Severity.Info))
        assertEquals(UserMessageDuration.Long, UserMessageHost.durationFor(UserMessage.Severity.Error))
    }

    @Test
    fun `info presents Short and error presents Long end to end`() = runTest {
        val presented = mutableListOf<Pair<String, UserMessageDuration>>()
        launchHost {
            host(presented).host(flowOf(info("saved"), error("failed")))
        }
        advanceUntilIdle()

        assertEquals(listOf("saved" to UserMessageDuration.Short, "failed" to UserMessageDuration.Long), presented)
    }

    // ── resolver seam ───────────────────────────────────────────────────

    @Test
    fun `text resolves through the injected resolver, not the raw payload`() = runTest {
        val resolvedInputs = mutableListOf<UiText>()
        val presented = mutableListOf<String>()
        launchHost {
            UserMessageHost(
                resolveText = { text ->
                    resolvedInputs.add(text)
                    "resolved:" + (text as UiText.Raw).value
                },
                present = { text, _ -> presented.add(text) },
            ).host(flowOf(info("raw-text")))
        }
        advanceUntilIdle()

        assertEquals(listOf<UiText>(UiText.Raw("raw-text")), resolvedInputs)
        assertEquals(listOf("resolved:raw-text"), presented)
    }

    // ── merge choreography ──────────────────────────────────────────────

    @Test
    fun `messages on each merged source present exactly once, per-source order kept`() = runTest {
        val presented = mutableListOf<Pair<String, UserMessageDuration>>()
        launchHost {
            host(presented).host(
                flowOf(info("a1"), error("a2")),
                flowOf(error("b1")),
            )
        }
        advanceUntilIdle()

        val texts = presented.map { it.first }
        assertEquals(listOf("a1", "a2", "b1"), texts.sorted(), "every message exactly once")
        assertTrue(texts.indexOf("a1") < texts.indexOf("a2"), "per-source order must hold")
    }

    @Test
    fun `a message arriving while one is showing queues until the surface frees`() = runTest {
        val releaseFirst = CompletableDeferred<Unit>()
        val presented = mutableListOf<String>()
        launchHost {
            UserMessageHost(
                resolveText = { (it as UiText.Raw).value },
                present = { text, _ ->
                    presented.add(text)
                    // The first message "holds the surface": its present only
                    // returns when the test releases it — the second must wait.
                    if (text == "first") releaseFirst.await()
                },
            ).host(flowOf(error("first"), error("second")))
        }
        advanceUntilIdle() // suspends inside the first present
        assertEquals(listOf("first"), presented)

        releaseFirst.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("first", "second"), presented)
    }

    // ── desktop regression: the shared bus must actually be hosted ──────

    @Test
    fun `a desktop-style host presents shared-bus messages the pre-seam shell dropped`() = runTest {
        val sharedBus = UserMessageBus()
        val presented = mutableListOf<Pair<String, UserMessageDuration>>()
        launchHost {
            // The desktop wiring: the shared bus FIRST (the flow the pre-seam
            // shell never collected) plus the shell-local sources slot, empty
            // when a custom MusicMessageBus binding degrades the relay away.
            host(presented).host(sharedBus.messages, emptyFlow())
        }
        advanceUntilIdle() // subscription live before the emission

        sharedBus.error("Couldn't refresh Home")
        advanceUntilIdle()

        assertEquals(listOf("Couldn't refresh Home" to UserMessageDuration.Long), presented)
    }

    // ── hostAdapted: foreign shell-owned payloads ───────────────────────

    @Test
    fun `hostAdapted serves a shell-owned payload with its own severity view and resolver`() = runTest {
        val presented = mutableListOf<Pair<String, UserMessageDuration>>()
        launchHost {
            host(presented).hostAdapted(
                sources = listOf(flowOf(legacy("no player found", isError = true), legacy("saved", isError = false))),
                severityOf = { if (it.isError) UserMessage.Severity.Error else UserMessage.Severity.Info },
                resolveText = { "legacy:" + it.text },
            )
        }
        advanceUntilIdle()

        assertEquals(
            listOf("legacy:no player found" to UserMessageDuration.Long, "legacy:saved" to UserMessageDuration.Short),
            presented,
        )
    }

    // ── helpers ─────────────────────────────────────────────────────────

    /** The shared-payload host every desktop/Android wiring reduces to. */
    private fun host(presented: MutableList<Pair<String, UserMessageDuration>>) = UserMessageHost(
        resolveText = { text -> (text as UiText.Raw).value },
        present = { text, duration -> presented.add(text to duration) },
    )

    private fun info(value: String) = UserMessage.Info(UiText.Raw(value))

    private fun error(value: String) = UserMessage.Error(UiText.Raw(value))

    /** Stand-in for a shell-owned payload type commonMain cannot name. */
    private data class LegacyMessage(val text: String, val isError: Boolean)

    private fun legacy(text: String, isError: Boolean) = LegacyMessage(text, isError)

    private fun <M> flowOf(vararg messages: M): Flow<M> = messages.asFlow()
}
