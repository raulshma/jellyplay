package com.raulshma.jellyplay.feature.music.feedback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/**
 * Wave 21B relay: the desktop MusicMessageBus actual buffers error messages
 * for the shell's snackbar host instead of dropping them. Pins the delivery
 * contract (a subscribed host receives what error() emitted), the drop-oldest
 * overflow shape (a slow host keeps the NEWEST messages of a burst), and the
 * Koin binding the shell collects through.
 */
class DesktopMusicMessageBusTest {

    @Test
    fun `error delivers the message to a subscribed host`() = runTest {
        val bus = DesktopMusicMessageBus()
        var delivered: String? = null
        val host = launch { delivered = bus.messages.first() } // the "snackbar host"

        yield() // let the host subscribe before the emission lands
        bus.error("Music refresh failed")
        host.join()

        assertEquals(
            "Music refresh failed",
            delivered,
            "a subscribed host must receive the emitted message",
        )
    }

    @Test
    fun `burst past the buffer keeps the newest messages for a slow host`() = runTest {
        val bus = DesktopMusicMessageBus()
        val collected = mutableListOf<String>()
        // UNDISPATCHED so the host is SUBSCRIBED (suspended on the first
        // value) before the burst starts — only a subscribed-but-slow host
        // exercises the buffer; emissions with no subscribers are dropped
        // (replay is 0) by design, not buffered.
        val host = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.messages.take(16).toList(collected)
        }

        repeat(20) { n -> bus.error("failure $n") } // tryEmit never suspends
        host.join()

        assertEquals(
            (4..19).map { "failure $it" },
            collected,
            "drop-oldest must keep the NEWEST 16 of the 20-message burst",
        )
    }

    @Test
    fun `koin module binds the relay as the MusicMessageBus`() {
        val koin = startKoin { modules(desktopMusicMessageBusModule()) }
        try {
            val bound: MusicMessageBus = koin.koin.get()
            assertEquals(
                DesktopMusicMessageBus::class,
                bound::class,
                "the shell collects the relay through the MusicMessageBus binding",
            )
        } finally {
            stopKoin()
        }
    }
}
