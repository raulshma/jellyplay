package com.raulshma.jellyplay.feature.player.video.engine

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.ExoHdrToneMappingMode
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType

/**
 * The HDR transfer family of a video stream — the coarse classification every
 * HDR output decision below answers against. Derived from the Jellyfin
 * `MediaStream` metadata (`videoRange`/`videoRangeType`/`videoDoViTitle`) by
 * [hdrContentFromStreams]; the engine-side per-format derivation (ExoPlayer
 * stats) produces the same names from the selected track's color metadata.
 *
 * HDR10+ is kept distinct from HDR10 because Android reports the two as
 * separate `Display.HdrCapabilities` types — but a display that supports the
 * plain HDR10 type renders HDR10+ content correctly at its baseline layer, so
 * [DisplayHdrSupport.supportsContent] treats the pair as mutually compatible.
 */
@Immutable
enum class HdrContentInfo(val displayName: String) {
    NONE("SDR"),
    HDR10("HDR10"),
    HDR10_PLUS("HDR10+"),
    HLG("HLG"),
    DOLBY_VISION("Dolby Vision"),
    ;

    /** `true` for every HDR family — the Boolean twin `EngineConfig.hdrSource` carries. */
    val isHdr: Boolean get() = this != NONE

    companion object {
        /**
         * The Android `Display.HdrCapabilities.HDR_TYPE_*` constants by value —
         * replicated (not imported) so this mapping stays commonMain and
         * JVM-testable (the platform probe injects this mapping and keeps its
         * own SDK guards).
         */
        const val ANDROID_HDR_TYPE_DOLBY_VISION = 1
        const val ANDROID_HDR_TYPE_HDR10 = 2
        const val ANDROID_HDR_TYPE_HDR10_PLUS = 3
        const val ANDROID_HDR_TYPE_HLG = 4

        /** The Android display-HDR type int → its [HdrContentInfo], `null` for unknown types. */
        fun fromAndroidHdrType(type: Int): HdrContentInfo? = when (type) {
            ANDROID_HDR_TYPE_DOLBY_VISION -> DOLBY_VISION
            ANDROID_HDR_TYPE_HDR10 -> HDR10
            ANDROID_HDR_TYPE_HDR10_PLUS -> HDR10_PLUS
            ANDROID_HDR_TYPE_HLG -> HLG
            else -> null
        }
    }
}

/**
 * The CURRENT item's HDR classification, folded into [EngineConfig.hdrContent]
 * beside the Boolean [EngineConfig.hdrSource] (`hdrSource = hdrContent != NONE`
 * always). The derivation order encodes specificity: Dolby Vision first (its
 * `DolbyVisionWithHDR10` range-type strings mention HDR10 — DV wins as the
 * delivery family that actually decides decoder/display behavior), then the
 * HDR10+ / HDR10 pair (the `hdr10plus`/`hdr10+` spellings must match before
 * the bare `hdr10`), then HLG, then the bare `hdr` catch-all (the legacy
 * `videoRange=HDR` and the local probe's `videoRangeType=HDR` — both mean
 * "HDR, static-info family").
 */
fun hdrContentFromStreams(streams: List<MediaStream>): HdrContentInfo =
    streams.firstOrNull { it.type == StreamType.VIDEO }
        ?.let(::hdrContentOfStream)
        ?: HdrContentInfo.NONE

/** Single-stream classification for [hdrContentFromStreams]. */
private fun hdrContentOfStream(stream: MediaStream): HdrContentInfo {
    val range = stream.videoRange?.lowercase().orEmpty()
    val rangeType = stream.videoRangeType?.lowercase().orEmpty()
    val combined = "$range $rangeType"
    return when {
        stream.videoDoViTitle != null ||
            combined.contains("dovi") ||
            combined.contains("dolbyvision") -> HdrContentInfo.DOLBY_VISION
        combined.contains("hdr10plus") || combined.contains("hdr10+") -> HdrContentInfo.HDR10_PLUS
        combined.contains("hdr10") -> HdrContentInfo.HDR10
        combined.contains("hlg") -> HdrContentInfo.HLG
        combined.contains("hdr") -> HdrContentInfo.HDR10
        else -> HdrContentInfo.NONE
    }
}

/**
 * The display's HDR output capability — the set of [HdrContentInfo] families
 * the CURRENT display accepts. Empty set = SDR display. Sourced per engine:
 * ExoPlayer reads the Android `Display.HdrCapabilities` types (via the
 * androidMain probe); the mpv engines answer from mpv's own
 * `video-target-params` probe instead, so they never build this value.
 */
@Immutable
data class DisplayHdrSupport(val supportedTypes: Set<HdrContentInfo>) {

    /** `true` when the display accepts at least one HDR family. */
    val isHdrDisplay: Boolean get() = supportedTypes.isNotEmpty()

    /**
     * Whether [content] renders as HDR on this display. The compat folds:
     * HDR10+ content plays on a plain HDR10 display at its HDR10 baseline (and
     * a DV display accepts everything HDR10-shaped); HLG and Dolby Vision need
     * their own declared types (DV profile 5 has no fallback layer — on a
     * non-DV display its IPTPQc2 frames decode to garbage colors, the classic
     * purple/green tint).
     */
    fun supportsContent(content: HdrContentInfo): Boolean = when (content) {
        HdrContentInfo.NONE -> true
        HdrContentInfo.HDR10 -> HdrContentInfo.HDR10 in supportedTypes ||
            HdrContentInfo.HDR10_PLUS in supportedTypes ||
            HdrContentInfo.DOLBY_VISION in supportedTypes
        HdrContentInfo.HDR10_PLUS -> HdrContentInfo.HDR10_PLUS in supportedTypes ||
            HdrContentInfo.HDR10 in supportedTypes
        HdrContentInfo.HLG -> HdrContentInfo.HLG in supportedTypes
        HdrContentInfo.DOLBY_VISION -> HdrContentInfo.DOLBY_VISION in supportedTypes
    }

    companion object {
        /** An SDR (no-HDR-types) display. */
        val SDR = DisplayHdrSupport(emptySet())

        /** Maps the Android `supportedHdrTypes` int array; unknown type ints are ignored. */
        fun fromAndroidHdrTypes(types: IntArray): DisplayHdrSupport = DisplayHdrSupport(
            types.map { HdrContentInfo.fromAndroidHdrType(it) }.filterNotNull().toSet(),
        )
    }
}

/**
 * The resolved HDR output state an engine should be in for the current
 * (content × display × setting) triple. The three flags are mutually
 * exclusive by construction of the resolvers; [notice] is the stats-overlay
 * diagnostic line (plain text — the desktop engine's stats notice precedent;
 * engines compose the SAME string constants declared here so tests and both
 * hosts stay in agreement).
 */
@Immutable
data class HdrOutputDecision(
    /** The engine is expected to output the content's HDR transfer natively. */
    val hdrOutputExpected: Boolean = false,
    /** The engine converts HDR → SDR in its render pipeline (mpv `tone-mapping`, Media3 GL path). */
    val toneMappingActive: Boolean = false,
    /** HDR content with no tone-mapping path — washed-out/incorrect colors are expected. */
    val unhandledHdr: Boolean = false,
    /** The one-line stats diagnostic, `null` = nothing to report. */
    val notice: String? = null,
) {
    companion object {
        /** SDR content, or a decision not yet worth reporting. */
        val NONE = HdrOutputDecision()

        /** Shared with the desktop engine's `HDR_SDR_FALLBACK_NOTICE`. */
        const val NOTICE_SDR_DISPLAY = "SDR display - tone mapping active"

        /** Tone mapping disabled while the display cannot take the transfer natively. */
        const val NOTICE_SDR_NO_TONE_MAPPING = "SDR display - HDR not tone mapped"

        /** DV profile 5 on a non-DV display decodes to uncorrectable colors on some devices. */
        const val NOTICE_DV_NON_DV_DISPLAY =
            "Dolby Vision on a display without DV support - colors may be incorrect" +
                " (consider the mpv engine)"
    }
}

/**
 * The ExoPlayer HDR output decision. Media3 1.11.1 exposes no force-tone-map
 * lever (`PlaybackVideoGraphWrapper.setRequestOpenGlToneMapping` is
 * instance-level, the wrapper not reachable), so the resolver's lever is the
 * GL video-graph engagement: `toneMappingActive` means "call
 * `ExoPlayer.setVideoEffects` so frames flow through `DefaultVideoFrameProcessor`,
 * whose SDR-output branch applies the HDR→SDR tone map whenever the surface's
 * EGL context cannot output the content's transfer". `hdrOutputExpected` is an
 * EXPECTATION read off the Android display capability probe (Media3's own EGL
 * check remains the final arbiter at render time).
 *
 *  - SDR content → the native MediaCodec→Surface path (no GL pass, zero cost).
 *  - [ExoHdrToneMappingMode.OFF] → always native. A display KNOWN unable to
 *    take the transfer gets the not-tone-mapped notice (the honest "why it
 *    looks washed out" line); an unknown display stays quiet.
 *  - AUTO + HDR content + display known and compatible → native HDR output.
 *  - AUTO + HDR content + SDR display → tone mapping requested + notice.
 *  - AUTO + HDR content + probe unavailable (`null`) → native (the pre-feature
 *    behavior — never regress a device because its display probe failed).
 */
fun resolveExoHdrOutput(
    content: HdrContentInfo,
    display: DisplayHdrSupport?,
    mode: ExoHdrToneMappingMode,
): HdrOutputDecision {
    if (!content.isHdr) return HdrOutputDecision.NONE
    if (mode == ExoHdrToneMappingMode.OFF) {
        // The user opted out of the GL path — native output either way, but a
        // KNOWN-incompatible display gets the washed-out explanation rather
        // than silence; a compatible one still gets the honest HDR badge.
        if (display != null && !display.supportsContent(content)) {
            return HdrOutputDecision(
                unhandledHdr = true,
                notice = HdrOutputDecision.NOTICE_SDR_NO_TONE_MAPPING,
            )
        }
        return HdrOutputDecision(hdrOutputExpected = display?.supportsContent(content) == true)
    }
    val displayKnownCompatible = display != null && display.supportsContent(content)
    if (displayKnownCompatible) return HdrOutputDecision(hdrOutputExpected = true)
    if (display == null) return HdrOutputDecision.NONE
    return when (content) {
        HdrContentInfo.DOLBY_VISION -> HdrOutputDecision(
            toneMappingActive = true,
            unhandledHdr = true,
            notice = HdrOutputDecision.NOTICE_DV_NON_DV_DISPLAY,
        )
        else -> HdrOutputDecision(
            toneMappingActive = true,
            notice = HdrOutputDecision.NOTICE_SDR_DISPLAY,
        )
    }
}

/**
 * The mpv HDR output decision — the pure shape of the desktop engine's inline
 * gate (`hdrPassthroughRequested && hdrTargetIsHdr`), shared so the Android
 * adapter answers identically. mpv ALWAYS tone maps when the target is SDR
 * (its `tone-mapping=auto` default), so "unhandled" never occurs; the notice
 * fires only when passthrough was REQUESTED but the `video-target-params`
 * probe says the output stayed SDR — the display ignored the colorspace hint.
 *
 * Before the per-file probe lands, [targetIsHdr] is `false` (the safe
 * fallback: the tone-mapping preset is written, HDR→HDR suppression closed) —
 * exactly the desktop's pre-probe posture.
 */
fun resolveMpvHdrOutput(
    content: HdrContentInfo,
    passthroughRequested: Boolean,
    targetIsHdr: Boolean,
): HdrOutputDecision = when {
    !content.isHdr -> HdrOutputDecision.NONE
    passthroughRequested && targetIsHdr -> HdrOutputDecision(hdrOutputExpected = true)
    passthroughRequested -> HdrOutputDecision(
        toneMappingActive = true,
        notice = HdrOutputDecision.NOTICE_SDR_DISPLAY,
    )
    else -> HdrOutputDecision(toneMappingActive = true)
}
