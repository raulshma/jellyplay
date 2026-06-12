package com.raulshma.jellyplay.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.LocalContentColor as TvLocalContentColor
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text as TvText
import androidx.tv.material3.rememberDrawerState
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKeyEvent
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class TvNavItem(
    val route: NavKey,
    val label: String,
    val icon: ImageVector,
)

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TvNavigationDrawer(
    primaryItems: List<TvNavItem>,
    currentTopLevel: NavKey,
    isSubPage: Boolean,
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val contentFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val drawerScope = rememberCoroutineScope()
    val railFocusRequesters = remember(primaryItems.size) {
        List(primaryItems.size) { FocusRequester() }
    }
    var focusedRailIndex by remember { mutableStateOf(0) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    fun selectedRailIndex(): Int =
        primaryItems.indexOfFirst { it.route == currentTopLevel }.coerceAtLeast(0)

    suspend fun focusContent(preferDirectionalMove: Boolean = false) {
        delay(80)
        if (preferDirectionalMove && focusManager.moveFocus(FocusDirection.Right)) return
        try {
            contentFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
        delay(120)
        if (preferDirectionalMove && focusManager.moveFocus(FocusDirection.Right)) return
        try {
            contentFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    suspend fun focusSelectedRail() {
        val targetIndex = selectedRailIndex()
        focusedRailIndex = targetIndex
        val requester = railFocusRequesters.getOrNull(targetIndex) ?: return
        try {
            requester.requestFocus()
        } catch (_: Exception) {
        }
        delay(120)
        try {
            requester.requestFocus()
        } catch (_: Exception) {
        }
    }

    fun navigateFromDrawer(route: NavKey, railIndex: Int) {
        focusedRailIndex = railIndex
        drawerScope.launch {
            drawerState.setValue(DrawerValue.Closed)
            onNavigate(route)
            focusContent(preferDirectionalMove = true)
        }
    }

    NavigationDrawer(
        drawerState = drawerState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onDpadKeyEvent(
                onBack = { e ->
                    if (e.isKeyUp) {
                        onBack()
                    }
                    true
                },
            ),
        drawerContent = { drawerValue ->
            val isClosed = drawerValue == DrawerValue.Closed

            LaunchedEffect(drawerValue) {
                if (!isClosed) {
                    focusSelectedRail()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(if (isClosed) 72.dp else 280.dp)
                    .padding(
                        start = 24.dp,
                        end = if (isClosed) 24.dp else 16.dp,
                        top = 8.dp,
                        bottom = 8.dp,
                    )
                    .verticalScroll(rememberScrollState())
                    .then(
                        if (isSubPage) {
                            Modifier.focusProperties {
                                @Suppress("DEPRECATION")
                                exit = { direction: FocusDirection ->
                                    when (direction) {
                                        FocusDirection.Right -> contentFocusRequester
                                        else -> FocusRequester.Default
                                    }
                                }
                            }
                        } else {
                            Modifier
                        },
                    )
                    .selectableGroup(),
            ) {
                primaryItems.forEachIndexed { index, item ->
                    val isSelected = item.route == currentTopLevel
                    NavigationDrawerItem(
                        selected = isSelected,
                        onClick = { navigateFromDrawer(item.route, index) },
                        leadingContent = {
                            TvNavIcon(item, isSelected, TvLocalContentColor.current)
                        },
                        content = {
                            TvText(
                                item.label,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        modifier = Modifier
                            .focusRequester(railFocusRequesters[index])
                            .onFocusChanged {
                                if (it.isFocused || it.hasFocus) {
                                    focusedRailIndex = index
                                }
                            },
                    )
                }
            }
        },
        content = {
            Box(
                modifier = Modifier
                    .focusRequester(contentFocusRequester)
                    .focusGroup()
                    .tvFocusRestorer(),
            ) {
                content()
            }
        },
    )

    LaunchedEffect(currentTopLevel, isSubPage) {
        focusedRailIndex = selectedRailIndex()
        drawerState.setValue(DrawerValue.Closed)
        focusContent()
    }
}

@Composable
private fun TvNavIcon(
    item: TvNavItem,
    selected: Boolean,
    tint: Color,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1.0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "iconScale",
    )
    Icon(
        imageVector = item.icon,
        contentDescription = item.label,
        tint = tint,
        modifier = Modifier.scale(scale),
    )
}
