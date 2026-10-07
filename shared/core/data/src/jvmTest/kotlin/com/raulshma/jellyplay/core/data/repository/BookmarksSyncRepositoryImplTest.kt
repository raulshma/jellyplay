package com.raulshma.jellyplay.core.data.repository

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.BookBookmarkEntity
import com.raulshma.jellyplay.core.model.BookProgressPolicy
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.network.api.JellyPlayActiveTranscode
import com.raulshma.jellyplay.core.network.api.JellyPlayBookmark
import com.raulshma.jellyplay.core.network.api.JellyPlayBookmarkRequest
import com.raulshma.jellyplay.core.network.api.JellyPlayCapabilities
import com.raulshma.jellyplay.core.network.api.JellyPlayDevice
import com.raulshma.jellyplay.core.network.api.JellyPlayEpisodeRatings
import com.raulshma.jellyplay.core.network.api.JellyPlayMessage
import com.raulshma.jellyplay.core.network.api.JellyPlayPluginApiClient
import com.raulshma.jellyplay.core.network.api.JellyPlayRatingsResult
import com.raulshma.jellyplay.core.network.api.JellyPlayRowResult
import com.raulshma.jellyplay.core.network.api.JellyPlayScoredItem
import com.raulshma.jellyplay.core.network.api.JellyPlaySeerrStatus
import com.raulshma.jellyplay.core.network.api.JellyPlaySeriesMarkers
import com.raulshma.jellyplay.core.network.api.JellyPlaySettingWrite
import com.raulshma.jellyplay.core.network.api.JellyPlaySettingsBatchResult
import com.raulshma.jellyplay.core.network.api.JellyPlaySettingsSnapshot
import com.raulshma.jellyplay.core.network.api.JellyPlaySseEvent
import com.raulshma.jellyplay.core.network.api.JellyPlayUserRating
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises [BookmarksSyncRepositoryImpl] against a real in-memory Room
 * database plus the fake-plugin-api harness of [ProfileSyncRepositoryTest]
 * (the jvmTest repository-suite pattern). The load-bearing invariants:
 *  - the ONE ticks<->seconds conversion round-trips every
 *    [BookProgressPolicy] encoding exactly (position is the join key);
 *  - pull ADOPTS unknown server rows and is SERVER-WINS only when the server
 *    `updatedAt` is strictly newer than the local row's `createdAt`;
 *  - push is LOCAL-TIMESTAMPS-WIN: an equal-or-newer server twin suppresses
 *    the push, a strictly older one is overwritten IN PLACE (its id rides the
 *    request, one server row per mark);
 *  - delete propagation removes the server twin matched by position;
 *  - every gate/failure path is a silent skip (local marks keep working).
 */
class BookmarksSyncRepositoryImplTest {

    // ------------------------------------------------------------------
    // fakes (the ProfileSyncRepositoryTest harness shape)
    // ------------------------------------------------------------------

    private class FakeSessionIdentity : com.raulshma.jellyplay.core.data.session.SessionIdentityProvider {
        override val transitions: kotlinx.coroutines.flow.SharedFlow<com.raulshma.jellyplay.core.data.session.HomeSessionTransition> =
            kotlinx.coroutines.flow.MutableSharedFlow()
        override suspend fun currentIdentity(): com.raulshma.jellyplay.core.data.session.SessionIdentity? = null
        override fun currentIdentitySnapshot(): com.raulshma.jellyplay.core.data.session.SessionIdentity? = null
        override suspend fun cacheIdentity(): com.raulshma.jellyplay.core.model.CacheIdentity =
            com.raulshma.jellyplay.core.model.CacheIdentity.UNKNOWN
        override fun cacheIdentitySnapshot(): com.raulshma.jellyplay.core.model.CacheIdentity =
            com.raulshma.jellyplay.core.model.CacheIdentity.UNKNOWN
    }

    /**
     * Api fake: capabilities/feature keys configurable; server bookmark state
     * lives in [server] (tests seed and mutate it directly to control
     * createdAt/updatedAt stamps).
     */
    private class FakePluginApi(
        val server: MutableList<JellyPlayBookmark> = mutableListOf(),
        var features: List<String> = listOf("bookmarks"),
        var capabilitiesFail: Boolean = false,
        var bookmarksFail: Boolean = false,
    ) : JellyPlayPluginApiClient {
        val upserts = mutableListOf<Pair<String, JellyPlayBookmarkRequest>>()
        val deletes = mutableListOf<Pair<String, String>>()
        var getBookmarksCalls = 0
        private var idSeq = 0

        override suspend fun getCapabilities(): Result<JellyPlayCapabilities> =
            if (capabilitiesFail) {
                Result.failure(IllegalStateException("404"))
            } else {
                Result.success(JellyPlayCapabilities(1, "1.0.0", features, 0, listOf("")))
            }

        override suspend fun getBookmarks(itemId: String): Result<List<JellyPlayBookmark>> {
            getBookmarksCalls += 1
            if (bookmarksFail) return Result.failure(IllegalStateException("network"))
            return Result.success(server.toList())
        }

        override suspend fun upsertBookmark(itemId: String, request: JellyPlayBookmarkRequest): Result<JellyPlayBookmark> {
            upserts += itemId to request
            val existing = request.id?.let { id -> server.firstOrNull { it.id == id } }
            if (existing != null) {
                val updated = existing.copy(
                    position = request.position,
                    chapterIndex = request.chapterIndex,
                    label = request.label,
                    notes = request.notes,
                )
                server[server.indexOf(existing)] = updated
                return Result.success(updated)
            }
            val created = JellyPlayBookmark(
                id = "srv-${idSeq++}",
                itemId = itemId,
                position = request.position,
                chapterIndex = request.chapterIndex,
                label = request.label,
                notes = request.notes,
                createdAt = 0,
                updatedAt = 0,
            )
            server += created
            return Result.success(created)
        }

        override suspend fun deleteBookmark(itemId: String, bookmarkId: String): Result<Unit> {
            deletes += itemId to bookmarkId
            server.removeAll { it.id == bookmarkId }
            return Result.success(Unit)
        }

        // ---- unused families ----
        override suspend fun getCustomRowCatalog(): Result<com.raulshma.jellyplay.core.network.api.JellyPlayRowCatalog?> = Result.failure(IllegalStateException("unused"))
        override suspend fun getSettings(profile: String?): Result<JellyPlaySettingsSnapshot> = Result.failure(IllegalStateException("unused"))
        override suspend fun getChangedSettings(
            since: Long,
            profile: String?,
            limit: Int?,
            cursor: Long?,
        ): Result<JellyPlaySettingsSnapshot> = Result.failure(IllegalStateException("unused"))
        override suspend fun applySettings(profile: String?, deviceId: String?, writes: List<JellyPlaySettingWrite>): Result<JellyPlaySettingsBatchResult> = Result.failure(IllegalStateException("unused"))
        override suspend fun resetNamespace(ns: String, profile: String?): Result<Unit> = Result.failure(IllegalStateException("unused"))
        override suspend fun resolveProfile(profile: String?): Result<JellyPlaySettingsSnapshot> = Result.failure(IllegalStateException("unused"))
        override suspend fun getSyncStatus(): Result<com.raulshma.jellyplay.core.network.api.JellyPlaySyncStatus?> = Result.failure(IllegalStateException("unused"))
        override suspend fun getSyncHistory(since: Long?, limit: Int): Result<com.raulshma.jellyplay.core.network.api.JellyPlaySyncHistory?> = Result.failure(IllegalStateException("unused"))
        override suspend fun getSyncHistoryKeys(seq: Long, limit: Int): Result<com.raulshma.jellyplay.core.network.api.JellyPlaySyncHistoryKeys?> = Result.failure(IllegalStateException("unused"))
        override suspend fun adminSyncOverview(): Result<com.raulshma.jellyplay.core.network.api.JellyPlaySyncAdminOverview?> = Result.failure(IllegalStateException("unused"))
        override suspend fun getSnapshots(): Result<List<com.raulshma.jellyplay.core.network.api.JellyPlaySnapshot>?> = Result.failure(IllegalStateException("unused"))
        override suspend fun createSnapshot(): Result<com.raulshma.jellyplay.core.network.api.JellyPlaySnapshotCreated?> = Result.failure(IllegalStateException("unused"))
        override suspend fun restoreSnapshot(id: String): Result<JellyPlaySettingsBatchResult?> = Result.failure(IllegalStateException("unused"))
        override suspend fun exportSettings(): Result<String?> = Result.failure(IllegalStateException("unused"))
        override suspend fun importSettings(bundleJson: String, deviceId: String?): Result<JellyPlaySettingsBatchResult?> = Result.failure(IllegalStateException("unused"))
        override suspend fun renameDevice(deviceId: String, name: String?, model: String?): Result<Unit> = Result.failure(IllegalStateException("unused"))
        override suspend fun revokeDevice(deviceId: String): Result<Unit> = Result.failure(IllegalStateException("unused"))
        override fun settingsStream(resumeFromEventId: Long): Flow<JellyPlaySseEvent> = emptyFlow()
        override suspend fun registerDevice(
            deviceId: String,
            name: String,
            platform: String,
            appVersion: String,
            push: com.raulshma.jellyplay.core.network.api.JellyPlayDevicePush,
            caps: List<String>,
            model: String?,
        ): Result<Unit> = Result.success(Unit)
        override suspend fun getDevices(): Result<List<JellyPlayDevice>> = Result.success(emptyList())
        override fun eventsStream(): Flow<JellyPlaySseEvent> = emptyFlow()
        override suspend fun broadcast(title: String, body: String, url: String?): Result<Unit> = Result.success(Unit)
        override suspend fun getMessages(): Result<List<JellyPlayMessage>> = Result.success(emptyList())
        override suspend fun markMessageRead(messageId: String): Result<Unit> = Result.success(Unit)
        override suspend fun seerrStatus(): Result<JellyPlaySeerrStatus> = Result.failure(IllegalStateException("unused"))
        override suspend fun seerrLogin(authType: String, username: String?, password: String?, quickConnectSecret: String?): Result<Unit> = Result.failure(IllegalStateException("unused"))
        override suspend fun seerrLogout(): Result<Unit> = Result.failure(IllegalStateException("unused"))
        override suspend fun getMdbListRatings(imdbId: String): Result<JellyPlayRatingsResult?> = Result.failure(IllegalStateException("unused"))
        override suspend fun getTmdbSeasonRatings(tmdbId: String, seasonNumber: Int): Result<Map<Int, JellyPlayEpisodeRatings>?> = Result.failure(IllegalStateException("unused"))
        override suspend fun getJellyPlaySimilarItems(itemId: String, limit: Int): Result<List<JellyPlayScoredItem>> = Result.failure(IllegalStateException("unused"))
        override suspend fun getAnimeMarkers(seriesId: String, providerSeriesId: String): Result<JellyPlaySeriesMarkers?> = Result.failure(IllegalStateException("unused"))
        override suspend fun getCustomRow(title: String): Result<JellyPlayRowResult?> = Result.failure(IllegalStateException("unused"))
        override suspend fun getSeasonalRow(keyword: String?): Result<JellyPlayRowResult?> = Result.failure(IllegalStateException("unused"))
        override suspend fun getUserRatings(filter: String?): Result<List<JellyPlayUserRating>> = Result.success(emptyList())
        override suspend fun getActiveTranscodes(): Result<List<JellyPlayActiveTranscode>> = Result.failure(IllegalStateException("unused"))
        override suspend fun getMyTranscodes(): Result<List<JellyPlayActiveTranscode>> = Result.failure(IllegalStateException("unused"))
        override suspend fun cancelTranscode(sessionId: String): Result<Unit> = Result.failure(IllegalStateException("unused"))
        override suspend fun getAnalyticsOverview(days: Int): Result<com.raulshma.jellyplay.core.network.api.JellyPlayAnalyticsOverview?> = Result.failure(IllegalStateException("unused"))
        override suspend fun getAnalyticsSessions(userId: String?, since: Long?, limit: Int): Result<com.raulshma.jellyplay.core.network.api.JellyPlayAnalyticsSessions?> = Result.failure(IllegalStateException("unused"))
        override suspend fun getMyAnalytics(days: Int): Result<com.raulshma.jellyplay.core.network.api.JellyPlayMyAnalytics?> = Result.failure(IllegalStateException("unused"))
    }

    // ------------------------------------------------------------------
    // harness
    // ------------------------------------------------------------------

    private lateinit var database: JellyPlayDatabase
    private lateinit var api: FakePluginApi
    private lateinit var repo: BookmarksSyncRepositoryImpl
    private lateinit var statusStore: JellyPlayPluginStatusStore

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        api = FakePluginApi()
        val registry = SessionCacheRegistry(FakeSessionIdentity(), CoroutineScope(Dispatchers.Default))
        statusStore = JellyPlayPluginStatusStore(api, registry)
        repo = BookmarksSyncRepositoryImpl(
            bookmarkDao = database.bookBookmarkDao(),
            apiClient = api,
            statusStore = statusStore,
        )
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    /** Drives the capability probe so the store reads AVAILABLE (with [features]). */
    private suspend fun makeAvailable(features: List<String> = listOf("bookmarks")) {
        api.features = features
        statusStore.refresh()
    }

    private suspend fun seedLocalBookmark(
        itemId: String = "book-1",
        positionTicks: Long,
        chapterLabel: String = "Chapter 1",
        createdAt: Long,
    ): BookBookmarkEntity {
        val entity = BookBookmarkEntity(
            itemId = itemId,
            positionTicks = positionTicks,
            cfi = null,
            chapterLabel = chapterLabel,
            createdAt = createdAt,
        )
        database.bookBookmarkDao().upsert(entity)
        return database.bookBookmarkDao().observeByItemId(itemId).first().last()
    }

    private fun serverBookmark(
        id: String,
        ticks: Long,
        label: String = "Server label",
        createdAt: Long = 0,
        updatedAt: Long = 0,
    ) = JellyPlayBookmark(
        id = id,
        itemId = "book-1",
        position = ticksToPositionSeconds(ticks),
        chapterIndex = null,
        label = label,
        notes = "server-only notes",
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private suspend fun localRows(itemId: String = "book-1"): List<BookBookmarkEntity> =
        database.bookBookmarkDao().observeByItemId(itemId).first()

    // ------------------------------------------------------------------
    // the ONE conversion
    // ------------------------------------------------------------------

    @Test
    fun `position mapping round-trips every BookProgressPolicy encoding exactly`() {
        val ticks = listOf(
            0L,
            BookProgressPolicy.pageToTicks(0),
            BookProgressPolicy.pageToTicks(1),
            BookProgressPolicy.pageToTicks(37),
            BookProgressPolicy.pageToTicks(9_999),
            BookProgressPolicy.percentToTicks(0.0),
            BookProgressPolicy.percentToTicks(0.42),
            BookProgressPolicy.percentToTicks(0.421234),
            BookProgressPolicy.percentToTicks(1.0),
        )
        for (value in ticks) {
            assertEquals(value, positionSecondsToTicks(ticksToPositionSeconds(value)), "round-trip failed for $value")
        }
        // Seconds really are seconds of the Jellyfin tick space (10M ticks).
        assertEquals(0.003, ticksToPositionSeconds(30_000L))
        assertEquals(1.0, ticksToPositionSeconds(BookProgressPolicy.TICKS_MAX_PERCENT))
    }

    // ------------------------------------------------------------------
    // pull
    // ------------------------------------------------------------------

    @Test
    fun `pull adopts unknown server rows as local marks with converted positions`() = runTest {
        makeAvailable()
        api.server += serverBookmark("srv-0", ticks = 30_000, label = "Chapter 2", createdAt = 111, updatedAt = 111)
        api.server += serverBookmark("srv-1", ticks = BookProgressPolicy.percentToTicks(0.42), label = "Chapter 7", createdAt = 222)

        repo.pullBookmarks("book-1")

        val rows = localRows()
        assertEquals(listOf(30_000L, BookProgressPolicy.percentToTicks(0.42)), rows.map { it.positionTicks })
        assertEquals(listOf("Chapter 2", "Chapter 7"), rows.map { it.chapterLabel })
        assertEquals(listOf(111L, 222L), rows.map { it.createdAt })
        // The wire carries no CFI: pulled rows degrade to position-only marks.
        assertTrue(rows.all { it.cfi == null })
    }

    @Test
    fun `pull is server-wins only when the server twin is strictly newer`() = runTest {
        makeAvailable()
        val local = seedLocalBookmark(positionTicks = 30_000, chapterLabel = "Mine", createdAt = 500)
        api.server += serverBookmark("srv-0", ticks = 30_000, label = "Theirs", updatedAt = 400)

        repo.pullBookmarks("book-1")

        // Server older than local createdAt → local survives untouched.
        assertEquals(listOf(local.id), localRows().map { it.id })
        assertEquals("Mine", localRows().single().chapterLabel)

        // Server strictly newer → replaced (local row deleted, server row
        // adopted with the wire stamps).
        api.server[0] = api.server[0].copy(label = "Theirs 2", createdAt = 600, updatedAt = 600)
        repo.pullBookmarks("book-1")

        val replaced = localRows().single()
        assertEquals("Theirs 2", replaced.chapterLabel)
        assertEquals(30_000L, replaced.positionTicks)
        assertEquals(600L, replaced.createdAt)
    }

    @Test
    fun `pull keeps an equal-timestamp local row`() = runTest {
        makeAvailable()
        val local = seedLocalBookmark(positionTicks = 30_000, chapterLabel = "Mine", createdAt = 500)
        api.server += serverBookmark("srv-0", ticks = 30_000, label = "Theirs", updatedAt = 500)

        repo.pullBookmarks("book-1")

        // Equal stamps keep the LOCAL row (no oscillation — the plugin's own
        // equal-timestamp rule mirrored client-side).
        assertEquals(listOf(local.id), localRows().map { it.id })
        assertEquals("Mine", localRows().single().chapterLabel)
    }

    // ------------------------------------------------------------------
    // push
    // ------------------------------------------------------------------

    @Test
    fun `push creates a server row at the converted position`() = runTest {
        makeAvailable()
        seedLocalBookmark(positionTicks = 30_000, chapterLabel = "Chapter 2", createdAt = 1_000)

        repo.pushBookmark("book-1", positionTicks = 30_000, chapterLabel = "Chapter 2")

        val created = api.server.single()
        assertEquals(ticksToPositionSeconds(30_000L), created.position)
        assertEquals("Chapter 2", created.label)
        // Local bookmarks carry no notes and no chapter index (schema truth).
        assertEquals("", created.notes)
        assertNull(created.chapterIndex)
    }

    @Test
    fun `push skips an equal-or-newer server twin and overwrites a strictly older one in place`() = runTest {
        makeAvailable()
        seedLocalBookmark(positionTicks = 30_000, chapterLabel = "Mine", createdAt = 1_000)
        api.server += serverBookmark("srv-0", ticks = 30_000, label = "Theirs", updatedAt = 1_500)

        repo.pushBookmark("book-1", positionTicks = 30_000, chapterLabel = "Mine")

        // Server twin is newer → no write at all.
        assertTrue(api.upserts.isEmpty())
        assertEquals("Theirs", api.server.single().label)

        // Local strictly newer → overwrite IN PLACE (twin's id rides the request).
        api.server[0] = api.server[0].copy(updatedAt = 500)
        repo.pushBookmark("book-1", positionTicks = 30_000, chapterLabel = "Mine")

        val request = api.upserts.single().second
        assertEquals("srv-0", request.id)
        val rows = api.server
        assertEquals(1, rows.size)
        assertEquals("Mine", rows.single().label)
    }

    @Test
    fun `push of a row that already vanished locally is a no-op`() = runTest {
        makeAvailable()

        repo.pushBookmark("book-1", positionTicks = 30_000, chapterLabel = "Ghost")

        assertTrue(api.upserts.isEmpty())
        assertEquals(0, api.getBookmarksCalls)
    }

    // ------------------------------------------------------------------
    // delete propagation
    // ------------------------------------------------------------------

    @Test
    fun `pushBookmarkDeleted removes the server twin matched by position`() = runTest {
        makeAvailable()
        api.server += serverBookmark("srv-0", ticks = 30_000)
        api.server += serverBookmark("srv-1", ticks = 70_000)

        repo.pushBookmarkDeleted("book-1", positionTicks = 30_000)

        assertEquals(listOf("srv-1"), api.server.map { it.id })
        assertEquals(listOf("book-1" to "srv-0"), api.deletes)
    }

    @Test
    fun `pushBookmarkDeleted with no server twin is a no-op`() = runTest {
        makeAvailable()

        repo.pushBookmarkDeleted("book-1", positionTicks = 30_000)

        assertTrue(api.deletes.isEmpty())
    }

    // ------------------------------------------------------------------
    // gating + silent-skip
    // ------------------------------------------------------------------

    @Test
    fun `plugin unavailable - every operation is a silent no-op`() = runTest {
        api.capabilitiesFail = true
        val local = seedLocalBookmark(positionTicks = 30_000, chapterLabel = "Mine", createdAt = 1)

        repo.pullBookmarks("book-1")
        repo.pushBookmark("book-1", positionTicks = 30_000, chapterLabel = "Mine")
        repo.pushBookmarkDeleted("book-1", positionTicks = 30_000)

        assertEquals(JellyPlayPluginStatus.UNAVAILABLE, statusStore.status.value)
        assertTrue(api.upserts.isEmpty() && api.deletes.isEmpty() && api.getBookmarksCalls == 0)
        assertEquals(listOf(local.id), localRows().map { it.id })
    }

    @Test
    fun `plugin available without the bookmarks feature - no bookmark endpoint is touched`() = runTest {
        makeAvailable(features = listOf("settings-sync"))

        repo.pullBookmarks("book-1")
        repo.pushBookmark("book-1", positionTicks = 30_000, chapterLabel = "Mine")
        repo.pushBookmarkDeleted("book-1", positionTicks = 30_000)

        assertEquals(JellyPlayPluginStatus.AVAILABLE, statusStore.status.value)
        assertTrue(api.upserts.isEmpty() && api.deletes.isEmpty() && api.getBookmarksCalls == 0)
    }

    @Test
    fun `bookmark endpoint failure is a silent skip - local store untouched`() = runTest {
        makeAvailable()
        api.bookmarksFail = true
        val local = seedLocalBookmark(positionTicks = 30_000, chapterLabel = "Mine", createdAt = 1)

        repo.pullBookmarks("book-1")
        repo.pushBookmark("book-1", positionTicks = 30_000, chapterLabel = "Mine")
        repo.pushBookmarkDeleted("book-1", positionTicks = 30_000)

        assertTrue(api.upserts.isEmpty() && api.deletes.isEmpty())
        assertEquals(listOf(local.id), localRows().map { it.id })
    }
}
