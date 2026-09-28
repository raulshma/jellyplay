package com.raulshma.jellyplay.navigation.playbackhost

import android.content.ComponentName
import android.content.Intent
import com.raulshma.jellyplay.core.model.ExternalPlayerApp
import com.raulshma.jellyplay.navigation.ExternalPlaybackOutcome
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
 * inputs/outputs were already pinned elsewhere (the per-contract outcome
 * parse in `ExternalPlayerPositionTicksTest`, the report pair in
 * MainViewModelTest); this pins the ORDERING between them, over fake
 * constructor lambdas that record call order (the `NavRequestCollectorTest`
 * shape):
 *
 *  - resolve failure short-circuits everything (no report, no stash, no
 *    chooser);
 *  - report-start precedes the stash, the stash precedes the chooser start;
 *  - a throwing chooser clears the stash FIRST, then emits the error — so a
 *    stray later result can never credit a playback that never started;
 *  - the result arm consumes the stash exactly once, folds the returned
 *    extras through the resolved player's contract and reports stopped;
 *  - a result with no stash is a complete no-op;
 *  - targeting: a preferred player that resolves installed starts its
 *    component intent directly and records the resolved app on the
 *    stash; an uninstalled one falls back to the chooser (resolvedApp
 *    stays null, so the result parse runs the alias arm).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ExternalPlayerHostTest {

    private fun launchOf(
        itemId: String,
        preferredApp: ExternalPlayerApp = ExternalPlayerApp.SYSTEM_CHOOSER,
        startPositionTicks: Long = 0L,
    ): ExternalPlayerLaunch = ExternalPlayerLaunch(
        intent = Intent(Intent.ACTION_VIEW),
        itemId = itemId,
        startPositionTicks = startPositionTicks,
        playSessionId = "session-$itemId",
        preferredApp = preferredApp,
    )

    /**
     * Records every seam call in order. The failure arm's stash-clear is
     * pinned by the assertions around it (null after the launch call, and a
     * stray result that reports nothing); the stash-set-before-start pin
     * rides [stashedAtStart], read from the start seam declared after the
     * host. [resolvedComponent] fakes the packageManager probe with the
     * production resolver's targeting policy: non-null for a targeted
     * ("installed") app, null for the chooser arm.
     */
    private class Harness(
        built: ExternalPlayerLaunch?,
        private val resolvedComponent: ComponentName? = null,
    ) {
        val calls = mutableListOf<String>()
        val resolvedArgs = mutableListOf<ExternalPlayerRequest>()

        /** The stash state observed when the start seam fired. */
        var stashedAtStart: ExternalPlayerLaunch? = null

        /** The intent the host asked to start. */
        var startedIntent: Intent? = null

        val host: ExternalPlayerHost = ExternalPlayerHost(
            buildLaunch = { request ->
                calls += "build"
                resolvedArgs += request
                built
            },
            resolveComponent = { app ->
                calls += "resolve:${app.name}"
                if (app.isTargeted) resolvedComponent else null
            },
            reportStart = { calls += "report-start" },
            reportStopped = { launch, outcome ->
                calls += "report-stopped:${launch.itemId}:$outcome"
            },
            notifyNoPlayerFound = { calls += "no-player" },
        )

        /** Records the start plus the stash state at that moment. */
        val recordingStart: (Intent) -> Unit = { intent ->
            stashedAtStart = host.pendingLaunch
            startedIntent = intent
            calls += "start"
        }

        /** The no-external-player-installed arm: the start itself throws. */
        val failingStart: (Intent) -> Unit = { throw IllegalStateException("no activity") }

        /** The silent start for result-arm tests that only care about onResult. */
        val quietStart: (Intent) -> Unit = { }
    }

    @Test
    fun `resolve failure short-circuits - no report, no stash, no chooser`() = runTest {
        val harness = Harness(built = null)

        harness.host.launch(ExternalPlayerRequest("item-1"), startChooser = harness.recordingStart)

        assertEquals(listOf("build"), harness.calls)
        assertNull(harness.host.pendingLaunch)
    }

    @Test
    fun `report-start precedes the stash which precedes the start`() = runTest {
        val built = launchOf("item-1")
        val harness = Harness(built = built)

        harness.host.launch(ExternalPlayerRequest("item-1"), startChooser = harness.recordingStart)

        assertEquals(listOf("build", "report-start", "resolve:SYSTEM_CHOOSER", "start"), harness.calls)
        assertEquals(built, harness.stashedAtStart)
        assertEquals(built, harness.host.pendingLaunch)
    }

    @Test
    fun `chooser failure clears the stash first, then emits the error`() = runTest {
        val harness = Harness(built = launchOf("item-1"))

        harness.host.launch(ExternalPlayerRequest("item-1"), startChooser = harness.failingStart)

        // The stash is cleared (observable: no pending launch, and a stray
        // result below reports nothing — it can never credit a playback that
        // never started).
        assertEquals(listOf("build", "report-start", "resolve:SYSTEM_CHOOSER", "no-player"), harness.calls)
        assertNull(harness.host.pendingLaunch)

        harness.host.onResult(mapOf("position" to 60_000L))
        assertEquals(listOf("build", "report-start", "resolve:SYSTEM_CHOOSER", "no-player"), harness.calls)
    }

    @Test
    fun `result consumes the stash once and folds the position ticks`() = runTest {
        val harness = Harness(built = launchOf("item-1"))
        harness.host.launch(ExternalPlayerRequest("item-1"), startChooser = harness.quietStart)

        harness.host.onResult(mapOf("position" to 12_345L))
        // The stash is consumed: a second result can never double-report.
        harness.host.onResult(mapOf("position" to 99_999L))

        assertEquals(
            listOf(
                "build",
                "report-start",
                "resolve:SYSTEM_CHOOSER",
                // Chooser arm (SYSTEM_CHOOSER): positive position → stopped at 12_345_000 ms in ticks.
                "report-stopped:item-1:StoppedAt(positionTicks=123450000)",
            ),
            harness.calls,
        )
        assertNull(harness.host.pendingLaunch)
    }

    @Test
    fun `positionMs aliases an absent position, and no extras parse as a cancellation`() = runTest {
        val harness = Harness(built = launchOf("item-1", startPositionTicks = 120_000_000L))
        harness.host.launch(ExternalPlayerRequest("item-1", startPositionTicks = 120_000_000L), startChooser = harness.quietStart)
        harness.host.onResult(mapOf("positionMs" to 2_500L))

        // A second hand-off stashes a fresh launch, so its no-extras result
        // exercises the cancellation arm through the same consume path.
        harness.host.launch(ExternalPlayerRequest("item-1", startPositionTicks = 120_000_000L), startChooser = harness.quietStart)
        harness.host.onResult(emptyMap())

        assertEquals(
            listOf(
                "build",
                "report-start",
                "resolve:SYSTEM_CHOOSER",
                "report-stopped:item-1:StoppedAt(positionTicks=25000000)",
                "build",
                "report-start",
                "resolve:SYSTEM_CHOOSER",
                "report-stopped:item-1:Cancelled(startPositionTicks=120000000)",
            ),
            harness.calls,
        )
    }

    @Test
    fun `a result with no stash is a no-op`() = runTest {
        val harness = Harness(built = launchOf("item-1"))

        harness.host.onResult(mapOf("position" to 1_000L))

        assertEquals(emptyList<String>(), harness.calls)
        assertTrue(harness.resolvedArgs.isEmpty())
    }

    @Test
    fun `launch forwards the router's decision fields to the resolver`() = runTest {
        val harness = Harness(built = launchOf("item-9"))
        val request = ExternalPlayerRequest(
            itemId = "item-9",
            mediaSourceId = "source-2",
            startPositionTicks = 600_000_000L,
            subtitleStreamIndex = 3,
        )

        harness.host.launch(request, startChooser = harness.quietStart)

        assertEquals(listOf(request), harness.resolvedArgs)
    }

    // ── preferred-player targeting ─────────────────────────────────────────

    @Test
    fun `an uninstalled preferred player falls back to the chooser`() = runTest {
        val harness = Harness(built = launchOf("item-1", preferredApp = ExternalPlayerApp.VLC))

        harness.host.launch(ExternalPlayerRequest("item-1"), startChooser = harness.recordingStart)

        // The probe ran and answered null → the chooser intent starts, and
        // the stash records NO resolved app (the result parse must run
        // the alias arm, never the VLC contract).
        assertEquals(listOf("build", "report-start", "resolve:VLC", "start"), harness.calls)
        assertEquals(Intent.ACTION_CHOOSER, harness.startedIntent!!.action)
        assertEquals(ExternalPlayerApp.VLC, harness.stashedAtStart!!.preferredApp)
        assertNull(harness.stashedAtStart!!.resolvedApp)
    }

    @Test
    fun `a targeted player starts its component directly without the chooser`() = runTest {
        val component = ComponentName(ExternalPlayerApp.VLC.packageName, ExternalPlayerApp.VLC.activity!!)
        val harness = Harness(
            built = launchOf("item-1", preferredApp = ExternalPlayerApp.VLC),
            resolvedComponent = component,
        )

        harness.host.launch(ExternalPlayerRequest("item-1"), startChooser = harness.recordingStart)

        assertEquals(listOf("build", "report-start", "resolve:VLC", "start"), harness.calls)
        assertEquals(Intent.ACTION_VIEW, harness.startedIntent!!.action)
        assertEquals(component, harness.startedIntent!!.component)
        assertEquals(ExternalPlayerApp.VLC, harness.stashedAtStart!!.resolvedApp)
    }

    @Test
    fun `the result of a targeted player parses through its own contract`() = runTest {
        val component = ComponentName(ExternalPlayerApp.MPV.packageName, ExternalPlayerApp.MPV.activity!!)
        val harness = Harness(
            built = launchOf("item-1", preferredApp = ExternalPlayerApp.MPV, startPositionTicks = 120_000_000L),
            resolvedComponent = component,
        )
        harness.host.launch(
            ExternalPlayerRequest("item-1", startPositionTicks = 120_000_000L),
            startChooser = harness.quietStart,
        )

        // MPV contract: absent `position` = completed (the chooser alias arm
        // would have parsed the same extras as a cancellation).
        harness.host.onResult(emptyMap())

        assertEquals(
            "report-stopped:item-1:Completed(positionTicks=0)",
            harness.calls.last(),
        )
    }
}
