package com.raulshma.jellyplay.core.ui.message

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.cancelAndJoin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the one-shot semantics of [UserMessageBus]: each posted message is
 * delivered to a single collector exactly once, in post order; messages posted
 * before any collector (or while one is slow) are buffered by the underlying
 * `Channel(Channel.BUFFERED)`, never lost and never conflated; and a consumer
 * subscribing after the stream was drained receives NO replay. Also pins the
 * [UserMessage.severity] mapping table (Info→Info, Error→Error) and the
 * `String` convenience factories wrapping dynamic text as [UiText.Raw].
 *
 * Drop observability (DECLARED FIX): a [Channel.trySend] into a FULL buffer
 * fails without throwing — the drop is reported through the module's
 * `logUiWarning` seam (stderr on the JVM, the [UiLogJvmTest capture idiom])
 * instead of vanishing silently, and a successful post stays silent.
 */
class UserMessageBusTest {

    private fun rawText(message: UserMessage): String =
        (message.text as UiText.Raw).value

    @Test
    fun `emitted messages reach the single collector exactly once and in post order`() = runTest {
        val bus = UserMessageBus()
        val received = mutableListOf<UserMessage>()
        val collector = launch { bus.messages.collect { received += it } }

        repeat(5) { bus.emit(UserMessage.Info(UiText.Raw("m$it"))) }
        bus.error("boom")
        runCurrent() // let the collector drain the buffered channel

        collector.cancelAndJoin()

        assertEquals(
            listOf("m0", "m1", "m2", "m3", "m4", "boom"),
            received.map { rawText(it) },
            "FIFO channel: the collector sees every post once, in order",
        )
        // Type mix survived the trip unchanged.
        assertEquals(UserMessage.Severity.Error, received.last().severity)
        assertTrue(received.take(5).all { it is UserMessage.Info })
    }

    @Test
    fun `messages posted before any collector are buffered and not lost`() = runTest {
        val bus = UserMessageBus()
        // No subscriber yet — Channel.BUFFERED must hold these.
        repeat(3) { bus.info("pre$it") }

        val received = bus.messages.take(3).toList()

        assertEquals(listOf("pre0", "pre1", "pre2"), received.map { rawText(it) })
    }

    @Test
    fun `identical messages are delivered once per post - no conflation`() = runTest {
        val bus = UserMessageBus()
        // A StateFlow would conflate these to one; the bus must not.
        bus.info("same")
        bus.info("same")

        val received = bus.messages.take(2).toList()

        assertEquals(2, received.size, "two posts of the same message deliver twice")
    }

    @Test
    fun `late second consumer receives no replay`() = runTest {
        val bus = UserMessageBus()
        val first = launch { bus.messages.take(2).toList() }
        bus.info("a")
        bus.error("b")
        first.join() // the first consumer drained the channel

        // One-shot semantics: a consumer arriving after the drain suspends
        // forever instead of replaying history. Virtual-time timeout fires
        // instantly when (and only when) nothing arrives.
        val replayed = withTimeoutOrNull(5_000) { bus.messages.first() }
        assertNull(replayed, "a late consumer must not receive already-consumed messages")
    }

    @Test
    fun `severity mapping - Info and Error map to their own Severity`() {
        val info = UserMessage.Info(UiText.Raw("i"))
        val error = UserMessage.Error(UiText.Raw("e"))

        assertEquals(UserMessage.Severity.Info, info.severity)
        assertEquals(UserMessage.Severity.Error, error.severity)
    }

    @Test
    fun `info(String) posts an Info wrapping Raw text`() = runTest {
        val bus = UserMessageBus()
        bus.info("dynamic info")

        val message = bus.messages.first()

        assertTrue(message is UserMessage.Info)
        assertEquals(UiText.Raw("dynamic info"), message.text)
        assertEquals(UserMessage.Severity.Info, message.severity)
    }

    @Test
    fun `error(String) posts an Error wrapping Raw text`() = runTest {
        val bus = UserMessageBus()
        bus.error("dynamic error")

        val message = bus.messages.first()

        assertTrue(message is UserMessage.Error)
        assertEquals(UiText.Raw("dynamic error"), message.text)
        assertEquals(UserMessage.Severity.Error, message.severity)
    }

    @Test
    fun `emit carries the exact message instance through the channel`() = runTest {
        val bus = UserMessageBus()
        val posted = UserMessage.Error(UiText.Raw("exact"))
        bus.emit(posted)

        assertEquals(posted, bus.messages.first())
    }

    // ── drop observability (DECLARED FIX pins) ──────────────────────────

    /** Captures stderr for the duration of [block] (the UiLogJvmTest idiom). */
    private fun withCapturedStderr(block: () -> Unit): String {
        val originalErr = System.err
        val errBuffer = ByteArrayOutputStream()
        System.setErr(PrintStream(errBuffer, true, Charsets.UTF_8))
        try {
            block()
        } finally {
            System.setErr(originalErr)
        }
        return errBuffer.toString(Charsets.UTF_8)
    }

    @Test
    fun `a full-buffer trySend does not throw and records the drop`() {
        val bus = UserMessageBus()
        // Fill the bus's Channel(Channel.BUFFERED) with no collector attached.
        // OVERFILL far past any capacity the factory default resolves to: the
        // Channel.BUFFERED constant is the magic sentinel -2 (and its resolved
        // capacity is not a public constant), so the row does not bake one in —
        // 10k emissions guarantee a full buffer and an unobservable drop.
        repeat(10_000) { bus.emit(UserMessage.Info(UiText.Raw("fill $it"))) }

        // The overflow post must not throw even though the channel is full —
        // and the dropped message is OBSERVABLE: logged through logUiWarning,
        // whose JVM actual writes the W/<tag> line to stderr.
        val stderr = withCapturedStderr { bus.error("the dropped one") }

        assertTrue(
            stderr.contains("W/UserMessageBus"),
            "a failed trySend must be observable through the logging seam, got: $stderr",
        )
        assertTrue(
            stderr.contains("dropped user message"),
            "the drop log must name the dropped message, got: $stderr",
        )
    }

    @Test
    fun `a successful post stays silent`() {
        val bus = UserMessageBus()
        // Far below capacity: every post succeeds and must log nothing.
        val stderr = withCapturedStderr {
            bus.info("delivered later")
            bus.error("delivered later too")
        }

        assertEquals("", stderr, "a successful post must not pollute the warning stream")
    }
}
