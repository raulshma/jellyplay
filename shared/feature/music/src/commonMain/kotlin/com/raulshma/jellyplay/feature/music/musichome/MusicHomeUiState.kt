package com.raulshma.jellyplay.feature.music.musichome

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_favorite_artists
import com.raulshma.jellyplay.feature.music.generated.resources.music_favorite_artists_subtitle
import com.raulshma.jellyplay.feature.music.generated.resources.music_favorite_tracks
import com.raulshma.jellyplay.feature.music.generated.resources.music_favorite_tracks_subtitle
import com.raulshma.jellyplay.feature.music.generated.resources.music_fresh_music
import com.raulshma.jellyplay.feature.music.generated.resources.music_latest_albums
import com.raulshma.jellyplay.feature.music.generated.resources.music_recently_played
import com.raulshma.jellyplay.feature.music.generated.resources.music_recently_played_subtitle
import com.raulshma.jellyplay.feature.music.generated.resources.music_top_rated_albums
import com.raulshma.jellyplay.feature.music.generated.resources.music_top_rated_albums_subtitle
import org.jetbrains.compose.resources.StringResource

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

    val displayNameRes: StringResource
        get() = when (this) {
            FAVORITE_ARTISTS -> Res.string.music_favorite_artists
            LATEST_ALBUMS -> Res.string.music_latest_albums
            RECENTLY_PLAYED -> Res.string.music_recently_played
            TOP_RATED_ALBUMS -> Res.string.music_top_rated_albums
            FAVORITE_TRACKS -> Res.string.music_favorite_tracks
        }

    /** Descriptive subtitle shown under the section header. */
    val subtitleRes: StringResource
        get() = when (this) {
            FAVORITE_ARTISTS -> Res.string.music_favorite_artists_subtitle
            LATEST_ALBUMS -> Res.string.music_fresh_music
            RECENTLY_PLAYED -> Res.string.music_recently_played_subtitle
            TOP_RATED_ALBUMS -> Res.string.music_top_rated_albums_subtitle
            FAVORITE_TRACKS -> Res.string.music_favorite_tracks_subtitle
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
