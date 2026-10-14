package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.EyeOff
import com.composables.icons.tabler.outline.PlayerPlay
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.feedback.rememberConfirmHaptic
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cd_hide_collection_row
import com.raulshma.jellyplay.feature.details.generated.resources.detail_play_collection_in_order
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_more_in_collection_format
import com.raulshma.jellyplay.feature.details.generated.resources.detail_see_all
import org.jetbrains.compose.resources.stringResource

/**
 * One D6 "More in {collection}" franchise row: a titled poster row of the
 * collection's members (server-default order, the loaded item filtered out)
 * with three header actions —
 *
 *  - **Play collection in order** (Fider #4214): starts the FIRST UNWATCHED
 *    member ([franchisePlayOrder]) and appends the members after it to the
 *    manual up-next queue via `VideoQueueController.enqueueLast` — the
 *    player drains that queue queue-first, so the collection continues in
 *    order after the start entry. The manual queue is suppressed under
 *    SyncPlay by the player's advance routing (the group's server-side
 *    queue owns the order there — the contract documented on
 *    VideoQueueController itself).
 *  - **Eye-hide**: hides this row for the current item (session memory;
 *    [DetailUiEvent.HideFranchiseRow]) — the Up Next card hide's pattern,
 *    per row instead of one global switch.
 *  - **See All**: the collection detail screen (Route.CollectionDetail).
 */
@Composable
internal fun FranchiseCollectionRow(
    collection: DetailUiState.FranchiseCollection,
    bodyContentPad: Dp,
    onPlayCollectionInOrder: (DetailUiState.FranchiseCollection) -> Unit,
    onHideRow: () -> Unit,
    onSeeAll: () -> Unit,
    onItemClick: (String) -> Unit,
    onItemFocus: (MediaItem) -> Unit,
    getImageUrl: (String) -> String,
) {
    Column {
        FadingItem {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = bodyContentPad),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.detail_section_more_in_collection_format, collection.name),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .semantics { heading() },
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                val playFocusState = rememberTvFocusState(focusedScale = 1.1f)
                val hideFocusState = rememberTvFocusState(focusedScale = 1.1f)
                val confirmHaptic = rememberConfirmHaptic()
                Surface(
                    modifier = Modifier
                        .clip(ShapeCache.smooth16)
                        .then(playFocusState.focusModifier)
                        .then(Modifier.tvFocusIndicator(playFocusState, ShapeCache.smooth16))
                        .clickable {
                            confirmHaptic()
                            onPlayCollectionInOrder(collection)
                        },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = ShapeCache.smooth16,
                ) {
                    Icon(
                        imageVector = Tabler.Outline.PlayerPlay,
                        contentDescription = stringResource(Res.string.detail_play_collection_in_order),
                        modifier = Modifier.padding(8.dp).size(20.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    modifier = Modifier
                        .clip(ShapeCache.smooth16)
                        .then(hideFocusState.focusModifier)
                        .then(Modifier.tvFocusIndicator(hideFocusState, ShapeCache.smooth16))
                        .clickable {
                            confirmHaptic()
                            onHideRow()
                        },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = ShapeCache.smooth16,
                ) {
                    Icon(
                        imageVector = Tabler.Outline.EyeOff,
                        contentDescription = stringResource(Res.string.detail_cd_hide_collection_row),
                        modifier = Modifier.padding(8.dp).size(20.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                val seeAllFocusState = rememberTvFocusState(focusedScale = 1.05f)
                Text(
                    text = stringResource(Res.string.detail_see_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(ShapeCache.smooth16)
                        .then(seeAllFocusState.focusModifier)
                        .then(Modifier.tvFocusIndicator(seeAllFocusState, ShapeCache.smooth16))
                        .clickable(onClick = onSeeAll)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        // Card width resolved once per slot (the DetailMoreLikeThisSection note).
        val adaptiveInfo = LocalAdaptiveInfo.current
        val franchiseCardWidth = if (adaptiveInfo.windowSizeClass != WindowSizeClass.Compact) 200.dp else 160.dp
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            TvFocusableItemRow(
                items = collection.members,
                key = { "franchise_${collection.id}_${it.id}" },
                contentPadding = PaddingValues(horizontal = bodyContentPad),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                onFocusedIndexChange = { index ->
                    collection.members.getOrNull(index)?.let(onItemFocus)
                },
            ) { _, member, focusModifier ->
                val memberClick = remember(member.id) { { onItemClick(member.id) } }
                val memberImageUrl = remember(member.id) { getImageUrl(member.id) }
                PosterCard(
                    item = member,
                    imageUrl = memberImageUrl,
                    onClick = memberClick,
                    modifier = focusModifier.width(franchiseCardWidth),
                )
            }
        }
    }
}
