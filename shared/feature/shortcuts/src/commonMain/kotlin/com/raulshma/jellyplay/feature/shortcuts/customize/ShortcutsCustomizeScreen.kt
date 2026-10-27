package com.raulshma.jellyplay.feature.shortcuts.customize

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.ChevronUp
import com.composables.icons.tabler.outline.DotsVertical
import com.composables.icons.tabler.outline.Pencil
import com.composables.icons.tabler.outline.Pin
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Restore
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.Trash
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.model.ShortcutDefinition
import com.raulshma.jellyplay.core.model.ShortcutSurface
import com.raulshma.jellyplay.core.model.ShortcutTargetKind
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.matchesPickerQuery
import com.raulshma.jellyplay.core.ui.components.ShortcutIconGridPicker
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.icons.ShortcutIconRegistry
import com.raulshma.jellyplay.feature.shortcuts.ShortcutCategory
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.Res
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_action_badge
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_add
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_also_in_header
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_also_in_menu
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_available_section
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_edit
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_empty_overflow
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_cancel
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_clear_search
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_empty_no_definitions
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_empty_unpinned
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_header_capacity
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_header_empty_message
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_header_empty_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_header_full
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_label_hint
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_move_down
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_move_up
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_no_matches
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_overflow_full
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_pick_icon
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_pick_target
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_search_targets
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_pin_header
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_pin_overflow
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_pin_section
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_pinned_section
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_preview
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_preview_more
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_remove
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_reset
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_reset_confirm_message
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_reset_confirm_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_save
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_screen_badge
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_tab_header
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_tab_menu
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_use_default_icon
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The shortcuts-customization editor: two tabs, one per surface (the home
 * header dock, the overflow menu — the Settings entry points land on their
 * own tab via the route's [initialSurface]). Each tab is self-sufficient: a
 * live preview of the surface, the pinned list (reorder via the up/down
 * commands — TV-first, dpad-reachable), the add flow through one
 * [TvSafeSheet] (target picker → surface pins → icon grid → label override),
 * and the quick-pin pool of definitions not yet on this surface. Every
 * command is a [ShortcutsCustomizeViewModel] one-liner over the store's
 * atomic write.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ShortcutsCustomizeScreen(
    onBack: () -> Unit,
    initialSurface: ShortcutSurface = ShortcutSurface.OVERFLOW,
    viewModel: ShortcutsCustomizeViewModel = org.koin.compose.viewmodel.koinViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.pruneUnknownTargets() }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var surface by rememberSaveable { mutableStateOf(initialSurface) }
    var draft by remember { mutableStateOf<EditorDraft?>(null) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.shortcuts_customize_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = { confirmReset = true }) {
                Icon(
                    imageVector = Tabler.Outline.Restore,
                    contentDescription = stringResource(Res.string.shortcuts_customize_reset),
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ShortcutsSurfaceTabs(
                selected = surface,
                onSelect = { surface = it },
            )

            val pinnedRows = if (surface == ShortcutSurface.HEADER) state.sections.headerRows else state.sections.overflowRows
            val availableRows = if (surface == ShortcutSurface.HEADER) state.sections.availableHeaderRows else state.sections.availableOverflowRows
            val surfaceCapacity = if (surface == ShortcutSurface.HEADER) state.sections.headerCapacity else state.sections.overflowCapacity
            val surfaceFull = if (surface == ShortcutSurface.HEADER) state.sections.headerFull else state.sections.overflowFull
            val capacityText = stringResource(
                Res.string.shortcuts_customize_header_capacity,
                pinnedRows.size,
                surfaceCapacity,
            )
            val pinnedSectionTitle = stringResource(Res.string.shortcuts_customize_pinned_section)
            val availableSectionTitle = stringResource(Res.string.shortcuts_customize_available_section)
            val pinCd = stringResource(
                if (surface == ShortcutSurface.HEADER) Res.string.shortcuts_customize_pin_header else Res.string.shortcuts_customize_pin_overflow,
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "preview") {
                    SurfacePreviewCard(surface = surface, rows = pinnedRows)
                }

                sectionHeader(
                    title = pinnedSectionTitle,
                    capacity = capacityText,
                )

                if (pinnedRows.isEmpty()) {
                    item(key = "pinned-empty") {
                        PinnedEmptyState(
                            surface = surface,
                            surfaceCapacity = surfaceCapacity,
                            onAdd = { draft = EditorDraft.new() },
                        )
                    }
                } else {
                    items(pinnedRows, key = { "p:" + it.definition.id }, contentType = { "pinnedRow" }) { row ->
                        val index = pinnedRows.indexOf(row)
                        PinnedShortcutRow(
                            row = row,
                            isFirst = index == 0,
                            isLast = index == pinnedRows.lastIndex,
                            onMoveUp = { viewModel.move(row.definition.id, surface, up = true) },
                            onMoveDown = { viewModel.move(row.definition.id, surface, up = false) },
                            onEdit = {
                                draft = EditorDraft.of(
                                    row.definition,
                                    headerPinned = row.pinnedHeader,
                                    overflowPinned = row.pinnedOverflow,
                                )
                            },
                            onRemove = { viewModel.remove(row.definition.id) },
                        )
                    }
                }

                item(key = "add") {
                    FilledTonalButton(
                        onClick = { draft = EditorDraft.new() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        Icon(Tabler.Outline.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.shortcuts_customize_add))
                    }
                }

                sectionHeader(
                    title = availableSectionTitle,
                    capacity = null,
                )

                if (availableRows.isEmpty()) {
                    item(key = "available-empty") {
                        // "Every shortcut is pinned." is only true when something
                        // exists to pin; a fresh user (zero definitions) needs the
                        // add-first nudge instead.
                        AvailableEmptyState(hasDefinitions = state.slice.definitions.isNotEmpty())
                    }
                } else {
                    items(availableRows, key = { "a:" + it.definition.id }, contentType = { "availableRow" }) { row ->
                        AvailableShortcutRow(
                            row = row,
                            surface = surface,
                            pinEnabled = !surfaceFull,
                            pinContentDescription = pinCd,
                            onPin = {
                                viewModel.setSurfaces(
                                    row.definition.id,
                                    header = surface == ShortcutSurface.HEADER,
                                    overflow = surface == ShortcutSurface.OVERFLOW,
                                )
                            },
                            onEdit = {
                                draft = EditorDraft.of(
                                    row.definition,
                                    headerPinned = row.pinnedHeader,
                                    overflowPinned = row.pinnedOverflow,
                                )
                            },
                            onRemove = { viewModel.remove(row.definition.id) },
                        )
                    }
                }
            }
        }
    }

    draft?.let { current ->
        ShortcutEditorSheet(
            draft = current,
            pickerByCategory = state.pickerByCategory,
            headerFull = state.sections.headerFull,
            overflowFull = state.sections.overflowFull,
            defaultSurface = surface,
            editingLabel = editingRowLabel(state, current),
            onDismiss = { draft = null },
            onSave = { saved ->
                if (saved.originalId == null && saved.kind != null && saved.targetId != null) {
                    viewModel.addShortcut(saved.kind, saved.targetId, saved.iconId, saved.labelOverride, saved.pinHeader, saved.pinOverflow)
                } else if (saved.originalId != null) {
                    val original = state.slice.definitions.firstOrNull { it.id == saved.originalId }
                    if (original != null) {
                        viewModel.updateShortcut(
                            original.copy(
                                iconId = saved.iconId,
                                labelOverride = saved.labelOverride.takeIf { it.isNotBlank() },
                            ),
                        )
                        viewModel.setSurfaces(saved.originalId, header = saved.pinHeader, overflow = saved.pinOverflow)
                    }
                }
                draft = null
            },
        )
    }

    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(Res.string.shortcuts_customize_reset_confirm_title),
            message = stringResource(Res.string.shortcuts_customize_reset_confirm_message),
            confirmText = stringResource(Res.string.shortcuts_customize_reset),
            dismissText = stringResource(Res.string.shortcuts_customize_cancel),
            onConfirm = {
                viewModel.resetAll()
                confirmReset = false
            },
            onDismiss = { confirmReset = false },
        )
    }
}

/** The label shown in the edit sheet's summary line: the definition's resolved face. */
@Composable
private fun editingRowLabel(
    state: ShortcutsCustomizeViewModel.UiState,
    draft: EditorDraft,
): String? {
    val id = draft.originalId ?: return null
    return state.sections.rowFor(id)?.let { editorRowLabel(it) }
}

// ─── Tabs ──────────────────────────────────────────────────────────────────

@Composable
private fun ShortcutsSurfaceTabs(
    selected: ShortcutSurface,
    onSelect: (ShortcutSurface) -> Unit,
) {
    TabRow(
        selectedTabIndex = if (selected == ShortcutSurface.HEADER) 0 else 1,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Tab(
            selected = selected == ShortcutSurface.HEADER,
            onClick = { onSelect(ShortcutSurface.HEADER) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Tabler.Outline.Pin, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.shortcuts_customize_tab_header))
                }
            },
        )
        Tab(
            selected = selected == ShortcutSurface.OVERFLOW,
            onClick = { onSelect(ShortcutSurface.OVERFLOW) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Tabler.Outline.DotsVertical, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.shortcuts_customize_tab_menu))
                }
            },
        )
    }
}

// ─── Live previews (read-only mirrors of the real surfaces) ─────────────────

@Composable
private fun SurfacePreviewCard(
    surface: ShortcutSurface,
    rows: List<ShortcutsCustomizePolicy.EditorRow>,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(Res.string.shortcuts_customize_preview),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            if (surface == ShortcutSurface.HEADER) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    HeaderDockPreview(rows)
                }
            } else {
                MenuPreview(rows)
            }
        }
    }
}

/** The header dock's capsule: the pinned icons, in order, in one rounded pill. */
@Composable
private fun HeaderDockPreview(rows: List<ShortcutsCustomizePolicy.EditorRow>) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (rows.isEmpty()) {
                Icon(
                    imageVector = Tabler.Outline.Pin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                rows.forEach { row ->
                    Icon(
                        imageVector = editorRowIcon(row),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

/**
 * The overflow menu's stacked pills (icon + label), at most six of them so
 * the preview stays a glance, not a scroll — a muted pill counts the rest.
 */
@Composable
private fun MenuPreview(rows: List<ShortcutsCustomizePolicy.EditorRow>) {
    val shown = rows.take(6)
    val hidden = rows.size - shown.size
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (rows.isEmpty()) {
            MenuPreviewPill(
                icon = Tabler.Outline.DotsVertical,
                label = stringResource(Res.string.shortcuts_customize_empty_overflow),
                isPlaceholder = true,
            )
        } else {
            shown.forEach { row ->
                MenuPreviewPill(icon = editorRowIcon(row), label = editorRowLabel(row))
            }
            if (hidden > 0) {
                MenuPreviewPill(
                    icon = Tabler.Outline.Plus,
                    label = stringResource(Res.string.shortcuts_customize_preview_more, hidden),
                    isPlaceholder = true,
                )
            }
        }
    }
}

@Composable
private fun MenuPreviewPill(
    icon: ImageVector,
    label: String,
    isPlaceholder: Boolean = false,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isPlaceholder) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isPlaceholder) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─── List sections ─────────────────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.sectionHeader(title: String, capacity: String?) {
    item(key = "header:$title") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            capacity?.let {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun PinnedEmptyState(
    surface: ShortcutSurface,
    surfaceCapacity: Int,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = if (surface == ShortcutSurface.HEADER) Tabler.Outline.Pin else Tabler.Outline.DotsVertical,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = if (surface == ShortcutSurface.HEADER) {
                    stringResource(Res.string.shortcuts_customize_header_empty_title)
                } else {
                    stringResource(Res.string.shortcuts_customize_empty_overflow)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            if (surface == ShortcutSurface.HEADER) {
                Spacer(Modifier.size(4.dp))
                Text(
                    text = stringResource(Res.string.shortcuts_customize_header_empty_message, surfaceCapacity),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.size(12.dp))
            Button(onClick = onAdd) {
                Icon(Tabler.Outline.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.shortcuts_customize_add))
            }
        }
    }
}

@Composable
private fun AvailableEmptyState(hasDefinitions: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(
            if (hasDefinitions) {
                Res.string.shortcuts_customize_empty_unpinned
            } else {
                Res.string.shortcuts_customize_empty_no_definitions
            },
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
    )
}

/** A pinned row: resolved face + up/down (TV-first reorder), edit, remove. */
@Composable
private fun PinnedShortcutRow(
    row: ShortcutsCustomizePolicy.EditorRow,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = editorRowIcon(row),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = editorRowLabel(row),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = rowKindBadge(row),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RowEditorAction(Tabler.Outline.ChevronUp, stringResource(Res.string.shortcuts_customize_move_up), enabled = !isFirst, onClick = onMoveUp)
            RowEditorAction(Tabler.Outline.ChevronDown, stringResource(Res.string.shortcuts_customize_move_down), enabled = !isLast, onClick = onMoveDown)
            RowEditorAction(Tabler.Outline.Pencil, stringResource(Res.string.shortcuts_customize_edit), onClick = onEdit)
            RowEditorAction(Tabler.Outline.Trash, stringResource(Res.string.shortcuts_customize_remove), onClick = onRemove)
        }
    }
}

/**
 * A quick-pin pool row: the definition isn't on this surface — one tap pins
 * it (the "also in" badge carries its other-surface pin), or edit/remove.
 */
@Composable
private fun AvailableShortcutRow(
    row: ShortcutsCustomizePolicy.EditorRow,
    surface: ShortcutSurface,
    pinEnabled: Boolean,
    pinContentDescription: String,
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val alsoInRes: StringResource? = when {
        surface == ShortcutSurface.HEADER && row.pinnedOverflow -> Res.string.shortcuts_customize_also_in_menu
        surface == ShortcutSurface.OVERFLOW && row.pinnedHeader -> Res.string.shortcuts_customize_also_in_header
        else -> null
    }
    val alsoInText: String? = if (alsoInRes != null) stringResource(alsoInRes) else null
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = editorRowIcon(row),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = editorRowLabel(row),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(rowKindBadge(row), alsoInText)
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RowEditorAction(Tabler.Outline.Pin, contentDescription = pinContentDescription, enabled = pinEnabled, onClick = onPin)
            RowEditorAction(Tabler.Outline.Pencil, stringResource(Res.string.shortcuts_customize_edit), onClick = onEdit)
            RowEditorAction(Tabler.Outline.Trash, stringResource(Res.string.shortcuts_customize_remove), onClick = onRemove)
        }
    }
}

// ─── Row faces ─────────────────────────────────────────────────────────────

/**
 * The icon the surfaces render: the user's picked icon when it resolves, else
 * the catalog default — the row never lies about the pin's appearance.
 */
private fun editorRowIcon(row: ShortcutsCustomizePolicy.EditorRow): ImageVector =
    ShortcutIconRegistry.resolve(row.definition.iconId)
        ?: row.entry?.defaultIcon
        ?: Tabler.Outline.DotsVertical

@Composable
private fun editorRowLabel(row: ShortcutsCustomizePolicy.EditorRow): String =
    row.definition.labelOverride
        ?: row.entry?.let { stringResource(it.labelRes) }
        ?: row.definition.targetId

@Composable
private fun rowKindBadge(row: ShortcutsCustomizePolicy.EditorRow): String =
    when {
        row.entry == null -> row.definition.targetId
        row.entry.isAction -> stringResource(Res.string.shortcuts_customize_action_badge)
        else -> stringResource(Res.string.shortcuts_customize_screen_badge)
    }

// ─── Editor draft (the sheet's state) ──────────────────────────────────────

/** The add/edit sheet's working state; [originalId] null = adding. */
@Immutable
private data class EditorDraft(
    val originalId: String?,
    val kind: ShortcutTargetKind?,
    val targetId: String?,
    /** Persisted icon id; blank = the target's catalog default. */
    val iconId: String,
    val labelOverride: String,
    val pinHeader: Boolean,
    val pinOverflow: Boolean,
) {
    companion object {
        fun new() = EditorDraft(null, null, null, "", "", pinHeader = false, pinOverflow = false)

        fun of(definition: ShortcutDefinition, headerPinned: Boolean, overflowPinned: Boolean) = EditorDraft(
            originalId = definition.id,
            kind = definition.kind,
            targetId = definition.targetId,
            iconId = definition.iconId,
            labelOverride = definition.labelOverride.orEmpty(),
            pinHeader = headerPinned,
            pinOverflow = overflowPinned,
        )
    }
}

// ─── Editor sheet ──────────────────────────────────────────────────────────

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ShortcutEditorSheet(
    draft: EditorDraft,
    pickerByCategory: Map<ShortcutCategory, List<ShortcutsCustomizeViewModel.PickerItem>>,
    headerFull: Boolean,
    overflowFull: Boolean,
    defaultSurface: ShortcutSurface,
    editingLabel: String?,
    onDismiss: () -> Unit,
    onSave: (EditorDraft) -> Unit,
) {
    var current by remember(draft) { mutableStateOf(draft) }

    TvSafeSheet(
        onDismissRequest = onDismiss,
        // Scrollable: an unfiltered destination picker + the icon grid + the
        // form tail overflow the sheet on phones, clipping Save. Both inner
        // scrollers carry bounded heights (picker 220dp, icon grid 264dp), so
        // the opt-in scroll is measurement-safe.
        scrollable = true,
        title = stringResource(
            if (draft.originalId == null) Res.string.shortcuts_customize_add else Res.string.shortcuts_customize_edit,
        ),
    ) {
        // Horizontal breathing room: TvSafeSheet's mobile column is edge-to-edge
        // by design (sheets add their own inset — same 16dp as the section
        // config sheet), so the editor carries its own gutter.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ── 1 · Destination (add) / resolved face (edit) ─────────────
            if (draft.originalId == null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(Res.string.shortcuts_customize_pick_target),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    ShortcutTargetPicker(
                        pickerByCategory = pickerByCategory,
                        selectedKind = current.kind,
                        selectedTargetId = current.targetId,
                        onSelect = { kind, targetId ->
                            // Picking under the Header tab suggests the header pin:
                            // the first pick auto-checks the tab's own surface so a
                            // new shortcut lands where the user started. Toggling a
                            // pin off is respected — the suggestion only fires while
                            // both are unchecked, i.e. on a fresh destination pick.
                            val suggest =
                                if (!current.pinHeader && !current.pinOverflow) defaultSurface else null
                            current = current.copy(
                                kind = kind,
                                targetId = targetId,
                                pinHeader = current.pinHeader || suggest == ShortcutSurface.HEADER,
                                pinOverflow = current.pinOverflow || suggest == ShortcutSurface.OVERFLOW,
                            )
                        },
                    )
                }
            } else if (editingLabel != null) {
                Text(
                    text = editingLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // ── 2 · Surface pins: where it lives outranks how it looks ────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(Res.string.shortcuts_customize_pin_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SurfacePinCard(
                        icon = Tabler.Outline.Pin,
                        label = stringResource(Res.string.shortcuts_customize_pin_header),
                        checked = current.pinHeader,
                        enabled = current.pinHeader || !headerFull,
                        onChecked = { current = current.copy(pinHeader = it) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    SurfacePinCard(
                        icon = Tabler.Outline.DotsVertical,
                        label = stringResource(Res.string.shortcuts_customize_pin_overflow),
                        checked = current.pinOverflow,
                        enabled = current.pinOverflow || !overflowFull,
                        onChecked = { current = current.copy(pinOverflow = it) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                // The capacity gates the store's write clamp enforces — pinned
                // rows don't consume extra slots, so a checked toggle always
                // stays enabled (unchecking must never lock); checking needs a
                // free slot, and a full+unchecked card gets the why here.
                val fullNote = when {
                    headerFull && !current.pinHeader -> stringResource(Res.string.shortcuts_customize_header_full)
                    overflowFull && !current.pinOverflow -> stringResource(Res.string.shortcuts_customize_overflow_full)
                    else -> null
                }
                fullNote?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── 3 · Icon ──────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(Res.string.shortcuts_customize_pick_icon),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                ShortcutIconGridPicker(
                    selectedIconId = current.iconId,
                    onIconSelected = { id -> current = current.copy(iconId = id) },
                )
                if (current.iconId.isNotEmpty()) {
                    TextButton(onClick = { current = current.copy(iconId = "") }) {
                        Text(stringResource(Res.string.shortcuts_customize_use_default_icon))
                    }
                }
            }

            // ── 4 · Label ─────────────────────────────────────────────────
            OutlinedTextField(
                value = current.labelOverride,
                onValueChange = { current = current.copy(labelOverride = it) },
                label = { Text(stringResource(Res.string.shortcuts_customize_label_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── 5 · Actions: explicit Cancel (swipe/back also dismiss) ────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.shortcuts_customize_cancel))
                }
                Button(
                    onClick = { onSave(current) },
                    enabled = current.kind != null && current.targetId != null,
                ) {
                    Text(stringResource(Res.string.shortcuts_customize_save))
                }
            }
        }
    }
}

@Composable
private fun ShortcutTargetPicker(
    pickerByCategory: Map<ShortcutCategory, List<ShortcutsCustomizeViewModel.PickerItem>>,
    selectedKind: ShortcutTargetKind?,
    selectedTargetId: String?,
    onSelect: (ShortcutTargetKind, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        singleLine = true,
        leadingIcon = {
            Icon(Tabler.Outline.Search, contentDescription = null, modifier = Modifier.size(20.dp))
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { query = "" }) {
                    Icon(
                        imageVector = Tabler.Outline.X,
                        contentDescription = stringResource(Res.string.shortcuts_customize_clear_search),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        placeholder = { Text(stringResource(Res.string.shortcuts_customize_search_targets)) },
        modifier = Modifier.fillMaxWidth(),
    )
    // The matcher runs on resolved strings — labels (and their category, so a
    // category word like "Services" finds every entry under it) — and a
    // category whose entries all miss drops out of the grouped list.
    val visibleByCategory = pickerByCategory.mapNotNull { (category, items) ->
        val categoryName = stringResource(category.displayNameRes)
        val visible = items.filter { item ->
            matchesPickerQuery(query, stringResource(item.entry.labelRes), categoryName)
        }
        if (visible.isEmpty()) null else category to visible
    }
    if (visibleByCategory.isEmpty()) {
        Text(
            text = stringResource(Res.string.shortcuts_customize_no_matches),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
        return
    }
    LazyColumn(modifier = modifier.fillMaxWidth().heightIn(max = 220.dp)) {
        visibleByCategory.forEach { (category, items) ->
            item(key = "cat:" + category.name, contentType = "categoryHeader") {
                Text(
                    text = stringResource(category.displayNameRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
            items(items, key = { it.entry.targetId }, contentType = { "pickerRow" }) { item ->
                val selected = item.entry.targetId == selectedTargetId && selectedKind != null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Tint is the selection face; the tinted icon alone read as
                        // hover noise, so the whole row carries the container color
                        // and a trailing check anchors it.
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        )
                        .clickable(enabled = !item.alreadyDefined, role = Role.Button) {
                            onSelect(
                                if (item.entry.isAction) ShortcutTargetKind.ACTION else ShortcutTargetKind.SCREEN,
                                item.entry.targetId,
                            )
                        }
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = item.entry.defaultIcon,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(item.entry.labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.alreadyDefined) {
                        Text(
                            text = stringResource(
                                if (item.entry.isAction) {
                                    Res.string.shortcuts_customize_action_badge
                                } else {
                                    Res.string.shortcuts_customize_screen_badge
                                },
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                    if (selected) {
                        Icon(
                            imageVector = Tabler.Outline.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One pin surface as a selectable card — the sheet's primary decision gets
 * the icon tile's selection grammar (primary border + secondary container)
 * instead of two stray checkboxes. Disabled = that surface is full and the
 * shortcut isn't on it yet; the sheet's capacity note below the pair
 * explains why.
 */
@Composable
private fun SurfacePinCard(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onChecked: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onChecked,
        ),
        shape = shape,
        color = if (checked) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = if (checked) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
        },
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp)
                    // A full surface the shortcut isn't pinned to yet reads dimmed;
                    // the note under the pair carries the explanation.
                    .alpha(if (enabled || checked) 1f else 0.5f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (checked) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun RowEditorAction(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(34.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
        )
    }
}
