package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.History
import com.composables.icons.tabler.outline.Logout
import com.composables.icons.tabler.outline.Photo
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cache_cleared
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cancel
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_clear_cache
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_clear_cache_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_clear_image_cache
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_clear_image_cache_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_clear_search_history
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_clear_search_history_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_factory_reset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_factory_reset_all_done
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_factory_reset_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_image_cache_cleared
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_privacy_data
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_search_history_cleared
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out_confirm_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out_confirm_message_server
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out_confirm_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out_confirm_title_server
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out_from_server
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out_from_server_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out_subtitle

/**
 * Privacy & Data hub. Consolidates the destructive data actions that are
 * otherwise scattered across [StorageSettingsScreen] (clear cache, clear
 * image cache), [FactoryResetScreen] (factory reset), the search surfaces
 * (clear search history) and the account group (sign out) into a single
 * drill-in from [SettingsScreen].
 *
 * Each action is gated by a shared confirmation [ConfirmDialog] (mirrors the
 * sign-out confirm in [SettingsScreen]); the pending action is tracked with
 * a single nullable state. Sign-out is delegated to the app-level
 * `onLogout(fromServer)` callback threaded through the nav graph — the same
 * one [SettingsScreen] receives — since it is not a settings-level concern.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyDataScreen(
    onBack: () -> Unit,
    onLogout: (Boolean) -> Unit,
    viewModel: PrivacyDataViewModel = koinViewModel(),
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val backgroundColorState = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState()

    // One-shot action confirmations (screen-forward seam): resolve the texts
    // here, forward each emitted message through the messenger actual.
    val messenger = rememberSettingsMessenger()
    val cacheClearedText = stringResource(Res.string.settings_cache_cleared)
    val imageCacheClearedText = stringResource(Res.string.settings_image_cache_cleared)
    val searchHistoryClearedText = stringResource(Res.string.settings_search_history_cleared)
    val factoryResetDoneText = stringResource(Res.string.settings_factory_reset_all_done)
    LaunchedEffect(messenger) {
        viewModel.messages.collect { message ->
            when (message) {
                PrivacyUserMessage.CacheCleared -> messenger?.info(cacheClearedText)
                PrivacyUserMessage.ImageCacheCleared -> messenger?.info(imageCacheClearedText)
                PrivacyUserMessage.SearchHistoryCleared -> messenger?.info(searchHistoryClearedText)
                PrivacyUserMessage.FactoryResetDone -> messenger?.info(factoryResetDoneText)
                is PrivacyUserMessage.Raw -> messenger?.info(message.text)
            }
        }
    }

    // One pending confirmation at a time: null = none.
    var pendingAction by remember { mutableStateOf<PendingPrivacyAction?>(null) }

    // Grab focus into the list so the first D-pad press lands on content, not the drawer rail.
    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(focusRequester = focusRequester, itemCount = 1, tag = "privacy_init")

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.settings_privacy_data),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .tvFocusRestorer()
                .focusRequester(focusRequester),
            contentPadding = PaddingValues(
                start = adaptiveInfo.contentPadding(isTv),
                end = adaptiveInfo.contentPadding(isTv),
                bottom = adaptiveInfo.bottomPadding(isTv),
                top = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "item_clear_cache") {
                SettingListItem(
                    icon = Tabler.Outline.Trash,
                    title = stringResource(Res.string.settings_clear_cache),
                    subtitle = stringResource(Res.string.settings_clear_cache_subtitle),
                    index = 0, count = 6,
                    onClick = { pendingAction = PendingPrivacyAction.ClearCache },
                )
            }
            item(key = "item_clear_image_cache") {
                SettingListItem(
                    icon = Tabler.Outline.Photo,
                    title = stringResource(Res.string.settings_clear_image_cache),
                    subtitle = stringResource(Res.string.settings_clear_image_cache_subtitle),
                    index = 1, count = 6,
                    onClick = { pendingAction = PendingPrivacyAction.ClearImageCache },
                )
            }
            item(key = "item_clear_search_history") {
                SettingListItem(
                    icon = Tabler.Outline.History,
                    title = stringResource(Res.string.settings_clear_search_history),
                    subtitle = stringResource(Res.string.settings_clear_search_history_subtitle),
                    index = 2, count = 6,
                    onClick = { pendingAction = PendingPrivacyAction.ClearSearchHistory },
                )
            }
            item(key = "item_factory_reset") {
                SettingListItem(
                    icon = Tabler.Outline.Refresh,
                    title = stringResource(Res.string.settings_factory_reset),
                    subtitle = stringResource(Res.string.settings_factory_reset_message),
                    isDestructive = true,
                    index = 3, count = 6,
                    onClick = { pendingAction = PendingPrivacyAction.FactoryReset },
                )
            }
            item(key = "item_sign_out") {
                SettingListItem(
                    icon = Tabler.Outline.Logout,
                    title = stringResource(Res.string.settings_sign_out),
                    subtitle = stringResource(Res.string.settings_sign_out_subtitle),
                    isDestructive = true,
                    index = 4, count = 6,
                    onClick = { pendingAction = PendingPrivacyAction.SignOut(fromServer = false) },
                )
            }
            item(key = "item_sign_out_from_server") {
                SettingListItem(
                    icon = Tabler.Outline.Logout,
                    title = stringResource(Res.string.settings_sign_out_from_server),
                    subtitle = stringResource(Res.string.settings_sign_out_from_server_subtitle),
                    isDestructive = true,
                    index = 5, count = 6,
                    onClick = { pendingAction = PendingPrivacyAction.SignOut(fromServer = true) },
                )
            }
        }
    }

    // ---- Single shared confirmation dialog ------------------------------
    pendingAction?.let { action ->
        ConfirmDialog(
            title = stringResource(action.titleRes),
            message = stringResource(action.messageRes),
            confirmText = stringResource(Res.string.settings_reset),
            onConfirm = {
                when (action) {
                    PendingPrivacyAction.ClearCache -> viewModel.clearCache()
                    PendingPrivacyAction.ClearImageCache -> viewModel.clearImageCache()
                    PendingPrivacyAction.ClearSearchHistory -> viewModel.clearSearchHistory()
                    PendingPrivacyAction.FactoryReset -> viewModel.factoryReset()
                    is PendingPrivacyAction.SignOut -> onLogout(action.fromServer)
                }
                pendingAction = null
            },
            onDismiss = { pendingAction = null },
            dismissText = stringResource(Res.string.settings_cancel),
        )
    }
}

/**
 * Which destructive action the user is confirming.
 *
 * Carries its own confirmation-dialog string resources so the dialog reads
 * `stringResource(action.titleRes)` / `stringResource(action.messageRes)`
 * instead of two parallel `when` mappers that must be kept in sync with the
 * subtype list.
 */
private sealed interface PendingPrivacyAction {
    val titleRes: StringResource
    val messageRes: StringResource

    data object ClearCache : PendingPrivacyAction {
        override val titleRes = Res.string.settings_clear_cache
        override val messageRes = Res.string.settings_clear_cache_subtitle
    }
    data object ClearImageCache : PendingPrivacyAction {
        override val titleRes = Res.string.settings_clear_image_cache
        override val messageRes = Res.string.settings_clear_image_cache_subtitle
    }
    data object ClearSearchHistory : PendingPrivacyAction {
        override val titleRes = Res.string.settings_clear_search_history
        override val messageRes = Res.string.settings_clear_search_history_subtitle
    }
    data object FactoryReset : PendingPrivacyAction {
        override val titleRes = Res.string.settings_factory_reset
        override val messageRes = Res.string.settings_factory_reset_message
    }
    data class SignOut(val fromServer: Boolean) : PendingPrivacyAction {
        override val titleRes
            get() = if (fromServer) Res.string.settings_sign_out_confirm_title_server
            else Res.string.settings_sign_out_confirm_title
        override val messageRes
            get() = if (fromServer) Res.string.settings_sign_out_confirm_message_server
            else Res.string.settings_sign_out_confirm_message
    }
}
