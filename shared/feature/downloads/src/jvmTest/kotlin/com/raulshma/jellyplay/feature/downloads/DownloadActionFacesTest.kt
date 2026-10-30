package com.raulshma.jellyplay.feature.downloads

import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.PlayerPause
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.PlayerStop
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.feature.downloads.generated.resources.Res
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_cancel
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_delete
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_pause
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_play
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_resume
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_retry
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_cancel_all
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_pause_all
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_resume_all
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_retry_failed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Contract test for the download action face table and the screen-side
 * availability fold:
 *
 *  - TOTALITY — every [DownloadBulkAction] entry has a face row, so the
 *    `getValue` lookups behind [DownloadBulkAction.face] can never miss at
 *    runtime (a missing row is a crash, not a silent gap, and this pin turns
 *    it into a test failure at the table's own home instead).
 *  - IDENTITY — each row pins the exact icon + label resource the surfaces
 *    rendered before the table existed, so a face re-dress that lands here
 *    shows up as a deliberate one-line change in this file, not a silent
 *    sweep of seven call sites.
 *  - AGREEMENT — `DownloadActions.availableBulkActions` folds the same
 *    verdicts as `DownloadActions.supports` per action (the never-offer-a-
 *    no-op rule keeps one fold, not a screen-side re-encoding).
 */
class DownloadActionFacesTest {

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

    // ── Face table totality ───────────────────────────────────────────────

    @Test
    fun scoped_face_table_covers_the_whole_bulk_vocabulary() {
        assertEquals(
            DownloadBulkAction.entries.toSet(),
            DownloadActionFaces.bulk.keys,
            "the scoped face table is total over DownloadBulkAction",
        )
        DownloadBulkAction.entries.forEach { action ->
            val face = DownloadActionFaces.bulk.getValue(action)
            assertNotNull(face.icon, "icon for $action")
            assertNotNull(face.labelRes, "label resource for $action")
        }
    }

    @Test
    fun all_scope_face_table_covers_exactly_the_app_bar_verbs() {
        // The app bar offers Pause All / Resume All / Cancel All / Retry all
        // failed — no all-scope DELETE verb exists (the destructive bulk
        // delete is the selection bar's text button). Pin the exact key set so
        // adding an all-verb forces a face row here, and DELETE's absence
        // stays deliberate.
        assertEquals(
            setOf(
                DownloadBulkAction.PAUSE,
                DownloadBulkAction.RESUME,
                DownloadBulkAction.CANCEL,
                DownloadBulkAction.RETRY_FAILED,
            ),
            DownloadActionFaces.bulkAll.keys,
            "the all-scope face table covers exactly the app bar's verbs",
        )
    }

    // ── Face identity ─────────────────────────────────────────────────────

    @Test
    fun scoped_faces_pin_icon_and_label_identity() {
        val expected: Map<DownloadBulkAction, Pair<Any, Any>> = mapOf(
            DownloadBulkAction.PAUSE to (Tabler.Outline.PlayerPause to Res.string.downloads_action_pause),
            DownloadBulkAction.RESUME to (Tabler.Outline.PlayerPlay to Res.string.downloads_action_resume),
            DownloadBulkAction.CANCEL to (Tabler.Outline.PlayerStop to Res.string.downloads_action_cancel),
            DownloadBulkAction.RETRY_FAILED to (Tabler.Outline.Refresh to Res.string.downloads_action_retry),
            DownloadBulkAction.DELETE to (Tabler.Outline.Trash to Res.string.downloads_action_delete),
        )
        expected.forEach { (action, face) ->
            val (icon, labelRes) = face
            assertSame(icon, DownloadActionFaces.bulk.getValue(action).icon, "icon for $action")
            assertSame(labelRes, DownloadActionFaces.bulk.getValue(action).labelRes, "label for $action")
        }
    }

    @Test
    fun all_scope_faces_pin_icon_and_label_identity() {
        val expected: Map<DownloadBulkAction, Pair<Any, Any>> = mapOf(
            DownloadBulkAction.PAUSE to (Tabler.Outline.PlayerPause to Res.string.downloads_pause_all),
            DownloadBulkAction.RESUME to (Tabler.Outline.PlayerPlay to Res.string.downloads_resume_all),
            DownloadBulkAction.CANCEL to (Tabler.Outline.PlayerStop to Res.string.downloads_cancel_all),
            DownloadBulkAction.RETRY_FAILED to (Tabler.Outline.Refresh to Res.string.downloads_retry_failed),
        )
        expected.forEach { (action, face) ->
            val (icon, labelRes) = face
            assertSame(icon, DownloadActionFaces.bulkAll.getValue(action).icon, "icon for $action (all voice)")
            assertSame(labelRes, DownloadActionFaces.bulkAll.getValue(action).labelRes, "label for $action (all voice)")
        }
    }

    @Test
    fun row_cancel_and_group_play_pin_their_bespoke_faces() {
        // The item row's cancel is deliberately a different face from the
        // selection bar's / group header's (Trash "remove this download" vs
        // PlayerStop "halt this transfer") — pin it so a merge can't land
        // silently.
        assertSame(Tabler.Outline.Trash, DownloadActionFaces.rowCancel.icon)
        assertSame(Res.string.downloads_action_cancel, DownloadActionFaces.rowCancel.labelRes)
        // The group header's Play target is a row-only action outside the
        // bulk vocabulary — PlayerPlay with the play label, NOT the resume
        // face it shares an icon with.
        assertSame(Tabler.Outline.PlayerPlay, DownloadActionFaces.groupPlay.icon)
        assertSame(Res.string.downloads_action_play, DownloadActionFaces.groupPlay.labelRes)
        assertFalse(
            DownloadActionFaces.groupPlay == DownloadActionFaces.bulk.getValue(DownloadBulkAction.RESUME),
            "group play is its own face, not the resume face",
        )
    }

    // ── availableBulkActions / supports agreement ─────────────────────────

    /**
     * The screen derives one set per scope and reads membership; this pins
     * that the fold agrees with [DownloadActions.supports] per action over
     * representative fixtures — every scope kind, selections that outlive
     * their rows, and statuses that split the vocabulary.
     */
    @Test
    fun availableBulkActions_agrees_with_supports_across_scopes() {
        val mixed = listOf(
            item("dl", DownloadStatus.DOWNLOADING),
            item("pa", DownloadStatus.PAUSED),
            item("ok", DownloadStatus.COMPLETED),
            item("f", DownloadStatus.FAILED),
        )
        val fixtures: List<Triple<List<DownloadItem>, Set<String>, DownloadActionScope>> = listOf(
            Triple(emptyList(), emptySet(), DownloadActionScope.All),
            Triple(emptyList(), setOf("a"), DownloadActionScope.Selected),
            Triple(mixed, setOf("dl", "pa", "ok"), DownloadActionScope.Selected),
            Triple(mixed, setOf("f", "vanished"), DownloadActionScope.Selected),
            Triple(mixed, emptySet(), DownloadActionScope.All),
            Triple(mixed, emptySet(), DownloadActionScope.Item("dl")),
            Triple(mixed, emptySet(), DownloadActionScope.Item("pa")),
            Triple(mixed, emptySet(), DownloadActionScope.Item("gone")),
            Triple(listOf(item("dl", DownloadStatus.DOWNLOADING)), emptySet(), DownloadActionScope.All),
        )
        fixtures.forEach { (downloads, selectedIds, scope) ->
            val folded = DownloadActions.availableBulkActions(downloads, selectedIds, scope)
            assertEquals(
                DownloadBulkAction.entries.filter { action ->
                    DownloadActions.supports(action, downloads, selectedIds, scope)
                }.toSet(),
                folded,
                "folded set for scope $scope over ${downloads.map { it.id }}",
            )
            DownloadBulkAction.entries.forEach { action ->
                assertEquals(
                    DownloadActions.supports(action, downloads, selectedIds, scope),
                    action in folded,
                    "$action availability vs supports for scope $scope",
                )
            }
        }
    }

    @Test
    fun availableBulkActions_never_offers_a_noop() {
        // Empty world: no scope admits anything.
        DownloadActionScope.All.let { scope ->
            assertTrue(DownloadActions.availableBulkActions(emptyList(), emptySet(), scope).isEmpty())
        }
        // Selection that outlived its rows: nothing is actionable.
        val downloads = listOf(item("here", DownloadStatus.DOWNLOADING))
        assertTrue(
            DownloadActions.availableBulkActions(downloads, setOf("vanished"), DownloadActionScope.Selected).isEmpty(),
        )
        // A paused-only world admits resume + cancel + delete, and nothing
        // else — the no-op guards (e.g. no PAUSE with only paused rows).
        val pausedOnly = listOf(item("p1", DownloadStatus.PAUSED), item("p2", DownloadStatus.PAUSED))
        assertEquals(
            setOf(DownloadBulkAction.RESUME, DownloadBulkAction.CANCEL, DownloadBulkAction.DELETE),
            DownloadActions.availableBulkActions(pausedOnly, emptySet(), DownloadActionScope.All),
        )
    }
}
