package com.raulshma.jellyplay.feature.settings

/**
 * Desktop actual of the [AppLocaleSetter] seam: the desktop locale is
 * OS-level, so applying the in-app choice is a no-op — only the preference
 * store write persists it.
 */
internal class DesktopAppLocaleSetter : AppLocaleSetter {
    override fun setAppLocale(language: String?) {
        // No per-app locale override surface on desktop.
    }
}
