package com.raulshma.jellyplay.feature.settings

import android.content.Context

/**
 * Android actual of the [AppLocaleSetter] seam — the pre-migration
 * LanguageSettingsViewModel body, verbatim: LocaleManager on TIRAMISU+, the
 * legacy core:ui LocaleApplier fallback below that.
 */
internal class AndroidAppLocaleSetter(
    private val context: Context,
) : AppLocaleSetter {
    override fun setAppLocale(language: String?) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
            localeManager?.applicationLocales = if (language != null) {
                android.os.LocaleList.forLanguageTags(language)
            } else {
                android.os.LocaleList.getEmptyLocaleList()
            }
        } else {
            com.raulshma.jellyplay.core.ui.components.LocaleApplier.apply(context, language)
        }
    }
}
