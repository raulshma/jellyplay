package com.raulshma.jellyplay.feature.editor

import com.raulshma.jellyplay.core.ui.components.oneDecimal

/**
 * Platform-format seam for the editor:
 *  - [formatOneDecimal] is a thin façade over the core/ui date-label seam
 *    (the "%.1f"-contract one-decimal renderer lives there once for every
 *    family);
 *  - the one-integer-slot resource-pattern renderer is likewise shared once
 *    by core/ui ([com.raulshma.jellyplay.core.ui.components.formatIntPattern],
 *    replacing this module's former expect/actual twin of settings' copy);
 *  - the localized string-slot read below remains module-internal
 *    expect/actual (genuinely editor-specific: it substitutes only this
 *    module's own translation patterns' single slot):
 *     - jvmShared actual: the verbatim `java.text.String.format` body
 *       (android + desktop output unchanged, locale included).
 */
internal fun formatOneDecimal(value: Double): String = oneDecimal(value)

/** Renders a localized resource pattern with one string slot (`%1$s`). */
internal expect fun formatStringPattern(pattern: String, value: String): String
