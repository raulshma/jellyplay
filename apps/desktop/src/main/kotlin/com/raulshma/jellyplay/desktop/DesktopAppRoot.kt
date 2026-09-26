package com.raulshma.jellyplay.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.awt.ComposeWindow
import com.raulshma.jellyplay.core.data.remote.RemoteControlReceiver
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.RealtimeConnection
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.feature.shell.RealtimeSessionController
import com.raulshma.jellyplay.feature.shell.SessionRestore
import com.raulshma.jellyplay.feature.shell.navigation.SignedOutAuthHost
import org.koin.compose.koinInject
import java.util.concurrent.atomic.AtomicReference

/**
 * Desktop nav root ("desktop nav v1"): session-gated shell over the
 * shared feature conveyor. Signed-out users get the shared
 * [SignedOutAuthHost] (the shared auth section; retired the legacy
 * DesktopSignInPane with its cut-list); a live session renders the
 * NavigationRail + NavDisplay scaffold below.
 *
 * What is deliberately NOT wired yet (each omission dead-ends in the
 * registration-ledger guard in [DesktopNavScaffold], so a shared screen
 * pushing the route sees a snackbar instead of crashing NavDisplay with an
 * unregistered entry):
 *  - LiveTvChannelPlayer — the live-TV surface has no desktop engine host;
 *  - SubtitleTester — androidMain-only, no commonMain section at all.
 *
 * The metadata editor went live with the store promotion:
 * StreamingSubtitleStoreImpl moved to jvmShared with a desktop binding in
 * desktopDataModule (appdata-backed), so [editorSection] renders in the
 * scaffold's graph
 * Route.MetadataEditor — the details screen's edit action opens the shared
 * EditorScreen (admin-gated, like Android).
 *
 * VIDEO went live on WINDOWS (SwingPanel/HWND mpv surface) and
 * wherever the mpv software-render surface smoke-passes
 * (DesktopSoftwareVideoPane, no child window); the per-session engine resolves
 * through PlayerEngineFactory (desktopPlayerModule); OSes with neither surface
 * story keep the dead-end guard.
 *
 * The AUDIO player went live with the real-audio engine:
 * [audioPlayerSection] registers Route.AudioPlayer + Route.Ambient, so music
 * track clicks (every music screen pushes Route.AudioPlayer(trackId)) open
 * the now-playing screen over the real desktop audio core —
 * DesktopAudioQueueManager in desktopPlayerModule.
 *
 * Home went live with the desktop wiring: the four WorkManager/
 * widget-backed HomeViewModel ctor deps (PlaybackSyncScheduler,
 * TvWatchNextScheduler, ContinueWatchingBroadcaster, LibrarySyncHook) gained
 * honest no-op desktop definitions in desktopDataModule, so [homeSection]
 * renders in the scaffold's rail (Video/Music mode persisted through
 * HomeDiscoveryStore like the Android shell).
 *
 * Details + the auth drill-ins went live with the details/auth conveyor
 * flips: [detailsSection] renders behind every shared screen that pushes a
 * detail route (search results, requests/calendar → SeerrDetail, person/
 * cast/collection drill-ins), and [authSection] backs the settings
 * Server/UserManagement pushes (AddServer/ServerList/Login/QuickConnect/
 * UserSelection). Since the SAME section is the sign-in flow:
 * [SignedOutAuthHost] registers it while signed out, so desktop signs
 * in through the shared screens (Quick Connect, remembered-user picker,
 * add-server discovery included) — here the section only serves signed-in
 * server management.
 *
 * Settings + admin went live with the admin repositories' Koin flip:
 * AdminRepository/AdminStatisticsRepository are Koin singles in
 * dataJvmModule on both platforms, so [settingsSection] and [adminSection]
 * render in the scaffold (the settings drill-ins SeerrSettings/ArrSettings included —
 * their Seerr/Arr/datastore ctor deps are all Koin-native).
 *
 * Music went live next — browse-only at first, and fully playable
 * since: the last unresolved music ctor dep (AudioQueueFacade) binds
 * to the shared DefaultAudioQueueFacade over the desktop
 * DesktopAudioQueueManager (audio-only MpvDesktopEngine behind it), so
 * [musicSection] renders in the scaffold with its full browse/albums/artists/
 * genres/
 * playlists cluster AND play/enqueue/instant-mix actions drive real playback.
 * Track clicks navigate to the live Route.AudioPlayer (registered by
 * [audioPlayerSection] above). Since the music error-feedback seam
 * has a host here too, and since the shared UserMessageHost landed, that host
 * is the seam itself: the shell snackbar serves BOTH the DesktopMusicMessageBus
 * relay and the shared UserMessageBus (whose messages desktop previously
 * dropped) through one collector — one surface shared with the dead-end guard
 * in the scaffold.
 *
 * First-run onboarding gate: once an authenticated session enters
 * [DesktopNavScaffold], a one-shot read of the persisted `onboarding_completed`
 * flag (AppRuntimeStateStore.isOnboardingCompleted) pushes Route.Onboarding
 * for a not-yet-onboarded user — the Android JellyPlayApp gate's order and
 * pref, so completion through the shared wizard never re-fires it.
 *
 * @param previousCrashLogPath non-null when the previous session wrote a crash
 *   report (DesktopCrashHandler marker consumed at boot); surfaced as a
 *   one-line note + log path in the About dialog — deliberately minimal, this
 *   is a diagnostics pointer, not an error UI.
 * @param windowRef the ComposeWindow handle (Main.kt's AWT ref), consumed
 *   ONLY by the session harness (screenshots + key injection).
 *   The parameter is always supplied; the ref's CONTENT is null until the
 *   window is composed.
 */
@Composable
internal fun DesktopAppRoot(
    showAbout: Boolean,
    onDismissAbout: () -> Unit,
    previousCrashLogPath: String? = null,
    windowRef: AtomicReference<ComposeWindow?>? = null,
    // File→Refresh signal (Main.kt's MenuBar owns the item; Ctrl+R). The
    // scaffold dispatches each emission into LocalPullToRefreshRegistry, so it
    // refreshes whatever pull-to-refresh screen is active — not just Home —
    // and is silently dropped when the current screen has no refresh action.
    menuRefreshRequests: kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow(),
) {
    val authRepository: AuthRepository = koinInject()
    val isAuthenticated by authRepository.isAuthenticated.collectAsState(initial = false)

    //  E2E harness containment: all three `jellyplay.*.enabled` lanes
    // (session / native-dialog / flows) arm through ONE call, each lane
    // composing nothing unless its property is set. The host lives HERE
    // (not in DesktopNavScaffold) because the session/flows harnesses
    // perform the login themselves and must keep running across the
    // sign-in → scaffold composition swap.
    DesktopHarnessHost(
        authRepository = authRepository,
        windowRef = windowRef,
    )

    // Session-restore probe: until it completes we cannot know whether a
    // persisted (server, user) pair exists, so hold on a neutral splash
    // instead of flashing the sign-in pane at every resuming session. The
    // choreography is the SHARED SessionRestore now (shared/feature/shell) —
    // restore call → authenticated-mirror wait → bounded timeout → release —
    // replacing the former bare restore-call-then-done effect; the mirror
    // wait and the 2.5 s cap are the discipline desktop GAINED (the release
    // can no longer land while this composition still shows the signed-out
    // host). The splash rendering below stays this shell's own.
    val sessionScope = rememberCoroutineScope()
    val sessionRestore = remember(authRepository) {
        SessionRestore(
            authChanges = authRepository.isAuthenticated,
            restoreSession = authRepository::restoreSession,
            currentServer = authRepository.currentServer,
            currentUser = authRepository.currentUser,
        )
    }
    val isRestoring by sessionRestore.isRestoring.collectAsState()
    LaunchedEffect(sessionRestore) {
        sessionRestore.restore(sessionScope)
    }

    // Desktop receiver port: realtime socket + capabilities + the
    // remote-control receiver, driven off the auth state for the life of the
    // composition (survives the sign-in → scaffold swap because it lives
    // HERE, like the harness hosts above; torn down with the window).
    // The choreography itself is the SHARED RealtimeSessionController now
    // (shared/feature/shell) — the former DesktopSessionCoordinator held only
    // this wiring and died with the fold; the per-shell share is the client
    // name and this `create` call. The restore above may leave this
    // composition already authenticated, and the controller's auth collector
    // picks that up off the StateFlow's current value on its first pass.
    val realtimeConnection: RealtimeConnection = koinInject()
    val serverIdentityStore: ServerIdentityStore = koinInject()
    val remoteControlReceiver: RemoteControlReceiver = koinInject()
    val realtimeSession = remember(
        authRepository,
        realtimeConnection,
        serverIdentityStore,
        remoteControlReceiver,
    ) {
        RealtimeSessionController.create(
            scope = sessionScope,
            isAuthenticated = authRepository.isAuthenticated,
            currentServer = authRepository.currentServer,
            currentUser = authRepository.currentUser,
            clientName = "JellyPlay Desktop",
            realtimeConnection = realtimeConnection,
            remoteControlReceiver = remoteControlReceiver,
            serverIdentityStore = serverIdentityStore,
            onReconnect = { authRepository.postCapabilities() },
        )
    }
    DisposableEffect(realtimeSession, sessionScope) {
        onDispose { realtimeSession.stop() }
    }

    when {
        isRestoring -> SessionRestoreSplash()
        // The signed-out gate is the SHARED SignedOutAuthHost now
        // (shared/feature/shell) — the former DesktopSignedOutAuthHost
        // hand-copy is retired with its v1 cut-list. The desktop-only chrome
        // rides the shared host's content slot: Esc / Alt+Left pop the
        // stack, refusing to pop below the ServerList root via
        // desktopBackKeyDecision — the same pure fold (back key above the
        // root, else refuse) DesktopNavScaffold's tab roots run. At the root
        // the key event falls through unconsumed — it deliberately neither
        // quits the app nor navigates; the window closes via the titlebar /
        // tray Quit like everywhere else in the shell. The saved-state
        // configuration stays desktop-supplied: the sealed Route serializer
        // is registered as the polymorphic NavKey default (see
        // desktopNavSavedStateConfiguration in DesktopRail.kt).
        !isAuthenticated -> {
            SignedOutAuthHost(
                savedStateConfiguration = desktopNavSavedStateConfiguration(),
                content = { authNavigator, backStackDepth, display ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                if (desktopBackKeyDecision(event.key, event.isAltPressed, backStackDepth())) {
                                    authNavigator.goBack()
                                    true
                                } else {
                                    // Root-refusing pop: at the ServerList seed
                                    // the event is not consumed (no quit-on-Esc
                                    // convention in this shell).
                                    false
                                }
                            },
                    ) {
                        display()
                    }
                },
            )
        }
        else -> DesktopNavScaffold(
            menuRefreshRequests = menuRefreshRequests,
            windowRef = windowRef,
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = onDismissAbout,
            confirmButton = { TextButton(onClick = onDismissAbout) { Text("Close") } },
            title = { Text("JellyPlay") },
            text = {
                Column {
                    Text("KMP desktop shell. Android app unaffected.")
                    //  crash scaffold: the previous session's crash
                    // marker, if any (Main.kt consumed it at boot). One line +
                    // the log path — no link, no error styling; users copy the
                    // path out of this text when filing a report.
                    if (previousCrashLogPath != null) {
                        Text(
                            "Previous session ended unexpectedly.\nCrash log: $previousCrashLogPath",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun SessionRestoreSplash() {
    Box(Modifier.fillMaxSize()) {
        Text("Restoring session…", Modifier.align(Alignment.Center))
    }
}
