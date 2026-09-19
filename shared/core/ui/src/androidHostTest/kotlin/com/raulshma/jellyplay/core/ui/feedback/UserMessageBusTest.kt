package com.raulshma.jellyplay.core.ui.feedback

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [UserMessageBus]'s one-shot event semantics:
 *
 * - Messages posted while no collector is attached are buffered, not dropped.
 * - A single collector receives every posted message exactly once, in order.
 * - A collector that subscribes late receives NOTHING (no replay, no state) —
 *   `receiveAsFlow` must behave like an event stream, not a state flow.
 */
class UserMessageBusTest {

    @Test
    fun `single collector receives every posted message exactly once in order`() = runTest {
        val bus = UserMessageBus()
        val received = async { bus.messages.take(3).toList() }

        // Posted before the collector has even started running — the buffered
        // channel must hold them.
        bus.info(UiText.Raw("first"))
        bus.error("second")
        bus.info("third")

        val messages = received.await()

        assertEquals(3, messages.size)
        assertTrue(messages[0] is UserMessage.Info)
        assertTrue(messages[1] is UserMessage.Error)
        assertTrue(messages[2] is UserMessage.Info)
        assertEquals("first", (messages[0].text as UiText.Raw).value)
        assertEquals("second", (messages[1].text as UiText.Raw).value)
        assertEquals("third", (messages[2].text as UiText.Raw).value)
    }

    @Test
    fun `late second collector receives nothing replayed`() = runTest {
        val bus = UserMessageBus()
        val first = async { bus.messages.take(1).toList() }
        bus.info("only for the first consumer")

        assertEquals(1, first.await().size)

        // Virtual time makes this instant; a replayed message would surface here.
        val replayed = withTimeoutOrNull(1_000) { bus.messages.first() }
        assertNull("late collector must not receive replayed messages", replayed)
    }

    @Test
    fun `messages posted after the collector subscribed are delivered`() = runTest {
        val bus = UserMessageBus()
        val received = mutableListOf<UserMessage>()
        val job = launch { bus.messages.collect { received += it } }
        runCurrent()

        bus.info(UiText.Raw("live"))

        runCurrent()
        assertEquals(1, received.size)
        job.cancel()
    }

    @Test
    fun `severity derives Info for Info and Error for Error`() {
        assertEquals(UserMessage.Severity.Info, UserMessage.Info(UiText.Raw("x")).severity)
        assertEquals(UserMessage.Severity.Error, UserMessage.Error(UiText.Raw("x")).severity)
        assertEquals(UserMessage.Severity.Error, UserMessage.Error(UiText.Raw("raw")).severity)
    }
}
