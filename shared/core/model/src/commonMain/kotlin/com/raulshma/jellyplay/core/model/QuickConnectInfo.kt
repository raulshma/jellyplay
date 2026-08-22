package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class QuickConnectInfo(
    val secret: String,
    val code: String,
)

@Immutable
@Serializable
data class QuickConnectState(
    val authenticated: Boolean,
    val secret: String,
)
