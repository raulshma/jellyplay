package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DeinterlaceMode
import com.raulshma.jellyplay.core.model.MpvAudioOutputMode
import com.raulshma.jellyplay.core.model.MpvDemuxerMaxBytes
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.MpvOption
import com.raulshma.jellyplay.core.model.MpvRenderQuality
import com.raulshma.jellyplay.core.model.MpvShaderPack
import com.raulshma.jellyplay.core.model.parseMpvConfigOptions

/**
 * Pure, testable mapping from [MpvEngineConfig] to the ordered mpv property
 * key/value pairs both mpv engines (Android `MpvPlayerEngine`, desktop
 * `MpvDesktopEngine`) apply. Extracted from MpvPlayerEngine's `initOptions`
 * application block — which was the only consumer of the structured engine
 * config, leaving the desktop engine deaf to every mpv knob the settings
 * screen exposes — so the two engines cannot drift.
 *
 * The mapping covers the structured config only: `scale`, `deband`,
 * `interpolation` (+ `video-sync=display-resample`), `framedrop`,
 * `vd-lavc-skiploopfilter`, the demuxer byte budgets, the audio trio
 * (`audio-device` / `audio-exclusive` / `audio-spdif` via the output mode),
 * and the user's `mpvExtraConfig` lines LAST (so a raw line overrides its
 * structured counterpart — the documented escape-hatch intent). Engine-owned
 * surfaces with platform-specific behavior (`hwdec`, the `ao` backend chain,
 * subtitle styling, the effects `af`/`vf` chains, msg-level/profile) stay in
 * the engines.
 *
 * Consumers:
 *  - Init-time: [applyMpvConfig] writes every pair through the caller's
 *    setter (`setOptionString` on Android init, `setPropertyString` on the
 *    desktop's FILE_LOADED apply). Every pair is written explicitly — values
 *    equal to mpv's defaults ("no"/"auto"/"") are no-ops against the core.
 *  - Runtime: [applyChanged] diffs a new pair list against the engine's
 *    last-applied map and writes only the changed keys — the desktop's
 *    diff-then-write `lastApplied*` discipline (an unchanged re-write of
 *    `af`-class properties re-inits pipelines and breaks the pacing clock;
 *    the same discipline keeps the cheap properties here from spamming
 *    writes at every FILE_LOADED).
 *
 * Deliberately emits explicit values for EVERY owned key (not just
 * non-defaults): the runtime diff must see a flip to default as a changed
 * pair (e.g. `audio-exclusive` true→false must write "no" or the device
 * stays locked), which an omitted-pair representation cannot express.
 *
 * Public (not internal) because the desktop adapter (apps/desktop) applies
 * this same mapping — the [MpvStyleMapping] precedent. Consumers stay the
 * two engine adapters and the mapping test; not a stable API surface.
 */
object MpvConfigMapping {

    /**
     * The spdif codec list the legacy [com.raulshma.jellyplay.core.model.EngineConfig.audioPassthrough]
     * boolean has always mapped to (Android init + runtime byte-parity —
     * `dtshd` is mpv's historical alias for dts-hd).
     */
    const val SPDIF_LEGACY_PASSTHROUGH = "ac3,eac3,dts,dtshd,truehd"

    /** S/PDIF (optical) carries the classic bitstream codecs only. */
    const val SPDIF_OPTICAL = "ac3,dts"

    /** HDMI carries the full bitstream set (list deduped, dts/dts-hd distinct). */
    const val SPDIF_HDMI = "ac3,eac3,dts,dts-hd,truehd"

    /**
     * The `audio-spdif` value for [mode], with [passthroughFallback] as the
     * legacy boolean consulted only by [MpvAudioOutputMode.AUTO] — that is
     * the whole passthrough-boolean reconciliation: an explicit mode WINS
     * over the platform boolean (the mode rows are the newer, finer surface),
     * and `AUTO` defers to it so the existing Android toggle stays the
     * single source until a mode is picked. `null` means "clear" — engines
     * write the empty string, mpv's list default.
     */
    fun audioSpdif(mode: MpvAudioOutputMode, passthroughFallback: Boolean): String? = when (mode) {
        MpvAudioOutputMode.AUTO -> if (passthroughFallback) SPDIF_LEGACY_PASSTHROUGH else null
        MpvAudioOutputMode.STEREO -> null
        MpvAudioOutputMode.OPTICAL -> SPDIF_OPTICAL
        MpvAudioOutputMode.HDMI -> SPDIF_HDMI
    }

    /**
     * The forced-downmix half of [MpvAudioOutputMode.STEREO]: `stereo` as the
     * `audio-channels` value, but ONLY where the audio-effects channel-mix
     * chain has no opinion of its own (its resolution isn't plain `auto`) —
     * the effects chain remains the single writer of `audio-channels` on both
     * engines (an `af`-class pipeline-re-initing property), so the mode folds
     * into that writer's value instead of becoming a second one. `null` =
     * no override, the caller's effects-derived value stands.
     */
    fun stereoDownmixOverride(
        mode: MpvAudioOutputMode,
        channelMixMode: ChannelMixMode,
        channelMixEnabled: Boolean,
    ): String? = if (mode == MpvAudioOutputMode.STEREO &&
        channelMixModeToAudioChannels(channelMixMode, channelMixEnabled) == AUTO_CHANNELS
    ) {
        "stereo"
    } else {
        null
    }

    /**
     * The effective `audio-channels` value: the effects chain's mapping with
     * the STEREO mode's forced downmix folded in — the one helper both
     * engines' audio-channels writers route through so the mode cannot drift
     * from the effects surface it composes with.
     */
    fun effectiveAudioChannels(
        mode: MpvAudioOutputMode,
        channelMixMode: ChannelMixMode,
        channelMixEnabled: Boolean,
    ): String = stereoDownmixOverride(mode, channelMixMode, channelMixEnabled)
        ?: channelMixModeToAudioChannels(channelMixMode, channelMixEnabled)

    /**
     * The demuxer byte budgets `(max, back)` for [config]. `AUTO` resolves
     * device-appropriately — low-RAM Android devices get the halved pair;
     * desktop (and every non-low-RAM device) the normal pair. The back
     * buffer keeps its historical half-of-max shape for explicit sizes too.
     */
    fun demuxerMaxBytesFor(config: MpvEngineConfig, lowRamDevice: Boolean): Pair<Long, Long> {
        val max = when (val selection = config.demuxerMaxBytes) {
            MpvDemuxerMaxBytes.AUTO -> if (lowRamDevice) DEMUXER_MAX_BYTES_LOW else DEMUXER_MAX_BYTES_NORMAL
            else -> selection.bytes
        }
        val back = when (val selection = config.demuxerMaxBytes) {
            MpvDemuxerMaxBytes.AUTO ->
                if (lowRamDevice) DEMUXER_MAX_BACK_BYTES_LOW else DEMUXER_MAX_BACK_BYTES_NORMAL
            else -> selection.bytes / 2
        }
        return max to back
    }

    /**
     * The ordered `*.glsl` file names of the Anime4K v4.0.1 pack chains
     * — verbatim from the upstream `GLSL_Instructions.md` "higher-end
     * GPU" recipes (the render-quality dial, not the pack, owns the
     * fast/quality trade-off elsewhere). Order is load-bearing: each stage
     * feeds the next, and `glsl-shaders` applies the list in order.
     */
    fun anime4kChain(pack: MpvShaderPack): List<String> = when (pack) {
        MpvShaderPack.ANIME4K_A -> listOf(
            "Anime4K_Clamp_Highlights.glsl",
            "Anime4K_Restore_CNN_VL.glsl",
            "Anime4K_Upscale_CNN_x2_VL.glsl",
            "Anime4K_AutoDownscalePre_x2.glsl",
            "Anime4K_AutoDownscalePre_x4.glsl",
            "Anime4K_Upscale_CNN_x2_M.glsl",
        )
        MpvShaderPack.ANIME4K_B -> listOf(
            "Anime4K_Clamp_Highlights.glsl",
            "Anime4K_Restore_CNN_Soft_VL.glsl",
            "Anime4K_Upscale_CNN_x2_VL.glsl",
            "Anime4K_AutoDownscalePre_x2.glsl",
            "Anime4K_AutoDownscalePre_x4.glsl",
            "Anime4K_Upscale_CNN_x2_M.glsl",
        )
        MpvShaderPack.ANIME4K_C -> listOf(
            "Anime4K_Clamp_Highlights.glsl",
            "Anime4K_Upscale_Denoise_CNN_x2_VL.glsl",
            "Anime4K_AutoDownscalePre_x2.glsl",
            "Anime4K_AutoDownscalePre_x4.glsl",
            "Anime4K_Upscale_CNN_x2_M.glsl",
        )
        else -> emptyList()
    }

    /**
     * The absolute `glsl-shaders` path list for [config], resolved against
     * [shaderDir] (the extracted pack directory). ANIME4K_* → chain files
     * inside [shaderDir]; CUSTOM → [MpvEngineConfig.customShaderFiles]
     * verbatim (absolute paths the user picked); OFF → empty. `null` return =
     * "say nothing": OFF with no directory (Android — the pack surface
     * doesn't exist there, so no pair at all) or an ANIME4K_* pack selected
     * with no directory to resolve it against (the caller omits the pair
     * rather than feeding mpv garbage).
     */
    fun shaderPaths(config: MpvEngineConfig, shaderDir: String?): List<String>? = when {
        config.shaderPack == MpvShaderPack.OFF && shaderDir == null -> null
        config.shaderPack == MpvShaderPack.OFF -> emptyList()
        config.shaderPack == MpvShaderPack.CUSTOM -> config.customShaderFiles
        shaderDir == null -> null
        else -> anime4kChain(config.shaderPack).map { file -> "${shaderDir.trimEnd('/')}/$file" }
    }

    /**
     * The `video-sync` value for [config]: `display-resample` when
     * interpolation is on, mpv's `audio` default when it is off. The off
     * value is written EXPLICITLY — at init it is a no-op against mpv's
     * default, but the runtime diff must see interpolation on→off as a
     * changed pair or `display-resample` stays stuck until restart (the
     * same every-owned-key rule as [renderQualityBundle]'s defaults).
     */
    fun videoSyncValue(config: MpvEngineConfig): String =
        if (config.interpolation) "display-resample" else "audio"

    /**
     * The `tscale` value for [config], or `null` when interpolation is off —
     * the temporal filter only matters with `interpolation=yes`, and mpv
     * never diverges from the diff cache while the pair is omitted (nothing
     * else writes `tscale`, so the cached value is also mpv's live value).
     */
    fun tscaleValue(config: MpvEngineConfig): String? =
        if (config.interpolation) config.interpolationTscale.key else null

    /**
     * The `tone-mapping` value for [config]: the preset's key, or mpv's
     * `auto` default for [com.raulshma.jellyplay.core.model.MpvToneMapping.AUTO]
     * and while [toneMappingSuppressed] is active (the HDR→HDR rule —
     * `auto` hands the decision to mpv's colorspace-hint path). The default
     * is written EXPLICITLY so the runtime diff can express preset→AUTO and
     * entering-passthrough transitions — an omitted pair would leave a stale
     * preset stuck until restart.
     */
    fun toneMappingValue(config: MpvEngineConfig, toneMappingSuppressed: Boolean): String {
        if (toneMappingSuppressed) return AUTO_TONE_MAPPING
        return config.toneMapping.key ?: AUTO_TONE_MAPPING
    }

    /**
     * The [MpvRenderQuality] bundle pairs appended AFTER the structured
     * `scale`/`deband` pairs so a non-[MpvRenderQuality.BALANCED] profile
     * overrides them by order (this mapper's later-same-key-wins convention).
     * BALANCED forces nothing — the structured rows stand — but still writes
     * the bundle-ONLY keys (`dscale`/`cscale`/`dither-depth`) at their mpv
     * defaults, EXPLICITLY: flipping HIGH→BALANCED through the runtime diff
     * cache must produce writes for those keys instead of leaving the forced
     * HIGH values stuck (an omitted-pair representation cannot express the
     * transition — the same rule as every other owned key in this mapper).
     */
    fun renderQualityBundle(quality: MpvRenderQuality): List<MpvOption> = when (quality) {
        MpvRenderQuality.BALANCED -> listOf(
            MpvOption("dscale", "mipmap"),
            MpvOption("cscale", "bilinear"),
            MpvOption("dither-depth", "auto"),
        )
        MpvRenderQuality.HIGH -> listOf(
            MpvOption("scale", "ewa_lanczossharp"),
            MpvOption("dscale", "spline36"),
            MpvOption("cscale", "spline36"),
            MpvOption("dither-depth", "auto"),
            MpvOption("deband", "yes"),
        )
        MpvRenderQuality.PERFORMANCE -> listOf(
            MpvOption("scale", "bilinear"),
            MpvOption("dscale", "bilinear"),
            MpvOption("cscale", "bilinear"),
            MpvOption("dither-depth", "auto"),
            MpvOption("deband", "no"),
        )
    }

    /**
     * The full ordered pair list for [config]. `audioPassthrough` is the
     * legacy [com.raulshma.jellyplay.core.model.EngineConfig.audioPassthrough]
     * boolean (both engines read it off the shared config); `lowRamDevice`
     * picks the AUTO demuxer budget. [deinterlace] is the base-config
     * mode (mpv `deinterlace`); [shaderDir] is the desktop-extracted pack
     * directory (`null` on platforms with no extraction — the `glsl-shaders`
     * pair is then omitted unless the CUSTOM pack names its own absolute
     * paths); [toneMappingSuppressed] is the active-HDR-passthrough gate
     * (the `tone-mapping` pair falls to mpv's `auto` default, HDR→HDR).
     * Later same-key pairs win (the
     * `mpvExtraConfig` escape hatch's override intent) — the list is the
     * write order.
     */
    fun configPairs(
        config: MpvEngineConfig,
        audioPassthrough: Boolean = false,
        lowRamDevice: Boolean = false,
        deinterlace: DeinterlaceMode = DeinterlaceMode.AUTO,
        shaderDir: String? = null,
        toneMappingSuppressed: Boolean = false,
    ): List<MpvOption> = buildList {
        add(MpvOption("scale", config.scaler.key))
        add(MpvOption("deband", if (config.deband) "yes" else "no"))
        add(MpvOption("interpolation", if (config.interpolation) "yes" else "no"))
        add(MpvOption("video-sync", videoSyncValue(config)))
        if (config.interpolation) {
            // display-resample only matters with interpolation on; the
            // off-branch writes mpv's `audio` default so the runtime diff
            // sees the on→off transition (see videoSyncValue).
            tscaleValue(config)?.let { add(MpvOption("tscale", it)) }
        }
        add(MpvOption("framedrop", config.frameDrop.key))
        add(MpvOption("vd-lavc-skiploopfilter", config.skipLoopFilter.key))
        add(MpvOption("deinterlace", deinterlaceKey(deinterlace)))
        val (demuxerMax, demuxerBack) = demuxerMaxBytesFor(config, lowRamDevice)
        add(MpvOption("demuxer-max-bytes", demuxerMax.toString()))
        add(MpvOption("demuxer-max-back-bytes", demuxerBack.toString()))
        add(MpvOption("audio-spdif", audioSpdif(config.audioOutputMode, audioPassthrough).orEmpty()))
        add(MpvOption("audio-device", config.audioDevice ?: AUTO_DEVICE))
        add(MpvOption("audio-exclusive", if (config.audioExclusive) "yes" else "no"))
        // ── Render surface ─────────────────────────────────────────────────
        // glsl-shaders: emitted whenever the paths resolve — an explicit ""
        // (mpv's cleared list) when OFF so the runtime diff cache sees the
        // pack→OFF transition as a changed pair; omitted only when the pack
        // is selected but no directory can resolve it (Android: the extraction
        // surface is desktop-only).
        shaderPaths(config, shaderDir)?.let { paths ->
            add(MpvOption("glsl-shaders", paths.joinToString(",")))
        }
        add(MpvOption("tone-mapping", toneMappingValue(config, toneMappingSuppressed)))
        // The quality bundle after the structured scale/deband pairs, so its
        // own scale/deband entries override them by order (BALANCED forces
        // neither — the structured rows stand; its dscale/cscale/dither-depth
        // entries are explicit defaults, see renderQualityBundle).
        addAll(renderQualityBundle(config.renderQuality))
        // Power-user escape hatch, LAST: a raw line overrides its structured
        // counterpart above.
        addAll(parseMpvConfigOptions(config.mpvExtraConfig))
    }

    /** mpv's `deinterlace` value for [mode]: auto / yes / no. */
    fun deinterlaceKey(mode: DeinterlaceMode): String = when (mode) {
        DeinterlaceMode.AUTO -> "auto"
        DeinterlaceMode.ON -> "yes"
        DeinterlaceMode.OFF -> "no"
    }

    /**
     * Init-time application: writes every pair through [setter] in order.
     * Android init passes `setOptionString` (which throws per rejected
     * extra-config line — catch inside the lambda, the engine logs); the
     * desktop's FILE_LOADED path passes a string property write.
     */
    fun applyMpvConfig(
        setter: (String, String) -> Unit,
        config: MpvEngineConfig,
        audioPassthrough: Boolean = false,
        lowRamDevice: Boolean = false,
        deinterlace: DeinterlaceMode = DeinterlaceMode.AUTO,
        shaderDir: String? = null,
        toneMappingSuppressed: Boolean = false,
    ) {
        configPairs(config, audioPassthrough, lowRamDevice, deinterlace, shaderDir, toneMappingSuppressed)
            .forEach { (key, value) ->
                setter(key, value)
            }
    }

    /**
     * Runtime diff-then-write: writes only the pairs whose [lastApplied]
     * value differs, via [write], and returns the updated applied-map (the
     * caller stores it back into its `lastApplied` field — the desktop
     * engine's `@Volatile` cache discipline). [lastApplied] starts empty so
     * the first application writes everything.
     */
    fun applyChanged(
        pairs: List<MpvOption>,
        lastApplied: Map<String, String>,
        write: (String, String) -> Unit,
    ): Map<String, String> {
        val next = lastApplied.toMutableMap()
        for (option in pairs) {
            if (next[option.key] != option.value) {
                write(option.key, option.value)
                next[option.key] = option.value
            }
        }
        return next
    }

    /** mpv's untouched `audio-device` default, written for [MpvEngineConfig.audioDevice] = null. */
    const val AUTO_DEVICE = "auto"

    /** mpv's untouched `tone-mapping` default (AUTO and passthrough-active). */
    const val AUTO_TONE_MAPPING = "auto"

    /** mpv's untouched `audio-channels` default (the effects chain's off value). */
    private const val AUTO_CHANNELS = "auto"

    // Historical Android low-RAM budgets — moved here so both engines and the
    // mapping test read one table (MpvDemuxerMaxBytes.AUTO's resolution).
    private const val DEMUXER_MAX_BYTES_LOW = 32L * 1024 * 1024
    private const val DEMUXER_MAX_BYTES_NORMAL = 64L * 1024 * 1024
    private const val DEMUXER_MAX_BACK_BYTES_LOW = 16L * 1024 * 1024
    private const val DEMUXER_MAX_BACK_BYTES_NORMAL = 32L * 1024 * 1024
}
