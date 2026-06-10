package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.model.MeteredNetworkBehavior
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StorageSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences = viewModel.preferences
    val showAdvanced = preferences.showAdvancedSettings
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()
    var showQualityPicker by remember { mutableStateOf(false) }

    val scrollState = rememberLazyListState()
    val scrollIndex = remember(highlightSettingId) {
        when (highlightSettingId) {
            in listOf("clear_cache", "wifi_only_downloads", "auto_delete_cache", "max_cache_size") -> 0
            in listOf("offline_mode", "adaptive_bitrate", "bandwidth_cap", "data_saver") -> 1
            in listOf("download_quality", "smart_downloads") -> 2
            else -> -1
        }
    }

    LaunchedEffect(scrollIndex) {
        if (scrollIndex >= 0) {
            try {
                scrollState.animateScrollToItem(scrollIndex)
            } catch (_: Exception) {}
        }
    }

    JellyPlayScreenScaffold(
        title = "Storage",
        onBack = onBack,
        backgroundColor = backgroundColor,
        actions = {
            AdvancedSettingsToggleButton(
                showAdvanced = showAdvanced,
                onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = adaptiveInfo.contentPadding(isTv),
                end = adaptiveInfo.contentPadding(isTv),
                bottom = adaptiveInfo.bottomPadding(isTv),
            ),
        ) {
            item {
                SettingsGroup(
                    icon = Tabler.Outline.Database,
                    title = "Storage",
                    summary = { "Cache: ${viewModel.cacheSizeMb} MB" },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    val storageTotal = if (showAdvanced) 6 else 2
                    var storageIdx = 0
                    SettingInfoItem(
                        icon = Tabler.Outline.Database,
                        title = "Cache Used",
                        subtitle = "${viewModel.cacheSizeMb} MB",
                        index = storageIdx++, count = storageTotal,
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Trash,
                        title = "Clear Cache",
                        subtitle = "Free up storage space",
                        highlighted = highlightSettingId == "clear_cache",
                        index = storageIdx++, count = storageTotal,
                        onClick = { viewModel.clearCache() },
                    )
                    if (showAdvanced) {
                        SettingToggleItem(
                            icon = Tabler.Outline.Wifi,
                            title = "WiFi Only",
                            subtitle = if (preferences.wifiOnlyDownloads) "Downloads only on unmetered networks" else "Downloads on any network",
                            checked = preferences.wifiOnlyDownloads,
                            highlighted = highlightSettingId == "wifi_only_downloads",
                            index = storageIdx++, count = storageTotal,
                            onCheckedChange = { viewModel.setWifiOnlyDownloads(it) },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Download,
                            title = "Download Connection Count",
                            subtitle = "Number of parallel download streams",
                            trailingText = "${preferences.downloadConnections}",
                            index = storageIdx++, count = storageTotal,
                            onClick = {
                                val options = listOf(1, 2, 4, 8, 12, 16)
                                val currentIndex = options.indexOf(preferences.downloadConnections)
                                val nextIndex = if (currentIndex == -1) 2 else (currentIndex + 1) % options.size
                                viewModel.setDownloadConnections(options[nextIndex])
                            },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Refresh,
                            title = "Auto-delete Cache",
                            subtitle = if (preferences.autoDeleteCache) "Automatically clears on low storage" else "Manual cache management",
                            checked = preferences.autoDeleteCache,
                            highlighted = highlightSettingId == "auto_delete_cache",
                            index = storageIdx++, count = storageTotal,
                            onCheckedChange = { viewModel.setAutoDeleteCache(it) },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Database,
                            title = "Max Cache Size",
                            subtitle = "Maximum disk space for caching",
                            trailingText = if (preferences.maxCacheSizeMb == 0) "Unlimited" else "${preferences.maxCacheSizeMb} MB",
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
                    title = "Network & Offline",
                    summary = {
                        val status = if (preferences.manualOfflineEnabled) "Offline Mode" else "Online"
                        "Status: $status"
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in listOf("offline_mode", "adaptive_bitrate", "bandwidth_cap", "data_saver"),
                ) {
                    val networkTotal = 7
                    var networkIdx = 0

                    SettingToggleItem(
                        icon = Tabler.Outline.CloudOff,
                        title = "Offline Mode",
                        subtitle = "Force application to run offline",
                        checked = preferences.manualOfflineEnabled,
                        highlighted = highlightSettingId == "offline_mode",
                        index = networkIdx++, count = networkTotal,
                        onCheckedChange = { viewModel.setManualOffline(it) },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.WifiOff,
                        title = "Auto Offline",
                        subtitle = "Automatically switch to offline when network is lost",
                        checked = preferences.autoOfflineEnabled,
                        index = networkIdx++, count = networkTotal,
                        onCheckedChange = { viewModel.setAutoOfflineEnabled(it) },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Gauge,
                        title = "Adaptive Bitrate",
                        subtitle = "Dynamically adjust playback quality based on network bandwidth",
                        checked = preferences.adaptiveBitrateEnabled,
                        highlighted = highlightSettingId == "adaptive_bitrate",
                        index = networkIdx++, count = networkTotal,
                        onCheckedChange = { viewModel.setAdaptiveBitrateEnabled(it) },
                    )

                    val caps = listOf(0L, 1_000_000L, 2_000_000L, 5_000_000L, 10_000_000L, 20_000_000L)
                    val capLabel = if (preferences.manualBandwidthCap == 0L) "Unlimited" else "${preferences.manualBandwidthCap / 1_000_000L} Mbps"

                    SettingListItem(
                        icon = Tabler.Outline.Lock,
                        title = "Manual Bandwidth Cap",
                        subtitle = "Restrict maximum streaming bandwidth",
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
                        title = "Metered Network Behavior",
                        subtitle = "Behavior when connected to a cellular/metered connection",
                        trailingText = preferences.meteredNetworkBehavior.displayName,
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
                        title = "Cellular Streaming Quality",
                        subtitle = "Preferred video quality profile when on cellular network",
                        trailingText = streamingQualityLabel(preferences.cellularStreamingQuality),
                        index = networkIdx++, count = networkTotal,
                        onClick = {
                            val options = StreamingQuality.entries
                            val currentIndex = options.indexOf(preferences.cellularStreamingQuality)
                            val nextIndex = (currentIndex + 1) % options.size
                            viewModel.setCellularStreamingQuality(options[nextIndex])
                        },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Gauge,
                        title = "Data Saver Mode",
                        subtitle = "Lower image resolutions, cellular cap, disable auto-downloads",
                        checked = preferences.dataSaverEnabled,
                        highlighted = highlightSettingId == "data_saver",
                        index = networkIdx, count = networkTotal,
                        onCheckedChange = { viewModel.setDataSaverEnabled(it) },
                    )
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.Download,
                    title = "Downloads",
                    summary = { "Quality: ${preferences.downloadQuality.displayName}" },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true || highlightSettingId in listOf("download_quality", "smart_downloads"),
                ) {
                    val downloadTotal = 5
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
                        index = downloadIdx++, count = downloadTotal,
                        onCheckedChange = { viewModel.setAutoDownloadNewEpisodes(it) }
                    )

                    SettingListItem(
                        icon = Tabler.Outline.Database,
                        title = "Max Download Storage Limit",
                        subtitle = "Restrict size of downloads directory",
                        trailingText = if (preferences.maxDownloadStorageGb == 0) "Unlimited" else "${preferences.maxDownloadStorageGb} GB",
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
