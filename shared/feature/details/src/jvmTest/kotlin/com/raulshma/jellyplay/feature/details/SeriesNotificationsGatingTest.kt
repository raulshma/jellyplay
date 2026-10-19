package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The notification gating matrices — the pure decision folds behind the three
 * notification surfaces (no Android / Compose):
 *
 *  - [offersSeriesNewEpisodeNotifications] — the ⋮ menu's "Notify me about
 *    new episodes" entry (SERIES-only, server-only, notification-stack
 *    platforms only — the offersPlayWithPrerolls template);
 *  - [showsSeriesNotifyBell] — the upcoming rows' per-row bell (stack +
 *    SERIES);
 *  - [shouldRequestPostNotifications] — the Android 13+ runtime-prompt gate
 *    behind the first-enable flow.
 */
class SeriesNotificationsGatingTest {

    // ── the ⋮ menu entry's gate ─────────────────────────────────────────

    @Test
    fun `menu entry shows on a remote series where notifications exist`() {
        assertTrue(
            offersSeriesNewEpisodeNotifications(
                mediaType = MediaType.SERIES,
                isOffline = false,
                platformSupported = true,
            ),
        )
    }

    @Test
    fun `menu entry is SERIES-only`() {
        listOf(MediaType.MOVIE, MediaType.EPISODE, MediaType.ALBUM, null).forEach { mediaType ->
            assertFalse(
                offersSeriesNewEpisodeNotifications(mediaType, isOffline = false, platformSupported = true),
                "$mediaType must not offer the series notify entry",
            )
        }
    }

    @Test
    fun `menu entry is server-only`() {
        assertFalse(
            offersSeriesNewEpisodeNotifications(
                mediaType = MediaType.SERIES,
                isOffline = true,
                platformSupported = true,
            ),
        )
    }

    @Test
    fun `menu entry hides on desktop - no notification stack`() {
        assertFalse(
            offersSeriesNewEpisodeNotifications(
                mediaType = MediaType.SERIES,
                isOffline = false,
                platformSupported = false,
            ),
        )
    }

    // ── the upcoming bell's gate ──────────────────────────────────────────────

    @Test
    fun `bell shows on a series with a notification stack`() {
        assertTrue(showsSeriesNotifyBell(platformSupported = true, mediaType = MediaType.SERIES))
    }

    @Test
    fun `bell hides on desktop`() {
        assertFalse(showsSeriesNotifyBell(platformSupported = false, mediaType = MediaType.SERIES))
    }

    @Test
    fun `bell hides off a series detail`() {
        listOf(MediaType.MOVIE, MediaType.EPISODE, null).forEach { mediaType ->
            assertFalse(showsSeriesNotifyBell(platformSupported = true, mediaType = mediaType))
        }
    }

    // ── the POST_NOTIFICATIONS runtime gate ─────────────────────────────

    @Test
    fun `prompt fires on android 13 plus when not granted`() {
        assertTrue(shouldRequestPostNotifications(sdkInt = 33, granted = false))
        assertTrue(shouldRequestPostNotifications(sdkInt = 34, granted = false))
    }

    @Test
    fun `no prompt below android 13 - notifications are granted by default`() {
        assertFalse(shouldRequestPostNotifications(sdkInt = 32, granted = false))
        assertFalse(shouldRequestPostNotifications(sdkInt = 26, granted = false))
    }

    @Test
    fun `no prompt when already granted`() {
        assertFalse(shouldRequestPostNotifications(sdkInt = 33, granted = true))
        assertFalse(shouldRequestPostNotifications(sdkInt = 34, granted = true))
    }

    // ── the store toggle seam's keying (episodes resolve to their series) ──

    @Test
    fun `episode keying resolves to the parent series - the next-up precedent`() {
        val episode = com.raulshma.jellyplay.core.model.MediaItem(
            id = "ep-1",
            name = "Pilot",
            mediaType = MediaType.EPISODE,
            seriesId = "series-9",
        )
        assertEquals("series-9", episode.seriesId ?: episode.id)
        val series = com.raulshma.jellyplay.core.model.MediaItem(
            id = "series-9",
            name = "Show",
            mediaType = MediaType.SERIES,
        )
        assertEquals("series-9", series.seriesId ?: series.id)
    }
}
