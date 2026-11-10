package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.AudioPassthroughCodec
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.LibVlcEngineConfig
import com.raulshma.jellyplay.core.model.MaxAudioChannelsEnum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pure libVLC instance-option fold ([buildLibVlcInstanceOptions])
 * and — because the list is also the engine's cached-handle key — the
 * equality policy the reuse decision rides on: identical inputs MUST fold to
 * an equal list (the binge-advance reuse hit) and every input the native
 * instance config depends on MUST move the list (otherwise a stale handle
 * would keep serving a changed config). Byte-exact rows pin the composition;
 * the engine reads no option source outside this fold.
 */
class LibVlcInstanceOptionsTest {

    private fun build(
        config: EngineConfig = EngineConfig(),
        preferredAudioLanguage: String? = null,
        preferredSubtitleLanguage: String? = null,
        hasRenderer: Boolean = false,
        isLowRamDevice: Boolean = false,
    ): List<String> = buildLibVlcInstanceOptions(
        config = config,
        preferredAudioLanguage = preferredAudioLanguage,
        preferredSubtitleLanguage = preferredSubtitleLanguage,
        hasRenderer = hasRenderer,
        isLowRamDevice = isLowRamDevice,
    )

    @Test
    fun `default config produces the documented baseline option list`() {
        val options = build()

        assertEquals(
            listOf(
                "--aout=aaudio",
                "--audio-time-stretch",
                "--avcodec-skiploopfilter",
                "1",
                "--avcodec-skip-idct",
                "0",
                "--network-caching=3000",
                "--drop-late-frames",
                "--skip-frames",
            ),
            options,
        )
    }

    @Test
    fun `identical inputs fold to equal lists - the reuse cache hit`() {
        val first = build(preferredAudioLanguage = "jpn")
        val second = build(preferredAudioLanguage = "jpn")

        // Structural equality is the whole reuse contract: the engine hands
        // each load's fold to VlcHandleCache.reusableFor and reuses the cached
        // libVLC instance when the lists are equal.
        assertEquals(first, second)
    }

    @Test
    fun `a language preference change moves the list - the recreate key`() {
        val baseline = build()
        val withAudioLang = build(preferredAudioLanguage = "jpn")
        val withSubLang = build(preferredSubtitleLanguage = "eng")

        assertTrue(withAudioLang.contains("--audio-language=jpn"))
        assertTrue(withSubLang.contains("--sub-language=eng"))
        assertNotEquals(baseline, withAudioLang)
        assertNotEquals(baseline, withSubLang)
        assertNotEquals(withAudioLang, withSubLang)
    }

    @Test
    fun `blank language preferences compose no language option`() {
        assertEquals(
            build(preferredAudioLanguage = null),
            build(preferredAudioLanguage = "  "),
        )
    }

    @Test
    fun `low-RAM tier swaps the caching default and adds the clock options`() {
        assertEquals(
            listOf(
                "--aout=aaudio",
                "--audio-time-stretch",
                "--avcodec-skiploopfilter",
                "1",
                "--avcodec-skip-idct",
                "0",
                "--network-caching=1500",
                "--clock-jitter=0",
                "--clock-synchro=0",
                "--drop-late-frames",
                "--skip-frames",
            ),
            build(isLowRamDevice = true),
        )
    }

    @Test
    fun `explicit network caching value overrides the tier default`() {
        val options = build(
            config = EngineConfig(engineSpecific = LibVlcEngineConfig(networkCaching = 800)),
        )

        assertTrue(options.contains("--network-caching=800"))
        assertNotEquals(build(), options)
    }

    @Test
    fun `null engineSpecific folds to the same list as the default slice`() {
        assertEquals(
            build(config = EngineConfig(engineSpecific = LibVlcEngineConfig())),
            build(config = EngineConfig(engineSpecific = null)),
        )
    }

    @Test
    fun `passthrough composes the codec allowlist only without a renderer`() {
        val passthrough = EngineConfig(
            audioPassthrough = true,
            audioPassthroughCodecs = setOf(AudioPassthroughCodec.EAC3, AudioPassthroughCodec.AC3),
        )

        val direct = build(config = passthrough)
        assertTrue(direct.contains("--codec=ac3,eac3"))

        val cast = build(config = passthrough, hasRenderer = true)
        // Renderer playback transcodes through sout instead: the allowlist is
        // dropped, the sout posture appears.
        assertTrue(cast.none { it.startsWith("--codec=") })
        assertEquals(listOf("--sout-keep", "--sout-chromecast-conversion-quality=2"), cast.takeLast(2))
        assertNotEquals(direct, cast)
    }

    @Test
    fun `night mode and DYNAMIC normalization share one compressor filter`() {
        val both = EngineConfig(
            audioEffects = AudioEffectsConfig(
                nightModeEnabled = true,
                nightModeStrength = EffectStrength.MODERATE,
                audioNormalizationEnabled = true,
                audioNormalizationMode = AudioNormalizationMode.DYNAMIC,
            ),
        )
        val options = build(config = both)

        // ONE --audio-filter (duplicate options resolve last-wins, so the
        // filters must compose into a single comma list)…
        assertEquals(1, options.count { it.startsWith("--audio-filter=") })
        assertTrue(options.contains("--audio-filter=compressor"))
        // …night mode's param set (MODERATE row), and the DYNAMIC arm's fixed
        // pair is skipped — a single filter instance takes ONE param set.
        assertTrue(
            options.containsAll(
                listOf("--compressor-ratio=4", "--compressor-threshold=-18", "--compressor-makeup-gain=3"),
            ),
        )
        assertTrue(options.none { it == "--compressor-ratio=3" })
    }

    @Test
    fun `TRACK normalization composes normvol with the norm cap`() {
        val track = EngineConfig(
            audioEffects = AudioEffectsConfig(
                audioNormalizationEnabled = true,
                audioNormalizationMode = AudioNormalizationMode.TRACK,
            ),
        )
        val options = build(config = track)

        assertTrue(options.contains("--audio-filter=normvol"))
        assertTrue(options.contains("--norm-max-level=0.8"))
        assertTrue(options.none { it.startsWith("--compressor") })
    }

    @Test
    fun `channel mix mode and the channel cap compose with the cap winning`() {
        val both = EngineConfig(
            audioEffects = AudioEffectsConfig(
                channelMixMode = ChannelMixMode.MONO,
                maxAudioChannels = MaxAudioChannelsEnum.STEREO,
            ),
        )
        val options = build(config = both)

        // Both rows appear — VLC resolves duplicate --stereo-mode last-wins,
        // so the pinned ORDER is the behavior: the cap (appended after) wins.
        val monoIndex = options.indexOf("--stereo-mode=mono")
        val stereoIndex = options.indexOf("--stereo-mode=stereo")
        assertTrue(monoIndex in 0 until stereoIndex)
    }

    @Test
    fun `audio delay emits the audio-desync option`() {
        val delayed = EngineConfig(audioDelayMs = 250L)
        val options = build(config = delayed)

        assertTrue(options.contains("--audio-desync=250"))
        assertNotEquals(build(), options)
    }
}
