package com.raulshma.jellyplay.core.data.playback

/**
 * Pure policy for the opt-in resume-on-headset-insert behavior (the
 * becoming-noisy auto-pause ships; this owns its revert).
 *
 * The contract has two halves, both decision-only here so the Android
 * receivers ([BecomingNoisyPauseReceiver]'s callers and
 * [HeadsetPlugReceiver]) stay chassis-only:
 *
 *  - **The pause marker.** A resume may fire only for playback the
 *    becoming-noisy event itself stopped — a user-initiated pause (or an
 *    already-paused engine at broadcast time) must never auto-resume. The
 *    pause path therefore records a marker only when the engine was actually
 *    playing when the broadcast arrived ([shouldMarkNoisyPause]).
 *
 *  - **The freshness window.** The marker decays: a headset re-plug minutes
 *    later is a new listening decision, not the continuation of the
 *    interrupted one ([RESUME_WINDOW_MS]). The resume decision
 *    ([shouldResumeOnHeadsetPlug]) requires the pref on, a marker present,
 *    and the pause still inside the window.
 *
 * Wall-clock-free on purpose: callers pass `nowMs` (Android uses
 * `SystemClock.elapsedRealtime`, immune to wall-clock jumps), so the whole
 * policy is a pure function pair, unit-tested in the module's jvmTest.
 */
object HeadsetResumePolicy {

    /**
     * How long after the becoming-noisy pause a headset re-plug may still
     * auto-resume. 5 minutes: beyond that the pause reads as "user stepped
     * away", which the pass-out-protection/autoplay surfaces own instead.
     */
    const val RESUME_WINDOW_MS: Long = 5L * 60_000L

    /**
     * Whether the just-executed becoming-noisy pause marks the session for a
     * later resume: only when the engine was actually playing at broadcast
     * time. (When it was already paused the pause is the user's, whatever the
     * broadcast timing — and ExoPlayer's built-in
     * `setHandleAudioBecomingNoisy` handler may have won the race to pause
     * it, which is exactly the "not ours" case.)
     */
    fun shouldMarkNoisyPause(wasPlaying: Boolean): Boolean = wasPlaying

    /**
     * The plug-in decision: the opt-in pref is on, a becoming-noisy pause
     * marker exists, and it is still fresh. Clock skew is tolerated
     * defensively (a negative age never resumes — it would mean a stale or
     * monotonic-clock-reset marker).
     */
    fun shouldResumeOnHeadsetPlug(
        nowMs: Long,
        noisyPauseAtMs: Long?,
        prefEnabled: Boolean,
    ): Boolean = prefEnabled &&
        noisyPauseAtMs != null &&
        nowMs >= noisyPauseAtMs &&
        nowMs - noisyPauseAtMs <= RESUME_WINDOW_MS
}
