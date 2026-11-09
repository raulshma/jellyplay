package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.seerr.TmdbImageUrls
import com.raulshma.jellyplay.core.model.seerr.TmdbReview
import com.raulshma.jellyplay.core.model.formatFixed
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.DelayedLoadingScreen
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.TopBarStyle
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusGroupRestorer
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_review_rating
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_reviews
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The full TMDB reviews screen — the "All reviews (N)" destination of the
 * detail screen's preview section. The [CastAndCrewScreen] shape: a lightweight
 * scaffold whose VM rides the cached detail + TMDB seams (see
 * [ReviewsViewModel]), rendering the same review-card vocabulary the detail
 * body previews, uncapped.
 */
@Composable
fun ReviewsScreen(
    itemId: String,
    onBack: () -> Unit,
    viewModel: ReviewsViewModel = koinViewModel(),
) {
    LaunchedEffect(itemId) { viewModel.load(itemId) }

    val state = viewModel.uiState.collectAsStateWithLifecycle()

    // TV focus-on-launch: the cards are the screen's only content, so grab the
    // list once reviews land. On TV the cards are focus targets too — without
    // one the list cannot be D-pad-scrolled and the screen strands focus.
    val reviews = (state.value as? ReviewsUiState.Success)?.reviews.orEmpty()
    val listFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = reviews.size,
        tag = "reviews_init",
    )

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.detail_section_reviews),
        onBack = onBack,
        topBarStyle = TopBarStyle.Collapsing,
    ) { padding ->
        when (val current = state.value) {
            ReviewsUiState.Loading -> DelayedLoadingScreen(modifier = Modifier.padding(padding))
            is ReviewsUiState.Error -> ErrorScreen(
                message = current.message,
                onRetry = { viewModel.load(itemId) },
                modifier = Modifier.padding(padding),
            )
            is ReviewsUiState.Success -> {
                val adaptiveInfo = com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo.current
                val isTv = LocalTvMode.current
                val contentPad = adaptiveInfo.contentPadding(isTv)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .tvFocusGroupRestorer()
                        .focusRequester(listFocusRequester),
                    contentPadding = PaddingValues(
                        start = contentPad,
                        end = contentPad,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + adaptiveInfo.bottomPadding(isTv),
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        count = current.reviews.size,
                        key = { index -> current.reviews[index].id.ifEmpty { "review_$index" } },
                        contentType = { "review" },
                    ) { index ->
                        // Informational cards are still focus targets on TV:
                        // the focus ring lets a long review list be scrolled
                        // to the end with the D-pad.
                        val reviewFocusState = rememberTvFocusState(focusedScale = 1.01f)
                        TmdbReviewCard(
                            review = current.reviews[index],
                            modifier = if (isTv) {
                                Modifier
                                    .then(reviewFocusState.focusModifier)
                                    .tvFocusIndicator(reviewFocusState, ShapeCache.smooth16)
                                    .focusable()
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
        }
    }
}

/** TMDB avatar URL for the 40dp review circle (w90 covers it at 2x density). */
private fun tmdbAvatarUrl(avatarPath: String?): String? =
    avatarPath?.takeIf { it.isNotBlank() }?.let { path ->
        // Gravatar-linked accounts ship "/https://…" — the path is already an
        // absolute URL, so it must not get the image.tmdb.org prefix.
        if (path.startsWith("/http")) path.drop(1) else "${TmdbImageUrls.BASE}/w90$path"
    }

/**
 * One TMDB review card — the shared vocabulary of the detail body's preview
 * section and this screen (the split; the card body moved here verbatim from
 * the former detail-local `ReviewsSection` so the two surfaces cannot drift).
 * Informational only: no click. [modifier] lets the full screen add a TV
 * focus target so long lists stay D-pad scrollable. [contentMaxLines] caps the
 * review body — the detail preview passes the former 6-line cap; the full
 * screen renders whole.
 */
@Composable
internal fun TmdbReviewCard(
    review: TmdbReview,
    contentMaxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
) {
    // Display name falls back username-first: TMDB often ships an
    // empty author_details.name for casual reviewers.
    val authorName = review.authorDetails.name.takeIf { it.isNotBlank() }
        ?: review.authorDetails.username.takeIf { it.isNotBlank() }
        ?: review.author
    val avatarUrl = remember(review.id) { tmdbAvatarUrl(review.authorDetails.avatarPath) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarUrl != null) {
                    MediaImage(
                        url = avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = authorName.take(1).uppercase().ifBlank { "?" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column {
                Text(
                    text = authorName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                review.authorDetails.rating?.let { rating ->
                    Text(
                        text = stringResource(Res.string.detail_review_rating, formatFixed(rating, 1)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            text = review.content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            maxLines = contentMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
