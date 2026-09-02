package com.raulshma.jellyplay.core.ui.model

import androidx.compose.runtime.Composable
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_album
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_album_plural
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_artist
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_artist_plural
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_channel
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_channel_plural
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_collection
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_collection_plural
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_episode
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_episode_plural
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_live_tv
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_live_tv_plural
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_movie
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_movie_plural
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_music_plural
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_music_video
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_music_video_plural
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_photo
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_photo_album
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_photo_album_plural
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_photo_plural
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_season
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_season_plural
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_series
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_series_plural
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_track
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_unknown
import com.raulshma.jellyplay.core.ui.generated.resources.core_media_unknown_plural
import org.jetbrains.compose.resources.stringResource

/**
 * Localizable display labels for [MediaType].
 *
 * The model enum itself has no resource access, so display strings are resolved
 * at the UI layer. These mappings live in `core:ui` so every feature screen
 * (library, search, details, …) shares one source of truth and translators get a
 * single set of `core_media_*` keys to translate.
 *
 * The `@StringRes Int` halves stay in the legacy `:core:ui` shim until every
 * consumer has migrated off resource ids (plan §Phase X).
 */

/** Singular, localized display name for this media type. */
@Composable
fun MediaType.mediaTypeDisplayName(): String = stringResource(
    when (this) {
        MediaType.MOVIE -> Res.string.core_media_movie
        MediaType.SERIES -> Res.string.core_media_series
        MediaType.SEASON -> Res.string.core_media_season
        MediaType.EPISODE -> Res.string.core_media_episode
        MediaType.MUSIC, MediaType.AUDIO -> Res.string.core_media_track
        MediaType.ALBUM -> Res.string.core_media_album
        MediaType.ARTIST -> Res.string.core_media_artist
        MediaType.MUSIC_VIDEO -> Res.string.core_media_music_video
        MediaType.COLLECTION -> Res.string.core_media_collection
        MediaType.PHOTO -> Res.string.core_media_photo
        MediaType.PHOTO_FOLDER -> Res.string.core_media_photo_album
        MediaType.LIVE_TV -> Res.string.core_media_live_tv
        MediaType.CHANNEL -> Res.string.core_media_channel
        MediaType.UNKNOWN -> Res.string.core_media_unknown
    },
)

/** Plural, localized display name for this media type. */
@Composable
fun MediaType.mediaTypeDisplayNamePlural(): String = stringResource(
    when (this) {
        MediaType.MOVIE -> Res.string.core_media_movie_plural
        MediaType.SERIES -> Res.string.core_media_series_plural
        MediaType.SEASON -> Res.string.core_media_season_plural
        MediaType.EPISODE -> Res.string.core_media_episode_plural
        MediaType.MUSIC, MediaType.AUDIO -> Res.string.core_media_music_plural
        MediaType.ALBUM -> Res.string.core_media_album_plural
        MediaType.ARTIST -> Res.string.core_media_artist_plural
        MediaType.MUSIC_VIDEO -> Res.string.core_media_music_video_plural
        MediaType.COLLECTION -> Res.string.core_media_collection_plural
        MediaType.PHOTO -> Res.string.core_media_photo_plural
        MediaType.PHOTO_FOLDER -> Res.string.core_media_photo_album_plural
        MediaType.LIVE_TV -> Res.string.core_media_live_tv_plural
        MediaType.CHANNEL -> Res.string.core_media_channel_plural
        MediaType.UNKNOWN -> Res.string.core_media_unknown_plural
    },
)
