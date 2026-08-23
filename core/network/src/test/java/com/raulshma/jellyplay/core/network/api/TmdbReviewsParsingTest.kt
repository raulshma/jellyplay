package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.network.seerr.SeerrApiClientImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Parses [parseTmdbReviews] against realistic TMDB `/reviews` payloads — the
 * same lenient Json configuration the production client uses.
 */
class TmdbReviewsParsingTest {

    private val json = SeerrApiClientImpl.lenientJson

    @Test
    fun `parses reviews with author details and snake_case fields`() {
        val text = """
            {
              "id": 550,
              "page": 1,
              "results": [
                {
                  "author": "Author One",
                  "author_details": {
                    "name": "Author One",
                    "username": "author1",
                    "avatar_path": "/abc.jpg",
                    "rating": 8.0
                  },
                  "content": "A flawless execution of the source material.",
                  "created_at": "2020-01-01T00:00:00.000Z",
                  "id": "5f0000000000000000000001",
                  "url": "https://www.themoviedb.org/review/5f0000000000000000000001",
                  "updated_at": "2020-01-02T00:00:00.000Z",
                  "iso_639_1": "en"
                },
                {
                  "author": "Second Reviewer",
                  "author_details": {
                    "name": "",
                    "username": "user2",
                    "avatar_path": null,
                    "rating": null
                  },
                  "content": "It was fine.",
                  "created_at": "2021-05-05T10:20:30.000Z",
                  "id": "5f0000000000000000000002"
                }
              ],
              "total_pages": 1,
              "total_results": 2,
              "unknown_top_level": true
            }
        """.trimIndent()

        val reviews = parseTmdbReviews(json, text)

        assertEquals(2, reviews.size)
        val first = reviews[0]
        assertEquals("5f0000000000000000000001", first.id)
        assertEquals("Author One", first.author)
        assertEquals("Author One", first.authorDetails.name)
        assertEquals("author1", first.authorDetails.username)
        assertEquals("/abc.jpg", first.authorDetails.avatarPath)
        assertEquals(8.0, first.authorDetails.rating!!, 0.0)
        assertEquals("A flawless execution of the source material.", first.content)
        assertEquals("2020-01-01T00:00:00.000Z", first.createdAt)
        assertEquals("https://www.themoviedb.org/review/5f0000000000000000000001", first.url)
        // The minimal review keeps defaults for everything the payload omits.
        val second = reviews[1]
        assertEquals("Second Reviewer", second.author)
        assertEquals("user2", second.authorDetails.username)
        assertNull(second.authorDetails.rating)
        assertNull(second.authorDetails.avatarPath)
        assertNull(second.url)
    }

    @Test
    fun `empty results list parses to an empty list`() {
        val reviews = parseTmdbReviews(json, """{"page":1,"results":[],"total_pages":1,"total_results":0}""")

        assertEquals(emptyList<Any>(), reviews)
    }
}
