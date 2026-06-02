package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class NewsletterData(
    val serverName: String = "",
    val recentlyAdded: List<MediaItem> = emptyList(),
    val activityDigest: List<ActivityLogEntry> = emptyList(),
    val libraryStats: ItemCounts? = null,
    val continueWatching: List<MediaItem> = emptyList(),
    val nextUp: List<MediaItem> = emptyList(),
    val curatedPicks: List<MediaItem> = emptyList(),
)

@Immutable
@Serializable
enum class NewsletterSectionType {
    RECENTLY_ADDED,
    ACTIVITY_DIGEST,
    LIBRARY_STATS,
    CONTINUE_WATCHING,
    NEXT_UP,
    CURATED_PICKS,
    ;

    companion object {
        val DEFAULT_ORDER = entries
    }
}

@Immutable
@Serializable
data class NewsletterPreferences(
    val enabled: Boolean = true,
    val dayOfWeek: Int = java.util.Calendar.SATURDAY,
    val lastViewedMs: Long = 0L,
)
