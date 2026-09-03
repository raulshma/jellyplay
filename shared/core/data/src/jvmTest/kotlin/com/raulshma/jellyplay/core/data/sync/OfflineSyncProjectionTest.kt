package com.raulshma.jellyplay.core.data.sync

import com.raulshma.jellyplay.core.database.dao.OfflineSyncUpdateRow
import com.raulshma.jellyplay.core.database.entity.SyncBaselineEntity
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the lossless freshness projection [toOfflineSyncState] — the single
 * source of truth both the write path (OfflineSyncManager) and the read path
 * (OfflineRepositoryImpl's DB-driven badge) call:
 *
 *  - status precedence: checking > error > media-file change > any per-axis
 *    change > legacy coarse guess > CURRENT (lastSyncedAt set) > UNKNOWN
 *    (never checked);
 *  - the **legacy-degrade branch**: a migrated row with `syncUpdateAvailable`
 *    set but every per-axis flag 0 must still surface an UPDATE_AVAILABLE
 *    badge, guessed as "metadata + images changed" — and per-axis detail wins
 *    as soon as any axis flag is set (the guess must not stack on top);
 *  - per-axis changed flags project 1:1 (and only the legacy row fakes
 *    metadata/images);
 *  - plus the [toOfflineSyncUpdate] downloads-sheet row mapping, including
 *    case-insensitive media-type resolution with a null for unknown types.
 */
class OfflineSyncProjectionTest {

    private fun entity(
        lastSyncedAt: Long? = null,
        syncUpdateAvailable: Int = 0,
        syncMediaChanged: Int = 0,
        syncChecking: Int = 0,
        syncError: Int = 0,
        syncMetadataChanged: Int = 0,
        syncImagesChanged: Int = 0,
        syncSubtitlesChanged: Int = 0,
        syncTrickplayChanged: Int = 0,
        syncSegmentsChanged: Int = 0,
    ) = SyncBaselineEntity(
        id = "item-1",
        lastSyncedAt = lastSyncedAt,
        syncUpdateAvailable = syncUpdateAvailable,
        syncMediaChanged = syncMediaChanged,
        syncChecking = syncChecking,
        syncError = syncError,
        syncMetadataChanged = syncMetadataChanged,
        syncImagesChanged = syncImagesChanged,
        syncSubtitlesChanged = syncSubtitlesChanged,
        syncTrickplayChanged = syncTrickplayChanged,
        syncSegmentsChanged = syncSegmentsChanged,
    )

    // ── baseline statuses ───────────────────────────────────────────────

    @Test
    fun `never-synced row projects to UNKNOWN`() {
        val state = entity().toOfflineSyncState()

        assertEquals(SyncStatus.UNKNOWN, state.status)
        assertFalse(state.metadataChanged)
        assertFalse(state.imagesChanged)
        assertFalse(state.mediaFileChanged)
        assertNull(state.lastCheckedAt)
    }

    @Test
    fun `synced row with no flags projects to CURRENT`() {
        val state = entity(lastSyncedAt = 1_000L).toOfflineSyncState()

        assertEquals(SyncStatus.CURRENT, state.status)
        assertEquals(1_000L, state.lastCheckedAt)
        assertFalse(state.needsResync)
    }

    // ── precedence ──────────────────────────────────────────────────────

    @Test
    fun `checking wins over every other flag`() {
        val state = entity(
            lastSyncedAt = 1_000L,
            syncChecking = 1,
            syncError = 1,
            syncMediaChanged = 1,
            syncMetadataChanged = 1,
        ).toOfflineSyncState()

        assertEquals(SyncStatus.CHECKING, state.status)
    }

    @Test
    fun `error beats update flags`() {
        val state = entity(
            lastSyncedAt = 1_000L,
            syncError = 1,
            syncMediaChanged = 1,
            syncSubtitlesChanged = 1,
        ).toOfflineSyncState()

        assertEquals(SyncStatus.ERROR, state.status)
    }

    @Test
    fun `media-file change beats per-axis changes`() {
        val state = entity(
            lastSyncedAt = 1_000L,
            syncMediaChanged = 1,
            syncTrickplayChanged = 1,
        ).toOfflineSyncState()

        assertEquals(SyncStatus.UPDATE_AVAILABLE, state.status)
        assertTrue(state.mediaFileChanged)
        assertTrue(state.trickplayChanged)
    }

    @Test
    fun `any per-axis change surfaces UPDATE_AVAILABLE`() {
        listOf(
            "metadata" to { e: SyncBaselineEntity -> e.copy(syncMetadataChanged = 1) },
            "images" to { e: SyncBaselineEntity -> e.copy(syncImagesChanged = 1) },
            "subtitles" to { e: SyncBaselineEntity -> e.copy(syncSubtitlesChanged = 1) },
            "trickplay" to { e: SyncBaselineEntity -> e.copy(syncTrickplayChanged = 1) },
            "segments" to { e: SyncBaselineEntity -> e.copy(syncSegmentsChanged = 1) },
        ).forEach { (axis, flag) ->
            val state = flag(entity(lastSyncedAt = 1_000L)).toOfflineSyncState()
            assertEquals(SyncStatus.UPDATE_AVAILABLE, state.status, "axis=$axis should surface an update")
        }
    }

    @Test
    fun `lastSyncedAt alone never upgrades an otherwise-unchecked row past CURRENT`() {
        // A row with lastSyncedAt and no flags is CURRENT even though it is not UNKNOWN.
        val state = entity(lastSyncedAt = 5L).toOfflineSyncState()

        assertEquals(SyncStatus.CURRENT, state.status)
    }

    // ── the legacy-degrade branch ───────────────────────────────────────

    @Test
    fun `legacy coarse-only row degrades to UPDATE_AVAILABLE with metadata plus images`() {
        // Migrated row: syncUpdateAvailable=1, all per-axis flags 0. It must
        // still badge (the old lossy behaviour) guessed as metadata + images.
        val state = entity(lastSyncedAt = 1_000L, syncUpdateAvailable = 1).toOfflineSyncState()

        assertEquals(SyncStatus.UPDATE_AVAILABLE, state.status)
        assertTrue(state.metadataChanged, "legacy guess must include metadata")
        assertTrue(state.imagesChanged, "legacy guess must include images")
        assertFalse(state.subtitlesChanged)
        assertFalse(state.trickplayChanged)
        assertFalse(state.segmentsChanged)
        assertFalse(state.mediaFileChanged)
        assertTrue(state.needsResync)
    }

    @Test
    fun `coarse flag with real per-axis detail projects losslessly - no guess stacked on top`() {
        // The guess must only fire when NO per-axis flag is set: with the
        // coarse OR plus subtitles detail, metadata/images stay false.
        val state = entity(
            lastSyncedAt = 1_000L,
            syncUpdateAvailable = 1,
            syncSubtitlesChanged = 1,
        ).toOfflineSyncState()

        assertEquals(SyncStatus.UPDATE_AVAILABLE, state.status)
        assertFalse(state.metadataChanged)
        assertFalse(state.imagesChanged)
        assertTrue(state.subtitlesChanged)
    }

    @Test
    fun `coarse flag alone without lastSyncedAt still degrades to UPDATE_AVAILABLE`() {
        val state = entity(syncUpdateAvailable = 1).toOfflineSyncState()

        assertEquals(SyncStatus.UPDATE_AVAILABLE, state.status)
        assertTrue(state.metadataChanged)
        assertTrue(state.imagesChanged)
    }

    @Test
    fun `coarse flag of 0 never triggers the legacy guess`() {
        val state = entity(lastSyncedAt = 1_000L, syncUpdateAvailable = 0).toOfflineSyncState()

        assertEquals(SyncStatus.CURRENT, state.status)
        assertFalse(state.metadataChanged)
        assertFalse(state.imagesChanged)
    }

    // ── per-axis flag passthrough ───────────────────────────────────────

    @Test
    fun `all five axes project 1-to-1 with no coarse flag`() {
        val state = entity(
            lastSyncedAt = 1_000L,
            syncUpdateAvailable = 1,
            syncMetadataChanged = 1,
            syncImagesChanged = 1,
            syncSubtitlesChanged = 1,
            syncTrickplayChanged = 1,
            syncSegmentsChanged = 1,
        ).toOfflineSyncState()

        assertEquals(SyncStatus.UPDATE_AVAILABLE, state.status)
        assertTrue(state.metadataChanged)
        assertTrue(state.imagesChanged)
        assertTrue(state.subtitlesChanged)
        assertTrue(state.trickplayChanged)
        assertTrue(state.segmentsChanged)
        assertTrue(state.needsResync)
    }

    @Test
    fun `checking flag still reports its change details beneath the CHECKING status`() {
        val state = entity(
            lastSyncedAt = 1_000L,
            syncChecking = 1,
            syncMetadataChanged = 1,
            syncSegmentsChanged = 1,
        ).toOfflineSyncState()

        assertEquals(SyncStatus.CHECKING, state.status)
        assertTrue(state.metadataChanged)
        assertTrue(state.segmentsChanged)
    }

    // ── toOfflineSyncUpdate (downloads-sheet row) ───────────────────────

    @Test
    fun `update row maps fields and resolves the media type case-insensitively`() {
        val update = OfflineSyncUpdateRow(
            id = "e-77",
            name = "Pilot",
            mediaType = "episode",
            seriesName = "Dark",
            seasonNumber = 1,
            episodeNumber = 2,
            mediaFileChanged = 1,
        ).toOfflineSyncUpdate()

        assertEquals("e-77", update.id)
        assertEquals("Pilot", update.name)
        assertEquals(MediaType.EPISODE, update.mediaType)
        assertEquals("Dark", update.seriesName)
        assertEquals(1, update.seasonNumber)
        assertEquals(2, update.episodeNumber)
        assertTrue(update.mediaFileChanged)
    }

    @Test
    fun `update row with unknown or null media type resolves to null`() {
        assertNull(
            OfflineSyncUpdateRow("a", "n", "bogus", null, null, null, 0).toOfflineSyncUpdate().mediaType,
        )
        assertNull(
            OfflineSyncUpdateRow("a", "n", null, null, null, null, 0).toOfflineSyncUpdate().mediaType,
        )
    }

    @Test
    fun `update row with mediaFileChanged 0 maps to false`() {
        val update = OfflineSyncUpdateRow("a", "n", "MOVIE", null, null, null, 0).toOfflineSyncUpdate()

        assertEquals(MediaType.MOVIE, update.mediaType)
        assertFalse(update.mediaFileChanged)
    }
}
