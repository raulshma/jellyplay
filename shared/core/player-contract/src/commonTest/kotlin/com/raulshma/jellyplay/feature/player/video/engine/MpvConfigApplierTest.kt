package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.MpvHwdec
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvPropertySurface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Dispatch pin for [MpvConfigApplier] — the shared mpv config-delta
 * choreography, driven over a RECORDING [MpvPropertySurface] and recording
 * hooks (the AudioQueueStateCoreTest EngineDispatch-recording pattern: no
 * mpv handle, every test asserts the DECISIONS — which arms fired, in which
 * order, what reached the surface). This is the pin the two engines' former
 * hand-mirrored `onConfigChanged` ladders never had: the ordering rules
 * drifted once before (the same ownership-refresh bug both engine files'
 * comments record), so the sequence here is the contract.
 */
class MpvConfigApplierTest {

    /**
     * One ordered event log shared by the surface and the hooks — the
     * cross-order between hook invocations and property writes is exactly
     * what the choreography owns.
     */
    private class Harness(deadSurface: Boolean = false) {
        val log = mutableListOf<String>()

        val surface = if (deadSurface) {
            null
        } else {
            object : MpvPropertySurface {
                override fun setOptionString(name: String, value: String) { log += "opt $name=$value" }
                override fun setPropertyString(name: String, value: String) { log += "str $name=$value" }
                override fun setPropertyDouble(name: String, value: Double) { log += "dbl $name=$value" }
                override fun setPropertyInt(name: String, value: Int) { log += "int $name=$value" }
                override fun setPropertyBoolean(name: String, value: Boolean) { log += "bool $name=$value" }
            }
        }

        val applier = MpvConfigApplier(
            surface = { surface },
            extras = { MpvConfigApplier.Extras(lowRamDevice = false, shaderDir = null, toneMappingSuppressed = false) },
            refreshOwnedKeys = { log += "owned" },
            hwdecValue = { cfg -> "hwdec(${cfg.decoderMode})" },
            applySubtitleStyle = { cfg -> log += "style(${cfg.subtitleStyle.fontSize})" },
            applyAudioEffects = { _, _, delta, full -> log += "audio(af=${delta.audioAfChainChanged},full=$full)" },
            applyVideoEffects = { cfg -> log += "video(${cfg.videoEffects.brightness})" },
        )
    }

    private val base = EngineConfig(engineSpecific = MpvEngineConfig())

    // ── the delta dispatch order ─────────────────────────────────────────────

    @Test
    fun applyDelta_dispatchesEveryChangedArmInThePinnedOrder() {
        val harness = Harness()
        // Seed the diff cache so the shared-pairs arm's writes are exactly the
        // changed keys (a first-apply writes every owned pair and would drown
        // the order signal).
        harness.applier.applyFull(base)
        harness.log.clear()

        val changed = base.copy(
            audioDelayMs = 250,
            subtitleDelayMs = -500,
            decoderMode = DecoderMode.SW_ONLY,
            // The passthrough boolean is a shared-pairs INPUT: its flip changes
            // exactly one pair (audio-spdif), deterministically.
            audioPassthrough = true,
            subtitleStyle = SubtitleStyle(fontSize = 31),
            videoEffects = VideoEffectsConfig(brightness = 0.2f),
        )
        harness.applier.applyDelta(base, changed)

        assertEquals(
            listOf(
                "owned",                                        // 1. ownership refresh, FIRST
                "dbl audio-delay=0.25",                         // 2. audio-delay
                "dbl sub-delay=-0.5",                           // 3. sub-delay
                "str hwdec=hwdec(SW_ONLY)",                     // 4. hwdec (decoderMode moved)
                "str audio-spdif=${MpvConfigMapping.SPDIF_LEGACY_PASSTHROUGH}", // 5. shared pairs re-diff
                "style(31)",                                    // 6. subtitle style
                "audio(af=false,full=false)",                   // 7. audio hook (engine-gated inside)
                "video(0.2)",                                   // 8. video effects
            ),
            harness.log,
            "the dispatch order is the contract both engines ride",
        )
    }

    @Test
    fun ownershipRefreshRunsBeforeTheStyleReapply_onAStyleOnlyChange() {
        val harness = Harness()
        harness.applier.applyFull(base)
        harness.log.clear()

        // Only the style moved: no delay/hwdec/pairs writes may land, and the
        // ownership refresh must precede the style consult (the fixed bug both
        // engines' ladders record).
        harness.applier.applyDelta(base, base.copy(subtitleStyle = SubtitleStyle(fontSize = 40)))

        // The audio hook is always invoked (engine-gated inside); no write of
        // any kind lands between the refresh and the style consult.
        assertEquals(listOf("owned", "style(40)", "audio(af=false,full=false)"), harness.log)
    }

    @Test
    fun noopDeltaRefreshesOwnershipAndConsultsTheAudioHook_butWritesNothing() {
        val harness = Harness()
        harness.applier.applyFull(base)
        harness.log.clear()

        harness.applier.applyDelta(base, base)

        // The audio hook is always invoked (the engines gate their native
        // arms on the delta inside it); every mpv write is cache/delta-gated.
        assertEquals(listOf("owned", "audio(af=false,full=false)"), harness.log)
    }

    // ── the hwdec arm's union condition ──────────────────────────────────────

    @Test
    fun hwdecRewrites_whenOnlyTheEngineSpecificOverrideMoved() {
        val harness = Harness()
        harness.applier.applyFull(base)
        harness.log.clear()

        // hwdecOverride lives in engineSpecific — an override-only change must
        // fire the hwdec arm (Android's condition) alongside the pairs re-diff
        // (engineSpecific moved ⇒ sharedPairsChanged).
        val override = base.copy(
            engineSpecific = MpvEngineConfig(hwdecOverride = MpvHwdec.MEDIACODEC_HW_ONLY),
        )
        harness.applier.applyDelta(base, override)

        assertTrue("str hwdec=hwdec(HW_PREFERRED)" in harness.log, harness.log.toString())
        assertTrue("owned" == harness.log.first(), harness.log.toString())
    }

    @Test
    fun hwdecDoesNotRewrite_whenNeitherTheModeNorTheOverrideMoved() {
        val harness = Harness()
        harness.applier.applyFull(base)
        harness.log.clear()

        harness.applier.applyDelta(base, base.copy(videoEffects = VideoEffectsConfig(brightness = 0.5f)))

        assertTrue(harness.log.none { it.startsWith("str hwdec=") }, harness.log.toString())
        assertTrue("video(0.5)" in harness.log, harness.log.toString())
    }

    // ── the full apply (FILE_LOADED) ─────────────────────────────────────────

    @Test
    fun applyFull_forcesEveryArm_andCachesThePairs() {
        val harness = Harness()
        harness.applier.applyFull(base)

        assertEquals("owned", harness.log.first(), "ownership refresh first")
        assertTrue("dbl audio-delay=0.0" in harness.log, harness.log.toString())
        assertTrue("dbl sub-delay=0.0" in harness.log, harness.log.toString())
        assertTrue("str hwdec=hwdec(HW_PREFERRED)" in harness.log, harness.log.toString())
        // The shared pairs: every owned key written once from the empty cache…
        assertTrue("str scale=bilinear" in harness.log, harness.log.toString())
        assertTrue("str tone-mapping=auto" in harness.log, harness.log.toString())
        // …then style, audio hook (full), video.
        assertTrue(
            harness.log.indexOf("style(24)") > harness.log.indexOf("str tone-mapping=auto") &&
                harness.log.indexOf("audio(af=false,full=true)") > harness.log.indexOf("style(24)") &&
                harness.log.indexOf("video(0.0)") > harness.log.indexOf("audio(af=false,full=true)"),
            harness.log.toString(),
        )

        // A repeated full apply re-forces the cheap scalars (delays + hwdec)
        // but writes no structured pair (the cache made the re-diff
        // write-free — the pacing rule).
        harness.log.clear()
        harness.applier.applyFull(base)
        assertTrue(harness.log.none { it.startsWith("str ") && !it.startsWith("str hwdec=") }, harness.log.toString())
        assertTrue("str hwdec=hwdec(HW_PREFERRED)" in harness.log, harness.log.toString())
        assertTrue("dbl audio-delay=0.0" in harness.log, harness.log.toString())
        assertTrue("video(0.0)" in harness.log, harness.log.toString())
    }

    @Test
    fun seededCacheMatchesTheInitTransport_androidsInitOptions() {
        val harness = Harness()
        // Android's initOptions writes OPTIONS (not through the applier) and
        // seeds the cache with exactly the pairs it wrote: a runtime push of
        // the SAME config must then write nothing.
        val pairs = MpvConfigMapping.configPairs(
            config = MpvEngineConfig(),
            audioPassthrough = false,
            passthroughCodecs = com.raulshma.jellyplay.core.model.AudioPassthroughCodec.ALL,
            lowRamDevice = false,
            deinterlace = com.raulshma.jellyplay.core.model.DeinterlaceMode.AUTO,
        )
        harness.applier.seedAppliedConfigProps(pairs.associate { it.key to it.value })

        harness.applier.applyDelta(base, base.copy(audioEffects = base.audioEffects))

        assertTrue(
            harness.log.none { it.startsWith("str ") },
            "an unchanged config push after an init seed performs zero writes: ${harness.log}",
        )
    }

    // ── the dead-handle guard ────────────────────────────────────────────────

    @Test
    fun nullSurfaceSkipsTheWholeApply_theEnginesEarlyReturn() {
        val harness = Harness(deadSurface = true)
        harness.applier.applyFull(base)
        harness.applier.applyDelta(base, base.copy(audioDelayMs = 5))
        assertTrue(harness.log.isEmpty(), "no hook may run against a dead handle: ${harness.log}")
    }

    @Test
    fun extrasReachTheSharedPairsArm() {
        val log = mutableListOf<String>()
        val liveSurface = object : MpvPropertySurface {
            override fun setOptionString(name: String, value: String) { log += "opt $name=$value" }
            override fun setPropertyString(name: String, value: String) { log += "str $name=$value" }
            override fun setPropertyDouble(name: String, value: Double) { log += "dbl $name=$value" }
            override fun setPropertyInt(name: String, value: Int) { log += "int $name=$value" }
            override fun setPropertyBoolean(name: String, value: Boolean) { log += "bool $name=$value" }
        }
        val applier = MpvConfigApplier(
            surface = { liveSurface },
            extras = { MpvConfigApplier.Extras(lowRamDevice = true, shaderDir = "/glsl", toneMappingSuppressed = false) },
            refreshOwnedKeys = { },
            hwdecValue = { "hwdec" },
            applySubtitleStyle = { },
            applyAudioEffects = { _, _, _, _ -> },
            applyVideoEffects = { },
        )
        applier.applyFull(
            base.copy(
                engineSpecific = MpvEngineConfig(shaderPack = com.raulshma.jellyplay.core.model.MpvShaderPack.ANIME4K_A),
                deinterlace = com.raulshma.jellyplay.core.model.DeinterlaceMode.ON,
            ),
        )
        // lowRamDevice=true picks the AUTO low demuxer budget; ANIME4K_A with a
        // shaderDir resolves its ordered chain; deinterlace rides the base config.
        assertTrue("str deinterlace=yes" in log, log.toString())
        assertTrue(
            "str glsl-shaders=/glsl/Anime4K_Clamp_Highlights.glsl,/glsl/Anime4K_Restore_CNN_VL.glsl,/glsl/Anime4K_Upscale_CNN_x2_VL.glsl,/glsl/Anime4K_AutoDownscalePre_x2.glsl,/glsl/Anime4K_AutoDownscalePre_x4.glsl,/glsl/Anime4K_Upscale_CNN_x2_M.glsl" in log,
            log.toString(),
        )
        assertTrue(
            "str demuxer-max-bytes=${32L * 1024 * 1024}" in log,
            "lowRamDevice=true must pick the AUTO low budget: $log",
        )
    }
}
