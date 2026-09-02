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
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.settings.components.CategoryDiff
import com.raulshma.jellyplay.feature.settings.components.IntegrationsNote
import com.raulshma.jellyplay.feature.settings.components.PreferenceDiffCategoryItem
import com.raulshma.jellyplay.feature.settings.components.PreferenceDiffSummaryCard
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cancel
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_factory_reset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_factory_reset_all
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_factory_reset_summary_card
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_factory_reset_all_done
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_factory_reset_all_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_factory_reset_category_done
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_factory_reset_category_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset

/**
 * Factory-reset review screen. Lists every preference category with a
 * changed-count and an expandable current-vs-factory diff. Two destructive
 * actions: per-category Reset and a screen-level Reset All (each gated by a
 * confirmation dialog). Resets are settings-only — they do not sign out or
 * delete downloads/cache/DB.
 *
 * Seerr / Radarr / Sonarr integration preferences live in separate DataStore
 * files and are intentionally not reset here (see the in-screen note).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FactoryResetScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: FactoryResetViewModel = koinViewModel(),
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val messenger = rememberSettingsMessenger()
    val prefs = viewModel.preferences
    val factory = viewModel.factory

    // One pending confirmation at a time: null = none.
    var pendingReset by remember { mutableStateOf<PendingReset?>(null) }
    var message by remember { mutableStateOf<StringResource?>(null) }

    // Compute the per-category diff once per (prefs, factory) snapshot — not
    // twice per recomposition. `remember(prefs)` invalidates when the live
    // preferences emit a new value.
    val categoryDiffs: List<CategoryDiff> = remember(prefs, factory) {
        PreferenceCategoryViews.map { view ->
            val changed = view.changedFields(prefs, factory)
            CategoryDiff(view, changed, view.totalFields(prefs, factory))
        }
    }
    val totalChanged = categoryDiffs.sumOf { it.changed.size }
    val totalFields = categoryDiffs.sumOf { it.total }

    val backgroundColorState = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState()

    // Grab focus into the list so the first D-pad press lands on content, not the drawer rail.
    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(focusRequester = focusRequester, itemCount = 1, tag = "factory_reset_init")

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.settings_factory_reset),
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
            // ---- Summary + Reset All --------------------------------------
            item(key = "summary") {
                PreferenceDiffSummaryCard(
                    totalChanged = totalChanged,
                    totalFields = totalFields,
                    titleRes = Res.string.settings_factory_reset,
                    summaryRes = Res.string.settings_factory_reset_summary_card,
                    primaryLabelRes = Res.string.settings_factory_reset_all,
                    onPrimary = { pendingReset = PendingReset.All },
                )
            }

            // ---- Integrations note ---------------------------------------
            item(key = "integrations_note") {
                IntegrationsNote()
            }

            // ---- Category list -------------------------------------------
            categoryDiffs.forEach { diff ->
                item(key = diff.view.category.key) {
                    PreferenceDiffCategoryItem(
                        icon = diff.view.icon,
                        nameRes = diff.view.displayNameRes,
                        changedCount = diff.changed.size,
                        totalInCategory = diff.total,
                        fields = diff.changed,
                        onAction = { pendingReset = PendingReset.Category(diff.view.category) },
                    )
                }
            }
        }
    }

    // ---- Single shared confirmation dialog ------------------------------
    pendingReset?.let { target ->
        ConfirmDialog(
            title = stringResource(
                if (target is PendingReset.All) Res.string.settings_factory_reset_all
                else Res.string.settings_factory_reset
            ),
            message = stringResource(
                if (target is PendingReset.All) Res.string.settings_factory_reset_all_message
                else Res.string.settings_factory_reset_category_message
            ),
            confirmText = stringResource(Res.string.settings_reset),
            onConfirm = {
                when (target) {
                    is PendingReset.All -> {
                        viewModel.resetAll()
                        message = Res.string.settings_factory_reset_all_done
                    }
                    is PendingReset.Category -> {
                        viewModel.resetCategory(target.category)
                        message = Res.string.settings_factory_reset_category_done
                    }
                }
                pendingReset = null
            },
            onDismiss = { pendingReset = null },
            dismissText = stringResource(Res.string.settings_cancel),
        )
    }

    // ---- Toast feedback -------------------------------------------------
    message?.let { res ->
        val text = stringResource(res)
        LaunchedEffect(res) {
            messenger?.info(text)
            message = null
        }
    }
}

/** Which reset the user is confirming. */
private sealed interface PendingReset {
    data object All : PendingReset
    data class Category(val category: PreferenceResetCategory) : PendingReset
}
