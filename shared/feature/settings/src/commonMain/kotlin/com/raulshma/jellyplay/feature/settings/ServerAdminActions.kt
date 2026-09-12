package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.SystemInfo

/**
 * Web seam over core:data's jvmShared `AdminRepository` — the
 * settings feature only ever consumes three of its ~40 operations (the
 * settings root's active-devices row and the About screen's server info), so
 * commonMain cannot name the class whose constructor closure reaches the
 * whole JVM admin surface. QuickDownloadActions template: the interface
 * carries exactly the host-facing surface, the jvmShared actual delegates to
 * the process-wide `AdminRepository` single (same DI graph, android/desktop
 * behavior unchanged), and the wasmJs actual is an honest no-op.
 *
 * Web behavior: the browser shell registers no admin surface, so the wasm
 * actual reports [isSupported] = false — [SettingsViewModel] folds that into
 * its session loading (the active-devices row stays structurally empty) and
 * [AboutViewModel] skips the system-info fetch (server name/version keep
 * their placeholders) — while `getSessions` succeeds empty and
 * `sendMessageToSession` reports failure, mirroring a server-less state.
 */
interface ServerAdminActions {

    /** Whether this platform has the admin surface; gates the consumers. */
    val isSupported: Boolean

    /** Server telemetry for the About screen (see AdminRepository.getSystemInfo). */
    suspend fun getSystemInfo(): Result<SystemInfo>

    /** The active sessions for the settings root's devices row. */
    suspend fun getSessions(): Result<List<SessionInfo>>

    /** Sends a message to a session's screen (see AdminRepository.sendMessageToSession). */
    suspend fun sendMessageToSession(sessionId: String, header: String, text: String): Result<Unit>
}
