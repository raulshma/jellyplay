package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Ban
import com.composables.icons.tabler.outline.Bookmarks
import com.composables.icons.tabler.outline.CalendarTime
import com.composables.icons.tabler.outline.CloudDownload
import com.composables.icons.tabler.outline.CloudUpload
import com.composables.icons.tabler.outline.Compass
import com.composables.icons.tabler.outline.EyeOff
import com.composables.icons.tabler.outline.Folders
import com.composables.icons.tabler.outline.History
import com.composables.icons.tabler.outline.LayersLinked
import com.composables.icons.tabler.outline.LayoutGrid
import com.composables.icons.tabler.outline.Pin
import com.composables.icons.tabler.outline.Pinned
import com.composables.icons.tabler.outline.Upload
import com.raulshma.jellyplay.core.data.repository.HomeLayoutRestoreFetch
import com.raulshma.jellyplay.core.model.DateFormatPreference
import com.raulshma.jellyplay.core.model.HomeCwProgressStyle
import com.raulshma.jellyplay.core.model.HomeScreenPreferences
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.ShortcutSurface
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.raulshma.jellyplay.core.ui.components.formatDate
import com.raulshma.jellyplay.core.ui.components.formatIntPattern
import com.raulshma.jellyplay.core.ui.components.homeSectionIcon
import com.raulshma.jellyplay.core.ui.message.LocalUserMessageBus
import com.raulshma.jellyplay.core.ui.reorder.rememberReorderableOrderedList
import com.raulshma.jellyplay.core.ui.navigation.Route
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.core_cancel
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_continue_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_configure_libraries
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_rows
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_exclude_active_sessions_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_exclude_active_sessions_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hidden_libraries
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_entry_rows
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_header_shortcuts
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_header_shortcuts_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_autopush_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_autopush_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_presets
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_restore
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_restore_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_restore_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_restore_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_restored
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_server_empty
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_server_undecodable
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_sync_failed
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_upload
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_upload_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_uploaded
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_screen_layout
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_sections_visible
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_merge_continue_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_merge_continue_next_up_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_merge_continue_next_up_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_next_up_hidden
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_next_up_time_window
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pinned_home_sections
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_progress_style_default
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_progress_style_percent
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_progress_style_thick
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_rewatching_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_rewatching_next_up_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_rewatching_next_up_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_classic_rows
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_classic_rows_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_classic_rows_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_curation
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_unhide_continue_watching
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_unhide_continue_watching_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_unlimited
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_x_days

/**
 * The restore confirmation dialog's content, pure for tests: the
 * timestamp-diff signal (the server write stamp) plus BOTH layouts'
 * enabled-section counts, so the dialog can show "server copy saved <date> —
 * N sections" against "this device — M sections" without any formatting
 * logic of its own.
 */
internal data class HomeLayoutRestorePreview(
    val serverSavedAtEpochMillis: Long?,
    val serverSectionCount: Int,
    val localSectionCount: Int,
)

internal fun homeLayoutRestorePreview(
    offer: HomeLayoutRestoreFetch.Offer,
    localEnabledSectionCount: Int,
): HomeLayoutRestorePreview = HomeLayoutRestorePreview(
    serverSavedAtEpochMillis = offer.savedAtEpochMillis,
    serverSectionCount = offer.config.enabledHomeSectionTypes.size,
    localSectionCount = localEnabledSectionCount,
)

/**
 * The Rows & layout leaf's ONE ordered slot declaration (ADR-0009 amendment;
 * ADR-0017's home split): the Next Up behavior group and the Home Screen
 * Layout group (moved whole off the former hub screen), then the curation
 * quartet (the former display group's hidden/restore rows). No advanced tier
 * — every row renders. The contract test pins the slot/group parity against
 * this same declaration.
 */
internal val HomeRowsSettingsScreenSlots: List<PreferenceScreenGroup> = listOf(
    SettingsScreenGroups.homeNextUp.asScreenGroup(),
    SettingsScreenGroups.homeLayout.asScreenGroup(),
    SettingsScreenGroups.homeCuration.asScreenGroup(),
)

/**
 * The Rows & layout LEAF (ADR-0017's home split): the home-rows curation
 * surface behind the hub's Rows & layout entrance — the Continue Watching &
 * Next Up behavior group, the Home Screen Layout group (the four drill-ins,
 * the header-shortcuts editor, the layout sync/upload/restore actions and the
 * reorderable section toggles) and the hidden/exclusion restore quartet. No
 * advanced tier; the drill-ins navigate through the shared nav facade.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeRowsSettingsScreen(
    onBack: () -> Unit,
    navActions: SettingsNavActions,
    highlightSettingId: String? = null,
    viewModel: HomeSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    // Manual sync actions: one-shot toast texts pre-resolved at composable
    // scope (the message bus takes plain Strings — the presets screen's
    // convention) plus the restore-confirmation dialog flag.
    val bus = LocalUserMessageBus.current
    val uploadedText = stringResource(Res.string.settings_home_layout_uploaded)
    val restoredText = stringResource(Res.string.settings_home_layout_restored)
    val syncFailedText = stringResource(Res.string.settings_home_layout_sync_failed)
    val serverEmptyText = stringResource(Res.string.settings_home_layout_server_empty)
    val serverUndecodableText = stringResource(Res.string.settings_home_layout_server_undecodable)
    var showRestoreConfirm by remember { mutableStateOf(false) }

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_home_entry_rows),
        onBack = onBack,
        focusTag = "home_rows_init",
        highlightSettingId = highlightSettingId,
        groups = HomeRowsSettingsScreenSlots.withContent(
            bodies = listOf(
                { activePicker ->
                    HomeNextUpGroupBody(
                        preferences = preferences,
                        highlightSettingId = highlightSettingId,
                        viewModel = viewModel,
                        activePicker = activePicker,
                    )
                },
                { activePicker ->
                    HomeLayoutGroupBody(
                        preferences = preferences,
                        highlightSettingId = highlightSettingId,
                        navActions = navActions,
                        viewModel = viewModel,
                        activePicker = activePicker,
                        onUpload = { result ->
                            if (result.isSuccess) bus.info(uploadedText) else bus.info(syncFailedText)
                        },
                        onRestoreFetch = { result ->
                            when (result.getOrNull()) {
                                is HomeLayoutRestoreFetch.Offer -> showRestoreConfirm = true
                                HomeLayoutRestoreFetch.Empty -> bus.info(serverEmptyText)
                                HomeLayoutRestoreFetch.Undecodable -> bus.info(serverUndecodableText)
                                null -> bus.info(syncFailedText)
                            }
                        },
                    )
                },
                { _ ->
                    HomeCurationGroupBody(
                        preferences = preferences,
                        highlightSettingId = highlightSettingId,
                        navActions = navActions,
                        viewModel = viewModel,
                    )
                },
            ),
        ),
        pickerHost = true,
    )

    // Restore confirmation: the timestamp-diff dialog (server write stamp
    // vs both layouts' enabled-section counts). Shown only while a decoded
    // offer is pending — fetchServerLayout retained it on the VM. The apply
    // routes through the store's applyLayout — the SAME command the local
    // preset-restore path uses (no second write path).
    val pendingOffer = viewModel.pendingRestoreOffer
    if (showRestoreConfirm && pendingOffer != null) {
        val restoreTitle = stringResource(Res.string.settings_home_layout_restore_title)
        val restoreMessageTemplate = stringResource(Res.string.settings_home_layout_restore_message)
        val cancelText = stringResource(CoreUiRes.string.core_cancel)
        // Client-clock sourced stamp (the transport carries no server
        // timestamp) — a "how old is the server copy" signal, not a guarantee.
        val savedAtLabel = remember(pendingOffer.savedAtEpochMillis) {
            pendingOffer.savedAtEpochMillis?.let { stamp ->
                runCatching { formatDate(stamp, DateFormatPreference.LONG) }.getOrDefault("")
            } ?: ""
        }
        val preview = homeLayoutRestorePreview(pendingOffer, preferences.enabledHomeSectionTypes.size)
        val message = restoreMessageTemplate
            .replace("%1\$s", savedAtLabel)
            .replace("%2\$s", preview.serverSectionCount.toString())
            .replace("%3\$s", preview.localSectionCount.toString())
        ConfirmDialog(
            title = restoreTitle,
            message = message,
            confirmText = stringResource(Res.string.settings_home_layout_restore),
            dismissText = cancelText,
            onConfirm = {
                viewModel.edit { it.homeDiscovery.applyLayout(pendingOffer.config) }
                viewModel.clearPendingRestoreOffer()
                showRestoreConfirm = false
                bus.info(restoredText)
            },
            onDismiss = {
                viewModel.clearPendingRestoreOffer()
                showRestoreConfirm = false
            },
            tone = ConfirmTone.NEUTRAL,
        )
    }
}

/** The `home.nextUp` group body — moved verbatim off the former hub screen. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeNextUpGroupBody(
    preferences: HomeScreenPreferences,
    highlightSettingId: String?,
    viewModel: HomeSettingsViewModel,
    activePicker: MutableState<PickerState<*>?>,
) {
    SettingsGroup(
        icon = Tabler.Outline.CalendarTime,
        title = stringResource(Res.string.settings_continue_next_up),
        summary = {
            if (preferences.mergeContinueWatchingAndNextUp) {
                stringResource(Res.string.settings_merge_continue_next_up_on)
            } else {
                stringResource(Res.string.settings_merge_continue_next_up_off)
            }
        },
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = highlightSettingId in SettingsScreenGroups.homeNextUp.itemIdSet,
    ) {
        val nextUpTotal = rowTotalFor(SettingsScreenGroups.homeNextUp, RowAdmissionFlags())
        SettingToggleItem(
            icon = Tabler.Outline.LayersLinked,
            title = rowTitle(HomeRows.MergeContinueNextUp),
            subtitle = if (preferences.mergeContinueWatchingAndNextUp) stringResource(Res.string.settings_merge_continue_next_up_on) else stringResource(Res.string.settings_merge_continue_next_up_off),
            checked = preferences.mergeContinueWatchingAndNextUp,
            highlighted = highlightSettingId == HomeRows.MergeContinueNextUp.id,
            index = 0, count = nextUpTotal,
            onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setMergeContinueWatchingAndNextUp(it) } },
        )

        val nextUpTitle = rowTitle(HomeRows.NextUpMaxDays)
        val unlimitedLabel = stringResource(Res.string.settings_unlimited)
        val xDaysFormat = stringResource(Res.string.settings_x_days)
        val dayLabels = mapOf(
            0 to unlimitedLabel,
            7 to formatIntPattern(xDaysFormat, 7),
            14 to formatIntPattern(xDaysFormat, 14),
            30 to formatIntPattern(xDaysFormat, 30),
            60 to formatIntPattern(xDaysFormat, 60),
            90 to formatIntPattern(xDaysFormat, 90),
        )
        SettingListItem(
            icon = Tabler.Outline.CalendarTime,
            title = nextUpTitle,
            subtitle = rowSubtitle(HomeRows.NextUpMaxDays),
            trailingText = dayLabels[preferences.nextUpMaxDays] ?: formatIntPattern(xDaysFormat, preferences.nextUpMaxDays),
            highlighted = highlightSettingId == HomeRows.NextUpMaxDays.id,
            index = 1, count = nextUpTotal,
            onClick = {
                activePicker.value = PickerState.List(
                    title = nextUpTitle,
                    items = listOf(0, 7, 14, 30, 60, 90),
                    label = { dayLabels[it] ?: formatIntPattern(xDaysFormat, it) },
                    isSelected = { it == preferences.nextUpMaxDays },
                    onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setNextUpMaxDays(it) } },
                )
            },
        )

        SettingToggleItem(
            icon = Tabler.Outline.History,
            title = rowTitle(HomeRows.NextUpRewatching),
            subtitle = if (preferences.nextUpRewatching) stringResource(Res.string.settings_rewatching_next_up_on) else stringResource(Res.string.settings_rewatching_next_up_off),
            checked = preferences.nextUpRewatching,
            highlighted = highlightSettingId == HomeRows.NextUpRewatching.id,
            index = 2, count = nextUpTotal,
            onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setNextUpRewatching(it) } },
        )

        val cwProgressStyleTitle = rowTitle(HomeRows.HomeCwProgressStyle)
        val progressStyleLabels = mapOf(
            HomeCwProgressStyle.DEFAULT to stringResource(Res.string.settings_progress_style_default),
            HomeCwProgressStyle.THICK to stringResource(Res.string.settings_progress_style_thick),
            HomeCwProgressStyle.PERCENT to stringResource(Res.string.settings_progress_style_percent),
        )
        SettingListItem(
            icon = rowIcon(HomeRows.HomeCwProgressStyle),
            title = cwProgressStyleTitle,
            subtitle = rowSubtitle(HomeRows.HomeCwProgressStyle),
            trailingText = progressStyleLabels[preferences.homeCwProgressStyle]
                ?: preferences.homeCwProgressStyle.name,
            highlighted = highlightSettingId == HomeRows.HomeCwProgressStyle.id,
            index = 3, count = nextUpTotal,
            onClick = {
                activePicker.value = PickerState.List(
                    title = cwProgressStyleTitle,
                    items = HomeCwProgressStyle.entries,
                    label = { progressStyleLabels.getValue(it) },
                    isSelected = { it == preferences.homeCwProgressStyle },
                    onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setHomeCwProgressStyle(it) } },
                )
            },
        )

        SettingToggleItem(
            icon = rowIcon(HomeRows.HomeAskContinueRemember),
            title = rowTitle(HomeRows.HomeAskContinueRemember),
            subtitle = rowSubtitle(HomeRows.HomeAskContinueRemember),
            checked = preferences.homeAskContinueRemember,
            highlighted = highlightSettingId == HomeRows.HomeAskContinueRemember.id,
            index = 4, count = nextUpTotal,
            onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHomeAskContinueRemember(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.Ban,
            title = rowTitle(HomeRows.ExcludeActiveSessions),
            subtitle = if (preferences.excludeActiveSessions) stringResource(Res.string.settings_exclude_active_sessions_on) else stringResource(Res.string.settings_exclude_active_sessions_off),
            checked = preferences.excludeActiveSessions,
            highlighted = highlightSettingId == HomeRows.ExcludeActiveSessions.id,
            index = 5, count = nextUpTotal,
            onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setExcludeActiveSessions(it) } },
        )
    }
}

/**
 * The `home.layout` group body — moved verbatim off the former hub screen:
 * the four drill-in rows, the header-shortcuts editor row, the auto-push
 * toggle, the manual upload/restore actions and the reorderable section
 * toggles (the layout declaration's five declared rows ride the drill-ins +
 * auto-push; the editor/upload/restore rows are screen-local action rows —
 * no search entry).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HomeLayoutGroupBody(
    preferences: HomeScreenPreferences,
    highlightSettingId: String?,
    navActions: SettingsNavActions,
    viewModel: HomeSettingsViewModel,
    activePicker: MutableState<PickerState<*>?>,
    onUpload: (Result<Unit>) -> Unit,
    onRestoreFetch: (Result<HomeLayoutRestoreFetch>) -> Unit,
) {
    val bus = LocalUserMessageBus.current
    SettingsGroup(
        icon = Tabler.Outline.LayoutGrid,
        title = stringResource(Res.string.settings_home_screen_layout),
        summary = {
            val enabled = preferences.enabledHomeSectionTypes
            stringResource(Res.string.settings_home_sections_visible, enabled.size, HomeSectionType.CONFIGURABLE.size)
        },
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = highlightSettingId in SettingsScreenGroups.homeLayout.itemIdSet,
    ) {
        SettingListItem(
            icon = Tabler.Outline.Pinned,
            title = rowTitle(HomeRows.PinnedHomeSections),
            subtitle = rowSubtitle(HomeRows.PinnedHomeSections),
            trailingText = if (preferences.pinnedHomeSections.isEmpty()) "" else "${preferences.pinnedHomeSections.size}",
            highlighted = highlightSettingId == HomeRows.PinnedHomeSections.id,
            index = 0, count = 1,
            onClick = { navActions.onNavigate(Route.PinnedHomeSections(if (highlightSettingId == HomeRows.PinnedHomeSections.id) PINNED_ADD_HIGHLIGHT_ID else null)) },
        )

        SettingListItem(
            icon = Tabler.Outline.Bookmarks,
            title = rowTitle(HomeRows.HomeLayoutPresets),
            subtitle = rowSubtitle(HomeRows.HomeLayoutPresets),
            trailingText = if (preferences.homeLayoutPresets.isEmpty()) "" else "${preferences.homeLayoutPresets.size}",
            highlighted = highlightSettingId == HomeRows.HomeLayoutPresets.id,
            index = 0, count = 1,
            onClick = { navActions.onNavigate(Route.HomeLayoutPresets(if (highlightSettingId == HomeRows.HomeLayoutPresets.id) PRESET_LIST_HIGHLIGHT_ID else null)) },
        )

        SettingListItem(
            icon = Tabler.Outline.Folders,
            title = rowTitle(HomeRows.ConfigureLibraries),
            subtitle = rowSubtitle(HomeRows.ConfigureLibraries),
            trailingText = "",
            highlighted = highlightSettingId == HomeRows.ConfigureLibraries.id,
            index = 0, count = 1,
            onClick = { navActions.onNavigate(Route.LibraryHomeSections(if (highlightSettingId == HomeRows.ConfigureLibraries.id) "configure_libraries" else null)) },
        )

        SettingListItem(
            icon = Tabler.Outline.Compass,
            title = rowTitle(HomeRows.DiscoverRows),
            subtitle = rowSubtitle(HomeRows.DiscoverRows),
            trailingText = if (preferences.discoverRows.isEmpty()) "" else "${preferences.discoverRows.size}",
            highlighted = highlightSettingId == HomeRows.DiscoverRows.id,
            index = 0, count = 1,
            onClick = { navActions.onNavigate(Route.DiscoverRows()) },
        )

        // The header dock's shortcuts — the sibling customization
        // surface. The editor opens on the header tab; the
        // Navigation settings row opens the same editor on the
        // menu tab. A plain action row (the upload/restore
        // precedent — no spec-backed declaration).
        SettingListItem(
            icon = Tabler.Outline.Pin,
            title = stringResource(Res.string.settings_home_header_shortcuts),
            subtitle = stringResource(Res.string.settings_home_header_shortcuts_subtitle),
            trailingText = "",
            highlighted = false,
            index = 0, count = 1,
            onClick = {
                navActions.onNavigate(
                    Route.ShortcutsCustomize(ShortcutSurface.HEADER),
                )
            },
        )

        SettingToggleItem(
            icon = Tabler.Outline.CloudUpload,
            title = rowTitle(HomeRows.LayoutAutopush),
            subtitle = if (preferences.homeLayoutAutopush) stringResource(Res.string.settings_home_layout_autopush_on) else stringResource(Res.string.settings_home_layout_autopush_off),
            checked = preferences.homeLayoutAutopush,
            highlighted = highlightSettingId == HomeRows.LayoutAutopush.id,
            index = 0, count = 1,
            onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setLayoutAutopush(it) } },
        )

        // Manual sync actions — work regardless of the
        // autopush pref (the manual and automatic pushes serialize
        // through the exact same codec inside the controller).
        SettingListItem(
            icon = Tabler.Outline.Upload,
            title = stringResource(Res.string.settings_home_layout_upload),
            subtitle = stringResource(Res.string.settings_home_layout_upload_subtitle),
            trailingText = "",
            highlighted = false,
            index = 0, count = 1,
            onClick = {
                viewModel.uploadLayoutToServer(onUpload)
            },
        )

        SettingListItem(
            icon = Tabler.Outline.CloudDownload,
            title = stringResource(Res.string.settings_home_layout_restore),
            subtitle = stringResource(Res.string.settings_home_layout_restore_subtitle),
            trailingText = "",
            highlighted = false,
            index = 0, count = 1,
            onClick = {
                viewModel.fetchServerLayout(onRestoreFetch)
            },
        )

        val homeSections = rememberReorderableOrderedList(
            storedOrder = preferences.homeSectionOrder,
            onPersist = { order -> viewModel.edit { it.homeDiscovery.setHomeSectionOrder(order) } },
        )

        homeSections.items.forEachIndexed { index, sectionType ->
            val enabled = sectionType in preferences.enabledHomeSectionTypes
            SettingReorderableToggleItem(
                icon = homeSectionIcon(sectionType),
                title = sectionType.displayName,
                subtitle = sectionType.description,
                checked = enabled,
                index = index,
                count = homeSections.items.size,
                modifier = Modifier.onSizeChanged { homeSections.recordHeight(sectionType, it.height) },
                onCheckedChange = { checked ->
                    viewModel.edit { it.homeDiscovery.setSectionVisible(sectionType, checked) }
                },
                onDrag = { delta -> homeSections.onDrag(sectionType, delta) },
                onDragStart = { homeSections.onDragStart(sectionType) },
                onDragEnd = homeSections::onDragEnd,
            )
        }
    }
}

/**
 * The `home.curation` group body (ADR-0017's home split): the former display
 * group's hidden/restore rows — the rows-style toggle, the Next Up exclusion
 * editor and hidden-libraries restore drill-ins, and the content-gated unhide
 * action row (+1 while hidden CW items exist — [homeCurationScreenRowTotal]).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeCurationGroupBody(
    preferences: HomeScreenPreferences,
    highlightSettingId: String?,
    navActions: SettingsNavActions,
    viewModel: HomeSettingsViewModel,
) {
    SettingsGroup(
        icon = Tabler.Outline.EyeOff,
        title = stringResource(Res.string.settings_home_curation),
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = highlightSettingId in SettingsScreenGroups.homeCuration.itemIdSet,
    ) {
        // Derived from the curation declaration (every row renders
        // unconditionally) plus the unhide row while hidden continue-watching
        // items exist — its ContentGated exception, the +1 term the total
        // adds explicitly.
        SettingsItemList(total = homeCurationScreenRowTotal(preferences.hiddenCwItemIds.size)) {
            SettingToggleItem(
                icon = rowIcon(HomeRows.ClassicRows),
                title = rowTitle(HomeRows.ClassicRows),
                subtitle = if (preferences.classicRows) stringResource(Res.string.settings_classic_rows_on) else stringResource(Res.string.settings_classic_rows_off),
                checked = preferences.classicRows,
                highlighted = highlightSettingId == HomeRows.ClassicRows.id,
                onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setClassicRows(it) } },
            )
            SettingListItem(
                icon = rowIcon(HomeRows.NextUpHidden),
                title = rowTitle(HomeRows.NextUpHidden),
                subtitle = rowSubtitle(HomeRows.NextUpHidden),
                trailingText = if (preferences.nextUpExcludedSeriesIds.isEmpty()) "" else "${preferences.nextUpExcludedSeriesIds.size}",
                highlighted = highlightSettingId == HomeRows.NextUpHidden.id,
                onClick = { navActions.onNavigate(Route.NextUpExcluded) },
            )
            SettingListItem(
                icon = rowIcon(HomeRows.HiddenLibraries),
                title = rowTitle(HomeRows.HiddenLibraries),
                subtitle = rowSubtitle(HomeRows.HiddenLibraries),
                trailingText = if (preferences.myMediaHiddenLibraryIds.isEmpty()) "" else "${preferences.myMediaHiddenLibraryIds.size}",
                highlighted = highlightSettingId == HomeRows.HiddenLibraries.id,
                onClick = { navActions.onNavigate(Route.MyMediaHidden) },
            )
            if (preferences.hiddenCwItemIds.isNotEmpty()) {
                SettingListItem(
                    icon = rowIcon(HomeRows.UnhideCw),
                    title = rowTitle(HomeRows.UnhideCw),
                    subtitle = stringResource(Res.string.settings_unhide_continue_watching_subtitle, preferences.hiddenCwItemIds.size),
                    highlighted = highlightSettingId == HomeRows.UnhideCw.id,
                    onClick = { viewModel.edit { it.homeDiscovery.unhideAllCwItems() } },
                )
            }
        }
    }
}
