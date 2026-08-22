package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Freshness status of an offline item relative to the server. Orthogonal to
 * [DownloadStatus] (which tracks the media-file transfer), this tracks whether
 * the persisted metadata / images are stale.
 */
@Immutable
@Serializable
enum class SyncStatus {
    /** No check has ever completed for this item (never synced / new download). */
    UNKNOWN,

    /** Last check found no changes; within the freshness TTL. */
    CURRENT,

    /** A check is in progress. */
    CHECKING,

    /** Server has newer metadata and/or images than the persisted baseline. */
    UPDATE_AVAILABLE,

    /** Last check failed (network error, server unreachable, …). */
    ERROR,
}

/**
 * The freshness state of a single offline item, surfaced to the UI. Persisted
 * (per content axis) on the `sync_baseline` row so a badge can render from the
 * DB with no network, and projected losslessly by
 * [com.raulshma.jellyplay.core.data.sync.OfflineSyncManager].
 */
@Immutable
@Serializable
data class OfflineSyncState(
    val status: SyncStatus,
    val metadataChanged: Boolean = false,
    val imagesChanged: Boolean = false,
    /** External subtitle streams differ from the persisted baseline signature.
     *  Computed in [com.raulshma.jellyplay.core.data.sync.OfflineSyncComparator]
     *  from the MediaDetail subtitle inventory and persisted as its own flag so
     *  the DB-driven badge surfaces it accurately. Only flipped by a resync that
     *  fetches fresh detail (the proactive check derives it from MediaDetail). */
    val subtitlesChanged: Boolean = false,
    /** Trickplay sprite manifest differs from the baseline signature. Persisted
     *  on its own flag and surfaced accurately by the DB-driven badge. */
    val trickplayChanged: Boolean = false,
    /** Media segments (intro/outro/recap) differ from the baseline signature.
     *  Only computed inside a resync that fetches fresh segments (segments are
     *  not part of MediaDetail), so this never flips from the proactive check. */
    val segmentsChanged: Boolean = false,
    /** The media file itself changed server-side (different MediaSource id/size).
     *  Surfaced separately because a resync cannot fix it — it requires a full
     *  re-download of the media file. */
    val mediaFileChanged: Boolean = false,
    /** Epoch millis of the last completed server check. Null when never checked. */
    val lastCheckedAt: Long? = null,
) {
    /** True when a lightweight resync (metadata, images, subtitles, trickplay,
     *  segments) will bring the item up to date. */
    val needsResync: Boolean get() =
        metadataChanged || imagesChanged || subtitlesChanged || trickplayChanged || segmentsChanged
}

/**
 * Result of a single-item freshness check. [needsResync] is true when a
 * metadata/images resync would bring the item current; [mediaFileChanged] is
 * reported separately since it requires a media-file re-download.
 */
@Immutable
@Serializable
data class ResyncCheckResult(
    val itemId: String,
    val state: OfflineSyncState,
)

/**
 * User-facing selection of which data categories a resync should refresh. Maps
 * onto the optional [ResyncStep]s (metadata -> PERSIST_METADATA, poster ->
 * DOWNLOAD_POSTER, backdrop -> DOWNLOAD_BACKDROP, subtitles ->
 * DOWNLOAD_SUBTITLES, trickplay -> DOWNLOAD_TRICKPLAY, segments ->
 * DOWNLOAD_SEGMENTS); FETCH_DETAIL and UPDATE_BASELINE are always-on
 * infrastructure and therefore not selectable.
 *
 * Defaults to all-true so the existing "resync everything" call sites behave
 * identically when the parameter is omitted. A user-driven force resync passes
 * an explicit selection (e.g. metadata + poster only) to skip categories.
 */
@Immutable
@Serializable
data class ResyncOptions(
    val metadata: Boolean = true,
    val poster: Boolean = true,
    val backdrop: Boolean = true,
    val subtitles: Boolean = true,
    val trickplay: Boolean = true,
    val segments: Boolean = true,
) {
    /** True when no category is selected — callers should treat this as a no-op. */
    val isEmpty: Boolean get() =
        !metadata && !poster && !backdrop && !subtitles && !trickplay && !segments

    companion object {
        /** Resync every category. The historical default behaviour. */
        val ALL get() = ResyncOptions()
    }
}

/** A single step in a resync operation, for granular progress reporting. */
@Immutable
@Serializable
enum class ResyncStep {
    FETCH_DETAIL,
    PERSIST_METADATA,
    DOWNLOAD_POSTER,
    DOWNLOAD_BACKDROP,
    DOWNLOAD_SUBTITLES,
    DOWNLOAD_TRICKPLAY,
    DOWNLOAD_SEGMENTS,
    UPDATE_BASELINE,
}

@Immutable
@Serializable
data class ResyncStepResult(
    val itemId: String,
    val step: ResyncStep,
    val success: Boolean,
    val message: String? = null,
)

/** Aggregate result of resyncing a single item. */
@Immutable
@Serializable
data class ResyncResult(
    val itemId: String,
    val steps: List<ResyncStepResult>,
    val mediaFileChanged: Boolean,
) {
    val succeeded: Boolean get() = steps.isNotEmpty() && steps.all { it.success }
}

/** Per-item progress phase for batch resync UI. */
@Immutable
@Serializable
enum class ResyncPhase { PENDING, WORKING, DONE, ERROR }

@Immutable
@Serializable
data class ResyncItemProgress(
    val itemId: String,
    val phase: ResyncPhase,
    val currentStep: ResyncStep? = null,
)

/**
 * Live progress for a batch resync, exposed as a [kotlinx.coroutines.flow.StateFlow]
 * so the downloads sheet can render per-item status + aggregate counts in real time.
 */
@Immutable
@Serializable
data class ResyncBatchProgress(
    val items: Map<String, ResyncItemProgress> = emptyMap(),
    val total: Int = 0,
) {
    val completed: Int get() = items.values.count { it.phase == ResyncPhase.DONE || it.phase == ResyncPhase.ERROR }
    val active: Boolean get() = items.values.any { it.phase == ResyncPhase.WORKING || it.phase == ResyncPhase.PENDING }
}

/**
 * Lightweight view of an item flagged for resync, surfaced to the downloads
 * screen's resync sheet. [mediaFileChanged] is reported separately because it
 * can't be fixed by a metadata/image resync.
 *
 * The episode-context fields ([mediaType], [seriesName], [seasonNumber],
 * [episodeNumber]) let the sheet render the same SXXEXX + series line the
 * downloads list shows, so episodes are identifiable in the flat sheet list.
 */
@Immutable
@Serializable
data class OfflineSyncUpdate(
    val id: String,
    val name: String,
    val mediaFileChanged: Boolean,
    val mediaType: MediaType? = null,
    val seriesName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
)
