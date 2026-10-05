package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.SettingsScreenPreferences
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.model.localizedDisplayName
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKeyEvent
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_about_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_backup_restore_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_biometric_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cache_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_contrast_suffix
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_default_speed_value
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_disabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dynamic_token
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_early_access_features
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_experimental
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_features_enabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_sections_visible
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_idle_ambient
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_idle_ambient_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_integrations_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_lang_default
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_language_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_lock_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_notifications_checking
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_oled_token
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_performance_token
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pin_biometric_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pin_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_privacy_data
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_privacy_data_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_whatsnew_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_whatsnew_title

/**
 * The browse pane: the settings root list shown while search is inactive —
 * the CompositionLocalProvider-wrapped LazyColumn wiring the profile /
 * power-user / devices / account / activity / system groups, the item rows,
 * and the TV screensaver + platform-gated desktop groups through
 * [settingsSection]. Relocated verbatim from [SettingsScreen]'s inline `else`
 * arm; the screen keeps the header chrome, dialogs, and state ownership.
 */
@Composable
internal fun SettingsBrowsePane(
    viewModel: SettingsViewModel,
    preferences: SettingsScreenPreferences,
    userName: String,
    currentServerAddress: String,
    isTv: Boolean,
    animateEntrance: Boolean,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    listFocusRequester: FocusRequester,
    onBack: () -> Unit,
    openSetting: (String, (String) -> Route) -> Unit,
    onNavigate: (Route) -> Unit,
    onSetupWizard: () -> Unit,
    onNewsletterClick: () -> Unit,
    lastClickedSettingId: String?,
    onLastClickedSettingIdChange: (String?) -> Unit,
    onSignOut: (fromServer: Boolean) -> Unit,
    activeDialogState: MutableState<PickerState<*>?>,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
        CompositionLocalProvider(LocalAnimateSettingsEntrance provides animateEntrance) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .then(Modifier
                        .tvFocusRestorer()
                        .focusRequester(listFocusRequester)
                        .onDpadKeyEvent(
                            onBack = { e ->
                                if (e.isKeyUp) { onBack() }
                                true
                            },
                        )
                    ),
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(
                    start = adaptiveInfo.contentPadding(LocalTvMode.current),
                    end = adaptiveInfo.contentPadding(LocalTvMode.current),
                    bottom = adaptiveInfo.bottomPadding(LocalTvMode.current),
                ),
            ) {
                settingsSection("profile", isTv) {
                    if (userName.isNotBlank()) {
                        SettingsProfileBanner(
                            userName = userName,
                            currentUser = viewModel.currentUser,
                            serverAddress = currentServerAddress,
                            isAdmin = viewModel.currentUser?.isAdmin == true,
                            onNewsletterClick = onNewsletterClick,
                            onUserManagementClick = { onNavigate(Route.UserManagement(null)) },
                            onServerManagementClick = { onNavigate(Route.ServerManagement(null)) },
                        )
                    }
                }

                settingsSection("power_user_mode", isTv) {
                    PowerUserModeCard(
                        checked = preferences.showAdvancedSettings,
                        onCheckedChange = { viewModel.edit { scope -> scope.appearance.setShowAdvancedSettings(it) } },
                    )
                }

                settingsSection("active_devices", isTv) {
                    if (viewModel.currentUser?.isAdmin == true && viewModel.activeSessions.isNotEmpty()) {
                        ActiveDevicesRow(
                            sessions = viewModel.activeSessions,
                            serverAddress = currentServerAddress,
                            onSendMessage = viewModel::sendMessageToSession,
                        )
                    }
                }

                settingsSection("account", isTv) {
                    SettingsAccountSection(
                        userName = userName,
                        isAdmin = viewModel.currentUser?.isAdmin == true,
                        openSetting = openSetting,
                        onSignOut = onSignOut,
                    )
                }

                settingsSection("activity", isTv) {
                    SettingsActivitySection(
                        viewModel = viewModel,
                        openSetting = openSetting,
                    )
                }

                settingsSection("system", isTv) {
                    SettingsSystemSection(
                        viewModel = viewModel,
                        openSetting = openSetting,
                        onSetupWizardClick = {
                            onLastClickedSettingIdChange(SystemRows.SetupWizard.id)
                            onSetupWizard()
                        },
                    )
                }

                settingsSection(HomeEntrance.section.key, isTv) {
                    val homePrefs by viewModel.homePreferences.collectAsStateWithLifecycle()
                    SettingListItem(
                        icon = HomeEntrance.icon,
                        title = stringResource(HomeEntrance.titleRes),
                        subtitle = stringResource(
                            Res.string.settings_home_sections_visible,
                            homePrefs.enabledHomeSectionTypes.size,
                            HomeSectionType.CONFIGURABLE.size,
                        ),
                        index = 0, count = 1,
                        onClick = { openSetting(HomeEntrance.rowId) { Route.HomeSettings(it) } },
                    )
                }

                // The converted domains' entrances: key, icon, title and
                // deep-link row id read the domain's declaration
                // ([AppearanceEntrance] and its wave siblings) — the same
                // declaration spliced into SETTINGS_ENTRANCE_SECTIONS derives
                // the section's entrance step — so emission and numbering
                // single-home on it.
                settingsSection(AppearanceEntrance.section.key, isTv) {
                    SettingListItem(
                        icon = AppearanceEntrance.icon,
                        title = stringResource(AppearanceEntrance.titleRes),
                        subtitle = appearanceSummarySubtitle(preferences),
                        index = 0, count = 1,
                        onClick = { openSetting(AppearanceEntrance.rowId) { Route.AppearanceSettings(it) } },
                    )
                }

                settingsSection(PlaybackEntrance.section.key, isTv) {
                    SettingListItem(
                        icon = PlaybackEntrance.icon,
                        title = stringResource(PlaybackEntrance.titleRes),
                        subtitle = stringResource(Res.string.settings_playback_subtitle, preferences.preferredPlayer.displayName),
                        index = 0, count = 1,
                        onClick = { openSetting(PlaybackEntrance.rowId) { Route.PlaybackSettings(it) } },
                    )
                }

                settingsSection(AudioEntrance.section.key, isTv) {
                    SettingListItem(
                        icon = AudioEntrance.icon,
                        title = stringResource(AudioEntrance.titleRes),
                        subtitle = stringResource(Res.string.settings_default_speed_value, if (preferences.audioDefaultSpeed == 1.0f) "1x" else "${preferences.audioDefaultSpeed}x"),
                        index = 0, count = 1,
                        onClick = { openSetting(AudioEntrance.rowId) { Route.AudioSettings(it) } },
                    )
                }

                settingsSection(LanguageEntrance.section.key, isTv) {
                    SettingListItem(
                        icon = LanguageEntrance.icon,
                        title = stringResource(LanguageEntrance.titleRes),
                        subtitle = stringResource(Res.string.settings_language_subtitle, preferences.preferredAudioLanguage ?: stringResource(Res.string.settings_lang_default)),
                        index = 0, count = 1,
                        onClick = { openSetting(LanguageEntrance.rowId) { Route.LanguageSettings(it) } },
                    )
                }

                // No desktop notification backend exists (the
                // NotificationSync seam no-ops there) — entry + screen
                // stay Android-only.
                if (settingsCapabilities.supportsNotifications) {
                    settingsSection(NotificationEntrance.section.key, isTv) {
                        val notifPrefs = preferences.notificationPreferences
                        SettingListItem(
                            icon = NotificationEntrance.icon,
                            title = stringResource(NotificationEntrance.titleRes),
                            subtitle = if (notifPrefs.enabled) stringResource(Res.string.settings_notifications_checking, notifPrefs.checkFrequency.localizedDisplayName().lowercase()) else stringResource(Res.string.settings_disabled),
                            index = 0, count = 1,
                            onClick = { openSetting(NotificationEntrance.rowId) { Route.NotificationSettings(it) } },
                        )
                    }
                }

                settingsSection(StorageEntrance.section.key, isTv) {
                    SettingListItem(
                        icon = StorageEntrance.icon,
                        title = stringResource(StorageEntrance.titleRes),
                        subtitle = stringResource(Res.string.settings_cache_subtitle, viewModel.cacheSizeMb),
                        index = 0, count = 1,
                        onClick = { openSetting(StorageEntrance.rowId) { Route.StorageSettings(it) } },
                    )
                }

                settingsSection(SecurityEntrance.section.key, isTv) {
                    SettingListItem(
                        icon = SecurityEntrance.icon,
                        title = stringResource(SecurityEntrance.titleRes),
                        subtitle = when {
                            preferences.pinLockEnabled && preferences.biometricLockEnabled -> stringResource(Res.string.settings_pin_biometric_on)
                            preferences.biometricLockEnabled -> stringResource(Res.string.settings_biometric_on)
                            preferences.pinLockEnabled -> stringResource(Res.string.settings_pin_on)
                            else -> stringResource(Res.string.settings_lock_off)
                        },
                        index = 0, count = 1,
                        onClick = { openSetting(SecurityEntrance.rowId) { Route.SecuritySettings(it) } },
                    )
                }

                settingsSection("item_privacy_data", isTv) {
                    SettingListItem(
                        icon = Tabler.Outline.ShieldLock,
                        title = stringResource(Res.string.settings_privacy_data),
                        subtitle = stringResource(Res.string.settings_privacy_data_subtitle),
                        index = 0, count = 1,
                        onClick = { openSetting("privacy_data") { Route.PrivacyData(it) } },
                    )
                }

                settingsSection(BackupEntrance.section.key, isTv) {
                    SettingListItem(
                        icon = BackupEntrance.icon,
                        title = stringResource(BackupEntrance.titleRes),
                        subtitle = stringResource(Res.string.settings_backup_restore_subtitle),
                        index = 0, count = 1,
                        onClick = { openSetting(BackupEntrance.rowId) { Route.BackupSettings(it) } },
                    )
                }

                if (isTv) {
                    settingsSection("group_screensaver", isTv) {
                        SettingsScreensaverSection(
                            preferences = preferences,
                            viewModel = viewModel,
                            lastClickedSettingId = lastClickedSettingId,
                            activeDialog = activeDialogState,
                        )
                    }
                }

                // the desktop idle "Ready to play" ambient
                // screen rows — capability-gated (structurally absent
                // on Android, whose lock-screen/screensaver story owns
                // idle display), rendered as their own group beside
                // the TV dream group so neither row total entangles.
                if (settingsCapabilities.supportsIdleAmbientScreen) {
                    settingsSection("group_idle_ambient", isTv) {
                        SettingsIdleAmbientSection(
                            preferences = preferences,
                            viewModel = viewModel,
                            lastClickedSettingId = lastClickedSettingId,
                            activeDialog = activeDialogState,
                        )
                    }
                }

                // the desktop Discord Rich Presence toggle (feature
                // 4.2) — capability-gated like the idle-ambient block
                // beside it (the IPC client lives in the desktop
                // shell only), rendered as its own group so neither
                // row total entangles.
                if (settingsCapabilities.supportsDiscordPresence) {
                    settingsSection("group_discord_presence", isTv) {
                        SettingsDiscordPresenceSection(
                            preferences = preferences,
                            viewModel = viewModel,
                            lastClickedSettingId = lastClickedSettingId,
                        )
                    }
                }

                // the desktop playback-event shell hooks (feature
                // 4.3) — same capability-gated shape; the command
                // rows open the shared free-form text editor.
                if (settingsCapabilities.supportsShellHooks) {
                    settingsSection("group_shell_hooks", isTv) {
                        SettingsShellHooksSection(
                            preferences = preferences,
                            viewModel = viewModel,
                            lastClickedSettingId = lastClickedSettingId,
                            activeDialog = activeDialogState,
                        )
                    }
                }

                settingsSection("item_experimental", isTv) {
                    SettingListItem(
                        icon = Tabler.Outline.Flask,
                        title = stringResource(Res.string.settings_experimental),
                        subtitle = experimentalSummarySubtitle(preferences),
                        index = 0, count = 1,
                        onClick = { openSetting(ExperimentalSettingsIds.EXPERIMENTAL) { Route.ExperimentalSettings(it) } },
                    )
                }

                settingsSection(IntegrationsEntrance.section.key, isTv) {
                    SettingListItem(
                        icon = IntegrationsEntrance.icon,
                        title = stringResource(IntegrationsEntrance.titleRes),
                        subtitle = stringResource(Res.string.settings_integrations_subtitle),
                        index = 0, count = 1,
                        onClick = { openSetting(IntegrationsEntrance.rowId) { Route.Integrations(it) } },
                    )
                }

                settingsSection(AboutEntrance.section.key, isTv) {
                    SettingListItem(
                        icon = AboutEntrance.icon,
                        title = stringResource(AboutEntrance.titleRes),
                        subtitle = stringResource(Res.string.settings_about_subtitle),
                        index = 0, count = 1,
                        onClick = { openSetting(AboutEntrance.rowId) { Route.About } },
                    )
                }

                settingsSection("item_whatsnew", isTv) {
                    SettingListItem(
                        icon = Tabler.Outline.Sparkles,
                        title = stringResource(Res.string.settings_whatsnew_title),
                        subtitle = stringResource(Res.string.settings_whatsnew_subtitle),
                        index = 0, count = 1,
                        onClick = { openSetting("whatsnew") { Route.WhatsNew } },
                    )
                }
            }
        }

}

// ---------------------------------------------------------------------------
// Landing-page summary builders — string-assembly policy freed from
// composition so SettingsSummariesTest (jvmTest) can pin which prefs appear,
// in what order, the separator, and the contrast-suffix casing without a
// composer. The builders emit renderable PARTS (resource identity + args,
// never resolved text): the Compose compiler forbids `stringResource` calls
// inside non-inline lambdas, so resolution cannot ride a plain resolver
// lambda — instead the screen resolves each part at composition through the
// inline `map` below ([SettingsSummaryPart.resolveAtComposition]) and hands
// the rendered tokens to the pure [joinSummaryTokens] join. Recomposition
// stays correct: every resource read happens at composition time.
// ---------------------------------------------------------------------------

/** One renderable piece of a landing-page summary row subtitle. */
internal sealed interface SettingsSummaryPart {
    /** Pre-composed literal text (the Title-cased theme-mode name). */
    data class Literal(val text: String) : SettingsSummaryPart

    /** A plain string-resource token. */
    data class Token(val resource: StringResource) : SettingsSummaryPart

    /** A formatted string-resource token (the contrast suffix with its cased label). */
    data class Formatted(val resource: StringResource, val arg: String) : SettingsSummaryPart

    /** A plural resource carrying its own count (features-enabled). */
    data class Plural(val resource: PluralStringResource, val count: Int) : SettingsSummaryPart
}

/**
 * Appearance row subtitle parts: theme mode first (Title-cased enum name),
 * then the Dynamic / OLED / contrast / Performance tokens for the enabled
 * prefs. The contrast token suffixes the Title-cased enum name (`Medium
 * contrast`, `High contrast`) and only appears off-DEFAULT.
 */
internal fun appearanceSummaryParts(preferences: SettingsScreenPreferences): List<SettingsSummaryPart> = buildList {
    add(SettingsSummaryPart.Literal(preferences.themeMode.name.lowercase().replaceFirstChar { it.uppercase() }))
    if (preferences.dynamicTheming) add(SettingsSummaryPart.Token(Res.string.settings_dynamic_token))
    if (preferences.oledMode) add(SettingsSummaryPart.Token(Res.string.settings_oled_token))
    if (preferences.contrastLevel != ContrastLevel.DEFAULT) add(
        SettingsSummaryPart.Formatted(
            Res.string.settings_contrast_suffix,
            preferences.contrastLevel.name.lowercase().replaceFirstChar { it.uppercase() },
        ),
    )
    if (preferences.performanceMode) add(SettingsSummaryPart.Token(Res.string.settings_performance_token))
}

/**
 * Experimental row subtitle parts: the early-access placeholder token when
 * nothing is enabled, otherwise the "%d feature(s) enabled" plural over the
 * count.
 */
internal fun experimentalSummaryParts(preferences: SettingsScreenPreferences): List<SettingsSummaryPart> {
    val count = preferences.enabledExperimentalFeatures.size
    return listOf(
        if (count == 0) SettingsSummaryPart.Token(Res.string.settings_early_access_features)
        else SettingsSummaryPart.Plural(Res.plurals.settings_features_enabled, count),
    )
}

/** Joins rendered summary tokens with the summary separator policy (", "). */
internal fun joinSummaryTokens(rendered: List<String>): String = rendered.joinToString(", ")

/** Resolves a summary part at composition; called from the inline `map` lambdas below. */
@Composable
private fun SettingsSummaryPart.resolveAtComposition(): String = when (this) {
    is SettingsSummaryPart.Literal -> text
    is SettingsSummaryPart.Token -> stringResource(resource)
    is SettingsSummaryPart.Formatted -> stringResource(resource, arg)
    is SettingsSummaryPart.Plural -> pluralStringResource(resource, count, count)
}

@Composable
private fun appearanceSummarySubtitle(preferences: SettingsScreenPreferences): String =
    joinSummaryTokens(appearanceSummaryParts(preferences).map { it.resolveAtComposition() })

@Composable
private fun experimentalSummarySubtitle(preferences: SettingsScreenPreferences): String =
    joinSummaryTokens(experimentalSummaryParts(preferences).map { it.resolveAtComposition() })
