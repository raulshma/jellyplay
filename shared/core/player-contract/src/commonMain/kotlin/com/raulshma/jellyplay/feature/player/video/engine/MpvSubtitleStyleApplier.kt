package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.SubtitleRenderDefaults
import com.raulshma.jellyplay.core.model.SubtitleStyle

/**
 * When in the mpv handle's lifecycle a [MpvSubtitleStyleApplier] run happens.
 *
 * - [INIT]: before `mpv_initialize` — the handle accepts OPTION writes only
 *   (`mpv_set_option_string`), so every value is a string pair. The Android
 *   engine's `initOptions` path.
 * - [RUNTIME]: a live handle — typed runtime property writes
 *   (`mpv_set_property` with the value's native format). The Android engine's
 *   style re-apply path and the desktop engine's only path (its handle is
 *   initialized before the first style application reaches it).
 */
enum class MpvSubtitleStylePhase {
    INIT,
    RUNTIME,
}

/**
 * The typed mpv write primitives the [MpvSubtitleStyleApplier] choreography
 * runs over — the minimal member set covering the former per-engine
 * safe-setter families (Android's throwing `is.xyz.mpv.MPV` wrapper with its
 * try/catch-and-log wrappers, the desktop's JNA int-return calls).
 *
 * Contract: implementations ABSORB their platform's write failures (log + no
 * rethrow) — the applier's choreography is exception-free by construction.
 * Ownership gating (issue #165) does NOT live here: the applier owns it, so
 * no scalar write site can forget the check the pair lists get from
 * [MpvUserSubtitleKeys.filterOwned].
 */
interface MpvPropertySurface {

    /** `mpv_set_option_string` — the pre-init write path ([MpvSubtitleStylePhase.INIT]). */
    fun setOptionString(name: String, value: String)

    /** `mpv_set_property` (MPV_FORMAT_STRING). */
    fun setPropertyString(name: String, value: String)

    /** `mpv_set_property` (MPV_FORMAT_DOUBLE). */
    fun setPropertyDouble(name: String, value: Double)

    /** `mpv_set_property` (MPV_FORMAT_INT64). */
    fun setPropertyInt(name: String, value: Int)

    /** `mpv_set_property` (MPV_FORMAT_FLAG). */
    fun setPropertyBoolean(name: String, value: Boolean)
}

/**
 * The ONE `SubtitleStyle` → mpv `sub-*` write choreography both mpv engines
 * run (the `MpvEventFold` precedent: raw policy in the contract, thin
 * adapters per platform). Previously four hand-rolled bodies with drift —
 * Android's init options + runtime properties pair and the desktop's runtime
 * body, which had silently diverged from the Android reference:
 *
 * - **Font-size discipline** (the recorded drift this applier fixes): mpv's
 *   `sub-font-size` is PINNED to libass's 720p-canvas reference
 *   ([MpvStyleMapping.MPV_LIBASS_REFERENCE_FONT_SIZE]) and the user's size is
 *   applied multiplicatively via `sub-scale = fontSize /
 *   [SubtitleRenderDefaults.REFERENCE_FONT_SIZE]` so libass layout matches
 *   across container sizes. The desktop previously wrote
 *   `sub-font-size = values.fontSize` directly (user size as an absolute
 *   libass size) and omitted `sub-font`, `sub-margin-y` — it now runs the
 *   Android discipline.
 * - **sub-visibility / sub-delay** stay APP-owned (the in-app toggle, the
 *   zoom-safe Compose overlay and in-app subtitle sync drive them) and are
 *   written unconditionally — they never route through the ownership gate.
 *
 * The choreography per phase (order preserved from the former Android bodies,
 * the reference implementation):
 *
 * 1. RUNTIME only: `sub-visibility = yes`.
 * 2. Custom vs default branch:
 *    - custom: the [MpvStyleMapping.customStyleEntries] string pairs, then
 *      (RUNTIME) the typed `sub-border-size`/`sub-shadow-offset` magnitudes,
 *      then `sub-font` (user family → [fallbackFontFamily] → `sans-serif`)
 *      and the multiplicative `sub-scale`;
 *    - default: the reset pairs ([MpvStyleMapping.defaultInitEntries] at INIT,
 *      [MpvStyleMapping.defaultEntries] at RUNTIME — `sub-ass-justify` rides
 *      the boolean setter at RUNTIME), the fallback `sub-font`, (RUNTIME) the
 *      default border/shadow magnitudes, and the default `sub-scale`.
 * 3. The shared tail: reference-pinned `sub-font-size`, `sub-pos`
 *    ([MpvStyleMapping.subPosPercent] — bottom-up percent), `sub-margin-y`
 *    (0 — `sub-pos` owns vertical placement), and the app-owned `sub-delay`.
 *
 * Engines keep only their platform-divergent extras (Android's `sub-reload`
 * + render-state log around the runtime apply, the desktop's released-handle
 * guard). Pinned by `MpvSubtitleStyleApplierTest` through a fake surface
 * recording every write.
 */
object MpvSubtitleStyleApplier {

    fun apply(
        surface: MpvPropertySurface,
        style: SubtitleStyle,
        phase: MpvSubtitleStylePhase,
        ownedKeys: Set<String>,
        fallbackFontFamily: String?,
        subtitleDelayMs: Long,
    ) {
        val values = MpvStyleMapping.computeValues(style)
        if (phase == MpvSubtitleStylePhase.RUNTIME) {
            // App-owned always (see class KDoc): the in-app show/hide toggle
            // and the zoom-safe overlay drive this flag.
            surface.setPropertyBoolean("sub-visibility", true)
        }

        if (style.applyCustomStyle) {
            MpvUserSubtitleKeys.filterOwned(MpvStyleMapping.customStyleEntries(style), ownedKeys)
                .forEach { (k, v) -> writeString(surface, phase, ownedKeys, k, v) }
            if (phase == MpvSubtitleStylePhase.RUNTIME) {
                // Typed numerics for the runtime path. sub-border-* are the
                // canonical mpv/libass names; sub-outline-* are deprecated
                // aliases that silently no-op on some libass versions.
                writeDouble(surface, phase, ownedKeys, "sub-border-size", values.outlineSize)
                writeDouble(surface, phase, ownedKeys, "sub-shadow-offset", values.shadowOffset)
            }
            writeString(
                surface,
                phase,
                ownedKeys,
                "sub-font",
                style.fontFamilyName?.takeIf { it.isNotBlank() } ?: fallbackFontFamily ?: "sans-serif",
            )
            writeDouble(
                surface,
                phase,
                ownedKeys,
                "sub-scale",
                style.fontSize.toDouble() / SubtitleRenderDefaults.REFERENCE_FONT_SIZE,
            )
        } else {
            // Reset to mpv/libass native defaults — the subset mpv needs at
            // init time is derived from the same DEFAULTS table
            // (defaultInitEntries), so this branch cannot drift from DEFAULTS.
            val resetEntries = if (phase == MpvSubtitleStylePhase.INIT) {
                MpvStyleMapping.defaultInitEntries()
            } else {
                MpvStyleMapping.defaultEntries()
            }
            MpvUserSubtitleKeys.filterOwned(resetEntries, ownedKeys).forEach { (k, v) ->
                // sub-ass-justify is flag-typed on mpv; the mapping emits it
                // as a "no" string pair, applied via the boolean setter at
                // RUNTIME (init pairs never include it).
                if (k == "sub-ass-justify" && phase == MpvSubtitleStylePhase.RUNTIME) {
                    if (k !in ownedKeys) surface.setPropertyBoolean(k, false)
                } else {
                    writeString(surface, phase, ownedKeys, k, v)
                }
            }
            writeString(surface, phase, ownedKeys, "sub-font", fallbackFontFamily ?: "sans-serif")
            if (phase == MpvSubtitleStylePhase.RUNTIME) {
                writeDouble(surface, phase, ownedKeys, "sub-border-size", MpvStyleMapping.defaultBorderSize)
                writeDouble(surface, phase, ownedKeys, "sub-shadow-offset", MpvStyleMapping.defaultShadowOffset)
            }
            writeDouble(surface, phase, ownedKeys, "sub-scale", MpvStyleMapping.defaultScale)
        }

        // Shared tail — the font-size discipline (see class KDoc), the
        // bottom-up position, the zero margin (sub-pos owns vertical), and
        // the app-owned delay.
        if (phase == MpvSubtitleStylePhase.INIT) {
            writeString(
                surface,
                phase,
                ownedKeys,
                "sub-font-size",
                MpvStyleMapping.MPV_LIBASS_REFERENCE_FONT_SIZE.toString(),
            )
            writeString(surface, phase, ownedKeys, "sub-pos", MpvStyleMapping.subPosPercent(style).toString())
            writeString(surface, phase, ownedKeys, "sub-margin-y", values.marginY.toString())
            // sub-delay stays app-owned (in-app subtitle sync drives it).
            surface.setOptionString("sub-delay", (subtitleDelayMs / 1000.0).toString())
        } else {
            writeDouble(
                surface,
                phase,
                ownedKeys,
                "sub-font-size",
                MpvStyleMapping.MPV_LIBASS_REFERENCE_FONT_SIZE.toDouble(),
            )
            writeInt(surface, phase, ownedKeys, "sub-pos", MpvStyleMapping.subPosPercent(style))
            writeInt(surface, phase, ownedKeys, "sub-margin-y", values.marginY)
            surface.setPropertyDouble("sub-delay", subtitleDelayMs / 1000.0)
        }
    }

    /** Ownership-gated string write; INIT options, RUNTIME string properties. */
    private fun writeString(
        surface: MpvPropertySurface,
        phase: MpvSubtitleStylePhase,
        ownedKeys: Set<String>,
        name: String,
        value: String,
    ) {
        if (name in ownedKeys) return
        if (phase == MpvSubtitleStylePhase.INIT) surface.setOptionString(name, value)
        else surface.setPropertyString(name, value)
    }

    /**
     * Ownership-gated double write; INIT writes the string form (mpv parses
     * numeric option strings), RUNTIME the typed property.
     */
    private fun writeDouble(
        surface: MpvPropertySurface,
        phase: MpvSubtitleStylePhase,
        ownedKeys: Set<String>,
        name: String,
        value: Double,
    ) {
        if (name in ownedKeys) return
        if (phase == MpvSubtitleStylePhase.INIT) surface.setOptionString(name, value.toString())
        else surface.setPropertyDouble(name, value)
    }

    /** [writeDouble] for integer magnitudes ([MpvSubtitleStylePhase.RUNTIME] typed). */
    private fun writeInt(
        surface: MpvPropertySurface,
        phase: MpvSubtitleStylePhase,
        ownedKeys: Set<String>,
        name: String,
        value: Int,
    ) {
        if (name in ownedKeys) return
        if (phase == MpvSubtitleStylePhase.INIT) surface.setOptionString(name, value.toString())
        else surface.setPropertyInt(name, value)
    }
}
