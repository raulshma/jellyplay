package com.raulshma.jellyplay.feature.details

import java.util.Locale

/**
 * Android/desktop actual of [languageDisplayName]: the HEAD body verbatim
 * (`Locale(language).displayLanguage` — default-locale display name; unknown
 * tags echo the tag back, so this never returns null).
 */
@Suppress("DEPRECATION") // Locale(tag) ctor is the verbatim HEAD body (deprecated on JDK 19+).
internal actual fun languageDisplayName(languageTag: String): String? =
    Locale(languageTag).displayLanguage
