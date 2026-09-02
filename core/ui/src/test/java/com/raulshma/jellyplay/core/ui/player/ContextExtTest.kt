package com.raulshma.jellyplay.core.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * Pins [Context.findActivity]: the ContextWrapper chain is walked until an
 * [Activity] is found (the player needs one for dialogs / key events), and a
 * context that never reaches an activity (the application context, whose base
 * is the bare ContextImpl) resolves to null instead of throwing.
 */
@RunWith(RobolectricTestRunner::class)
class ContextExtTest {

    private val activity: Activity = Robolectric.setupActivity(Activity::class.java)

    @Test
    fun `an activity context resolves to itself`() {
        assertEquals(activity, activity.findActivity())
    }

    @Test
    fun `a single wrapper over an activity is unwrapped`() {
        val wrapper = ContextWrapper(activity)

        assertEquals(activity, wrapper.findActivity())
    }

    @Test
    fun `nested wrappers walk down to the activity`() {
        val doubleWrapper = ContextWrapper(ContextWrapper(activity))

        assertEquals(activity, doubleWrapper.findActivity())
    }

    @Test
    fun `an activity-derived theme wrapper still finds the activity`() {
        val themeWrapper = android.view.ContextThemeWrapper(activity, android.R.style.Theme_Material)

        assertEquals(activity, themeWrapper.findActivity())
    }

    @Test
    fun `the application context resolves to null`() {
        val appContext: Context = ApplicationProvider.getApplicationContext()

        assertNull(appContext.findActivity())
    }
}
