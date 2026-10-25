package com.raulshma.jellyplay.core.data.util

/**
 * Builds the Jellyfin image URLs the UI renders. Every member takes the
 * item's image tag (the [com.raulshma.jellyplay.core.model.MediaImageTags]
 * half of [com.raulshma.jellyplay.core.model.MediaItem])
 * and appends it as the `?tag=` query param: the tag is Jellyfin's per-image
 * revision stamp, so a server-side artwork change mints a new URL — and since
 * Coil keys its memory AND disk caches on the URL string, a new tag is a
 * cache miss. That is the whole stale-artwork fix: no manual cache clears,
 * no invalidation bus; re-uploaded art appears on the next item refetch.
 *
 * Callers that have the [com.raulshma.jellyplay.core.model.MediaItem] in hand
 * should prefer the [ArtworkUrls] resolver, which owns the tag-selection
 * policy (own tag vs parent/series/album tag) in one place. This interface is
 * the raw itemId-level seam underneath it.
 */
interface ImageUrlProvider {
    // Nullable so callers (e.g. the photo viewer) can request original
    // resolution: a null maxWidth omits the Jellyfin param entirely. Coalescing
    // null to a fixed default silently capped full-res photos at 400px.
    fun getImageUrl(itemId: String, maxWidth: Int? = DEFAULT_MAX_WIDTH, tag: String? = null): String

    /**
     * The tag-guard fold every Live TV card uses: a null image tag means the
     * item HAS no image → empty string (never a URL for an image that does
     * not exist); any tag → [getImageUrl] at its default width, the tag
     * forwarded so a changed channel logo gets a new cache key. This used to
     * be hand-copied into every Live TV ViewModel.
     */
    fun getImageUrlOrNull(itemId: String, imageTag: String?): String =
        if (imageTag != null) getImageUrl(itemId, tag = imageTag) else ""

    /** Chapter thumbnail for the detail-screen chapter row (imageType = Chapter). */
    fun getChapterImageUrl(itemId: String, imageIndex: Int, tag: String? = null): String

    fun getBackdropUrl(itemId: String, maxWidth: Int = DEFAULT_BACKDROP_WIDTH, tag: String? = null): String

    /** Clear-logo image for the detail-screen title block (imageType = Logo). */
    fun getLogoUrl(itemId: String, tag: String? = null): String

    /**
     * The tag-guard fold for logos, mirroring [getImageUrlOrNull]: a null logo
     * tag means the item HAS no clear-logo → empty string (never a URL for an
     * image that does not exist); any tag → [getLogoUrl], the tag forwarded.
     */
    fun getLogoUrlOrNull(itemId: String, logoTag: String?): String =
        if (logoTag != null) getLogoUrl(itemId, tag = logoTag) else ""

    companion object {
        const val DEFAULT_MAX_WIDTH = 400
        const val MUSIC_MAX_WIDTH = 300
        const val DEFAULT_BACKDROP_WIDTH = 1920
    }
}
