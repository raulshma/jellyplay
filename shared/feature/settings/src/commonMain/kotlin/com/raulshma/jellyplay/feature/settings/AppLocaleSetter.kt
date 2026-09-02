package com.raulshma.jellyplay.feature.settings

/**
 * Platform seam that applies a newly chosen app language (V3 settings
 * conveyor). Android routes through the system LocaleManager on TIRAMISU+ and
 * the legacy core:ui LocaleApplier below that (reached through the
 * androidMain→:core:ui edge); desktop locale is OS-level, so the actual is a
 * no-op and only the preference store write persists the choice.
 */
fun interface AppLocaleSetter {
    fun setAppLocale(language: String?)
}
