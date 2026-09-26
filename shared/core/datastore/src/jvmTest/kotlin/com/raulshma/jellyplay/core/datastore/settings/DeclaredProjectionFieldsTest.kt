package com.raulshma.jellyplay.core.datastore.settings

import com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice
import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheSlice
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice
import com.raulshma.jellyplay.core.datastore.library.LibrarySlice
import com.raulshma.jellyplay.core.datastore.navigation.NavigationSlice
import com.raulshma.jellyplay.core.model.AudioCacheNetworkPolicy
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.model.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the DECLARED projection field-sets (DeclaredProjectionFields.kt) against
 * explicit, hand-written expected constructions — the guard that makes the
 * declared-set derivation safe: if a field drops out of a declared holder or
 * an assembly misroutes a slice field, these full-equality assertions fail.
 *
 * Every distinct value used below is non-default so a misroute cannot
 * accidentally match.
 */
class DeclaredProjectionFieldsTest {

    private val audio = AudioSlice(
        audioDefaultSpeed = 1.25f,
        audioNightModeVolume = 0.6f,
        audioNightModeGain = 900,
        audioSkipPreviousThresholdMs = 4_000L,
        audioAutoplayNext = false,
        audioPreloadBufferSize = PreloadBufferSize.LOW,
        audioNormalizationMode = AudioNormalizationMode.TRACK,
        audioNormalizationEnabled = true,
        replayGainPreAmpDb = 1.5f,
        channelMixMode = ChannelMixMode.STEREO_DOWNMIX,
        channelMixEnabled = true,
        audioGaplessEnabled = false,
        audioCrossfadeDurationMs = 2_500L,
        audioDelayMs = 150L,
        audioVisualizerEnabled = true,
        sleepTimerDurationMs = 900_000L,
    )
    private val effects = AudioEffectsSlice(
        equalizerEnabled = true,
        equalizerPreset = EqualizerPreset.BASS_BOOST,
        bassBoostEnabled = true,
        bassBoostStrength = EffectStrength.HIGH,
        virtualizerEnabled = true,
        virtualizerStrength = 700,
        reverbPreset = ReverbPreset.PLATE,
        lrBalance = -0.25f,
        autoEqByGenre = true,
        pitchSemitones = -2f,
        dialogueBoostEnabled = true,
        dialogueBoostStrength = EffectStrength.LOW,
        nightModeEnabled = true,
        nightModeStrength = EffectStrength.MODERATE,
        volumeBoostEnabled = true,
        volumeBoostGain = 6,
    )

    @Test
    fun `audio player projection equals the hand-written field list`() {
        val derived = audioSurfaceValues(audio, effects).toAudioPlayerPreferences()

        // Explicitly named construction — the lane's field set, written out.
        val expected = com.raulshma.jellyplay.core.model.AudioPlayerPreferences(
            audioDefaultSpeed = 1.25f,
            audioNightModeVolume = 0.6f,
            audioNightModeGain = 900,
            audioSkipPreviousThresholdMs = 4_000L,
            audioAutoplayNext = false,
            audioPreloadBufferSize = PreloadBufferSize.LOW,
            audioNormalizationMode = AudioNormalizationMode.TRACK,
            audioNormalizationEnabled = true,
            replayGainPreAmpDb = 1.5f,
            channelMixMode = ChannelMixMode.STEREO_DOWNMIX,
            channelMixEnabled = true,
            audioGaplessEnabled = false,
            audioCrossfadeDurationMs = 2_500L,
            equalizerEnabled = true,
            equalizerSettings = effects.equalizerSettings,
            equalizerPreset = EqualizerPreset.BASS_BOOST,
            bassBoostEnabled = true,
            bassBoostStrength = EffectStrength.HIGH,
            virtualizerEnabled = true,
            virtualizerStrength = 700,
            reverbPreset = ReverbPreset.PLATE,
            lrBalance = -0.25f,
            autoEqByGenre = true,
            pitchSemitones = -2f,
            audioDelayMs = 150L,
            dialogueBoostEnabled = true,
            dialogueBoostStrength = EffectStrength.LOW,
            nightModeEnabled = true,
            nightModeStrength = EffectStrength.MODERATE,
            audioVisualizerEnabled = true,
        )
        assertEquals(expected, derived)
    }

    @Test
    fun `audio screen projection equals the hand-written field list`() {
        val cache = AudioCacheSlice(
            audioCachingEnabled = false,
            audioCacheSizeMb = 512,
            audioPrefetchLookahead = 2,
            audioPrefetchBackfill = 4,
            audioCacheNetworkPolicy = AudioCacheNetworkPolicy.ANY_NETWORK,
        )
        val experimental = ExperimentalSlice(preferAudioDescription = true)

        val derived = audioSurfaceValues(audio, effects).toAudioPreferences(cache, experimental)

        // Explicitly named construction; note audioDelayMs is NOT on this lane.
        val expected = com.raulshma.jellyplay.core.model.AudioPreferences(
            audioDefaultSpeed = 1.25f,
            audioNightModeVolume = 0.6f,
            audioNightModeGain = 900,
            audioSkipPreviousThresholdMs = 4_000L,
            audioAutoplayNext = false,
            audioPreloadBufferSize = PreloadBufferSize.LOW,
            audioNormalizationMode = AudioNormalizationMode.TRACK,
            audioNormalizationEnabled = true,
            replayGainPreAmpDb = 1.5f,
            channelMixMode = ChannelMixMode.STEREO_DOWNMIX,
            channelMixEnabled = true,
            audioGaplessEnabled = false,
            audioCrossfadeDurationMs = 2_500L,
            equalizerEnabled = true,
            equalizerSettings = effects.equalizerSettings,
            equalizerPreset = EqualizerPreset.BASS_BOOST,
            bassBoostEnabled = true,
            bassBoostStrength = EffectStrength.HIGH,
            virtualizerEnabled = true,
            virtualizerStrength = 700,
            reverbPreset = ReverbPreset.PLATE,
            lrBalance = -0.25f,
            autoEqByGenre = true,
            pitchSemitones = -2f,
            dialogueBoostEnabled = true,
            dialogueBoostStrength = EffectStrength.LOW,
            nightModeEnabled = true,
            nightModeStrength = EffectStrength.MODERATE,
            audioVisualizerEnabled = true,
            audioCachingEnabled = false,
            audioCacheSizeMb = 512,
            audioPrefetchLookahead = 2,
            audioPrefetchBackfill = 4,
            audioCacheNetworkPolicy = AudioCacheNetworkPolicy.ANY_NETWORK,
            sleepTimerDurationMs = 900_000L,
            preferAudioDescription = true,
            volumeBoostEnabled = true,
            volumeBoostGain = 6,
        )
        assertEquals(expected, derived)
    }

    @Test
    fun `appearance core projection equals the hand-written field list`() {
        val appearance = AppearanceSlice(
            themeMode = ThemeMode.DARK,
            contrastLevel = ContrastLevel.HIGH,
            oledMode = true,
            accentColorSwatch = "violet",
            colorStyle = ColorStyle.VIBRANT,
            performanceMode = true,
            themeVariant = "soothing",
            synthwaveAccent = "cyberpink",
            soothingAccent = "mist",
            vividAccent = "flare",
            auroraAccent = "glacier",
            sakuraAccent = "blossom",
            vectorPopAccent = "neon",
        )
        val navigation = NavigationSlice(navBarShowLabels = false)
        val home = HomeDiscoverySlice(homeHeroEnabled = false, homeBackdropEnabled = false)
        val library = LibrarySlice(libraryViewMode = LibraryViewMode.LIST)

        val core = appearanceCoreValues(appearance, navigation, home, library)

        val derived = core.toAppearancePreferences()
        val expected = com.raulshma.jellyplay.core.model.AppearancePreferences(
            dynamicTheming = true,
            themeMode = ThemeMode.DARK,
            contrastLevel = ContrastLevel.HIGH,
            oledMode = true,
            accentColorSwatch = "violet",
            colorStyle = ColorStyle.VIBRANT,
            navBarShowLabels = false,
            homeHeroEnabled = false,
            homeBackdropEnabled = false,
            performanceMode = true,
            themeVariant = "soothing",
            synthwaveAccent = "cyberpink",
            soothingAccent = "mist",
            vividAccent = "flare",
            auroraAccent = "glacier",
            sakuraAccent = "blossom",
            vectorPopAccent = "neon",
            libraryViewMode = LibraryViewMode.LIST,
        )
        assertEquals(expected, derived)

        // The bundle-half extraction matches the direct one.
        assertEquals(
            core,
            AppearanceScreenBundle(appearance, navigation, home, library, ExperimentalSlice()).appearanceCoreValues(),
        )
    }

    @Test
    fun `appearance theme quad is declared once and reads the slice`() {
        val appearance = AppearanceSlice(
            dynamicTheming = false,
            oledMode = true,
            colorStyle = ColorStyle.EXPRESSIVE,
            accentColorSwatch = "amber",
        )
        assertEquals(
            com.raulshma.jellyplay.core.model.AppearanceTheme(
                dynamicTheming = false,
                oledMode = true,
                colorStyle = ColorStyle.EXPRESSIVE,
                accentColorSwatch = "amber",
            ),
            appearance.appearanceTheme(),
        )
    }
}
