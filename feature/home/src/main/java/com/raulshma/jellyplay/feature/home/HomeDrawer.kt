package com.raulshma.jellyplay.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChartBar
import com.composables.icons.tabler.outline.Heart
import com.composables.icons.tabler.outline.Inbox
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.Puzzle
import com.composables.icons.tabler.outline.Server
import com.composables.icons.tabler.outline.Shield
import com.composables.icons.tabler.outline.User
import com.composables.icons.tabler.outline.Users
import com.composables.icons.tabler.outline.Wand
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The body of the Home modal navigation drawer. Extracted from
 * [MainHomeContent] so the drawer can be read, previewed, and tested in
 * isolation. All navigation is delegated to [callbacks]; the drawer only
 * closes itself via [drawerState] before invoking a callback.
 */
@Composable
internal fun HomeDrawerBody(
    currentUser: UserInfo?,
    backgroundColor: Color,
    drawerState: DrawerState,
    scope: CoroutineScope,
    callbacks: HomeCallbacks,
) {
    ModalDrawerSheet(
        drawerContainerColor = backgroundColor.copy(alpha = 0.98f),
        modifier = Modifier
            .width(320.dp)
            .fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DrawerUserHeader(currentUser)

            DrawerSectionLabel("ACCOUNT")
            DrawerItem(
                icon = Tabler.Outline.Server,
                label = "Server Management",
                drawerState = drawerState,
                scope = scope,
                onClick = callbacks.onServerManagementClick,
            )
            DrawerItem(
                icon = Tabler.Outline.Users,
                label = "Switch User",
                drawerState = drawerState,
                scope = scope,
                onClick = callbacks.onUserManagementClick,
            )

            Spacer(modifier = Modifier.height(8.dp))

            DrawerSectionLabel("ACTIVITY & INSIGHTS")
            DrawerItem(
                icon = Tabler.Outline.Heart,
                label = "Browse Favorites",
                drawerState = drawerState,
                scope = scope,
                onClick = callbacks.onFavoritesClick,
            )
            DrawerItem(
                icon = Tabler.Outline.ChartBar,
                label = "Watch History Heatmap",
                drawerState = drawerState,
                scope = scope,
                onClick = callbacks.onWatchProgressHeatmapClick,
            )
            DrawerItem(
                icon = Tabler.Outline.Inbox,
                label = "Seerr Requests",
                drawerState = drawerState,
                scope = scope,
                onClick = callbacks.onRequestsClick,
            )

            Spacer(modifier = Modifier.height(8.dp))

            DrawerSectionLabel("SYSTEM")
            if (currentUser?.isAdmin == true) {
                DrawerItem(
                    icon = Tabler.Outline.Shield,
                    label = "Admin Dashboard",
                    drawerState = drawerState,
                    scope = scope,
                    onClick = callbacks.onAdminDashboardClick,
                )
            }
            DrawerItem(
                icon = Tabler.Outline.Puzzle,
                label = "Seerr Integration",
                drawerState = drawerState,
                scope = scope,
                onClick = callbacks.onSeerrSettingsClick,
            )
            DrawerItem(
                icon = Tabler.Outline.Wand,
                label = "Setup Wizard",
                drawerState = drawerState,
                scope = scope,
                onClick = callbacks.onSetupWizardClick,
            )
            DrawerItem(
                icon = Tabler.Outline.InfoCircle,
                label = "About JellyPlay",
                drawerState = drawerState,
                scope = scope,
                onClick = callbacks.onAboutClick,
            )
        }
    }
}

@Composable
private fun DrawerUserHeader(user: UserInfo?) {
    if (user == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Tabler.Outline.User,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = "Welcome back,",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = user.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(bottom = 12.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun DrawerItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    drawerState: DrawerState,
    scope: CoroutineScope,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
        label = { Text(label) },
        selected = false,
        onClick = {
            scope.launch { drawerState.close() }
            onClick()
        },
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = Color.Transparent
        )
    )
}
