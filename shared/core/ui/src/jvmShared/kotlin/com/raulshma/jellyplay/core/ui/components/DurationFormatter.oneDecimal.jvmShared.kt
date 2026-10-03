package com.raulshma.jellyplay.core.ui.components

/**
 * The JVM (android/desktop) actual of DurationFormatter.kt's
 * [formatOneDecimal] expect — the verbatim `"%.1f".format` body every former
 * call site shipped, host locale included. Moved here from
 * PlatformTime.jvmShared.kt together with its expect (the formatting home);
 * the `%.1f` HALF_UP contract is pinned by DurationFormatterTest and
 * PlatformTimeJvmTest's rounding tests.
 */
actual fun formatOneDecimal(value: Double): String = "%.1f".format(value)
