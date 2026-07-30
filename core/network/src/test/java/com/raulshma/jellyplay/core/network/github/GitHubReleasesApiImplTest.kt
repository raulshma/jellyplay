package com.raulshma.jellyplay.core.network.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleasesApiImplTest {

    // ---- compareVersions ----

    @Test
    fun `compareVersions returns positive when first is newer`() {
        assertTrue(GitHubReleasesApiImpl.compareVersions("1.2.4", "1.2.3") > 0)
        assertTrue(GitHubReleasesApiImpl.compareVersions("2.0.0", "1.9.9") > 0)
        assertTrue(GitHubReleasesApiImpl.compareVersions("1.2", "1.1.9") > 0)
    }

    @Test
    fun `compareVersions returns negative when first is older`() {
        assertTrue(GitHubReleasesApiImpl.compareVersions("1.2.2", "1.2.3") < 0)
        assertTrue(GitHubReleasesApiImpl.compareVersions("1.9.9", "2.0.0") < 0)
    }

    @Test
    fun `compareVersions returns zero when equal`() {
        assertEquals(0, GitHubReleasesApiImpl.compareVersions("1.2.3", "1.2.3"))
        assertEquals(0, GitHubReleasesApiImpl.compareVersions("1.2", "1.2.0"))
    }

    @Test
    fun `compareVersions treats non-numeric segments as zero`() {
        assertEquals(0, GitHubReleasesApiImpl.compareVersions("1.2.x", "1.2.0"))
        assertTrue(GitHubReleasesApiImpl.compareVersions("1.2.1", "1.2.x") > 0)
    }

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
}
