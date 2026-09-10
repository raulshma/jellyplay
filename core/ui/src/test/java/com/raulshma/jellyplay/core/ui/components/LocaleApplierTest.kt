package com.raulshma.jellyplay.core.ui.components

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins [LocaleApplier]'s legacy per-app language path (API < 33, where
 * [android.app.LocaleManager] does not exist):
 *
 * - `apply(tag)` switches the process default locale (BCP-47 parsed), updates
 *   the context configuration, and emits exactly one [LocaleApplier.recreateSignal]
 *   so the host activity can recreate.
 * - `apply(null)` reverts to the system default locale and still emits.
 * - [LocaleApplier.currentLanguageTag] reports the BCP-47 tag for a context
 *   whose configuration locale differs from the process default, and null when
 *   they match.
 *
 * (On API 33+ apply is a system no-op — pinned by LocaleApplierApi33Test.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class LocaleApplierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun applyAndAwait(language: String?) {
        runBlocking {
            var signal = false
            val job = launch { LocaleApplier.recreateSignal.first(); signal = true }
            LocaleApplier._recreateSignal.subscriptionCount.first { it > 0 }
            LocaleApplier.apply(context, language)
            withTimeout(2_000) { job.join() }
            assertEquals(true, signal)
        }
    }

    @Test
    fun `apply switches the process locale and emits a recreate signal`() {
        LocaleListReset.toSystemDefault()

        applyAndAwait("fr-FR")

        assertEquals("fr", Locale.getDefault().language)
        assertEquals("FR", Locale.getDefault().country)
    }

    @Test
    fun `apply updates the context configuration locale`() {
        LocaleListReset.toSystemDefault()

        LocaleApplier.apply(context, "de-DE")

        val configLocale = context.resources.configuration.locales[0]
        assertEquals("de", configLocale.language)
    }

    @Test
    fun `apply with null reverts to the system default locale`() {
        LocaleListReset.toSystemDefault()
        LocaleApplier.apply(context, "fr-FR")
        assertEquals("fr", Locale.getDefault().language)

        applyAndAwait(null)

        assertEquals(LocaleListReset.systemDefault().language, Locale.getDefault().language)
    }

    @Test
    fun `currentLanguageTag reports a context locale differing from the process default`() {
        LocaleListReset.toSystemDefault()
        val localized = context.createConfigurationContext(
            Configuration().apply { setLocale(java.util.Locale.FRANCE) },
        )

        assertEquals("fr-FR", LocaleApplier.currentLanguageTag(localized))
    }

    @Test
    fun `currentLanguageTag is null when the context locale matches the process default`() {
        LocaleListReset.toSystemDefault()

        assertNull(LocaleApplier.currentLanguageTag(context))
    }
}

/** Small helper pinning a known system default before each assertion. */
private object LocaleListReset {
    private val original: Locale = Locale.getDefault()

    fun systemDefault(): Locale = original

    fun toSystemDefault() {
        Locale.setDefault(original)
        android.os.LocaleList.setDefault(android.os.LocaleList(original))
    }
}
