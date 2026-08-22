package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class ServerInfo(
    val id: String,
    val name: String,
    val address: String,
    val userId: String? = null,
    val accessToken: String? = null,
    val isConnected: Boolean = false,
    val alternateAddresses: List<String> = emptyList(),
    val allowCleartext: Boolean = false,
)

@Immutable
@Serializable
data class UserInfo(
    val id: String,
    val name: String,
    val serverAddress: String,
    val accessToken: String,
    val serverId: String? = null,
    val isAdmin: Boolean = false,
    val canDeleteContent: Boolean = false,
    val maxParentalAgeRating: Int? = null,
    val primaryImageTag: String? = null,
    val enabledFolderIds: List<String> = emptyList(),
)
