package com.raulshma.jellyplay.feature.details

/**
 * Display name for an ISO-639 language tag ("en" → "English") — the Seerr
 * information section's Language row. expect/actual seam (wave 16C): the
 * original inline body was `java.util.Locale(language).displayLanguage`,
 * which is JVM-API and blocked the wasmJs target.
 *
 * - android/jvm (jvmShared actual): `Locale(languageTag).displayLanguage`
 *   verbatim — default-locale display name, unresolvable tags echo the tag
 *   itself (never null).
 * - wasmJs actual: `Intl.DisplayNames` (page-locale display name); ANY
 *   failure returns null and the call site falls back to the raw tag — the
 *   same display java produced where the name cannot be resolved.
 *
 * Same seam shape as core:player-contract's LanguageDisplayName (wave 12D);
 * that one is `internal` to its module, so this is the module-local replica
 * rather than a dependency.
 */
internal expect fun languageDisplayName(languageTag: String): String?
