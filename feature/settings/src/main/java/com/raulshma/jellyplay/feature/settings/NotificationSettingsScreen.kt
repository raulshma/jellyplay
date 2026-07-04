package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.clickable
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.model.CheckFrequency
import com.raulshma.jellyplay.core.model.LibraryNotificationConfig
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

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
    object FrequencyPicker : NotificationSettingsDialog()
    object QuietStartPicker : NotificationSettingsDialog()
    object QuietEndPicker : NotificationSettingsDialog()
    object MaxPerCheckPicker : NotificationSettingsDialog()
    object LibrariesPicker : NotificationSettingsDialog()
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences = viewModel.preferences
    val showAdvanced = preferences.showAdvancedSettings
    val context = LocalContext.current
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    var activeDialog by remember { mutableStateOf<NotificationSettingsDialog>(NotificationSettingsDialog.None) }
    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()
    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = "notifications_init",
    )

    JellyPlayScreenScaffold(
        title = stringResource(R.string.settings_notifications),
        onBack = onBack,
        backgroundColor = backgroundColor,
        actions = {
            AdvancedSettingsToggleButton(
                showAdvanced = showAdvanced,
                onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
            )
        },
    ) { innerPadding ->
        val notifPrefs = preferences.notificationPreferences

        // Center a highlighted (search-navigated) setting in the viewport instead of parking it
        // at the bottom edge, which is the default BringIntoViewSpec behaviour.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides
                com.raulshma.jellyplay.core.ui.tv.CenterBringIntoViewSpec
        ) {
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
                    title = stringResource(R.string.settings_notifications),
                    summary = {
                        if (notifPrefs.enabled) stringResource(R.string.settings_notifications_checking, notifPrefs.checkFrequency.displayName.lowercase())
                        else stringResource(R.string.settings_disabled)
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    var notifIdx = 0
                    val notifBaseTotal = run {
                        var c = 1
                        if (notifPrefs.enabled) {
                            c += 4 // Check Frequency, Sound, Vibrate, Lights
                            if (showAdvanced) {
                                c += 5 // Quiet Hours, Max Per Check, Libraries, Respect System DND, System Notification Settings
                                if (notifPrefs.quietHoursEnabled) c += 2
                            }
                        }
                        c
                    }
                    val notifTotal = notifBaseTotal

                    SettingToggleItem(
                        icon = Tabler.Outline.Bell,
                        title = stringResource(R.string.settings_enable_notifications),
                        subtitle = stringResource(R.string.settings_enable_notifications_subtitle),
                        checked = notifPrefs.enabled,
                        highlighted = highlightSettingId == "notifications_enable",
                        index = notifIdx++, count = notifTotal,
                        onCheckedChange = { enabled ->
                            viewModel.updateNotificationPreferences { it.copy(enabled = enabled) }
                        },
                    )
                    if (notifPrefs.enabled) {
                        SettingListItem(
                            icon = Tabler.Outline.Clock,
                            title = stringResource(R.string.settings_check_frequency),
                            subtitle = stringResource(R.string.settings_check_frequency_subtitle),
                            trailingText = notifPrefs.checkFrequency.displayName,
                            highlighted = highlightSettingId == "notification_check_frequency",
                            index = notifIdx++, count = notifTotal,
                            onClick = { activeDialog = NotificationSettingsDialog.FrequencyPicker },
                        )
                        if (showAdvanced) {
                            SettingToggleItem(
                                icon = Tabler.Outline.Moon,
                                title = stringResource(R.string.settings_quiet_hours),
                                subtitle = stringResource(R.string.settings_quiet_hours_subtitle),
                                checked = notifPrefs.quietHoursEnabled,
                                highlighted = highlightSettingId == "quiet_hours",
                                index = notifIdx++, count = notifTotal,
                                onCheckedChange = { enabled ->
                                    viewModel.updateNotificationPreferences { it.copy(quietHoursEnabled = enabled) }
                                },
                            )
                            if (notifPrefs.quietHoursEnabled) {
                                SettingListItem(
                                    icon = Tabler.Outline.Sunset,
                                    title = stringResource(R.string.settings_quiet_start),
                                    subtitle = stringResource(R.string.settings_quiet_start_subtitle),
                                    trailingText = formatMinutes(notifPrefs.quietHoursStart),
                                    highlighted = highlightSettingId == "quiet_start",
                                    index = notifIdx++, count = notifTotal,
                                    onClick = { activeDialog = NotificationSettingsDialog.QuietStartPicker },
                                )
                                SettingListItem(
                                    icon = Tabler.Outline.Sunrise,
                                    title = stringResource(R.string.settings_quiet_end),
                                    subtitle = stringResource(R.string.settings_quiet_end_subtitle),
                                    trailingText = formatMinutes(notifPrefs.quietHoursEnd),
                                    highlighted = highlightSettingId == "quiet_end",
                                    index = notifIdx++, count = notifTotal,
                                    onClick = { activeDialog = NotificationSettingsDialog.QuietEndPicker },
                                )
                            }
                            SettingToggleItem(
                                icon = Tabler.Outline.BellOff,
                                title = stringResource(R.string.settings_respect_system_dnd),
                                subtitle = stringResource(R.string.settings_respect_system_dnd_subtitle),
                                checked = notifPrefs.respectSystemDnd,
                                highlighted = highlightSettingId == "respect_system_dnd",
                                index = notifIdx++, count = notifTotal,
                                onCheckedChange = { enabled ->
                                    viewModel.updateNotificationPreferences { it.copy(respectSystemDnd = enabled) }
                                },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Settings,
                                title = stringResource(R.string.settings_system_notification_settings),
                                subtitle = stringResource(R.string.settings_system_notification_settings_subtitle),
                                highlighted = highlightSettingId == "system_notification_settings",
                                index = notifIdx++, count = notifTotal,
                                onClick = {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    context.startActivity(intent)
                                },
                            )
                        }
                        SettingToggleItem(
                            icon = Tabler.Outline.Volume,
                            title = stringResource(R.string.settings_sound),
                            subtitle = stringResource(R.string.settings_sound_subtitle),
                            checked = notifPrefs.soundEnabled,
                            highlighted = highlightSettingId == "notification_sound",
                            index = notifIdx++, count = notifTotal,
                            onCheckedChange = { enabled ->
                                viewModel.updateNotificationPreferences { it.copy(soundEnabled = enabled) }
                            },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Bell,
                            title = stringResource(R.string.settings_vibrate),
                            subtitle = stringResource(R.string.settings_vibrate_subtitle),
                            checked = notifPrefs.vibrateEnabled,
                            highlighted = highlightSettingId == "notification_vibrate",
                            index = notifIdx++, count = notifTotal,
                            onCheckedChange = { enabled ->
                                viewModel.updateNotificationPreferences { it.copy(vibrateEnabled = enabled) }
                            },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Bulb,
                            title = stringResource(R.string.settings_notification_lights),
                            subtitle = stringResource(R.string.settings_notification_lights_subtitle),
                            checked = notifPrefs.lightsEnabled,
                            highlighted = highlightSettingId == "notification_lights",
                            index = notifIdx++, count = notifTotal,
                            onCheckedChange = { enabled ->
                                viewModel.updateNotificationPreferences { it.copy(lightsEnabled = enabled) }
                            },
                        )
                        if (showAdvanced) {
                            SettingListItem(
                                icon = Tabler.Outline.LetterCase,
                                title = stringResource(R.string.settings_max_per_check),
                                subtitle = stringResource(R.string.settings_max_per_check_subtitle),
                                trailingText = "${notifPrefs.maxPerCheck}",
                                highlighted = highlightSettingId == "max_per_check",
                                index = notifIdx++, count = notifTotal,
                                onClick = { activeDialog = NotificationSettingsDialog.MaxPerCheckPicker },
                            )
                            val libraryCount = viewModel.libraryFolders.size
                            val enabledLibraries = viewModel.libraryFolders.count { folder ->
                                notifPrefs.libraryConfigs[folder.id]?.enabled ?: true
                            }
                            SettingListItem(
                                icon = Tabler.Outline.Folders,
                                title = stringResource(R.string.settings_libraries),
                                subtitle = stringResource(R.string.settings_libraries_subtitle, enabledLibraries, libraryCount),
                                highlighted = highlightSettingId == "notification_libraries",
                                index = notifIdx, count = notifTotal,
                                onClick = { activeDialog = NotificationSettingsDialog.LibrariesPicker },
                            )
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

    if (activeDialog is NotificationSettingsDialog.FrequencyPicker) {
        val notifPrefs = preferences.notificationPreferences
        AlertDialog(
            onDismissRequest = { activeDialog = NotificationSettingsDialog.None },
            title = { Text(stringResource(R.string.settings_check_frequency)) },
            text = {
                Column {
                    CheckFrequency.entries.forEachIndexed { index, freq ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateNotificationPreferences {
                                        it.copy(checkFrequency = CheckFrequency.entries[index])
                                    }
                                    activeDialog = NotificationSettingsDialog.None
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = index == CheckFrequency.entries.indexOf(notifPrefs.checkFrequency),
                                onClick = {
                                    viewModel.updateNotificationPreferences {
                                        it.copy(checkFrequency = CheckFrequency.entries[index])
                                    }
                                    activeDialog = NotificationSettingsDialog.None
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = freq.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = NotificationSettingsDialog.None }) { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }

    if (activeDialog is NotificationSettingsDialog.QuietStartPicker) {
        val notifPrefs = preferences.notificationPreferences
        val timePickerState = rememberTimePickerState(
            initialHour = notifPrefs.quietHoursStart / 60,
            initialMinute = notifPrefs.quietHoursStart % 60,
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = { activeDialog = NotificationSettingsDialog.None },
            title = { Text(stringResource(R.string.settings_quiet_hours_start)) },
            text = {
                androidx.compose.material3.TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateNotificationPreferences {
                            it.copy(quietHoursStart = timePickerState.hour * 60 + timePickerState.minute)
                        }
                        activeDialog = NotificationSettingsDialog.None
                    }
                ) { Text(stringResource(R.string.settings_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { activeDialog = NotificationSettingsDialog.None }) { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }

    if (activeDialog is NotificationSettingsDialog.QuietEndPicker) {
        val notifPrefs = preferences.notificationPreferences
        val timePickerState = rememberTimePickerState(
            initialHour = notifPrefs.quietHoursEnd / 60,
            initialMinute = notifPrefs.quietHoursEnd % 60,
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = { activeDialog = NotificationSettingsDialog.None },
            title = { Text(stringResource(R.string.settings_quiet_hours_end)) },
            text = {
                androidx.compose.material3.TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateNotificationPreferences {
                            it.copy(quietHoursEnd = timePickerState.hour * 60 + timePickerState.minute)
                        }
                        activeDialog = NotificationSettingsDialog.None
                    }
                ) { Text(stringResource(R.string.settings_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { activeDialog = NotificationSettingsDialog.None }) { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }

    if (activeDialog is NotificationSettingsDialog.MaxPerCheckPicker) {
        val notifPrefs = preferences.notificationPreferences
        val options = listOf(5, 10, 15, 20, 30, 50, 100)
        AlertDialog(
            onDismissRequest = { activeDialog = NotificationSettingsDialog.None },
            title = { Text(stringResource(R.string.settings_max_items_per_check)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { opt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateNotificationPreferences { it.copy(maxPerCheck = opt) }
                                    activeDialog = NotificationSettingsDialog.None
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = opt == notifPrefs.maxPerCheck,
                                onClick = {
                                    viewModel.updateNotificationPreferences { it.copy(maxPerCheck = opt) }
                                    activeDialog = NotificationSettingsDialog.None
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = stringResource(R.string.settings_items_count, opt), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = NotificationSettingsDialog.None }) { Text(stringResource(R.string.settings_cancel)) }
            }
        )
    }

    if (activeDialog is NotificationSettingsDialog.LibrariesPicker) {
        val notifPrefs = preferences.notificationPreferences
        AlertDialog(
            onDismissRequest = { activeDialog = NotificationSettingsDialog.None },
            title = { Text(stringResource(R.string.settings_monitored_libraries)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (viewModel.libraryFolders.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_no_libraries_found),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        viewModel.libraryFolders.forEach { folder ->
                            val currentConfig = notifPrefs.libraryConfigs[folder.id] ?: LibraryNotificationConfig(enabled = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val newConfig = currentConfig.copy(enabled = !currentConfig.enabled)
                                        val newConfigs = notifPrefs.libraryConfigs.toMutableMap().apply {
                                            put(folder.id, newConfig)
                                        }
                                        viewModel.updateNotificationPreferences { it.copy(libraryConfigs = newConfigs) }
                                    }
                                    .padding(vertical = 8.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = currentConfig.enabled,
                                    onCheckedChange = { checked ->
                                        val newConfig = currentConfig.copy(enabled = checked)
                                        val newConfigs = notifPrefs.libraryConfigs.toMutableMap().apply {
                                            put(folder.id, newConfig)
                                        }
                                        viewModel.updateNotificationPreferences { it.copy(libraryConfigs = newConfigs) }
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(text = folder.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = NotificationSettingsDialog.None }) { Text(stringResource(R.string.settings_done)) }
            }
        )
    }
}
