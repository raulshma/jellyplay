package com.raulshma.jellyplay.floating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowSettings
import org.robolectric.annotation.Config

/**
 * Pins the static launcher contract of [FloatingPlayerService]: the
 * permission gate runs BEFORE the service start (no overlay grant → no
 * `startForegroundService`, no leaked window), and both the start and stop
 * paths address the service by explicit component carrying the
 * ACTION_START / ACTION_STOP action so the service's onStartCommand can
 * tell them apart. The service itself stays a pure started service
 * ([FloatingPlayerService.onBind] returns null).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class FloatingPlayerServiceTest {

    private val application get() = RuntimeEnvironment.getApplication()

    @Test
    fun `start without the overlay permission is refused and starts nothing`() {
        ShadowSettings.setCanDrawOverlays(false)

        val started = FloatingPlayerService.start(application)

        assertFalse(started)
        assertNull(Shadows.shadowOf(application).nextStartedService)
    }

    @Test
    fun `start with the overlay permission launches the service with ACTION_START`() {
        ShadowSettings.setCanDrawOverlays(true)

        val started = FloatingPlayerService.start(application)

        assertTrue(started)
        val intent = Shadows.shadowOf(application).nextStartedService
        assertNotNull(intent)
        assertEquals(FloatingPlayerService::class.java.name, intent?.component?.className)
        assertEquals(FloatingPlayerService.ACTION_START, intent?.action)
    }

    @Test
    fun `stop sends the service an ACTION_STOP intent`() {
        FloatingPlayerService.stop(application)

        val intent = Shadows.shadowOf(application).nextStartedService
        assertNotNull(intent)
        assertEquals(FloatingPlayerService::class.java.name, intent?.component?.className)
        assertEquals(FloatingPlayerService.ACTION_STOP, intent?.action)
    }

    @Test
    fun `onBind returns null - the service is started, not bound`() {
        // buildService (no .create()) skips onCreate, whose notification
        // channel setup needs the full app resource table.
        val service = Robolectric.buildService(FloatingPlayerService::class.java).get()

        assertNull(service.onBind(null))
    }
}
