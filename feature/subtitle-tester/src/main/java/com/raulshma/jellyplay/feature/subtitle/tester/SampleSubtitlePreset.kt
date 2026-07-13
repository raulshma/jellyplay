package com.raulshma.jellyplay.feature.subtitle.tester

import androidx.annotation.RawRes
import com.raulshma.jellyplay.feature.subtitle.tester.R

/** A built-in sample subtitle track used by the tester. */
data class SampleSubtitlePreset(
    val id: String,
    val displayNameRes: Int,
    @RawRes val srtResId: Int,
    @RawRes val assResId: Int,
)

/** Registry of the built-in sample presets. */
object SampleSubtitlePresets {

    val dialogue = SampleSubtitlePreset(
        id = "dialogue",
        displayNameRes = R.string.subtitle_tester_preset_dialogue,
        srtResId = R.raw.sub_sample_dialogue_srt,
        assResId = R.raw.sub_sample_dialogue_ass,
    )

    val lyrics = SampleSubtitlePreset(
        id = "lyrics",
        displayNameRes = R.string.subtitle_tester_preset_lyrics,
        srtResId = R.raw.sub_sample_lyrics_srt,
        assResId = R.raw.sub_sample_lyrics_ass,
    )

    val signs = SampleSubtitlePreset(
        id = "signs",
        displayNameRes = R.string.subtitle_tester_preset_signs,
        srtResId = R.raw.sub_sample_signs_srt,
        assResId = R.raw.sub_sample_signs_ass,
    )

    val action = SampleSubtitlePreset(
        id = "action",
        displayNameRes = R.string.subtitle_tester_preset_action,
        srtResId = R.raw.sub_sample_action_srt,
        assResId = R.raw.sub_sample_action_ass,
    )

    val ALL: List<SampleSubtitlePreset> = listOf(dialogue, lyrics, signs, action)

    val DEFAULT: SampleSubtitlePreset = dialogue

    fun byId(id: String): SampleSubtitlePreset =
        ALL.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown sample preset id: $id")
}
