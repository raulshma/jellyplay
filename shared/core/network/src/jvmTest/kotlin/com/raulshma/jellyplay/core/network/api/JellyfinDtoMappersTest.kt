package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ImageBlurHashes
import com.raulshma.jellyplay.core.model.ItemQualityBadge
import com.raulshma.jellyplay.core.model.MediaImageTags
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
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.LocationType
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.PersonKind
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

    // ── credited-People retention ─────────────

    @Test
    fun `toMediaItem retains the credited People projected on the item`() {
        val personId = UUID.fromString("77777777-7777-4777-8777-777777777777")
        val mapped = BaseItemDto(
            id = UUID.fromString("55555555-5555-4555-8555-555555555555"),
            name = "Credited Movie",
            type = BaseItemKind.MOVIE,
            people = listOf(
                BaseItemPerson(
                    name = "The Person",
                    id = personId,
                    role = "John Smith",
                    type = PersonKind.ACTOR,
                    primaryImageTag = "portrait-tag",
                ),
                BaseItemPerson(
                    name = "The Person",
                    id = personId,
                    role = null,
                    type = PersonKind.DIRECTOR,
                ),
            ),
        ).toMediaItem()

        // Deduped to the first credit per person id (the multi-role rule),
        // projected with the same shape as toMediaDetail.
        assertEquals(1, mapped.people.size)
        val credit = mapped.people.single()
        assertEquals(personId.toString(), credit.id)
        assertEquals("John Smith", credit.role)
        assertEquals("Actor", credit.type)
        assertEquals("portrait-tag", credit.primaryImageTag)
    }

    @Test
    fun `toMediaItem leaves the People empty without a projected list`() {
        val mapped = BaseItemDto(
            id = UUID.fromString("66666666-6666-4666-8666-666666666666"),
            name = "Bare Movie",
            type = BaseItemKind.MOVIE,
        ).toMediaItem()

        assertTrue(mapped.people.isEmpty())
    }

    // ── image-tag threading (the stale-artwork cache busters) ────────────

    @Test
    fun `toMediaItem carries the own and inherited image tags`() {
        val mapped = BaseItemDto(
            id = UUID.fromString("88888888-8888-4888-8888-888888888888"),
            name = "Tagged Episode",
            type = BaseItemKind.EPISODE,
            imageTags = mapOf(
                ImageType.PRIMARY to "primary-tag",
                ImageType.LOGO to "logo-tag",
            ),
            backdropImageTags = listOf("backdrop-tag", "unused"),
            parentPrimaryImageTag = "parent-tag",
            parentThumbImageTag = "parent-thumb-tag",
            seriesPrimaryImageTag = "series-tag",
            seriesThumbImageTag = "series-thumb-tag",
            albumPrimaryImageTag = "album-tag",
        ).toMediaItem()

        val tags = mapped.imageTags
        assertEquals("primary-tag", tags.primary)
        assertEquals("backdrop-tag", tags.backdrop)
        assertEquals("logo-tag", tags.logo)
        assertEquals("parent-tag", tags.parentPrimary)
        assertEquals("parent-thumb-tag", tags.parentThumb)
        assertEquals("series-tag", tags.seriesPrimary)
        assertEquals("series-thumb-tag", tags.seriesThumb)
        assertEquals("album-tag", tags.album)
    }

    @Test
    fun `toMediaItem defaults the image tags when the item carries none`() {
        val mapped = BaseItemDto(
            id = UUID.fromString("99999999-9999-4999-8999-999999999999"),
            name = "Tagless Movie",
            type = BaseItemKind.MOVIE,
        ).toMediaItem()

        assertEquals(MediaImageTags(), mapped.imageTags)
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

    // ── episode stream-info derivation (season rows' stream badges) ──

    private fun wireStream(
        type: org.jellyfin.sdk.model.api.MediaStreamType,
        channels: Int? = null,
        codec: String? = null,
        language: String? = null,
        profile: String? = null,
        title: String? = null,
        displayTitle: String? = null,
        width: Int? = null,
        height: Int? = null,
        // SDK 1.8.x made the range enums non-null; an absent server field
        // deserializes to UNKNOWN ("Unknown"), which carries no HDR/DoVi
        // badge marker — the same reading the old null had.
        videoRange: org.jellyfin.sdk.model.api.VideoRange = org.jellyfin.sdk.model.api.VideoRange.UNKNOWN,
        videoRangeType: org.jellyfin.sdk.model.api.VideoRangeType = org.jellyfin.sdk.model.api.VideoRangeType.UNKNOWN,
        videoDoViTitle: String? = null,
        index: Int = 0,
    ) = org.jellyfin.sdk.model.api.MediaStream(
        type = type,
        channels = channels,
        codec = codec,
        language = language,
        profile = profile,
        title = title,
        displayTitle = displayTitle,
        width = width,
        height = height,
        videoRange = videoRange,
        videoRangeType = videoRangeType,
        videoDoViTitle = videoDoViTitle,
        index = index,
        isInterlaced = false,
        isDefault = false,
        isForced = false,
        isHearingImpaired = false,
        isExternal = false,
        isTextSubtitleStream = false,
        supportsExternalStream = false,
    )

    @Test
    fun `stream info detects subtitles and counts distinct audio languages`() {
        val info = toEpisodeStreamInfo(
            listOf(
                wireStream(org.jellyfin.sdk.model.api.MediaStreamType.VIDEO),
                wireStream(org.jellyfin.sdk.model.api.MediaStreamType.AUDIO, codec = "aac", language = "eng"),
                wireStream(org.jellyfin.sdk.model.api.MediaStreamType.AUDIO, codec = "aac", language = "jpn"),
                // Same language as the first track — must not double-count.
                wireStream(org.jellyfin.sdk.model.api.MediaStreamType.AUDIO, codec = "eac3", language = "ENG"),
                wireStream(org.jellyfin.sdk.model.api.MediaStreamType.SUBTITLE, language = "eng"),
            ),
        )

        assertTrue(info!!.hasSubtitles)
        assertEquals(2, info.audioLanguageCount)
    }

    @Test
    fun `stream info without subtitle streams reports hasSubtitles false`() {
        val info = toEpisodeStreamInfo(
            listOf(
                wireStream(org.jellyfin.sdk.model.api.MediaStreamType.VIDEO),
                wireStream(org.jellyfin.sdk.model.api.MediaStreamType.AUDIO, codec = "aac"),
            ),
        )

        assertFalse(info!!.hasSubtitles)
    }

    @Test
    fun `stream info derives the 5_1 layout from a 6-channel audio track`() {
        val info = toEpisodeStreamInfo(
            listOf(
                wireStream(org.jellyfin.sdk.model.api.MediaStreamType.VIDEO),
                wireStream(org.jellyfin.sdk.model.api.MediaStreamType.AUDIO, channels = 2, codec = "aac"),
                wireStream(org.jellyfin.sdk.model.api.MediaStreamType.AUDIO, channels = 6, codec = "ac3"),
            ),
        )

        // The best (most-channelled) track decides the layout.
        assertEquals("5.1", info!!.audioLayout)
    }

    @Test
    fun `stream info derives the 7_1 layout from an 8-channel audio track`() {
        val info = toEpisodeStreamInfo(
            listOf(wireStream(org.jellyfin.sdk.model.api.MediaStreamType.AUDIO, channels = 8, codec = "truehd")),
        )

        assertEquals("7.1", info!!.audioLayout)
    }

    @Test
    fun `stream info maps stereo-and-below audio layouts to null`() {
        val stereo = toEpisodeStreamInfo(
            listOf(wireStream(org.jellyfin.sdk.model.api.MediaStreamType.AUDIO, channels = 2, codec = "aac")),
        )
        val mono = toEpisodeStreamInfo(
            listOf(wireStream(org.jellyfin.sdk.model.api.MediaStreamType.AUDIO, channels = 1, codec = "mp3")),
        )
        val channelless = toEpisodeStreamInfo(
            listOf(wireStream(org.jellyfin.sdk.model.api.MediaStreamType.AUDIO, codec = "aac")),
        )

        assertNull(stereo!!.audioLayout)
        assertNull(mono!!.audioLayout)
        assertNull(channelless!!.audioLayout)
    }

    @Test
    fun `stream info prefers the Atmos label when any audio stream advertises it`() {
        val info = toEpisodeStreamInfo(
            listOf(
                // The Atmos track has FEWER channels than the best one — the
                // Atmos detection still wins, mirroring the player's label.
                wireStream(org.jellyfin.sdk.model.api.MediaStreamType.AUDIO, channels = 8, codec = "dts"),
                wireStream(
                    org.jellyfin.sdk.model.api.MediaStreamType.AUDIO,
                    channels = 6,
                    codec = "eac3",
                    profile = "Dolby Digital Plus with Dolby Atmos",
                ),
            ),
        )

        assertEquals("Atmos", info!!.audioLayout)
    }

    @Test
    fun `stream info detects Atmos from the display title when the profile is absent`() {
        val info = toEpisodeStreamInfo(
            listOf(
                wireStream(
                    org.jellyfin.sdk.model.api.MediaStreamType.AUDIO,
                    channels = 6,
                    codec = "eac3",
                    displayTitle = "Eng 5.1 EAC3 Atmos",
                ),
            ),
        )

        assertEquals("Atmos", info!!.audioLayout)
    }

    @Test
    fun `stream info with no audio streams still reports subtitles with null layout`() {
        val info = toEpisodeStreamInfo(
            listOf(
                wireStream(org.jellyfin.sdk.model.api.MediaStreamType.VIDEO),
                wireStream(org.jellyfin.sdk.model.api.MediaStreamType.SUBTITLE, language = "eng"),
            ),
        )

        assertTrue(info!!.hasSubtitles)
        assertNull(info.audioLayout)
        assertEquals(0, info.audioLanguageCount)
    }

    @Test
    fun `stream info maps missing or empty streams to null`() {
        assertNull(toEpisodeStreamInfo(emptyList()))
        // A fetch without the MediaStreams projection (all-episodes path,
        // older servers) carries no mediaSources at all.
        val bare = BaseItemDto(
            id = UUID.fromString("66666666-6666-4666-8666-666666666666"),
            name = "Episode",
            type = BaseItemKind.EPISODE,
        ).toMediaItem()
        assertNull(bare.episodeStreamInfo)
    }

    @Test
    fun `toMediaItem carries stream info only when the fetch projected MediaStreams`() {
        val streams = listOf(
            wireStream(org.jellyfin.sdk.model.api.MediaStreamType.AUDIO, channels = 6, codec = "ac3", language = "eng"),
            wireStream(org.jellyfin.sdk.model.api.MediaStreamType.SUBTITLE, language = "eng"),
        )
        val withStreams = BaseItemDto(
            id = UUID.fromString("77777777-7777-4777-8777-777777777777"),
            name = "Season Episode",
            type = BaseItemKind.EPISODE,
            mediaSources = listOf(
                sourceInfo(org.jellyfin.sdk.model.api.MediaSourceType.DEFAULT)
                    .copy(mediaStreams = streams),
            ),
        ).toMediaItem()

        assertEquals("5.1", withStreams.episodeStreamInfo?.audioLayout)
        assertTrue(withStreams.episodeStreamInfo?.hasSubtitles == true)
    }

    // ── quality-badge derivation (cards' 4K/HDR/DV/Atmos row) ───────

    private fun videoStream(
        width: Int? = null,
        height: Int? = null,
        videoRange: org.jellyfin.sdk.model.api.VideoRange = org.jellyfin.sdk.model.api.VideoRange.UNKNOWN,
        videoRangeType: org.jellyfin.sdk.model.api.VideoRangeType = org.jellyfin.sdk.model.api.VideoRangeType.UNKNOWN,
        videoDoViTitle: String? = null,
        profile: String? = null,
    ) = wireStream(
        org.jellyfin.sdk.model.api.MediaStreamType.VIDEO,
        width = width,
        height = height,
        videoRange = videoRange,
        videoRangeType = videoRangeType,
        videoDoViTitle = videoDoViTitle,
        profile = profile,
    )

    private fun atmosAudioStream() = wireStream(
        org.jellyfin.sdk.model.api.MediaStreamType.AUDIO,
        channels = 6,
        codec = "eac3",
        profile = "Dolby Digital Plus with Dolby Atmos",
    )

    @Test
    fun `quality badges derive the 4K tier from width and height`() {
        val byWidth = toQualityBadges(listOf(videoStream(width = 3840, height = 2160)))
        val byHeight = toQualityBadges(listOf(videoStream(width = 1920, height = 2400)))
        assertEquals(listOf(ItemQualityBadge.FOUR_K), byWidth)
        assertEquals(listOf(ItemQualityBadge.FOUR_K), byHeight)
    }

    @Test
    fun `quality badges derive HD and SD tiers from width`() {
        assertEquals(listOf(ItemQualityBadge.HD), toQualityBadges(listOf(videoStream(width = 1920, height = 1080))))
        assertEquals(listOf(ItemQualityBadge.HD), toQualityBadges(listOf(videoStream(width = 1900))))
        assertEquals(listOf(ItemQualityBadge.SD), toQualityBadges(listOf(videoStream(width = 1280, height = 720))))
    }

    @Test
    fun `quality badges keep HDR when the range type advertises it`() {
        val badges = toQualityBadges(
            listOf(videoStream(width = 3840, height = 2160, videoRangeType = org.jellyfin.sdk.model.api.VideoRangeType.HDR10)),
        )

        // Resolution tier first, then the format badge (render-priority order).
        assertEquals(listOf(ItemQualityBadge.FOUR_K, ItemQualityBadge.HDR), badges)
    }

    @Test
    fun `quality badges detect Dolby Vision from the range type and block HDR`() {
        // A DoVi-with-HDR10 stream is DV, never both — the dovi serial wins.
        val badges = toQualityBadges(
            listOf(
                videoStream(
                    width = 3840,
                    height = 2160,
                    videoRangeType = org.jellyfin.sdk.model.api.VideoRangeType.DOVI_WITH_HDR10,
                ),
            ),
        )

        assertEquals(listOf(ItemQualityBadge.FOUR_K, ItemQualityBadge.DOLBY_VISION), badges)
    }

    @Test
    fun `quality badges detect Dolby Vision from the profile and the DoVi title`() {
        val byProfile = toQualityBadges(
            listOf(videoStream(width = 1920, height = 1080, profile = "Dolby Vision Profile 5")),
        )
        val byTitle = toQualityBadges(
            listOf(videoStream(width = 1920, height = 1080, videoDoViTitle = "Dolby Vision")),
        )

        assertEquals(listOf(ItemQualityBadge.HD, ItemQualityBadge.DOLBY_VISION), byProfile)
        assertEquals(listOf(ItemQualityBadge.HD, ItemQualityBadge.DOLBY_VISION), byTitle)
    }

    @Test
    fun `quality badges reuse the episode stream info's Atmos detection`() {
        val badges = toQualityBadges(
            listOf(
                videoStream(width = 3840, height = 2160),
                atmosAudioStream(),
            ),
        )

        // 4K > DV > HDR > Atmos render order — three chips cap later; the
        // derivation itself carries everything the streams advertise.
        assertEquals(
            listOf(ItemQualityBadge.FOUR_K, ItemQualityBadge.ATMOS),
            badges,
        )
    }

    @Test
    fun `quality badges map missing or audio-only streams to null`() {
        assertNull(toQualityBadges(emptyList()))
        // Music tracks / audio-only items carry no video stream — no
        // resolution ladder exists for them.
        assertNull(
            toQualityBadges(
                listOf(wireStream(org.jellyfin.sdk.model.api.MediaStreamType.AUDIO, channels = 2, codec = "aac")),
            ),
        )
    }

    @Test
    fun `toMediaItem carries quality badges only when the fetch projected MediaStreams`() {
        val projected = BaseItemDto(
            id = UUID.fromString("88888888-8888-4888-8888-888888888888"),
            name = "4K Movie",
            type = BaseItemKind.MOVIE,
            mediaSources = listOf(
                sourceInfo(org.jellyfin.sdk.model.api.MediaSourceType.DEFAULT)
                    .copy(
                        mediaStreams = listOf(
                            videoStream(width = 3840, height = 2160),
                            atmosAudioStream(),
                        ),
                    ),
            ),
        ).toMediaItem()
        val bare = BaseItemDto(
            id = UUID.fromString("99999999-9999-4999-8999-999999999999"),
            name = "Projected-less",
            type = BaseItemKind.MOVIE,
        ).toMediaItem()

        assertEquals(
            listOf(ItemQualityBadge.FOUR_K, ItemQualityBadge.ATMOS),
            projected.qualityBadges,
        )
        assertNull(bare.qualityBadges)
    }

    @Test
    fun `toMediaItem carries the DateCreated projection as epoch millis`() {
        val created = DateTime.now()
        val item = BaseItemDto(
            id = UUID.fromString("10101010-1010-4010-8010-101010101010"),
            name = "Fresh",
            type = BaseItemKind.MOVIE,
            dateCreated = created,
        ).toMediaItem()

        assertEquals(created.toEpochMillis(), item.dateCreatedEpochMs)

        // A listing without the DateCreated projection maps to null — the
        // NEW-badge predicate reads "never eligible".
        val bare = BaseItemDto(
            id = UUID.fromString("11111111-1010-4010-8010-101010101010"),
            name = "Projected-less",
            type = BaseItemKind.MOVIE,
        ).toMediaItem()
        assertNull(bare.dateCreatedEpochMs)
    }
}
