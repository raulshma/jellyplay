package com.raulshma.jellyplay.widget

import com.raulshma.jellyplay.core.model.MediaItem

/**
 * Single home for the widget poster-identity policy: which server item id a
 * widget poster url is derived from, and at what cell width, per widget
 * flavour. Both Jellyfin-server flavours resolve the series id first (an
 * episode row shows its series poster); the Seerr recommendations widget has
 * no rule here — its posters are TMDB CDN urls the worker builds straight
 * from the search item's poster path.
 *
 * Consumers: the Continue-Watching factory/broadcaster poster pipeline (via
 * `WidgetImageLoader.continueWatchingPosterEntry`) and the
 * library-recommendations worker's item mapping — so the cache-key rule and
 * the url widths cannot drift between the prewarm and the bind.
 */
internal object WidgetPosterIdentity {

    /** Continue-Watching rows: the single-column cell's poster width. */
    const val CONTINUE_WATCHING_POSTER_MAX_WIDTH = 300

    /** Library-recommendation grid cells: the multi-column poster width. */
    const val LIBRARY_RECOMMENDATIONS_POSTER_MAX_WIDTH = 400

    /**
     * Cache-key/image id for a Continue-Watching row: the series id when the
     * row is an episode, else the item id. Single source for the poster
     * entry's image id AND the factory's getViewAt lookup, so the key rule
     * cannot drift between the two.
     */
    fun continueWatchingPosterImageId(item: MediaItem): String = item.seriesId ?: item.id

    /**
     * Image id for a library-recommendations row's poster url: the series id
     * when the row belongs to a series, else the item id (the same
     * resolution the Continue-Watching flavour uses — kept as its own member
     * so a future divergence has one home).
     */
    fun libraryRecommendationsPosterImageId(item: MediaItem): String = item.seriesId ?: item.id
}
