package com.raulshma.jellyplay.core.network.api

/**
 * The item-image-URL family seam of [LibraryApiClient]: the pure URL-builder
 * math (no transport — a URL string is assembled, nothing is fetched), which
 * never belonged on a network API surface. The builders delegate to
 * core:model's [com.raulshma.jellyplay.core.model.buildItemImageUrl] — the
 * single owner of the item-image URL math — over the engine's base URL; the
 * adapter exists so the consumers that build URLs ([com.raulshma.jellyplay.core.data.util.ImageUrlProviderImpl]'s
 * memoised policy, the metadata editor's raw getter) inject this seam, never
 * the wide library surface (the [PlaylistApiClient] carve shape).
 *
 * The child-image ROW read moved here too (the carve's completion): a
 * transport read that projects to URL strings — its wire shape and Primary-tag
 * fold live beside the builders so the URL math stays in exactly one place,
 * and its one consumer ([com.raulshma.jellyplay.core.data.repository.MediaRepositoryImpl]'s
 * photo-folder read) injects this family directly.
 */
interface ImageUrlApiClient {
    // No default arguments on any member (the family-seam rule): every caller
    // states its values explicitly — the seam carries no hidden policy.
    fun getImageUrl(
        itemId: String,
        imageType: String,
        maxWidth: Int?,
        imageIndex: Int?,
        tag: String?,
    ): String

    fun getBackdropImageUrl(
        itemId: String,
        maxWidth: Int,
        tag: String?,
    ): String

    /**
     * The child-image ROW read (the photo-folder cover probe): one `/Items`
     * transport read over the shared query policy
     * ([com.raulshma.jellyplay.core.network.library.buildChildItemImagesQuerySpec])
     * folded through
     * [com.raulshma.jellyplay.core.network.library.toChildItemImageUrls] —
     * Primary-tagged rows only, each survivor rendered as its width-200
     * Primary URL via [getImageUrl]. Degrades to an empty list when the
     * transport fails: the row renders without covers, never as an error.
     */
    suspend fun getChildItemImageUrls(
        parentId: String,
        limit: Int,
    ): List<String>
}
