package com.raulshma.jellyplay.core.data.util

interface ImageUrlProvider {
    // Nullable so callers (e.g. the photo viewer) can request original
    // resolution: a null maxWidth omits the Jellyfin param entirely. Coalescing
    // null to a fixed default silently capped full-res photos at 400px.
    fun getImageUrl(itemId: String, maxWidth: Int? = DEFAULT_MAX_WIDTH): String

    /** Chapter thumbnail for the detail-screen chapter row (imageType = Chapter). */
    fun getChapterImageUrl(itemId: String, imageIndex: Int, tag: String? = null): String

    fun getBackdropUrl(itemId: String, maxWidth: Int = DEFAULT_BACKDROP_WIDTH): String

    companion object {
        const val DEFAULT_MAX_WIDTH = 400
        const val MUSIC_MAX_WIDTH = 300
        const val DEFAULT_BACKDROP_WIDTH = 1920
    }
}
