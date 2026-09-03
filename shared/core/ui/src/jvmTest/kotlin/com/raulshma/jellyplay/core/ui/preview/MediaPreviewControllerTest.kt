package com.raulshma.jellyplay.core.ui.preview

import androidx.compose.ui.geometry.Rect
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.cancelAndJoin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins [MediaPreviewController]'s transient peek-state machine: a fresh
 * controller holds `null`; [MediaPreviewController.show] publishes the preview
 * verbatim (replacing any open one); [MediaPreviewController.hide] returns to
 * `null`; and the exposed [MediaPreviewController.state] is a live StateFlow —
 * a collector sees the full null → preview → … → null sequence in order.
 *
 * The controller is a plain class (MutableStateFlow-backed), so these run
 * headlessly on the JVM; the `remember*` composables in the same file stay
 * composition-bound and untested here.
 */
class MediaPreviewControllerTest {

    private val movie = MediaItem(id = "m-1", name = "Movie", mediaType = MediaType.MOVIE)

    private fun preview(id: String, sourceBounds: Rect? = null) = MediaPreview(
        item = movie,
        posterUrl = "$id-poster",
        backdropUrl = "$id-backdrop",
        blurHash = null,
        sourceBounds = sourceBounds,
    )

    @Test
    fun freshController_holdsNull() {
        assertNull(MediaPreviewController().state.value)
    }

    @Test
    fun show_publishesThePreviewVerbatim() {
        val controller = MediaPreviewController()
        val peek = preview("p1", sourceBounds = Rect(10f, 20f, 110f, 220f))

        controller.show(peek)

        val shown = controller.state.value
        assertTrue(shown != null && shown === peek, "the exact instance shown must be the state value")
        assertEquals(Rect(10f, 20f, 110f, 220f), shown.sourceBounds)
        assertEquals("p1-poster", shown.posterUrl)
    }

    @Test
    fun show_replacesAnyCurrentlyOpenPreview() {
        val controller = MediaPreviewController()
        val first = preview("p1")
        val second = preview("p2")

        controller.show(first)
        controller.show(second)

        assertSame(second, controller.state.value)
    }

    @Test
    fun hide_returnsToNull() {
        val controller = MediaPreviewController()
        controller.show(preview("p1"))

        controller.hide()

        assertNull(controller.state.value)
    }

    @Test
    fun hide_onAlreadyHiddenController_staysNull() {
        val controller = MediaPreviewController()

        controller.hide()

        assertNull(controller.state.value)
    }

    @Test
    fun collector_seesTheFullShowHideSequenceInOrder() = runTest {
        val controller = MediaPreviewController()
        val p1 = preview("p1")
        val p2 = preview("p2")
        val seen = mutableListOf<MediaPreview?>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.state.collect { seen += it }
        }

        controller.show(p1)
        controller.show(p2)
        controller.hide()

        collector.cancelAndJoin()

        assertEquals(listOf<MediaPreview?>(null, p1, p2, null), seen)
    }

    @Test
    fun mediaPreview_nullSourceBounds_isCarriedThrough() {
        // null bounds = documented fallback to the simple centered fade/scale.
        val peek = preview("p1", sourceBounds = null)
        val controller = MediaPreviewController()

        controller.show(peek)

        assertNull(controller.state.value?.sourceBounds)
    }
}
