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
        supportsAssStyleOverride = false,
        supportsFontFamily = true,
        supportsFreeFormColors = true,
        supportsBorderStyles = true,
        supportsSecondarySubtitles = false,
        supportsScreenshot = true,
        // Media3 parses embedded APPLICATION_PGS bitmaps inside a container but
        // cannot reliably decode an external .sup sidecar file.
        supportsImageSubtitles = false,
        // Zoom-safe subtitle strategy is declared per-engine via
        // [MediaEngine.zoomSafeSubtitleStrategy] (ExoPlayer = NATIVE_PINNED),
        // not as a capability flag here.
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
        supportsAssStyleOverride = true,
        supportsFontFamily = true,
        supportsFreeFormColors = true,
        supportsBorderStyles = true,
        supportsSecondarySubtitles = true,
        supportsScreenshot = true,
        // libav decodes bitmap subtitles (PGS/VOBSUB/DVB) from content — mime
        // is irrelevant to sub-add, so offline .sup sidecars just work.
        supportsImageSubtitles = true,
        // mpv's zoom-safe subtitle strategy (COMPOSE_CUE, via
        // [MediaEngine.zoomSafeSubtitleStrategy]) is declared on the engine, not
        // as a capability flag here.
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
        supportsSubtitleVerticalPosition = true,
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
        supportsSecondarySubtitles = false,
        supportsScreenshot = true,
        // No bitmap-subtitle decoder at all.
        supportsImageSubtitles = false,
        // libVLC 3.7.x composites subs into a native surface with no text/event
        // callback, so no zoom-safe path is available (strategy = DISABLED on
        // the engine). Only unblocked by a libvlc 4.x aar, a JNI fork, or
        // app-side subtitle demux — all out of scope.
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
        // Never decodes anything.
        supportsImageSubtitles = false,
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
