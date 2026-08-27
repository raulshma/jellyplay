package com.raulshma.jellyplay.core.network.arr

import com.raulshma.jellyplay.core.model.arr.ArrDownloadStatus
import com.raulshma.jellyplay.core.model.arr.ArrMediaType
import com.raulshma.jellyplay.core.network.seerr.arrSeerrWireJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the wasm Radarr/Sonarr wire DTOs' decode behavior and the wire→model
 * mappers against the jvmShared `RadarrApiClientImpl` / `SonarrApiClientImpl`
 * behavior they substitute for (field-for-field, including every fallback:
 * "Unknown" titles, poster remoteUrl-then-url preference, the progress math,
 * the nested quality.language walk, and the hardcoded Sonarr wanted
 * `monitored = true`). Decoding runs through the same lenient Json
 * configuration the JVM impls use (`SeerrApiClientImpl.lenientJson`'s twin).
 */
class ArrWireMapperTest {

    private val json = arrSeerrWireJson

    // ── Radarr queue ────────────────────────────────────────────────────────

    @Test
    fun `radarr queue envelope decodes and maps with movie identity`() {
        val resp = json.decodeFromString<RadarrQueueResponse>(
            """
            {"page":1,"pageSize":10,"totalRecords":1,
             "records":[{
               "id":101,"downloadId":"guid-abc","size":1073741824.0,"sizeleft":268435456.0,
               "timeleft":"00:12:34","status":"downloading","trackedDownloadStatus":"ok",
               "trackedDownloadState":"downloading","protocol":"torrent","downloadClient":"qBittorrent",
               "indexer":"Prowlarr","outputPath":"/data/movies/Fight Club (1999)",
               "quality":{"quality":{"id":7,"name":"HDTV-1080p"}},
               "languages":[{"name":"English"}],
               "customFormats":[{"name":"HDR"}],
               "statusMessages":[{"title":"iTunes","messages":["Download failed: not found"]}],
               "movie":{"id":9,"title":"Fight Club","tmdbId":550,"monitored":true,"hasFile":false,
                        "movieFileId":0,"inCinemas":"1999-10-15","images":[]},
               "UnknownFutureField":1
             }]}
            """.trimIndent(),
        )
        assertEquals(1, resp.records.size)
        val item = resp.records[0].toArrQueueItem()
        assertEquals(101, item.queueId)
        assertEquals("guid-abc", item.downloadId)
        assertEquals(550, item.tmdbId)
        assertEquals("Fight Club", item.title)
        assertEquals(ArrDownloadStatus.DOWNLOADING, item.status)
        assertEquals("ok", item.trackedDownloadStatus)
        // progress = (size - sizeleft) / size = 0.75.
        assertEquals(0.75f, item.progress)
        assertEquals(1073741824L, item.sizeBytes)
        assertEquals(268435456L, item.sizeLeft)
        assertEquals("HDTV-1080p", item.quality)
        assertEquals(listOf("English"), item.languages)
        assertEquals(listOf("HDR"), item.customFormats)
        assertEquals(listOf("Download failed: not found" to "iTunes"), item.messages.map { it.message to it.title })
        assertTrue(item.needsAttention, "a statusMessage row marks the queue item as needing attention")
    }

    @Test
    fun `radarr queue row without movie falls back to Unknown title and zero progress`() {
        val item = json.decodeFromString<RadarrQueueResponse>("""{"records":[{"id":5,"status":"queued"}]}""")
            .records[0].toArrQueueItem()
        assertEquals("Unknown", item.title)
        assertNull(item.tmdbId)
        assertEquals(0f, item.progress, "null size/sizeleft guard → 0")
        assertEquals(ArrDownloadStatus.QUEUED, item.status)
    }

    @Test
    fun `radarr queue progress coerces into the zero-one band`() {
        val item = json.decodeFromString<RadarrQueueResponse>(
            """{"records":[{"id":6,"size":10.0,"sizeleft":-5.0,"status":"downloading"}]}""",
        ).records[0].toArrQueueItem()
        assertEquals(1f, item.progress, "(10 - -5)/10 = 1.5 coerced to 1")
    }

    @Test
    fun `tracked download state drives the collapsed status`() {
        fun statusOf(jsonBody: String) = json.decodeFromString<RadarrQueueResponse>(jsonBody)
            .records[0].toArrQueueItem().status
        assertEquals(
            ArrDownloadStatus.IMPORTED,
            statusOf("""{"records":[{"id":1,"status":"completed","trackedDownloadState":"imported"}]}"""),
        )
        assertEquals(
            ArrDownloadStatus.FAILED,
            statusOf("""{"records":[{"id":1,"status":"download","trackedDownloadStatus":"error"}]}"""),
        )
        assertEquals(
            ArrDownloadStatus.WARNING,
            statusOf("""{"records":[{"id":1,"status":"completed","trackedDownloadStatus":"warning"}]}"""),
        )
        assertEquals(
            ArrDownloadStatus.COMPLETED,
            statusOf("""{"records":[{"id":1,"status":"completed"}]}"""),
        )
    }

    // ── Radarr calendar / wanted / blocklist / history / command ────────────

    @Test
    fun `radarr calendar row picks digital then physical then cinematic date and prefers remote poster`() {
        val rows = json.decodeFromString<List<RadarrMovieResource>>(
            """
            [{"id":9,"title":"Fight Club","tmdbId":550,"monitored":true,"hasFile":false,
              "inCinemas":"1999-10-15","digitalRelease":"2026-09-01","physicalRelease":"2026-10-01",
              "overview":"meh",
              "images":[{"coverType":"fanart","remoteUrl":"http://x/f.jpg"},
                        {"coverType":"poster","remoteUrl":null,"url":"/Media/9/poster"}]},
             {"id":10,"title":"B","tmdbId":551,"monitored":false,"hasFile":true,
              "physicalRelease":"2026-08-01","images":[]}]
            """.trimIndent(),
        )
        val first = rows[0].toCalendarItem()
        assertEquals("2026-09-01", first.airDateUtc, "digital beats physical beats cinematic")
        assertEquals("/Media/9/poster", first.posterPath, "poster row: null remoteUrl falls back to url; fanart row skipped")
        assertEquals(ArrMediaType.MOVIE, first.mediaType)
        assertEquals(550, first.tmdbId)
        val second = rows[1].toCalendarItem()
        assertEquals("2026-08-01", second.airDateUtc, "physical used when digital is absent")
        assertNull(second.posterPath, "no poster cover → null")
    }

    @Test
    fun `radarr wanted keeps the movie's own monitored flag and poster preference`() {
        val rows = json.decodeFromString<RadarrWantedResponse>(
            """
            {"records":[{"id":9,"title":"Fight Club","tmdbId":550,"monitored":true,"hasFile":false,
                         "digitalRelease":"2026-09-01",
                         "images":[{"coverType":"poster","remoteUrl":"http://abs/p.jpg","url":"/rel/p.jpg"}]}]}
            """.trimIndent(),
        )
        val wanted = rows.records[0].toArrWantedItem()
        assertEquals(true, wanted.monitored, "Radarr wanted carries the movie's monitored flag (unlike Sonarr's hardcoded true)")
        assertEquals("http://abs/p.jpg", wanted.posterPath, "absolute remoteUrl preferred over relative url")
        assertEquals(ArrMediaType.MOVIE, wanted.mediaType)
    }

    @Test
    fun `radarr blocklist and history map movie identity with Unknown fallback`() {
        val block = json.decodeFromString<RadarrBlocklistResponse>(
            """{"records":[{"id":3,"date":"2026-01-01","protocol":"torrent","indexer":"Prowlarr",
                             "message":"rejected","movie":{"id":9,"title":"Fight Club","tmdbId":550}}]}""",
        ).records[0].toArrBlocklistItem()
        assertEquals(550, block.tmdbId)
        assertEquals("Fight Club", block.title)
        assertEquals("rejected", block.message)

        val history = json.decodeFromString<RadarrHistoryResponse>(
            """{"records":[{"id":77,"eventType":"grabbed","date":"2026-02-02",
                             "data":{"indexer":"Prowlarr","releaseGroup":"EMU"},
                             "movie":{"id":9,"title":"Fight Club","tmdbId":550}}]}""",
        ).records[0].toArrHistoryItem()
        assertEquals(77, history.historyId)
        assertEquals("grabbed", history.eventType)
        assertEquals(550, history.tmdbId)
        assertEquals("Prowlarr", history.data["indexer"])
        assertEquals(
            "Unknown",
            json.decodeFromString<RadarrHistoryResponse>("""{"records":[{"id":1}]}""")
                .records[0].toArrHistoryItem().title,
        )
    }

    @Test
    fun `radarr command maps with queued-before-started-before-ended date preference`() {
        val cmd = json.decodeFromString<RadarrCommandResource>(
            """{"id":42,"name":"SearchMovie","status":"queued","message":null,
                "queued":"2026-01-01T00:00:00Z","started":null,"ended":null}""",
        ).toArrCommand()
        assertEquals(42, cmd.id)
        assertEquals("SearchMovie", cmd.name)
        assertEquals("2026-01-01T00:00:00Z", cmd.dateUtc)
        assertEquals(false, cmd.isCompleted)
    }

    // ── Sonarr queue / calendar / wanted / episodes ──────────────────────────

    @Test
    fun `sonarr queue title joins series and episode and falls back to Unknown`() {
        val mapped = json.decodeFromString<SonarrQueueResponse>(
            """
            {"records":[
               {"id":201,"series":{"id":4,"title":"Breaking Bad","tvdbId":81189},
                "episode":{"id":60,"title":"Pilot"}},
               {"id":202,"series":{"id":4,"title":"Breaking Bad"},
                "episode":{"id":61,"title":"   "}},
               {"id":203,"episode":null}
            ]}
            """.trimIndent(),
        ).records.map { it.toArrQueueItem() }
        assertEquals("Breaking Bad - Pilot", mapped[0].title)
        assertEquals("Breaking Bad - ", mapped[1].title, "an episode object (even blank-titled) still appends the separator — verbatim JVM string building")
        assertEquals("Unknown", mapped[2].title, "no series + no episode → Unknown")
        assertEquals(81189, mapped[0].tvdbId)
    }

    @Test
    fun `sonarr calendar row reads the series sub-object with monitored defaulting true`() {
        val rows = json.decodeFromString<List<SonarrEpisodeResource>>(
            """
            [{"id":60,"title":"Pilot","airDateUtc":"2026-01-20T00:00:00Z","hasFile":true,
              "overview":"the start","series":{"id":4,"title":"Breaking Bad","tvdbId":81189,
              "monitored":true,"path":"/tv/bb",
              "images":[{"coverType":"poster","remoteUrl":"http://abs/bb.jpg","url":"/rel/bb.jpg"}]}},
             {"id":61,"title":"","series":null}]
            """.trimIndent(),
        )
        val first = rows[0].toCalendarItem()
        assertEquals(81189, first.tvdbId)
        assertEquals("Breaking Bad", first.title, "series title wins over episode title")
        assertEquals(ArrMediaType.SERIES, first.mediaType)
        assertEquals(true, first.monitored)
        assertEquals("http://abs/bb.jpg", first.posterPath)
        val orphan = rows[1].toCalendarItem()
        assertEquals("Unknown", orphan.title, "blank episode title with no series → Unknown")
        assertEquals(true, orphan.monitored, "series?.monitored ?: true")
    }

    @Test
    fun `sonarr wanted hardcodes monitored true like the JVM mapper`() {
        val row = json.decodeFromString<SonarrWantedResponse>(
            """{"records":[{"id":61,"title":"","airDateUtc":"2026-01-27T00:00:00Z",
                             "series":{"id":4,"title":"Breaking Bad","tvdbId":81189}}]}""",
        ).records[0].toArrWantedItem()
        assertEquals("Breaking Bad", row.title, "series title ?: raw title ?: Unknown")
        assertEquals(true, row.monitored, "the JVM mapper passes literal true here")
        assertEquals(81189, row.tvdbId)
        assertEquals(ArrMediaType.SERIES, row.mediaType)
    }

    @Test
    fun `sonarr episode lookup maps into the redownload contract`() {
        val row = json.decodeFromString<SonarrEpisodeLookupResource>(
            """{"id":60,"seasonNumber":1,"episodeNumber":1,"episodeFileId":0,
                "hasFile":false,"monitored":true,"series":{}}""",
        )
        assertEquals(
            SonarrEpisodeInfo(id = 60, episodeFileId = 0, hasFile = false, monitored = true, seasonNumber = 1),
            row.toSonarrEpisodeInfo(),
        )
    }

    @Test
    fun `sonarr managed episode maps file size and quality with blank-title fallback`() {
        val row = json.decodeFromString<SonarrManagedEpisodeResource>(
            """
            {"id":62,"seasonNumber":2,"episodeNumber":7,"absoluteEpisodeNumber":13,
             "title":"","airDateUtc":"2026-03-03T00:00:00Z","overview":"o","hasFile":true,
             "monitored":false,"episodeFileId":88,
             "episodeFile":{"id":88,"size":2147483648.0,
                            "quality":{"quality":{"name":"WEBDL-1080p"}}},
             "UnknownFutureField":1}
            """.trimIndent(),
        )
        val episode = row.toArrSeriesEpisode()
        assertEquals("Episode 7", episode.title)
        assertEquals(13, episode.absoluteEpisodeNumber)
        assertEquals(88, episode.episodeFileId)
        assertEquals(2147483648L, episode.fileSizeBytes)
        assertEquals("WEBDL-1080p", episode.quality)
        assertEquals(true, episode.hasDownload)
    }

    @Test
    fun `sonarr series rows filter by their OWN tvdbId - server param is untrusted`() {
        val rows = json.decodeFromString<List<SonarrSeriesResource>>(
            """
            [{"id":1,"title":"Wrong Show","tvdbId":999},
             {"id":2,"title":"Right Show","tvdbId":81189,"monitored":true,"path":"/tv/right"},
             {"id":3,"title":"Untracked","tvdbId":null}]
            """.trimIndent(),
        )
        val match = filterSeriesByTvdb(rows, 81189)
        assertEquals(2, match?.id)
        assertEquals("Right Show", match?.title)
        assertEquals("/tv/right", match?.path)
        assertNull(filterSeriesByTvdb(rows, 1), "no row carries tvdbId 1 → null, never a wrong match")
    }

    @Test
    fun `sonarr command maps with the same queued-first date preference`() {
        val cmd = json.decodeFromString<SonarrCommandResource>(
            """{"id":7,"name":"EpisodeSearch","status":"completed",
                "queued":"2026-01-01T00:00:00Z","started":"2026-01-01T00:00:05Z"}""",
        ).toArrCommand()
        assertEquals("2026-01-01T00:00:00Z", cmd.dateUtc)
        assertEquals(true, cmd.isCompleted)
        assertEquals(false, cmd.isFailed)
    }
}
