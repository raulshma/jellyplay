package com.raulshma.jellyplay.feature.downloads

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.PlayerPause
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.PlayerStop
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.feature.downloads.generated.resources.Res
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_cancel
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_delete
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_pause
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_play
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_resume
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_retry
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_cancel_all
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_pause_all
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_resume_all
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_retry_failed

/**
 * One download action's FACE — what a control looks like (icon + label
 * resource) — as distinct from its PLACEMENT: which surface offers it, in
 * which slot, gated by which admission verdict. The face table is the
 * module's "request action table" idiom (one home for label/icon faces,
 * placement/styling stays per-surface); placement keyed on the admission
 * set stays per-surface code — this table never decides when a button
 * exists, only what a button says.
 */
data class DownloadActionFace(
    val icon: ImageVector,
    val labelRes: StringResource,
)

/**
 * The download actions' face table — ONE home for every (icon, label) pair
 * the downloads surfaces render, so an icon/label re-dress is a one-row
 * change instead of a seven-block sweep. The rows:
 *
 *  - [bulk] — the scoped voice ("Pause" / "Cancel"), shared by the selection
 *    bar, the group headers' transfer controls and the item rows: these
 *    surfaces speak per-target, so they share one face row per action. The
 *    key set is pinned to the whole [DownloadBulkAction] vocabulary by
 *    DownloadActionFacesTest (totality: an action without a face row is a
 *    compile-away runtime crash at the lookup, not a silent gap).
 *  - [bulkAll] — the app bar's whole-list voice ("Pause all" / "Cancel
 *    all"): same icons, deliberately different label resources (every
 *    locale carries both voices). DELETE is absent — no all-scope delete
 *    verb exists (the destructive bulk delete is a selection-bar text
 *    button, and the swipe/confirm path is its own surface).
 *  - [rowCancel] — the item row's cancel variant: Trash ("remove this
 *    download") against the PlayerStop ("halt this transfer") everywhere
 *    else. A genuine face divergence in today's rows, kept as a named row
 *    rather than flattened onto [bulk]'s CANCEL — merging the icons would
 *    change what the rows render.
 *  - [groupPlay] — the group header's Play target (next unwatched episode):
 *    a row-only action outside the bulk vocabulary, tabled here so the
 *    header renders no hand-spelled face.
 *
 * The group header's PAUSE reads [bulkAll] (it kept the app bar's "pause
 * all" label while CANCEL took the scoped one) — a preserved quirk, not a
 * rule; re-voicing either label is a copy change, and this refactor is
 * output-preserving by contract.
 */
object DownloadActionFaces {
    internal val bulk: Map<DownloadBulkAction, DownloadActionFace> = mapOf(
        DownloadBulkAction.PAUSE to DownloadActionFace(Tabler.Outline.PlayerPause, Res.string.downloads_action_pause),
        DownloadBulkAction.RESUME to DownloadActionFace(Tabler.Outline.PlayerPlay, Res.string.downloads_action_resume),
        DownloadBulkAction.CANCEL to DownloadActionFace(Tabler.Outline.PlayerStop, Res.string.downloads_action_cancel),
        DownloadBulkAction.RETRY_FAILED to DownloadActionFace(Tabler.Outline.Refresh, Res.string.downloads_action_retry),
        DownloadBulkAction.DELETE to DownloadActionFace(Tabler.Outline.Trash, Res.string.downloads_action_delete),
    )

    internal val bulkAll: Map<DownloadBulkAction, DownloadActionFace> = mapOf(
        DownloadBulkAction.PAUSE to DownloadActionFace(Tabler.Outline.PlayerPause, Res.string.downloads_pause_all),
        DownloadBulkAction.RESUME to DownloadActionFace(Tabler.Outline.PlayerPlay, Res.string.downloads_resume_all),
        DownloadBulkAction.CANCEL to DownloadActionFace(Tabler.Outline.PlayerStop, Res.string.downloads_cancel_all),
        DownloadBulkAction.RETRY_FAILED to DownloadActionFace(Tabler.Outline.Refresh, Res.string.downloads_retry_failed),
    )

    internal val rowCancel: DownloadActionFace =
        DownloadActionFace(Tabler.Outline.Trash, Res.string.downloads_action_cancel)

    internal val groupPlay: DownloadActionFace =
        DownloadActionFace(Tabler.Outline.PlayerPlay, Res.string.downloads_action_play)
}

/** The scoped-voice face ([DownloadActionFaces.bulk]) for [action]. */
internal val DownloadBulkAction.face: DownloadActionFace
    get() = DownloadActionFaces.bulk.getValue(this)

/** The app bar's whole-list face ([DownloadActionFaces.bulkAll]) for [action]. */
internal val DownloadBulkAction.allFace: DownloadActionFace
    get() = DownloadActionFaces.bulkAll.getValue(this)

/**
 * The TV focus chrome + icon-button shell every downloads action control
 * shares, spelled ONCE here: focus state, the `then(focusModifier) →
 * tvFocusIndicator` chain (order matters — the indicator wraps the focus
 * observer), the IconButton, and the face's icon + localized content
 * description. Hosts pass the face and their per-surface placement/styling —
 * shape (app bar rings circles, in-card controls ring the row's smooth
 * corners) and tint stay at the call site. The tint default rides
 * [LocalContentColor] — the app bar passes none and inherits the bar's
 * content color; surfaces with a divergent tint (the in-card controls dim to
 * onSurfaceVariant / accent to primary) pass it explicitly. `Color.Unspecified`
 * would NOT inherit — for material3's [Icon] it means "no colorFilter at
 * all", rendering the Tabler vectors' intrinsic black — so it is never a
 * valid default here.
 *
 * `rememberTvFocusState()` is spelled without arguments on purpose: its
 * focusedScale parameter is inert (the focus impl never scales; it draws),
 * so the app-bar and in-card spellings were behaviorally identical before
 * this composable existed.
 */
@Composable
internal fun TvActionIconButton(
    face: DownloadActionFace,
    shape: Shape = CircleShape,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
    onClick: () -> Unit,
) {
    val focus = rememberTvFocusState()
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .then(focus.focusModifier)
            .tvFocusIndicator(focus, shape),
    ) {
        Icon(
            face.icon,
            contentDescription = stringResource(face.labelRes),
            tint = tint,
        )
    }
}
