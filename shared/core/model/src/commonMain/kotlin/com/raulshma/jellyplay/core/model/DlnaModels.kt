package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class DlnaDeviceRef(
    val id: String,
    val name: String,
    val locationUrl: String,
)
