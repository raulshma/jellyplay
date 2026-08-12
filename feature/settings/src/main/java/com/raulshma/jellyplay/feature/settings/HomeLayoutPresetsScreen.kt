package com.raulshma.jellyplay.feature.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.text.KeyboardOptions
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape
import com.raulshma.jellyplay.core.model.HomeLayoutConfig
import com.raulshma.jellyplay.core.model.HomeLayoutPreset
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.ui.feedback.LocalUserMessageBus
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.enableMarqueeOnFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.feedback.uiTextOf
import com.raulshma.jellyplay.core.ui.components.formatDate
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.model.DateFormatPreference
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.raulshma.jellyplay.feature.settings.R
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeLayoutPresetsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: LibraryLayoutViewModel = hiltViewModel(),
) {
    val presets = viewModel.homeLayoutPresets
    val isTv = LocalTvMode.current
    val backgroundColor = rememberScreenBackgroundColor()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val userMessageBus = LocalUserMessageBus.current

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(focusRequester = focusRequester, itemCount = 1, tag = "home_presets_init")

    var showSaveSheet by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<HomeLayoutPreset?>(null) }
    var resetConfirm by remember { mutableStateOf(false) }

    val shareSubject = stringResource(R.string.settings_home_layout_subject_plain)
    val shareChooser = stringResource(R.string.settings_share_home_layout)

    val scrollState = rememberLazyListState()

    JellyPlayScreenScaffold(
        title = stringResource(R.string.settings_home_layout_presets),
        onBack = onBack,
        backgroundColor = backgroundColor,
    ) { innerPadding ->
        // Center a highlighted (search-navigated) setting in the viewport instead of parking it
        // at the bottom edge, which is the default BringIntoViewSpec behaviour.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides
                com.raulshma.jellyplay.core.ui.tv.CenterBringIntoViewSpec
        ) {
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
            item {
                SettingsGroup(
                    icon = Tabler.Outline.Bookmarks,
                    title = stringResource(R.string.settings_layout_presets),
                    summary = {
                        if (presets.isEmpty()) stringResource(R.string.settings_no_saved_presets) else pluralStringResource(R.plurals.settings_saved_presets_count, presets.size, presets.size)
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    ActionRow(
                        icon = Tabler.Outline.DeviceFloppy,
                        title = stringResource(R.string.settings_save_current_layout),
                        subtitle = stringResource(R.string.settings_save_current_layout_subtitle),
                        index = 0,
                        count = 4,
                        onClick = { showSaveSheet = true },
                    )
                    ActionRow(
                        icon = Tabler.Outline.Download,
                        title = stringResource(R.string.settings_import_preset),
                        subtitle = stringResource(R.string.settings_import_preset_subtitle),
                        index = 1,
                        count = 4,
                        onClick = { showImportSheet = true },
                    )
                    ActionRow(
                        icon = Tabler.Outline.Share,
                        title = stringResource(R.string.settings_share_current_layout),
                        subtitle = stringResource(R.string.settings_share_current_layout_subtitle),
                        index = 2,
                        count = 4,
                        onClick = {
                            val json = viewModel.exportCurrentLayoutJson()
                            clipboard.setText(AnnotatedString(json))
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_SUBJECT, shareSubject)
                                putExtra(Intent.EXTRA_TEXT, json)
                            }
                            context.startActivity(Intent.createChooser(share, shareChooser))
                        },
                    )
                    ActionRow(
                        icon = Tabler.Outline.Refresh,
                        title = stringResource(R.string.settings_reset_to_default),
                        subtitle = stringResource(R.string.settings_reset_to_default_subtitle),
                        index = 3,
                        count = 4,
                        isDestructive = true,
                        onClick = { resetConfirm = true },
                    )
                }
            }

            if (presets.isNotEmpty()) {
                item {
                    SettingsGroup(
                        icon = Tabler.Outline.Bookmarks,
                        title = stringResource(R.string.settings_saved_presets),
                        summary = { stringResource(R.string.settings_saved_presets_summary) },
                        modifier = Modifier.padding(vertical = 8.dp),
                        initiallyExpanded = highlightSettingId == "preset_list",
                    ) {
                        val totalCount = presets.size
                        presets.forEachIndexed { index, preset ->
                            PresetRow(
                                preset = preset,
                                index = index,
                                count = totalCount,
                                onClick = { actionTarget = preset },
                            )
                        }
                    }
                }
            }
        }
        }
    }

    if (showSaveSheet) {
        SavePresetSheet(
            existingNames = presets.map { it.name }.toSet(),
            onDismiss = { showSaveSheet = false },
            onSave = { name ->
                viewModel.saveCurrentLayoutAsPreset(name)
                showSaveSheet = false
                userMessageBus.info(uiTextOf(R.string.settings_preset_saved, name))
            },
        )
    }

    if (showImportSheet) {
        ImportPresetSheet(
            error = viewModel.presetImportError,
            onDismiss = {
                showImportSheet = false
                viewModel.clearPresetImportError()
            },
            onImport = { raw ->
                viewModel.importPresetFromJson(raw) { result ->
                    result.onSuccess { (config, name) ->
                        viewModel.applyPreset(config)
                        if (!name.isNullOrBlank()) {
                            viewModel.saveCurrentLayoutAsPreset(name)
                        }
                        showImportSheet = false
                        viewModel.clearPresetImportError()
                        userMessageBus.info(uiTextOf(R.string.settings_preset_applied))
                    }
                }
            },
        )
    }

    actionTarget?.let { preset ->
        PresetActionSheet(
            preset = preset,
            onDismiss = { actionTarget = null },
            onLoad = {
                viewModel.applyPreset(preset.config)
                actionTarget = null
                userMessageBus.info(uiTextOf(R.string.settings_preset_applied_named, preset.name))
            },
            onShare = {
                val json = viewModel.exportPresetJson(preset)
                clipboard.setText(AnnotatedString(json))
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.settings_home_layout_subject, preset.name))
                    putExtra(Intent.EXTRA_TEXT, json)
                }
                context.startActivity(Intent.createChooser(share, context.getString(R.string.settings_share_preset, preset.name)))
            },
            onDelete = {
                viewModel.deleteHomeLayoutPreset(preset.id)
                actionTarget = null
                userMessageBus.info(uiTextOf(R.string.settings_preset_deleted, preset.name))
            },
        )
    }

    if (resetConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.settings_home_layout_reset_title),
            message = stringResource(R.string.settings_home_layout_reset_message),
            confirmText = stringResource(com.raulshma.jellyplay.core.ui.R.string.core_reset),
            onConfirm = {
                viewModel.resetHomeLayout()
                resetConfirm = false
                userMessageBus.info(uiTextOf(R.string.settings_home_layout_reset))
            },
            onDismiss = { resetConfirm = false },
            dismissText = stringResource(com.raulshma.jellyplay.core.ui.R.string.core_cancel),
            tone = ConfirmTone.NEUTRAL,
        )
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    index: Int,
    count: Int,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = expressiveListShape(index, count, innerRadius = 0.dp)
    val tvFocusState = rememberTvFocusState(focusedScale = 1.01f)
    val headlineColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    androidx.compose.material3.ListItem(
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = headlineColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.enableMarqueeOnFocus(focused = tvFocusState.isFocused),
            )
        },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        },
        trailingContent = {
            Icon(
                Tabler.Outline.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        },
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, shape)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun PresetRow(
    preset: HomeLayoutPreset,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    val shape = expressiveListShape(index, count, innerRadius = 0.dp)
    val tvFocusState = rememberTvFocusState(focusedScale = 1.01f)
    val dateLabel = remember(preset.createdAt) {
        runCatching { formatDate(preset.createdAt, DateFormatPreference.LONG) }
            .getOrDefault("")
    }
    androidx.compose.material3.ListItem(
        headlineContent = {
            Text(
                preset.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.enableMarqueeOnFocus(focused = tvFocusState.isFocused),
            )
        },
        supportingContent = {
            Text(
                stringResource(R.string.settings_preset_saved_date, dateLabel, preset.config.pinnedHomeSections.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Tabler.Outline.Bookmarks,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        trailingContent = {
            Icon(
                Tabler.Outline.DotsVertical,
                contentDescription = stringResource(R.string.settings_preset_actions_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        },
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, shape)
            .clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavePresetSheet(
    existingNames: Set<String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val isDuplicate = name.trim() in existingNames
    val canSave = name.isNotBlank() && !isDuplicate

    TvSafeSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(title = stringResource(R.string.settings_save_current_layout), icon = Tabler.Outline.DeviceFloppy)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.settings_preset_name)) },
                singleLine = true,
                isError = isDuplicate,
                supportingText = if (isDuplicate) {
                    { Text(stringResource(R.string.settings_preset_name_exists)) }
                } else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Button(
                    onClick = { onSave(name) },
                    enabled = canSave,
                    shape = ShapeCache.smoothPill,
                ) { Text(stringResource(R.string.settings_save)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportPresetSheet(
    error: String?,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var raw by remember { mutableStateOf("") }

    TvSafeSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(title = stringResource(R.string.settings_import_preset), icon = Tabler.Outline.Download)
            Text(
                stringResource(R.string.settings_import_paste_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = raw,
                onValueChange = { raw = it },
                label = { Text(stringResource(R.string.settings_preset_json)) },
                minLines = 4,
                maxLines = 8,
                isError = error != null,
                supportingText = if (error != null) {
                    { Text(error, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Button(
                    onClick = { onImport(raw) },
                    enabled = raw.isNotBlank(),
                    shape = ShapeCache.smoothPill,
                ) { Text(stringResource(R.string.settings_apply)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetActionSheet(
    preset: HomeLayoutPreset,
    onDismiss: () -> Unit,
    onLoad: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val actions = remember {
        listOf(
            Triple(Tabler.Outline.DeviceFloppy, "Load", "Apply this preset to your home"),
            Triple(Tabler.Outline.Share, "Share", "Copy / send the preset JSON"),
            Triple(Tabler.Outline.Trash, "Delete", "Remove this preset"),
        )
    }
    TvSafeSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(title = preset.name, icon = Tabler.Outline.DotsVertical)
            LazyColumn {
                items(actions.size, key = { actions[it].second }, contentType = { "preset_action" }) { index ->
                    val (icon, title, subtitle) = actions[index]
                    val isDestructive = title == "Delete"
                    val shape = expressiveListShape(index, actions.size, innerRadius = 0.dp)
                    val tvFocusState = rememberTvFocusState(focusedScale = 1.01f)
                    val headlineColor = if (isDestructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                            .then(tvFocusState.focusModifier)
                            .tvFocusIndicator(tvFocusState, shape)
                            .clickable {
                                when (title) {
                                    "Load" -> onLoad()
                                    "Share" -> onShare()
                                    "Delete" -> onDelete()
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (isDestructive) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                title,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = headlineColor,
                            )
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
