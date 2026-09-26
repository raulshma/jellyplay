package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The Jellyfin "Identify" flow (jellyfin-web parity): search metadata
 * providers for the real match of a mismatched item, preview the candidates,
 * and apply one back onto the item (metadata + images replacement via
 * `POST /Items/RemoteSearch/Apply/{itemId}`).
 *
 * First version supports Series and Movie (the 90% mismatch case); the
 * client's search endpoint set is dispatch-per-type so further types slot in
 * without a model change.
 */

/**
 * The search sent to the providers: the item being identified plus the
 * editable prefill (name / year / provider ids) the user may correct before
 * searching. Provider-id keys are lowercase ("tmdb", "tvdb", "imdb"),
 * matching the [MediaDetail.providerIds] convention.
 */
@Immutable
@Serializable
data class IdentifyQuery(
    val itemId: String,
    /** Wire item type the search endpoint dispatches on: "Series" or "Movie". */
    val itemType: String,
    val name: String,
    val year: Int? = null,
    val providerIds: Map<String, String> = emptyMap(),
)

/** One provider candidate returned by the remote search. */
@Immutable
@Serializable
data class IdentifyResult(
    val name: String = "",
    val year: Int? = null,
    val providerIds: Map<String, String> = emptyMap(),
    val searchProviderName: String? = null,
    val imageUrl: String? = null,
    val overview: String? = null,
)
