package com.raulshma.jellyplay.core.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UpdateItemRequest(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("Genres") val genres: List<String>,
    @SerialName("Tags") val tags: List<String>,
    @SerialName("Studios") val studios: List<StudioEntry>,
    @SerialName("LockData") val lockData: Boolean,
    @SerialName("OriginalTitle") val originalTitle: String? = null,
    @SerialName("ForcedSortName") val sortName: String? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("Taglines") val taglines: List<String>? = null,
    @SerialName("CommunityRating") val communityRating: Double? = null,
    @SerialName("CriticRating") val criticRating: Double? = null,
    @SerialName("OfficialRating") val officialRating: String? = null,
    @SerialName("CustomRating") val customRating: String? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("PremiereDate") val premiereDate: String? = null,
    @SerialName("EndDate") val endDate: String? = null,
    @SerialName("RunTimeTicks") val runtimeTicks: Long? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("DisplayOrder") val displayOrder: String? = null,
    @SerialName("Status") val status: String? = null,
    @SerialName("AirDays") val airDays: List<String>? = null,
    @SerialName("AirTime") val airTime: String? = null,
    @SerialName("People") val people: List<PersonEntry>? = null,
    @SerialName("ProviderIds") val providerIds: Map<String, String>? = null,
    @SerialName("LockedFields") val lockedFields: List<String>? = null,
    @SerialName("PreferredMetadataLanguage") val preferredMetadataLanguage: String? = null,
    @SerialName("PreferredMetadataCountryCode") val preferredMetadataCountryCode: String? = null,
    @SerialName("ProductionLocations") val productionLocations: List<String>? = null,
) {
    @Serializable
    data class StudioEntry(
        @SerialName("Name") val name: String,
    )

    @Serializable
    data class PersonEntry(
        @SerialName("Id") val id: String,
        @SerialName("Name") val name: String,
        @SerialName("Role") val role: String? = null,
        @SerialName("Type") val type: String,
        @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
        @SerialName("SortOrder") val sortOrder: Int? = null,
    )
}
