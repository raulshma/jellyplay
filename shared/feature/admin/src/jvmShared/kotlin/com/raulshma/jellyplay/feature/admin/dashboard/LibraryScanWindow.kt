package com.raulshma.jellyplay.feature.admin.dashboard

import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.SessionInfo
import java.util.concurrent.atomic.AtomicLong

/**
 * The scan optimistic grace-window machine — pure time logic kept out of
 * [AdminDashboardViewModel] (the mediacleanup `MediaCleanupScanStateHolder`
 * shape).
 *
 * The server takes a beat to flip the "Scan media library" scheduled task
 * IDLE → RUNNING after the start request, and a WS push landing in that
 * window would report IDLE and collapse the optimistic
 * [LibraryScanState.Running] the tap just raised. The machine holds ONE
 * deadline: [start] arms it (now + [graceMs]), [confirmRunning] disarms it
 * the moment the WS confirms RUNNING (so a later IDLE genuinely means the
 * scan finished), [isActive] answers whether an armed, unexpired window is
 * open, and the caller clears it ([clear]) when it expires unconfirmed so
 * the UI can settle to Idle.
 *
 * Time is read through the [now] supplier (default the wall clock) so the
 * JVM test pins the machine with timestamps in → boolean out. Thread-safety
 * rides the [AtomicLong] the VM's field already was.
 */
internal class LibraryScanWindow(
    private val now: () -> Long = System::currentTimeMillis,
    /**
     * How long the optimistic hold lasts after a user tap — generous enough
     * to cover startTask latency + the IDLE→RUNNING flip, short enough to
     * recover the UI if the start request silently failed.
     */
    private val graceMs: Long = DEFAULT_GRACE_MS,
) {

    private val optimisticUntilMs = AtomicLong(0L)

    /** Arms the window for a fresh user-initiated scan. */
    fun start() {
        optimisticUntilMs.set(now() + graceMs)
    }

    /** The server confirmed RUNNING — no more optimistic hold. */
    fun confirmRunning() {
        optimisticUntilMs.set(0L)
    }

    /** True while a started, unexpired window is open. */
    fun isActive(): Boolean {
        val untilMs = optimisticUntilMs.get()
        return untilMs != 0L && now() < untilMs
    }

    /** Drops the hold (window expired unconfirmed, or the scan settled). */
    fun clear() {
        optimisticUntilMs.set(0L)
    }

    internal companion object {
        /** The default optimistic-hold window after a user tap. */
        const val DEFAULT_GRACE_MS = 15_000L
    }
}

/**
 * The WS-task-push freshness heuristic — pure time logic like
 * [LibraryScanWindow], from [AdminDashboardViewModel.wsTasksFresh]: pushes
 * arrive at 1 Hz, so [isFresh] trusts them over a REST snapshot only within
 * [staleAfterMs] of the channel's last push — more silence than that means
 * the socket is dead and REST may refresh the running-task card again.
 *
 * Freshness is judged from the PUSH timestamp passed in, not from collection
 * time: the shared flow replays its last emission to every new collector,
 * and that replay can be arbitrarily old. [now] is injectable for the same
 * test reason as [LibraryScanWindow].
 */
internal class WsTaskFreshness(
    private val staleAfterMs: Long = DEFAULT_STALE_AFTER_MS,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * True when [lastPushAtMs] is a real timestamp (non-zero) younger than
     * [staleAfterMs].
     */
    fun isFresh(lastPushAtMs: Long): Boolean =
        lastPushAtMs != 0L && now() - lastPushAtMs < staleAfterMs

    internal companion object {
        /** The VM's former `WS_PUSH_STALE_AFTER_MS` constant, moved with the rule. */
        const val DEFAULT_STALE_AFTER_MS = 30_000L
    }
}

/**
 * The WS task-push rebuild gate — pure comparison logic like
 * [WsTaskFreshness], from [AdminDashboardViewModel]'s push collector.
 *
 * The server pushes the full task list at 1 Hz whether anything visible
 * changed or not (a running task's progress double moves almost every
 * push), so the collector asks [presentationChanged] before publishing a new
 * dashboard state: only a change the running-task card can actually render
 * — membership, order, state, name, or the whole-percent reading of the
 * progress — counts. Sub-percent progress drift is invisible at the card's
 * rendered unit (`${progress.toInt()}%` text and a bar that moves 1% of its
 * 6 dp), and suppressing it keeps the ~1 Hz pushes from rebuilding the
 * state (and recomposing the dashboard) for nothing. Any structural change
 * propagates immediately.
 */
internal object RunningTasksGate {

    /**
     * True when the running-task card's presentation differs between [old]
     * and [new]: same tasks in the same order with the same state, name,
     * and whole-percent progress read as unchanged.
     */
    fun presentationChanged(old: List<ScheduledTaskInfo>, new: List<ScheduledTaskInfo>): Boolean {
        if (old.size != new.size) return true
        return old.indices.any { i ->
            val previous = old[i]
            val current = new[i]
            previous.id != current.id ||
                previous.name != current.name ||
                previous.state != current.state ||
                displayedProgress(previous) != displayedProgress(current)
        }
    }

    /**
     * The progress value the card renders: the whole-percent truncation
     * (`${progress.toInt()}%`) — or null when the task reports none (the
     * bar's absence is itself a presentation change).
     */
    private fun displayedProgress(task: ScheduledTaskInfo): Int? =
        task.currentProgressPercentage?.toInt()
}

/**
 * The WS session-push rebuild gate — [RunningTasksGate]'s comparison shape
 * over the dashboard's session list, from the sessions push collector.
 *
 * The server pushes the full `Sessions` array on session/playstate changes of
 * any client, so a playing session's position ticks (and the other playstate
 * fields the session card does not render) drift push after push. The
 * collector asks [presentationChanged] before publishing: only a change the
 * ActiveSessionsSection card can actually render — membership, order, user,
 * device, client, or the now-playing title — counts. Playstate drift is
 * invisible at the card and suppressing it keeps the pushes from rebuilding
 * the dashboard state for nothing.
 */
internal object SessionsGate {

    /**
     * True when the session card's presentation differs between [old] and
     * [new]: same sessions in the same order with the same user, device,
     * client, and now-playing title read as unchanged.
     */
    fun presentationChanged(old: List<SessionInfo>, new: List<SessionInfo>): Boolean {
        if (old.size != new.size) return true
        return old.indices.any { i ->
            val previous = old[i]
            val current = new[i]
            previous.id != current.id ||
                previous.userName != current.userName ||
                previous.deviceName != current.deviceName ||
                previous.client != current.client ||
                previous.nowPlayingItem?.name != current.nowPlayingItem?.name
        }
    }
}
