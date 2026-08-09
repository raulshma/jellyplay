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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.model.CheckFrequency
import com.raulshma.jellyplay.core.model.LibraryNotificationConfig
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
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
    object QuietStartPicker : NotificationSettingsDialog()
    object QuietEndPicker : NotificationSettingsDialog()
    object LibrariesPicker : NotificationSettingsDialog()
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()
    val libraryFolders by viewModel.libraryFolders.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    var activeDialog by remember { mutableStateOf<NotificationSettingsDialog>(NotificationSettingsDialog.None) }
    var activePicker by remember { mutableStateOf<PickerState<*>?>(null) }
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
        val notifPrefs = preferences

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
                        val frequencyTitle = stringResource(R.string.settings_check_frequency)
                        SettingListItem(
                            icon = Tabler.Outline.Clock,
                            title = frequencyTitle,
                            subtitle = stringResource(R.string.settings_check_frequency_subtitle),
                            trailingText = notifPrefs.checkFrequency.displayName,
                            highlighted = highlightSettingId == "notification_check_frequency",
                            index = notifIdx++, count = notifTotal,
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
                            val maxPerCheckTitle = stringResource(R.string.settings_max_per_check)
                            val countFormat = stringResource(R.string.settings_items_count, 0)
                            SettingListItem(
                                icon = Tabler.Outline.LetterCase,
                                title = maxPerCheckTitle,
                                subtitle = stringResource(R.string.settings_max_per_check_subtitle),
                                trailingText = "${notifPrefs.maxPerCheck}",
                                highlighted = highlightSettingId == "max_per_check",
                                index = notifIdx++, count = notifTotal,
                                onClick = {
                                    val options = listOf(5, 10, 15, 20, 30, 50, 100)
                                    activePicker = PickerState.List(
                                        title = maxPerCheckTitle,
                                        items = options,
                                        label = { count -> countFormat.format(count) },
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

    if (activeDialog is NotificationSettingsDialog.QuietStartPicker) {
        QuietHoursTimeSheet(
            title = stringResource(R.string.settings_quiet_hours_start),
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
            title = stringResource(R.string.settings_quiet_hours_end),
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
                    stringResource(R.string.settings_monitored_libraries),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(12.dp))
                if (libraryFolders.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_no_libraries_found),
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
                ) { Text(stringResource(R.string.settings_done)) }
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
                Text(stringResource(R.string.settings_ok))
            }
        }
    }
}
