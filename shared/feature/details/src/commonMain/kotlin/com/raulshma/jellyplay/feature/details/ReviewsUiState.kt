package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.seerr.TmdbReview

/**
 * UI state for the Reviews screen (D9). Three-state sealed model mirroring
 * [CastAndCrewUiState] — avoids nullable field reads and the separate
 * `isLoading` flag the old per-field pattern forced.
 */
@Immutable
sealed interface ReviewsUiState {
    data object Loading : ReviewsUiState
    data class Success(
        val title: String,
        val reviews: List<TmdbReview>,
    ) : ReviewsUiState
    data class Error(val message: String) : ReviewsUiState
}

/**
 * The detail screen's Reviews section split (D9): the preview cards shown
 * inline plus the "All reviews (N)" overflow. [preview] is the first
 * [PREVIEW_COUNT] entries; [allCount] is the full fetched size, null when the
 * button must NOT render — i.e. when every review is already visible (N ≤
 * [PREVIEW_COUNT], including the empty list). Pure → directly unit-testable
 * (the [partitionCastAndCrew] convention).
 */
internal data class ReviewsPreviewSplit(
    val preview: List<TmdbReview>,
    val allCount: Int?,
)

/**
 * The [ReviewsPreviewSplit] fold over the fetched reviews. The detail screen
 * renders [ReviewsPreviewSplit.preview] cards and, when
 * [ReviewsPreviewSplit.allCount] is non-null, the "All reviews (N)" button
 * that opens the full [Route.Reviews] screen.
 */
internal fun tmdbReviewsPreviewSplit(reviews: List<TmdbReview>): ReviewsPreviewSplit =
    ReviewsPreviewSplit(
        preview = reviews.take(REVIEWS_PREVIEW_COUNT),
        allCount = reviews.size.takeIf { it > REVIEWS_PREVIEW_COUNT },
    )

/** Preview cards the detail screen's reviews section shows inline. */
internal const val REVIEWS_PREVIEW_COUNT = 2
