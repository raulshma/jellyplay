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

/**
 * The editable field set of one item, as submitted by the metadata editor's
 * save action. Bundled so repository/update call sites don't thread ~30
 * positional primitives.
 */
@Immutable
@Serializable
data class EditableItemMetadata(
    val name: String,
    val originalTitle: String? = null,
    val sortName: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val studios: List<String> = emptyList(),
    val communityRating: Float? = null,
    val criticRating: Float? = null,
    val officialRating: String? = null,
    val customRating: String? = null,
    val productionYear: Int? = null,
    val premiereDate: String? = null,
    val endDate: String? = null,
    val runtimeTicks: Long? = null,
    val indexNumber: Int? = null,
    val parentIndexNumber: Int? = null,
    val displayOrder: String? = null,
    val status: String? = null,
    val airDays: List<String> = emptyList(),
    val airTime: String? = null,
    val people: List<EditorPerson> = emptyList(),
    val providerIds: Map<String, String> = emptyMap(),
    val lockData: Boolean = false,
    val lockedFields: List<String> = emptyList(),
    val preferredMetadataLanguage: String? = null,
    val preferredMetadataCountryCode: String? = null,
    val taglines: List<String> = emptyList(),
    val productionLocations: List<String> = emptyList(),
    val dateCreated: String? = null,
    val type: String = "Unknown",
)
