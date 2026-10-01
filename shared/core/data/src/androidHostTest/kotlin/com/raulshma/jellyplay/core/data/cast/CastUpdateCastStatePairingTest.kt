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
import io.mockk.every
import io.mockk.mockk
import io.mockk.coVerify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The branch-PAIRING pin over [CastManager.updateCastState] — the manager
 * half of the contract whose pure half [CastStateFanoutTest] pins. Full
 * cast-seam consolidation is descoped until a desktop cast story exists
 * (one platform = hypothetical seam), so this pin exists instead to keep the
 * two halves from drifting apart: updateCastState must gather a payload for
 * EXACTLY the strategy branches castStateFanout handles — DLNA renderer
 * state, Jellyfin now-playing state, and everything else riding the
 * manager-owned CastPlayer snapshot. Adding a fourth strategy therefore
 * edits BOTH files loudly: the fan-out gains a branch here-first (the set
 * pins below fail), and the manager must gain a matching gather arm (the
 * effect pins below fail when the new payload is never refreshed).
 *
 * Pinned here:
 *  - the vocabulary: [CastStrategyNames] is exactly {GOOGLE, DLNA, JELLYFIN}
 *    and CastManager's public STRATEGY_* aliases mirror it 1:1;
 *  - the effect pairing, driven through the public surface (loadMedia →
 *    updateCastState on the manager scope): the active name decides which
 *    `refreshPlaybackState` arm fires — DLNA's iff DLNA is active, Jellyfin's
 *    iff Jellyfin is active, neither for the local-transport else-arm
 *    (Google / unknown ad-hoc names; the manager-owned CastPlayer is null in
 *    this harness, so the player payload — and the fan-out with it — is
 *    correctly all-null and every now-playing flow stays untouched);
 *  - the per-arm field fan at the manager-flow level matches the pure half:
 *    DLNA contributes position/duration/isPlaying/volume; Jellyfin those
 *    four plus title/subtitle; buffered position belongs to no renderer arm.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CastUpdateCastStatePairingTest {

    private lateinit var context: Context

    private val googleConnected = MutableStateFlow(false)
    private val dlnaConnected = MutableStateFlow(false)
    private val jellyfinConnected = MutableStateFlow(false)

    private val googleCastStrategy: GoogleCastStrategy = mockk(relaxUnitFun = true)
    private val dlnaCastStrategy: DlnaCastStrategy = mockk(relaxUnitFun = true)
    private val jellyfinCastStrategy: JellyfinRemotePlayCastStrategy = mockk(relaxUnitFun = true)
    private val syncPlayCastStore: SyncPlayCastStore = mockk()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        every { googleCastStrategy.isAvailable } returns MutableStateFlow(true)
        every { googleCastStrategy.isConnected } returns googleConnected
        every { googleCastStrategy.isConnecting } returns MutableStateFlow(false)
        every { googleCastStrategy.discoveredDevices } returns MutableStateFlow(emptyList())
        every { dlnaCastStrategy.isAvailable } returns MutableStateFlow(false)
        every { dlnaCastStrategy.isConnected } returns dlnaConnected
        every { dlnaCastStrategy.isConnecting } returns MutableStateFlow(false)
        every { dlnaCastStrategy.discoveredDevices } returns MutableStateFlow(emptyList())
        every { jellyfinCastStrategy.isAvailable } returns MutableStateFlow(false)
        every { jellyfinCastStrategy.isConnected } returns jellyfinConnected
        every { jellyfinCastStrategy.isConnecting } returns MutableStateFlow(false)
        every { jellyfinCastStrategy.discoveredDevices } returns MutableStateFlow(emptyList())
        every { syncPlayCastStore.syncPlayCast } returns MutableStateFlow(SyncPlayCastSlice())
    }

    @After
    fun tearDown() {
        dlnaConnected.value = false
        jellyfinConnected.value = false
    }

    private fun manager() = CastManager(
        context = context,
        googleCastStrategy = googleCastStrategy,
        dlnaCastStrategy = dlnaCastStrategy,
        jellyfinRemotePlayCastStrategy = jellyfinCastStrategy,
        syncPlayCastStore = syncPlayCastStore,
    )

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    private fun loadItem(manager: CastManager) {
        manager.loadMedia(
            MediaItem.Builder().setMediaId("id").setUri("http://server/v/stream").build(),
            0L,
            mockk<Player.Listener>(),
        )
        idleMain()
    }

    // ── the vocabulary pins: a fourth strategy must edit this test ───────

    @Test
    fun `CastStrategyNames is exactly the three strategies both halves branch on`() {
        // Exhaustive on purpose: castStateFanout branches DLNA / JELLYFIN /
        // else, and CastManager.updateCastState gathers DLNA / JELLYFIN /
        // player. A fourth strategy must extend castStateFanout AND the
        // manager's gather arms AND this pin — never one of the three alone.
        assertEquals(
            setOf("google", "dlna", "jellyfin"),
            setOf(
                CastStrategyNames.GOOGLE,
                CastStrategyNames.DLNA,
                CastStrategyNames.JELLYFIN,
            ),
        )
    }

    @Test
    fun `the manager's STRATEGY aliases mirror the canonical names 1-1`() {
        assertEquals(CastStrategyNames.GOOGLE, CastManager.STRATEGY_GOOGLE)
        assertEquals(CastStrategyNames.DLNA, CastManager.STRATEGY_DLNA)
        assertEquals(CastStrategyNames.JELLYFIN, CastManager.STRATEGY_JELLYFIN)
    }

    // ── the effect pairing: gather arms per active strategy ──────────────

    @Test
    fun `DLNA active refreshes the DLNA renderer arm only and fans its four fields`() {
        every { dlnaCastStrategy.rendererPositionMs } returns MutableStateFlow(12_000L)
        every { dlnaCastStrategy.rendererDurationMs } returns MutableStateFlow(300_000L)
        every { dlnaCastStrategy.rendererIsPlaying } returns MutableStateFlow(true)
        every { dlnaCastStrategy.rendererVolume } returns MutableStateFlow(0.25f)
        every { dlnaCastStrategy.loadMedia(any(), any(), any(), any()) } returns true
        every { dlnaCastStrategy.ownsExternalListener } returns false
        val manager = manager()
        manager.setActiveStrategy(CastManager.STRATEGY_DLNA)

        loadItem(manager)

        // Exactly the paired arm refreshed.
        coVerify(exactly = 1) { dlnaCastStrategy.refreshPlaybackState() }
        coVerify(exactly = 0) { jellyfinCastStrategy.refreshPlaybackState() }

        // The manager-flow fan matches the pure half's DLNA branch.
        assertEquals(12_000L, manager.castPositionMs.value)
        assertEquals(300_000L, manager.castDurationMs.value)
        assertTrue(manager.castIsPlaying.value)
        assertEquals(0.25f, manager.castVolume.value)
        // DECLARED divergences: neither renderer arm owns buffered position
        // or title/subtitle.
        assertEquals(0L, manager.castBufferedPositionMs.value)
        assertEquals("", manager.castTitle.value)
        assertEquals("", manager.castSubtitle.value)
    }

    @Test
    fun `Jellyfin active refreshes the Jellyfin arm only and fans its six fields`() {
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

        loadItem(manager)

        // Exactly the paired arm refreshed.
        coVerify(exactly = 1) { jellyfinCastStrategy.refreshPlaybackState() }
        coVerify(exactly = 0) { dlnaCastStrategy.refreshPlaybackState() }

        // The manager-flow fan matches the pure half's Jellyfin branch.
        assertEquals(1_000L, manager.castPositionMs.value)
        assertEquals(2_000L, manager.castDurationMs.value)
        assertTrue(manager.castIsPlaying.value)
        assertEquals(0.5f, manager.castVolume.value)
        assertEquals("Pilot", manager.castTitle.value)
        assertEquals("Show · S1E1", manager.castSubtitle.value)
        assertEquals(0L, manager.castBufferedPositionMs.value)
    }

    @Test
    fun `the local-transport else-arm never fires a renderer arm - Google without a player`() {
        val manager = manager() // default strategy: Google; no CastPlayer in harness

        loadItem(manager)

        // The else-arm's payload is the manager-owned CastPlayer — absent
        // here, so the fan-out is all-null: no renderer arm fired, nothing
        // was gathered, every now-playing flow stayed untouched. The load
        // itself is the known silent no-op.
        coVerify(exactly = 0) { dlnaCastStrategy.refreshPlaybackState() }
        coVerify(exactly = 0) { jellyfinCastStrategy.refreshPlaybackState() }
        assertEquals(0L, manager.castPositionMs.value)
        assertEquals("", manager.castTitle.value)
        assertEquals("", manager.castSubtitle.value)
    }

    @Test
    fun `an unknown ad-hoc strategy name rides the same else-arm`() {
        val manager = manager()
        manager.setActiveStrategy("custom-strategy")

        loadItem(manager)

        // Unknown names route to the local transport (the fan-out's else
        // branch): no renderer arm, no writes — same as Google.
        coVerify(exactly = 0) { dlnaCastStrategy.refreshPlaybackState() }
        coVerify(exactly = 0) { jellyfinCastStrategy.refreshPlaybackState() }
        assertEquals(0L, manager.castPositionMs.value)
        assertEquals("", manager.castTitle.value)
    }
}
