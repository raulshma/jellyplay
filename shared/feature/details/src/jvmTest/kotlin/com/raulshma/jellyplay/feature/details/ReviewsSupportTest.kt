package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.seerr.TmdbReview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the reviews preview/all split: the detail screen's reviews section
 * renders [REVIEWS_PREVIEW_COUNT] preview cards and the "All reviews (N)"
 * button only when the fetched list hides reviews behind the preview — the
 * count N is the FULL fetched size (the Route.Reviews screen shows every
 * review the detail screen's take-limited leg cached).
 */
class ReviewsSupportTest {

    private fun review(id: String) = TmdbReview(id = id, author = "Author $id", content = "Body $id")

    @Test
    fun `preview is capped at the constant and the button count is the full size`() {
        val reviews = (1..5).map { review("r$it") }
        val split = tmdbReviewsPreviewSplit(reviews)
        assertEquals(listOf("r1", "r2"), split.preview.map { it.id })
        assertEquals(5, split.allCount)
    }

    @Test
    fun `no button when the list fits the preview exactly`() {
        val reviews = (1..REVIEWS_PREVIEW_COUNT).map { review("r$it") }
        val split = tmdbReviewsPreviewSplit(reviews)
        assertEquals(REVIEWS_PREVIEW_COUNT, split.preview.size)
        assertNull(split.allCount)
    }

    @Test
    fun `no button below the preview count`() {
        val split = tmdbReviewsPreviewSplit(listOf(review("r1")))
        assertEquals(1, split.preview.size)
        assertNull(split.allCount)
    }

    @Test
    fun `empty list yields an empty split with no button`() {
        val split = tmdbReviewsPreviewSplit(emptyList())
        assertTrue(split.preview.isEmpty())
        assertNull(split.allCount)
    }
}
