package com.raulshma.jellyplay.feature.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The [DetailDialogSession] contract, pinned as plain JVM assertions (the
 * flags are Compose snapshot state — readable/writable without a
 * composition). What's under test is the choreography that used to live as
 * seven hand-synced `remember` booleans + per-call-site cascade bodies in
 * the screen, reachable by no test:
 *
 *  - open ALWAYS carries its cascade (the series sheet's prefetch pair ran
 *    from three separate call sites);
 *  - close ALWAYS carries its cascade ("close means reset" — the series
 *    sheet's reset arm was duplicated in three places, and the
 *    download-details and resync sheets each dropped their loaded state on
 *    close);
 *  - confirm clears the flag BEFORE firing the command, and still resets the
 *    sheet state afterwards;
 *  - [DetailDialogSession.closeAll] brings every sheet down with the same
 *    cascades a single dismiss carries.
 */
class DetailDialogSessionTest {

    /** Records every cascade call in order — the session's observable side. */
    private class RecordingCascades : DetailDialogSession.Cascades {
        val calls = mutableListOf<String>()
        override fun prepareSeriesDownloadSheet() {
            calls += "prepareSeriesDownloadSheet"
        }

        override fun resetSeriesDownloadSheetState() {
            calls += "resetSeriesDownloadSheetState"
        }

        override fun loadDownloadFileInventory() {
            calls += "loadDownloadFileInventory"
        }

        override fun clearDownloadFileInventory() {
            calls += "clearDownloadFileInventory"
        }

        override fun clearResyncState() {
            calls += "clearResyncState"
        }
    }

    private fun session(): Pair<DetailDialogSession, RecordingCascades> {
        val cascades = RecordingCascades()
        return DetailDialogSession(cascades) to cascades
    }

    @Test
    fun openSeriesDownloadSheet_prefetchesDownloadedIdsAndSheetEpisodes() {
        val (session, cascades) = session()

        session.openSeriesDownloadSheet()

        assertTrue(session.showSeriesDownloadSheet)
        assertEquals(listOf("prepareSeriesDownloadSheet"), cascades.calls)
    }

    @Test
    fun dismissSeriesDownloadSheet_resetsSheetState() {
        val (session, cascades) = session()
        session.openSeriesDownloadSheet()
        cascades.calls.clear()

        session.dismissSeriesDownloadSheet()

        assertFalse(session.showSeriesDownloadSheet)
        assertEquals(listOf("resetSeriesDownloadSheetState"), cascades.calls)
    }

    @Test
    fun seriesDownloadConfirmed_closesFirst_thenDownloads_thenResets() {
        val (session, cascades) = session()
        session.openSeriesDownloadSheet()
        cascades.calls.clear()
        val order = mutableListOf<String>()

        session.seriesDownloadConfirmed { order += "downloadSeries" }

        assertFalse(session.showSeriesDownloadSheet)
        // Flag cleared BEFORE the command; sheet-state reset AFTER it — the
        // exact sequence the former callback hand-maintained.
        assertEquals(listOf("downloadSeries", "resetSeriesDownloadSheetState"), order + cascades.calls)
    }

    @Test
    fun openDownloadDetailsSheet_loadsInventoryBeforeShowing_andDismissDropsIt() {
        val (session, cascades) = session()

        session.openDownloadDetailsSheet()
        assertTrue(session.showDownloadDetailsSheet)
        assertEquals(listOf("loadDownloadFileInventory"), cascades.calls)

        cascades.calls.clear()
        session.dismissDownloadDetailsSheet()
        assertFalse(session.showDownloadDetailsSheet)
        assertEquals(listOf("clearDownloadFileInventory"), cascades.calls)
    }

    @Test
    fun dismissResyncSheet_clearsResyncState() {
        val (session, cascades) = session()
        session.openResyncSheet()

        session.dismissResyncSheet()

        assertFalse(session.showResyncSheet)
        assertEquals(listOf("clearResyncState"), cascades.calls)
    }

    @Test
    fun refreshMetadataConfirmed_clearsTheFlagBeforeFiring() {
        val (session, cascades) = session()
        session.openRefreshMetadataSheet()
        val order = mutableListOf<String>()

        session.refreshMetadataConfirmed { order += "refresh" ; order += "flagWas=${session.showRefreshMetadataSheet}" }

        assertFalse(session.showRefreshMetadataSheet)
        // The command observed the flag already cleared.
        assertEquals(listOf("refresh", "flagWas=false"), order)
        assertTrue(cascades.calls.isEmpty())
    }

    @Test
    fun splitConfirmed_clearsTheFlagBeforeFiring() {
        val (session, cascades) = session()
        session.openSplitConfirm()
        val order = mutableListOf<String>()

        session.splitConfirmed { order += "flagWas=${session.showSplitConfirm}"; order += "split" }

        assertFalse(session.showSplitConfirm)
        assertEquals(listOf("flagWas=false", "split"), order)
    }

    @Test
    fun independentFlags_openAndCloseWithoutCascades() {
        val (session, cascades) = session()

        session.openRefreshMetadataSheet()
        session.openVersionPicker()
        session.openSplitConfirm()
        session.openDeleteEpisodesSheet()
        assertTrue(
            session.showRefreshMetadataSheet && session.showVersionPicker &&
                session.showSplitConfirm && session.showDeleteEpisodesSheet,
        )

        session.dismissRefreshMetadataSheet()
        session.dismissVersionPicker()
        session.dismissSplitConfirm()
        session.dismissDeleteEpisodesSheet()

        assertFalse(session.showRefreshMetadataSheet)
        assertFalse(session.showVersionPicker)
        assertFalse(session.showSplitConfirm)
        assertFalse(session.showDeleteEpisodesSheet)
        // None of these five dialogs carries a cascade.
        assertTrue(cascades.calls.isEmpty())
    }

    @Test
    fun closeAll_bringsEverySheetDown_withTheSingleDismissCascades() {
        val (session, cascades) = session()
        session.openSeriesDownloadSheet()
        session.openDownloadDetailsSheet()
        session.openResyncSheet()
        session.openVersionPicker()
        cascades.calls.clear()

        session.closeAll()

        assertFalse(session.showSeriesDownloadSheet)
        assertFalse(session.showDownloadDetailsSheet)
        assertFalse(session.showResyncSheet)
        assertFalse(session.showRefreshMetadataSheet)
        assertFalse(session.showVersionPicker)
        assertFalse(session.showSplitConfirm)
        assertFalse(session.showDeleteEpisodesSheet)
        assertEquals(
            listOf(
                "resetSeriesDownloadSheetState",
                "clearDownloadFileInventory",
                "clearResyncState",
            ),
            cascades.calls,
        )
    }
}
