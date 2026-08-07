package com.raulshma.jellyplay.core.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.raulshma.jellyplay.core.model.AppUpdateInfo
import com.raulshma.jellyplay.core.network.github.GitHubReleasesApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class AppUpdateRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gitHubReleasesApi: GitHubReleasesApi,
    // Pre-tuned download client (connect=30s, read=60s, write=30s). Cloned
    // with cache=null so a large APK isn't written through the HTTP disk
    // cache — mirroring how the image client is built in JellyPlayApplication.
    @Named("download") private val downloadClient: OkHttpClient,
) : AppUpdateRepository {

    override suspend fun checkForUpdate(supportedAbis: Array<String>): Result<AppUpdateInfo> {
        val current = currentVersionName()
        return gitHubReleasesApi.fetchLatestUpdate(
            currentVersionName = current,
            flavor = currentFlavor(),
            supportedAbis = supportedAbis,
        )
    }

    /**
     * Derives the running product flavor from the package name. Library modules
     * cannot read `Build.FLAVOR` (it is empty outside the :app module's
     * flavor), so this is the single source of truth shared by every caller.
     * TV builds carry a `.tv` applicationId suffix (see app/build.gradle.kts).
     */
    private fun currentFlavor(): String {
        val packageName = context.packageName
        return if (packageName.endsWith(".tv")) "tv" else "phone"
    }

    override suspend fun downloadApk(
        url: String,
        onProgress: (Float, Long, Long) -> Unit,
    ): Result<File> {
        // Clone once with no cache. A binary APK must not pollute the shared
        // JSON/asset cache, and we want a clean connection for streaming.
        val client = downloadClient.newBuilder()
            .cache(null)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "JellyPlay/Update")
            .get()
            .build()

        return try {
            withContext(Dispatchers.IO) {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    return@withContext Result.failure<File>(
                        java.io.IOException("Download failed: HTTP ${response.code}"),
                    )
                }
                val total = response.body?.contentLength()?.coerceAtLeast(0L) ?: 0L
                val body = response.body ?: run {
                    return@withContext Result.failure<File>(
                        java.io.IOException("Download failed: empty body"),
                    )
                }

                // Persist under filesDir (NOT cacheDir): the APK must survive
                // backgrounding during the system-installer round trip. cacheDir
                // is swept by CacheManager on ON_STOP (autoDeleteCache) and can
                // be evicted by the OS at any time — both delete the APK mid
                // install, producing "There was a problem parsing the package".
                val updatesDir = File(context.filesDir, UPDATES_DIR).apply { mkdirs() }
                // Clean up any previously downloaded APK so a stale/partial file
                // never masquerades as the new build.
                updatesDir.listFiles()?.forEach { runCatching { it.delete() } }
                val outFile = File(updatesDir, OUTPUT_APK_NAME)

                try {
                    body.byteStream().buffered().use { input ->
                        outFile.outputStream().buffered().use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var bytesRead: Int
                            var downloaded = 0L
                            var lastReport = 0L
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloaded += bytesRead
                                // Throttle progress callbacks to avoid flooding the
                                // main thread through the collector.
                                val now = System.currentTimeMillis()
                                if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                                    lastReport = now
                                    val fraction = if (total > 0) downloaded.toFloat() / total else 0f
                                    onProgress(fraction, downloaded, total)
                                }
                                ensureActive()
                            }
                            onProgress(if (total > 0) 1f else 0f, downloaded, total)
                        }
                    }
                    response.close()
                    Result.success(outFile)
                } catch (e: Throwable) {
                    response.close()
                    runCatching { outFile.delete() }
                    throw e
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun buildInstallIntent(apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_APK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    override fun cleanupDownloadedApk() {
        // The system installer is launched in a separate process; we get no
        // result callback for ACTION_VIEW. But a successful package replace
        // kills and restarts our process, so onCreate runs again in the newly
        // installed version — at which point any previously-downloaded APK is
        // orphaned. Sweeping it here on startup removes those leftovers without
        // racing an in-flight download (onCreate precedes any user action).
        val updatesDir = File(context.filesDir, UPDATES_DIR)
        runCatching {
            updatesDir.listFiles()?.forEach { it.delete() }
            if (updatesDir.exists() && updatesDir.listFiles()?.isEmpty() == true) {
                updatesDir.delete()
            }
        }
    }

    private fun currentVersionName(): String {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        // Prefer the long version code as the source of truth; fall back to
        // versionName for the human-readable dotted string used by the
        // comparator. versionName is what the build injects via -PversionName.
        @Suppress("DEPRECATION")
        return info.versionName ?: PackageInfoCompat.getLongVersionCode(info).toString()
    }

    companion object {
        private const val UPDATES_DIR = "updates"
        private const val OUTPUT_APK_NAME = "jellyplay-update.apk"
        private const val BUFFER_SIZE = 65536
        private const val PROGRESS_INTERVAL_MS = 500L
        private const val MIME_APK = "application/vnd.android.package-archive"
    }
}
