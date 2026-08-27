package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.network.seerr.arrSeerrWireJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the wasm TMDB wire DTOs + mappers against the jvmShared
 * `TmdbApiClientImpl` behavior: snake_case review decoding, the YouTube-only
 * watch-URL synthesis (case-insensitive site match), and the results-list
 * unwrapping through the shared lenient Json.
 */
class TmdbWireDtoTest {

    private val json = arrSeerrWireJson

    @Test
    fun `videos response decodes and synthesizes youtube urls only`() {
        val response = json.decodeFromString<TmdbVideosResponseWire>(
            """
            {"id":550,"results":[
               {"key":"qtRKdVHc-cE","name":"Trailer","size":1080,"type":"Trailer","site":"YouTube"},
               {"key":"abc123","name":"Clip","size":720,"type":"Clip","site":"youtube"},
               {"key":"vimeo1","name":"Featurette","size":480,"type":"Featurette","site":"Vimeo"},
               {"name":"No key","size":0}
            ]}
            """.trimIndent(),
        )
        val videos = response.results.map { it.toSeerrRelatedVideo() }
        assertEquals(4, videos.size)
        assertEquals("https://www.youtube.com/watch?v=qtRKdVHc-cE", videos[0].url)
        assertEquals("https://www.youtube.com/watch?v=abc123", videos[1].url, "site match is case-insensitive")
        assertNull(videos[2].url, "non-YouTube sites keep url null")
        assertNull(videos[3].key)
        assertNull(videos[3].site)
        assertEquals(0, videos[3].size)
        assertEquals(1080, videos[0].size)
        assertEquals("Trailer", videos[0].type)
    }

    @Test
    fun `reviews response decodes snake_case fields with defaults`() {
        val reviews = parseTmdbReviewsWire(
            """
            {"id":550,"page":1,"results":[
               {"id":"rev1","author":"critic","author_details":{"name":"Critic","username":"critic1",
                 "avatar_path":"/a.jpg","rating":7.5},
                "content":"great","created_at":"2026-01-01T00:00:00Z","url":"http://r/1",
                "UnknownFutureField":true},
               {"id":"rev2","author":"anon","content":"ok"}
            ]}
            """.trimIndent(),
        )
        assertEquals(2, reviews.size)
        val first = reviews[0]
        assertEquals("rev1", first.id)
        assertEquals("critic", first.author)
        assertEquals("Critic", first.authorDetails.name)
        assertEquals("critic1", first.authorDetails.username)
        assertEquals("/a.jpg", first.authorDetails.avatarPath)
        assertEquals(7.5, first.authorDetails.rating)
        assertEquals("great", first.content)
        assertEquals("2026-01-01T00:00:00Z", first.createdAt)
        // Missing author_details falls back to the model default, like the JVM decode.
        assertEquals("", reviews[1].authorDetails.username)
        assertNull(reviews[1].authorDetails.rating)
    }

    @Test
    fun `empty results decode to an empty list`() {
        assertEquals(0, parseTmdbReviewsWire("""{"results":[]}""").size)
        assertEquals(0, json.decodeFromString<TmdbVideosResponseWire>("""{"results":[]}""").results.size)
    }
}
