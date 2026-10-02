package com.raulshma.jellyplay.feature.admin.backups

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.error.UserErrorMessages
import com.raulshma.jellyplay.core.data.repository.AdminBackupRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.model.BackupComponentOptions
import com.raulshma.jellyplay.core.model.ServerBackup
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.viewmodel.loadInto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow

/**
 * The backups screen's phases between "restore fired" and "usable session
 * again". The restore endpoint answers 204 and the server restarts
 * immediately with no progress payload — [Restoring] covers the downtime
 * poll, [Reconnecting] the standard session-recovery pass once the server
 * answers again.
 */
@Immutable
sealed interface RestorePhase {
    data object Idle : RestorePhase
    data object Restoring : RestorePhase
    data object Reconnecting : RestorePhase
}

@Immutable
data class AdminBackupsState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val backups: List<ServerBackup> = emptyList(),
    /** False when the server has no backup service (GET /Backup 404) — the feature hides. */
    val isSupported: Boolean = true,
    /** Create request in flight (create is synchronous: the response is the manifest). */
    val isCreating: Boolean = false,
    val restorePhase: RestorePhase = RestorePhase.Idle,
    /** One-shot success feedback after the poll-and-reconnect ladder completes. */
    val restoreComplete: Boolean = false,
    /**
     * One-shot failure feedback: the restore fired but the server never
     * answered the health probe again — the screen renders the localized
     * unreachable-server message (the VM carries no user-facing text).
     */
    val restoreServerUnreachable: Boolean = false,
)

class AdminBackupsViewModel(
    private val backupRepository: AdminBackupRepository,
    private val authRepository: AuthRepository,
) : JellyPlayViewModel() {

    private val _state = stateFlow(AdminBackupsState())
    val state: StateFlow<AdminBackupsState> = _state.flow

    init {
        loadBackups()
    }

    fun loadBackups() {
        launch {
            loadInto(
                start = {
                    // The unreachable-server flag clears here too: the error
                    // screen's retry lands on this ladder, and a stale flag
                    // would otherwise own the UI forever (the restore ladder
                    // is unreachable behind the error screen).
                    _state.update { it.copy(isLoading = true, error = null, restoreServerUnreachable = false) }
                },
                fetch = { runCatchingRethrowingCancellation { backupRepository.getBackupsSnapshot().getOrThrow() } },
                onSuccess = { snapshot ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            backups = snapshot.backups,
                            isSupported = snapshot.supportsBackups,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                },
            )
        }
    }

    fun refresh() {
        launch {
            _state.update { it.copy(isRefreshing = true) }
            fetchSnapshot()
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    /** The shared fetch both list ladders dispatch (ScheduledTasksViewModel's shape). */
    private suspend fun fetchSnapshot() {
        loadInto(
            start = { },
            fetch = { runCatchingRethrowingCancellation { backupRepository.getBackupsSnapshot().getOrThrow() } },
            onSuccess = { snapshot ->
                _state.update {
                    it.copy(backups = snapshot.backups, isSupported = snapshot.supportsBackups)
                }
            },
            onFailure = { e ->
                _state.update { it.copy(error = e.message) }
            },
        )
    }

    /**
     * Runs a backup with the dialog's component selection. Create is
     * synchronous — the response IS the new archive's manifest — so the only
     * follow-up is a list refresh.
     */
    fun createBackup(options: BackupComponentOptions) {
        if (_state.value.isCreating) return
        launch {
            _state.update { it.copy(isCreating = true, error = null) }
            val result = backupRepository.createBackup(options)
            if (result.isSuccess) {
                _state.update { it.copy(isCreating = false) }
                fetchSnapshot()
            } else {
                _state.update {
                    it.copy(isCreating = false, error = UserErrorMessages.resolve(result, "unknown error"))
                }
            }
        }
    }

    /**
     * The restore ladder: fire-and-forget 204 → poll `/System/Info/Public`
     * (the address-failover health probe) until the server answers again →
     * re-auth/reconnect via the standard session-restore path → refresh the
     * list. The phase gates re-entry: a restore in flight refuses a second.
     */
    fun restoreBackup(archiveFileName: String) {
        if (_state.value.restorePhase != RestorePhase.Idle) return
        launch {
            _state.update {
                it.copy(restorePhase = RestorePhase.Restoring, restoreComplete = false, restoreServerUnreachable = false, error = null)
            }
            val fired = backupRepository.restoreBackup(archiveFileName)
            if (fired.isFailure) {
                _state.update {
                    it.copy(restorePhase = RestorePhase.Idle, error = UserErrorMessages.resolve(fired, "unknown error"))
                }
                return@launch
            }
            if (awaitServerRestart()) {
                _state.update { it.copy(restorePhase = RestorePhase.Reconnecting) }
                authRepository.restoreSession()
                fetchSnapshot()
                _state.update { it.copy(restorePhase = RestorePhase.Idle, restoreComplete = true) }
            } else {
                _state.update {
                    it.copy(
                        restorePhase = RestorePhase.Idle,
                        restoreServerUnreachable = true,
                    )
                }
            }
        }
    }

    /**
     * Polls the health probe until the restarting server answers again. The
     * probe is the address-failover machinery's reachability check
     * (`GET /System/Info/Public` against the exact address — never rerouted
     * to an alternate while the server is down).
     */
    private suspend fun awaitServerRestart(): Boolean {
        val address = authRepository.currentServer.first()?.address ?: return false
        repeat(RESTORE_POLL_ATTEMPTS) {
            delay(RESTORE_POLL_INTERVAL_MS)
            if (authRepository.probeServer(address).isSuccess) return true
        }
        return false
    }

    private companion object {
        /** Restore downtime budget: 60 probes × 3 s ≈ 3 minutes of restart. */
        const val RESTORE_POLL_ATTEMPTS = 60
        const val RESTORE_POLL_INTERVAL_MS = 3_000L
    }
}
