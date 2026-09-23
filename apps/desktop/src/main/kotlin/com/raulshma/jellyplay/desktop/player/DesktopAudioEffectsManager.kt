package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.data.playback.AudioEffectsSession
import com.raulshma.jellyplay.core.data.playback.AudioEffectsStateCore
import com.raulshma.jellyplay.feature.player.video.engine.AudioEffectsConfig

/**
 * Desktop half of the audio-effects manager — the mpv push only. The state
 * machine (every flow + strength + night-mode params + the per-track
 * ReplayGain computation, with the setter interplay the Android reference
 * copy defined) lives in the shared [AudioEffectsStateCore] (core:data
 * commonMain); this class extends it and overrides the single coarse
 * change hook: every state mutation re-folds [snapshotConfig] and the audio
 * core (`DesktopAudioQueueManager`, core:data jvmMain) pushes it onto the
 * engine's mpv `af` chain (on load via the [onEffectsChanged] callback
 * wired there, live on every change). The filter mapping lives in
 * [DesktopAudioEffectChain] (Android effect → mpv filter parity table
 * there).
 *
 * Also implements [AudioEffectsSession] — the narrow port the relocated
 * queue manager (core:data jvmMain) ctor-injects instead of this concrete
 * app-side type. The three port members are the historical surface,
 * widened from `internal` to `override` (public) for the cross-module
 * seam; same signatures, same bodies.
 *
 * Why the core still flips the flows (rather than freezing them): the audio
 * player's effects controller persists the manager's `StateFlow.value`
 * right after the apply leg — a frozen flow would write the PREVIOUS value
 * back to the store, silently undoing every toggle.
 *
 * Declared desktop divergences (state-level, encoded in the core):
 *  - out-of-range equalizer band indices no-op — the guard is now
 *    unconditional in the core (both hosts construct guarded; the historical
 *    Android IndexOutOfBoundsException is retired);
 *  - the visualizer stays fully inert — `fftData`/`waveformData` stay empty
 *    (mpv offers no in-sink PCM tap without a full render-API audio pull)
 *    and `enableVisualizer` neither notifies nor mutates.
 * Genuinely-desktop-only state: none survives — the private per-track
 * ReplayGain context turned out to be a mirror of the Android call-site
 * context and folded into the core as `setReplayGainContext`.
 */
class DesktopAudioEffectsManager : AudioEffectsStateCore(),
    AudioEffectsSession {

    override var onEffectsChanged: (() -> Unit)? = null

    override fun onEffectsStateChanged() {
        onEffectsChanged?.invoke()
    }

    /** Compatibility views over the core's night-mode params (the pre-core private fields). */
    internal val nightModeVolumeInternal: Float get() = nightModeVolume
    internal val nightModeGainInternal: Int get() = nightModeGain

    /**
     * Fold the whole state machine into the shared engine config. The queue
     * manager pushes this onto the engine via `updateConfig` — on engine
     * creation and after every mutation (the [onEffectsChanged] hook).
     */
    override fun snapshotConfig(): AudioEffectsConfig = AudioEffectsConfig(
        dialogueBoostEnabled = dialogueBoostEnabled.value,
        dialogueBoostStrength = dialogueBoostStrengthState,
        nightModeEnabled = nightModeEnabled.value,
        nightModeStrength = nightModeStrengthState,
        nightModeGain = nightModeGain,
        equalizerEnabled = equalizerEnabled.value,
        equalizerSettings = equalizerSettings.value,
        audioNormalizationMode = replayGainMode.value,
        audioNormalizationEnabled = replayGainNormalizationActive,
        channelMixMode = channelMixMode.value,
        channelMixEnabled = channelMixEnabled.value,
        bassBoostEnabled = bassBoostEnabled.value,
        bassBoostStrength = bassBoostStrengthState,
        virtualizerEnabled = virtualizerEnabled.value,
        virtualizerStrength = virtualizerStrength.value,
        reverbPreset = reverbPresetState.value,
        lrBalance = lrBalance.value,
        pitchSemitones = pitchSemitones.value,
        replayGainEffectiveDb = replayGainContextEffectiveDb(),
    )

    /**
     * Per-track ReplayGain context from the audio core — the desktop mirror
     * of the Android manager's `effectsProcessor.applyReplayGain(
     * item.normalizationGain, isShuffled)` at the same two sites (explicit
     * play + advance). Stores into the core and notifies so the snapshot
     * re-folds.
     */
    override fun applyReplayGainForTrack(trackGainDb: Float?, isShuffled: Boolean) =
        setReplayGainContext(trackGainDb, isShuffled)
}
