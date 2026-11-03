package com.raulshma.jellyplay.core.data.playback

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.opengl.EGL14
import android.opengl.GLES10
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Android [DevicePerformanceProbe] (issue #186 §5.5 signals list):
 *
 *  - `ActivityManager.isLowRamDevice` + `MemoryInfo.totalMem` for the RAM
 *    signals;
 *  - `Runtime.availableProcessors()` for cores;
 *  - `Build.SOC_MODEL` (API 31+, "unknown" rejected) with `HARDWARE` /
 *    `BOARD` / `DEVICE` fallbacks for the SoC family, plus the raw
 *    manufacturer/model/device/board identity strings the detection
 *    fingerprint hashes;
 *  - a `MediaCodecList(REGULAR_CODECS)` scan over the hardware video
 *    decoders (`video/avc` / `video/hevc` / `video/av1` /
 *    `video/x-vnd.on2.vp9`) for the tallest supported height, the HEVC flag
 *    and the bitrate-range upper bound — `isHardwareAccelerated` on API 29+,
 *    the software-name heuristic below it;
 *  - `FINGERPRINT`/`MODEL`/`PRODUCT`/`HARDWARE` sniffing for emulator
 *    detection;
 *  - the GL renderer string when one is reachable (diagnostics only).
 *
 * Every platform read is wrapped defensively: a failed sub-read degrades that
 * field (a failed codec query is null, not a crash); only a failure of the
 * basic RAM/cores reads makes [probe] return null (→ MEDIUM + PROBE_FAILED).
 * The probe hops to [Dispatchers.Default] itself so callers never have to
 * dispatch off the main thread.
 */
class AndroidDevicePerformanceProbe(private val context: Context) : DevicePerformanceProbe {

    override suspend fun probe(): DevicePerformanceSignals? = withContext(Dispatchers.Default) {
        runCatching { gatherSignals() }.getOrNull()
    }

    private fun gatherSignals(): DevicePerformanceSignals {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: error("ActivityManager unavailable")
        val memoryInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val (hardwareHeight, hardwareBitrate, hardwareHevc) =
            scanHardwareVideoDecoders() ?: Triple(null, null, false)
        return DevicePerformanceSignals(
            isAndroid = true,
            isLowRamDevice = runCatching { activityManager.isLowRamDevice }.getOrDefault(false),
            totalRamMb = (memoryInfo.totalMem / BYTES_PER_MB).toInt(),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            maxHardwareDecodeHeight = hardwareHeight,
            hardwareHevcDecode = hardwareHevc,
            maxHardwareDecodeBitrate = hardwareBitrate,
            socFamily = socFamily(),
            gpuRenderer = glRendererOrNull(),
            isEmulator = isEmulator(),
            manufacturer = Build.MANUFACTURER?.takeIf { it.isNotBlank() },
            model = Build.MODEL?.takeIf { it.isNotBlank() },
            device = Build.DEVICE?.takeIf { it.isNotBlank() },
            board = Build.BOARD?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * First non-blank of `SOC_MODEL` (API 31+, with the documented "unknown"
     * placeholder rejected), `HARDWARE`, `BOARD`, `DEVICE` — the §5.5
     * fallback chain the entry-SoC matcher and the fingerprint consume.
     */
    private fun socFamily(): String? = listOf(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
        } else {
            null
        },
        Build.HARDWARE?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) },
        Build.BOARD?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) },
        Build.DEVICE?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) },
    ).firstOrNull { it != null }

    /**
     * The GL renderer string — only when a GL context is current on the
     * calling thread. The probe runs on [Dispatchers.Default], which has
     * none, so this reads null in practice; that degradation is the point:
     * `glGetString` without a current context is undefined behavior, and on
     * some vendor GLES drivers (POCO F1 / beryllium, LineageOS 22.2 observed)
     * it is a native SIGSEGV inside `glGetString` that no `runCatching` can
     * catch — it killed the process at playback start during issue-#186
     * device validation. Diagnostics-only either way: the classifier and the
     * detection fingerprint exclude this field.
     */
    private fun glRendererOrNull(): String? {
        if (EGL14.eglGetCurrentContext() == EGL14.EGL_NO_CONTEXT) return null
        return runCatching { GLES10.glGetString(GLES10.GL_RENDERER) }.getOrNull()
    }

    /**
     * The hardware video-decoder scan. Returns null when the codec list
     * itself cannot be read (→ the height/HEVC fields degrade, the probe
     * still succeeds); individual codec failures are skipped.
     */
    private fun scanHardwareVideoDecoders(): Triple<Int?, Long?, Boolean>? {
        val codecInfos = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        }.getOrNull() ?: return null

        var maxHeight: Int? = null
        var maxBitrate: Long? = null
        var hevc = false
        for (info in codecInfos) {
            if (info.isEncoder) continue
            if (!isHardwareDecoder(info)) continue
            for (mime in VIDEO_MIMES) {
                val videoCapabilities = runCatching {
                    info.getCapabilitiesForType(mime).videoCapabilities
                }.getOrNull() ?: continue
                runCatching {
                    val height = videoCapabilities.supportedHeights.upper
                    if (maxHeight == null || height > maxHeight) maxHeight = height
                    val bitrate = videoCapabilities.bitrateRange.upper.toLong()
                    if (maxBitrate == null || bitrate > maxBitrate) maxBitrate = bitrate
                }
                if (mime == MIME_HEVC) hevc = true
            }
        }
        return Triple(maxHeight, maxBitrate, hevc)
    }

    /**
     * API 29+ reads the platform's own flag; below it the classic heuristic —
     * software decoders are the `omx.google.*` / `c2.android.*` / `*.sw.*`
     * families.
     */
    private fun isHardwareDecoder(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated
        } else {
            val name = info.name.lowercase()
            !name.startsWith("omx.google") &&
                !name.startsWith("c2.android") &&
                !name.contains(".sw.") &&
                !name.contains(".sw.dec")
        }

    /** The §5.5 emulator sniff across FINGERPRINT/MODEL/PRODUCT/HARDWARE. */
    private fun isEmulator(): Boolean {
        val combined = buildString {
            append(Build.FINGERPRINT).append(' ')
            append(Build.MODEL).append(' ')
            append(Build.PRODUCT).append(' ')
            append(Build.HARDWARE)
        }.lowercase()
        return EMULATOR_HINTS.any { combined.contains(it) }
    }

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024L

        const val MIME_AVC = "video/avc"
        const val MIME_HEVC = "video/hevc"
        const val MIME_AV1 = "video/av1"
        const val MIME_VP9 = "video/x-vnd.on2.vp9"

        val VIDEO_MIMES = arrayOf(MIME_AVC, MIME_HEVC, MIME_AV1, MIME_VP9)

        val EMULATOR_HINTS = listOf(
            "generic",
            "emulator",
            "sdk_gphone",
            "google_sdk",
            "ranchu",
            "goldfish",
            "vsemu",
        )
    }
}
