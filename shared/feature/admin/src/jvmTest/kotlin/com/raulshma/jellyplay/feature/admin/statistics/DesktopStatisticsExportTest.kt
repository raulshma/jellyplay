package com.raulshma.jellyplay.feature.admin.statistics

import com.raulshma.jellyplay.core.model.ContentBreakdown
import com.raulshma.jellyplay.core.model.UserDetailPage
import com.raulshma.jellyplay.core.model.UserStatistics
import com.raulshma.jellyplay.core.model.UserTopItem
import com.raulshma.jellyplay.core.model.ViewingStreak
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Pins the desktop actual of the [StatisticsExport] seam — the file-degradation
 * half (no Android share sheet): the per-user CSV lands in `java.io.tmpdir`
 * with the exact `User,Plays,...` header and RFC-style quoting (names are
 * always wrapped in double quotes, embedded quotes doubled — commas and quote
 * characters in a display name must not break the row shape), and the
 * "My Year in Jellyfin" summary writes every populated section (hours, titles,
 * top-5 items, top-3 genres, streak) while OMITTING zero sections for an empty
 * detail page. The AWT folder-open is best-effort UI sugar outside the seam's
 * contract (runCatching'd in the actual); these tests force headless so the
 * exports stay quiet when possible.
 *
 * NOT pinned here: the swallow-on-write-failure catch — a tmpdir write cannot
 * be made to fail portably; and [rememberStatisticsExport] (Composable).
 */
class DesktopStatisticsExportTest {

    private lateinit var export: DesktopStatisticsExport
    private val tmpdir = File(System.getProperty("java.io.tmpdir"))

    @BeforeTest
    fun setUp() {
        // Best-effort: keep AWT from opening real folder windows during the
        // test run. Already-initialized Toolkit ignores this — harmless.
        System.setProperty("java.awt.headless", "true")
        export = DesktopStatisticsExport()
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("java.awt.headless")
    }

    private fun newFilesSince(prefix: String, before: Long): List<File> =
        tmpdir.listFiles { f -> f.name.startsWith(prefix) && f.lastModified() >= before }
            ?.sortedBy { it.name }
            .orEmpty()

    // ----------------------------------------------------------------- CSV

    @Test
    fun `csv export writes the header and one row per user`() {
        val before = System.currentTimeMillis()
        val users = listOf(
            UserStatistics(
                userId = "u1",
                userName = "Alice",
                totalPlayCount = 12,
                moviePlayCount = 5,
                episodePlayCount = 6,
                songPlayCount = 1,
                totalWatchTimeSec = 7_200L,
                completionRate = 0.75f,
            ),
            UserStatistics(userId = "u2", userName = "Bob", totalPlayCount = 3),
        )

        export.shareUserStatsCsv(users)

        val files = newFilesSince("user_stats_", before)
        assertTrue(files.isNotEmpty(), "the export must land a file in the tmpdir")
        val lines = files.last().readText().lineSequence().filter { it.isNotEmpty() }.toList()
        assertEquals("User,Plays,Movies,Episodes,Songs,WatchTimeSec,CompletionRate", lines.first())
        assertEquals(
            "\"Alice\",12,5,6,1,7200,0.75",
            lines.elementAtOrNull(1),
            "values must serialize positionally, unquoted except the name",
        )
        assertEquals("\"Bob\",3,0,0,0,0,0.0", lines.elementAtOrNull(2), "defaults fill the unset columns")
    }

    @Test
    fun `csv names with commas and quotes stay inside a quoted field`() {
        val before = System.currentTimeMillis()
        val trickyName = "He said \"hi\", Jr"

        export.shareUserStatsCsv(listOf(UserStatistics(userName = trickyName, totalPlayCount = 1)))

        val files = newFilesSince("user_stats_", before)
        assertTrue(files.isNotEmpty())
        val row = files.last().readText().lineSequence().filter { it.isNotEmpty() }.elementAtOrNull(1).orEmpty()
        assertEquals("\"He said \"\"hi\"\", Jr\",1,0,0,0,0,0.0", row)
    }

    @Test
    fun `an empty user list still writes a parseable header-only csv`() {
        val before = System.currentTimeMillis()

        export.shareUserStatsCsv(emptyList())

        val files = newFilesSince("user_stats_", before)
        assertTrue(files.isNotEmpty())
        val text = files.last().readText()
        assertEquals("User,Plays,Movies,Episodes,Songs,WatchTimeSec,CompletionRate\n", text)
    }

    // -------------------------------------------------- year in jellyfin

    @Test
    fun `year-in-jellyfin writes every populated section`() = runTest {
        val before = System.currentTimeMillis()
        val detail = UserDetailPage(
            statistics = UserStatistics(totalPlayCount = 42, totalWatchTimeSec = 10_800L),
            topItems = (1..6).map { i ->
                UserTopItem(itemId = "i$i", name = "Item $i", playCount = 10 - i)
            },
            genrePieData = listOf(
                ContentBreakdown(label = "Drama"),
                ContentBreakdown(label = "Comedy"),
                ContentBreakdown(label = "Action"),
                ContentBreakdown(label = "Horror"),
            ),
            viewingStreak = ViewingStreak(currentStreak = 2, longestStreak = 7),
        )

        export.shareYearInJellyfin(detail)

        val files = newFilesSince("year_in_jellyfin_", before)
        assertTrue(files.isNotEmpty(), "the summary must land a file in the tmpdir")
        val text = files.last().readText()
        assertTrue(text.startsWith("My Year in Jellyfin"))
        assertTrue("3 hours watched" in text, "10800s / 3600 truncates to whole hours")
        assertTrue("42 titles played" in text)
        assertTrue("Top Watched:" in text)
        assertTrue("1. Item 1 (9x)" in text)
        assertTrue("5. Item 5 (5x)" in text, "the summary caps the top list at five")
        assertFalse("Item 6" in text, "entries beyond five must be dropped")
        assertTrue("Favorite Genres: Drama, Comedy, Action" in text, "genres cap at three")
        assertFalse("Horror" in text)
        assertTrue("Longest Streak: 7 days" in text)
        assertTrue(text.trimEnd().endsWith("- JellyPlay"))
    }

    @Test
    fun `year-in-jellyfin with an empty detail omits the zero sections`() = runTest {
        val before = System.currentTimeMillis()

        export.shareYearInJellyfin(UserDetailPage())

        val files = newFilesSince("year_in_jellyfin_", before)
        assertTrue(files.isNotEmpty())
        val text = files.last().readText()
        assertFalse("hours watched" in text, "0 hours must not render")
        assertFalse("titles played" in text, "0 plays must not render")
        assertFalse("Top Watched" in text)
        assertFalse("Favorite Genres" in text)
        assertFalse("Longest Streak" in text)
        assertTrue(text.trimEnd().endsWith("- JellyPlay"), "the signature always renders")
    }
}
