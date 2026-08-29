package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
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
// bundles no font binaries. The @Composable getters mirror the expect
// declarations (the jvm/wasm actuals load bundled resources through the
// composable `org.jetbrains.compose.resources.Font` API).
internal actual val displayFontFamily: FontFamily
    @Composable get() = googleFont("Space Grotesk")

internal actual val bodyFontFamily: FontFamily
    @Composable get() = googleFont("Roboto Flex")

internal actual val synthwaveDisplayFontFamily: FontFamily
    @Composable get() = googleFont("Orbitron")

internal actual val synthwaveBodyFontFamily: FontFamily
    @Composable get() = googleFont("Share Tech Mono")

internal actual val soothingFontFamily: FontFamily
    @Composable get() = googleFont("Nunito Sans")

internal actual val monochromeDisplayFontFamily: FontFamily
    @Composable get() = googleFont("DotGothic16")

internal actual val monochromeBodyFontFamily: FontFamily
    @Composable get() = googleFont("Space Grotesk")
