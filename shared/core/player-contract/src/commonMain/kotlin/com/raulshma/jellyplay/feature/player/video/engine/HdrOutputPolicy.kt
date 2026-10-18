package com.raulshma.jellyplay.feature.player.video.engine

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.ExoHdrToneMappingMode
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.PlayerType
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
 * The single HDR-family vocabulary parser — the ONE place free-form HDR text
 * (Jellyfin `videoRange`/`videoRangeType` claims, the engines' stats
 * projections, the HDR sheet's chip labels) becomes an [HdrContentInfo].
 * Every consumer that used to spell the vocabulary itself — the stream fold
 * ([hdrContentFromStreams]), the family labels ([hdrLabelFor]) and the
 * playback-metadata row's DV badge — folds through here, so the spelling
 * sets that grew independently (dovi / dolbyvision / dolby_vision / the
 * label-shaped "dolby …"; hdr10plus / hdr10+ / hdr10_plus) can never drift
 * apart again.
 *
 * The rung order encodes the same specificity as [HdrContentInfo]: Dolby
 * Vision first (its `DolbyVisionWithHDR10` profile strings mention HDR10 —
 * `dolby` subsumes the `dolbyvision`/`dolby_vision`/"dolby vision" label
 * spellings), then the HDR10+ / HDR10 pair (`hdr10plus`/`hdr10+`/
 * `hdr10_plus` must match before the bare `hdr10`), then HLG, then the bare
 * `hdr` catch-all (the legacy `videoRange=HDR` — the "HDR, static-info"
 * family). Each rung scans ALL the texts before the next rung starts — the
 * contains-ladder semantics of the space-joined range+rangeType string the
 * stream fold used to build, not a first-text-wins fold (range "HDR" +
 * rangeType "DolbyVision…" must still read DV).
 *
 * Returns `null` when no text carries an HDR family (SDR / unknown /
 * unrecognized) — callers' fallback chains stay single-sourced on that null.
 */
fun hdrFamilyOf(vararg texts: String?): HdrContentInfo? {
    val lowered = texts.filterNotNull().filter { it.isNotBlank() }.map { it.lowercase() }
    if (lowered.isEmpty()) return null
    return when {
        lowered.any { it.contains("dovi") || it.contains("dolby") } -> HdrContentInfo.DOLBY_VISION
        lowered.any { it.contains("hdr10plus") || it.contains("hdr10+") || it.contains("hdr10_plus") } ->
            HdrContentInfo.HDR10_PLUS
        lowered.any { it.contains("hdr10") } -> HdrContentInfo.HDR10
        lowered.any { it.contains("hlg") } -> HdrContentInfo.HLG
        lowered.any { it.contains("hdr") } -> HdrContentInfo.HDR10
        else -> null
    }
}

/**
 * The HDR family's user-facing label ("Dolby Vision", "HDR10", "HDR10+"), or
 * `null` for SDR/unknown — the label twin of [hdrFamilyOf]. The HDR sheet's
 * content row and the playback-metadata row's HDR chip both render through
 * it, so a family cannot gain a spelling without gaining its label. The
 * SDR/unknown vocabularies normalize to null so callers' fallback chains
 * (metadata → observed → "SDR") stay single-sourced; the bare legacy `hdr`
 * claim keeps its own label (the chip reads "HDR", not "HDR10" — the
 * static-info family [hdrFamilyOf] classifies it as); anything unrecognized
 * is shouted through unchanged (`raw.uppercase()`).
 */
fun hdrLabelFor(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    if (raw.equals("sdr", ignoreCase = true) || raw.equals("unknown", ignoreCase = true)) return null
    if (raw.equals("hdr", ignoreCase = true)) return "HDR"
    return hdrFamilyOf(raw)?.displayName ?: raw.uppercase()
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
    // The DoVi title alone decides (the local MediaExtractor probe's DV path).
    if (stream.videoDoViTitle != null) return HdrContentInfo.DOLBY_VISION
    return hdrFamilyOf(stream.videoRange, stream.videoRangeType) ?: HdrContentInfo.NONE
}

/**
 * The display's HDR output capability — the set of [HdrContentInfo] families
 * the CURRENT display accepts. Empty set = SDR display. Sourced per engine:
 * both Android hosts (ExoPlayer AND the mpv engine — the same
 * `DisplayHdrCapabilitiesProbe` helper, built to be owned by every engine
 * that wants the facts) read the Android `Display.HdrCapabilities` types;
 * the DESKTOP mpv host answers from mpv's own `video-target-params` probe
 * instead, so it never builds this value.
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

// ── The status-card verdict (pure, testable; the HDR sheet renders it) ──

/**
 * The user-facing HDR playback mode the status card leads with — the
 * shopping-list answer to "what is happening to the HDR picture right now".
 */
@Immutable
enum class HdrPlaybackMode {
    /** The display switched to the content's HDR transfer — playback is native HDR. */
    HdrOutput,
    /** The engine converts HDR → SDR in its render pipeline. */
    ToneMapped,
    /** Native output with NO tone-mapping path — washed-out colors are expected. */
    NativeUnhandled,
    /** No HDR signal in the current decode — no HDR processing is involved. */
    SdrPlayback,
    /** The engine exposes no HDR output state (VLC, external players). */
    NotReported,
}

@Immutable
data class HdrPlaybackModeInfo(
    val mode: HdrPlaybackMode,
    /** The status card's headline ("Tone mapped to SDR"). */
    val title: String,
    /** The status card's one-line explanation. */
    val explanation: String,
)

private fun sdrPlaybackInfo() = HdrPlaybackModeInfo(
    mode = HdrPlaybackMode.SdrPlayback,
    title = "SDR playback",
    explanation = "What's playing right now has no HDR signal — the picture plays as-is.",
)

/**
 * The HDR sheet's honest mapping from the (player type × content × output)
 * facts to the status-card headline. Matches the shared [HdrOutputDecision]
 * notice CONSTANT references (compile-checked; never raw string literals).
 *
 * The content basis is engine-specific, mirroring how each engine publishes
 * its verdict:
 *  - **ExoPlayer** identifies the DECODING stream every time (colorInfo /
 *    MIME → [observedHdrType]), and DOWNGRADES its published
 *    `hdrOutputActive` when the observed track is clearly SDR (the adaptive
 *    transcode-rendition case). Its observed family is therefore
 *    authoritative: observed null ⇒ no HDR signal in the decode.
 *  - **mpv hosts** never publish an observed transfer (the stats projection
 *    reads no transfer property), so the metadata claim ([contentHdrType])
 *    is the only content basis; the output verdict comes from the
 *    `video-target-params` probe — mpv always tone maps a non-HDR target.
 */
fun resolveHdrPlaybackMode(
    playerType: PlayerType,
    contentHdrType: String?,
    observedHdrType: String?,
    hdrOutputActive: Boolean,
    hdrOutputNotice: String?,
): HdrPlaybackModeInfo {
    val reportsOutput = playerType == PlayerType.MPV || playerType == PlayerType.EXO_PLAYER
    val claimsHdr = !contentHdrType.isNullOrBlank() || !observedHdrType.isNullOrBlank()
    val observesHdr = !observedHdrType.isNullOrBlank()
    return when {
        // Engines that expose no HDR output state: the honest empty state.
        !reportsOutput -> HdrPlaybackModeInfo(
            mode = HdrPlaybackMode.NotReported,
            title = "No HDR report",
            explanation = "This player doesn't report an HDR output state.",
        )
        // The engine's display-in-HDR verdict wins over everything (the Exo
        // observed-SDR downgrade would already have cleared it).
        hdrOutputActive -> HdrPlaybackModeInfo(
            mode = HdrPlaybackMode.HdrOutput,
            title = "Native HDR output",
            explanation = "Your display switched to its HDR mode — the picture plays natively.",
        )
        // mpv: the claim (or the desktop's observed family) decides content;
        // a non-HDR target gets mpv's tone-mapping preset, always — whether
        // passthrough is off or the display ignored the colorspace hint.
        playerType == PlayerType.MPV ->
            if (claimsHdr) {
                HdrPlaybackModeInfo(
                    mode = HdrPlaybackMode.ToneMapped,
                    title = "Tone mapped to SDR",
                    explanation = "The output stayed SDR — colors are converted for your display.",
                )
            } else {
                sdrPlaybackInfo()
            }
        // Exo with an HDR decode: the notice disambiguates the posture.
        observesHdr -> when {
            hdrOutputNotice == HdrOutputDecision.NOTICE_SDR_NO_TONE_MAPPING ||
                hdrOutputNotice == HdrOutputDecision.NOTICE_DV_NON_DV_DISPLAY ->
                // The tone-mapping lever was declined (AUTO/OFF) or the
                // family is unrepresentable (DV profile 5): native output,
                // colors unhandled.
                HdrPlaybackModeInfo(
                    mode = HdrPlaybackMode.NativeUnhandled,
                    title = "HDR output unhandled",
                    explanation = hdrOutputNotice.orEmpty(),
                )
            hdrOutputNotice == HdrOutputDecision.NOTICE_SDR_DISPLAY ->
                // AUTO + a reported-SDR display: the GL tone-mapping path is
                // engaged (the notice says it).
                HdrPlaybackModeInfo(
                    mode = HdrPlaybackMode.ToneMapped,
                    title = "Tone mapped to SDR",
                    explanation = "The display can't show this HDR family — the picture is " +
                        "converted for SDR.",
                )
            else ->
                // The display probe never answered (AUTO/OFF both land here):
                // the tone-mapping lever was not engaged, so the output is
                // native either way — on an SDR display that means washed out.
                HdrPlaybackModeInfo(
                    mode = HdrPlaybackMode.NativeUnhandled,
                    title = "HDR output unhandled",
                    explanation = "Tone mapping isn't engaged and the display's HDR support " +
                        "is unknown — colors may look washed out.",
                )
        }
        // Exo with no observed HDR signal: SDR decode (or the format hasn't
        // reported yet) — no HDR processing in the pipeline.
        else -> sdrPlaybackInfo()
    }
}

/**
 * The DETECTED display type the facts row shows — the most precise fact
 * available, with honest render-path inference when the platform probe has
 * no answer (some devices answer `Display.getHdrCapabilities` with null
 * even on HDR-capable panels):
 *  - the probe's family list when it answered ("HDR10, Dolby Vision" /
 *    "SDR" — both Android engines publish the same shapes),
 *  - the output spot-check when only a target verdict exists (mpv's
 *    `video-target-params` HDR verdict — the desktop mpv hosts),
 *  - "SDR (the output stayed SDR)" when the engine tone mapped — the
 *    tone-mapping render path itself proves the output is SDR,
 *  - "Unknown" with the reason when only a lever decision exists but no
 *    display fact (the probing exo lever is not engaged — the display probe
 *    never answered),
 *  - an honest "Not detected" only when nothing observable says anything.
 */
fun resolveDetectedDisplayLabel(
    displayHdrTypes: String?,
    mode: HdrPlaybackMode,
): String = when {
    !displayHdrTypes.isNullOrBlank() -> displayHdrTypes
    mode == HdrPlaybackMode.HdrOutput -> "HDR (detected from the output)"
    mode == HdrPlaybackMode.ToneMapped -> "SDR (the output stayed SDR)"
    mode == HdrPlaybackMode.NativeUnhandled -> "Unknown (the display probe didn't answer)"
    else -> "Not detected"
}
