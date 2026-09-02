package com.raulshma.jellyplay.core.network.seerr

import com.raulshma.jellyplay.core.model.seerr.SeerrAuthJellyfinRequest
import com.raulshma.jellyplay.core.model.seerr.SeerrAuthLocalRequest
import com.raulshma.jellyplay.core.model.seerr.SeerrCurrentUser
import com.raulshma.jellyplay.core.model.seerr.SeerrEditRequestPayload
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestListResponse
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestPayload
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse
import com.raulshma.jellyplay.core.model.seerr.SeerrStatusResponse
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the wasm Seerr client's DECODE + ENCODE wire contract. The JVM impl
 * has no intermediate DTOs on this seam — it decodes the commonMain
 * `core.model.seerr` types straight through its lenient Json and encodes the
 * request payloads with the SAME instance, so the pins here are exactly the
 * bytes that cross the wire:
 *  - decode: unknown keys ignored, unquoted scalars tolerated, null inputs
 *    coerced to field defaults (the `coerceInputValues` leg of
 *    `SeerrApiClientImpl.lenientJson`).
 *  - encode: kotlinx's default `encodeDefaults = false` — every null field
 *    and the never-set `is4k=false` are OMITTED from POST/PUT bodies,
 *    byte-parity with what the OkHttp client sends.
 */
class SeerrModelWireTest {

    private val json = arrSeerrWireJson

    // ── decode ──────────────────────────────────────────────────────────────

    @Test
    fun `status response decodes with unknown keys and coerced nulls`() {
        // "version": null exercises coerceInputValues — the JVM lenient Json
        // coerces the null to the field default "" instead of failing.
        val status = json.decodeFromString<SeerrStatusResponse>(
            """{"version":null,"commitTag":"master","updateAvailable":true,"commitsBehind":3,"versionTag":1}""",
        )
        assertEquals("", status.version)
        assertEquals("master", status.commitTag)
        assertEquals(true, status.updateAvailable)
        assertEquals(3, status.commitsBehind)
    }

    @Test
    fun `search response decodes result items with defaults for absent fields`() {
        val response = json.decodeFromString<SeerrSearchResponse>(
            """
            {"page":1,"totalPages":12,"totalResults":234,"results":[
               {"id":550,"mediaType":"movie","title":"Fight Club","posterPath":"/p.jpg",
                "voteAverage":8.4,"genreIds":[18,53],"mediaInfo":{"id":9,"status":5}},
               {"id":1396,"mediaType":"tv","name":"Breaking Bad"},
               {"id":7,"mediaType":"movie","title":"Unknown Movie","voteAverage":"8.1"}
            ]}
            """.trimIndent(),
        )
        assertEquals(12, response.totalPages)
        assertEquals(3, response.results.size)
        val movie = response.results[0]
        assertEquals(550, movie.id)
        assertEquals("Fight Club", movie.displayName)
        assertEquals(listOf(18, 53), movie.genreIds)
        assertEquals(5, movie.mediaInfo?.status)
        // Absent fields fall back to the model defaults, same as OkHttp+Kotlinx on JVM.
        assertEquals(false, movie.adult)
        assertEquals(null, response.results[1].title)
        assertEquals("Breaking Bad", response.results[1].displayName)
        // isLenient: an unquoted number-as-string scalar decodes instead of failing.
        assertEquals(8.1f, response.results[2].voteAverage)
    }

    @Test
    fun `requests page decodes the envelope with nested seasons and users`() {
        val page = json.decodeFromString<SeerrRequestListResponse>(
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
        assertEquals(3, page.pageInfo.pages)
        assertEquals(57, page.pageInfo.results)
        val item = page.results[0]
        assertEquals(11, item.id)
        assertEquals(2, item.status)
        assertEquals(81189, item.media.tvdbId)
        assertEquals("alice", item.requestedBy.username)
        assertEquals(1, item.seasons.single().seasonNumber)
        assertTrue(item.canRemove)
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
