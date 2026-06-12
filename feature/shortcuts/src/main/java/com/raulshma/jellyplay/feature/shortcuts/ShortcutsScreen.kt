package com.raulshma.jellyplay.feature.shortcuts

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowRight
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.navigation.Route
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Composable
fun ShortcutsScreen(
    onBack: () -> Unit,
    onNavigate: (Route) -> Unit,
    viewModel: ShortcutsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    JellyPlayScreenScaffold(
        title = "",
        onBack = onBack,
    ) { paddingValues ->
        val bottomPadding = paddingValues.calculateBottomPadding()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // Elegant reimagined header
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Feature Portal",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                )
            )
            Text(
                text = "Quick access to all areas of JellyPlay",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )

            if (state.categories.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No shortcuts available.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 220.dp),
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = 24.dp + bottomPadding
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    state.categories.forEach { (category, items) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 24.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Dynamic theme category indicator bar
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(20.dp)
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = when (category) {
                                                    ShortcutCategory.MEDIA -> listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                                                    ShortcutCategory.PERSONAL -> listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondaryContainer)
                                                    ShortcutCategory.SYSTEM -> listOf(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.tertiaryContainer)
                                                }
                                            ),
                                            shape = RoundedCornerShape(2.dp)
                                        )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = category.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // Count badge
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${items.size} items",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        items(items) { item ->
                            ShortcutCard(
                                item = item,
                                onClick = { onNavigate(item.route) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShortcutCard(
    item: ShortcutItem,
    onClick: () -> Unit,
) {
    val isTv = LocalTvMode.current
    val focusState = rememberTvFocusState(focusedScale = 1.05f)
    
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isActive = focusState.isFocused || isHovered

    val cardShape = AbsoluteSmoothCornerShape(18.dp, 80)
    
    // Smooth transitions for scale, alpha and position
    val arrowOffset by animateDpAsState(
        targetValue = if (isActive) 0.dp else (-6).dp,
        label = "arrowOffset"
    )
    val arrowAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.25f,
        label = "arrowAlpha"
    )
    val cardBgAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.85f else 0.5f,
        label = "cardBgAlpha"
    )

    // Base design color palette matching Material 3 theme colors
    val cardBackground = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = cardBgAlpha)
    val cardBorderColor = if (isActive) {
        when (item.category) {
            ShortcutCategory.MEDIA -> MaterialTheme.colorScheme.primary
            ShortcutCategory.PERSONAL -> MaterialTheme.colorScheme.secondary
            ShortcutCategory.SYSTEM -> MaterialTheme.colorScheme.tertiary
        }
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
    }

    Surface(
        onClick = onClick,
        shape = cardShape,
        color = cardBackground,
        border = BorderStroke(1.dp, cardBorderColor),
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .height(136.dp)
            .hoverable(interactionSource)
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, shape = cardShape)
            .graphicsLayer {
                val scale = focusState.scale
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Beautiful squircled gradient icon box
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = when (item.category) {
                                        ShortcutCategory.MEDIA -> listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primaryContainer
                                        )
                                        ShortcutCategory.PERSONAL -> listOf(
                                            MaterialTheme.colorScheme.secondary,
                                            MaterialTheme.colorScheme.secondaryContainer
                                        )
                                        ShortcutCategory.SYSTEM -> listOf(
                                            MaterialTheme.colorScheme.tertiary,
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        )
                                    }
                                ),
                                shape = AbsoluteSmoothCornerShape(11.dp, 80)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = when (item.category) {
                                ShortcutCategory.MEDIA -> MaterialTheme.colorScheme.onPrimary
                                ShortcutCategory.PERSONAL -> MaterialTheme.colorScheme.onSecondary
                                ShortcutCategory.SYSTEM -> MaterialTheme.colorScheme.onTertiary
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .weight(1f)
                )
            }

            // Animated interactive arrow indicator
            Icon(
                imageVector = Tabler.Outline.ArrowRight,
                contentDescription = null,
                tint = when (item.category) {
                    ShortcutCategory.MEDIA -> MaterialTheme.colorScheme.primary
                    ShortcutCategory.PERSONAL -> MaterialTheme.colorScheme.secondary
                    ShortcutCategory.SYSTEM -> MaterialTheme.colorScheme.tertiary
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 14.dp, end = 16.dp)
                    .graphicsLayer {
                        alpha = arrowAlpha
                        translationX = arrowOffset.toPx()
                    }
                    .size(18.dp)
            )
        }
    }
}
