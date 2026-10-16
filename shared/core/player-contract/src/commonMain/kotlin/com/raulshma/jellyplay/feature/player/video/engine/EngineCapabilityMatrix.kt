package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.PlayerType

/**
 * Single source of truth for the [EngineCapabilities] published by every
 * [PlayerType] backend.
 *
 * Moved verbatim from :feature:player:video's engine package into this
 * module's commonMain (SAME package — RemotePlayableEngine/V2a move pattern)
 * with the subtitle-tester conveyor: shared feature modules' commonMain
 * (e.g. SubtitleTesterUiState) needs the matrix, and legacy player:video
 * recompiles unchanged through :feature:player:core's api() re-export of
 * this module.
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
        // FORCE override rides the ASS header rewrite (AssStyleOverrideParserFactory
        // rewrites `Style: Default` before libass parses the track — ass-media
        // 0.5.0 binds no style-override JNI). Apply-at-load: the rewritten header
        // takes effect when the subtitle track next parses, not live like mpv.
        // Scope (the C3 spec's "font/color/position" ask): covers
        // font/colors/border/size but NOT authored ASS positioning
        // (alignment/margins — rewriting them would break signage/karaoke
        // event positioning; the user's vertical position keeps applying via
        // the overlay host), and only the `Style: Default` line (first style
        // line as fallback; sign/song styles keep their design).
        supportsAssStyleOverride = true,
        supportsFontFamily = true,
        supportsFreeFormColors = true,
        supportsBorderStyles = true,
        supportsSecondarySubtitles = false,
        supportsScreenshot = true,
        // Media3 parses embedded APPLICATION_PGS bitmaps inside a container but
        // cannot reliably decode an external .sup sidecar file.
        supportsImageSubtitles = false,
        // Media3 has no deinterlace surface; interlaced content relies on the
        // decoder/render defaults.
        supportsDeinterlace = false,
        // The only engine whose sink delivers AudioTrack-qualifying multichannel
        // PCM the platform spatializer (API 32+) can engage. Runtime-gated: see
        // [EngineCapabilities.supportsSpatialAudio].
        supportsSpatialAudio = true,
        // No HDR passthrough switch — Media3 outputs whatever the display
        // pipeline accepts. Its HDR surface is the GL tone-mapping path.
        supportsHdrPassthrough = false,
        // HDR→SDR tone mapping via the Media3 GL video-graph path
        // (`resolveExoHdrOutput` decides engagement; the SDR-output branch of
        // DefaultVideoFrameProcessor applies the map). Not offered for DRM
        // sessions (`setVideoEffects` limitation).
        supportsHdrToneMapping = true,
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
        // mpv `deinterlace` is a runtime-settable property on both
        // mpv adapters (Android + desktop).
        supportsDeinterlace = true,
        // mpv's zoom-safe subtitle strategy (COMPOSE_CUE, via
        // [MediaEngine.zoomSafeSubtitleStrategy]) is declared on the engine, not
        // as a capability flag here.
        // mpv renders through its own audio output (ao); no observable platform
        // Spatializer surface and its own passthrough path.
        supportsSpatialAudio = false,
        // HDR passthrough on BOTH hosts now: the desktop's HWND-embed
        // `target-colorspace-hint` path and Android's SurfaceView `vo=gpu-next`
        // twin (evaluated against the shared `video-target-params` probe).
        supportsHdrPassthrough = true,
        // No in-engine HDR→SDR dial beyond `MpvEngineConfig.toneMapping` — mpv
        // tone maps by default whenever the target is SDR, so the ExoPlayer
        // engagement flag has no analogue to advertise.
        supportsHdrToneMapping = false,
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
        // libVLC exposes no per-session deinterlace toggle through this
        // contract (its video options are media-construction-time).
        supportsDeinterlace = false,
        // libVLC 3.7.x composites subs into a native surface with no text/event
        // callback, so no zoom-safe path is available (strategy = DISABLED on
        // the engine). Only unblocked by a libvlc 4.x aar, a JNI fork, or
        // app-side subtitle demux — all out of scope.
        // No spatializer surface: libVLC owns its AudioTrack directly with no
        // observable android.media.Spatializer hook.
        supportsSpatialAudio = false,
        // No HDR switch and no tone-mapping surface: libVLC 3.7.x renders HDR
        // content through whatever the display pipeline does with it.
        supportsHdrPassthrough = false,
        supportsHdrToneMapping = false,
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
        supportsDeinterlace = false,
        supportsSpatialAudio = false,
        supportsHdrPassthrough = false,
        supportsHdrToneMapping = false,
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
