package com.raulshma.jellyplay.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.model.UpdateDismissPeriod
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adminRepository: AdminRepository,
    private val authRepository: AuthRepository,
    private val experimentalStore: ExperimentalStore,
) : JellyPlayViewModel() {

    val appVersion: String by lazy {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
    }

    val buildType: String by lazy {
        val info = context.applicationInfo
        if ((info.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) "Debug" else "Release"
    }

    // Min/target SDK info derived at runtime from the application info so the About screen
    // never drifts from the actual build configuration in app/build.gradle.kts.
    private val appInfo get() = context.applicationInfo
    val minSdkInfo: String get() = "API ${appInfo.minSdkVersion}"
    val targetSdkInfo: String get() = "API ${appInfo.targetSdkVersion}"

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

    var updateDismissPeriod by composeState(UpdateDismissPeriod.DEFAULT)
        private set

    init {
        loadServerInfo()
        launch {
            experimentalStore.experimental.collect { prefs ->
                selfUpdateCheckEnabled = prefs.selfUpdateCheckEnabled
                selfUpdateDownloadEnabled = prefs.selfUpdateDownloadEnabled
                updateDismissPeriod = prefs.updateDismissPeriod
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

    fun sendAppLogs(onResult: (Uri?) -> Unit) {
        launch {
            isCollectingLogs = true
            try {
                val uri = withContext(Dispatchers.IO) { collectLogs() }
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

    fun updateDismissPeriodPref(period: UpdateDismissPeriod) {
        launch { experimentalStore.setUpdateDismissPeriod(period) }
    }

    private fun collectLogs(): Uri? {
        val logDir = File(context.cacheDir, "shared_logs").apply { mkdirs() }
        val logFile = File(logDir, "jellyplay_logs_${System.currentTimeMillis()}.txt")
        val pid = android.os.Process.myPid()
        try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "--pid=$pid", "-v", "time"))
            process.inputStream.use { input ->
                logFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (_: Exception) {
            logFile.writeText("Unable to capture logcat output. This may require logcat permission.\n\nApp: JellyPlay $appVersion ($buildType)\nServer: ${serverAddress ?: "Not connected"}")
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", logFile)
    }
}
