package com.raulshma.jellyplay.core.network.github

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubReleasesApiImplTest {

    // ---- selectAsset ----

    private fun asset(name: String, url: String = "https://github.com/raulshma/jellyplay/releases/download/v1.2.3/$name", size: Long = 1L) =
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

    // ---- fetchLatestUpdate deserialization + the allow-list gates.
    // The canned responses come from an application interceptor against the
    // REAL LATEST_RELEASE_URL — never a loopback MockWebServer, because a
    // loopback origin cannot be on the compiled-in GitHubRepoAllowList. ----

    /** snake_case fixture, regression-pinned from the @SerialName bug era. */
    private val sampleGithubReleaseJson = """
        {
          "tag_name": "v1.2.3",
          "html_url": "https://github.com/raulshma/jellyplay/releases/tag/v1.2.3",
          "body": "Release notes here",
          "assets": [
            {
              "name": "jellyplay-v1.2.3-phone-arm64-v8a.apk",
              "browser_download_url": "https://github.com/raulshma/jellyplay/releases/download/v1.2.3/jellyplay-v1.2.3-phone-arm64-v8a.apk",
              "size": 5242880
            },
            {
              "name": "jellyplay-v1.2.3-phone-universal.apk",
              "browser_download_url": "https://github.com/raulshma/jellyplay/releases/download/v1.2.3/jellyplay-v1.2.3-phone-universal.apk",
              "size": 10485760
            }
          ]
        }
    """.trimIndent()

    /**
     * Answers every call with a canned 200 carrying [body]. Application-level
     * (above the retry/follow-up machinery) so no socket is ever opened; for
     * the moved-repo test [boundTo] pins the canned response to a DIFFERENT
     * request — exactly the final post-redirect shape OkHttp hands the impl
     * after transparently following a 301.
     */
    private class CannedGitHub(
        private val body: String,
        private val boundTo: Request? = null,
    ) : Interceptor {
        val requests = mutableListOf<Request>()

        override fun intercept(chain: Interceptor.Chain): Response = Response.Builder()
            .request(boundTo ?: chain.request().also { requests += it })
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private class CannedApi(val api: GitHubReleasesApiImpl, val interceptor: CannedGitHub)

    private fun canned(body: String, boundTo: Request? = null): CannedApi {
        val interceptor = CannedGitHub(body, boundTo)
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        return CannedApi(GitHubReleasesApiImpl(client), interceptor)
    }

    private suspend fun fetchFrom(api: GitHubReleasesApiImpl, currentVersionName: String = "1.0.0") =
        api.fetchLatestUpdate(
            currentVersionName = currentVersionName,
            flavor = "phone",
            supportedAbis = arrayOf("arm64-v8a"),
        )

    @Test
    fun `fetchLatestUpdate parses snake_case GitHub fields and flags an available update`() = runBlocking {
        val fixture = canned(sampleGithubReleaseJson)

        // Regression: before @SerialName, tagName deserialized to null → tag ""
        // → compareVersions("", "1.0.0") = -1 → isUpdateAvailable false.
        val info = fetchFrom(fixture.api).getOrThrow()
        assertEquals("1.2.3", info.latestVersion)
        assertTrue(info.htmlUrl.contains("releases/tag/v1.2.3"))
        assertTrue(info.isUpdateAvailable, "expected an update to be flagged for 1.0.0 vs 1.2.3")
        assertEquals(
            "https://github.com/raulshma/jellyplay/releases/download/v1.2.3/jellyplay-v1.2.3-phone-arm64-v8a.apk",
            info.downloadAssetUrl,
        )
        assertEquals("jellyplay-v1.2.3-phone-arm64-v8a.apk", info.downloadAssetName)
        assertEquals(5242880L, info.releaseSize)

        // Sanity: the request actually went to the pinned endpoint.
        assertEquals(
            GitHubReleasesApiImpl.LATEST_RELEASE_URL,
            fixture.interceptor.requests.single().url.toString(),
        )
    }

    @Test
    fun `fetchLatestUpdate reports no update when installed version equals the tag`() = runBlocking {
        val info = fetchFrom(canned(sampleGithubReleaseJson).api, currentVersionName = "1.2.3").getOrThrow()

        // Equal versions must not flag an update, AND the tag must still be
        // parsed (not silently null as in the bug).
        assertEquals("1.2.3", info.latestVersion)
        assertTrue(!info.isUpdateAvailable)
    }

    @Test
    fun `fetchLatestUpdate fails closed when an asset download url leaves the pinned repo`() = runBlocking {
        val tampered = sampleGithubReleaseJson.replace(
            "https://github.com/raulshma/jellyplay/releases/download/v1.2.3/jellyplay-v1.2.3-phone-arm64-v8a.apk",
            "https://evil.example.com/jellyplay-v1.2.3-phone-arm64-v8a.apk",
        )
        val fixture = canned(tampered)

        val result = fetchFrom(fixture.api)

        // The tampered asset must fail the WHOLE check — never surface as a
        // success carrying a poisoned downloadAssetUrl.
        val error = result.exceptionOrNull()
        assertIs<UpdateSecurityException>(error)
        assertTrue(error.message!!.contains("asset"), "unexpected message: $error")
    }

    @Test
    fun `fetchLatestUpdate fails closed when html_url leaves the pinned repo`() = runBlocking {
        val tampered = sampleGithubReleaseJson.replace(
            "https://github.com/raulshma/jellyplay/releases/tag/v1.2.3",
            "https://github.com/attacker/jellyplay/releases/tag/v1.2.3",
        )
        val fixture = canned(tampered)

        val error = fetchFrom(fixture.api).exceptionOrNull()

        assertIs<UpdateSecurityException>(error)
        assertTrue(error.message!!.contains("html_url"), "unexpected message: $error")
    }

    @Test
    fun `fetchLatestUpdate fails closed when the repo moved and the final URL lands on another org`() = runBlocking {
        // Simulates OkHttp having followed the moved-repo 301: the response is
        // served from the attacker-org final URL. Even a perfectly-formed
        // body must be rejected — only the pinned /repos/raulshma/ path is
        // allowed to describe the latest release.
        val movedFinalUrl = "https://api.github.com/repos/attacker/jellyplay/releases/latest"
        val fixture = canned(
            sampleGithubReleaseJson,
            boundTo = Request.Builder().url(movedFinalUrl).build(),
        )

        val error = fetchFrom(fixture.api).exceptionOrNull()

        assertIs<UpdateSecurityException>(error)
        assertTrue(error.message!!.contains("endpoint"), "unexpected message: $error")
    }

    // ---- fetchReleaseNotes (the What's New feed's remote source) ----

    private val sampleReleasesListJson = """
        [
          {
            "tag_name": "v0.11.2",
            "name": "Fresh coat of paint",
            "published_at": "2026-09-26T10:30:00Z",
            "body": "## What's New\n\n| Title |\n|---|\n| Cards |"
          },
          {
            "tag_name": "v0.11.1",
            "name": "",
            "published_at": "2026-09-05T08:00:00Z",
            "body": "plain notes"
          },
          {
            "tag_name": "",
            "name": "draft with no tag",
            "published_at": null,
            "body": "dropped"
          }
        ]
    """.trimIndent()

    @Test
    fun `fetchReleaseNotes maps the list items and drops blank tags`() = runBlocking {
        val fixture = canned(sampleReleasesListJson)

        val notes = fixture.api.fetchReleaseNotes().getOrThrow()

        assertEquals(listOf("0.11.2", "0.11.1"), notes.map { it.version })
        val fresh = notes[0]
        assertEquals("Fresh coat of paint", fresh.title)
        assertEquals("2026-09-26", fresh.date)
        assertTrue(fresh.body.startsWith("## What's New"))
        // A blank release name degrades to no title, not an empty string.
        assertNull(notes[1].title)
        assertEquals("plain notes", notes[1].body)

        // Sanity: the request actually went to the pinned list endpoint.
        assertEquals(
            GitHubReleasesApiImpl.RELEASES_LIST_URL,
            fixture.interceptor.requests.single().url.toString(),
        )
    }

    @Test
    fun `fetchReleaseNotes fails closed when the final URL leaves the pinned repo`() = runBlocking {
        val movedFinalUrl = "https://api.github.com/repos/attacker/jellyplay/releases?per_page=30"
        val fixture = canned(
            sampleReleasesListJson,
            boundTo = Request.Builder().url(movedFinalUrl).build(),
        )

        val error = fixture.api.fetchReleaseNotes().exceptionOrNull()

        assertIs<UpdateSecurityException>(error)
        assertTrue(error.message!!.contains("endpoint"), "unexpected message: $error")
    }
}
