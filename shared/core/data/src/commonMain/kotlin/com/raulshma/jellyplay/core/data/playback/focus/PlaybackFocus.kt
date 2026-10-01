package com.raulshma.jellyplay.core.data.playback.focus

import kotlinx.coroutines.flow.StateFlow

/**
 * The app's in-process playback surfaces — the closed world the exclusivity
 * matrix arbitrates over. MUSIC and READ_ALOUD live here since slice 1;
 * VIDEO joined with the video slice (its OS focus moved out of the deleted
 * `PlayerAudioLifecycle` into this module — one seat, one owner).
 */
enum class PlaybackSurfaceId { MUSIC, VIDEO, READ_ALOUD }

/** Result of [PlaybackFocus.acquire]. GRANTED means "produce audio now". */
sealed interface FocusOutcome {
    /** Claim registered; every pause directive the matrix demanded was dispatched. */
    data object Granted : FocusOutcome

    /**
     * The claim was refused (OS focus denied, or a slice-1 closed-world row
     * with no matrix ruling). The caller MUST NOT produce audio; the next
     * user action may retry.
     */
    data object Denied : FocusOutcome
}

/** Why the OS took the audio context away. Resume is manual in both cases. */
enum class FocusLossReason { Transient, Permanent }

/**
 * The single exclusivity slot. One holder at a time; [Suspended] covers OS
 * losses — uniform caller reaction (stop producing audio, show paused), and
 * in both cases resume is manual ([FocusLossReason.Permanent] simply means
 * no regain event will ever follow).
 */
sealed interface FocusClaimState {
    /** Nobody holds the floor — steady state, and the state after release. */
    data object Idle : FocusClaimState

    data class Held(val holder: PlaybackSurfaceId) : FocusClaimState

    data class Suspended(val holder: PlaybackSurfaceId, val reason: FocusLossReason) : FocusClaimState
}

/**
 * PlaybackFocus — the ONE owner of cross-player exclusivity: who is playing,
 * and who must pause whom. Before this module the policy was emergent — OS
 * audio focus reached through two different mechanisms on Android, nothing
 * on desktop, and read-aloud TTS talking over music by accident. The policy
 * is now a pinnable decision table ([PlaybackFocusMatrix]) behind one small
 * interface; platform differences live in adapters (see docs/adr/0004).
 *
 * Contract (TTS-over-music slice 1, through the video slice):
 *  - `acquire(READ_ALOUD)` requests OS focus (Android adapter) and, on
 *    grant, commands the MUSIC and VIDEO surfaces to pause (pause carries
 *    the playWhenReady guard — resume stays manual) before returning
 *    GRANTED.
 *  - `acquire(MUSIC)` requests the OS seat too (with the matrix's MUSIC
 *    attributes; `handleAudioFocus` is off on the music players — the
 *    module owns the whole story), publishes `Held(MUSIC)` and commands the
 *    VIDEO surface if one holds the floor (newest-wins); the reader
 *    observes [claimState] and pauses its own speech loop.
 *  - `acquire(VIDEO)` — since the video slice — takes the OS seat with the
 *    MOVIE attributes row (the deleted `PlayerAudioLifecycle`'s dual
 *    OS-leg, ExoPlayer-builtin and manual, moved in here as ONE seat),
 *    commands MUSIC to pause, and publishes `Held(VIDEO)`.
 *  - OS focus losses on an OS-leg claimant dispatch the claimant's
 *    [FocusLossDirective]: Pause suspends the holder AND commands its
 *    surface pause (the enforcement leg — a displaced claimant pauses
 *    itself by observation, but the holder has no observer); Duck keeps the
 *    claim Held and ducks instead, with a regain commanding the restore. A
 *    suspended claimant re-acquires on the user's next resume.
 */
interface PlaybackFocus {

    /** The exclusivity slot — the only observation a caller needs. */
    val claimState: StateFlow<FocusClaimState>

    /**
     * Take the floor for [claimant]. Non-suspending; every pause command the
     * matrix demands is dispatched synchronously before [FocusOutcome.Granted]
     * returns. Idempotent short-circuit while [claimant] already holds the
     * slot (no OS re-request, no re-pause). A suspended claimant re-requests.
     */
    fun acquire(claimant: PlaybackSurfaceId): FocusOutcome

    /**
     * Abandon the slot if [claimant] holds it; a no-op otherwise. Never
     * auto-resumes anything the claim displaced.
     */
    fun release(claimant: PlaybackSurfaceId)
}

/**
 * Honest fallback where no focus authority is bound (test harnesses without
 * a focus fixture; platforms whose Koin graph registers no
 * [DefaultPlaybackFocus]). Arbitration is vacuously GRANTED: on platforms
 * where only ONE sound-maker can exist, "exclusive by default" is the
 * truthful answer — there is no second surface to pause, and a deny here
 * would break single-player sessions for no protective gain.
 */
object NoopPlaybackFocus : PlaybackFocus {
    override val claimState: StateFlow<FocusClaimState> =
        kotlinx.coroutines.flow.MutableStateFlow(FocusClaimState.Idle)

    override fun acquire(claimant: PlaybackSurfaceId): FocusOutcome = FocusOutcome.Granted

    override fun release(claimant: PlaybackSurfaceId) {}
}

/**
 * A playback surface the matrix may command — in the cases where a command,
 * not an observation, is the only enforcement there is: the VICTIM pause a
 * new claim dispatches before taking the floor, the SUSPENDED-HOLDER pause
 * an OS loss dispatches on the holder itself (a holder has no observer that
 * would pause it on its own), and — since the video slice — the
 * TRANSIENT-LOSS duck and its GAIN restore on a duck-policy video claim
 * (see [FocusLossDirective.Duck]). The music adapter exists on both
 * platforms (`AudioPlaybackManagerSurface` and the desktop twin); the video
 * family binds a [VideoPlaybackSurface] target per screen. READ_ALOUD has
 * deliberately none: its reader observes [PlaybackFocus.claimState].
 *
 * [pause] MUST be a no-op when the surface is idle, and MUST leave the
 * surface unable to auto-resume (the playWhenReady guard — the module's
 * manual-resume decision is enforced at this command, so neither a release
 * nor an ignored OS regain can resurrect a paused surface). [duck] and
 * [restore] carry the same no-when-idle rule; [volume] is PROGRAMMATIC
 * (engines with per-content-type volume memory must never capture it — the
 * legacy duck/restore round-trip's `isUserChange = false`).
 */
interface PlaybackSurface {
    val id: PlaybackSurfaceId
    fun pause()

    /** Drop to [volume] for a transient loss. Default no-op (pause-only rows). */
    fun duck(volume: Float) {}

    /** Undo [duck]: restore the pre-duck level (re-asserting mute if muted). */
    fun restore() {}
}

/**
 * What the executor does when the OS takes focus from a held claim — the
 * per-claimant loss ruling (the matrix's loss-vocabulary growth the ADR
 * consequences record; the interface did not grow). MUSIC and READ_ALOUD
 * stay [Pause]-only with manual resume; VIDEO is the one row the duck
 * upgrade applies to (the user's `duckOnTransientFocusLoss` pref), keeping
 * its legacy transient-loss semantics alive: duck, never pause, and the
 * claim stays HELD through the duck (a regain restores; nothing else
 * changes).
 */
sealed interface FocusLossDirective {

    /**
     * Suspend the holder and command its surface pause — the slice-1 ruling
     * every non-video row keeps. Resume is manual.
     */
    data object Pause : FocusLossDirective

    /**
     * Keep the claim Held and duck the surface to [volume] instead. The
     * transient-only vocabulary: a PERMANENT loss suspends + pauses a duck
     * row too (there is no regain to restore with). [volume] is the legacy
     * duck level (audible-but-quiet during a phone call).
     */
    data class Duck(val volume: Float) : FocusLossDirective
}

/**
 * The audio attributes a claimant's OS focus seat is requested with — the
 * per-claimant input [FocusArbiter.request] takes (ADR-0004 slice-2
 * checklist: OS routing and ducking policy read usage + content type, so
 * speech, music and movie claims must not share one hardcoded pair).
 * Deliberately minimal — two closed enums carrying exactly what the Android
 * adapter maps onto `android.media.AudioAttributes`; anything richer
 * (flags, bundle hints) grows HERE when a claimant needs it, never as
 * extra parameters at the port's call sites.
 */
data class FocusAudioAttributes(
    val usage: FocusUsage,
    val contentType: FocusContentType,
)

/** Audio usage the OS routes by. Every JellyPlay surface is long-form media. */
enum class FocusUsage { MEDIA }

/** Content classification the OS ducking policy keys on (speech vs music vs movie). */
enum class FocusContentType { SPEECH, MUSIC, MOVIE }

/**
 * Platform focus authority port. Android: a fresh AudioFocusRequest owned by
 * the adapter; desktop slice 2: an in-process always-arbitrating twin; tests:
 * a scripted fake. Two adapters minimum, satisfied.
 */
interface FocusArbiter {

    /**
     * Request exclusive media focus with [attributes] and install [listener].
     * Returns the OS verdict synchronously — implementations MUST NOT use
     * delayed focus gain (an async grant would break [PlaybackFocus.acquire]'s
     * synchronous contract). Events arrive on the platform main thread.
     * Idempotent re-request replaces the listener. [attributes] are
     * claimant-derived (see [PlaybackFocusMatrix.attributesOf]) and constant
     * per claimant, so an attributes change under the seat means the holder
     * changed — adapters keep at most ONE outstanding request.
     */
    fun request(attributes: FocusAudioAttributes, listener: FocusListener): Boolean

    /** Abandon the outstanding request. Idempotent; safe before any request. */
    fun abandon()
}

/** OS focus events, attributed to the request they belong to. */
fun interface FocusListener {
    /**
     * [FocusEvent.Regained] never RESUMES playback (resume is manual,
     * pinned), but it is NOT a dead event: the module consumes it to restore
     * a ducked row's volume — adapters MUST forward it, not filter it.
     */
    fun onFocusEvent(event: FocusEvent)
}

/** The OS-side focus story (the in-process matrix never ducks or auto-resumes). */
sealed interface FocusEvent {
    data object Regained : FocusEvent
    data object LostTransient : FocusEvent
    data object LostPermanent : FocusEvent
}

/**
 * The exclusivity matrix — pure, total over the closed surface world, and
 * the module's front-door documentation. Flows in, directives out; it never
 * touches state or surfaces (the executor dispatches).
 *
 * Rulings (newest-wins, pause-not-duck by default, manual-resume; ADR-0004):
 *  - READ_ALOUD claims → pause MUSIC and VIDEO (the reader's speech loop is
 *    itself not commandable; the video surface is, since the video slice).
 *  - MUSIC claims → pause VIDEO (commandable since the video slice; before
 *    it, the row had no victims — the reader paused its own loop by
 *    observing the published Held state, an observation that never needed a
 *    command).
 *  - VIDEO claims → pause MUSIC (the playWhenReady guard rides the music
 *    surface's pause).
 *
 * The loss rulings below are the PREF-NEUTRAL defaults: every row suspends
 * + pauses on an OS loss. The video slice's duck upgrade is an INJECTED
 * input ([DefaultPlaybackFocus] carries the user's
 * `duckOnTransientFocusLoss` pref from the video-side wiring); the matrix
 * itself stays pure over the closed world.
 */
internal object PlaybackFocusMatrix {

    /** Commandable surfaces to pause before the claim takes effect. */
    fun victimsOf(claim: PlaybackSurfaceId): List<PlaybackSurfaceId> = when (claim) {
        PlaybackSurfaceId.READ_ALOUD -> listOf(PlaybackSurfaceId.MUSIC, PlaybackSurfaceId.VIDEO)
        PlaybackSurfaceId.MUSIC -> listOf(PlaybackSurfaceId.VIDEO)
        PlaybackSurfaceId.VIDEO -> listOf(PlaybackSurfaceId.MUSIC)
    }

    /** The closed world: which claims the executor may grant at all. */
    fun isGrantable(claim: PlaybackSurfaceId): Boolean = when (claim) {
        PlaybackSurfaceId.READ_ALOUD -> true
        PlaybackSurfaceId.MUSIC -> true
        PlaybackSurfaceId.VIDEO -> true
    }

    /**
     * OS-seat audio attributes per claimant (ADR-0004 slice-2 checklist: the
     * migration slice must not force music onto speech attributes or onto a
     * second OS request). READ_ALOUD keeps the slice-1 hardcoded pair
     * (USAGE_MEDIA + CONTENT_TYPE_SPEECH) — zero behavior change. MUSIC's
     * migration slice claimed the MUSIC row; VIDEO's row was reserved ahead
     * of its claim and the video slice now takes it under
     * CONTENT_TYPE_MOVIE (the OS routing/ducking policy reads the content
     * type).
     */
    fun attributesOf(claim: PlaybackSurfaceId): FocusAudioAttributes = when (claim) {
        PlaybackSurfaceId.READ_ALOUD -> FocusAudioAttributes(FocusUsage.MEDIA, FocusContentType.SPEECH)
        PlaybackSurfaceId.MUSIC -> FocusAudioAttributes(FocusUsage.MEDIA, FocusContentType.MUSIC)
        PlaybackSurfaceId.VIDEO -> FocusAudioAttributes(FocusUsage.MEDIA, FocusContentType.MOVIE)
    }

    /**
     * The pref-neutral loss ruling: every row pauses + suspends on an OS
     * loss (resume manual). VIDEO's duck upgrade rides the injected
     * video-pref input, never this table — see [FocusLossDirective.Duck].
     */
    fun lossDirectiveOf(claim: PlaybackSurfaceId): FocusLossDirective = when (claim) {
        PlaybackSurfaceId.READ_ALOUD -> FocusLossDirective.Pause
        PlaybackSurfaceId.MUSIC -> FocusLossDirective.Pause
        PlaybackSurfaceId.VIDEO -> FocusLossDirective.Pause
    }

    /**
     * Claims whose OS audio-focus seat THIS module owns by default. READ_ALOUD
     * since slice 1, MUSIC since its migration slice; VIDEO joins with the
     * video slice — but its membership is pref-gated at runtime by the
     * injected video policy (the wiring computes the gate as the focus
     * prefs' OR — in the legacy dual-mechanism world the duck seat ran on
     * the duck pref ALONE, so pause-off/duck-on still takes the seat; the
     * module's executor carries the gate).
     */
    val DEFAULT_OS_LEG_CLAIMANTS: Set<PlaybackSurfaceId> =
        setOf(PlaybackSurfaceId.READ_ALOUD, PlaybackSurfaceId.MUSIC, PlaybackSurfaceId.VIDEO)
}
