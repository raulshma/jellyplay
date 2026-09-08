package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the OFFLINE Continue Watching row's membership contract
 * ([buildOfflineHomeSections]) — the local mirror of the server's
 * `/Items/Resume` rules. This is the offline half of the #157 regression
 * class ("Continue watching shows already watched episode"): the offline row
 * has ALWAYS dropped played/finished rows (`!it.isPlayed &&
 * !it.isFinishedOffline`); these tests make that contract load-bearing so the
 * online row's matching filter (the #157 fix in LibraryApiClientImpl /
 * KtorWasmLibraryApiClient) cannot drift away from it unnoticed.
 *
 * The mirror rules under test, in the order the implementation applies them:
 *  1. `IsResumable` position rule — `playbackPositionTicks > 0`;
 *  2. the server's MinResumePct analog — a ~1% progress floor;
 *  3. the watched rules — `isPlayed` OR the app's 95% finished-offline
 *     threshold (a sticky mirror row at ≥95% is "watched" even before the
 *     played flip lands, per the #153 mirror semantics);
 *  4. downloaded SERIES rows are hierarchy echoes, never resume points;
 *  5. the user's hidden-CW list and the 20-item cap.
 */
class OfflineHomeSectionsContinueWatchingTest {

    private val titles = OfflineHomeSectionTitles(
        continueWatching = "Continue Watching",
        nextUp = "Next Up",
        recentlyDownloaded = "Recently Downloaded",
        movies = "Movies",
        series = "Series",
        music = "Music",
    )

    private fun continueWatching(vararg items: OfflineMediaItem): List<String>? =
        buildOfflineHomeSections(
            library = items.filter { it.mediaType == MediaType.MOVIE },
            episodes = items.filter { it.mediaType == MediaType.EPISODE },
            titles = titles,
            prefs = OfflineHomeSectionPrefs(),
        ).singleOrNull { it.type == HomeSectionType.CONTINUE_WATCHING }?.items?.map { it.id }

    private fun item(
        id: String,
        mediaType: MediaType = MediaType.MOVIE,
        positionTicks: Long? = 1_000_000L,
        playedPercentage: Double = 50.0,
        isPlayed: Boolean = false,
        lastPlayedDate: String? = "2026-01-01T10:00:00Z",
    ) = OfflineMediaItem(
        id = id,
        name = id,
        mediaType = mediaType,
        runTimeTicks = 60_000_000L,
        downloadPath = "/downloads/$id/video.mkv",
        downloadStatus = DownloadStatus.COMPLETED,
        playbackPositionTicks = positionTicks,
        playedPercentage = playedPercentage,
        isPlayed = isPlayed,
        lastPlayedDate = lastPlayedDate,
        createdAt = 1_770_000_000_000L,
    )

    // ── The row keeps genuinely in-progress items ─────────────────────

    @Test
    fun `in-progress unplayed item with a position stays in the row`() {
        val row = continueWatching(item("watching"))

        assertEquals(listOf("watching"), row)
    }

    @Test
    fun `movies and episodes both feed the row, most recently played first`() {
        val row = continueWatching(
            item("older", lastPlayedDate = "2026-01-01T10:00:00Z"),
            item("newer", mediaType = MediaType.EPISODE, lastPlayedDate = "2026-01-02T10:00:00Z"),
        )

        assertEquals(listOf("newer", "older"), row)
    }

    // ── The #157 mirror rules: watched items never occupy the row ─────

    @Test
    fun `played item with a leftover position is dropped`() {
        // The server-side poison shape: Played=true AND position>0. The
        // offline row must not render it even though the position survives.
        val row = continueWatching(item("poisoned", isPlayed = true))

        assertTrue(row.isNullOrEmpty(), "a watched item must never appear in Continue Watching")
    }

    @Test
    fun `finished-offline row is dropped even before the played flip lands`() {
        // Sticky mirror semantics (#153): the progress mirror writes
        // isPlayed=true at ≥95%, but a lost flip can leave playedPercentage
        // high while isPlayed is still false — isFinishedOffline catches it.
        val row = continueWatching(item("finished", playedPercentage = 97.0))

        assertTrue(row.isNullOrEmpty())
    }

    @Test
    fun `the finished-offline threshold is inclusive at exactly 95 percent`() {
        // Boundary pins: 95.0 (OFFLINE_WATCHED_THRESHOLD_PERCENT) IS watched;
        // 94.9 is still an in-progress resume point. A drifted comparison
        // operator here silently re-admits watched rows (#157 regression).
        val dropped = continueWatching(item("at-boundary", playedPercentage = 95.0))
        val kept = continueWatching(item("just-below", playedPercentage = 94.9))

        assertTrue(dropped.isNullOrEmpty(), "exactly 95% counts as watched")
        assertEquals(listOf("just-below"), kept)
    }

    @Test
    fun `the MinResumePct floor is inclusive at exactly 1 percent`() {
        val dropped = continueWatching(item("below-floor", playedPercentage = 0.9))
        val kept = continueWatching(item("at-floor", playedPercentage = 1.0))

        assertTrue(dropped.isNullOrEmpty())
        assertEquals(listOf("at-floor"), kept)
    }

    @Test
    fun `rows without a parseable lastPlayedDate sort last but stay in the row`() {
        // Legacy/corrupt rows with a null stamp degrade to MIN_VALUE in the
        // recency sort — they keep their resume point instead of vanishing.
        val row = continueWatching(
            item("unstamped", lastPlayedDate = null),
            item("stamped", lastPlayedDate = "2026-01-02T10:00:00Z"),
        )

        assertEquals(listOf("stamped", "unstamped"), row)
    }

    @Test
    fun `item without a resume position is dropped even when partially watched`() {
        val row = continueWatching(item("no-position", positionTicks = 0L, playedPercentage = 40.0))

        assertTrue(row.isNullOrEmpty(), "IsResumable mirror: position must be > 0")
    }

    @Test
    fun `played rows alongside an in-progress row only drop the played one`() {
        val row = continueWatching(
            item("poisoned", isPlayed = true),
            item("watching"),
        )

        assertEquals(listOf("watching"), row)
    }

    // ── Structural rules ──────────────────────────────────────────────

    @Test
    fun `downloaded series rows are hierarchy echoes and never resume points`() {
        val row = continueWatching(
            item("series-echo", mediaType = MediaType.SERIES, playedPercentage = 50.0),
        )

        assertTrue(row.isNullOrEmpty())
    }

    @Test
    fun `user-hidden cw items are dropped`() {
        val row = buildOfflineHomeSections(
            library = listOf(item("visible"), item("hidden")),
            episodes = emptyList(),
            titles = titles,
            prefs = OfflineHomeSectionPrefs(hiddenCwItemIds = setOf("hidden")),
        ).single { it.type == HomeSectionType.CONTINUE_WATCHING }

        assertEquals(listOf("visible"), row.items.map { it.id })
    }

    @Test
    fun `row is capped at twenty items, newest played first`() {
        val items = (1..25).map {
            item(
                "ep-%02d".format(it),
                mediaType = MediaType.EPISODE,
                lastPlayedDate = "2026-01-%02dT10:00:00Z".format(it),
            )
        }

        val row = buildOfflineHomeSections(
            library = emptyList(),
            episodes = items,
            titles = titles,
            prefs = OfflineHomeSectionPrefs(),
        ).single { it.type == HomeSectionType.CONTINUE_WATCHING }

        assertEquals(20, row.items.size)
        // Newest first: the five oldest (ep-01..ep-05) are the ones cut.
        assertEquals("ep-25", row.items.first().id)
        assertEquals("ep-06", row.items.last().id)
    }

    @Test
    fun `disabled continue watching pref removes the row entirely`() {
        val sections = buildOfflineHomeSections(
            library = listOf(item("watching")),
            episodes = emptyList(),
            titles = titles,
            prefs = OfflineHomeSectionPrefs(continueWatchingEnabled = false),
        )

        assertTrue(sections.none { it.type == HomeSectionType.CONTINUE_WATCHING })
    }
}
