package com.raulshma.jellyplay.feature.shortcuts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Apps
import com.composables.icons.tabler.outline.ChevronRight
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.LayoutGrid
import com.composables.icons.tabler.outline.Puzzle
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.Settings
import com.composables.icons.tabler.outline.Shield
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.groupedItemContainerColor
import com.raulshma.jellyplay.core.designsystem.theme.hairlineBorderColor
import com.raulshma.jellyplay.core.designsystem.theme.lightModeHairlineBorder
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.adaptive.settingsColumns
import com.raulshma.jellyplay.core.ui.animation.pressScaleValue
import com.raulshma.jellyplay.core.ui.components.ExpressiveChipContainer
import com.raulshma.jellyplay.core.ui.components.JellyPlayBackHandler
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.TopBarStyle
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.core_cancel
import com.raulshma.jellyplay.core.ui.generated.resources.core_search
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.Res
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_admin_badge
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_empty_action
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_empty_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_empty_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_filter_all
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_screen_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_search_clear
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_search_no_results
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_search_placeholder
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

// ─── Filter Model ──────────────────────────────────────────────────────────

private sealed interface ShortcutFilter {
    val key: String

    data object All : ShortcutFilter {
        override val key: String = "all"
    }
    data class Category(val category: ShortcutCategory) : ShortcutFilter {
        override val key: String get() = category.name
    }
}

@Composable
private fun ShortcutFilter.label(): String = when (this) {
    ShortcutFilter.All -> stringResource(Res.string.shortcuts_filter_all)
    is ShortcutFilter.Category -> stringResource(category.displayNameRes)
}

private val ShortcutFilter.icon: ImageVector
    get() = when (this) {
        ShortcutFilter.All -> Tabler.Outline.LayoutGrid
        is ShortcutFilter.Category -> when (category) {
            ShortcutCategory.LIBRARY -> Tabler.Outline.Download
            ShortcutCategory.SERVICES -> Tabler.Outline.Puzzle
            ShortcutCategory.SYSTEM -> Tabler.Outline.Settings
        }
    }

// ─── Main Screen ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShortcutsScreen(
    onBack: () -> Unit,
    onNavigate: (Route) -> Unit,
    viewModel: ShortcutsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isTv = LocalTvMode.current
    val adaptiveInfo = LocalAdaptiveInfo.current
    val backgroundColorState = rememberScreenBackgroundColorState()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }

    val firstShortcutFocusRequester = remember { FocusRequester() }
    val firstShortcutRoute = remember(state.categories) {
        state.categories.values.firstOrNull { it.isNotEmpty() }?.firstOrNull()?.route
    }

    LaunchedEffect(isTv, firstShortcutRoute) {
        if (isTv && firstShortcutRoute != null && !isSearchActive) {
            delay(180)
            firstShortcutFocusRequester.tryRequestFocus("first_shortcut")
        }
    }

    val filters = remember(state.categories) {
        buildList {
            add(ShortcutFilter.All)
            state.categories.keys.forEach { add(ShortcutFilter.Category(it)) }
        }
    }
    var activeFilter by remember(filters) { mutableStateOf<ShortcutFilter>(ShortcutFilter.All) }

    // Resolve displayed shortcuts based on active filter and search query
    val allItems = remember(state.categories) {
        state.categories.values.flatten()
    }

    // Search matching needs the resolved title/description labels, which only
    // the suspend compose-resources resolver can produce outside composition
    // (syncplay flow-escape lesson). The map is keyed per item and recomputed
    // whenever the item list changes. Documented delta (livetv 🟢 class): HEAD
    // re-resolved via Context.getString on every keystroke, so a mid-session
    // locale switch applied from the next keystroke on; this resolve-once-per-
    // items map stays stale in that corner until the screen is re-entered —
    // the same accepted-staleness class. Same trigger class when allItems
    // changes mid-search (admin toggle): brand-new items fall through the
    // `?: ("" to "")` below for the one producer window before the map
    // re-lands, so they miss the active filter for that frame (HEAD
    // re-resolved per keystroke and never excluded them). Self-healing.
    val labels by produceState(
        initialValue = emptyMap<ShortcutItem, Pair<String, String>>(),
        allItems,
    ) {
        value = allItems.associate { item ->
            item to (getString(item.titleRes) to getString(item.descriptionRes))
        }
    }

    val filteredItemsByQuery = remember(labels, searchQuery) {
        if (searchQuery.isBlank()) null
        // Guard: while the label map hasn't resolved yet a restored non-blank
        // query (rememberSaveable survives process restore) would match zero
        // labels and flash a false "No results" state — treat it as unfiltered
        // until the produceState block above lands its first value.
        else if (labels.isEmpty()) null
        else {
            val q = searchQuery.trim().lowercase()
            allItems.filter { item ->
                val (title, desc) = labels[item] ?: ("" to "")
                title.lowercase().contains(q) || desc.lowercase().contains(q)
            }
        }
    }

    val displayedCategories = remember(state.categories, activeFilter, filteredItemsByQuery) {
        if (filteredItemsByQuery != null) {
            // When search is active, group filtered items by category
            filteredItemsByQuery
                .filter { item ->
                    when (val f = activeFilter) {
                        ShortcutFilter.All -> true
                        is ShortcutFilter.Category -> item.category == f.category
                    }
                }
                .groupBy { it.category }
        } else {
            when (val f = activeFilter) {
                ShortcutFilter.All -> state.categories
                is ShortcutFilter.Category -> state.categories.filterKeys { it == f.category }
            }
        }
    }

    JellyPlayBackHandler(enabled = isSearchActive || searchQuery.isNotEmpty() || activeFilter != ShortcutFilter.All) {
        when {
            searchQuery.isNotEmpty() -> searchQuery = ""
            isSearchActive -> isSearchActive = false
            activeFilter != ShortcutFilter.All -> activeFilter = ShortcutFilter.All
        }
    }

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.shortcuts_screen_title),
        onBack = onBack,
        topBarStyle = TopBarStyle.Standard,
        backgroundColorState = backgroundColorState,
        actions = {
            val searchActionFocus = rememberTvFocusState()
            IconButton(
                onClick = {
                    isSearchActive = !isSearchActive
                    if (!isSearchActive) searchQuery = ""
                },
                modifier = Modifier
                    .then(searchActionFocus.focusModifier)
                    .tvFocusIndicator(searchActionFocus, CircleShape),
            ) {
                Icon(
                    imageVector = if (isSearchActive) Tabler.Outline.X else Tabler.Outline.Search,
                    contentDescription = stringResource(
                        if (isSearchActive) CoreUiRes.string.core_cancel
                        else CoreUiRes.string.core_search
                    ),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    ) { paddingValues ->
        val horizontalPadding = adaptiveInfo.contentPadding(isTv)
        val itemSpacing = adaptiveInfo.itemSpacing(isTv)
        val bottomPadding = adaptiveInfo.bottomPadding(isTv) + paddingValues.calculateBottomPadding()
        val numColumns = adaptiveInfo.settingsColumns()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .tvFocusRestorer(),
            contentPadding = PaddingValues(bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            // Search Input Row (animated visibility)
            item(key = "search_row") {
                AnimatedVisibility(
                    visible = isSearchActive,
                    enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) + slideInVertically(),
                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + slideOutVertically(),
                ) {
                    ShortcutSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onClose = {
                            isSearchActive = false
                            searchQuery = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding, vertical = 6.dp),
                    )
                }
            }

            // Category Filter Chips
            item(key = "filter_row") {
                AnimatedVisibility(visible = filters.size > 1) {
                    ShortcutFilterRow(
                        filters = filters,
                        activeFilter = activeFilter,
                        categories = state.categories,
                        onFilterSelected = { activeFilter = it },
                        horizontalPadding = horizontalPadding,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                }
            }

            // Empty State
            if (displayedCategories.isEmpty() || displayedCategories.values.all { it.isEmpty() }) {
                item(key = "empty_state") {
                    if (searchQuery.isNotBlank()) {
                        ScreenEmptyState(
                            icon = Tabler.Outline.Search,
                            title = stringResource(Res.string.shortcuts_search_no_results, searchQuery),
                            description = stringResource(Res.string.shortcuts_empty_description),
                            actionLabel = stringResource(Res.string.shortcuts_search_clear),
                            onAction = { searchQuery = "" },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                        )
                    } else {
                        ScreenEmptyState(
                            icon = Tabler.Outline.Apps,
                            title = stringResource(Res.string.shortcuts_empty_title),
                            description = stringResource(Res.string.shortcuts_empty_description),
                            actionLabel = stringResource(Res.string.shortcuts_empty_action),
                            onAction = { onNavigate(Route.Library) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                        )
                    }
                }
            } else {
                displayedCategories.forEach { (category, shortcuts) ->
                    if (shortcuts.isNotEmpty()) {
                        // Section Header
                        item(key = "section_header_${category.name}") {
                            ShortcutSectionHeader(
                                title = stringResource(category.displayNameRes),
                                count = shortcuts.size,
                                modifier = Modifier
                                    .padding(horizontal = horizontalPadding)
                                    .padding(top = 16.dp, bottom = 4.dp),
                            )
                        }

                        if (numColumns > 1) {
                            val chunks = shortcuts.chunked(numColumns)
                            itemsIndexed(chunks, key = { index, _ -> "chunk_${category.name}_$index" }) { _, chunk ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = horizontalPadding),
                                    horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                                ) {
                                    chunk.forEach { item ->
                                        val isFirst = item.route == firstShortcutRoute
                                        ShortcutCard(
                                            item = item,
                                            onClick = { onNavigate(item.route) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .then(
                                                    if (isFirst) Modifier.focusRequester(firstShortcutFocusRequester)
                                                    else Modifier
                                                ),
                                        )
                                    }
                                    if (chunk.size < numColumns) {
                                        repeat(numColumns - chunk.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        } else {
                            items(shortcuts, key = { "${category.name}_${it.route}" }) { item ->
                                val isFirst = item.route == firstShortcutRoute
                                ShortcutCard(
                                    item = item,
                                    onClick = { onNavigate(item.route) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = horizontalPadding)
                                        .then(
                                            if (isFirst) Modifier.focusRequester(firstShortcutFocusRequester)
                                            else Modifier
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Filter Row ────────────────────────────────────────────────────────────

@Composable
private fun ShortcutFilterRow(
    filters: List<ShortcutFilter>,
    activeFilter: ShortcutFilter,
    categories: Map<ShortcutCategory, List<ShortcutItem>>,
    onFilterSelected: (ShortcutFilter) -> Unit,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val isLight = LocalIsLightTheme.current
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filters, key = { it.key }) { filter ->
            val isSelected = activeFilter == filter
            val count = when (filter) {
                ShortcutFilter.All -> categories.values.sumOf { it.size }
                is ShortcutFilter.Category -> categories[filter.category]?.size ?: 0
            }

            val containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.10f)
            }
            val contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            }

            ExpressiveChipContainer(
                onClick = { onFilterSelected(filter) },
                containerColor = containerColor,
                forceActive = isSelected,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = filter.icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = filter.label(),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
                if (count > 0) {
                    Box(
                        modifier = Modifier
                            .clip(ShapeCache.smoothPill)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor,
                        )
                    }
                }
            }
        }
    }
}

// ─── Search Bar ────────────────────────────────────────────────────────────

@Composable
private fun ShortcutSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val focusState = rememberTvFocusState()
    LaunchedEffect(Unit) {
        delay(120)
        focusRequester.tryRequestFocus("shortcut_search")
    }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth16),
        placeholder = {
            Text(
                text = stringResource(Res.string.shortcuts_search_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Tabler.Outline.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Tabler.Outline.X,
                        contentDescription = stringResource(Res.string.shortcuts_search_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Tabler.Outline.X,
                        contentDescription = stringResource(CoreUiRes.string.core_cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        singleLine = true,
        shape = ShapeCache.smooth16,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = groupedItemContainerColor(),
            unfocusedContainerColor = groupedItemContainerColor(),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = hairlineBorderColor(),
        ),
    )
}

// ─── Section Header ────────────────────────────────────────────────────────

@Composable
private fun ShortcutSectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Surface(
            shape = ShapeCache.smoothPill,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
    }
}

// ─── Shortcut Card ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShortcutCard(
    item: ShortcutItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTv = LocalTvMode.current
    val focusState = rememberTvFocusState(focusedScale = 1.025f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val shape = if (isTv) ShapeCache.smooth20 else ShapeCache.smooth16

    val scale by animateFloatAsState(
        targetValue = pressScaleValue(isPressed, 0.97f),
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "shortcutCardScale",
    )

    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale * focusState.scale
                scaleY = scale * focusState.scale
            }
            .lightModeHairlineBorder(shape)
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, shape = shape, color = MaterialTheme.colorScheme.primary),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Leading Icon
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(if (isTv) 28.dp else 24.dp),
            )

            // Text Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(item.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.requiresAdmin) {
                        AdminBadge()
                    }
                }
                Text(
                    text = stringResource(item.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Trailing Chevron
            Icon(
                imageVector = Tabler.Outline.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ─── Admin Badge ───────────────────────────────────────────────────────────

@Composable
private fun AdminBadge(
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = ShapeCache.smoothPill,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = Tabler.Outline.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(11.dp),
            )
            Text(
                text = stringResource(Res.string.shortcuts_admin_badge),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}
