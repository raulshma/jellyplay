package com.raulshma.jellyplay.core.data.cast.remote

import android.content.Context
import com.raulshma.jellyplay.core.data.cast.CastDevice
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the [PlayOnController] facade invariant: it is a **thin delegation
 * layer** over [JellyfinRemotePlayCastStrategy] — every transport call is
 * forwarded verbatim and in call order (fling → `loadMedia` with all five
 * arguments passed through untouched), and the connection state is the
 * strategy's own flows re-exposed (never a copy). No CastManager is
 * consulted: Play On keeps an independent connection, deliberately isolated
 * from the video player's cast UI flag.
 */
class PlayOnControllerTest {

    private val strategy: JellyfinRemotePlayCastStrategy = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    /** Fresh controller per test — its constructor reads the strategy's flows. */
    private fun controller() = PlayOnController(strategy)

    @Test
    fun `fling forwards all arguments to loadMedia untouched`() {
        controller().fling(
            itemId = "item-1",
            startPositionMs = 45_000L,
            mediaSourceId = "ms-1",
            audioStreamIndex = 2,
            subtitleStreamIndex = 5,
        )

        verify(exactly = 1) {
            strategy.loadMedia(
                itemId = "item-1",
                startPositionMs = 45_000L,
                mediaSourceId = "ms-1",
                audioStreamIndex = 2,
                subtitleStreamIndex = 5,
            )
        }
    }

    @Test
    fun `fling defaults forward as explicit zero and null arguments`() {
        controller().fling(itemId = "item-2")

        verify(exactly = 1) {
            strategy.loadMedia(
                itemId = "item-2",
                startPositionMs = 0L,
                mediaSourceId = null,
                audioStreamIndex = null,
                subtitleStreamIndex = null,
            )
        }
    }

    @Test
    fun `transport controls delegate to the strategy in call order`() {
        controller().play()
        controller().pause()
        controller().seekTo(12_345L)
        controller().setVolume(0.4f)

        verifyOrder {
            strategy.play()
            strategy.pause()
            strategy.seekTo(12_345L)
            strategy.setRendererVolume(0.4f)
        }
    }

    @Test
    fun `connect and disconnect delegate to the strategy with the context`() {
        val device = CastDevice(id = "sess-1", name = "Living Room", type = "jellyfin")

        controller().connect(context, device)
        controller().disconnect(context)

        verifyOrder {
            strategy.connect(context, device)
            strategy.disconnect(context)
        }
    }

    @Test
    fun `isConnected is the strategy flow - same instance re-exposed`() {
        val connected = MutableStateFlow(false)
        every { strategy.isConnected } returns connected

        // The facade exposes the strategy's flow instance itself (assigned at
        // construction), so UI collectors observe the strategy's own state.
        val controller = controller()
        assertSame(connected, controller.isConnected)

        connected.value = true
        assertTrue(controller.isConnected.value)
    }

    @Test
    fun `targetName reads through to the strategy flow`() {
        val targetName = MutableStateFlow<String?>(null)
        every { strategy.targetName } returns targetName
        val controller = controller()

        // targetName is a computed property: always the strategy's live value.
        assertFalse(controller.targetName.value != null)
        targetName.value = "Living Room"
        assertEquals("Living Room", controller.targetName.value)
    }
}
