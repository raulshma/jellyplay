package com.raulshma.jellyplay.feature.player.video.state

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.feature.player.video.TrackOption

/**
 * Audio/subtitle track enumeration + per-series preference-override flags.
 */
@Immutable
data class TrackState(
    val audioTracks: List<TrackOption> = emptyList(),
    val subtitleTracks: List<TrackOption> = emptyList(),
    val hasAudioOverride: Boolean = false,
    val hasSubtitleOverride: Boolean = false,
    val hasSeriesAudioPref: Boolean = false,
    val hasSeriesSubtitlePref: Boolean = false,
    val hasSeriesSubtitleOffPref: Boolean = false,
    val hasSeriesDialogueBoostPref: Boolean = false,
    /** A preferred version (media source) is remembered for the current item/series (the Version sheet's footer toggle). */
    val hasPreferredMediaSource: Boolean = false,
)
