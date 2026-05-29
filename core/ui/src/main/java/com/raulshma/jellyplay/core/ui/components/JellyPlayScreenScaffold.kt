package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

private fun isLightColor(color: Color): Boolean =
    (color.red * 0.299f + color.green * 0.587f + color.blue * 0.114f) > 0.5f

@Composable
fun rememberScreenBackgroundColor(
    artworkColor: Color? = null,
    isLightTheme: Boolean = isLightColor(MaterialTheme.colorScheme.background),
): Color {
    val baseColor = artworkColor
        ?: MaterialTheme.colorScheme.background
    val targetBackgroundColor = if (isLightTheme) {
        MaterialTheme.colorScheme.background
    } else {
        lerp(baseColor, Color.Black, 0.65f)
    }
    val backgroundColor by animateColorAsState(
        targetValue = targetBackgroundColor,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "screenBackgroundColor",
    )
    return backgroundColor
}

enum class TopBarStyle {
    Standard,
    Collapsing,
    None,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JellyPlayScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    topBarStyle: TopBarStyle = TopBarStyle.Standard,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val navBarColor = LocalNavigationBarColor.current
    SideEffect { navBarColor.value = backgroundColor }

    val scrollBehavior = when (topBarStyle) {
        TopBarStyle.Collapsing -> TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
        TopBarStyle.Standard -> TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
        TopBarStyle.None -> null
    }

    val isTv = LocalTvMode.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .then(
                if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                else Modifier
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (topBarStyle != TopBarStyle.None) {
                val topAppBarColors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                )

                if (topBarStyle == TopBarStyle.Collapsing) {
                    MediumTopAppBar(
                        title = {
                            ScreenTitle(text = title)
                        },
                        navigationIcon = {
                            if (onBack != null) {
                                CircleBgBackButton(onClick = onBack)
                            }
                        },
                        actions = { actions() },
                        colors = topAppBarColors,
                        scrollBehavior = scrollBehavior,
                    )
                } else {
                    TopAppBar(
                        title = {
                            ScreenTitle(text = title)
                        },
                        navigationIcon = {
                            if (onBack != null) {
                                CircleBgBackButton(onClick = onBack)
                            }
                        },
                        actions = { actions() },
                        colors = topAppBarColors,
                        scrollBehavior = scrollBehavior,
                    )
                }
            }

            content(PaddingValues())
        }
    }
}

@Composable
private fun ScreenTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge.copy(
            fontWeight = FontWeight.Bold,
        ),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun CircleBgBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    scrollCollapsed: Float = 0f,
    iconColor: Color? = null,
) {
    val isTv = LocalTvMode.current
    val tvFocusState = rememberTvFocusState(focusedScale = 1.15f)
    val resolvedIconColor = iconColor
        ?: lerp(Color.White, MaterialTheme.colorScheme.onSurface, scrollCollapsed)
    val bgAlpha = if (scrollCollapsed < 0.5f) 0.3f else 0f
    val bgColor = MaterialTheme.colorScheme.surface.copy(alpha = bgAlpha)

    if (isTv) {
        Box(
            modifier = modifier
                .padding(8.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(bgColor)
                .then(tvFocusState.focusModifier)
                .tvFocusIndicator(tvFocusState, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Tabler.Outline.ArrowLeft,
                contentDescription = "Back",
                tint = resolvedIconColor,
                modifier = Modifier.size(22.dp),
            )
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = modifier
                .padding(8.dp)
                .clip(CircleShape)
                .background(bgColor),
        ) {
            Icon(
                Tabler.Outline.ArrowLeft,
                contentDescription = "Back",
                tint = resolvedIconColor,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScreenLoadingState(
    message: String? = null,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                slideInVertically(
                    initialOffsetY = { it / 10 },
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                ),
    ) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            ) {
                androidx.compose.material3.ContainedLoadingIndicator(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                )
                if (message != null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun ScreenEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                slideInVertically(
                    initialOffsetY = { it / 10 },
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                ),
    ) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedButton(onClick = onAction) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}
