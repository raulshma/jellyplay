package com.raulshma.jellyplay.core.ui.components

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.filled.Heart
import com.composables.icons.tabler.outline.Bookmark
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.DownloadOff
import com.composables.icons.tabler.outline.Eye
import com.composables.icons.tabler.outline.EyeOff
import com.composables.icons.tabler.outline.Heart
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.PlayerPlay
import com.raulshma.jellyplay.core.model.QuickAction
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_action_add_to_playlist
import com.raulshma.jellyplay.core.ui.generated.resources.core_action_delete
import com.raulshma.jellyplay.core.ui.generated.resources.core_action_details
import com.raulshma.jellyplay.core.ui.generated.resources.core_action_download
import com.raulshma.jellyplay.core.ui.generated.resources.core_action_favorite
import com.raulshma.jellyplay.core.ui.generated.resources.core_action_mark_unwatched
import com.raulshma.jellyplay.core.ui.generated.resources.core_action_mark_watched
import com.raulshma.jellyplay.core.ui.generated.resources.core_action_play
import com.raulshma.jellyplay.core.ui.generated.resources.core_action_unfavorite
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * Pins the presentation mapping attached to the pure [QuickAction] model enum:
 * every action's [QuickAction.labelRes] and [QuickAction.icon] resolve to the
 * documented resource / icon pair. The sheet rows render exactly these two
 * getters, so the mapping IS the sheet's content contract. Notable pin: the
 * favorite/unfavorite pair deliberately differs — outline heart means "add to
 * favorites", filled heart means "currently favorited".
 */
class QuickActionPresentationTest {

    @Test
    fun everyAction_hasBothLabelAndIcon() {
        QuickAction.entries.forEach { action ->
            assertNotNull(action.labelRes, "$action must have a label resource")
            assertNotNull(action.icon, "$action must have an icon")
        }
    }

    @Test
    fun labelRes_mappingMatchesTheDocumentedResources() {
        val expected = mapOf(
            QuickAction.PLAY to Res.string.core_action_play,
            QuickAction.MARK_WATCHED to Res.string.core_action_mark_watched,
            QuickAction.MARK_UNWATCHED to Res.string.core_action_mark_unwatched,
            QuickAction.FAVORITE to Res.string.core_action_favorite,
            QuickAction.UNFAVORITE to Res.string.core_action_unfavorite,
            QuickAction.DOWNLOAD to Res.string.core_action_download,
            QuickAction.ADD_TO_PLAYLIST to Res.string.core_action_add_to_playlist,
            // REMOVE_DOWNLOAD is labeled "delete" (it deletes the local download).
            QuickAction.REMOVE_DOWNLOAD to Res.string.core_action_delete,
            QuickAction.DETAILS to Res.string.core_action_details,
        )
        expected.forEach { (action, res) ->
            assertEquals(res, action.labelRes, "wrong label resource for $action")
        }
    }

    @Test
    fun icon_mappingMatchesTheDocumentedIcons() {
        assertEquals(Tabler.Outline.PlayerPlay, QuickAction.PLAY.icon)
        assertEquals(Tabler.Outline.Eye, QuickAction.MARK_WATCHED.icon)
        assertEquals(Tabler.Outline.EyeOff, QuickAction.MARK_UNWATCHED.icon)
        // Outline vs filled heart is the favorited-state affordance.
        assertEquals(Tabler.Outline.Heart, QuickAction.FAVORITE.icon)
        assertEquals(Tabler.Filled.Heart, QuickAction.UNFAVORITE.icon)
        assertEquals(Tabler.Outline.Download, QuickAction.DOWNLOAD.icon)
        assertEquals(Tabler.Outline.Bookmark, QuickAction.ADD_TO_PLAYLIST.icon)
        assertEquals(Tabler.Outline.DownloadOff, QuickAction.REMOVE_DOWNLOAD.icon)
        assertEquals(Tabler.Outline.InfoCircle, QuickAction.DETAILS.icon)

        assertNotEquals(QuickAction.FAVORITE.icon, QuickAction.UNFAVORITE.icon)
    }
}
