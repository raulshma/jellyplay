package com.raulshma.jellyplay.core.ui.components

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins [LocaleApplier]'s API 33+ behaviour: per-app locales are owned by the
 * system [android.app.LocaleManager], so `apply` must be a complete no-op —
 * the process default locale stays untouched and no recreate signal is
 * emitted (the host must not recreate spuriously).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocaleApplierApi33Test {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `apply is a no-op on API 33 and emits nothing`() = runBlocking {
        val before = Locale.getDefault()

        var signalArrived = false
        val job = launch {
            LocaleApplier.recreateSignal.first()
            signalArrived = true
        }
        LocaleApplier._recreateSignal.subscriptionCount.first { it > 0 }

        LocaleApplier.apply(context, "fr-FR")

        Thread.sleep(150) // give a wrongly-emitted signal a chance to arrive
        assertEquals(before, Locale.getDefault())
        assertEquals(false, signalArrived)
        job.cancel()
    }
}
