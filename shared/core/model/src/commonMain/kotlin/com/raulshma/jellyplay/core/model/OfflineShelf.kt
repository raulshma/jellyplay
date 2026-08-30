package com.raulshma.jellyplay.core.model

/**
 * The canonical top-level grouping of offline items. Every surface that
 * partitions the offline store by "video vs music" — the home's mode filter,
 * the downloads screen's filter chips — reads THIS enum instead of declaring
 * its own media-type set (three copies used to exist, and they disagreed:
 * the home kept ARTIST rows in music mode while the downloads screen's MUSIC
 * filter didn't).
 *
 * Deliberately Kotlin-side only: the DAO's SQL literals stay as they are —
 * `getTopLevelItems` matches `('SERIES','MOVIE','AUDIO','MUSIC')` and
 * deliberately omits ALBUM/ARTIST there, so a divergence in this enum cannot
 * silently change what a query returns. The DAO's type list is the storage
 * contract; this enum is the presentation partition.
 */
enum class OfflineMediaTypeGroup(val mediaTypes: Set<MediaType>) {
    VIDEO(setOf(MediaType.SERIES, MediaType.MOVIE)),
    MUSIC(setOf(MediaType.AUDIO, MediaType.MUSIC, MediaType.ALBUM, MediaType.ARTIST)),
}

/** Which [OfflineMediaTypeGroup] an offline row belongs to, null for rows with no home-shelf presence (photos, folders, …). */
val OfflineMediaItem.typeGroup: OfflineMediaTypeGroup?
    get() = OfflineMediaTypeGroup.entries.firstOrNull { mediaType in it.mediaTypes }

/**
 * The name/series/season substring match every offline search shares — the
 * repository's `searchOffline` (as SQL LIKE) and client-side query filters
 * (as this predicate) must agree on WHICH fields count. Callers own the
 * minimum-length policy.
 */
fun OfflineMediaItem.matchesOfflineQuery(query: String): Boolean {
    val q = query.trim()
    return name.contains(q, ignoreCase = true) ||
        seriesName?.contains(q, ignoreCase = true) == true ||
        seasonName?.contains(q, ignoreCase = true) == true
}

/**
 * Finished by the same rule the display normalization
 * ([OfflineMediaItem.toMediaItem]) and the player's watched trigger use —
 * one fact, owned by [OFFLINE_WATCHED_THRESHOLD]; read it instead of
 * re-deriving the percentage math per surface.
 */
val OfflineMediaItem.isFinishedOffline: Boolean
    get() = playedPercentage >= OFFLINE_WATCHED_THRESHOLD * 100
