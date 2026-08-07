package com.raulshma.jellyplay.navigation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Search
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.navigation.NavIcon
import com.raulshma.jellyplay.core.ui.navigation.Route

/**
 * Material Design 3 Expressive Floating Navigation Bar (Google Photos redesign style).
 *
 * Consists of:
 * 1. A floating main capsule pill holding top-level navigation items (e.g. Home, Library, Live TV)
 *    and an integrated right action pill button ("Create" / Quick action).
 * 2. A standalone floating circular Search FAB docked beside the main capsule.
 */
@Composable
fun ExpressiveFloatingNavigationBar(
    routes: Map<Route, String>,
    currentTopLevel: NavKey,
    onNavigate: (Route) -> Unit,
    showLabels: Boolean,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    modifier: Modifier = Modifier,
) {
    // Separate Search route if present in top level routes
    val searchEntry = routes.entries.find { it.key is Route.Search }
    val mainRoutes = routes.filterKeys { it !is Route.Search }

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier
    ) {
        val availableWidth = maxWidth
        val nMain = mainRoutes.size
        val hasSearch = searchEntry != null

        // Calculate width required to comfortably fit all text labels + search FAB
        val requiredFullWidth = (90.dp * nMain) + (if (hasSearch) 55.dp else 0.dp) + 32.dp

        // When screen width is constrained (< requiredFullWidth), hide unselected text labels
        // so the main capsule pill stays compact (~180-220.dp) and the separate Search FAB remains docked beside it.
        val canFitLabels = showLabels && availableWidth >= requiredFullWidth

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── 1. Main Navigation Capsule Pill ──
            Surface(
                shape = ShapeCache.smoothPill,
                color = containerColor.copy(alpha = 0.85f),
                tonalElevation = 2.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                ),
                modifier = Modifier.height(48.dp)
            ) {
                Row(
                    modifier = Modifier
                        .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
                        .fillMaxHeight()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    mainRoutes.forEach { (route, label) ->
                        androidx.compose.runtime.key(route) {
                            val selected = route == currentTopLevel
                            val activeContainerColor = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                            } else {
                                Color.Transparent
                            }
                            val contentColor by animateColorAsState(
                                targetValue = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                                label = "navItemColor"
                            )

                            val shouldShowLabel = selected || canFitLabels

                            Surface(
                                shape = CircleShape,
                                color = activeContainerColor,
                                modifier = Modifier
                                    .focusIndicator(CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { onNavigate(route) }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
                                        .padding(
                                            horizontal = if (shouldShowLabel) 12.dp else 10.dp,
                                            vertical = 6.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    NavIcon(route, label, selected = selected, tint = contentColor)
                                    if (shouldShowLabel) {
                                        Text(
                                            text = label,
                                            color = contentColor,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 2. Standalone Circular Search Floating Button ──
            if (searchEntry != null) {
                val isSearchSelected = currentTopLevel is Route.Search
                val fabColor by animateColorAsState(
                    targetValue = if (isSearchSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        containerColor.copy(alpha = 0.85f)
                    },
                    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    label = "searchFabColor"
                )
                val iconTint by animateColorAsState(
                    targetValue = if (isSearchSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    label = "searchFabIconTint"
                )

                Surface(
                    shape = CircleShape,
                    color = fabColor,
                    tonalElevation = 3.dp,
                    shadowElevation = 6.dp,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .focusIndicator(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onNavigate(searchEntry.key) }
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Tabler.Outline.Search,
                            contentDescription = searchEntry.value,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
