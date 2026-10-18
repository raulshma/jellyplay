package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.remote.ActivePlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The ONE fold over the video-engine / audio-manager fork answering "is
 * anything playing, anywhere" (the ratified GLOSSARY "Now-playing verdict").
 * Before this module the answer existed only as restatements — the progress
 * reporter forked on engine-vs-manager twice in one file, the remote-control
 * receiver restated a third formulation (idle = no bound engine AND no
 * tracker item), the widget and tile read the audio manager's flows
 * directly — plus the mutable `remoteSessionActive` flag whose
 * single-writer contract lived in a prose note. All of it folds here:
 *
 *  - [isPlaying] / [hasActiveSession] — the cross-family OR-folds (the
 *    term's headline answers; engine bound or audio session live);
 *  - [isIdle] — the DisplayContent conjunction, kept EXACT (it is NOT
 *    `!hasActiveSession`: a bound-but-idle engine and the play()'s
 *    claim-before-publish window both split the two);
 *  - [progressTicksAndPaused] / [isPausedNow] — the progress reporter's
 *    fork, moved verbatim: a bound video engine owns the answer REGARDLESS
 *    of its playing state (video-priority, not either-or — the reporter's
 *    session duty rides whichever surface the remote initiated);
 *  - [remoteSessionActive] — the flag's storage moved here from the audio
 *    manager (the writer stays [com.raulshma.jellyplay.core.data.remote.RemotePlaybackReporter]
 *    alone, via the internal [markRemoteSession]).
 *
 * `NowPlayingSurface` stays the audio metadata surface; this is the
 * cross-family verdict beside it. The family sources are ctor-injected
 * (the video registry + the audio [AudioNowPlayingFace]) so the module
 * sits in commonMain: the video reference is commonMain's
 * [ActivePlayerController] over the player-contract engine interface —
 * zero platform cost — and the audio face is satisfied directly by both
 * managers (Android media3, desktop mpv). The tile and widget read the
 * audio family through [audio] because their folds are the AUDIO
 * transport's (feeding them the cross-family faces would flip the QS tile
 * ACTIVE and re-render the audio widget on video-only play edges —
 * observable changes, refused; the cross-family adoption is the
 * interfaces' to make the day the surfaces go cross-family).
 *
 * FOLD STYLE — pull, not observed: every cross face is computed on read
 * and the family flows are re-exposed by reference, so there is no new
 * emission timing anywhere (the reporter polls, the receiver answers an
 * event, the tile/widget collect the same StateFlow instances as before).
 * An observed fold would need a scope + `stateIn` (inventing conflation
 * and a start latency the pull reads never had) or a cold combine whose
 * engine leg goes stale between bind/unbind emissions (an engine's
 * isPlaying changes while BOUND — no `activeEngine` emission rides that
 * edge, so the cold fold would answer with the last evaluated value).
 * Refused under behavior preservation; the pull faces are the fold.
 */
class NowPlayingVerdict(
    private val videoEngines: ActivePlayerController,
    /** The audio family's read face — the flows are by-reference instances. */
    val audio: AudioNowPlayingFace,
) {

    /**
     * The remote-session marker, formerly the audio manager's
     * `@Volatile var remoteSessionActive` with the single-writer contract
     * in a prose note. NOT derivable from the playback flows — it marks
     * "another device initiated this session" (a cast / Play-To session
     * may play with the local engine idle), so deriving it from
     * is-playing would gate the local reporter off during plain local
     * playback. The cell lives here; [com.raulshma.jellyplay.core.data.remote.RemotePlaybackReporter]
     * commands it at the SAME two protocol points as before (startSession:
     * after the auth gate, before `PlaybackStart` — and only after the
     * pre-flight `stopSessionInternal` cleared it; stopSession: after the
     * session fields clear). The audio manager's progress loop reads the
     * same value through its ctor lambda (wired over this flow), so the
     * REMOTE GATE protocol is byte-identical — the `@Volatile` became a
     * [MutableStateFlow] write (atomic + visible, same synchronous
     * happens-before the gate's next read).
     */
    private val _remoteSessionActive = MutableStateFlow(false)
    val remoteSessionActive: StateFlow<Boolean> = _remoteSessionActive.asStateFlow()

    /** Single-writer command ([RemotePlaybackReporter] is the writer). */
    internal fun markRemoteSession(active: Boolean) {
        _remoteSessionActive.value = active
    }

    /**
     * "Is anything playing, anywhere" — the OR-fold: the bound video
     * engine's playing state, or the audio engine's. No consumer yet (the
     * reporter's fork below is deliberately engine-priority, not this);
     * this is the term's canonical answer for the next adopter.
     */
    val isPlaying: Boolean
        get() = videoEngines.engine?.isPlaying?.value == true || audio.isPlaying.value

    /**
     * "Is there a resumable session, anywhere" — the OR-fold over the
     * video registry's bound engine and the audio face's loaded session.
     * No consumer yet; exposed because the tile's and the reporter's
     * audio-only reads answer this question's AUDIO HALF through
     * [audio] today.
     */
    val hasActiveSession: Boolean
        get() = videoEngines.engine != null || audio.hasActiveSession

    /**
     * The remote "DisplayContent" gate, exactly as the receiver restated
     * it: no bound video engine AND no tracker item in the audio queue.
     * NOT `!hasActiveSession` — a bound-but-stopped engine keeps this
     * false (the player screen is up; flipping the UI under it is
     * hostile), and the play()'s claim-before-publish window (item cell
     * claimed, tracker not yet published) keeps [hasActiveSession] true
     * while this is still true. The two formulations disagree there BY
     * DESIGN — that disagreement is why this face exists.
     */
    val isIdle: Boolean
        get() = videoEngines.engine == null && audio.currentPlayingItemId.value == null

    /**
     * The progress reporter's position/paused face, moved verbatim from
     * its `currentPositionTicksAndPaused`: the bound video engine wins
     * unconditionally (ms → ticks ×10 000, paused = engine not playing);
     * otherwise the audio face answers ONLY with a loaded session —
     * position flows regardless (the ms → ticks ×10 000 conversion moved
     * with the fold, so this returns ticks; the video loop's copy of the
     * conversion stays in [PeriodicProgressReportLoop]), and no session
     * means null (the reporter skips the cycle). Null = nothing reportable.
     */
    fun progressTicksAndPaused(): Pair<Long, Boolean>? {
        val engine = videoEngines.engine
        if (engine != null) {
            return engine.currentPositionMs * 10_000L to !engine.isPlaying.value
        }
        val pos = audio.currentPosition.value
        val paused = !audio.isPlaying.value
        if (audio.hasActiveSession) {
            return pos * 10_000L to paused
        }
        return null
    }

    /**
     * The progress reporter's cadence face, moved verbatim from its
     * `isPausedNow`: engine-priority (a bound engine's paused state even
     * when it is also the 60 s-vs-10 s decider), else the audio engine's.
     * Unlike [progressTicksAndPaused] it never consults the session gate —
     * the reporter's delay decision only.
     */
    fun isPausedNow(): Boolean {
        val engine = videoEngines.engine
        if (engine != null) return !engine.isPlaying.value
        return !audio.isPlaying.value
    }
}
