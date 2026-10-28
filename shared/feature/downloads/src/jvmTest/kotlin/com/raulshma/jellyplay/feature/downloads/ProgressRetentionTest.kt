package com.raulshma.jellyplay.feature.downloads

import com.raulshma.jellyplay.core.data.repository.DownloadProgress
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the [retainedDownloadProgress] exit-transition retention scan: live
 * bytes land, a row the live query dropped keeps its last bytes for exactly
 * the one structural frame it still shows as DOWNLOADING, the next frame
 * forgets it, a re-appearing id's fresh bytes win, and an empty query drains
 * everything structural no longer holds as DOWNLOADING.
 */
class ProgressRetentionTest {

    private fun item(id: String, status: DownloadStatus) = DownloadItem(
        id = id,
        mediaItemId = id,
        name = "Item $id",
        mediaType = MediaType.MOVIE,
        downloadPath = "/data/$id",
        downloadUrl = "https://server/$id",
        totalSizeBytes = 0L,
        downloadedBytes = 0L,
        status = status,
        priority = 0,
    )

    private fun progress(id: String, bytes: Long) = DownloadProgress(
        id = id,
        downloadedBytes = bytes,
        speedBytesPerSec = 0L,
    )

    // ── Live rows ─────────────────────────────────────────────────────────

    @Test
    fun live_row_bytes_land_in_result() {
        val structural = listOf(item("d", DownloadStatus.DOWNLOADING))
        val live = mapOf("d" to progress("d", 100))

        val result = retainedDownloadProgress(emptyMap(), live, structural)

        assertEquals(mapOf("d" to progress("d", 100)), result)
    }

    @Test
    fun live_entry_for_non_downloading_row_still_passes_through() {
        // putAll(live) overlays unconditionally — a QUEUED row with early
        // bytes is live vocabulary even though retention never keys on it.
        val structural = listOf(item("q", DownloadStatus.QUEUED))
        val live = mapOf("q" to progress("q", 40))

        val result = retainedDownloadProgress(emptyMap(), live, structural)

        assertEquals(mapOf("q" to progress("q", 40)), result)
    }

    // ── Exit transition ───────────────────────────────────────────────────

    @Test
    fun dropped_live_row_keeps_last_bytes_while_structural_still_downloading() {
        // The race frame: the live query already dropped "d" (completed),
        // the structural list still shows DOWNLOADING.
        val structural = listOf(item("d", DownloadStatus.DOWNLOADING))
        val retained = mapOf("d" to progress("d", 100))

        val result = retainedDownloadProgress(retained, emptyMap(), structural)

        assertEquals(mapOf("d" to progress("d", 100)), result)
    }

    @Test
    fun structural_flip_forgets_the_dropped_row_next_step() {
        // The step after: structural re-emitted with the new status — the
        // retained bytes are now stale and must go.
        val structural = listOf(item("d", DownloadStatus.COMPLETED))

        val result = retainedDownloadProgress(
            mapOf("d" to progress("d", 100)),
            emptyMap(),
            structural,
        )

        assertEquals(emptyMap(), result)
    }

    @Test
    fun row_removed_from_structural_is_forgotten_even_while_downloading_lookup() {
        // A row gone from the structural list entirely has nothing to retain
        // against — retention keys on structural DOWNLOADING membership.
        val result = retainedDownloadProgress(
            mapOf("d" to progress("d", 100)),
            emptyMap(),
            emptyList(),
        )

        assertEquals(emptyMap(), result)
    }

    @Test
    fun reappearing_id_refreshes_bytes() {
        val structural = listOf(item("d", DownloadStatus.DOWNLOADING))

        val retained = retainedDownloadProgress(
            mapOf("d" to progress("d", 100)),
            emptyMap(),
            structural,
        )
        val result = retainedDownloadProgress(
            retained,
            mapOf("d" to progress("d", 250)),
            structural,
        )

        assertEquals(mapOf("d" to progress("d", 250)), result)
    }

    // ── Empty query ───────────────────────────────────────────────────────

    @Test
    fun empty_query_drains_retained_entries() {
        // Nothing live and nothing structural DOWNLOADING — every retained
        // entry drains, whichever way it left (status flip or list removal).
        val structural = listOf(
            item("ok", DownloadStatus.COMPLETED),
            item("gone", DownloadStatus.FAILED),
        )

        val result = retainedDownloadProgress(
            mapOf(
                "ok" to progress("ok", 100),
                "gone" to progress("gone", 200),
            ),
            emptyMap(),
            structural,
        )

        assertEquals(emptyMap(), result)
    }

    @Test
    fun empty_query_keeps_only_rows_structural_still_shows_downloading() {
        // The actual drain rule is keyed on structural DOWNLOADING membership,
        // not on the live query: an empty live map alone does NOT drain a row
        // still shown DOWNLOADING — that frame is the retention window.
        val structural = listOf(
            item("d", DownloadStatus.DOWNLOADING),
            item("ok", DownloadStatus.COMPLETED),
        )

        val result = retainedDownloadProgress(
            mapOf(
                "d" to progress("d", 100),
                "ok" to progress("ok", 200),
            ),
            emptyMap(),
            structural,
        )

        assertEquals(mapOf("d" to progress("d", 100)), result)
    }
}
