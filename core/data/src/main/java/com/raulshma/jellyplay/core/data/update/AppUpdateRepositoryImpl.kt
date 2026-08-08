package com.raulshma.jellyplay.core.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.raulshma.jellyplay.core.model.AppUpdateInfo
import com.raulshma.jellyplay.core.network.github.GitHubReleasesApi
import com.raulshma.jellyplay.core.network.github.GitHubReleasesApiImpl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

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
        info: AppUpdateInfo,
        onProgress: (Float, Long, Long) -> Unit,
    ): Result<File> {
        val url = info.downloadAssetUrl
            ?: return Result.failure(java.io.IOException("Download failed: no asset URL"))
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
                val finalFile = File(updatesDir, OUTPUT_APK_NAME)
                // Stream into a .part file so a failed/cancelled re-download
                // leaves the previously-installed APK + sidecar untouched. The
                // swap to the final name only happens once the transfer is
                // complete — that's what keeps the "keep until install" promise
                // across re-downloads.
                val partFile = File(updatesDir, OUTPUT_PART_NAME)

                try {
                    body.byteStream().buffered().use { input ->
                        partFile.outputStream().buffered().use { output ->
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
                    // Atomically promote the complete .part to the final name,
                    // removing the old APK + sidecar first so the rename lands
                    // cleanly (Windows refuses rename onto an existing file).
                    finalFile.delete()
                    if (!partFile.renameTo(finalFile)) {
                        partFile.copyTo(finalFile, overwrite = true)
                        partFile.delete()
                    }
                    // Write the sidecar last, only after a fully-successful
                    // transfer, so a partial download can never be mistaken for
                    // a pending install on the next launch. A sidecar write
                    // failure must not undo a completed download: the APK is
                    // already in place and still installable; the worst case is
                    // the next launch's sweep can't identify it (treats it as an
                    // orphan) — preferable to deleting a usable APK.
                    runCatching { writeSidecar(info, updatesDir) }
                    Result.success(finalFile)
                } catch (e: Throwable) {
                    response.close()
                    // Only the in-flight .part is discarded; the prior APK +
                    // sidecar (if any) survive so the user can still install what
                    // they had before the failed re-download.
                    runCatching { partFile.delete() }
                    throw e
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPendingUpdate(): PendingAppUpdate? = withContext(Dispatchers.IO) {
        val updatesDir = File(context.filesDir, UPDATES_DIR)
        val apkFile = File(updatesDir, OUTPUT_APK_NAME)
        if (!apkFile.exists()) return@withContext null
        val meta = readSidecar(updatesDir) ?: return@withContext null
        // Only treat the on-disk APK as pending when its version is genuinely
        // newer than the installed build. A completed install restarts the
        // process in the new version, so equality/older means it's an orphan
        // that the sweep will reclaim — don't surface a stale "ready" state.
        if (!isNewerThanInstalled(meta.version)) {
            return@withContext null
        }
        val info = AppUpdateInfo(
            latestVersion = meta.version,
            htmlUrl = "",
            releaseNotes = "",
            isUpdateAvailable = true,
            downloadAssetUrl = meta.downloadUrl,
            downloadAssetName = meta.assetName,
            releaseSize = meta.releaseSize,
        )
        PendingAppUpdate(info = info, apkFile = apkFile)
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
        // installed version — at which point the sidecar's version is no longer
        // newer than the installed one and the APK is an orphan. Keep only a
        // genuinely pending update; sweep everything else (orphan APK, stale
        // sidecar, and any abandoned .part from a failed download). Safe at
        // startup: onCreate precedes any new download.
        val updatesDir = File(context.filesDir, UPDATES_DIR)
        val apkFile = File(updatesDir, OUTPUT_APK_NAME)
        runCatching {
            val meta = readSidecar(updatesDir)
            val keep = apkFile.exists() && meta != null &&
                isNewerThanInstalled(meta.version)
            if (!keep) {
                updatesDir.listFiles()?.forEach { it.delete() }
                if (updatesDir.exists() && updatesDir.listFiles()?.isEmpty() == true) {
                    updatesDir.delete()
                }
            } else {
                // Even when keeping a pending APK, drop any abandoned .part left
                // by a failed/cancelled re-download so it can't linger forever.
                runCatching { File(updatesDir, OUTPUT_PART_NAME).delete() }
            }
        }
    }

    /**
     * True when [version] (from the sidecar) is strictly newer than the
     * currently-installed build — i.e. the on-disk APK is a genuinely pending
     * update the user hasn't installed yet. Centralised so [getPendingUpdate]
     * and [cleanupDownloadedApk] share one definition of "pending".
     */
    private fun isNewerThanInstalled(version: String): Boolean =
        GitHubReleasesApiImpl.compareVersions(version, currentVersionName()) > 0

    /**
     * Writes [PendingUpdateMeta] derived from [info] into the sidecar file in
     * [updatesDir], atomically (write to a temp file then rename) so a crash
     * mid-write can't leave a half-written sidecar that looks valid.
     */
    private fun writeSidecar(info: AppUpdateInfo, updatesDir: File?) {
        if (updatesDir == null) return
        val meta = PendingUpdateMeta(
            version = info.latestVersion,
            downloadUrl = info.downloadAssetUrl,
            assetName = info.downloadAssetName,
            releaseSize = info.releaseSize,
            downloadedAtMs = System.currentTimeMillis(),
        )
        val target = File(updatesDir, META_NAME)
        val tmp = File(updatesDir, "$META_NAME.tmp")
        tmp.writeText(json.encodeToString(meta))
        // renameTo overwrites an existing target on both ART and the JVM's
        // File.renameTo for same-directory names; atomic on the same volume. If
        // it fails (some Windows filesystems refuse rename when the target
        // exists), fall back to a copy + delete.
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    /** Reads + decodes the sidecar in [updatesDir], or null if absent/corrupt. */
    private fun readSidecar(updatesDir: File): PendingUpdateMeta? {
        val file = File(updatesDir, META_NAME)
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<PendingUpdateMeta>(file.readText()) }
            .getOrNull()
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
        private const val OUTPUT_PART_NAME = "jellyplay-update.apk.part"
        private const val META_NAME = "jellyplay-update.meta.json"
        private const val BUFFER_SIZE = 65536
        private const val PROGRESS_INTERVAL_MS = 500L
        private const val MIME_APK = "application/vnd.android.package-archive"
    }
}
