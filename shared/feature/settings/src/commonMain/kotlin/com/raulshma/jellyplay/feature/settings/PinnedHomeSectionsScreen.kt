package com.raulshma.jellyplay.feature.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import com.raulshma.jellyplay.core.model.PinnedSectionType
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.enableMarqueeOnFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_add_pinned_section
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_already_pinned_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_back
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_choose_x
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_couldnt_load_items
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_favorites
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_move_down_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_move_up_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_no_pinned_sections
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nothing_to_pin
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pin_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pinned_collection_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pinned_favorites_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pinned_genre_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pinned_helper
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pinned_home_sections
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pinned_playlist_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pinned_sections
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pinned_studio_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_remove_section_cd

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PinnedHomeSectionsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: LibraryLayoutViewModel = koinViewModel(),
) {
    val pinnedSections by viewModel.pinnedHomeSectionsFlow.collectAsStateWithLifecycle()
    val isTv = LocalTvMode.current
    val backgroundColorState = rememberScreenBackgroundColorState()
    val adaptiveInfo = LocalAdaptiveInfo.current

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = "pinned_home_init",
    )

    var showAddSheet by remember { mutableStateOf(false) }

    val scrollState = rememberLazyListState()

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.settings_pinned_home_sections),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
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
                    icon = Tabler.Outline.Pinned,
                    title = stringResource(Res.string.settings_pinned_sections),
                    summary = {
                        if (pinnedSections.isEmpty()) {
                            stringResource(Res.string.settings_no_pinned_sections)
                        } else {
                            val byType = pinnedSections.groupBy { it.type.displayName }
                            byType.entries.joinToString(", ") { (type, list) ->
                                "$type: ${list.size}"
                            }
                        }
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    if (pinnedSections.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Tabler.Outline.PinnedOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(Res.string.settings_pinned_helper),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        val totalCount = pinnedSections.size
                        pinnedSections.forEachIndexed { index, section ->
                            PinnedSectionRow(
                                section = section,
                                position = index + 1,
                                index = index,
                                count = totalCount,
                                canMoveUp = index > 0,
                                canMoveDown = index < totalCount - 1,
                                onMoveUp = { viewModel.movePinnedHomeSection(index, index - 1) },
                                onMoveDown = { viewModel.movePinnedHomeSection(index, index + 1) },
                                onRemove = { viewModel.removePinnedHomeSection(section.id) },
                            )
                        }
                    }

                    AddPinnedSectionRow(
                        index = pinnedSections.size,
                        count = pinnedSections.size + 1,
                        highlighted = highlightSettingId == "pinned_add",
                        onClick = { showAddSheet = true },
                    )
                }
            }
        }
        }
    }

    if (showAddSheet) {
        AddPinnedSectionSheet(
            viewModel = viewModel,
            alreadyPinnedIds = pinnedSections.map { it.id }.toSet(),
            onDismiss = {
                showAddSheet = false
                viewModel.clearPinnedBrowse()
            },
            onPinned = {
                showAddSheet = false
                viewModel.clearPinnedBrowse()
            },
        )
    }
}

@Composable
private fun PinnedSectionRow(
    section: PinnedHomeSection,
    position: Int,
    index: Int,
    count: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = expressiveListShape(index, count, innerRadius = 0.dp)
    val tvFocusState = rememberTvFocusState(focusedScale = 1.01f)

    androidx.compose.material3.ListItem(
        headlineContent = {
            Text(
                text = section.title,
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
                text = "${section.type.displayName} • Position $position",
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
                Text(
                    text = position.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReorderIconButton(
                    icon = Tabler.Outline.ChevronUp,
                    contentDescription = stringResource(Res.string.settings_move_up_cd),
                    enabled = canMoveUp,
                    onClick = onMoveUp,
                )
                ReorderIconButton(
                    icon = Tabler.Outline.ChevronDown,
                    contentDescription = stringResource(Res.string.settings_move_down_cd),
                    enabled = canMoveDown,
                    onClick = onMoveDown,
                )
                ReorderIconButton(
                    icon = Tabler.Outline.X,
                    contentDescription = stringResource(Res.string.settings_remove_section_cd, section.title),
                    enabled = true,
                    onClick = onRemove,
                    isDestructive = true,
                )
            }
        },
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, shape),
    )
}

@Composable
private fun ReorderIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
) {
    val tvFocusState = rememberTvFocusState(focusedScale = 1.12f)
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        isDestructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(ShapeCache.smooth8)
            .then(if (enabled) tvFocusState.focusModifier else Modifier)
            .then(if (enabled) Modifier.tvFocusIndicator(tvFocusState, ShapeCache.smooth8) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddPinnedSectionRow(
    index: Int,
    count: Int,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val shape = expressiveListShape(index, count, innerRadius = 0.dp)
    val tvFocusState = rememberTvFocusState(focusedScale = 1.01f)
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val primaryColor = MaterialTheme.colorScheme.primary
    val highlightColor = remember { androidx.compose.animation.Animatable(Color.Transparent) }
    val highlightFadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()

    LaunchedEffect(highlighted) {
        if (highlighted) {
            highlightColor.snapTo(primaryColor.copy(alpha = 0.25f))
            highlightColor.animateTo(
                targetValue = Color.Transparent,
                animationSpec = highlightFadeSpec,
            )
        }
    }
    LaunchedEffect(highlighted) {
        if (highlighted) {
            // Wait for the parent group's expand animation to settle, then center the row.
            kotlinx.coroutines.delay(400)
            focusRequester.tryRequestFocus("pinned_add")
            bringIntoViewRequester.bringIntoView()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(highlightColor.value)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
            .focusRequester(focusRequester)
            .bringIntoViewRequester(bringIntoViewRequester)
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Tabler.Outline.Plus,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = stringResource(Res.string.settings_add_pinned_section),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPinnedSectionSheet(
    viewModel: LibraryLayoutViewModel,
    alreadyPinnedIds: Set<String>,
    onDismiss: () -> Unit,
    onPinned: () -> Unit,
) {
    var selectedType by remember { mutableStateOf<PinnedSectionType?>(null) }

    TvSafeSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(
                title = if (selectedType == null) stringResource(Res.string.settings_add_pinned_section) else stringResource(Res.string.settings_choose_x, selectedType!!.displayName),
                icon = Tabler.Outline.Pin,
            )

            val currentType = selectedType
            val favoritesTitle = stringResource(Res.string.settings_favorites)
            if (currentType == null) {
                PinnedTypePicker(onSelect = { type ->
                    if (type == PinnedSectionType.FAVORITES) {
                        viewModel.addPinnedHomeSection(
                            PinnedHomeSection(
                                type = type,
                                sourceId = PinnedHomeSection.FAVORITES_SOURCE_ID,
                                title = favoritesTitle,
                            )
                        )
                        onPinned()
                    } else {
                        selectedType = type
                        viewModel.loadPinnableOptions(type)
                    }
                })
            } else {
                PinnedOptionBrowser(
                    viewModel = viewModel,
                    alreadyPinnedIds = alreadyPinnedIds,
                    onBack = {
                        selectedType = null
                        viewModel.clearPinnedBrowse()
                    },
                    onSelect = { option ->
                        viewModel.addPinnedHomeSection(
                            PinnedHomeSection(
                                type = currentType,
                                sourceId = option.sourceId,
                                title = option.title,
                            )
                        )
                        onPinned()
                    },
                )
            }
        }
    }
}

@Composable
private fun PinnedTypePicker(onSelect: (PinnedSectionType) -> Unit) {
    val types = PinnedSectionType.entries
    val totalCount = types.size
    LazyColumn {
        items(types.size, key = { types[it].name }, contentType = { "pinned_type" }) { index ->
            val type = types[index]
            val shape = expressiveListShape(index, totalCount, innerRadius = 0.dp)
            val tvFocusState = rememberTvFocusState(focusedScale = 1.01f)
            val iconAndSubtitle: Pair<ImageVector, String> = when (type) {
                PinnedSectionType.COLLECTION -> Tabler.Outline.Folders to stringResource(Res.string.settings_pinned_collection_desc)
                PinnedSectionType.PLAYLIST -> Tabler.Outline.List to stringResource(Res.string.settings_pinned_playlist_desc)
                PinnedSectionType.FAVORITES -> Tabler.Outline.Heart to stringResource(Res.string.settings_pinned_favorites_desc)
                PinnedSectionType.GENRE -> Tabler.Outline.Category to stringResource(Res.string.settings_pinned_genre_desc)
                PinnedSectionType.STUDIO -> Tabler.Outline.Building to stringResource(Res.string.settings_pinned_studio_desc)
            }
            val icon = iconAndSubtitle.first
            val subtitle = iconAndSubtitle.second
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                    .then(tvFocusState.focusModifier)
                    .tvFocusIndicator(tvFocusState, shape)
                    .clickable { onSelect(type) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        type.displayName,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
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

@Composable
private fun PinnedOptionBrowser(
    viewModel: LibraryLayoutViewModel,
    alreadyPinnedIds: Set<String>,
    onBack: () -> Unit,
    onSelect: (PinnableOption) -> Unit,
) {
    val options = viewModel.pinnedBrowseOptions
    val loading = viewModel.pinnedBrowseLoading
    val error = viewModel.pinnedBrowseError

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 4.dp),
    ) {
        TvSafeTextButton(onClick = onBack) {
            Icon(Tabler.Outline.ArrowLeft, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.settings_back))
        }
    }

    when {
        loading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator()
            }
        }
        error != null -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(Res.string.settings_couldnt_load_items),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        options.isEmpty() -> {
            Text(
                stringResource(Res.string.settings_nothing_to_pin),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                textAlign = TextAlign.Center,
            )
        }
        else -> {
            val totalCount = options.size
            LazyColumn {
                items(options.size, key = { options[it].sourceId }, contentType = { "pinned_option" }) { index ->
                    val option = options[index]
                    val isPinned = option.sourceId in alreadyPinnedIds
                    val shape = expressiveListShape(index, totalCount, innerRadius = 0.dp)
                    val tvFocusState = rememberTvFocusState(focusedScale = 1.01f)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .clip(shape)
                            .background(
                                if (isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                            )
                            .then(tvFocusState.focusModifier)
                            .tvFocusIndicator(tvFocusState, shape)
                            .clickable(enabled = !isPinned) { onSelect(option) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                option.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isPinned) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isPinned) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            option.subtitle?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (isPinned) {
                            Icon(
                                Tabler.Outline.Check,
                                contentDescription = stringResource(Res.string.settings_already_pinned_cd),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Icon(
                                Tabler.Outline.Plus,
                                contentDescription = stringResource(Res.string.settings_pin_cd),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Small D-pad-safe text button used inside the picker sheet. */
@Composable
private fun TvSafeTextButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    val isTv = LocalTvMode.current
    val tvFocusState = rememberTvFocusState(focusedScale = 1.05f)
    Row(
        modifier = Modifier
            .clip(ShapeCache.smoothPill)
            .then(tvFocusState.focusModifier)
            .then(Modifier.tvFocusIndicator(tvFocusState, ShapeCache.smoothPill))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}
