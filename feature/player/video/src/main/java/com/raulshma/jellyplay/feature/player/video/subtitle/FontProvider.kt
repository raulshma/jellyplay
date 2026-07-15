package com.raulshma.jellyplay.feature.player.video.subtitle

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.yubyf.truetypeparser.TTFFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared font infrastructure for libass-based subtitle rendering on both the
 * ExoPlayer (ass-media) and mpv backends. Ensures a fonts directory containing
 * the bundled fallback [subfont.ttf] and a fontconfig `fonts.conf` exists, and
 * optionally installs a user-picked font (copied from a SAF uri) so ASS files
 * referencing a specific font family can resolve it.
 *
 * Extracted from [com.raulshma.jellyplay.feature.player.video.engine.MpvPlayerEngine]'s
 * private `setupFonts` / `writeFontsConf` so both engines share one source of
 * truth for font setup.
 */
@Singleton
class FontProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val fontsDir: File by lazy {
        File(context.cacheDir, "subtitle-fonts").apply { mkdirs() }
    }

    /** Bundled fallback font, always present in the fonts dir. */
    private val bundledFallback: File by lazy {
        File(fontsDir, "subfont.ttf").also { f ->
            if (!f.exists() || f.length() == 0L) {
                context.assets.open("subfont.ttf").use { inp ->
                    f.outputStream().use { out -> inp.copyTo(out) }
                }
            }
        }
    }

    /**
     * Ensures the fonts directory, bundled fallback, and fonts.conf are ready.
     * Returns the directory both engines point libass at (mpv `sub-fonts-dir`,
     * ass-media `AssHandler` font config). Idempotent.
     */
    fun provideFontsDir(): File {
        bundledFallback // force lazy init
        writeFontsConf(fontsDir, File(context.cacheDir, "fontconfig"))
        return fontsDir
    }

    /**
     * Returns the selected font with the requested synthetic weight and slant.
     *
     * Android's native subtitle renderer only accepts a single [Typeface].  In
     * particular, passing the regular bundled face alone silently drops the
     * bold/italic toggles, so apply the requested style after loading the font.
     */
    fun typefaceFor(style: SubtitleStyle): Typeface {
        val fontFile = style.fontFamilyPath
            ?.let(::File)
            ?.takeIf(File::isFile)
            ?: bundledFallback
        val base = runCatching { Typeface.createFromFile(fontFile) }
            .getOrDefault(Typeface.SANS_SERIF)
        val typefaceStyle = when {
            style.bold && style.italic -> Typeface.BOLD_ITALIC
            style.bold -> Typeface.BOLD
            style.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(base, typefaceStyle)
    }

    /** Absolute path to the bundled fallback, for engines that accept a font file. */
    fun bundledFallbackPath(): String = bundledFallback.absolutePath

    /**
     * Copies a user-picked font (from a SAF uri) into the fonts dir and parses
     * its family name. Returns null on copy/parse failure (caller keeps the
     * bundled fallback). The copied file is named deterministically from the
     * family name so repeated installs overwrite rather than accumulate.
     */
    suspend fun installUserFont(uri: Uri): InstalledFont? {
        return try {
            val tempFile = File(fontsDir, "user-${System.currentTimeMillis()}.ttf")
            context.contentResolver.openInputStream(uri)?.use { inp ->
                tempFile.outputStream().use { out -> inp.copyTo(out) }
            } ?: return null
            val family = parseFontFamily(tempFile)
            val finalName = family?.let { sanitizeFileName(it) + ".ttf" } ?: tempFile.name
            val finalFile = File(fontsDir, finalName)
            if (finalFile != tempFile) {
                tempFile.renameTo(finalFile)
            }
            InstalledFont(finalFile, family ?: finalFile.nameWithoutExtension)
        } catch (e: Exception) {
            null
        }
    }

    private fun sanitizeFileName(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "user" }

    companion object {
        /**
         * Writes a fontconfig `fonts.conf` into [dir] aliasing common generic
         * families (serif/sans-serif/monospace) to their Android system font
         * equivalents, with [cacheDir] as the fontconfig cache. Mirrors the
         * config previously inlined in MpvPlayerEngine.writeFontsConf.
         *
         * Internal so the pure XML-generation logic is unit-testable without a
         * Context.
         */
        internal fun writeFontsConf(dir: File, cacheDir: File) {
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val configFile = File(dir, "fonts.conf")
            val config = """
                |<fontconfig>
                |  <dir>/system/fonts/</dir>
                |  <dir>/product/fonts/</dir>
                |  <dir>${dir.absolutePath}</dir>
                |  <cachedir>${cacheDir.absolutePath}</cachedir>
                |  <alias><family>serif</family><prefer><family>Noto Serif</family></prefer></alias>
                |  <alias><family>sans-serif</family><prefer><family>Roboto</family><family>Noto Sans</family></prefer></alias>
                |  <alias><family>monospace</family><prefer><family>Droid Sans Mono</family></prefer></alias>
                |  <match target="pattern"><edit name="family" mode="append_last"><string>sans-serif</string></edit></match>
                |</fontconfig>
            """.trimMargin()
            configFile.writeText(config)
        }

        /**
         * Extracts the font family name from a .ttf/.otf file's name table via
         * the truetypeparser lib. Returns null on any parse failure (corrupt
         * file, non-font, IO error) — callers fall back to the filename.
         *
         * Uses the `TTFFile.open(InputStream)` overload (the form mpvkt uses on
         * the identical `io.github.yubyf:truetypeparser-light` dependency) and
         * reads the `.families` map — the `-light` variant exposes only
         * `families` (no `familyName`), matching mpvkt's
         * `TTFFile.open(...).families.values.first()` usage.
         *
         * Internal so the null-on-failure contract is unit-testable without a
         * Context.
         */
        internal fun parseFontFamily(file: File): String? = runCatching {
            file.inputStream().use { stream ->
                TTFFile.open(stream).families.values.firstOrNull { it.isNotBlank() }
            }
        }.getOrNull()
    }
}

data class InstalledFont(val file: File, val familyName: String)
