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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared font infrastructure for libass-based subtitle rendering on both the
 * ExoPlayer (ass-media) and mpv backends. Ensures a fonts directory containing
 * the bundled fallback [subfont.ttf] exists, and optionally installs a
 * user-picked font (copied from a SAF uri) so ASS files referencing a specific
 * font family can resolve it.
 *
 * The fonts directory holds ONLY `.ttf` files: libass scans `sub-fonts-dir` and
 * treats every file there as a font, so a non-font file (a `fonts.conf`) makes
 * libass emit "Error opening memory font 'fonts.conf'" and can fail font-provider
 * init on some builds (every subtitle then rasterizes to an empty bitmap). A
 * stale `fonts.conf` left by older app versions is removed on sight.
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
     * Ensures the fonts directory and bundled fallback are ready. Returns the
     * directory both engines point libass at (mpv `sub-fonts-dir`, ass-media
     * `AssHandler` font config). Idempotent.
     *
     * The directory holds ONLY `.ttf` files: libass enumerates `sub-fonts-dir`
     * and tries every file as a font, so a non-font file (a `fonts.conf`) here
     * makes libass emit "Error opening memory font 'fonts.conf'" and can fail
     * font-provider init on some builds. Older app versions wrote a `fonts.conf`
     * into this dir; delete it on sight so existing installs pick up the fix on
     * upgrade without a data clear. No replacement `fonts.conf` is written —
     * libass uses the system fontconfig by default (matching mpvkt).
     */
    fun provideFontsDir(): File {
        bundledFallback // force lazy init
        runCatching { File(fontsDir, "fonts.conf").delete() }
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
     * The font family name parsed from the bundled fallback [subfont.ttf], e.g.
     * "Droid Sans Fallback". Used as mpv's `sub-font` default so that with
     * `sub-font-provider=none` (required on devices where libass's fontconfig
     * provider fails to init — every subtitle else rasterizes empty) libass
     * resolves the requested family to the one font it has loaded from
     * `sub-fonts-dir`. Cached; null if the bundled font failed to parse.
     */
    private val bundledFallbackFamily: String? by lazy {
        bundledFallback // ensure copied
        parseFontFamily(bundledFallback)
    }

    /** The bundled fallback font family name, or null if unparseable. */
    fun bundledFallbackFamilyName(): String? = bundledFallbackFamily

    /**
     * Copies a user-picked font (from a SAF uri) into the fonts dir and parses
     * its family name. Returns null on copy/parse failure (caller keeps the
     * bundled fallback). The copied file is named deterministically from the
     * family name so repeated installs overwrite rather than accumulate.
     */
    suspend fun installUserFont(uri: Uri): InstalledFont? = withContext(Dispatchers.IO) {
        try {
            val tempFile = File(fontsDir, "user-${System.currentTimeMillis()}.ttf")
            context.contentResolver.openInputStream(uri)?.use { inp ->
                tempFile.outputStream().use { out -> inp.copyTo(out) }
            } ?: return@withContext null
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
