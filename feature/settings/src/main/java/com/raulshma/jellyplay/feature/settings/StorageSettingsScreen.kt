package com.raulshma.jellyplay.feature.settings

import androidx.annotation.StringRes
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
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@StringRes
private fun streamingQualityLabelRes(quality: StreamingQuality): Int = when (quality) {
    StreamingQuality.AUTO -> R.string.settings_streaming_quality_auto_adaptive
    StreamingQuality.LOW_360P -> R.string.settings_quality_360p_low
    StreamingQuality.SD_480P -> R.string.settings_quality_480p_sd
    StreamingQuality.HD_720P -> R.string.settings_quality_720p_hd
    StreamingQuality.FHD_1080P -> R.string.settings_quality_1080p_full_hd
    StreamingQuality.UHD_4K -> R.string.settings_quality_4k_ultra_hd
}

private val STORAGE_CACHE_GROUP_IDS = setOf("clear_cache", "clear_image_cache", "wifi_only_downloads", "download_connections", "max_concurrent_downloads", "auto_delete_cache", "max_cache_size")
private val STORAGE_NETWORK_GROUP_IDS = setOf("offline_mode", "auto_offline", "adaptive_bitrate", "bandwidth_cap", "metered_network_behavior", "cellular_streaming_quality", "cellular_download_warning", "data_saver", "network_timeout", "verbose_logging", "user_data_sync")
private val STORAGE_DOWNLOADS_GROUP_IDS = setOf("download_quality", "smart_downloads", "auto_download_new_episodes", "download_schedule", "download_schedule_start", "download_schedule_end", "download_schedule_wifi_only", "max_download_storage_limit", "download_storage_location", "auto_delete_after_watch")

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
    var activePicker by remember { mutableStateOf<PickerState<*>?>(null) }

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
                        val connectionsTitle = stringResource(R.string.settings_connections_per_download)
                        SettingListItem(
                            icon = Tabler.Outline.Download,
                            title = connectionsTitle,
                            subtitle = stringResource(R.string.settings_connections_per_download_subtitle),
                            trailingText = "${preferences.downloadConnections}",
                            highlighted = highlightSettingId == "download_connections",
                            index = storageIdx++, count = storageTotal,
                            onClick = {
                                activePicker = PickerState.List(
                                    title = connectionsTitle,
                                    items = listOf(1, 2, 4, 8, 12, 16),
                                    label = { it.toString() },
                                    isSelected = { it == preferences.downloadConnections },
                                    onSelect = { viewModel.setDownloadConnections(it) },
                                )
                            },
                        )
                        val concurrentDownloadsTitle = stringResource(R.string.settings_max_simultaneous_downloads)
                        SettingListItem(
                            icon = Tabler.Outline.ArrowBarToDown,
                            title = concurrentDownloadsTitle,
                            subtitle = stringResource(R.string.settings_max_simultaneous_downloads_subtitle),
                            trailingText = "${preferences.maxConcurrentDownloads}",
                            highlighted = highlightSettingId == "max_concurrent_downloads",
                            index = storageIdx++, count = storageTotal,
                            onClick = {
                                activePicker = PickerState.List(
                                    title = concurrentDownloadsTitle,
                                    items = listOf(1, 2, 3, 4, 5, 6),
                                    label = { it.toString() },
                                    isSelected = { it == preferences.maxConcurrentDownloads },
                                    onSelect = { viewModel.setMaxConcurrentDownloads(it) },
                                )
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
                        val maxCacheSizeTitle = stringResource(R.string.settings_max_cache_size)
                        val maxCacheUnlimited = stringResource(R.string.settings_unlimited)
                        SettingListItem(
                            icon = Tabler.Outline.Database,
                            title = maxCacheSizeTitle,
                            subtitle = stringResource(R.string.settings_max_cache_size_subtitle),
                            trailingText = if (preferences.maxCacheSizeMb == 0) maxCacheUnlimited else "${preferences.maxCacheSizeMb} MB",
                            highlighted = highlightSettingId == "max_cache_size",
                            index = storageIdx, count = storageTotal,
                            onClick = {
                                activePicker = PickerState.List(
                                    title = maxCacheSizeTitle,
                                    items = listOf(0, 250, 500, 1000, 2000, 5000),
                                    label = { if (it == 0) maxCacheUnlimited else "$it MB" },
                                    isSelected = { it == preferences.maxCacheSizeMb },
                                    onSelect = { viewModel.setMaxCacheSize(it) },
                                )
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
                    val bandwidthCapTitle = stringResource(R.string.settings_manual_bandwidth_cap)
                    val bandwidthUnlimited = stringResource(R.string.settings_unlimited)
                    val capLabel = if (preferences.manualBandwidthCap == 0L) bandwidthUnlimited else "${preferences.manualBandwidthCap / 1_000_000L} Mbps"

                    SettingListItem(
                        icon = Tabler.Outline.Lock,
                        title = bandwidthCapTitle,
                        subtitle = stringResource(R.string.settings_manual_bandwidth_cap_subtitle),
                        trailingText = capLabel,
                        highlighted = highlightSettingId == "bandwidth_cap",
                        index = networkIdx++, count = networkTotal,
                        onClick = {
                            activePicker = PickerState.List(
                                title = bandwidthCapTitle,
                                items = caps,
                                label = { if (it == 0L) bandwidthUnlimited else "${it / 1_000_000L} Mbps" },
                                isSelected = { it == preferences.manualBandwidthCap },
                                onSelect = { viewModel.setManualBandwidthCap(it) },
                            )
                        },
                    )

                    val meteredTitle = stringResource(R.string.settings_metered_network_behavior)
                    SettingListItem(
                        icon = Tabler.Outline.Compass,
                        title = meteredTitle,
                        subtitle = stringResource(R.string.settings_metered_network_behavior_subtitle),
                        trailingText = preferences.meteredNetworkBehavior.displayName,
                        highlighted = highlightSettingId == "metered_network_behavior",
                        index = networkIdx++, count = networkTotal,
                        onClick = {
                            activePicker = PickerState.List(
                                title = meteredTitle,
                                items = MeteredNetworkBehavior.entries,
                                label = { it.displayName },
                                isSelected = { it == preferences.meteredNetworkBehavior },
                                onSelect = { viewModel.setMeteredNetworkBehavior(it) },
                            )
                        },
                    )

                    val cellularQualityTitle = stringResource(R.string.settings_cellular_streaming_quality)
                    val qualityLabels = StreamingQuality.entries.associateWith { stringResource(streamingQualityLabelRes(it)) }
                    SettingListItem(
                        icon = Tabler.Outline.DeviceMobile,
                        title = cellularQualityTitle,
                        subtitle = stringResource(R.string.settings_cellular_streaming_quality_subtitle),
                        trailingText = qualityLabels[preferences.cellularStreamingQuality] ?: preferences.cellularStreamingQuality.name,
                        highlighted = highlightSettingId == "cellular_streaming_quality",
                        index = networkIdx++, count = networkTotal,
                        onClick = {
                            activePicker = PickerState.List(
                                title = cellularQualityTitle,
                                items = StreamingQuality.entries,
                                label = { qualityLabels[it] ?: it.name },
                                isSelected = { it == preferences.cellularStreamingQuality },
                                onSelect = { viewModel.setCellularStreamingQuality(it) },
                            )
                        },
                    )

                    val downloadWarningTitle = stringResource(R.string.settings_cellular_download_size_warning)
                    val disabledLabel = stringResource(R.string.settings_disabled)
                    val downloadWarningLabel = if (preferences.cellularDownloadSizeWarningMb == 0) disabledLabel else "${preferences.cellularDownloadSizeWarningMb} MB"
                    SettingListItem(
                        icon = Tabler.Outline.AlertTriangle,
                        title = downloadWarningTitle,
                        subtitle = stringResource(R.string.settings_cellular_download_warning_subtitle),
                        trailingText = downloadWarningLabel,
                        highlighted = highlightSettingId == "cellular_download_warning",
                        index = networkIdx++, count = networkTotal,
                        onClick = {
                            activePicker = PickerState.List(
                                title = downloadWarningTitle,
                                items = listOf(0, 100, 250, 500, 1000, 2000),
                                label = { if (it == 0) disabledLabel else "$it MB" },
                                isSelected = { it == preferences.cellularDownloadSizeWarningMb },
                                onSelect = { viewModel.setCellularDownloadSizeWarningMb(it) },
                            )
                        },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Gauge,
                        title = stringResource(R.string.settings_data_saver_mode),
                        subtitle = stringResource(R.string.settings_data_saver_mode_subtitle),
                        checked = preferences.dataSaverEnabled,
                        highlighted = highlightSettingId == "data_saver",
                        index = networkIdx++, count = networkTotal,
                        onCheckedChange = { viewModel.setDataSaverEnabled(it) },
                    )
                    val networkTimeoutsTitle = stringResource(R.string.settings_network_timeouts)
                    SettingListItem(
                        icon = Tabler.Outline.Clock,
                        title = networkTimeoutsTitle,
                        subtitle = stringResource(R.string.settings_network_timeouts_subtitle),
                        trailingText = preferences.networkTimeoutPreset.displayName.substringBefore(" ("),
                        highlighted = highlightSettingId == "network_timeout",
                        index = networkIdx++, count = networkTotal,
                        onClick = {
                            activePicker = PickerState.List(
                                title = networkTimeoutsTitle,
                                items = com.raulshma.jellyplay.core.model.NetworkTimeoutPreset.entries,
                                label = { it.displayName },
                                isSelected = { it == preferences.networkTimeoutPreset },
                                onSelect = { viewModel.setNetworkTimeoutPreset(it) },
                            )
                        },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Code,
                        title = stringResource(R.string.settings_verbose_logging),
                        subtitle = if (preferences.verboseNetworkLogging) stringResource(R.string.settings_verbose_logging_on) else stringResource(R.string.settings_verbose_logging_off),
                        checked = preferences.verboseNetworkLogging,
                        highlighted = highlightSettingId == "verbose_logging",
                        index = networkIdx++, count = networkTotal,
                        onCheckedChange = { viewModel.setVerboseNetworkLogging(it) },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Refresh,
                        title = stringResource(R.string.settings_background_sync),
                        subtitle = if (preferences.userDataSyncEnabled) stringResource(R.string.settings_background_sync_on) else stringResource(R.string.settings_background_sync_off),
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
                    title = stringResource(R.string.settings_downloads),
                    summary = { stringResource(R.string.settings_downloads_summary, preferences.downloadQuality.displayName) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    val downloadTotal = 8
                    var downloadIdx = 0

                    val downloadQualityTitle = stringResource(R.string.settings_download_quality)
                    SettingListItem(
                        icon = Tabler.Outline.Video,
                        title = downloadQualityTitle,
                        subtitle = stringResource(R.string.settings_download_quality_subtitle),
                        trailingText = preferences.downloadQuality.displayName,
                        highlighted = highlightSettingId == "download_quality",
                        index = downloadIdx++, count = downloadTotal,
                        onClick = {
                            activePicker = PickerState.List(
                                title = downloadQualityTitle,
                                items = com.raulshma.jellyplay.core.model.DownloadQuality.entries,
                                label = { it.displayName },
                                isSelected = { it == preferences.downloadQuality },
                                onSelect = { viewModel.setDownloadQuality(it) },
                            )
                        }
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Trash,
                        title = stringResource(R.string.settings_smart_downloads),
                        subtitle = stringResource(R.string.settings_smart_downloads_subtitle),
                        checked = preferences.smartDownloadsEnabled,
                        highlighted = highlightSettingId == "smart_downloads",
                        index = downloadIdx++, count = downloadTotal,
                        onCheckedChange = { viewModel.setSmartDownloadsEnabled(it) }
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Download,
                        title = stringResource(R.string.settings_auto_download_new),
                        subtitle = stringResource(R.string.settings_auto_download_new_subtitle),
                        checked = preferences.autoDownloadNewEpisodes,
                        highlighted = highlightSettingId == "auto_download_new_episodes",
                        index = downloadIdx++, count = downloadTotal,
                        onCheckedChange = { viewModel.setAutoDownloadNewEpisodes(it) }
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Clock,
                        title = stringResource(R.string.settings_download_schedule),
                        subtitle = stringResource(R.string.settings_download_schedule_subtitle),
                        checked = preferences.downloadScheduleEnabled,
                        highlighted = highlightSettingId == "download_schedule",
                        index = downloadIdx++, count = downloadTotal,
                        onCheckedChange = { viewModel.setDownloadScheduleEnabled(it) }
                    )

                    if (preferences.downloadScheduleEnabled) {
                        val scheduleStartTitle = stringResource(R.string.settings_schedule_start)
                        SettingListItem(
                            icon = Tabler.Outline.Sun,
                            title = scheduleStartTitle,
                            subtitle = stringResource(R.string.settings_schedule_start_subtitle),
                            trailingText = "${preferences.downloadScheduleWindow.startHour}:00",
                            highlighted = highlightSettingId == "download_schedule_start",
                            index = downloadIdx++, count = downloadTotal,
                            onClick = {
                                val current = preferences.downloadScheduleWindow
                                activePicker = PickerState.List(
                                    title = scheduleStartTitle,
                                    items = (0..23).toList(),
                                    label = { "$it:00" },
                                    isSelected = { it == current.startHour },
                                    onSelect = { viewModel.setDownloadScheduleWindow(current.copy(startHour = it)) },
                                )
                            }
                        )

                        val scheduleEndTitle = stringResource(R.string.settings_schedule_end)
                        SettingListItem(
                            icon = Tabler.Outline.Moon,
                            title = scheduleEndTitle,
                            subtitle = stringResource(R.string.settings_schedule_end_subtitle),
                            trailingText = "${preferences.downloadScheduleWindow.endHour}:00",
                            highlighted = highlightSettingId == "download_schedule_end",
                            index = downloadIdx++, count = downloadTotal,
                            onClick = {
                                val current = preferences.downloadScheduleWindow
                                activePicker = PickerState.List(
                                    title = scheduleEndTitle,
                                    items = (0..23).toList(),
                                    label = { "$it:00" },
                                    isSelected = { it == current.endHour },
                                    onSelect = { viewModel.setDownloadScheduleWindow(current.copy(endHour = it)) },
                                )
                            }
                        )

                        SettingToggleItem(
                            icon = Tabler.Outline.Wifi,
                            title = stringResource(R.string.settings_download_schedule_wifi_only),
                            subtitle = stringResource(R.string.settings_download_schedule_wifi_only_subtitle),
                            checked = preferences.downloadScheduleWindow.wifiOnly,
                            highlighted = highlightSettingId == "download_schedule_wifi_only",
                            index = downloadIdx++, count = downloadTotal,
                            onCheckedChange = {
                                val current = preferences.downloadScheduleWindow
                                viewModel.setDownloadScheduleWindow(current.copy(wifiOnly = it))
                            }
                        )
                    }

                    val unlimitedLabel = stringResource(R.string.settings_unlimited)
                    val maxDownloadStorageTitle = stringResource(R.string.settings_max_download_storage)
                    SettingListItem(
                        icon = Tabler.Outline.Database,
                        title = maxDownloadStorageTitle,
                        subtitle = stringResource(R.string.settings_max_download_storage_subtitle),
                        trailingText = if (preferences.maxDownloadStorageGb == 0) unlimitedLabel else "${preferences.maxDownloadStorageGb} GB",
                        highlighted = highlightSettingId == "max_download_storage_limit",
                        index = downloadIdx++, count = downloadTotal,
                        onClick = {
                            activePicker = PickerState.List(
                                title = maxDownloadStorageTitle,
                                items = listOf(0, 5, 10, 20, 50),
                                label = { if (it == 0) unlimitedLabel else "$it GB" },
                                isSelected = { it == preferences.maxDownloadStorageGb },
                                onSelect = { viewModel.setMaxDownloadStorageGb(it) },
                            )
                        }
                    )

                    val internalLabel = stringResource(R.string.settings_storage_internal)
                    val externalLabel = stringResource(R.string.settings_storage_external)
                    val sdCardLabel = stringResource(R.string.storage_sd_card)
                    val storageLocationTitle = stringResource(R.string.settings_download_storage_location)
                    // Build the picker from the *real*
                    // available mounts (primary + any SD/USB) instead of the
                    // hardcoded INTERNAL/EXTERNAL pair. Falls back to the
                    // legacy pair if mount enumeration hasn't resolved yet.
                    // NOTE: string resolution happens here in the @Composable
                    // body; the lambda below only closes over the resolved
                    // CharSequences so it can be invoked from non-composable
                    // callbacks (onClick) too.
                    val mounts = viewModel.storageMounts
                    val mountLabel = { kind: com.raulshma.jellyplay.core.data.repository.StorageMountKind ->
                        when (kind) {
                            com.raulshma.jellyplay.core.data.repository.StorageMountKind.INTERNAL -> internalLabel
                            com.raulshma.jellyplay.core.data.repository.StorageMountKind.PRIMARY_EXTERNAL -> externalLabel
                            com.raulshma.jellyplay.core.data.repository.StorageMountKind.REMOVABLE -> sdCardLabel
                            com.raulshma.jellyplay.core.data.repository.StorageMountKind.EXTERNAL -> externalLabel
                        }
                    }
                    val selectedMountLabel = mounts.firstOrNull { it.prefValue == preferences.downloadStorageLocation }
                        ?.let { mountLabel(it.kind) }
                        ?: if (preferences.downloadStorageLocation == "INTERNAL") internalLabel else externalLabel
                    SettingListItem(
                        icon = Tabler.Outline.Folder,
                        title = storageLocationTitle,
                        subtitle = stringResource(R.string.settings_download_storage_location_subtitle),
                        trailingText = selectedMountLabel,
                        highlighted = highlightSettingId == "download_storage_location",
                        index = downloadIdx++, count = downloadTotal,
                        onClick = {
                            val items = if (mounts.isNotEmpty()) mounts else emptyList()
                            activePicker = PickerState.List(
                                title = storageLocationTitle,
                                items = items,
                                label = { mount ->
                                    val label = mountLabel(mount.kind)
                                    if (mount.availableBytes > 0L) {
                                        "$label (${mount.availableBytes / (1024L * 1024 * 1024)} GB)"
                                    } else {
                                        label
                                    }
                                },
                                isSelected = { it.prefValue == preferences.downloadStorageLocation },
                                onSelect = { viewModel.setDownloadStorageLocation(it.prefValue) },
                            )
                        }
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Trash,
                        title = stringResource(R.string.downloads_auto_delete_after_watch),
                        subtitle = stringResource(R.string.downloads_auto_delete_after_watch_subtitle),
                        checked = preferences.autoDeleteAfterWatch,
                        highlighted = highlightSettingId == "auto_delete_after_watch",
                        index = downloadIdx, count = downloadTotal,
                        onCheckedChange = { viewModel.setAutoDeleteAfterWatch(it) }
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

    SettingsPickerDialog(
        state = activePicker,
        onDismiss = { activePicker = null },
    )
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
