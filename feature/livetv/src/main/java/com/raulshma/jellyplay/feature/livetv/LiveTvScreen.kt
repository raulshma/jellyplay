package com.raulshma.jellyplay.feature.livetv

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.model.RecordingFolder
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.feature.livetv.channels.ChannelsScreen
import com.raulshma.jellyplay.feature.livetv.epg.EpgScreen
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
 * Each tab owns its own `hiltViewModel()` so tab switches don't tear down the
 * others' state. Channel/program taps route through the shared [onChannelClick]
 * / [onRecordingClick] navigations passed in from the host app.
 *
 * @param onChannelClick channelId, channelName — opens the channel player.
 * @param onRecordingClick recordingId — opens the recording in the video player.
 * @param onFolderClick folder — opens the recording folder contents.
 */
@Composable
fun LiveTvScreen(
    onChannelClick: (String, String) -> Unit,
    onRecordingClick: (String) -> Unit,
    onFolderClick: (RecordingFolder) -> Unit,
) {
    val tabs = listOf(
        LiveTvTab.PROGRAMS,
        LiveTvTab.GUIDE,
        LiveTvTab.CHANNELS,
        LiveTvTab.RECORDINGS,
        LiveTvTab.SCHEDULE,
        LiveTvTab.SERIES,
    )
    // Default to Programs (index 0), matching jellyfin-web's default landing.
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val backgroundColor = rememberScreenBackgroundColor()

    JellyPlayScreenScaffold(
        title = "Live TV",
        backgroundColor = backgroundColor,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = backgroundColor,
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                text = stringResource(tab.titleRes),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (tabs[page]) {
                    LiveTvTab.PROGRAMS -> ProgramsScreen(
                        onProgramClick = { program ->
                            onChannelClick(program.channelId, program.name)
                        },
                    )
                    LiveTvTab.GUIDE -> EpgScreen(
                        onProgramClick = { program: LiveTvProgram ->
                            onChannelClick(program.channelId, program.name)
                        },
                        onBack = { scope.launch { pagerState.animateScrollToPage(LiveTvTab.PROGRAMS.ordinal) } },
                    )
                    LiveTvTab.CHANNELS -> ChannelsScreen(
                        onChannelClick = onChannelClick,
                    )
                    LiveTvTab.RECORDINGS -> RecordingsScreen(
                        onRecordingClick = onRecordingClick,
                        onFolderClick = onFolderClick,
                    )
                    LiveTvTab.SCHEDULE -> ScheduleScreen()
                    LiveTvTab.SERIES -> SeriesScreen()
                }
            }
        }
    }
}

private enum class LiveTvTab(val titleRes: Int) {
    PROGRAMS(R.string.livetv_tab_programs),
    GUIDE(R.string.livetv_tab_guide),
    CHANNELS(R.string.livetv_tab_channels),
    RECORDINGS(R.string.livetv_tab_recordings),
    SCHEDULE(R.string.livetv_tab_schedule),
    SERIES(R.string.livetv_tab_series),
}
