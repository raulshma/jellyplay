package com.raulshma.jellyplay.core.model

import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.Test

/**
 * Round-trip + legacy-format tests for [LibraryFilters].
 *
 * Moved to core/model alongside the type (the type was promoted out of the
 * feature module so [LibraryBrowserState]/[LibraryBrowserReducer] can live in
 * core/model). The on-disk wire format is unchanged from the legacy
 * `SavedLibraryFilters` mirror — enums serialise by Kotlin `.name`, exactly what
 * the String mirror stored.
 */
class LibraryFiltersSerializationTest {

    // Mirrors the `libraryJson` in LibraryViewModel.kt — same flags.
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
            isResumable = true,
            isDownloaded = true,
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<LibraryFilters>(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `decodes the legacy SavedLibraryFilters wire format`() {
        // Exact JSON shape the deleted String-typed mirror used to emit:
        // enum fields stored as their .name, everything else as-is.
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
        // Booleans added after the legacy format (isResumable, isDownloaded)
        // default to "off" when absent from the blob.
        assertEquals(null, decoded.isResumable)
        assertEquals(null, decoded.isDownloaded)
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
        val decoded = json.decodeFromString<LibraryFilters>(
            """{"sortBy":"RATING","futureField":42}""",
        )

        assertEquals(SortOption.RATING, decoded.sortBy)
    }
}
