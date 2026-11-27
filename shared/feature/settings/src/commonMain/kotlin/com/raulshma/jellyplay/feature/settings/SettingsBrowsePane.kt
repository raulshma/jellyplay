package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.PerformanceLevel
import com.raulshma.jellyplay.core.model.SettingsScreenPreferences
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.model.labelResource
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKeyEvent
import com.raulshma.jellyplay.core.ui.tv.tvFocusGroupRestorer
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_about_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_backup_restore_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_contrast_suffix
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dynamic_token
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_early_access_features
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_experimental
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_features_enabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_integrations_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_lang_default
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_oled_token
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_privacy_data
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_privacy_data_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_whatsnew_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_whatsnew_title

/**
 * The browse pane: the settings root list shown while search is inactive —
 * the CompositionLocalProvider-wrapped LazyColumn emitting the root's flat
 * entrance/action rows under the declared non-clickable category headers
 * (ADR-0017), in the media-first order [SETTINGS_ENTRANCE_SECTIONS]
 * declares: account pinned top, then Media & playback (Playback / Audio /
 * Language), Look & feel (Appearance / Home / Performance / Ambient),
 * System & data (Storage / Notifications / Security / Privacy / Backup /
 * System), Integrations (plugin / Integrations / Experimental) and About.
 * The former inline tuning groups are gone — screensaver + idle-ambient
 * live on the Ambient screen, Discord + shell hooks on Integrations, the
 * plugin section on its own screen (each behind its gate, as here).
 * The screen keeps the header chrome, dialogs, and state ownership.
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
    onLastClickedSettingIdChange: (String?) -> Unit,
    onSignOut: (fromServer: Boolean) -> Unit,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
        CompositionLocalProvider(LocalAnimateSettingsEntrance provides animateEntrance) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .then(Modifier
                        .tvFocusGroupRestorer()
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
                            // The plugin's newsletter entry: probe AVAILABLE +
                            // feature exposed + the user's toggle on.
                            newsletterVisible =
                                isJellyPlayFeatureShown(viewModel, com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures.Newsletter),
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

                // The Account category's device row: admin-gated content in a
                // always-composed slot (the category header renders even when
                // the row drops).
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

                // ── Media & Playback ────────────────────────────────────
                // Each entrance's subtitle is its domain's live summary —
                // a pure *SummaryParts fold over the slice the domain screen
                // reads (exposed on the VM), resolved part-by-part at
                // composition (ADR-0017 decision 3; folds jvmTest-pinned).
                settingsSection(PlaybackEntrance.section.key, isTv) {
                    val playback by viewModel.playbackPreferences.collectAsStateWithLifecycle()
                    SettingListItem(
                        icon = PlaybackEntrance.icon,
                        title = stringResource(PlaybackEntrance.titleRes),
                        subtitle = joinSummaryTokens(playbackSummaryParts(playback).map { it.resolveAtComposition() }),
                        index = 0, count = 1,
                        onClick = { openSetting(PlaybackEntrance.rowId) { Route.PlaybackSettings(it) } },
                    )
                }

                settingsSection(AudioEntrance.section.key, isTv) {
                    val audio by viewModel.audioPreferences.collectAsStateWithLifecycle()
                    SettingListItem(
                        icon = AudioEntrance.icon,
                        title = stringResource(AudioEntrance.titleRes),
                        subtitle = joinSummaryTokens(audioSummaryParts(audio).map { it.resolveAtComposition() }),
                        index = 0, count = 1,
                        onClick = { openSetting(AudioEntrance.rowId) { Route.AudioSettings(it) } },
                    )
                }

                settingsSection(LanguageEntrance.section.key, isTv) {
                    val language by viewModel.languagePreferences.collectAsStateWithLifecycle()
                    val langDefaultFace = stringResource(Res.string.settings_lang_default)
                    SettingListItem(
                        icon = LanguageEntrance.icon,
                        title = stringResource(LanguageEntrance.titleRes),
                        subtitle = joinSummaryTokens(
                            languageSummaryParts(language, langDefaultFace).map { it.resolveAtComposition() },
                        ),
                        index = 0, count = 1,
                        onClick = { openSetting(LanguageEntrance.rowId) { Route.LanguageSettings(it) } },
                    )
                }

                // ── Look & Feel ─────────────────────────────────────────
                settingsSection(AppearanceEntrance.section.key, isTv) {
                    SettingListItem(
                        icon = AppearanceEntrance.icon,
                        title = stringResource(AppearanceEntrance.titleRes),
                        subtitle = appearanceSummarySubtitle(preferences),
                        index = 0, count = 1,
                        onClick = { openSetting(AppearanceEntrance.rowId) { Route.AppearanceSettings(it) } },
                    )
                }

                settingsSection(HomeEntrance.section.key, isTv) {
                    val homePrefs by viewModel.homePreferences.collectAsStateWithLifecycle()
                    SettingListItem(
                        icon = HomeEntrance.icon,
                        title = stringResource(HomeEntrance.titleRes),
                        subtitle = joinSummaryTokens(homeSummaryParts(homePrefs).map { it.resolveAtComposition() }),
                        index = 0, count = 1,
                        onClick = { openSetting(HomeEntrance.rowId) { Route.HomeSettings(it) } },
                    )
                }

                // The Performance value face, not the static explainer
                // sentence — the explainer wrapped to two lines in most
                // locales and broke the root list's uniform row height. Same
                // value pattern as the playback/audio/language entrances
                // beside it; the hub screen owns the explanatory copy.
                settingsSection(PerformanceEntrance.section.key, isTv) {
                    val reduceMotion by viewModel.reduceMotionEnabled.collectAsStateWithLifecycle()
                    SettingListItem(
                        icon = PerformanceEntrance.icon,
                        title = stringResource(PerformanceEntrance.titleRes),
                        subtitle = joinSummaryTokens(
                            performanceSummaryParts(preferences.performanceLevel, reduceMotion)
                                .map { it.resolveAtComposition() },
                        ),
                        index = 0, count = 1,
                        onClick = { openSetting(PerformanceEntrance.rowId) { Route.PerformanceSettings(it) } },
                    )
                }

                // The Ambient entrance (ADR-0017): the TV dream group + the
                // desktop idle-ambient group as ONE screen — visible when
                // either gate can show content (TV mode OR the idle-screen
                // capability). The summary reads the same fork the gate does.
                settingsSection(AmbientEntrance.section.key, isTv) {
                    if (ambientScreenAdmitted(isTv)) {
                        SettingListItem(
                            icon = AmbientEntrance.icon,
                            title = stringResource(AmbientEntrance.titleRes),
                            subtitle = joinSummaryTokens(
                                ambientSummaryParts(
                                    isTv = isTv,
                                    idleAmbientEnabled = preferences.idleAmbientEnabled,
                                    idleAmbientTimeoutMin = preferences.idleAmbientTimeoutMin,
                                    dreamShowTitle = preferences.dreamShowTitle,
                                    dreamKenBurnsEnabled = preferences.dreamKenBurnsEnabled,
                                ).map { it.resolveAtComposition() },
                            ),
                            index = 0, count = 1,
                            onClick = { openSetting(AmbientEntrance.rowId) { Route.AmbientSettings(it) } },
                        )
                    }
                }

                // ── System & Data ───────────────────────────────────────
                settingsSection(StorageEntrance.section.key, isTv) {
                    val storage by viewModel.storagePreferences.collectAsStateWithLifecycle()
                    SettingListItem(
                        icon = StorageEntrance.icon,
                        title = stringResource(StorageEntrance.titleRes),
                        subtitle = joinSummaryTokens(
                            storageSummaryParts(storage, viewModel.cacheSizeMb).map { it.resolveAtComposition() },
                        ),
                        index = 0, count = 1,
                        onClick = { openSetting(StorageEntrance.rowId) { Route.StorageSettings(it) } },
                    )
                }

                // The Downloads sibling entrance (ADR-0017's storage split):
                // the live-subtitle entrance opening the Downloads screen the
                // cache/network screen's group moved out of.
                settingsSection(DownloadsEntrance.section.key, isTv) {
                    val downloads by viewModel.downloadPreferences.collectAsStateWithLifecycle()
                    SettingListItem(
                        icon = DownloadsEntrance.icon,
                        title = stringResource(DownloadsEntrance.titleRes),
                        subtitle = joinSummaryTokens(downloadsSummaryParts(downloads).map { it.resolveAtComposition() }),
                        index = 0, count = 1,
                        onClick = { openSetting(DownloadsEntrance.rowId) { Route.DownloadsSettings(it) } },
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
                            subtitle = joinSummaryTokens(
                                notificationsSummaryParts(notifPrefs).map { it.resolveAtComposition() },
                            ),
                            index = 0, count = 1,
                            onClick = { openSetting(NotificationEntrance.rowId) { Route.NotificationSettings(it) } },
                        )
                    }
                }

                settingsSection(SecurityEntrance.section.key, isTv) {
                    SettingListItem(
                        icon = SecurityEntrance.icon,
                        title = stringResource(SecurityEntrance.titleRes),
                        subtitle = joinSummaryTokens(
                            securitySummaryParts(preferences.pinLockEnabled, preferences.biometricLockEnabled)
                                .map { it.resolveAtComposition() },
                        ),
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

                // ── Integrations ────────────────────────────────────────
                // The companion plugin's ONE entrance (ADR-0017): the root's
                // former inline plugin section is now one gated row into the
                // plugin screen. The slot always composes (it carries the
                // Integrations category header), the probe rides the slot's
                // visibility exactly as the inline section's did — UNKNOWN
                // probes once; UNAVAILABLE composes nothing except the
                // refused-contract banner (the drift's only surface);
                // AVAILABLE renders the row.
                settingsSection(JellyPlayPluginEntrance.section.key, isTv) {
                    val pluginStatus by viewModel.jellyPlayPluginStatus.collectAsStateWithLifecycle()
                    val contractMismatch by viewModel.jellyPlayContractMismatch.collectAsStateWithLifecycle()
                    LaunchedEffect(pluginStatus) {
                        if (pluginStatus == com.raulshma.jellyplay.core.model.JellyPlayPluginStatus.UNKNOWN) {
                            viewModel.refreshJellyPlayPluginStatus()
                        }
                    }
                    val mismatch = contractMismatch
                    when {
                        pluginStatus == com.raulshma.jellyplay.core.model.JellyPlayPluginStatus.UNAVAILABLE && mismatch != null ->
                            JellyPlayContractMismatchBanner(mismatch)
                        pluginStatus == com.raulshma.jellyplay.core.model.JellyPlayPluginStatus.AVAILABLE -> {
                            // The live summary's inputs: the sync engine's
                            // enabled flag and the probe-exposed features the
                            // user's toggles leave on (the section's own
                            // switch-state intersection).
                            val syncState by viewModel.jellyPlaySyncState.collectAsStateWithLifecycle()
                            val exposedFeatures by viewModel.jellyPlayPluginFeatures.collectAsStateWithLifecycle()
                            val featureToggles by viewModel.jellyPlayFeatureToggles.collectAsStateWithLifecycle()
                            SettingListItem(
                                icon = JellyPlayPluginEntrance.icon,
                                title = stringResource(JellyPlayPluginEntrance.titleRes),
                                subtitle = joinSummaryTokens(
                                    jellyPlayPluginSummaryParts(
                                        syncEnabled = syncState.enabled,
                                        enabledFeatureCount = exposedFeatures.count { it in featureToggles },
                                    ).map { it.resolveAtComposition() },
                                ),
                                index = 0, count = 1,
                                onClick = { openSetting(JellyPlayPluginEntrance.rowId) { Route.JellyPlayPluginSettings(it) } },
                            )
                        }
                    }
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

                settingsSection("item_experimental", isTv) {
                    SettingListItem(
                        icon = Tabler.Outline.Flask,
                        title = stringResource(Res.string.settings_experimental),
                        subtitle = experimentalSummarySubtitle(preferences),
                        index = 0, count = 1,
                        onClick = { openSetting(ExperimentalSettingsIds.EXPERIMENTAL) { Route.ExperimentalSettings(it) } },
                    )
                }

                // ── About ───────────────────────────────────────────────
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

    /** A formatted string-resource token; [arg] carries the placeholder's own type (String/Int/Long). */
    data class Formatted(val resource: StringResource, val arg: Any) : SettingsSummaryPart

    /** A two-argument formatted string-resource token (the count-pair faces). */
    data class Formatted2(val resource: StringResource, val arg1: Any, val arg2: Any) : SettingsSummaryPart

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
    // The master level's own face off the level, not the derived legacy
    // boolean — the middle/essential faces must surface too. The face resolves
    // through the PreferenceEnumNames seam (same localized words as the
    // pickers and the player sheet's Level line — never the raw enum name).
    if (preferences.performanceLevel != PerformanceLevel.STANDARD) {
        add(SettingsSummaryPart.Token(preferences.performanceLevel.labelResource()))
    }
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

/**
 * The default-speed face the playback/audio summaries share ("1x", "1.5x") —
 * the row faces' long-standing shape, extracted so both folds render it one
 * way (and the test can pin it).
 */
internal fun defaultSpeedFace(speed: Float): String =
    if (speed == 1.0f) "1x" else "${speed}x"

/**
 * Resolves a summary part at composition; called from inline `map` lambdas at
 * every summary wiring site (the root browse pane and the three domain hub
 * screens — internal so the hub screens share the one resolver).
 */
@Composable
internal fun SettingsSummaryPart.resolveAtComposition(): String = when (this) {
    is SettingsSummaryPart.Literal -> text
    is SettingsSummaryPart.Token -> stringResource(resource)
    is SettingsSummaryPart.Formatted -> stringResource(resource, arg)
    is SettingsSummaryPart.Formatted2 -> stringResource(resource, arg1, arg2)
    is SettingsSummaryPart.Plural -> pluralStringResource(resource, count, count)
}

@Composable
private fun appearanceSummarySubtitle(preferences: SettingsScreenPreferences): String =
    joinSummaryTokens(appearanceSummaryParts(preferences).map { it.resolveAtComposition() })

@Composable
private fun experimentalSummarySubtitle(preferences: SettingsScreenPreferences): String =
    joinSummaryTokens(experimentalSummaryParts(preferences).map { it.resolveAtComposition() })
