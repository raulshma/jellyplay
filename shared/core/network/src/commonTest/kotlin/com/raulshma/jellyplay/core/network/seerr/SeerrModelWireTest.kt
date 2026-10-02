package com.raulshma.jellyplay.core.network.seerr

import com.raulshma.jellyplay.core.model.seerr.SeerrAuthJellyfinRequest
import com.raulshma.jellyplay.core.model.seerr.SeerrAuthLocalRequest
import com.raulshma.jellyplay.core.model.seerr.SeerrCurrentUser
import com.raulshma.jellyplay.core.model.seerr.SeerrEditRequestPayload
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaStatus
import com.raulshma.jellyplay.core.model.seerr.SeerrReleaseDateType
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestPayload
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestStatus
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the Seerr client's DECODE + ENCODE wire contract after the DTO↔model
 * seam split: the JVM impl decodes THIS package's internal wire DTOs
 * ([WireSeerrSearchResponse], [WireSeerrRequestListResponse], …) through its
 * lenient Json and maps to the core/model read models at the seam, so the
 * decode pins here run against the WIRE types and the fold/interpret
 * assertions run against their `toModel()` tails — exactly the bytes and the
 * mapping that cross the seam:
 *  - decode: unknown keys ignored, unquoted scalars tolerated, null inputs
 *    coerced to field defaults (the `coerceInputValues` leg of
 *    `SeerrApiClientImpl.lenientJson`).
 *  - interpret: raw status ints → [SeerrMediaStatus]/[SeerrRequestStatus]
 *    (unmapped folds), envelopes folded (search page → items, request page →
 *    items + flattened totals, `releases.results` → the flat region list),
 *    wire-named region columns renamed (`iso_3166_1`/`flatrate`/
 *    `release_dates`), TMDB `genreIds` dropped at the seam.
 *  - encode: kotlinx's default `encodeDefaults = false` — every null field
 *    and the never-set `is4k=false` are OMITTED from POST/PUT bodies,
 *    byte-parity with what the OkHttp client sends.
 */
class SeerrModelWireTest {

    private val json = arrSeerrWireJson

    // ── decode ──────────────────────────────────────────────────────────────

    @Test
    fun `status response decodes with unknown keys and coerced nulls`() {
        // The status payload carries no interpreting edge — the read model
        // IS the wire shape here, decoded straight through.
        // "version": null exercises coerceInputValues — the lenient Json
        // coerces the null to the field default "" instead of failing.
        val status = json.decodeFromString<com.raulshma.jellyplay.core.model.seerr.SeerrStatusResponse>(
            """{"version":null,"commitTag":"master","updateAvailable":true,"commitsBehind":3,"versionTag":1}""",
        )
        assertEquals("", status.version)
        assertEquals("master", status.commitTag)
        assertEquals(true, status.updateAvailable)
        assertEquals(3, status.commitsBehind)
    }

    @Test
    fun `search response decodes the wire envelope and folds to items with interpreted media status`() {
        val response = json.decodeFromString<WireSeerrSearchResponse>(
            """
            {"page":1,"totalPages":12,"totalResults":234,"results":[
               {"id":550,"mediaType":"movie","title":"Fight Club","posterPath":"/p.jpg",
                "voteAverage":8.4,"genreIds":[18,53],"mediaInfo":{"id":9,"status":5}},
               {"id":1396,"mediaType":"tv","name":"Breaking Bad",
                "mediaInfo":{"id":10,"status":999,"requests":[{"id":1,"status":2}]}},
               {"id":7,"mediaType":"movie","title":"Unknown Movie","voteAverage":"8.1"}
            ]}
            """.trimIndent(),
        )
        assertEquals(12, response.totalPages)
        assertEquals(3, response.results.size)
        val items = response.toModels()
        assertEquals(3, items.size)
        val movie = items[0]
        assertEquals(550, movie.id)
        assertEquals("Fight Club", movie.displayName)
        // genreIds are TMDB wire vocabulary — they do NOT cross the seam;
        // the raw 5 interpreted to AVAILABLE at the mapper tail.
        assertEquals(SeerrMediaStatus.AVAILABLE, movie.mediaInfo?.status)
        // The unmapped 999 folds to UNKNOWN at the mapper tail, and the nested
        // request's status int interprets to APPROVED.
        assertEquals(SeerrMediaStatus.UNKNOWN, items[1].mediaInfo?.status)
        assertEquals(SeerrRequestStatus.APPROVED, items[1].mediaInfo?.requests?.single()?.status)
        // Absent fields fall back to the model defaults, same as OkHttp+Kotlinx on JVM.
        assertEquals(false, movie.adult)
        assertEquals(null, items[1].title)
        assertEquals("Breaking Bad", items[1].displayName)
        // isLenient: an unquoted number-as-string scalar decodes instead of failing.
        assertEquals(8.1f, items[2].voteAverage)
    }

    @Test
    fun `requests page decodes the envelope and folds to the page read model`() {
        val page = json.decodeFromString<WireSeerrRequestListResponse>(
            """
            {"pageInfo":{"pages":3,"results":57},"results":[
               {"id":11,"status":2,"type":"tv","createdAt":"2026-01-01T00:00:00.000Z",
                "media":{"id":5,"tmdbId":1396,"tvdbId":81189,"status":3,"status4k":0},
                "requestedBy":{"id":2,"email":"a@b.c","username":"alice","permissions":2},
                "is4k":false,"canRemove":true,"seasons":[{"id":91,"seasonNumber":1}],
                "UnknownFutureField":{"x":1}}
            ]}
            """.trimIndent(),
        )
        val model = page.toModel()
        // The fold: items + flattened totals (no nested pageInfo object).
        assertEquals(3, model.totalPages)
        assertEquals(57, model.totalResults)
        val item = model.items.single()
        assertEquals(11, item.id)
        assertEquals(SeerrRequestStatus.APPROVED, item.status)
        // 4k=0 on the media columns folds to UNKNOWN (a live edge on non-4K servers).
        assertEquals(SeerrMediaStatus.PROCESSING, item.media.status)
        assertEquals(SeerrMediaStatus.UNKNOWN, item.media.status4k)
        assertEquals(81189, item.media.tvdbId)
        assertEquals("alice", item.requestedBy.username)
        assertEquals(1, item.seasons.single().seasonNumber)
        assertTrue(item.canRemove)
    }

    @Test
    fun `movie details decode folds the watch-provider and release-date region families`() {
        // The wire's region vocabulary decodes raw here — snake_case
        // `iso_3166_1`, `flatrate`, the `releases.results` envelope, the
        // `release_date`/`type` columns — and the mapper tail renames/folds/
        // interprets into the clean read models.
        val details = json.decodeFromString<WireSeerrMovieDetails>(
            """
            {"id":550,"title":"Fight Club",
             "watchProviders":[
               {"iso_3166_1":"US","link":"https://www.themoviedb.org/watch/US",
                "flatrate":[{"id":8,"name":"HBO Max","logoPath":"/hbo.jpg","displayPriority":1}],
                "buy":[{"id":2,"name":"Apple TV"}],"rent":[]},
               {"iso_3166_1":"DE","link":"https://www.themoviedb.org/watch/DE","flatrate":[]}],
             "releases":{"results":[
               {"iso_3166_1":"US","release_dates":[
                  {"certification":"R","release_date":"1999-10-15T00:00:00.000Z","type":3,"note":"theatrical"},
                  {"certification":"","release_date":"2001-06-11T00:00:00.000Z","type":4},
                  {"certification":"","release_date":"2002-01-01","type":99}]},
               {"iso_3166_1":"DE","release_dates":[
                  {"certification":"16","release_date":"1999-11-25","type":3}]}]}
            }
            """.trimIndent(),
        )
        val model = details.toModel()
        // Watch-provider regions: the wire flatrate column lands on the read
        // model's streaming list; buy/rent pass through untouched.
        assertEquals(2, model.watchProviders.size)
        val us = model.watchProviders.single { it.iso31661 == "US" }
        assertEquals("HBO Max", us.streaming.single().name)
        assertEquals("/hbo.jpg", us.streaming.single().logoPath)
        assertEquals(1, us.streaming.single().displayPriority)
        assertEquals("Apple TV", us.buy.single().name)
        assertEquals(0, us.rent.size)
        assertEquals(0, model.watchProviders.single { it.iso31661 == "DE" }.streaming.size)
        // Release-date regions: the releases.results envelope folds to the
        // flat region list, release_date columns keep their wire values.
        assertEquals(2, model.releases.size)
        val usReleases = model.releases.single { it.iso31661 == "US" }
        assertEquals("1999-10-15T00:00:00.000Z", usReleases.releaseDates[0].releaseDate)
        assertEquals("theatrical", usReleases.releaseDates[0].note)
        // The raw type ints interpret at the mapper tail; the unmapped 99
        // folds to UNKNOWN.
        assertEquals(
            listOf(SeerrReleaseDateType.THEATRICAL, SeerrReleaseDateType.DIGITAL, SeerrReleaseDateType.UNKNOWN),
            usReleases.releaseDates.map { it.type },
        )
        assertEquals("16", model.releases.single { it.iso31661 == "DE" }.releaseDates.single().certification)
    }

    @Test
    fun `current user decodes permissions as a long bitmask`() {
        // 16386 = 2 (ADMIN) + 16384 (REQUEST_VIEW): isAdmin short-circuits
        // every can* branch to true.
        val admin = json.decodeFromString<SeerrCurrentUser>(
            """{"id":1,"email":"admin@x","username":"root","permissions":16386,"userType":1}""",
        )
        assertEquals(true, admin.isAdmin)
        assertEquals(true, admin.canViewRequests)
        assertEquals(true, admin.canManageRequests)
        // 16400 = 16384 (REQUEST_VIEW) + 16 (MANAGE_REQUESTS), no ADMIN bit —
        // the individual permission branches decide.
        val viewer = json.decodeFromString<SeerrCurrentUser>(
            """{"id":2,"email":"v@x","permissions":16400}""",
        )
        assertEquals(false, viewer.isAdmin)
        assertEquals(true, viewer.canViewRequests)
        assertEquals(true, viewer.canManageRequests)
        assertEquals(false, viewer.canRequestAdvanced, "8192 (REQUEST_ADVANCED) not set")
    }

    // ── encode (POST/PUT bodies must be byte-identical to the JVM wire) ─────

    @Test
    fun `request media body omits null fields and the false is4k`() {
        // JVM: postAndParse(..., SeerrRequestPayload(mediaType, mediaId, tvdbId,
        // seasons, serverId, profileId, rootFolder, tags)) through
        // encodeDefaults=false — only the set fields reach the wire.
        assertEquals(
            """{"mediaType":"movie","mediaId":550}""",
            json.encodeToString(
                SeerrRequestPayload(
                    mediaType = "movie", mediaId = 550, tvdbId = null, seasons = null,
                    serverId = null, profileId = null, rootFolder = null, tags = null,
                ),
            ),
        )
        assertEquals(
            """{"mediaType":"tv","mediaId":1396,"seasons":[1,2],"serverId":1,"profileId":2,"rootFolder":"/data","tags":[7]}""",
            json.encodeToString(
                SeerrRequestPayload(
                    mediaType = "tv", mediaId = 1396, tvdbId = null, seasons = listOf(1, 2),
                    serverId = 1, profileId = 2, rootFolder = "/data", tags = listOf(7),
                ),
            ),
        )
    }

    @Test
    fun `edit request body omits null fields and keeps JVM field order`() {
        assertEquals(
            """{"mediaType":"movie","mediaId":42}""",
            json.encodeToString(
                SeerrEditRequestPayload(
                    mediaType = "movie", mediaId = 42, serverId = null, profileId = null,
                    rootFolder = null, tags = null, seasons = null,
                ),
            ),
        )
    }

    @Test
    fun `auth bodies encode both credential payloads verbatim`() {
        assertEquals(
            """{"username":"joey","password":"s3cret"}""",
            json.encodeToString(SeerrAuthJellyfinRequest(username = "joey", password = "s3cret")),
        )
        assertEquals(
            """{"email":"joey@x.y","password":"pw"}""",
            json.encodeToString(SeerrAuthLocalRequest(email = "joey@x.y", password = "pw")),
        )
    }
}
