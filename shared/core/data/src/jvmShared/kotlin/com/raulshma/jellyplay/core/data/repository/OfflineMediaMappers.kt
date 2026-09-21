package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.datastore.toEnumOrNull
import com.raulshma.jellyplay.core.database.dao.DownloadProgressRow
import com.raulshma.jellyplay.core.database.dao.OfflineMediaWithPlayback
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import com.raulshma.jellyplay.core.database.entity.PlaybackStateEntity
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflinePersonInfo

/**
 * The single owner of the offline/download entity ⟷ domain mapping — the
 * persistence-side mappers both repository engines share over the same tables:
 *
 *  - `offline_media` ([OfflineMediaEntity]): written by
 *    [DownloadRepositoryImpl] via [MediaItem.toOfflineMediaEntity] /
 *    [MediaDetail.toOfflineMediaEntity], read by [OfflineRepositoryImpl] via
 *    [OfflineMediaWithPlayback.toOfflineMediaItem].
 *  - `playback_state` ([PlaybackStateEntity]): download-time seed via
 *    [MediaItem.toPlaybackState].
 *  - `downloads` ([DownloadEntity]): read via [DownloadEntity.toDownloadItem] /
 *    [DownloadProgressRow.toDownloadProgress].
 *
 * The two homes previously carried these as private members, so the table's
 * column semantics (CSV joining, subtitle clearing, blob null-vs-empty, enum
 * fallbacks) were known in two places and could drift. They are documented
 * here once; write-side normalization and its read-side restoration are
 * deliberate mirror images, not duplication to flatten.
 *
 * ## Column contract
 *
 * **Series subtitle clearing (write-side normalization).** `seriesName` is
 * persisted only for EPISODE and SEASON rows, `seasonName` only for EPISODE
 * rows — for any other type the server's series/season subtitle would just
 * duplicate the item's own title, so it is nulled on write. The read side
 * restores both verbatim; only rows that were written by this mapper ever
 * carry a non-null value.
 *
 * **CSV columns (`genres`, `studios`).** Written via `joinToString(",")` — an
 * empty list persists as `""`, never null. Read back by splitting on `,`,
 * trimming each element and dropping empties, with null yielding the empty
 * list. The pair is round-trip safe (`[] → "" → []`) and read-normalizes
 * surrounding whitespace the write side does not trim. Inherent CSV limit,
 * unchanged since the column existed: a genre/studio name containing a comma
 * cannot round-trip.
 *
 * **JSON blob columns (`peopleJson`, `providerIdsJson`, `externalUrlsJson`,
 * `chaptersJson`).** Written only by the detail path
 * ([MediaDetail.toOfflineMediaEntity]) as `null` when the collection is empty
 * (unlike the CSV columns' `""`). Decoded by the lenient codecs in
 * [OfflineRepositoryImpl] (`encodeCast`/`decodeCast` & siblings): null, blank
 * and garbage blobs decode to the empty collection, so rows written before a
 * column existed degrade gracefully. `peopleJson` persists **actors only**
 * (`type == "Actor"`); other person types are dropped on write even though
 * cast-image preloading considers them.
 *
 * **Bare-item upsert hazard.** [MediaItem.toOfflineMediaEntity] leaves every
 * rich column (`originalTitle`…`chaptersJson`) at its entity default (null) —
 * the DAO upserts REPLACE, so re-persisting an item whose detail row was
 * already written wipes those blobs. That is why the download engine's
 * series/season seeding never re-upserts the episode row through the item
 * mapper (see `seedEpisodeParents`).
 *
 * **Write-only columns.** `parentId`, `indexNumber` and `premiereDate` are
 * persisted for hierarchy/ordering queries and search ordering but have no
 * [OfflineMediaItem] counterpart — they are dropped on read by design.
 * `childCount` is nullable in the table and defaults to `0` on read.
 *
 * **Enum round-trips.** `mediaType` persists the enum name and reads back
 * through the repo-wide [toEnumOrNull] seam, degrading an unknown persisted
 * value to [MediaType.UNKNOWN]. The `downloads.status` column degrades to
 * [DownloadStatus.FAILED] instead — a row whose status cannot be parsed must
 * surface as failed, not as a phantom "unknown" state the UI cannot act on.
 *
 * **Playback seed.** [MediaItem.toPlaybackState] derives `playedPercentage`
 * from the position/runtime ticks via [PlayedStateSync.computePlayedPercentage]
 * (isPlayed short-circuits to 100, matching server UserData semantics) and
 * seeds `lastPlayedDate = null` — the download moment is not a play. Read-side
 * playback columns come from the `offline_media ⟕ playback_state` LEFT JOIN
 * and fall back to the "not started" defaults (0.0 / false) when no row
 * exists.
 */
// Visibility: internal — these cross the repository engines and the module's
// own tests, but are not part of the module's public surface. Same package as
// the impls so their previously-private call sites resolve unchanged.

/**
 * Persists the lightweight [MediaItem] form for an offline row (no chapters,
 * cast, or other [MediaDetail]-only fields — see the file-level column
 * contract for the upsert hazard that follows from that).
 *
 * Applies the write-side normalizations: series-subtitle clearing
 * (`seriesName` only for EPISODE/SEASON, `seasonName` only for EPISODE) and
 * the `genres` CSV join (empty list → `""`).
 */
internal fun MediaItem.toOfflineMediaEntity(imageUrl: String?, backdropUrl: String?) = OfflineMediaEntity(
    id = id,
    name = name,
    mediaType = mediaType.name,
    overview = overview,
    year = year,
    communityRating = communityRating,
    officialRating = officialRating,
    runTimeTicks = runTimeTicks,
    parentId = parentId,
    seriesId = seriesId,
    seasonId = seasonId,
    // Clear the series subtitle for top-level entities where it would just
    // duplicate the title; only episodes carry a meaningful
    // series name distinct from their own.
    seriesName = if (mediaType == MediaType.EPISODE || mediaType == MediaType.SEASON) seriesName else null,
    seasonName = if (mediaType == MediaType.EPISODE) seasonName else null,
    episodeNumber = episodeNumber,
    seasonNumber = seasonNumber,
    indexNumber = indexNumber,
    childCount = childCount,
    posterPath = imageUrl,
    backdropPath = backdropUrl,
    blurHashPrimary = blurHashes.primary,
    blurHashBackdrop = blurHashes.backdrop,
    premiereDate = premiereDate,
    genres = genres.joinToString(","),
)

/**
 * Server `UserData` snapshot seeded at download time (and re-seeded on a
 * metadata re-persist) into `playback_state`. Mirrors the playback fields
 * the metadata row used to carry, so a freshly downloaded item shows its
 * watched / resume state immediately.
 */
internal fun MediaItem.toPlaybackState(): PlaybackStateEntity = PlaybackStateEntity(
    id = id,
    playbackPositionTicks = playbackPositionTicks,
    playedPercentage = PlayedStateSync.computePlayedPercentage(playbackPositionTicks, runTimeTicks, isPlayed),
    isPlayed = isPlayed,
    isFavorite = isFavorite,
    lastPlayedDate = null,
)

/**
 * Maps a [MediaDetail] (the rich server response) to an [OfflineMediaEntity],
 * additionally persisting original title, critic rating, studios, tagline,
 * the cast as a JSON blob, and the chapter list as a JSON blob (so chapter
 * markers and the chapter sheet work offline). Falls back to the item-level
 * values for the base fields so this stays consistent with
 * [MediaItem.toOfflineMediaEntity].
 *
 * Rich columns follow the blob contract: `null` when the collection is empty,
 * and `peopleJson` carries actors only (`type == "Actor"`). `studios` uses the
 * CSV contract (empty list → `""`).
 */
internal fun MediaDetail.toOfflineMediaEntity(imageUrl: String?, backdropUrl: String?): OfflineMediaEntity {
    val base = item.toOfflineMediaEntity(imageUrl, backdropUrl)
    val cast = people
        .filter { it.type == "Actor" }
        .map { person ->
            OfflinePersonInfo(
                id = person.id,
                name = person.name,
                role = person.role,
                type = person.type,
                imageTag = person.primaryImageTag,
                blurHash = person.primaryBlurHash,
            )
        }
    return base.copy(
        originalTitle = item.originalTitle,
        criticRating = criticRating,
        studios = item.studios.joinToString(","),
        tagline = taglines.firstOrNull(),
        peopleJson = if (cast.isEmpty()) null else encodeCast(cast),
        providerIdsJson = if (providerIds.isEmpty()) null else encodeProviderIds(providerIds),
        externalUrlsJson = if (externalUrls.isEmpty()) null else encodeExternalUrls(externalUrls),
        chaptersJson = if (chapters.isEmpty()) null else encodeChapters(chapters),
    )
}

/** Name → [MediaType] with the unknown-value degradation documented above. */
private fun safeMediaTypeOf(name: String): MediaType =
    name.toEnumOrNull() ?: MediaType.UNKNOWN

/**
 * Maps a metadata + playback join row to the UI model. Playback fields come
 * from the LEFT JOIN'd `playback_state` columns and fall back to the same
 * "not started" defaults a missing row carried under the old single-table
 * shape.
 *
 * Applies the read-side restorations of the file-level column contract: CSV
 * split/trim/filter for `genres`/`studios`, lenient blob decodes, the
 * `childCount ?: 0` default, the [MediaType.UNKNOWN] media-type fallback, and
 * the drop of the write-only hierarchy/ordering columns.
 */
internal fun OfflineMediaWithPlayback.toOfflineMediaItem(): OfflineMediaItem {
    val m = media
    return OfflineMediaItem(
        id = m.id,
        name = m.name,
        mediaType = safeMediaTypeOf(m.mediaType),
        overview = m.overview,
        year = m.year,
        communityRating = m.communityRating,
        officialRating = m.officialRating,
        runTimeTicks = m.runTimeTicks,
        seriesId = m.seriesId,
        seasonId = m.seasonId,
        seriesName = m.seriesName,
        seasonName = m.seasonName,
        episodeNumber = m.episodeNumber,
        seasonNumber = m.seasonNumber,
        posterPath = m.posterPath,
        backdropPath = m.backdropPath,
        blurHashPrimary = m.blurHashPrimary,
        blurHashBackdrop = m.blurHashBackdrop,
        genres = m.genres?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() } ?: emptyList(),
        childCount = m.childCount ?: 0,
        playbackPositionTicks = playbackPositionTicks,
        playedPercentage = playedPercentage ?: 0.0,
        isPlayed = isPlayed ?: false,
        isFavorite = isFavorite ?: false,
        lastPlayedDate = lastPlayedDate,
        originalTitle = m.originalTitle,
        criticRating = m.criticRating,
        studios = m.studios?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() } ?: emptyList(),
        tagline = m.tagline,
        cast = decodeCast(m.peopleJson),
        providerIds = decodeProviderIds(m.providerIdsJson),
        externalUrls = decodeExternalUrls(m.externalUrlsJson),
        chapters = decodeChapters(m.chaptersJson),
        createdAt = m.createdAt,
    )
}

/**
 * `downloads` row → feature-facing [DownloadItem]. Enum columns degrade as
 * documented in the file-level contract: unknown `mediaType` →
 * [MediaType.UNKNOWN], unparseable `status` → [DownloadStatus.FAILED] (a row
 * the UI cannot act on must surface as failed, not as a phantom state).
 */
internal fun DownloadEntity.toDownloadItem() = DownloadItem(
    id = id,
    mediaItemId = mediaItemId,
    name = name,
    mediaType = mediaType.toEnumOrNull() ?: MediaType.UNKNOWN,
    downloadPath = downloadPath,
    downloadUrl = downloadUrl,
    totalSizeBytes = totalSizeBytes,
    downloadedBytes = downloadedBytes,
    status = status.toEnumOrNull() ?: DownloadStatus.FAILED,
    speedBytesPerSec = speedBytesPerSec,
    mediaSourceId = mediaSourceId,
    imageUrl = imageUrl,
    imageBlurHash = imageBlurHash,
    seriesId = seriesId,
    seasonId = seasonId,
    seriesName = seriesName,
    seasonName = seasonName,
    episodeNumber = episodeNumber,
    seasonNumber = seasonNumber,
    errorMessage = errorMessage,
    priority = priority,
    container = container,
)

/** DAO progress projection → feature-facing [DownloadProgress] (repository boundary keeps DAO types in). */
internal fun DownloadProgressRow.toDownloadProgress() = DownloadProgress(
    id = id,
    downloadedBytes = downloadedBytes,
    speedBytesPerSec = speedBytesPerSec,
)
