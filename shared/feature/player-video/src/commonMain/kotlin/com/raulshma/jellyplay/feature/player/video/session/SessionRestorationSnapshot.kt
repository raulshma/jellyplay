package com.raulshma.jellyplay.feature.player.video.session

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.isVideoType

/**
 * The cold-start restoration candidate: the last item the SERVER reports as
 * mid-playback, carried as plain values so the policy ([decideSessionRestoration])
 * and the shells never touch a repository type. Server-authoritative by design —
 * there is deliberately NO new local storage behind this type: the snapshot is
 * derived from the same resume data the home Continue Watching row renders
 * ([com.raulshma.jellyplay.core.network.api.LibraryApiClient.getContinueWatching]),
 * fetched once per process through the shell-provided seam and cached nowhere.
 *
 * INCOGNITO NOTE: a session played with incognito playback enabled reports no
 * progress to the server (the playback reporting is suppressed), so the server's
 * resume data carries nothing for it — this feature has nothing to offer for
 * incognito sessions by construction. No local incognito record is consulted or
 * written here; the incognito offline-resume handling stays where it already
 * lives (the player's own offline-resume resolution).
 */
data class SessionRestorationSnapshot(
    val itemId: String,
    val title: String,
    /** The server's resume position in milliseconds (`playbackPositionTicks / 10_000`). */
    val positionMs: Long,
    /**
     * When the server last stamped the item's play state (epoch millis, from the
     * row's `lastPlayedDate`). `null` when the server did not date the row — the
     * policy treats a missing stamp as FRESH, mirroring the process-death rule in
     * `resolveResumeTicks` (a zero/missing timestamp never blocks a restore).
     */
    val timestampMs: Long?,
)

/**
 * Maps the server's resume rows into the cold-start candidate: the most
 * recently dated PLAYABLE video row (movie / episode / music video — the
 * player-video surface this feature restores into; Series/Season rollups and
 * book rows are not direct video routes and are skipped).
 *
 * WHY NOT SIMPLY ROWS.FIRST: the resume query cannot carry an explicit sort
 * from here (the wire shape is the client's), so recency is decided client-side
 * over the rows' `lastPlayedDate` stamps. Rows without a parsable stamp sort
 * last, keeping the relative order among them (the server's order). `null` when
 * no row qualifies — the legitimately-empty case (nothing mid-playback, or an
 * incognito-only history — see the [SessionRestorationSnapshot] KDoc), which the
 * policy folds to `None`.
 *
 * The timestamp parse follows the same ladder as core:ui's
 * `parseIsoTimestampToEpochMillis` (offset-aware first, then bare local
 * datetime in the system zone — the Jellyfin SDK mapper's form); it is
 * reproduced here because that helper is internal to core:ui and this module
 * must not grow a core:ui production edge for five lines.
 */
fun latestResumeVideoSnapshot(rows: List<MediaItem>): SessionRestorationSnapshot? =
    rows.asSequence()
        .filter { it.mediaType.isVideoType && it.hasResumePosition }
        .map { row -> row to parseEpochMsOrNull(row.lastPlayedDate) }
        .maxWithOrNull(
            // Undated rows sort last (null stamp → MIN); among equal stamps
            // the item id breaks the tie so the pick is deterministic.
            compareBy({ it.second ?: Long.MIN_VALUE }, { it.first.id }),
        )
        ?.let { (row, stamp) ->
            SessionRestorationSnapshot(
                itemId = row.id,
                title = row.name,
                positionMs = (row.playbackPositionTicks ?: 0L) / 10_000L,
                timestampMs = stamp,
            )
        }

/** A resume row this feature may restore into: a non-zero server position. */
private val MediaItem.hasResumePosition: Boolean
    get() = (playbackPositionTicks ?: 0L) > 0L

/** Nullable stamp → epoch millis, `null` when absent or unparseable (never throws). */
private fun parseEpochMsOrNull(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    // Offset-aware first ("…Z" and "…±hh:mm" forms).
    runCatching {
        return java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }
    // Bare local datetime (the Jellyfin SDK mapper's toString form) in system zone.
    return runCatching {
        java.time.LocalDateTime.parse(value).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
}
