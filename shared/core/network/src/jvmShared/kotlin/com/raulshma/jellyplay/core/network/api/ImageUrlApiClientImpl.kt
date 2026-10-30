package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.buildItemImageUrl
import com.raulshma.jellyplay.core.network.library.ChildItemImageRow
import com.raulshma.jellyplay.core.network.library.buildChildItemImagesQuerySpec
import com.raulshma.jellyplay.core.network.library.toChildItemImageUrls
import org.jellyfin.sdk.api.client.extensions.*
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.serializer.toUUID

/** ImageType resolved by serial name once — [ImageType.fromNameOrNull] linear-scans per call and Coil binds run per item. Keys lowercased: [fromNameOrNull] matches serial names case-insensitively, so callers passing server-JSON casing ("primary") must resolve too. */
private val IMAGE_TYPES_BY_SERIAL_NAME = ImageType.entries.associateBy { it.serialName.lowercase() }

/**
 * The [ImageUrlApiClient] family adapter: the pure item-image URL-builder
 * math moved verbatim off [LibraryApiClientImpl], plus the child-image ROW
 * read (the carve's completion — its former wide-client body kept only this
 * SDK transport; the URL-building half discharges through [getImageUrl],
 * here on the same class). Closes over the SAME [JellyfinApiEngine] single
 * the wide client uses — base URL for the builders, transport for the row
 * read.
 */
class ImageUrlApiClientImpl(
    private val engine: JellyfinApiEngine,
) : ImageUrlApiClient {

    /**
     * Delegates to core:model's [buildItemImageUrl] — the single owner of the
     * item-image URL math (a byte-parity port of the SDK `imageApi` builder,
     * pinned by ImageUrlBuilderTest); the SDK transport stays for the actual
     * requests. One deliberate divergence from the former direct SDK call:
     * the builder's [normalizeItemIdGuid] accepts the compact 32-hex item ids
     * Jellyfin 10.11 emits (the `toUUID()` parse refused them), normalizing
     * to the dashed form the server serves either way.
     */
    override fun getImageUrl(itemId: String, imageType: String, maxWidth: Int?, imageIndex: Int?, tag: String?): String {
        val api = engine.api ?: return ""
        val imageTypeEnum = IMAGE_TYPES_BY_SERIAL_NAME[imageType.lowercase()]
            ?: return ""
        return buildItemImageUrl(
            baseUrl = api.baseUrl,
            itemId = itemId,
            imageType = imageTypeEnum.serialName,
            maxWidth = maxWidth,
            tag = tag,
            imageIndex = imageIndex,
        )
    }

    override fun getBackdropImageUrl(itemId: String, maxWidth: Int, tag: String?): String =
        getImageUrl(itemId, "Backdrop", maxWidth, null, tag)

    /**
     * The child-image row read, moved verbatim off [LibraryApiClientImpl]
     * (byte-identical wire): the request shape + Primary-tag fold live in
     * commonMain (ChildItemImageUrls.kt); this side keeps only the SDK
     * transport and the image-URL seam ([getImageUrl] is the caller-side URL
     * lambda the fold always took).
     */
    override suspend fun getChildItemImageUrls(parentId: String, limit: Int): List<String> {
        return try {
            val spec = buildChildItemImagesQuerySpec(parentId, limit)
            val api = engine.requireApi()
            val response = api.itemsApi.getItems(
                parentId = spec.parentId?.toUUID(),
                includeItemTypes = spec.includeKinds.toBaseItemKinds(),
                limit = spec.limit,
                sortBy = spec.sortBy.toItemSortBys(),
                sortOrder = spec.sortOrderDescending.toSortOrderList(),
                fields = spec.fields.toItemFieldsList(),
            ).content
            response.items.map { item ->
                ChildItemImageRow(
                    id = item.id.toString(),
                    hasPrimaryImage = item.imageTags?.containsKey(ImageType.PRIMARY) == true,
                )
            }.toChildItemImageUrls { itemId -> getImageUrl(itemId, "Primary", 200, null, null) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
