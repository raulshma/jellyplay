package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.PlaybackStateDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.database.dao.personReferenceLikePattern
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * The one offline-deletion choreography behind every delete path —
 * [OfflineRepositoryImpl.deleteOfflineItem] / [OfflineRepositoryImpl.deleteOfflineSeries] /
 * [OfflineRepositoryImpl.deleteOfflineSeason] and [DownloadRepositoryImpl.cleanupDownloadFiles]
 * were four hand-copies of the same dance; the scopes differ only in the DAO query that collects
 * the `downloads` rows and the metadata delete targets, both of which the caller supplies.
 *
 * The ordering invariants the former copies' comments pinned down, in execution order:
 *
 *  1. **Artifact files before the DB delete** — each download's media file and per-item
 *     artifacts ([DownloadArtifacts.cleanup], scoped by mediaItemId so one item's cleanup never
 *     clobbers another item's images in the shared flat downloads dir) go first, concurrently
 *     off the caller's dispatcher; the subsequent DB transaction does not depend on the file
 *     deletion result.
 *  2. **Capture before the rows are removed** — the affected dirs and the cast ids decoded from
 *     each row's persisted `peopleJson` are captured before the transaction so the cast images
 *     written beside each download at fetch time can be pruned afterward (the reference scan
 *     runs post-delete).
 *  3. **One 4-table cascade transaction** — the `downloads` rows first, then the caller's
 *     metadata targets across `offline_media` / `playback_state` / `sync_baseline`.
 *  4. **Memo evict after the transaction** — the deleted row's artifacts (and possibly its
 *     whole dir) are gone; cached local artwork paths for it and its series siblings would
 *     dangle until process death. Deletes are rare — a full evict is cheap and the next read
 *     re-resolves from disk. Only callers that own the memo pass [delete]'s
 *     [delete.evictArtworkMemo] ([DownloadRepositoryImpl] resolves no artwork and passes none).
 *  5. **Cast-image prune after the delete** — the reference scan must count only *surviving*
 *     rows: a person can appear across many movies/episodes and the `personId`-keyed image
 *     serves all of them, so a shared image is kept while any sibling row still references its
 *     person and is deleted only when the last reference is gone.
 *  6. **Orphan prune last** — childless season/series metadata rows, then playback/baseline
 *     rows whose metadata row just disappeared (or drifted from older data).
 *
 * [DownloadRepositoryImpl.cleanupDownloadFiles] is the one caller whose former inline body
 * diverged from this order (single transaction with an in-transaction orphan prune, no cast
 * cleanup): it now runs the majority choreography — see that method's KDoc for the declared
 * delta.
 */
internal class OfflineDeletionCore(
    private val database: JellyPlayDatabase,
    private val downloadDao: DownloadDao,
    private val offlineMediaDao: OfflineMediaDao,
    private val playbackStateDao: PlaybackStateDao,
    private val syncBaselineDao: SyncBaselineDao,
) {
    /**
     * Runs the full deletion dance for the `downloads` rows the caller collected.
     *
     * [deleteMetadataRows] carries the scope's metadata cascade (offline_media + playback_state
     * + sync_baseline rows, by item id / seriesId / seasonId) and runs inside the transaction
     * after the `downloads` rows are gone. [cleanupSeriesArtwork] (series scope only) prunes
     * the series-scoped poster/backdrop from every affected episode dir between steps 1 and 2.
     * [evictArtworkMemo] (step 4) is the artwork-memo owner's hook.
     */
    suspend fun delete(
        downloads: List<DownloadEntity>,
        deleteMetadataRows: suspend () -> Unit,
        evictArtworkMemo: (() -> Unit)? = null,
        cleanupSeriesArtwork: ((episodeDirs: List<File>) -> Unit)? = null,
    ) {
        // (1) File deletion off the caller's (Main) dispatcher — the DB step below does not
        // depend on the result.
        deleteArtifactsParallel(downloads)
        // Dirs are pure path math over the collected rows (no FS reads), so locating them here
        // — before the transaction removes the rows — is safe. They feed the series-artwork
        // hook and the post-delete cast prune.
        val episodeDirs = downloads
            .asSequence()
            .mapNotNull { it.downloadPath.takeIf { p -> p.isNotBlank() } }
            .mapNotNull { File(it).parentFile }
            .distinct()
            .toList()
        cleanupSeriesArtwork?.invoke(episodeDirs)
        // (2) Capture the deleted rows' cast ids before the rows are removed so the cast images
        // written beside each download at fetch time can be pruned afterward (the reference
        // scan below runs post-delete).
        val mediaById = offlineMediaById(downloads)
        val deletedCastIds = downloads.mapNotNull { mediaById[it.mediaItemId] }
            .flatMap(::castIdsOf)
            .distinct()
        // (3) Downloads first, then the scope's metadata cascade — one transaction so a
        // concurrent read never sees a half-deleted scope.
        database.withTransaction {
            val ids = downloads.map { it.id }
            if (ids.isNotEmpty()) downloadDao.deleteDownloadsByIds(ids)
            deleteMetadataRows()
        }
        // (4) The deleted artifacts may have served as cached artwork sources — drop the memo
        // and let the next read re-resolve from disk.
        evictArtworkMemo?.invoke()
        // (5) Prune cast images after the rows are gone so the reference scan only counts
        // surviving rows — a person still referenced by a sibling's row keeps their shared image.
        cleanupOrphanedCastArtwork(episodeDirs, deletedCastIds)
        // (6)
        pruneOrphans()
    }

    /**
     * Removes orphaned season/series metadata rows, then any playback / baseline rows whose
     * metadata row just disappeared (or drifted from older data). One transaction so a
     * concurrent read never sees a half-cleaned state.
     */
    suspend fun pruneOrphans() {
        database.withTransaction {
            offlineMediaDao.cleanupOrphans()
            playbackStateDao.deleteUnreferenced()
            syncBaselineDao.deleteUnreferenced()
        }
    }

    /**
     * Deletes downloaded artifact files concurrently (was a serial per-episode
     * `File.delete()` + `cleanup()` loop — for a 100-episode series that was
     * 100+ serial FS syscalls). Runs on Dispatchers.IO; the subsequent DB
     * transaction does not depend on the file deletion result.
     *
     * Each download's per-item poster/backdrop are scoped by [DownloadEntity.mediaItemId]
     * so deleting one item's artifacts never clobbers another item's images in
     * the shared flat downloads dir. Cast images are pruned separately via
     * [cleanupOrphanedCastArtwork] (they are keyed by `personId`, not `mediaItemId`).
     */
    private suspend fun deleteArtifactsParallel(downloads: List<DownloadEntity>) {
        val nonBlank = downloads.filter { it.downloadPath.isNotBlank() }
        if (nonBlank.isEmpty()) return
        coroutineScope {
            nonBlank.map { entity ->
                async(Dispatchers.IO) {
                    val file = File(entity.downloadPath)
                    if (file.exists()) file.delete()
                    DownloadArtifacts.cleanup(file.parentFile, entity.mediaItemId)
                }
            }.awaitAll()
        }
    }

    private suspend fun offlineMediaById(downloads: List<DownloadEntity>): Map<String, OfflineMediaEntity> {
        if (downloads.isEmpty()) return emptyMap()
        return offlineMediaDao.getByIds(downloads.map { it.mediaItemId }).associateBy { it.id }
    }

    /**
     * Cast ids carried by [item]'s persisted `peopleJson`, decoded via [decodeCast].
     * Empty when the row has no cast column (older downloads) so cast cleanup is a
     * no-op for them.
     */
    private fun castIdsOf(item: OfflineMediaEntity): List<String> =
        decodeCast(item.peopleJson).map { it.id }

    /**
     * Prunes the cast-image files for [candidateCastIds] from every [parentDirs],
     * keeping any person still referenced by a *surviving* offline row. A person
     * can appear across many movies/episodes and the `personId`-keyed image file
     * serves all of them, so a file is deleted only when its person is no longer
     * referenced anywhere. **Must be called after the deleted rows are removed
     * from the DB** so the per-candidate reference check reflects only surviving
     * rows — otherwise the just-deleted rows would still count as references and
     * nothing would be pruned.
     *
     * Reference checking is a per-candidate existence scan over `peopleJson`
     * ([OfflineMediaDao.isPersonReferenced]), not the former whole-table load +
     * JSON decode of every surviving row's multi-KB cast blob: the candidates
     * (≤ dozens — the union of the deleted rows' cast) are already known here,
     * so each one needs only a "does any surviving blob mention this person"
     * answer, and the EXISTS short-circuits at the first surviving reference.
     * Semantics are unchanged: a person's image survives iff at least one
     * surviving row references them; empty candidates (or no dirs) run zero
     * queries.
     */
    private suspend fun cleanupOrphanedCastArtwork(
        parentDirs: List<File>,
        candidateCastIds: List<String>,
    ) {
        if (parentDirs.isEmpty() || candidateCastIds.isEmpty()) return
        val orphans = candidateCastIds.filter { candidate ->
            !offlineMediaDao.isPersonReferenced(personReferenceLikePattern(candidate))
        }
        if (orphans.isEmpty()) return
        parentDirs.forEach { DownloadArtifacts.cleanupCastArtwork(it, orphans) }
    }
}
