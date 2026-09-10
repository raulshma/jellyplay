package com.raulshma.jellyplay.widget

import android.content.Context
import android.content.Intent
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the continue-watching widget refresh broadcast: an EXPLICIT-component
 * broadcast (implicit broadcasts to manifest receivers are blocked on O+)
 * carrying [ContinueWatchingWidget.ACTION_REFRESH], addressed to the widget
 * provider class in this package — the action string and receiver class are
 * owned by the broadcaster, not the Home ViewModel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ContinueWatchingBroadcasterImplTest {

    private val context: Context = mockk(relaxed = true)

    @Test
    fun `refresh sends an explicit-component broadcast to the widget provider`() {
        every { context.packageName } returns "com.raulshma.jellyplay"
        // Relaxed fakes: an empty continue-watching snapshot keeps the poster
        // prewarm a no-op, so the test pins only the broadcast contract.
        val widgetDataStore = mockk<WidgetDataStore>(relaxed = true)
        val playbackRepository = mockk<PlaybackRepository>(relaxed = true)

        ContinueWatchingBroadcasterImpl(context, widgetDataStore, playbackRepository)
            .refreshContinueWatching()

        val intent = slot<Intent>()
        verify(exactly = 1) { context.sendBroadcast(capture(intent)) }
        assertEquals(ContinueWatchingWidget.ACTION_REFRESH, intent.captured.action)
        assertEquals(
            ContinueWatchingWidget::class.java.name,
            intent.captured.component?.className,
        )
        assertEquals("com.raulshma.jellyplay", intent.captured.component?.packageName)
    }
}
