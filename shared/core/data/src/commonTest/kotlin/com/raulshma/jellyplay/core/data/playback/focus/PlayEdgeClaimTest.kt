package com.raulshma.jellyplay.core.data.playback.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Pins [claimOnPlayEdge] — the ONE play-edge body every player's is-playing
 * observer folds to (the ADR-0004 claim chokepoints): a true edge acquires,
 * a Denied acquire invokes [claimOnPlayEdge]'s `onDenied` (the caller MUST
 * NOT produce audio) exactly once, and a false edge releases without
 * touching the seat.
 */
class PlayEdgeClaimTest {

    private class RecordingFocus(private val outcome: FocusOutcome) : PlaybackFocus {
        override val claimState: MutableStateFlow<FocusClaimState> =
            MutableStateFlow(FocusClaimState.Idle)
        val acquires = mutableListOf<PlaybackSurfaceId>()
        val releases = mutableListOf<PlaybackSurfaceId>()

        override fun acquire(claimant: PlaybackSurfaceId): FocusOutcome {
            acquires += claimant
            return outcome
        }

        override fun release(claimant: PlaybackSurfaceId) {
            releases += claimant
        }
    }

    @Test
    fun `true edge with a granted claim acquires and never invokes the denial callback`() {
        val focus = RecordingFocus(FocusOutcome.Granted)
        var denied = 0

        focus.claimOnPlayEdge(
            surfaceId = PlaybackSurfaceId.VIDEO,
            isPlaying = true,
            onDenied = { denied++ },
        )

        assertEquals(listOf(PlaybackSurfaceId.VIDEO), focus.acquires)
        assertTrue(focus.releases.isEmpty(), "a granted claim never releases")
        assertEquals(0, denied)
    }

    @Test
    fun `true edge with a denied claim invokes onDenied exactly once`() {
        val focus = RecordingFocus(FocusOutcome.Denied)
        var denied = 0

        focus.claimOnPlayEdge(
            surfaceId = PlaybackSurfaceId.MUSIC,
            isPlaying = true,
            onDenied = { denied++ },
        )

        assertEquals(listOf(PlaybackSurfaceId.MUSIC), focus.acquires)
        assertEquals(1, denied, "the host MUST NOT produce audio on a denial")
        assertTrue(focus.releases.isEmpty(), "the denial does not release the attempt")
    }

    @Test
    fun `false edge releases the seat without acquiring`() {
        val focus = RecordingFocus(FocusOutcome.Granted)
        var denied = 0

        focus.claimOnPlayEdge(
            surfaceId = PlaybackSurfaceId.READ_ALOUD,
            isPlaying = false,
            onDenied = { denied++ },
        )

        assertEquals(listOf(PlaybackSurfaceId.READ_ALOUD), focus.releases)
        assertTrue(focus.acquires.isEmpty(), "a false edge never claims")
        assertEquals(0, denied)
    }
}
