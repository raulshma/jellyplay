package com.raulshma.jellyplay.feature.player.video.engine

/**
 * Pure slice-diff between two [EngineConfig] values — the DECISION half of the
 * mpv adapters' `onConfigChanged` reconfiguration: which config slices moved,
 * never what to write. [of] is total (every pair of configs yields a delta),
 * and both mpv hosts — Android's `MpvPlayerEngine` and the desktop
 * `MpvDesktopEngine` — switch on the returned flags and apply their own native
 * writes, keeping each host's `lastApplied*` diff caches and dispatch
 * threading exactly as they were.
 *
 * Converged behaviour (the two hand-mirrored ladders had drifted):
 *  - The shared-pairs re-diff ([sharedPairsChanged]) fires on ANY
 *    `engineSpecific` change, including a change TO null. The desktop's
 *    former `newConfig.engineSpecific != null` guard skipped the re-diff on
 *    that edge; that was accidental — the mapping treats a null slice as its
 *    defaults, exactly what Android's ladder (which never had the guard)
 *    already did.
 *  - The desktop's former engineSpecific-triggered FULL re-apply — which
 *    unconditionally re-wrote the delay/hwdec/subtitle-style values even when
 *    those slices had not moved — narrows to the per-slice flags below. Its
 *    diff caches already made those re-writes no-ops, so native state is
 *    unchanged; only the redundant property writes disappear (Android's
 *    per-slice discipline).
 *
 * Deliberately not covered: `pauseOnAudioFocusLoss` and
 * `drmSessionManagerProvider` — no engine's `onConfigChanged` consumes them.
 */
data class EngineConfigDelta(
    /** `audioDelayMs` moved — the mpv `audio-delay` write. */
    val audioDelayChanged: Boolean,
    /** `subtitleDelayMs` moved — the mpv `sub-delay` write. */
    val subtitleDelayChanged: Boolean,
    /**
     * `decoderMode` moved — the hwdec mapping write. Adapters may additionally
     * OR in their own `engineSpecific` sub-diffs (Android's `hwdecOverride`
     * participates in the same write; the desktop's `hwdecFor` is decoder
     * mode-only).
     */
    val decoderModeChanged: Boolean,
    /** `subtitleStyle` moved — the full subtitle-style application. */
    val subtitleStyleChanged: Boolean,
    /** The `engineSpecific` slice moved as a whole (value-blind equality). */
    val engineSpecificChanged: Boolean,
    /**
     * The shared mpv pair re-diff group: `engineSpecific`, `audioPassthrough`,
     * `deinterlace` or `hdrSource` moved. The passthrough/deinterlace/HDR
     * inputs ride the SHARED [EngineConfig] on both hosts — the spdif list is
     * composed from passthrough, the session-scoped deinterlace cycle rides
     * the base config, and an HDR-source change flips the tone-mapping
     * suppression gate — so any of them must reach the pair diff even when
     * the engine-specific slice did not move.
     */
    val sharedPairsChanged: Boolean,
    /**
     * The whole [AudioEffectsConfig] slice moved. The desktop re-derives its
     * `af`/`audio-channels`/`pitch` writes from the slice through diff caches;
     * Android consumes the three narrower groups below instead.
     */
    val audioEffectsChanged: Boolean,
    /** `channelMixMode`/`channelMixEnabled` moved — Android's `audio-channels` fx half. */
    val channelMixChanged: Boolean,
    /**
     * Normalization (enabled/mode) or dialogue-boost ENABLED moved — Android's
     * mpv `af` chain rebuild gate (dialogue boost contributes a highpass stage
     * to the chain).
     */
    val audioAfChainChanged: Boolean,
    /**
     * The AudioEffect session group moved — dialogue-boost strength/enabled,
     * night-mode strength/enabled, or equalizer enabled (Android's AudioEffect
     * re-apply; no mpv property consumes it).
     */
    val audioSessionEffectsChanged: Boolean,
    /** The whole `com.raulshma.jellyplay.core.model.VideoEffectsConfig` slice moved. */
    val videoEffectsChanged: Boolean,
) {
    companion object {
        /** The identity for equal configs — every flag false. */
        val NONE = EngineConfigDelta(
            audioDelayChanged = false,
            subtitleDelayChanged = false,
            decoderModeChanged = false,
            subtitleStyleChanged = false,
            engineSpecificChanged = false,
            sharedPairsChanged = false,
            audioEffectsChanged = false,
            channelMixChanged = false,
            audioAfChainChanged = false,
            audioSessionEffectsChanged = false,
            videoEffectsChanged = false,
        )

        /** The total slice-diff: which of the [EngineConfig] slices above moved. */
        fun of(old: EngineConfig, new: EngineConfig): EngineConfigDelta {
            val oldAudioFx = old.audioEffects
            val newAudioFx = new.audioEffects
            val engineSpecificChanged = old.engineSpecific != new.engineSpecific
            return EngineConfigDelta(
                audioDelayChanged = old.audioDelayMs != new.audioDelayMs,
                subtitleDelayChanged = old.subtitleDelayMs != new.subtitleDelayMs,
                decoderModeChanged = old.decoderMode != new.decoderMode,
                subtitleStyleChanged = old.subtitleStyle != new.subtitleStyle,
                engineSpecificChanged = engineSpecificChanged,
                sharedPairsChanged = engineSpecificChanged ||
                    old.audioPassthrough != new.audioPassthrough ||
                    old.deinterlace != new.deinterlace ||
                    old.hdrSource != new.hdrSource,
                audioEffectsChanged = old.audioEffects != new.audioEffects,
                channelMixChanged = oldAudioFx.channelMixMode != newAudioFx.channelMixMode ||
                    oldAudioFx.channelMixEnabled != newAudioFx.channelMixEnabled,
                audioAfChainChanged = oldAudioFx.audioNormalizationEnabled != newAudioFx.audioNormalizationEnabled ||
                    oldAudioFx.audioNormalizationMode != newAudioFx.audioNormalizationMode ||
                    oldAudioFx.dialogueBoostEnabled != newAudioFx.dialogueBoostEnabled,
                audioSessionEffectsChanged = oldAudioFx.dialogueBoostStrength != newAudioFx.dialogueBoostStrength ||
                    oldAudioFx.dialogueBoostEnabled != newAudioFx.dialogueBoostEnabled ||
                    oldAudioFx.nightModeStrength != newAudioFx.nightModeStrength ||
                    oldAudioFx.nightModeEnabled != newAudioFx.nightModeEnabled ||
                    oldAudioFx.equalizerEnabled != newAudioFx.equalizerEnabled,
                videoEffectsChanged = old.videoEffects != new.videoEffects,
            )
        }
    }
}
