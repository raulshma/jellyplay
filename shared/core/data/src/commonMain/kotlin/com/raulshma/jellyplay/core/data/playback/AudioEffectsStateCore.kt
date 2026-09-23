package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.ReverbPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow

/**
 * Deep module for the shared audio-effects STATE half — the ONE owner of the
 * state machine the two platform managers used to duplicate nearly verbatim:
 * every [MutableStateFlow] with its initial value (the twins' initial values
 * were already identical — "AudioEffectsProcessor line-by-line"), the
 * non-flow strength / night-mode-param cells, the setter interplay (what
 * flips what, in the Android reference order), the night-mode param
 * computation, and the per-track ReplayGain computation. The Android
 * [AudioEffectsProcessor] and desktop `DesktopAudioEffectsManager` now only
 * carry their DSP halves and override the apply hooks below.
 *
 * ## The seam
 *
 * Every command flips state FIRST, then fires exactly one fine-grained hook.
 * Each fine-grained hook's DEFAULT is the coarse [onEffectsStateChanged]
 * funnel, so a snapshot-push platform (desktop) overrides ONLY the funnel
 * while a session-attach platform (Android) overrides the specific hooks
 * with its DSP writes. The halves re-expose the flows through inheritance —
 * same instances, so every consumer is unchanged.
 *
 * ## Declared divergences (encoded, never silently unified)
 *
 * (A FORMER member of this list is retired: the out-of-range
 * `setEqualizerBand` guard is unconditional now — desktop always no-oped
 * bad indices, Android's deliberate cleanup flipped its historical
 * IndexOutOfBoundsException to the same guarded no-op, and the
 * constructor flag is gone.)
 *
 *  - **Conditional vs unconditional reactions** — the core fires the
 *    strength/param hooks UNCONDITIONALLY; the Android half's overrides
 *    re-apply only while the effect is enabled (its historical `if`
 *    guards), the desktop funnel notifies on every mutation. The
 *    conditionality is platform behavior, so it lives in the halves.
 *  - **`setEqualizerPreset(CUSTOM)`** — Android historically pushes nothing
 *    to the DSP for CUSTOM (preset flip only); desktop always notifies.
 *    Encoded as [onEqualizerSettingsChanged]'s `levelsRewritten` flag.
 *  - **Interface-shim ReplayGain recompute context** — Android's
 *    `setReplayGainMode(mode)` / `setReplayGainPreAmpDb(db)` shims re-apply
 *    with a NULL track context (gain = pre-amp only); desktop re-folds the
 *    LAST STORED per-track context. Each half's hook override keeps its
 *    historical semantics.
 *  - **Visualizer** — [onVisualizerEnabledChanged] does NOT funnel (desktop
 *    has no PCM tap and must stay fully inert); Android routes it to the
 *    audio-session visualizer.
 *
 * ## Divergences that are NOT state (stay in the halves)
 *
 * Android attaches android.media.audiofx effects to the ExoPlayer audio
 * session (plus the media3 in-sink processors and `PlaybackParameters`
 * pitch push); desktop folds state into `AudioEffectsConfig` and pushes the
 * mpv `af` chain. Those are apply-hook differences and live in the halves.
 */
abstract class AudioEffectsStateCore : AudioEffectsManager {

    // ── Shared state cells: initial values identical on both platforms ─────

    protected val _nightModeEnabled = MutableStateFlow(false)
    override val nightModeEnabled: StateFlow<Boolean> = _nightModeEnabled.asStateFlow()

    protected val _dialogueBoostEnabled = MutableStateFlow(false)
    override val dialogueBoostEnabled: StateFlow<Boolean> = _dialogueBoostEnabled.asStateFlow()

    protected val _equalizerEnabled = MutableStateFlow(false)
    override val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    protected val _equalizerSettings = MutableStateFlow(EqualizerSettings())
    override val equalizerSettings: StateFlow<EqualizerSettings> = _equalizerSettings.asStateFlow()

    protected val _equalizerPreset = MutableStateFlow(EqualizerPreset.FLAT)
    override val equalizerPreset: StateFlow<EqualizerPreset> = _equalizerPreset.asStateFlow()

    protected val _bassBoostEnabled = MutableStateFlow(false)
    override val bassBoostEnabled: StateFlow<Boolean> = _bassBoostEnabled.asStateFlow()

    protected val _virtualizerEnabled = MutableStateFlow(false)
    override val virtualizerEnabled: StateFlow<Boolean> = _virtualizerEnabled.asStateFlow()

    protected val _virtualizerStrength = MutableStateFlow(500)
    override val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()

    protected val _reverbPreset = MutableStateFlow(ReverbPreset.NONE)
    override val reverbPresetState: StateFlow<ReverbPreset> = _reverbPreset.asStateFlow()

    protected val _lrBalance = MutableStateFlow(0f)
    override val lrBalance: StateFlow<Float> = _lrBalance.asStateFlow()

    protected val _pitchSemitones = MutableStateFlow(0f)
    override val pitchSemitones: StateFlow<Float> = _pitchSemitones.asStateFlow()

    protected val _autoEqByGenre = MutableStateFlow(false)
    override val autoEqByGenre: StateFlow<Boolean> = _autoEqByGenre.asStateFlow()

    protected val _replayGainMode = MutableStateFlow(AudioNormalizationMode.NONE)
    override val replayGainMode: StateFlow<AudioNormalizationMode> = _replayGainMode.asStateFlow()

    protected val _replayGainPreAmpDb = MutableStateFlow(0f)
    override val replayGainPreAmpDb: StateFlow<Float> = _replayGainPreAmpDb.asStateFlow()

    protected val _channelMixMode = MutableStateFlow(ChannelMixMode.AUTO)
    override val channelMixMode: StateFlow<ChannelMixMode> = _channelMixMode.asStateFlow()

    protected val _channelMixEnabled = MutableStateFlow(false)
    override val channelMixEnabled: StateFlow<Boolean> = _channelMixEnabled.asStateFlow()

    // ── Non-flow cells: strengths + night-mode params (no flow exists to
    // flip — the setters below are their only writers, so the value is
    // authoritative immediately after a set; mirrors the interface's
    // plain-val strength accessors). ────────────────────────────────────────

    protected var _bassBoostStrength = EffectStrength.MODERATE
    override val bassBoostStrengthState: EffectStrength get() = _bassBoostStrength

    protected var _dialogueBoostStrength = EffectStrength.MODERATE
    override val dialogueBoostStrengthState: EffectStrength get() = _dialogueBoostStrength

    protected var _nightModeStrength = EffectStrength.MODERATE
    override val nightModeStrengthState: EffectStrength get() = _nightModeStrength

    /**
     * Night-mode runtime params stored by [setNightModeParams]. Android kept
     * these as public fields (write-only there: `applyNightMode` reads the
     * strength-derived values below instead); desktop folds them into the
     * engine config snapshot. Public so the Android half's surface is
     * unchanged.
     */
    var nightModeVolume = 0.4f
    var nightModeGain = 1200

    /** Night-mode volume attenuation for the CURRENT strength (shared table). */
    val nightModeVolumeForStrength: Float
        get() = EffectStrengthMapping.nightModeVolumeAttenuation(_nightModeStrength)

    /** Night-mode loudness-enhancer gain (mB) for the CURRENT strength (shared table). */
    val nightModeGainForStrength: Int
        get() = EffectStrengthMapping.nightModeGainMb(_nightModeStrength)

    // ── Per-track ReplayGain context ────────────────────────────────────────

    /**
     * Last per-track ReplayGain context seen. Desktop feeds it via its
     * `applyReplayGainForTrack` (the queue manager's play/advance sites) and
     * re-folds it lazily in every snapshot; Android passes fresh context
     * through `applyReplayGain(trackGain, isShuffled)` at the same two sites,
     * so recording it here keeps the core's view coherent (inert on Android —
     * nothing reads it there).
     */
    protected var lastTrackGainDb: Float? = null
    protected var lastShuffled: Boolean = false

    /**
     * The shared per-track ReplayGain rule (the twins' "mirrors ... exactly"
     * comment, now one implementation): TRACK → `(trackGain ?: 0) + preAmp`;
     * ALBUM → the same EXCEPT a shuffled queue pins the gain at exactly 0
     * (checked BEFORE the pre-amp add); DYNAMIC/NONE → null (the compressor
     * stage / nothing runs).
     */
    fun effectiveReplayGainDb(trackGainDb: Float?, isShuffled: Boolean): Float? =
        when (_replayGainMode.value) {
            AudioNormalizationMode.TRACK -> (trackGainDb ?: 0f) + _replayGainPreAmpDb.value
            AudioNormalizationMode.ALBUM ->
                if (isShuffled) 0f else (trackGainDb ?: 0f) + _replayGainPreAmpDb.value
            AudioNormalizationMode.DYNAMIC, AudioNormalizationMode.NONE -> null
        }

    /** [effectiveReplayGainDb] over the STORED per-track context (desktop's snapshot read). */
    fun replayGainContextEffectiveDb(): Float? = effectiveReplayGainDb(lastTrackGainDb, lastShuffled)

    /** True while a normalization mode is active (desktop folds this as `audioNormalizationEnabled`). */
    val replayGainNormalizationActive: Boolean
        get() = _replayGainMode.value != AudioNormalizationMode.NONE

    /**
     * Pitch ratio `2^(semitones/12)`, unity at 0 — the exact multiplier the
     * Android media3 `PlaybackParameters` push and the desktop `pitch`
     * property both use.
     */
    val pitchMultiplier: Float
        get() = if (_pitchSemitones.value == 0f) 1.0f else 2.0f.pow(_pitchSemitones.value / 12.0f)

    // ── Visualizer: declared platform divergence ────────────────────────────

    /**
     * Desktop default: permanently empty. Android overrides both with the
     * audio-session visualizer helper's taps (same instances).
     */
    override val fftData: StateFlow<ByteArray> = MutableStateFlow(ByteArray(0)).asStateFlow()
    override val waveformData: StateFlow<ByteArray> = MutableStateFlow(ByteArray(0)).asStateFlow()

    // ── Apply-hook seam ─────────────────────────────────────────────────────
    //
    // Contract: the core has ALREADY flipped the state cells when the hook
    // runs; overrides read the current state (or their own platform context)
    // and perform their DSP writes.

    /**
     * Coarse change funnel — the DEFAULT reaction of every fine-grained hook
     * below (except [onVisualizerEnabledChanged], which deliberately stays
     * inert). Desktop overrides only this (→ re-push the engine snapshot);
     * Android leaves it empty and overrides the specific hooks instead.
     */
    protected open fun onEffectsStateChanged() {}

    protected open fun onNightModeEnabledChanged() = onEffectsStateChanged()

    /**
     * Fired UNCONDITIONALLY after the strength write. Named divergence:
     * Android's override re-applies only while night mode is enabled; the
     * desktop funnel notifies either way (its historical behavior).
     */
    protected open fun onNightModeStrengthChanged() = onEffectsStateChanged()

    /** Same unconditional contract as [onNightModeStrengthChanged]. */
    protected open fun onNightModeParamsChanged() = onEffectsStateChanged()

    protected open fun onDialogueBoostEnabledChanged() = onEffectsStateChanged()

    /**
     * Fired UNCONDITIONALLY after the strength write. Named divergence:
     * Android's override pushes the strength to its helper and re-applies
     * only while dialogue boost is enabled; the desktop funnel notifies
     * either way.
     */
    protected open fun onDialogueBoostStrengthChanged() = onEffectsStateChanged()

    protected open fun onEqualizerEnabledChanged() = onEffectsStateChanged()

    /**
     * Fired by [setEqualizerBand] / [resetEqualizer] (always with
     * `levelsRewritten = true`) and by [setEqualizerPreset]. The
     * `levelsRewritten` flag encodes the CUSTOM divergence: Android
     * historically pushed NOTHING to the DSP when the preset flip kept the
     * user's curve (`setEqualizerPreset(CUSTOM)`), while desktop still
     * notifies — so the Android override writes levels only when the flag is
     * set and the desktop funnel reacts regardless.
     */
    protected open fun onEqualizerSettingsChanged(levelsRewritten: Boolean) = onEffectsStateChanged()

    /**
     * Fired UNCONDITIONALLY after the mode write. Named divergence: the
     * Android override re-applies with a NULL track context (the historical
     * interface shim: gain = pre-amp only); the desktop funnel re-folds the
     * LAST STORED per-track context.
     */
    protected open fun onReplayGainModeChanged() = onEffectsStateChanged()

    /** Same null-vs-stored-context divergence as [onReplayGainModeChanged]. */
    protected open fun onReplayGainPreAmpDbChanged() = onEffectsStateChanged()

    /** Per-track context arrived ([setReplayGainContext]) — desktop re-folds its snapshot from it. */
    protected open fun onReplayGainContextChanged() = onEffectsStateChanged()

    protected open fun onChannelMixChanged() = onEffectsStateChanged()

    protected open fun onBassBoostEnabledChanged() = onEffectsStateChanged()

    /**
     * Fired UNCONDITIONALLY after the strength write. Named divergence:
     * Android's override pushes the strength to its `BassBoost` helper and
     * NOTHING else (no re-attach, no re-enable — its historical behavior);
     * the desktop funnel notifies.
     */
    protected open fun onBassBoostStrengthChanged() = onEffectsStateChanged()

    protected open fun onVirtualizerEnabledChanged() = onEffectsStateChanged()

    /**
     * Android's override pushes the strength to its `Virtualizer` helper and
     * NOTHING else (no attach, no re-enable); the desktop funnel notifies.
     */
    protected open fun onVirtualizerStrengthChanged() = onEffectsStateChanged()

    protected open fun onReverbPresetChanged() = onEffectsStateChanged()

    protected open fun onLrBalanceChanged() = onEffectsStateChanged()

    protected open fun onPitchSemitonesChanged() = onEffectsStateChanged()

    protected open fun onAutoEqByGenreChanged() = onEffectsStateChanged()

    /**
     * Deliberately does NOT funnel to [onEffectsStateChanged]: desktop has no
     * in-sink PCM tap, so its historical `enableVisualizer` is a FULL no-op
     * (no state, no notification). Android routes the flag to its
     * audio-session visualizer helper.
     */
    protected open fun onVisualizerEnabledChanged(enabled: Boolean) {}

    // ── Commands: state half only, Android reference order ──────────────────

    override fun toggleNightMode() {
        _nightModeEnabled.value = !_nightModeEnabled.value
        onNightModeEnabledChanged()
    }

    /**
     * Explicit night-mode enable. Historically Android-only (its
     * `EffectCommand` restore path); additive on desktop where it now also
     * notifies — no desktop code called it before.
     */
    fun setNightModeEnabled(enabled: Boolean) {
        _nightModeEnabled.value = enabled
        onNightModeEnabledChanged()
    }

    override fun toggleDialogueBoost() {
        _dialogueBoostEnabled.value = !_dialogueBoostEnabled.value
        onDialogueBoostEnabledChanged()
    }

    /** Explicit dialogue-boost enable — see [setNightModeEnabled]. */
    fun setDialogueBoostEnabled(enabled: Boolean) {
        _dialogueBoostEnabled.value = enabled
        onDialogueBoostEnabledChanged()
    }

    override fun toggleEqualizer() {
        _equalizerEnabled.value = !_equalizerEnabled.value
        onEqualizerEnabledChanged()
    }

    /** Explicit equalizer enable — see [setNightModeEnabled]. */
    fun setEqualizerEnabled(enabled: Boolean) {
        _equalizerEnabled.value = enabled
        onEqualizerEnabledChanged()
    }

    override fun toggleBassBoost() {
        _bassBoostEnabled.value = !_bassBoostEnabled.value
        onBassBoostEnabledChanged()
    }

    /** Explicit bass-boost enable — see [setNightModeEnabled]. */
    fun setBassBoostEnabled(enabled: Boolean) {
        _bassBoostEnabled.value = enabled
        onBassBoostEnabledChanged()
    }

    override fun toggleVirtualizer() {
        _virtualizerEnabled.value = !_virtualizerEnabled.value
        onVirtualizerEnabledChanged()
    }

    /** Explicit virtualizer enable — see [setNightModeEnabled]. */
    fun setVirtualizerEnabled(enabled: Boolean) {
        _virtualizerEnabled.value = enabled
        onVirtualizerEnabledChanged()
    }

    override fun setDialogueBoostStrength(strength: EffectStrength) {
        _dialogueBoostStrength = strength
        onDialogueBoostStrengthChanged()
    }

    override fun setNightModeStrength(strength: EffectStrength) {
        _nightModeStrength = strength
        onNightModeStrengthChanged()
    }

    override fun setBassBoostStrength(strength: EffectStrength) {
        _bassBoostStrength = strength
        onBassBoostStrengthChanged()
    }

    override fun setVirtualizerStrength(strength: Int) {
        _virtualizerStrength.value = strength
        onVirtualizerStrengthChanged()
    }

    override fun setEqualizerBand(bandIndex: Int, levelDb: Int) {
        val newLevels = _equalizerSettings.value.bandLevels.toMutableList()
        // The guard is UNCONDITIONAL (the former declared divergence is
        // retired — see the class KDoc): a bad index is a full no-op, no
        // flip and no hook; the Android half's historical unguarded write
        // threw until its deliberate cleanup adopted this guarded behavior.
        if (bandIndex !in newLevels.indices) return
        newLevels[bandIndex] = levelDb
        _equalizerSettings.value = EqualizerSettings(newLevels)
        _equalizerPreset.value = EqualizerPreset.CUSTOM
        onEqualizerSettingsChanged(levelsRewritten = true)
    }

    override fun resetEqualizer() {
        _equalizerSettings.value = EqualizerSettings()
        _equalizerPreset.value = EqualizerPreset.FLAT
        onEqualizerSettingsChanged(levelsRewritten = true)
    }

    override fun setEqualizerPreset(preset: EqualizerPreset) {
        _equalizerPreset.value = preset
        val levelsRewritten = preset != EqualizerPreset.CUSTOM
        if (levelsRewritten) {
            _equalizerSettings.value = EqualizerSettings(preset.bandLevels())
        }
        onEqualizerSettingsChanged(levelsRewritten)
    }

    override fun setNightModeParams(volume: Float, gain: Int) {
        nightModeVolume = volume
        nightModeGain = gain
        onNightModeParamsChanged()
    }

    override fun setReplayGainMode(mode: AudioNormalizationMode) {
        _replayGainMode.value = mode
        onReplayGainModeChanged()
    }

    override fun setReplayGainPreAmpDb(db: Float) {
        _replayGainPreAmpDb.value = db
        onReplayGainPreAmpDbChanged()
    }

    /**
     * Per-track ReplayGain context from the audio core (the queue manager's
     * play/advance sites; Android's `applyReplayGain` funnels through here
     * too). Stores the context and fires [onReplayGainContextChanged] —
     * desktop re-folds its snapshot from the stored values.
     */
    fun setReplayGainContext(trackGainDb: Float?, isShuffled: Boolean) {
        lastTrackGainDb = trackGainDb
        lastShuffled = isShuffled
        onReplayGainContextChanged()
    }

    override fun setChannelMix(mode: ChannelMixMode, enabled: Boolean) {
        _channelMixMode.value = mode
        _channelMixEnabled.value = enabled
        onChannelMixChanged()
    }

    override fun setReverbPreset(preset: ReverbPreset) {
        _reverbPreset.value = preset
        onReverbPresetChanged()
    }

    override fun setLrBalance(balance: Float) {
        _lrBalance.value = balance
        onLrBalanceChanged()
    }

    /** Shared state flip for the interface shim and the speed-aware overloads. */
    protected fun setPitchSemitonesState(semitones: Float) {
        _pitchSemitones.value = semitones
    }

    override fun setPitchSemitones(semitones: Float) {
        setPitchSemitonesState(semitones)
        onPitchSemitonesChanged()
    }

    override fun setAutoEqByGenre(enabled: Boolean) {
        _autoEqByGenre.value = enabled
        onAutoEqByGenreChanged()
    }

    /**
     * Resolve the first genre-matched preset onto the equalizer — no-op
     * unless the auto flag is on, nothing matches, or the resolved preset
     * already IS the active one (identical in both twins; routes through
     * [setEqualizerPreset], so the platform hooks fire as usual).
     */
    override fun applyAutoEqForGenre(genres: List<String>?) {
        if (!_autoEqByGenre.value) return
        if (genres.isNullOrEmpty()) return
        val matchedPreset = genres.firstNotNullOfOrNull { genre ->
            EqualizerPreset.fromGenre(genre)
        } ?: return
        if (matchedPreset != _equalizerPreset.value) {
            setEqualizerPreset(matchedPreset)
        }
    }

    override fun enableVisualizer(enabled: Boolean) {
        // No funnel — see [onVisualizerEnabledChanged].
        onVisualizerEnabledChanged(enabled)
    }
}
