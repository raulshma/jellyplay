package com.raulshma.jellyplay.feature.library

import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayedStatus
import com.raulshma.jellyplay.core.model.SortOption
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round-trip + legacy-format tests for the now-directly-serializable
 * [LibraryFilters]. These pin two things:
 *
 * 1. The on-disk wire format is unchanged after deleting `SavedLibraryFilters`
 *    (enums serialise by Kotlin `.name`, exactly what the String mirror stored).
 * 2. The decode resilience boundary (unknown enum → fallback) holds, since
 *    `Json { ignoreUnknownKeys = true }` does not suppress unknown enum
 *    constants — that path stays wrapped in try/catch at the call site.
 */
class LibraryFiltersSerializationTest {

    // Mirrors the private `libraryJson` in LibraryViewModel.kt — same flags.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `round-trips a fully populated LibraryFilters`() {
        val original = LibraryFilters(
            mediaTypes = listOf(MediaType.MOVIE, MediaType.EPISODE),
            genres = listOf("Action", "Sci-Fi"),
            years = listOf(2020, 2021),
            sortBy = SortOption.YEAR_DESC,
            playedStatus = PlayedStatus.UNPLAYED,
            tags = listOf("fav"),
            minRating = 4.5f,
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<LibraryFilters>(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `decodes the legacy SavedLibraryFilters wire format`() {
        // Exact JSON shape the deleted String-typed mirror used to emit:
        // enum fields stored as their .name, everything else as-is.
        // `encodeDefaults` on the mirror guaranteed all 7 keys were present,
        // but decode must also tolerate any subset (older backups).
        val legacy = """
            {
              "mediaTypes": ["MOVIE", "EPISODE"],
              "genres": ["Drama"],
              "years": [2019],
              "sortBy": "SORT_NAME",
              "playedStatus": "ALL",
              "tags": [],
              "minRating": 0.0
            }
        """.trimIndent()

        val decoded = json.decodeFromString<LibraryFilters>(legacy)

        assertEquals(
            LibraryFilters(
                mediaTypes = listOf(MediaType.MOVIE, MediaType.EPISODE),
                genres = listOf("Drama"),
                years = listOf(2019),
                sortBy = SortOption.SORT_NAME,
                playedStatus = PlayedStatus.ALL,
                tags = emptyList(),
                minRating = 0f,
            ),
            decoded,
        )
    }

    @Test
    fun `omitted fields fall back to the data-class defaults`() {
        val decoded = json.decodeFromString<LibraryFilters>("{}")

        assertEquals(LibraryFilters(), decoded)
    }

    @Test
    fun `unknown enum constant throws, which the call site catches`() {
        // kotlinx.serialization does not coerce unknown enum names even with
        // ignoreUnknownKeys = true. LibraryViewModel.selectFolder relies on a
        // surrounding try/catch to fall back to LibraryFilters() — this test
        // documents that contract by asserting the raw decode throws.
        var threw = false
        try {
            json.decodeFromString<LibraryFilters>("""{"sortBy":"NOPE"}""")
        } catch (_: Exception) {
            threw = true
        }
        assertEquals(true, threw)
    }

    @Test
    fun `unknown object keys are ignored on decode`() {
        // Forward-compatibility: a future field added to LibraryFilters and
        // then removed again must not poison older decoders.
        val decoded = json.decodeFromString<LibraryFilters>(
            """{"sortBy":"RATING","futureField":42}""",
        )

        assertEquals(SortOption.RATING, decoded.sortBy)
    }
}
