package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
enum class NavigationStyle {
    EXPRESSIVE,
    CLASSIC,
}
