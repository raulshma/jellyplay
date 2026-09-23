package com.raulshma.jellyplay.feature.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.model.CheckFrequency
import com.raulshma.jellyplay.core.model.LibraryNotificationConfig
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.tv.CenteredBringIntoView
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_check_frequency
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_check_frequency_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_disabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_done
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_enable_notifications
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_enable_notifications_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_items_count
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_libraries
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_libraries_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_max_per_check
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_max_per_check_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_monitored_libraries
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_no_libraries_found
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_notification_lights
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_notification_lights_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_notifications
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_notifications_checking
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_ok
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quiet_end
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quiet_end_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quiet_hours
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quiet_hours_end
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quiet_hours_start
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quiet_hours_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quiet_start
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quiet_start_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_respect_system_dnd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_respect_system_dnd_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sound
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sound_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_system_notification_settings
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_system_notification_settings_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_vibrate
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_vibrate_subtitle

private fun formatMinutes(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    val amPm = if (hours >= 12) "PM" else "AM"
    val displayHour = when {
        hours == 0 -> 12
        hours > 12 -> hours - 12
        else -> hours
    }
    return "${displayHour}:${mins.toString().padStart(2, '0')} $amPm"
}

sealed class NotificationSettingsDialog {
    object None : NotificationSettingsDialog()
    object QuietStartPicker : NotificationSettingsDialog()
    object QuietEndPicker : NotificationSettingsDialog()
    object LibrariesPicker : NotificationSettingsDialog()
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: NotificationSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()
    val libraryFolders by viewModel.libraryFolders.collectAsStateWithLifecycle()
    val platformIntents = rememberPlatformIntents()
    // Capability flag (pinned == the intents seam's query in
    // DesktopPlatformActualsTest); the intents object stays for the open call.
    val canOpenSystemNotificationSettings = settingsCapabilities.supportsSystemNotificationSettings
    // The declared row admissions both the SettingsItemList total and the
    // emission `if`s below read — one gate per id, declared beside the group
    // items (SettingsScreenGroups.notifications.rowAdmitted). The `enabled`
    // and `showAdvanced` structural wrappers around the gated rows carry
    // those halves of their All(...) gates, exactly the playback
    // advanced-video precedent.
    val rowFlags = RowAdmissionFlags(
        showAdvanced = showAdvanced,
        supportsSystemNotificationSettings = canOpenSystemNotificationSettings,
        parentsOn = rowParentsOn(
            NotificationSettingsIds.NOTIFICATIONS_ENABLE to preferences.enabled,
            NotificationSettingsIds.QUIET_HOURS to preferences.quietHoursEnabled,
        ),
    )
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    var activeDialog by remember { mutableStateOf<NotificationSettingsDialog>(NotificationSettingsDialog.None) }
    var activePicker by remember { mutableStateOf<PickerState<*>?>(null) }
    val backgroundColorState = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState()
    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = "notifications_init",
    )

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.settings_notifications),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
        actions = {
            AdvancedSettingsToggleButton(
                showAdvanced = showAdvanced,
                onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
            )
        },
    ) { innerPadding ->
        val notifPrefs = preferences

        CenteredBringIntoView {
        LazyColumn(
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
                    icon = Tabler.Outline.Bell,
                    title = stringResource(Res.string.settings_notifications),
                    summary = {
                        if (notifPrefs.enabled) stringResource(Res.string.settings_notifications_checking, notifPrefs.checkFrequency.displayName.lowercase())
                        else stringResource(Res.string.settings_disabled)
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    // Derived from the notifications group declaration via the
                    // admission total beside SettingsScreenGroups (the hand-run
                    // `1 + 4 + 4 + cap + 2` arithmetic this replaces never
                    // drifted from these gates — now it cannot).
                    SettingsItemList(
                        total = notificationScreenRowTotal(
                            enabled = notifPrefs.enabled,
                            showAdvanced = showAdvanced,
                            quietHoursEnabled = notifPrefs.quietHoursEnabled,
                            canOpenSystemNotificationSettings = canOpenSystemNotificationSettings,
                        ),
                    ) {

                    SettingToggleItem(
                        icon = Tabler.Outline.Bell,
                        title = rowTitle(NotificationSettingsIds.NOTIFICATIONS_ENABLE),
                        subtitle = stringResource(Res.string.settings_enable_notifications_subtitle),
                        checked = notifPrefs.enabled,
                        highlighted = highlightSettingId == NotificationSettingsIds.NOTIFICATIONS_ENABLE,
                        onCheckedChange = { enabled ->
                            viewModel.updateNotificationPreferences { it.copy(enabled = enabled) }
                        },
                    )
                    if (notifPrefs.enabled) {
                        val frequencyTitle = rowTitle(NotificationSettingsIds.NOTIFICATION_CHECK_FREQUENCY)
                        SettingListItem(
                            icon = Tabler.Outline.Clock,
                            title = frequencyTitle,
                            subtitle = stringResource(Res.string.settings_check_frequency_subtitle),
                            trailingText = notifPrefs.checkFrequency.displayName,
                            highlighted = highlightSettingId == NotificationSettingsIds.NOTIFICATION_CHECK_FREQUENCY,
                            onClick = {
                                activePicker = PickerState.List(
                                    title = frequencyTitle,
                                    items = CheckFrequency.entries,
                                    label = { it.displayName },
                                    isSelected = { it == preferences.checkFrequency },
                                    onSelect = { freq ->
                                        viewModel.updateNotificationPreferences { prefs ->
                                            prefs.copy(checkFrequency = freq)
                                        }
                                    },
                                )
                            },
                        )
                        if (showAdvanced) {
                            SettingToggleItem(
                                icon = Tabler.Outline.Moon,
                                title = rowTitle(NotificationSettingsIds.QUIET_HOURS),
                                subtitle = stringResource(Res.string.settings_quiet_hours_subtitle),
                                checked = notifPrefs.quietHoursEnabled,
                                highlighted = highlightSettingId == NotificationSettingsIds.QUIET_HOURS,
                                onCheckedChange = { enabled ->
                                    viewModel.updateNotificationPreferences { it.copy(quietHoursEnabled = enabled) }
                                },
                            )
                            if (SettingsScreenGroups.notifications.rowAdmitted(NotificationSettingsIds.QUIET_START, rowFlags)) {
                                SettingListItem(
                                    icon = Tabler.Outline.Sunset,
                                    title = rowTitle(NotificationSettingsIds.QUIET_START),
                                    subtitle = stringResource(Res.string.settings_quiet_start_subtitle),
                                    trailingText = formatMinutes(notifPrefs.quietHoursStart),
                                    highlighted = highlightSettingId == NotificationSettingsIds.QUIET_START,
                                    onClick = { activeDialog = NotificationSettingsDialog.QuietStartPicker },
                                )
                            }
                            if (SettingsScreenGroups.notifications.rowAdmitted(NotificationSettingsIds.QUIET_END, rowFlags)) {
                                SettingListItem(
                                    icon = Tabler.Outline.Sunrise,
                                    title = rowTitle(NotificationSettingsIds.QUIET_END),
                                    subtitle = stringResource(Res.string.settings_quiet_end_subtitle),
                                    trailingText = formatMinutes(notifPrefs.quietHoursEnd),
                                    highlighted = highlightSettingId == NotificationSettingsIds.QUIET_END,
                                    onClick = { activeDialog = NotificationSettingsDialog.QuietEndPicker },
                                )
                            }
                            SettingToggleItem(
                                icon = Tabler.Outline.BellOff,
                                title = rowTitle(NotificationSettingsIds.RESPECT_SYSTEM_DND),
                                subtitle = stringResource(Res.string.settings_respect_system_dnd_subtitle),
                                checked = notifPrefs.respectSystemDnd,
                                highlighted = highlightSettingId == NotificationSettingsIds.RESPECT_SYSTEM_DND,
                                onCheckedChange = { enabled ->
                                    viewModel.updateNotificationPreferences { it.copy(respectSystemDnd = enabled) }
                                },
                            )
                            if (SettingsScreenGroups.notifications.rowAdmitted(NotificationSettingsIds.SYSTEM_NOTIFICATION_SETTINGS, rowFlags)) {
                                SettingListItem(
                                    icon = Tabler.Outline.Settings,
                                    title = rowTitle(NotificationSettingsIds.SYSTEM_NOTIFICATION_SETTINGS),
                                    subtitle = stringResource(Res.string.settings_system_notification_settings_subtitle),
                                    highlighted = highlightSettingId == NotificationSettingsIds.SYSTEM_NOTIFICATION_SETTINGS,
                                    onClick = platformIntents::openSystemNotificationSettings,
                                )
                            }
                        }
                        SettingToggleItem(
                            icon = Tabler.Outline.Volume,
                            title = rowTitle(NotificationSettingsIds.NOTIFICATION_SOUND),
                            subtitle = stringResource(Res.string.settings_sound_subtitle),
                            checked = notifPrefs.soundEnabled,
                            highlighted = highlightSettingId == NotificationSettingsIds.NOTIFICATION_SOUND,
                            onCheckedChange = { enabled ->
                                viewModel.updateNotificationPreferences { it.copy(soundEnabled = enabled) }
                            },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Bell,
                            title = rowTitle(NotificationSettingsIds.NOTIFICATION_VIBRATE),
                            subtitle = stringResource(Res.string.settings_vibrate_subtitle),
                            checked = notifPrefs.vibrateEnabled,
                            highlighted = highlightSettingId == NotificationSettingsIds.NOTIFICATION_VIBRATE,
                            onCheckedChange = { enabled ->
                                viewModel.updateNotificationPreferences { it.copy(vibrateEnabled = enabled) }
                            },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Bulb,
                            title = rowTitle(NotificationSettingsIds.NOTIFICATION_LIGHTS),
                            subtitle = stringResource(Res.string.settings_notification_lights_subtitle),
                            checked = notifPrefs.lightsEnabled,
                            highlighted = highlightSettingId == NotificationSettingsIds.NOTIFICATION_LIGHTS,
                            onCheckedChange = { enabled ->
                                viewModel.updateNotificationPreferences { it.copy(lightsEnabled = enabled) }
                            },
                        )
                        if (showAdvanced) {
                            val maxPerCheckTitle = rowTitle(NotificationSettingsIds.MAX_PER_CHECK)
                            // Pre-substituted with 0 like the legacy screen — the
                            // picker rows therefore all show this one label
                            // (legacy-identical; common stdlib has no
                            // String.format to re-format per count).
                            val countFormat = stringResource(Res.string.settings_items_count, 0)
                            SettingListItem(
                                icon = Tabler.Outline.LetterCase,
                                title = maxPerCheckTitle,
                                subtitle = stringResource(Res.string.settings_max_per_check_subtitle),
                                trailingText = "${notifPrefs.maxPerCheck}",
                                highlighted = highlightSettingId == NotificationSettingsIds.MAX_PER_CHECK,
                                onClick = {
                                    val options = listOf(5, 10, 15, 20, 30, 50, 100)
                                    activePicker = PickerState.List(
                                        title = maxPerCheckTitle,
                                        items = options,
                                        label = { countFormat },
                                        isSelected = { it == preferences.maxPerCheck },
                                        onSelect = { selected ->
                                            viewModel.updateNotificationPreferences { it.copy(maxPerCheck = selected) }
                                        },
                                    )
                                },
                            )
                            val libraryCount = libraryFolders.size
                            val enabledLibraries = libraryFolders.count { folder ->
                                notifPrefs.libraryConfigs[folder.id]?.enabled ?: true
                            }
                            SettingListItem(
                                icon = Tabler.Outline.Folders,
                                title = rowTitle(NotificationSettingsIds.NOTIFICATION_LIBRARIES),
                                subtitle = stringResource(Res.string.settings_libraries_subtitle, enabledLibraries, libraryCount),
                                highlighted = highlightSettingId == NotificationSettingsIds.NOTIFICATION_LIBRARIES,
                                onClick = { activeDialog = NotificationSettingsDialog.LibrariesPicker },
                            )
                        }
                    }
                    }
                }
            }

            if (!showAdvanced) {
                item {
                    HiddenSettingsHint(
                        hiddenCount = if (notifPrefs.enabled) 4 else 0,
                        onShowAdvanced = { viewModel.setShowAdvancedSettings(true) },
                    )
                }
            }
        }
        }
    }

    if (activeDialog is NotificationSettingsDialog.QuietStartPicker) {
        QuietHoursTimeSheet(
            title = stringResource(Res.string.settings_quiet_hours_start),
            initialMinutes = preferences.quietHoursStart,
            onDismiss = { activeDialog = NotificationSettingsDialog.None },
            onConfirm = { minutes ->
                viewModel.updateNotificationPreferences { it.copy(quietHoursStart = minutes) }
                activeDialog = NotificationSettingsDialog.None
            },
        )
    }

    if (activeDialog is NotificationSettingsDialog.QuietEndPicker) {
        QuietHoursTimeSheet(
            title = stringResource(Res.string.settings_quiet_hours_end),
            initialMinutes = preferences.quietHoursEnd,
            onDismiss = { activeDialog = NotificationSettingsDialog.None },
            onConfirm = { minutes ->
                viewModel.updateNotificationPreferences { it.copy(quietHoursEnd = minutes) }
                activeDialog = NotificationSettingsDialog.None
            },
        )
    }

    if (activeDialog is NotificationSettingsDialog.LibrariesPicker) {
        val notifPrefs = preferences
        TvSafeSheet(onDismissRequest = { activeDialog = NotificationSettingsDialog.None }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    stringResource(Res.string.settings_monitored_libraries),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(12.dp))
                if (libraryFolders.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.settings_no_libraries_found),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                } else {
                    libraryFolders.forEach { folder ->
                        val currentConfig = notifPrefs.libraryConfigs[folder.id] ?: LibraryNotificationConfig(enabled = true)
                        val toggleLibrary = {
                            val newConfig = currentConfig.copy(enabled = !currentConfig.enabled)
                            val newConfigs = notifPrefs.libraryConfigs.toMutableMap().apply {
                                put(folder.id, newConfig)
                            }
                            viewModel.updateNotificationPreferences { it.copy(libraryConfigs = newConfigs) }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = currentConfig.enabled,
                                    role = Role.Checkbox,
                                    onValueChange = { toggleLibrary() },
                                )
                                .padding(vertical = 8.dp, horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = currentConfig.enabled,
                                onCheckedChange = null,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = folder.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { activeDialog = NotificationSettingsDialog.None },
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = 24.dp),
                ) { Text(stringResource(Res.string.settings_done)) }
            }
        }
    }

    SettingsPickerDialog(
        state = activePicker,
        onDismiss = { activePicker = null },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietHoursTimeSheet(
    title: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = false,
    )
    TvSafeSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SheetHeader(title = title, icon = Tabler.Outline.Clock)
            TimePicker(state = timePickerState)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    onConfirm(timePickerState.hour * 60 + timePickerState.minute)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeCache.smoothPill,
            ) {
                Text(stringResource(Res.string.settings_ok))
            }
        }
    }
}
