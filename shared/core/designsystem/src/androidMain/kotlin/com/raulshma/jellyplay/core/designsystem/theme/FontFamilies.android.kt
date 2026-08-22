package com.raulshma.jellyplay.core.designsystem.theme

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

actual val displayFontFamily: FontFamily = googleFont("Space Grotesk")
actual val bodyFontFamily: FontFamily = googleFont("Roboto Flex")
actual val synthwaveDisplayFontFamily: FontFamily = googleFont("Orbitron")
actual val synthwaveBodyFontFamily: FontFamily = googleFont("Share Tech Mono")
actual val soothingFontFamily: FontFamily = googleFont("Nunito Sans")
actual val monochromeDisplayFontFamily: FontFamily = googleFont("DotGothic16")
actual val monochromeBodyFontFamily: FontFamily = googleFont("Space Grotesk")
