package com.raulshma.jellyplay.core.data.sync

import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
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
 *
 * An empty signature string (`""`) means "this axis has no baseline yet" (a
 * pre-feature row or an axis that was never synced). The [diff] treats an empty
 * baseline signature as non-comparable so a first contact never flags a
 * spurious change — the axis is silently seeded instead.
 */
data class SyncBaseline(
    val posterTag: String?,
    val backdropTag: String?,
    val metadataSignature: String,
    val subtitleSignature: String,
    /**
     * True when the subtitle sidecar bundle failed and was never fetched (the
     * persisted `sync_baseline.syncSubtitlesPending` flag). Forces the subtitle
     * axis to flag as changed regardless of signature equality — the retry
     * driver for a bundle that never landed. Lives beside (not inside)
     * [subtitleSignature] so that column remains a pure server snapshot.
     */
    val subtitlesPending: Boolean = false,
    val trickplaySignature: String,
    val segmentsSignature: String,
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
 * genres, studios, ratings, runtime, year, tagline, name, original title,
 * and chapters (name / startPositionTicks / imageTag) so server chapter edits
 * after download surface as "update available" and a metadata resync refreshes
 * chapters.
 *
 * The sidecar axes (subtitles, trickplay, segments) carry their own content
 * signatures because Jellyfin exposes no etag/version for them. Subtitles and
 * trickplay are derived from [MediaDetail] (free with the detail fetch);
 * segments are not part of [MediaDetail] and are only compared when a caller
 * passes a freshly fetched list (see [diff]).
 */
@Singleton
class OfflineSyncComparator @Inject constructor() {

    companion object {
        /**
         * Separator between list items in a metadata-signature part, chosen
         * because it never occurs in Jellyfin field values — a person name or
         * chapter title containing `§` can't merge two items. (A value that
         * does contain [FIELD_SEPARATOR] only shifts fields within that one
         * item's rendering; the payload is hashed, never parsed back.)
         * Signature format is frozen: changing these literals would shift
         * every digest and read as fleet-wide churn.
         */
        private const val ITEM_SEPARATOR = "§"

        /** Separator between fields inside one list item's rendering. */
        private const val FIELD_SEPARATOR = "|"
    }

    /**
     * Deterministic hash of the user-facing metadata fields. Stable across equal
     * inputs and insensitive to field declaration order (collections are sorted
     * before hashing). Null and empty are normalized so "no overview" and
     * "blank overview" produce the same signature.
     */
    fun metadataSignature(detail: MediaDetail): String {
        val item = detail.item
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
                .map { listOf(it.id, it.name.trim(), it.role?.trim().orEmpty()).joinToString(FIELD_SEPARATOR) }
                .sorted()
                .joinToString(ITEM_SEPARATOR))
            // Chapters: name+startPositionTicks+imageTag, ordered by start ticks
            // then name so a server-side reorder without content change doesn't
            // flip the signature. (Sorting must happen on the parsed values: the
            // serialized strings start with the name.)
            add("ch" to detail.chapters
                .asSequence()
                .map { Triple(it.name.trim(), it.startPositionTicks, it.imageTag?.trim().orEmpty()) }
                .sortedWith(compareBy({ (_, ticks) -> ticks }, { (name, _) -> name }))
                .joinToString(ITEM_SEPARATOR) { (name, ticks, imageTag) ->
                    listOf(name, ticks, imageTag).joinToString(FIELD_SEPARATOR)
                })
            add("crt" to (detail.criticRating?.toString().orEmpty()))
        }
        val payload = parts.joinToString("\n") { (k, v) -> "$k=$v" }
        return sha256Hex(payload)
    }

    /**
     * Deterministic hash of the deliverable subtitle inventory (external +
     * embedded-with-delivery-url SUBTITLE streams). Sorted by index so stream
     * reordering doesn't flip the signature; only real changes to the set of
     * subtitles (added/removed/index/codec/language/forced/SDH/external) do.
     * Matches the filter [com.raulshma.jellyplay.core.data.repository.OfflineDownloadWriter.downloadExternalSubtitles]
     * applies, so the signature describes exactly the streams a resync would
     * re-download.
     */
    fun subtitleSignature(detail: MediaDetail): String {
        val streams = deliverableSubtitles(detail)
        if (streams.isEmpty()) return ""
        val payload = streams
            .sortedBy { it.index }
            .joinToString("\n") { s ->
                listOf(
                    s.index,
                    s.codec.orEmpty().trim(),
                    s.language.orEmpty().trim(),
                    s.isForced,
                    s.isHearingImpaired,
                    s.isExternal,
                    s.displayTitle.orEmpty().trim(),
                ).joinToString("|") { it.toString() }
            }
        return sha256Hex(payload)
    }

    /**
     * Deterministic signature of the trickplay sprite grid from its
     * [com.raulshma.jellyplay.core.model.TrickplayInfo]. Covers the structural
     * fields that decide whether the on-disk tiles are still valid
     * (grid + count + interval); excludes [com.raulshma.jellyplay.core.model.TrickplayInfo.bandwidth],
     * which can vary by server transcode load without invalidating the tiles.
     * Returns the empty string when the source carries no trickplay info.
     *
     * Unlike [metadataSignature]/[subtitleSignature]/[segmentsSignature] this is
     * a raw `width|height|…` join rather than a SHA-256 hex: the inputs are a
     * fixed set of integers, so there's no entropy or length-attack benefit to
     * hashing, and the readable form aids debugging. Still opaque to callers,
     * who only ever compare it for equality.
     */
    fun trickplaySignature(detail: MediaDetail): String {
        val info = detail.mediaSources.firstOrNull()?.trickplayInfo ?: return ""
        return listOf(info.width, info.height, info.tileWidth, info.tileHeight, info.thumbnailCount, info.interval)
            .joinToString("|") { it.toString() }
    }

    /**
     * Deterministic hash of a media-segment list. Sorted by (startTicks, type)
     * so reordering doesn't flip the signature; covers count + each segment's
     * type and bounds. Returns the empty string when there are no segments so
     * an item with no segments doesn't fingerprint differently from one whose
     * segments have never been fetched.
     */
    fun segmentsSignature(segments: List<MediaSegment>): String {
        if (segments.isEmpty()) return ""
        val payload = "${segments.size}\n" + segments
            .sortedWith(compareBy({ it.startTicks }, { it.type.name }))
            .joinToString("\n") { "${it.type.name}|${it.startTicks}|${it.endTicks}" }
        return sha256Hex(payload)
    }

    /**
     * Builds the baseline that should be persisted alongside an item. Subtitles
     * and trickplay signatures are derived from [detail] (free); the segments
     * signature is taken from [segments] when supplied, otherwise left empty
     * (segments are not part of [MediaDetail] and are only available after a
     * separate fetch).
     */
    fun baseline(detail: MediaDetail, segments: List<MediaSegment>? = null): SyncBaseline {
        val source = detail.mediaSources.firstOrNull()
        return SyncBaseline(
            posterTag = detail.posterImageTag,
            backdropTag = detail.backdropImageTag,
            metadataSignature = metadataSignature(detail),
            subtitleSignature = subtitleSignature(detail),
            trickplaySignature = trickplaySignature(detail),
            segmentsSignature = segments?.let { segmentsSignature(it) }.orEmpty(),
            mediaSourceId = source?.id,
            mediaSizeBytes = source?.size,
        )
    }

    /**
     * Diffs a fresh [MediaDetail] against a persisted baseline. Returns the
     * resulting [OfflineSyncState]; [SyncStatus.UPDATE_AVAILABLE] when any
     * comparable axis changed, regardless of whether the media file also changed.
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
     *
     * The metadata and sidecar (subtitle, trickplay, segment) axes compare the
     * fresh content signature against the baseline signature via [hasChanged]:
     * an empty baseline signature means "never recorded" and never flags
     * (first-contact seeding handled by the caller) — the shared rule is also
     * what lets a signature payload format change be retired cleanly by
     * clearing the stored baseline. Segments additionally require the caller
     * to pass [freshSegments] (they are not part of [MediaDetail]), so a check
     * that skips the segments fetch never flags segments-changed.
     */
    fun diff(
        baseline: SyncBaseline,
        fresh: MediaDetail,
        itemId: String,
        freshSegments: List<MediaSegment>? = null,
    ): ResyncCheckResult {
        val freshMetadataSig = metadataSignature(fresh)
        val freshSubtitleSig = subtitleSignature(fresh)
        val freshTrickplaySig = trickplaySignature(fresh)
        val freshSegmentsSig = freshSegments?.let { segmentsSignature(it) }
        val source = fresh.mediaSources.firstOrNull()

        // hasChanged, not raw inequality: an empty baseline signature ("never
        // recorded") must seed silently rather than flag — the metadata axis
        // follows the same first-contact rule as the sidecar axes.
        val metadataChanged = hasChanged(baseline.metadataSignature, freshMetadataSig)
        val imagesChanged = imageChanged(baseline.posterTag, fresh.posterImageTag) ||
            imageChanged(baseline.backdropTag, fresh.backdropImageTag)
        val mediaFileChanged = mediaSourceChanged(baseline, source)
        val subtitlesChanged = subtitleAxisChanged(baseline, freshSubtitleSig)
        val trickplayChanged = hasChanged(baseline.trickplaySignature, freshTrickplaySig)
        val segmentsChanged = hasChanged(baseline.segmentsSignature, freshSegmentsSig)

        val status = if (metadataChanged || imagesChanged || mediaFileChanged ||
            subtitlesChanged || trickplayChanged || segmentsChanged
        ) {
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
                subtitlesChanged = subtitlesChanged,
                trickplayChanged = trickplayChanged,
                segmentsChanged = segmentsChanged,
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

    /**
     * True when the subtitle axis needs a re-fetch: the bundle is pending
     * (failed and never fetched), or the fresh inventory signature differs from
     * the baseline. Exposed so a resync can decide whether to re-fetch.
     */
    fun isSubtitleChanged(baseline: SyncBaseline, fresh: MediaDetail): Boolean =
        subtitleAxisChanged(baseline, subtitleSignature(fresh))

    /** True when the fresh trickplay signature differs from the baseline. */
    fun isTrickplayChanged(baseline: SyncBaseline, fresh: MediaDetail): Boolean =
        hasChanged(baseline.trickplaySignature, trickplaySignature(fresh))

    /**
     * The subtitle axis's single flag rule, shared by [diff] and
     * [isSubtitleChanged]: a pending bundle (failed and never fetched) flags
     * regardless of signature equality — that is the retry driver — otherwise
     * [hasChanged]'s first-contact guard applies.
     */
    private fun subtitleAxisChanged(baseline: SyncBaseline, freshSubtitleSignature: String?): Boolean =
        baseline.subtitlesPending ||
            hasChanged(baseline.subtitleSignature, freshSubtitleSignature)

    private fun deliverableSubtitles(detail: MediaDetail): List<MediaStream> =
        detail.mediaSources.firstOrNull()?.mediaStreams
            ?.filter { it.isBundleableSubtitle }
            ?: emptyList()

    /**
     * First-contact-aware signature compare shared by [diff] and the is*Changed
     * helpers. An empty baseline ("never recorded") never flags, and a null
     * fresh signature (axis not fetched this pass) never flags; otherwise the
     * axis changed iff the fresh signature differs from the baseline. Keeping
     * the rule here means the first-contact guard can't drift between call sites.
     */
    private fun hasChanged(baseline: String, freshSignature: String?): Boolean {
        if (baseline.isEmpty()) return false
        if (freshSignature == null) return false
        return freshSignature != baseline
    }

    /** SHA-256 of [payload] (UTF-8) as a lowercase hex string. */
    private fun sha256Hex(payload: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

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
