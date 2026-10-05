package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.core.model.MeteredNetworkBehavior
import com.raulshma.jellyplay.core.designsystem.theme.smoothCornerShape
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.ui.components.ConsumeSettingsItemIndex
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_live_updates
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_live_updates_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_live_updates_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_delete_cache
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_delete_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_delete_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_all_servers
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_clean_up_now
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_clean_up_now_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_cleaned_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_cleaned_up_none
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_keep_days_value
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_keep_days_year
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_server_count
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_offline
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_background_sync
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_background_sync_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_background_sync_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cache_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cache_used
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cellular_download_size_warning
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cellular_streaming_quality
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_clear_cache
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_clear_image_cache
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_connections_per_download
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_data_saver_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_disabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_download_quality
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_download_schedule
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_download_schedule_wifi_only
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_download_storage_location
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_downloads
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_downloads_storage_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_downloads_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_legend_cache
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_legend_downloads
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_legend_images
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_manual_bandwidth_cap
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_max_cache_size
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_max_download_storage
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_max_simultaneous_downloads
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_metered_network_behavior
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_network_offline
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_network_timeouts
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_offline_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_online
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_1080p_full_hd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_360p_low
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_480p_sd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_4k_ultra_hd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quality_720p_hd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_schedule_end
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_schedule_start
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_smart_downloads
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_status_value
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_storage
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_storage_breakdown
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_storage_external
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_storage_internal
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_streaming_quality_auto_adaptive
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_unlimited
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_verbose_logging
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_verbose_logging_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_verbose_logging_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_wifi_only
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_wifi_only_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_wifi_only_on
import com.raulshma.jellyplay.feature.settings.generated.resources.storage_sd_card

private fun streamingQualityLabelRes(quality: StreamingQuality): StringResource = when (quality) {
    StreamingQuality.AUTO -> Res.string.settings_streaming_quality_auto_adaptive
    StreamingQuality.LOW_360P -> Res.string.settings_quality_360p_low
    StreamingQuality.SD_480P -> Res.string.settings_quality_480p_sd
    StreamingQuality.HD_720P -> Res.string.settings_quality_720p_hd
    StreamingQuality.FHD_1080P -> Res.string.settings_quality_1080p_full_hd
    StreamingQuality.UHD_4K -> Res.string.settings_quality_4k_ultra_hd
}

/**
 * The declared storage screen groups in LazyColumn order — the derivation
 * source the deep-link scroll resolver consumes (see HighlightScroll.kt), so
 * the scroll target can never drift from the UI. All three groups always
 * render, so no advanced offset applies.
 */
private val storageScreenGroups: List<Set<String>> = listOf(
    SettingsScreenGroups.storageCache.itemIdSet,
    SettingsScreenGroups.storageNetwork.itemIdSet,
    SettingsScreenGroups.storageDownloads.itemIdSet,
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun StorageSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: StorageSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()
    // The declared row admissions both the SettingsItemList totals and the
    // emission `if`s below read — one gate per id, declared beside the group
    // items (SettingsSearchItemGroup.rowAdmitted).
    val rowFlags = RowAdmissionFlags(
        showAdvanced = showAdvanced,
        parentsOn = rowParentsOn(
            StorageRows.DownloadSchedule.id to preferences.downloadScheduleEnabled,
            StorageRows.AutoDownloadNewEpisodes.id to preferences.autoDownloadNewEpisodes,
            StorageRows.AutoDownloadKeepDays.id to (preferences.autoDownloadKeepDays > 0),
        ),
    )
    LaunchedEffect(Unit) { viewModel.refreshCacheSize() }

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_downloads_storage_title),
        onBack = onBack,
        focusTag = "storage_init",
        highlightSettingId = highlightSettingId,
        highlightGroups = storageScreenGroups,
        advancedToggle = PreferenceAdvancedToggle(
            showAdvanced = showAdvanced,
            onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
        ),
        pickerHost = true,
    ) { activePicker ->
            item {
                SettingsGroup(
                    icon = Tabler.Outline.Database,
                    title = stringResource(Res.string.settings_storage),
                    summary = { stringResource(Res.string.settings_cache_summary, viewModel.cacheSizeMb) },
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
                                text = stringResource(Res.string.settings_storage_breakdown),
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
                                LegendItem(color = MaterialTheme.colorScheme.primary, label = stringResource(Res.string.settings_legend_cache, breakdown.cacheMb))
                                LegendItem(color = MaterialTheme.colorScheme.tertiary, label = stringResource(Res.string.settings_legend_downloads, breakdown.downloadsMb))
                                LegendItem(color = MaterialTheme.colorScheme.secondary, label = stringResource(Res.string.settings_legend_images, breakdown.imagesMb))
                            }
                        }
                    }

                    // Derived by rowTotalFor from the cache group
                    // declaration (full per-id admission coverage — the
                    // advanced rows behind the advanced toggle) plus the
                    // cache-used info row (a screen-local row with no
                    // search entry — the explicit +1 term).
                    val storageTotal =
                        rowTotalFor(SettingsScreenGroups.storageCache, RowAdmissionFlags(showAdvanced = showAdvanced)) + 1
                    SettingsItemList(total = storageTotal) {
                        // Screen-local info row: SettingInfoItem takes
                        // explicit index/count (it does not consume the list's
                        // auto-index), so slot it at 0 and advance the counter
                        // for the rows after it.
                        SettingInfoItem(
                            icon = Tabler.Outline.Database,
                            title = stringResource(Res.string.settings_cache_used),
                            subtitle = "${viewModel.cacheSizeMb} MB",
                            index = 0, count = storageTotal,
                        )
                        ConsumeSettingsItemIndex()
                        SettingListItem(
                            icon = Tabler.Outline.Trash,
                            title = rowTitle(StorageRows.ClearCache),
                            subtitle = rowSubtitle(StorageRows.ClearCache),
                            highlighted = highlightSettingId == StorageRows.ClearCache.id,
                            onClick = { viewModel.clearCache() },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Photo,
                            title = rowTitle(StorageRows.ClearImageCache),
                            subtitle = rowSubtitle(StorageRows.ClearImageCache),
                            highlighted = highlightSettingId == StorageRows.ClearImageCache.id,
                            onClick = { viewModel.clearImageCache() },
                        )
                        if (showAdvanced) {
                            SettingToggleItem(
                                icon = Tabler.Outline.Wifi,
                                title = rowTitle(StorageRows.WifiOnlyDownloads),
                                subtitle = if (preferences.wifiOnlyDownloads) stringResource(Res.string.settings_wifi_only_on) else stringResource(Res.string.settings_wifi_only_off),
                                checked = preferences.wifiOnlyDownloads,
                                highlighted = highlightSettingId == StorageRows.WifiOnlyDownloads.id,
                                onCheckedChange = { viewModel.edit { scope -> scope.downloads.setWifiOnlyDownloads(it) } },
                            )
                            val connectionsTitle = rowTitle(StorageRows.DownloadConnections)
                            SettingListItem(
                                icon = Tabler.Outline.Download,
                                title = connectionsTitle,
                                subtitle = rowSubtitle(StorageRows.DownloadConnections),
                                trailingText = "${preferences.downloadConnections}",
                                highlighted = highlightSettingId == StorageRows.DownloadConnections.id,
                                onClick = {
                                    activePicker.value = PickerState.List(
                                        title = connectionsTitle,
                                        items = listOf(1, 2, 4, 8, 12, 16),
                                        label = { it.toString() },
                                        isSelected = { it == preferences.downloadConnections },
                                        onSelect = { viewModel.edit { scope -> scope.downloads.setDownloadConnections(it) } },
                                    )
                                },
                            )
                            val concurrentDownloadsTitle = rowTitle(StorageRows.MaxConcurrentDownloads)
                            SettingListItem(
                                icon = Tabler.Outline.ArrowBarToDown,
                                title = concurrentDownloadsTitle,
                                subtitle = rowSubtitle(StorageRows.MaxConcurrentDownloads),
                                trailingText = "${preferences.maxConcurrentDownloads}",
                                highlighted = highlightSettingId == StorageRows.MaxConcurrentDownloads.id,
                                onClick = {
                                    activePicker.value = PickerState.List(
                                        title = concurrentDownloadsTitle,
                                        items = listOf(1, 2, 3, 4, 5, 6),
                                        label = { it.toString() },
                                        isSelected = { it == preferences.maxConcurrentDownloads },
                                        onSelect = { viewModel.edit { scope -> scope.downloads.setMaxConcurrentDownloads(it) } },
                                    )
                                },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.Refresh,
                                title = rowTitle(StorageRows.AutoDeleteCache),
                                subtitle = if (preferences.autoDeleteCache) stringResource(Res.string.settings_auto_delete_on) else stringResource(Res.string.settings_auto_delete_off),
                                checked = preferences.autoDeleteCache,
                                highlighted = highlightSettingId == StorageRows.AutoDeleteCache.id,
                                onCheckedChange = { viewModel.edit { scope -> scope.networkOffline.setAutoDeleteCache(it) } },
                            )
                            val maxCacheSizeTitle = rowTitle(StorageRows.MaxCacheSize)
                            val maxCacheUnlimited = stringResource(Res.string.settings_unlimited)
                            SettingListItem(
                                icon = Tabler.Outline.Database,
                                title = maxCacheSizeTitle,
                                subtitle = rowSubtitle(StorageRows.MaxCacheSize),
                                trailingText = if (preferences.maxCacheSizeMb == 0) maxCacheUnlimited else "${preferences.maxCacheSizeMb} MB",
                                highlighted = highlightSettingId == StorageRows.MaxCacheSize.id,
                                onClick = {
                                    activePicker.value = PickerState.List(
                                        title = maxCacheSizeTitle,
                                        items = listOf(0, 250, 500, 1000, 2000, 5000),
                                        label = { if (it == 0) maxCacheUnlimited else "$it MB" },
                                        isSelected = { it == preferences.maxCacheSizeMb },
                                        onSelect = { viewModel.edit { scope -> scope.networkOffline.setMaxCacheSize(it) } },
                                    )
                                },
                            )
                        }
                    }
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.Cloud,
                    title = stringResource(Res.string.settings_network_offline),
                    summary = {
                        val status = if (preferences.manualOfflineEnabled) stringResource(Res.string.settings_offline_mode) else stringResource(Res.string.settings_online)
                        stringResource(Res.string.settings_status_value, status)
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in SettingsScreenGroups.storageNetwork.itemIdSet,
                ) {
                    SettingsItemList(total = rowTotalFor(SettingsScreenGroups.storageNetwork, rowFlags)) {
                    SettingToggleItem(
                        icon = Tabler.Outline.CloudOff,
                        title = rowTitle(StorageRows.OfflineMode),
                        subtitle = rowSubtitle(StorageRows.OfflineMode),
                        checked = preferences.manualOfflineEnabled,
                        highlighted = highlightSettingId == StorageRows.OfflineMode.id,
                        onCheckedChange = { viewModel.edit { scope -> scope.networkOffline.setManualOffline(it) } },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.WifiOff,
                        title = rowTitle(StorageRows.AutoOffline),
                        subtitle = rowSubtitle(StorageRows.AutoOffline),
                        checked = preferences.autoOfflineEnabled,
                        highlighted = highlightSettingId == StorageRows.AutoOffline.id,
                        onCheckedChange = { viewModel.edit { scope -> scope.networkOffline.setAutoOfflineEnabled(it) } },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Gauge,
                        title = rowTitle(StorageRows.AdaptiveBitrate),
                        subtitle = rowSubtitle(StorageRows.AdaptiveBitrate),
                        checked = preferences.adaptiveBitrateEnabled,
                        highlighted = highlightSettingId == StorageRows.AdaptiveBitrate.id,
                        onCheckedChange = { viewModel.edit { scope -> scope.networkOffline.setAdaptiveBitrateEnabled(it) } },
                    )

                    val caps = listOf(0L, 1_000_000L, 2_000_000L, 5_000_000L, 10_000_000L, 20_000_000L)
                    val bandwidthCapTitle = rowTitle(StorageRows.BandwidthCap)
                    val bandwidthUnlimited = stringResource(Res.string.settings_unlimited)
                    val capLabel = if (preferences.manualBandwidthCap == 0L) bandwidthUnlimited else "${preferences.manualBandwidthCap / 1_000_000L} Mbps"

                    SettingListItem(
                        icon = Tabler.Outline.Lock,
                        title = bandwidthCapTitle,
                        subtitle = rowSubtitle(StorageRows.BandwidthCap),
                        trailingText = capLabel,
                        highlighted = highlightSettingId == StorageRows.BandwidthCap.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = bandwidthCapTitle,
                                items = caps,
                                label = { if (it == 0L) bandwidthUnlimited else "${it / 1_000_000L} Mbps" },
                                isSelected = { it == preferences.manualBandwidthCap },
                                onSelect = { viewModel.edit { scope -> scope.networkOffline.setManualBandwidthCap(it) } },
                            )
                        },
                    )

                    val meteredTitle = rowTitle(StorageRows.MeteredNetworkBehavior)
                    SettingListItem(
                        icon = Tabler.Outline.Compass,
                        title = meteredTitle,
                        subtitle = rowSubtitle(StorageRows.MeteredNetworkBehavior),
                        trailingText = preferences.meteredNetworkBehavior.displayName,
                        highlighted = highlightSettingId == StorageRows.MeteredNetworkBehavior.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = meteredTitle,
                                items = MeteredNetworkBehavior.entries,
                                label = { it.displayName },
                                isSelected = { it == preferences.meteredNetworkBehavior },
                                onSelect = { viewModel.edit { scope -> scope.networkOffline.setMeteredNetworkBehavior(it) } },
                            )
                        },
                    )

                    val cellularQualityTitle = rowTitle(StorageRows.CellularStreamingQuality)
                    val qualityLabels = StreamingQuality.entries.associateWith { stringResource(streamingQualityLabelRes(it)) }
                    SettingListItem(
                        icon = Tabler.Outline.DeviceMobile,
                        title = cellularQualityTitle,
                        subtitle = rowSubtitle(StorageRows.CellularStreamingQuality),
                        trailingText = qualityLabels[preferences.cellularStreamingQuality] ?: preferences.cellularStreamingQuality.name,
                        highlighted = highlightSettingId == StorageRows.CellularStreamingQuality.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = cellularQualityTitle,
                                items = StreamingQuality.entries,
                                label = { qualityLabels[it] ?: it.name },
                                isSelected = { it == preferences.cellularStreamingQuality },
                                onSelect = { viewModel.edit { scope -> scope.playback.setCellularStreamingQuality(it) } },
                            )
                        },
                    )

                    val downloadWarningTitle = rowTitle(StorageRows.CellularDownloadWarning)
                    val disabledLabel = stringResource(Res.string.settings_disabled)
                    val downloadWarningLabel = if (preferences.cellularDownloadSizeWarningMb == 0) disabledLabel else "${preferences.cellularDownloadSizeWarningMb} MB"
                    SettingListItem(
                        icon = Tabler.Outline.AlertTriangle,
                        title = downloadWarningTitle,
                        subtitle = rowSubtitle(StorageRows.CellularDownloadWarning),
                        trailingText = downloadWarningLabel,
                        highlighted = highlightSettingId == StorageRows.CellularDownloadWarning.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = downloadWarningTitle,
                                items = listOf(0, 100, 250, 500, 1000, 2000),
                                label = { if (it == 0) disabledLabel else "$it MB" },
                                isSelected = { it == preferences.cellularDownloadSizeWarningMb },
                                onSelect = { viewModel.edit { scope -> scope.downloads.setCellularDownloadSizeWarningMb(it) } },
                            )
                        },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Gauge,
                        title = rowTitle(StorageRows.DataSaver),
                        subtitle = rowSubtitle(StorageRows.DataSaver),
                        checked = preferences.dataSaverEnabled,
                        highlighted = highlightSettingId == StorageRows.DataSaver.id,
                        onCheckedChange = { viewModel.edit { scope -> scope.networkOffline.setDataSaverEnabled(it) } },
                    )
                    val networkTimeoutsTitle = rowTitle(StorageRows.NetworkTimeout)
                    SettingListItem(
                        icon = Tabler.Outline.Clock,
                        title = networkTimeoutsTitle,
                        subtitle = rowSubtitle(StorageRows.NetworkTimeout),
                        trailingText = preferences.networkTimeoutPreset.displayName.substringBefore(" ("),
                        highlighted = highlightSettingId == StorageRows.NetworkTimeout.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = networkTimeoutsTitle,
                                items = com.raulshma.jellyplay.core.model.NetworkTimeoutPreset.entries,
                                label = { it.displayName },
                                isSelected = { it == preferences.networkTimeoutPreset },
                                onSelect = { viewModel.edit { scope -> scope.networkOffline.setNetworkTimeoutPreset(it) } },
                            )
                        },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Code,
                        title = rowTitle(StorageRows.VerboseLogging),
                        subtitle = if (preferences.verboseNetworkLogging) stringResource(Res.string.settings_verbose_logging_on) else stringResource(Res.string.settings_verbose_logging_off),
                        checked = preferences.verboseNetworkLogging,
                        highlighted = highlightSettingId == StorageRows.VerboseLogging.id,
                        onCheckedChange = { viewModel.edit { scope -> scope.networkOffline.setVerboseNetworkLogging(it) } },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Refresh,
                        title = rowTitle(StorageRows.UserDataSync),
                        subtitle = if (preferences.userDataSyncEnabled) stringResource(Res.string.settings_background_sync_on) else stringResource(Res.string.settings_background_sync_off),
                        checked = preferences.userDataSyncEnabled,
                        highlighted = highlightSettingId == StorageRows.UserDataSync.id,
                        onCheckedChange = { viewModel.edit { scope -> scope.playback.setUserDataSyncEnabled(it) } },
                    )
                    }
                }
            }

            item {
                // Android 16 Live Updates opt-in (the download-progress
                // notification's promoted rendering). Null below API 36 /
                // on desktop — the row does not exist there.
                val liveUpdatesGate = rememberLiveUpdatesGate()
                val showLiveUpdatesRow = liveUpdatesGate != null
                SettingsGroup(
                    icon = Tabler.Outline.Download,
                    title = stringResource(Res.string.settings_downloads),
                    summary = { stringResource(Res.string.settings_downloads_summary, preferences.downloadQuality.displayName) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    // Derived by rowTotalFor from the downloads group
                    // declaration (full per-id admission coverage): the
                    // three schedule-window rows only render when scheduling
                    // is on (their declared WhenOn gate, which rowFlags
                    // carries — the emission `if`s below read it too).
                    SettingsItemList(
                        total = rowTotalFor(SettingsScreenGroups.storageDownloads, rowFlags) +
                            if (showLiveUpdatesRow) 1 else 0,
                    ) {

                    // Shared picker labels — resolved in the composable body so
                    // the onClick lambdas below can close over them.
                    val offLabel = stringResource(Res.string.settings_off)
                    val unlimitedLabel = stringResource(Res.string.settings_unlimited)

                    val downloadQualityTitle = rowTitle(StorageRows.DownloadQuality)
                    SettingListItem(
                        icon = Tabler.Outline.Video,
                        title = downloadQualityTitle,
                        subtitle = rowSubtitle(StorageRows.DownloadQuality),
                        trailingText = preferences.downloadQuality.displayName,
                        highlighted = highlightSettingId == StorageRows.DownloadQuality.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = downloadQualityTitle,
                                items = com.raulshma.jellyplay.core.model.DownloadQuality.entries,
                                label = { it.displayName },
                                isSelected = { it == preferences.downloadQuality },
                                onSelect = { viewModel.edit { scope -> scope.downloads.setDownloadQuality(it) } },
                            )
                        }
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Trash,
                        title = rowTitle(StorageRows.SmartDownloads),
                        subtitle = rowSubtitle(StorageRows.SmartDownloads),
                        checked = preferences.smartDownloadsEnabled,
                        highlighted = highlightSettingId == StorageRows.SmartDownloads.id,
                        onCheckedChange = { viewModel.edit { scope -> scope.downloads.setSmartDownloadsEnabled(it) } }
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Download,
                        title = rowTitle(StorageRows.AutoDownloadNewEpisodes),
                        subtitle = rowSubtitle(StorageRows.AutoDownloadNewEpisodes),
                        checked = preferences.autoDownloadNewEpisodes,
                        highlighted = highlightSettingId == StorageRows.AutoDownloadNewEpisodes.id,
                        onCheckedChange = { viewModel.setAutoDownloadNewEpisodes(it) }
                    )

                    // The auto-download retention-policy cluster: every row
                    // rides the toggle (their declared WhenOn gate, which
                    // rowFlags carries — the emission `if`s below read it).
                    if (SettingsScreenGroups.storageDownloads.rowAdmitted(StorageRows.AutoDownloadLookahead.id, rowFlags)) {
                        val lookaheadTitle = rowTitle(StorageRows.AutoDownloadLookahead)
                        SettingListItem(
                            icon = Tabler.Outline.PlayerTrackNext,
                            title = lookaheadTitle,
                            subtitle = rowSubtitle(StorageRows.AutoDownloadLookahead),
                            trailingText = preferences.autoDownloadLookahead.takeIf { it > 0 }?.toString()
                                ?: stringResource(Res.string.settings_off),
                            highlighted = highlightSettingId == StorageRows.AutoDownloadLookahead.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = lookaheadTitle,
                                    items = (0..10).toList(),
                                    label = { if (it == 0) offLabel else it.toString() },
                                    isSelected = { it == preferences.autoDownloadLookahead },
                                    onSelect = { viewModel.edit { scope -> scope.downloads.setAutoDownloadLookahead(it) } },
                                )
                            }
                        )
                    }

                    if (SettingsScreenGroups.storageDownloads.rowAdmitted(StorageRows.AutoDownloadMaxPerPass.id, rowFlags)) {
                        val maxPerPassTitle = rowTitle(StorageRows.AutoDownloadMaxPerPass)
                        SettingListItem(
                            icon = Tabler.Outline.ArrowBarToDown,
                            title = maxPerPassTitle,
                            subtitle = rowSubtitle(StorageRows.AutoDownloadMaxPerPass),
                            trailingText = if (preferences.autoDownloadMaxPerPass == 0) unlimitedLabel else preferences.autoDownloadMaxPerPass.toString(),
                            highlighted = highlightSettingId == StorageRows.AutoDownloadMaxPerPass.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = maxPerPassTitle,
                                    items = (0..50).toList(),
                                    label = { if (it == 0) unlimitedLabel else it.toString() },
                                    isSelected = { it == preferences.autoDownloadMaxPerPass },
                                    onSelect = { viewModel.edit { scope -> scope.downloads.setAutoDownloadMaxPerPass(it) } },
                                )
                            }
                        )
                    }

                    if (SettingsScreenGroups.storageDownloads.rowAdmitted(StorageRows.AutoDownloadKeepDays.id, rowFlags)) {
                        val keepDaysTitle = rowTitle(StorageRows.AutoDownloadKeepDays)
                        val keepDaysYearLabel = stringResource(Res.string.settings_auto_download_keep_days_year)
                        SettingListItem(
                            icon = Tabler.Outline.Clock,
                            title = keepDaysTitle,
                            subtitle = rowSubtitle(StorageRows.AutoDownloadKeepDays),
                            trailingText = when (val days = preferences.autoDownloadKeepDays) {
                                0 -> offLabel
                                365 -> keepDaysYearLabel
                                else -> stringResource(Res.string.settings_auto_download_keep_days_value, days)
                            },
                            highlighted = highlightSettingId == StorageRows.AutoDownloadKeepDays.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = keepDaysTitle,
                                    items = listOf(0, 7, 30, 90, 365),
                                    label = { if (it == 0) offLabel else if (it == 365) keepDaysYearLabel else "$it" },
                                    isSelected = { it == preferences.autoDownloadKeepDays },
                                    onSelect = { viewModel.edit { scope -> scope.downloads.setAutoDownloadKeepDays(it) } },
                                )
                            }
                        )
                    }

                    if (SettingsScreenGroups.storageDownloads.rowAdmitted(StorageRows.AutoDownloadServers.id, rowFlags)) {
                        val serversTitle = rowTitle(StorageRows.AutoDownloadServers)
                        val allServersLabel = stringResource(Res.string.settings_auto_download_all_servers)
                        // Toggle-select semantics: the sheet dismisses after each
                        // tap (the shared single-select sheet), and the row's
                        // checkmarks show the current membership — re-open to
                        // toggle more.
                        SettingListItem(
                            icon = Tabler.Outline.Server,
                            title = serversTitle,
                            subtitle = rowSubtitle(StorageRows.AutoDownloadServers),
                            trailingText = if (preferences.autoDownloadServers.isEmpty()) {
                                allServersLabel
                            } else {
                                stringResource(
                                    Res.string.settings_auto_download_server_count,
                                    preferences.autoDownloadServers.size,
                                    viewModel.servers.size,
                                )
                            },
                            highlighted = highlightSettingId == StorageRows.AutoDownloadServers.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = serversTitle,
                                    items = viewModel.servers,
                                    label = { it.name },
                                    subtitle = { it.address },
                                    isSelected = { it.id in preferences.autoDownloadServers },
                                    onSelect = { server ->
                                        val current = preferences.autoDownloadServers
                                        val next = if (server.id in current) current - server.id else current + server.id
                                        viewModel.edit { scope -> scope.downloads.setAutoDownloadServers(next) }
                                    },
                                )
                            }
                        )
                    }

                    if (SettingsScreenGroups.storageDownloads.rowAdmitted(StorageRows.AutoDownloadCleanUpNow.id, rowFlags)) {
                        SettingListItem(
                            icon = Tabler.Outline.Trash,
                            title = rowTitle(StorageRows.AutoDownloadCleanUpNow),
                            subtitle = viewModel.lastCleanupSummary?.let { summary ->
                                if (summary.deletedCount > 0) {
                                    stringResource(
                                        Res.string.settings_auto_download_cleaned_up,
                                        summary.deletedCount,
                                        summary.bytesReclaimed.formatBytes(),
                                    )
                                } else {
                                    stringResource(Res.string.settings_auto_download_cleaned_up_none)
                                }
                            } ?: stringResource(Res.string.settings_auto_download_clean_up_now_subtitle),
                            highlighted = highlightSettingId == StorageRows.AutoDownloadCleanUpNow.id,
                            onClick = { viewModel.cleanupDownloadsNow() },
                        )
                    }

                    // Delete-after-watch completes the cluster: it fires
                    // immediately on a watched flip, while the keep-days sweep
                    // above never deletes unwatched downloads (the row
                    // subtitle spells out the interaction).
                    SettingToggleItem(
                        icon = Tabler.Outline.Trash,
                        title = rowTitle(StorageRows.AutoDeleteAfterWatch),
                        subtitle = rowSubtitle(StorageRows.AutoDeleteAfterWatch),
                        checked = preferences.autoDeleteAfterWatch,
                        highlighted = highlightSettingId == StorageRows.AutoDeleteAfterWatch.id,
                        onCheckedChange = { viewModel.edit { scope -> scope.downloads.setAutoDeleteAfterWatch(it) } }
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Clock,
                        title = rowTitle(StorageRows.DownloadSchedule),
                        subtitle = rowSubtitle(StorageRows.DownloadSchedule),
                        checked = preferences.downloadScheduleEnabled,
                        highlighted = highlightSettingId == StorageRows.DownloadSchedule.id,
                        onCheckedChange = { viewModel.edit { scope -> scope.downloads.setDownloadScheduleEnabled(it) } }
                    )

                    if (SettingsScreenGroups.storageDownloads.rowAdmitted(StorageRows.DownloadScheduleStart.id, rowFlags)) {
                        val scheduleStartTitle = rowTitle(StorageRows.DownloadScheduleStart)
                        SettingListItem(
                            icon = Tabler.Outline.Sun,
                            title = scheduleStartTitle,
                            subtitle = rowSubtitle(StorageRows.DownloadScheduleStart),
                            trailingText = "${preferences.downloadScheduleWindow.startHour}:00",
                            highlighted = highlightSettingId == StorageRows.DownloadScheduleStart.id,
                            onClick = {
                                val current = preferences.downloadScheduleWindow
                                activePicker.value = PickerState.List(
                                    title = scheduleStartTitle,
                                    items = (0..23).toList(),
                                    label = { "$it:00" },
                                    isSelected = { it == current.startHour },
                                    onSelect = { viewModel.edit { scope -> scope.downloads.setDownloadScheduleWindow(current.copy(startHour = it)) } },
                                )
                            }
                        )

                        val scheduleEndTitle = rowTitle(StorageRows.DownloadScheduleEnd)
                        SettingListItem(
                            icon = Tabler.Outline.Moon,
                            title = scheduleEndTitle,
                            subtitle = rowSubtitle(StorageRows.DownloadScheduleEnd),
                            trailingText = "${preferences.downloadScheduleWindow.endHour}:00",
                            highlighted = highlightSettingId == StorageRows.DownloadScheduleEnd.id,
                            onClick = {
                                val current = preferences.downloadScheduleWindow
                                activePicker.value = PickerState.List(
                                    title = scheduleEndTitle,
                                    items = (0..23).toList(),
                                    label = { "$it:00" },
                                    isSelected = { it == current.endHour },
                                    onSelect = { viewModel.edit { scope -> scope.downloads.setDownloadScheduleWindow(current.copy(endHour = it)) } },
                                )
                            }
                        )

                        SettingToggleItem(
                            icon = Tabler.Outline.Wifi,
                            title = rowTitle(StorageRows.DownloadScheduleWifiOnly),
                            subtitle = rowSubtitle(StorageRows.DownloadScheduleWifiOnly),
                            checked = preferences.downloadScheduleWindow.wifiOnly,
                            highlighted = highlightSettingId == StorageRows.DownloadScheduleWifiOnly.id,
                            onCheckedChange = {
                                val current = preferences.downloadScheduleWindow
                                viewModel.edit { scope -> scope.downloads.setDownloadScheduleWindow(current.copy(wifiOnly = it)) }
                            }
                        )
                    }

                    val maxDownloadStorageTitle = rowTitle(StorageRows.MaxDownloadStorageLimit)
                    SettingListItem(
                        icon = Tabler.Outline.Database,
                        title = maxDownloadStorageTitle,
                        subtitle = rowSubtitle(StorageRows.MaxDownloadStorageLimit),
                        trailingText = if (preferences.maxDownloadStorageGb == 0) unlimitedLabel else "${preferences.maxDownloadStorageGb} GB",
                        highlighted = highlightSettingId == StorageRows.MaxDownloadStorageLimit.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = maxDownloadStorageTitle,
                                items = listOf(0, 5, 10, 20, 50),
                                label = { if (it == 0) unlimitedLabel else "$it GB" },
                                isSelected = { it == preferences.maxDownloadStorageGb },
                                onSelect = { viewModel.edit { scope -> scope.downloads.setMaxDownloadStorageGb(it) } },
                            )
                        }
                    )

                    val internalLabel = stringResource(Res.string.settings_storage_internal)
                    val externalLabel = stringResource(Res.string.settings_storage_external)
                    val sdCardLabel = stringResource(Res.string.storage_sd_card)
                    val storageLocationTitle = rowTitle(StorageRows.DownloadStorageLocation)
                    // Build the picker from the *real*
                    // available mounts (primary + any SD/USB) instead of the
                    // hardcoded INTERNAL/EXTERNAL pair. Falls back to the
                    // legacy pair if mount enumeration hasn't resolved yet.
                    // NOTE: string resolution happens here in the @Composable
                    // body; the lambda below only closes over the resolved
                    // CharSequences so it can be invoked from non-composable
                    // callbacks (onClick) too.
                    val mounts = viewModel.storageMounts
                    val mountLabel = { kind: StorageMountKind ->
                        when (kind) {
                            StorageMountKind.INTERNAL -> internalLabel
                            StorageMountKind.PRIMARY_EXTERNAL -> externalLabel
                            StorageMountKind.REMOVABLE -> sdCardLabel
                            StorageMountKind.EXTERNAL -> externalLabel
                        }
                    }
                    val selectedMountLabel = mounts.firstOrNull { it.prefValue == preferences.downloadStorageLocation }
                        ?.let { mountLabel(it.kind) }
                        ?: if (preferences.downloadStorageLocation == "INTERNAL") internalLabel else externalLabel
                    SettingListItem(
                        icon = Tabler.Outline.Folder,
                        title = storageLocationTitle,
                        subtitle = rowSubtitle(StorageRows.DownloadStorageLocation),
                        trailingText = selectedMountLabel,
                        highlighted = highlightSettingId == StorageRows.DownloadStorageLocation.id,
                        onClick = {
                            val items = if (mounts.isNotEmpty()) mounts else emptyList()
                            activePicker.value = PickerState.List(
                                title = storageLocationTitle,
                                items = items,
                                label = { mount ->
                                    val label = mountLabel(mount.kind)
                                    if (mount.availableBytes > 0L) {
                                        "$label (${mount.availableBytes.formatBytes()})"
                                    } else {
                                        label
                                    }
                                },
                                isSelected = { it.prefValue == preferences.downloadStorageLocation },
                                onSelect = { viewModel.edit { scope -> scope.downloads.setDownloadStorageLocation(it.prefValue) } },
                            )
                        }
                    )

                    if (liveUpdatesGate != null) {
                        // Platform-conditional (API 36+), outside the search
                        // registry: the deep-link target and the grant state
                        // are device state, not persisted app preferences.
                        SettingListItem(
                            icon = Tabler.Outline.Rocket,
                            title = stringResource(Res.string.settings_live_updates),
                            subtitle = stringResource(
                                if (liveUpdatesGate.isPromoted()) Res.string.settings_live_updates_on
                                else Res.string.settings_live_updates_off,
                            ),
                            highlighted = false,
                            onClick = { liveUpdatesGate.openGrantScreen() },
                        )
                    }
                    }
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
