package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The system/network behavior preference enums shared by the storage and
 * network-offline settings.
 */

@Immutable
@Serializable
enum class MeteredNetworkBehavior(val displayName: String) {
    ALLOW("Allow Connection"),
    WARN("Warn Before Streaming"),
    BLOCK("Block Streaming"),
}

@Immutable
@Serializable
enum class NetworkTimeoutPreset(
    val displayName: String,
    val connectSec: Long,
    val readSec: Long,
    val writeSec: Long,
) {
    FAST("Fast (5s connect / 10s read)", 5, 10, 10),
    DEFAULT("Default (15s)", 15, 15, 15),
    RELAXED("Relaxed (30s)", 30, 30, 30),
    VERY_RELAXED("Very Relaxed (60s)", 60, 60, 60),
}
