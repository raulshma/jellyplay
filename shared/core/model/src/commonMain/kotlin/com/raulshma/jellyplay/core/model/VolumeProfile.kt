package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Per-content-type volume-memory bucket. One remembered volume level
 * is stored per bucket — movies louder than audiobooks, music in between —
 * scoped to the surfaces where the app owns a volume scalar (desktop mpv
 * video, the audio players). Android video deliberately stays on the system
 * `STREAM_MUSIC` model and never reads or writes these buckets.
 */
@Immutable
@Serializable
enum class VolumeBucket {
    /** MOVIE / EPISODE / LIVE_TV / … — everything played by the video player. */
    VIDEO,

    /** Music playback (the audio player's default diet). */
    MUSIC,

    /** Audio books (Jellyfin `BOOK` items with an audio stream). */
    AUDIOBOOK,

    /** Anything that fits neither diet; keeps unknown types from poisoning VIDEO/MUSIC. */
    OTHER,
}

/**
 * Pure bucket classification: [MediaType] → [VolumeBucket].
 *
 *  - VIDEO: every playable video type the video player can be handed —
 *    MOVIE, EPISODE, MUSIC_VIDEO, LIVE_TV, CHANNEL.
 *  - MUSIC: the audio-player diet — MUSIC, AUDIO, plus the container types
 *    (ALBUM/ARTIST) that resolve into it.
 *  - AUDIOBOOK: BOOK (audio books are played through the audio stack).
 *  - OTHER: non-playable containers (SERIES, SEASON, COLLECTION, FOLDER,
 *    PHOTO*) and UNKNOWN — a bucket that exists so an unmapped future type
 *    never silently inherits the VIDEO or MUSIC level.
 */
fun volumeBucketFor(mediaType: MediaType): VolumeBucket = when (mediaType) {
    MediaType.MOVIE,
    MediaType.EPISODE,
    MediaType.MUSIC_VIDEO,
    MediaType.LIVE_TV,
    MediaType.CHANNEL,
    -> VolumeBucket.VIDEO

    MediaType.MUSIC,
    MediaType.AUDIO,
    MediaType.ALBUM,
    MediaType.ARTIST,
    -> VolumeBucket.MUSIC

    MediaType.BOOK -> VolumeBucket.AUDIOBOOK

    MediaType.SERIES,
    MediaType.SEASON,
    MediaType.COLLECTION,
    MediaType.PHOTO,
    MediaType.PHOTO_FOLDER,
    MediaType.FOLDER,
    MediaType.UNKNOWN,
    -> VolumeBucket.OTHER
}
