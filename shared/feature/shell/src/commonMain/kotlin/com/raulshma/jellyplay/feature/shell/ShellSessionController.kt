package com.raulshma.jellyplay.feature.shell

import com.raulshma.jellyplay.core.model.AppUpdateInfo
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The shell-message form of a completed app-update check (ADR 0001's
 * update-surface split): the check→message MAPPING is shared, the rendering is
 * each shell's own surface. Android never uses this — its [UpdateCoordinator]
 * (app module) maps the same repository result into a full update-sheet state
 * machine (pending-APK fallback, dismissal suppression, auto-download), a
 * structurally richer mapping that stays put per the desktop auto-update ADR.
 *
 * Desktop renders [UpdateAvailable] / [UpToDate] / [Failed] as snackbar text;
 * its inert self-update sentinel (no desktop self-update — the
 * `999999.0.0` version sentinel makes `isUpdateAvailable` permanently false)
 * is untouched by this mapping.
 */
sealed interface UpdateCheckMessage {
    /** A newer release exists; [latestVersion] is the tag-derived version. */
    data class UpdateAvailable(val latestVersion: String) : UpdateCheckMessage

    /** The check succeeded and the install is current. */
    data object UpToDate : UpdateCheckMessage

    /** The check failed; [reason] is the exception message (may be null). */
    data class Failed(val reason: String?) : UpdateCheckMessage
}

/**
 * The session-policy wiring both shells used to duplicate (ADR 0001, reversing
 * CONTEXT.md's old per-shell ruling): admin-status state +
 * [AdminRefreshGate] arbitration (shouldStart → refreshCurrentUser →
 * onRefreshCompleted → finally-reset), homeMode collect/persist, the
 * revoke/plain logout fork, and — as a pure mapping — the app-update
 * check→shell-message translation ([updateCheckMessage]).
 *
 * Lives in commonMain beside the gate it drives; constructed DIRECTLY by each
 * shell (no Koin binding — the same construction pattern [AdminRefreshGate]
 * had): Android's `MainViewModel` and desktop's `DesktopNavScaffold` both pass
 * their own [CoroutineScope] and their own repository collaborators as plain
 * flows / suspend lambdas, so this module keeps its repository-free
 * dependency set (see the gate: pure policy over injected lambdas). What each
 * shell keeps per-shell: the update SURFACE (Android's UpdateCoordinator
 * sheet, desktop's snackbar wording) and every platform-conditional block
 * (rail, media-key bridge, video-surface probe, desktop nav saved-state,
 * Android's server/user bootstrap + auth timeouts).
 *
 * State:
 *  - [isAdmin] — the current user's admin flag, projected off [currentUser]
 *    exactly as both shells did (`user?.isAdmin == true`).
 *  - [isRefreshingAdmin] — true while the admin refresh launched by
 *    [refreshAdminStatusNow] is in flight; the state the admin route's guard
 *    renders so it never flashes access-denied before the first refresh.
 *  - [homeMode] — Home's Video/Music mode. Collected from [homeModeChanges]
 *    when the shell supplies them (desktop collects the HomeDiscoveryStore
 *    slice); Android derives its rendered homeMode from its own preferences
 *    pipeline instead, passes `null`, and never reads this state. Writes via
 *    [setHomeMode] land optimistically (matching the desktop rail's instant
 *    switch) and persist through [persistHomeMode] on [scope].
 *
 * Commands run on [scope] — Android passes the ViewModel scope, desktop the
 * scaffold composition scope, so every job dies with its shell exactly as the
 * inlined copies did.
 *
 * @param scope the shell's own coroutine scope (see class KDoc).
 * @param nowMs wall-clock millis; injected like the gate's, so tests pin
 *   arbitration with a fake clock.
 * @param currentUser the signed-in user flow (`null` when signed out).
 * @param refreshCurrentUser re-validates the current user against the server;
 *   ONLY the success result advances the dedupe window (a failed refresh must
 *   not push the next attempt a full window out).
 * @param persistHomeMode the HomeDiscoveryStore write.
 * @param homeModeChanges the persisted homeMode stream, or `null` when the
 *   shell renders homeMode from its own pipeline (Android).
 * @param signOut the shell's logout mechanism — desktop: the AuthRepository
 *   pair; Android: the SessionCoordinator pair (which stops remote control
 *   before signing out). The controller owns only the fork + dispatch.
 */
class ShellSessionController(
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
    currentUser: Flow<UserInfo?>,
    private val refreshCurrentUser: suspend () -> Result<UserInfo>,
    private val persistHomeMode: suspend (HomeMode) -> Unit,
    homeModeChanges: Flow<HomeMode>?,
    private val signOut: suspend (revoke: Boolean) -> Unit,
) {

    /**
     * The admin refresh dedupe policy (30 s window + in-flight guard). The
     * in-flight state is [isRefreshingAdmin] — owned here, read through the
     * gate's lambda exactly as MainViewModel and the desktop scaffold each
     * wired it before.
     */
    private val adminRefreshGate = AdminRefreshGate(
        isRefreshInFlight = { _isRefreshingAdmin.value },
        nowMs = nowMs,
    )

    /** The signed-in user's admin flag, for the admin route's access gate. */
    val isAdmin: StateFlow<Boolean> = currentUser
        .map { it?.isAdmin == true }
        .stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), false)

    private val _isRefreshingAdmin = MutableStateFlow(false)

    /**
     * True while a server admin-status refresh is in flight. The admin route's
     * guard renders this so it can show a brief loading state instead of
     * flashing the access-denied screen before the first refresh completes.
     */
    val isRefreshingAdmin: StateFlow<Boolean> = _isRefreshingAdmin.asStateFlow()

    private val _homeMode = MutableStateFlow(HomeMode.VIDEO)

    /**
     * Home's Video/Music mode. Populated only when [homeModeChanges] was
     * supplied (desktop); defaults to VIDEO otherwise (Android renders its own
     * preferences-derived value and never reads this).
     */
    val homeMode: StateFlow<HomeMode> = _homeMode.asStateFlow()

    init {
        homeModeChanges?.let { changes ->
            scope.launch {
                changes.collect { _homeMode.value = it }
            }
        }
    }

    /**
     * Re-validates the current user's admin status against the server. Called
     * by the admin route container on entering any admin screen, de-duplicated
     * by the shared [AdminRefreshGate]: [shouldStart] early-outs SYNCHRONOUSLY
     * (before launch) so the same-frame two-entries-composing transition fires
     * one refresh, the in-flight flag serializes genuine concurrent entries,
     * and only a successful [refreshCurrentUser] stamps the window. Failures
     * other than access-denied are swallowed (the cached value is kept).
     */
    fun refreshAdminStatusNow() {
        if (!adminRefreshGate.shouldStart()) return
        // Raised before launch, not inside it: a same-frame second entry runs
        // before either launched coroutine dispatches, so the flag must already
        // be up for the gate's isRefreshInFlight read to early it out.
        _isRefreshingAdmin.value = true
        scope.launch {
            try {
                val result = refreshCurrentUser()
                if (result.isSuccess) adminRefreshGate.onRefreshCompleted()
            } finally {
                _isRefreshingAdmin.value = false
            }
        }
    }

    /**
     * Records the Home mode switch: the state updates optimistically (the rail
     * must not wait on the DataStore round trip) and the write lands on
     * [scope] through [persistHomeMode].
     */
    fun setHomeMode(mode: HomeMode) {
        _homeMode.value = mode
        scope.launch { persistHomeMode(mode) }
    }

    /**
     * Ends the session: `revoke = true` also revokes the server session token,
     * `false` signs out locally only. Dispatched on [scope] through the
     * shell-supplied [signOut] action.
     */
    fun logout(revoke: Boolean) {
        scope.launch { signOut(revoke) }
    }

    companion object {
        /**
         * Maps a completed `AppUpdateRepository.checkForUpdate()` result onto
         * the shared [UpdateCheckMessage] both shells' wording renders: a
         * successful check with a newer release → [UpdateCheckMessage.UpdateAvailable],
         * a successful up-to-date check → [UpdateCheckMessage.UpToDate], and
         * any failure → [UpdateCheckMessage.Failed] carrying the exception
         * message (null when the throwable has none). See the sealed
         * interface's KDoc for what each shell does with the message — and why
         * Android's richer coordinator mapping deliberately stays per-shell.
         */
        fun updateCheckMessage(result: Result<AppUpdateInfo>): UpdateCheckMessage =
            result.getOrNull()?.let { info ->
                if (info.isUpdateAvailable) {
                    UpdateCheckMessage.UpdateAvailable(info.latestVersion)
                } else {
                    UpdateCheckMessage.UpToDate
                }
            } ?: UpdateCheckMessage.Failed(result.exceptionOrNull()?.message)

        /** [SharingStarted.WhileSubscribed] stop timeout, as app-wide. */
        private const val SUBSCRIBE_TIMEOUT_MS = 5_000L
    }
}
