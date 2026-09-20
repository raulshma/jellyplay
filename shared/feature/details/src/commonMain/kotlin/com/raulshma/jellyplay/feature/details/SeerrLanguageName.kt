package com.raulshma.jellyplay.feature.details

/**
 * Display name for an ISO-639 language tag ("en" → "English") — the Seerr
 * information section's Language row. expect/actual seam: the
 * original inline body was `java.util.Locale(language).displayLanguage`,
 * which is JVM-API kept behind a platform seam.
 *
 * - android/jvm (jvmShared actual): `Locale(languageTag).displayLanguage`
 *   verbatim — default-locale display name, unresolvable tags echo the tag
 *   itself (never null).
 *
 * Same seam shape as core:player-contract's LanguageDisplayName;
 * that one is `internal` to its module, so this is the module-local replica
 * rather than a dependency.
 */
internal expect fun languageDisplayName(languageTag: String): String?
