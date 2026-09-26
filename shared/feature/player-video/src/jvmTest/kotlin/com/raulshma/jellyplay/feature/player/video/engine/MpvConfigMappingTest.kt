package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DeinterlaceMode
import com.raulshma.jellyplay.core.model.MpvAudioOutputMode
import com.raulshma.jellyplay.core.model.MpvDemuxerMaxBytes
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.MpvFrameDrop
import com.raulshma.jellyplay.core.model.MpvInterpolationTscale
import com.raulshma.jellyplay.core.model.MpvOption
import com.raulshma.jellyplay.core.model.MpvRenderQuality
import com.raulshma.jellyplay.core.model.MpvScaler
import com.raulshma.jellyplay.core.model.MpvShaderPack
import com.raulshma.jellyplay.core.model.MpvSkipLoopFilter
import com.raulshma.jellyplay.core.model.MpvToneMapping
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins for the shared [MpvConfigMapping] — the MpvEngineConfig → mpv property
 * table BOTH mpv engines apply (Android init + runtime, desktop FILE_LOADED +
 * live change). The groundwork matrix: every structured field's emitted pair,
 * the audio-spdif mode composition with the legacy passthrough-boolean
 * reconciliation, the STEREO downmix fold into the effects-owned
 * `audio-channels`, the demuxer budgets, the extra-config override order, and
 * the runtime diff-then-write discipline.
 */
class MpvConfigMappingTest {

    // ── audioSpdif: mode → spdif composition + boolean reconciliation ──────

    @Test
    fun spdif_autoMode_offPassthrough_clears() {
        assertNull(MpvConfigMapping.audioSpdif(MpvAudioOutputMode.AUTO, passthroughFallback = false))
    }

    @Test
    fun spdif_autoMode_passthroughKeepsTheLegacyAndroidList() {
        // Byte-parity with the boolean toggle's historical list (dtshd alias).
        assertEquals(
            "ac3,eac3,dts,dtshd,truehd",
            MpvConfigMapping.audioSpdif(MpvAudioOutputMode.AUTO, passthroughFallback = true),
        )
    }

    @Test
    fun spdif_explicitMode_winsOverTheBoolean() {
        // The reconciliation: once a mode is picked, the platform boolean is
        // ignored — STEREO never bitstreams even with passthrough on.
        assertNull(MpvConfigMapping.audioSpdif(MpvAudioOutputMode.STEREO, passthroughFallback = true))
        assertEquals("ac3,dts", MpvConfigMapping.audioSpdif(MpvAudioOutputMode.OPTICAL, passthroughFallback = false))
        assertEquals(
            "ac3,eac3,dts,dts-hd,truehd",
            MpvConfigMapping.audioSpdif(MpvAudioOutputMode.HDMI, passthroughFallback = false),
        )
    }

    // ── audio-channels: the STEREO downmix folds into the effects writer ───

    @Test
    fun channels_stereoMode_forcesDownmixWhenEffectsHaveNoOpinion() {
        assertEquals(
            "stereo",
            MpvConfigMapping.effectiveAudioChannels(MpvAudioOutputMode.STEREO, ChannelMixMode.AUTO, channelMixEnabled = false),
        )
    }

    @Test
    fun channels_explicitEffectMix_beatsTheStereoMode() {
        // MONO is narrower than the mode's stereo — the explicit effect wins.
        assertEquals("mono", MpvConfigMapping.effectiveAudioChannels(MpvAudioOutputMode.STEREO, ChannelMixMode.MONO, true))
        assertEquals("5.1", MpvConfigMapping.effectiveAudioChannels(MpvAudioOutputMode.STEREO, ChannelMixMode.SURROUND_UPMIX, true))
        // The effect's own stereo downmix agrees with the mode.
        assertEquals("stereo", MpvConfigMapping.effectiveAudioChannels(MpvAudioOutputMode.STEREO, ChannelMixMode.STEREO_DOWNMIX, true))
    }

    @Test
    fun channels_nonStereoModes_neverOverrideTheEffects() {
        for (mode in MpvAudioOutputMode.entries - MpvAudioOutputMode.STEREO) {
            assertEquals(
                "auto",
                MpvConfigMapping.effectiveAudioChannels(mode, ChannelMixMode.AUTO, channelMixEnabled = false),
                "mode $mode must not touch audio-channels",
            )
        }
    }

    // ── demuxer budgets ─────────────────────────────────────────────────────

    @Test
    fun demuxer_auto_resolvesByDeviceProfile() {
        assertEquals(
            (32L * 1024 * 1024) to (16L * 1024 * 1024),
            MpvConfigMapping.demuxerMaxBytesFor(MpvEngineConfig(), lowRamDevice = true),
        )
        assertEquals(
            (64L * 1024 * 1024) to (32L * 1024 * 1024),
            MpvConfigMapping.demuxerMaxBytesFor(MpvEngineConfig(), lowRamDevice = false),
        )
    }

    @Test
    fun demuxer_explicitSize_backStaysAtHalf() {
        val (max, back) = MpvConfigMapping.demuxerMaxBytesFor(
            MpvEngineConfig(demuxerMaxBytes = MpvDemuxerMaxBytes.MB_128),
            lowRamDevice = true, // irrelevant for explicit sizes
        )
        assertEquals(128L * 1024 * 1024, max)
        assertEquals(64L * 1024 * 1024, back)
    }

    // ── configPairs: the full ordered matrix ────────────────────────────────

    @Test
    fun pairs_defaults_writeEveryOwnedKeyExplicitly() {
        // Defaults produce mpv-equivalent values — but the pairs are explicit
        // so the runtime diff can see a flip back to default as a change.
        assertEquals(
            listOf(
                MpvOption("scale", "bilinear"),
                MpvOption("deband", "no"),
                MpvOption("interpolation", "no"),
                MpvOption("video-sync", "audio"),
                MpvOption("framedrop", "decoder+vo"),
                MpvOption("vd-lavc-skiploopfilter", "default"),
                MpvOption("deinterlace", "auto"),
                MpvOption("demuxer-max-bytes", (64L * 1024 * 1024).toString()),
                MpvOption("demuxer-max-back-bytes", (32L * 1024 * 1024).toString()),
                MpvOption("audio-spdif", ""),
                MpvOption("audio-device", "auto"),
                MpvOption("audio-exclusive", "no"),
                MpvOption("tone-mapping", "auto"),
                // The BALANCED quality bundle's explicit defaults (no dir →
                // no glsl-shaders pair).
                MpvOption("dscale", "mipmap"),
                MpvOption("cscale", "bilinear"),
                MpvOption("dither-depth", "auto"),
            ),
            MpvConfigMapping.configPairs(MpvEngineConfig()),
        )
    }

    @Test
    fun pairs_videoPipeline_fieldsMapToTheirKeys() {
        val config = MpvEngineConfig(
            scaler = MpvScaler.EWA_LANCZOS_SHARP,
            deband = true,
            interpolation = true,
            frameDrop = MpvFrameDrop.VO,
            skipLoopFilter = MpvSkipLoopFilter.ALL,
        )
        val byKey = MpvConfigMapping.configPairs(config).associate { it.key to it.value }
        assertEquals("ewa_lanczossharp", byKey["scale"])
        assertEquals("yes", byKey["deband"])
        assertEquals("yes", byKey["interpolation"])
        assertEquals("display-resample", byKey["video-sync"])
        assertEquals("vo", byKey["framedrop"])
        assertEquals("all", byKey["vd-lavc-skiploopfilter"])
    }

    @Test
    fun pairs_interpolationOff_resetsVideoSyncToMpvDefault() {
        // The off value is explicit so the runtime diff sees on→off — an
        // omitted pair would leave `display-resample` stuck until restart.
        val byKey = MpvConfigMapping.configPairs(MpvEngineConfig(interpolation = false)).associate { it.key to it.value }
        assertEquals("no", byKey["interpolation"])
        assertEquals("audio", byKey["video-sync"])
        assertNull(byKey["tscale"])
    }

    @Test
    fun pairs_audioTrio_carriesTheConfig() {
        val config = MpvEngineConfig(
            audioDevice = "wasapi/{00000000-0000-0000-0000-000000000000}",
            audioExclusive = true,
            audioOutputMode = MpvAudioOutputMode.HDMI,
        )
        val byKey = MpvConfigMapping.configPairs(config, audioPassthrough = false).associate { it.key to it.value }
        assertEquals("wasapi/{00000000-0000-0000-0000-000000000000}", byKey["audio-device"])
        assertEquals("yes", byKey["audio-exclusive"])
        assertEquals("ac3,eac3,dts,dts-hd,truehd", byKey["audio-spdif"])
    }

    @Test
    fun pairs_extraConfig_appliesLastAndOverrides() {
        val config = MpvEngineConfig(
            scaler = MpvScaler.SPLINE36,
            mpvExtraConfig = """
                # comment lines are skipped

                scale=ewa_lanczossharp
                tscale=mitchell
            """.trimIndent(),
        )
        val pairs = MpvConfigMapping.configPairs(config)
        // Extras last (as a group, in line order): the raw `scale` line comes
        // AFTER the structured one, so a raw line overrides its structured
        // counterpart — the documented escape-hatch intent.
        assertEquals(MpvOption("tscale", "mitchell"), pairs.last())
        assertEquals(MpvOption("scale", "ewa_lanczossharp"), pairs[pairs.size - 2])
        // …and the override wins only by ORDER, not by removal of the
        // structured pair before it (the structured scale pair is still
        // emitted first).
        assertEquals("spline36", pairs.first().value)
        assertEquals(2, pairs.count { it.key == "scale" })
    }

    @Test
    fun pairs_bareExtraFlag_parsesAsYes() {
        val pairs = MpvConfigMapping.configPairs(MpvEngineConfig(mpvExtraConfig = "hm"))
        assertEquals(MpvOption("hm", "yes"), pairs.last())
    }

    // ── applyChanged: the runtime diff-then-write discipline ────────────────

    @Test
    fun applyChanged_firstApplication_writesEverything() {
        val written = mutableListOf<MpvOption>()
        val applied = MpvConfigMapping.applyChanged(
            MpvConfigMapping.configPairs(MpvEngineConfig(deband = true)),
            lastApplied = emptyMap(),
            write = { key, value -> written.add(MpvOption(key, value)) },
        )
        assertEquals(MpvConfigMapping.configPairs(MpvEngineConfig(deband = true)).associate { it.key to it.value }, applied)
        assertEquals("yes", written.single { it.key == "deband" }.value)
    }

    @Test
    fun applyChanged_unchangedReapply_performsZeroWrites() {
        val config = MpvEngineConfig(deband = true, interpolation = true)
        val pairs = MpvConfigMapping.configPairs(config)
        var applied = MpvConfigMapping.applyChanged(pairs, emptyMap()) { _, _ -> }
        val written = mutableListOf<MpvOption>()
        applied = MpvConfigMapping.applyChanged(pairs, applied) { key, value -> written.add(MpvOption(key, value)) }
        assertTrue(written.isEmpty(), "an unchanged re-push must write nothing, wrote $written")
        assertEquals(pairs.associate { it.key to it.value }, applied)
    }

    @Test
    fun applyChanged_flipToDefault_writesTheExplicitDefault() {
        // audio-exclusive true→false must WRITE "no" — an omitted-pair
        // representation would leave the device locked.
        val on = MpvConfigMapping.applyChanged(
            MpvConfigMapping.configPairs(MpvEngineConfig(audioExclusive = true)),
            emptyMap(),
        ) { _, _ -> }
        val written = mutableListOf<MpvOption>()
        MpvConfigMapping.applyChanged(
            MpvConfigMapping.configPairs(MpvEngineConfig(audioExclusive = false)),
            on,
        ) { key, value -> written.add(MpvOption(key, value)) }
        assertEquals(listOf(MpvOption("audio-exclusive", "no")), written)
    }

    @Test
    fun applyChanged_partialChange_writesOnlyTheChangedKeys() {
        val before = MpvConfigMapping.applyChanged(
            MpvConfigMapping.configPairs(MpvEngineConfig(deband = false, scaler = MpvScaler.BILINEAR)),
            emptyMap(),
        ) { _, _ -> }
        val written = mutableListOf<MpvOption>()
        MpvConfigMapping.applyChanged(
            MpvConfigMapping.configPairs(MpvEngineConfig(deband = true, scaler = MpvScaler.BILINEAR)),
            before,
        ) { key, value -> written.add(MpvOption(key, value)) }
        assertEquals(listOf(MpvOption("deband", "yes")), written)
    }

    // ── applyMpvConfig: the init-order convenience ─────────────────────────

    @Test
    fun applyMpvConfig_writesAllPairsInOrder() {
        val written = mutableListOf<MpvOption>()
        MpvConfigMapping.applyMpvConfig(
            setter = { key, value -> written.add(MpvOption(key, value)) },
            config = MpvEngineConfig(),
            audioPassthrough = true,
        )
        assertEquals(
            MpvConfigMapping.configPairs(MpvEngineConfig(), audioPassthrough = true),
            written,
        )
        // Passthrough flows through to the spdif pair at init too.
        assertEquals("ac3,eac3,dts,dtshd,truehd", written.single { it.key == "audio-spdif" }.value)
    }

    // ── Shader packs: chain files, path resolution, glsl-shaders ───

    @Test
    fun anime4kChains_matchTheOfficialV4Recipes() {
        // Order is load-bearing — each stage feeds the next.
        assertEquals(
            listOf(
                "Anime4K_Clamp_Highlights.glsl",
                "Anime4K_Restore_CNN_VL.glsl",
                "Anime4K_Upscale_CNN_x2_VL.glsl",
                "Anime4K_AutoDownscalePre_x2.glsl",
                "Anime4K_AutoDownscalePre_x4.glsl",
                "Anime4K_Upscale_CNN_x2_M.glsl",
            ),
            MpvConfigMapping.anime4kChain(MpvShaderPack.ANIME4K_A),
        )
        assertEquals(
            listOf(
                "Anime4K_Clamp_Highlights.glsl",
                "Anime4K_Restore_CNN_Soft_VL.glsl",
                "Anime4K_Upscale_CNN_x2_VL.glsl",
                "Anime4K_AutoDownscalePre_x2.glsl",
                "Anime4K_AutoDownscalePre_x4.glsl",
                "Anime4K_Upscale_CNN_x2_M.glsl",
            ),
            MpvConfigMapping.anime4kChain(MpvShaderPack.ANIME4K_B),
        )
        assertEquals(
            listOf(
                "Anime4K_Clamp_Highlights.glsl",
                "Anime4K_Upscale_Denoise_CNN_x2_VL.glsl",
                "Anime4K_AutoDownscalePre_x2.glsl",
                "Anime4K_AutoDownscalePre_x4.glsl",
                "Anime4K_Upscale_CNN_x2_M.glsl",
            ),
            MpvConfigMapping.anime4kChain(MpvShaderPack.ANIME4K_C),
        )
        assertTrue(MpvConfigMapping.anime4kChain(MpvShaderPack.OFF).isEmpty())
        assertTrue(MpvConfigMapping.anime4kChain(MpvShaderPack.CUSTOM).isEmpty())
    }

    @Test
    fun shaderPaths_resolveChainFilesAgainstTheExtractedDir() {
        val config = MpvEngineConfig(shaderPack = MpvShaderPack.ANIME4K_C)
        assertEquals(
            listOf(
                "C:/shaders/Anime4K_Clamp_Highlights.glsl",
                "C:/shaders/Anime4K_Upscale_Denoise_CNN_x2_VL.glsl",
                "C:/shaders/Anime4K_AutoDownscalePre_x2.glsl",
                "C:/shaders/Anime4K_AutoDownscalePre_x4.glsl",
                "C:/shaders/Anime4K_Upscale_CNN_x2_M.glsl",
            ),
            MpvConfigMapping.shaderPaths(config, "C:/shaders"),
        )
    }

    @Test
    fun shaderPaths_noDir_anime4kPackIsUnresolvable_offIsEmpty_customIsVerbatim() {
        // No extraction dir (Android): the Anime4K pack cannot resolve → null
        // (the caller omits the pair rather than feeding mpv garbage).
        assertNull(MpvConfigMapping.shaderPaths(MpvEngineConfig(shaderPack = MpvShaderPack.ANIME4K_A), null))
        // OFF is empty — resolvable, but nothing to play.
        assertEquals(emptyList(), MpvConfigMapping.shaderPaths(MpvEngineConfig(), "C:/shaders"))
        // CUSTOM plays the user's absolute paths verbatim, dir not consulted.
        val custom = listOf("/home/u/fsrcnnx_x2.glsl", "/home/u/ArtCNN.glsl")
        assertEquals(
            custom,
            MpvConfigMapping.shaderPaths(
                MpvEngineConfig(shaderPack = MpvShaderPack.CUSTOM, customShaderFiles = custom),
                null,
            ),
        )
    }

    @Test
    fun pairs_glslShaders_emittedWhenResolvable_explicitClearWhenOff() {
        val dir = "C:/shaders"
        // Pack on → joined absolute paths.
        val on = MpvConfigMapping.configPairs(
            MpvEngineConfig(shaderPack = MpvShaderPack.ANIME4K_A),
            shaderDir = dir,
        ).single { it.key == "glsl-shaders" }
        assertTrue(on.value.startsWith("$dir/Anime4K_Clamp_Highlights.glsl,$dir/"), on.value)
        // Pack off (with a dir) → an explicit empty list so the runtime diff
        // cache sees the pack→OFF transition as a changed pair.
        val off = MpvConfigMapping.configPairs(MpvEngineConfig(), shaderDir = dir)
            .single { it.key == "glsl-shaders" }
        assertEquals("", off.value)
        // No dir + pack off (Android defaults) → pair omitted entirely.
        assertNull(MpvConfigMapping.configPairs(MpvEngineConfig()).firstOrNull { it.key == "glsl-shaders" })
    }

    // ── Tone mapping + the HDR-passthrough default ─────────

    @Test
    fun toneMapping_auto_writesMpvDefault() {
        // Explicit "auto" so the runtime diff can express preset→AUTO.
        assertEquals(
            "auto",
            MpvConfigMapping.configPairs(MpvEngineConfig()).single { it.key == "tone-mapping" }.value,
        )
    }

    @Test
    fun toneMapping_presets_mapToMpvValues() {
        val expected = mapOf(
            MpvToneMapping.BT2390 to "bt.2390",
            MpvToneMapping.HABLE to "hable",
            MpvToneMapping.REINHARD to "reinhard",
            MpvToneMapping.MOBIUS to "mobius",
            MpvToneMapping.CLIP to "clip",
            MpvToneMapping.GAMMA to "gamma",
        )
        for ((preset, value) in expected) {
            val pair = MpvConfigMapping.configPairs(MpvEngineConfig(toneMapping = preset))
                .single { it.key == "tone-mapping" }
            assertEquals(value, pair.value, "preset $preset")
        }
    }

    @Test
    fun toneMapping_activeHdrPassthrough_fallsBackToMpvDefault() {
        val config = MpvEngineConfig(toneMapping = MpvToneMapping.BT2390)
        // Passthrough requested but not active → tone mapping written.
        assertEquals(
            "bt.2390",
            MpvConfigMapping.configPairs(config, toneMappingSuppressed = false)
                .single { it.key == "tone-mapping" }.value,
        )
        // Active (setting on + HDR item + HDR display target) → mpv's `auto`
        // default, HDR→HDR: the colorspace-hint path owns the output, and a
        // stale preset from an earlier SDR session is explicitly unstuck.
        assertEquals(
            "auto",
            MpvConfigMapping.configPairs(config, toneMappingSuppressed = true)
                .single { it.key == "tone-mapping" }.value,
        )
    }

    @Test
    fun applyChanged_presetToAuto_andInterpolationOff_writeTheResets() {
        // The transition that motivated explicit defaults: preset→AUTO and
        // interpolation on→off must WRITE through the runtime diff, not
        // silently omit and leave the old value live until restart.
        val before = MpvConfigMapping.applyChanged(
            MpvConfigMapping.configPairs(
                MpvEngineConfig(toneMapping = MpvToneMapping.BT2390, interpolation = true),
            ),
            emptyMap(),
        ) { _, _ -> }
        val written = mutableListOf<MpvOption>()
        MpvConfigMapping.applyChanged(
            MpvConfigMapping.configPairs(MpvEngineConfig(toneMapping = MpvToneMapping.AUTO)),
            before,
        ) { key, value -> written.add(MpvOption(key, value)) }
        assertEquals(
            listOf(
                MpvOption("interpolation", "no"),
                MpvOption("video-sync", "audio"),
                MpvOption("tone-mapping", "auto"),
            ),
            written,
        )
    }

    // ── Render quality bundle ───────────────────────────────────────

    @Test
    fun renderQuality_balanced_forcesNoScaleOrDeband_writesBundleDefaults() {
        // BALANCED: the structured scale/deband rows stand (exactly ONE
        // structured pair each), while the bundle-only keys are written at
        // their mpv defaults — explicitly, so the diff cache sees a flip.
        val pairs = MpvConfigMapping.configPairs(
            MpvEngineConfig(renderQuality = MpvRenderQuality.BALANCED),
        )
        assertEquals(1, pairs.count { it.key == "scale" })
        assertEquals("bilinear", pairs.single { it.key == "scale" }.value)
        assertEquals(1, pairs.count { it.key == "deband" })
        assertEquals("mipmap", pairs.single { it.key == "dscale" }.value)
        assertEquals("bilinear", pairs.single { it.key == "cscale" }.value)
        assertEquals("auto", pairs.single { it.key == "dither-depth" }.value)
    }

    @Test
    fun renderQuality_highAndPerformance_forceTheBundleOverTheStructuredRows() {
        // The structured row says spline36/deband-off; the bundle must win by
        // order (later same-key pairs win — the mapper's override convention).
        fun bundle(quality: MpvRenderQuality): List<MpvOption> =
            MpvConfigMapping.configPairs(
                MpvEngineConfig(scaler = MpvScaler.SPLINE36, renderQuality = quality),
            ).filter { it.key in setOf("scale", "dscale", "cscale", "dither-depth", "deband") }

        val high = bundle(MpvRenderQuality.HIGH)
        assertEquals(
            listOf(
                MpvOption("scale", "spline36"),
                MpvOption("deband", "no"),
                MpvOption("scale", "ewa_lanczossharp"),
                MpvOption("dscale", "spline36"),
                MpvOption("cscale", "spline36"),
                MpvOption("dither-depth", "auto"),
                MpvOption("deband", "yes"),
            ),
            high,
        )
        assertEquals("bilinear", bundle(MpvRenderQuality.PERFORMANCE).single { it.key == "scale" && it.value == "bilinear" }.value)
        assertEquals("no", bundle(MpvRenderQuality.PERFORMANCE).last { it.key == "deband" }.value)
    }

    @Test
    fun renderQuality_flipToBalanced_rewritesTheBundleKeys() {
        // The bundle's dscale/cscale/dither-depth keys are ALWAYS written
        // (defaults included) so flipping HIGH→BALANCED through the diff
        // cache writes explicit defaults instead of leaving stale values.
        val high = MpvConfigMapping.applyChanged(
            MpvConfigMapping.configPairs(
                MpvEngineConfig(renderQuality = MpvRenderQuality.HIGH),
            ),
            emptyMap(),
        ) { _, _ -> }
        val written = mutableListOf<MpvOption>()
        MpvConfigMapping.applyChanged(
            MpvConfigMapping.configPairs(MpvEngineConfig(renderQuality = MpvRenderQuality.BALANCED)),
            high,
        ) { key, value -> written.add(MpvOption(key, value)) }
        // dscale/cscale/scale/deband all changed back; dither-depth was
        // already "auto" under HIGH, so the diff writes it zero times. The
        // write order follows the pair list: structured rows first, bundle
        // keys after.
        assertEquals(
            listOf(
                MpvOption("scale", "bilinear"),
                MpvOption("deband", "no"),
                MpvOption("dscale", "mipmap"),
                MpvOption("cscale", "bilinear"),
            ),
            written,
        )
    }

    // ── Interpolation tscale ───────────────────────────────────────

    @Test
    fun tscale_emittedOnlyWithInterpolation() {
        // Off → neither video-sync nor tscale.
        val off = MpvConfigMapping.configPairs(MpvEngineConfig()).associate { it.key to it.value }
        assertNull(off["tscale"])
        // On → the preset rides video-sync.
        val on = MpvConfigMapping.configPairs(
            MpvEngineConfig(interpolation = true, interpolationTscale = MpvInterpolationTscale.OVERSAMPLE),
        ).associate { it.key to it.value }
        assertEquals("display-resample", on["video-sync"])
        assertEquals("oversample", on["tscale"])
        assertEquals("mitchell", MpvConfigMapping.tscaleValue(MpvEngineConfig(interpolation = true)))
        assertNull(MpvConfigMapping.tscaleValue(MpvEngineConfig()))
    }

    // ── Deinterlace ────────────────────────────────────────────────

    @Test
    fun deinterlace_mapsEveryModeExplicitly() {
        assertEquals("auto", MpvConfigMapping.deinterlaceKey(DeinterlaceMode.AUTO))
        assertEquals("yes", MpvConfigMapping.deinterlaceKey(DeinterlaceMode.ON))
        assertEquals("no", MpvConfigMapping.deinterlaceKey(DeinterlaceMode.OFF))
        for (mode in DeinterlaceMode.entries) {
            assertEquals(
                MpvConfigMapping.deinterlaceKey(mode),
                MpvConfigMapping.configPairs(MpvEngineConfig(), deinterlace = mode)
                    .single { it.key == "deinterlace" }.value,
            )
        }
    }
}
