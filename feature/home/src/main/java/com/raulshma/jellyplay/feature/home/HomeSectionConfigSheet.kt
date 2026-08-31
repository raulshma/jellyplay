package com.raulshma.jellyplay.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowDown
import com.composables.icons.tabler.outline.ArrowUp
import com.composables.icons.tabler.outline.Settings
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.components.rememberHomeSectionIcon
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

/**
 * The inline sheet's whole derived-input surface, computed by
 * [sectionConfigCapabilities]: the current toggle state (global or
 * per-library), the row's position in the user's ordering, and the
 * Move Up/Down enablement. One value so the sheet's interface stays flat and
 * the derivation is assertable in one place.
 */
@Immutable
internal data class SectionConfigCapabilities(
    val enabled: Boolean,
    val perLibrary: Boolean,
    val position: Int,
    val total: Int,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
)

/**
 * Derives the section-config sheet's inputs from the preference mirrors.
 *
 * - **Enabled**: globally, membership in [enabledTypes]; per-library
 *   ([libraryId] non-null, LATEST_MEDIA), absence from that library's
 *   DISABLED override set (defaulting to enabled when absent).
 * - **Move enablement**: [SectionConfigCapabilities.canMoveUp] /
 *   [canMoveDown] are false at the respective edges AND when the type is
 *   absent from [order] entirely (position -1). Pure and internal so the
 *   test asserts THIS rule instead of a copy.
 */
internal fun sectionConfigCapabilities(
    type: HomeSectionType,
    libraryId: String?,
    order: List<HomeSectionType>,
    enabledTypes: Set<HomeSectionType>,
    libraryOverrides: Map<String, Set<HomeSectionType>>,
): SectionConfigCapabilities {
    val perLibrary = libraryId != null
    val index = order.indexOf(type)
    return SectionConfigCapabilities(
        enabled = if (perLibrary) {
            type !in libraryOverrides[libraryId].orEmpty()
        } else {
            type in enabledTypes
        },
        perLibrary = perLibrary,
        position = index,
        total = order.size,
        canMoveUp = index > 0,
        canMoveDown = index in 0..(order.lastIndex - 1),
    )
}

/**
 * Inline bottom sheet for configuring a single home section — opened by
 * long-pressing the section title on Home. Its action lambdas route through
 * the `HomeDiscoveryStore` section-prefs commands (`setSectionVisible`,
 * `moveSection`, `setLibrarySectionVisible`) — the same sanctioned write path
 * as the Settings screens, so there is one source of truth, no duplicated
 * logic.
 *
 * Two modes, selected by [perLibrary]:
 *
 * - **Global** ([perLibrary] = false): the common configurable sections
 *   (CONTINUE_WATCHING, NEXT_UP, RECENTLY_ADDED, RECOMMENDATIONS). Exposes a
 *   global Show/Hide toggle (`setSectionVisible`) plus Move Up / Move Down
 *   (`moveSection`) relative to the user's section ordering, with a live
 *   position indicator ("Position 2 of 5").
 * - **Per-library** ([perLibrary] = true): LATEST_MEDIA rows, of which there is
 *   one per Jellyfin library. Exposes a per-library Show/Hide toggle
 *   (`setLibrarySectionVisible`) — identical to Settings → Configure
 *   Libraries. Per-library rows are not individually ordered (they move as a
 *   group), so Move Up/Down is hidden in this mode.
 *
 * Both modes offer a deep-link for pinned sections, library overrides, presets,
 * and global ordering that the inline sheet doesn't cover.
 *
 * Visual structure mirrors the app's sheet design language (SeerrRequestDialog,
 * SettingsGroup): a header row with a leading section icon, a grouped card
 * containing the actions, a divider, and a trailing deep-link row.
 *
 * Built on [TvSafeSheet] so TV gets its D-pad-navigable Dialog variant and
 * mobile gets the standard `ModalBottomSheet`.
 *
 * All action lambdas are caller-provided and intended to be `remember`-ed at
 * the hoisting site so this composable stays skippable across recompositions
 * while the sheet is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeSectionConfigSheet(
    sectionType: HomeSectionType,
    capabilities: SectionConfigCapabilities,
    onToggleVisible: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onConfigureLayout: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isTv = LocalTvMode.current
    val colorScheme = MaterialTheme.colorScheme
    val sectionIcon = rememberHomeSectionIcon(sectionType)

    TvSafeSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = if (isTv) 8.dp else 16.dp),
        ) {
            // ── Header: section icon + title + description ───────────────
            SheetHeader(
                title = sectionType.displayName,
                subtitle = sectionType.description,
                icon = sectionIcon,
                modifier = Modifier.padding(bottom = 14.dp),
            )

            // ── Action card: visibility toggle (+ move up/down for global) ─
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShapeCache.smooth20)
                    .background(colorScheme.surfaceContainerLow)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                VisibilityToggleRow(
                    icon = sectionIcon,
                    enabled = capabilities.enabled,
                    onCheckedChange = onToggleVisible,
                )

                // Per-library rows aren't individually ordered — only the
                // global path exposes reorder controls and a position chip.
                if (!capabilities.perLibrary) {
                    HorizontalDivider(
                        color = colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Position ${capabilities.position + 1} of ${capabilities.total}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        MoveButton(
                            label = stringResource(R.string.home_move_up),
                            icon = Tabler.Outline.ArrowUp,
                            enabled = capabilities.canMoveUp,
                            onClick = onMoveUp,
                            modifier = Modifier.weight(1f),
                        )
                        MoveButton(
                            label = stringResource(R.string.home_move_down),
                            icon = Tabler.Outline.ArrowDown,
                            enabled = capabilities.canMoveDown,
                            onClick = onMoveDown,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Text(
                        text = "Applies to this library only. Other libraries are unaffected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Deep-link row ────────────────────────────────────────────
            TextButton(
                onClick = onConfigureLayout,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.End),
            ) {
                Icon(
                    Tabler.Outline.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(if (capabilities.perLibrary) "Configure Libraries" else "Configure Home Layout")
            }
        }
    }
}

/**
 * The show/hide toggle row. The leading icon's tint animates with the toggle
 * state (primary when on, muted when off), giving immediate feedback that
 * mirrors the settings row chrome.
 */
@Composable
private fun VisibilityToggleRow(
    icon: ImageVector,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val iconTint by animateColorAsState(
        targetValue = if (enabled) colorScheme.primary else colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 250, easing = LinearEasing),
        label = "visibilityIconTint",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (enabled) "Visible on Home" else "Hidden from Home",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = colorScheme.onSurface,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

/**
 * Reorder button with press feedback (scale + alpha) matching the settings row
 * interaction language. TV uses focus-indicator; touch uses the press scale.
 */
@Composable
private fun MoveButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val tvFocusState = com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState()
    val scale = if (isPressed) 0.97f else 1f
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .tvFocusIndicator(tvFocusState, ShapeCache.smooth12),
        shape = ShapeCache.smooth12,
        interactionSource = interactionSource,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}
