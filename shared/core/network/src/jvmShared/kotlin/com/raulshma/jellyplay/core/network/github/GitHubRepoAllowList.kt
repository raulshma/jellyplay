package com.raulshma.jellyplay.core.network.github

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The compiled-in allow-list pinning every URL the update-check pipeline
 * trusts: the GitHub Releases feed is the ONE identity the update
 * check may talk to, and the three server-controlled URL surfaces it feeds —
 * the final post-redirect endpoint URL, the release `html_url`, and every
 * asset's `browser_download_url` — must stay inside that identity before any
 * of them can drive a download or a browser handoff.
 *
 * ## Why compiled-in
 *
 * Nothing here is configurable on purpose: a setting or a server-side
 * redirect could otherwise move the pin itself. The owner is written into
 * the binary, so a repo move/transfer — GitHub answers the old
 * `/repos/<owner>/…` endpoint with a 301 onto the new owner on the SAME
 * host — is rejected by the path check even though the host half alone would
 * pass. This is the same fail-closed spirit as `SelfSignedTrustMatcher`
 * (the pattern for this file).
 *
 * ## Rules
 *
 *  - Release endpoints: https, host in [ALLOWED_RELEASE_HOSTS], and the
 *    path under `/repos/<ALLOWED_OWNER>/` (owner pin, matching the
 *    [GitHubReleasesApiImpl.LATEST_RELEASE_URL] identity).
 *  - Asset/download URLs — and release PAGE urls, which share the
 *    `github.com/<owner>/<repo>/…` shape: https, host in
 *    [ALLOWED_ASSET_HOSTS], and an owner-repo path — except the legacy S3
 *    signer `objects.githubusercontent.com`, whose signed paths are opaque
 *    tokens bound to one object by the query signature, so host + non-empty
 *    path is the strongest owner check available there.
 *
 * Unparseable, non-https, or unknown-host URLs never pass (fail closed).
 * Pure and JVM-side by design: `okhttp3.HttpUrl` is a jvmShared dependency
 * of this module, and every enforcement point ([GitHubReleasesApiImpl],
 * core:data's `AppUpdateRepositoryImpl`, desktop `DesktopUpdateLinks`) is
 * JVM. Lives beside the trust matcher's decisions so the check can never
 * drift from the constants it pins.
 */
object GitHubRepoAllowList {

    /** The compiled-in anti-takeover pin: the only owner we accept. */
    const val ALLOWED_OWNER = "raulshma"

    /** The only repo whose asset/page paths we accept (owner + repo). */
    const val ALLOWED_REPO = "jellyplay"

    /** Hosts allowed to serve the release-API endpoint (post-redirect final host). */
    val ALLOWED_RELEASE_HOSTS = setOf("api.github.com", "github.com")

    /** Hosts allowed to serve release pages and downloadable assets. */
    val ALLOWED_ASSET_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
    )

    /** The legacy S3 signer whose paths are opaque signed tokens. */
    private const val SIGNED_CDN_HOST = "objects.githubusercontent.com"

    private const val API_PATH_PREFIX = "/repos/$ALLOWED_OWNER/"
    private const val REPO_PATH_PREFIX = "/$ALLOWED_OWNER/$ALLOWED_REPO/"

    /**
     * Whether [url] is the pinned GitHub Releases API endpoint. Checked on
     * the FINAL post-redirect URL (`response.request.url` after OkHttp has
     * followed every redirect transparently).
     */
    fun isReleaseEndpoint(url: HttpUrl): Boolean =
        url.isHttps &&
            url.host.lowercase() in ALLOWED_RELEASE_HOSTS &&
            url.encodedPath.lowercase().startsWith(API_PATH_PREFIX)

    /** String convenience overload — unparseable URLs fail closed. */
    fun isReleaseEndpoint(url: String): Boolean =
        url.toHttpUrlOrNull()?.let { isReleaseEndpoint(it) } == true

    /**
     * Whether [url] may drive a download or a browser handoff: an asset
     * `browser_download_url`, or a release page (`html_url` / the desktop
     * releases-page fallback), both of which share the
     * `github.com/<owner>/<repo>/…` shape.
     */
    fun isAssetEndpoint(url: HttpUrl): Boolean {
        if (!url.isHttps) return false
        val host = url.host.lowercase()
        if (host !in ALLOWED_ASSET_HOSTS) return false
        val path = url.encodedPath.lowercase()
        return path.startsWith(REPO_PATH_PREFIX) ||
            // The legacy S3 signer: opaque signed paths (e.g.
            // /production-asset-…/<token>?X-Amz-Signature=…) bind the object
            // by its query signature, so a non-root path is the strongest
            // owner check available on that host.
            (host == SIGNED_CDN_HOST && path.length > 1)
    }

    /** String convenience overload — unparseable URLs fail closed. */
    fun isAssetEndpoint(url: String): Boolean =
        url.toHttpUrlOrNull()?.let { isAssetEndpoint(it) } == true
}
