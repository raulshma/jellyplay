package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowBarToDown
import com.composables.icons.tabler.outline.Clock
import com.composables.icons.tabler.outline.Database
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.Folder
import com.composables.icons.tabler.outline.Moon
import com.composables.icons.tabler.outline.PlayerTrackNext
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Rocket
import com.composables.icons.tabler.outline.Rss
import com.composables.icons.tabler.outline.Server
import com.composables.icons.tabler.outline.Sun
import com.composables.icons.tabler.outline.Trash
import com.composables.icons.tabler.outline.Video
import com.composables.icons.tabler.outline.Wifi
import com.raulshma.jellyplay.core.model.AutoDownloadMode
import com.raulshma.jellyplay.core.model.StoragePreferences
import com.raulshma.jellyplay.core.model.isEnabled
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_all_servers
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_clean_up_now
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_clean_up_now_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_cleaned_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_cleaned_up_none
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_keep_days_value
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_keep_days_year
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_mode_ask
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_mode_instant
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_mode_never
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_server_count
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_download_quality
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_downloads
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_downloads_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_live_updates
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_live_updates_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_live_updates_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_max_download_storage
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_max_simultaneous_downloads
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_storage_builtin_external
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_storage_external
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_storage_internal
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_unlimited
import com.raulshma.jellyplay.feature.settings.generated.resources.storage_sd_card

/**
 * The Downloads screen's ONE ordered group declaration (ADR-0009 amendment;
 * ADR-0017's storage split): the whole Downloads group, moved intact off the
 * former combined storage screen. No advanced tier (the group's gated rows
 * ride their WhenOn parents, none the advanced toggle). The contract test
 * pins the slot/group parity against this same declaration.
 */
internal val DownloadsSettingsScreenSlots: List<PreferenceScreenGroup> = listOf(
    SettingsScreenGroups.storageDownloads.asScreenGroup(),
)

/**
 * The Downloads LEAF (ADR-0017's storage split): the download-behavior
 * sibling screen behind the settings root's Downloads entrance — download
 * quality, smart/auto-downloads with their retention clusters, the schedule
 * window and the storage caps, exactly the former combined screen's Downloads
 * group. No advanced tier; the platform-conditional Live Updates opt-in row
 * (API 36+ Android) rides the group's tail as before.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadsSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: StorageSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    // The declared row admissions both the SettingsItemList total and the
    // emission `if`s below read — one gate per id, declared beside the group
    // items (SettingsSearchItemGroup.rowAdmitted).
    val rowFlags = RowAdmissionFlags(
        parentsOn = rowParentsOn(
            StorageRows.DownloadSchedule.id to preferences.downloadScheduleEnabled,
            StorageRows.AutoDownloadMode.id to preferences.autoDownloadMode.isEnabled,
            StorageRows.AutoDownloadKeepDays.id to (preferences.autoDownloadKeepDays > 0),
        ),
    )
    LaunchedEffect(Unit) { viewModel.refreshCacheSize() }

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_downloads),
        onBack = onBack,
        focusTag = "downloads_init",
        highlightSettingId = highlightSettingId,
        groups = DownloadsSettingsScreenSlots.withContent(
            bodies = listOf(
                { activePicker ->
                    DownloadsGroupBody(
                        preferences = preferences,
                        highlightSettingId = highlightSettingId,
                        viewModel = viewModel,
                        rowFlags = rowFlags,
                        activePicker = activePicker,
                    )
                },
            ),
        ),
        pickerHost = true,
    )
}

/**
 * The `storage.downloads` group body — moved verbatim off the former combined
 * storage screen: the declared rows in catalog order (the schedule-window and
 * auto-download retention clusters behind their WhenOn parents, the
 * ContentGated auto-arm row behind the experimental Direct *arr flag), plus
 * the platform-conditional Live Updates opt-in row (outside the search
 * registry — the explicit +1 term).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadsGroupBody(
    preferences: StoragePreferences,
    highlightSettingId: String?,
    viewModel: StorageSettingsViewModel,
    rowFlags: RowAdmissionFlags,
    activePicker: MutableState<PickerState<*>?>,
) {
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
                (if (showLiveUpdatesRow) 1 else 0) +
                // The auto-arm row's ContentGated term: its
                // admission rides the experimental Direct *arr
                // flag (viewModel.directArrEnabled), not a
                // flags-vocabulary gate — the +1 is explicit,
                // like the Live Updates row's.
                (if (viewModel.directArrEnabled) 1 else 0),
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

        // The tri-state new-episode policy (gh-181): the old on/off
        // toggle became Instantly / Ask / Never.
        val autoDownloadModeTitle = rowTitle(StorageRows.AutoDownloadMode)
        val modeLabels = mapOf(
            AutoDownloadMode.INSTANT to stringResource(Res.string.settings_auto_download_mode_instant),
            AutoDownloadMode.ASK to stringResource(Res.string.settings_auto_download_mode_ask),
            AutoDownloadMode.NEVER to stringResource(Res.string.settings_auto_download_mode_never),
        )
        SettingListItem(
            icon = Tabler.Outline.Download,
            title = autoDownloadModeTitle,
            subtitle = rowSubtitle(StorageRows.AutoDownloadMode),
            trailingText = modeLabels.getValue(preferences.autoDownloadMode),
            highlighted = highlightSettingId == StorageRows.AutoDownloadMode.id,
            onClick = {
                // Deliberately an explicit three-value list, never
                // `AutoDownloadMode.entries`: FOLLOW_GLOBAL is the
                // series-row sentinel (ADR-0015), never a legal
                // global mode, and must not leak into this picker.
                val modes = listOf(AutoDownloadMode.INSTANT, AutoDownloadMode.ASK, AutoDownloadMode.NEVER)
                activePicker.value = PickerState.List(
                    title = autoDownloadModeTitle,
                    items = modes,
                    label = { modeLabels.getValue(it) },
                    isSelected = { it == preferences.autoDownloadMode },
                    onSelect = { viewModel.setAutoDownloadMode(it) },
                )
            }
        )

        if (SettingsScreenGroups.storageDownloads.rowAdmitted(StorageRows.AutoDownloadCheckNow.id, rowFlags)) {
            SettingListItem(
                icon = Tabler.Outline.Refresh,
                title = rowTitle(StorageRows.AutoDownloadCheckNow),
                subtitle = rowSubtitle(StorageRows.AutoDownloadCheckNow),
                highlighted = highlightSettingId == StorageRows.AutoDownloadCheckNow.id,
                onClick = { viewModel.checkForNewEpisodesNow() },
            )
        }

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

        // The gh-225 opt-in rides the experimental Direct *arr
        // Integration flag: [RowAdmission.ContentGated] on the
        // row (the screen owns the gate AND the +1 count term
        // above — rowAdmitted would admit it unconditionally).
        if (viewModel.directArrEnabled) {
            SettingToggleItem(
                icon = Tabler.Outline.Rss,
                title = rowTitle(StorageRows.AutoArmFromArr),
                subtitle = rowSubtitle(StorageRows.AutoArmFromArr),
                checked = preferences.autoArmFromArr,
                highlighted = highlightSettingId == StorageRows.AutoArmFromArr.id,
                onCheckedChange = { viewModel.setAutoArmFromArr(it) }
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
                showChevron = false,
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
        val builtinExternalLabel = stringResource(Res.string.settings_storage_builtin_external)
        val sdCardLabel = stringResource(Res.string.storage_sd_card)
        val storageLocationTitle = rowTitle(StorageRows.DownloadStorageLocation)
        // Build the picker from the *real*
        // available mounts (primary + any SD/USB) instead of a
        // hardcoded INTERNAL/EXTERNAL pair. Falls back to the
        // plain pair if mount enumeration hasn't resolved yet.
        // NOTE: string resolution happens here in the @Composable
        // body; the lambda below only closes over the resolved
        // CharSequences so it can be invoked from non-composable
        // callbacks (onClick) too.
        val mounts = viewModel.storageMounts
        val mountLabel = { kind: StorageMountKind ->
            when (kind) {
                StorageMountKind.INTERNAL -> internalLabel
                // Emulated external (/storage/emulated) — the SAME
                // built-in flash as INTERNAL, not a card. Labeling
                // it "External SD Card" made it look like a second,
                // distinct SD option.
                StorageMountKind.PRIMARY_EXTERNAL -> builtinExternalLabel
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
