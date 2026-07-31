package com.raulshma.jellyplay.feature.shortcuts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.smoothCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowRight
import com.composables.icons.tabler.outline.ChevronRight
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.Keyboard
import com.composables.icons.tabler.outline.LayoutGrid
import com.composables.icons.tabler.outline.Puzzle
import com.composables.icons.tabler.outline.Settings
import com.composables.icons.tabler.outline.Stack2
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.TopBarStyle
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import kotlinx.coroutines.delay

// ─── Filter tab model ──────────────────────────────────────────────────────────

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
    ShortcutFilter.All -> stringResource(R.string.shortcuts_filter_all)
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

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun ShortcutsScreen(
    onBack: () -> Unit,
    onNavigate: (Route) -> Unit,
    viewModel: ShortcutsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isTv = LocalTvMode.current
    val adaptiveInfo = LocalAdaptiveInfo.current
    val firstShortcutFocusRequester = remember { FocusRequester() }
    val firstShortcutRoute = remember(state.categories) {
        state.categories.values.firstOrNull { it.isNotEmpty() }?.firstOrNull()?.route
    }

    LaunchedEffect(isTv, firstShortcutRoute) {
        if (isTv && firstShortcutRoute != null) {
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

    val displayedCategories = remember(state.categories, activeFilter) {
        when (val f = activeFilter) {
            ShortcutFilter.All -> state.categories
            is ShortcutFilter.Category -> state.categories.filterKeys { it == f.category }
        }
    }

    JellyPlayScreenScaffold(
        title = stringResource(R.string.shortcuts_screen_title),
        onBack = onBack,
        topBarStyle = TopBarStyle.None,
    ) { paddingValues ->
        val horizontalPadding = adaptiveInfo.contentPadding(isTv)
        val bottomPadding = adaptiveInfo.bottomPadding(isTv) + paddingValues.calculateBottomPadding()
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .tvFocusRestorer(),
            contentPadding = PaddingValues(bottom = bottomPadding),
        ) {
            item(key = "header") {
                ShortcutsHero(
                    categories = state.categories,
                    statusBarTop = statusBarTop,
                    horizontalPadding = horizontalPadding,
                    isTv = isTv,
                )
            }

            item(key = "filters") {
                AnimatedVisibility(visible = filters.size > 1) {
                    ShortcutFilterRow(
                        filters = filters,
                        activeFilter = activeFilter,
                        onFilterSelected = { activeFilter = it },
                        horizontalPadding = horizontalPadding,
                    )
                }
            }

            if (displayedCategories.isEmpty()) {
                item(key = "empty") {
                    ScreenEmptyState(
                        icon = Tabler.Outline.Keyboard,
                        title = stringResource(R.string.shortcuts_empty_title),
                        description = stringResource(R.string.shortcuts_empty_description),
                        actionLabel = stringResource(R.string.shortcuts_empty_action),
                        onAction = { onNavigate(Route.Library) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                    )
                }
            } else {
                displayedCategories.forEach { (category, shortcuts) ->
                    item(key = "section_$category") {
                        ShortcutSectionHeader(
                            category = category,
                            count = shortcuts.size,
                            modifier = Modifier.padding(
                                start = horizontalPadding,
                                end = horizontalPadding,
                                top = 24.dp,
                                bottom = 10.dp,
                            ),
                        )
                    }

                    val featured = shortcuts.first()
                    item(key = "featured_${featured.route}") {
                        val isFirstShortcut = featured.route == firstShortcutRoute
                        ShortcutFeaturedCard(
                            item = featured,
                            onClick = { onNavigate(featured.route) },
                            modifier = Modifier
                                .padding(horizontal = horizontalPadding)
                                .then(
                                    if (isFirstShortcut) Modifier.focusRequester(firstShortcutFocusRequester)
                                    else Modifier
                                ),
                        )
                    }

                    val rest = shortcuts.drop(1)
                    if (rest.isNotEmpty()) {
                        item(key = "list_$category") {
                            ShortcutCompactList(
                                items = rest,
                                firstShortcutRoute = firstShortcutRoute,
                                firstShortcutFocusRequester = firstShortcutFocusRequester,
                                horizontalPadding = horizontalPadding,
                                isTv = isTv,
                                onNavigate = onNavigate,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Hero ─────────────────────────────────────────────────────────────────────

@Composable
private fun ShortcutsHero(
    categories: Map<ShortcutCategory, List<ShortcutItem>>,
    statusBarTop: androidx.compose.ui.unit.Dp,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val totalShortcuts = categories.values.sumOf { it.size }
    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = horizontalPadding,
                end = horizontalPadding,
                top = statusBarTop + (if (isTv) 28.dp else 20.dp),
                bottom = 6.dp,
            ),
    ) {
        // Label row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(primary),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.shortcuts_hero_eyebrow),
                style = MaterialTheme.typography.labelSmall,
                color = primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.shortcuts_hero_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.shortcuts_hero_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        // Stat pills
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                HeroStatPill(
                    label = stringResource(R.string.shortcuts_stat_total, totalShortcuts),
                    color = primary,
                )
            }
            categories.forEach { (category, shortcuts) ->
                item(key = category.name) {
                    HeroStatPill(
                        label = stringResource(
                            R.string.shortcuts_stat_category,
                            shortcuts.size,
                            stringResource(category.displayNameRes),
                        ),
                        color = category.accentColor(),
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Simple solid divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        )
    }
}

@Composable
private fun HeroStatPill(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = ShapeCache.smoothPill,
        color = color.copy(alpha = 0.12f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ─── Filter row ───────────────────────────────────────────────────────────────

@Composable
private fun ShortcutFilterRow(
    filters: List<ShortcutFilter>,
    activeFilter: ShortcutFilter,
    onFilterSelected: (ShortcutFilter) -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
        contentPadding = PaddingValues(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(filters, key = { _, f -> f.key }) { _, filter ->
            ShortcutFilterChip(
                filter = filter,
                isSelected = activeFilter == filter,
                onClick = { onFilterSelected(filter) },
            )
        }
    }
}

@Composable
private fun ShortcutFilterChip(
    filter: ShortcutFilter,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.05f)
    val shape = ShapeCache.smoothPill

    val bgColor = when {
        isSelected -> when (filter) {
            ShortcutFilter.All -> MaterialTheme.colorScheme.primary
            is ShortcutFilter.Category -> filter.category.accentColor()
        }
        else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
    }
    val contentColor = if (isSelected) {
        when (filter) {
            ShortcutFilter.All -> MaterialTheme.colorScheme.onPrimary
            is ShortcutFilter.Category -> filter.category.onAccentColor()
        }
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        shape = shape,
        color = bgColor,
        modifier = modifier
            .graphicsLayer {
                scaleX = focusState.scale
                scaleY = focusState.scale
            }
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, shape = shape),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = filter.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = filter.label(),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

// ─── Section header ───────────────────────────────────────────────────────────

@Composable
private fun ShortcutSectionHeader(
    category: ShortcutCategory,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val accentColor = category.accentColor()

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Solid accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .clip(ShapeCache.smoothPill)
                    .background(accentColor),
            )
            Text(
                text = stringResource(category.displayNameRes).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                letterSpacing = 1.5.sp,
            )
        }
        Surface(
            shape = ShapeCache.smoothPill,
            color = accentColor.copy(alpha = 0.14f),
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
    }
}

// ─── Featured card ────────────────────────────────────────────────────────────

@Composable
private fun ShortcutFeaturedCard(
    item: ShortcutItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTv = LocalTvMode.current
    val focusState = rememberTvFocusState(focusedScale = 1.025f)
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isActive = focusState.isFocused || isHovered
    val accentColor = item.category.accentColor()
    val shape = smoothCornerShape(if (isTv) 22.dp else 20.dp)

    val arrowOffset by animateDpAsState(
        targetValue = if (isActive) 0.dp else (-6).dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "featuredArrowOffset",
    )

    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .height(if (isTv) 110.dp else 124.dp)
            .hoverable(interactionSource)
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, shape = shape, color = accentColor)
            .graphicsLayer {
                scaleX = focusState.scale
                scaleY = focusState.scale
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isTv) 18.dp else 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Icon box — solid tinted background
            Box(
                modifier = Modifier
                    .size(if (isTv) 56.dp else 62.dp)
                    .clip(ShapeCache.smooth16)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = stringResource(item.titleRes),
                    tint = accentColor,
                    modifier = Modifier.size(if (isTv) 28.dp else 30.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.shortcuts_featured_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor.copy(alpha = 0.75f),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(item.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(item.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Arrow circle — solid background
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = if (isActive) 0.18f else 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Tabler.Outline.ArrowRight,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { translationX = arrowOffset.toPx() },
                )
            }
        }
    }
}

// ─── Compact list ─────────────────────────────────────────────────────────────

@Composable
private fun ShortcutCompactList(
    items: List<ShortcutItem>,
    firstShortcutRoute: Route?,
    firstShortcutFocusRequester: FocusRequester,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    isTv: Boolean,
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = smoothCornerShape(if (isTv) 18.dp else 16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .padding(top = 8.dp),
    ) {
        Column {
            items.forEachIndexed { index, item ->
                val isFirst = item.route == firstShortcutRoute
                ShortcutCompactRow(
                    item = item,
                    isLast = index == items.lastIndex,
                    onClick = { onNavigate(item.route) },
                    modifier = if (isFirst) Modifier.focusRequester(firstShortcutFocusRequester)
                    else Modifier,
                )
            }
        }
    }
}

@Composable
private fun ShortcutCompactRow(
    item: ShortcutItem,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTv = LocalTvMode.current
    val focusState = rememberTvFocusState(focusedScale = 1.02f)
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isActive = focusState.isFocused || isHovered
    val accentColor = item.category.accentColor()
    val shape = smoothCornerShape(if (isTv) 18.dp else 16.dp)

    val arrowAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.3f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "rowArrowAlpha",
    )

    Column {
        Surface(
            onClick = onClick,
            shape = if (isActive) shape else RoundedCornerShape(0.dp),
            color = if (isActive) accentColor.copy(alpha = 0.1f) else Color.Transparent,
            modifier = modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .then(focusState.focusModifier)
                .tvFocusIndicator(focusState, shape = shape, color = accentColor)
                .graphicsLayer {
                    scaleX = focusState.scale
                    scaleY = focusState.scale
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (isTv) 14.dp else 16.dp,
                        vertical = if (isTv) 12.dp else 14.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(smoothCornerShape(11.dp))
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = stringResource(item.titleRes),
                        tint = accentColor,
                        modifier = Modifier.size(19.dp),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(item.titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(item.descriptionRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Icon(
                    imageVector = Tabler.Outline.ChevronRight,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { alpha = arrowAlpha },
                )
            }
        }

        if (!isLast) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .padding(start = 68.dp, end = 16.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            )
        }
    }
}

// ─── Accent color mapping ─────────────────────────────────────────────────────

@Composable
private fun ShortcutCategory.accentColor(): Color = when (this) {
    ShortcutCategory.LIBRARY -> MaterialTheme.colorScheme.primary
    ShortcutCategory.SERVICES -> MaterialTheme.colorScheme.secondary
    ShortcutCategory.SYSTEM -> MaterialTheme.colorScheme.tertiary
}

@Composable
private fun ShortcutCategory.onAccentColor(): Color = when (this) {
    ShortcutCategory.LIBRARY -> MaterialTheme.colorScheme.onPrimary
    ShortcutCategory.SERVICES -> MaterialTheme.colorScheme.onSecondary
    ShortcutCategory.SYSTEM -> MaterialTheme.colorScheme.onTertiary
}
