package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.MediaSourceType
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MissingEpisodeReason
import io.mockk.mockk
import java.util.UUID
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.model.DateTime
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.LocationType
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    // ── virtual (missing/unaired) episode mapping ────────────────────────

    @Test
    fun `virtual episode with future premiere date derives unaired`() {
        val now = DateTime.now()
        assertEquals(
            MissingEpisodeReason.UNAIRED,
            deriveMissingEpisodeReason(LocationType.VIRTUAL, now.plusDays(7), now),
        )
    }

    @Test
    fun `virtual episode with past or absent premiere date derives missing file`() {
        val now = DateTime.now()
        assertEquals(
            MissingEpisodeReason.MISSING_FILE,
            deriveMissingEpisodeReason(LocationType.VIRTUAL, now.minusDays(1), now),
        )
        assertEquals(
            MissingEpisodeReason.MISSING_FILE,
            deriveMissingEpisodeReason(LocationType.VIRTUAL, null, now),
        )
    }

    @Test
    fun `non-virtual locations derive no missing reason`() {
        val now = DateTime.now()
        assertNull(deriveMissingEpisodeReason(LocationType.FILE_SYSTEM, now.plusDays(7), now))
        assertNull(deriveMissingEpisodeReason(LocationType.OFFLINE, now.plusDays(7), now))
        assertNull(deriveMissingEpisodeReason(null, now.plusDays(7), now))
    }

    @Test
    fun `toMediaItem carries virtual state from LocationType`() {
        val now = DateTime.now()
        val virtual = BaseItemDto(
            id = UUID.fromString("11111111-1111-4111-8111-111111111111"),
            name = "Unaired Episode",
            type = BaseItemKind.EPISODE,
            locationType = LocationType.VIRTUAL,
            premiereDate = now.plusDays(3),
        ).toMediaItem()
        val real = BaseItemDto(
            id = UUID.fromString("22222222-2222-4222-8222-222222222222"),
            name = "Real Episode",
            type = BaseItemKind.EPISODE,
            locationType = LocationType.FILE_SYSTEM,
        ).toMediaItem()

        assertTrue(virtual.isVirtual)
        assertEquals(MissingEpisodeReason.UNAIRED, virtual.missingReason)
        assertFalse(real.isVirtual)
        assertNull(real.missingReason)
    }

    // ── detail image-tag mapping ("prefer logos" title) ──────────────────

    @Test
    fun `toMediaDetail carries the logo image tag`() {
        val detail = BaseItemDto(
            id = UUID.fromString("33333333-3333-4333-8333-333333333333"),
            name = "Logo Movie",
            type = BaseItemKind.MOVIE,
            imageTags = mapOf(ImageType.LOGO to "logo-tag-1", ImageType.PRIMARY to "poster-tag-1"),
        ).toMediaDetail()

        assertEquals("logo-tag-1", detail.logoImageTag)
    }

    @Test
    fun `toMediaDetail leaves the logo tag null without a LOGO image`() {
        val detail = BaseItemDto(
            id = UUID.fromString("44444444-4444-4444-8444-444444444444"),
            name = "Bare Movie",
            type = BaseItemKind.MOVIE,
            imageTags = mapOf(ImageType.PRIMARY to "poster-tag-1"),
        ).toMediaDetail()
        val tagless = BaseItemDto(
            id = UUID.fromString("55555555-5555-4555-8555-555555555555"),
            name = "Tagless Movie",
            type = BaseItemKind.MOVIE,
        ).toMediaDetail()

        assertNull(detail.logoImageTag)
        assertNull(tagless.logoImageTag)
    }

    // ── MediaSourceType mapping (multi-version selection, 1.2) ──────────

    private fun sourceInfo(type: org.jellyfin.sdk.model.api.MediaSourceType) = MediaSourceInfo(
        protocol = MediaProtocol.FILE,
        id = "abc12345",
        type = type,
        isRemote = false,
        readAtNativeFramerate = false,
        ignoreDts = false,
        ignoreIndex = false,
        genPtsInput = false,
        supportsTranscoding = false,
        supportsDirectStream = false,
        supportsDirectPlay = false,
        isInfiniteStream = false,
        requiresOpening = false,
        requiresClosing = false,
        requiresLooping = false,
        supportsProbing = false,
        hasSegments = false,
        transcodingSubProtocol = org.jellyfin.sdk.model.api.MediaStreamProtocol.HLS,
    )

    @Test
    fun `toMediaSource maps each wire MediaSourceType`() {
        assertEquals(
            MediaSourceType.DEFAULT,
            sourceInfo(org.jellyfin.sdk.model.api.MediaSourceType.DEFAULT).toMediaSource().type,
        )
        assertEquals(
            MediaSourceType.GROUPING,
            sourceInfo(org.jellyfin.sdk.model.api.MediaSourceType.GROUPING).toMediaSource().type,
        )
        assertEquals(
            MediaSourceType.PLACEHOLDER,
            sourceInfo(org.jellyfin.sdk.model.api.MediaSourceType.PLACEHOLDER).toMediaSource().type,
        )
    }

    @Test
    fun `toMediaSourceType maps an absent wire type to DEFAULT`() {
        // The wire field is nullable on some server shapes; null reads as the
        // safe "playable file" default.
        val absent: org.jellyfin.sdk.model.api.MediaSourceType? = null
        assertEquals(MediaSourceType.DEFAULT, absent.toMediaSourceType())
    }
}
