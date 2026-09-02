package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Filters shared by the Programs/Recommended/Guide queries. Each is null when
 * the query must not constrain that category (omitted flags are not sent to the
 * server, matching the jellyfin-web client behaviour).
 */
@Immutable
@Serializable
data class ProgramFilters(
    val isAiring: Boolean? = null,
    val hasAired: Boolean? = null,
    val isMovie: Boolean? = null,
    val isSeries: Boolean? = null,
    val isNews: Boolean? = null,
    val isKids: Boolean? = null,
    val isSports: Boolean? = null,
)

@Immutable
@Serializable
data class LiveTvChannel(
    val id: String,
    val name: String,
    val number: String? = null,
    val imageTag: String? = null,
    val currentProgram: LiveTvProgram? = null,
    val mediaType: MediaType = MediaType.CHANNEL,
    val primaryBlurHash: String? = null,
)

@Immutable
@Serializable
data class LiveTvProgram(
    val id: String,
    val name: String,
    val overview: String? = null,
    val channelId: String,
    val channelName: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val durationTicks: Long? = null,
    val episodeTitle: String? = null,
    val officialRating: String? = null,
    val isMovie: Boolean = false,
    val isNews: Boolean = false,
    val isSports: Boolean = false,
    val isKids: Boolean = false,
    val isLive: Boolean = false,
    val isPremiere: Boolean = false,
    val isSeries: Boolean = false,
    val isRepeat: Boolean = false,
    val hasAired: Boolean = false,
    val indexNumber: Int? = null,
    val parentIndexNumber: Int? = null,
    val imageUrl: String? = null,
    val imageTag: String? = null,
    /** Present when a single-recording timer is already set for this program. */
    val timerId: String? = null,
    /** Present when this program is part of a series recording. */
    val seriesTimerId: String? = null,
)

@Immutable
@Serializable
data class EpgGuide(
    val channels: List<LiveTvChannel>,
    val programs: List<LiveTvProgram>,
)

/**
 * Completed/recording item from `GET /LiveTv/Recordings`. Unlike [DvrTimer]
 * (a scheduled recording rule) this is an actual recorded media item that can
 * be played back.
 */
@Immutable
@Serializable
data class LiveTvRecording(
    val id: String,
    val name: String,
    val overview: String? = null,
    val channelId: String? = null,
    val channelName: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val runTimeTicks: Long? = null,
    val imageUrl: String? = null,
    val imageTag: String? = null,
    val seriesTimerId: String? = null,
    val status: DvrTimerStatus = DvrTimerStatus.COMPLETED,
)

/** EPG availability window returned by `GET /LiveTv/GuideInfo`. */
@Immutable
@Serializable
data class GuideInfo(
    val startDate: String,
    val endDate: String,
)
