package com.raulshma.jellyplay.core.model

import kotlinx.serialization.Serializable

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

@Serializable
data class LiveTvProgram(
    val id: String,
    val name: String,
    val overview: String? = null,
    val channelId: String,
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
)

@Serializable
data class EpgGuide(
    val channels: List<LiveTvChannel>,
    val programs: List<LiveTvProgram>,
)
