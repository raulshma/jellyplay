package com.raulshma.jellyplay.feature.player.video.subtitle

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.lruMapOf
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
     * Cached font file bytes, keyed by file name with a `(length, lastModified)`
     * stamp so a re-installed user font (same deterministic name, new content)
     * is re-read. Populated from [Dispatchers.IO] by [prewarm]; engine `load()`
     * reads from this cache so every ASS session start (each track change) stops
     * re-reading multi-MB .ttf files from disk on the Main thread.
     */
    private class FontBytes(val length: Long, val lastModified: Long, val bytes: ByteArray)

    private val fontBytesCache = java.util.concurrent.ConcurrentHashMap<String, FontBytes>()

    /**
     * Ensures the fonts dir + bundled fallback exist and loads every installed
     * `.ttf`'s bytes into the cache, off the caller's dispatcher. Idempotent;
     * safe to call repeatedly (cached stamps short-circuit unchanged files).
     */
    suspend fun prewarm() {
        withContext(Dispatchers.IO) {
            loadFontBytesInternal()
        }
    }

    /** Cache-populating read of every font file's bytes; runs on IO. */
    private fun loadFontBytesInternal(): Map<String, ByteArray> {
        val dir = provideFontsDir()
        val result = LinkedHashMap<String, ByteArray>()
        dir.listFiles { file -> file.isFile && file.extension.equals("ttf", ignoreCase = true) }
            ?.forEach { ttf ->
                readFontBytes(ttf)?.takeIf { it.isNotEmpty() }?.let { bytes ->
                    result[ttf.nameWithoutExtension] = bytes
                }
            }
        return result
    }

    /** Cache hit (stamp-validated) or disk read; stores the result. */
    private fun readFontBytes(file: File): ByteArray? {
        val cached = fontBytesCache[file.name]
        val length = file.length()
        val lastModified = file.lastModified()
        if (cached != null && cached.length == length && cached.lastModified == lastModified) {
            return cached.bytes
        }
        return runCatching { file.readBytes() }
            .getOrNull()
            ?.also { fontBytesCache[file.name] = FontBytes(length, lastModified, it) }
    }

    /**
     * Font bytes for the ass-media `AssHandler.addFont(name, bytes)` API, as a
     * `(familyName, bytes)` map. Cache-first — the name says so — synchronous
     * on purpose because the engine's `load()` is Main-thread, and populated
     * by [prewarm]; on a cold cache (first playback before the startup
     * pre-warm lands) it falls back to a disk read, byte-identical to the
     * previous behavior.
     */
    fun cachedFontBytes(): Map<String, ByteArray> = loadFontBytesInternal()

    /**
     * Returns the selected font with the requested synthetic weight and slant.
     *
     * Android's native subtitle renderer only accepts a single [Typeface].  In
     * particular, passing the regular bundled face alone silently drops the
     * bold/italic toggles, so apply the requested style after loading the font.
     *
     * Memoized per `(path, bold, italic)` — [Typeface] is immutable and this
     * is called from `applySubtitleStyle` on every style diff / track toggle,
     * which previously re-parsed the TTF from disk each time.
     */
    fun typefaceFor(style: SubtitleStyle): Typeface {
        val fontFile = style.fontFamilyPath
            ?.let(::File)
            ?.takeIf(File::isFile)
            ?: bundledFallback
        val typefaceStyle = when {
            style.bold && style.italic -> Typeface.BOLD_ITALIC
            style.bold -> Typeface.BOLD
            style.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val key = "${fontFile.absolutePath}|${style.bold}|${style.italic}|$typefaceStyle"
        // Access-order LinkedHashMap touched from Main here and from IO in
        // [installUserFont] eviction — every read/write holds the map lock.
        // The TTF parse inside getOrPut only runs on a miss, at most once per
        // key, so the lock is never held across repeated expensive work.
        synchronized(typefaceCache) {
            return typefaceCache.getOrPut(key) {
                val base = runCatching { Typeface.createFromFile(fontFile) }
                    .getOrDefault(Typeface.SANS_SERIF)
                Typeface.create(base, typefaceStyle)
            }
        }
    }

    private val typefaceCache = lruMapOf<String, Typeface>(MAX_TYPEFACES)

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
            // New content under a deterministic name: drop the stale byte-cache
            // entry and any memoized typeface for that path (stamps also guard
            // this, but evicting keeps the cache from serving a renamed file).
            fontBytesCache.remove(finalName)
            synchronized(typefaceCache) { typefaceCache.keys.removeIf { it.startsWith(finalFile.absolutePath + "|") } }
            InstalledFont(finalFile, family ?: finalFile.nameWithoutExtension)
        } catch (e: Exception) {
            null
        }
    }

    private fun sanitizeFileName(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "user" }

    companion object {
        /** Fonts installed are few (bundled + one per family); cap defensively. */
        private const val MAX_TYPEFACES = 16

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
