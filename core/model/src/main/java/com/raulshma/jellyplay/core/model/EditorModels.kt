package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class ImageInfo(
    val imageType: String,
    val imageIndex: Int,
    val width: Int = 0,
    val height: Int = 0,
    val blurHash: String? = null,
    val imageTag: String? = null,
)

@Immutable
@Serializable
data class RemoteImageResult(
    val images: List<RemoteImageInfo> = emptyList(),
    val totalRecordCount: Int = 0,
    val providers: List<String> = emptyList(),
)

@Immutable
@Serializable
data class RemoteImageInfo(
    val providerName: String = "",
    val url: String = "",
    val thumbnailUrl: String = "",
    val height: Int = 0,
    val width: Int = 0,
    val language: String? = null,
    val communityRating: Double? = null,
    val voteCount: Int? = null,
    val ratingType: Int = 0,
)

@Immutable
@Serializable
data class MetadataEditorInfo(
    val parentalRatingOptions: List<ParentalRating> = emptyList(),
    val contentTypeOptions: List<NameValuePair> = emptyList(),
    val externalIdInfos: List<ExternalIdInfo> = emptyList(),
    val cultures: List<CultureInfo> = emptyList(),
    val countries: List<CountryInfo> = emptyList(),
)

@Immutable
@Serializable
data class ParentalRating(
    val name: String = "",
    val value: Int = 0,
)

@Immutable
@Serializable
data class NameValuePair(
    val name: String = "",
    val value: String = "",
)

@Immutable
@Serializable
data class ExternalIdInfo(
    val name: String = "",
    val key: String = "",
    val urlFormatString: String? = null,
)

@Immutable
@Serializable
data class ImageProviderInfo(
    val name: String = "",
    val supportedImages: List<String> = emptyList(),
)

@Immutable
@Serializable
data class CultureInfo(
    val name: String = "",
    val displayName: String = "",
    val twoLetterISOLanguageName: String? = null,
    val threeLetterISOLanguageName: String? = null,
)

@Immutable
@Serializable
data class CountryInfo(
    val name: String = "",
    val displayName: String = "",
    val twoLetterISORegionName: String? = null,
    val threeLetterISORegionName: String? = null,
)

@Immutable
@Serializable
data class EditorPerson(
    val id: String = "",
    val name: String = "",
    val role: String? = null,
    val type: String = "",
    val primaryImageTag: String? = null,
    val sortOrder: Int? = null,
)
