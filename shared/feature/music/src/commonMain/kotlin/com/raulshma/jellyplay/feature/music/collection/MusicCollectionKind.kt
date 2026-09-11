package com.raulshma.jellyplay.feature.music.collection

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Disc
import com.composables.icons.tabler.outline.Music
import com.composables.icons.tabler.outline.Playlist
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_failed_load
import com.raulshma.jellyplay.feature.music.generated.resources.music_failed_load_albums
import com.raulshma.jellyplay.feature.music.generated.resources.music_failed_load_artists
import com.raulshma.jellyplay.feature.music.generated.resources.music_failed_load_tracks
import com.raulshma.jellyplay.feature.music.generated.resources.music_no_albums_found
import com.raulshma.jellyplay.feature.music.generated.resources.music_no_artists_found
import com.raulshma.jellyplay.feature.music.generated.resources.music_no_genres_found
import com.raulshma.jellyplay.feature.music.generated.resources.music_no_playlists_found
import com.raulshma.jellyplay.feature.music.generated.resources.music_no_tracks_found
import org.jetbrains.compose.resources.StringResource

/**
 * The ONE configuration table for the five music collections (artists, albums,
 * tracks, genres, playlists). Each entry is the collection's declared sort
 * admission set, its paged-source binding, and its empty/error presentation —
 * the sort menus and the collection ladder render from this data, never from
 * per-screen hand-picked lists.
 *
 * **Sort-admission policy** (pinned by `MusicCollectionSortTableTest`): a
 * collection offers exactly [sortOptions] — nothing else may appear in its
 * sort menu, and the list is the richer of the two lists the browse tab and
 * the standalone screen used to declare separately:
 *  - ARTISTS gains DATE_PLAYED on the browse side (the browse tab offered
 *    NAME/DATE_ADDED/RANDOM while the standalone screen offered the
 *    four-entry list unified here);
 *  - ALBUMS keeps all [MusicSortOption] entries (both families already offered
 *    the full set — YEAR is an albums-only axis);
 *  - TRACKS keeps the standalone four-entry list (YEAR was offered by neither);
 *  - GENRES/PLAYLISTS declare no sort: their list sources
 *    (`getGenres`/`getPlaylists`) take no sort parameter.
 *
 * [mediaType] is the `SortedPagedCollection` binding — non-null exactly for
 * the three server-paged collections; GENRES/PLAYLISTS are list-sourced
 * ([com.raulshma.jellyplay.feature.music.collection.SimpleListCollection]) and
 * carry null.
 *
 * [emptyTitleRes]/[emptyIcon] are the ladder's declared empty state per
 * collection, and [errorFallbackRes] the ladder's null-message error fallback
 * for both families — the paged rungs render it under a `LoadState.Error`
 * without a message, the list-sourced rungs under
 * [com.raulshma.jellyplay.feature.music.collection.SimpleListCollection]'s
 * error.
 */
enum class MusicCollectionKind(
    val sortOptions: List<MusicSortOption>,
    val mediaType: MediaType?,
    val emptyTitleRes: StringResource,
    val emptyIcon: ImageVector,
    val errorFallbackRes: StringResource,
) {
    ARTISTS(
        sortOptions = listOf(
            MusicSortOption.NAME,
            MusicSortOption.DATE_ADDED,
            MusicSortOption.DATE_PLAYED,
            MusicSortOption.RANDOM,
        ),
        mediaType = MediaType.ARTIST,
        emptyTitleRes = Res.string.music_no_artists_found,
        emptyIcon = Tabler.Outline.Music,
        errorFallbackRes = Res.string.music_failed_load_artists,
    ),
    ALBUMS(
        sortOptions = MusicSortOption.entries.toList(),
        mediaType = MediaType.ALBUM,
        emptyTitleRes = Res.string.music_no_albums_found,
        emptyIcon = Tabler.Outline.Disc,
        errorFallbackRes = Res.string.music_failed_load_albums,
    ),
    TRACKS(
        sortOptions = listOf(
            MusicSortOption.NAME,
            MusicSortOption.DATE_ADDED,
            MusicSortOption.DATE_PLAYED,
            MusicSortOption.RANDOM,
        ),
        mediaType = MediaType.AUDIO,
        emptyTitleRes = Res.string.music_no_tracks_found,
        emptyIcon = Tabler.Outline.Music,
        errorFallbackRes = Res.string.music_failed_load_tracks,
    ),
    GENRES(
        sortOptions = emptyList(),
        mediaType = null,
        emptyTitleRes = Res.string.music_no_genres_found,
        emptyIcon = Tabler.Outline.Music,
        errorFallbackRes = Res.string.music_failed_load,
    ),
    PLAYLISTS(
        sortOptions = emptyList(),
        mediaType = null,
        emptyTitleRes = Res.string.music_no_playlists_found,
        emptyIcon = Tabler.Outline.Playlist,
        errorFallbackRes = Res.string.music_failed_load,
    ),
}
