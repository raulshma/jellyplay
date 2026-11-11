package com.raulshma.jellyplay.core.data.cast

import android.content.Context
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.data.cast.dlna.DlnaCastStrategy
import com.raulshma.jellyplay.core.data.cast.remote.JellyfinRemotePlayCastStrategy
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastSlice
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.model.CastingStrategy
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Pins [CastManager]'s strategy-routing and lifecycle invariants:
 *
 * - The active strategy is seeded from the `defaultCastingStrategy` preference:
 *   PREFER_DLNA → `dlna`, PREFER_CAST/ASK → `google`.
 * - Transport calls (play/pause/seekTo/setRendererVolume) route to the active
 *   strategy; DLNA/Jellyfin delegate to their strategy, Google needs a live
 *   [androidx.media3.cast.CastPlayer] (absent in unit tests → silent no-op).
 * - `connect` switches the active strategy when the device names a different
 *   one, stopping discovery on the previous strategy first.
 * - A DLNA/Jellyfin strategy's `isConnected` edge emits exactly one
 *   [CastSessionEvent.Connected] / [CastSessionEvent.Disconnected] and a
 *   disconnect resets the transport state flows.
 * - `discoveredDevices` merges every registered strategy's list.
 * - A successful DLNA/Jellyfin load fans the strategy's playback state into
 *   the manager's now-playing flows (write-through of the pure
 *   [castStateFanout]; the full per-field contract, including the
 *   Jellyfin-only title/subtitle divergence, is pinned in CastStateFanoutTest).
 * - A session-disconnect edge resets the ENTIRE now-playing family — the
 *   transport quartet AND title/subtitle/volume (the former reset let the
 *   dead session's title and volume bleed into the next one).
 * - `isActive()` derives from the ACTIVE strategy's connection phase, not the
 *   Google `castPlayer != null` shape: a live DLNA session is active with no
 *   CastPlayer, and no stale CastPlayer can fake activity.
 * - `unregisterStrategy` of the active strategy re-seats routing on a
 *   REMAINING strategy (google when it survives, else the first remaining
 *   renderer) — never on the name just removed; a NON-active unregister
 *   leaves the active strategy alone.
 * - Consumer refcounting: `releaseConsumer` to zero tears the shared state
 *   down (strategy `release()` + `stopDiscovery()`), `softRelease` only stops
 *   discovery.
 * - `withCastQueryParams` / `withCastOptions` append the cast options as
 *   Jellyfin query params, preserving existing params and only adding
 *   non-null options.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CastManagerTest {

    private lateinit var context: Context

    private val googleConnected = MutableStateFlow(false)
    private val dlnaConnected = MutableStateFlow(false)
    private val jellyfinConnected = MutableStateFlow(false)

    private val googleAvailable = MutableStateFlow(true)
    private val dlnaAvailable = MutableStateFlow(false)
    private val jellyfinAvailable = MutableStateFlow(false)

    private val googleDevices = MutableStateFlow<List<CastDevice>>(emptyList())
    private val dlnaDevices = MutableStateFlow<List<CastDevice>>(emptyList())
    private val jellyfinDevices = MutableStateFlow<List<CastDevice>>(emptyList())

    // The google-path strategy rides the CastStrategy interface now (the GMS
    // classes live behind the integration seam and never reach this test).
    private val googleStrategy: CastStrategy = mockk(relaxUnitFun = true)
    private val dlnaCastStrategy: DlnaCastStrategy = mockk(relaxUnitFun = true)
    private val jellyfinCastStrategy: JellyfinRemotePlayCastStrategy = mockk(relaxUnitFun = true)
    private val syncPlayCastStore: SyncPlayCastStore = mockk()

    private val castPreference = MutableStateFlow(SyncPlayCastSlice())

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        every { googleStrategy.isAvailable } returns googleAvailable
        every { googleStrategy.isConnected } returns googleConnected
        every { googleStrategy.isConnecting } returns MutableStateFlow(false)
        every { googleStrategy.discoveredDevices } returns googleDevices
        every { dlnaCastStrategy.isAvailable } returns dlnaAvailable
        every { dlnaCastStrategy.isConnected } returns dlnaConnected
        every { dlnaCastStrategy.isConnecting } returns MutableStateFlow(false)
        every { dlnaCastStrategy.discoveredDevices } returns dlnaDevices
        every { jellyfinCastStrategy.isAvailable } returns jellyfinAvailable
        every { jellyfinCastStrategy.isConnected } returns jellyfinConnected
        every { jellyfinCastStrategy.isConnecting } returns MutableStateFlow(false)
        every { jellyfinCastStrategy.discoveredDevices } returns jellyfinDevices
        every { syncPlayCastStore.syncPlayCast } returns castPreference
    }

    @After
    fun tearDown() {
        dlnaConnected.value = false
        jellyfinConnected.value = false
    }

    private fun manager() = CastManager(
        context = context,
        // Fake integration wrapping the mocked strategy: the manager registers
        // it under STRATEGY_GOOGLE and every discovery/connect/release verify
        // below lands on the strategy exactly as the GMS production path did.
        // ensureCastPlayer returns null (no CastPlayer under Robolectric).
        googleCastIntegration = fakeGoogleIntegration(googleStrategy),
        dlnaCastStrategy = dlnaCastStrategy,
        jellyfinRemotePlayCastStrategy = jellyfinCastStrategy,
        syncPlayCastStore = syncPlayCastStore,
        applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    /** Stand-in for the store-only GMS integration: null strategy degrades; non-null registers. */
    private fun fakeGoogleIntegration(strategy: CastStrategy?): GoogleCastIntegration =
        object : GoogleCastIntegration {
            override val strategy: CastStrategy? = strategy
            override fun preinit(context: Context): Boolean = false
            override fun ensureSessionListener(context: Context, events: GoogleCastIntegration.SessionEvents) = Unit
            override fun ensureCastPlayer(
                context: Context,
                playerListener: Player.Listener,
                onSessionUnavailable: () -> Unit,
            ): Player? = null
            override fun release() = Unit
        }

    private fun dlnaDevice() = CastDevice(
        id = "uuid-1",
        name = "Living Room",
        type = "dlna",
        tag = null,
        strategyName = CastManager.STRATEGY_DLNA,
    )

    @Test
    fun `PREFER_DLNA preference seeds the DLNA strategy as active`() {
        castPreference.value = SyncPlayCastSlice(defaultCastingStrategy = CastingStrategy.PREFER_DLNA)

        assertEquals(CastManager.STRATEGY_DLNA, manager().currentStrategyName)
    }

    @Test
    fun `PREFER_CAST and ASK preferences both seed the Google strategy`() {
        castPreference.value = SyncPlayCastSlice(defaultCastingStrategy = CastingStrategy.PREFER_CAST)
        assertEquals(CastManager.STRATEGY_GOOGLE, manager().currentStrategyName)

        castPreference.value = SyncPlayCastSlice(defaultCastingStrategy = CastingStrategy.ASK)
        assertEquals(CastManager.STRATEGY_GOOGLE, manager().currentStrategyName)
    }

    @Test
    fun `setVolume routes to the DLNA strategy when DLNA is active`() {
        castPreference.value = SyncPlayCastSlice(defaultCastingStrategy = CastingStrategy.PREFER_DLNA)
        val manager = manager()

        manager.setVolume(0.4f)

        verify(exactly = 1) { dlnaCastStrategy.setRendererVolume(0.4f) }
    }

    @Test
    fun `setVolume routes to the Jellyfin strategy when Jellyfin is active`() {
        val manager = manager()
        manager.setActiveStrategy(CastManager.STRATEGY_JELLYFIN)

        manager.setVolume(0.6f)

        verify(exactly = 1) { jellyfinCastStrategy.setRendererVolume(0.6f) }
    }

    @Test
    fun `setVolume on the Google strategy is a no-op without a cast player`() {
        val manager = manager()

        manager.setVolume(0.5f)

        // No CastPlayer exists under Robolectric — must not throw, volume untouched.
        assertEquals(1f, manager.castVolume.value)
    }

    @Test
    fun `transport calls route to the active DLNA strategy`() {
        castPreference.value = SyncPlayCastSlice(defaultCastingStrategy = CastingStrategy.PREFER_DLNA)
        val manager = manager()

        manager.play()
        manager.pause()
        manager.seekTo(1_500L)

        verifyOrder {
            dlnaCastStrategy.play()
            dlnaCastStrategy.pause()
            dlnaCastStrategy.seekTo(1_500L)
        }
    }

    @Test
    fun `loadMedia routes to the active DLNA strategy`() {
        castPreference.value = SyncPlayCastSlice(defaultCastingStrategy = CastingStrategy.PREFER_DLNA)
        val manager = manager()
        every { dlnaCastStrategy.loadMedia(any(), any(), any(), any()) } returns true
        // relaxUnitFun covers only Unit members; the handoff gate reads this
        // val after any successful transport load — production DLNA uses the
        // CastStrategy default (false, the transport never owns the listener).
        every { dlnaCastStrategy.ownsExternalListener } returns false
        val item = MediaItem.Builder().setMediaId("id").setUri("http://server/Videos/1/stream").build()

        manager.loadMedia(item, 0L, mockk())

        verify(exactly = 1) { dlnaCastStrategy.loadMedia(item, 0L, any(), any()) }
    }

    @Test
    fun `loadMedia on the Google strategy without a cast player is a silent no-op`() {
        val manager = manager()
        val item = MediaItem.Builder().setMediaId("id").setUri("http://server/stream").build()

        // No CastPlayer exists under Robolectric — must not throw.
        manager.loadMedia(item, 0L, mockk())
    }

    @Test
    fun `connect switches to the device's strategy and stops the previous discovery`() {
        val manager = manager()

        manager.connect(context, dlnaDevice())

        assertEquals(CastManager.STRATEGY_DLNA, manager.currentStrategyName)
        verify(exactly = 1) { googleStrategy.stopDiscovery() }
        verify(exactly = 1) { dlnaCastStrategy.connect(context, dlnaDevice()) }
    }

    @Test
    fun `connect with a blank strategy name routes to the currently active strategy`() {
        val manager = manager()
        val googleDevice = CastDevice(id = "g1", name = "Chromecast", type = "chromecast", strategyName = "")

        manager.connect(context, googleDevice)

        assertEquals(CastManager.STRATEGY_GOOGLE, manager.currentStrategyName)
        verify(exactly = 1) { googleStrategy.connect(context, googleDevice) }
    }

    @Test
    fun `strategy connection edges emit session events and a disconnect resets transport state`() {
        castPreference.value = SyncPlayCastSlice(defaultCastingStrategy = CastingStrategy.PREFER_DLNA)
        val manager = manager()
        val events = mutableListOf<CastSessionEvent>()
        val collector = CoroutineScope(Dispatchers.Unconfined)
        val job = collector.launch { manager.sessionEvents.collect { events.add(it) } }

        dlnaConnected.value = true
        idleMain()

        assertTrue(events.contains(CastSessionEvent.Connected))

        dlnaConnected.value = false
        idleMain()

        assertTrue(events.contains(CastSessionEvent.Disconnected))
        assertEquals(0L, manager.castPositionMs.value)
        assertEquals(0L, manager.castDurationMs.value)
        assertFalse(manager.castIsPlaying.value)
        job.cancel()
        collector.cancel()
    }

    @Test
    fun `a disconnect edge resets the whole now-playing family including title subtitle and volume`() {
        // Set the full family through the Jellyfin fan-out (the only branch
        // that contributes title/subtitle), then kill the session: the reset
        // must return EVERY flow to its construction default — the former
        // reset covered only the transport quartet, so the dead session's
        // title and volume bled into the next one.
        every { jellyfinCastStrategy.positionMs } returns MutableStateFlow(1_000L)
        every { jellyfinCastStrategy.durationMs } returns MutableStateFlow(2_000L)
        every { jellyfinCastStrategy.isPlaying } returns MutableStateFlow(true)
        every { jellyfinCastStrategy.volume } returns MutableStateFlow(0.5f)
        every { jellyfinCastStrategy.nowPlayingTitle } returns MutableStateFlow("Pilot")
        every { jellyfinCastStrategy.nowPlayingSubtitle } returns MutableStateFlow("Show · S1E1")
        every {
            jellyfinCastStrategy.loadMedia(
                any<MediaItem>(), any(), any<Player.Listener>(), any(),
            )
        } returns true
        every { jellyfinCastStrategy.ownsExternalListener } returns false
        val manager = manager()
        manager.setActiveStrategy(CastManager.STRATEGY_JELLYFIN)
        manager.loadMedia(MediaItem.Builder().setMediaId("id").setUri("http://server/v/stream").build(), 0L, mockk())
        idleMain()
        assertEquals("Pilot", manager.castTitle.value)
        assertEquals(0.5f, manager.castVolume.value)

        // The reset fires on the session edge, so the observer must see the
        // connect first (same pattern as the DLNA reset pin above).
        jellyfinConnected.value = true
        idleMain()

        jellyfinConnected.value = false
        idleMain()

        assertEquals("", manager.castTitle.value)
        assertEquals("", manager.castSubtitle.value)
        assertEquals(1f, manager.castVolume.value)
        assertEquals(0L, manager.castPositionMs.value)
        assertEquals(0L, manager.castDurationMs.value)
        assertEquals(0L, manager.castBufferedPositionMs.value)
        assertFalse(manager.castIsPlaying.value)
    }

    // ── state fan-out write-through ───────────────────────────────────────

    @Test
    fun `a DLNA refresh fans renderer state into the manager's now-playing flows`() {
        castPreference.value = SyncPlayCastSlice(defaultCastingStrategy = CastingStrategy.PREFER_DLNA)
        every { dlnaCastStrategy.rendererPositionMs } returns MutableStateFlow(12_000L)
        every { dlnaCastStrategy.rendererDurationMs } returns MutableStateFlow(300_000L)
        every { dlnaCastStrategy.rendererIsPlaying } returns MutableStateFlow(true)
        every { dlnaCastStrategy.rendererVolume } returns MutableStateFlow(0.25f)
        every { dlnaCastStrategy.loadMedia(any(), any(), any(), any()) } returns true
        every { dlnaCastStrategy.ownsExternalListener } returns false
        val manager = manager()

        manager.loadMedia(MediaItem.Builder().setMediaId("id").setUri("http://server/v/stream").build(), 0L, mockk())
        idleMain()

        assertEquals(12_000L, manager.castPositionMs.value)
        assertEquals(300_000L, manager.castDurationMs.value)
        assertTrue(manager.castIsPlaying.value)
        assertEquals(0.25f, manager.castVolume.value)
        // DECLARED divergence: DLNA owns neither buffered position nor
        // title/subtitle — those flows keep their previous values.
        assertEquals(0L, manager.castBufferedPositionMs.value)
        assertEquals("", manager.castTitle.value)
        assertEquals("", manager.castSubtitle.value)
    }

    @Test
    fun `a Jellyfin refresh fans now-playing title and subtitle into the manager's flows`() {
        every { jellyfinCastStrategy.positionMs } returns MutableStateFlow(1_000L)
        every { jellyfinCastStrategy.durationMs } returns MutableStateFlow(2_000L)
        every { jellyfinCastStrategy.isPlaying } returns MutableStateFlow(true)
        every { jellyfinCastStrategy.volume } returns MutableStateFlow(0.5f)
        every { jellyfinCastStrategy.nowPlayingTitle } returns MutableStateFlow("Pilot")
        every { jellyfinCastStrategy.nowPlayingSubtitle } returns MutableStateFlow("Show · S1E1")
        // Explicit types: JellyfinRemotePlayCastStrategy overloads loadMedia
        // (String-based admin variant beside the interface override).
        every {
            jellyfinCastStrategy.loadMedia(
                any<MediaItem>(), any(), any<Player.Listener>(), any(),
            )
        } returns true
        every { jellyfinCastStrategy.ownsExternalListener } returns false
        val manager = manager()
        manager.setActiveStrategy(CastManager.STRATEGY_JELLYFIN)

        manager.loadMedia(MediaItem.Builder().setMediaId("id").setUri("http://server/v/stream").build(), 0L, mockk())
        idleMain()

        assertEquals(1_000L, manager.castPositionMs.value)
        assertEquals(2_000L, manager.castDurationMs.value)
        assertTrue(manager.castIsPlaying.value)
        assertEquals(0.5f, manager.castVolume.value)
        assertEquals("Pilot", manager.castTitle.value)
        assertEquals("Show · S1E1", manager.castSubtitle.value)
        // DECLARED divergence: the remote session reports no buffered position.
        assertEquals(0L, manager.castBufferedPositionMs.value)
    }

    // ── discovery merge ────────────────────────────────────────────────────

    @Test
    fun `discovered devices merge every strategy's list`() {
        val manager = manager()
        val fromGoogle = CastDevice(id = "g", name = "Cast", type = "chromecast")
        val fromDlna = dlnaDevice()

        googleDevices.value = listOf(fromGoogle)
        dlnaDevices.value = listOf(fromDlna)
        idleMain()

        val merged = manager.discoveredDevices.value
        assertEquals(2, merged.size)
        assertTrue(merged.contains(fromGoogle))
        assertTrue(merged.contains(fromDlna))
    }

    @Test
    fun `unregistering the active strategy falls back to Google`() {
        val manager = manager()
        manager.setActiveStrategy(CastManager.STRATEGY_DLNA)
        assertEquals(CastManager.STRATEGY_DLNA, manager.currentStrategyName)

        manager.unregisterStrategy(CastManager.STRATEGY_DLNA)

        assertEquals(CastManager.STRATEGY_GOOGLE, manager.currentStrategyName)
    }

    @Test
    fun `unregistering the ACTIVE Google strategy re-seats routing on a remaining strategy`() {
        castPreference.value = SyncPlayCastSlice(defaultCastingStrategy = CastingStrategy.PREFER_CAST)
        val manager = manager()
        assertEquals(CastManager.STRATEGY_GOOGLE, manager.currentStrategyName)

        manager.unregisterStrategy(CastManager.STRATEGY_GOOGLE)

        // The former body re-seated STRATEGY_GOOGLE — the very name just
        // removed — leaving activeStrategy null and every cast call a silent
        // no-op. Routing now lands on a strategy the map still holds (DLNA,
        // the renderer-capable fallback).
        assertEquals(CastManager.STRATEGY_DLNA, manager.currentStrategyName)
    }

    @Test
    fun `unregistering a NON-active strategy leaves the active strategy alone`() {
        castPreference.value = SyncPlayCastSlice(defaultCastingStrategy = CastingStrategy.PREFER_DLNA)
        val manager = manager()

        manager.unregisterStrategy(CastManager.STRATEGY_GOOGLE)

        assertEquals(CastManager.STRATEGY_DLNA, manager.currentStrategyName)
    }

    // ── isActive: strategy-phase derivation ────────────────────────────────

    @Test
    fun `isActive follows the active strategy's connection phase not the Google player shape`() {
        castPreference.value = SyncPlayCastSlice(defaultCastingStrategy = CastingStrategy.PREFER_DLNA)
        val manager = manager()

        // No CastPlayer exists under Robolectric (the fake integration's
        // ensureCastPlayer returns null) — the OLD `castPlayer != null &&
        // isConnected` shape reported FALSE for this live DLNA session.
        assertFalse(manager.isActive())

        dlnaConnected.value = true

        assertTrue(manager.isActive())

        // The dead-player arm: Google active with no CastPlayer never
        // reports active — with or without a strategy edge.
        dlnaConnected.value = false
        manager.setActiveStrategy(CastManager.STRATEGY_GOOGLE)

        assertFalse(manager.isActive())
    }

    @Test
    fun `start and stop discovery fan out to every strategy`() {
        val manager = manager()

        manager.startDiscovery(context)
        verify(exactly = 1) { googleStrategy.startDiscovery(context) }
        verify(exactly = 1) { dlnaCastStrategy.startDiscovery(context) }
        verify(exactly = 1) { jellyfinCastStrategy.startDiscovery(context) }

        manager.stopDiscovery()
        verify(exactly = 1) { googleStrategy.stopDiscovery() }
        verify(exactly = 1) { dlnaCastStrategy.stopDiscovery() }
        verify(exactly = 1) { jellyfinCastStrategy.stopDiscovery() }
    }

    @Test
    fun `releaseConsumer at zero tears strategies down and clears background-casting`() {
        val manager = manager()
        manager.acquireConsumer()
        manager.markBackgroundCasting(true)
        assertTrue(manager.isBackgroundCasting)

        manager.releaseConsumer()

        verify(exactly = 1) { googleStrategy.release() }
        verify(exactly = 1) { dlnaCastStrategy.release() }
        verify(exactly = 1) { jellyfinCastStrategy.release() }
        assertFalse(manager.isBackgroundCasting)
    }

    @Test
    fun `softRelease only stops discovery and leaves strategies alive`() {
        val manager = manager()

        manager.softRelease()

        verify(exactly = 1) { googleStrategy.stopDiscovery() }
        verify(exactly = 1) { dlnaCastStrategy.stopDiscovery() }
        verify(exactly = 1) { jellyfinCastStrategy.stopDiscovery() }
        verify(exactly = 0) { googleStrategy.release() }
        verify(exactly = 0) { dlnaCastStrategy.release() }
    }

    @Test
    fun `availability and connection flags proxy the active strategy`() {
        val manager = manager()
        manager.setActiveStrategy(CastManager.STRATEGY_JELLYFIN)

        jellyfinAvailable.value = true
        jellyfinConnected.value = true

        assertTrue(manager.isCastAvailable)
        assertTrue(manager.isConnected)
        assertSame(jellyfinAvailable, manager.isAvailableFlow)
        assertSame(jellyfinConnected, manager.isConnectedFlow)
    }

    // ── cast URL enrichment helpers ───────────────────────────────────────

    @Test
    fun `withCastQueryParams with all-null options returns the receiver unchanged`() {
        val url = "http://server/Videos/1/stream"

        assertEquals(url, url.withCastQueryParams(CastMediaOptions()))
    }

    @Test
    fun `withCastQueryParams appplies audio subtitle and bitrate params in order`() {
        val url = "http://server/Videos/1/stream"

        val enriched = url.withCastQueryParams(
            CastMediaOptions(audioStreamIndex = 2, subtitleStreamIndex = 3, maxVideoBitrate = 8_000_000),
        )

        assertEquals(
            "http://server/Videos/1/stream?AudioStreamIndex=2&SubtitleStreamIndex=3&MaxVideoBitrate=8000000",
            enriched,
        )
    }

    @Test
    fun `withCastQueryParams preserves existing query params and only adds non-null options`() {
        val url = "http://server/Videos/1/stream?api_key=k"

        val enriched = url.withCastQueryParams(CastMediaOptions(audioStreamIndex = 1))

        assertEquals("http://server/Videos/1/stream?api_key=k&AudioStreamIndex=1", enriched)
    }

    @Test
    fun `withCastOptions on an item without a URI returns the same instance`() {
        val item = MediaItem.Builder().setMediaId("id").build()

        assertSame(item, item.withCastOptions(CastMediaOptions(audioStreamIndex = 1)))
    }

    @Test
    fun `withCastOptions enriches the URI while preserving metadata`() {
        val item = MediaItem.Builder()
            .setMediaId("id")
            .setUri("http://server/stream")
            .build()

        val enriched = item.withCastOptions(CastMediaOptions(audioStreamIndex = 2))

        assertEquals("http://server/stream?AudioStreamIndex=2", enriched.localConfiguration?.uri?.toString())
        assertEquals("id", enriched.mediaId)
        assertNull(enriched.mediaMetadata.title)
    }
}
