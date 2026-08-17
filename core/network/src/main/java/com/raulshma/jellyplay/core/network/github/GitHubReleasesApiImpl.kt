package com.raulshma.jellyplay.core.network.github

import com.raulshma.jellyplay.core.model.AppUpdateInfo
import com.raulshma.jellyplay.core.model.compareVersions
import com.raulshma.jellyplay.core.network.api.ApiException
import com.raulshma.jellyplay.core.network.api.emptyResponseBodyError
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClientImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

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

@Singleton
class GitHubReleasesApiImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    // Overridable only so unit tests can point at a MockWebServer; production
    // wiring leaves it as the GitHub Releases latest endpoint.
    private val latestReleaseUrl: String = LATEST_RELEASE_URL,
) : GitHubReleasesApi {

    private val json = SeerrApiClientImpl.lenientJson

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
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            ApiException.fromHttp(
                                httpCode = response.code,
                                message = "GitHub request failed: ${response.code}",
                            )
                        )
                    }
                    val stream = response.body?.byteStream()
                    if (stream == null) {
                        return@withContext Result.failure<AppUpdateInfo>(
                            emptyResponseBodyError("GitHub")
                        )
                    }
                    // Stream-decode: release notes can be large and previously
                    // paid double (buffered String + decoded objects).
                    val release = json.decodeFromStream<GitHubRelease>(stream)
                    val tag = release.tagName.orEmpty().removePrefix("v")
                    val isUpdateAvailable = compareVersions(tag, currentVersionName) > 0
                    val chosen = selectAsset(
                        release.assets.map { it.toPublic() },
                        tag,
                        flavor,
                        supportedAbis,
                    )

                    Result.success(
                        AppUpdateInfo(
                            latestVersion = tag,
                            htmlUrl = release.htmlUrl.orEmpty(),
                            releaseNotes = release.body.orEmpty(),
                            isUpdateAvailable = isUpdateAvailable,
                            downloadAssetUrl = chosen?.browserDownloadUrl,
                            downloadAssetName = chosen?.name,
                            releaseSize = chosen?.size ?: 0L,
                        )
                    )
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(ApiException.fromNetwork(e, e.message ?: "GitHub request failed"))
        }
    }

    companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/raulshma/jellyplay/releases/latest"

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
