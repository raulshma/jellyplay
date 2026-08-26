package com.raulshma.jellyplay.feature.player.video.subtitle

import java.io.File

/**
 * Subtitle font seam (wave 8C): the member set the commonMain
 * [VideoPlayerViewModel][com.raulshma.jellyplay.feature.player.video.VideoPlayerViewModel]
 * (and the app's startup prewarm) call. The androidMain class formerly named
 * `FontProvider` was renamed [AndroidFontProvider][com.raulshma.jellyplay.feature.player.video.subtitle.AndroidFontProvider]
 * and implements this interface; the Android-only surface (Typeface cache,
 * libass fonts dir, TTF parsing) stays on that class. [uri] is the string
 * form of the SAF-picked font — Uri is stringified at the API boundary. The
 * jvmMain actual is a no-op (no desktop subtitle renderer yet).
 */
interface FontProvider {

    /**
     * Copies a user-picked font into the fonts dir and parses its family
     * name; null on copy/parse failure (caller keeps the bundled fallback).
     */
    suspend fun installUserFont(uri: String): InstalledFont?

    /**
     * Ensures the fonts dir + bundled fallback exist and warms the byte
     * cache, off the caller's dispatcher. Idempotent.
     */
    suspend fun prewarm()
}

/** A successfully installed user font (deterministic file + parsed family). */
data class InstalledFont(val file: File, val familyName: String)
