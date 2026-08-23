package com.raulshma.jellyplay.feature.settings

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File

/**
 * Android actual of the [LogCollector] seam — the pre-migration
 * AboutViewModel.collectLogs body, verbatim (logcat -d --pid capture with the
 * diagnostics-header fallback write), except it returns the serialised
 * FileProvider Uri so the common ViewModel can hand it to
 * [PlatformIntents.shareLogFile] as an opaque string.
 */
internal class AndroidLogCollector(
    private val context: Context,
) : LogCollector {
    override fun collectLogs(appVersion: String, buildType: String, serverAddress: String?): String? {
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
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", logFile).toString()
    }
}
