package com.raulshma.jellyplay.feature.player.audio

/**
 * Platform-format seam (the editor's PlatformFormats template): the JVM-only
 * `String.format` read in commonMain UI moved behind a module-internal expect.
 *  - jvmShared actual: the verbatim `"%.1f".format` body (android + desktop
 *    output unchanged, locale included).
 */
internal expect fun formatOneDecimal(value: Double): String
