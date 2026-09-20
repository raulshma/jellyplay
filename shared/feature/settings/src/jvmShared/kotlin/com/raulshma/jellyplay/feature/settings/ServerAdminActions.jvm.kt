package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.SystemInfo

/**
 * The JVM adapter over core:data's `AdminRepository` single — the settings
 * feature's three operations consume the repository verbatim; the adapter
 * only bridges the seam type (android/desktop behavior unchanged).
 */
internal class JvmServerAdminActions(
    private val adminRepository: AdminRepository,
) : ServerAdminActions {
    override val isSupported: Boolean = true
    override suspend fun getSystemInfo(): Result<SystemInfo> = adminRepository.getSystemInfo()
    override suspend fun getSessions(): Result<List<SessionInfo>> = adminRepository.getSessions()
    override suspend fun sendMessageToSession(sessionId: String, header: String, text: String): Result<Unit> =
        adminRepository.sendMessageToSession(sessionId, header, text)
}
