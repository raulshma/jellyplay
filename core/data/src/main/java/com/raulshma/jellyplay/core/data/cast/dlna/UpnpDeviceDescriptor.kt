package com.raulshma.jellyplay.core.data.cast.dlna

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.DlnaDeviceRef

@Immutable
data class UpnpDevice(
    val udn: String,
    val friendlyName: String,
    val modelName: String = "",
    val manufacturer: String = "",
    val modelDescription: String = "",
    val locationUrl: String,
    val iconUrl: String? = null,
    val avTransportControlUrl: String? = null,
    val renderingControlUrl: String? = null,
    val connectionManagerUrl: String? = null,
)

data class TransportInfo(
    val positionMs: Long,
    val durationMs: Long,
    val state: TransportState,
)

enum class TransportState {
    PLAYING,
    PAUSED,
    STOPPED,
    TRANSITIONING,
    NO_MEDIA,
    UNKNOWN,
}
