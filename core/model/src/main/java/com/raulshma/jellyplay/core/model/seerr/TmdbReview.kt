package com.raulshma.jellyplay.core.model.seerr

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A TMDB review entry shown on the details screen. */
@Immutable
@Serializable
data class TmdbReview(
    val id: String = "",
    val author: String = "",
    @SerialName("author_details")
    val authorDetails: TmdbReviewAuthor = TmdbReviewAuthor(),
    val content: String = "",
    @SerialName("created_at")
    val createdAt: String? = null,
    val url: String? = null,
)

/** Author facets of a [TmdbReview]; [rating] is 0-10, null when unrated. */
@Immutable
@Serializable
data class TmdbReviewAuthor(
    val name: String = "",
    val username: String = "",
    @SerialName("avatar_path")
    val avatarPath: String? = null,
    val rating: Double? = null,
)
