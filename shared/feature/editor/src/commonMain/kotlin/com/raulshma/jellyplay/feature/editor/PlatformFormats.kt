package com.raulshma.jellyplay.feature.editor

/**
 * Platform-format seam for the editor:
 *  - the one-decimal renderer is gone: this module's former
 *    `formatOneDecimal` façade folded onto core/ui's public
 *    [com.raulshma.jellyplay.core.ui.components.formatOneDecimal] (the
 *    "%.1f"-contract one-decimal renderer lives there once for every
 *    family — call sites import it directly);
 *  - the one-integer-slot resource-pattern renderer is likewise shared once
 *    by core/ui ([com.raulshma.jellyplay.core.ui.components.formatIntPattern],
 *    replacing this module's former expect/actual twin of settings' copy);
 *  - the localized string-slot read below remains module-internal
 *    expect/actual (genuinely editor-specific: it substitutes only this
 *    module's own translation patterns' single slot):
 *     - jvmShared actual: the verbatim `java.text.String.format` body
 *       (android + desktop output unchanged, locale included).
 */

/** Renders a localized resource pattern with one string slot (`%1$s`). */
internal expect fun formatStringPattern(pattern: String, value: String): String
