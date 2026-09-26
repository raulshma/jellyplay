package com.raulshma.jellyplay.core.data.update

import com.raulshma.jellyplay.core.model.AppUpdateInfo
import com.raulshma.jellyplay.core.network.github.GitHubReleasesApi
import com.raulshma.jellyplay.core.network.github.UpdateSecurityException
import io.mockk.mockk
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File

/**
 * kotlin.test port of the legacy Robolectric AppUpdateRepositoryImplTest
 * (AppUpdate split): the moved ctor made the PackageManager/filesDir
 * Context stubs unnecessary — the updates dir is a temp directory and the
 * installed version is a mutable lambda capture. The download fake is an
 * application interceptor (no socket, no loopback MockWebServer — a loopback
 * origin cannot pass the compiled-in GitHubRepoAllowList the download path
 * verifies): downloads run against the REAL allow-listed github.com release
 * URL, so the production allow-list is exercised end-to-end. The
 * download/.part/sidecar logic itself is unchanged from the legacy impl.
 */
class AppUpdateRepositoryImplTest {

    companion object {
        /** The allow-listed production download shape (github.com + owner/repo path). */
        private val DOWNLOAD_URL =
            "https://github.com/raulshma/jellyplay/releases/download/v2.0/jellyplay-v2.0-phone-arm64-v8a.apk"
    }

    private lateinit var interceptor: CannedDownload
    private lateinit var repo: AppUpdateRepositoryImpl
    private lateinit var updatesDir: File

    // The installed version is now injected: any sidecar version strictly
    // greater than it (e.g. "2.0") reads as "pending"; an equal version reads
    // as "installed/orphan" — simulating a completed install where the process
    // restarted in the new build (the legacy test leaned on Robolectric's
    // versionName=null → longVersionCode="0" for the same effect).
    private var installedVersion = "1.0"
    private val orphanVersion: String get() = installedVersion

    /**
     * The download fake: canned responses from an application interceptor.
     * [boundTo] pins the canned response to a DIFFERENT request — the final
     * post-redirect shape OkHttp hands the impl after transparently
     * following github.com → release-assets CDN (or, in the attack test, an
     * off-list host).
     */
    private class CannedDownload(
        var code: Int = 200,
        var body: String = "fake-apk-bytes",
        private val boundTo: Request? = null,
    ) : Interceptor {
        val requests = mutableListOf<Request>()

        override fun intercept(chain: Interceptor.Chain): Response = Response.Builder()
            .request(boundTo ?: chain.request().also { requests += it })
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code < 300) "OK" else "Redirected")
            .body(body.toResponseBody(null))
            .build()
    }

    private fun updateInfo(version: String, url: String? = DOWNLOAD_URL) = AppUpdateInfo(
        latestVersion = version,
        htmlUrl = "https://github.com/raulshma/jellyplay/releases/tag/v$version",
        releaseNotes = "notes",
        isUpdateAvailable = true,
        downloadAssetUrl = url,
        downloadAssetName = "jellyplay-v$version-phone-arm64-v8a.apk",
        releaseSize = 0L,
    )

    @BeforeTest
    fun setUp() {
        interceptor = CannedDownload()
        updatesDir = createTempDirectory("jellyplay-update-test").toFile()
        // Start clean so tests don't see leftovers from one another.
        updatesDir.deleteRecursively()
        repo = AppUpdateRepositoryImpl(
            gitHubReleasesApi = mockk<GitHubReleasesApi>(),
            downloadClient = OkHttpClient.Builder().addInterceptor(interceptor).build(),
            updatesDir = updatesDir,
            currentVersionName = { installedVersion },
            flavor = "phone",
            supportedAbis = arrayOf("arm64-v8a"),
        )
    }

    @AfterTest
    fun tearDown() {
        updatesDir.deleteRecursively()
    }

    @Test
    fun `download writes apk and sidecar`() = runBlocking {
        val result = repo.downloadUpdate(updateInfo("2.0"))

        assertTrue(result.isSuccess)
        val apk = File(updatesDir, "jellyplay-update.apk")
        assertTrue(apk.exists())
        assertEquals("fake-apk-bytes", apk.readText())
        // Sidecar present with the recorded version.
        val sidecar = File(updatesDir, "jellyplay-update.meta.json")
        assertTrue(sidecar.exists())
        assertTrue(sidecar.readText().contains("\"version\":\"2.0\""))
        // Sanity: the request went to the pinned download URL.
        assertEquals(DOWNLOAD_URL, interceptor.requests.single().url.toString())
    }

    @Test
    fun `download fails closed on a poisoned asset url before anything streams`() = runBlocking {
        // The poisoned-cache defense: a tampered/cached AppUpdateInfo
        // whose asset URL is off the compiled-in allow-list must be rejected
        // BEFORE the stream opens (no request, no .part, no sidecar).
        val result = repo.downloadUpdate(updateInfo("2.0", url = "https://evil.example.com/apk.apk"))

        val error = result.exceptionOrNull()
        assertIs<UpdateSecurityException>(error)
        assertFalse(File(updatesDir, "jellyplay-update.apk").exists())
        assertTrue(interceptor.requests.isEmpty(), "the poisoned URL must never reach the wire")
    }

    @Test
    fun `download fails closed when the redirect chain lands off the allow-list`() = runBlocking {
        // Simulates OkHttp having followed the download redirect onto a host
        // outside ALLOWED_ASSET_HOSTS: the final URL is checked before a byte
        // is written.
        val redirectInterceptor = CannedDownload(
            boundTo = Request.Builder().url("https://evil.example.com/apk.apk").build(),
        )
        repo = AppUpdateRepositoryImpl(
            gitHubReleasesApi = mockk<GitHubReleasesApi>(),
            downloadClient = OkHttpClient.Builder().addInterceptor(redirectInterceptor).build(),
            updatesDir = updatesDir,
            currentVersionName = { installedVersion },
            flavor = "phone",
            supportedAbis = arrayOf("arm64-v8a"),
        )

        val result = repo.downloadUpdate(updateInfo("2.0"))

        val error = result.exceptionOrNull()
        assertIs<UpdateSecurityException>(error)
        assertTrue(error.message!!.contains("redirected"), "unexpected message: $error")
        assertFalse(File(updatesDir, "jellyplay-update.apk").exists())
        assertFalse(File(updatesDir, "jellyplay-update.apk.part").exists())
    }

    @Test
    fun `getPendingUpdate returns pending when sidecar newer than installed`() = runBlocking {
        repo.downloadUpdate(updateInfo("2.0"))

        val pending = repo.getPendingUpdate()

        assertNotNull(pending)
        assertEquals("2.0", pending.info.latestVersion)
        assertTrue(pending.apkFile.exists())
        assertTrue(pending.info.isUpdateAvailable)
    }

    @Test
    fun `getPendingUpdate returns null when sidecar version not newer`() = runBlocking {
        // Equal to installed: simulates a completed install where the process
        // restarted in the new build (sidecar version now <= installed).
        repo.downloadUpdate(updateInfo(orphanVersion))

        assertNull(repo.getPendingUpdate())
    }

    @Test
    fun `getPendingUpdate returns null when apk missing`() = runBlocking {
        repo.downloadUpdate(updateInfo("2.0"))
        // Delete the APK but leave the sidecar — must not surface a ghost state.
        File(updatesDir, "jellyplay-update.apk").delete()

        assertNull(repo.getPendingUpdate())
    }

    @Test
    fun `cleanup keeps genuinely pending apk`() = runBlocking {
        repo.downloadUpdate(updateInfo("2.0"))

        repo.cleanupDownloadedUpdate()

        assertTrue(File(updatesDir, "jellyplay-update.apk").exists())
        assertTrue(File(updatesDir, "jellyplay-update.meta.json").exists())
    }

    @Test
    fun `cleanup deletes orphan when version is not newer`() = runBlocking {
        repo.downloadUpdate(updateInfo(orphanVersion))

        repo.cleanupDownloadedUpdate()

        assertFalse(File(updatesDir, "jellyplay-update.apk").exists())
        assertFalse(File(updatesDir, "jellyplay-update.meta.json").exists())
    }

    @Test
    fun `cleanup deletes orphan apk without sidecar`() {
        // A leftover APK with no sidecar (e.g. from an older app version that
        // didn't write one) must be swept, not retained forever.
        updatesDir.mkdirs()
        File(updatesDir, "jellyplay-update.apk").writeText("stale")

        repo.cleanupDownloadedUpdate()

        assertFalse(File(updatesDir, "jellyplay-update.apk").exists())
    }

    @Test
    fun `redownload overwrites prior apk and sidecar`() = runBlocking {
        // First download: version 2.0.
        repo.downloadUpdate(updateInfo("2.0"))
        // Second download: version 3.0, reusing the same output path.
        interceptor.body = "apk-v3"
        repo.downloadUpdate(updateInfo("3.0", url = DOWNLOAD_URL.replace("/v2.0/", "/v3.0/")))

        val apk = File(updatesDir, "jellyplay-update.apk")
        assertEquals("apk-v3", apk.readText())
        val sidecar = File(updatesDir, "jellyplay-update.meta.json")
        assertTrue(sidecar.readText().contains("\"version\":\"3.0\""))
        // Only one APK + one sidecar in the dir — no leftovers.
        assertEquals(2, updatesDir.listFiles()?.size)
    }

    @Test
    fun `failed download deletes apk and leaves no sidecar`() = runBlocking {
        interceptor.code = 500

        val result = repo.downloadUpdate(updateInfo("2.0"))

        assertTrue(result.isFailure)
        // No APK, no sidecar — nothing for the next launch to mistake as pending.
        assertFalse(File(updatesDir, "jellyplay-update.apk").exists())
        assertFalse(File(updatesDir, "jellyplay-update.meta.json").exists())
    }
}
