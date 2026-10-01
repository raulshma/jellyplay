package com.raulshma.jellyplay.core.data.playback.focus

/**
 * The engine-facing command target the video-family focus surface drives —
 * the legacy `PlayerAudioLifecycle.PlaybackControl` shape, carried over
 * verbatim when the video slice deleted that class (the duck/restore
 * round-trip's contract rode exactly these members). Each screen builds one
 * over its CURRENT engine/player with lambda-backed fields, so every
 * callback re-reads the live target — the VOD wiring swaps engines on
 * retry/fallback and the live VM nulls its player on stop.
 *
 * [setVolume]'s [isUserChange] is always driven `false` from the duck path:
 * ducking is not a user volume choice, and engines with per-content-type
 * volume memory must never capture it.
 */
interface FocusCommandTarget {
    /** True if the target is currently producing audio. */
    val isPlaying: Boolean

    /** Current volume, in the target's native range (unclamped). */
    val volume: Float

    /** Whether the target is user-muted (read on duck/restore — see below). */
    val isMuted: Boolean

    fun pause()

    /** Re-assert the muted state (the duck-while-muted guard on restore). */
    fun setMuted(muted: Boolean)

    /** Set the native-range volume; the Boolean is the user-vs-programmatic flag. */
    fun setVolume(volume: Float, isUserChange: Boolean)
}

/**
 * The VIDEO-family commandable surface (one instance in the focus module's
 * surface list; the VOD wiring and the live player both bind their current
 * engine/player into it — the screens never coexist, so the last binding is
 * the live one, and each host unbinds on its teardown).
 *
 * The duck/restore mechanics are the deleted `PlayerAudioLifecycle`'s
 * transient-loss listener body, moved with the behavior:
 *  - [duck] skips volume capture while muted (the player volume is 0f while
 *    muted — capturing it would clobber the real level on restore) and
 *    leaves a muted target at 0f: ducking must never make muted audio
 *    audible during a phone call. Mute is re-asserted on restore instead.
 *  - [restore] restores the pre-duck volume programmatically (or re-asserts
 *    mute), then fires the bound [bind]'s `onRestore` hook — the VOD
 *    wiring's `videoSkipBackOnResumeMs` resume-skip rides exactly there,
 *    where the legacy focus-regain hook used to.
 *  - [pause] restores the pre-duck level first (legacy left a ducked engine
 *    ducked across a pause — the user's resume then played at the duck
 *    level; the round-trip is closed here instead) before the pause command.
 *
 * Unlike the legacy class this surface owns NO focus request: the seat
 * belongs to the focus module now. It is main-confined (the executor and
 * the Android arbiter's marshalled events are); the duck bookkeeping is a
 * plain var for the same reason. The binding fields are `@Volatile` for a
 * different, weaker guarantee: [bind]/[unbind] ride screen lifecycle paths
 * (the live engine's Media3 callbacks among them) whose dispatcher the
 * focus module doesn't confine, so a visibility floor is cheaper than
 * proving confinement.
 */
class VideoPlaybackSurface : PlaybackSurface {

    @Volatile private var targetProvider: (() -> FocusCommandTarget?)? = null
    @Volatile private var onRestore: (() -> Unit)? = null

    private var preDuckVolume: Float? = null

    /**
     * Bind the current screen's target provider and restore hook. Re-binding
     * replaces both (the previous screen's teardown [unbind]s; a stray
     * late command from a bound-but-idle provider is a no-op through the
     * null-target guard).
     */
    fun bind(target: () -> FocusCommandTarget?, onRestore: (() -> Unit)? = null) {
        this.targetProvider = target
        this.onRestore = onRestore
    }

    /** Drop the binding (screen teardown — commands become no-ops after this). */
    fun unbind() {
        targetProvider = null
        onRestore = null
    }

    override val id: PlaybackSurfaceId = PlaybackSurfaceId.VIDEO

    override fun pause() {
        val target = targetProvider?.invoke() ?: return
        preDuckVolume?.takeIf { !target.isMuted }?.let { target.setVolume(it, false) }
        preDuckVolume = null
        target.pause()
    }

    override fun duck(volume: Float) {
        val target = targetProvider?.invoke() ?: return
        if (target.isMuted) return
        if (preDuckVolume == null) preDuckVolume = target.volume
        target.setVolume(volume, false)
    }

    override fun restore() {
        val target = targetProvider?.invoke() ?: return
        if (target.isMuted) {
            target.setMuted(true)
        } else {
            preDuckVolume?.let { target.setVolume(it, false) }
        }
        preDuckVolume = null
        onRestore?.invoke()
    }
}
