package com.raulshma.jellyplay.core.network.github

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleasesApiImplTest {

    // ---- selectAsset ----

    private fun asset(name: String, url: String = "https://x/$name", size: Long = 1L) =
        GitHubReleaseAsset(name = name, browserDownloadUrl = url, size = size)

    @Test
    fun `selectAsset prefers exact flavor and ABI match`() {
        val assets = listOf(
            asset("jellyplay-v1.2.3-phone-arm64-v8a.apk"),
            asset("jellyplay-v1.2.3-phone-universal.apk"),
            asset("jellyplay-v1.2.3-tv-arm64-v8a.apk"),
        )
        val chosen = GitHubReleasesApiImpl.selectAsset(assets, "1.2.3", "phone", arrayOf("arm64-v8a"))
        assertEquals("jellyplay-v1.2.3-phone-arm64-v8a.apk", chosen?.name)
    }

    @Test
    fun `selectAsset picks most preferred ABI when several supported`() {
        val assets = listOf(
            asset("jellyplay-v1.2.3-phone-x86_64.apk"),
            asset("jellyplay-v1.2.3-phone-arm64-v8a.apk"),
        )
        // arm64-v8a listed first → wins despite x86_64 also present.
        val chosen = GitHubReleasesApiImpl.selectAsset(
            assets, "1.2.3", "phone", arrayOf("arm64-v8a", "x86_64"),
        )
        assertEquals("jellyplay-v1.2.3-phone-arm64-v8a.apk", chosen?.name)
    }

    @Test
    fun `selectAsset falls back to universal when ABI build missing`() {
        val assets = listOf(
            asset("jellyplay-v1.2.3-phone-x86_64.apk"),
            asset("jellyplay-v1.2.3-phone-universal.apk"),
        )
        val chosen = GitHubReleasesApiImpl.selectAsset(assets, "1.2.3", "phone", arrayOf("arm64-v8a"))
        assertEquals("jellyplay-v1.2.3-phone-universal.apk", chosen?.name)
    }

    @Test
    fun `selectAsset falls back to any universal asset`() {
        val assets = listOf(
            asset("jellyplay-v1.2.3-tv-universal.apk"),
            asset("jellyplay-v1.2.3-source.zip"),
        )
        // No phone asset at all → last resort is the tv universal.
        val chosen = GitHubReleasesApiImpl.selectAsset(assets, "1.2.3", "phone", arrayOf("arm64-v8a"))
        assertEquals("jellyplay-v1.2.3-tv-universal.apk", chosen?.name)
    }

    @Test
    fun `selectAsset returns null when no apk asset exists`() {
        val assets = listOf(asset("jellyplay-v1.2.3-source.zip"))
        val chosen = GitHubReleasesApiImpl.selectAsset(assets, "1.2.3", "phone", arrayOf("arm64-v8a"))
        assertNull(chosen)
    }

    @Test
    fun `selectAsset matches case-insensitively`() {
        val assets = listOf(asset("JELLYPLAY-v1.2.3-PHONE-ARM64-V8A.apk"))
        val chosen = GitHubReleasesApiImpl.selectAsset(assets, "1.2.3", "phone", arrayOf("arm64-v8a"))
        assertEquals("JELLYPLAY-v1.2.3-PHONE-ARM64-V8A.apk", chosen?.name)
    }

    // ---- fetchLatestUpdate deserialization (regression for the snake_case
    // @SerialName bug that made tagName/html_url/browser_download_url come back
    // null, so every update check reported "already latest") ----

    private val sampleGithubReleaseJson = """
        {
          "tag_name": "v1.2.3",
          "html_url": "https://github.com/raulshma/jellyplay/releases/tag/v1.2.3",
          "body": "Release notes here",
          "assets": [
            {
              "name": "jellyplay-v1.2.3-phone-arm64-v8a.apk",
              "browser_download_url": "https://example.com/jellyplay-v1.2.3-phone-arm64-v8a.apk",
              "size": 5242880
            },
            {
              "name": "jellyplay-v1.2.3-phone-universal.apk",
              "browser_download_url": "https://example.com/jellyplay-v1.2.3-phone-universal.apk",
              "size": 10485760
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `fetchLatestUpdate parses snake_case GitHub fields and flags an available update`() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setBody(sampleGithubReleaseJson))
            start()
        }
        val api = GitHubReleasesApiImpl(OkHttpClient(), server.url("/").toString())

        val result = api.fetchLatestUpdate(
            currentVersionName = "1.0.0",
            flavor = "phone",
            supportedAbis = arrayOf("arm64-v8a"),
        )

        // Regression: before @SerialName, tagName deserialized to null → tag ""
        // → compareVersions("", "1.0.0") = -1 → isUpdateAvailable false.
        val info = result.getOrThrow()
        assertEquals("1.2.3", info.latestVersion)
        assertNotNull(info.htmlUrl)
        assertTrue(info.htmlUrl!!.contains("releases/tag/v1.2.3"))
        assertTrue("expected an update to be flagged for 1.0.0 vs 1.2.3", info.isUpdateAvailable)
        assertNotNull(info.downloadAssetUrl)
        assertEquals("jellyplay-v1.2.3-phone-arm64-v8a.apk", info.downloadAssetName)
        assertEquals(5242880L, info.releaseSize)

        // Sanity: the request actually hit our mocked endpoint.
        val recorded = server.takeRequest()
        assertEquals("/", recorded.path)
        server.shutdown()
    }

    @Test
    fun `fetchLatestUpdate reports no update when installed version equals the tag`() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setBody(sampleGithubReleaseJson))
            start()
        }
        val api = GitHubReleasesApiImpl(OkHttpClient(), server.url("/").toString())

        val info = api.fetchLatestUpdate(
            currentVersionName = "1.2.3",
            flavor = "phone",
            supportedAbis = arrayOf("arm64-v8a"),
        ).getOrThrow()

        // Equal versions must not flag an update, AND the tag must still be
        // parsed (not silently null as in the bug).
        assertEquals("1.2.3", info.latestVersion)
        assertTrue(!info.isUpdateAvailable)
        server.shutdown()
    }
}
