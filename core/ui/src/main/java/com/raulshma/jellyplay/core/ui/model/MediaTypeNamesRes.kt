package com.raulshma.jellyplay.core.ui.model

import androidx.annotation.StringRes
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.R

/**
 * `@StringRes Int` halves of the media-type label tables. The `@Composable`
 * halves live in `shared/core/ui` over Compose Resources; these id-based
 * variants stay for legacy consumers building [com.raulshma.jellyplay.core.ui.feedback.UiText]
 * and die at cutover (plan §Phase X).
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
