package com.raulshma.jellyplay.feature.player.video.components

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
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Subtitles
import com.raulshma.jellyplay.core.model.CultureInfo
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.SubtitleStylePreset
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.SheetTabRow
import com.raulshma.jellyplay.core.ui.harness.harnessClickTarget
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusState
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_subtitles
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_open_tester
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_subtitle_delay
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_subtitle_delay_hub_hint
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_subtitle_delay_show_controls
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_subtitle_hub_section_delay
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_subtitle_hub_section_get
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_subtitle_hub_section_style
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_subtitle_hub_section_tracks
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_subtitle_offset
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_subtitle_settings











import com.raulshma.jellyplay.feature.player.video.SubtitleDownloadStatus
import com.raulshma.jellyplay.feature.player.video.TrackOption
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvSubtitleOwnership

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
 * The hub's per-tab input bundles (the [PlayerControls] carrier precedent):
 * each tab's values + action lambdas ride one `@Immutable` data class, so the
 * composable's signature is the four tab inputs instead of a 41-parameter
 * wall sectioned by comments. [com.raulshma.jellyplay.feature.player.video.SubtitleHubSheetModule]
 * builds them at the router branch (its slices collected sheet-locally, the
 * placement rule); the hub only re-slots them into its tab sections.
 */

/** The Tracks tab: the active-track picker ([TrackPickerSection]) plus the per-series preference footer. */
@Immutable
internal data class SubtitleHubTracksTab(
    val subtitleTracks: List<TrackOption>,
    val onSelectSubtitleTrack: (TrackOption) -> Unit,
    val onResetSubtitleTrack: (() -> Unit)?,
    val tracksFooter: @Composable (() -> Unit)? = null,
)

/**
 * The Style tab: the manual style controls, the named presets row, the engine
 * capability gates, and the custom-mpv-config ownership snapshot (notice card
 * in [SubtitleStyleControls]: which sub-* keys the engine yields to Advanced
 * MPV Configuration / mpv.conf this session).
 */
@Immutable
internal data class SubtitleHubStyleTab(
    val subtitleStyle: SubtitleStyle,
    val capabilities: EngineCapabilities,
    val subtitleOwnership: MpvSubtitleOwnership = MpvSubtitleOwnership.NONE,
    /** Named presets (built-ins are code; user presets ride the subtitle slice via the host). */
    val userStylePresets: List<SubtitleStylePreset> = emptyList(),
    val onStyleChange: (SubtitleStyle) -> Unit,
    /** The per-item delay setter — an in-player delay edit must not clobber the global default. */
    val onSubtitleDelayChange: (Long) -> Unit,
    val onPickFont: () -> Unit,
    val onOpenTester: () -> Unit,
    val onSaveStylePreset: (String) -> Unit = {},
    val onDeleteStylePreset: (String) -> Unit = {},
)

/** The Get tab: the download / search / upload workflow ([SubtitleManagerSection]). */
@Immutable
internal data class SubtitleHubGetTab(
    val downloadSubtitles: List<RemoteSubtitleInfo>,
    val isDownloading: Boolean,
    val remoteSubtitlesError: String?,
    val onDownload: (RemoteSubtitleInfo) -> Unit,
    val onLoadLocalFile: () -> Unit,
    val searchResults: List<RemoteSubtitleInfo>,
    val isSearching: Boolean,
    val hasSearched: Boolean,
    val searchError: String?,
    val cultures: List<CultureInfo>,
    val defaultLanguage: String,
    val onSearch: (String) -> Unit,
    val onDownloadSearched: (RemoteSubtitleInfo) -> Unit,
    val providerSearchResults: List<SubtitleSearchResult>,
    val providerSearchErrors: Map<SubtitleProviderKind, String>,
    val configuredProviders: Set<SubtitleProviderKind>,
    val onSearchAllProviders: (String) -> Unit,
    val onDownloadProviderSubtitle: (SubtitleSearchResult) -> Unit,
    /** Re-attempts the server upload for a device-only provider download row. */
    val onSaveSubtitleToServer: (String) -> Unit = {},
    val downloadingSubtitles: Map<String, SubtitleDownloadStatus>,
    /**
     * Activates the downloaded subtitle identified by row key. Returns true
     * when the track is now selected (the hub navigates to Tracks); false
     * when the side-load is still in flight (the host retries automatically).
     */
    val onUseSubtitle: (String) -> Boolean,
    val isUploading: Boolean,
    /**
     * KMP seam: the picked SAF document travels as its string form
     * (android.net.Uri died with the commonMain move); the Android host
     * re-parses it at the call site.
     */
    val onUpload: (String, String, String?, Boolean, Boolean) -> Unit,
)

/** The Delay tab: the offset readout plus the button that opens the transparent overlay. */
@Immutable
internal data class SubtitleHubDelayTab(
    val currentDelayMs: Long,
    val onOpenDelayOverlay: () -> Unit,
)

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
 * primary Subtitles button and the overflow "Subtitles" entry open Tracks,
 * while the metadata row's mpv-config chip opens Style (the full ownership
 * notice lives there).
 *
 * The 41-parameter wall is collapsed into the four per-tab input bundles
 * ([SubtitleHubTracksTab] / [SubtitleHubStyleTab] / [SubtitleHubGetTab] /
 * [SubtitleHubDelayTab]), built by the sheet's module
 * ([com.raulshma.jellyplay.feature.player.video.SubtitleHubSheetModule]) —
 * the wave-2 sheet-module migration; each bundle's KDoc carries its tab's
 * value/lambda documentation.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SubtitleHubSheet(
    initialTab: SubtitleHubTab = SubtitleHubTab.TRACKS,
    onDismiss: () -> Unit,
    tracks: SubtitleHubTracksTab,
    style: SubtitleHubStyleTab,
    get: SubtitleHubGetTab,
    delay: SubtitleHubDelayTab,
) {
    val isTv = LocalTvMode.current

    // Build the visible-tab list from capabilities (the Style tab bundle
    // carries the engine's EngineCapabilities — Style/Delay hide on engines
    // without the support). Tracks + Get are always shown.
    val visibleTabs: List<SubtitleHubTab> = buildList {
        add(SubtitleHubTab.TRACKS)
        if (style.capabilities.supportsSubtitleStyle) add(SubtitleHubTab.STYLE)
        add(SubtitleHubTab.GET)
        if (style.capabilities.supportsSubtitleDelay) add(SubtitleHubTab.DELAY)
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

    // "Get" tab hoisted state — owned here, passed down to SubtitleManagerSection.
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
            SheetTabRow(selectedTabIndex = safeIndex) {
                visibleTabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = safeIndex == index,
                        onClick = { selectedTabIndex = index },
                        // e2e: click-reach target (harness-gated no-op) — the
                        // harness lands on Get to reach the upload sub-tab.
                        modifier = if (tab == SubtitleHubTab.GET) {
                            Modifier.harnessClickTarget("player-subtitle-hub-get-tab")
                        } else {
                            Modifier
                        },
                        text = {
                            Text(
                                when (tab) {
                                    SubtitleHubTab.TRACKS -> stringResource(Res.string.player_video_subtitle_hub_section_tracks)
                                    SubtitleHubTab.STYLE -> stringResource(Res.string.player_video_subtitle_hub_section_style)
                                    SubtitleHubTab.GET -> stringResource(Res.string.player_video_subtitle_hub_section_get)
                                    SubtitleHubTab.DELAY -> stringResource(Res.string.player_video_subtitle_hub_section_delay)
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
                    title = stringResource(Res.string.player_subtitles),
                    tracks = tracks.subtitleTracks,
                    onSelect = tracks.onSelectSubtitleTrack,
                    onReset = tracks.onResetSubtitleTrack,
                    // Tracks selection keeps the hub open so the user can adjust
                    // other subtitle settings without re-opening it.
                    onPickDismiss = {},
                    footer = tracks.tracksFooter,
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
                        title = stringResource(Res.string.player_video_subtitle_settings),
                        icon = Tabler.Outline.Subtitles,
                        trailing = {
                            TextButton(onClick = style.onOpenTester) {
                                Text(stringResource(Res.string.player_video_open_tester))
                            }
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                    // named presets above the manual controls; applying
                    // folds through SubtitleStylePresetPolicy.appliedStyle
                    // (look forced authoritative, per-item delay carried) and
                    // then the same onStyleChange path a manual edit takes.
                    SubtitleStylePresetsRow(
                        userPresets = style.userStylePresets,
                        onApplyPreset = { preset ->
                            style.onStyleChange(
                                com.raulshma.jellyplay.feature.player.video.SubtitleStylePresetPolicy
                                    .appliedStyle(preset, style.subtitleStyle)
                            )
                        },
                        onSavePreset = style.onSaveStylePreset,
                        onDeletePreset = style.onDeleteStylePreset,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    SubtitleStyleControls(
                        currentStyle = style.subtitleStyle,
                        onStyleChange = style.onStyleChange,
                        capabilities = style.capabilities,
                        subtitleOwnership = style.subtitleOwnership,
                        onPickFont = style.onPickFont,
                        showOverrideToggle = true,
                        onReset = { style.onStyleChange(SubtitleStyle(applyCustomStyle = true)) },
                        // Route the offset slider to the per-item delay setter so
                        // an in-player delay edit doesn't clobber the global default.
                        onSubtitleDelayChange = style.onSubtitleDelayChange,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                SubtitleHubTab.GET -> SubtitleManagerSection(
                    downloadSubtitles = get.downloadSubtitles,
                    isDownloading = get.isDownloading,
                    remoteSubtitlesError = get.remoteSubtitlesError,
                    onDownload = get.onDownload,
                    onLoadLocalFile = get.onLoadLocalFile,
                    searchResults = get.searchResults,
                    isSearching = get.isSearching,
                    hasSearched = get.hasSearched,
                    searchError = get.searchError,
                    cultures = get.cultures,
                    defaultLanguage = get.defaultLanguage,
                    onSearch = get.onSearch,
                    onDownloadSearched = get.onDownloadSearched,
                    providerSearchResults = get.providerSearchResults,
                    providerSearchErrors = get.providerSearchErrors,
                    configuredProviders = get.configuredProviders,
                    onSearchAllProviders = get.onSearchAllProviders,
                    onDownloadProviderSubtitle = get.onDownloadProviderSubtitle,
                    onSaveSubtitleToServer = get.onSaveSubtitleToServer,
                    downloadingSubtitles = get.downloadingSubtitles,
                    // "Use" activates the downloaded subtitle as the current
                    // track (host callback) and jumps to the Tracks tab so the
                    // user sees the applied selection. When the side-load has
                    // not landed yet the host arms an automatic retry instead;
                    // stay on the Get tab then — jumping to an unselected track
                    // list is exactly the confusion this guard avoids.
                    onUseSubtitle = { rowKey ->
                        val activated = get.onUseSubtitle(rowKey)
                        if (activated) {
                            selectedTabIndex = visibleTabs.indexOf(SubtitleHubTab.TRACKS)
                                .takeIf { it >= 0 } ?: 0
                        }
                    },
                    isUploading = get.isUploading,
                    onUpload = get.onUpload,
                    isTv = isTv,
                    selectedTab = getTabIndex,
                    onTabChange = { getTabIndex = it },
                    downloadFocus = downloadFocus,
                    searchFocus = searchFocus,
                    uploadFocus = uploadFocus,
                    loadBtnFocus = loadBtnFocus,
                )
                SubtitleHubTab.DELAY -> SubtitleDelayLauncherSection(
                    currentDelayMs = delay.currentDelayMs,
                    onOpenDelayOverlay = delay.onOpenDelayOverlay,
                    isTv = isTv,
                )
            }
        }
    }
}

/**
 * The hub's Delay tab: a short explainer + a button that dismisses the hub and
 * opens the transparent [SubtitleDelayOverlay] on the video. The offset is
 * adjusted there so the user can watch the live subtitles shift — VLC-style.
 */
@Composable
private fun SubtitleDelayLauncherSection(
    currentDelayMs: Long,
    onOpenDelayOverlay: () -> Unit,
    isTv: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
    ) {
        SheetHeader(
            title = stringResource(Res.string.player_video_subtitle_delay),
            icon = Tabler.Outline.Subtitles,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(Res.string.player_video_subtitle_delay_hub_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (currentDelayMs != 0L) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    Res.string.player_video_subtitle_offset,
                    formatDelayLabelMs(currentDelayMs),
                ),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onOpenDelayOverlay, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.player_video_subtitle_delay_show_controls))
        }
    }
}
