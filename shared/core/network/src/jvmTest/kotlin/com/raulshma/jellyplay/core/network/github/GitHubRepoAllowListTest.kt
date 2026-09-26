package com.raulshma.jellyplay.core.network.github

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exhaustive table-driven tests for the compiled-in update-check allow-list
 * the release-endpoint pin (host + `/repos/raulshma/` owner path,
 * checked on the FINAL post-redirect URL) and the asset/download pin (CDN
 * hosts + owner-repo path, with the opaque signed-CDN carve-out). Every row
 * is an attack shape the pin must survive or a production URL it must keep
 * accepting — `SelfSignedTrustMatcherTest` is the style reference.
 */
class GitHubRepoAllowListTest {

    // -------------------------------------------------------- constants pin

    @Test
    fun `the anti-takeover pin is compiled in`() {
        // These ARE the security contract — a silent change here is a takeover.
        assertEquals("raulshma", GitHubRepoAllowList.ALLOWED_OWNER)
        assertEquals("jellyplay", GitHubRepoAllowList.ALLOWED_REPO)
        assertEquals(setOf("api.github.com", "github.com"), GitHubRepoAllowList.ALLOWED_RELEASE_HOSTS)
        assertEquals(
            setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com"),
            GitHubRepoAllowList.ALLOWED_ASSET_HOSTS,
        )
    }

    // --------------------------------------------------- isReleaseEndpoint

    @Test
    fun `isReleaseEndpoint accepts the pinned endpoint shapes`() {
        assertTable(
            "isReleaseEndpoint",
            verdict = GitHubRepoAllowList::isReleaseEndpoint,
            "https://api.github.com/repos/raulshma/jellyplay/releases/latest" to true,
            // github.com is on the release-host list too (any owner-scoped path).
            "https://github.com/repos/raulshma/jellyplay/releases/latest" to true,
            // Owner pin is owner-only, per the LATEST_RELEASE_URL identity.
            "https://api.github.com/repos/raulshma/jellyplay/tags/v1.0.0" to true,
            // Host comparison is case-insensitive (HttpUrl lowercases the host).
            "https://API.GITHUB.COM/repos/raulshma/jellyplay/releases/latest" to true,
        )
    }

    @Test
    fun `isReleaseEndpoint rejects a moved repo - the attacker-org 301`() {
        // The core takeover: GitHub answers the old endpoint with a 301 onto
        // the NEW owner on the SAME host; only the path check can see it.
        assertTable(
            "isReleaseEndpoint",
            verdict = GitHubRepoAllowList::isReleaseEndpoint,
            "https://api.github.com/repos/attacker/jellyplay/releases/latest" to false,
            "https://github.com/repos/attacker/jellyplay/releases/latest" to false,
        )
    }

    @Test
    fun `isReleaseEndpoint rejects unknown and spoofed hosts`() {
        assertTable(
            "isReleaseEndpoint",
            verdict = GitHubRepoAllowList::isReleaseEndpoint,
            "https://evil.com/repos/raulshma/jellyplay/releases/latest" to false,
            // Suffix spoof: the host must match EXACTLY, never by tail.
            "https://api.github.com.evil.com/repos/raulshma/jellyplay/releases/latest" to false,
            "https://github.com.evil.com/repos/raulshma/jellyplay/releases/latest" to false,
            // Credential spoof: user info does not make a host trusted.
            "https://api.github.com@evil.com/repos/raulshma/jellyplay/releases/latest" to false,
            // Asset-only CDN hosts never serve the release endpoint.
            "https://objects.githubusercontent.com/repos/raulshma/jellyplay/releases/latest" to false,
            // Root/short paths carry no owner pin.
            "https://api.github.com/" to false,
            "https://api.github.com/repos" to false,
            "https://api.github.com/repos/raulshma" to false,
        )
    }

    @Test
    fun `isReleaseEndpoint rejects non-https`() {
        assertTable(
            "isReleaseEndpoint",
            verdict = GitHubRepoAllowList::isReleaseEndpoint,
            "http://api.github.com/repos/raulshma/jellyplay/releases/latest" to false,
            "http://github.com/repos/raulshma/jellyplay/releases/latest" to false,
        )
    }

    // ---------------------------------------------------- isAssetEndpoint

    @Test
    fun `isAssetEndpoint accepts the pinned asset and page shapes`() {
        assertTable(
            "isAssetEndpoint",
            verdict = GitHubRepoAllowList::isAssetEndpoint,
            // Asset download links (the browser_download_url shape).
            "https://github.com/raulshma/jellyplay/releases/download/v1.0.0/jellyplay-v1.0.0-phone-arm64-v8a.apk" to true,
            // Release PAGE urls (the html_url shape) and the releases-page fallback.
            "https://github.com/raulshma/jellyplay/releases/tag/v1.0.0" to true,
            "https://github.com/raulshma/jellyplay/releases" to true,
            // The redirect target of a download link (modern CDN form).
            "https://release-assets.githubusercontent.com/raulshma/jellyplay/releases/download/v1.0.0/app.apk" to true,
            // Host comparison is case-insensitive.
            "https://GitHub.com/raulshma/jellyplay/releases/tag/v1.0.0" to true,
        )
    }

    @Test
    fun `isAssetEndpoint accepts the legacy signed-CDN host with an opaque path`() {
        // objects.githubusercontent.com paths are opaque signed tokens (the
        // signature binds the object); host + non-empty path is the check.
        assertTable(
            "isAssetEndpoint",
            verdict = GitHubRepoAllowList::isAssetEndpoint,
            "https://objects.githubusercontent.com/production-asset-69638/abc123?X-Amz-Signature=sig" to true,
            // ...but its root path carries nothing to bind — fail closed.
            "https://objects.githubusercontent.com/" to false,
        )
    }

    @Test
    fun `isAssetEndpoint rejects attacker and spoofed repo paths`() {
        assertTable(
            "isAssetEndpoint",
            verdict = GitHubRepoAllowList::isAssetEndpoint,
            // A repo move puts attacker assets under the SAME hosts.
            "https://github.com/attacker/jellyplay/releases/download/v1.0.0/app.apk" to false,
            "https://release-assets.githubusercontent.com/attacker/jellyplay/releases/download/v1.0.0/app.apk" to false,
            // Repo-prefix spoof: jellyplay-evil must not match the pin.
            "https://github.com/raulshma/jellyplay-evil/releases/download/v1.0.0/app.apk" to false,
            // Owner spoof: raulshma-evil must not match either.
            "https://github.com/raulshma-evil/jellyplay/releases/download/v1.0.0/app.apk" to false,
            // Path traversal canonicalizes away the pin during parse.
            "https://github.com/raulshma/jellyplay/../../attacker/app.apk" to false,
        )
    }

    @Test
    fun `isAssetEndpoint rejects unknown hosts`() {
        assertTable(
            "isAssetEndpoint",
            verdict = GitHubRepoAllowList::isAssetEndpoint,
            "https://example.com/jellyplay-v1.0.0-phone-arm64-v8a.apk" to false,
            "https://evil.com/raulshma/jellyplay/releases/download/v1.0.0/app.apk" to false,
            // githubusercontent.com WITHOUT the exact host prefix: suffix spoof.
            "https://evil-githubusercontent.com/raulshma/jellyplay/releases/download/v1.0.0/app.apk" to false,
            // raw.githubusercontent.com is not an asset host.
            "https://raw.githubusercontent.com/raulshma/jellyplay/main/app.apk" to false,
            "https://api.github.com/raulshma/jellyplay/releases/download/v1.0.0/app.apk" to false,
        )
    }

    @Test
    fun `isAssetEndpoint rejects non-https`() {
        assertTable(
            "isAssetEndpoint",
            verdict = GitHubRepoAllowList::isAssetEndpoint,
            "http://github.com/raulshma/jellyplay/releases/download/v1.0.0/app.apk" to false,
            "http://objects.githubusercontent.com/production-asset-69638/abc123" to false,
        )
    }

    // ------------------------------------------- string overload fail-closed

    @Test
    fun `string overloads agree with the parsed verdicts and fail closed on garbage`() {
        for (url in listOf(
            "https://api.github.com/repos/raulshma/jellyplay/releases/latest",
            "https://api.github.com/repos/attacker/jellyplay/releases/latest",
        )) {
            assertEquals(
                GitHubRepoAllowList.isReleaseEndpoint(url.toHttpUrl()),
                GitHubRepoAllowList.isReleaseEndpoint(url),
                "isReleaseEndpoint overload drift on $url",
            )
        }
        for (url in listOf(
            "https://github.com/raulshma/jellyplay/releases/download/v1.0.0/app.apk",
            "https://example.com/app.apk",
            "https://github.com/attacker/jellyplay/releases/download/v1.0.0/app.apk",
        )) {
            assertEquals(
                GitHubRepoAllowList.isAssetEndpoint(url.toHttpUrl()),
                GitHubRepoAllowList.isAssetEndpoint(url),
                "isAssetEndpoint overload drift on $url",
            )
        }
        // Unparseable strings never grant anything.
        assertFalse(GitHubRepoAllowList.isReleaseEndpoint("not a url"))
        assertFalse(GitHubRepoAllowList.isAssetEndpoint(""))
        assertFalse(GitHubRepoAllowList.isAssetEndpoint("github.com/raulshma/jellyplay/releases"))
    }

    @Test
    fun `malformed input to the parsed overloads surfaces as a parse exception not a grant`() {
        // Documents the contract the string overloads wrap: HttpUrl parsing
        // THROWS on garbage — callers holding raw strings must use the
        // overload (which fails closed), never a bare parse.
        assertFailsWith<IllegalArgumentException> {
            "not a url".toHttpUrl()
        }
    }

    // ------------------------------------------------------------- harness

    /** One assertion per table row, with the failing URL in the message. */
    private fun assertTable(
        name: String,
        verdict: (HttpUrl) -> Boolean,
        vararg cases: Pair<String, Boolean>,
    ) {
        for ((url, expected) in cases) {
            assertEquals(
                expected,
                verdict(url.toHttpUrl()),
                "$name verdict drifted for $url",
            )
        }
    }
}
