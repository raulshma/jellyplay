package com.raulshma.jellyplay.feature.shortcuts

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowRight
import com.composables.icons.tabler.outline.Keyboard
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.TopBarStyle
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import kotlinx.coroutines.delay

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

    JellyPlayScreenScaffold(
        title = "Shortcuts",
        onBack = onBack,
        topBarStyle = TopBarStyle.None,
    ) { paddingValues ->
        val horizontalPadding = adaptiveInfo.contentPadding(isTv)
        val bottomPadding = adaptiveInfo.bottomPadding(isTv) + paddingValues.calculateBottomPadding()
        val totalShortcuts = state.categories.values.sumOf { it.size }
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        LazyVerticalGrid(
            columns = if (isTv) GridCells.Fixed(4) else GridCells.Adaptive(minSize = 168.dp),
            modifier = Modifier
                .fillMaxSize()
                .tvFocusRestorer(),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                top = (if (isTv) 24.dp else 16.dp) + statusBarTop,
                bottom = bottomPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTv) 12.dp else 10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ShortcutsHeader(
                    totalShortcuts = totalShortcuts,
                    modifier = Modifier.padding(bottom = if (isTv) 6.dp else 10.dp),
                )
            }

            if (state.categories.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ScreenEmptyState(
                        icon = Tabler.Outline.Keyboard,
                        title = "No shortcuts available",
                        description = "Browse the app to discover features and content",
                        actionLabel = "Browse Library",
                        onAction = { onNavigate(com.raulshma.jellyplay.core.ui.navigation.Route.Library) },
                    )
                }
            } else {
                state.categories.forEach { (category, shortcuts) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ShortcutSectionHeader(
                            category = category,
                            count = shortcuts.size,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }

                    items(
                        items = shortcuts,
                        key = { item -> item.route.toString() },
                    ) { item ->
                        val isFirstShortcut = item.route == firstShortcutRoute
                        ShortcutTile(
                            item = item,
                            onClick = { onNavigate(item.route) },
                            modifier = if (isFirstShortcut) {
                                Modifier.focusRequester(firstShortcutFocusRequester)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutsHeader(
    totalShortcuts: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Shortcuts",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = "Downloads, requests, settings, and admin tools",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        CountPill(text = "$totalShortcuts items")
    }
}

@Composable
private fun ShortcutSectionHeader(
    category: ShortcutCategory,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(50))
                .background(category.accentColor()),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = category.displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        CountPill(text = count.toString())
    }
}

@Composable
private fun CountPill(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun ShortcutTile(
    item: ShortcutItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTv = LocalTvMode.current
    val focusState = rememberTvFocusState(focusedScale = 1.035f)
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isActive = focusState.isFocused || isHovered
    val shape = RoundedCornerShape(if (isTv) 18.dp else 16.dp)
    val accentColor = item.category.accentColor()
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.95f else 0.64f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "shortcutBackgroundAlpha",
    )
    val arrowOffset by animateDpAsState(
        targetValue = if (isActive) 0.dp else (-5).dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "shortcutArrowOffset",
    )
    val arrowAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.35f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "shortcutArrowAlpha",
    )

    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = backgroundAlpha),
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
        ),
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .height(if (isTv) 82.dp else 92.dp)
            .hoverable(interactionSource)
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, shape = shape)
            .graphicsLayer {
                scaleX = focusState.scale
                scaleY = focusState.scale
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isTv) 12.dp else 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShortcutIcon(
                icon = item.icon,
                title = item.title,
                accentColor = accentColor,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                imageVector = Tabler.Outline.ArrowRight,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = arrowAlpha
                        translationX = arrowOffset.toPx()
                    }
                    .size(18.dp),
            )
        }
    }
}

@Composable
private fun ShortcutIcon(
    icon: ImageVector,
    title: String,
    accentColor: Color,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(accentColor.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = accentColor,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ShortcutCategory.accentColor(): Color = when (this) {
    ShortcutCategory.LIBRARY -> MaterialTheme.colorScheme.primary
    ShortcutCategory.SERVICES -> MaterialTheme.colorScheme.secondary
    ShortcutCategory.SYSTEM -> MaterialTheme.colorScheme.tertiary
}
