package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.SystemInfo
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepositoryImpl @Inject constructor(
    private val apiClient: JellyfinApiClient,
) : AdminRepository {

    override suspend fun getSystemInfo(): Result<SystemInfo> = apiClient.getSystemInfo()
}
