package com.raulshma.jellyplay.core.data.util

interface ImageUrlProvider {
    // Nullable so callers (e.g. the photo viewer) can request original
    // resolution: a null maxWidth omits the Jellyfin param entirely. Coalescing
    // null to a fixed default silently capped full-res photos at 400px.
    fun getImageUrl(itemId: String, maxWidth: Int? = DEFAULT_MAX_WIDTH): String

    /**
     * The tag-guard fold every Live TV card uses: a null image tag means the
     * item HAS no image → empty string (never a URL for an image that does
     * not exist); any tag → [getImageUrl] at its default width. The tag value
     * itself is dropped on the floor — the Jellyfin URL is keyed by item id
     * alone; the tag only gates existence. This used to be hand-copied into
     * every Live TV ViewModel.
     */
    fun getImageUrlOrNull(itemId: String, imageTag: String?): String =
        if (imageTag != null) getImageUrl(itemId) else ""

    /** Chapter thumbnail for the detail-screen chapter row (imageType = Chapter). */
    fun getChapterImageUrl(itemId: String, imageIndex: Int, tag: String? = null): String

    fun getBackdropUrl(itemId: String, maxWidth: Int = DEFAULT_BACKDROP_WIDTH): String

    companion object {
        const val DEFAULT_MAX_WIDTH = 400
        const val MUSIC_MAX_WIDTH = 300
        const val DEFAULT_BACKDROP_WIDTH = 1920
    }
}
