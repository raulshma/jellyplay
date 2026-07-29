package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.PlayerType

/**
 * Single source of truth for the [EngineCapabilities] published by every
 * [PlayerType] backend.
 *
 * **Why this exists.** Capabilities were previously declared in three places
 * that could silently drift apart:
 *  1. Each concrete engine's `override val capabilities = EngineCapabilities(...)`.
 *  2. The UI, which reads [MediaEngine.capabilities] to show/hide controls.
 *  3. `CrossPlayerTest`, which kept its own hand-typed copy of the matrix to
 *     assert per-engine feature support — and had already drifted (it claimed
 *     MPV did not support cues while `MpvPlayerEngine` actually does).
 *
 * Consolidating the values here means there is exactly one place to update when
 * an engine gains or loses a feature, and tests/UI/factory all read the same
 * constant. The concrete engines delegate to these constants, so the engine
 * runtime cannot diverge from the matrix by construction.
 *
 * **Behaviour contract (the "behaviour" half of the matrix).** When a flag here
 * is `false`, the corresponding engine must silently no-op the related call
 * (e.g. an engine with `supportsAudioDelay = false` ignores `audioDelayMs`).
 * This is what lets the UI branch purely on [EngineCapabilities] instead of
 * scattering `when (engine)` checks across the UI.
 *
 * Adding a new capability: add the field to [EngineCapabilities], set it
 * explicitly on every constant below (do not rely on the `false` default — an
 * explicit value documents intent and survives future default changes), and
 * extend [EngineCapabilityMatrixTest].
 */
object EngineCapabilityMatrix {

    /** Media3 / ExoPlayer backend. */
    val EXO_PLAYER: EngineCapabilities = EngineCapabilities(
        supportsPip = true,
        supportsMiniMode = true,
        supportsCues = true,
        supportsAudioDelay = false,
        supportsSubtitleDelay = true,
        supportsAudioPassthrough = false,
        supportsSubtitleStyle = true,
        supportsSubtitleVerticalPosition = true,
        supportsDialogueBoost = true,
        supportsNightMode = true,
        supportsAudioNormalization = true,
        supportsChannelMixing = true,
        supportsVideoFilters = false,
        supportsLiveQualitySwitch = true,
        supportsBandwidthEstimate = true,
        supportsAssOverride = true,
        // ExoPlayer renders ASS via libass but ass-media 0.4.0 exposes no
        // compile-time override API — ASS tracks render as-authored. User style
        // overrides (colors, borders, Force) apply to SRT/VTT only. Font SCALE
        // is honored separately via AssRender.setFontScale (see ExoPlayerEngine).
        supportsAssStyleOverride = false,
        supportsFontFamily = true,
        supportsFreeFormColors = true,
        supportsBorderStyles = true,
    )

    /** libmpv backend. */
    val MPV: EngineCapabilities = EngineCapabilities(
        supportsPip = true,
        supportsMiniMode = false,
        supportsCues = true,
        supportsAudioDelay = true,
        supportsSubtitleDelay = true,
        supportsAudioPassthrough = true,
        supportsSubtitleStyle = true,
        supportsSubtitleVerticalPosition = true,
        supportsDialogueBoost = true,
        supportsNightMode = true,
        supportsAudioNormalization = true,
        supportsChannelMixing = true,
        supportsVideoFilters = true,
        supportsLiveQualitySwitch = false,
        supportsBandwidthEstimate = false,
        supportsAssOverride = true,
        // mpv's libass supports `--ass-override=force`, so the user's colors,
        // borders, edges, and Force override DO apply to ASS/SSA tracks.
        supportsAssStyleOverride = true,
        supportsFontFamily = true,
        supportsFreeFormColors = true,
        supportsBorderStyles = true,
    )

    /** libVLC backend. */
    val LIBVLC: EngineCapabilities = EngineCapabilities(
        supportsPip = true,
        supportsMiniMode = false,
        supportsCues = false,
        supportsAudioDelay = true,
        supportsSubtitleDelay = true,
        supportsAudioPassthrough = true,
        supportsSubtitleStyle = true,
        // `supportsSubtitleVerticalPosition` is honoured in `Media.applySubtitleStyle`
        // via `:sub-margin`; surfacing the flag lets the SubtitleStyleSheet UI expose
        // the slider for VLC (parity with ExoPlayer/MPV) instead of hiding it.
        supportsSubtitleVerticalPosition = true,
        // The four flags below are Android-AudioEffect-based (DialogueBoostHelper /
        // NightModeHelper, with normalization/channel-mix driven by the in-sink
        // DSP processors). They bind to the engine's audio session id, but LibVLC
        // 3.7.x exposes no audio-session API (`audioSessionId` returns the 0/UNSET
        // sentinel), so the helpers short-circuit and these effects cannot be
        // applied at runtime. They can be configured only as LibVLC `--audio-filter`
        // startup options, so advertising them as supported here (and letting the UI
        // expose live toggles) misled users into thinking a mid-playback toggle
        // takes effect. Per the matrix behaviour contract, `false`
        // ⇒ the engine silently no-ops the related `updateConfig` call — which is exactly
        // what already happens. (Contrast ExoPlayer/MPV, which expose a real session id.)
        supportsDialogueBoost = false,
        supportsNightMode = false,
        supportsAudioNormalization = false,
        supportsChannelMixing = false,
        supportsVideoFilters = true,
        supportsLiveQualitySwitch = false,
        supportsBandwidthEstimate = false,
        supportsAssOverride = false,
        supportsAssStyleOverride = false,
        supportsFontFamily = true,
        supportsFreeFormColors = false,
        supportsBorderStyles = false,
    )

    /**
     * External playback is launched in a third-party app; the in-app
     * [NoOpEngine] performs no playback, so it advertises no capabilities.
     */
    val EXTERNAL: EngineCapabilities = EngineCapabilities(
        supportsPip = false,
        supportsMiniMode = false,
        supportsCues = false,
        supportsAudioDelay = false,
        supportsSubtitleDelay = false,
        supportsAudioPassthrough = false,
        supportsSubtitleStyle = false,
        supportsSubtitleVerticalPosition = false,
        supportsDialogueBoost = false,
        supportsNightMode = false,
        supportsAudioNormalization = false,
        supportsChannelMixing = false,
        supportsVideoFilters = false,
        supportsLiveQualitySwitch = false,
        supportsBandwidthEstimate = false,
        supportsAssOverride = false,
        supportsAssStyleOverride = false,
        supportsFontFamily = false,
        supportsFreeFormColors = false,
        supportsBorderStyles = false,
    )

    /** All declared matrices, keyed by [PlayerType]. Asserted total in tests. */
    val allByType: Map<PlayerType, EngineCapabilities> = mapOf(
        PlayerType.EXO_PLAYER to EXO_PLAYER,
        PlayerType.MPV to MPV,
        PlayerType.LIBVLC to LIBVLC,
        PlayerType.EXTERNAL to EXTERNAL,
    )

    /** Returns the canonical [EngineCapabilities] for [playerType]. */
    fun forType(playerType: PlayerType): EngineCapabilities = allByType.getValue(playerType)
}
