package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.QuickAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the screen-scoped quick-action state machine behind the
 * [MediaQuickActionSheet] (the `remember*` factory and the host composable are
 * composition-bound and untested):
 *
 *  - a fresh controller has a null current item (sheet closed);
 *  - `show` publishes the item on [MediaQuickActionController.currentItem] and
 *    `hide` returns to null; showing again REPLACES the open item;
 *  - the published flow is a StateFlow: late collectors replay the current
 *    item without re-invoking `show`;
 *  - `actionsFor` delegates resolution to the host-supplied lambda verbatim;
 *  - `execute` hands the (item, action) pair to the host lambda — the
 *    controller itself never interprets the action.
 */
class MediaQuickActionControllerTest {

    private fun item(id: String, name: String = "Item $id") =
        MediaItem(id = id, name = name, mediaType = MediaType.MOVIE)

    @Test
    fun freshController_currentItemIsNull() = runTest {
        val controller = MediaQuickActionController({ emptyList() }, { _, _ -> })

        assertNull(controller.currentItem.first())
    }

    @Test
    fun show_publishesItem_hideClearsIt() {
        val controller = MediaQuickActionController({ emptyList() }, { _, _ -> })
        val target = item("m1")

        controller.show(target)
        assertEquals(target, controller.currentItem.value)

        controller.hide()
        assertNull(controller.currentItem.value)
    }

    @Test
    fun show_againReplacesTheOpenItem() {
        val controller = MediaQuickActionController({ emptyList() }, { _, _ -> })

        controller.show(item("m1"))
        val second = item("m2")
        controller.show(second)

        assertEquals(second, controller.currentItem.value)
    }

    @Test
    fun currentItem_replaysToLateCollector() = runTest {
        val controller = MediaQuickActionController({ emptyList() }, { _, _ -> })
        val target = item("m1")
        controller.show(target)

        assertEquals(target, controller.currentItem.first())
    }

    @Test
    fun actionsFor_delegatesToTheHostResolver() {
        val requested = mutableListOf<MediaItem>()
        val actions = listOf(QuickAction.PLAY, QuickAction.MARK_WATCHED)
        val controller = MediaQuickActionController(
            resolveActions = { item ->
                requested += item
                if (item.id == "playable") actions else emptyList()
            },
            executeAction = { _, _ -> },
        )

        assertEquals(actions, controller.actionsFor(item("playable")))
        assertTrue(controller.actionsFor(item("empty")).isEmpty())
        assertEquals(listOf("playable", "empty"), requested.map { it.id })
    }

    @Test
    fun execute_handsItemAndActionToTheHost() {
        val executed = mutableListOf<Pair<String, QuickAction>>()
        val controller = MediaQuickActionController(
            resolveActions = { emptyList() },
            executeAction = { item, action -> executed += item.id to action },
        )

        controller.execute(item("m1"), QuickAction.DOWNLOAD)
        controller.execute(item("m2"), QuickAction.FAVORITE)

        assertEquals(listOf("m1" to QuickAction.DOWNLOAD, "m2" to QuickAction.FAVORITE), executed)
    }

    @Test
    fun execute_doesNotTouchThePublishedItem() {
        // The host composable hides the sheet BEFORE executing; the controller
        // itself must keep the two concerns decoupled.
        val controller = MediaQuickActionController({ emptyList() }, { _, _ -> })
        val target = item("m1")
        controller.show(target)

        controller.execute(target, QuickAction.PLAY)

        assertEquals(target, controller.currentItem.value)
    }
}
