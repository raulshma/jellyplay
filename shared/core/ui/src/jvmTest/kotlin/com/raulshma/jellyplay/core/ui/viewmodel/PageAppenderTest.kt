package com.raulshma.jellyplay.core.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Decision-table pins for [PageAppender], the append-page core both paged
 * sites route through (Requests' page navigation, admin Stats Detail's
 * `loadMore`). The interleaving these decisions produce at each site is
 * pinned by the per-site suites: `RequestsViewModelPagingInterleaveTest`
 * and `UserStatisticsDetailViewModelTest`.
 */
class PageAppenderTest {

    @Test
    fun next_page_advances_by_one_when_free_and_more_exists() {
        assertEquals(2, PageAppender.nextPageOrNull(currentPage = 1, inFlight = false, hasMore = true))
        assertEquals(5, PageAppender.nextPageOrNull(currentPage = 4, inFlight = false, hasMore = true))
    }

    @Test
    fun an_in_flight_load_suppresses_the_append() {
        assertNull(PageAppender.nextPageOrNull(currentPage = 1, inFlight = true, hasMore = true))
    }

    @Test
    fun a_terminal_pager_suppresses_the_append() {
        assertNull(PageAppender.nextPageOrNull(currentPage = 3, inFlight = false, hasMore = false))
    }

    @Test
    fun in_flight_and_terminal_together_still_suppress() {
        assertNull(PageAppender.nextPageOrNull(currentPage = 3, inFlight = true, hasMore = false))
    }

    @Test
    fun skip_math_is_one_based_and_scales_with_page_size() {
        assertEquals(0, PageAppender.skipForPage(page = 1, pageSize = 10))
        assertEquals(10, PageAppender.skipForPage(page = 2, pageSize = 10))
        assertEquals(20, PageAppender.skipForPage(page = 3, pageSize = 10))
        assertEquals(45, PageAppender.skipForPage(page = 10, pageSize = 5))
    }
}
