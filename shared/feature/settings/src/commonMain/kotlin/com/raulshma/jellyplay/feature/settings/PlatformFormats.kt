package com.raulshma.jellyplay.feature.settings

/**
 * Platform-format seam for the settings screens (the editor's PlatformFormats
 * template): the JVM-only `String.format` / `"%1$d".format` reads in commonMain
 * UI moved behind module-internal expects. Two members were since folded onto
 * shared core/ui seams and deleted from here: the one-integer-slot
 * resource-pattern renderer (core/ui's
 * [com.raulshma.jellyplay.core.ui.components.formatIntPattern]) and
 * `formatOneDecimal` (core/ui's public
 * [com.raulshma.jellyplay.core.ui.components.formatOneDecimal] — the
 * byte-identical `"%.1f".format` actual this file used to carry).
 *  - jvmShared actual: the verbatim `java.text.String.format` bodies
 *    (android + desktop output unchanged, locale included).
 */

/** Renders a one-to-two-decimal fixed notation ("%.2f" on the JVM). */
internal expect fun formatTwoDecimals(value: Double): String

/** Renders a signed integer (`%+d`: explicit '+' for non-negative values). */
internal expect fun formatSignedInt(value: Int): String
