package com.raulshma.jellyplay.feature.player.video.session

import com.raulshma.jellyplay.core.model.SessionRestoreMode
import com.raulshma.jellyplay.feature.player.video.STALE_POSITION_THRESHOLD_MS

/**
 * Why the process is asking about a cold-start restore. The shell folds its
 * launch facts into ONE value before [decideSessionRestoration] runs:
 *
 *  - [ColdStart] — a plain app launch (icon, task switch, restored-but-stripped
 *    nav state). The only context a restore may fire from.
 *  - [DeepLink] — the launch carried an explicit destination (deep link,
 *    launcher shortcut, shared-text/search target on Android; an argv/clipboard
 *    link on desktop). The shell folds every explicit-destination arm here —
 *    the policy treats them identically.
 *  - [ExternalPlaybackIntent] — an out-of-band playback request (Play On /
 *    cast routing, external-player hand-off).
 *  - [AlreadyInPlayer] — a player route is (or is about to be) on top of the
 *    nav state at evaluation time.
 *
 * The three non-[ColdStart] values exist so a shell NEVER hijacks an explicit
 * destination with its own "resume?" prompt: every non-ColdStart context
 * decides `None` regardless of mode.
 */
enum class LaunchContext {
    ColdStart,
    DeepLink,
    ExternalPlaybackIntent,
    AlreadyInPlayer,
}

/**
 * What the shell should do about the interrupted session. Carries the
 * [SessionRestorationSnapshot] it decided on, so the shell needs no follow-up
 * lookup: `Ask` renders the resume prompt, `AutoRestore` navigates straight to
 * the player route.
 */
sealed interface SessionRestorationDecision {
    /** Nothing to offer (or the restore was refused) — the shell does nothing. */
    data object None : SessionRestorationDecision

    /** Offer the restore: shell shows a dismissible "Resume '<title>'?" prompt. */
    data class Ask(val snapshot: SessionRestorationSnapshot) : SessionRestorationDecision

    /** Restore silently: shell navigates to the player at the snapshot's position. */
    data class AutoRestore(val snapshot: SessionRestorationSnapshot) : SessionRestorationDecision
}

/**
 * The cold-start session-restoration policy — the pure decision half of the
 * feature (Compose-free, jvmTest-pinned by SessionRestorationPolicyTest). The
 * shells only apply the decision; every rule lives here.
 *
 * Rules, in precedence order:
 *  1. [SessionRestoreMode.OFF] → `None` always (the persisted "discard" mode).
 *  2. Non-[LaunchContext.ColdStart] → `None` — an explicit destination
 *     (deep link / playback intent / an already-open player) is never hijacked.
 *  3. `snapshot == null` → `None` — nothing to restore (nothing mid-playback on
 *     the server, the fetch failed, or an incognito-only history: incognito
 *     sessions suppress progress reports, so the server has no resume data for
 *     them — see [SessionRestorationSnapshot]).
 *  4. Staleness: a snapshot stamped more than [STALE_POSITION_THRESHOLD_MS] ago
 *     is refused — the SAME 1 h constant the process-death restore uses
 *     (`ResumeTicks.kt`, shared module-internally). An interrupted session older
 *     than an hour is better reached through the Continue Watching row than a
 *     boot-time prompt. A missing/zero stamp is treated FRESH, mirroring
 *     `resolveResumeTicks`'s missing-timestamp rule (a server that omits
 *     `lastPlayedDate` must not silently disable the feature).
 *  5. [SessionRestoreMode.AUTO] → `AutoRestore`; [SessionRestoreMode.ASK] →
 *     `Ask`.
 *
 * [nowMs] defaults to the wall clock so the mandated three-argument call shape
 * works; tests pin the table with explicit instants (pure decision otherwise).
 */
fun decideSessionRestoration(
    mode: SessionRestoreMode,
    snapshot: SessionRestorationSnapshot?,
    launchContext: LaunchContext,
    nowMs: Long = System.currentTimeMillis(),
): SessionRestorationDecision = when {
    mode == SessionRestoreMode.OFF -> SessionRestorationDecision.None
    launchContext != LaunchContext.ColdStart -> SessionRestorationDecision.None
    snapshot == null -> SessionRestorationDecision.None
    isSnapshotStale(snapshot, nowMs) -> SessionRestorationDecision.None
    mode == SessionRestoreMode.AUTO -> SessionRestorationDecision.AutoRestore(snapshot)
    // ASK is the only mode left (the enum is closed: OFF / ASK / AUTO).
    else -> SessionRestorationDecision.Ask(snapshot)
}

/** Rule 4: stamped, non-zero, and older than the shared 1 h staleness threshold. */
private fun isSnapshotStale(snapshot: SessionRestorationSnapshot, nowMs: Long): Boolean {
    val stamp = snapshot.timestampMs ?: return false
    if (stamp <= 0L) return false
    return nowMs - stamp > STALE_POSITION_THRESHOLD_MS
}
