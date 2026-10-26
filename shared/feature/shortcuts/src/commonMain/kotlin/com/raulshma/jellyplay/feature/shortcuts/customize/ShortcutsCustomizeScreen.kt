package com.raulshma.jellyplay.feature.shortcuts.customize

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.ChevronUp
import com.composables.icons.tabler.outline.DotsVertical
import com.composables.icons.tabler.outline.Pencil
import com.composables.icons.tabler.outline.Pin
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Restore
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.model.ShortcutDefinition
import com.raulshma.jellyplay.core.model.ShortcutTargetKind
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ShortcutIconGridPicker
import com.raulshma.jellyplay.core.ui.icons.ShortcutIconRegistry
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.navigation.ShortcutCatalogTarget
import com.raulshma.jellyplay.feature.shortcuts.ShortcutCategory
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.Res
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_action_badge
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_add
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_edit
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_header_capacity
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_overflow_capacity
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_header_section
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_label_hint
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_move_down
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_move_up
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_overflow_capacity
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_overflow_section
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_pick_icon
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_pick_target
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_pin_header
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_pin_overflow
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_remove
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_reset
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_reset_confirm_message
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_reset_confirm_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_save
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_screen_badge
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_unpinned_section
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_customize_use_default_icon
import org.jetbrains.compose.resources.stringResource

/**
 * The shortcuts-customization editor: the pinned Header / Overflow lists
 * (reorder via the up/down commands — TV-first, dpad-reachable), the unpinned
 * definitions, add/edit through one [TvSafeSheet] (target picker → icon grid
 * → label override → surface pins), and reset-to-defaults. Every command is a
 * [ShortcutsCustomizeViewModel] one-liner over the store's atomic write.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ShortcutsCustomizeScreen(
    onBack: () -> Unit,
    viewModel: ShortcutsCustomizeViewModel = org.koin.compose.viewmodel.koinViewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.pruneUnknownTargets() }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
        val headerTitle = stringResource(Res.string.shortcuts_customize_header_section)
        val headerCapacity = stringResource(
            Res.string.shortcuts_customize_header_capacity,
            state.sections.headerRows.size,
            state.sections.headerCapacity,
        )
        val overflowTitle = stringResource(Res.string.shortcuts_customize_overflow_section)
        val overflowCapacity = stringResource(
            Res.string.shortcuts_customize_overflow_capacity,
            state.sections.overflowRows.size,
            state.sections.overflowCapacity,
        )
        val unpinnedTitle = stringResource(Res.string.shortcuts_customize_unpinned_section)
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            sectionHeader(title = headerTitle, capacity = headerCapacity)
            items(state.sections.headerRows, key = { "h:" + it.definition.id }) { row ->
                EditorListRow(
                    row = row,
                    surfacePinned = true,
                    onMoveUp = { viewModel.move(row.definition.id, com.raulshma.jellyplay.core.model.ShortcutSurface.HEADER, up = true) },
                    onMoveDown = { viewModel.move(row.definition.id, com.raulshma.jellyplay.core.model.ShortcutSurface.HEADER, up = false) },
                    onEdit = { draft = EditorDraft.of(row.definition, headerPinned = true, overflowPinned = row.pinnedOverflow) },
                    onRemove = { viewModel.remove(row.definition.id) },
                )
            }

            sectionHeader(title = overflowTitle, capacity = overflowCapacity)
            items(state.sections.overflowRows, key = { "o:" + it.definition.id }) { row ->
                EditorListRow(
                    row = row,
                    surfacePinned = true,
                    onMoveUp = { viewModel.move(row.definition.id, com.raulshma.jellyplay.core.model.ShortcutSurface.OVERFLOW, up = true) },
                    onMoveDown = { viewModel.move(row.definition.id, com.raulshma.jellyplay.core.model.ShortcutSurface.OVERFLOW, up = false) },
                    onEdit = { draft = EditorDraft.of(row.definition, headerPinned = row.pinnedHeader, overflowPinned = true) },
                    onRemove = { viewModel.remove(row.definition.id) },
                )
            }

            sectionHeader(title = unpinnedTitle, capacity = null)
            items(state.sections.unpinnedRows, key = { "u:" + it.definition.id }) { row ->
                EditorListRow(
                    row = row,
                    surfacePinned = false,
                    pinHeaderEnabled = !state.sections.headerFull,
                    pinOverflowEnabled = !state.sections.overflowFull,
                    onPinHeader = {
                        viewModel.setSurfaces(row.definition.id, header = true, overflow = row.pinnedOverflow)
                    },
                    onPinOverflow = {
                        viewModel.setSurfaces(row.definition.id, header = row.pinnedHeader, overflow = true)
                    },
                    onEdit = { draft = EditorDraft.of(row.definition, headerPinned = false, overflowPinned = false) },
                    onRemove = { viewModel.remove(row.definition.id) },
                )
            }

            item(key = "add") {
                Button(
                    onClick = { draft = EditorDraft.new() },
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Icon(Tabler.Outline.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.shortcuts_customize_add))
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
            onConfirm = {
                viewModel.resetAll()
                confirmReset = false
            },
            onDismiss = { confirmReset = false },
        )
    }
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
    onDismiss: () -> Unit,
    onSave: (EditorDraft) -> Unit,
) {
    var current by remember(draft) { mutableStateOf(draft) }

    TvSafeSheet(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.shortcuts_customize_title),
    ) {
        if (draft.originalId == null) {
            Text(
                text = stringResource(Res.string.shortcuts_customize_pick_target),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.size(8.dp))
            ShortcutTargetPicker(
                pickerByCategory = pickerByCategory,
                selectedKind = current.kind,
                selectedTargetId = current.targetId,
                onSelect = { kind, targetId -> current = current.copy(kind = kind, targetId = targetId) },
            )
            Spacer(Modifier.size(16.dp))
        }

        Text(
            text = stringResource(Res.string.shortcuts_customize_pick_icon),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.size(8.dp))
        ShortcutIconGridPicker(
            selectedIconId = current.iconId,
            onIconSelected = { id -> current = current.copy(iconId = id) },
        )
        if (current.iconId.isNotEmpty()) {
            TextButton(onClick = { current = current.copy(iconId = "") }) {
                Text(stringResource(Res.string.shortcuts_customize_use_default_icon))
            }
        }

        Spacer(Modifier.size(8.dp))
        OutlinedTextField(
            value = current.labelOverride,
            onValueChange = { current = current.copy(labelOverride = it) },
            label = { Text(stringResource(Res.string.shortcuts_customize_label_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.size(8.dp))
        // The capacity gates the store's write clamp enforces — pinned rows
        // don't consume extra slots, so a checked toggle always stays
        // enabled (unchecking must never lock); checking needs a free slot.
        SurfacePinToggle(
            label = stringResource(Res.string.shortcuts_customize_pin_header),
            icon = Tabler.Outline.Pin,
            checked = current.pinHeader,
            enabled = current.pinHeader || !headerFull,
            onChecked = { current = current.copy(pinHeader = it) },
        )
        SurfacePinToggle(
            label = stringResource(Res.string.shortcuts_customize_pin_overflow),
            icon = Tabler.Outline.DotsVertical,
            checked = current.pinOverflow,
            enabled = current.pinOverflow || !overflowFull,
            onChecked = { current = current.copy(pinOverflow = it) },
        )

        Spacer(Modifier.size(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = { onSave(current) },
                enabled = current.kind != null && current.targetId != null,
            ) {
                Text(stringResource(Res.string.shortcuts_customize_save))
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
    LazyColumn(modifier = modifier.fillMaxWidth().heightIn(max = 220.dp)) {
        pickerByCategory.forEach { (category, items) ->
            item(key = "cat:" + category.name) {
                Text(
                    text = stringResource(category.displayNameRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
            items(items, key = { it.entry.targetId }) { item ->
                val selected = item.entry.targetId == selectedTargetId && selectedKind != null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !item.alreadyDefined, role = Role.Button) {
                            onSelect(
                                if (item.entry.isAction) ShortcutTargetKind.ACTION else ShortcutTargetKind.SCREEN,
                                item.entry.targetId,
                            )
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = item.entry.defaultIcon,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(item.entry.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (item.alreadyDefined) {
                        Text(
                            text = stringResource(Res.string.shortcuts_customize_screen_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SurfacePinToggle(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean,
    onChecked: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Checkbox) { onChecked(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = if (enabled) { { onChecked(it) } } else null)
    }
}

// ─── List sections ─────────────────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.sectionHeader(title: String, capacity: String?) {
    item(key = "header:$title") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            capacity?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun EditorListRow(
    row: ShortcutsCustomizePolicy.EditorRow,
    surfacePinned: Boolean,
    pinHeaderEnabled: Boolean = true,
    pinOverflowEnabled: Boolean = true,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onPinHeader: (() -> Unit)? = null,
    onPinOverflow: (() -> Unit)? = null,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val entry = row.entry
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                // The icon the surfaces render: the user's picked icon when it
                // resolves, else the catalog default — the row never lies
                // about the pin's appearance.
                imageVector = ShortcutIconRegistry.resolve(row.definition.iconId)
                    ?: entry?.defaultIcon
                    ?: Tabler.Outline.Trash,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.definition.labelOverride
                        ?: entry?.let { stringResource(it.labelRes) }
                        ?: row.definition.targetId,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        entry == null -> row.definition.targetId
                        entry.isAction -> stringResource(Res.string.shortcuts_customize_action_badge)
                        else -> stringResource(Res.string.shortcuts_customize_screen_badge)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (surfacePinned) {
                RowEditorAction(Tabler.Outline.ChevronUp, Res.string.shortcuts_customize_move_up, enabled = onMoveUp != null, onClick = { onMoveUp?.invoke() })
                RowEditorAction(Tabler.Outline.ChevronDown, Res.string.shortcuts_customize_move_down, enabled = onMoveDown != null, onClick = { onMoveDown?.invoke() })
            } else {
                RowEditorAction(Tabler.Outline.Pin, Res.string.shortcuts_customize_pin_header, enabled = pinHeaderEnabled, onClick = { onPinHeader?.invoke() })
                RowEditorAction(Tabler.Outline.DotsVertical, Res.string.shortcuts_customize_pin_overflow, enabled = pinOverflowEnabled, onClick = { onPinOverflow?.invoke() })
            }
            RowEditorAction(Tabler.Outline.Pencil, Res.string.shortcuts_customize_edit, onClick = onEdit)
            RowEditorAction(Tabler.Outline.Trash, Res.string.shortcuts_customize_remove, onClick = onRemove)
        }
    }
}

@Composable
private fun RowEditorAction(
    icon: ImageVector,
    contentDescriptionRes: org.jetbrains.compose.resources.StringResource,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(34.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(contentDescriptionRes),
            modifier = Modifier.size(18.dp),
        )
    }
}
