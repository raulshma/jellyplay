package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * The Jellyfin "Identify" flow (jellyfin-web parity): search metadata
 * providers for the real match of a mismatched item, preview the candidates,
 * and apply one back onto the item (metadata + images replacement via
 * `POST /Items/RemoteSearch/Apply/{itemId}`).
 *
 * First version supports Series and Movie (the 90% mismatch case); further
 * types slot into [IdentifyItemType] and the client's endpoint dispatch
 * without a model change.
 */

/**
 * The item types the Identify flow supports. The client's endpoint dispatch
 * (one SDK search endpoint per type) switches on this enum — no caller
 * spells a "Series"/"Movie" string.
 */
@Immutable
@Serializable
enum class IdentifyItemType {
    SERIES,
    MOVIE,
}

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
    /** The item type whose search endpoint the call dispatches on. */
    val itemType: IdentifyItemType,
    val name: String,
    val year: Int? = null,
    val providerIds: Map<String, String> = emptyMap(),
)

/**
 * One provider candidate returned by the remote search.
 *
 * [raw] carries the server's original RemoteSearchResult DTO (an SDK type —
 * opaque here so the model stays SDK-free): the apply endpoint posts it back
 * verbatim, exactly like jellyfin-web, so nothing the server returned is
 * lost to the trimmed fields below. Only the network client writes it and
 * only the network client reads it back on apply.
 */
@Immutable
@Serializable
data class IdentifyResult(
    val name: String = "",
    val year: Int? = null,
    val providerIds: Map<String, String> = emptyMap(),
    val searchProviderName: String? = null,
    val imageUrl: String? = null,
    val overview: String? = null,
    @Transient val raw: Any? = null,
)
