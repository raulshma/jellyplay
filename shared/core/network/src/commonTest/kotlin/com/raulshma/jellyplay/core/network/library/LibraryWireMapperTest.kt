package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.MediaType
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the Phase W library wire DTOs' PascalCase contract and the
 * DTO→core.model mapping semantics (mirrors the jvmShared JellyfinDtoMappers
 * these tests substitute for), decoded through the same lenient Json the
 * wasm client uses. Field-for-field spot checks per the chunk-2 task list:
 * MediaItem (UserData/ImageTags/series fields), MediaDetail/MediaSource/
 * MediaStream, playlists/collections, parental-rating filter and sort-token
 * parsing.
 */
class LibraryWireMapperTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun movieJson() = """
        {
          "Id": "0b0f2a75-5677-4c76-a416-a1c0d9d11111",
          "Name": "Blade Runner",
          "OriginalTitle": "Blade Runner (Final Cut)",
          "Overview": "A blade runner must pursue.",
          "Type": "Movie",
          "ProductionYear": 1982,
          "CommunityRating": 8.1,
          "OfficialRating": "R",
          "RunTimeTicks": 69820000000,
          "PremiereDate": "1982-06-25T00:00:00.0000000Z",
          "Genres": ["Sci-Fi", "Drama"],
          "Studios": [{"Id": "s1", "Name": "Warner Bros"}, {"Id": "s2"}],
          "Tags": ["sync"],
          "SeriesId": null,
          "ChildCount": null,
          "AlbumArtist": null,
          "NormalizationGain": -3.5,
          "PrimaryImageAspectRatio": 0.6666667,
          "ImageTags": {"Primary": "ptag", "Logo": "ltag"},
          "ImageBlurHashes": {
            "Primary": {"ptag": "hashPrimary"},
            "Backdrop": {"btag": "hashBackdrop"}
          },
          "UserData": {
            "PlaybackPositionTicks": 1200000000,
            "Played": false,
            "IsFavorite": true,
            "PlayCount": 3,
            "LastPlayedDate": "2026-01-02T03:04:05.0000000Z",
            "UnplayedItemCount": 4
          },
          "FutureField": {"ignored": true}
        }
    """.trimIndent()

    @Test
    fun `base item maps field-for-field with all fallbacks`() {
        val item = json.decodeFromString<BaseItemDtoWire>(movieJson()).toMediaItem()

        assertEquals("0b0f2a75-5677-4c76-a416-a1c0d9d11111", item.id)
        assertEquals("Blade Runner", item.name)
        assertEquals("Blade Runner (Final Cut)", item.originalTitle)
        assertEquals(MediaType.MOVIE, item.mediaType)
        assertEquals(1982, item.year)
        assertEquals(8.1f, item.communityRating)
        assertEquals("R", item.officialRating)
        assertEquals(69820000000L, item.runTimeTicks)
        assertEquals(1200000000L, item.playbackPositionTicks)
        assertEquals(false, item.isPlayed)
        assertEquals(true, item.isFavorite)
        assertEquals(3, item.playCount)
        assertEquals("2026-01-02T03:04:05.0000000Z", item.lastPlayedDate)
        assertEquals(4, item.unplayedItemCount)
        // Raw wire string kept (wasm delta vs the SDK zone-shifted format).
        assertEquals("1982-06-25T00:00:00.0000000Z", item.premiereDate)
        assertEquals(listOf("Sci-Fi", "Drama"), item.genres)
        assertEquals(listOf("Warner Bros"), item.studios, "studio without a name is dropped")
        assertEquals(listOf("sync"), item.tags)
        assertEquals("hashPrimary", item.blurHashes.primary)
        assertEquals("hashBackdrop", item.blurHashes.backdrop)
        assertEquals(0.6666667f, item.posterAspectRatio)
        assertEquals(-3.5f, item.normalizationGain)
    }

    @Test
    fun `missing aspect ratio falls back to poster default and unknown type maps to UNKNOWN`() {
        val item = json.decodeFromString<BaseItemDtoWire>(
            """{"Id":"x1"}""",
        ).toMediaItem()
        assertEquals(MediaType.UNKNOWN, item.mediaType)
        assertEquals(2f / 3f, item.posterAspectRatio)
        assertEquals("", item.name, "name fallback")
        assertEquals(0, item.playCount)
        assertNull(item.playbackPositionTicks)
    }

    @Test
    fun `episode season id falls back to parent id`() {
        val episode = json.decodeFromString<BaseItemDtoWire>(
            """{"Id":"e1","Type":"Episode","ParentId":"season-1","SeriesId":"series-9",
               "ParentIndexNumber":2,"IndexNumber":5,"SeriesName":"Show"}""",
        ).toMediaItem()
        assertEquals("season-1", episode.seasonId)
        assertEquals("series-9", episode.seriesId)
        assertEquals(2, episode.seasonNumber)
        assertEquals(5, episode.episodeNumber)
        assertEquals(5, episode.indexNumber)
        assertEquals("Show", episode.seriesName)

        // Explicit SeasonId wins; non-episode types never use the fallback.
        val explicit = json.decodeFromString<BaseItemDtoWire>(
            """{"Id":"e2","Type":"Season","ParentId":"series-9","SeasonId":"s-x"}""",
        ).toMediaItem()
        assertEquals("s-x", explicit.seasonId)
    }

    @Test
    fun `wire item kinds map both ways including aliases`() {
        assertEquals(MediaType.CHANNEL, "TvChannel".toMediaType())
        assertEquals(MediaType.LIVE_TV, "LiveTvProgram".toMediaType())
        assertEquals(MediaType.COLLECTION, "BoxSet".toMediaType())
        assertEquals("MusicAlbum", MediaType.ALBUM.toWireItemKind())
        assertEquals("Audio", MediaType.MUSIC.toWireItemKind())
        assertNull(MediaType.UNKNOWN.toWireItemKind(), "UNKNOWN drops the include filter")
    }

    @Test
    fun `media detail maps people, chapters, sources and trickplay`() {
        val detailJson = """
            {
              "Id": "d1", "Name": "Detail", "Type": "Movie",
              "ForcedSortName": "sort-me", "CustomRating": "CR", "CriticRating": 7.7,
              "Taglines": ["tag"], "ProductionLocations": ["US"],
              "LockData": true, "LockedFields": ["Cast", "Genres"],
              "Status": "Continuing", "AirDays": ["Friday"], "AirTime": "20:00",
              "DisplayOrder": "aired", "PreferredMetadataLanguage": "en",
              "PreferredMetadataCountryCode": "us",
              "DateCreated": "2025-05-01T00:00:00.0000000Z",
              "People": [
                {"Id": "p1", "Name": "A", "Role": "Lead", "Type": "Actor", "PrimaryImageTag": "t1"},
                {"Id": "p1", "Name": "A-duplicate", "Type": "Actor"},
                {"Id": "p2", "Name": "B", "Type": "Director"}
              ],
              "Chapters": [
                {"Name": null, "StartPositionTicks": null,
                 "ImageDateModified": "2025-05-01T01:02:03.0000000Z", "ImageTag": "ctag"}
              ],
              "ExternalUrls": [{"Name": "IMDb", "Url": "https://imdb"}],
              "ProviderIds": {"Tmdb": "123", "Imdb": null, "Tvdb": "999"},
              "MediaSources": [
                {"Id": "ms1", "Name": "4K", "Container": "mkv", "Size": 90000000000,
                 "Bitrate": 40000000, "RunTimeTicks": 70000000000,
                 "SupportsTranscoding": true, "SupportsDirectStream": true,
                 "SupportsDirectPlay": false, "TranscodingUrl": "/transcode?x=1",
                 "LiveStreamId": null, "RequiresOpening": false, "Path": "/mnt/f.mkv",
                 "MediaStreams": [
                   {"Index": 0, "Type": "Video", "Codec": "hevc", "Width": 3840, "Height": 2160,
                    "BitRate": 38000000, "VideoRange": "HDR", "VideoRangeType": "DOVI",
                    "RealFrameRate": 23.976, "IsDefault": true},
                   {"Index": 1, "Type": "Audio", "Codec": "truehd", "Channels": 8, "SampleRate": 48000},
                   {"Index": 2, "Type": "Subtitle", "Codec": "subrip", "IsExternal": true,
                    "DeliveryUrl": "/Videos/d1/ms1/Subtitles/2/Stream.srt"},
                   {"Index": 3, "Type": "EmbeddedImage"}
                 ]}
              ],
              "Trickplay": {
                "ms1": {
                  "320": {"Width": 320, "Height": 180, "TileWidth": 10, "TileHeight": 1,
                          "ThumbnailCount": 62, "Interval": 10000, "Bandwidth": 60307},
                  "960": {"Width": 960}
                }
              }
            }
        """.trimIndent()
        val detail = json.decodeFromString<BaseItemDtoWire>(detailJson).toMediaDetail()

        assertEquals("sort-me", detail.sortName)
        assertEquals("CR", detail.customRating)
        assertEquals(7.7f, detail.criticRating)
        assertEquals(listOf("tag"), detail.taglines)
        assertEquals(true, detail.lockData)
        assertEquals(listOf("Cast", "Genres"), detail.lockedFields)
        assertEquals("Continuing", detail.status)
        assertEquals(listOf("Friday"), detail.airDays)
        assertEquals("20:00", detail.airTime)
        assertEquals(listOf("US"), detail.productionLocations, "raw wire strings, no case folding (JVM parity)")
        assertEquals("2025-05-01T00:00:00.0000000Z", detail.dateCreated)
        assertEquals(2, detail.people.size, "people are distinctBy id")
        assertEquals("A", detail.people[0].name)
        assertEquals("Lead", detail.people[0].role)
        assertEquals("Actor", detail.people[0].type)
        assertEquals("t1", detail.people[0].primaryImageTag)
        assertEquals("", detail.chapters[0].name, "chapter name fallback")
        assertEquals(0L, detail.chapters[0].startPositionTicks, "chapter ticks fallback")
        assertEquals("ctag", detail.chapters[0].imageTag)
        assertEquals(listOf(com.raulshma.jellyplay.core.model.ExternalUrl("IMDb", "https://imdb")), detail.externalUrls)
        assertEquals(mapOf("tmdb" to "123", "tvdb" to "999"), detail.providerIds, "keys lowercased, nulls dropped")

        val source = detail.mediaSources.single()
        assertEquals("ms1", source.id)
        assertEquals("mkv", source.container)
        assertEquals(90000000000L, source.size)
        assertEquals(40000000L, source.bitrate, "int bitrate widened to long")
        assertEquals("/transcode?x=1", source.transcodeUrl)
        assertEquals(false, source.supportsDirectPlay)
        assertEquals(4, source.mediaStreams.size)
        val video = source.mediaStreams[0]
        assertEquals(com.raulshma.jellyplay.core.model.StreamType.VIDEO, video.type)
        assertEquals(3840, video.width)
        assertEquals(38000000L, video.bitRate)
        assertEquals("HDR", video.videoRange)
        assertEquals(23.976f, video.realFrameRate)
        val subtitle = source.mediaStreams[2]
        assertEquals(com.raulshma.jellyplay.core.model.StreamType.SUBTITLE, subtitle.type)
        assertEquals("/Videos/d1/ms1/Subtitles/2/Stream.srt", subtitle.deliveryUrl)
        assertEquals(true, subtitle.isExternal)
        assertEquals(com.raulshma.jellyplay.core.model.StreamType.EMBEDDED_IMAGE, source.mediaStreams[3].type)

        // Trickplay projection: the WIDEST tile set for the source wins.
        val trickplay = source.trickplayInfo!!
        assertEquals(960, trickplay.width)
        assertEquals(180, trickplay.height, "narrow-set fields keep their fallbacks")
        assertEquals(10000, trickplay.interval)
    }

    @Test
    fun `missing trickplay stream fields fall back to sdk defaults`() {
        val trickplay = json.decodeFromString<TrickplayInfoDtoWire>("{}").toTrickplayInfo()
        assertEquals(320, trickplay.width)
        assertEquals(180, trickplay.height)
        assertEquals(10, trickplay.tileWidth)
        assertEquals(1, trickplay.tileHeight)
        assertEquals(0, trickplay.thumbnailCount)
        assertEquals(10000, trickplay.interval)
        assertEquals(0, trickplay.bandwidth)
    }

    @Test
    fun `query result, playlist, playlist item and collection summary map`() {
        val resultJson = """
            {"Items": [
               {"Id": "pl1", "Name": "Road trip", "Overview": "ov", "ChildCount": 7,
                "CanDelete": false, "DateCreated": "2026-02-03T00:00:00.0000000Z",
                "ImageTags": {"Primary": "pt"}},
               {"Id": "t1", "Name": "Track", "Type": "Audio", "PlaylistItemId": "pli1",
                "AlbumArtist": "Artist", "Album": "Album", "RunTimeTicks": 123},
               {"Id": "c1", "Name": "Collection", "ChildCount": 3, "ImageTags": {"Primary": "ct"}}
             ],
             "TotalRecordCount": 3, "StartIndex": 0}
        """.trimIndent()
        val result = json.decodeFromString<BaseItemQueryResultDtoWire>(resultJson)
        assertEquals(3, result.totalRecordCount)

        val playlist = result.items[0].toPlaylist(currentUserId = "u9")
        assertEquals("pl1", playlist.id)
        assertEquals("Road trip", playlist.name)
        assertEquals("ov", playlist.overview)
        assertEquals(7, playlist.itemCount)
        assertEquals("pt", playlist.imageTag)
        assertEquals("u9", playlist.userId)
        assertEquals(false, playlist.canDelete, "CanDelete=false honored")
        assertEquals(false, playlist.isReadOnly, "JVM hardcodes isReadOnly=false")
        assertEquals("2026-02-03T00:00:00.0000000Z", playlist.createdAt)

        val track = result.items[1].toPlaylistItem()
        assertEquals("pli1", track.playlistItemId)
        assertEquals("Artist", track.artist)
        assertEquals("Album", track.album)
        assertEquals(MediaType.AUDIO, track.mediaType)
        assertEquals(123L, track.runTimeTicks)

        val collection = result.items[2].toCollectionSummary()
        assertEquals(3, collection.itemCount)
        assertEquals("ct", collection.imageTag)
    }

    @Test
    fun `playlist artist falls back to first artist item`() {
        val item = json.decodeFromString<BaseItemDtoWire>(
            """{"Id":"t2","Name":"n","AlbumArtist":null,
               "ArtistItems":[{"Id":"a1","Name":"Someone"}]}""",
        ).toPlaylistItem()
        assertEquals("Someone", item.artist)
    }

    @Test
    fun `folder, genre and studio mappings`() {
        val folder = json.decodeFromString<BaseItemDtoWire>(
            """{"Id":"f1","Name":"Movies","CollectionType":"movies","Type":"CollectionFolder"}""",
        ).toLibraryFolder()
        assertEquals(com.raulshma.jellyplay.core.model.LibraryFolder("f1", "Movies", "movies", "CollectionFolder"), folder)

        val genre = json.decodeFromString<BaseItemDtoWire>("""{"Id":"g1","Name":"Sci-Fi"}""").toGenre()
        assertEquals(com.raulshma.jellyplay.core.model.Genre("g1", "Sci-Fi"), genre)

        val studio = json.decodeFromString<BaseItemDtoWire>("""{"Id":"st1","Name":"WB"}""").toStudio()
        assertEquals(com.raulshma.jellyplay.core.model.Studio("st1", "WB"), studio)
    }

    @Test
    fun `parental rating filter mirrors the engine semantics`() {
        val items = listOf(
            movieJson(), // R
            """{"Id":"m2","Name":"Family","Type":"Movie","OfficialRating":"PG"}""",
            """{"Id":"m3","Name":"Unrated","Type":"Movie"}""",
            """{"Id":"m4","Name":"Teen","Type":"Movie","OfficialRating":"TV-14"}""",
        ).map { json.decodeFromString<BaseItemDtoWire>(it).toMediaItem() }

        assertEquals(4, items.filterByParentalRating(null).size, "no max rating = unfiltered")
        assertEquals(listOf("m2", "m3", "m4"), items.filterByParentalRating(13).map { it.id },
            "R (age 17) dropped, TV-14 (age 13) kept")
        assertEquals(
            listOf("0b0f2a75-5677-4c76-a416-a1c0d9d11111", "m2", "m3", "m4"),
            items.filterByParentalRating(17).map { it.id },
        )
        assertEquals(listOf("m3"), items.filterByParentalRating(0).map { it.id },
            "unrated passes even with max 0; every known rating is above 0")
    }

    @Test
    fun `sort tokens parse compound keys and drop unknowns`() {
        assertEquals(
            listOf("ProductionYear", "SortName"),
            parseItemSortList("ProductionYear,SortName"),
        )
        assertEquals(listOf("Random"), parseItemSortList(" Random , bogus-token "))
        assertEquals(emptyList(), parseItemSortList(""))
        // The enum-name aliases the JVM lookup registers still resolve.
        assertEquals(listOf("SortName", "DateCreated"), parseItemSortList("sort_name,DATE_CREATED"))
        assertEquals(listOf("IsFavoriteOrLiked"), parseItemSortList("IsFavoriteOrLiked"))
    }

    @Test
    fun `theme songs and lyric wire shapes decode`() {
        val theme = json.decodeFromString<ThemeMediaResultDtoWire>(
            """{"Items":[{"Id":"t","Name":"Theme","Type":"Audio"}],"TotalRecordCount":1}""",
        )
        assertEquals(1, theme.items.size)

        val lyrics = json.decodeFromString<LyricsDtoWire>(
            """{"Metadata":{},"Lyrics":[
                {"Text":"row one","Start":0,"Cues":[{"Position":0,"EndPosition":7,"Start":0,"End":70}]},
                {"Text":"row two","Start":100}
            ]}""",
        )
        assertEquals(2, lyrics.lyrics.size)
        assertTrue(lyrics.lyrics[0].cues!!.single().end == 70L)
    }
}
