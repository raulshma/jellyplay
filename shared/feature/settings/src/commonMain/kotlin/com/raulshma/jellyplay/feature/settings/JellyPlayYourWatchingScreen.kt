package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.network.api.JellyPlayAnalyticsDay
import com.raulshma.jellyplay.core.network.api.JellyPlayAnalyticsTopItem
import com.raulshma.jellyplay.core.network.api.JellyPlayMyAnalytics
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.formatDurationApproxSeconds
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.jellyPlayItemTypeIcon
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_yw_days_30
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_yw_days_7
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_yw_days_90
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_yw_empty
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_yw_plays
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_yw_section_chart
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_yw_section_top_items
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_yw_titles
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_yw_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_yw_unavailable
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_yw_watch_time
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.jellyplay_ur_plays
import org.koin.compose.viewmodel.koinViewModel

/**
 * The JellyPlay companion-plugin's "Your watching" screen — the signed-in
 * user's OWN play aggregates (the per-user face of the plugin's analytics;
 * any user, unlike the admin dashboard), one fetch per window chip: the
 * window's roll-up cards (plays / humanized watch time / titles), a
 * plays-per-day bar chart, and the most-played titles ranked.
 *
 * Reachability IS the gate — this screen is only navigated to from the
 * settings root's capability-gated entry (plugin AVAILABLE + the `analytics`
 * feature key + the user's toggle); the ViewModel still re-checks the feature
 * key before each api call. A server whose plugin predates the per-user
 * analytics face 404s the read — the screen degrades to the quiet "not
 * available" line, never an error.
 *
 * The chart is a private minimal Canvas bar composable: the admin module's
 * [com.raulshma.jellyplay.feature.admin.statistics.components.ActivityBarChart]
 * is welded to its admin-local ChartGeometry core + string resources —
 * extracting it to core:ui would drag the geometry helper and its test across
 * the module boundary, which is not a small clean move.
 */
@Composable
fun JellyPlayYourWatchingScreen(
    onBack: () -> Unit,
    viewModel: JellyPlayYourWatchingViewModel = koinViewModel(),
) {
    val backgroundColorState = rememberScreenBackgroundColorState()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Freshness on open: one pull per visit (the messages screen's idiom) —
    // window chips refetch through [viewModel.selectWindow].
    LaunchedEffect(Unit) { viewModel.refresh() }

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.settings_jellyplay_yw_title),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
    ) { contentPadding ->
        val analytics = state.analytics
        when {
            // Quiet degrade: the route 404'd (old plugin / analytics key
            // absent) or the gate degraded — never an error banner.
            !state.isLoading && analytics == null -> Text(
                text = stringResource(Res.string.settings_jellyplay_yw_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(24.dp),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .focusGroup(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "days") {
                    WindowChips(
                        selected = state.window,
                        onSelect = viewModel::selectWindow,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                analytics?.let { data ->
                    item(key = "totals") {
                        TotalsRow(
                            analytics = data,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    if (data.perDay.isNotEmpty()) {
                        item(key = "chart") {
                            SectionCard(stringResource(Res.string.settings_jellyplay_yw_section_chart)) {
                                PerDayBarChart(data = data.perDay)
                            }
                        }
                    }
                    if (data.topItems.isNotEmpty()) {
                        item(key = "top-items-label") {
                            SectionLabel(
                            stringResource(Res.string.settings_jellyplay_yw_section_top_items),
                            Modifier.padding(top = 8.dp, start = 20.dp),
                        )
                        }
                        itemsIndexed(data.topItems, key = { _, item -> item.itemId }) { index, item ->
                            TopItemRow(
                                rank = index + 1,
                                item = item,
                            )
                        }
                    }
                    if (data.totals.plays == 0L && data.perDay.isEmpty() && data.topItems.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = stringResource(Res.string.settings_jellyplay_yw_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The analytics window's chips (7/30/90) — the pill language of the admin analytics' day picker. */
@Composable
private fun WindowChips(
    selected: JellyPlayYourWatchingWindow,
    onSelect: (JellyPlayYourWatchingWindow) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            JellyPlayYourWatchingWindow.Seven to Res.string.settings_jellyplay_yw_days_7,
            JellyPlayYourWatchingWindow.Thirty to Res.string.settings_jellyplay_yw_days_30,
            JellyPlayYourWatchingWindow.Ninety to Res.string.settings_jellyplay_yw_days_90,
        ).forEach { (window, labelRes) ->
            val selectedChip = window == selected
            Box(
                modifier = Modifier
                    .clip(ShapeCache.smoothPill)
                    .background(
                        if (selectedChip) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    .focusIndicator(ShapeCache.smoothPill)
                    .clickable { onSelect(window) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    stringResource(labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selectedChip) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The window roll-up: plays, humanized watch time, distinct titles. */
@Composable
private fun TotalsRow(
    analytics: JellyPlayMyAnalytics,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(
            value = analytics.totals.plays.toString(),
            label = stringResource(Res.string.settings_jellyplay_yw_plays),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = formatDurationApproxSeconds(analytics.totals.playSeconds),
            label = stringResource(Res.string.settings_jellyplay_yw_watch_time),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = analytics.totals.uniqueItems.toString(),
            label = stringResource(Res.string.settings_jellyplay_yw_titles),
            modifier = Modifier.weight(1f),
        )
    }
}

/** One roll-up tile (the analytics stat-card look, pre-formatted values). */
@Composable
private fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A titled content card (the section wrapper for the chart). */
@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * One most-played title: the server-ranked list, the rank on the trailing
 * edge (the unread-badge slot), the item-type icon leading.
 */
@Composable
private fun TopItemRow(
    rank: Int,
    item: JellyPlayAnalyticsTopItem,
) {
    SettingListItem(
        icon = jellyPlayItemTypeIcon(item.itemType),
        title = item.itemName.ifBlank { item.itemId },
        subtitle = buildList {
            add(pluralStringResource(Res.plurals.jellyplay_ur_plays, item.plays.toInt(), item.plays))
            if (item.playSeconds > 0) add(formatDurationApproxSeconds(item.playSeconds))
        }.joinToString(" • "),
        trailingText = "#$rank",
        // The rank rows are informational (the sync screen's device rows
        // idiom): no navigation target exists for a bare itemId.
        onClick = {},
    )
}

/**
 * The plays-per-day bars — a minimal private Canvas composable (see the
 * screen KDoc for why the admin module's chart is not shared). Sparse x
 * labels: every bar up to ten, then a widening step ladder, the last day
 * always labeled.
 */
@Composable
private fun PerDayBarChart(
    data: List<JellyPlayAnalyticsDay>,
    modifier: Modifier = Modifier,
) {
    if (data.isEmpty()) return
    val maxValue = data.maxOf { it.plays }.coerceAtLeast(1L).toFloat()
    val barColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val cornerRadius = 4.dp

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val slot = size.width / data.size
            val barWidth = slot * 0.7f
            val chartHeight = size.height
            data.forEachIndexed { index, day ->
                val fraction = day.plays / maxValue
                val barHeight = chartHeight * fraction
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(index * slot + (slot - barWidth) / 2, chartHeight - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                )
            }
        }
        val step = perDayLabelStep(data.size)
        // One equally-weighted slot per bar (skipped slots render empty) so a
        // thinned label stays under ITS bar — a SpaceBetween over only the
        // labeled texts would redistribute them evenly instead.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            data.forEachIndexed { index, day ->
                Text(
                    text = if (index % step == 0 || index == data.lastIndex) day.day.takeLast(5) else "",
                    style = labelStyle,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The chart's label-admission ladder: every bar labels up to ten, then thins out. */
private fun perDayLabelStep(size: Int): Int = when {
    size <= 10 -> 1
    size <= 16 -> 2
    size <= 32 -> 5
    else -> 7
}
