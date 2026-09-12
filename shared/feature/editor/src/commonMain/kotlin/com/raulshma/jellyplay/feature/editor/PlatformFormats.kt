package com.raulshma.jellyplay.feature.editor

/**
 * Platform-format seam for the editor (core:ui's PlatformTime.kt template):
 * the JVM-only `String.format` / `"%.1f".format` reads in commonMain UI moved
 * behind module-internal expects so the wasmJs target compiles.
 *  - jvmShared actual: the verbatim `java.text.String.format` bodies
 *    (android + desktop output unchanged, locale included).
 *  - wasmJs actual: hand-rolled slot substitution over the module's own
 *    resource patterns + integer-math one-decimal rendering — documented
 *    locale degrade.
 */
internal expect fun formatOneDecimal(value: Double): String

/** Renders a localized resource pattern with one integer slot (`%1$d`). */
internal expect fun formatIntPattern(pattern: String, value: Int): String

/** Renders a localized resource pattern with one string slot (`%1$s`). */
internal expect fun formatStringPattern(pattern: String, value: String): String
