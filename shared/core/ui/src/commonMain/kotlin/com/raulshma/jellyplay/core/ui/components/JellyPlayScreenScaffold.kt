package com.raulshma.jellyplay.core.ui.components
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_report
import com.raulshma.jellyplay.core.ui.generated.resources.core_retry
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_back

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.core.designsystem.theme.backgroundBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.adaptive.LocalJellyPlayUi
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@Composable
fun rememberScreenBackgroundColor(
    artworkColor: Color? = null,
    isLightTheme: Boolean = com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme.current,
): Color {
    val themeVariant = com.raulshma.jellyplay.core.designsystem.theme.LocalThemeVariant.current
    // Gradient variants (Synthwave, Aurora) paint their own full-bleed background,
    // so the remembered screen background must stay transparent over them.
    if (themeVariant.backgroundBrush() != null) return Color.Transparent

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

/**
 * House styles for [JellyPlayScreenScaffold]'s header. The value decides both where the
 * title sits and how the bar tracks scroll:
 *
 * @property Standard   Compact pinned header. The title renders inline, right of the back
 *                      button, on a single line; it ellipsizes when long and never pushes
 *                      [JellyPlayScreenScaffold]'s `actions` out of the bar. This is the
 *                      default and the house style for list/detail screens.
 * @property Collapsing [MediumTopAppBar]: the title starts as a
 *                      large heading below the back/actions row and collapses into the row
 *                      as content scrolls. Use only when the screen intentionally leads
 *                      with a hero-style large title.
 * @property None       No header at all; the screen owns its own top bar or has none.
 */
enum class TopBarStyle {
    Standard,
    Collapsing,
    None,
}

/**
 * Standard screen scaffold: shared header (title + optional back button + `actions`) over
 * scrollable content on the themed background.
 *
 * Title placement is governed by [TopBarStyle]; see its KDoc before picking a non-default
 * style. `actions` are always laid out at the end of the header row and keep their full
 * width regardless of title length.
 */
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

    val isTv = LocalJellyPlayUi.current.isTv

    val themeVariant = com.raulshma.jellyplay.core.designsystem.theme.LocalThemeVariant.current
    val variantBrush = themeVariant.backgroundBrush()
    val backgroundModifier = if (variantBrush != null) {
        Modifier.background(variantBrush)
    } else {
        Modifier.background(backgroundColor)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(backgroundModifier)
            .then(
                if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                else Modifier
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (topBarStyle != TopBarStyle.None) {
                val topAppBarColors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                )

                if (topBarStyle == TopBarStyle.Collapsing) {
                    MediumTopAppBar(
                        title = {
                            ScreenTitle(text = title)
                        },
                        navigationIcon = {
                            if (onBack != null) {
                                CircleBgBackButton(onClick = onBack, iconColor = MaterialTheme.colorScheme.onSurface)
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
                                CircleBgBackButton(onClick = onBack, iconColor = MaterialTheme.colorScheme.onSurface)
                            }
                        },
                        actions = { actions() },
                        colors = topAppBarColors,
                        scrollBehavior = scrollBehavior,
                    )
                }
            }

            content(WindowInsets.navigationBars.asPaddingValues())
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
    val isTv = LocalJellyPlayUi.current.isTv

    if (isTv) return

    val resolvedIconColor = iconColor
        ?: lerp(Color.White, MaterialTheme.colorScheme.onSurface, scrollCollapsed)
    // The translucent scrim only helps a white icon over arbitrary artwork. When a caller passes
    // an explicit iconColor (e.g. a solid-background screen) they own contrast, so drop the scrim.
    val bgAlpha = when {
        iconColor != null -> 0f
        scrollCollapsed < 0.5f -> 0.3f
        else -> 0f
    }
    val bgColor = MaterialTheme.colorScheme.surface.copy(alpha = bgAlpha)

    IconButton(
        onClick = onClick,
        modifier = modifier
            .padding(8.dp)
            .clip(CircleShape)
            .background(bgColor),
    ) {
        Icon(
            Tabler.Outline.ArrowLeft,
            contentDescription = stringResource(Res.string.core_ui_back),
            tint = resolvedIconColor,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScreenLoadingState(
    message: String? = null,
    modifier: Modifier = Modifier,
) {
    val isTv = com.raulshma.jellyplay.core.ui.tv.LocalTvMode.current
    val focusRequester = remember { FocusRequester() }
    // On TV the spinner must hold focus while real data is unavailable, otherwise focus is orphaned
    // (nothing else on the screen is focusable until the list/grid composes).
    if (isTv) {
        LaunchedEffect(Unit) { focusRequester.tryRequestFocus("screen_loading") }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (isTv) Modifier.focusRequester(focusRequester).focusable() else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            JellyPlayLoadingIndicator(
                color = MaterialTheme.colorScheme.primary,
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScreenEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    /** When true, disables the action button and swaps its label for a small
     *  spinner. Use while the action is in flight so the tap isn't silent. */
    actionLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val isTv = LocalTvMode.current
    val focusRequester = remember { FocusRequester() }
    val hasAction = actionLabel != null && onAction != null
    // On TV the empty state must hold focus, otherwise focus is orphaned and falls to the
    // always-composed navigation drawer rail, which snaps open when it gains focus.
    if (isTv) {
        LaunchedEffect(Unit) { focusRequester.tryRequestFocus("screen_empty") }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            // With an action button the button is the focus target; without one the Box itself
            // becomes a focus sink so the screen always owns focus on TV.
            .then(if (isTv && !hasAction) Modifier.focusRequester(focusRequester).focusable() else Modifier),
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                val actionFocusState = rememberTvFocusState(focusedScale = 1.05f)
                androidx.compose.material3.OutlinedButton(
                    onClick = onAction,
                    enabled = !actionLoading,
                    modifier = Modifier
                        .then(if (isTv) Modifier.focusRequester(focusRequester) else Modifier)
                        .then(actionFocusState.focusModifier)
                        .tvFocusIndicator(actionFocusState, ShapeCache.smooth12),
                ) {
                    if (actionLoading) {
                        JellyPlayCircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

/**
 * Centralized full-screen error state, mirroring [ScreenLoadingState] /
 * [ScreenEmptyState]. Every screen previously rolled its own error UI
 * (a `Surface` with `errorContainer` + retry, or a snackbar). Adopting this
 * gives consistent iconography, retry affordance, and an optional report hook.
 *
 * @param message   localized error description.
 * @param onRetry   retry callback; when null, the Retry button is hidden.
 * @param onReport  optional "report" affordance for support requests.
 * @param retryLoading when true, disables Retry and shows a spinner.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScreenErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onReport: (() -> Unit)? = null,
    retryLoading: Boolean = false,
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
                com.composables.icons.tabler.Tabler.Outline.AlertTriangle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (onRetry != null) {
                Spacer(Modifier.height(8.dp))
                val retryFocusState = rememberTvFocusState(focusedScale = 1.05f)
                androidx.compose.material3.OutlinedButton(
                    onClick = onRetry,
                    enabled = !retryLoading,
                    modifier = Modifier
                        .then(retryFocusState.focusModifier)
                        .tvFocusIndicator(retryFocusState, ShapeCache.smooth12),
                ) {
                    if (retryLoading) {
                        JellyPlayCircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text(stringResource(Res.string.core_retry))
                    }
                }
            }
            if (onReport != null) {
                androidx.compose.material3.TextButton(onClick = onReport) { Text(stringResource(Res.string.core_report)) }
            }
        }
    }
}
