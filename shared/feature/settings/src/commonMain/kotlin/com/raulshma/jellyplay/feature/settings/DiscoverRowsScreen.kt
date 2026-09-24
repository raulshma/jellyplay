package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.DiscoverRowSource
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.tv.CenteredBringIntoView
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.enableMarqueeOnFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_add_discover_row
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_movies
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_tv
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_upcoming_short
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_rows
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_rows_helper
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_rows_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_edit_discover_row_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_move_down_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_move_up_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_remove_section_cd

/**
 * Settings → Appearance → Home Screen Layout → Discover Rows: the manage
 * screen for the user's custom discover rows. List order = render order within
 * the home DISCOVER block; per-row toggle, edit (navigates to the editor),
 * delete; templates on the empty state.
 */
@Composable
fun DiscoverRowsScreen(
    onBack: () -> Unit,
    onEditRow: (rowId: String) -> Unit,
    onAddRow: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: DiscoverRowsViewModel = koinViewModel(),
) {
    val rows by viewModel.discoverRowsFlow.collectAsStateWithLifecycle()
    val isTv = LocalTvMode.current
    val backgroundColorState = rememberScreenBackgroundColorState()
    val adaptiveInfo = LocalAdaptiveInfo.current

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = "discover_rows_init",
    )

    val scrollState = rememberLazyListState()

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.settings_discover_rows),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
    ) { innerPadding ->
        CenteredBringIntoView {
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .tvFocusRestorer()
                    .focusRequester(focusRequester),
                contentPadding = PaddingValues(
                    start = adaptiveInfo.contentPadding(isTv),
                    end = adaptiveInfo.contentPadding(isTv),
                    bottom = adaptiveInfo.bottomPadding(isTv),
                ),
            ) {
                item {
                    SettingsGroup(
                        icon = Tabler.Outline.Compass,
                        title = stringResource(Res.string.settings_discover_rows_title),
                        summary = { stringResource(Res.string.settings_discover_rows_helper) },
                        modifier = Modifier.padding(vertical = 8.dp),
                        initiallyExpanded = true,
                    ) {
                        if (rows.isEmpty()) {
                            DiscoverRowTemplatesPresentation(
                                onUseTemplate = { viewModel.addFromTemplate(it) },
                            )
                        } else {
                            val totalCount = rows.size
                            rows.forEachIndexed { index, row ->
                                DiscoverRowListEntry(
                                    row = row,
                                    position = index + 1,
                                    index = index,
                                    count = totalCount,
                                    onEdit = { onEditRow(row.id) },
                                    onToggle = { viewModel.setDiscoverRowEnabled(row.id, it) },
                                    onMoveUp = { viewModel.moveDiscoverRow(row.id, up = true) },
                                    onMoveDown = { viewModel.moveDiscoverRow(row.id, up = false) },
                                    onRemove = { viewModel.removeDiscoverRow(row.id) },
                                )
                            }
                        }

                        AddDiscoverRowRow(
                            index = rows.size,
                            count = rows.size + 1,
                            highlighted = highlightSettingId == "discover_add",
                            onClick = onAddRow,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverRowListEntry(
    row: DiscoverRowConfig,
    position: Int,
    index: Int,
    count: Int,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = expressiveListShape(index, count, innerRadius = 0.dp)
    val tvFocusState = rememberTvFocusState(focusedScale = 1.01f)

    ListItem(
        headlineContent = {
            Text(
                text = row.title.ifBlank { stringResource(Res.string.settings_discover_rows) },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (row.enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.enableMarqueeOnFocus(focused = tvFocusState.isFocused),
            )
        },
        supportingContent = {
            Text(
                text = "${discoverRowSourceLabel(row.source)} • ${describeDiscoverRow(row, discoverRowSummaryLabels())} • #$position",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = position.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = row.enabled,
                    onCheckedChange = onToggle,
                )
                Spacer(Modifier.width(4.dp))
                DiscoverRowIconButton(
                    icon = Tabler.Outline.ChevronUp,
                    contentDescription = stringResource(Res.string.settings_move_up_cd),
                    enabled = position > 1,
                    onClick = onMoveUp,
                )
                DiscoverRowIconButton(
                    icon = Tabler.Outline.ChevronDown,
                    contentDescription = stringResource(Res.string.settings_move_down_cd),
                    enabled = position < count,
                    onClick = onMoveDown,
                )
                DiscoverRowIconButton(
                    icon = Tabler.Outline.Edit,
                    contentDescription = stringResource(Res.string.settings_edit_discover_row_cd),
                    enabled = true,
                    onClick = onEdit,
                )
                DiscoverRowIconButton(
                    icon = Tabler.Outline.X,
                    contentDescription = stringResource(Res.string.settings_remove_section_cd, row.title),
                    enabled = true,
                    onClick = onRemove,
                    isDestructive = true,
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, shape)
            .clickable(onClick = onEdit),
    )
}

/** One-line human summary of the row's active filters (the list subtitle). [labels] supplies the localized bits. */
internal fun describeDiscoverRow(
    row: DiscoverRowConfig,
    labels: DiscoverRowSummaryLabels,
): String {
    if (row.source == DiscoverRowSource.SEERR) {
        val f = row.seerrFilters
        val media = if (f.media == com.raulshma.jellyplay.core.model.SeerrRowMedia.MOVIE) labels.movies else labels.tv
        val bits = mutableListOf(media)
        if (f.genres.isNotEmpty()) bits += f.genres.joinToString("/") { it.name }
        if (f.upcomingOnly) bits += labels.upcoming
        return bits.joinToString(" • ")
    }
    val f = row.filters
    val bits = mutableListOf<String>()
    if (f.mediaTypes.isNotEmpty()) {
        bits += f.mediaTypes.joinToString("/") { it.name.lowercase().replace('_', ' ') }
    }
    if (f.genres.isNotEmpty()) bits += f.genres.joinToString("/") { it }
    if (f.playedStatus != com.raulshma.jellyplay.core.model.PlayedStatus.ALL) bits += f.playedStatus.displayName
    if (f.minRating > 0f) bits += "★ ${f.minRating}"
    if (row.studios.isNotEmpty()) bits += row.studios.joinToString("/") { it.name }
    if (row.people.isNotEmpty()) bits += row.people.joinToString("/") { it.name }
    row.addedWithinDays?.let { bits += "added ≤ ${it}d" }
    row.premieredWithinYears?.let { bits += "last ${it}y" }
    return bits.joinToString(" • ").ifEmpty { f.sortBy.displayName }
}

/** Localized bits [describeDiscoverRow] interpolates — resolved per composition by [discoverRowSummaryLabels]. */
internal data class DiscoverRowSummaryLabels(
    val movies: String,
    val tv: String,
    val upcoming: String,
)

@Composable
internal fun discoverRowSummaryLabels(): DiscoverRowSummaryLabels = DiscoverRowSummaryLabels(
    movies = stringResource(Res.string.settings_discover_row_movies),
    tv = stringResource(Res.string.settings_discover_row_tv),
    upcoming = stringResource(Res.string.settings_discover_row_upcoming_short),
)

@Composable
private fun DiscoverRowTemplatesPresentation(
    onUseTemplate: (DiscoverRowConfig) -> Unit,
) {
    val templates = listOf(
        Triple(Tabler.Outline.EyeOff, DiscoverRowTemplates::unwatchedMovies, "Unwatched Movies"),
        Triple(Tabler.Outline.Star, DiscoverRowTemplates::highlyRated, "Highly Rated Gems"),
        Triple(Tabler.Outline.CalendarPlus, DiscoverRowTemplates::newThisMonth, "New This Month"),
        Triple(Tabler.Outline.Dice, DiscoverRowTemplates::randomSurprise, "Random Surprise"),
        Triple(Tabler.Outline.TrendingUp, DiscoverRowTemplates::trendingSeerr, "Trending on Seerr"),
    )
    Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
        templates.forEachIndexed { index, (icon, factory, _) ->
            val shape = expressiveListShape(index, templates.size, innerRadius = 0.dp)
            val tvFocusState = rememberTvFocusState(focusedScale = 1.01f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                    .then(tvFocusState.focusModifier)
                    .tvFocusIndicator(tvFocusState, shape)
                    .clickable { onUseTemplate(factory()) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        factory().title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Icon(
                    Tabler.Outline.Plus,
                    contentDescription = stringResource(Res.string.settings_add_discover_row),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun AddDiscoverRowRow(
    index: Int,
    count: Int,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val shape = expressiveListShape(index, count, innerRadius = 0.dp)
    val tvFocusState = rememberTvFocusState(focusedScale = 1.01f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Tabler.Outline.Plus,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = stringResource(Res.string.settings_add_discover_row),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun DiscoverRowIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
) {
    val tvFocusState = rememberTvFocusState(focusedScale = 1.12f)
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        isDestructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(ShapeCache.smooth8)
            .then(if (enabled) tvFocusState.focusModifier else Modifier)
            .then(if (enabled) Modifier.tvFocusIndicator(tvFocusState, ShapeCache.smooth8) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}
