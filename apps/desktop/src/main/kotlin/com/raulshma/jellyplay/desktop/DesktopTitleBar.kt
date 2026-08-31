package com.raulshma.jellyplay.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Maximize
import com.composables.icons.tabler.outline.Minimize
import com.composables.icons.tabler.outline.Minus
import com.composables.icons.tabler.outline.X

/**
 * Custom title bar for the undecorated desktop window (Main.kt's
 * `Window(undecorated = true)`): the native OS chrome could not follow the
 * app theme — Windows paints its caption from the system palette, so a dark
 * JellyPlay theme sat under a white title bar. Drawing the bar in Compose
 * makes it ride [MaterialTheme] like every other shell surface; it uses the
 * same `surface` role as the NavigationRail beneath it so the left edge of
 * the window reads as one column.
 *
 * Replaces the AWT [androidx.compose.ui.window.MenuBar] too: an undecorated
 * frame has no native menu strip on Windows, so the same File/View/Help
 * entries live here as dropdown menus (their keyboard accelerators —
 * Ctrl+R, Ctrl+Q, F11 — are wired in Main.kt's window-level
 * onPreviewKeyEvent, which fires with or without a focused Compose node).
 *
 * Window controls follow the Windows convention: minimize + maximize/restore
 * hover with a subtle on-surface wash, close hovers with the theme's error
 * pair. The whole bar minus the buttons is a [WindowDraggableArea]; it does
 * not consume pointer events, so the menu buttons inside still receive
 * clicks. Double-clicking the bar toggles maximize, like the native caption.
 */
@Composable
internal fun WindowScope.DesktopTitleBar(
    icon: Painter?,
    isMaximized: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onExit: () -> Unit,
    onToggleFullscreen: () -> Unit,
    isFullscreenActive: Boolean,
    onAbout: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(TitleBarHeight)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            WindowDraggableArea(Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    Modifier
                        .fillMaxHeight()
                        // Double-click anywhere non-interactive on the bar
                        // (icon, title, empty space) toggles maximize.
                        //
                        // OBSERVE-ONLY, in PointerEventPass.Initial: the drag
                        // area's own handler awaits the first down with
                        // requireUnconsumed=true in the Main pass, and Main
                        // pass runs children first — a tap detector here
                        // (detectTapGestures consumes the down) would starve
                        // it and the window could no longer be dragged.
                        // Awaiting in Initial runs parent-first and consumes
                        // nothing, so dragging, menus and this detector all
                        // coexist.
                        .pointerInput(Unit) {
                            var lastPressAt = 0L
                            awaitEachGesture {
                                awaitFirstDown(pass = PointerEventPass.Initial)
                                val now = System.currentTimeMillis()
                                if (now - lastPressAt < NativeDoubleClickTimeoutMs) {
                                    lastPressAt = 0L
                                    onToggleMaximize()
                                } else {
                                    lastPressAt = now
                                }
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) {
                        Image(
                            icon,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(start = 10.dp, end = 6.dp)
                                .size(18.dp),
                        )
                    }
                    Text(
                        "JellyPlay",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TitleBarMenuButton(label = "File") { closeMenu ->
                        DropdownMenuItem(
                            text = { Text("Refresh") },
                            trailingIcon = { MenuShortcutText("Ctrl+R") },
                            onClick = {
                                closeMenu()
                                onRefresh()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Exit") },
                            trailingIcon = { MenuShortcutText("Ctrl+Q") },
                            onClick = {
                                closeMenu()
                                onExit()
                            },
                        )
                    }
                    TitleBarMenuButton(label = "View") { closeMenu ->
                        DropdownMenuItem(
                            text = {
                                Text(if (isFullscreenActive) "Exit Fullscreen" else "Fullscreen")
                            },
                            trailingIcon = { MenuShortcutText("F11") },
                            onClick = {
                                closeMenu()
                                onToggleFullscreen()
                            },
                        )
                    }
                    TitleBarMenuButton(label = "Help") { closeMenu ->
                        DropdownMenuItem(
                            text = { Text("About JellyPlay") },
                            onClick = {
                                closeMenu()
                                onAbout()
                            },
                        )
                    }
                }
            }

            WindowControlButton(
                icon = Tabler.Outline.Minus,
                contentDescription = "Minimize",
                onClick = onMinimize,
            )
            WindowControlButton(
                // Tabler's Maximize/Minimize are the corner arrows pointing
                // out/in — the same shapes Windows uses for maximize/restore.
                icon = if (isMaximized) Tabler.Outline.Minimize else Tabler.Outline.Maximize,
                contentDescription = if (isMaximized) "Restore" else "Maximize",
                onClick = onToggleMaximize,
            )
            WindowControlButton(
                icon = Tabler.Outline.X,
                contentDescription = "Close",
                onClick = onClose,
                isClose = true,
            )
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

/** Title-bar height — Windows' standard caption is 32px; 40dp reads well with the 44dp rail items. */
private val TitleBarHeight = 40.dp

/** Double-press window, ~AWT's native multi-click interval. */
private const val NativeDoubleClickTimeoutMs = 500L

/** Width of one window-control hit target (height is the full bar). */
private val WindowControlButtonWidth = 46.dp

@Composable
private fun MenuShortcutText(shortcut: String) {
    Text(
        shortcut,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TitleBarMenuButton(
    label: String,
    items: @Composable (closeMenu: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .height(TitleBarHeight)
                .clickable { expanded = true }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items { expanded = false }
        }
    }
}

@Composable
private fun WindowControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isClose: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val background = when {
        isClose && hovered -> MaterialTheme.colorScheme.errorContainer
        hovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    Box(
        Modifier
            .width(WindowControlButtonWidth)
            .fillMaxHeight()
            .background(background)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = if (isClose && hovered) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
