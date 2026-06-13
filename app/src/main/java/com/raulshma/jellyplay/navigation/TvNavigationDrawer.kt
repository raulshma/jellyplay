package com.raulshma.jellyplay.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.LocalContentColor as TvLocalContentColor
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text as TvText
import androidx.tv.material3.rememberDrawerState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer

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
    drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed),
    drawerListState: LazyListState = rememberLazyListState(),
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val selectedItemFocusRequester = remember { FocusRequester() }
    val drawerSheetFocusRequester = remember { FocusRequester() }
    var initializationComplete by remember { mutableStateOf(false) }
    var sheetHasFocus by remember { mutableStateOf(false) }
    var focusedRailIndex by remember { mutableStateOf(0) }

    fun selectedRailIndex(): Int =
        primaryItems.indexOfFirst { it.route == currentTopLevel }
            .takeIf { it >= 0 }
            ?: 0

    val selectedIndex = selectedRailIndex()

    fun requestSelectedRailFocus() {
        if (primaryItems.isEmpty()) return
        selectedItemFocusRequester.tryRequestFocus("tv_drawer_selected")
    }

    fun openDrawer() {
        drawerState.setValue(DrawerValue.Open)
        requestSelectedRailFocus()
    }

    fun closeDrawerAndMoveToContent() {
        drawerState.setValue(DrawerValue.Closed)
        if (!focusManager.moveFocus(FocusDirection.Right)) {
            focusManager.clearFocus(force = true)
        }
    }

    fun navigateFromDrawer(route: NavKey, railIndex: Int) {
        focusedRailIndex = railIndex
        closeDrawerAndMoveToContent()
        onNavigate(route)
    }

    BackHandler(enabled = drawerState.currentValue == DrawerValue.Open) {
        closeDrawerAndMoveToContent()
    }

    BackHandler(enabled = drawerState.currentValue == DrawerValue.Closed) {
        if (isSubPage) {
            onBack()
        } else {
            openDrawer()
        }
    }

    // Grab focus on the drawer sheet when it is programmatically opened, and only then allow the
    // onFocusChanged -> drawerState write-back. Requesting focus FIRST and flipping the guard AFTER
    // prevents the first-frame hasFocus==false callback from snapping a freshly-opened drawer closed
    // (the exact race initializationComplete exists to suppress). Mirrors Wholphin
    // NavigationDrawerAndroid.kt:96-102.
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open && !sheetHasFocus) {
            drawerSheetFocusRequester.tryRequestFocus("tv_drawer_sheet")
        }
        initializationComplete = true
    }

    LaunchedEffect(drawerState.currentValue, selectedIndex, primaryItems.size) {
        if (drawerState.currentValue == DrawerValue.Open && primaryItems.isNotEmpty()) {
            drawerListState.scrollToItem(selectedIndex.coerceIn(0, primaryItems.lastIndex))
            requestSelectedRailFocus()
        }
    }

    NavigationDrawer(
        drawerState = drawerState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        drawerContent = { drawerValue ->
            val isClosed = drawerValue == DrawerValue.Closed

            LazyColumn(
                state = drawerListState,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(if (isClosed) 72.dp else 280.dp)
                    .padding(
                        start = 24.dp,
                        end = if (isClosed) 24.dp else 16.dp,
                        top = 8.dp,
                        bottom = 8.dp,
                    )
                    .focusRequester(drawerSheetFocusRequester)
                    .onFocusChanged {
                        sheetHasFocus = it.hasFocus
                        if (initializationComplete) {
                            drawerState.setValue(if (it.hasFocus) DrawerValue.Open else DrawerValue.Closed)
                        }
                    }
                    .focusGroup()
                    .tvFocusRestorer(selectedItemFocusRequester)
                    .focusProperties {
                        onEnter = {
                            requestSelectedRailFocus()
                        }
                    }
                    .selectableGroup(),
            ) {
                items(primaryItems.size) { index ->
                    val item = primaryItems[index]
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
                            .then(
                                if (index == selectedIndex) {
                                    Modifier.focusRequester(selectedItemFocusRequester)
                                } else {
                                    Modifier
                                },
                            )
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
                    .focusGroup()
                    .tvFocusRestorer(),
            ) {
                content()
            }
        },
    )
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
