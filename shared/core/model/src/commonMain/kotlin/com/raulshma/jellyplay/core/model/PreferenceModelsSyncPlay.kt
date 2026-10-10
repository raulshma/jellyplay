package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The SyncPlay/casting preference enums (the datastore's `syncplaycast`
 * domain): how the client joins sessions and which cast target it prefers.
 */

@Immutable
@Serializable
enum class SyncPlayJoinBehavior(val displayName: String) {
    ALWAYS_JOIN("Always Join"),
    ASK("Always Ask"),
    NEVER_JOIN("Never Join"),
}

@Immutable
@Serializable
enum class CastingStrategy(val displayName: String) {
    PREFER_CAST("Prefer Google Cast"),
    PREFER_DLNA("Prefer DLNA"),
    ASK("Always Ask"),
}
