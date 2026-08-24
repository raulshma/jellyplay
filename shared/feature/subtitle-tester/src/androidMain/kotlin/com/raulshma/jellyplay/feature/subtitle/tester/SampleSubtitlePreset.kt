package com.raulshma.jellyplay.feature.subtitle.tester

import androidx.annotation.RawRes
import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.feature.subtitle.tester.generated.resources.Res
import com.raulshma.jellyplay.feature.subtitle.tester.generated.resources.subtitle_tester_preset_action
import com.raulshma.jellyplay.feature.subtitle.tester.generated.resources.subtitle_tester_preset_dialogue
import com.raulshma.jellyplay.feature.subtitle.tester.generated.resources.subtitle_tester_preset_lyrics
import com.raulshma.jellyplay.feature.subtitle.tester.generated.resources.subtitle_tester_preset_signs
import com.raulshma.jellyplay.shared.feature.subtitle.tester.R
import org.jetbrains.compose.resources.StringResource

/** A built-in sample subtitle track used by the tester. */
@Immutable
data class SampleSubtitlePreset(
    val id: String,
    val displayName: StringResource,
    @RawRes val srtResId: Int,
    @RawRes val assResId: Int,
)

/**
 * Registry of the built-in sample presets. Android-only on purpose: the
 * `@RawRes` ids come from this module's androidMain R class (the raw assets
 * feed native engines through file:// paths — see [preview.PlaybackRequestFactory]),
 * so the registry can never be commonMain.
 */
object SampleSubtitlePresets {

    val dialogue = SampleSubtitlePreset(
        id = SampleSubtitlePresetIds.DIALOGUE,
        displayName = Res.string.subtitle_tester_preset_dialogue,
        srtResId = R.raw.sub_sample_dialogue_srt,
        assResId = R.raw.sub_sample_dialogue_ass,
    )

    val lyrics = SampleSubtitlePreset(
        id = SampleSubtitlePresetIds.LYRICS,
        displayName = Res.string.subtitle_tester_preset_lyrics,
        srtResId = R.raw.sub_sample_lyrics_srt,
        assResId = R.raw.sub_sample_lyrics_ass,
    )

    val signs = SampleSubtitlePreset(
        id = SampleSubtitlePresetIds.SIGNS,
        displayName = Res.string.subtitle_tester_preset_signs,
        srtResId = R.raw.sub_sample_signs_srt,
        assResId = R.raw.sub_sample_signs_ass,
    )

    val action = SampleSubtitlePreset(
        id = SampleSubtitlePresetIds.ACTION,
        displayName = Res.string.subtitle_tester_preset_action,
        srtResId = R.raw.sub_sample_action_srt,
        assResId = R.raw.sub_sample_action_ass,
    )

    val ALL: List<SampleSubtitlePreset> = listOf(dialogue, lyrics, signs, action)

    val DEFAULT: SampleSubtitlePreset = dialogue

    fun byId(id: String): SampleSubtitlePreset =
        ALL.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown sample preset id: $id")
}
