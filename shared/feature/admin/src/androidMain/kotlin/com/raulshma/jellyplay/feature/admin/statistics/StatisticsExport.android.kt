package com.raulshma.jellyplay.feature.admin.statistics

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.raulshma.jellyplay.core.model.UserDetailPage
import com.raulshma.jellyplay.core.model.UserStatistics
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android actual of the [StatisticsExport] seam — the pre-migration
 * UserStatisticsScreen / UserStatisticsDetailScreen share bodies, verbatim,
 * just funnelled through one object reached from the composable's
 * LocalContext.
 */
internal class AndroidStatisticsExport(
    private val context: Context,
) : StatisticsExport {

    override fun shareUserStatsCsv(users: List<UserStatistics>) {
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
        val file = File(context.cacheDir, "user_stats_${System.currentTimeMillis()}.csv")
        file.writeText(csv)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export CSV"))
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

            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    putExtra(Intent.EXTRA_SUBJECT, "My Year in Jellyfin")
                }
                context.startActivity(Intent.createChooser(intent, "Share Stats"))
            }
        } catch (_: Exception) {
        }
    }
}

@Composable
internal actual fun rememberStatisticsExport(): StatisticsExport {
    val context = LocalContext.current
    return remember(context) { AndroidStatisticsExport(context) }
}
