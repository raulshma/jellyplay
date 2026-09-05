package com.raulshma.jellyplay.feature.shell.navigation

import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.feature.home.navigation.HomePlayOnRedirect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The shell-supplied surface behind [appSections]: everything the shared
 * section graph needs that genuinely differs per shell — by source-set
 * availability (each shell reads its own audio core) or by state owner
 * (Android wires MainViewModel, desktop wires AuthRepository + stores).
 *
 * Kept deliberately small by the deletion test: a callback both shells
 * implement identically (e.g. the settings "rerun setup" push) lives inside
 * [appSections] itself, not here. The constructor having eleven parameters is
 * the honest measure of how much of the old per-shell entryProvider blocks
 * was already shell policy rather than graph shape. A constructor-arg bundle
 * (the HomeCallbacks / SettingsNavActions idiom), not an interface: shells
 * build it inside `remember`, and the named arguments reference the shell's
 * own locals without any member-name shadowing.
 */
class ShellHostHooks(
    /**
     * Home's Video/Music mode + its persistence callback. Android derives
     * both from MainViewModel's persisted preferences; desktop from
     * HomeDiscoveryStore — same persisted pref either way.
     */
    val homeMode: HomeMode,
    val onHomeModeChange: (HomeMode) -> Unit,
    /**
     * MusicHomeScreen's Now Playing / Ambient cards. Each shell reads its own
     * audio core at click time — Android the AudioPlaybackManager, desktop
     * the DesktopAudioQueueManager — which is exactly what cannot live in the
     * shared graph.
     */
    val onNowPlayingClick: () -> Unit,
    val onAmbientClick: () -> Unit,
    /**
     * settingsSection's logout (revoke=true also revokes the server session).
     * Android: SessionCoordinator; desktop: AuthRepository.
     */
    val onLogout: (Boolean) -> Unit,
    /**
     * settingsSection's About-row update check. Android: the
     * UpdateCoordinator's manual check; desktop: AppUpdateRepository + shell
     * snackbar (no self-update on desktop).
     */
    val onCheckForUpdates: () -> Unit,
    /**
     * adminSection's access gate, read lazily per entry composition so admin
     * refreshes never rebuild the graph. Android: MainViewModel state;
     * desktop: AuthRepository + the 30 s dedupe refresh.
     */
    val isAdmin: () -> Boolean,
    val isRefreshingAdmin: () -> Boolean,
    val onRefreshAdmin: () -> Unit,
    /**
     * homeSection's Play-On redirect — the "remote session is the current
     * player" cast surface behind the HomePlayOnRedirect seam. Only the
     * Android shell has a cast strategy to adapt; shells without one keep the
     * default null (no redirect offered).
     */
    val playOnRedirect: HomePlayOnRedirect? = null,
    /**
     * homeSection's "Surprise Me" signal flow, armed from the Android
     * launcher-shortcut intent. Shells without the seam keep the default
     * never-firing flow.
     */
    val surpriseRequests: Flow<Unit> = emptyFlow(),
)
