package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class AboutViewModel(
    private val appMetaProvider: AppMetaProvider,
    private val logCollector: LogCollector,
    private val adminRepository: AdminRepository,
    private val authRepository: AuthRepository,
    private val experimentalStore: ExperimentalStore,
) : JellyPlayViewModel() {

    val appVersion: String by lazy {
        appMetaProvider.versionName ?: "1.0"
    }

    val buildType: String by lazy {
        if (appMetaProvider.isDebugBuild) "Debug" else "Release"
    }

    // Min/target SDK info derived at runtime from the platform seam so the About screen
    // never drifts from the actual build configuration in app/build.gradle.kts.
    val minSdkInfo: String get() = "API ${appMetaProvider.minSdk}"
    val targetSdkInfo: String get() = "API ${appMetaProvider.targetSdk}"

    var serverName by composeState<String?>(null)
        private set

    var serverVersion by composeState<String?>(null)
        private set

    var serverAddress by composeState<String?>(null)
        private set

    var isCollectingLogs by composeState(false)
        private set

    var selfUpdateCheckEnabled by composeState(true)
        private set

    var selfUpdateDownloadEnabled by composeState(false)
        private set

    init {
        loadServerInfo()
        launch {
            experimentalStore.experimental.collect { prefs ->
                selfUpdateCheckEnabled = prefs.selfUpdateCheckEnabled
                selfUpdateDownloadEnabled = prefs.selfUpdateDownloadEnabled
            }
        }
    }

    private fun loadServerInfo() {
        launch {
            try {
                val server = authRepository.currentServer.first()
                serverAddress = server?.address
                val systemInfo = adminRepository.getSystemInfo().getOrNull()
                serverName = systemInfo?.serverName
                serverVersion = systemInfo?.version
            } catch (_: Exception) {
                serverVersion = "Unavailable"
            }
        }
    }

    /** Collects logs and hands the opaque shareable file reference (or null) to [onResult]. */
    fun sendAppLogs(onResult: (String?) -> Unit) {
        launch {
            isCollectingLogs = true
            try {
                val uri = withContext(Dispatchers.IO) {
                    logCollector.collectLogs(appVersion, buildType, serverAddress)
                }
                onResult(uri)
            } catch (_: Exception) {
                onResult(null)
            } finally {
                isCollectingLogs = false
            }
        }
    }

    fun updateSelfUpdateCheckPref(enabled: Boolean) {
        launch { experimentalStore.setSelfUpdateCheckEnabled(enabled) }
    }

    fun updateSelfUpdateDownloadPref(enabled: Boolean) {
        launch { experimentalStore.setSelfUpdateDownloadEnabled(enabled) }
    }
}
