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
import com.raulshma.jellyplay.core.model.AutoDownloadMode
import com.raulshma.jellyplay.core.model.MeteredNetworkBehavior
import com.raulshma.jellyplay.core.model.isEnabled
import com.raulshma.jellyplay.core.model.CACHE_SIZE_DISABLED
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_max_cache_applies_after_restart
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_max_cache_size
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_max_download_storage
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_max_simultaneous_downloads
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_metered_network_behavior
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_network_diagnostics
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_network_offline
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_network_timeouts
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_mode_ask
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_mode_instant
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_mode_never
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_storage_network
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_storage_breakdown
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_storage_builtin_external
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

/**
 * The storage screen's ONE ordered group declaration — the derivation-only
 * shape (group ids/order; every slot uses the default declared-admission
 * count hook, the content slots stay empty) that BOTH the screen and the
 * catalog contract test's section-7 pins consume, so a reordered or
 * re-flagged group cannot strand a stale replica (the mirror-drift class
 * ADR-0009's amendment closes). ADR-0017's storage split: the Downloads group
 * moved to the sibling [Route.DownloadsSettings] screen — the declaration
 * narrowed to the cache + network halves. Per-group row emission stays
 * hand-written: the screen maps each slot to its body inline.
 */
internal val StorageSettingsScreenSlots: List<PreferenceScreenGroup> = listOf(
    SettingsScreenGroups.storageCache.asScreenGroup(),
    SettingsScreenGroups.storageNetwork.asScreenGroup(),
    SettingsScreenGroups.storageNetworkTuning.asScreenGroup(advancedOnly = true),
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
            StorageRows.AutoDownloadMode.id to preferences.autoDownloadMode.isEnabled,
            StorageRows.AutoDownloadKeepDays.id to (preferences.autoDownloadKeepDays > 0),
        ),
    )
    LaunchedEffect(Unit) { viewModel.refreshCacheSize() }

    // The screen's group bodies mapped onto the ONE ordered declaration
    // ([StorageSettingsScreenSlots], in LazyColumn order — the shell derives
    // the deep-link scroll resolution and the hidden-settings hint count from
    // it, and the contract test pins the same declaration). Slots stop at
    // GROUP grain: the row emission inside each slot is the hand-written
    // body, unchanged (ADR-0009).
    val storageGroups = StorageSettingsScreenSlots.withContent(
        bodies = listOf(
        { activePicker ->
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
                            showChevron = false,
                            onClick = { viewModel.clearCache() },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Photo,
                            title = rowTitle(StorageRows.ClearImageCache),
                            subtitle = rowSubtitle(StorageRows.ClearImageCache),
                            highlighted = highlightSettingId == StorageRows.ClearImageCache.id,
                            showChevron = false,
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
                            val maxCacheDisabled = stringResource(Res.string.settings_disabled)
                            val maxCacheRestartHint = stringResource(Res.string.settings_max_cache_applies_after_restart)
                            val maxCacheLabel: (Int) -> String = {
                                when (it) {
                                    CACHE_SIZE_DISABLED -> maxCacheDisabled
                                    0 -> maxCacheUnlimited
                                    else -> "$it MB"
                                }
                            }
                            SettingListItem(
                                icon = Tabler.Outline.Database,
                                title = maxCacheSizeTitle,
                                subtitle = rowSubtitle(StorageRows.MaxCacheSize),
                                trailingText = maxCacheLabel(preferences.maxCacheSizeMb),
                                highlighted = highlightSettingId == StorageRows.MaxCacheSize.id,
                                onClick = {
                                    activePicker.value = PickerState.List(
                                        title = maxCacheSizeTitle,
                                        items = listOf(CACHE_SIZE_DISABLED, 0, 250, 500, 1000, 2000, 5000),
                                        label = maxCacheLabel,
                                        // The Coil/OkHttp caches read the size once
                                        // per process — only Disabled takes effect
                                        // immediately (it also wipes the image cache
                                        // below); every other value is next-launch.
                                        subtitle = { if (it == CACHE_SIZE_DISABLED) "" else maxCacheRestartHint },
                                        isSelected = { it == preferences.maxCacheSizeMb },
                                        onSelect = {
                                            viewModel.edit { scope -> scope.networkOffline.setMaxCacheSize(it) }
                                            if (it == CACHE_SIZE_DISABLED) viewModel.clearImageCache()
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
        },
        { activePicker ->
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

                    SettingToggleItem(
                        icon = Tabler.Outline.Gauge,
                        title = rowTitle(StorageRows.DataSaver),
                        subtitle = rowSubtitle(StorageRows.DataSaver),
                        checked = preferences.dataSaverEnabled,
                        highlighted = highlightSettingId == StorageRows.DataSaver.id,
                        onCheckedChange = { viewModel.edit { scope -> scope.networkOffline.setDataSaverEnabled(it) } },
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
        },
        { activePicker ->
                // The advanced "Network diagnostics" group (the phase-5
                // reclassification): the timeout/log/metered/warning
                // fine-tuning moved off the network group whole — the shell
                // only composes the slot behind the advanced toggle, so the
                // total reads the advanced leg directly.
                SettingsGroup(
                    icon = Tabler.Outline.Wifi,
                    title = stringResource(Res.string.settings_network_diagnostics),
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in SettingsScreenGroups.storageNetworkTuning.itemIdSet,
                ) {
                    SettingsItemList(total = rowTotalFor(SettingsScreenGroups.storageNetworkTuning, RowAdmissionFlags(showAdvanced = true))) {
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
                    }
                }
        },
        ),
    )

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_storage_network),
        onBack = onBack,
        focusTag = "storage_init",
        highlightSettingId = highlightSettingId,
        groups = storageGroups,
        advancedToggle = PreferenceAdvancedToggle(
            showAdvanced = showAdvanced,
            onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
        ),
        hiddenHint = PreferenceHiddenHint(
            // Derived in the shell from the declaration above: the cache
            // group's five advanced tuning rows plus the Network diagnostics
            // slot's four (the Downloads group lives on the sibling Downloads
            // screen now).
            rowFlags = rowFlags,
            onShowAdvanced = { viewModel.setShowAdvancedSettings(true) },
        ),
        pickerHost = true,
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
