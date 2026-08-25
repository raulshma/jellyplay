package com.raulshma.jellyplay.core.data.update

import com.raulshma.jellyplay.core.model.AppUpdateInfo
import com.raulshma.jellyplay.core.network.github.GitHubReleasesApi
import io.mockk.mockk
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.File

/**
 * kotlin.test port of the legacy Robolectric AppUpdateRepositoryImplTest
 * (AppUpdate split, Wave xB): the moved ctor made the PackageManager/filesDir
 * Context stubs unnecessary — the updates dir is a temp directory and the
 * installed version is a mutable lambda capture. Uses a MockWebServer to
 * exercise the actual download path end-to-end; download/.part/sidecar logic
 * is unchanged from the legacy impl.
 */
class AppUpdateRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var repo: AppUpdateRepositoryImpl
    private lateinit var updatesDir: File

    // The installed version is now injected: any sidecar version strictly
    // greater than it (e.g. "2.0") reads as "pending"; an equal version reads
    // as "installed/orphan" — simulating a completed install where the process
    // restarted in the new build (the legacy test leaned on Robolectric's
    // versionName=null → longVersionCode="0" for the same effect).
    private var installedVersion = "1.0"
    private val orphanVersion: String get() = installedVersion

    private fun updateInfo(version: String, url: String? = null) = AppUpdateInfo(
        latestVersion = version,
        htmlUrl = "https://example.com/release",
        releaseNotes = "notes",
        isUpdateAvailable = true,
        downloadAssetUrl = url,
        downloadAssetName = "jellyplay-v$version-phone-arm64-v8a.apk",
        releaseSize = 0L,
    )

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        updatesDir = createTempDirectory("jellyplay-update-test").toFile()
        // Start clean so tests don't see leftovers from one another.
        updatesDir.deleteRecursively()
        repo = AppUpdateRepositoryImpl(
            gitHubReleasesApi = mockk<GitHubReleasesApi>(),
            downloadClient = OkHttpClient(),
            updatesDir = updatesDir,
            currentVersionName = { installedVersion },
            flavor = "phone",
            supportedAbis = arrayOf("arm64-v8a"),
        )
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
        updatesDir.deleteRecursively()
    }

    @Test
    fun `download writes apk and sidecar`() = runBlocking {
        server.enqueue(MockResponse().setBody("fake-apk-bytes"))
        val info = updateInfo("2.0", server.url("/apk").toString())

        val result = repo.downloadUpdate(info)

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
        repo.downloadUpdate(info)

        val pending = repo.getPendingUpdate()

        assertNotNull(pending)
        assertEquals("2.0", pending.info.latestVersion)
        assertTrue(pending.apkFile.exists())
        assertTrue(pending.info.isUpdateAvailable)
    }

    @Test
    fun `getPendingUpdate returns null when sidecar version not newer`() = runBlocking {
        server.enqueue(MockResponse().setBody("apk"))
        // Equal to installed: simulates a completed install where the process
        // restarted in the new build (sidecar version now <= installed).
        val info = updateInfo(orphanVersion, server.url("/apk").toString())
        repo.downloadUpdate(info)

        val pending = repo.getPendingUpdate()

        assertNull(pending)
    }

    @Test
    fun `getPendingUpdate returns null when apk missing`() = runBlocking {
        server.enqueue(MockResponse().setBody("apk"))
        repo.downloadUpdate(updateInfo("2.0", server.url("/apk").toString()))
        // Delete the APK but leave the sidecar — must not surface a ghost state.
        File(updatesDir, "jellyplay-update.apk").delete()

        assertNull(repo.getPendingUpdate())
    }

    @Test
    fun `cleanup keeps genuinely pending apk`() = runBlocking {
        server.enqueue(MockResponse().setBody("apk"))
        repo.downloadUpdate(updateInfo("2.0", server.url("/apk").toString()))

        repo.cleanupDownloadedUpdate()

        assertTrue(File(updatesDir, "jellyplay-update.apk").exists())
        assertTrue(File(updatesDir, "jellyplay-update.meta.json").exists())
    }

    @Test
    fun `cleanup deletes orphan when version is not newer`() = runBlocking {
        server.enqueue(MockResponse().setBody("apk"))
        repo.downloadUpdate(updateInfo(orphanVersion, server.url("/apk").toString()))

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
        server.enqueue(MockResponse().setBody("apk-v2"))
        repo.downloadUpdate(updateInfo("2.0", server.url("/apk").toString()))
        // Second download: version 3.0, reusing the same output path.
        server.enqueue(MockResponse().setBody("apk-v3"))
        repo.downloadUpdate(updateInfo("3.0", server.url("/apk").toString()))

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

        val result = repo.downloadUpdate(info)

        assertTrue(result.isFailure)
        // No APK, no sidecar — nothing for the next launch to mistake as pending.
        assertFalse(File(updatesDir, "jellyplay-update.apk").exists())
        assertFalse(File(updatesDir, "jellyplay-update.meta.json").exists())
    }
}
