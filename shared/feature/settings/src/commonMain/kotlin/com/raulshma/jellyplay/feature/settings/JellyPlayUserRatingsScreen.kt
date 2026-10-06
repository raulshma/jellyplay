package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.network.api.JellyPlayUserRating
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.jellyPlayItemTypeIcon
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.localDateFromIsoTimestamp
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.components.shortMonthDayYear
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.jellyplay_ur_empty
import com.raulshma.jellyplay.feature.settings.generated.resources.jellyplay_ur_filter_dislikes
import com.raulshma.jellyplay.feature.settings.generated.resources.jellyplay_ur_filter_likes
import com.raulshma.jellyplay.feature.settings.generated.resources.jellyplay_ur_filter_rated
import com.raulshma.jellyplay.feature.settings.generated.resources.jellyplay_ur_last_played
import com.raulshma.jellyplay.feature.settings.generated.resources.jellyplay_ur_disliked
import com.raulshma.jellyplay.feature.settings.generated.resources.jellyplay_ur_liked
import com.raulshma.jellyplay.feature.settings.generated.resources.jellyplay_ur_plays
import com.raulshma.jellyplay.feature.settings.generated.resources.jellyplay_ur_rating_value
import com.raulshma.jellyplay.feature.settings.generated.resources.jellyplay_ur_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The JellyPlay companion-plugin's "My ratings" screen (ADR 0010) — the
 * user's synced likes / dislikes / ratings, one fetch per filter tab, tapping
 * a row opens the item's detail. Deliberately minimal: the existing
 * [SettingListItem] rows carry title / indicator / play facts, and the filter
 * tabs are the settings module's [SingleChoiceSegmentedButtonRow] idiom
 * (the Arr/Seerr settings precedent) over the plugin's
 * `userratings/mine?filter=` wire values.
 *
 * Reachability IS the gate — this screen is only navigated to from the
 * settings root's capability-gated entry (plugin AVAILABLE +
 * `user-ratings` feature key); the ViewModel still re-checks the feature key
 * before each api call.
 */
@Composable
fun JellyPlayUserRatingsScreen(
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    viewModel: JellyPlayUserRatingsViewModel = koinViewModel(),
) {
    val backgroundColorState = rememberScreenBackgroundColorState()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Freshness on open: one pull per visit (the messages screen's idiom) —
    // tab selects refetch through [viewModel.selectFilter].
    LaunchedEffect(Unit) { viewModel.refresh() }

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.jellyplay_ur_title),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                JellyPlayUserRatingsFilter.entries.forEachIndexed { index, filter ->
                    SegmentedButton(
                        selected = state.filter == filter,
                        onClick = { viewModel.selectFilter(filter) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index,
                            JellyPlayUserRatingsFilter.entries.size,
                        ),
                    ) {
                        Text(stringResource(filter.labelRes()))
                    }
                }
            }

            // Not-loading guard: the empty-state text must not flash during the
            // open-fetch (isLoading defaults true until the first load lands).
            if (!state.isLoading && state.ratings.isEmpty()) {
                Text(
                    text = stringResource(Res.string.jellyplay_ur_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(state.ratings, key = { _, rating -> rating.itemId }) { _, rating ->
                        UserRatingRow(
                            rating = rating,
                            onClick = { onOpenItem(rating.itemId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRatingRow(
    rating: JellyPlayUserRating,
    onClick: () -> Unit,
) {
    // Locals, not member reads inside the when: a cross-module Int?/Boolean?
    // property cannot smart-cast into the format arg.
    val ratingValue = rating.rating
    val liked = rating.likes
    SettingListItem(
        icon = jellyPlayItemTypeIcon(rating.itemType),
        title = rating.name,
        subtitle = ratingSubtitleParts(rating).joinToString(" • "),
        // The rating itself rides the trailing edge (the unread-badge slot):
        // "9/10" for a rated item, the like word when only a like/dislike
        // exists, nothing otherwise.
        trailingText = when {
            ratingValue != null -> stringResource(Res.string.jellyplay_ur_rating_value, ratingValue)
            liked == true -> stringResource(Res.string.jellyplay_ur_liked)
            liked == false -> stringResource(Res.string.jellyplay_ur_disliked)
            else -> null
        },
        onClick = onClick,
    )
}

/** The row's factual line: the play count (when any) then the last-played date. */
@Composable
private fun ratingSubtitleParts(rating: JellyPlayUserRating): List<String> = buildList {
    if (rating.playCount > 0) {
        add(pluralStringResource(Res.plurals.jellyplay_ur_plays, rating.playCount, rating.playCount))
    }
    val lastPlayed = rating.lastPlayedDate
        ?.let { localDateFromIsoTimestamp(it) }
        ?.let { shortMonthDayYear(it) }
    if (lastPlayed != null) {
        add(stringResource(Res.string.jellyplay_ur_last_played, lastPlayed))
    }
}

/** The tab's label resource (the one place the enum meets its strings). */
@Composable
private fun JellyPlayUserRatingsFilter.labelRes() = when (this) {
    JellyPlayUserRatingsFilter.Likes -> Res.string.jellyplay_ur_filter_likes
    JellyPlayUserRatingsFilter.Dislikes -> Res.string.jellyplay_ur_filter_dislikes
    JellyPlayUserRatingsFilter.Rated -> Res.string.jellyplay_ur_filter_rated
}
