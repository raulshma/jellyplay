package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.home.testutil.FakeTimeSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Genre rail: the VM-scoped TTL cache (refetch only past the sub-call
 * TTL window, failure hides, identity-keyed so a previous user's list is
 * never served), the blank-name filter, and the route-construction parity
 * with the detail screen's genre chips.
 */
class HomeGenreRailTest {

    private val libraryApiClient: LibraryApiClient = mockk(relaxed = true)
    private val clock = FakeTimeSource(nowMs = 1_000_000L)

    private fun buildCache() = HomeGenreRailCache(
        libraryApiClient = libraryApiClient,
        clock = clock,
    )

    private fun stubGenres(vararg names: String, failure: Boolean = false) {
        coEvery { libraryApiClient.getGenres(parentId = null, startIndex = 0, limit = HomeGenreRailCache.GENRE_RAIL_LIMIT) } returns
            if (failure) {
                Result.failure(IllegalStateException("offline"))
            } else {
                Result.success(names.map { Genre(id = "g_$it", name = it) })
            }
    }

    @Test
    fun cacheAnswersWithinTtl_withoutRefetching() = runTest {
        stubGenres("Drama", "Action")
        val cache = buildCache()

        assertEquals(
            listOf(Genre("g_Drama", "Drama"), Genre("g_Action", "Action")),
            cache.genres(userId = "u1"),
        )

        // Well inside the 2-minute sub-call window: served from the cache,
        // the network client is still called exactly once.
        clock.nowMs += HomeGenreRailCache.GENRE_RAIL_TTL_MS - 1
        assertEquals(
            listOf(Genre("g_Drama", "Drama"), Genre("g_Action", "Action")),
            cache.genres(userId = "u1"),
        )
        coVerify(exactly = 1) { libraryApiClient.getGenres(any(), any(), any()) }
    }

    @Test
    fun expiredCache_refetches() = runTest {
        stubGenres("Drama")
        val cache = buildCache()
        cache.genres(userId = "u1")

        clock.nowMs += HomeGenreRailCache.GENRE_RAIL_TTL_MS + 1
        stubGenres("Drama", "Comedy")
        val refreshed = cache.genres(userId = "u1")

        assertEquals(
            listOf(Genre("g_Drama", "Drama"), Genre("g_Comedy", "Comedy")),
            refreshed,
        )
        coVerify(exactly = 2) { libraryApiClient.getGenres(any(), any(), any()) }
    }

    @Test
    fun failedFetch_returnsEmpty_railHides() = runTest {
        stubGenres(failure = true)

        val genres = buildCache().genres(userId = "u1")

        assertTrue(genres.isEmpty())
    }

    @Test
    fun blankNames_filtered() = runTest {
        coEvery { libraryApiClient.getGenres(any(), any(), any()) } returns Result.success(
            listOf(Genre("g1", "Drama"), Genre("g2", "  "), Genre("g3", "")),
        )

        val genres = buildCache().genres(userId = "u1")

        assertEquals(listOf(Genre("g1", "Drama")), genres)
    }

    @Test
    fun limitCapsTheFetch_atTwentyFour() = runTest {
        stubGenres("Drama")

        buildCache().genres(userId = "u1")

        coVerify {
            libraryApiClient.getGenres(parentId = null, startIndex = 0, limit = 24)
        }
        assertEquals(24, HomeGenreRailCache.GENRE_RAIL_LIMIT)
    }

    @Test
    fun userSwitch_isAGuaranteedMiss_previousUsersListNeverServed() = runTest {
        // The CacheIdentity contract: a user-scoped TTL cache must never hand
        // user A's entry to user B, even well inside the TTL window.
        stubGenres("Drama")
        val cache = buildCache()
        assertEquals(
            listOf(Genre("g_Drama", "Drama")),
            cache.genres(userId = "u1"),
        )

        clock.nowMs += HomeGenreRailCache.GENRE_RAIL_TTL_MS - 1
        stubGenres("Comedy")
        assertEquals(
            listOf(Genre("g_Comedy", "Comedy")),
            cache.genres(userId = "u2"),
        )
        coVerify(exactly = 2) { libraryApiClient.getGenres(any(), any(), any()) }

        // u2's fresh entry answers u2 from the cache; u1 misses again.
        clock.nowMs += HomeGenreRailCache.GENRE_RAIL_TTL_MS - 2
        assertEquals(
            listOf(Genre("g_Comedy", "Comedy")),
            cache.genres(userId = "u2"),
        )
        coVerify(exactly = 2) { libraryApiClient.getGenres(any(), any(), any()) }
        stubGenres("Drama")
        assertEquals(
            listOf(Genre("g_Drama", "Drama")),
            cache.genres(userId = "u1"),
        )
        coVerify(exactly = 3) { libraryApiClient.getGenres(any(), any(), any()) }
    }

    @Test
    fun failedRefetchForNewUser_neverServesTheOldUsersEntry() = runTest {
        stubGenres("Drama")
        val cache = buildCache()
        cache.genres(userId = "u1")

        stubGenres(failure = true)
        assertTrue(cache.genres(userId = "u2").isEmpty())

        // And the failed fetch didn't overwrite u1's entry either.
        stubGenres("Drama")
        clock.nowMs += HomeGenreRailCache.GENRE_RAIL_TTL_MS + 1
        assertEquals(
            listOf(Genre("g_Drama", "Drama")),
            cache.genres(userId = "u1"),
        )
    }

    // ── Route-construction parity with the detail screen's genre chips ────

    @Test
    fun homeGenreRoute_matchesDetailChipConstruction() {
        // The detail screen's DetailChipRow onClick builds
        // Route.LibrarySection(title = genre, genre = genre); the home rail
        // must construct the exact same route.
        assertEquals(
            Route.LibrarySection(title = "Drama", genre = "Drama"),
            homeGenreRoute("Drama"),
        )
        assertEquals(
            Route.LibrarySection(title = "Science Fiction", genre = "Science Fiction"),
            homeGenreRoute("Science Fiction"),
        )
    }
}
