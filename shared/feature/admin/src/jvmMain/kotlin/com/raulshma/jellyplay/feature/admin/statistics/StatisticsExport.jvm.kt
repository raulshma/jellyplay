package com.raulshma.jellyplay.feature.admin.statistics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.raulshma.jellyplay.core.model.UserDetailPage
import com.raulshma.jellyplay.core.model.UserStatistics
import java.awt.Desktop
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop actual of the [StatisticsExport] seam: no Android share sheet, so
 * the exports degrade to files under `java.io.tmpdir` with the folder opened
 * through AWT [Desktop] when the platform supports it (PhotoExport seam
 * precedent). Failures are swallowed exactly like the Android bodies.
 */
internal class DesktopStatisticsExport : StatisticsExport {

    override fun shareUserStatsCsv(users: List<UserStatistics>) {
        try {
            val header = "User,Plays,Movies,Episodes,Songs,WatchTimeSec,CompletionRate"
            val rows = users.joinToString("\n") { u ->
                val name = "\"" + u.userName.replace("\"", "\"\"") + "\""
                listOf(
                    name,
                    u.totalPlayCount.toString(),
                    u.moviePlayCount.toString(),
                    u.episodePlayCount.toString(),
                    u.songPlayCount.toString(),
                    u.totalWatchTimeSec.toString(),
                    u.completionRate.toString(),
                ).joinToString(",")
            }
            val csv = "$header\n$rows"
            val file = File(System.getProperty("java.io.tmpdir"), "user_stats_${System.currentTimeMillis()}.csv")
            file.writeText(csv)
            openContainingFolder(file)
        } catch (_: Exception) {
        }
    }

    override suspend fun shareYearInJellyfin(detail: UserDetailPage) = withContext(Dispatchers.IO) {
        try {
            val shareText = buildString {
                append("My Year in Jellyfin\n\n")
                val totalHours = detail.statistics.totalWatchTimeSec / 3600
                if (totalHours > 0) {
                    append("${totalHours} hours watched\n")
                }
                if (detail.statistics.totalPlayCount > 0) {
                    append("${detail.statistics.totalPlayCount} titles played\n")
                }
                if (detail.topItems.isNotEmpty()) {
                    append("\nTop Watched:\n")
                    detail.topItems.take(5).forEachIndexed { i, item ->
                        append("${i + 1}. ${item.name} (${item.playCount}x)\n")
                    }
                }
                if (detail.genrePieData.isNotEmpty()) {
                    append("\nFavorite Genres: ${detail.genrePieData.take(3).joinToString(", ") { it.label }}\n")
                }
                if (detail.viewingStreak.longestStreak > 0) {
                    append("\nLongest Streak: ${detail.viewingStreak.longestStreak} days\n")
                }
                append("\n- JellyPlay")
            }
            val file = File(System.getProperty("java.io.tmpdir"), "year_in_jellyfin_${System.currentTimeMillis()}.txt")
            file.writeText(shareText)
            openContainingFolder(file)
        } catch (_: Exception) {
        }
    }

    private fun openContainingFolder(file: File) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file.parentFile ?: return)
            }
        }
    }
}

@Composable
internal actual fun rememberStatisticsExport(): StatisticsExport {
    return remember { DesktopStatisticsExport() }
}
