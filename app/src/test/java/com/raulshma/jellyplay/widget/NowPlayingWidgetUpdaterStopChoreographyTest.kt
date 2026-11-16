package com.raulshma.jellyplay.widget

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [joinCollectorsAndResetGate] — the NowPlayingWidgetUpdater stop
 * choreography's DECLARED FIX: the gate reset happens only AFTER both
 * collectors have fully unwound. Cancellation is asynchronous, so a plain
 * cancel() returns while a collector cancelled mid-artwork-load still has a
 * non-suspending tail to run (readPushSnapshot →
 * [WidgetPushGate.decideOnMetadata]); without the join that tail landed on
 * the gate after reset() and its stale render snapshot survived into the
 * next presence-on cycle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingWidgetUpdaterStopChoreographyTest {

    /** The stale render snapshot the cancelled collector's tail re-lands. */
    private val stalePush = WidgetPushSnapshot(
        title = "Title",
        subtitle = "Artist",
        isPlaying = true,
        positionMs = 1_000,
        durationMs = 200_000,
        artUrl = null,
        isEmptyState = false,
    )

    @Test
    fun `a cancelled collector's in-flight tail cannot land on the gate after the reset`() = runTest {
        val gate = WidgetPushGate()
        var tailLanded = false
        // The collector cancelled mid-loadArtwork: the NonCancellable block
        // stands in for the artwork tail that survives the cancel() and lands
        // the metadata decision on the gate.
        val metadataCollector = launch {
            withContext(NonCancellable) {
                delay(1_000)
                gate.decideOnMetadata(
                    triggerItemId = "item-1",
                    loadedArt = null,
                    pushed = stalePush,
                )
                tailLanded = true
            }
        }
        runCurrent() // the collector is parked mid-artwork-load

        launch { joinCollectorsAndResetGate(metadataCollector, null, gate) }
        runCurrent() // the join is now waiting on the parked tail

        advanceUntilIdle() // the tail lands, the join completes, reset() runs

        // The tail really did run past its own cancellation...
        assertTrue(tailLanded)
        // ...but it landed BEFORE the reset, so the reset wiped its write:
        // the next presence-on cycle starts clean. Without the join the
        // reset completed first, the tail's write survived, and this same
        // position tick would read the stale render as Partial.
        assertEquals(
            WidgetPushGate.Decision.Skip,
            gate.decideOnPositionTick(stalePush.copy(positionMs = 2_000)),
        )
    }
}
