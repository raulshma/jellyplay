package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.mutableStateOf
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pins the PRODUCTION quick-action routing table ([quickActionEffect]) and
 * the mechanical dispatch over it ([dispatchQuickActionEffect] /
 * [executeQuickAction]) — the ~45-line block eight host screens used to
 * hand-copy (issue #147), where no test could reach the `when`.
 */
class QuickActionIntakeTest {

    private fun item(type: MediaType = MediaType.MOVIE) =
        MediaItem(id = "i1", name = "Item", mediaType = type)

    /** Records every adapter invocation as "member:itemId" (plus the played flag). */
    private class RecordingAdapter {
        val calls = mutableListOf<String>()
        var played: Boolean? = null

        val adapter = QuickActionAdapter(
            onPlay = { calls += "play:${it.id}" },
            onOpenDetail = { calls += "detail:${it.id}" },
            onMarkPlayed = { i, p ->
                calls += "played:${i.id}"
                played = p
            },
            onDownload = { calls += "download:${it.id}" },
            onRemoveDownload = { calls += "remove:${it.id}" },
            onToggleFavorite = { calls += "favorite:${it.id}" },
        )
    }

    // ── the fold: one decision table over every action kind ──

    @Test
    fun play_download_removeDownload_details_carryTheItem() {
        val movie = item()

        assertEquals(QuickActionEffect.Play(movie), quickActionEffect(movie, QuickAction.PLAY))
        assertEquals(QuickActionEffect.Download(movie), quickActionEffect(movie, QuickAction.DOWNLOAD))
        assertEquals(
            QuickActionEffect.RemoveDownload(movie),
            quickActionEffect(movie, QuickAction.REMOVE_DOWNLOAD),
        )
        assertEquals(QuickActionEffect.OpenDetail(movie), quickActionEffect(movie, QuickAction.DETAILS))
    }

    @Test
    fun markWatchedAndUnwatched_carryThePlayedFlag() {
        val movie = item()

        assertEquals(
            QuickActionEffect.MarkPlayed(movie, played = true),
            quickActionEffect(movie, QuickAction.MARK_WATCHED),
        )
        assertEquals(
            QuickActionEffect.MarkPlayed(movie, played = false),
            quickActionEffect(movie, QuickAction.MARK_UNWATCHED),
        )
    }

    @Test
    fun addToPlaylist_foldsOntoOpenDetail_theDeclaredDelta() {
        // The library grid — the only host offering the action — always routed
        // it to the detail screen (the picker lives in feature/details).
        assertEquals(
            QuickActionEffect.OpenDetail(item()),
            quickActionEffect(item(), QuickAction.ADD_TO_PLAYLIST),
        )
    }

    @Test
    fun favoriteAndUnfavorite_foldOntoTheSameToggle() {
        assertEquals(
            QuickActionEffect.ToggleFavorite(item()),
            quickActionEffect(item(), QuickAction.FAVORITE),
        )
        assertEquals(
            QuickActionEffect.ToggleFavorite(item()),
            quickActionEffect(item(), QuickAction.UNFAVORITE),
        )
    }

    @Test
    fun fold_coversEveryActionKind() {
        // Exhaustiveness guard: the fold has NO `else -> None` escape, so a
        // new QuickAction entry must be added to the table deliberately.
        QuickAction.entries.forEach { action ->
            quickActionEffect(item(), action)
        }
    }

    // ── the dispatch: effects route to the adapter; removal queues the confirm ──

    @Test
    fun dispatch_routesEachEffectToItsAdapterMember() {
        val recording = RecordingAdapter()
        val removeState = RemoveDownloadState()
        val movie = item()

        dispatchQuickActionEffect(QuickActionEffect.Play(movie), recording.adapter, removeState)
        dispatchQuickActionEffect(QuickActionEffect.OpenDetail(movie), recording.adapter, removeState)
        dispatchQuickActionEffect(QuickActionEffect.Download(movie), recording.adapter, removeState)
        dispatchQuickActionEffect(QuickActionEffect.ToggleFavorite(movie), recording.adapter, removeState)
        dispatchQuickActionEffect(QuickActionEffect.MarkPlayed(movie, played = false), recording.adapter, removeState)

        assertEquals(
            listOf("play:i1", "detail:i1", "download:i1", "favorite:i1", "played:i1"),
            recording.calls,
        )
        assertEquals(false, recording.played)
        assertNull(removeState.pending, "no effect but RemoveDownload touches the confirm state")
    }

    @Test
    fun removeDownload_queuesTheConfirm_andNeverDeletesDirectly() {
        val recording = RecordingAdapter()
        val removeState = RemoveDownloadState()
        val movie = item()

        dispatchQuickActionEffect(QuickActionEffect.RemoveDownload(movie), recording.adapter, removeState)

        assertEquals(emptyList(), recording.calls, "removal waits for the confirm dialog")
        assertEquals(movie, removeState.pending, "the item is queued for confirmation")
    }

    @Test
    fun executeQuickAction_foldsAndDispatchesInOneStep_theRememberedLambdaPath() {
        val recording = RecordingAdapter()
        val removeState = RemoveDownloadState()

        executeQuickAction(item(), QuickAction.MARK_UNWATCHED, recording.adapter, removeState)
        executeQuickAction(item(), QuickAction.REMOVE_DOWNLOAD, recording.adapter, removeState)

        assertEquals(listOf("played:i1"), recording.calls)
        assertEquals(false, recording.played)
        assertNotNull(removeState.pending)
    }

    // ── the TV focus key ──

    @Test
    fun openFocusedItem_showsTheFocusedCardsSheet_andReportsHandled() {
        val controller = MediaQuickActionController(
            resolveActions = { emptyList() },
            executeAction = { _, _ -> },
        )
        val intake = QuickActionIntake(
            controller = controller,
            removeDownloadState = RemoveDownloadState(),
            adapter = RecordingAdapter().adapter,
            tvFocusedItemState = mutableStateOf(null),
        )

        assertFalse(intake.openFocusedItem(), "no focused card → not handled (media-detail contract)")
        assertNull(controller.currentItem.value)

        val movie = item()
        intake.tvFocusedItem = movie
        assertTrue(intake.openFocusedItem())
        assertEquals(movie, controller.currentItem.value)
    }
}
