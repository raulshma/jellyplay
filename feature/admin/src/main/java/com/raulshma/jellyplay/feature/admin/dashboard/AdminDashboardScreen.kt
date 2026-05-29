package com.raulshma.jellyplay.feature.admin.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowLeft
import com.composables.icons.tabler.outline.Refresh
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.LoadingScreen
import com.raulshma.jellyplay.core.ui.components.StaggeredSection
import com.raulshma.jellyplay.feature.admin.dashboard.components.ActiveSessionsSection
import com.raulshma.jellyplay.feature.admin.dashboard.components.LibraryStatsRow
import com.raulshma.jellyplay.feature.admin.dashboard.components.QuickActionsSection
import com.raulshma.jellyplay.feature.admin.dashboard.components.RecentActivityTimeline
import com.raulshma.jellyplay.feature.admin.dashboard.components.RunningTasksCard
import com.raulshma.jellyplay.feature.admin.dashboard.components.ServerHeroHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onBack: () -> Unit,
    onScheduledTasks: () -> Unit,
    onDevices: () -> Unit,
    onLogs: () -> Unit,
    viewModel: AdminDashboardViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    val adaptiveInfo = LocalAdaptiveInfo.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    var showRestartDialog by remember { mutableStateOf(false) }
    var showShutdownDialog by remember { mutableStateOf(false) }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text("Restart Server") },
            text = { Text("Are you sure you want to restart the Jellyfin server? All active sessions will be disconnected.") },
            confirmButton = {
                TextButton(onClick = {
                    showRestartDialog = false
                    viewModel.restartServer()
                }) { Text("Restart") }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showShutdownDialog) {
        AlertDialog(
            onDismissRequest = { showShutdownDialog = false },
            title = { Text("Shutdown Server") },
            text = { Text("Are you sure you want to shut down the Jellyfin server? It will need to be restarted manually.") },
            confirmButton = {
                TextButton(onClick = {
                    showShutdownDialog = false
                    viewModel.shutdownServer()
                }) { Text("Shutdown") }
            },
            dismissButton = {
                TextButton(onClick = { showShutdownDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable(onClick = onBack),
                    ) {
                        Icon(
                            Tabler.Outline.ArrowLeft,
                            contentDescription = "Back",
                            modifier = Modifier.padding(12.dp).size(20.dp),
                        )
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(CircleShape)
                            .clickable(onClick = { viewModel.loadDashboard() }),
                    ) {
                        Icon(
                            Tabler.Outline.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.padding(12.dp).size(20.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
    ) { padding ->
        when {
            state.isLoading -> {
                LoadingScreen(modifier = Modifier.fillMaxSize().padding(padding))
            }
            state.error != null -> {
                ErrorScreen(
                    message = state.error ?: "Unknown error",
                    onRetry = { viewModel.loadDashboard() },
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }
            else -> {
                val useGrid = adaptiveInfo.windowSizeClass == WindowSizeClass.Expanded
                DashboardContent(
                    state = state,
                    useGrid = useGrid,
                    contentPadding = adaptiveInfo.contentPadding(false) - 8.dp,
                    onRestart = { showRestartDialog = true },
                    onShutdown = { showShutdownDialog = true },
                    onScheduledTasks = onScheduledTasks,
                    onDevices = onDevices,
                    onLogs = onLogs,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }
        }
    }
}

@Composable
private fun DashboardContent(
    state: AdminDashboardState,
    useGrid: Boolean,
    contentPadding: androidx.compose.ui.unit.Dp,
    onRestart: () -> Unit,
    onShutdown: () -> Unit,
    onScheduledTasks: () -> Unit,
    onDevices: () -> Unit,
    onLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var staggerIndex = 0

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(
                start = contentPadding,
                end = contentPadding,
                top = 8.dp,
                bottom = 100.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.systemInfo?.let { info ->
            StaggeredSection(visible = true, index = staggerIndex++) {
                ServerHeroHeader(
                    systemInfo = info,
                    isRestarting = state.isRestarting,
                    isShuttingDown = state.isShuttingDown,
                    onRestart = onRestart,
                    onShutdown = onShutdown,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }

        state.itemCounts?.let { counts ->
            StaggeredSection(visible = true, index = staggerIndex++) {
                LibraryStatsRow(counts = counts)
            }
        }

        if (useGrid) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.runningTasks.isNotEmpty()) {
                        StaggeredSection(visible = true, index = staggerIndex) {
                            RunningTasksCard(
                                tasks = state.runningTasks,
                                onViewAll = onScheduledTasks,
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.sessions.isNotEmpty()) {
                        StaggeredSection(visible = true, index = staggerIndex + 1) {
                            ActiveSessionsSection(
                                sessions = state.sessions,
                                onViewAll = onDevices,
                            )
                        }
                    }
                }
            }
            staggerIndex += 2
        } else {
            if (state.runningTasks.isNotEmpty()) {
                StaggeredSection(visible = true, index = staggerIndex++) {
                    RunningTasksCard(
                        tasks = state.runningTasks,
                        onViewAll = onScheduledTasks,
                    )
                }
            }

            if (state.sessions.isNotEmpty()) {
                StaggeredSection(visible = true, index = staggerIndex++) {
                    ActiveSessionsSection(
                        sessions = state.sessions,
                        onViewAll = onDevices,
                    )
                }
            }
        }

        if (state.recentActivity.isNotEmpty()) {
            StaggeredSection(visible = true, index = staggerIndex++) {
                RecentActivityTimeline(
                    entries = state.recentActivity,
                    onViewAll = onLogs,
                )
            }
        }

        StaggeredSection(visible = true, index = staggerIndex) {
            QuickActionsSection(
                onScheduledTasks = onScheduledTasks,
                onDevices = onDevices,
                onLogs = onLogs,
            )
        }
    }
}
