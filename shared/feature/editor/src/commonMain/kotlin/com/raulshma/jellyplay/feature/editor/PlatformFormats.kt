package com.raulshma.jellyplay.feature.editor

import com.raulshma.jellyplay.core.ui.components.oneDecimal

/**
 * Platform-format seam for the editor:
 *  - [formatOneDecimal] is a thin façade over the core/ui date-label seam
 *    (the "%.1f"-contract one-decimal renderer lives there once for every
 *    family — the fixed-English/locale degrade is documented there);
 *  - the localized resource-pattern reads below remain module-internal
 *    expect/actual (genuinely editor-specific: they substitute the module's
 *    own translation patterns' single slot):
 *     - jvmShared actual: the verbatim `java.text.String.format` bodies
 *       (android + desktop output unchanged, locale included).
 *     - wasmJs actual: hand-rolled slot substitution — documented degrade.
 */
internal fun formatOneDecimal(value: Double): String = oneDecimal(value)

/** Renders a localized resource pattern with one integer slot (`%1$d`). */
internal expect fun formatIntPattern(pattern: String, value: Int): String

/** Renders a localized resource pattern with one string slot (`%1$s`). */
internal expect fun formatStringPattern(pattern: String, value: String): String
