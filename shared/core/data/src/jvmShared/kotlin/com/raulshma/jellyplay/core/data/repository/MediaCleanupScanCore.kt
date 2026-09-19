package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.datastore.toEnumOrNull
import com.raulshma.jellyplay.core.database.dao.AuditLogDao
import com.raulshma.jellyplay.core.database.dao.ScanStateDao
import com.raulshma.jellyplay.core.database.entity.MediaAuditLogEntity
import com.raulshma.jellyplay.core.database.entity.ScanStateEntity
import com.raulshma.jellyplay.core.model.AuditItemDetail
import com.raulshma.jellyplay.core.model.AuditLogEntry
import com.raulshma.jellyplay.core.model.CleanupActionType
import com.raulshma.jellyplay.core.model.JellyfinUser
import com.raulshma.jellyplay.core.model.MediaCleanupConfig
import com.raulshma.jellyplay.core.model.MediaItemStub
import com.raulshma.jellyplay.core.model.ScanPhase
import com.raulshma.jellyplay.core.model.ScanProgress
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The media-cleanup half of [AdminStatisticsRepositoryImpl] — the stale/watched
 * scan engine and the audit-log family it feeds, extracted verbatim (the
 * [DownloadSidecarCore] shape) so a scan-policy change no longer edits the
 * class every statistics test constructs:
 *  - the scan chassis [runScan] (page-by-page accumulation, the targeted
 *    progress write whose 0-affected-rows read means cancelled, the completion
 *    upsert, the catch → FAILED tail, the [MAX_SCAN_RESULTS] cap) plus its two
 *    configurations [runStaleMediaScan] / [runWatchedMediaScan];
 *  - the progress/result reads ([getScanProgress]'s row → [ScanProgress]
 *    mapping and [getScanResultJson]);
 *  - the audit family: [removeMediaItems]'s delete + entry persist, the
 *    [getAuditHistory] decode, and the 90-day retention prune
 *    [cleanupOldAuditLogs] (the repository's status refresh keeps its original
 *    call site and delegates here).
 *
 * Shared-input decisions (nothing is duplicated across the split):
 *  - [timeSource] and [labels] are genuinely shared — the statistics half's
 *    chart/streak math and type-breakdown labels, this core's added-ago
 *    ladder, audit timestamp, prune cutoff and scan-row formatting — so each
 *    has ONE owner: the repository's constructor parameter, whose instance is
 *    handed to this core.
 *  - the `whenPlugin` gate is statistics-half-only and stays there: the scans
 *    fetch through `getStaleItems`/`getWatchedItems`/`getUsers`
 *    unconditionally, so this core takes no plugin status at all and the
 *    gate's caller-captured one-status-per-page-load property is untouched.
 *  - [formatSize] has exactly one caller (the watched scan's size text) and
 *    moved with it instead of being passed in.
 *
 * The repository constructs this from its own constructor dependencies, so
 * its public constructor — and every existing test construction of it — is
 * unchanged ([DownloadSidecarCore] precedent). [runScan] is internal (not
 * private) so [MediaCleanupScanCoreTest] can pin the chassis corners the
 * repository-level suite never reached (cancel-stop, FAILED tail, cap).
 */
internal class MediaCleanupScanCore(
    private val apiClient: JellyfinApiClient,
    private val auditLogDao: AuditLogDao,
    private val scanStateDao: ScanStateDao,
    private val json: Json,
    /**
     * Shared application scope for the fire-and-forget background scans
     * (the application-scope Koin single — never cancelled for this
     * singleton, matching the scan jobs' previous dedicated-scope lifetime).
     */
    private val scope: CoroutineScope,
    private val labels: AdminStatisticsLabelProvider,
    private val timeSource: TimeSource,
) {
    suspend fun detectStaleMedia(config: MediaCleanupConfig): Result<String> = runCatchingRethrowingCancellation {
        val scanId = java.util.UUID.randomUUID().toString()
        scanStateDao.insert(
            ScanStateEntity(
                scanId = scanId,
                type = "STALE",
                configJson = json.encodeToString(MediaCleanupConfig.serializer(), config),
                status = ScanPhase.SCANNING.name,
            )
        )
        scope.launch { runStaleMediaScan(scanId, config) }
        scanId
    }

    suspend fun detectWatchedMedia(config: MediaCleanupConfig): Result<String> = runCatchingRethrowingCancellation {
        val scanId = java.util.UUID.randomUUID().toString()
        scanStateDao.insert(
            ScanStateEntity(
                scanId = scanId,
                type = "WATCHED",
                configJson = json.encodeToString(MediaCleanupConfig.serializer(), config),
                status = ScanPhase.SCANNING.name,
            )
        )
        scope.launch { runWatchedMediaScan(scanId, config) }
        scanId
    }

    /**
     * Stale-media scan as a [runScan] config: one paged `getStaleItems` pass
     * whose continuation rule is `page full && startIndex < server total`,
     * progress clamped to the server total, and every raw row mapped 1:1 to
     * a stub (the label formatting pinned by the test suite).
     */
    private suspend fun runStaleMediaScan(scanId: String, config: MediaCleanupConfig) {
        val pageSize = 200
        var startIndex = 0
        var pageStartIndex = 0
        var hasMore = true
        runScan(
            scanId = scanId,
            fetchNextPage = {
                if (!hasMore) {
                    null
                } else {
                    pageStartIndex = startIndex
                    val result = apiClient.getStaleItems(
                        daysThreshold = config.daysThreshold,
                        includeNeverPlayed = config.includeNeverPlayed,
                        includeItemTypes = config.includeItemTypes.toList(),
                        startIndex = startIndex,
                        limit = pageSize,
                        useDateAdded = config.useDateAdded,
                    ).getOrDefault(Pair(0, emptyList()))
                    startIndex += pageSize
                    hasMore = result.second.size >= pageSize && startIndex < result.first
                    result
                }
            },
            mapRows = { rows ->
                rows.map { staleItem ->
                    val dateStr = if (config.useDateAdded) staleItem.dateAdded else staleItem.lastPlayedDate
                    val formattedDate = dateStr?.take(10)
                    val neverPlayed = staleItem.daysSincePlay <= 0 && staleItem.playCount == 0
                    val addedAgoText = staleItem.dateAdded?.let { added ->
                        try {
                            val addedDate = java.time.LocalDate.parse(added.take(10))
                            val days = java.time.temporal.ChronoUnit.DAYS.between(
                                addedDate,
                                timeSource.today(java.time.ZoneId.systemDefault()),
                            )
                            when {
                                days < 1 -> labels.addedToday()
                                days == 1L -> labels.addedOneDayAgo()
                                days < 30 -> labels.addedDaysAgo(days.toInt())
                                days < 365 -> labels.addedMonthsAgo((days / 30).toInt())
                                else -> labels.addedYearsAgo((days / 365).toInt())
                            }
                        } catch (_: Exception) { null }
                    }
                    MediaItemStub(
                        itemId = staleItem.itemId,
                        name = staleItem.name,
                        type = staleItem.type,
                        sizeText = staleItem.sizeText,
                        detail = buildString {
                            if (staleItem.daysSincePlay > 0) append(labels.daysSincePlay(staleItem.daysSincePlay))
                            else append(labels.neverPlayed())
                            if (staleItem.playCount > 0) {
                                append(" · " + labels.playsCount(staleItem.playCount))
                            }
                        },
                        seriesName = staleItem.seriesName,
                        seasonName = staleItem.seasonName,
                        seasonNumber = staleItem.seasonNumber,
                        episodeNumber = staleItem.episodeNumber,
                        dateText = if (config.useDateAdded) {
                            formattedDate?.let { labels.addedDate(it) } ?: labels.addedUnknown()
                        } else if (neverPlayed) {
                            addedAgoText ?: labels.neverPlayed()
                        } else {
                            formattedDate?.let { labels.playedDate(it) }
                        },
                    )
                }
            },
            progressOf = { page, _, foundSoFar ->
                Triple(minOf(pageStartIndex + page.second.size, page.first), page.first, foundSoFar)
            },
        )
    }

    /**
     * Watched-media scan as a [runScan] config: a per-user cursor flattened
     * into the single page stream (a short page advances to the next user),
     * the partial-watch filter + cross-user dedup in [mapRows], and progress
     * reported as the running found count against a live-computed total.
     */
    private suspend fun runWatchedMediaScan(scanId: String, config: MediaCleanupConfig) {
        val pageSize = 200
        // The user list is fetched lazily inside the fetch closure so a throw
        // on it lands in [runScan]'s catch → FAILED tail, exactly where the
        // hand-rolled loop's getUsers() (inside its try) put it.
        var users: List<JellyfinUser>? = null
        var userIndex = 0
        var startIndex = 0
        // Seen-id set alongside the mapped results so dedup is O(1) per item
        // instead of O(n) via a results.any{}. Without this the .mapNotNull
        // below is O(total_watched_items²) because the results grow every
        // iteration. First-occurrence wins, identical to the previous
        // results.any{} semantics — output ordering is irrelevant here
        // (results are persisted as JSON and the UI doesn't depend on
        // insertion order).
        val seenItemIds = HashSet<String>()
        runScan(
            scanId = scanId,
            fetchNextPage = {
                val list = users ?: apiClient.getUsers().getOrDefault(emptyList()).also { users = it }
                val user = list.getOrNull(userIndex)
                if (user == null) {
                    null
                } else {
                    val result = apiClient.getWatchedItems(
                        userId = user.id,
                        includeItemTypes = config.includeItemTypes.toList(),
                        minDaysSincePlayed = config.minDaysSinceWatched,
                        keepFavorites = config.keepFavorites,
                        startIndex = startIndex,
                        limit = pageSize,
                    ).getOrDefault(Pair(0, emptyList()))
                    if (result.second.size >= pageSize) {
                        startIndex += pageSize
                    } else {
                        userIndex++
                        startIndex = 0
                    }
                    result
                }
            },
            mapRows = { rows ->
                rows
                    .filter { if (!config.includePartiallyWatched) it.completionPct >= 0.9f else true }
                    .mapNotNull { watched ->
                        if (!seenItemIds.add(watched.itemId)) return@mapNotNull null
                        val lastPlayedStr = watched.lastPlayedDate?.take(10)
                        MediaItemStub(
                            itemId = watched.itemId,
                            name = watched.name,
                            type = watched.type,
                            sizeText = formatSize(watched.sizeBytes),
                            detail = buildString {
                                append(labels.playsCount(watched.playCount))
                                if (watched.completionPct < 1f) {
                                    append(" · ${(watched.completionPct * 100).toInt()}%")
                                }
                            },
                            seriesName = watched.seriesName,
                            seasonName = watched.seasonName,
                            seasonNumber = watched.seasonNumber,
                            episodeNumber = watched.episodeNumber,
                            dateText = lastPlayedStr?.let { labels.playedDate(it) },
                        )
                    }
            },
            progressOf = { page, keptCount, foundSoFar ->
                Triple(foundSoFar, foundSoFar + (page.first.coerceAtLeast(0) - keptCount), foundSoFar)
            },
        )
    }

    /**
     * Shared chassis of the two cleanup scans (the ~90-line structural twins
     * [runStaleMediaScan] / [runWatchedMediaScan]): page-by-page accumulation,
     * a targeted progress write after every page (0 affected rows = the scan
     * row was deleted, i.e. cancelled — stop without the full-row read), the
     * completion upsert, and the catch → FAILED tail. The scans differ only
     * in configuration:
     *  - [fetchNextPage] yields the next raw page (`totalHint` to rows); a
     *    null return ends the loop — each scan folds its own continuation
     *    rule (stale: full page + server total; watched: per-user cursors)
     *    into the closure.
     *  - [mapRows] converts one raw page to the persisted stubs (the watched
     *    scan's partial-watch filter + dedup live here).
     *  - [progressOf] derives `(progress, total, itemsFound)` for the targeted
     *    write from the raw page, this page's kept count, and the running
     *    found count — the two scans' accounting rules differ and stay theirs.
     */
    internal suspend fun <R> runScan(
        scanId: String,
        fetchNextPage: suspend () -> Pair<Int, List<R>>?,
        mapRows: (List<R>) -> List<MediaItemStub>,
        progressOf: (page: Pair<Int, List<R>>, keptCount: Int, foundSoFar: Int) -> Triple<Int, Int, Int>,
    ) {
        try {
            val allResults = mutableListOf<MediaItemStub>()
            while (true) {
                val page = fetchNextPage() ?: break
                val items = mapRows(page.second)
                // Retain at most MAX_SCAN_RESULTS stubs: without the
                // cap the scan accumulates the whole server library in memory
                // and then serializes it as one JSON blob — on a 50k-item
                // server a transient double allocation (list + multi-MB
                // string) on this background worker. Truncation past the cap
                // is accepted behavior for these explicit admin actions (the
                // cleanup UI already reads the audit log at LIMIT 500);
                // progressOf still sees the full raw page, so the progress
                // accounting is unchanged.
                val room = MAX_SCAN_RESULTS - allResults.size
                allResults.addAll(if (room >= items.size) items else items.take(room))

                // Targeted progress write; 0 affected rows = scan row deleted
                // (cancelled) — stop without the full-row read.
                val (progress, total, itemsFound) = progressOf(page, items.size, allResults.size)
                if (scanStateDao.updateProgress(
                        scanId = scanId,
                        progress = progress,
                        total = total,
                        itemsFound = itemsFound,
                    ) == 0
                ) {
                    return
                }

                // Cap reached — stop paging; every further page would only be
                // fetched to be discarded.
                if (allResults.size >= MAX_SCAN_RESULTS) break
            }

            val entity = scanStateDao.getById(scanId) ?: return
            scanStateDao.update(
                entity.copy(
                    status = ScanPhase.COMPLETED.name,
                    progress = entity.total,
                    itemsFound = allResults.size,
                    resultJson = json.encodeToString(ListSerializer(MediaItemStub.serializer()), allResults),
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val entity = scanStateDao.getById(scanId)
            if (entity != null) {
                scanStateDao.update(entity.copy(status = ScanPhase.FAILED.name))
            }
        }
    }

    fun getScanProgress(scanId: String): Flow<ScanProgress> =
        scanStateDao.observeProgress(scanId).map { row ->
            if (row == null) ScanProgress()
            else ScanProgress(
                phase = row.status.toEnumOrNull() ?: ScanPhase.IDLE,
                scanned = row.progress,
                total = row.total,
                itemsFound = row.itemsFound,
            )
        }

    suspend fun getScanResultJson(scanId: String): String? {
        return scanStateDao.getById(scanId)?.resultJson
    }

    suspend fun removeMediaItems(
        itemIds: List<String>,
        itemNameMap: Map<String, String>,
        actionType: CleanupActionType,
        config: MediaCleanupConfig,
    ): Result<AuditLogEntry> = runCatchingRethrowingCancellation {
        val currentUser = apiClient.currentUser.first()
        val adminId = currentUser?.id ?: ""
        val adminName = currentUser?.name ?: ""

        val deleted = apiClient.deleteItems(itemIds).getOrThrow()

        val itemDetails = itemIds.map { id ->
            AuditItemDetail(
                itemId = id,
                name = itemNameMap[id] ?: "",
                type = "",
                sizeText = "",
                detail = "",
            )
        }

        val entry = AuditLogEntry(
            id = java.util.UUID.randomUUID().toString(),
            timestamp = timeSource.nowEpochMillis(),
            adminUserId = adminId,
            adminUserName = adminName,
            actionType = actionType,
            configSnapshot = json.encodeToString(MediaCleanupConfig.serializer(), config),
            itemCount = itemDetails.size,
            itemDetails = itemDetails,
        )

        auditLogDao.insert(
            MediaAuditLogEntity(
                id = entry.id,
                timestamp = entry.timestamp,
                adminUserId = entry.adminUserId,
                adminUserName = entry.adminUserName,
                actionType = entry.actionType.name,
                configJson = entry.configSnapshot,
                itemCount = entry.itemCount,
                itemDetailsJson = json.encodeToString(
                    kotlinx.serialization.serializer<List<AuditItemDetail>>(),
                    entry.itemDetails,
                ),
            )
        )

        entry
    }

    fun getAuditHistory(actionType: CleanupActionType?): Flow<List<AuditLogEntry>> =
        if (actionType != null) {
            auditLogDao.getByActionType(actionType.name)
        } else {
            auditLogDao.getAll()
        }.map { entities ->
            entities.map { entity ->
                AuditLogEntry(
                    id = entity.id,
                    timestamp = entity.timestamp,
                    adminUserId = entity.adminUserId,
                    adminUserName = entity.adminUserName,
                    actionType = entity.actionType.toEnumOrNull() ?: CleanupActionType.STALE_REMOVAL,
                    configSnapshot = entity.configJson,
                    itemCount = entity.itemCount,
                    itemDetails = runCatching {
                        json.decodeFromString<List<AuditItemDetail>>(entity.itemDetailsJson)
                    }.getOrDefault(emptyList()),
                )
            }
        }

    /**
     * The audit-log retention window (the 90-day `deleteOlderThan` cutoff);
     * called by [AdminStatisticsRepositoryImpl.refreshPlaybackReportingStatus]
     * — the prune's original call site before the split.
     */
    suspend fun cleanupOldAuditLogs() {
        try {
            val ninetyDaysAgo = timeSource.nowEpochMillis() - 90L * 24 * 60 * 60 * 1000
            auditLogDao.deleteOlderThan(ninetyDaysAgo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d("AdminStats", "Failed to cleanup old audit logs", e)
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
    }

    companion object {
        /**
         * Upper bound on the stubs a cleanup scan retains (and serializes
         * into one `scan_state` row) — see [runScan]. Two orders of magnitude
         * above the audit-log LIMIT 500 the cleanup UI already reads
         * ([AuditLogDao.getAll]), and far beyond any actionable manual
         * selection; only >5k-item explicit admin scans are truncated.
         */
        internal const val MAX_SCAN_RESULTS = 5_000
    }
}
