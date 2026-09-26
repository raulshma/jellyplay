package com.raulshma.jellyplay.core.network.github

import com.raulshma.jellyplay.core.model.AppUpdateInfo
import com.raulshma.jellyplay.core.model.compareVersions
import com.raulshma.jellyplay.core.network.NetworkLog
import com.raulshma.jellyplay.core.network.api.ApiException
import com.raulshma.jellyplay.core.network.api.HttpExecutor
import com.raulshma.jellyplay.core.network.api.fromNetwork
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClientImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * A single downloadable asset attached to a GitHub release. Public so the
 * asset-selection logic in [GitHubReleasesApiImpl.selectAsset] can be unit
 * tested without exposing the kotlinx.serialization wire DTO.
 */
data class GitHubReleaseAsset(
    val name: String?,
    val browserDownloadUrl: String?,
    val size: Long,
)

class GitHubReleasesApiImpl(
    private val okHttpClient: OkHttpClient,
    // Overridable only so unit tests can point at a MockWebServer; production
    // wiring leaves these as the GitHub Releases endpoints.
    private val latestReleaseUrl: String = LATEST_RELEASE_URL,
    private val releasesListUrl: String = RELEASES_LIST_URL,
) : GitHubReleasesApi {

    private val json = SeerrApiClientImpl.lenientJson

    /**
     * The shared OkHttp execute chassis, shaped with GitHub's texts. No retry
     * (GitHub never had a `Resilient*` wrapper — the DI graph binds this impl
     * directly); Retry-After capture is new with the chassis fold so
     * `RetryPolicy` would honor the server's advice on the HTTP-failure arm.
     */
    private val http = HttpExecutor(
        okHttpClient = okHttpClient,
        options = HttpExecutor.Options(
            parseErrorMessage = { code, _ -> "GitHub request failed: $code" },
            formatNetworkError = { e -> e.message ?: "GitHub request failed" },
            captureRetryAfter = true,
            emptyBodyText = "Empty response from GitHub",
        ),
    )

    // GitHub's REST API serializes fields in snake_case (tag_name, html_url,
    // browser_download_url). kotlinx.serialization is case-sensitive and does
    // not map snake_case to camelCase automatically, so every mismatched field
    // needs an explicit @SerialName — otherwise it silently deserializes to
    // null under ignoreUnknownKeys=true, which made tagName come back empty and
    // the update check report "already latest" for every build.
    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String? = null,
        @SerialName("html_url") val htmlUrl: String? = null,
        val body: String? = null,
        val assets: List<GitHubAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubAsset(
        val name: String? = null,
        @SerialName("browser_download_url") val browserDownloadUrl: String? = null,
        val size: Long = 0,
    ) {
        fun toPublic(): GitHubReleaseAsset = GitHubReleaseAsset(
            name = name,
            browserDownloadUrl = browserDownloadUrl,
            size = size,
        )
    }

    // The /releases list item: notes metadata only — no assets to vet, the
    // body is rendered (markdown/cards), never used to drive a download.
    @Serializable
    private data class GitHubReleaseListItem(
        @SerialName("tag_name") val tagName: String? = null,
        val name: String? = null,
        @SerialName("published_at") val publishedAt: String? = null,
        val body: String? = null,
    )

    override suspend fun fetchLatestUpdate(
        currentVersionName: String,
        flavor: String,
        supportedAbis: Array<String>,
    ): Result<AppUpdateInfo> {
        val request = Request.Builder()
            .url(latestReleaseUrl)
            .header("Accept", "application/vnd.github.v3+json")
            .get()
            .build()

        return try {
            withContext(Dispatchers.IO) {
                http.execute(request) { response ->
                    // Gate #1: response.request.url is the FINAL
                    // post-redirect URL — OkHttp follows every redirect
                    // transparently (including cross-host), and a moved repo
                    // lands as a 301 onto a different /repos/<owner>/ path on
                    // the SAME host, which the host half alone cannot see.
                    // Fail closed before a byte of the body is trusted.
                    // (Redirects are deliberately NOT disabled — verifying the
                    // final URL is sufficient and keeps the client shared.)
                    val finalUrl = response.request.url
                    if (!GitHubRepoAllowList.isReleaseEndpoint(finalUrl)) {
                        throw securityViolation(
                            "GitHub releases endpoint left the pinned repo (final URL: $finalUrl)",
                        )
                    }
                    val stream = response.body?.byteStream()
                        ?: throw http.emptyBodyNetworkError()
                    // Stream-decode: release notes can be large and previously
                    // paid double (buffered String + decoded objects).
                    val release = json.decodeFromStream<GitHubRelease>(stream)
                    // Gate #2: html_url and every asset's
                    // browser_download_url are server-controlled strings that
                    // later flow into AppUpdateRepositoryImpl.downloadUpdate /
                    // the desktop browser handoff — verify each against the
                    // asset allow-list before any of them can be published.
                    release.htmlUrl?.takeIf { it.isNotBlank() }?.let { htmlUrl ->
                        if (!GitHubRepoAllowList.isAssetEndpoint(htmlUrl)) {
                            throw securityViolation(
                                "GitHub release html_url left the pinned repo: $htmlUrl",
                            )
                        }
                    }
                    release.assets.forEach { asset ->
                        val downloadUrl = asset.browserDownloadUrl
                        if (!downloadUrl.isNullOrBlank() &&
                            !GitHubRepoAllowList.isAssetEndpoint(downloadUrl)
                        ) {
                            throw securityViolation(
                                "GitHub release asset left the pinned repo (${asset.name}): $downloadUrl",
                            )
                        }
                    }
                    val tag = release.tagName.orEmpty().removePrefix("v")
                    val isUpdateAvailable = compareVersions(tag, currentVersionName) > 0
                    val chosen = selectAsset(
                        release.assets.map { it.toPublic() },
                        tag,
                        flavor,
                        supportedAbis,
                    )

                    AppUpdateInfo(
                        latestVersion = tag,
                        htmlUrl = release.htmlUrl.orEmpty(),
                        releaseNotes = release.body.orEmpty(),
                        isUpdateAvailable = isUpdateAvailable,
                        downloadAssetUrl = chosen?.browserDownloadUrl,
                        downloadAssetName = chosen?.name,
                        releaseSize = chosen?.size ?: 0L,
                    )
                }
            }.let { Result.success(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: UpdateSecurityException) {
            // Fail closed WITHOUT the ApiException ladder: fromNetwork would
            // classify an IOException as retryable, and a repo-move/redirect
            // takeover must surface to the user, never be retried.
            Result.failure(e)
        } catch (e: ApiException) {
            // Already classified by the chassis (HTTP status with Retry-After,
            // or the empty-body arm) — pass through, never re-classify.
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(ApiException.fromNetwork(e, e.message ?: "GitHub request failed"))
        }
    }

    override suspend fun fetchReleaseNotes(): Result<List<GitHubReleaseNotes>> {
        val request = Request.Builder()
            .url(releasesListUrl)
            .header("Accept", "application/vnd.github.v3+json")
            .get()
            .build()

        return try {
            withContext(Dispatchers.IO) {
                http.execute(request) { response ->
                    // Same fail-closed gate as the latest-release fetch: the
                    // FINAL post-redirect URL must stay on the pinned repo's
                    // releases path before a byte of the list is trusted.
                    val finalUrl = response.request.url
                    if (!GitHubRepoAllowList.isReleaseEndpoint(finalUrl)) {
                        throw securityViolation(
                            "GitHub releases endpoint left the pinned repo (final URL: $finalUrl)",
                        )
                    }
                    val stream = response.body?.byteStream()
                        ?: throw http.emptyBodyNetworkError()
                    val items = json.decodeFromStream<List<GitHubReleaseListItem>>(stream)
                    items.mapNotNull { item ->
                        val tag = item.tagName?.trim()?.removePrefix("v").orEmpty()
                        if (tag.isBlank()) null
                        else GitHubReleaseNotes(
                            version = tag,
                            // ISO-8601 `2026-09-26T10:00:00Z` → display date.
                            date = item.publishedAt?.takeIf { it.length >= 10 }?.take(10),
                            title = item.name?.takeIf { it.isNotBlank() },
                            body = item.body.orEmpty(),
                        )
                    }
                }
            }.let { Result.success(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: UpdateSecurityException) {
            Result.failure(e)
        } catch (e: ApiException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(ApiException.fromNetwork(e, e.message ?: "GitHub request failed"))
        }
    }

    /**
     * Logs the allow-list violation at warn (the security breadcrumb) and
     * shapes it as the fail-closed failure the callers surface.
     */
    private fun securityViolation(message: String): UpdateSecurityException {
        NetworkLog.w(TAG, message)
        return UpdateSecurityException(message)
    }

    companion object {
        private const val TAG = "GitHubReleases"

        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/raulshma/jellyplay/releases/latest"

        /** The What's New feed's remote source: the recent-releases list. */
        const val RELEASES_LIST_URL =
            "https://api.github.com/repos/raulshma/jellyplay/releases?per_page=30"

        /**
         * Picks the APK asset whose name matches the running [flavor] and the
         * device's most-preferred ABI. Asset names follow the CI convention
         * `jellyplay-v<version>-<flavor>-<abi>.apk` (e.g.
         * `jellyplay-v1.2.3-phone-arm64-v8a.apk`). Falls back to the
         * `-universal.apk` of the same flavor when no ABI-specific build is
         * published, and finally to any universal asset.
         *
         * Mirrors the Android ABI name (`arm64-v8a`, `x86_64`, `armeabi-v7a`)
         * that the release workflow embeds in asset names.
         */
        fun selectAsset(
            assets: List<GitHubReleaseAsset>,
            version: String,
            flavor: String,
            supportedAbis: Array<String>,
        ): GitHubReleaseAsset? {
            val prefix = "jellyplay-v$version-$flavor"
            // Prefer the device's most-preferred ABI, in declaration order.
            for (abi in supportedAbis) {
                val wanted = "$prefix-$abi.apk"
                assets.firstOrNull { it.name.equals(wanted, ignoreCase = true) }
                    ?.let { return it }
            }
            // Fall back to the universal build for this flavor.
            assets.firstOrNull {
                it.name.equals("$prefix-universal.apk", ignoreCase = true)
            }?.let { return it }
            // Last resort: any universal asset (covers a flavorless publish).
            return assets.firstOrNull {
                it.name?.lowercase()?.endsWith("-universal.apk") == true
            }
        }
    }
}
