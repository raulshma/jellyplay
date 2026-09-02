package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.CultureInfo
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.components.SubtitleResultMetadata
import com.raulshma.jellyplay.core.ui.model.localizedDisplayName
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_change_file
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_download
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_failed_tap_to_retry
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_forced_subtitle
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_hearing_impaired
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_language
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_load_from_device
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_load_subtitles_failed
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_no_remote_subtitles
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_no_results_found
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_perfect_match
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_pick_language_hint
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_search
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_search_failed
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_select_file
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_subtitle_downloaded_device_only
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_subtitle_provider_all
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_taking_a_while
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_unknown
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_upload
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_uploading
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_use





















import com.raulshma.jellyplay.feature.player.video.SubtitleDownloadState
import com.raulshma.jellyplay.feature.player.video.SubtitleDownloadStatus
import com.raulshma.jellyplay.feature.player.video.state.providerSubtitleRowKey
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.components.SheetTabRow
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusState
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.verticalWrapAround

private enum class SubtitleManagerTab(val label: String) {
    DOWNLOAD("Download"),
    SEARCH("Search"),
    UPLOAD("Upload"),
}

/**
 * In-player subtitle manager. A single bottom sheet with three tabs:
 *
 * - **Download** — the server's default remote-subtitle browse + "Load from
 *   device" (the former [SubtitleDownloadSheet] surface).
 * - **Search** — language-scoped remote subtitle search (OpenSubtitles via the
 *   server), reusing the editor's search flow.
 * - **Upload** — upload a local subtitle file with language + forced/SDH flags,
 *   reusing the editor's upload flow via `MetadataApiClient.uploadSubtitle`.
 *
 * Replaces the single-purpose `SubtitleDownloadSheet` without leaving playback.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SubtitleManagerSheet(
    // Download tab
    downloadSubtitles: List<RemoteSubtitleInfo>,
    isDownloading: Boolean,
    remoteSubtitlesError: String? = null,
    onDownload: (RemoteSubtitleInfo) -> Unit,
    onLoadLocalFile: () -> Unit,
    // Search tab
    searchResults: List<RemoteSubtitleInfo>,
    isSearching: Boolean,
    hasSearched: Boolean,
    searchError: String?,
    cultures: List<CultureInfo>,
    defaultLanguage: String,
    onSearch: (String) -> Unit,
    onDownloadSearched: (RemoteSubtitleInfo) -> Unit,
    // Multi-provider search (Jellyfin + Wyzie + OpenSubtitles). When external
    // providers are configured, the Search tab merges these into one list with
    // provider filter chips; otherwise only the legacy Jellyfin list shows.
    providerSearchResults: List<com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult> = emptyList(),
    providerSearchErrors: Map<com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind, String> = emptyMap(),
    configuredProviders: Set<com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind> = setOf(com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind.JELLYFIN),
    onSearchAllProviders: (String) -> Unit = {},
    onDownloadProviderSubtitle: (com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult) -> Unit = {},
    // Shared: per-subtitle-id download status (spinner / ✓-Downloaded / delayed /
    // failed) for both Download + Search rows, and the "Use" affordance that opens
    // the subtitle track picker once a download has surfaced.
    downloadingSubtitles: Map<String, SubtitleDownloadStatus> = emptyMap(),
    /** Row key: the remote-subtitle id (Jellyfin rows) or `"provider:id"` composite (external rows). */
    onUseSubtitle: (String) -> Unit = {},
    // Upload tab
    isUploading: Boolean,
    // KMP seam (wave 7C): the picked SAF document travels as its string form
    // (android.net.Uri died with the commonMain move); the Android host
    // re-parses it at the call site.
    onUpload: (String, String, String?, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val isTv = LocalTvMode.current
    // selectedTabIndex is remembered *saveably* so a config change (rotation,
    // locale switch) while the sheet is open restores the active tab.
    var selectedTab by rememberSaveable { mutableIntStateOf(SubtitleManagerTab.DOWNLOAD.ordinal) }
    // One focus requester per tab's primary action so D-pad focus lands on a
    // real, on-screen target whenever the tab changes — not just the Download
    // tab. Without this the Search/Upload tabs are unreachable on a TV remote.
    val downloadFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val uploadFocus = remember { FocusRequester() }
    val loadBtnFocus = rememberTvFocusState()

    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        SubtitleManagerSection(
            downloadSubtitles = downloadSubtitles,
            isDownloading = isDownloading,
            remoteSubtitlesError = remoteSubtitlesError,
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
            onUseSubtitle = onUseSubtitle,
            isUploading = isUploading,
            onUpload = onUpload,
            isTv = isTv,
            selectedTab = selectedTab,
            onTabChange = { selectedTab = it },
            downloadFocus = downloadFocus,
            searchFocus = searchFocus,
            uploadFocus = uploadFocus,
            loadBtnFocus = loadBtnFocus,
        )
    }
}

/**
 * The body of [SubtitleManagerSheet] (Download / Search / Upload tabbed
 * content) without its own sheet chrome, for embedding inside the unified
 * subtitle hub's "Get" tab. The host owns the selected-tab state + focus
 * requesters so it can hoist them when needed.
 */
@Composable
internal fun androidx.compose.foundation.layout.ColumnScope.SubtitleManagerSection(
    downloadSubtitles: List<RemoteSubtitleInfo>,
    isDownloading: Boolean,
    remoteSubtitlesError: String? = null,
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
    providerSearchResults: List<com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult>,
    providerSearchErrors: Map<com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind, String>,
    configuredProviders: Set<com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind>,
    onSearchAllProviders: (String) -> Unit,
    onDownloadProviderSubtitle: (com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult) -> Unit,
    downloadingSubtitles: Map<String, SubtitleDownloadStatus>,
    /** Row key: the remote-subtitle id (Jellyfin rows) or `"provider:id"` composite (external rows). */
    onUseSubtitle: (String) -> Unit,
    isUploading: Boolean,
    onUpload: (String, String, String?, Boolean, Boolean) -> Unit,
    isTv: Boolean,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    downloadFocus: FocusRequester,
    searchFocus: FocusRequester,
    uploadFocus: FocusRequester,
    loadBtnFocus: TvFocusState,
) {
    val tabs = SubtitleManagerTab.entries

    LaunchedEffect(isTv, selectedTab) {
        if (!isTv) return@LaunchedEffect
        val requester = when (tabs.getOrElse(selectedTab) { SubtitleManagerTab.DOWNLOAD }) {
            SubtitleManagerTab.DOWNLOAD -> downloadFocus
            SubtitleManagerTab.SEARCH -> searchFocus
            SubtitleManagerTab.UPLOAD -> uploadFocus
        }
        requester.tryRequestFocus("sheet")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        SheetTabRow(
            selectedTabIndex = selectedTab,
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onTabChange(index) },
                    text = {
                        Text(
                            when (tab) {
                                SubtitleManagerTab.DOWNLOAD -> stringResource(Res.string.player_video_download)
                                SubtitleManagerTab.SEARCH -> stringResource(Res.string.player_video_search)
                                SubtitleManagerTab.UPLOAD -> stringResource(Res.string.player_video_upload)
                            },
                            fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        when (tabs.getOrElse(selectedTab) { SubtitleManagerTab.DOWNLOAD }) {
            SubtitleManagerTab.DOWNLOAD -> DownloadTab(
                modifier = Modifier.weight(1f, fill = false),
                subtitles = downloadSubtitles,
                isLoading = isDownloading,
                remoteSubtitlesError = remoteSubtitlesError,
                onDownload = onDownload,
                onLoadLocalFile = onLoadLocalFile,
                downloadingSubtitles = downloadingSubtitles,
                onUseSubtitle = onUseSubtitle,
                isTv = isTv,
                focusRequester = downloadFocus,
                loadBtnFocus = loadBtnFocus,
            )
            SubtitleManagerTab.SEARCH -> SearchTab(
                modifier = Modifier.weight(1f, fill = false),
                cultures = cultures,
                defaultLanguage = defaultLanguage,
                results = searchResults,
                isLoading = isSearching,
                hasSearched = hasSearched,
                searchError = searchError,
                onSearch = onSearch,
                onDownload = onDownloadSearched,
                downloadingSubtitles = downloadingSubtitles,
                onUseSubtitle = onUseSubtitle,
                isTv = isTv,
                focusRequester = searchFocus,
                providerSearchResults = providerSearchResults,
                providerSearchErrors = providerSearchErrors,
                configuredProviders = configuredProviders,
                onSearchAllProviders = onSearchAllProviders,
                onDownloadProviderSubtitle = onDownloadProviderSubtitle,
            )
            SubtitleManagerTab.UPLOAD -> UploadTab(
                modifier = Modifier.weight(1f, fill = false),
                cultures = cultures,
                defaultLanguage = defaultLanguage,
                isUploading = isUploading,
                onUpload = onUpload,
                isTv = isTv,
                focusRequester = uploadFocus,
            )
        }
    }
}

// region Download tab (formerly SubtitleDownloadSheet body) ------------------

/**
 * Inline fetch-failure block shared by the Download tab (server-default list)
 * and the legacy search results: a red title so the state reads as a failure
 * rather than "no subtitles exist", plus the raw error detail beneath it.
 */
@Composable
private fun SubtitleFetchErrorBlock(
    titleRes: StringResource,
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

@Composable
private fun DownloadTab(
    subtitles: List<RemoteSubtitleInfo>,
    isLoading: Boolean,
    remoteSubtitlesError: String?,
    onDownload: (RemoteSubtitleInfo) -> Unit,
    onLoadLocalFile: () -> Unit,
    downloadingSubtitles: Map<String, SubtitleDownloadStatus>,
    onUseSubtitle: (String) -> Unit,
    isTv: Boolean,
    focusRequester: FocusRequester,
    loadBtnFocus: TvFocusState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FilledTonalButton(
            onClick = onLoadLocalFile,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .ifElse(isTv, Modifier.focusRequester(focusRequester))
                .then(loadBtnFocus.focusModifier)
                .tvFocusIndicator(loadBtnFocus, ShapeCache.smoothPill),
        ) {
            Icon(
                imageVector = Tabler.Outline.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(stringResource(Res.string.player_video_load_from_device))
        }
        Spacer(Modifier.height(8.dp))

        if (isLoading) {
            JellyPlayLoadingIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(24.dp),
            )
        } else if (subtitles.isEmpty()) {
            // Inline failure, never a global toast — see [SubtitleState.remoteSubtitlesError].
            if (remoteSubtitlesError != null) {
                SubtitleFetchErrorBlock(
                    titleRes = Res.string.player_video_load_subtitles_failed,
                    message = remoteSubtitlesError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                )
            } else {
                Text(
                    stringResource(Res.string.player_video_no_remote_subtitles),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.verticalWrapAround().weight(1f, fill = false)) {
                // Composite key: providers (OpenSubtitles, etc.) occasionally
                // return duplicate `Id`s across sources, which previously
                // crashed with `IllegalArgumentException: Key "x" was already
                // used`. Combining the (stable where unique) id with the
                // index guarantees uniqueness without losing identity on
                // ordinary list updates.
                itemsIndexed(
                    subtitles,
                    key = { index, sub -> "${sub.id}_$index" },
                    contentType = { _, _ -> "subtitle" },
                ) { index, sub ->
                    SubtitleDownloadItem(
                        subtitle = sub,
                        isLast = index == subtitles.lastIndex,
                        itemCount = subtitles.size,
                        status = downloadingSubtitles[sub.id],
                        onDownload = { onDownload(sub) },
                        onUse = { onUseSubtitle(sub.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SubtitleDownloadItem(
    subtitle: RemoteSubtitleInfo,
    isLast: Boolean,
    itemCount: Int,
    status: SubtitleDownloadStatus?,
    onDownload: () -> Unit,
    onUse: () -> Unit,
) {
    val shape = when {
        itemCount == 1 -> ShapeCache.smooth16
        isLast -> com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape(if (isLast) itemCount - 1 else 0, itemCount)
        else -> ShapeCache.smooth8
    }
    val focusState = rememberTvFocusState(focusedScale = 1.02f)
    // While a download is in flight or already done, the row itself no longer
    // re-triggers a download — the right-side slot drives the next action
    // (spinner while working, "Use" once done). DELAYED/FAILED let a tap retry.
    val isDownloadActive = status?.state == SubtitleDownloadState.DOWNLOADING ||
        status?.state == SubtitleDownloadState.DOWNLOADED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, shape)
            .clickable(enabled = !isDownloadActive) { onDownload() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                subtitle.name ?: subtitle.language ?: stringResource(Res.string.player_video_unknown),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                ),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (subtitle.isHashMatch) {
                    Text(
                        stringResource(Res.string.player_video_perfect_match),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                subtitle.communityRating?.let {
                    Text(
                        "\u2605 ${"%.1f".format(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                subtitle.language?.let {
                    Text(
                        it.uppercase(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                subtitle.format?.let {
                    Text(
                        it.uppercase(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                subtitle.provider?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        SubtitleDownloadStatusSlot(
            status = status,
            downloadCount = subtitle.downloadCount,
            onUse = onUse,
        )
    }
}

/**
 * Right-side slot of a subtitle row. Renders the download count when idle, or a
 * status-driven affordance otherwise:
 * - [SubtitleDownloadState.DOWNLOADING] → spinner.
 * - [SubtitleDownloadState.DELAYED] → clock + "Taking a while…" (tap the row retries).
 * - [SubtitleDownloadState.DOWNLOADED] → check + "Use" button (opens the track picker).
 * - [SubtitleDownloadState.FAILED] → alert + short message (tap the row retries).
 */
@Composable
private fun SubtitleDownloadStatusSlot(
    status: SubtitleDownloadStatus?,
    downloadCount: Int,
    onUse: () -> Unit,
) {
    when (status?.state) {
        SubtitleDownloadState.DOWNLOADING -> JellyPlayLoadingIndicator(
            modifier = Modifier.size(18.dp),
        )
        SubtitleDownloadState.DELAYED -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Tabler.Outline.Clock,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(Res.string.player_video_taking_a_while),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SubtitleDownloadState.DOWNLOADED -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Tabler.Outline.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            FilledTonalButton(
                onClick = onUse,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 0.dp,
                ),
            ) {
                Text(stringResource(Res.string.player_video_use), style = MaterialTheme.typography.labelLarge)
            }
        }
        SubtitleDownloadState.DOWNLOADED_DEVICE_ONLY -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Subtitle is usable on-device but not synced to the server — badge
            // it with the device-only note + a "Use" affordance (the durable
            // copy still backs the side-load).
            Icon(
                imageVector = Tabler.Outline.DeviceMobile,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(Res.string.player_video_subtitle_downloaded_device_only),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = onUse,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 0.dp,
                ),
            ) {
                Text(stringResource(Res.string.player_video_use), style = MaterialTheme.typography.labelLarge)
            }
        }
        SubtitleDownloadState.FAILED -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Tabler.Outline.AlertCircle,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                stringResource(Res.string.player_video_failed_tap_to_retry),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        // null (idle): show the remote download count, if any — unchanged from
        // the pre-status-slot row.
        null -> if (downloadCount > 0) {
            Text(
                "$downloadCount",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// endregion

// region Search tab ----------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTab(
    cultures: List<CultureInfo>,
    defaultLanguage: String,
    results: List<RemoteSubtitleInfo>,
    isLoading: Boolean,
    hasSearched: Boolean,
    searchError: String?,
    onSearch: (String) -> Unit,
    onDownload: (RemoteSubtitleInfo) -> Unit,
    downloadingSubtitles: Map<String, SubtitleDownloadStatus>,
    onUseSubtitle: (String) -> Unit,
    isTv: Boolean,
    focusRequester: FocusRequester,
    // Multi-provider search state.
    providerSearchResults: List<com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult>,
    providerSearchErrors: Map<com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind, String>,
    configuredProviders: Set<com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind>,
    onSearchAllProviders: (String) -> Unit,
    onDownloadProviderSubtitle: (com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchLanguage by rememberSaveable { mutableStateOf(defaultLanguage) }
    val searchBtnFocus = rememberTvFocusState()
    // External providers configured beyond Jellyfin → use the merged multi-provider flow.
    val hasExternalProviders = configuredProviders.size > 1

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LanguageDropdown(
                language = searchLanguage,
                onLanguageChange = { searchLanguage = it },
                cultures = cultures,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(
                onClick = {
                    if (hasExternalProviders) onSearchAllProviders(searchLanguage) else onSearch(searchLanguage)
                },
                modifier = Modifier
                    .ifElse(isTv, Modifier.focusRequester(focusRequester))
                    .then(searchBtnFocus.focusModifier)
                    .tvFocusIndicator(searchBtnFocus, ShapeCache.smoothPill),
            ) {
                Icon(Tabler.Outline.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(Res.string.player_video_search))
            }
        }
        Spacer(Modifier.height(12.dp))

        if (hasExternalProviders) {
            ProviderSearchResults(
                modifier = Modifier.weight(1f, fill = false),
                results = providerSearchResults,
                errors = providerSearchErrors,
                isLoading = isLoading,
                hasSearched = hasSearched,
                configuredProviders = configuredProviders,
                downloadingSubtitles = downloadingSubtitles,
                onDownload = onDownloadProviderSubtitle,
                onUse = { result ->
                    // Jellyfin rows key their download status + ready hints on
                    // the plain remote-subtitle id; external rows on the
                    // composite "provider:id" key. Both routes end in the same
                    // "activate this subtitle" action.
                    onUseSubtitle(result.jellyfinInfo?.id ?: providerSubtitleRowKey(result.provider, result.id))
                },
            )
        } else {
            LegacySearchResults(
                modifier = Modifier.weight(1f, fill = false),
                results = results,
                isLoading = isLoading,
                hasSearched = hasSearched,
                searchError = searchError,
                downloadingSubtitles = downloadingSubtitles,
                onDownload = onDownload,
                onUseSubtitle = onUseSubtitle,
            )
        }
    }
}

// endregion

// region Legacy (Jellyfin-only) search results — used when no external providers configured

@Composable
private fun LegacySearchResults(
    results: List<RemoteSubtitleInfo>,
    isLoading: Boolean,
    hasSearched: Boolean,
    searchError: String?,
    downloadingSubtitles: Map<String, SubtitleDownloadStatus>,
    onDownload: (RemoteSubtitleInfo) -> Unit,
    onUseSubtitle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        when {
            isLoading -> JellyPlayLoadingIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
            )
        // Not an empty result — see [SubtitleFetchErrorBlock].
        searchError != null -> SubtitleFetchErrorBlock(
            titleRes = Res.string.player_video_search_failed,
            message = searchError,
            modifier = Modifier.fillMaxWidth().height(180.dp),
        )
        hasSearched && results.isEmpty() -> Box(
            modifier = Modifier.fillMaxWidth().height(180.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(Res.string.player_video_no_results_found),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        results.isEmpty() -> Box(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(Res.string.player_video_pick_language_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().verticalWrapAround(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(
                results,
                key = { index, sub -> "${sub.id}_$index" },
                contentType = { _, _ -> "searchedSubtitle" },
            ) { index, sub ->
                SubtitleDownloadItem(
                    subtitle = sub,
                    isLast = index == results.lastIndex,
                    itemCount = results.size,
                    status = downloadingSubtitles[sub.id],
                    onDownload = { onDownload(sub) },
                    onUse = { onUseSubtitle(sub.id) },
                )
            }
        }
    }
    }
}

// endregion

// region Multi-provider search results (Jellyfin + Wyzie + OpenSubtitles) ----

@Composable
private fun ProviderSearchResults(
    results: List<com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult>,
    errors: Map<com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind, String>,
    isLoading: Boolean,
    hasSearched: Boolean,
    configuredProviders: Set<com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind>,
    downloadingSubtitles: Map<String, SubtitleDownloadStatus>,
    onDownload: (com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult) -> Unit,
    onUse: (com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable {
        mutableStateOf<com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind?>(null)
    }
    val visible = remember(results, filter) {
        if (filter == null) results else results.filter { it.provider == filter }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        // Provider filter chips — only shown when more than one provider is
        // configured. "All" + one chip per configured provider. A chip with an
        // error gets an error tint so the user sees which provider failed.
        ProviderFilterRow(
            configuredProviders = configuredProviders,
            errors = errors,
            selected = filter,
            onSelect = { filter = it },
        )
        Spacer(Modifier.height(8.dp))
        when {
            // Partial results from at least one provider: render them
            // immediately, even while other providers are still loading. This is
            // the core fix — a slow/retrying provider can no longer hide its
            // siblings' results behind a spinner. Errors that already landed are
            // shown inline below the list; if still loading, a compact spinner
            // signals the remaining providers are pending.
            visible.isNotEmpty() -> Column(modifier = Modifier.weight(1f, fill = false)) {
                LazyColumn(
                    modifier = Modifier.verticalWrapAround().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(
                        visible,
                        key = { index, r -> "${r.provider.name}_${r.id}_$index" },
                        contentType = { _, _ -> "providerSubtitle" },
                    ) { index, r ->
                        ProviderSubtitleRow(
                            result = r,
                            isLast = index == visible.lastIndex,
                            itemCount = visible.size,
                            status = downloadingSubtitles[providerSubtitleRowKey(r.provider, r.id)],
                            onDownload = { onDownload(r) },
                            onUse = { onUse(r) },
                        )
                    }
                }
                if (errors.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    errors.forEach { (kind, msg) ->
                        Text(
                            "${providerDisplayName(kind)}: $msg".trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (isLoading) {
                    Spacer(Modifier.height(4.dp))
                    JellyPlayLoadingIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(8.dp),
                    )
                }
            }
            // Provider errors landed but no results yet (other providers still
            // pending or all failed): show the errors immediately rather than a
            // blank spinner, plus a spinner if more providers are still loading.
            errors.isNotEmpty() -> Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = if (isLoading) 8.dp else 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (isLoading) {
                    JellyPlayLoadingIndicator(
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                } else {
                    Text(
                        stringResource(Res.string.player_video_search_failed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                errors.forEach { (_, msg) ->
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            isLoading -> JellyPlayLoadingIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(24.dp),
            )
            hasSearched && visible.isEmpty() && errors.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(Res.string.player_video_no_results_found),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            visible.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(Res.string.player_video_pick_language_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProviderFilterRow(
    configuredProviders: Set<com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind>,
    errors: Map<com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind, String>,
    selected: com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind?,
    onSelect: (com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(Res.string.player_video_subtitle_provider_all)) },
        )
        configuredProviders.forEach { kind ->
            val hasError = errors[kind] != null
            FilterChip(
                selected = selected == kind,
                onClick = { onSelect(if (selected == kind) null else kind) },
                label = { Text(providerDisplayName(kind)) },
                colors = if (hasError) androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    labelColor = MaterialTheme.colorScheme.error,
                ) else androidx.compose.material3.FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

@Composable
private fun providerDisplayName(kind: SubtitleProviderKind): String = kind.localizedDisplayName()

@Composable
private fun ProviderSubtitleRow(
    result: com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult,
    isLast: Boolean,
    itemCount: Int,
    status: SubtitleDownloadStatus?,
    onDownload: () -> Unit,
    onUse: () -> Unit,
) {
    val shape = when {
        itemCount == 1 -> ShapeCache.smooth16
        isLast -> com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape(
            if (isLast) itemCount - 1 else 0, itemCount,
        )
        else -> ShapeCache.smooth8
    }
    val focusState = rememberTvFocusState(focusedScale = 1.02f)
    // While a download is in flight or already done, the row itself no longer
    // re-triggers a download — the right-side slot drives the next action
    // (spinner while working, "Use" once done). DELAYED/FAILED let a tap retry.
    val isDownloadActive = status?.state == SubtitleDownloadState.DOWNLOADING ||
        status?.state == SubtitleDownloadState.DOWNLOADED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, shape)
            .clickable(enabled = !isDownloadActive) { onDownload() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Provider badge.
                Surface(
                    shape = ShapeCache.smooth8,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(
                        text = providerShortName(result.provider),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = result.displayName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                )
            }
            // Cross-provider metadata (SDH/FORCED/AI badges + language · format ·
            // rating · downloads · fps · perfect-match). The player's right-side
            // status slot already surfaces the download count when idle, so this
            // line omits it to avoid duplication.
            SubtitleResultMetadata(
                result = result,
                perfectMatchLabel = stringResource(Res.string.player_video_perfect_match),
                includeDownloadCount = false,
            )
            result.releaseName?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        SubtitleDownloadStatusSlot(
            status = status,
            downloadCount = result.downloadCount ?: 0,
            onUse = onUse,
        )
    }
}

@Composable
private fun providerShortName(kind: com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind): String =
    when (kind) {
        com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind.JELLYFIN -> "JF"
        com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind.WYZIE -> "WYZ"
        com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind.OPENSUBTITLES -> "OS"
    }

// endregion

// region Upload tab ----------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadTab(
    cultures: List<CultureInfo>,
    defaultLanguage: String,
    isUploading: Boolean,
    onUpload: (String, String, String?, Boolean, Boolean) -> Unit,
    isTv: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var selectedLanguage by rememberSaveable { mutableStateOf(defaultLanguage) }
    var isForced by rememberSaveable { mutableStateOf(false) }
    var isHearingImpaired by rememberSaveable { mutableStateOf(false) }
    // Stored as strings so they survive a config change mid-upload, consistent
    // with the saveable tab/language state above (a Uri isn't Saveable by default).
    var selectedFile by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedFileName by rememberSaveable { mutableStateOf("") }
    val selectFileBtnFocus = rememberTvFocusState()

    // KMP seam (wave 7C): the SAF launcher moved behind the
    // rememberSubtitleUploadPicker expect/actual (EditorFilePicker precedent);
    // the Android actual is the verbatim OpenDocument launcher.
    val filePicker = rememberSubtitleUploadPicker { uri, fileName ->
        selectedFile = uri
        selectedFileName = fileName
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledTonalButton(
            onClick = { filePicker?.launch() },
            modifier = Modifier
                .fillMaxWidth()
                .ifElse(isTv, Modifier.focusRequester(focusRequester))
                .then(selectFileBtnFocus.focusModifier)
                .tvFocusIndicator(selectFileBtnFocus, ShapeCache.smoothPill),
            enabled = !isUploading,
        ) {
            Icon(Tabler.Outline.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (selectedFile != null) stringResource(Res.string.player_video_change_file, selectedFileName) else stringResource(Res.string.player_video_select_file))
        }

        LanguageDropdown(
            language = selectedLanguage,
            onLanguageChange = { selectedLanguage = it },
            cultures = cultures,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(Res.string.player_video_language),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isForced, onCheckedChange = { isForced = it }, enabled = !isUploading)
            Text(stringResource(Res.string.player_video_forced_subtitle))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isHearingImpaired, onCheckedChange = { isHearingImpaired = it }, enabled = !isUploading)
            Text(stringResource(Res.string.player_video_hearing_impaired))
        }

        androidx.compose.material3.Button(
            onClick = {
                selectedFile?.let { uriStr ->
                    onUpload(uriStr, selectedFileName, selectedLanguage, isForced, isHearingImpaired)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isUploading && selectedFile != null && selectedLanguage.isNotBlank(),
        ) {
            if (isUploading) {
                Text(stringResource(Res.string.player_video_uploading))
            } else {
                Text(stringResource(Res.string.player_video_upload))
            }
        }
    }
}

// endregion

// region Shared language dropdown -------------------------------------------

private val DEFAULT_SUBTITLE_CULTURES = listOf(
    CultureInfo(name = "eng", displayName = "English", twoLetterISOLanguageName = "en", threeLetterISOLanguageName = "eng"),
    CultureInfo(name = "spa", displayName = "Spanish", twoLetterISOLanguageName = "es", threeLetterISOLanguageName = "spa"),
    CultureInfo(name = "fre", displayName = "French", twoLetterISOLanguageName = "fr", threeLetterISOLanguageName = "fre"),
    CultureInfo(name = "ger", displayName = "German", twoLetterISOLanguageName = "de", threeLetterISOLanguageName = "ger"),
    CultureInfo(name = "ita", displayName = "Italian", twoLetterISOLanguageName = "it", threeLetterISOLanguageName = "ita"),
    CultureInfo(name = "por", displayName = "Portuguese", twoLetterISOLanguageName = "pt", threeLetterISOLanguageName = "por"),
    CultureInfo(name = "rus", displayName = "Russian", twoLetterISOLanguageName = "ru", threeLetterISOLanguageName = "rus"),
    CultureInfo(name = "zho", displayName = "Chinese", twoLetterISOLanguageName = "zh", threeLetterISOLanguageName = "zho"),
    CultureInfo(name = "jpn", displayName = "Japanese", twoLetterISOLanguageName = "ja", threeLetterISOLanguageName = "jpn"),
    CultureInfo(name = "kor", displayName = "Korean", twoLetterISOLanguageName = "ko", threeLetterISOLanguageName = "kor"),
    CultureInfo(name = "dut", displayName = "Dutch", twoLetterISOLanguageName = "nl", threeLetterISOLanguageName = "dut"),
    CultureInfo(name = "pol", displayName = "Polish", twoLetterISOLanguageName = "pl", threeLetterISOLanguageName = "pol"),
    CultureInfo(name = "ara", displayName = "Arabic", twoLetterISOLanguageName = "ar", threeLetterISOLanguageName = "ara"),
    CultureInfo(name = "hin", displayName = "Hindi", twoLetterISOLanguageName = "hi", threeLetterISOLanguageName = "hin"),
    CultureInfo(name = "tur", displayName = "Turkish", twoLetterISOLanguageName = "tr", threeLetterISOLanguageName = "tur"),
    CultureInfo(name = "swe", displayName = "Swedish", twoLetterISOLanguageName = "sv", threeLetterISOLanguageName = "swe"),
)

/**
 * Editable language field backed by the server's [CultureInfo] list, mirroring
 * the editor's subtitle sheets. Free text is still allowed so users can type a
 * code the server didn't return.
 *
 * Uses the basic [DropdownMenu] rather than `ExposedDropdownMenuBox`. The
 * exposed-menu position provider crashes inside the constrained bottom sheet
 * when the IME inset animation shrinks the available vertical space below its
 * margin floor (IllegalArgumentException: Cannot coerce value to an empty
 * range). The anchored [DropdownMenu] positions relative to the field bounds
 * and is unaffected.
 */
@Composable
private fun LanguageDropdown(
    language: String,
    onLanguageChange: (String) -> Unit,
    cultures: List<CultureInfo>,
    modifier: Modifier = Modifier,
    label: String = stringResource(Res.string.player_video_language),
) {
    var expanded by remember { mutableStateOf(false) }
    val effectiveCultures = remember(cultures) {
        if (cultures.isNotEmpty()) cultures else DEFAULT_SUBTITLE_CULTURES
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = language,
                onValueChange = onLanguageChange,
                label = { Text(label) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Box {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = Tabler.Outline.ChevronDown,
                        contentDescription = stringResource(Res.string.player_video_language),
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.heightIn(max = 300.dp),
                ) {
                    effectiveCultures.forEach { culture ->
                        val textToShow = if (culture.displayName.isNotBlank()) {
                            "${culture.displayName} (${culture.name})"
                        } else {
                            culture.name
                        }
                        DropdownMenuItem(
                            text = { Text(textToShow) },
                            onClick = {
                                onLanguageChange(culture.name)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

// endregion

