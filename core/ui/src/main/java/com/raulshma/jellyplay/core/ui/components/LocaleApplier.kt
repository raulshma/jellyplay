package com.raulshma.jellyplay.core.ui.components

import android.content.Context
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Applies a per-app language on Android < 13 (API < 33) where the system
 * [android.app.LocaleManager] API is unavailable. On API 33+ the system
 * handles locale application automatically via [LocaleManager], so this
 * helper is a no-op.
 *
 * After updating the configuration it emits a [recreateSignal] so the host
 * activity can call `recreate()` to pick up the new string resources.
 */
object LocaleApplier {

    private val _recreateSignal = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val recreateSignal: kotlinx.coroutines.flow.SharedFlow<Unit> = _recreateSignal

    /**
     * Returns the BCP-47 tag of the locale currently applied to [context],
     * or `null` when the system default is in effect.
     */
    fun currentLanguageTag(context: Context): String? {
        val locale = context.resources.configuration.locales[0]
        val default = LocaleList.getDefault()[0]
        return if (locale == default) null else locale.toLanguageTag()
    }

    /**
     * Applies [language] (a BCP-47 tag, or `null` for system default) to the
     * app's resources configuration on API < 33. No-op on API 33+. After
     * applying, emits [recreateSignal] so observers can recreate their
     * activity.
     */
    fun apply(context: Context, language: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return

        val locale = language?.let { Locale.forLanguageTag(it) }
            ?: LocaleList.getDefault()[0]
        Locale.setDefault(locale)
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        _recreateSignal.tryEmit(Unit)
    }
}
