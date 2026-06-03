package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ContentBreakdown
import com.raulshma.jellyplay.core.model.CountryInfo
import com.raulshma.jellyplay.core.model.CultureInfo
import com.raulshma.jellyplay.core.model.EditorPerson
import com.raulshma.jellyplay.core.model.ExternalIdInfo
import com.raulshma.jellyplay.core.model.ImageInfo
import com.raulshma.jellyplay.core.model.ImageProviderInfo
import com.raulshma.jellyplay.core.model.MetadataEditorInfo
import com.raulshma.jellyplay.core.model.ParentalRating
import com.raulshma.jellyplay.core.model.RemoteImageInfo
import com.raulshma.jellyplay.core.model.RemoteImageResult
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jellyfin.sdk.api.client.extensions.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetadataApiClientImpl @Inject constructor(
    private val engine: JellyfinApiEngine,
) : MetadataApiClient {

    override suspend fun updateItem(
        itemId: String, name: String, originalTitle: String?, sortName: String?,
        overview: String?, tagline: String?, genres: List<String>, tags: List<String>,
        studios: List<String>, communityRating: Float?, criticRating: Float?,
        officialRating: String?, customRating: String?, productionYear: Int?,
        premiereDate: String?, endDate: String?, runtimeTicks: Long?,
        indexNumber: Int?, parentIndexNumber: Int?, displayOrder: String?,
        status: String?, airDays: List<String>, airTime: String?,
        people: List<EditorPerson>, providerIds: Map<String, String>,
        lockData: Boolean, lockedFields: List<String>,
        preferredMetadataLanguage: String?, preferredMetadataCountryCode: String?,
        taglines: List<String>, productionLocations: List<String>, dateCreated: String?,
    ): Result<Unit> = engine.apiResult {
        val server = engine._currentServer.value ?: throw IllegalStateException("No server")
        val user = engine._currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Items/$itemId"
        val body = buildJsonObject {
            put("Id", JsonPrimitive(itemId))
            put("Name", JsonPrimitive(name))
            originalTitle?.let { put("OriginalTitle", JsonPrimitive(it)) }
            sortName?.let { put("ForcedSortName", JsonPrimitive(it)) }
            overview?.let { put("Overview", JsonPrimitive(it)) }
            if (taglines.isNotEmpty()) {
                put("Taglines", buildJsonArray { taglines.forEach { add(JsonPrimitive(it)) } })
            }
            put("Genres", buildJsonArray { genres.forEach { add(JsonPrimitive(it)) } })
            put("Tags", buildJsonArray { tags.forEach { add(JsonPrimitive(it)) } })
            put("Studios", buildJsonArray {
                studios.forEach { studio ->
                    add(buildJsonObject { put("Name", JsonPrimitive(studio)) })
                }
            })
            communityRating?.let { put("CommunityRating", JsonPrimitive(it.toDouble())) }
            criticRating?.let { put("CriticRating", JsonPrimitive(it.toDouble())) }
            officialRating?.let { put("OfficialRating", JsonPrimitive(it)) }
            customRating?.let { put("CustomRating", JsonPrimitive(it)) }
            productionYear?.let { put("ProductionYear", JsonPrimitive(it)) }
            premiereDate?.let { put("PremiereDate", JsonPrimitive(it)) }
            endDate?.let { put("EndDate", JsonPrimitive(it)) }
            runtimeTicks?.let { put("RunTimeTicks", JsonPrimitive(it)) }
            indexNumber?.let { put("IndexNumber", JsonPrimitive(it)) }
            parentIndexNumber?.let { put("ParentIndexNumber", JsonPrimitive(it)) }
            displayOrder?.let { put("DisplayOrder", JsonPrimitive(it)) }
            status?.let { put("Status", JsonPrimitive(it)) }
            if (airDays.isNotEmpty()) {
                put("AirDays", buildJsonArray { airDays.forEach { add(JsonPrimitive(it)) } })
            }
            airTime?.let { put("AirTime", JsonPrimitive(it)) }
            if (people.isNotEmpty()) {
                put("People", buildJsonArray {
                    people.forEach { person ->
                        add(buildJsonObject {
                            put("Id", JsonPrimitive(person.id))
                            put("Name", JsonPrimitive(person.name))
                            person.role?.let { put("Role", JsonPrimitive(it)) }
                            put("Type", JsonPrimitive(person.type))
                            person.primaryImageTag?.let { put("PrimaryImageTag", JsonPrimitive(it)) }
                            person.sortOrder?.let { put("SortOrder", JsonPrimitive(it)) }
                        })
                    }
                })
            }
            if (providerIds.isNotEmpty()) {
                put("ProviderIds", buildJsonObject {
                    providerIds.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                })
            }
            put("LockData", JsonPrimitive(lockData))
            if (lockedFields.isNotEmpty()) {
                put("LockedFields", buildJsonArray { lockedFields.forEach { add(JsonPrimitive(it)) } })
            }
            preferredMetadataLanguage?.let { put("PreferredMetadataLanguage", JsonPrimitive(it)) }
            preferredMetadataCountryCode?.let { put("PreferredMetadataCountryCode", JsonPrimitive(it)) }
            if (productionLocations.isNotEmpty()) {
                put("ProductionLocations", buildJsonArray { productionLocations.forEach { add(JsonPrimitive(it)) } })
            }
        }
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to update item: ${response.code}")
            }
        }
    }

    override suspend fun getMetadataEditorInfo(itemId: String): Result<MetadataEditorInfo> = engine.apiResult {
        val server = engine._currentServer.value ?: throw IllegalStateException("No server")
        val user = engine._currentUser.value ?: throw IllegalStateException("No user")

        val editorUrl = "${server.address}/Items/$itemId/MetadataEditorInfo"
        val editorRequest = Request.Builder()
            .url(editorUrl)
            .header("X-Emby-Token", user.accessToken)
            .build()
        val editorJson = engine.okHttpClient.newCall(editorRequest).execute().use { response ->
            response.body?.string() ?: throw Exception("Empty response")
        }

        val jsonElement = JellyfinApiEngine.sharedJson.parseToJsonElement(editorJson).jsonObject

        val externalIds = jsonElement["ExternalIdInfos"]?.jsonArray?.map { elem ->
            val obj = elem.jsonObject
            ExternalIdInfo(
                name = obj["Name"]?.jsonPrimitive?.content ?: "",
                key = obj["Key"]?.jsonPrimitive?.content ?: "",
                urlFormatString = obj["UrlFormatString"]?.jsonPrimitive?.contentOrNull,
            )
        } ?: emptyList()

        val parentalRatings = jsonElement["ParentalRatingOptions"]?.jsonArray?.map { elem ->
            val obj = elem.jsonObject
            ParentalRating(
                name = obj["Name"]?.jsonPrimitive?.content ?: "",
                value = obj["Value"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        } ?: emptyList()

        val result = coroutineScope {
            val culturesDeferred = async {
                try {
                    val culturesUrl = "${server.address}/Localization/Cultures"
                    val culturesRequest = Request.Builder()
                        .url(culturesUrl)
                        .header("X-Emby-Token", user.accessToken)
                        .build()
                    engine.okHttpClient.newCall(culturesRequest).execute().use { response ->
                        response.body?.string()?.let { body ->
                            JellyfinApiEngine.sharedJson.parseToJsonElement(body).jsonArray.map { elem ->
                                val obj = elem.jsonObject
                                CultureInfo(
                                    name = obj["Name"]?.jsonPrimitive?.content ?: "",
                                    displayName = obj["DisplayName"]?.jsonPrimitive?.content ?: "",
                                    twoLetterISOLanguageName = obj["TwoLetterISOLanguageName"]?.jsonPrimitive?.contentOrNull,
                                    threeLetterISOLanguageName = obj["ThreeLetterISOLanguageName"]?.jsonPrimitive?.contentOrNull,
                                )
                            }
                        } ?: emptyList()
                    }
                } catch (_: Exception) { emptyList() }
            }

            val countriesDeferred = async {
                try {
                    val countriesUrl = "${server.address}/Localization/Countries"
                    val countriesRequest = Request.Builder()
                        .url(countriesUrl)
                        .header("X-Emby-Token", user.accessToken)
                        .build()
                    engine.okHttpClient.newCall(countriesRequest).execute().use { response ->
                        response.body?.string()?.let { body ->
                            JellyfinApiEngine.sharedJson.parseToJsonElement(body).jsonArray.map { elem ->
                                val obj = elem.jsonObject
                                CountryInfo(
                                    name = obj["Name"]?.jsonPrimitive?.content ?: "",
                                    displayName = obj["DisplayName"]?.jsonPrimitive?.content ?: "",
                                    twoLetterISORegionName = obj["TwoLetterISORegionName"]?.jsonPrimitive?.contentOrNull,
                                    threeLetterISORegionName = obj["ThreeLetterISORegionName"]?.jsonPrimitive?.contentOrNull,
                                )
                            }
                        } ?: emptyList()
                    }
                } catch (_: Exception) { emptyList() }
            }

            culturesDeferred.await() to countriesDeferred.await()
        }

        val (cultures, countries) = result

        MetadataEditorInfo(
            parentalRatingOptions = parentalRatings,
            contentTypeOptions = emptyList(),
            externalIdInfos = externalIds,
            cultures = cultures,
            countries = countries,
        )
    }

    override suspend fun refreshItemMetadata(
        itemId: String,
        metadataRefreshMode: String,
        imageRefreshMode: String,
        replaceAllMetadata: Boolean,
        replaceAllImages: Boolean,
        regenerateTrickplay: Boolean,
    ): Result<Unit> = engine.apiResult {
        val server = engine._currentServer.value ?: throw IllegalStateException("No server")
        val user = engine._currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Items/$itemId/Refresh" +
            "?MetadataRefreshMode=$metadataRefreshMode" +
            "&ImageRefreshMode=$imageRefreshMode" +
            "&ReplaceAllMetadata=$replaceAllMetadata" +
            "&ReplaceAllImages=$replaceAllImages" +
            "&RegenerateTrickplay=$regenerateTrickplay"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .post(ByteArray(0).toRequestBody())
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to refresh metadata: ${response.code}")
            }
        }
    }

    override suspend fun getItemImageInfo(itemId: String): Result<List<ImageInfo>> = engine.apiResult {
        val server = engine._currentServer.value ?: throw IllegalStateException("No server")
        val user = engine._currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Items/$itemId/Images"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return@use emptyList()
            if (!response.isSuccessful) return@use emptyList()
            JellyfinApiEngine.sharedJson.parseToJsonElement(body).jsonArray.map { elem ->
                val obj = elem.jsonObject
                ImageInfo(
                    imageType = obj["ImageType"]?.jsonPrimitive?.content ?: "",
                    imageIndex = obj["ImageIndex"]?.jsonPrimitive?.intOrNull ?: 0,
                    width = obj["Width"]?.jsonPrimitive?.intOrNull ?: 0,
                    height = obj["Height"]?.jsonPrimitive?.intOrNull ?: 0,
                    blurHash = obj["BlurHash"]?.jsonPrimitive?.contentOrNull,
                    imageTag = obj["ImageTag"]?.jsonPrimitive?.contentOrNull,
                )
            }
        }
    }

    override suspend fun setItemImage(itemId: String, imageType: String, imageBytes: ByteArray): Result<Unit> = engine.apiResult {
        val server = engine._currentServer.value ?: throw IllegalStateException("No server")
        val user = engine._currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Items/$itemId/Images/$imageType"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .header("Content-Type", "image/*")
            .post(imageBytes.toRequestBody("image/*".toMediaType()))
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to upload image: ${response.code}")
            }
        }
    }

    override suspend fun deleteItemImage(itemId: String, imageType: String, imageIndex: Int?): Result<Unit> = engine.apiResult {
        val server = engine._currentServer.value ?: throw IllegalStateException("No server")
        val user = engine._currentUser.value ?: throw IllegalStateException("No user")
        val indexPart = imageIndex?.let { "/$it" } ?: ""
        val url = "${server.address}/Items/$itemId/Images/$imageType$indexPart"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .delete()
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to delete image: ${response.code}")
            }
        }
    }

    override suspend fun getRemoteImages(
        itemId: String,
        imageType: String?,
        provider: String?,
        startIndex: Int?,
        limit: Int?,
    ): Result<RemoteImageResult> = engine.apiResult {
        val server = engine._currentServer.value ?: throw IllegalStateException("No server")
        val user = engine._currentUser.value ?: throw IllegalStateException("No user")
        val url = buildString {
            append("${server.address}/Items/$itemId/RemoteImages")
            append("?Limit=${limit ?: 50}")
            imageType?.let { append("&Type=$it") }
            provider?.let { append("&ProviderName=$it") }
            startIndex?.let { append("&StartIndex=$it") }
        }
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("Empty response")
            val json = JellyfinApiEngine.sharedJson.parseToJsonElement(body).jsonObject
            val images = json["Images"]?.jsonArray?.map { elem ->
                val obj = elem.jsonObject
                RemoteImageInfo(
                    providerName = obj["ProviderName"]?.jsonPrimitive?.content ?: "",
                    url = obj["Url"]?.jsonPrimitive?.content ?: "",
                    thumbnailUrl = obj["ThumbnailUrl"]?.jsonPrimitive?.content ?: "",
                    height = obj["Height"]?.jsonPrimitive?.intOrNull ?: 0,
                    width = obj["Width"]?.jsonPrimitive?.intOrNull ?: 0,
                    language = obj["Language"]?.jsonPrimitive?.contentOrNull,
                    communityRating = obj["CommunityRating"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull(),
                    voteCount = obj["VoteCount"]?.jsonPrimitive?.intOrNull,
                    ratingType = obj["RatingType"]?.jsonPrimitive?.intOrNull ?: 0,
                )
            } ?: emptyList()
            val providers = json["Providers"]?.jsonArray?.mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull ?: it.jsonObject["Name"]?.jsonPrimitive?.contentOrNull
            } ?: emptyList()
            RemoteImageResult(
                images = images,
                totalRecordCount = json["TotalRecordCount"]?.jsonPrimitive?.intOrNull ?: 0,
                providers = providers,
            )
        }
    }

    override suspend fun getRemoteImageProviders(itemId: String): Result<List<ImageProviderInfo>> = engine.apiResult {
        val server = engine._currentServer.value ?: throw IllegalStateException("No server")
        val user = engine._currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Items/$itemId/RemoteImages/Providers"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return@use emptyList()
            if (!response.isSuccessful) return@use emptyList()
            JellyfinApiEngine.sharedJson.parseToJsonElement(body).jsonArray.map { elem ->
                val obj = elem.jsonObject
                ImageProviderInfo(
                    name = obj["Name"]?.jsonPrimitive?.content ?: "",
                    supportedImages = obj["SupportedImages"]?.jsonArray?.mapNotNull {
                        (it as? JsonPrimitive)?.contentOrNull
                    } ?: emptyList(),
                )
            }
        }
    }

    override suspend fun downloadRemoteImage(itemId: String, imageType: String, imageUrl: String): Result<Unit> = engine.apiResult {
        val server = engine._currentServer.value ?: throw IllegalStateException("No server")
        val user = engine._currentUser.value ?: throw IllegalStateException("No user")
        val encodedUrl = java.net.URLEncoder.encode(imageUrl, "UTF-8")
        val url = "${server.address}/Items/$itemId/RemoteImages/Download" +
            "?Type=$imageType&ImageUrl=$encodedUrl"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .post(ByteArray(0).toRequestBody())
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to download remote image: ${response.code}")
            }
        }
    }

    override suspend fun uploadSubtitle(
        itemId: String,
        data: String,
        fileName: String,
        language: String?,
        isForced: Boolean,
        isHearingImpaired: Boolean,
    ): Result<Unit> = engine.apiResult {
        val server = engine._currentServer.value ?: throw IllegalStateException("No server")
        val user = engine._currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Videos/$itemId/Subtitles"
        val body = buildJsonObject {
            put("Data", JsonPrimitive(data))
            put("FileName", JsonPrimitive(fileName))
            put("Language", JsonPrimitive(language ?: ""))
            put("IsForced", JsonPrimitive(isForced))
            put("IsHearingImpaired", JsonPrimitive(isHearingImpaired))
        }
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to upload subtitle: ${response.code}")
            }
        }
    }

    override suspend fun deleteSubtitle(itemId: String, index: Int): Result<Unit> = engine.apiResult {
        val server = engine._currentServer.value ?: throw IllegalStateException("No server")
        val user = engine._currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Videos/$itemId/Subtitles/$index"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .delete()
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to delete subtitle: ${response.code}")
            }
        }
    }

    override suspend fun searchRemoteSubtitles(itemId: String, language: String): Result<List<RemoteSubtitleInfo>> = engine.apiResult {
        val server = engine._currentServer.value ?: throw IllegalStateException("No server")
        val user = engine._currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Items/$itemId/RemoteSearch/Subtitles/$language"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return@use emptyList()
            if (!response.isSuccessful) return@use emptyList()
            JellyfinApiEngine.sharedJson.decodeFromString<List<RemoteSubtitleInfo>>(body)
        }
    }
}
