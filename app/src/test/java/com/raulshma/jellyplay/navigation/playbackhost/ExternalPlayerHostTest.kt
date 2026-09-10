package com.raulshma.jellyplay.navigation.playbackhost

import android.content.Intent
import com.raulshma.jellyplay.ExternalPlayerLaunch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Choreography test for [ExternalPlayerHost] — the external-player launch
 * protocol that used to live composable-inline in JellyPlayApp. The pure
 * inputs/outputs were already pinned elsewhere (the position parse in
 * `ExternalPlayerResultPolicy`'s suite, the report pair in MainViewModelTest);
 * this pins the ORDERING between them, over fake constructor lambdas that
 * record call order (the `NavRequestCollectorTest` shape):
 *
 *  - resolve failure short-circuits everything (no report, no stash, no
 *    chooser);
 *  - report-start precedes the stash, the stash precedes the chooser start;
 *  - a throwing chooser clears the stash FIRST, then emits the error — so a
 *    stray later result can never credit a playback that never started;
 *  - the result arm consumes the stash exactly once, folds the position
 *    extras (position wins, positionMs aliases, no extras = -1) and reports
 *    stopped;
 *  - a result with no stash is a complete no-op.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ExternalPlayerHostTest {

    private fun launchOf(itemId: String): ExternalPlayerLaunch = ExternalPlayerLaunch(
        intent = Intent(Intent.ACTION_VIEW),
        itemId = itemId,
        startPositionTicks = 0L,
        playSessionId = "session-$itemId",
    )

    /**
     * Records every seam call in order. The failure arm's stash-clear is
     * pinned by the assertions around it (null after the launch call, and a
     * stray result that reports nothing); the stash-set-before-chooser pin
     * rides [stashedAtChooser], read from the chooser seam declared after
     * the host.
     */
    private class Harness(built: ExternalPlayerLaunch?) {
        val calls = mutableListOf<String>()
        val resolvedArgs = mutableListOf<Triple<String, String?, Long>>()

        /** The stash state observed when the chooser seam fired. */
        var stashedAtChooser: ExternalPlayerLaunch? = null

        val host: ExternalPlayerHost = ExternalPlayerHost(
            buildLaunch = { itemId, mediaSourceId, startPositionTicks ->
                calls += "build"
                resolvedArgs += Triple(itemId, mediaSourceId, startPositionTicks)
                built
            },
            reportStart = { calls += "report-start" },
            reportStopped = { launch, ticks ->
                calls += "report-stopped:${launch.itemId}:$ticks"
            },
            notifyNoPlayerFound = { calls += "no-player" },
        )

        /** Records the chooser start plus the stash state at that moment. */
        val recordingChooser: (Intent) -> Unit = {
            stashedAtChooser = host.pendingLaunch
            calls += "chooser"
        }

        /** The no-external-player-installed arm: the start itself throws. */
        val failingChooser: (Intent) -> Unit = { throw IllegalStateException("no activity") }

        /** The silent chooser for result-arm tests that only care about onResult. */
        val quietChooser: (Intent) -> Unit = { }
    }

    @Test
    fun `resolve failure short-circuits - no report, no stash, no chooser`() = runTest {
        val harness = Harness(built = null)

        harness.host.launch("item-1", null, 0L, startChooser = harness.recordingChooser)

        assertEquals(listOf("build"), harness.calls)
        assertNull(harness.host.pendingLaunch)
    }

    @Test
    fun `report-start precedes the stash which precedes the chooser`() = runTest {
        val built = launchOf("item-1")
        val harness = Harness(built = built)

        harness.host.launch("item-1", null, 0L, startChooser = harness.recordingChooser)

        assertEquals(listOf("build", "report-start", "chooser"), harness.calls)
        assertEquals(built, harness.stashedAtChooser)
        assertEquals(built, harness.host.pendingLaunch)
    }

    @Test
    fun `chooser failure clears the stash first, then emits the error`() = runTest {
        val harness = Harness(built = launchOf("item-1"))

        harness.host.launch("item-1", null, 0L, startChooser = harness.failingChooser)

        // The stash is cleared (observable: no pending launch, and a stray
        // result below reports nothing — it can never credit a playback that
        // never started).
        assertEquals(listOf("build", "report-start", "no-player"), harness.calls)
        assertNull(harness.host.pendingLaunch)

        harness.host.onResult(position = 60_000L, positionMs = null)
        assertEquals(listOf("build", "report-start", "no-player"), harness.calls)
    }

    @Test
    fun `result consumes the stash once and folds the position ticks`() = runTest {
        val harness = Harness(built = launchOf("item-1"))
        harness.host.launch("item-1", null, 0L, startChooser = harness.quietChooser)

        harness.host.onResult(position = 12_345L, positionMs = null)
        // The stash is consumed: a second result can never double-report.
        harness.host.onResult(position = 99_999L, positionMs = null)

        assertEquals(
            listOf("build", "report-start", "report-stopped:item-1:123450000"),
            harness.calls,
        )
        assertNull(harness.host.pendingLaunch)
    }

    @Test
    fun `positionMs aliases an absent position, and no extras parse as -1`() = runTest {
        val harness = Harness(built = launchOf("item-1"))
        harness.host.launch("item-1", null, 0L, startChooser = harness.quietChooser)
        harness.host.onResult(position = null, positionMs = 2_500L)

        // A second hand-off stashes a fresh launch, so its no-extras result
        // exercises the -1 arm through the same consume path.
        harness.host.launch("item-1", null, 0L, startChooser = harness.quietChooser)
        harness.host.onResult(position = null, positionMs = null)

        assertEquals(
            listOf(
                "build",
                "report-start",
                "report-stopped:item-1:25000000",
                "build",
                "report-start",
                "report-stopped:item-1:-1",
            ),
            harness.calls,
        )
    }

    @Test
    fun `a result with no stash is a no-op`() = runTest {
        val harness = Harness(built = launchOf("item-1"))

        harness.host.onResult(position = 1_000L, positionMs = null)

        assertEquals(emptyList<String>(), harness.calls)
        assertTrue(harness.resolvedArgs.isEmpty())
    }

    @Test
    fun `launch forwards the router's decision fields to the resolver`() = runTest {
        val harness = Harness(built = launchOf("item-9"))

        harness.host.launch(
            "item-9",
            mediaSourceId = "source-2",
            startPositionTicks = 600_000_000L,
            startChooser = harness.quietChooser,
        )

        assertEquals(listOf(Triple("item-9", "source-2", 600_000_000L)), harness.resolvedArgs)
    }
}
