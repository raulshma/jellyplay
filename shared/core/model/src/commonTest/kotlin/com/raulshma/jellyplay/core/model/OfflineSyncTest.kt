package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the invariants of the offline-resync models:
 *
 *  - [OfflineSyncState.needsResync] is true when ANY lightweight-resync axis
 *    (metadata, images, subtitles, trickplay, segments) changed, and
 *    deliberately EXCLUDES [OfflineSyncState.mediaFileChanged] — a changed
 *    media file requires a re-download, not a resync.
 *  - [ResyncOptions] defaults to every category ([ResyncOptions.ALL]); its
 *    operators are pure value transforms (plus/minus never mutate the receiver).
 *  - [ResyncOptions.writesMetadataRow] couples METADATA **and** CHAPTERS to the
 *    offline detail-row persist (chapters are a column of that row).
 *  - [ResyncResult.succeeded] requires a NON-EMPTY step list in which every
 *    step succeeded — an item with no applicable steps is not a success.
 *  - [ResyncBatchProgress.completed] counts DONE + ERROR items (terminal
 *    phases); [ResyncBatchProgress.active] is true while any item is
 *    WORKING or PENDING.
 */
class OfflineSyncTest {

    // ── OfflineSyncState.needsResync ─────────────────────────────────────────

    @Test
    fun `clean state needs no resync`() {
        val state = OfflineSyncState(status = SyncStatus.CURRENT)
        assertFalse(state.needsResync)
    }

    @Test
    fun `every lightweight axis alone triggers a resync`() {
        val axes = listOf(
            OfflineSyncState(status = SyncStatus.UPDATE_AVAILABLE, metadataChanged = true),
            OfflineSyncState(status = SyncStatus.UPDATE_AVAILABLE, imagesChanged = true),
            OfflineSyncState(status = SyncStatus.UPDATE_AVAILABLE, subtitlesChanged = true),
            OfflineSyncState(status = SyncStatus.UPDATE_AVAILABLE, trickplayChanged = true),
            OfflineSyncState(status = SyncStatus.UPDATE_AVAILABLE, segmentsChanged = true),
        )
        assertTrue(axes.all { it.needsResync })
    }

    @Test
    fun `media file change alone does not trigger a resync`() {
        val state = OfflineSyncState(status = SyncStatus.UPDATE_AVAILABLE, mediaFileChanged = true)
        assertFalse(state.needsResync)
    }

    @Test
    fun `media file change does not mask a metadata resync`() {
        val state = OfflineSyncState(
            status = SyncStatus.UPDATE_AVAILABLE,
            metadataChanged = true,
            mediaFileChanged = true,
        )
        assertTrue(state.needsResync)
    }

    // ── ResyncOptions ────────────────────────────────────────────────────────

    @Test
    fun `ALL selects every category and NONE selects none`() {
        assertEquals(ResyncCategory.entries.toSet(), ResyncOptions.ALL.categories)
        assertEquals(emptySet<ResyncCategory>(), ResyncOptions.NONE.categories)
    }

    @Test
    fun `of selects exactly the given categories`() {
        val options = ResyncOptions.of(ResyncCategory.METADATA, ResyncCategory.POSTER)
        assertEquals(setOf(ResyncCategory.METADATA, ResyncCategory.POSTER), options.categories)
    }

    @Test
    fun `isEmpty is true only for the empty selection`() {
        assertTrue(ResyncOptions.NONE.isEmpty)
        assertFalse(ResyncOptions.ALL.isEmpty)
    }

    @Test
    fun `writesMetadataRow couples METADATA and CHAPTERS only`() {
        assertTrue(ResyncOptions.of(ResyncCategory.METADATA).writesMetadataRow)
        assertTrue(ResyncOptions.of(ResyncCategory.CHAPTERS).writesMetadataRow)
        assertTrue(ResyncOptions.ALL.writesMetadataRow)
        assertFalse(ResyncOptions.of(ResyncCategory.POSTER, ResyncCategory.SUBTITLES).writesMetadataRow)
        assertFalse(ResyncOptions.NONE.writesMetadataRow)
    }

    @Test
    fun `contains is the set membership operator`() {
        val options = ResyncOptions.of(ResyncCategory.TRICKPLAY)
        assertTrue(ResyncCategory.TRICKPLAY in options)
        assertFalse(ResyncCategory.SEGMENTS in options)
    }

    @Test
    fun `plus and minus produce flipped copies without mutating the receiver`() {
        val base = ResyncOptions.of(ResyncCategory.POSTER)

        val added = base + ResyncCategory.SUBTITLES
        assertEquals(setOf(ResyncCategory.POSTER, ResyncCategory.SUBTITLES), added.categories)
        assertEquals(setOf(ResyncCategory.POSTER), base.categories)

        val removed = base - ResyncCategory.POSTER
        assertEquals(emptySet<ResyncCategory>(), removed.categories)
        assertEquals(setOf(ResyncCategory.POSTER), base.categories)
    }

    // ── ResyncResult / batch progress ────────────────────────────────────────

    @Test
    fun `ResyncResult succeeded requires non-empty all-success steps`() {
        assertTrue(
            ResyncResult(
                itemId = "i",
                steps = listOf(
                    ResyncStepResult("i", ResyncStep.FETCH_DETAIL, success = true),
                    ResyncStepResult("i", ResyncStep.DOWNLOAD_POSTER, success = true),
                ),
                mediaFileChanged = false,
            ).succeeded,
        )
        assertFalse(
            ResyncResult(
                itemId = "i",
                steps = listOf(ResyncStepResult("i", ResyncStep.FETCH_DETAIL, success = false)),
                mediaFileChanged = false,
            ).succeeded,
        )
        assertFalse(
            ResyncResult(itemId = "i", steps = emptyList(), mediaFileChanged = false).succeeded,
        )
    }

    @Test
    fun `ResyncResult succeeded ignores mediaFileChanged`() {
        val result = ResyncResult(
            itemId = "i",
            steps = listOf(ResyncStepResult("i", ResyncStep.FETCH_DETAIL, success = true)),
            mediaFileChanged = true,
        )
        assertTrue(result.succeeded)
    }

    @Test
    fun `batch progress counts terminal phases as completed`() {
        val progress = ResyncBatchProgress(
            items = mapOf(
                "a" to ResyncItemProgress("a", ResyncPhase.DONE),
                "b" to ResyncItemProgress("b", ResyncPhase.ERROR),
                "c" to ResyncItemProgress("c", ResyncPhase.WORKING, ResyncStep.FETCH_DETAIL),
                "d" to ResyncItemProgress("d", ResyncPhase.PENDING),
            ),
            total = 4,
        )
        assertEquals(2, progress.completed)
        assertTrue(progress.active)
    }

    @Test
    fun `batch progress is inactive when nothing is working or pending`() {
        val progress = ResyncBatchProgress(
            items = mapOf(
                "a" to ResyncItemProgress("a", ResyncPhase.DONE),
                "b" to ResyncItemProgress("b", ResyncPhase.ERROR),
            ),
        )
        assertFalse(progress.active)
    }

    @Test
    fun `empty batch progress is complete and inactive`() {
        val progress = ResyncBatchProgress()
        assertEquals(0, progress.completed)
        assertFalse(progress.active)
        assertNull(progress.items["missing"])
    }
}
