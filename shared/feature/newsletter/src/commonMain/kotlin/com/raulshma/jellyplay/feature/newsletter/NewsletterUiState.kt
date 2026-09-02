package com.raulshma.jellyplay.feature.newsletter

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.model.ItemCounts
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.NewsletterSectionType

@Immutable
data class NewsletterUiState(
    val serverName: String = "",
    val recentlyAdded: List<MediaItem> = emptyList(),
    val activityDigest: List<ActivityLogEntry> = emptyList(),
    val libraryStats: ItemCounts? = null,
    val continueWatching: List<MediaItem> = emptyList(),
    val nextUp: List<MediaItem> = emptyList(),
    val curatedPicks: List<MediaItem> = emptyList(),
    val sectionOrder: List<NewsletterSectionType> = NewsletterSectionType.DEFAULT_ORDER,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val isAdmin: Boolean = false,
    val isSending: Boolean = false,
    val sendResult: NewsletterMessage? = null,
    val pendingSendAction: NewsletterSendAction? = null,
)

enum class NewsletterSendAction { SEND_NOW, SEND_TEST }
