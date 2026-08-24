package com.raulshma.jellyplay.feature.admin.statistics

import androidx.compose.runtime.Composable
import com.raulshma.jellyplay.core.model.UserDetailPage
import com.raulshma.jellyplay.core.model.UserStatistics

/**
 * Platform seam for the statistics screens' share actions (admin conveyor,
 * settings' PlatformIntents pattern). Android keeps the pre-migration bodies
 * verbatim in the androidMain actual: the per-user statistics CSV export
 * (ACTION_SEND chooser over a FileProvider stream from `cacheDir`) and the
 * "Year in Jellyfin" plain-text share. Desktop has no share sheet: the CSV is
 * written under `java.io.tmpdir` and the folder is opened through AWT
 * `Desktop` when supported; both affordances stay available because a file
 * landing on disk degrades gracefully (PhotoExport seam precedent).
 */
internal interface StatisticsExport {

    /**
     * Builds the `User,Plays,...` CSV and hands it to the platform
     * (Android: cacheDir file + FileProvider + ACTION_SEND chooser).
     */
    fun shareUserStatsCsv(users: List<UserStatistics>)

    /**
     * Builds the "My Year in Jellyfin" summary text and shares it
     * (Android: ACTION_SEND EXTRA_TEXT chooser).
     */
    suspend fun shareYearInJellyfin(detail: UserDetailPage)
}

/** Composition-scoped [StatisticsExport] pick for the current platform. */
@Composable
internal expect fun rememberStatisticsExport(): StatisticsExport
