package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The desktop [DevicePerformanceProbe] (issue #186 §5.5 desktop signals
 * bullet): OS name/version, cores, `Runtime.maxMemory`, and no codec probe —
 * `isAndroid = false`, so the classifier short-circuits AUTO to HIGH (§5.13:
 * "Desktop: Android-only UI in v1; AUTO resolves HIGH").
 *
 * The signals are still gathered honestly for the settings diagnostics
 * display: on desktop `totalRamMb` reports the JVM heap maximum (the only RAM
 * seam the JVM exposes — it is never scored for non-Android signals), the
 * decode fields stay null ("not measured"), and the SoC/model slots carry the
 * OS identity.
 */
class DesktopDevicePerformanceProbe : DevicePerformanceProbe {

    override suspend fun probe(): DevicePerformanceSignals? = withContext(Dispatchers.Default) {
        runCatchingRethrowingCancellation {
            val osName = System.getProperty("os.name") ?: "unknown"
            val osVersion = System.getProperty("os.version") ?: "unknown"
            DevicePerformanceSignals(
                isAndroid = false,
                isLowRamDevice = false,
                totalRamMb = (Runtime.getRuntime().maxMemory() / BYTES_PER_MB)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt(),
                cpuCores = Runtime.getRuntime().availableProcessors(),
                // No codec probe on desktop — the classifier ignores decode
                // signals for non-Android probes (see its class KDoc).
                maxHardwareDecodeHeight = null,
                hardwareHevcDecode = false,
                maxHardwareDecodeBitrate = null,
                socFamily = osName,
                gpuRenderer = null,
                isEmulator = false,
                manufacturer = null,
                model = "$osName $osVersion",
                device = null,
                board = null,
            )
        }.getOrNull()
    }

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024L
    }
}
