package com.raulshma.jellyplay.core.data.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.model.AppUpdateInfo
import com.raulshma.jellyplay.core.network.github.GitHubReleasesApi
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Robolectric tests for the sidecar + version-aware-sweep behaviour backing the
 * "keep downloaded APK until install, allow re-download" feature. Uses a real
 * filesDir (via ApplicationProvider) and a MockWebServer to exercise the actual
 * download path end-to-end. The installed version is faked by stubbing the
 * package manager's PackageInfo (Robolectric returns versionName="1.0" by
 * default for the test application).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppUpdateRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var repo: AppUpdateRepositoryImpl
    private lateinit var updatesDir: File

    // Under Robolectric the test app has no explicit versionName/versionCode, so
    // currentVersionName() falls back to longVersionCode = "0". Any sidecar
    // version strictly greater than 0 (e.g. "2.0") reads as "pending"; an equal
    // version ("0") reads as "installed/orphan" — simulating a completed install
    // where the process restarted in the new build.
    private val orphanVersion = "0"

    private fun updateInfo(version: String, url: String? = null) = AppUpdateInfo(
        latestVersion = version,
        htmlUrl = "https://example.com/release",
        releaseNotes = "notes",
        isUpdateAvailable = true,
        downloadAssetUrl = url,
        downloadAssetName = "jellyplay-v$version-phone-arm64-v8a.apk",
        releaseSize = 0L,
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        updatesDir = File(context.filesDir, "updates")
        // Start clean so tests don't see leftovers from one another.
        updatesDir.deleteRecursively()
        repo = AppUpdateRepositoryImpl(
            context = context,
            gitHubReleasesApi = mockk<GitHubReleasesApi>(),
            downloadClient = OkHttpClient(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        updatesDir.deleteRecursively()
    }

    @Test
    fun `download writes apk and sidecar`() = runBlocking {
        server.enqueue(MockResponse().setBody("fake-apk-bytes"))
        val info = updateInfo("2.0", server.url("/apk").toString())

        val result = repo.downloadApk(info)

        assertTrue(result.isSuccess)
        val apk = File(updatesDir, "jellyplay-update.apk")
        assertTrue(apk.exists())
        assertEquals("fake-apk-bytes", apk.readText())
        // Sidecar present with the recorded version.
        val sidecar = File(updatesDir, "jellyplay-update.meta.json")
        assertTrue(sidecar.exists())
        assertTrue(sidecar.readText().contains("\"version\":\"2.0\""))
    }

    @Test
    fun `getPendingUpdate returns pending when sidecar newer than installed`() = runBlocking {
        server.enqueue(MockResponse().setBody("apk"))
        val info = updateInfo("2.0", server.url("/apk").toString())
        repo.downloadApk(info)

        val pending = repo.getPendingUpdate()

        assertNotNull(pending)
        assertEquals("2.0", pending!!.info.latestVersion)
        assertTrue(pending.apkFile.exists())
        assertTrue(pending.info.isUpdateAvailable)
    }

    @Test
    fun `getPendingUpdate returns null when sidecar version not newer`() = runBlocking {
        server.enqueue(MockResponse().setBody("apk"))
        // Older than installed: simulates a completed install where the process
        // restarted in the new build (sidecar version now <= installed).
        val info = updateInfo(orphanVersion, server.url("/apk").toString())
        repo.downloadApk(info)

        val pending = repo.getPendingUpdate()

        assertNull(pending)
    }

    @Test
    fun `getPendingUpdate returns null when apk missing`() = runBlocking {
        server.enqueue(MockResponse().setBody("apk"))
        repo.downloadApk(updateInfo("2.0", server.url("/apk").toString()))
        // Delete the APK but leave the sidecar — must not surface a ghost state.
        File(updatesDir, "jellyplay-update.apk").delete()

        assertNull(repo.getPendingUpdate())
    }

    @Test
    fun `cleanup keeps genuinely pending apk`() = runBlocking {
        server.enqueue(MockResponse().setBody("apk"))
        repo.downloadApk(updateInfo("2.0", server.url("/apk").toString()))

        repo.cleanupDownloadedApk()

        assertTrue(File(updatesDir, "jellyplay-update.apk").exists())
        assertTrue(File(updatesDir, "jellyplay-update.meta.json").exists())
    }

    @Test
    fun `cleanup deletes orphan when version is not newer`() = runBlocking {
        server.enqueue(MockResponse().setBody("apk"))
        repo.downloadApk(updateInfo(orphanVersion, server.url("/apk").toString()))

        repo.cleanupDownloadedApk()

        assertFalse(File(updatesDir, "jellyplay-update.apk").exists())
        assertFalse(File(updatesDir, "jellyplay-update.meta.json").exists())
    }

    @Test
    fun `cleanup deletes orphan apk without sidecar`() = runBlocking {
        // A leftover APK with no sidecar (e.g. from an older app version that
        // didn't write one) must be swept, not retained forever.
        updatesDir.mkdirs()
        File(updatesDir, "jellyplay-update.apk").writeText("stale")

        repo.cleanupDownloadedApk()

        assertFalse(File(updatesDir, "jellyplay-update.apk").exists())
    }

    @Test
    fun `redownload overwrites prior apk and sidecar`() = runBlocking {
        // First download: version 2.0.
        server.enqueue(MockResponse().setBody("apk-v2"))
        repo.downloadApk(updateInfo("2.0", server.url("/apk").toString()))
        // Second download: version 3.0, reusing the same output path.
        server.enqueue(MockResponse().setBody("apk-v3"))
        repo.downloadApk(updateInfo("3.0", server.url("/apk").toString()))

        val apk = File(updatesDir, "jellyplay-update.apk")
        assertEquals("apk-v3", apk.readText())
        val sidecar = File(updatesDir, "jellyplay-update.meta.json")
        assertTrue(sidecar.readText().contains("\"version\":\"3.0\""))
        // Only one APK + one sidecar in the dir — no leftovers.
        assertEquals(2, updatesDir.listFiles()?.size)
    }

    @Test
    fun `failed download deletes apk and leaves no sidecar`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val info = updateInfo("2.0", server.url("/apk").toString())

        val result = repo.downloadApk(info)

        assertTrue(result.isFailure)
        // No APK, no sidecar — nothing for the next launch to mistake as pending.
        assertFalse(File(updatesDir, "jellyplay-update.apk").exists())
        assertFalse(File(updatesDir, "jellyplay-update.meta.json").exists())
    }
}
