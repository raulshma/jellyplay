package com.raulshma.jellyplay.distribution

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.raulshma.jellyplay.core.designsystem.theme.ThemeFontRegistry
import com.raulshma.jellyplay.core.designsystem.theme.ThemeFontRegistry.FamilyKey
import com.raulshma.jellyplay.shell.AppUpdateCoordinator
import com.raulshma.jellyplay.shell.NoopAppUpdateCoordinator
import com.raulshma.jellyplay.shell.ShellSurface
import com.raulshma.jellyplay.shared.core.designsystem.R
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The distribution-flavor linchpin — fdroid line. Exactly one implementation
 * of this object is on each variant's classpath (src/store vs src/fdroid);
 * src/main references the FQCN only. Members are grouped by seam: cast,
 * fonts, self-update.
 *
 * F-Droid line (GMS-free — the merged manifest must carry no
 * com.google.android.gms identifiers):
 *  - cast: no Play services on this classpath, so [preinitCast] is always
 *    false and the hidden media-route button is never composed.
 *  - fonts: fdroid installs the 11 theme families from bundled TTFs via
 *    ThemeFontRegistry (see [installBundledFonts] for the mapping).
 *  - self-update: none (updates come from the F-Droid client; the fdroid
 *    manifest strips REQUEST_INSTALL_PACKAGES) — [UpdateSheetOverlay] is a
 *    no-op, [selfUpdateSupported] hides the Settings → About update rows,
 *    and [updateModules] binds only the permanently-idle noop coordinator so
 *    no GitHub download/install code exists on this classpath.
 */
object DistributionPlatform {

    /** Koin modules contributed by this distribution flavor. */
    fun koinModules(app: Application): List<Module> = updateModules(app)

    /**
     * Cast pre-init, run on Dispatchers.Main after onCreate. Returns whether
     * the hidden media-route button may be composed. fdroid: always false.
     */
    fun preinitCast(activity: ComponentActivity): Boolean = false

    /** Hidden 1dp media-route button; composed only when [preinitCast] returned true. */
    @Composable
    fun HiddenCastRouteButton() {
    }

    /**
     * Installs bundled TTF font families for the theme. fdroid has no GMS
     * downloadable-fonts provider, so every family slot resolves from the
     * TTFs bundled in shared/core/designsystem's res/font/ — installed here
     * via [ThemeFontRegistry.install] (Application.onCreate, before the first
     * composition; see the registry's KDoc for the install-once semantics).
     *
     * The family→TTF mapping mirrors the GMS slots in FontFamilies.android.kt
     * (same families, same slot order); the weights follow the
     * FontFamilies.jvm.kt contract (Regular 400 / Medium 500 / Semibold 600 /
     * Bold 700). SYNTHWAVE_BODY and MONOCHROME_DISPLAY ship a single weight —
     * their GMS slots are single-style fonts too.
     *
     * This reference chain is also what keeps the TTFs IN the fdroid release
     * APK: shrinkResources strips res/font/ entries no code references, and
     * these R.font reads are the only keep path (tools/fdroid/verify-apk.sh
     * check 3 asserts the floor).
     */
    fun installBundledFonts() {
        ThemeFontRegistry.install(
            mapOf(
                FamilyKey.DISPLAY to weightedFamily(
                    R.font.space_grotesk_regular,
                    R.font.space_grotesk_medium,
                    R.font.space_grotesk_semibold,
                    R.font.space_grotesk_bold,
                ),
                FamilyKey.BODY to weightedFamily(
                    R.font.roboto_flex_regular,
                    R.font.roboto_flex_medium,
                    R.font.roboto_flex_semibold,
                    R.font.roboto_flex_bold,
                ),
                FamilyKey.SYNTHWAVE_DISPLAY to weightedFamily(
                    R.font.orbitron_regular,
                    R.font.orbitron_medium,
                    R.font.orbitron_semibold,
                    R.font.orbitron_bold,
                ),
                FamilyKey.SYNTHWAVE_BODY to FontFamily(Font(R.font.share_tech_mono_regular, FontWeight.Normal)),
                FamilyKey.SOOTHING to weightedFamily(
                    R.font.nunito_sans_regular,
                    R.font.nunito_sans_medium,
                    R.font.nunito_sans_semibold,
                    R.font.nunito_sans_bold,
                ),
                FamilyKey.MONOCHROME_DISPLAY to FontFamily(Font(R.font.dotgothic16_regular, FontWeight.Normal)),
                FamilyKey.MONOCHROME_BODY to weightedFamily(
                    R.font.space_grotesk_regular,
                    R.font.space_grotesk_medium,
                    R.font.space_grotesk_semibold,
                    R.font.space_grotesk_bold,
                ),
                FamilyKey.AURORA to weightedFamily(
                    R.font.manrope_regular,
                    R.font.manrope_medium,
                    R.font.manrope_semibold,
                    R.font.manrope_bold,
                ),
                FamilyKey.SAKURA to weightedFamily(
                    R.font.quicksand_regular,
                    R.font.quicksand_medium,
                    R.font.quicksand_semibold,
                    R.font.quicksand_bold,
                ),
                FamilyKey.VECTOR_POP to weightedFamily(
                    R.font.poppins_regular,
                    R.font.poppins_medium,
                    R.font.poppins_semibold,
                    R.font.poppins_bold,
                ),
                FamilyKey.VIVID to weightedFamily(
                    R.font.outfit_regular,
                    R.font.outfit_medium,
                    R.font.outfit_semibold,
                    R.font.outfit_bold,
                ),
            ),
        )
    }

    /** Self-update is NOT supported on this line: the About update rows are hidden. */
    val selfUpdateSupported: Boolean = false

    /**
     * Root overlay for the self-update sheet. Self-update is unsupported on
     * fdroid — the bound coordinator's state is permanently Idle, so this
     * never renders anything. [surface] exists for signature parity with the
     * store line (the single call site in JellyPlayApp is flavor-agnostic
     * and passes the shell's current surface to both); with a
     * permanently-Idle coordinator the store line's main-surface gate
     * (AppUpdateDecision.shouldShowOverlay) is moot here.
     */
    @Composable
    fun UpdateSheetOverlay(update: AppUpdateCoordinator, surface: ShellSurface) {
    }
}

/**
 * The fdroid line's self-update Koin binding: ONLY the permanently-idle
 * [NoopAppUpdateCoordinator], so the shell / What's-New graph can resolve the
 * seam uniformly. Deliberately NO ApkInstallBuilder / AppUpdateRepository
 * bindings — nothing on this line can reach the GitHub download/install code,
 * letting R8 strip those impls from fdroid APKs entirely.
 */
private fun updateModules(app: Application): List<Module> = listOf(
    module {
        single<AppUpdateCoordinator> { NoopAppUpdateCoordinator() }
    },
)

/**
 * The four-weight bundled family shape (Regular 400 / Medium 500 /
 * Semibold 600 / Bold 700) — the same weight contract FontFamilies.jvm.kt
 * serves on desktop, so theme consumers see identical style stepping on both
 * JVM platforms.
 */
private fun weightedFamily(regular: Int, medium: Int, semibold: Int, bold: Int): FontFamily = FontFamily(
    Font(regular, FontWeight.Normal),
    Font(medium, FontWeight.Medium),
    Font(semibold, FontWeight.SemiBold),
    Font(bold, FontWeight.Bold),
)
