package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.MediaItem

/**
 * Resolved media context for a single item lookup: a title/poster pair, not a
 * full detail snapshot.
 *
 * @property item the resolved [MediaItem] (title, type, episode context), or
 * `null` if neither the offline store nor the server had a row for the id.
 * `null` marks a resolved-but-not-found id so callers can cache the miss
 * instead of refetching on every request.
 * @property posterUrl the URL to load the poster from. For offline items this
 * is the locally-saved `OfflineMediaItem.posterPath`; otherwise it is the
 * id-derived server URL, which will only resolve once back online.
 */
data class ResolvedMediaRef(
    val item: MediaItem?,
    val posterUrl: String,
)

/**
 * The narrow offline-first item-resolution seam: the single
 * owner of the remote/local decision for lightweight lookups that need only a
 * title + poster — e.g. the home screen's pending-sync sheet rows.
 *
 * Deliberately NOT `MediaDetailProvider` (screen-lifetime detail snapshots with
 * catalogue sessions and generation tracking) and NOT `PlaybackSourceResolver`
 * (download-vs-stream playback sources). Both are the wrong depth for a
 * per-row title+poster lookup; this interface is intentionally tiny.
 *
 * Fallback ordering, pinned by [OfflineFirstItemResolverTest]:
 * 1. offline row → adapted [MediaItem] with its local poster path;
 * 2. else — only while ONLINE — a `getMediaDetail` lookup (skipped while
 *    offline to avoid a guaranteed-failing network call);
 * 3. else the id-derived server poster URL with a `null` item, so the row can
 *    still attempt to load the poster once back online.
 */
interface OfflineFirstItemResolver {
    suspend fun resolveMediaRef(id: String): ResolvedMediaRef
}
