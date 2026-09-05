package com.raulshma.jellyplay.core.data.playback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the [InstantMixStateHolder] choreography the album / artist /
 * media-detail VMs used to hand-copy: the isStarting flag lifecycle, the
 * first-track one-shot, and the outcome → [InstantMixError] mapping.
 */
class InstantMixStateHolderTest {

    @Test
    fun start_withFirstTrack_setsOneShot_andLowersFlag() = runTest {
        val holder = InstantMixStateHolder(this) { seedItemId, fallbackName ->
            assertEquals("album1", seedItemId)
            assertEquals("Album", fallbackName)
            InstantMixOutcome.Started("t1")
        }

        holder.start("album1", "Album")
        advanceUntilIdle()

        assertFalse(holder.state.value.isStarting)
        assertEquals("t1", holder.state.value.firstTrackId)
        assertNull(holder.state.value.error)
    }

    @Test
    fun start_withNullQueueHead_setsNullOneShot_withoutError() = runTest {
        val holder = InstantMixStateHolder(this) { _, _ ->
            InstantMixOutcome.Started(firstTrackId = null)
        }

        holder.start("album1", null)
        advanceUntilIdle()

        assertFalse(holder.state.value.isStarting)
        assertNull(holder.state.value.firstTrackId)
        assertNull(holder.state.value.error)
    }

    @Test
    fun start_emptyMix_mapsToEmptyMixError() = runTest {
        val holder = InstantMixStateHolder(this) { _, _ -> InstantMixOutcome.EmptyMix }

        holder.start("ar1", null)
        advanceUntilIdle()

        assertFalse(holder.state.value.isStarting)
        assertNull(holder.state.value.firstTrackId)
        assertEquals(InstantMixError.EmptyMix, holder.state.value.error)
    }

    @Test
    fun start_failure_mapsCauseMessage() = runTest {
        val holder = InstantMixStateHolder(this) { _, _ ->
            InstantMixOutcome.Failed(RuntimeException("boom"))
        }

        holder.start("ar1", null)
        advanceUntilIdle()

        assertEquals(InstantMixError.Failed("boom"), holder.state.value.error)
    }

    @Test
    fun start_failure_withoutCauseMessage_carriesNull() = runTest {
        val holder = InstantMixStateHolder(this) { _, _ ->
            InstantMixOutcome.Failed(RuntimeException(null as String?))
        }

        holder.start("ar1", null)
        advanceUntilIdle()

        assertEquals(InstantMixError.Failed(null), holder.state.value.error)
    }

    @Test
    fun start_suppressed_staysSilent() = runTest {
        val holder = InstantMixStateHolder(this) { _, _ -> InstantMixOutcome.Suppressed }

        holder.start("ar1", null)
        advanceUntilIdle()

        assertFalse(holder.state.value.isStarting)
        assertNull(holder.state.value.firstTrackId)
        assertNull(holder.state.value.error)
    }

    @Test
    fun isStarting_isTrueWhileTheSeamIsInFlight() = runTest {
        val gate = CompletableDeferred<Unit>()
        val holder = InstantMixStateHolder(this) { _, _ ->
            gate.await()
            InstantMixOutcome.Started("t1")
        }

        holder.start("album1", null)
        advanceUntilIdle()
        assertTrue(holder.state.value.isStarting)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(holder.state.value.isStarting)
        assertEquals("t1", holder.state.value.firstTrackId)
    }

    @Test
    fun start_clearsAPriorError() = runTest {
        var outcome: InstantMixOutcome = InstantMixOutcome.Failed(RuntimeException("boom"))
        val holder = InstantMixStateHolder(this) { _, _ -> outcome }

        holder.start("ar1", null)
        advanceUntilIdle()
        assertTrue(holder.state.value.error != null)

        outcome = InstantMixOutcome.Started("t1")
        holder.start("ar1", null)
        advanceUntilIdle()

        assertNull(holder.state.value.error)
        assertEquals("t1", holder.state.value.firstTrackId)
    }

    @Test
    fun consumeStartedEvent_clearsTheOneShot() = runTest {
        val holder = InstantMixStateHolder(this) { _, _ -> InstantMixOutcome.Started("t1") }
        holder.start("album1", null)
        advanceUntilIdle()
        assertEquals("t1", holder.state.value.firstTrackId)

        holder.consumeStartedEvent()
        assertNull(holder.state.value.firstTrackId)
    }

    @Test
    fun clearError_rearmsIdenticalRepeatFailures() = runTest {
        val holder = InstantMixStateHolder(this) { _, _ -> InstantMixOutcome.EmptyMix }

        holder.start("ar1", null)
        advanceUntilIdle()
        assertEquals(InstantMixError.EmptyMix, holder.state.value.error)

        holder.clearError()
        assertNull(holder.state.value.error)
    }
}
