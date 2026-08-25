package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.DotsVertical
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.CircleBgBackButton
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.verticalWrapAround
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cd_options
import org.jetbrains.compose.resources.stringResource

/**
 * The collapsing medium top bar for the detail screen: back button, item title
 * (fades in as the backdrop scrolls under), and the overflow options menu
 * (touch `DropdownMenu` + TV `TvSafeSheet`).
 *
 * Extracted verbatim from the former `DetailContent` in `MediaDetailScreen.kt`;
 * behaviour is identical. The [mediaOptions] list is the single source of truth
 * shared between the touch dropdown and the TV sheet so they can never drift.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailTopBar(
    itemName: String,
    scrollState: DetailScrollState,
    onBack: () -> Unit,
    mediaOptions: List<MediaOption>,
    contentFocusRequester: FocusRequester,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    val scrollCollapsed = scrollState.scrollCollapsed
    val animatedContainerColor = scrollState.animatedContainerColor
    val animatedTitleAlpha = scrollState.animatedTitleAlpha
    val isTv = LocalTvMode.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showTvOptionsMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind { drawRect(animatedContainerColor) },
    ) {
        MediumTopAppBar(
            title = {
                Text(
                    text = itemName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = animatedTitleAlpha),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            },
            navigationIcon = {
                CircleBgBackButton(
                    onClick = onBack,
                    scrollCollapsed = scrollCollapsed,
                )
            },
            actions = {
                val editIconColor = if (scrollCollapsed < 0.5f) Color.White else MaterialTheme.colorScheme.onSurface
                Box {
                    IconButton(
                        onClick = {
                            if (isTv) {
                                showTvOptionsMenu = true
                            } else {
                                menuExpanded = true
                            }
                        },
                        modifier = Modifier
                            .focusIndicator(CircleShape)
                            .focusProperties { down = contentFocusRequester }
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(
                                color = if (scrollCollapsed < 0.5f) MaterialTheme.colorScheme.surface.copy(alpha = 0.3f) else Color.Transparent,
                            ),
                    ) {
                        Icon(
                            Tabler.Outline.DotsVertical,
                            contentDescription = stringResource(Res.string.detail_cd_options),
                            tint = editIconColor,
                        )
                    }
                    if (!isTv) {
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            mediaOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = { menuExpanded = false; option.onClick() },
                                    enabled = option.enabled,
                                    leadingIcon = {
                                        Icon(option.icon, contentDescription = null)
                                    },
                                )
                            }
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
            modifier = Modifier.statusBarsPadding(),
            scrollBehavior = scrollBehavior,
        )
    }

    if (showTvOptionsMenu) {
        TvSafeSheet(
            onDismissRequest = { showTvOptionsMenu = false },
            title = stringResource(Res.string.detail_cd_options),
        ) {
            Column(modifier = Modifier.verticalWrapAround()) {
                mediaOptions.forEach { option ->
                    TvOptionItem(
                        icon = option.icon,
                        label = option.label,
                        enabled = option.enabled,
                        onClick = {
                            showTvOptionsMenu = false
                            option.onClick()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvOptionItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ShapeCache.smooth12,
        color = if (isFocused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
        else if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else MaterialTheme.colorScheme.onSurface,
        interactionSource = interactionSource,
        modifier = Modifier.focusIndicator().fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
