package com.raulshma.jellyplay.feature.player.video.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Subtitles
import com.raulshma.jellyplay.core.model.CultureInfo
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusState
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.feature.player.video.R
import com.raulshma.jellyplay.feature.player.video.SubtitleDownloadStatus
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities

/**
 * The hub tabs. Order is fixed: Tracks is always first (the primary use case —
 * pick which subtitle is active), then Style, Get, Delay. The host may hide
 * Style/Delay based on engine capabilities; the visible-tab list is computed
 * in [SubtitleHubSheet].
 */
internal enum class SubtitleHubTab {
    TRACKS,
    STYLE,
    GET,
    DELAY,
}

/**
 * The unified subtitle hub — a single bottom sheet that consolidates the four
 * previously-separate subtitle surfaces:
 *
 *  - **Tracks** — pick the active subtitle track ([TrackPickerSection]).
 *  - **Style** — font/size/color/background ([SubtitleStyleControls]).
 *  - **Get** — download / search / upload subtitles ([SubtitleManagerSection]).
 *  - **Delay** — subtitle offset sync ([SubtitleDelaySection]).
 *
 * Replaces `PlayerSheet.Subtitle`, `SubtitleStyle`, and `SubtitleDownload`;
 * subtitle delay is pulled out of the `AVSync` sheet (audio delay stays there).
 *
 * The hub owns its own tab state (saveably restored across config changes) and
 * focus requesters. [initialTab] lets the caller land on a specific tab — the
 * primary Subtitles button opens Tracks, while the overflow "Subtitles" entry
 * opens Get (the former "Get Subtitles" entry point).
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SubtitleHubSheet(
    initialTab: SubtitleHubTab = SubtitleHubTab.TRACKS,
    onDismiss: () -> Unit,
    // Tracks tab
    subtitleTracks: List<com.raulshma.jellyplay.feature.player.video.TrackOption>,
    onSelectSubtitleTrack: (com.raulshma.jellyplay.feature.player.video.TrackOption) -> Unit,
    onResetSubtitleTrack: (() -> Unit)?,
    tracksFooter: @Composable (() -> Unit)? = null,
    // Style tab
    subtitleStyle: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit,
    onPickFont: () -> Unit,
    onOpenTester: () -> Unit,
    capabilities: EngineCapabilities,
    // Get tab
    downloadSubtitles: List<RemoteSubtitleInfo>,
    isDownloading: Boolean,
    onDownload: (RemoteSubtitleInfo) -> Unit,
    onLoadLocalFile: () -> Unit,
    searchResults: List<RemoteSubtitleInfo>,
    isSearching: Boolean,
    hasSearched: Boolean,
    searchError: String?,
    cultures: List<CultureInfo>,
    defaultLanguage: String,
    onSearch: (String) -> Unit,
    onDownloadSearched: (RemoteSubtitleInfo) -> Unit,
    providerSearchResults: List<SubtitleSearchResult>,
    providerSearchErrors: Map<SubtitleProviderKind, String>,
    configuredProviders: Set<SubtitleProviderKind>,
    onSearchAllProviders: (String) -> Unit,
    onDownloadProviderSubtitle: (SubtitleSearchResult) -> Unit,
    downloadingSubtitles: Map<String, SubtitleDownloadStatus>,
    onUseSubtitle: () -> Unit,
    isUploading: Boolean,
    onUpload: (Uri, String, String?, Boolean, Boolean) -> Unit,
    // Delay tab
    currentSubtitleDelayMs: Long,
    onSubtitleDelayChange: (Long) -> Unit,
    activeSubtitleCues: List<com.raulshma.jellyplay.feature.player.video.subtitle.TimedCue>?,
    subtitlePreviewSource: com.raulshma.jellyplay.feature.player.video.SubtitlePreviewSource =
        com.raulshma.jellyplay.feature.player.video.SubtitlePreviewSource.NONE,
    playbackPositionMs: () -> Long,
) {
    val isTv = LocalTvMode.current

    // Build the visible-tab list from capabilities. Tracks + Get are always
    // shown; Style/Delay depend on engine support.
    val visibleTabs: List<SubtitleHubTab> = buildList {
        add(SubtitleHubTab.TRACKS)
        if (capabilities.supportsSubtitleStyle) add(SubtitleHubTab.STYLE)
        add(SubtitleHubTab.GET)
        if (capabilities.supportsSubtitleDelay) add(SubtitleHubTab.DELAY)
    }
    // Tab index is saveable so a config change (rotation) while the hub is open
    // restores the active tab. Clamped to the visible list.
    var selectedTabIndex by rememberSaveable(initialTab) {
        val initial = visibleTabs.indexOf(initialTab).takeIf { it >= 0 } ?: 0
        mutableIntStateOf(initial)
    }
    // Clamp in case the visible set shrank (capability changed) since last open.
    val safeIndex = selectedTabIndex.coerceIn(0, visibleTabs.lastIndex)
    val activeTab = visibleTabs.getOrElse(safeIndex) { SubtitleHubTab.TRACKS }

    // "Get" tab hoisted state — mirrors SubtitleManagerSheet's own.
    var getTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val downloadFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val uploadFocus = remember { FocusRequester() }
    val loadBtnFocus = rememberTvFocusState()

    // Hub-level focus: when the hub opens (or the tab changes), move D-pad
    // focus onto the active tab's primary content so the remote isn't stranded.
    val tracksFocus = remember { FocusRequester() }
    LaunchedEffect(isTv, activeTab) {
        if (!isTv) return@LaunchedEffect
        when (activeTab) {
            SubtitleHubTab.TRACKS -> tracksFocus.tryRequestFocus("hub-tracks")
            // Get/Style/Delay sections manage their own focus requesters.
            else -> {}
        }
    }

    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            PrimaryTabRow(selectedTabIndex = safeIndex) {
                visibleTabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = safeIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                when (tab) {
                                    SubtitleHubTab.TRACKS -> stringResource(R.string.player_video_subtitle_hub_section_tracks)
                                    SubtitleHubTab.STYLE -> stringResource(R.string.player_video_subtitle_hub_section_style)
                                    SubtitleHubTab.GET -> stringResource(R.string.player_video_subtitle_hub_section_get)
                                    SubtitleHubTab.DELAY -> stringResource(R.string.player_video_subtitle_hub_section_delay)
                                },
                                fontWeight = if (safeIndex == index) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            when (activeTab) {
                SubtitleHubTab.TRACKS -> TrackPickerSection(
                    title = stringResource(R.string.player_subtitles),
                    tracks = subtitleTracks,
                    onSelect = onSelectSubtitleTrack,
                    onReset = onResetSubtitleTrack,
                    // Tracks selection keeps the hub open so the user can adjust
                    // other subtitle settings without re-opening it.
                    onPickDismiss = {},
                    footer = tracksFooter,
                    focusRequester = tracksFocus,
                    modifier = Modifier.weight(1f, fill = false),
                )
                SubtitleHubTab.STYLE -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
                ) {
                    SheetHeader(
                        title = stringResource(R.string.player_video_subtitle_settings),
                        icon = Tabler.Outline.Subtitles,
                        trailing = {
                            TextButton(onClick = onOpenTester) {
                                Text(stringResource(R.string.player_video_open_tester))
                            }
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                    SubtitleStyleControls(
                        currentStyle = subtitleStyle,
                        onStyleChange = onStyleChange,
                        capabilities = capabilities,
                        onPickFont = onPickFont,
                        showOverrideToggle = true,
                        onReset = { onStyleChange(SubtitleStyle(applyCustomStyle = true)) },
                    )
                }
                SubtitleHubTab.GET -> SubtitleManagerSection(
                    downloadSubtitles = downloadSubtitles,
                    isDownloading = isDownloading,
                    onDownload = onDownload,
                    onLoadLocalFile = onLoadLocalFile,
                    searchResults = searchResults,
                    isSearching = isSearching,
                    hasSearched = hasSearched,
                    searchError = searchError,
                    cultures = cultures,
                    defaultLanguage = defaultLanguage,
                    onSearch = onSearch,
                    onDownloadSearched = onDownloadSearched,
                    providerSearchResults = providerSearchResults,
                    providerSearchErrors = providerSearchErrors,
                    configuredProviders = configuredProviders,
                    onSearchAllProviders = onSearchAllProviders,
                    onDownloadProviderSubtitle = onDownloadProviderSubtitle,
                    downloadingSubtitles = downloadingSubtitles,
                    // After a download surfaces, jump to the Tracks tab so the
                    // user can apply the freshly-attached subtitle — replaces the
                    // old sheet-to-sheet onSheetChange(PlayerSheet.Subtitle) hop.
                    onUseSubtitle = {
                        onUseSubtitle()
                        selectedTabIndex = visibleTabs.indexOf(SubtitleHubTab.TRACKS)
                            .takeIf { it >= 0 } ?: 0
                    },
                    isUploading = isUploading,
                    onUpload = onUpload,
                    isTv = isTv,
                    selectedTab = getTabIndex,
                    onTabChange = { getTabIndex = it },
                    downloadFocus = downloadFocus,
                    searchFocus = searchFocus,
                    uploadFocus = uploadFocus,
                    loadBtnFocus = loadBtnFocus,
                )
                SubtitleHubTab.DELAY -> SubtitleDelaySection(
                    currentSubtitleDelayMs = currentSubtitleDelayMs,
                    onSubtitleDelayChange = onSubtitleDelayChange,
                    activeSubtitleCues = activeSubtitleCues,
                    subtitlePreviewSource = subtitlePreviewSource,
                    playbackPositionMs = playbackPositionMs,
                    isTv = isTv,
                )
            }
        }
    }
}
