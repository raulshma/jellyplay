package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class JellyfinUser(
    val id: String = "",
    val name: String = "",
    val primaryImageTag: String? = null,
    val lastLoginDate: String? = null,
    val lastActivityDate: String? = null,
    val isAdmin: Boolean = false,
    val isDisabled: Boolean = false,
    val isHidden: Boolean = false,
    val hasPassword: Boolean = false,
)

@Immutable
@Serializable
data class UserStatistics(
    val userId: String = "",
    val userName: String = "",
    val userAvatarTag: String? = null,
    val isAdmin: Boolean = false,
    val totalPlayCount: Int = 0,
    val moviePlayCount: Int = 0,
    val episodePlayCount: Int = 0,
    val songPlayCount: Int = 0,
    val totalWatchTimeSec: Long = 0,
    val lastSeen: String? = null,
    val completionRate: Float = 0f,
    val isCurrentlyActive: Boolean = false,
    val activityPoints: List<PlaybackActivityPoint> = emptyList(),
    val topGenres: List<ContentBreakdown> = emptyList(),
)

@Immutable
@Serializable
data class PlaybackActivityPoint(
    val date: String = "",
    val value: Long = 0,
)

@Immutable
@Serializable
data class ContentBreakdown(
    val label: String = "",
    val value: Long = 0,
    val colorIndex: Int = 0,
)

@Immutable
@Serializable
data class StaleMediaItem(
    val itemId: String = "",
    val name: String = "",
    val type: String = "",
    val mediaType: String? = null,
    val lastPlayedDate: String? = null,
    val daysSincePlay: Int = 0,
    val playCount: Int = 0,
    val sizeBytes: Long = 0,
    val sizeText: String = "",
    val parentId: String? = null,
    val seriesName: String? = null,
    val seasonName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val posterBlurHash: String? = null,
    val premiereDate: String? = null,
    val overview: String? = null,
    val year: Int? = null,
    val dateAdded: String? = null,
)

@Immutable
@Serializable
data class WatchedMediaItem(
    val itemId: String = "",
    val name: String = "",
    val type: String = "",
    val mediaType: String? = null,
    val playCount: Int = 0,
    val lastPlayedDate: String? = null,
    val completionPct: Float = 0f,
    val runtimeTicks: Long = 0,
    val isFavorite: Boolean = false,
    val parentId: String? = null,
    val seriesName: String? = null,
    val seasonName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val posterBlurHash: String? = null,
    val overview: String? = null,
    val year: Int? = null,
    val sizeBytes: Long = 0,
)

@Immutable
@Serializable
data class MediaCleanupConfig(
    val daysThreshold: Int = 90,
    val includeNeverPlayed: Boolean = true,
    val includeItemTypes: Set<String> = setOf("Movie", "Series", "Episode", "Audio"),
    val keepFavorites: Boolean = true,
    val libraryIds: Set<String> = emptySet(),
    val dryRun: Boolean = true,
    val minDaysSinceWatched: Int = 0,
    val includePartiallyWatched: Boolean = false,
    val useDateAdded: Boolean = false,
)

@Immutable
@Serializable
enum class CleanupActionType {
    STALE_REMOVAL,
    WATCHED_REMOVAL,
}

@Immutable
@Serializable
data class AuditLogEntry(
    val id: String = "",
    val timestamp: Long = 0L,
    val adminUserId: String = "",
    val adminUserName: String = "",
    val actionType: CleanupActionType = CleanupActionType.STALE_REMOVAL,
    val configSnapshot: String = "",
    val itemCount: Int = 0,
    val itemDetails: List<AuditItemDetail> = emptyList(),
)

@Immutable
@Serializable
data class AuditItemDetail(
    val itemId: String = "",
    val name: String = "",
    val type: String = "",
    val sizeText: String = "",
    val detail: String = "",
)

@Immutable
@Serializable
enum class PlaybackReportingStatus {
    AVAILABLE,
    UNAVAILABLE,
    UNKNOWN,
}

@Immutable
@Serializable
data class PlaybackReportingActivity(
    val userId: String = "",
    val userName: String = "",
    val totalTime: Long = 0,
    val latestDate: String = "",
    val totalPlayTime: Long = 0,
    val hasImage: Boolean = false,
)

@Immutable
@Serializable
data class PlaybackReportingDetail(
    val time: String = "",
    val itemId: String = "",
    val name: String = "",
    val type: String = "",
    val client: String = "",
    val method: String = "",
    val device: String = "",
    val duration: Long = 0,
)

@Immutable
@Serializable
data class ScanProgress(
    val phase: ScanPhase = ScanPhase.IDLE,
    val scanned: Int = 0,
    val total: Int = 0,
    val itemsFound: Int = 0,
)

@Immutable
@Serializable
enum class ScanPhase {
    IDLE,
    SCANNING,
    COMPLETED,
    FAILED,
    DELETING,
    DELETED,
}

@Immutable
@Serializable
data class ScanResult(
    val scanId: String = "",
    val items: List<MediaItemStub> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val config: MediaCleanupConfig = MediaCleanupConfig(),
)

@Immutable
@Serializable
data class MediaItemStub(
    val itemId: String = "",
    val name: String = "",
    val type: String = "",
    val sizeText: String = "",
    val detail: String = "",
    val seriesName: String? = null,
    val seasonName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val dateText: String? = null,
)

@Immutable
@Serializable
data class ViewingStreak(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val streakStartDate: String? = null,
)

@Immutable
@Serializable
data class MonthlyComparison(
    val currentMonthMinutes: Long = 0,
    val previousMonthMinutes: Long = 0,
    val percentageChange: Float = 0f,
)

@Immutable
@Serializable
data class MusicStatistics(
    val totalListeningHours: Float = 0f,
    val topArtists: List<ContentBreakdown> = emptyList(),
    val topGenres: List<ContentBreakdown> = emptyList(),
    val topTracks: List<UserTopItem> = emptyList(),
)

@Immutable
@Serializable
data class UserDetailPage(
    val user: JellyfinUser = JellyfinUser(),
    val statistics: UserStatistics = UserStatistics(),
    val topItems: List<UserTopItem> = emptyList(),
    val topItemsTotalCount: Int = 0,
    val hasMoreItems: Boolean = false,
    val activityChart: List<PlaybackActivityPoint> = emptyList(),
    val typeBreakdown: List<ContentBreakdown> = emptyList(),
    val genreBreakdown: List<ContentBreakdown> = emptyList(),
    val methodBreakdown: List<ContentBreakdown> = emptyList(),
    val deviceBreakdown: List<ContentBreakdown> = emptyList(),
    val weeklyWatchTimeSec: Long = 0,
    val monthlyWatchTimeSec: Long = 0,
    val viewingStreak: ViewingStreak = ViewingStreak(),
    val trendData: List<PlaybackActivityPoint> = emptyList(),
    val averageDailyMinutes: Int = 0,
    val monthlyComparison: MonthlyComparison = MonthlyComparison(),
    val musicStats: MusicStatistics = MusicStatistics(),
    val genrePieData: List<ContentBreakdown> = emptyList(),
)

@Immutable
@Serializable
data class UserTopItem(
    val itemId: String = "",
    val name: String = "",
    val type: String = "",
    val playCount: Int = 0,
    val lastPlayedDate: String? = null,
    val posterBlurHash: String? = null,
    val seriesName: String? = null,
    val runtimeTicks: Long = 0,
    val watchDuration: Long = 0,
)
