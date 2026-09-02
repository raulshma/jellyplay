package com.raulshma.jellyplay.core.network.api

/**
 * Desktop [DeviceCodecCapabilities]: desktop ships mpv (software decode via
 * libav/ffmpeg), so DeviceProfileProvider treats the MPV player as exempt with
 * a fixed permissive profile and never consults a hardware codec list. Both
 * sets stay empty; the hardware profile built from them degrades to the
 * forced-audio fallback rather than over-claiming codecs.
 */
class DesktopDeviceCodecCapabilities : DeviceCodecCapabilities {

    override val supportedVideoCodecs: Set<String> = emptySet()

    override val supportedAudioCodecs: Set<String> = emptySet()
}
