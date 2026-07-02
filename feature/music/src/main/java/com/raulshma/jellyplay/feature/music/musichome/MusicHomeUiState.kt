package com.raulshma.jellyplay.feature.music.musichome

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflineMode

/**
 * Identifies a home-screen music section by type rather than by display title, so
 * rendering can use an exhaustive `when` and a renamed label can never silently
 * hide a section. Display strings live on [displayName]/[subtitle] so the UI
 * layer never matches against magic strings.
 */
enum class MusicHomeSectionType {
    FAVORITE_ARTISTS,
    LATEST_ALBUMS,
    RECENTLY_PLAYED,
    TOP_RATED_ALBUMS,
    FAVORITE_TRACKS,
    ;

    val displayName: String
        get() = when (this) {
            FAVORITE_ARTISTS -> "Favorite Artists"
            LATEST_ALBUMS -> "Latest Albums"
            RECENTLY_PLAYED -> "Recently Played"
            TOP_RATED_ALBUMS -> "Top Rated Albums"
            FAVORITE_TRACKS -> "Favorite Tracks"
        }

    /** Descriptive subtitle shown under the section header. */
    val subtitle: String
        get() = when (this) {
            FAVORITE_ARTISTS -> "The voices you keep coming back to"
            LATEST_ALBUMS -> "Fresh music just for you"
            RECENTLY_PLAYED -> "Continue your musical journey"
            TOP_RATED_ALBUMS -> "Highest rated by the community"
            FAVORITE_TRACKS -> "Songs you love the most"
        }
}

@Immutable
data class MusicHomeSection(
    val type: MusicHomeSectionType,
    val items: List<MediaItem>,
)

@Immutable
data class MusicHomeUiState(
    val sections: List<MusicHomeSection> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val homeMode: HomeMode = HomeMode.VIDEO,
    val offlineMode: OfflineMode = OfflineMode.ONLINE,
    val offlineLibrary: List<OfflineMediaItem> = emptyList(),
)
