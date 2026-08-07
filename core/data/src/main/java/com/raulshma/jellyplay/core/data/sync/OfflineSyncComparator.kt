package com.raulshma.jellyplay.core.data.sync

import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.OfflineSyncState
import com.raulshma.jellyplay.core.model.ResyncCheckResult
import com.raulshma.jellyplay.core.model.SyncStatus
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persisted snapshot of the server's view of an item at the time it was last
 * downloaded or resynced. Captured from a [MediaDetail] by [OfflineSyncComparator]
 * and stored on the `offline_media` row so a later freshness check can diff a
 * fresh fetch against it without keeping the full detail in memory.
 */
data class SyncBaseline(
    val posterTag: String?,
    val backdropTag: String?,
    val metadataSignature: String,
    val mediaSourceId: String?,
    val mediaSizeBytes: Long?,
)

/**
 * Pure, side-effect-free decision layer for offline download resync. Computes a
 * deterministic metadata signature and extracts a [SyncBaseline] from a fresh
 * [MediaDetail], then diffs a fresh fetch against a persisted baseline to decide
 * whether metadata and/or images have changed server-side.
 *
 * **No I/O here.** Network, DB, and disk live in [OfflineSyncManager]; this
 * object only turns inputs into decisions, which keeps the freshness rules
 * trivially unit-testable and independent of Room/Coil/OkHttp.
 *
 * The signature deliberately excludes fields that mutate on every server write
 * (UserData: played state, playback position, favorite) so a watched/unwatched
 * flip on another client doesn't surface a false "metadata changed" signal.
 * It covers the fields a resync would actually overwrite: overview, people,
 * genres, studios, ratings, runtime, year, tagline, name, original title.
 */
@Singleton
class OfflineSyncComparator @Inject constructor() {

    /**
     * Deterministic hash of the user-facing metadata fields. Stable across equal
     * inputs and insensitive to field declaration order (collections are sorted
     * before hashing). Null and empty are normalized so "no overview" and
     * "blank overview" produce the same signature.
     */
    fun metadataSignature(detail: MediaDetail): String {
        val item = detail.item
        val md = MessageDigest.getInstance("SHA-256")
        val parts = buildList {
            add("n" to item.name.trim())
            add("ot" to item.originalTitle?.trim().orEmpty())
            add("ov" to item.overview?.trim().orEmpty())
            add("y" to (item.year?.toString().orEmpty()))
            add("cr" to (item.communityRating?.toString().orEmpty()))
            add("orr" to (item.officialRating?.trim().orEmpty()))
            add("rt" to (item.runTimeTicks?.toString().orEmpty()))
            add("tl" to detail.taglines.joinToString("|") { it.trim() }.trim())
            // Genres/studios: sorted so reordering doesn't flip the signature.
            add("g" to item.genres.map { it.trim() }.filter { it.isNotEmpty() }.sorted().joinToString("|"))
            add("s" to (item.studios.map { it.trim() }.filter { it.isNotEmpty() }.sorted()
                + detail.studios.map { it.name.trim() }.filter { it.isNotEmpty() }.sorted())
                .joinToString("|"))
            // People: id+name+role, sorted by id so cast ordering churn doesn't
            // trigger a resync — only actual cast changes do.
            add("p" to detail.people
                .map { "${it.id}|${it.name.trim()}|${it.role?.trim().orEmpty()}" }
                .sorted()
                .joinToString("§"))
            add("crt" to (detail.criticRating?.toString().orEmpty()))
        }
        val payload = parts.joinToString("\n") { (k, v) -> "$k=$v" }
        return md.digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /** Builds the baseline that should be persisted alongside an item. */
    fun baseline(detail: MediaDetail): SyncBaseline {
        val source = detail.mediaSources.firstOrNull()
        return SyncBaseline(
            posterTag = detail.posterImageTag,
            backdropTag = detail.backdropImageTag,
            metadataSignature = metadataSignature(detail),
            mediaSourceId = source?.id,
            mediaSizeBytes = source?.size,
        )
    }

    /**
     * Diffs a fresh [MediaDetail] against a persisted baseline. Returns the
     * resulting [OfflineSyncState]; [SyncStatus.UPDATE_AVAILABLE] when metadata
     * or images changed, regardless of whether the media file also changed.
     *
     * Image comparison is tag-based: Jellyfin issues a new image tag whenever an
     * image is replaced, so a tag change is a reliable proxy for "image bytes
     * changed" without fetching bytes. Null-vs-null and null-vs-blank are treated
     * as equal to avoid false positives on items that never had a backdrop.
     *
     * Media-file comparison keys on the primary MediaSource id and size; a change
     * here means the underlying file the user downloaded is no longer the file
     * the server would serve, which a metadata/images resync cannot fix — so it
     * is reported via [OfflineSyncState.mediaFileChanged] for separate handling.
     */
    fun diff(baseline: SyncBaseline, fresh: MediaDetail, itemId: String): ResyncCheckResult {
        val freshSignature = metadataSignature(fresh)
        val source = fresh.mediaSources.firstOrNull()

        val metadataChanged = freshSignature != baseline.metadataSignature
        val imagesChanged = imageChanged(baseline.posterTag, fresh.posterImageTag) ||
            imageChanged(baseline.backdropTag, fresh.backdropImageTag)
        val mediaFileChanged = mediaSourceChanged(baseline, source)

        val status = if (metadataChanged || imagesChanged || mediaFileChanged) {
            SyncStatus.UPDATE_AVAILABLE
        } else {
            SyncStatus.CURRENT
        }
        return ResyncCheckResult(
            itemId = itemId,
            state = OfflineSyncState(
                status = status,
                metadataChanged = metadataChanged,
                imagesChanged = imagesChanged,
                mediaFileChanged = mediaFileChanged,
                lastCheckedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** True when a primary [MediaSource]'s id or size differs from [baseline].
     *  Exposed separately so a resync can re-evaluate the media-file flag after
     *  refreshing metadata without re-running the full diff. */
    fun isMediaSourceChanged(baseline: SyncBaseline, source: MediaSource?): Boolean =
        mediaSourceChanged(baseline, source)

    /** True when an image tag differs from the baseline tag (null/blank normalized). */
    fun isImageChanged(baselineTag: String?, freshTag: String?): Boolean =
        imageChanged(baselineTag, freshTag)

    private fun imageChanged(baselineTag: String?, freshTag: String?): Boolean {
        // Normalize null/blank together: an item that gains or loses an image
        // entirely is a real change, but null<->"" (server omitted vs absent)
        // is not.
        val b = baselineTag?.takeIf { it.isNotBlank() }
        val f = freshTag?.takeIf { it.isNotBlank() }
        return b != f
    }

    private fun mediaSourceChanged(baseline: SyncBaseline, fresh: MediaSource?): Boolean {
        val idChanged = fresh?.id != null && fresh.id != baseline.mediaSourceId
        // Only treat a size delta as a change when both are known; a transition
        // null->known is captured by the id check (a new MediaSource) rather
        // than risk false positives from the server not reporting size.
        val sizeChanged = fresh?.size != null && baseline.mediaSizeBytes != null &&
            fresh.size != baseline.mediaSizeBytes
        return idChanged || sizeChanged
    }
}
