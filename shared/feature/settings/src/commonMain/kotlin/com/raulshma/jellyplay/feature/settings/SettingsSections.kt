package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.DreamImageCategory
import com.raulshma.jellyplay.core.model.DreamTransitionStyle
import com.raulshma.jellyplay.core.model.PARENTAL_RATING_PICKER_LADDER
import com.raulshma.jellyplay.core.model.SettingsScreenPreferences
import com.raulshma.jellyplay.core.model.parentalRatingAge
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_account
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_activity_insights
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_activity_insights_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_category_movies
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_category_music
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_category_photos
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_category_tv
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discord_presence
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discord_presence_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discord_presence_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_display_media_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dream_dim_after_1_minute
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dream_dim_after_30_seconds
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dream_dim_after_5_minutes
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dream_dim_after_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dream_rating_none
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_media_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_ken_burns_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_ken_burns_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hooks
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hooks_cmd_not_set
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hooks_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hooks_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hooks_placeholder_hint
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_idle_ambient
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_idle_ambient_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_idle_ambient_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_idle_ambient_timeout_minutes
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_idle_ambient_timeout_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_signed_in_as_name
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_screensaver
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_system
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_system_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_transition_crossfade
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_transition_none
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_transition_slide

/** The `account` section: the Account / Users / Servers SettingsGroup (records: AccountRowRecords). */
@Composable
internal fun SettingsAccountSection(
    userName: String,
    isAdmin: Boolean,
    openSetting: (String, (String) -> Route) -> Unit,
    onSignOut: (fromServer: Boolean) -> Unit
) {
                            SettingsGroup(
                                icon = Tabler.Outline.User,
                                title = stringResource(Res.string.settings_account),
                                summary = { stringResource(Res.string.settings_signed_in_as_name, userName) },
                                badge = {
                                    RoleBadge(isAdmin = isAdmin)
                                },
                                initiallyExpanded = false,
                            ) {
                                // Row count derived from the account group
                                // declaration's row gates — the four declared
                                // rows are exactly the rows rendered here.
                                val accountCount = rowTotalFor(SettingsScreenGroups.account, RowAdmissionFlags())
                                SettingListItem(
                                    icon = rowIcon(AccountRows.ServerManagement),
                                    title = rowTitle(AccountRows.ServerManagement),
                                    subtitle = rowSubtitle(AccountRows.ServerManagement),
                                    index = 0, count = accountCount,
                                    onClick = { openSetting(AccountRows.ServerManagement.id) { Route.ServerManagement(it) } },
                                )
                                SettingListItem(
                                    icon = rowIcon(AccountRows.UserManagement),
                                    title = rowTitle(AccountRows.UserManagement),
                                    subtitle = rowSubtitle(AccountRows.UserManagement),
                                    index = 1, count = accountCount,
                                    onClick = { openSetting(AccountRows.UserManagement.id) { Route.UserManagement(it) } },
                                )
                                SettingListItem(
                                    icon = rowIcon(AccountRows.Logout),
                                    title = rowTitle(AccountRows.Logout),
                                    subtitle = rowSubtitle(AccountRows.Logout),
                                    index = 2, count = accountCount,
                                    isDestructive = true,
                                    onClick = { onSignOut(false) },
                                )
                                SettingListItem(
                                    icon = rowIcon(AccountRows.SignOutFromServer),
                                    title = rowTitle(AccountRows.SignOutFromServer),
                                    subtitle = rowSubtitle(AccountRows.SignOutFromServer),
                                    index = 3, count = accountCount,
                                    isDestructive = true,
                                    onClick = { onSignOut(true) },
                                )
                            }
}

/** The `activity` section: the Activity & Insights SettingsGroup (records: ActivityInsightsRowRecords). */
@Composable
internal fun SettingsActivitySection(
    viewModel: SettingsViewModel,
    openSetting: (String, (String) -> Route) -> Unit
) {
                            val pendingCount = viewModel.pendingRequestCount.collectAsStateWithLifecycle().value
                            SettingsGroup(
                                icon = Tabler.Outline.Activity,
                                title = stringResource(Res.string.settings_activity_insights),
                                summary = { stringResource(Res.string.settings_activity_insights_subtitle) },
                                badge = if (pendingCount > 0) {
                                    {
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "$pendingCount pending",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        }
                                    }
                                } else null,
                                initiallyExpanded = false,
                            ) {
                                // Row count derived from the activity-insights
                                // group declaration's row gates.
                                val insightsCount = rowTotalFor(SettingsScreenGroups.activityInsights, RowAdmissionFlags())
                                SettingListItem(
                                    icon = rowIcon(ActivityInsightsRows.Favorites),
                                    title = rowTitle(ActivityInsightsRows.Favorites),
                                    subtitle = rowSubtitle(ActivityInsightsRows.Favorites),
                                    index = 0, count = insightsCount,
                                    onClick = { openSetting(ActivityInsightsRows.Favorites.id) { Route.Favorites } },
                                )
                                SettingListItem(
                                    icon = rowIcon(ActivityInsightsRows.WatchProgressHeatmap),
                                    title = rowTitle(ActivityInsightsRows.WatchProgressHeatmap),
                                    subtitle = rowSubtitle(ActivityInsightsRows.WatchProgressHeatmap),
                                    index = 1, count = insightsCount,
                                    onClick = { openSetting(ActivityInsightsRows.WatchProgressHeatmap.id) { Route.WatchProgressHeatmap } },
                                )
                                SettingListItem(
                                    icon = rowIcon(ActivityInsightsRows.ActivityQueue),
                                    title = rowTitle(ActivityInsightsRows.ActivityQueue),
                                    subtitle = rowSubtitle(ActivityInsightsRows.ActivityQueue),
                                    index = 2, count = insightsCount,
                                    onClick = { openSetting(ActivityInsightsRows.ActivityQueue.id) { Route.ArrQueue } },
                                )
                                SettingListItem(
                                    icon = rowIcon(ActivityInsightsRows.Upcoming),
                                    title = rowTitle(ActivityInsightsRows.Upcoming),
                                    subtitle = rowSubtitle(ActivityInsightsRows.Upcoming),
                                    index = 3, count = insightsCount,
                                    onClick = { openSetting(ActivityInsightsRows.Upcoming.id) { Route.UpcomingCalendar } },
                                )
                                SettingListItem(
                                    icon = rowIcon(ActivityInsightsRows.Requests),
                                    title = rowTitle(ActivityInsightsRows.Requests),
                                    subtitle = rowSubtitle(ActivityInsightsRows.Requests),
                                    index = 4, count = insightsCount,
                                    trailingText = pendingCount.takeIf { it > 0 }?.toString(),
                                    onClick = { openSetting(ActivityInsightsRows.Requests.id) { Route.Requests } },
                                )
                            }
}

/** The `system` section: the System SettingsGroup (records: SystemRowRecords). */
@Composable
internal fun SettingsSystemSection(
    viewModel: SettingsViewModel,
    openSetting: (String, (String) -> Route) -> Unit,
    onSetupWizardClick: () -> Unit
) {
                            val activeSessionCount = viewModel.activeSessions.size
                            SettingsGroup(
                                icon = Tabler.Outline.Adjustments,
                                title = stringResource(Res.string.settings_system),
                                summary = { stringResource(Res.string.settings_system_subtitle) },
                                badge = if (viewModel.currentUser?.isAdmin == true && activeSessionCount > 0) {
                                    {
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "$activeSessionCount active",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        }
                                    }
                                } else null,
                                initiallyExpanded = false,
                            ) {
                                // Row count derived from the system-core group
                                // declaration: the admin-dashboard row drops for
                                // non-admins, every other declared row renders.
                                val systemCount = SettingsScreenGroups.systemCore.items.count { item ->
                                    item.id != SystemRows.AdminDashboard.id || viewModel.currentUser?.isAdmin == true
                                }
                                var systemIndex = 0
                                if (viewModel.currentUser?.isAdmin == true) {
                                    SettingListItem(
                                        icon = rowIcon(SystemRows.AdminDashboard),
                                        title = rowTitle(SystemRows.AdminDashboard),
                                        subtitle = rowSubtitle(SystemRows.AdminDashboard),
                                        index = systemIndex++, count = systemCount,
                                        onClick = { openSetting(SystemRows.AdminDashboard.id) { Route.AdminDashboard } },
                                    )
                                }
                                SettingListItem(
                                    icon = rowIcon(SystemRows.SetupWizard),
                                    title = rowTitle(SystemRows.SetupWizard),
                                    subtitle = rowSubtitle(SystemRows.SetupWizard),
                                    index = systemIndex++, count = systemCount,
                                    onClick = { onSetupWizardClick() },
                                )
                            }
}

/** The `group_screensaver` section: the TV dream SettingsGroup (records: SystemRowRecords screensaver rows). */
@Composable
internal fun SettingsScreensaverSection(
    preferences: SettingsScreenPreferences,
    viewModel: SettingsViewModel,
    lastClickedSettingId: String?,
    activeDialog: MutableState<PickerState<*>?>
) {
                                SettingsGroup(
                                    icon = Tabler.Outline.Moon,
                                    title = stringResource(Res.string.settings_screensaver),
                                    summary = {
                                        val cats = preferences.dreamImageCategories
                                        remember(cats) {
                                            cats.joinToString(", ") { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
                                        }
                                    },
                                    initiallyExpanded = lastClickedSettingId in SettingsScreenGroups.systemScreensaver.itemIdSet,
                                ) {
                                    // Row count derived from the screensaver group
                                    // declaration's row gates — the eight declared
                                    // dream rows are exactly the rows rendered here.
                                    val dreamTotal = rowTotalFor(SettingsScreenGroups.systemScreensaver, RowAdmissionFlags())
                                    val slideshowIntervalTitle = rowTitle(SystemRows.ScreensaverSlideshowInterval)
                                    val transitionStyleTitle = rowTitle(SystemRows.ScreensaverTransitionStyle)
                                    val transitionCrossfadeLabel = stringResource(Res.string.settings_transition_crossfade)
                                    val transitionSlideLabel = stringResource(Res.string.settings_transition_slide)
                                    val transitionNoneLabel = stringResource(Res.string.settings_transition_none)
                                    SettingToggleItem(
                                        icon = rowIcon(SystemRows.ScreensaverShowTitle),
                                        title = rowTitle(SystemRows.ScreensaverShowTitle),
                                        subtitle = if (preferences.dreamShowTitle) stringResource(Res.string.settings_display_media_title) else stringResource(Res.string.settings_hide_media_title),
                                        checked = preferences.dreamShowTitle,
                                        index = 0, count = dreamTotal,
                                        highlighted = lastClickedSettingId == SystemRows.ScreensaverShowTitle.id,
                                        onCheckedChange = { viewModel.edit { scope -> scope.screensaver.setDreamShowTitle(it) } },
                                    )
                                    val categoryMovies = stringResource(Res.string.settings_category_movies)
                                    val categoryTv = stringResource(Res.string.settings_category_tv)
                                    val categoryMusic = stringResource(Res.string.settings_category_music)
                                    val categoryPhotos = stringResource(Res.string.settings_category_photos)
                                    SettingListItem(
                                        icon = rowIcon(SystemRows.ScreensaverCategories),
                                        title = rowTitle(SystemRows.ScreensaverCategories),
                                        subtitle = rowSubtitle(SystemRows.ScreensaverCategories),
                                        trailingText = remember(preferences.dreamImageCategories, categoryMovies, categoryTv, categoryMusic, categoryPhotos) {
                                            preferences.dreamImageCategories.joinToString(", ") {
                                                when (it) {
                                                    DreamImageCategory.MOVIES -> categoryMovies
                                                    DreamImageCategory.SERIES -> categoryTv
                                                    DreamImageCategory.MUSIC -> categoryMusic
                                                    DreamImageCategory.PHOTOS -> categoryPhotos
                                                }
                                            }
                                        },
                                        index = 1, count = dreamTotal,
                                        highlighted = lastClickedSettingId == SystemRows.ScreensaverCategories.id,
                                        onClick = {
                                            val allCats = DreamImageCategory.entries.toSet()
                                            val current = preferences.dreamImageCategories
                                            val next = if (current.size == allCats.size) {
                                                setOf(DreamImageCategory.MOVIES)
                                            } else {
                                                val cycle = allCats.toList()
                                                val nextIndex = current.size
                                                cycle.take(nextIndex + 1).toSet()
                                            }
                                            viewModel.edit { scope -> scope.screensaver.setDreamImageCategories(next) }
                                        },
                                    )
                                    SettingListItem(
                                        icon = rowIcon(SystemRows.ScreensaverSlideshowInterval),
                                        title = rowTitle(SystemRows.ScreensaverSlideshowInterval),
                                        subtitle = rowSubtitle(SystemRows.ScreensaverSlideshowInterval),
                                        trailingText = "${preferences.dreamSlideshowIntervalMs / 1000}s",
                                        index = 2, count = dreamTotal,
                                        highlighted = lastClickedSettingId == SystemRows.ScreensaverSlideshowInterval.id,
                                        onClick = {
                                            activeDialog.value = PickerState.List(
                                                title = slideshowIntervalTitle,
                                                items = listOf(5_000L, 10_000L, 15_000L, 30_000L, 60_000L),
                                                label = { "${it / 1000}s" },
                                                isSelected = { it == preferences.dreamSlideshowIntervalMs },
                                                onSelect = { viewModel.edit { scope -> scope.screensaver.setDreamSlideshowIntervalMs(it) } },
                                            )
                                        },
                                    )
                                    SettingToggleItem(
                                        icon = rowIcon(SystemRows.ScreensaverKenBurns),
                                        title = rowTitle(SystemRows.ScreensaverKenBurns),
                                        subtitle = if (preferences.dreamKenBurnsEnabled) stringResource(Res.string.settings_ken_burns_on) else stringResource(Res.string.settings_ken_burns_off),
                                        checked = preferences.dreamKenBurnsEnabled,
                                        index = 3, count = dreamTotal,
                                        highlighted = lastClickedSettingId == SystemRows.ScreensaverKenBurns.id,
                                        onCheckedChange = { viewModel.edit { scope -> scope.screensaver.setDreamKenBurnsEnabled(it) } },
                                    )
                                    SettingListItem(
                                        icon = rowIcon(SystemRows.ScreensaverTransitionStyle),
                                        title = rowTitle(SystemRows.ScreensaverTransitionStyle),
                                        subtitle = preferences.dreamTransitionStyle.name,
                                        trailingText = preferences.dreamTransitionStyle.name,
                                        index = 4, count = dreamTotal,
                                        highlighted = lastClickedSettingId == SystemRows.ScreensaverTransitionStyle.id,
                                        onClick = {
                                            val labels = mapOf(
                                                DreamTransitionStyle.CROSSFADE to transitionCrossfadeLabel,
                                                DreamTransitionStyle.SLIDE to transitionSlideLabel,
                                                DreamTransitionStyle.NONE to transitionNoneLabel,
                                            )
                                            activeDialog.value = PickerState.List(
                                                title = transitionStyleTitle,
                                                items = DreamTransitionStyle.entries,
                                                label = { labels[it] ?: it.name },
                                                isSelected = { it == preferences.dreamTransitionStyle },
                                                onSelect = { viewModel.edit { scope -> scope.screensaver.setDreamTransitionStyle(it) } },
                                            )
                                        },
                                    )
                                    // The (label, canonical age) picker rows; null age =
                                    // no local cap ("None"). Both the row vocabulary and
                                    // the age resolution come from the canonical rating
                                    // table (core:network's LibraryWirePolicy).
                                    val maxRatingTitle = rowTitle(SystemRows.ScreensaverMaxParentalRating)
                                    val maxRatingNoneLabel = stringResource(Res.string.settings_dream_rating_none)
                                    val maxRatingItems = remember(maxRatingNoneLabel) {
                                        buildList {
                                            add(null to maxRatingNoneLabel)
                                            for (rating in PARENTAL_RATING_PICKER_LADDER) {
                                                parentalRatingAge(rating)?.let { add(it to rating) }
                                            }
                                        }
                                    }
                                    SettingListItem(
                                        icon = rowIcon(SystemRows.ScreensaverMaxParentalRating),
                                        title = rowTitle(SystemRows.ScreensaverMaxParentalRating),
                                        subtitle = rowSubtitle(SystemRows.ScreensaverMaxParentalRating),
                                        trailingText = maxRatingItems
                                            .firstOrNull { it.first == preferences.dreamMaxParentalRating }
                                            ?.second ?: maxRatingNoneLabel,
                                        index = 5, count = dreamTotal,
                                        highlighted = lastClickedSettingId == SystemRows.ScreensaverMaxParentalRating.id,
                                        onClick = {
                                            activeDialog.value = PickerState.List(
                                                title = maxRatingTitle,
                                                items = maxRatingItems,
                                                label = { it.second },
                                                isSelected = { it.first == preferences.dreamMaxParentalRating },
                                                onSelect = { viewModel.edit { scope -> scope.screensaver.setDreamMaxParentalRating(it.first) } },
                                            )
                                        },
                                    )
                                    val dimAfterTitle = rowTitle(SystemRows.ScreensaverDimAfter)
                                    val dimAfterOffLabel = stringResource(Res.string.settings_dream_dim_after_off)
                                    // The auto-lock ladder minus the 10-minute rung
                                    // (the shared SETTINGS_TIMER_LADDER_MS —
                                    // SecuritySettingsScreen takes the whole ladder),
                                    // each rung zipped to its label so every lookup
                                    // keys on the ms VALUE, never a hand-built
                                    // index pairing. An off-ladder stored value
                                    // falls back to the Off label.
                                    val dimAfterChoices = SETTINGS_TIMER_LADDER_MS.dropLast(1).zip(
                                        listOf(
                                            dimAfterOffLabel,
                                            stringResource(Res.string.settings_dream_dim_after_30_seconds),
                                            stringResource(Res.string.settings_dream_dim_after_1_minute),
                                            stringResource(Res.string.settings_dream_dim_after_5_minutes),
                                        ),
                                    )
                                    fun dimAfterLabel(ms: Long): String =
                                        dimAfterChoices.firstOrNull { it.first == ms }?.second ?: dimAfterOffLabel
                                    SettingListItem(
                                        icon = rowIcon(SystemRows.ScreensaverDimAfter),
                                        title = rowTitle(SystemRows.ScreensaverDimAfter),
                                        subtitle = rowSubtitle(SystemRows.ScreensaverDimAfter),
                                        trailingText = dimAfterLabel(preferences.dreamDimAfterMs),
                                        index = 6, count = dreamTotal,
                                        highlighted = lastClickedSettingId == SystemRows.ScreensaverDimAfter.id,
                                        onClick = {
                                            activeDialog.value = PickerState.List(
                                                title = dimAfterTitle,
                                                items = dimAfterChoices.map { it.first },
                                                label = { dimAfterLabel(it) },
                                                isSelected = { it == preferences.dreamDimAfterMs },
                                                onSelect = { viewModel.edit { scope -> scope.screensaver.setDreamDimAfterMs(it) } },
                                            )
                                        },
                                    )
                                    val dimPercentTitle = rowTitle(SystemRows.ScreensaverDimPercent)
                                    SettingListItem(
                                        icon = rowIcon(SystemRows.ScreensaverDimPercent),
                                        title = rowTitle(SystemRows.ScreensaverDimPercent),
                                        subtitle = rowSubtitle(SystemRows.ScreensaverDimPercent),
                                        trailingText = "${preferences.dreamDimPercent}%",
                                        index = 7, count = dreamTotal,
                                        highlighted = lastClickedSettingId == SystemRows.ScreensaverDimPercent.id,
                                        onClick = {
                                            activeDialog.value = PickerState.Slider(
                                                title = dimPercentTitle,
                                                value = preferences.dreamDimPercent.toFloat(),
                                                valueRange = 0f..95f,
                                                steps = 18,
                                                valueLabel = { "${it.toInt()}%" },
                                                rangeStartLabel = "0%",
                                                rangeEndLabel = "95%",
                                                onConfirm = { viewModel.edit { scope -> scope.screensaver.setDreamDimPercent(it.toInt()) } },
                                            )
                                        },
                                    )
                                }
}

/** The `group_idle_ambient` section: the desktop idle-ambient SettingsGroup (records: SystemRowRecords idle rows). */
@Composable
internal fun SettingsIdleAmbientSection(
    preferences: SettingsScreenPreferences,
    viewModel: SettingsViewModel,
    lastClickedSettingId: String?,
    activeDialog: MutableState<PickerState<*>?>
) {
                                SettingsGroup(
                                    icon = Tabler.Outline.Moon,
                                    title = stringResource(Res.string.settings_idle_ambient),
                                    summary = {
                                        if (preferences.idleAmbientEnabled) stringResource(Res.string.settings_idle_ambient_on)
                                        else stringResource(Res.string.settings_idle_ambient_off)
                                    },
                                    initiallyExpanded = lastClickedSettingId in SettingsScreenGroups.systemIdleAmbient.itemIdSet,
                                ) {
                                    val idleTotal = rowTotalFor(SettingsScreenGroups.systemIdleAmbient, RowAdmissionFlags())
                                    val idleTimeoutTitle = rowTitle(SystemRows.IdleAmbientTimeout)
                                    val idleTimeoutOffLabel = stringResource(Res.string.settings_idle_ambient_timeout_off)
                                    val idleTimeoutOptions = listOf(0L, 1L, 5L, 10L, 15L, 30L)
                                    // stringResource resolves in composition — pre-build the
                                    // whole label column so the picker's plain label lambda only
                                    // indexes (the auto-lock timer row's pattern).
                                    val idleTimeoutLabels = idleTimeoutOptions.map { minutes ->
                                        if (minutes == 0L) idleTimeoutOffLabel
                                        else stringResource(Res.string.settings_idle_ambient_timeout_minutes, minutes)
                                    }
                                    SettingToggleItem(
                                        icon = rowIcon(SystemRows.IdleAmbientEnabled),
                                        title = rowTitle(SystemRows.IdleAmbientEnabled),
                                        subtitle = rowSubtitle(SystemRows.IdleAmbientEnabled),
                                        checked = preferences.idleAmbientEnabled,
                                        index = 0, count = idleTotal,
                                        highlighted = lastClickedSettingId == SystemRows.IdleAmbientEnabled.id,
                                        onCheckedChange = { enabled ->
                                            viewModel.edit { scope -> scope.screensaver.setIdleAmbientEnabled(enabled) }
                                        },
                                    )
                                    SettingListItem(
                                        icon = rowIcon(SystemRows.IdleAmbientTimeout),
                                        title = rowTitle(SystemRows.IdleAmbientTimeout),
                                        subtitle = rowSubtitle(SystemRows.IdleAmbientTimeout),
                                        trailingText = idleTimeoutLabels[
                                            idleTimeoutOptions.indexOf(preferences.idleAmbientTimeoutMin)
                                                .coerceAtMost(idleTimeoutLabels.lastIndex),
                                        ],
                                        index = 1, count = idleTotal,
                                        highlighted = lastClickedSettingId == SystemRows.IdleAmbientTimeout.id,
                                        onClick = {
                                            activeDialog.value = PickerState.List(
                                                title = idleTimeoutTitle,
                                                items = idleTimeoutOptions,
                                                label = { idleTimeoutLabels[idleTimeoutOptions.indexOf(it).coerceAtMost(idleTimeoutLabels.lastIndex)] },
                                                isSelected = { it == preferences.idleAmbientTimeoutMin },
                                                onSelect = { minutes ->
                                                    viewModel.edit { scope -> scope.screensaver.setIdleAmbientTimeoutMin(minutes) }
                                                },
                                            )
                                        },
                                    )
                                }
}

/** The `group_discord_presence` section: the Discord Rich Presence toggle (SystemRowRecords discord_ rows). */
@Composable
internal fun SettingsDiscordPresenceSection(
    preferences: SettingsScreenPreferences,
    viewModel: SettingsViewModel,
    lastClickedSettingId: String?,
) {
                                SettingsGroup(
                                    icon = Tabler.Outline.BrandDiscord,
                                    title = stringResource(Res.string.settings_discord_presence),
                                    summary = {
                                        if (preferences.discordPresenceEnabled) {
                                            stringResource(Res.string.settings_discord_presence_on)
                                        } else {
                                            stringResource(Res.string.settings_discord_presence_off)
                                        }
                                    },
                                    initiallyExpanded = lastClickedSettingId in SettingsScreenGroups.systemDiscordPresence.itemIdSet,
                                ) {
                                    val discordTotal = rowTotalFor(SettingsScreenGroups.systemDiscordPresence, RowAdmissionFlags())
                                    SettingToggleItem(
                                        icon = rowIcon(SystemRows.DiscordPresenceEnabled),
                                        title = rowTitle(SystemRows.DiscordPresenceEnabled),
                                        subtitle = rowSubtitle(SystemRows.DiscordPresenceEnabled),
                                        checked = preferences.discordPresenceEnabled,
                                        index = 0, count = discordTotal,
                                        highlighted = lastClickedSettingId == SystemRows.DiscordPresenceEnabled.id,
                                        onCheckedChange = { enabled ->
                                            viewModel.edit { scope -> scope.screensaver.setDiscordPresenceEnabled(enabled) }
                                        },
                                    )
                                }
}

/**
 * The `group_shell_hooks` section: the playback-event shell hooks (feature
 * 4.3) — the master toggle (whose subtitle carries the safety copy) plus the
 * five mpv-shim-named command rows, each opened in the shared free-form text
 * editor with the placeholder hint as its helper text.
 */
@Composable
internal fun SettingsShellHooksSection(
    preferences: SettingsScreenPreferences,
    viewModel: SettingsViewModel,
    lastClickedSettingId: String?,
    activeDialog: MutableState<PickerState<*>?>,
) {
                                SettingsGroup(
                                    icon = Tabler.Outline.Terminal2,
                                    title = stringResource(Res.string.settings_hooks),
                                    summary = {
                                        if (preferences.hooksEnabled) {
                                            stringResource(Res.string.settings_hooks_on)
                                        } else {
                                            stringResource(Res.string.settings_hooks_off)
                                        }
                                    },
                                    initiallyExpanded = lastClickedSettingId in SettingsScreenGroups.systemHooks.itemIdSet,
                                ) {
                                    val hooksTotal = rowTotalFor(SettingsScreenGroups.systemHooks, RowAdmissionFlags())
                                    val placeholderHint = stringResource(Res.string.settings_hooks_placeholder_hint)
                                    SettingToggleItem(
                                        icon = rowIcon(SystemRows.HooksEnabled),
                                        title = rowTitle(SystemRows.HooksEnabled),
                                        subtitle = rowSubtitle(SystemRows.HooksEnabled),
                                        checked = preferences.hooksEnabled,
                                        index = 0, count = hooksTotal,
                                        highlighted = lastClickedSettingId == SystemRows.HooksEnabled.id,
                                        onCheckedChange = { enabled ->
                                            viewModel.edit { scope -> scope.screensaver.setHooksEnabled(enabled) }
                                        },
                                    )
                                    hooksCommandRow(
                                        row = SystemRows.HooksPlayCmd,
                                        command = preferences.hooksPlayCmd,
                                        index = 1, count = hooksTotal,
                                        lastClickedSettingId = lastClickedSettingId,
                                        placeholderHint = placeholderHint,
                                        activeDialog = activeDialog,
                                        onSave = { viewModel.edit { scope -> scope.screensaver.setHooksPlayCmd(it) } },
                                    )
                                    hooksCommandRow(
                                        row = SystemRows.HooksStopCmd,
                                        command = preferences.hooksStopCmd,
                                        index = 2, count = hooksTotal,
                                        lastClickedSettingId = lastClickedSettingId,
                                        placeholderHint = placeholderHint,
                                        activeDialog = activeDialog,
                                        onSave = { viewModel.edit { scope -> scope.screensaver.setHooksStopCmd(it) } },
                                    )
                                    hooksCommandRow(
                                        row = SystemRows.HooksEndedCmd,
                                        command = preferences.hooksEndedCmd,
                                        index = 3, count = hooksTotal,
                                        lastClickedSettingId = lastClickedSettingId,
                                        placeholderHint = placeholderHint,
                                        activeDialog = activeDialog,
                                        onSave = { viewModel.edit { scope -> scope.screensaver.setHooksEndedCmd(it) } },
                                    )
                                    hooksCommandRow(
                                        row = SystemRows.HooksIdleCmd,
                                        command = preferences.hooksIdleCmd,
                                        index = 4, count = hooksTotal,
                                        lastClickedSettingId = lastClickedSettingId,
                                        placeholderHint = placeholderHint,
                                        activeDialog = activeDialog,
                                        onSave = { viewModel.edit { scope -> scope.screensaver.setHooksIdleCmd(it) } },
                                    )
                                    hooksCommandRow(
                                        row = SystemRows.HooksIdleEndedCmd,
                                        command = preferences.hooksIdleEndedCmd,
                                        index = 5, count = hooksTotal,
                                        lastClickedSettingId = lastClickedSettingId,
                                        placeholderHint = placeholderHint,
                                        activeDialog = activeDialog,
                                        onSave = { viewModel.edit { scope -> scope.screensaver.setHooksIdleEndedCmd(it) } },
                                    )
                                }
}

/** One shell-hook command row: shows the configured command, opens the text editor. */
@Composable
private fun hooksCommandRow(
    row: SettingsRow,
    command: String,
    index: Int,
    count: Int,
    lastClickedSettingId: String?,
    placeholderHint: String,
    activeDialog: MutableState<PickerState<*>?>,
    onSave: (String) -> Unit,
) {
    val title = rowTitle(row)
    val notSet = stringResource(Res.string.settings_hooks_cmd_not_set)
    SettingListItem(
        icon = rowIcon(row),
        title = title,
        subtitle = command.ifBlank { notSet },
        index = index, count = count,
        highlighted = lastClickedSettingId == row.id,
        onClick = {
            activeDialog.value = PickerState.Text(
                title = title,
                initialText = command,
                helperText = placeholderHint,
                onSave = onSave,
            )
        },
    )
}
