package com.raulshma.jellyplay.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Policy tests for [MediaItem.quickActions] — the single source of which
 * download/remove-download actions a card's long-press offers.
 */
class QuickActionsPolicyTest {

    private fun item(type: MediaType) = MediaItem(id = "i1", name = "T", mediaType = type)

    @Test
    fun `downloadable item without a download offers DOWNLOAD`() {
        val actions = item(MediaType.MOVIE).quickActions(
            MediaQuickActionScope.LIBRARY,
            includeDownload = true,
        )

        assertTrue(QuickAction.DOWNLOAD in actions)
        assertFalse(QuickAction.REMOVE_DOWNLOAD in actions)
    }

    @Test
    fun `downloaded item flips the download slot to REMOVE_DOWNLOAD`() {
        val actions = item(MediaType.MOVIE).quickActions(
            MediaQuickActionScope.LIBRARY,
            includeDownload = true,
            isDownloaded = true,
        )

        assertTrue(QuickAction.REMOVE_DOWNLOAD in actions)
        assertFalse(QuickAction.DOWNLOAD in actions)
    }

    @Test
    fun `series offer REMOVE_DOWNLOAD via includeRemoveDownload only`() {
        // A downloaded series resolves to remove-download whether the gate is
        // the explicit includeRemoveDownload flag (offline hosts) or the
        // isDownloaded flip of the download slot.
        val actions = item(MediaType.SERIES).quickActions(
            MediaQuickActionScope.HOME,
            includeDownload = true,
            includeRemoveDownload = true,
            isDownloaded = true,
        )

        assertTrue(QuickAction.REMOVE_DOWNLOAD in actions)
        assertFalse(QuickAction.DOWNLOAD in actions)
    }

    @Test
    fun `series without a download offers DOWNLOAD`() {
        // Hosts that can resolve a season/episode selection (home's in-place
        // download sheet; the detail screen's pre-presented one) offer the
        // download action for series too.
        val actions = item(MediaType.SERIES).quickActions(
            MediaQuickActionScope.HOME,
            includeDownload = true,
        )

        assertTrue(QuickAction.DOWNLOAD in actions)
        assertFalse(QuickAction.REMOVE_DOWNLOAD in actions)
    }

    @Test
    fun `no download actions without either gate`() {
        val actions = item(MediaType.MOVIE).quickActions(MediaQuickActionScope.HOME)

        assertFalse(QuickAction.DOWNLOAD in actions)
        assertFalse(QuickAction.REMOVE_DOWNLOAD in actions)
    }

    @Test
    fun `downloaded photo-adjacent and non-actionable types stay action-free`() {
        assertTrue(
            item(MediaType.PHOTO_FOLDER).quickActions(
                MediaQuickActionScope.LIBRARY, includeDownload = true,
            ).isEmpty()
        )
    }

    @Test
    fun `base actions are always present for actionable types`() {
        val actions = item(MediaType.MOVIE).quickActions(
            MediaQuickActionScope.LIBRARY,
            includeDownload = true,
            isDownloaded = true,
        )

        assertEquals(
            listOf(
                QuickAction.PLAY,
                QuickAction.MARK_WATCHED,
                QuickAction.REMOVE_DOWNLOAD,
                QuickAction.DETAILS,
            ),
            actions,
        )
    }
}
