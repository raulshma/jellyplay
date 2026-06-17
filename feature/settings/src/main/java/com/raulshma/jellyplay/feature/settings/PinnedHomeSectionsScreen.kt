package com.raulshma.jellyplay.feature.settings

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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.enableMarqueeOnFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinnedHomeSectionsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences = viewModel.preferences
    val pinnedSections = preferences.pinnedHomeSections
    val isTv = LocalTvMode.current
    val backgroundColor = rememberScreenBackgroundColor()
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
        title = "Pinned Home Sections",
        onBack = onBack,
        backgroundColor = backgroundColor,
    ) { innerPadding ->
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
                    title = "Pinned Sections",
                    summary = {
                        if (pinnedSections.isEmpty()) {
                            "No pinned sections yet"
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Pin collections, playlists, favorites, genres or studios to your home screen for quick access.",
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
                    contentDescription = "Move up",
                    enabled = canMoveUp,
                    onClick = onMoveUp,
                )
                ReorderIconButton(
                    icon = Tabler.Outline.ChevronDown,
                    contentDescription = "Move down",
                    enabled = canMoveDown,
                    onClick = onMoveDown,
                )
                ReorderIconButton(
                    icon = Tabler.Outline.X,
                    contentDescription = "Remove ${section.title}",
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
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
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
    val primaryColor = MaterialTheme.colorScheme.primary
    val highlightColor = remember { androidx.compose.animation.Animatable(Color.Transparent) }

    LaunchedEffect(highlighted) {
        if (highlighted) {
            highlightColor.snapTo(primaryColor.copy(alpha = 0.25f))
            highlightColor.animateTo(
                targetValue = Color.Transparent,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 1500),
            )
        }
    }
    LaunchedEffect(highlighted) {
        if (highlighted) focusRequester.tryRequestFocus("pinned_add")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(highlightColor.value)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
            .focusRequester(focusRequester)
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
            text = "Add Pinned Section",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPinnedSectionSheet(
    viewModel: SettingsViewModel,
    alreadyPinnedIds: Set<String>,
    onDismiss: () -> Unit,
    onPinned: () -> Unit,
) {
    var selectedType by remember { mutableStateOf<PinnedSectionType?>(null) }

    AdaptiveSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            Text(
                if (selectedType == null) "Add Pinned Section" else "Choose ${selectedType!!.displayName}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(12.dp))

            val currentType = selectedType
            if (currentType == null) {
                PinnedTypePicker(onSelect = { type ->
                    if (type == PinnedSectionType.FAVORITES) {
                        viewModel.addPinnedHomeSection(
                            PinnedHomeSection(
                                type = type,
                                sourceId = PinnedHomeSection.FAVORITES_SOURCE_ID,
                                title = "Favorites",
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
                PinnedSectionType.COLLECTION -> Tabler.Outline.Folders to "A curated box-set of items"
                PinnedSectionType.PLAYLIST -> Tabler.Outline.List to "A saved playback playlist"
                PinnedSectionType.FAVORITES -> Tabler.Outline.Heart to "Everything you have favorited"
                PinnedSectionType.GENRE -> Tabler.Outline.Category to "Items matching a genre"
                PinnedSectionType.STUDIO -> Tabler.Outline.Building to "Items from a studio"
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
    viewModel: SettingsViewModel,
    alreadyPinnedIds: Set<String>,
    onBack: () -> Unit,
    onSelect: (SettingsViewModel.PinnableOption) -> Unit,
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
            Text("Back")
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
                androidx.compose.material3.CircularProgressIndicator()
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
                    "Couldn't load items",
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
                "Nothing available to pin.",
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
                                contentDescription = "Already pinned",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Icon(
                                Tabler.Outline.Plus,
                                contentDescription = "Pin",
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
            .then(if (isTv) tvFocusState.focusModifier else Modifier)
            .then(if (isTv) Modifier.tvFocusIndicator(tvFocusState, ShapeCache.smoothPill) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}
