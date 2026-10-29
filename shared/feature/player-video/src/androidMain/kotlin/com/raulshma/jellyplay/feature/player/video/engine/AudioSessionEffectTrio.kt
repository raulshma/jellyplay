package com.raulshma.jellyplay.feature.player.video.engine

import androidx.media3.common.C
import com.raulshma.jellyplay.core.data.playback.DialogueBoostHelper
import com.raulshma.jellyplay.core.data.playback.EqualizerHelper
import com.raulshma.jellyplay.core.data.playback.NightModeHelper

/**
 * The engine-agnostic half of the [AudioEffectChain] split: the three
 * Android session-effect helpers (equalizer + dialogue boost + night mode)
 * as one attach/apply/release surface over an audio session id. Both shipped
 * video engines consume it — the Exo chain composes it alongside its Exo-only
 * in-sink processor half, and [MpvPlayerEngine] rides it alone (its
 * normalization/dialogue-boost rumble-cut DSP rides mpv `af` filters, a
 * genuinely different mechanism).
 *
 * This is the ONE home of the apply choreography and of the equalizer /
 * dialogue-boost co-enabling call (the `||` rule itself lives inside
 * [EqualizerHelper.setEnabled] — both flags are passed together here, so the
 * two hosts cannot re-derive it differently). Previously mpv hand-copied the
 * same attach/strength/enable ladder minus the chain's bookkeeping.
 *
 * Not a Hilt type: constructed directly by each engine over its own helper
 * instances (the underlying priority-0 `Equalizer` is a system-global
 * per-session effect, so both engines' helpers already share it by
 * construction).
 */
internal class AudioSessionEffectTrio(
    private val equalizerHelper: EqualizerHelper,
    private val dialogueBoost: DialogueBoostHelper,
    private val nightMode: NightModeHelper,
) {
    private var attachedSid: Int = C.AUDIO_SESSION_ID_UNSET

    /**
     * Binds the three helpers to session [sid]. Idempotent per sid — the
     * helpers' own attach is idempotent too, but the guard keeps a repeated
     * apply (mpv's per-config-change path) from churning the underlying
     * effects. A sid change rebinds (a fresh session's handles replace the
     * dead ones).
     */
    fun attach(sid: Int) {
        if (sid == attachedSid) return
        equalizerHelper.attach(sid)
        dialogueBoost.attach(sid)
        nightMode.attach(sid)
        attachedSid = sid
    }

    /**
     * Pushes [config]'s dialogue-boost / night-mode / equalizer slices onto
     * the attached session. Unconditional — the skip discipline is the
     * caller's (the Exo chain's config cache; mpv's config-delta gate).
     */
    fun apply(config: AudioEffectsConfig) {
        dialogueBoost.setStrength(config.dialogueBoostStrength)
        dialogueBoost.setEnabled(config.dialogueBoostEnabled)
        // dialogueBoost also toggles its rumble-cut high-pass internally.

        nightMode.setStrength(config.nightModeStrength)
        nightMode.setEnabled(config.nightModeEnabled)

        // equalizerHelper owns the single priority-0 Equalizer for this
        // session and DialogueBoostHelper overlays its vocal-band gains on
        // top. The co-enabling rule (on while EITHER is on) lives inside
        // [EqualizerHelper.setEnabled] — callers pass both flags.
        equalizerHelper.setSettings(config.equalizerSettings)
        equalizerHelper.setEnabled(
            equalizerEnabled = config.equalizerEnabled,
            dialogueBoostEnabled = config.dialogueBoostEnabled,
        )
    }

    /** Detaches the three helpers and forgets the session binding. */
    fun release() {
        dialogueBoost.detach()
        nightMode.detach()
        equalizerHelper.detach()
        attachedSid = C.AUDIO_SESSION_ID_UNSET
    }
}
