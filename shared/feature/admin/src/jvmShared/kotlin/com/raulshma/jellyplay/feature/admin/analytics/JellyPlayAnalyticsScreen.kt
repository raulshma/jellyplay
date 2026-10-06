package com.raulshma.jellyplay.feature.admin.analytics

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.Graph
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Users
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.jellyPlayItemTypeIcon
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_load_more
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_loading_dots
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_refresh
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_unknown_error
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_an_days_30
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_an_days_7
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_an_days_90
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_an_degraded_body
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_an_degraded_title
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_an_empty
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_an_filter_all
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_an_plays
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_an_section_chart
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_an_section_sessions
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_an_section_top_items
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_an_section_users
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_an_sessions_empty
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_an_title
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_an_totals_items
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_an_totals_transcode_time
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_an_totals_users
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_an_totals_watch_time
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_an_unavailable_body
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_an_unavailable_title
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_tr_play_direct
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_tr_play_transcode
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_tr_unknown
import com.raulshma.jellyplay.feature.admin.statistics.components.ActivityBarChart
import com.raulshma.jellyplay.feature.admin.transcodes.TranscodePlayMethod
import com.raulshma.jellyplay.feature.admin.transcodes.humanizeTranscodeReason

/**
 * The admin analytics dashboard (Route.JellyPlayAnalytics): the companion
 * plugin's play-history aggregates — window totals, a plays-per-day bar
 * chart, per-user and top-item splits, and the recent-sessions ledger with a
 * per-user filter and Load-more pagination. Admin access is enforced by the
 * wrapping AdminRouteContainer; the plugin gate lives in the
 * [JellyPlayAnalyticsViewModel], so the screen renders four states — loading
 * (gate unresolved), gated-off (plugin/feature absent), degraded (the
 * overview route 404'd — quiet), and the dashboard.
 *
 * Layout idioms follow the admin module: the transcodes monitor's scaffold +
 * refresh action + row cards, the statistics charts' [ActivityBarChart] for
 * the per-day bars, and the dashboard tiles' pill language for the
 * window/filter chips.
 */
@Composable
fun JellyPlayAnalyticsScreen(
    onBack: () -> Unit,
    viewModel: JellyPlayAnalyticsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val backgroundColorState = rememberScreenBackgroundColorState()

    LifecycleStartEffect(Unit) {
        viewModel.start()
        onStopOrDispose { viewModel.stop() }
    }

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.jellyplay_an_title),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
        actions = {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .clip(CircleShape)
                    .focusIndicator(CircleShape)
                    .clickable(onClick = { viewModel.refresh() }),
            ) {
                Icon(
                    Tabler.Outline.Refresh,
                    contentDescription = stringResource(Res.string.admin_refresh),
                    modifier = Modifier.padding(12.dp).size(20.dp),
                )
            }
        },
    ) {
        when {
            state.isLoading -> {
                ScreenLoadingState(modifier = Modifier.fillMaxSize())
            }
            state.gate == AnalyticsGate.Unavailable -> {
                GatedOffState(modifier = Modifier.fillMaxSize())
            }
            state.error != null -> {
                ErrorScreen(
                    message = state.error ?: stringResource(Res.string.admin_unknown_error),
                    onRetry = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            state.overviewDegraded -> {
                DegradedState(modifier = Modifier.fillMaxSize())
            }
            // A local read (the delegated `state` refuses a smart cast): the
            // overview rides the gate-on load, so null here means an empty
            // window rather than a mid-load gap.
            state.overview == null || state.overview?.totals?.plays == 0L -> {
                EmptyAnalyticsState(modifier = Modifier.fillMaxSize())
            }
            else -> {
                AnalyticsContent(
                    state = state,
                    contentPadding = PaddingValues(
                        start = adaptiveInfo.contentPadding(false),
                        end = adaptiveInfo.contentPadding(false),
                        top = 8.dp,
                        bottom = adaptiveInfo.bottomPadding(isTv),
                    ),
                    onSelectDays = viewModel::selectDays,
                    onSelectUser = viewModel::selectUser,
                    onLoadMore = viewModel::loadMoreSessions,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun AnalyticsContent(
    state: AnalyticsState,
    contentPadding: PaddingValues,
    onSelectDays: (Int) -> Unit,
    onSelectUser: (String?) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overview = state.overview ?: return
    // The user filter rides the sessions fetch AND the visible list — rows
    // loaded before the filter flipped stay honest while the next page is
    // in flight.
    val visibleSessions = state.sessions.filter { session ->
        state.selectedUserId == null || session.userId == state.selectedUserId
    }
    val userNameById = overview.perUser.associate { it.userId to it.userName }

    LazyColumn(
        modifier = modifier.focusGroup(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "days") {
            DaysRangeChips(
                selectedDays = state.days,
                onSelect = onSelectDays,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        item(key = "totals") {
            TotalsRow(totals = overview.totals, modifier = Modifier.padding(horizontal = 8.dp))
        }
        if (overview.perDay.isNotEmpty()) {
            item(key = "chart") {
                AnalyticsSectionCard(stringResource(Res.string.jellyplay_an_section_chart)) {
                    ActivityBarChart(data = overview.perDay)
                }
            }
        }
        if (overview.perUser.isNotEmpty()) {
            item(key = "users") {
                AnalyticsSectionCard(stringResource(Res.string.jellyplay_an_section_users)) {
                    UserRows(
                        users = overview.perUser,
                        selectedUserId = state.selectedUserId,
                        onSelectUser = onSelectUser,
                    )
                }
            }
        }
        if (overview.topItems.isNotEmpty()) {
            item(key = "top-items") {
                AnalyticsSectionCard(stringResource(Res.string.jellyplay_an_section_top_items)) {
                    TopItemRows(items = overview.topItems)
                }
            }
        }
        item(key = "sessions-header") {
            SessionsHeader(
                selectedUserName = state.selectedUserId?.let { userId ->
                    userNameById[userId] ?: stringResource(Res.string.jellyplay_tr_unknown)
                },
                users = overview.perUser,
                selectedUserId = state.selectedUserId,
                onSelectUser = onSelectUser,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        if (visibleSessions.isEmpty()) {
            item(key = "sessions-empty") {
                Text(
                    stringResource(Res.string.jellyplay_an_sessions_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        } else {
            itemsIndexed(
                visibleSessions,
                // Sessions carry no unique id — the key is positional with the
                // row identity folded in (the list is replaced wholesale on
                // every refetch, so index-stable keys never collide).
                key = { index, session -> "$index/${session.userId}/${session.itemId}/${session.endedAt}" },
                contentType = { _, _ -> "session" },
            ) { _, session ->
                SessionRowCard(
                    row = session,
                    userName = userNameById[session.userId] ?: stringResource(Res.string.jellyplay_tr_unknown),
                )
            }
        }
        if (state.hasMoreSessions) {
            item(key = "load-more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    FilledTonalButton(
                        onClick = onLoadMore,
                        enabled = !state.isLoadingMore,
                        shape = ShapeCache.smooth16,
                    ) {
                        Text(
                            if (state.isLoadingMore) {
                                stringResource(Res.string.admin_loading_dots)
                            } else {
                                stringResource(Res.string.admin_load_more)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** The overview window's segmented chips (7/30/90) — the dashboard tiles' pill language. */
@Composable
private fun DaysRangeChips(
    selectedDays: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            7 to Res.string.jellyplay_an_days_7,
            30 to Res.string.jellyplay_an_days_30,
            90 to Res.string.jellyplay_an_days_90,
        ).forEach { (days, labelRes) ->
            val selected = days == selectedDays
            Box(
                modifier = Modifier
                    .clip(ShapeCache.smoothPill)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    )
                    .focusIndicator(ShapeCache.smoothPill)
                    .clickable { onSelect(days) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    stringResource(labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** The window roll-up: plays, watch time, transcode time, unique users/items. */
@Composable
private fun TotalsRow(
    totals: AnalyticsTotalsUi,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnalyticsStatCard(
                value = totals.plays.toString(),
                label = stringResource(Res.string.jellyplay_an_plays),
                modifier = Modifier.weight(1f),
            )
            AnalyticsStatCard(
                value = totals.watchTimeLabel,
                label = stringResource(Res.string.jellyplay_an_totals_watch_time),
                modifier = Modifier.weight(1f),
            )
            AnalyticsStatCard(
                value = totals.transcodeTimeLabel,
                label = stringResource(Res.string.jellyplay_an_totals_transcode_time),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnalyticsStatCard(
                value = totals.uniqueUsers.toString(),
                label = stringResource(Res.string.jellyplay_an_totals_users),
                modifier = Modifier.weight(1f),
            )
            AnalyticsStatCard(
                value = totals.uniqueItems.toString(),
                label = stringResource(Res.string.jellyplay_an_totals_items),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One roll-up tile (the statistics SummaryStatCard's look, pre-formatted values). */
@Composable
private fun AnalyticsStatCard(
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

/** A titled content card (the section wrapper for chart / users / top items). */
@Composable
private fun AnalyticsSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
 * The per-user split. Tapping a row filters the sessions ledger to that user
 * (tapping it again clears the filter) — the selection rides the VM's
 * [JellyPlayAnalyticsViewModel.selectUser], which re-scopes the sessions
 * fetch too.
 */
@Composable
private fun UserRows(
    users: List<AnalyticsUserRow>,
    selectedUserId: String?,
    onSelectUser: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        users.forEach { user ->
            val selected = user.userId == selectedUserId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShapeCache.smooth8)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f)
                        },
                    )
                    .focusIndicator(ShapeCache.smooth8)
                    .clickable { onSelectUser(if (selected) null else user.userId) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        user.userName.ifBlank { stringResource(Res.string.jellyplay_tr_unknown) },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append(user.plays)
                            append(' ')
                            append(stringResource(Res.string.jellyplay_an_plays).lowercase())
                            append(" · ")
                            append(user.watchTimeLabel)
                            append(" · ")
                            append(user.transcodeTimeLabel)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (selected) {
                    Icon(
                        Tabler.Outline.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** The window's most-played items: rank, type icon, name, plays, watch time. */
@Composable
private fun TopItemRows(
    items: List<AnalyticsTopItemRow>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(24.dp),
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        jellyPlayItemTypeIcon(item.itemType),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.itemName.ifBlank { stringResource(Res.string.jellyplay_tr_unknown) },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        item.watchTimeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    item.plays.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** The sessions section header: title + the per-user filter dropdown chip. */
@Composable
private fun SessionsHeader(
    selectedUserName: String?,
    users: List<AnalyticsUserRow>,
    selectedUserId: String?,
    onSelectUser: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.jellyplay_an_section_sessions),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Box {
            Row(
                modifier = Modifier
                    .clip(ShapeCache.smoothPill)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .focusIndicator(ShapeCache.smoothPill)
                    .clickable { menuOpen = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Tabler.Outline.Users,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    selectedUserName ?: stringResource(Res.string.jellyplay_an_filter_all),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.jellyplay_an_filter_all)) },
                    onClick = {
                        onSelectUser(null)
                        menuOpen = false
                    },
                    leadingIcon = {
                        if (selectedUserId == null) {
                            Icon(Tabler.Outline.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        } else {
                            Spacer(Modifier.size(18.dp))
                        }
                    },
                )
                users.forEach { user ->
                    DropdownMenuItem(
                        text = { Text(user.userName.ifBlank { stringResource(Res.string.jellyplay_tr_unknown) }) },
                        onClick = {
                            onSelectUser(user.userId)
                            menuOpen = false
                        },
                        leadingIcon = {
                            if (user.userId == selectedUserId) {
                                Icon(Tabler.Outline.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            } else {
                                Spacer(Modifier.size(18.dp))
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * One recent session — the transcodes monitor's row card: item title with a
 * play-method pill, user · device subtitle, and a meta line (watched
 * position, bitrate, client, re-encode reasons while transcoding).
 */
@Composable
private fun SessionRowCard(
    row: AnalyticsSessionRow,
    userName: String,
    modifier: Modifier = Modifier,
) {
    val unknown = stringResource(Res.string.jellyplay_tr_unknown)
    val isTranscoding = row.playMethod == TranscodePlayMethod.TRANSCODE
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.itemName.ifBlank { unknown },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    PlayMethodChip(playMethod = row.playMethod, playMethodRaw = row.playMethodRaw)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append(userName.ifBlank { unknown })
                        append(" · ")
                        append(row.deviceName?.ifBlank { null } ?: row.clientName ?: unknown)
                        row.seriesName?.takeIf { it.isNotBlank() }?.let {
                            append(" · ")
                            append(it)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                SessionMetaRow(row = row, isTranscoding = isTranscoding)
            }
        }
    }
}

@Composable
private fun SessionMetaRow(row: AnalyticsSessionRow, isTranscoding: Boolean) {
    val parts = buildList {
        row.watchedLabel?.let { add(it) }
        row.bitrateLabel?.let { add(it) }
        // Re-encode reasons are only meaningful while the server transcoded —
        // a direct play has nothing to show for them (the transcodes
        // monitor's rule).
        if (isTranscoding && row.transcodeReasons.isNotEmpty()) {
            add(row.transcodeReasons.joinToString(separator = ", ") { it.humanizeTranscodeReason() })
        }
    }
    if (parts.isEmpty()) return
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The play-method pill (the paused-badge idiom): transcode tinted loudest. */
@Composable
private fun PlayMethodChip(
    playMethod: TranscodePlayMethod,
    playMethodRaw: String?,
    modifier: Modifier = Modifier,
) {
    val label = when (playMethod) {
        TranscodePlayMethod.DIRECT -> stringResource(Res.string.jellyplay_tr_play_direct)
        TranscodePlayMethod.TRANSCODE -> stringResource(Res.string.jellyplay_tr_play_transcode)
        TranscodePlayMethod.OTHER -> playMethodRaw ?: stringResource(Res.string.jellyplay_tr_unknown)
    }
    val (container, content) = when (playMethod) {
        TranscodePlayMethod.TRANSCODE ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        TranscodePlayMethod.DIRECT ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        TranscodePlayMethod.OTHER ->
            MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = content,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** The gated-off state: plugin absent or the `analytics` feature key missing. */
@Composable
private fun GatedOffState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Tabler.Outline.Graph,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(Res.string.jellyplay_an_unavailable_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(Res.string.jellyplay_an_unavailable_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The quiet degraded state: the probe exposes `analytics` but the overview
 * route answered 404 (the server's plugin predates the analytics wave).
 */
@Composable
private fun DegradedState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Tabler.Outline.Graph,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(Res.string.jellyplay_an_degraded_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(Res.string.jellyplay_an_degraded_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The enabled-but-empty state: the window has no recorded playback yet. */
@Composable
private fun EmptyAnalyticsState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Tabler.Outline.Graph,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(Res.string.jellyplay_an_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
