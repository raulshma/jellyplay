package com.raulshma.jellyplay.core.network.github

import com.raulshma.jellyplay.core.model.AppUpdateInfo
import com.raulshma.jellyplay.core.network.api.ApiException
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClientImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
) : GitHubReleasesApi {

    private val json = SeerrApiClientImpl.lenientJson

    @Serializable
    private data class GitHubRelease(
        val tagName: String? = null,
        val htmlUrl: String? = null,
        val body: String? = null,
        val assets: List<GitHubAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubAsset(
        val name: String? = null,
        val browserDownloadUrl: String? = null,
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
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github.v3+json")
            .get()
            .build()

        return try {
            withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (body.isNullOrBlank()) {
                        return@withContext Result.failure<AppUpdateInfo>(
                            ApiException.fromNetwork(
                                java.io.IOException("Empty response from GitHub"),
                                "Empty response from GitHub",
                            )
                        )
                    }
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            ApiException.fromHttp(
                                httpCode = response.code,
                                message = "GitHub request failed: ${response.code}",
                            )
                        )
                    }
                    val release = json.decodeFromString<GitHubRelease>(body)
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
         * Compares two dotted numeric version strings. Returns a positive int if
         * [v1] is newer, negative if older, 0 if equal. Non-numeric segments
         * are treated as 0. Lifted verbatim from the former
         * `AboutViewModel.compareVersions` so the comparison semantics are
         * unchanged.
         */
        fun compareVersions(v1: String, v2: String): Int {
            val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
            val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(parts1.size, parts2.size)) {
                val p1 = parts1.getOrElse(i) { 0 }
                val p2 = parts2.getOrElse(i) { 0 }
                if (p1 != p2) return p1 - p2
            }
            return 0
        }

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
