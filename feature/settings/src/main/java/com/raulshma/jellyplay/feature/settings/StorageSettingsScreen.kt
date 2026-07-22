package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.model.MeteredNetworkBehavior
import com.raulshma.jellyplay.core.designsystem.theme.smoothCornerShape
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

private fun streamingQualityLabel(quality: StreamingQuality): String = when (quality) {
    StreamingQuality.AUTO -> "Auto (adaptive)"
    StreamingQuality.LOW_360P -> "360p (Low)"
    StreamingQuality.SD_480P -> "480p (SD)"
    StreamingQuality.HD_720P -> "720p (HD)"
    StreamingQuality.FHD_1080P -> "1080p (Full HD)"
    StreamingQuality.UHD_4K -> "4K (Ultra HD)"
}

private val STORAGE_CACHE_GROUP_IDS = setOf("clear_cache", "clear_image_cache", "wifi_only_downloads", "download_connections", "max_concurrent_downloads", "auto_delete_cache", "max_cache_size")
private val STORAGE_NETWORK_GROUP_IDS = setOf("offline_mode", "auto_offline", "adaptive_bitrate", "bandwidth_cap", "metered_network_behavior", "cellular_streaming_quality", "cellular_download_warning", "data_saver", "network_timeout", "verbose_logging", "user_data_sync")
private val STORAGE_DOWNLOADS_GROUP_IDS = setOf("download_quality", "smart_downloads", "auto_download_new_episodes", "download_schedule", "download_schedule_start", "download_schedule_end", "download_schedule_wifi_only", "max_download_storage_limit", "download_storage_location")

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun StorageSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: StorageSettingsViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()
    var showQualityPicker by remember { mutableStateOf(false) }

    // #11 — cache size computed lazily on screen entry, not at VM construction.
    LaunchedEffect(Unit) { viewModel.refreshCacheSize() }

    val scrollState = rememberLazyListState()
    val scrollIndex = remember(highlightSettingId) {
        when (highlightSettingId) {
            in STORAGE_CACHE_GROUP_IDS -> 0
            in STORAGE_NETWORK_GROUP_IDS -> 1
            in STORAGE_DOWNLOADS_GROUP_IDS -> 2
            else -> -1
        }
    }

    // Phase 1 (coarse): scroll the containing group into the LazyColumn's composition window so the
    // target item is actually composed — items in off-screen groups (later sections) are otherwise
    // never mounted and their bringIntoViewRequester has no target. Phase 2 (centering) is then
    // performed by the highlighted item itself via CenterBringIntoViewSpec.
    LaunchedEffect(scrollIndex) {
        if (scrollIndex >= 0) {
            try {
                scrollState.animateScrollToItem(scrollIndex)
            } catch (_: Exception) {}
        }
    }

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = "storage_init",
    )

    JellyPlayScreenScaffold(
        title = stringResource(R.string.settings_downloads_storage_title),
        onBack = onBack,
        backgroundColor = backgroundColor,
        actions = {
            AdvancedSettingsToggleButton(
                showAdvanced = showAdvanced,
                onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
            )
        },
    ) { innerPadding ->
        // Center a highlighted (search-navigated) setting in the viewport instead of parking it
        // at the bottom edge, which is the default BringIntoViewSpec behaviour.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides
                com.raulshma.jellyplay.core.ui.tv.CenterBringIntoViewSpec
        ) {
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
                    icon = Tabler.Outline.Database,
                    title = stringResource(R.string.settings_storage),
                    summary = { stringResource(R.string.settings_cache_summary, viewModel.cacheSizeMb) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    val breakdown = viewModel.storageBreakdown
                    if (breakdown.totalMb > 0) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_storage_breakdown),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            val barHeight = 12.dp
                            val totalForFractions = breakdown.totalMb.coerceAtLeast(1L).toFloat()
                            val cacheFraction = breakdown.cacheMb.toFloat() / totalForFractions
                            val downloadsFraction = breakdown.downloadsMb.toFloat() / totalForFractions
                            val imagesFraction = breakdown.imagesMb.toFloat() / totalForFractions

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(barHeight)
                                    .clip(smoothCornerShape(6.dp)),
                            ) {
                                if (cacheFraction > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .weight(cacheFraction)
                                            .fillMaxHeight()
                                            .background(MaterialTheme.colorScheme.primary),
                                    )
                                }
                                if (downloadsFraction > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .weight(downloadsFraction)
                                            .fillMaxHeight()
                                            .background(MaterialTheme.colorScheme.tertiary),
                                    )
                                }
                                if (imagesFraction > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .weight(imagesFraction)
                                            .fillMaxHeight()
                                            .background(MaterialTheme.colorScheme.secondary),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                LegendItem(color = MaterialTheme.colorScheme.primary, label = stringResource(R.string.settings_legend_cache, breakdown.cacheMb))
                                LegendItem(color = MaterialTheme.colorScheme.tertiary, label = stringResource(R.string.settings_legend_downloads, breakdown.downloadsMb))
                                LegendItem(color = MaterialTheme.colorScheme.secondary, label = stringResource(R.string.settings_legend_images, breakdown.imagesMb))
                            }
                        }
                    }

                    val storageTotal = if (showAdvanced) 8 else 3
                    var storageIdx = 0
                    SettingInfoItem(
                        icon = Tabler.Outline.Database,
                        title = stringResource(R.string.settings_cache_used),
                        subtitle = "${viewModel.cacheSizeMb} MB",
                        index = storageIdx++, count = storageTotal,
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Trash,
                        title = stringResource(R.string.settings_clear_cache),
                        subtitle = stringResource(R.string.settings_clear_cache_subtitle),
                        highlighted = highlightSettingId == "clear_cache",
                        index = storageIdx++, count = storageTotal,
                        onClick = { viewModel.clearCache() },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Photo,
                        title = stringResource(R.string.settings_clear_image_cache),
                        subtitle = stringResource(R.string.settings_clear_image_cache_subtitle),
                        highlighted = highlightSettingId == "clear_image_cache",
                        index = storageIdx++, count = storageTotal,
                        onClick = { viewModel.clearImageCache() },
                    )
                    if (showAdvanced) {
                        SettingToggleItem(
                            icon = Tabler.Outline.Wifi,
                            title = stringResource(R.string.settings_wifi_only),
                            subtitle = if (preferences.wifiOnlyDownloads) stringResource(R.string.settings_wifi_only_on) else stringResource(R.string.settings_wifi_only_off),
                            checked = preferences.wifiOnlyDownloads,
                            highlighted = highlightSettingId == "wifi_only_downloads",
                            index = storageIdx++, count = storageTotal,
                            onCheckedChange = { viewModel.setWifiOnlyDownloads(it) },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Download,
                            title = stringResource(R.string.settings_connections_per_download),
                            subtitle = stringResource(R.string.settings_connections_per_download_subtitle),
                            trailingText = "${preferences.downloadConnections}",
                            highlighted = highlightSettingId == "download_connections",
                            index = storageIdx++, count = storageTotal,
                            onClick = {
                                val options = listOf(1, 2, 4, 8, 12, 16)
                                val currentIndex = options.indexOf(preferences.downloadConnections)
                                val nextIndex = if (currentIndex == -1) 2 else (currentIndex + 1) % options.size
                                viewModel.setDownloadConnections(options[nextIndex])
                            },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.ArrowBarToDown,
                            title = stringResource(R.string.settings_max_simultaneous_downloads),
                            subtitle = stringResource(R.string.settings_max_simultaneous_downloads_subtitle),
                            trailingText = "${preferences.maxConcurrentDownloads}",
                            highlighted = highlightSettingId == "max_concurrent_downloads",
                            index = storageIdx++, count = storageTotal,
                            onClick = {
                                val options = listOf(1, 2, 3, 4, 5, 6)
                                val currentIndex = options.indexOf(preferences.maxConcurrentDownloads)
                                val nextIndex = if (currentIndex == -1) 2 else (currentIndex + 1) % options.size
                                viewModel.setMaxConcurrentDownloads(options[nextIndex])
                            },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Refresh,
                            title = stringResource(R.string.settings_auto_delete_cache),
                            subtitle = if (preferences.autoDeleteCache) stringResource(R.string.settings_auto_delete_on) else stringResource(R.string.settings_auto_delete_off),
                            checked = preferences.autoDeleteCache,
                            highlighted = highlightSettingId == "auto_delete_cache",
                            index = storageIdx++, count = storageTotal,
                            onCheckedChange = { viewModel.setAutoDeleteCache(it) },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Database,
                            title = stringResource(R.string.settings_max_cache_size),
                            subtitle = stringResource(R.string.settings_max_cache_size_subtitle),
                            trailingText = if (preferences.maxCacheSizeMb == 0) stringResource(R.string.settings_unlimited) else "${preferences.maxCacheSizeMb} MB",
                            highlighted = highlightSettingId == "max_cache_size",
                            index = storageIdx, count = storageTotal,
                            onClick = {
                                val sizes = listOf(0, 250, 500, 1000, 2000, 5000)
                                val currentIndex = sizes.indexOf(preferences.maxCacheSizeMb)
                                val nextIndex = (currentIndex + 1) % sizes.size
                                viewModel.setMaxCacheSize(sizes[nextIndex])
                            },
                        )
                    }
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.Cloud,
                    title = stringResource(R.string.settings_network_offline),
                    summary = {
                        val status = if (preferences.manualOfflineEnabled) stringResource(R.string.settings_offline_mode) else stringResource(R.string.settings_online)
                        stringResource(R.string.settings_status_value, status)
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in STORAGE_NETWORK_GROUP_IDS,
                ) {
                    val networkTotal = 11
                    var networkIdx = 0

                    SettingToggleItem(
                        icon = Tabler.Outline.CloudOff,
                        title = stringResource(R.string.settings_offline_mode),
                        subtitle = stringResource(R.string.settings_offline_mode_subtitle),
                        checked = preferences.manualOfflineEnabled,
                        highlighted = highlightSettingId == "offline_mode",
                        index = networkIdx++, count = networkTotal,
                        onCheckedChange = { viewModel.setManualOffline(it) },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.WifiOff,
                        title = stringResource(R.string.settings_auto_offline),
                        subtitle = stringResource(R.string.settings_auto_offline_subtitle),
                        checked = preferences.autoOfflineEnabled,
                        highlighted = highlightSettingId == "auto_offline",
                        index = networkIdx++, count = networkTotal,
                        onCheckedChange = { viewModel.setAutoOfflineEnabled(it) },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Gauge,
                        title = stringResource(R.string.settings_adaptive_bitrate),
                        subtitle = stringResource(R.string.settings_adaptive_bitrate_subtitle),
                        checked = preferences.adaptiveBitrateEnabled,
                        highlighted = highlightSettingId == "adaptive_bitrate",
                        index = networkIdx++, count = networkTotal,
                        onCheckedChange = { viewModel.setAdaptiveBitrateEnabled(it) },
                    )

                    val caps = listOf(0L, 1_000_000L, 2_000_000L, 5_000_000L, 10_000_000L, 20_000_000L)
                    val capLabel = if (preferences.manualBandwidthCap == 0L) stringResource(R.string.settings_unlimited) else "${preferences.manualBandwidthCap / 1_000_000L} Mbps"

                    SettingListItem(
                        icon = Tabler.Outline.Lock,
                        title = stringResource(R.string.settings_manual_bandwidth_cap),
                        subtitle = stringResource(R.string.settings_manual_bandwidth_cap_subtitle),
                        trailingText = capLabel,
                        highlighted = highlightSettingId == "bandwidth_cap",
                        index = networkIdx++, count = networkTotal,
                        onClick = {
                            val currentIndex = caps.indexOf(preferences.manualBandwidthCap)
                            val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % caps.size
                            viewModel.setManualBandwidthCap(caps[nextIndex])
                        },
                    )

                    SettingListItem(
                        icon = Tabler.Outline.Compass,
                        title = stringResource(R.string.settings_metered_network_behavior),
                        subtitle = stringResource(R.string.settings_metered_network_behavior_subtitle),
                        trailingText = preferences.meteredNetworkBehavior.displayName,
                        highlighted = highlightSettingId == "metered_network_behavior",
                        index = networkIdx++, count = networkTotal,
                        onClick = {
                            val options = MeteredNetworkBehavior.entries
                            val currentIndex = options.indexOf(preferences.meteredNetworkBehavior)
                            val nextIndex = (currentIndex + 1) % options.size
                            viewModel.setMeteredNetworkBehavior(options[nextIndex])
                        },
                    )

                    SettingListItem(
                        icon = Tabler.Outline.DeviceMobile,
                        title = stringResource(R.string.settings_cellular_streaming_quality),
                        subtitle = stringResource(R.string.settings_cellular_streaming_quality_subtitle),
                        trailingText = streamingQualityLabel(preferences.cellularStreamingQuality),
                        highlighted = highlightSettingId == "cellular_streaming_quality",
                        index = networkIdx++, count = networkTotal,
                        onClick = {
                            val options = StreamingQuality.entries
                            val currentIndex = options.indexOf(preferences.cellularStreamingQuality)
                            val nextIndex = (currentIndex + 1) % options.size
                            viewModel.setCellularStreamingQuality(options[nextIndex])
                        },
                    )

                    val downloadWarningOptions = listOf(0, 100, 250, 500, 1000, 2000)
                    val downloadWarningLabel = if (preferences.cellularDownloadSizeWarningMb == 0) "Disabled" else "${preferences.cellularDownloadSizeWarningMb} MB"
                    SettingListItem(
                        icon = Tabler.Outline.AlertTriangle,
                        title = stringResource(R.string.settings_cellular_download_size_warning),
                        subtitle = "Warn before downloading large files on cellular",
                        trailingText = downloadWarningLabel,
                        highlighted = highlightSettingId == "cellular_download_warning",
                        index = networkIdx++, count = networkTotal,
                        onClick = {
                            val currentIndex = downloadWarningOptions.indexOf(preferences.cellularDownloadSizeWarningMb)
                            val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % downloadWarningOptions.size
                            viewModel.setCellularDownloadSizeWarningMb(downloadWarningOptions[nextIndex])
                        },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Gauge,
                        title = "Data Saver Mode",
                        subtitle = "Lower image resolutions, cellular cap, disable auto-downloads",
                        checked = preferences.dataSaverEnabled,
                        highlighted = highlightSettingId == "data_saver",
                        index = networkIdx++, count = networkTotal,
                        onCheckedChange = { viewModel.setDataSaverEnabled(it) },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Clock,
                        title = "Network Timeouts",
                        subtitle = "Connect/read/write timeout preset (restart to apply)",
                        trailingText = preferences.networkTimeoutPreset.displayName.substringBefore(" ("),
                        highlighted = highlightSettingId == "network_timeout",
                        index = networkIdx++, count = networkTotal,
                        onClick = {
                            val options = com.raulshma.jellyplay.core.model.NetworkTimeoutPreset.entries
                            val currentIndex = options.indexOf(preferences.networkTimeoutPreset)
                            val nextIndex = (currentIndex + 1) % options.size
                            viewModel.setNetworkTimeoutPreset(options[nextIndex])
                        },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Code,
                        title = "Verbose Network Logging",
                        subtitle = if (preferences.verboseNetworkLogging) "Logs request headers (restart to apply)" else "Standard logging",
                        checked = preferences.verboseNetworkLogging,
                        highlighted = highlightSettingId == "verbose_logging",
                        index = networkIdx++, count = networkTotal,
                        onCheckedChange = { viewModel.setVerboseNetworkLogging(it) },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Refresh,
                        title = "Background Sync",
                        subtitle = if (preferences.userDataSyncEnabled) "Periodically refresh favourites / played / progress from server" else "No background user-data refresh",
                        checked = preferences.userDataSyncEnabled,
                        highlighted = highlightSettingId == "user_data_sync",
                        index = networkIdx++, count = networkTotal,
                        onCheckedChange = { viewModel.setUserDataSyncEnabled(it) },
                    )
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.Download,
                    title = "Downloads",
                    summary = { "Quality: ${preferences.downloadQuality.displayName}" },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    val downloadTotal = 7
                    var downloadIdx = 0

                    SettingListItem(
                        icon = Tabler.Outline.Video,
                        title = "Download Quality",
                        subtitle = "Preferred video quality for downloads",
                        trailingText = preferences.downloadQuality.displayName,
                        highlighted = highlightSettingId == "download_quality",
                        index = downloadIdx++, count = downloadTotal,
                        onClick = { showQualityPicker = true }
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Trash,
                        title = "Smart Downloads",
                        subtitle = "Auto-delete watched episodes (>= 95%)",
                        checked = preferences.smartDownloadsEnabled,
                        highlighted = highlightSettingId == "smart_downloads",
                        index = downloadIdx++, count = downloadTotal,
                        onCheckedChange = { viewModel.setSmartDownloadsEnabled(it) }
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Download,
                        title = "Auto-Download New Episodes",
                        subtitle = "Automatically download next episode of active series",
                        checked = preferences.autoDownloadNewEpisodes,
                        highlighted = highlightSettingId == "auto_download_new_episodes",
                        index = downloadIdx++, count = downloadTotal,
                        onCheckedChange = { viewModel.setAutoDownloadNewEpisodes(it) }
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Clock,
                        title = "Download Schedule",
                        subtitle = "Only download during specific hours (e.g., overnight)",
                        checked = preferences.downloadScheduleEnabled,
                        highlighted = highlightSettingId == "download_schedule",
                        index = downloadIdx++, count = downloadTotal,
                        onCheckedChange = { viewModel.setDownloadScheduleEnabled(it) }
                    )

                    if (preferences.downloadScheduleEnabled) {
                        SettingListItem(
                            icon = Tabler.Outline.Sun,
                            title = "Schedule Start",
                            subtitle = "Hour when downloads are allowed (24h format)",
                            trailingText = "${preferences.downloadScheduleWindow.startHour}:00",
                            highlighted = highlightSettingId == "download_schedule_start",
                            index = downloadIdx++, count = downloadTotal,
                            onClick = {
                                val current = preferences.downloadScheduleWindow
                                val nextStart = (current.startHour + 1) % 24
                                viewModel.setDownloadScheduleWindow(current.copy(startHour = nextStart))
                            }
                        )

                        SettingListItem(
                            icon = Tabler.Outline.Moon,
                            title = "Schedule End",
                            subtitle = "Hour when downloads stop (24h format)",
                            trailingText = "${preferences.downloadScheduleWindow.endHour}:00",
                            highlighted = highlightSettingId == "download_schedule_end",
                            index = downloadIdx++, count = downloadTotal,
                            onClick = {
                                val current = preferences.downloadScheduleWindow
                                val nextEnd = (current.endHour + 1) % 24
                                viewModel.setDownloadScheduleWindow(current.copy(endHour = nextEnd))
                            }
                        )

                        SettingToggleItem(
                            icon = Tabler.Outline.Wifi,
                            title = "Wi-Fi Only During Schedule",
                            subtitle = "Require unmetered (Wi-Fi) network during the schedule window",
                            checked = preferences.downloadScheduleWindow.wifiOnly,
                            highlighted = highlightSettingId == "download_schedule_wifi_only",
                            index = downloadIdx++, count = downloadTotal,
                            onCheckedChange = {
                                val current = preferences.downloadScheduleWindow
                                viewModel.setDownloadScheduleWindow(current.copy(wifiOnly = it))
                            }
                        )
                    }

                    SettingListItem(
                        icon = Tabler.Outline.Database,
                        title = "Max Download Storage Limit",
                        subtitle = "Restrict size of downloads directory",
                        trailingText = if (preferences.maxDownloadStorageGb == 0) "Unlimited" else "${preferences.maxDownloadStorageGb} GB",
                        highlighted = highlightSettingId == "max_download_storage_limit",
                        index = downloadIdx++, count = downloadTotal,
                        onClick = {
                            val sizes = listOf(0, 5, 10, 20, 50)
                            val currentIndex = sizes.indexOf(preferences.maxDownloadStorageGb)
                            val nextIndex = (currentIndex + 1) % sizes.size
                            viewModel.setMaxDownloadStorageGb(sizes[nextIndex])
                        }
                    )

                    SettingListItem(
                        icon = Tabler.Outline.Folder,
                        title = "Download Storage Location",
                        subtitle = "Folder path for storing downloaded media",
                        trailingText = if (preferences.downloadStorageLocation == "INTERNAL") "Internal Storage" else "External SD Card",
                        highlighted = highlightSettingId == "download_storage_location",
                        index = downloadIdx, count = downloadTotal,
                        onClick = {
                            val nextLoc = if (preferences.downloadStorageLocation == "INTERNAL") "EXTERNAL" else "INTERNAL"
                            viewModel.setDownloadStorageLocation(nextLoc)
                        }
                    )
                }
            }

            if (!showAdvanced) {
                item {
                    HiddenSettingsHint(
                        hiddenCount = 3,
                        onShowAdvanced = { viewModel.setShowAdvancedSettings(true) },
                    )
                }
            }
        }
        }
    }

    if (showQualityPicker) {
        SettingsListPickerSheet(
            title = "Download Quality",
            items = com.raulshma.jellyplay.core.model.DownloadQuality.entries,
            label = { it.displayName },
            isSelected = { it == preferences.downloadQuality },
            onDismiss = { showQualityPicker = false },
            onSelect = {
                viewModel.setDownloadQuality(it)
                showQualityPicker = false
            },
        )
    }
}

@Composable
private fun LegendItem(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(smoothCornerShape(2.dp))
                .background(color),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
