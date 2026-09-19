package com.raulshma.jellyplay.feature.settings

/**
 * Platform-format seam for the settings screens (the editor's PlatformFormats
 * template): the JVM-only `String.format` / `"%1$d".format` reads in commonMain
 * UI moved behind module-internal expects so the wasmJs target compiles.
 *  - jvmShared actual: the verbatim `java.text.String.format` bodies
 *    (android + desktop output unchanged, locale included).
 *  - wasmJs actual: hand-rolled slot substitution over the module's own
 *    resource patterns + integer-math decimal rendering — documented locale
 *    degrade (fixed '.' decimal separator, fixed +/- sign position).
 */
internal expect fun formatOneDecimal(value: Double): String

/** Renders a one-to-two-decimal fixed notation ("%.2f" on the JVM). */
internal expect fun formatTwoDecimals(value: Double): String

/** Renders a localized resource pattern with one integer slot (`%1$d`). */
internal expect fun formatIntPattern(pattern: String, value: Int): String

/** Renders a signed integer (`%+d`: explicit '+' for non-negative values). */
internal expect fun formatSignedInt(value: Int): String
