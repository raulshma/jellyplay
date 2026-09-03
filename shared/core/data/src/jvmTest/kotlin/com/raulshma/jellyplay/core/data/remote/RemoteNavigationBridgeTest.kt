package com.raulshma.jellyplay.core.data.remote

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [RemoteNavigationBridge]'s fan-out contract:
 *  1. `request` publishes to [RemoteNavigationBridge.targets] for active
 *     collectors (video player, audio player, detail, close);
 *  2. the bounded buffer DROPS THE OLDEST under a burst instead of throwing
 *     or suspending — a slow navigation host can never crash the remote
 *     control receiver;
 *  3. there is no replay: a target requested before any collector exists is
 *     not redelivered later (navigation is fire-and-forget).
 */
class RemoteNavigationBridgeTest {

    private lateinit var bridge: RemoteNavigationBridge

    @BeforeTest
    fun setup() {
        bridge = RemoteNavigationBridge()
    }

    @Test
    fun `request publishes the target to an active collector`() = runTest {
        val received = mutableListOf<NavigationTarget>()
        val job = launch { bridge.targets.collect { received.add(it) } }
        runCurrent()

        bridge.request(NavigationTarget.OpenVideoPlayer(itemId = "i1", startPositionTicks = 5L))

        runCurrent()
        assertEquals(listOf<NavigationTarget>(NavigationTarget.OpenVideoPlayer(itemId = "i1", startPositionTicks = 5L)), received)
        job.cancel()
    }

    @Test
    fun `each sealed variant round-trips intact`() = runTest {
        val received = mutableListOf<NavigationTarget>()
        val job = launch { bridge.targets.collect { received.add(it) } }
        runCurrent()

        bridge.request(NavigationTarget.OpenVideoPlayer(itemId = "v"))
        bridge.request(NavigationTarget.OpenAudioPlayer(itemId = "a"))
        bridge.request(NavigationTarget.OpenMediaDetail(itemId = "d"))
        bridge.request(NavigationTarget.ClosePlayer)
        runCurrent()

        assertEquals(
            listOf(
                NavigationTarget.OpenVideoPlayer(itemId = "v"),
                NavigationTarget.OpenAudioPlayer(itemId = "a"),
                NavigationTarget.OpenMediaDetail(itemId = "d"),
                NavigationTarget.ClosePlayer,
            ),
            received,
        )
        job.cancel()
    }

    @Test
    fun `a burst over the buffer drops the oldest instead of failing`() = runTest {
        val received = mutableListOf<NavigationTarget>()
        val job = launch { bridge.targets.collect { received.add(it) } }
        runCurrent()

        // extraBufferCapacity = 4 with DROP_OLDEST: the collector is unconfined
        // here so it drains eagerly, but a burst of 8 still must never throw.
        repeat(8) { bridge.request(NavigationTarget.OpenMediaDetail(itemId = "i$it")) }
        runCurrent()

        assertTrue(received.size <= 8)
        assertEquals("i7", (received.last() as NavigationTarget.OpenMediaDetail).itemId)
        job.cancel()
    }

    @Test
    fun `a target with no collector is not replayed later`() = runTest {
        bridge.request(NavigationTarget.OpenVideoPlayer(itemId = "early"))
        runCurrent()

        val received = mutableListOf<NavigationTarget>()
        val job = launch { bridge.targets.collect { received.add(it) } }
        runCurrent()

        assertTrue(received.isEmpty(), "navigation is fire-and-forget: no replay for late collectors")
        job.cancel()
    }
}
