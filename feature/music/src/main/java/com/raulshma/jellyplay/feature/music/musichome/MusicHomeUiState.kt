package com.raulshma.jellyplay.feature.music.musichome

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.feature.music.R

/**
 * Identifies a home-screen music section by type rather than by display title, so
 * rendering can use an exhaustive `when` and a renamed label can never silently
 * hide a section. Display strings live on [displayNameRes]/[subtitleRes] so the UI
 * layer never matches against magic strings.
 */
enum class MusicHomeSectionType {
    FAVORITE_ARTISTS,
    LATEST_ALBUMS,
    RECENTLY_PLAYED,
    TOP_RATED_ALBUMS,
    FAVORITE_TRACKS,
    ;

    @get:StringRes
    val displayNameRes: Int
        get() = when (this) {
            FAVORITE_ARTISTS -> R.string.music_favorite_artists
            LATEST_ALBUMS -> R.string.music_latest_albums
            RECENTLY_PLAYED -> R.string.music_recently_played
            TOP_RATED_ALBUMS -> R.string.music_top_rated_albums
            FAVORITE_TRACKS -> R.string.music_favorite_tracks
        }

    /** Descriptive subtitle shown under the section header. */
    @get:StringRes
    val subtitleRes: Int
        get() = when (this) {
            FAVORITE_ARTISTS -> R.string.music_favorite_artists_subtitle
            LATEST_ALBUMS -> R.string.music_fresh_music
            RECENTLY_PLAYED -> R.string.music_recently_played_subtitle
            TOP_RATED_ALBUMS -> R.string.music_top_rated_albums_subtitle
            FAVORITE_TRACKS -> R.string.music_favorite_tracks_subtitle
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
)
