package com.raulshma.jellyplay.core.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import com.raulshma.jellyplay.core.database.entity.PlaybackStateEntity
import com.raulshma.jellyplay.core.database.entity.SyncBaselineEntity
import com.raulshma.jellyplay.core.data.util.SystemTimeSource
import com.raulshma.jellyplay.core.model.OfflinePersonInfo
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * File-backed, real-Room (in-memory, bundled JVM driver) suite over the shared
 * deletion choreography ([OfflineRepositoryImpl.deleteOfflineItem] /
 * deleteOfflineSeries / deleteOfflineSeason — the three scopes
 * [OfflineDeletionCore] serves). Parameterized over the scope, it pins the
 * ordering invariants' observable outcomes:
 *
 *  - a person's shared cast image is RETAINED while a sibling row still
 *    references them (reference scan runs post-delete), while images only the
 *    deleted row referenced are pruned;
 *  - zero orphan rows survive in any of the four tables (the deleted subtree
 *    is gone everywhere, the sibling's rows all survive);
 *  - the deleted leaf's media file and per-item artifacts are gone;
 *  - the one per-scope divergence: series-scoped artwork
 *    (`${seriesId}_poster/backdrop.jpg` beside the episodes) is removed by the
 *    SERIES scope only — item/season deletes keep it for the surviving rows.
 */
class OfflineRepositoryDeletionTest {

    private enum class Scope { ITEM, SEASON, SERIES }

    /** kotlin.test has no TemporaryFolder rule — same contract, one root dir. */
    private val tempRoot = createTempDirectory("offline-deletion-test")

    private fun newFolder(name: String): File =
        tempRoot.resolve(name).toFile().apply { mkdirs() }

    private lateinit var database: JellyPlayDatabase
    private lateinit var repository: OfflineRepositoryImpl

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        repository = OfflineRepositoryImpl(
            database.offlineMediaDao(),
            database.playbackStateDao(),
            database.syncBaselineDao(),
            database.downloadDao(),
            database,
            timeSource = SystemTimeSource(),
        )
    }

    @AfterTest
    fun teardown() {
        database.close()
        tempRoot.toFile().deleteRecursively()
    }

    // ── fixture helpers ──────────────────────────────────────────────────────

    private fun metadata(
        id: String,
        mediaType: String,
        seriesId: String? = null,
        seasonId: String? = null,
        cast: List<OfflinePersonInfo> = emptyList(),
    ) = OfflineMediaEntity(
        id = id,
        name = id,
        mediaType = mediaType,
        seriesId = seriesId,
        seasonId = seasonId,
        peopleJson = if (cast.isEmpty()) null else encodeCast(cast),
    )

    /** Seeds the row in all three metadata-side tables, like a downloaded item. */
    private suspend fun seedRow(entity: OfflineMediaEntity) {
        database.offlineMediaDao().upsert(entity)
        database.playbackStateDao().upsert(PlaybackStateEntity(id = entity.id))
        database.syncBaselineDao().upsert(SyncBaselineEntity(id = entity.id))
    }

    private fun downloadRow(
        mediaItemId: String,
        dir: File,
        seriesId: String? = null,
        seasonId: String? = null,
    ) = DownloadEntity(
        id = "dl-$mediaItemId",
        mediaItemId = mediaItemId,
        name = mediaItemId,
        mediaType = "EPISODE",
        downloadPath = File(dir, "$mediaItemId.mkv").absolutePath,
        downloadUrl = "https://stream",
        totalSizeBytes = 1L,
        downloadedBytes = 1L,
        status = "COMPLETED",
        seriesId = seriesId,
        seasonId = seasonId,
    )

    /** Writes the artifact set the deletion paths manage for [itemId] into [dir]. */
    private fun writeArtifacts(dir: File, itemId: String, castIds: List<String>) {
        File(dir, "$itemId.mkv").writeText("media-bytes")
        File(dir, DownloadArtifacts.posterFile(itemId)).writeText("poster-bytes")
        File(dir, DownloadArtifacts.backdropFile(itemId)).writeText("backdrop-bytes")
        castIds.forEach { File(dir, DownloadArtifacts.personImageFile(it)).writeText("person-bytes") }
    }

    // ── shared-cast scenario (parameterized over the three scopes) ───────────

    private data class Scenario(
        val rootId: String,
        val deletedIds: List<String>,
        val survivingIds: List<String>,
        val deletedLeafId: String,
        val survivingLeafId: String,
        val dirOfDeleted: File,
    )

    /**
     * Builds the scope's tree: the deleted subtree plus a surviving sibling
     * subtree whose leaf rows share cast `person-1`; the deleted leaf uniquely
     * carries `person-2`. Full artifact sets are written into both leaves' dirs.
     */
    private suspend fun seedSharedCastScenario(scope: Scope): Scenario {
        val dirOfDeleted = newFolder("${scope.name}-del")
        val dirOfSurviving = newFolder("${scope.name}-keep")
        val sharedCast = listOf(OfflinePersonInfo(id = "person-1", name = "Lead"))
        val uniqueCast = sharedCast + OfflinePersonInfo(id = "person-2", name = "Guest")
        return when (scope) {
            Scope.ITEM -> {
                seedRow(metadata("movie-del", "MOVIE", cast = uniqueCast))
                seedRow(metadata("movie-keep", "MOVIE", cast = sharedCast))
                database.downloadDao().insertDownload(downloadRow("movie-del", dirOfDeleted))
                database.downloadDao().insertDownload(downloadRow("movie-keep", dirOfSurviving))
                writeArtifacts(dirOfDeleted, "movie-del", listOf("person-1", "person-2"))
                writeArtifacts(dirOfSurviving, "movie-keep", listOf("person-1"))
                Scenario(
                    rootId = "movie-del",
                    deletedIds = listOf("movie-del"),
                    survivingIds = listOf("movie-keep"),
                    deletedLeafId = "movie-del",
                    survivingLeafId = "movie-keep",
                    dirOfDeleted = dirOfDeleted,
                )
            }
            Scope.SEASON -> {
                seedRow(metadata("series-1", "SERIES"))
                seedRow(metadata("season-del", "SEASON", seriesId = "series-1"))
                seedRow(metadata("season-keep", "SEASON", seriesId = "series-1"))
                seedRow(metadata("ep-del", "EPISODE", seriesId = "series-1", seasonId = "season-del", cast = uniqueCast))
                seedRow(
                    metadata("ep-keep", "EPISODE", seriesId = "series-1", seasonId = "season-keep", cast = sharedCast),
                )
                database.downloadDao().insertDownload(
                    downloadRow("ep-del", dirOfDeleted, seriesId = "series-1", seasonId = "season-del"),
                )
                database.downloadDao().insertDownload(
                    downloadRow("ep-keep", dirOfSurviving, seriesId = "series-1", seasonId = "season-keep"),
                )
                writeArtifacts(dirOfDeleted, "ep-del", listOf("person-1", "person-2"))
                writeArtifacts(dirOfSurviving, "ep-keep", listOf("person-1"))
                Scenario(
                    rootId = "season-del",
                    deletedIds = listOf("season-del", "ep-del"),
                    survivingIds = listOf("series-1", "season-keep", "ep-keep"),
                    deletedLeafId = "ep-del",
                    survivingLeafId = "ep-keep",
                    dirOfDeleted = dirOfDeleted,
                )
            }
            Scope.SERIES -> {
                seedRow(metadata("series-del", "SERIES"))
                seedRow(metadata("season-del", "SEASON", seriesId = "series-del"))
                seedRow(metadata("ep-del", "EPISODE", seriesId = "series-del", seasonId = "season-del", cast = uniqueCast))
                seedRow(metadata("series-keep", "SERIES"))
                seedRow(metadata("season-keep", "SEASON", seriesId = "series-keep"))
                seedRow(
                    metadata("ep-keep", "EPISODE", seriesId = "series-keep", seasonId = "season-keep", cast = sharedCast),
                )
                database.downloadDao().insertDownload(
                    downloadRow("ep-del", dirOfDeleted, seriesId = "series-del", seasonId = "season-del"),
                )
                database.downloadDao().insertDownload(
                    downloadRow("ep-keep", dirOfSurviving, seriesId = "series-keep", seasonId = "season-keep"),
                )
                writeArtifacts(dirOfDeleted, "ep-del", listOf("person-1", "person-2"))
                writeArtifacts(dirOfSurviving, "ep-keep", listOf("person-1"))
                Scenario(
                    rootId = "series-del",
                    deletedIds = listOf("series-del", "season-del", "ep-del"),
                    survivingIds = listOf("series-keep", "season-keep", "ep-keep"),
                    deletedLeafId = "ep-del",
                    survivingLeafId = "ep-keep",
                    dirOfDeleted = dirOfDeleted,
                )
            }
        }
    }

    private suspend fun runSharedScenario(scope: Scope) {
        val scenario = seedSharedCastScenario(scope)
        val offlineMediaDao = database.offlineMediaDao()
        val playbackStateDao = database.playbackStateDao()
        val syncBaselineDao = database.syncBaselineDao()
        val downloadDao = database.downloadDao()

        when (scope) {
            Scope.ITEM -> repository.deleteOfflineItem(scenario.rootId)
            Scope.SEASON -> repository.deleteOfflineSeason(scenario.rootId)
            Scope.SERIES -> repository.deleteOfflineSeries(scenario.rootId)
        }

        // Zero orphan metadata rows: the deleted subtree is gone, the sibling's rows survive.
        scenario.deletedIds.forEach { id ->
            assertNull(offlineMediaDao.getById(id), "offline_media row for $id must be deleted")
        }
        scenario.survivingIds.forEach { id ->
            assertNotNull(offlineMediaDao.getById(id), "offline_media row for $id must survive")
        }
        // Zero orphan playback/baseline rows: the cascade + unreferenced prune left
        // exactly the sibling's rows behind.
        scenario.deletedIds.forEach { id ->
            assertNull(playbackStateDao.getById(id), "playback_state row for $id must be deleted")
            assertNull(syncBaselineDao.getBaseline(id), "sync_baseline row for $id must be deleted")
        }
        scenario.survivingIds.forEach { id ->
            assertNotNull(playbackStateDao.getById(id), "playback_state row for $id must survive")
            assertNotNull(syncBaselineDao.getBaseline(id), "sync_baseline row for $id must survive")
        }
        // The deleted leaf's downloads row is gone; the sibling's download survives.
        assertNull(downloadDao.getDownloadByMediaItemId(scenario.deletedLeafId))
        assertNotNull(downloadDao.getDownloadByMediaItemId(scenario.survivingLeafId))

        // Artifacts gone: media file + per-item poster/backdrop of the deleted leaf.
        assertFalse(File(scenario.dirOfDeleted, "${scenario.deletedLeafId}.mkv").exists())
        assertFalse(File(scenario.dirOfDeleted, DownloadArtifacts.posterFile(scenario.deletedLeafId)).exists())
        assertFalse(File(scenario.dirOfDeleted, DownloadArtifacts.backdropFile(scenario.deletedLeafId)).exists())

        // Shared cast image retained: the surviving sibling leaf still references
        // person-1, so their personId-keyed image must NOT be pruned from the
        // deleted leaf's dir…
        assertTrue(
            File(scenario.dirOfDeleted, DownloadArtifacts.personImageFile("person-1")).exists(),
            "cast image still referenced by a sibling row must be retained",
        )
        // …while the image only the deleted row referenced is gone.
        assertFalse(
            File(scenario.dirOfDeleted, DownloadArtifacts.personImageFile("person-2")).exists(),
            "cast image referenced by no surviving row must be pruned",
        )
    }

    @Test
    fun `deleteOfflineItem retains a sibling-shared cast image and leaves zero orphan rows`() = runTest {
        runSharedScenario(Scope.ITEM)
    }

    @Test
    fun `deleteOfflineSeason retains a sibling-shared cast image and leaves zero orphan rows`() = runTest {
        runSharedScenario(Scope.SEASON)
    }

    @Test
    fun `deleteOfflineSeries retains a sibling-shared cast image and leaves zero orphan rows`() = runTest {
        runSharedScenario(Scope.SERIES)
    }

    // ── per-scope divergence: series-scoped artwork cleanup ──────────────────
    // Only the whole-series scope prunes ${seriesId}_poster/backdrop.jpg beside
    // the episodes (no sibling row would use them anymore); item and season
    // deletes keep the files for the surviving series/episodes.

    private suspend fun runSeriesArtworkScenario(scope: Scope) {
        val dir = newFolder("series-art-${scope.name}")
        seedRow(metadata("series-1", "SERIES"))
        seedRow(metadata("season-1", "SEASON", seriesId = "series-1"))
        seedRow(metadata("ep-1", "EPISODE", seriesId = "series-1", seasonId = "season-1"))
        database.downloadDao().insertDownload(
            downloadRow("ep-1", dir, seriesId = "series-1", seasonId = "season-1"),
        )
        val poster = File(dir, DownloadArtifacts.posterFile("series-1")).apply { writeText("poster-bytes") }
        val backdrop = File(dir, DownloadArtifacts.backdropFile("series-1")).apply { writeText("backdrop-bytes") }

        when (scope) {
            Scope.ITEM -> repository.deleteOfflineItem("ep-1")
            Scope.SEASON -> repository.deleteOfflineSeason("season-1")
            Scope.SERIES -> repository.deleteOfflineSeries("series-1")
        }

        if (scope == Scope.SERIES) {
            assertFalse(poster.exists(), "series-scoped poster must be pruned by the series delete")
            assertFalse(backdrop.exists(), "series-scoped backdrop must be pruned by the series delete")
        } else {
            assertTrue(poster.exists(), "series-scoped poster must survive a ${scope.name} delete")
            assertTrue(backdrop.exists(), "series-scoped backdrop must survive a ${scope.name} delete")
        }
    }

    @Test
    fun `deleteOfflineItem keeps series-scoped artwork`() = runTest {
        runSeriesArtworkScenario(Scope.ITEM)
    }

    @Test
    fun `deleteOfflineSeason keeps series-scoped artwork`() = runTest {
        runSeriesArtworkScenario(Scope.SEASON)
    }

    @Test
    fun `deleteOfflineSeries removes series-scoped artwork`() = runTest {
        runSeriesArtworkScenario(Scope.SERIES)
    }
}
