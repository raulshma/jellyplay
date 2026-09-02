package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.model.api.BaseItemKind
import kotlin.test.Test
import kotlin.test.assertEquals

class JellyfinDtoMappersTest {

    @Test
    fun `BaseItemKind to MediaType mapping`() {
        assertEquals(MediaType.MOVIE, BaseItemKind.MOVIE.toMediaType())
        assertEquals(MediaType.SERIES, BaseItemKind.SERIES.toMediaType())
        assertEquals(MediaType.SEASON, BaseItemKind.SEASON.toMediaType())
        assertEquals(MediaType.EPISODE, BaseItemKind.EPISODE.toMediaType())
        assertEquals(MediaType.ALBUM, BaseItemKind.MUSIC_ALBUM.toMediaType())
        assertEquals(MediaType.AUDIO, BaseItemKind.AUDIO.toMediaType())
        assertEquals(MediaType.ARTIST, BaseItemKind.MUSIC_ARTIST.toMediaType())
        assertEquals(MediaType.COLLECTION, BaseItemKind.BOX_SET.toMediaType())
        assertEquals(MediaType.CHANNEL, BaseItemKind.LIVE_TV_CHANNEL.toMediaType())
        assertEquals(MediaType.LIVE_TV, BaseItemKind.LIVE_TV_PROGRAM.toMediaType())
    }

    @Test
    fun `unknown BaseItemKind maps to UNKNOWN`() {
        assertEquals(MediaType.UNKNOWN, BaseItemKind.PLAYLIST.toMediaType())
        assertEquals(MediaType.UNKNOWN, BaseItemKind.VIDEO.toMediaType())
    }

    @Test
    fun `PHOTO BaseItemKind maps to PHOTO`() {
        assertEquals(MediaType.PHOTO, BaseItemKind.PHOTO.toMediaType())
    }

    @Test
    fun `PHOTO_ALBUM BaseItemKind maps to PHOTO_FOLDER`() {
        assertEquals(MediaType.PHOTO_FOLDER, BaseItemKind.PHOTO_ALBUM.toMediaType())
    }
}
