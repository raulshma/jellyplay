package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import com.raulshma.jellyplay.core.designsystem.generated.resources.Res
import com.raulshma.jellyplay.core.designsystem.generated.resources.nunito_sans_bold
import com.raulshma.jellyplay.core.designsystem.generated.resources.nunito_sans_medium
import com.raulshma.jellyplay.core.designsystem.generated.resources.nunito_sans_regular
import com.raulshma.jellyplay.core.designsystem.generated.resources.nunito_sans_semibold
import com.raulshma.jellyplay.core.designsystem.generated.resources.orbitron_bold
import com.raulshma.jellyplay.core.designsystem.generated.resources.orbitron_medium
import com.raulshma.jellyplay.core.designsystem.generated.resources.orbitron_regular
import com.raulshma.jellyplay.core.designsystem.generated.resources.orbitron_semibold
import com.raulshma.jellyplay.core.designsystem.generated.resources.roboto_flex_bold
import com.raulshma.jellyplay.core.designsystem.generated.resources.roboto_flex_medium
import com.raulshma.jellyplay.core.designsystem.generated.resources.roboto_flex_regular
import com.raulshma.jellyplay.core.designsystem.generated.resources.roboto_flex_semibold
import com.raulshma.jellyplay.core.designsystem.generated.resources.share_tech_mono_regular
import com.raulshma.jellyplay.core.designsystem.generated.resources.space_grotesk_bold
import com.raulshma.jellyplay.core.designsystem.generated.resources.space_grotesk_medium
import com.raulshma.jellyplay.core.designsystem.generated.resources.space_grotesk_regular
import com.raulshma.jellyplay.core.designsystem.generated.resources.space_grotesk_semibold

/**
 * Bundled brand fonts (all SIL OFL — license texts in the module's
 * licenses/fonts/ directory) so desktop renders the same typography Android
 * gets from the GMS fonts provider. Each family ships the static weights the
 * app actually requests (Normal 400, Medium 500, SemiBold 600, Bold 700):
 * every Typography slot and every scattered `fontWeight` usage across the
 * shared feature modules stays inside that range — the ExtraBold/Black
 * outliers (home hero, Seerr episode badge, newsletter header, karaoke
 * lyrics) all ride on display styles, and Android's variable Space Grotesk
 * clamps at 700 there too, so the nearest-match resolution is identical.
 *
 * Share Tech Mono is only published as a Regular cut; the Medium/SemiBold
 * requests in the Synthwave body slots resolve to the 400 face, matching
 * Android where the GMS font has no bolder variation either.
 *
 * Resolution is @Composable because CMP 1.11.1 only exposes the resource
 * `Font` loader as a composable.
 */
internal actual val displayFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.space_grotesk_regular, FontWeight.Normal, FontStyle.Normal),
        Font(Res.font.space_grotesk_medium, FontWeight.Medium, FontStyle.Normal),
        Font(Res.font.space_grotesk_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(Res.font.space_grotesk_bold, FontWeight.Bold, FontStyle.Normal),
    )

internal actual val bodyFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.roboto_flex_regular, FontWeight.Normal, FontStyle.Normal),
        Font(Res.font.roboto_flex_medium, FontWeight.Medium, FontStyle.Normal),
        Font(Res.font.roboto_flex_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(Res.font.roboto_flex_bold, FontWeight.Bold, FontStyle.Normal),
    )

internal actual val synthwaveDisplayFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.orbitron_regular, FontWeight.Normal, FontStyle.Normal),
        Font(Res.font.orbitron_medium, FontWeight.Medium, FontStyle.Normal),
        Font(Res.font.orbitron_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(Res.font.orbitron_bold, FontWeight.Bold, FontStyle.Normal),
    )

internal actual val synthwaveBodyFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.share_tech_mono_regular, FontWeight.Normal, FontStyle.Normal),
    )

internal actual val soothingFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.nunito_sans_regular, FontWeight.Normal, FontStyle.Normal),
        Font(Res.font.nunito_sans_medium, FontWeight.Medium, FontStyle.Normal),
        Font(Res.font.nunito_sans_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(Res.font.nunito_sans_bold, FontWeight.Bold, FontStyle.Normal),
    )

// Accepted delta: the Monochrome variant's pixel display font (DotGothic16)
// is NOT bundled for desktop and web — a ~2.1 MB Japanese font for a single
// theme-variant display slot is a disproportionate bundle cost, and Android
// only fetches it on demand from GMS (Android ships no font binaries either),
// so bundling would not close an Android-side gap, it would only grow the
// Android APK. Desktop and web keep the system monospace fallback for this
// slot.
internal actual val monochromeDisplayFontFamily: FontFamily
    @Composable get() = FontFamily.Monospace

internal actual val monochromeBodyFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.space_grotesk_regular, FontWeight.Normal, FontStyle.Normal),
        Font(Res.font.space_grotesk_medium, FontWeight.Medium, FontStyle.Normal),
        Font(Res.font.space_grotesk_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(Res.font.space_grotesk_bold, FontWeight.Bold, FontStyle.Normal),
    )
