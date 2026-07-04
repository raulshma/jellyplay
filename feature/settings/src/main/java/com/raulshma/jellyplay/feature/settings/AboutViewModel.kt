package com.raulshma.jellyplay.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.network.JellyfinApiClient
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
    private val apiClient: JellyfinApiClient,
    private val authRepository: AuthRepository,
    private val preferencesStore: UserPreferencesStore,
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

    var isCheckingUpdate by composeState(false)
        private set

    var updateInfo by composeState<UpdateInfo?>(null)
        private set

    var selfUpdateCheckEnabled by composeState(true)
        private set

    init {
        loadServerInfo()
        launch {
            preferencesStore.preferences.collect { prefs ->
                selfUpdateCheckEnabled = prefs.selfUpdateCheckEnabled
            }
        }
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

    fun checkForUpdate() {
        if (!selfUpdateCheckEnabled) return
        launch {
            isCheckingUpdate = true
            try {
                val info = withContext(Dispatchers.IO) { fetchLatestRelease() }
                updateInfo = info
            } catch (_: Exception) {
                updateInfo = null
            } finally {
                isCheckingUpdate = false
            }
        }
    }

    fun updateSelfUpdateCheckPref(enabled: Boolean) {
        launch { preferencesStore.setSelfUpdateCheckEnabled(enabled) }
    }

    private fun fetchLatestRelease(): UpdateInfo {
        val url = java.net.URL("https://api.github.com/repos/raulshma/jellyplay/releases/latest")
        val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github.v3+json")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        conn.inputStream.bufferedReader().use { reader ->
            val text = reader.readText()
            val json = org.json.JSONObject(text)
            val tagName = json.optString("tag_name", "").removePrefix("v")
            val htmlUrl = json.optString("html_url", "")
            val body = json.optString("body", "")
            val isUpdateAvailable = compareVersions(tagName, appVersion) > 0
            return UpdateInfo(
                latestVersion = tagName,
                downloadUrl = htmlUrl,
                releaseNotes = body,
                isUpdateAvailable = isUpdateAvailable,
            )
        }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
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

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val isUpdateAvailable: Boolean,
)
