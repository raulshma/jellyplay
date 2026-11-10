package com.raulshma.jellyplay.feature.admin.dashboard

import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.SessionNowPlayingItem
import com.raulshma.jellyplay.core.model.TaskState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the extracted dashboard time machines — [LibraryScanWindow] (the scan
 * optimistic grace window) and [WsTaskFreshness] (the WS-push trust rule) —
 * with injected clocks: timestamps in → state out, no wall clock and no VM
 * (the mediacleanup `MediaCleanupScanStateHolderTest` precedent). The
 * [RunningTasksGate] and [SessionsGate] comparisons ride here too (same
 * extracted-pure-logic family, pinned without a VM).
 */
class LibraryScanWindowTest {

    // ── LibraryScanWindow ───────────────────────────────────────────────

    @Test
    fun `start arms the window for exactly the grace duration`() {
        var nowMs = 1_000L
        val window = LibraryScanWindow(now = { nowMs }, graceMs = 15_000L)

        assertFalse(window.isActive(), "an unstarted window is never active")

        window.start()
        // 1ms before the deadline.
        nowMs = 1_000L + 15_000L - 1
        assertTrue(window.isActive(), "inside the window")
        // Exactly at the deadline: expired (the strict < the VM compared).
        nowMs = 1_000L + 15_000L
        assertFalse(window.isActive(), "at the deadline")
    }

    @Test
    fun `confirmRunning disarms immediately so a later idle means finished`() {
        var nowMs = 0L
        val window = LibraryScanWindow(now = { nowMs }, graceMs = 15_000L)
        window.start()
        nowMs = 1_000L
        window.confirmRunning()

        assertTrue(nowMs < 16_000L)
        assertFalse(window.isActive(), "the WS confirmed RUNNING — no more optimistic hold")
        // And it stays disarmed as time advances.
        nowMs = 2_000L
        assertFalse(window.isActive())
    }

    @Test
    fun `a re-armed window after clear restarts the full grace`() {
        var nowMs = 0L
        val window = LibraryScanWindow(now = { nowMs }, graceMs = 15_000L)

        window.start()
        window.clear()
        assertFalse(window.isActive(), "cleared")
        nowMs = 10_000L
        assertFalse(window.isActive())

        // Second scan tap: the window re-arms from NOW, not from the first tap.
        window.start()
        nowMs = 10_000L + 15_000L - 1
        assertTrue(window.isActive(), "re-armed window covers the second tap")
        nowMs = 10_000L + 15_000L
        assertFalse(window.isActive())
    }

    @Test
    fun `clear resets an active window`() {
        var nowMs = 0L
        val window = LibraryScanWindow(now = { nowMs }, graceMs = 15_000L)
        window.start()
        nowMs = 5_000L
        assertTrue(window.isActive())
        window.clear()
        assertFalse(window.isActive(), "explicit clear wins over the deadline")
    }

    @Test
    fun `default grace matches the VM constant`() {
        // The extraction moved the VM's 15s literal; pin the default so the
        // behavior delta of the fold stays zero.
        assertEquals(15_000L, LibraryScanWindow.DEFAULT_GRACE_MS)
    }

    // ── WsTaskFreshness ─────────────────────────────────────────────────

    @Test
    fun `a recent push is fresh and silence past the window is not`() {
        var nowMs = 100_000L
        val freshness = WsTaskFreshness(staleAfterMs = 30_000L, now = { nowMs })

        assertTrue(freshness.isFresh(lastPushAtMs = 100_000L), "pushed this instant")
        nowMs = 129_999L
        assertTrue(freshness.isFresh(lastPushAtMs = 100_000L), "29.999s of silence is fresh")
        nowMs = 130_000L
        assertFalse(freshness.isFresh(lastPushAtMs = 100_000L), "exactly 30s is stale (strict <)")
    }

    @Test
    fun `the zero sentinel is never fresh`() {
        // A channel that never pushed carries 0 — the heuristic must not read
        // it as "pushed an eternity ago that is somehow fresh".
        val freshness = WsTaskFreshness(staleAfterMs = 30_000L, now = { 1_000L })
        assertFalse(freshness.isFresh(lastPushAtMs = 0L))
    }

    @Test
    fun `a replay older than the window is stale`() {
        // The shared flow's replay can be arbitrarily old — freshness is
        // judged from the push timestamp, so a stale replay is refused even
        // though a collector just received it.
        val freshness = WsTaskFreshness(staleAfterMs = 30_000L, now = { 500_000L })
        assertFalse(freshness.isFresh(lastPushAtMs = 100_000L))
    }

    @Test
    fun `default staleness matches the VM constant`() {
        assertEquals(30_000L, WsTaskFreshness.DEFAULT_STALE_AFTER_MS)
    }

    // ── RunningTasksGate ──────────────────────────────────────────────────

    private fun runningTask(
        id: String = "task-1",
        name: String = "Scan Media Library",
        progress: Double? = 25.0,
    ) = ScheduledTaskInfo(id = id, key = "RefreshLibrary", name = name, state = TaskState.RUNNING, currentProgressPercentage = progress)

    @Test
    fun `a sub-percent progress drift is not a presentation change`() {
        val old = listOf(runningTask(progress = 33.3))
        val new = listOf(runningTask(progress = 33.8))

        // The card renders the whole-percent truncation; a drift the card
        // cannot display must not rebuild the dashboard state.
        assertFalse(RunningTasksGate.presentationChanged(old, new))
    }

    @Test
    fun `a whole-percent progress move is a presentation change`() {
        val old = listOf(runningTask(progress = 33.3))
        val new = listOf(runningTask(progress = 34.01))

        assertTrue(RunningTasksGate.presentationChanged(old, new))
    }

    @Test
    fun `membership order state and name changes propagate immediately`() {
        // A task joins the running list.
        assertTrue(RunningTasksGate.presentationChanged(emptyList(), listOf(runningTask())))
        // A task leaves.
        assertTrue(RunningTasksGate.presentationChanged(listOf(runningTask()), emptyList()))
        // Same size, different task.
        assertTrue(
            RunningTasksGate.presentationChanged(
                listOf(runningTask(id = "a")),
                listOf(runningTask(id = "b")),
            ),
        )
        // Reordered.
        assertTrue(
            RunningTasksGate.presentationChanged(
                listOf(runningTask(id = "a"), runningTask(id = "b")),
                listOf(runningTask(id = "b"), runningTask(id = "a")),
            ),
        )
        // State flip (RUNNING → CANCELLING) and a rename both render.
        assertTrue(
            RunningTasksGate.presentationChanged(
                listOf(runningTask()),
                listOf(runningTask().copy(state = TaskState.CANCELLING)),
            ),
        )
        assertTrue(
            RunningTasksGate.presentationChanged(
                listOf(runningTask()),
                listOf(runningTask(name = "Renamed Task")),
            ),
        )
        // The progress bar's presence itself (null ↔ a value) renders.
        assertTrue(
            RunningTasksGate.presentationChanged(
                listOf(runningTask(progress = null)),
                listOf(runningTask(progress = 1.0)),
            ),
        )
    }

    @Test
    fun `identical pushes are not a presentation change`() {
        val tasks = listOf(runningTask(progress = 25.0), runningTask(id = "task-2", name = "Optimize", progress = null))
        assertFalse(RunningTasksGate.presentationChanged(tasks, tasks))
    }

    // ── SessionsGate ──────────────────────────────────────────────────────

    private fun session(
        id: String = "s-1",
        userName: String = "Alice",
        deviceName: String = "Pixel",
        client: String = "JellyPlay Android",
        nowPlaying: String? = "Movie",
    ) = SessionInfo(
        id = id,
        userName = userName,
        deviceName = deviceName,
        client = client,
        nowPlayingItem = nowPlaying?.let { SessionNowPlayingItem(id = "i-1", name = it) },
    )

    @Test
    fun `a playstate-only session drift is not a presentation change`() {
        val old = listOf(session())
        // The push's playstate (position ticks, pause) moved — nothing the
        // session card renders.
        val new = listOf(session()).map {
            it.copy(playState = com.raulshma.jellyplay.core.model.SessionPlayState(positionTicks = 900_000_000L, isPaused = true))
        }

        assertFalse(SessionsGate.presentationChanged(old, new))
    }

    @Test
    fun `session membership user device client and now-playing changes propagate`() {
        val old = listOf(session())
        // A session joins.
        assertTrue(SessionsGate.presentationChanged(emptyList(), listOf(session())))
        // A session ends.
        assertTrue(SessionsGate.presentationChanged(old, emptyList()))
        // Same size, different session.
        assertTrue(SessionsGate.presentationChanged(old, listOf(session(id = "s-2"))))
        // User, device, client, and the now-playing title all render.
        assertTrue(SessionsGate.presentationChanged(old, listOf(session(userName = "Bob"))))
        assertTrue(SessionsGate.presentationChanged(old, listOf(session(deviceName = "Tablet"))))
        assertTrue(SessionsGate.presentationChanged(old, listOf(session(client = "JellyPlay Web"))))
        assertTrue(SessionsGate.presentationChanged(old, listOf(session(nowPlaying = "Other Movie"))))
        // Playback stopped on the session (now-playing → device line).
        assertTrue(SessionsGate.presentationChanged(old, listOf(session(nowPlaying = null))))
    }

    @Test
    fun `identical session pushes are not a presentation change`() {
        val sessions = listOf(session(), session(id = "s-2", userName = "Bob", deviceName = "Tablet", nowPlaying = null))
        assertFalse(SessionsGate.presentationChanged(sessions, sessions))
    }
}
