package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    viewModel: PrivacyDataViewModel = hiltViewModel(),
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()

    // One pending confirmation at a time: null = none.
    var pendingAction by remember { mutableStateOf<PendingPrivacyAction?>(null) }

    // Grab focus into the list so the first D-pad press lands on content, not the drawer rail.
    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(focusRequester = focusRequester, itemCount = 1, tag = "privacy_init")

    JellyPlayScreenScaffold(
        title = stringResource(R.string.settings_privacy_data),
        onBack = onBack,
        backgroundColor = backgroundColor,
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
                    title = stringResource(R.string.settings_clear_cache),
                    subtitle = stringResource(R.string.settings_clear_cache_subtitle),
                    index = 0, count = 6,
                    onClick = { pendingAction = PendingPrivacyAction.ClearCache },
                )
            }
            item(key = "item_clear_image_cache") {
                SettingListItem(
                    icon = Tabler.Outline.Photo,
                    title = stringResource(R.string.settings_clear_image_cache),
                    subtitle = stringResource(R.string.settings_clear_image_cache_subtitle),
                    index = 1, count = 6,
                    onClick = { pendingAction = PendingPrivacyAction.ClearImageCache },
                )
            }
            item(key = "item_clear_search_history") {
                SettingListItem(
                    icon = Tabler.Outline.History,
                    title = stringResource(R.string.settings_clear_search_history),
                    subtitle = stringResource(R.string.settings_clear_search_history_subtitle),
                    index = 2, count = 6,
                    onClick = { pendingAction = PendingPrivacyAction.ClearSearchHistory },
                )
            }
            item(key = "item_factory_reset") {
                SettingListItem(
                    icon = Tabler.Outline.Refresh,
                    title = stringResource(R.string.settings_factory_reset),
                    subtitle = stringResource(R.string.settings_factory_reset_message),
                    isDestructive = true,
                    index = 3, count = 6,
                    onClick = { pendingAction = PendingPrivacyAction.FactoryReset },
                )
            }
            item(key = "item_sign_out") {
                SettingListItem(
                    icon = Tabler.Outline.Logout,
                    title = stringResource(R.string.settings_sign_out),
                    subtitle = stringResource(R.string.settings_sign_out_subtitle),
                    isDestructive = true,
                    index = 4, count = 6,
                    onClick = { pendingAction = PendingPrivacyAction.SignOut(fromServer = false) },
                )
            }
            item(key = "item_sign_out_from_server") {
                SettingListItem(
                    icon = Tabler.Outline.Logout,
                    title = stringResource(R.string.settings_sign_out_from_server),
                    subtitle = stringResource(R.string.settings_sign_out_from_server_subtitle),
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
            confirmText = stringResource(R.string.settings_reset),
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
            dismissText = stringResource(R.string.settings_cancel),
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
    val titleRes: Int
    val messageRes: Int

    data object ClearCache : PendingPrivacyAction {
        override val titleRes = R.string.settings_clear_cache
        override val messageRes = R.string.settings_clear_cache_subtitle
    }
    data object ClearImageCache : PendingPrivacyAction {
        override val titleRes = R.string.settings_clear_image_cache
        override val messageRes = R.string.settings_clear_image_cache_subtitle
    }
    data object ClearSearchHistory : PendingPrivacyAction {
        override val titleRes = R.string.settings_clear_search_history
        override val messageRes = R.string.settings_clear_search_history_subtitle
    }
    data object FactoryReset : PendingPrivacyAction {
        override val titleRes = R.string.settings_factory_reset
        override val messageRes = R.string.settings_factory_reset_message
    }
    data class SignOut(val fromServer: Boolean) : PendingPrivacyAction {
        override val titleRes
            get() = if (fromServer) R.string.settings_sign_out_confirm_title_server
            else R.string.settings_sign_out_confirm_title
        override val messageRes
            get() = if (fromServer) R.string.settings_sign_out_confirm_message_server
            else R.string.settings_sign_out_confirm_message
    }
}
