package com.raulshma.jellyplay.core.data.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * THE one PiP-dismissal discharge both player hosts (`player-video`,
 * `player-live`) run — previously two verbatim `pipDismissed` collectors, one
 * per host ViewModel, each re-implementing the same ordering and carrying its
 * own copy of the issue-#145 story.
 *
 * Landing in this collector means the PiP window is showing a dead stream:
 * the host Activity's auto-exit collector translated
 * [PipController.requestAutoExitPip] into `notifyPipDismissed`, and the
 * screen must now close. The choreography is ORDER-SENSITIVE:
 *
 *  1. [teardown] — the host's ordered teardown list. Both hosts put the
 *     engine pause(s) FIRST so audio dies before anything is released, then
 *     their session teardown (VOD: media-session + mini-player release +
 *     `release()`; live: `stop()`, which releases the engine and resets PiP).
 *  2. [close] — the one-shot screen-close emission, AFTER the teardown so
 *     the screen never observes a half-torn-down session while closing (VOD:
 *     the `_closePlayer` channel; live: the engine-session shell's
 *     tryEmit-only event pipe — a mid-teardown emission never suspends).
 *  3. The defensive [PipController.clearPipDismissed] — this is the
 *     load-bearing step, not paranoia: the full teardown path normally
 *     clears the latch via [PipController.reset], but the host's release can
 *     early-return on its idempotence latch (already-released session),
 *     which leaves `pipDismissed == true` stuck on the process-@Singleton
 *     controller. The next player instance then read it as the INITIAL
 *     collected value of this collector and closed instantly (issue #145,
 *     "can't start play on anything"). Clearing here unconditionally makes
 *     the latch one-shot per dismissal no matter how the teardown resolved.
 *
 * The per-host MAPPING stays host-owned: each host supplies only its
 * [teardown] list and its [close] pipe, so the two hosts cannot drift on the
 * discharge mechanics while their session surfaces stay free to differ.
 *
 * [pip] is nullable because live's constructor takes `PipController?`
 * (platforms without PiP bind null there); a null controller is a no-op —
 * the same posture as [reArmPipTransport].
 *
 * A latch already `true` when the collector starts IS discharged (plain
 * StateFlow collect semantics): the hosts clear the latch defensively at
 * initialize-time, so a `true` seen at collection start is a live dismissal,
 * not a stale one — issue #145's fix is the defensive clear in step 3, not
 * swallowing the initial value.
 */
fun CoroutineScope.dischargePipDismissal(
    pip: PipController?,
    teardown: () -> Unit,
    close: () -> Unit,
): Job = launch {
    val controller = pip ?: return@launch
    controller.pipDismissed.collect { dismissed ->
        if (dismissed) {
            teardown()
            close()
            controller.clearPipDismissed()
        }
    }
}
