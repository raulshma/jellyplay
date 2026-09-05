package com.raulshma.jellyplay.feature.downloads

import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the [DownloadActions] admission table and the selected/all/item
 * targeting fold — the table the former hand-written filter lambdas in
 * [DownloadsViewModel] encoded action-by-action, now in one place.
 */
class DownloadActionsTest {

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

    // ── Admission table (derived from the former VM filters) ──────────────

    @Test
    fun pause_admits_only_downloading() {
        val statuses = listOf(
            DownloadStatus.PENDING,
            DownloadStatus.QUEUED,
            DownloadStatus.DOWNLOADING,
            DownloadStatus.PAUSED,
            DownloadStatus.COMPLETED,
            DownloadStatus.FAILED,
            DownloadStatus.CANCELLED,
        )
        statuses.forEach { status ->
            assertEquals(
                status == DownloadStatus.DOWNLOADING,
                DownloadActions.supports(DownloadBulkAction.PAUSE, listOf(item("x", status)), emptySet(), DownloadActionScope.All),
                "PAUSE admission for $status",
            )
        }
    }

    @Test
    fun resume_admits_only_paused() {
        val statuses = DownloadStatus.entries
        statuses.forEach { status ->
            assertEquals(
                status == DownloadStatus.PAUSED,
                DownloadActions.supports(DownloadBulkAction.RESUME, listOf(item("x", status)), emptySet(), DownloadActionScope.All),
                "RESUME admission for $status",
            )
        }
    }

    @Test
    fun cancel_admits_active_statuses_but_not_terminal_ones() {
        val cancellable = setOf(
            DownloadStatus.PENDING,
            DownloadStatus.QUEUED,
            DownloadStatus.DOWNLOADING,
            DownloadStatus.PAUSED,
        )
        DownloadStatus.entries.forEach { status ->
            assertEquals(
                status in cancellable,
                DownloadActions.supports(DownloadBulkAction.CANCEL, listOf(item("x", status)), emptySet(), DownloadActionScope.All),
                "CANCEL admission for $status",
            )
        }
    }

    @Test
    fun retryFailed_admits_only_failed() {
        DownloadStatus.entries.forEach { status ->
            assertEquals(
                status == DownloadStatus.FAILED,
                DownloadActions.supports(DownloadBulkAction.RETRY_FAILED, listOf(item("x", status)), emptySet(), DownloadActionScope.All),
                "RETRY_FAILED admission for $status",
            )
        }
    }

    @Test
    fun delete_admits_every_status() {
        DownloadStatus.entries.forEach { status ->
            assertTrue(
                DownloadActions.supports(DownloadBulkAction.DELETE, listOf(item("x", status)), emptySet(), DownloadActionScope.All),
                "DELETE admission for $status",
            )
        }
    }

    // ── Selected-scope targeting ──────────────────────────────────────────

    @Test
    fun selected_scope_intersects_selection_with_admitted_live_items() {
        val downloads = listOf(
            item("dl", DownloadStatus.DOWNLOADING),
            item("pa", DownloadStatus.PAUSED),
            item("ok", DownloadStatus.COMPLETED),
            item("f", DownloadStatus.FAILED),
        )

        assertEquals(
            listOf("dl"),
            DownloadActions.targets(DownloadBulkAction.PAUSE, downloads, setOf("dl", "pa", "ok"), DownloadActionScope.Selected).map { it.id },
        )
        assertEquals(
            listOf("pa"),
            DownloadActions.targets(DownloadBulkAction.RESUME, downloads, setOf("dl", "pa", "ok"), DownloadActionScope.Selected).map { it.id },
        )
        assertEquals(
            listOf("dl", "pa"),
            DownloadActions.targets(DownloadBulkAction.CANCEL, downloads, setOf("dl", "pa", "ok"), DownloadActionScope.Selected).map { it.id },
        )
        // DELETE has no status filter: every selected live item is a target.
        assertEquals(
            listOf("dl", "pa", "ok"),
            DownloadActions.targets(DownloadBulkAction.DELETE, downloads, setOf("dl", "pa", "ok"), DownloadActionScope.Selected).map { it.id },
        )
    }

    @Test
    fun selected_scope_drops_ids_missing_from_the_live_list() {
        val downloads = listOf(item("here", DownloadStatus.DOWNLOADING))

        assertEquals(
            listOf("here"),
            DownloadActions.targets(DownloadBulkAction.PAUSE, downloads, setOf("here", "vanished"), DownloadActionScope.Selected).map { it.id },
        )
        assertFalse(
            DownloadActions.supports(DownloadBulkAction.PAUSE, downloads, setOf("vanished"), DownloadActionScope.Selected),
        )
    }

    @Test
    fun empty_selection_or_empty_list_never_supports() {
        DownloadBulkAction.entries.forEach { action ->
            assertFalse(DownloadActions.supports(action, emptyList(), setOf("a"), DownloadActionScope.Selected))
            assertFalse(
                DownloadActions.supports(action, listOf(item("a", DownloadStatus.DOWNLOADING)), emptySet(), DownloadActionScope.Selected),
            )
        }
    }

    // ── All-scope targeting (the former global actions) ──────────────────

    @Test
    fun all_scope_targets_every_admitted_item_without_selection() {
        val downloads = listOf(
            item("dl", DownloadStatus.DOWNLOADING),
            item("f1", DownloadStatus.FAILED),
            item("f2", DownloadStatus.FAILED),
            item("ok", DownloadStatus.COMPLETED),
        )

        assertEquals(
            listOf("dl"),
            DownloadActions.targets(DownloadBulkAction.PAUSE, downloads, emptySet(), DownloadActionScope.All).map { it.id },
        )
        assertEquals(
            listOf("f1", "f2"),
            DownloadActions.targets(DownloadBulkAction.RETRY_FAILED, downloads, setOf("ignored"), DownloadActionScope.All).map { it.id },
        )
    }

    // ── Item-scope targeting (the former per-row actions) ────────────────

    @Test
    fun item_scope_targets_the_matching_id_only_when_admitted() {
        val downloads = listOf(
            item("a", DownloadStatus.PAUSED),
            item("b", DownloadStatus.FAILED),
        )

        assertEquals(
            listOf("a"),
            DownloadActions.targets(DownloadBulkAction.RESUME, downloads, emptySet(), DownloadActionScope.Item("a")).map { it.id },
        )
        // A row action on a non-admitted status yields nothing (guarded no-op).
        assertTrue(
            DownloadActions.targets(DownloadBulkAction.RESUME, downloads, emptySet(), DownloadActionScope.Item("b")).isEmpty(),
        )
        // DELETE stays admitted at item scope for every status.
        assertEquals(
            listOf("b"),
            DownloadActions.targets(DownloadBulkAction.DELETE, downloads, emptySet(), DownloadActionScope.Item("b")).map { it.id },
        )
        // Unknown id (row left the live list) yields nothing.
        assertTrue(
            DownloadActions.targets(DownloadBulkAction.DELETE, downloads, emptySet(), DownloadActionScope.Item("gone")).isEmpty(),
        )
    }
}
