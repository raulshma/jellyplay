package com.raulshma.jellyplay.feature.admin.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Refresh
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.AnimatedSectionEntrance
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.admin.dashboard.components.ActiveSessionsSection
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.feature.admin.dashboard.components.LibraryStatsRow
import com.raulshma.jellyplay.feature.admin.dashboard.components.QuickActionsSection
import com.raulshma.jellyplay.feature.admin.dashboard.components.RecentActivityTimeline
import com.raulshma.jellyplay.feature.admin.dashboard.components.RunningTasksCard
import com.raulshma.jellyplay.feature.admin.dashboard.components.ServerHeroHeader
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_cancel
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_dashboard_title
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_refresh
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_restart
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_restart_server_body
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_restart_server_title
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_shutdown
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_shutdown_server_body
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_shutdown_server_title
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_stop
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_stop_session_body
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_stop_session_title
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_unknown_error

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onBack: () -> Unit,
    onScheduledTasks: () -> Unit,
    onDevices: () -> Unit,
    onLogs: () -> Unit,
    onUserStatistics: () -> Unit = {},
    onStaleMedia: () -> Unit = {},
    onWatchedMediaCleanup: () -> Unit = {},
    onPlugins: () -> Unit = {},
    onUsers: () -> Unit = {},
    viewModel: AdminDashboardViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val backgroundColor = rememberScreenBackgroundColor()

    // TV focus-on-launch: focus the first quick action once content arrives so D-pad input lands on
    // content, not the navigation drawer.
    val contentFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = contentFocusRequester,
        itemCount = if (state.isLoading || state.error != null) 0 else 1,
        tag = "admin_dashboard_init",
    )

    var showRestartDialog by remember { mutableStateOf(false) }
    var showShutdownDialog by remember { mutableStateOf(false) }

    if (showRestartDialog) {
        ConfirmDialog(
            title = stringResource(Res.string.admin_restart_server_title),
            message = stringResource(Res.string.admin_restart_server_body),
            confirmText = stringResource(Res.string.admin_restart),
            dismissText = stringResource(Res.string.admin_cancel),
            tone = ConfirmTone.NEUTRAL,
            onConfirm = {
                showRestartDialog = false
                viewModel.restartServer()
            },
            onDismiss = { showRestartDialog = false },
        )
    }

    if (showShutdownDialog) {
        ConfirmDialog(
            title = stringResource(Res.string.admin_shutdown_server_title),
            message = stringResource(Res.string.admin_shutdown_server_body),
            confirmText = stringResource(Res.string.admin_shutdown),
            dismissText = stringResource(Res.string.admin_cancel),
            tone = ConfirmTone.NEUTRAL,
            onConfirm = {
                showShutdownDialog = false
                viewModel.shutdownServer()
            },
            onDismiss = { showShutdownDialog = false },
        )
    }

    // Stop active playback confirm dialog. Session to stop is held in VM
    // state so it survives recomposition; dismissed on confirm/cancel/away-tap.
    state.pendingStopSession?.let { session ->
        ConfirmDialog(
            title = stringResource(Res.string.admin_stop_session_title),
            message = stringResource(
                Res.string.admin_stop_session_body,
                session.userName.ifBlank { session.deviceName },
            ),
            confirmText = stringResource(Res.string.admin_stop),
            dismissText = stringResource(Res.string.admin_cancel),
            tone = ConfirmTone.DESTRUCTIVE,
            confirmLoading = state.isStoppingSession,
            onConfirm = { viewModel.stopSession() },
            onDismiss = { viewModel.dismissStopSessionDialog() },
        )
    }

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.admin_dashboard_title),
        onBack = onBack,
        backgroundColor = backgroundColor,
        actions = {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .clip(CircleShape)
                    .focusIndicator(CircleShape)
                    .clickable(onClick = { viewModel.loadDashboard() }),
            ) {
                Icon(
                    Tabler.Outline.Refresh,
                    contentDescription = stringResource(Res.string.admin_refresh),
                    modifier = Modifier.padding(12.dp).size(20.dp),
                )
            }
        },
    ) {
        when {
            state.isLoading -> {
                ScreenLoadingState(modifier = Modifier.fillMaxSize())
            }
            state.error != null -> {
                ErrorScreen(
                    message = state.error ?: stringResource(Res.string.admin_unknown_error),
                    onRetry = { viewModel.loadDashboard() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                val useGrid = adaptiveInfo.windowSizeClass == WindowSizeClass.Expanded
                DashboardContent(
                    state = state,
                    useGrid = useGrid,
                    contentPadding = adaptiveInfo.contentPadding(false) - 8.dp,
                    bottomPadding = adaptiveInfo.bottomPadding(isTv),
                    onRestart = { showRestartDialog = true },
                    onShutdown = { showShutdownDialog = true },
                    onScanLibrary = viewModel::scanLibrary,
                    onScheduledTasks = onScheduledTasks,
                    onDevices = onDevices,
                    onLogs = onLogs,
                    onUserStatistics = onUserStatistics,
                    onStaleMedia = onStaleMedia,
                    onWatchedMediaCleanup = onWatchedMediaCleanup,
                    onPlugins = onPlugins,
                    onUsers = onUsers,
                    onStopSession = { viewModel.showStopSessionDialog(it) },
                    contentFocusRequester = contentFocusRequester,
                    modifier = Modifier.fillMaxSize(),
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
    bottomPadding: androidx.compose.ui.unit.Dp,
    onRestart: () -> Unit,
    onShutdown: () -> Unit,
    onScanLibrary: () -> Unit,
    onScheduledTasks: () -> Unit,
    onDevices: () -> Unit,
    onLogs: () -> Unit,
    onUserStatistics: () -> Unit = {},
    onStaleMedia: () -> Unit = {},
    onWatchedMediaCleanup: () -> Unit = {},
    onPlugins: () -> Unit = {},
    onUsers: () -> Unit = {},
    onStopSession: (SessionInfo) -> Unit = {},
    contentFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .tvFocusRestorer()
            .focusGroup()
            .focusRequester(contentFocusRequester)
            .verticalScroll(rememberScrollState())
            .padding(
                start = contentPadding,
                end = contentPadding,
                top = 8.dp,
                bottom = bottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.systemInfo?.let { info ->
            AnimatedSectionEntrance(visible = true) {
                ServerHeroHeader(
                    systemInfo = info,
                    isRestarting = state.isRestarting,
                    isShuttingDown = state.isShuttingDown,
                    libraryScanState = state.libraryScanState,
                    onRestart = onRestart,
                    onShutdown = onShutdown,
                    onScanLibrary = onScanLibrary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }

        state.itemCounts?.let { counts ->
            AnimatedSectionEntrance(visible = true) {
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
                        AnimatedSectionEntrance(visible = true) {
                            RunningTasksCard(
                                tasks = state.runningTasks,
                                onViewAll = onScheduledTasks,
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.sessions.isNotEmpty()) {
                        AnimatedSectionEntrance(visible = true) {
                            ActiveSessionsSection(
                                sessions = state.sessions,
                                onViewAll = onDevices,
                                onStop = onStopSession,
                            )
                        }
                    }
                }
            }
        } else {
            if (state.runningTasks.isNotEmpty()) {
                AnimatedSectionEntrance(visible = true) {
                    RunningTasksCard(
                        tasks = state.runningTasks,
                        onViewAll = onScheduledTasks,
                    )
                }
            }

            if (state.sessions.isNotEmpty()) {
                AnimatedSectionEntrance(visible = true) {
                    ActiveSessionsSection(
                        sessions = state.sessions,
                        onViewAll = onDevices,
                        onStop = onStopSession,
                    )
                }
            }
        }

        if (state.recentActivity.isNotEmpty()) {
            AnimatedSectionEntrance(visible = true) {
                RecentActivityTimeline(
                    entries = state.recentActivity,
                    onViewAll = onLogs,
                )
            }
        }

        AnimatedSectionEntrance(visible = true) {
            QuickActionsSection(
                onScheduledTasks = onScheduledTasks,
                onDevices = onDevices,
                onLogs = onLogs,
                onUserStatistics = onUserStatistics,
                onStaleMedia = onStaleMedia,
                onWatchedMediaCleanup = onWatchedMediaCleanup,
                onPlugins = onPlugins,
                onUsers = onUsers,
            )
        }
    }
}
