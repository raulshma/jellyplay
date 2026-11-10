package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.ui.text.font.FontFamily

/**
 * Runtime override point for the Android theme font families.
 *
 * Designsystem's android actual ([FontFamilies.android.kt]) resolves the 11
 * expect/actual family slots through the GMS downloadable-fonts provider,
 * which needs Google Play services on the device. An app flavor that must
 * stay free of GMS (fdroid) installs bundled-TTF [FontFamily]s here before
 * the first composition; the family getters then read the override instead
 * of the provider. Designsystem itself never installs anything and knows
 * nothing about flavors — the GMS provider stays the default.
 *
 * Volatile + install-once semantics: [install] runs in `Application.onCreate`,
 * before any Composable reads a family, so the plain volatile read in
 * [overrideFor] needs no locking on the (composable, hot) read path.
 * Re-installation is technically allowed (the map is replaced wholesale) but
 * nothing re-composes on it — slots already composed keep the family they last
 * read — so treat it as once-at-startup.
 */
object ThemeFontRegistry {

    /** Keys mirror the 11 expect/actual family slots (see Type.kt). */
    enum class FamilyKey {
        DISPLAY,
        BODY,
        SYNTHWAVE_DISPLAY,
        SYNTHWAVE_BODY,
        SOOTHING,
        MONOCHROME_DISPLAY,
        MONOCHROME_BODY,
        AURORA,
        SAKURA,
        VECTOR_POP,
        VIVID,
    }

    @Volatile
    private var overrides: Map<FamilyKey, FontFamily>? = null

    /** True once [install] has run; startup diagnostics and tests only. */
    val hasOverrides: Boolean
        get() = overrides != null

    /**
     * Replace the override map wholesale. Call from `Application.onCreate`,
     * before the first composition — see the class KDoc for the semantics.
     */
    fun install(map: Map<FamilyKey, FontFamily>) {
        overrides = map.toMap() // defensive copy; later caller mutation must not leak in
    }

    /** Null = no override installed; the caller falls back to the GMS provider. */
    internal fun overrideFor(key: FamilyKey): FontFamily? = overrides?.get(key)
}
