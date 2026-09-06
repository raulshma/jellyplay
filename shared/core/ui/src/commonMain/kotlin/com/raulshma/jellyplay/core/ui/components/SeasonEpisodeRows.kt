package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronDown
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.defaultContentSizeSpec
import com.raulshma.jellyplay.core.designsystem.theme.defaultSpatialSpring
import com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.detail_cd_collapse
import com.raulshma.jellyplay.core.ui.generated.resources.detail_cd_expand
import org.jetbrains.compose.resources.stringResource

/**
 * Episode title + runtime labels shared by the season-scoped episode lists in
 * [DeleteDownloadedEpisodesSheet] and [SeriesDownloadSheet]: both render an
 * `E# Name` body line with an `Nm` runtime under it. The caller passes the
 * title color so each sheet keeps its own selected/downloaded tinting while
 * the text shape itself can't drift between the two.
 */
@Composable
fun RowScope.SeasonEpisodeMetaLabels(episode: MediaItem, nameColor: Color) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = buildString {
                episode.episodeNumber?.let { append("E$it. ") }
                append(episode.name)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = nameColor,
        )
        episode.runTimeTicks?.let { ticks ->
            val minutes = ticks / 600_000_000
            Text(
                text = "${minutes}m",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Per-sheet tint bundle for [SeasonEpisodeRow]: the delete sheet paints
 * selections with the error scheme, the download sheet with primary (queued)
 * or tertiary (already downloaded) containers. Bundled so the triple travels
 * as one value through [seasonEpisodeItems] instead of three loose params.
 */
class SeasonEpisodeRowTints(
    val selectedContainerColor: Color,
    val nameColor: Color,
    val checkboxColors: CheckboxColors,
)

/**
 * Episode row chassis shared by the season lists in
 * [DeleteDownloadedEpisodesSheet] and [SeriesDownloadSheet]: horizontal inset,
 * expressive-list shape, selected-background color animation, checkbox, and
 * [SeasonEpisodeMetaLabels]. The caller passes a [SeasonEpisodeRowTints]
 * bundle so each sheet keeps its own selected/downloaded tinting while the
 * row shape itself can't drift between the two. [enabled] gates both the row
 * click and the checkbox (locked downloaded rows pass false);
 * [trailingContent] slots per-sheet extras after the labels (e.g. the
 * "Downloaded" status tag).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SeasonEpisodeRow(
    episode: MediaItem,
    index: Int,
    count: Int,
    selected: Boolean,
    tints: SeasonEpisodeRowTints,
    onToggle: () -> Unit,
    enabled: Boolean = true,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    // The horizontal inset replaces the padding a Column around the episode
    // list used to apply; the LazyColumn's 4 dp item spacing supplies the rest.
    Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp)) {
        val shape = expressiveListShape(
            index = index,
            count = count,
            outerRadius = 14.dp,
            innerRadius = 8.dp,
        )

        val episodeBgColor by animateColorAsState(
            targetValue = if (selected) tints.selectedContainerColor else Color.Transparent,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "epBg",
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(episodeBgColor)
                .clickable(enabled = enabled, onClick = onToggle)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = null,
                enabled = enabled,
                colors = tints.checkboxColors,
            )
            Spacer(Modifier.width(8.dp))
            SeasonEpisodeMetaLabels(episode = episode, nameColor = tints.nameColor)
            trailingContent?.invoke(this)
        }
    }
}

/**
 * Season header row shared by the season lists in
 * [DeleteDownloadedEpisodesSheet] and [SeriesDownloadSheet]: tri-state
 * checkbox, title, optional subtitle, and the rotation-animated expand
 * chevron on an animateContentSize column. The caller passes the expanded
 * container color so each sheet keeps its own selected/downloaded tinting
 * while the header chassis itself can't drift between the two.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SeasonHeaderRow(
    title: String,
    subtitle: String?,
    triState: ToggleableState,
    isExpanded: Boolean,
    expandedContainerColor: Color,
    onHeaderClick: () -> Unit,
    onCheckboxClick: () -> Unit,
    checkboxEnabled: Boolean = true,
) {
    val seasonBgColor by animateColorAsState(
        targetValue = if (isExpanded) expandedContainerColor
        else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "seasonBg",
    )

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = defaultSpatialSpring(),
        label = "chevron",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = defaultContentSizeSpec()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ShapeCache.smooth16)
                .background(seasonBgColor)
                .clickable(onClick = onHeaderClick)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TriStateCheckbox(
                state = triState,
                onClick = onCheckboxClick,
                enabled = checkboxEnabled,
            )
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = Tabler.Outline.ChevronDown,
                contentDescription = stringResource(if (isExpanded) Res.string.detail_cd_collapse else Res.string.detail_cd_expand),
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = chevronRotation },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Emits the keyed, content-typed season header item shared by the season
 * lists in [DeleteDownloadedEpisodesSheet] and [SeriesDownloadSheet],
 * pinning the "season-{id}" key + "season" contentType of the key family
 * ("season-{id}", "season-{id}-ep-…", "season-{id}-divider") so both lists
 * scroll-preserve the same way. The header body stays a per-sheet slot:
 * selection tri-state and subtitle counts must be read inside the item's
 * composable scope, so each sheet composes its own [SeasonHeaderRow] there.
 */
fun LazyListScope.seasonHeaderItem(
    seasonId: String,
    content: @Composable () -> Unit,
) {
    item(key = "season-$seasonId", contentType = "season") { content() }
}

/**
 * Per-season closing divider shared by the season lists in
 * [DeleteDownloadedEpisodesSheet] and [SeriesDownloadSheet], keyed and
 * content-typed identically so both lists scroll-preserve the same way.
 */
fun LazyListScope.seasonDividerItem(seasonId: String) {
    item(key = "season-$seasonId-divider", contentType = "divider") {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        )
    }
}

/**
 * Emits one keyed, content-typed [SeasonEpisodeRow] per episode of an
 * expanded season, wiring the season-scoped key scheme ("season-{id}-ep-…")
 * and the row invocation both sheets share so an expanded 100+ episode
 * season composes only its visible rows and the keys can't drift between the
 * two lists. Keys are season-scoped because a sheet may keep several seasons
 * expanded at once. Per-episode lambdas supply each sheet's own selection,
 * tinting (downloaded rows lock + re-tint), enabled state, and trailing
 * extras.
 */
fun LazyListScope.seasonEpisodeItems(
    seasonId: String,
    episodes: List<MediaItem>,
    selected: (episodeId: String) -> Boolean,
    tints: @Composable (episode: MediaItem) -> SeasonEpisodeRowTints,
    onToggle: (episodeId: String) -> Unit,
    enabled: (episodeId: String) -> Boolean = { true },
    trailingContent: (@Composable RowScope.(episode: MediaItem) -> Unit)? = null,
) {
    itemsIndexed(
        episodes,
        key = { _, episode -> "season-$seasonId-ep-${episode.id}" },
        contentType = { _, _ -> "episode" },
    ) { index, episode ->
        val episodeTints = tints(episode)
        SeasonEpisodeRow(
            episode = episode,
            index = index,
            count = episodes.size,
            selected = selected(episode.id),
            tints = episodeTints,
            onToggle = { onToggle(episode.id) },
            enabled = enabled(episode.id),
            trailingContent = trailingContent?.let { content -> { content(episode) } },
        )
    }
}
