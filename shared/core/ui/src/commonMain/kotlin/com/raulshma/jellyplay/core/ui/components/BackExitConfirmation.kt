package com.raulshma.jellyplay.core.ui.components

/**
 * One "press back again to exit" policy, two thin BackHandler feeds.
 *
 * The phone shell (`PhoneContent` in `:app`'s `JellyPlayApp.kt`) and the TV
 * drawer (`TvNavigationDrawer`) implement the same fold — at an exit point
 * (phone: a tab root; TV: a top-level page), a back press inside
 * [DEFAULT_EXIT_CONFIRMATION_TIMEOUT_MS] of the previous one backs the task
 * out of the foreground, otherwise it prompts (the call sites show a Toast)
 * and stamps the clock. Outside an exit point the press pops in-app
 * navigation instead. This class owns the fold;
 * each call site keeps only a thin feed translating the decision:
 *
 *  - [Decision.Pop] — not at an exit point: the host navigates back itself;
 *  - [Decision.Prompt] — outside the window: show the exit prompt and stamp
 *    [Decision.Prompt.nowMs] as the last press time;
 *  - [Decision.Exit] — inside the window: move the task to the back and reset
 *    the tracked time to `0L`, so the window restarts closed (a prompt → exit
 *    → prompt sequence never exits on the third press).
 *
 * The policy is stateless: the last-press timestamp lives at the call site (a
 * `remember { mutableLongStateOf(0L) }`) and is fed in as [lastAtMs], `0L`
 * meaning "no press tracked". The window is the shared
 * [DEFAULT_EXIT_CONFIRMATION_TIMEOUT_MS] — two presses strictly inside it
 * exit; exactly that far apart has already expired (both former hand copies
 * compared `now - last < timeout`, so the boundary is exclusive).
 */
class BackExitConfirmation {

    /** What the caller must do with one back press. */
    sealed interface Decision {

        /** Not at an exit point — the host pops its own in-app navigation. */
        data object Pop : Decision

        /** Outside the window — show the exit prompt and stamp [nowMs]. */
        data class Prompt(val nowMs: Long) : Decision

        /** Inside the window — leave the foreground. */
        data object Exit : Decision
    }

    /**
     * Folds one back press at [nowMs] against the previously stamped
     * [lastAtMs]. [atExitPoint] feeds the host's exit-point gate (phone: at a
     * tab root; TV: not on a sub-page) so the whole back-press fold resolves
     * here; outside an exit point the decision is [Decision.Pop] regardless of
     * the window.
     */
    fun onBack(nowMs: Long, lastAtMs: Long, atExitPoint: Boolean = true): Decision {
        if (!atExitPoint) return Decision.Pop
        return if (lastAtMs > 0L && nowMs - lastAtMs < DEFAULT_EXIT_CONFIRMATION_TIMEOUT_MS) {
            Decision.Exit
        } else {
            Decision.Prompt(nowMs)
        }
    }

    companion object {

        /** The window both former hand copies used. */
        const val DEFAULT_EXIT_CONFIRMATION_TIMEOUT_MS = 2000L
    }
}
