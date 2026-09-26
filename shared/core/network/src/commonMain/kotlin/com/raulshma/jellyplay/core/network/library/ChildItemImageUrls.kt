package com.raulshma.jellyplay.core.network.library

/**
 * `getChildItemImageUrls` — the photo-folder cover probe's shared policy.
 * The client twins used to duplicate the whole ~20-line body INCLUDING
 * the logic; now the client keeps only its transport:
 *  - the request SHAPE is decided here ([buildChildItemImagesQuerySpec], a
 *    [LibraryItemsQuerySpec] like every other `/Items` read — Photo filter,
 *    DateCreated-descending, the list-projection's aspect-ratio half only);
 *  - the post-fetch fold ([toChildItemImageUrls]) keeps the Primary-tagged
 *    rows and renders each survivor's width-200 Primary URL through the
 *    caller's URL seam;
 *  - the `/Items` fetch itself (SDK typed setters) and
 *    the URL builder (SDK image API on the JVM)
 *    stay per client, as does the try/catch → `emptyList` guard — it wraps
 *    the transport call, which only the client can see.
 */
internal fun buildChildItemImagesQuerySpec(
    parentId: String,
    limit: Int,
): LibraryItemsQuerySpec = LibraryItemsQuerySpec(
    parentId = parentId,
    includeKinds = listOf("Photo"),
    sortBy = listOf("DateCreated"),
    sortOrderDescending = true,
    limit = limit,
    // Not LIST_PROJECTION_FIELDS: the cover fold reads no Overview — the
    // pre-fold twins projected the aspect-ratio field alone, kept as-is.
    fields = listOf("PrimaryImageAspectRatio"),
)

/**
 * One fetched row reduced to what the fold needs — the id and whether the
 * row carries a Primary image tag. A tiny transport-neutral view so the
 * fold runs identically over SDK DTOs (JVM) and wire DTOs.
 */
internal data class ChildItemImageRow(
    val id: String,
    val hasPrimaryImage: Boolean,
)

/**
 * The post-fetch fold: Primary-tagged rows only, each mapped through
 * [imageUrl] (the caller's width-200 Primary URL builder). Rows without a
 * Primary tag are dropped — a cover slot without artwork is no slot.
 */
internal fun List<ChildItemImageRow>.toChildItemImageUrls(
    imageUrl: (itemId: String) -> String,
): List<String> = mapNotNull { row ->
    if (row.hasPrimaryImage) imageUrl(row.id) else null
}
