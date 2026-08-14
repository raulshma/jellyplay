package com.raulshma.jellyplay.core.ui.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.R

/**
 * Localizable display labels for [MediaType].
 *
 * The model enum itself has no resource access, so display strings must be resolved
 * at the UI layer. These mappings live in `core:ui` so every feature screen
 * (library, search, details, …) shares one source of truth and translators get a
 * single set of `core_media_*` keys to translate.
 *
 * Prefer [mediaTypeDisplayName] / [mediaTypeDisplayNamePlural] inside `@Composable`
 * scope; use [mediaTypeDisplayNameRes] / [mediaTypeDisplayNamePluralRes] when you
 * need a `@StringRes Int` (e.g. to build a [com.raulshma.jellyplay.core.ui.feedback.UiText]).
 */

/** Singular label resource for this media type (e.g. "Movie", "Series", "Track"). */
@StringRes
fun MediaType.mediaTypeDisplayNameRes(): Int = when (this) {
    MediaType.MOVIE -> R.string.core_media_movie
    MediaType.SERIES -> R.string.core_media_series
    MediaType.SEASON -> R.string.core_media_season
    MediaType.EPISODE -> R.string.core_media_episode
    MediaType.MUSIC, MediaType.AUDIO -> R.string.core_media_track
    MediaType.ALBUM -> R.string.core_media_album
    MediaType.ARTIST -> R.string.core_media_artist
    MediaType.MUSIC_VIDEO -> R.string.core_media_music_video
    MediaType.COLLECTION -> R.string.core_media_collection
    MediaType.PHOTO -> R.string.core_media_photo
    MediaType.PHOTO_FOLDER -> R.string.core_media_photo_album
    MediaType.LIVE_TV -> R.string.core_media_live_tv
    MediaType.CHANNEL -> R.string.core_media_channel
    MediaType.UNKNOWN -> R.string.core_media_unknown
}

/** Plural label resource for this media type (e.g. "Movies", "TV Shows", "Music"). */
@StringRes
fun MediaType.mediaTypeDisplayNamePluralRes(): Int = when (this) {
    MediaType.MOVIE -> R.string.core_media_movie_plural
    MediaType.SERIES -> R.string.core_media_series_plural
    MediaType.SEASON -> R.string.core_media_season_plural
    MediaType.EPISODE -> R.string.core_media_episode_plural
    MediaType.MUSIC, MediaType.AUDIO -> R.string.core_media_music_plural
    MediaType.ALBUM -> R.string.core_media_album_plural
    MediaType.ARTIST -> R.string.core_media_artist_plural
    MediaType.MUSIC_VIDEO -> R.string.core_media_music_video_plural
    MediaType.COLLECTION -> R.string.core_media_collection_plural
    MediaType.PHOTO -> R.string.core_media_photo_plural
    MediaType.PHOTO_FOLDER -> R.string.core_media_photo_album_plural
    MediaType.LIVE_TV -> R.string.core_media_live_tv_plural
    MediaType.CHANNEL -> R.string.core_media_channel_plural
    MediaType.UNKNOWN -> R.string.core_media_unknown_plural
}

/** Singular, localized display name for this media type. */
@Composable
fun MediaType.mediaTypeDisplayName(): String = stringResource(mediaTypeDisplayNameRes())

/** Plural, localized display name for this media type. */
@Composable
fun MediaType.mediaTypeDisplayNamePlural(): String = stringResource(mediaTypeDisplayNamePluralRes())
