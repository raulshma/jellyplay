package com.raulshma.jellyplay.core.data.playback.focus

import kotlinx.coroutines.flow.StateFlow

/**
 * The app's in-process playback surfaces — the closed world the exclusivity
 * matrix arbitrates over. Slice 1 live pair: MUSIC and READ_ALOUD. VIDEO is
 * reserved (its OS focus still lives in PlayerAudioLifecycle; it joins with
 * the video slice, which must add a matrix row — the exhaustive `when` makes
 * forgetting a row a build error, not a runtime mixing bug).
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
 * Slice-1 contract (TTS-over-music):
 *  - `acquire(READ_ALOUD)` requests OS focus (Android adapter) and, on
 *    grant, commands the MUSIC surface to pause (its `pause()` also drops
 *    playWhenReady, so the focus-stack regain at release can never
 *    auto-resume music — resume stays manual) before returning GRANTED.
 *  - `acquire(MUSIC)` publishes `Held(MUSIC)` (newest user action wins); the
 *    reader observes [claimState] and pauses its own speech loop. No OS
 *    request is made for MUSIC at slice 1 — ExoPlayer's built-in focus
 *    handling remains music's OS leg until the migration slice.
 *  - OS focus losses on the OS-leg claimant suspend it ([Suspended]); a
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
 * Honest fallback where no focus authority is bound (wasmJs; desktop until
 * slice 2; test harnesses without a focus fixture). Arbitration is vacuously
 * GRANTED: on platforms where only ONE sound-maker can exist, "exclusive by
 * default" is the truthful answer — there is no second surface to pause, and
 * a deny here would break single-player sessions for no protective gain.
 */
object NoopPlaybackFocus : PlaybackFocus {
    override val claimState: StateFlow<FocusClaimState> =
        kotlinx.coroutines.flow.MutableStateFlow(FocusClaimState.Idle)

    override fun acquire(claimant: PlaybackSurfaceId): FocusOutcome = FocusOutcome.Granted

    override fun release(claimant: PlaybackSurfaceId) {}
}

/**
 * A playback surface the matrix may command. Slice 1 has exactly one adapter
 * (music); the desktop twin arrives with slice 2 — two production adapters
 * make this a real seam. [pause] MUST be a no-op when the surface is idle,
 * and MUST leave the surface unable to auto-resume (the playWhenReady guard
 * — the module's manual-resume decision is enforced at this command, so the
 * OS focus stack cannot resurrect a paused victim when the claim releases).
 */
interface PlaybackSurface {
    val id: PlaybackSurfaceId
    fun pause()
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
 * the adapter (PlayerAudioLifecycle stays engine-bound to video/live);
 * desktop slice 2: an in-process always-arbitrating twin; tests: a scripted
 * fake. Two adapters minimum, satisfied.
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
     * [FocusEvent.Regained] is IGNORED by the module (resume is manual,
     * pinned) — it exists so adapters can keep their OS bookkeeping honest.
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
 * Slice-1 rulings (newest-wins, ADR-0004):
 *  - READ_ALOUD claims → pause MUSIC (a no-op when music is idle; the
 *    surface's pause carries the playWhenReady guard).
 *  - MUSIC claims → no commandable victims (the reader pauses its own loop
 *    by observing the published Held state — the reader's claim is an
 *    observation, not a command target).
 *  - VIDEO claims → no slice-1 row: the executor DENIES (fail closed)
 *    instead of silently letting video overlap everything.
 */
internal object PlaybackFocusMatrix {

    /** Commandable surfaces to pause before the claim takes effect. */
    fun victimsOf(claim: PlaybackSurfaceId): List<PlaybackSurfaceId> = when (claim) {
        PlaybackSurfaceId.READ_ALOUD -> listOf(PlaybackSurfaceId.MUSIC)
        PlaybackSurfaceId.MUSIC -> emptyList()
        PlaybackSurfaceId.VIDEO -> emptyList()
    }

    /** Slice-1 closed world: which claims the executor may grant at all. */
    fun isGrantable(claim: PlaybackSurfaceId): Boolean = when (claim) {
        PlaybackSurfaceId.READ_ALOUD -> true
        PlaybackSurfaceId.MUSIC -> true
        PlaybackSurfaceId.VIDEO -> false
    }

    /**
     * OS-seat audio attributes per claimant (ADR-0004 slice-2 checklist: the
     * migration slice must not force music onto speech attributes or onto a
     * second OS request). READ_ALOUD keeps the slice-1 hardcoded pair
     * (USAGE_MEDIA + CONTENT_TYPE_SPEECH) — zero behavior change; the MUSIC
     * and VIDEO rows exist now so the exhaustive `when` forces a conscious
     * decision the day those claimants migrate onto this module's OS leg,
     * instead of silently inheriting speech attributes.
     */
    fun attributesOf(claim: PlaybackSurfaceId): FocusAudioAttributes = when (claim) {
        PlaybackSurfaceId.READ_ALOUD -> FocusAudioAttributes(FocusUsage.MEDIA, FocusContentType.SPEECH)
        PlaybackSurfaceId.MUSIC -> FocusAudioAttributes(FocusUsage.MEDIA, FocusContentType.MUSIC)
        PlaybackSurfaceId.VIDEO -> FocusAudioAttributes(FocusUsage.MEDIA, FocusContentType.MOVIE)
    }
}
