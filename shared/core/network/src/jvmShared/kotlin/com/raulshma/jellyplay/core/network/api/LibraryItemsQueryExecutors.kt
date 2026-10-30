package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.network.library.LibraryItemsQuerySpec
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.*
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.serializer.toUUID

/**
 * The transport half of the [LibraryItemsQuerySpec] seam — the counterpart of
 * the resolvers beside this file ([toBaseItemKinds] and siblings): one spec →
 * ONE `itemsApi.getItems` call → one page fold. The commonMain builders hold
 * the per-endpoint request DECISIONS in wire vocabulary; this file holds the
 * call site the JVM twins collapsed into, so the 18-argument expansion exists
 * exactly once and a call site can no longer drift its own copy of it.
 *
 * Passing a dimension as null vs omitting the named argument is
 * wire-identical: the SDK's UrlBuilder skips null query values (the same
 * property [LibraryItemsQuerySpec]'s header cites for "null and empty are
 * wire-identical"). The full expansion below therefore sends exactly the
 * params the former per-site partial expansions sent whenever a spec leaves
 * a dimension unset — every collapsed site was diffed arg-for-arg against it.
 *
 * Two depths, because the folds differ while the wire does not:
 * [getItemsQuery] is the expansion + call alone — getMediaItems runs its
 * empty-library fallback BETWEEN the call and the parental filter, so it
 * cannot take a pre-assembled page — and [getItemsPage] adds the shared
 * SearchResult fold ([toSearchResult]). The bespoke (non-spec) getItems
 * queries adopt only [toSearchResult], the tail they used to hand-copy.
 */

/**
 * The spec→SDK expansion: every [LibraryItemsQuerySpec] dimension resolved
 * through the [toBaseItemKinds]-family regime (serialName lookup, failing
 * fast on SDK drift) and sent as one `itemsApi.getItems` call. Named
 * arguments evaluate independently and the SDK builds its query map in its
 * own fixed parameter order, so the declaration order here — mirroring the
 * spec's, not any one former call site's — leaves the wire untouched.
 *
 * [sendPresenceParams] pins the ONE deliberate divergence the former
 * expansions carried: the discover-row query has never sent the presence
 * chips (hasSubtitles/hasTrailer) even though a row's LibraryFilters — the
 * shared persisted blob the spec mirrors — CAN carry them, so its call opts
 * out and the takeIf below drops them from the wire. searchTerm needs no
 * gate: the discover builder hard-pins it null
 * (com.raulshma.jellyplay.core.network.library.buildDiscoverRowQuerySpec),
 * an invariant the executor test pins too.
 */
internal suspend fun ApiClient.getItemsQuery(
    spec: LibraryItemsQuerySpec,
    sendPresenceParams: Boolean = true,
): BaseItemDtoQueryResult = itemsApi.getItems(
    parentId = spec.parentId?.toUUID(),
    includeItemTypes = spec.includeKinds.toBaseItemKinds(),
    excludeItemTypes = spec.excludeKinds.toBaseItemKinds(),
    genres = spec.genres,
    years = spec.years,
    studioIds = spec.studioIds?.map { it.toUUID() },
    tags = spec.tags,
    sortBy = spec.sortBy.toItemSortBys(),
    sortOrder = spec.sortOrderDescending.toSortOrderList(),
    startIndex = spec.startIndex,
    limit = spec.limit,
    recursive = spec.recursive,
    searchTerm = spec.searchTerm,
    filters = spec.itemFilters.toItemFilters(),
    // The presence chips ride only the endpoints that have always sent them
    // (the library grid) — see the [sendPresenceParams] note above.
    hasSubtitles = spec.hasSubtitles.takeIf { sendPresenceParams },
    hasTrailer = spec.hasTrailer.takeIf { sendPresenceParams },
    minCommunityRating = spec.minCommunityRating,
    personIds = spec.personIds?.map { it.toUUID() },
    minDateLastSaved = spec.minDateLastSavedMs.toSdkLocalDateTime(),
    minPremiereDate = spec.minPremiereDateMs.toSdkLocalDateTime(),
    fields = spec.fields.toItemFieldsList(),
).content

/**
 * The pre-assembled page: [getItemsQuery] plus the fold every spec'd /Items
 * endpoint used to hand-write — parental filter over the page, the server's
 * totalRecordCount carried through, `startIndex` echoed back. The echo reads
 * the spec's: every collapsed site's builder receives the call's own
 * non-null startIndex, so `?: 0` (the first-page default an omitted paging
 * param means on the wire) never fires for them — it only keeps the fold
 * total for a spec that leaves paging unset.
 */
internal suspend fun ApiClient.getItemsPage(
    spec: LibraryItemsQuerySpec,
    maxParentalRating: Int?,
    sendPresenceParams: Boolean = true,
): SearchResult = getItemsQuery(spec, sendPresenceParams).toSearchResult(
    maxParentalRating = maxParentalRating,
    startIndex = spec.startIndex ?: 0,
)

/**
 * The duplicated page tail — `SearchResult(items = response.items
 * .toFilteredMediaItems(rating), totalRecordCount = response
 * .totalRecordCount, startIndex = …)` — extracted verbatim from the
 * hand-copies (the spec'd sites above, the search suggestions, the collection
 * members read). [startIndex] stays a parameter, not a field read: the
 * bespoke sites echo their own call arguments (the suggestions fold
 * hard-codes 0), and those echoes are part of the pinned wire contract.
 */
internal fun BaseItemDtoQueryResult.toSearchResult(
    maxParentalRating: Int?,
    startIndex: Int,
): SearchResult = SearchResult(
    items = items.toFilteredMediaItems(maxParentalRating),
    totalRecordCount = totalRecordCount,
    startIndex = startIndex,
)
