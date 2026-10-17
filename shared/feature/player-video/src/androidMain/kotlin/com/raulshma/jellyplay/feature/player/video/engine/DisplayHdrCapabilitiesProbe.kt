package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import com.raulshma.jellyplay.feature.player.video.engine.HdrContentInfo.Companion.fromAndroidHdrType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The platform display-HDR facts behind [resolveExoHdrOutput] — the
 * [SpatializerHelper] pattern applied to color: an engine-private (non-Hilt)
 * helper that reads the API surface behind SDK guards and `runCatching`,
 * publishes an immutable snapshot as a [StateFlow], and refreshes on display
 * events (HDR monitor hot-plug / display-mode change).
 *
 * Reads `Display.getHdrCapabilities().supportedHdrTypes` (API 24+) on the
 * default display and folds the Android type ints through the shared
 * [HdrContentInfo.fromAndroidHdrType] mapping. The read itself is an
 * injectable function ([probeTypes]) so Robolectric tests pin the fold without
 * framework display plumbing — a null/failed read is "unknown", NOT "SDR":
 * [resolveExoHdrOutput] keeps the native output path for unknown displays
 * (the pre-probe behavior) instead of forcing tone mapping on a device the
 * probe simply could not answer.
 *
 * Every engine that wants the facts owns one instance (the probe registers
 * its listener for the engine's lifetime — the spatializer precedent; a
 * released engine's refresh writing a dead engine's field is harmless).
 * Threading: reads and listener callbacks arrive on the main handler, matching
 * the engines' main-thread config/stats paths.
 */
internal class DisplayHdrCapabilitiesProbe(
    context: Context,
    private val probeTypes: (Display) -> IntArray? = ::readDisplayHdrTypes,
) {

    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refresh()
        override fun onDisplayRemoved(displayId: Int) = refresh()
        override fun onDisplayChanged(displayId: Int) = refresh()
    }

    private val _support = MutableStateFlow<DisplayHdrSupport?>(readCurrent())

    /** The display's HDR support, or `null` while the probe has no answer (UNKNOWN — not SDR). */
    val support: StateFlow<DisplayHdrSupport?> = _support.asStateFlow()

    init {
        runCatching {
            displayManager?.registerDisplayListener(listener, mainHandler)
        }
    }

    /** Re-reads the default display's HDR types; emits only on an actual change. */
    fun refresh() {
        val next = readCurrent()
        if (next != _support.value) {
            _support.value = next
        }
    }

    private fun readCurrent(): DisplayHdrSupport? {
        val display = runCatching {
            displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        }.getOrNull() ?: return null
        val types = runCatching { probeTypes(display) }.getOrNull() ?: return null
        return DisplayHdrSupport.fromAndroidHdrTypes(types)
    }

    companion object {
        /**
         * The framework read: `supportedHdrTypes` on API 24+, `null` for an
         * SDR-declared display (empty array), a failed call, or pre-API 24.
         * `null` means UNKNOWN — distinct from an empty array (a real SDR
         * display); see the class KDoc.
         */
        fun readDisplayHdrTypes(display: Display): IntArray? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
            return runCatching { display.hdrCapabilities?.supportedHdrTypes }.getOrNull()
        }
    }
}

/**
 * The [EngineVideoStats.displayHdrTypes] summary shape every Android engine
 * publishes: the accepted families joined ("HDR10, Dolby Vision"), "SDR" for
 * a declared-SDR display, and `null` left for the caller when the probe has
 * no answer (UNKNOWN stays UNKNOWN — never the "SDR" label).
 */
internal fun DisplayHdrSupport.displayHdrTypesSummary(): String =
    supportedTypes.joinToString { it.displayName }.ifEmpty { "SDR" }
