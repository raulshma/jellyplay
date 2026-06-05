package com.raulshma.jellyplay.feature.settings

import android.content.Context
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiClient: JellyfinApiClient,
    private val authRepository: AuthRepository,
) : JellyPlayViewModel() {

    val appVersion: String by lazy {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
    }

    val buildType: String by lazy {
        val info = context.applicationInfo
        if ((info.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) "Debug" else "Release"
    }

    var serverName by composeState<String?>(null)
        private set

    var serverVersion by composeState<String?>(null)
        private set

    var serverAddress by composeState<String?>(null)
        private set

    init {
        loadServerInfo()
    }

    private fun loadServerInfo() {
        launch {
            try {
                val server = authRepository.currentServer.first()
                serverAddress = server?.address
                val systemInfo = apiClient.getSystemInfo().getOrNull()
                serverName = systemInfo?.serverName
                serverVersion = systemInfo?.version
            } catch (_: Exception) {
                serverVersion = "Unavailable"
            }
        }
    }
}
