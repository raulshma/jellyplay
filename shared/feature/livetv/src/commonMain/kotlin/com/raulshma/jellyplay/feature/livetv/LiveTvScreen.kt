package com.raulshma.jellyplay.feature.livetv

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.feature.livetv.channels.ChannelsScreen
import com.raulshma.jellyplay.feature.livetv.epg.EpgScreen
import com.raulshma.jellyplay.feature.livetv.generated.resources.Res
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_screen_title
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_tab_channels
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_tab_guide
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_tab_programs
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_tab_recordings
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_tab_schedule
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_tab_series
import com.raulshma.jellyplay.feature.livetv.programs.ProgramsScreen
import com.raulshma.jellyplay.feature.livetv.recordings.RecordingsScreen
import com.raulshma.jellyplay.feature.livetv.schedule.ScheduleScreen
import com.raulshma.jellyplay.feature.livetv.series.SeriesScreen
import kotlinx.coroutines.launch

/**
 * Top-level Live TV host — a 6-tab screen mirroring jellyfin-web's Live TV
 * collection: Programs, Guide, Channels, Recordings, Schedule, Series. The
 * default landing tab is Programs (the web app's default landing), reachable
 * via the top-level "Live TV" navigation entry.
 *
 * The tab bar uses Material 3 Expressive styling: a content-hugging pill
 * indicator under the selected tab and per-tab badges (counts for the
 * Recordings / Schedule / Series tabs sourced from [LiveTvOverviewViewModel]).
 *
 * Each tab owns its own `koinViewModel()` so tab switches don't tear down the
 * others' state. Channel/program taps route through the shared [onChannelClick]
 * / [onRecordingClick] navigations passed in from the host app.
 *
 * @param onChannelClick channelId, channelName — opens the channel player.
 * @param onOpenChannelDetail channelId, channelName — opens the channel detail
 *   (program guide) screen. Used by the Channels tab row tap; the Programs and
 *   Guide tabs keep playing directly via [onChannelClick].
 * @param onRecordingClick recordingId — opens the recording in the video player.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LiveTvScreen(
    onChannelClick: (String, String) -> Unit,
    onOpenChannelDetail: (String, String) -> Unit,
    onRecordingClick: (String) -> Unit,
    overviewViewModel: LiveTvOverviewViewModel = koinViewModel(),
) {
    val tabs = LiveTvTab.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val backgroundColor = rememberScreenBackgroundColor()
    val badges by overviewViewModel.badges.collectAsStateWithLifecycle()

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.livetv_screen_title),
        backgroundColor = backgroundColor,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            LiveTvTabBar(
                selectedTabIndex = pagerState.currentPage,
                badges = badges,
                onTabSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                backgroundColor = backgroundColor,
            )

            val onProgramClick = remember(onChannelClick) { { program: LiveTvProgram -> onChannelClick(program.channelId, program.name) } }
            val onEpgBack: () -> Unit = remember { { scope.launch { pagerState.animateScrollToPage(LiveTvTab.PROGRAMS.ordinal) } } }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (tabs[page]) {
                    LiveTvTab.PROGRAMS -> ProgramsScreen(
                        onProgramClick = onProgramClick,
                    )
                    LiveTvTab.GUIDE -> EpgScreen(
                        onProgramClick = onProgramClick,
                        onBack = onEpgBack,
                    )
                    LiveTvTab.CHANNELS -> ChannelsScreen(
                        onChannelClick = onOpenChannelDetail,
                        onPlayChannel = onChannelClick,
                    )
                    LiveTvTab.RECORDINGS -> RecordingsScreen(
                        onRecordingClick = onRecordingClick,
                    )
                    LiveTvTab.SCHEDULE -> ScheduleScreen()
                    LiveTvTab.SERIES -> SeriesScreen()
                }
            }
        }
    }
}

/**
 * Material 3 Expressive Live TV tab bar: a [PrimaryScrollableTabRow] (six tabs
 * need scrolling on narrow phones) with a content-hugging rounded pill
 * indicator (`tabIndicatorOffset(matchContentSize = true)` + a
 * [ShapeCache.smoothPill] pill) and per-tab [Badge]s for Recordings / Schedule /
 * Series counts. Mirrors the expressive tab style used elsewhere in the app.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LiveTvTabBar(
    selectedTabIndex: Int,
    badges: LiveTvBadges,
    onTabSelected: (Int) -> Unit,
    backgroundColor: androidx.compose.ui.graphics.Color,
) {
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedTabIndex,
        containerColor = backgroundColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        edgePadding = 12.dp,
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier.tabIndicatorOffset(
                    selectedTabIndex = selectedTabIndex,
                    matchContentSize = true,
                ),
                width = androidx.compose.ui.unit.Dp.Unspecified,
                height = 6.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = ShapeCache.smoothPill,
            )
        },
        divider = {},
    ) {
        LiveTvTab.entries.forEachIndexed { index, tab ->
            val count = tab.badgeCount(badges)
            Tab(
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(index) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = {
                    BadgedBox(
                        badge = {
                            if (count > 0) Badge { Text("$count") }
                        },
                    ) {
                        Text(
                            text = stringResource(tab.titleRes),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                },
            )
        }
    }
}

/** Per-tab title string + badge selector. */
private enum class LiveTvTab(val titleRes: StringResource) {
    PROGRAMS(Res.string.livetv_tab_programs),
    GUIDE(Res.string.livetv_tab_guide),
    CHANNELS(Res.string.livetv_tab_channels),
    RECORDINGS(Res.string.livetv_tab_recordings),
    SCHEDULE(Res.string.livetv_tab_schedule),
    SERIES(Res.string.livetv_tab_series);

    /** Badge count to render for this tab, or 0 for no badge. */
    fun badgeCount(b: LiveTvBadges): Int = when (this) {
        RECORDINGS -> b.recordings
        SCHEDULE -> b.activeRecordings.coerceAtLeast(b.upcoming)
        SERIES -> b.series
        else -> 0
    }
}
