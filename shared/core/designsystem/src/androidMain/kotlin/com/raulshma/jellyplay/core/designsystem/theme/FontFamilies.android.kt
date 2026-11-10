package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.raulshma.jellyplay.core.designsystem.theme.ThemeFontRegistry.FamilyKey
import com.raulshma.jellyplay.shared.core.designsystem.R

private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private fun googleFont(name: String): FontFamily = FontFamily(
    Font(googleFont = GoogleFont(name), fontProvider = fontProvider),
)

// GMS fonts are resolved on demand from the Google Fonts provider; Android
// bundles no font binaries. Bundled overrides installed via ThemeFontRegistry
// take precedence when present (fdroid flavor, GMS-free devices). The
// @Composable getters mirror the expect declarations (the jvm actual loads
// bundled resources through the composable `org.jetbrains.compose.resources.Font` API).
internal actual val displayFontFamily: FontFamily
    @Composable get() = ThemeFontRegistry.overrideFor(FamilyKey.DISPLAY) ?: googleFont("Space Grotesk")

internal actual val bodyFontFamily: FontFamily
    @Composable get() = ThemeFontRegistry.overrideFor(FamilyKey.BODY) ?: googleFont("Roboto Flex")

internal actual val synthwaveDisplayFontFamily: FontFamily
    @Composable get() = ThemeFontRegistry.overrideFor(FamilyKey.SYNTHWAVE_DISPLAY) ?: googleFont("Orbitron")

internal actual val synthwaveBodyFontFamily: FontFamily
    @Composable get() = ThemeFontRegistry.overrideFor(FamilyKey.SYNTHWAVE_BODY) ?: googleFont("Share Tech Mono")

internal actual val soothingFontFamily: FontFamily
    @Composable get() = ThemeFontRegistry.overrideFor(FamilyKey.SOOTHING) ?: googleFont("Nunito Sans")

internal actual val monochromeDisplayFontFamily: FontFamily
    @Composable get() = ThemeFontRegistry.overrideFor(FamilyKey.MONOCHROME_DISPLAY) ?: googleFont("DotGothic16")

internal actual val monochromeBodyFontFamily: FontFamily
    @Composable get() = ThemeFontRegistry.overrideFor(FamilyKey.MONOCHROME_BODY) ?: googleFont("Space Grotesk")

internal actual val auroraFontFamily: FontFamily
    @Composable get() = ThemeFontRegistry.overrideFor(FamilyKey.AURORA) ?: googleFont("Manrope")

internal actual val sakuraFontFamily: FontFamily
    @Composable get() = ThemeFontRegistry.overrideFor(FamilyKey.SAKURA) ?: googleFont("Quicksand")

internal actual val vectorPopFontFamily: FontFamily
    @Composable get() = ThemeFontRegistry.overrideFor(FamilyKey.VECTOR_POP) ?: googleFont("Poppins")

internal actual val vividFontFamily: FontFamily
    @Composable get() = ThemeFontRegistry.overrideFor(FamilyKey.VIVID) ?: googleFont("Outfit")
