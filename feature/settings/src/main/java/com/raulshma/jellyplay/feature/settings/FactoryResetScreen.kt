package com.raulshma.jellyplay.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.feedback.LocalUserMessageBus
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowRight
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Rocket

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
    viewModel: FactoryResetViewModel = hiltViewModel(),
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val userMessageBus = LocalUserMessageBus.current
    val prefs = viewModel.preferences
    val factory = viewModel.factory

    // One pending confirmation at a time: null = none.
    var pendingReset by remember { mutableStateOf<PendingReset?>(null) }
    var message by remember { mutableStateOf<Int?>(null) }

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

    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()

    // Grab focus into the list so the first D-pad press lands on content, not the drawer rail.
    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(focusRequester = focusRequester, itemCount = 1, tag = "factory_reset_init")

    JellyPlayScreenScaffold(
        title = stringResource(R.string.settings_factory_reset),
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
            // ---- Summary + Reset All --------------------------------------
            item(key = "summary") {
                FactoryResetSummaryCard(
                    totalChanged = totalChanged,
                    totalFields = totalFields,
                    onResetAll = { pendingReset = PendingReset.All },
                )
            }

            // ---- Integrations note ---------------------------------------
            item(key = "integrations_note") {
                IntegrationsNote()
            }

            // ---- Category list -------------------------------------------
            categoryDiffs.forEach { diff ->
                item(key = diff.view.category.key) {
                    FactoryResetCategoryItem(
                        icon = diff.view.icon,
                        nameRes = diff.view.displayNameRes,
                        changedCount = diff.changed.size,
                        totalInCategory = diff.total,
                        fields = diff.changed,
                        onResetCategory = { pendingReset = PendingReset.Category(diff.view.category) },
                    )
                }
            }
        }
    }

    // ---- Single shared confirmation dialog ------------------------------
    pendingReset?.let { target ->
        ConfirmDialog(
            title = stringResource(
                if (target is PendingReset.All) R.string.settings_factory_reset_all
                else R.string.settings_factory_reset
            ),
            message = stringResource(
                if (target is PendingReset.All) R.string.settings_factory_reset_all_message
                else R.string.settings_factory_reset_category_message
            ),
            confirmText = stringResource(R.string.settings_reset),
            onConfirm = {
                when (target) {
                    is PendingReset.All -> {
                        viewModel.resetAll()
                        message = R.string.settings_factory_reset_all_done
                    }
                    is PendingReset.Category -> {
                        viewModel.resetCategory(target.category)
                        message = R.string.settings_factory_reset_category_done
                    }
                }
                pendingReset = null
            },
            onDismiss = { pendingReset = null },
            dismissText = stringResource(R.string.settings_cancel),
        )
    }

    // ---- Toast feedback -------------------------------------------------
    message?.let { res ->
        val text = stringResource(res)
        LaunchedEffect(res) {
            userMessageBus.info(text)
            message = null
        }
    }
}

/** Which reset the user is confirming. */
private sealed interface PendingReset {
    data object All : PendingReset
    data class Category(val category: PreferenceResetCategory) : PendingReset
}

/** Precomputed per-category snapshot handed to the list body. */
private data class CategoryDiff(
    val view: PreferenceCategoryView,
    val changed: List<PreferenceField>,
    val total: Int,
)

// ---------------------------------------------------------------------------
// Summary card
// ---------------------------------------------------------------------------

@Composable
private fun FactoryResetSummaryCard(
    totalChanged: Int,
    totalFields: Int,
    onResetAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth24)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Tabler.Outline.Rocket,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_factory_reset),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.settings_factory_reset_summary_card, totalChanged, totalFields
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onResetAll,
            enabled = totalChanged > 0,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Tabler.Outline.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_factory_reset_all))
        }
    }
}

// ---------------------------------------------------------------------------
// Integrations out-of-scope note
// ---------------------------------------------------------------------------

@Composable
private fun IntegrationsNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth20)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Tabler.Outline.InfoCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.settings_factory_reset_integrations_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Category item
// ---------------------------------------------------------------------------

@Composable
private fun FactoryResetCategoryItem(
    icon: ImageVector,
    nameRes: Int,
    changedCount: Int,
    totalInCategory: Int,
    fields: List<PreferenceField>,
    onResetCategory: () -> Unit,
) {
    var expanded by rememberSaveable(totalInCategory == 0) { mutableStateOf(false) }

    val headerColor by animateColorAsState(
        targetValue = if (changedCount > 0) MaterialTheme.colorScheme.surfaceContainerLow
        else MaterialTheme.colorScheme.surfaceContainerLowest,
        label = "catHeaderColor",
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "catChevron",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val headerPressed by interactionSource.collectIsPressedAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth20)
            .background(headerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = if (headerPressed) 0.99f else 1f
                    scaleY = if (headerPressed) 0.99f else 1f
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = totalInCategory > 0,
                ) { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (changedCount > 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(nameRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (changedCount == 0) {
                        stringResource(R.string.settings_factory_reset_up_to_date)
                    } else {
                        stringResource(
                            R.string.settings_factory_reset_changed_count,
                            changedCount, totalInCategory,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (changedCount > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (changedCount > 0) {
                Icon(
                    imageVector = Tabler.Outline.ChevronDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer { rotationZ = chevronRotation },
                )
            }
        }

        AnimatedVisibility(
            visible = expanded && changedCount > 0,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                fields.forEach { field -> PreferenceFieldRow(field) }
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onResetCategory,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Tabler.Outline.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_factory_reset))
                }
            }
        }
    }
}

@Composable
private fun PreferenceFieldRow(field: PreferenceField) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = true),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = field.currentValue,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Tabler.Outline.ArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = field.factoryValue,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
