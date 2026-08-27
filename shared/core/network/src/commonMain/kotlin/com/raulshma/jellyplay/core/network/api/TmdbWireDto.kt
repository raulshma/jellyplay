package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.model.seerr.TmdbReview
import com.raulshma.jellyplay.core.network.seerr.arrSeerrWireJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire DTOs for the Phase W wasm TMDB client — field-for-field transcriptions
 * of the jvmShared `TmdbApiClientImpl`'s DTOs (`TmdbVideosResponse` /
 * `TmdbVideo` are nested in the impl class; `TmdbReviewsResponse` is a
 * file-private top-level there, hence the `Wire` suffix here to keep the
 * package's declaration space conflict-free). Decoding runs through
 * [arrSeerrWireJson] — the same lenient config the JVM impl uses (it borrows
 * `SeerrApiClientImpl.lenientJson`).
 */

/** `TmdbApiClientImpl.TmdbReviewsResponse`. */
@Serializable
internal data class TmdbReviewsResponseWire(
    val results: List<TmdbReview> = emptyList(),
)

/** `TmdbApiClientImpl.TmdbVideosResponse`. */
@Serializable
internal data class TmdbVideosResponseWire(
    val results: List<TmdbVideoWire> = emptyList(),
)

/** `TmdbApiClientImpl.TmdbVideo`. */
@Serializable
internal data class TmdbVideoWire(
    val key: String? = null,
    val name: String? = null,
    val size: Int = 0,
    val type: String? = null,
    val site: String? = null,
)

/** `parseTmdbReviews` (jvmShared): parses a TMDB `/reviews` response body. */
internal fun parseTmdbReviewsWire(text: String): List<TmdbReview> =
    arrSeerrWireJson.decodeFromString<TmdbReviewsResponseWire>(text).results

/**
 * `TmdbApiClientImpl.getVideos`' result mapping, verbatim: the watch URL is
 * synthesized ONLY for YouTube (case-insensitive site match); every other
 * site keeps url = null.
 */
internal fun TmdbVideoWire.toSeerrRelatedVideo(): SeerrRelatedVideo = SeerrRelatedVideo(
    key = key,
    name = name,
    size = size,
    type = type,
    site = site,
    url = if (site?.lowercase() == "youtube") "https://www.youtube.com/watch?v=$key" else null,
)
