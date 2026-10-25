package com.raulshma.jellyplay.core.data.util

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.PersonInfo

/**
 * The UI-facing artwork resolver: one [MediaItem]/[PersonInfo] in, the image
 * URL its surface renders out — with the right image tag already selected.
 *
 * Why this exists: the tag is Jellyfin's per-image etag and the ONLY thing
 * that makes a re-upload of artwork visible (Coil's memory + disk caches key
 * on the URL string; a tag change mints a new URL, a new URL is a cache
 * miss). Which tag belongs to which slot is a per-item-shape decision — own
 * art vs parent/series/album fallback — and this interface is its single
 * home. Call sites pass the item they already hold; they never pick tags by
 * hand, and the `(itemId) -> url` lambdas this replaces could not carry a tag
 * at all (the exact shape that caused the stale-artwork bug).
 *
 * Layering: wraps [ImageUrlProvider] (the raw itemId-level seam); core:ui's
 * [com.raulshma.jellyplay.core.ui.image.MediaImage] stays a pure URL renderer.
 * Slots deliberately stay orthogonal — "own art, else fallback" composition
 * remains at the call site via MediaImage's `fallbackUrls` — so the resolver
 * never second-guesses which slot a surface wants.
 */
interface ArtworkUrls {

    /** The item's own poster (Primary art) with its own tag. */
    fun poster(item: MediaItem, maxWidth: Int = ImageUrlProvider.DEFAULT_MAX_WIDTH): String

    /**
     * The direct parent's poster: the season art for episodes, the series art
     * for seasons, the album art for tracks (parent id + parent tag, album
     * tag as the music fallback). Empty string when the item has no parent.
     */
    fun parentPoster(item: MediaItem, maxWidth: Int = ImageUrlProvider.DEFAULT_MAX_WIDTH): String

    /** The containing series' poster (series id + series tag). Empty string when not an episode/season. */
    fun seriesPoster(item: MediaItem, maxWidth: Int = ImageUrlProvider.DEFAULT_MAX_WIDTH): String

    /**
     * The id-addressed poster read for surfaces that ALSO hold a candidate
     * item: when [itemId] is [candidate]'s id, the tagged [poster] URL;
     * otherwise (no item in hand, or the surface addresses a DIFFERENT id —
     * e.g. the parent-series leg) the raw untagged id-level URL. The fold
     * keeps the tag from being attached to the WRONG item's URL (a tag only
     * validates the id it shipped with) — one home instead of a re-decided
     * id-match test at every media-session/cast/resolver seam.
     */
    fun posterFor(candidate: MediaItem?, itemId: String, maxWidth: Int = ImageUrlProvider.DEFAULT_MAX_WIDTH): String

    /** The item's first backdrop (wide art) with its own tag. */
    fun backdrop(item: MediaItem, maxWidth: Int = ImageUrlProvider.DEFAULT_BACKDROP_WIDTH): String

    /** The item's clear-logo with its own tag (details title block). */
    fun logo(item: MediaItem): String

    /** A credited person's portrait with their own tag. */
    fun personPortrait(person: PersonInfo, maxWidth: Int = ImageUrlProvider.DEFAULT_MAX_WIDTH): String

    /**
     * Preview/no-session stand-in: every slot renders empty. Swappable with
     * the real resolver at the same injection points, so @Preview states keep
     * the exact composition the runtime uses.
     */
    object EMPTY : ArtworkUrls {
        override fun poster(item: MediaItem, maxWidth: Int): String = ""
        override fun parentPoster(item: MediaItem, maxWidth: Int): String = ""
        override fun seriesPoster(item: MediaItem, maxWidth: Int): String = ""
        override fun posterFor(candidate: MediaItem?, itemId: String, maxWidth: Int): String = ""
        override fun backdrop(item: MediaItem, maxWidth: Int): String = ""
        override fun logo(item: MediaItem): String = ""
        override fun personPortrait(person: PersonInfo, maxWidth: Int): String = ""
    }
}

/** Production [ArtworkUrls] over the [ImageUrlProvider] seam (tag threading included). */
class DefaultArtworkUrls(
    private val provider: ImageUrlProvider,
) : ArtworkUrls {

    override fun poster(item: MediaItem, maxWidth: Int): String =
        provider.getImageUrl(item.id, maxWidth = maxWidth, tag = item.imageTags.primary)

    override fun parentPoster(item: MediaItem, maxWidth: Int): String {
        val parentId = item.parentId ?: return ""
        val tag = item.imageTags.parentPrimary ?: item.imageTags.album
        return provider.getImageUrl(parentId, maxWidth = maxWidth, tag = tag)
    }

    override fun seriesPoster(item: MediaItem, maxWidth: Int): String {
        val seriesId = item.seriesId ?: return ""
        return provider.getImageUrl(seriesId, maxWidth = maxWidth, tag = item.imageTags.seriesPrimary)
    }

    override fun posterFor(candidate: MediaItem?, itemId: String, maxWidth: Int): String =
        if (candidate != null && candidate.id == itemId) {
            poster(candidate, maxWidth)
        } else {
            provider.getImageUrl(itemId, maxWidth = maxWidth)
        }

    override fun backdrop(item: MediaItem, maxWidth: Int): String =
        provider.getBackdropUrl(item.id, maxWidth = maxWidth, tag = item.imageTags.backdrop)

    override fun logo(item: MediaItem): String =
        provider.getLogoUrl(item.id, tag = item.imageTags.logo)

    override fun personPortrait(person: PersonInfo, maxWidth: Int): String =
        provider.getImageUrl(person.id, maxWidth = maxWidth, tag = person.primaryImageTag)
}
