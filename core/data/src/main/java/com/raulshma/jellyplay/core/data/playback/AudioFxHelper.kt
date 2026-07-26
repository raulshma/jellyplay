package com.raulshma.jellyplay.core.data.playback

import android.util.Log
import androidx.media3.common.C

/**
 * Lifecycle skeleton for the thin `android.media.audiofx.*` wrappers
 * ([BassBoostHelper], [VirtualizerHelper], [LoudnessEnhancerHelper],
 * [ReverbHelper]).
 *
 * **Why this is deep.** Those four helpers each re-implemented the same
 * skeleton: a `private var fx`, a `currentAudioSessionId` guard, an
 * `isEnabled` flag, and the release-before-create / remember-session-id
 * dance inside `attach`. Deletion test on any one of them: deleting it
 * moves the skeleton, it doesn't concentrate it — the seam is real but
 * duplicated four times. This base owns the skeleton once; each subclass
 * supplies three template methods ([create], [applyEnabled], [releaseFx])
 * and keeps only its effect-specific state (strength / gain / preset).
 *
 * Subclass contract:
 *  - [create] opens the `audiofx` object for a session, applying whatever
 *    initial state the subclass tracks. Return null on failure (logged).
 *  - [applyEnabled] flips the underlying effect's enabled flag (default) AND
 *    re-applies any state that enabling requires (e.g. LoudnessEnhancer
 *    re-pushes its target gain when switched on). Override only when needed.
 *  - [releaseFx] tears down the underlying effect.
 *
 * Thread safety matches the originals — callers serialize lifecycle calls
 * from the audio/effects thread.
 */
abstract class AudioFxHelper<T : android.media.audiofx.AudioEffect>(protected val logTag: String) {

    protected var fx: T? = null
        protected set

    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    /** Whether [setEnabled] was last called with `true`. */
    var isEnabled: Boolean = false
        private set

    /** Subclass hook: open the underlying effect for [audioSessionId]. */
    protected abstract fun create(audioSessionId: Int): T?

    /**
     * Subclass hook: set the enabled flag on [effect] (and re-apply state if needed).
     *
     * Default: flip the underlying effect's `enabled` flag. Override only when
     * enabling must re-apply effect-specific state (e.g. [LoudnessEnhancerHelper]
     * re-pushes its target gain when switched on).
     */
    protected open fun applyEnabled(effect: T, enabled: Boolean) {
        effect.enabled = enabled
    }

    /** Subclass hook: release the underlying effect. Default delegates to [AudioEffect.release]. */
    protected open fun releaseFx(effect: T) {
        try {
            effect.release()
        } catch (_: Exception) {
            // best-effort
        }
    }

    /**
     * Open the effect for [audioSessionId]. No-op for `UNSET`; idempotent if
     * already attached to the same session. Releases any prior instance first
     * so we never hold two effects for overlapping sessions.
     */
    fun attach(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        if (audioSessionId == currentAudioSessionId && fx != null) return
        if (!shouldAttach()) return

        detach()
        currentAudioSessionId = audioSessionId

        fx = try {
            create(audioSessionId)?.also { applyEnabled(it, isEnabled) }
        } catch (e: Exception) {
            Log.w(logTag, "Failed to create audio effect", e)
            null
        }
    }

    /**
     * Subclass override point to skip attach when there is nothing to apply
     * (e.g. [ReverbHelper] skips when the preset is NONE). Default: always attach.
     */
    protected open fun shouldAttach(): Boolean = true

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        val current = fx ?: return
        try {
            applyEnabled(current, enabled)
        } catch (e: Exception) {
            Log.w(logTag, "Failed to set enabled=$enabled", e)
        }
    }

    fun detach() {
        fx?.let { releaseFx(it) }
        fx = null
        currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    /** Current attached session id, or `UNSET` when detached. Exposed for subclasses. */
    protected val attachedAudioSessionId: Int
        get() = currentAudioSessionId
}
