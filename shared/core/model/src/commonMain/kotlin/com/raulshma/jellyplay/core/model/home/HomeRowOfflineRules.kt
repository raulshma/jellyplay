package com.raulshma.jellyplay.core.model.home

import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.hasPlaybackPosition
import com.raulshma.jellyplay.core.model.isWatchedOffline
import com.raulshma.jellyplay.core.model.sortedWithCachedKey
import com.raulshma.jellyplay.core.model.wallNowMillis
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant

/**
 * The per-type offline projection bodies — absorbed verbatim from
 * feature/home's offline mirror (`OfflineHomeSections`) so each resume row's
 * local compute lives on ITS [HomeRowModule] instead of a parallel private
 * copy. Everything here is a pure fold over the downloaded store's lists; the
 * prefs types and the localized titles stay in the feature layer and arrive
 * as parameters (see [HomeRowOfflineProjection]'s KDoc).
 *
 * The rules themselves (the played-row exclusion, the hidden-item drop, the
 * Next Up eligibility, the CW+NextUp merge) are NOT duplicated here — they
 * route through [ContinueWatchingRowRule], the same single owner the online
 * fetch, the single-row refresh and the ordering use case consume.
 */

/** Number of items shown in the "Recently Downloaded" row. */
private const val RECENT_LIMIT = 10

/**
 * The minimum-progress rule a resume row applies: the video row's MinResumePct
 * analog (a few scrubbed seconds are not a resume point), or no floor at all.
 */
private enum class ResumeRowFloor {
    /** Video resume rows: drop items under the minimum-progress floor. */
    VIDEO_MIN_PERCENT,

    /**
     * Book resume rows: no floor — books carry no runTimeTicks, so their
     * stored playedPercentage stays 0.0 until the played flag flips, and the
     * video floor would drop every real book.
     */
    NONE,
}

/**
 * Row cap for the offline Next Up section — mirrors the network layer's
 * default `getNextUp(limit = 20)`.
 */
private const val NEXT_UP_LIMIT = 20

/**
 * Row cap for the offline Continue Watching section — mirrors the network
 * layer's default `getResumeItems(limit = 20)`.
 */
private const val CONTINUE_WATCHING_LIMIT = 20

/**
 * Row cap for the offline Continue Reading section — mirrors the network
 * layer's default `getContinueReading(limit = 20)`.
 */
private const val CONTINUE_READING_LIMIT = 20

/**
 * The shared resume-row shape of the offline Continue Watching / Continue
 * Reading rows: position > 0, not played (the #157 rule — the server never
 * resets a finished item's lingering position), not finished by the local
 * watched threshold, not user-hidden, most recently played first, capped.
 *
 * [floor] selects the row's minimum-progress rule — see [ResumeRowFloor] —
 * and with it the #157 rule's half: the video row runs
 * [ContinueWatchingRowRule.filterResumable] (books excluded outright — the
 * candidates above already dropped them), the book row
 * [ContinueWatchingRowRule.filterReadingResumable]. The offline "played"
 * notion fed into the rule is [com.raulshma.jellyplay.core.model.OfflineMediaItem.isWatchedOffline]
 * (the played flag OR the local watched threshold) — the local rendering of
 * the same fact the server row's `isPlayed` carries; the hidden-item drop is
 * the rule's too.
 */
private fun offlineResumeRow(
    candidates: Sequence<OfflineMediaItem>,
    floor: ResumeRowFloor,
    hiddenItemIds: Set<String>,
    limit: Int,
): List<OfflineMediaItem> =
    candidates
        .filter { it.hasPlaybackPosition }
        .filter { floor != ResumeRowFloor.VIDEO_MIN_PERCENT || it.playedPercentage >= 1.0 }
        .toList()
        .let { rows ->
            if (floor == ResumeRowFloor.NONE) {
                ContinueWatchingRowRule.filterReadingResumable(
                    rows,
                    isPlayed = { it.isWatchedOffline },
                    mediaType = { it.mediaType },
                )
            } else {
                ContinueWatchingRowRule.filterResumable(
                    rows,
                    isPlayed = { it.isWatchedOffline },
                    mediaType = { it.mediaType },
                )
            }
        }
        .let { ContinueWatchingRowRule.excludingHiddenItems(it, hiddenItemIds, id = { item -> item.id }) }
        .sortedWithCachedKey(
            keySelector = { isoEpochMillis(it.lastPlayedDate) ?: Long.MIN_VALUE },
            comparator = compareByDescending<Pair<OfflineMediaItem, Long>> { (_, lastPlayedMillis) -> lastPlayedMillis }
                .thenByDescending { (item, _) -> item.createdAt },
        )
        .take(limit)

/**
 * The offline Continue Watching projection: downloaded movies + episodes with
 * a resume position (the server's `IsResumable` rule: position > 0, under the
 * watched threshold), most recently played first, minus the user's hidden CW
 * items, capped. Downloaded SERIES rows are dropped: their aggregate progress
 * is a hierarchy echo, not a resume point — the episodes themselves carry the
 * real progress. BOOK rows are dropped too: reading position surfaces in the
 * offline Continue Reading row, mirroring the online split (ResumeRowFilter's
 * resumable halves). The server zeroes the position once an item is played
 * (UserDataManager marks MaxResumePct-crossing plays complete), so the local
 * mirrors of those rules are `position > 0`, the app's 95% watched threshold,
 * and a small minimum-progress floor (the server's MinResumePct analog — a
 * few seconds scrubbed into a file is not a resume point).
 */
public object ContinueWatchingOfflineProjection : HomeRowOfflineProjection() {
    override fun resumeItems(
        library: List<OfflineMediaItem>,
        episodes: List<OfflineMediaItem>,
        hiddenItemIds: Set<String>,
    ): List<OfflineMediaItem> = offlineResumeRow(
        candidates = library.asSequence()
            .filter { it.mediaType != MediaType.SERIES && it.mediaType != MediaType.BOOK } +
            episodes.asSequence(),
        floor = ResumeRowFloor.VIDEO_MIN_PERCENT,
        hiddenItemIds = hiddenItemIds,
        limit = CONTINUE_WATCHING_LIMIT,
    )

    override fun mirrorRow(cached: HomeSection, ctx: OfflineMirrorContext): OfflineMirrorRow =
        if (!ctx.continueWatchingEnabled || ctx.continueWatching.isEmpty()) {
            OfflineMirrorRow.Dropped
        } else {
            OfflineMirrorRow.Derived(ctx.continueWatching, "offline_continue_watching")
        }
}

/**
 * The offline Continue Reading projection: the books half of the same resume
 * query — downloaded books with reading progress (the identical
 * position > 0 / played / hidden-item rules as Continue Watching), most
 * recently read first, capped. No percentage floor — see [ResumeRowFloor.NONE].
 * Mirrors the online split's `readingResumableOnly`, which keys on played +
 * position only.
 */
public object ContinueReadingOfflineProjection : HomeRowOfflineProjection() {
    override fun resumeItems(
        library: List<OfflineMediaItem>,
        episodes: List<OfflineMediaItem>,
        hiddenItemIds: Set<String>,
    ): List<OfflineMediaItem> = offlineResumeRow(
        candidates = library.asSequence().filter { it.mediaType == MediaType.BOOK },
        floor = ResumeRowFloor.NONE,
        hiddenItemIds = hiddenItemIds,
        limit = CONTINUE_READING_LIMIT,
    )

    override fun mirrorRow(cached: HomeSection, ctx: OfflineMirrorContext): OfflineMirrorRow =
        if (!ctx.continueReadingEnabled || ctx.continueReading.isEmpty()) {
            OfflineMirrorRow.Dropped
        } else {
            OfflineMirrorRow.Derived(ctx.continueReading, "offline_continue_reading")
        }
}

/**
 * The offline Next Up projection — the local mirror of Jellyfin's server-side
 * rule (TVSeriesManager + NextUpService), restricted to what is downloaded;
 * see [computeOfflineNextUp]. The mirror arm swaps in the locally derived
 * list when the separate Next Up row renders (enabled, not merged into
 * Continue Watching, non-empty — decided once by the orchestrator).
 */
public object NextUpOfflineProjection : HomeRowOfflineProjection() {
    override fun nextUpItems(
        episodes: List<OfflineMediaItem>,
        excludedSeriesIds: Set<String>,
        maxDays: Int,
        rewatching: Boolean,
    ): List<OfflineMediaItem> = computeOfflineNextUp(episodes, excludedSeriesIds, maxDays, rewatching)

    override fun mirrorRow(cached: HomeSection, ctx: OfflineMirrorContext): OfflineMirrorRow =
        if (!ctx.showNextUpRow) {
            OfflineMirrorRow.Dropped
        } else {
            OfflineMirrorRow.Derived(ctx.nextUp, "offline_next_up")
        }
}

/**
 * The DOWNLOADED rows' projection: the offline-only fallback partition
 * (recent / movies / series / music) in one pass, and — in the mirror — a
 * drop: DOWNLOADED never appears in an online snapshot.
 */
public object DownloadedOfflineProjection : HomeRowOfflineProjection() {
    override fun downloadedGroups(library: List<OfflineMediaItem>): OfflineDownloadedGroups = partitionOfflineLibrary(library)

    override fun mirrorRow(cached: HomeSection, ctx: OfflineMirrorContext): OfflineMirrorRow =
        OfflineMirrorRow.Dropped
}

/**
 * The LIVE TV mirror arm: unplayable offline — the snapshot row always drops.
 * (No offline compute: LIVE_TV is never derived from the downloaded store.)
 */
public object LiveTvOfflineProjection : HomeRowOfflineProjection() {
    override fun mirrorRow(cached: HomeSection, ctx: OfflineMirrorContext): OfflineMirrorRow =
        OfflineMirrorRow.Dropped
}

/**
 * The RECOMMENDATIONS mirror keeps the snapshot's seed item — the
 * "Because you watched …" header seed is the one identity fact the generic
 * item filter must not strip.
 */
public object RecommendationsOfflineProjection : HomeRowOfflineProjection() {
    override val keepsSeedItem: Boolean = true
}

/**
 * The DOWNLOADED fallback partition of the (mode-filtered) offline library:
 * movies / series / music buckets plus the newest [RECENT_LIMIT] items by
 * download date via a bounded top-k keeper — the same top-k selection the
 * former java.util.PriorityQueue min-heap made (an item enters only if it
 * beats the current oldest; O(n·k) with k = RECENT_LIMIT, a handful — the
 * heap's tie-breaking at equal createdAt was arbitrary either way).
 */
private fun partitionOfflineLibrary(library: List<OfflineMediaItem>): OfflineDownloadedGroups {
    val movies = ArrayList<OfflineMediaItem>()
    val series = ArrayList<OfflineMediaItem>()
    val music = ArrayList<OfflineMediaItem>()
    val recentKept = ArrayList<OfflineMediaItem>(RECENT_LIMIT)
    for (item in library) {
        when (item.mediaType) {
            MediaType.MOVIE -> movies += item
            MediaType.SERIES -> series += item
            MediaType.AUDIO, MediaType.MUSIC, MediaType.ALBUM -> music += item
            // Other types (PHOTO, PHOTO_FOLDER, …) have no home row here.
            else -> Unit
        }
        if (recentKept.size < RECENT_LIMIT) {
            recentKept.add(item)
        } else {
            var oldestIndex = 0
            var oldestCreatedAt = Long.MAX_VALUE
            for ((index, kept) in recentKept.withIndex()) {
                if (kept.createdAt < oldestCreatedAt) {
                    oldestCreatedAt = kept.createdAt
                    oldestIndex = index
                }
            }
            if (item.createdAt > oldestCreatedAt) {
                recentKept[oldestIndex] = item
            }
        }
    }
    return OfflineDownloadedGroups(
        recent = recentKept.sortedByDescending { it.createdAt },
        movies = movies,
        series = series,
        music = music,
    )
}

/**
 * Offline Next Up — the local mirror of Jellyfin's server-side rule
 * (TVSeriesManager + NextUpService), restricted to what is downloaded:
 *
 *  1. **Series selection** — only series with watch activity (any downloaded
 *     episode carrying a parseable `lastPlayedDate`); ordered by most recent
 *     activity, capped, and dropped entirely when older than the Next Up date
 *     cutoff ([maxDays], the `nextUpDateCutoff` param the online fetch sends).
 *  2. **Anchor** — the highest (season, episode) PLAYED episode (specials
 *     excluded, matching the server's `ParentIndexNumber != 0` filter).
 *  3. **Next episode** — the first UNPLAYED episode strictly AFTER the
 *     anchor in (season, episode) order; with no played episode yet, the
 *     first unplayed episode of the series. A candidate that already has a
 *     resume position is skipped (server: `EnableResumable = false` —
 *     mid-watch episodes live in Continue Watching, not Next Up).
 *  4. **Rewatching** — with [rewatching] on (the online `enableRewatching`
 *     param), a second per-series pass picks the first PLAYED episode after
 *     the most-recently-played one, appended alongside the regular entry.
 *
 * Entries sort by their series' most recent watch activity (most recent
 * first), then series id for stability; the row is capped at [NEXT_UP_LIMIT].
 * Sorting by the series activity — not the anchor episode's date — keeps a
 * series whose only activity is a resumable (unplayed) episode ranked by
 * when that watch happened instead of sinking to the row's tail.
 *
 * Stored `lastPlayedDate` strings mix server-synced ISO stamps (UTC `Z`,
 * nanosecond fraction) with local `OffsetDateTime.now().toString()` writes
 * (host-zone offset, variable precision), so every date ordering and the
 * cutoff compare go through [isoEpochMillis] — those forms are NOT
 * lexicographically comparable (a `+02:00` stamp vs a `Z` stamp mis-orders
 * by hours).
 *
 * Named divergence: the server additionally interleaves specials
 * (`DisplaySpecialsWithinSeasons`) via aired-before/after ordering — the
 * offline row does not persist that metadata, so specials (season 0) are
 * excluded here, matching the server's base season/episode order.
 */
private fun computeOfflineNextUp(
    episodes: List<OfflineMediaItem>,
    excludedSeriesIds: Set<String>,
    maxDays: Int,
    rewatching: Boolean,
): List<OfflineMediaItem> {
    if (episodes.isEmpty()) return emptyList()

    /** One next-up row entry: the episode plus its sort key (series activity, epoch millis). */
    data class NextUpEntry(
        val episode: OfflineMediaItem,
        val lastWatched: Long,
        val seriesId: String,
    )

    // Specials (season 0) and unsighted episodes (null season) are not
    // Next Up material — the server's `ParentIndexNumber != 0` filter.
    val nonSpecials = episodes.filter { it.seasonNumber != null && it.seasonNumber != 0 }

    // The "remove from Next Up" series blocklist through the rule's single
    // owner (ContinueWatchingRowRule.nextUpEligible) — the same eligibility
    // fold the online NEXT_UP arms apply. The CW-overlap half is empty here:
    // the resumable candidates were already diverted to Continue Watching by
    // the per-series rule below, which is the offline rendering of that drop.
    val eligible = ContinueWatchingRowRule.nextUpEligible(
        nonSpecials,
        continueWatchingIds = emptySet(),
        excludedSeriesIds = excludedSeriesIds,
        id = { it.id },
        seriesId = { it.seriesId },
    )

    val bySeries = LinkedHashMap<String, MutableList<OfflineMediaItem>>()
    for (episode in eligible) {
        val seriesId = episode.seriesId ?: continue
        bySeries.getOrPut(seriesId) { ArrayList() }.add(episode)
    }

    // Same cutoff the online fetch sends as `nextUpDateCutoff`: now - maxDays.
    val cutoffMillis = maxDays.takeIf { it > 0 }
        ?.let { wallNowMillis() - it.toLong() * MILLIS_PER_DAY }

    val entries = ArrayList<NextUpEntry>(bySeries.size)
    for ((seriesId, group) in bySeries) {
        // Season/episode order; nulls first (mirrors the DAO query's ASC sort).
        val ordered = group.sortedWith(seasonEpisodeOrder)

        // Series eligibility: any watch activity, within the date cutoff.
        val lastActivityMillis =
            ordered.mapNotNull { isoEpochMillis(it.lastPlayedDate) }.maxOrNull() ?: continue
        if (cutoffMillis != null && lastActivityMillis < cutoffMillis) continue

        // Anchor: the highest played episode by (season, episode).
        val anchor = ordered.lastOrNull { it.isPlayed }

        // First unplayed episode strictly after the anchor (all unplayed
        // when nothing is played yet). Resumable candidates are skipped —
        // they render in Continue Watching instead.
        val unplayed = ordered.asSequence().filter { !it.isPlayed }
        val regularCandidate =
            (if (anchor != null) unplayed.filter { isAfter(it, anchor) } else unplayed)
                .filter { !it.hasPlaybackPosition }
                .firstOrNull()
        if (regularCandidate != null) {
            entries += NextUpEntry(regularCandidate, lastActivityMillis, seriesId)
        }

        // Rewatch pass: the first PLAYED episode after the most-recently
        // played one (server keys the rewatch anchor by date, not position).
        if (rewatching) {
            val played = ordered.filter { it.isPlayed }
            val dateAnchor =
                played.maxByOrNull { isoEpochMillis(it.lastPlayedDate) ?: Long.MIN_VALUE }
            if (dateAnchor != null) {
                val rewatchCandidate = played
                    .filter { isAfter(it, dateAnchor) }
                    .filter { !it.hasPlaybackPosition }
                    .minWithOrNull(seasonEpisodeOrder)
                if (rewatchCandidate != null) {
                    entries += NextUpEntry(rewatchCandidate, lastActivityMillis, seriesId)
                }
            }
        }
    }

    return entries
        .sortedWith(
            compareByDescending<NextUpEntry> { it.lastWatched }
                .thenBy { it.seriesId }
        )
        .take(NEXT_UP_LIMIT)
        .map { it.episode }
}

/** (season, episode) order with nulls first — the one ordering every Next Up pass shares. */
private val seasonEpisodeOrder: Comparator<OfflineMediaItem> = compareBy(
    { it.seasonNumber ?: Int.MIN_VALUE },
    { it.episodeNumber ?: Int.MIN_VALUE },
)

/** True when [episode] sits strictly after [anchor] in (season, episode) order. */
private fun isAfter(
    episode: OfflineMediaItem,
    anchor: OfflineMediaItem?,
): Boolean = anchor == null || seasonEpisodeOrder.compare(episode, anchor) > 0

/** Milliseconds in one day — the Next Up date-cutoff unit. */
private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Parses the `lastPlayedDate` forms the offline store carries into comparable
 * epoch millis: server-synced ISO stamps (offset / `Z`, up to nanosecond
 * fraction), local `OffsetDateTime.now().toString()` writes (host-zone
 * offset, variable precision) and bare local dates. Null when blank or
 * unparseable — callers treat null as "no activity".
 *
 * kotlinx-datetime replaces the former java.time trio. Honest
 * deltas vs `OffsetDateTime.parse` (ISO_OFFSET_DATE_TIME): kotlinx's
 * ISO_DATE_TIME_OFFSET requires SECONDS in the offset where java accepted
 * their absence, and accepts bare-hours offsets ("+02") where java
 * rejected them — the offline store's writes always carry full ±HH:MM
 * offsets, so no real payload moves legs.
 */
private fun isoEpochMillis(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    val zone = TimeZone.currentSystemDefault()
    // The swallow is parse-scoped by construction: all three legs can only
    // throw on malformed input (IllegalArgumentException family). A null
    // return renders as "no activity" for that day — the pre-port behavior
    // for DateTimeParseException.
    return runCatching {
        when {
            value.length == 10 -> // bare local date (`2026-01-05`)
                LocalDate.parse(value).atStartOfDayIn(zone).toEpochMilliseconds()
            ISO_OFFSET_SUFFIX.containsMatchIn(value) -> {
                val parsed = DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET.parse(value)
                parsed.toLocalDateTime().toInstant(parsed.toUtcOffset()).toEpochMilliseconds()
            }
            else -> // bare local date-time, no offset
                LocalDateTime.parse(value).toInstant(zone).toEpochMilliseconds()
        }
    }.getOrNull()
}

/** Trailing `Z` / `±HH:mm` offset on a stored ISO timestamp. */
private val ISO_OFFSET_SUFFIX = Regex("(?:Z|[+-]\\d{2}:\\d{2})$")
