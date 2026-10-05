package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Refresh
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.tv.CenteredBringIntoView
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cancel
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset

/**
 * The optional advanced-toggle pair the scaffold renders as its first top-bar
 * action. Screens without an advanced tier pass `null` and get no toggle.
 */
@Immutable
internal data class PreferenceAdvancedToggle(
    val showAdvanced: Boolean,
    val onToggle: () -> Unit,
)

/**
 * The optional reset-to-defaults action: the top-bar refresh [IconButton] plus
 * the confirmation dialog it opens. The icon's content description and both
 * dialog strings arrive resolved (callers read them from string resources);
 * [onReset] performs the category reset itself — the scaffold owns only the
 * dialog open/close choreography.
 */
@Immutable
internal data class PreferenceResetAction(
    val iconContentDescription: String,
    val dialogTitle: String,
    val dialogMessage: String,
    val onReset: () -> Unit,
)

/**
 * The settings sub-screen chassis: the ~35 lines of identical chrome every
 * preference screen hand-copied — background-color state, initial TV focus
 * grab ([TvGrabInitialFocus], tag preserved verbatim per screen), the list
 * state, the optional deep-link highlight-scroll derivation
 * ([rememberHighlightScrollIndex] + [HighlightScrollEffect], active only when
 * [highlightGroups] is non-null), the optional advanced toggle and reset
 * action in the top bar, the [JellyPlayScreenScaffold] →
 * [CenteredBringIntoView] → [LazyColumn] stack (`fillMaxSize` + inner padding
 * + [tvFocusRestorer] + focus requester, adaptive start/end/bottom content
 * padding), and the optional [SettingsPickerDialog] host.
 *
 * Screens whose chrome deviates (input forms with `imePadding`, directory
 * pages with `verticalArrangement`, bespoke column layouts) stay inline —
 * this chassis is only for the chrome-identical cluster.
 *
 * TV focus contract (do not regress): [focusTag] is passed through to
 * [TvGrabInitialFocus] unchanged, the focus requester attaches to the
 * [LazyColumn] exactly as the hand-copied chrome did, and the LazyColumn
 * modifier chain keeps its original order (`fillMaxSize` → `padding` →
 * `tvFocusRestorer` → `focusRequester`).
 *
 * @param pickerHost when true, the scaffold renders the single root
 *   [SettingsPickerDialog] (including dismissal). Screens with no pickers
 *   pass `false` and get no dialog host at all.
 * @param pickerHostState a screen-owned picker cell handed in place of the
 *   scaffold's private one — for screens that must open a picker from
 *   OUTSIDE the lazy content (a screen-scope effect a lazy item scrolled
 *   out of the viewport could never fire). The scaffold reads it for the
 *   dialog host exactly as it reads its own.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
internal fun PreferenceScreenScaffold(
    title: String,
    onBack: () -> Unit,
    focusTag: String,
    highlightSettingId: String? = null,
    highlightGroups: List<Set<String>>? = null,
    adjustForAdvanced: (groupIndex: Int) -> Int = { it },
    advancedToggle: PreferenceAdvancedToggle? = null,
    reset: PreferenceResetAction? = null,
    pickerHost: Boolean = false,
    pickerHostState: MutableState<PickerState<*>?>? = null,
    actions: @Composable () -> Unit = {},
    content: LazyListScope.(activePicker: MutableState<PickerState<*>?>) -> Unit,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val backgroundColorState = rememberScreenBackgroundColorState()

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = focusTag,
    )

    val scrollState = rememberLazyListState()
    if (highlightGroups != null) {
        val scrollIndex = rememberHighlightScrollIndex(
            highlightSettingId,
            highlightGroups,
            adjustForAdvanced,
        )
        HighlightScrollEffect(scrollState, scrollIndex)
    }

    val activePicker = pickerHostState ?: remember { mutableStateOf<PickerState<*>?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    JellyPlayScreenScaffold(
        title = title,
        onBack = onBack,
        backgroundColorState = backgroundColorState,
        actions = {
            advancedToggle?.let { advanced ->
                AdvancedSettingsToggleButton(
                    showAdvanced = advanced.showAdvanced,
                    onToggle = advanced.onToggle,
                )
            }
            if (reset != null) {
                IconButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.focusIndicator(CircleShape),
                ) {
                    Icon(
                        Tabler.Outline.Refresh,
                        contentDescription = reset.iconContentDescription,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            actions()
        },
    ) { innerPadding ->
        CenteredBringIntoView {
            LazyColumn(
                state = scrollState,
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
                content(activePicker)
            }
        }
    }

    if (reset != null && showResetDialog) {
        ConfirmDialog(
            title = reset.dialogTitle,
            message = reset.dialogMessage,
            confirmText = stringResource(Res.string.settings_reset),
            onConfirm = {
                reset.onReset()
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false },
            dismissText = stringResource(Res.string.settings_cancel),
        )
    }

    if (pickerHost) {
        SettingsPickerDialog(
            state = activePicker.value,
            onDismiss = { activePicker.value = null },
        )
    }
}
