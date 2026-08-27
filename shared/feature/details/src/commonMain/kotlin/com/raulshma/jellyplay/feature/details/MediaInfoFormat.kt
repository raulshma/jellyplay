package com.raulshma.jellyplay.feature.details

/**
 * Pure formatters for the technical media-info screen.
 *
 * Previously these four value-only helpers lived as private functions on
 * [MediaInfoScreen], so their branches (bitrate unit selection, tick→Hh Mm
 * math, resolution thresholds, channel mapping) were untestable without driving
 * the whole composable. Extracted to top-level `internal` functions so each has
 * a home and a direct (synchronous, reflection-free) test surface — the same
 * pattern used by [SmartPlayResolver] and [resolveTmdbId].
 */
internal object MediaInfoFormat {

    /**
     * Formats a bitrate (bits/sec) into Mbps / Kbps / bps.
     *
     * Wave 16C purification: `"%.1f".format` / `"%.0f".format` are JVM-API
     * (default-locale formatting, no wasm variant). Replaced with integer/
     * HALF_UP math — identical digits, and the decimal point no longer flips
     * to a comma under non-English JVM default locales.
     */
    fun formatBitrate(bps: Long): String = when {
        bps >= 1_000_000 -> formatTenths(bps / 1_000_000.0) + " Mbps"
        bps >= 1_000 -> "${kotlin.math.round(bps / 1_000.0).toLong()} Kbps"
        else -> "$bps bps"
    }

    /** "%.1f" shape via integer math: HALF_UP at the first decimal, dot rendered. */
    private fun formatTenths(value: Double): String {
        val magnitude = kotlin.math.round(kotlin.math.abs(value) * 10).toLong()
        val rendered = "${magnitude / 10}.${magnitude % 10}"
        return if (value < 0) "-$rendered" else rendered
    }

    /**
     * Formats a Jellyfin runtime expressed in .NET ticks (10 000 000 / second)
     * into "Xh Ym" (≥ 1h) or "Xm Ys" (< 1h).
     */
    fun formatTicks(ticks: Long): String {
        val totalSeconds = ticks / 10_000_000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m ${seconds}s"
    }

    /** Maps a video stream height to a human-readable quality bucket. */
    fun resolutionLabel(height: Int?): String = when {
        height == null -> "Unknown"
        height >= 2160 -> "4K UHD"
        height >= 1440 -> "1440p QHD"
        height >= 1080 -> "1080p Full HD"
        height >= 720 -> "720p HD"
        height >= 480 -> "480p SD"
        else -> "${height}p"
    }

    /** Maps an audio channel count to a labelled layout (mono / stereo / 5.1 / 7.1). */
    fun channelLabel(channels: Int): String = when (channels) {
        1 -> "1.0 (Mono)"
        2 -> "2.0 (Stereo)"
        6 -> "5.1 (Surround)"
        8 -> "7.1 (Surround)"
        else -> "$channels ch"
    }
}
