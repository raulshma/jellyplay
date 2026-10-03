package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

/**
 * Shared chassis for the player's picker sheets — everything those sheets used
 * to repeat byte-for-byte, and nothing else:
 *
 *  - the TV focus prologue: a [FocusRequester] plus a [LaunchedEffect] that
 *    grabs D-pad focus onto the sheet's primary row (tag `"sheet"`) while
 *    [LocalTvMode] is active;
 *  - the [PlayerModalBottomSheet] + [SheetHeader] envelope (title, optional
 *    [subtitle], optional [headerTrailing] slot) with the standard
 *    `bottom = 32.dp` body padding;
 *  - the header-to-content gap ([contentTopGap] — 20.dp for the chip-pickers,
 *    12.dp for the dense chapter/track-style lists, 8.dp for the filters
 *    sheet).
 *
 * Stays per-sheet, passed through [content]:
 *  - the TV/touch branch itself — the TV option list (built from
 *    [TvFocusableOptionRow]) and the touch chip row differ per picker;
 *  - where the initial focus lands: the caller attaches the handed-out
 *    [FocusRequester] to its first-or-selected row via
 *    `Modifier.ifElse(…, Modifier.focusRequester(…))`;
 *  - one-off header extras via [preContent] (the aspect-ratio "Detected: …"
 *    line, the decoder / playback-mode note) that sit between the header and
 *    the content gap.
 *
 * @param focusEffectKeys extra [LaunchedEffect] keys appended after `isTv`.
 *   Lists that load asynchronously (chapters, tracks) pass their list here so
 *   the focus grab re-arms once data lands.
 * @param canGrabFocus guard evaluated inside the effect — an asynchronously
 *   loaded, still-empty list must not steal focus.
 * @param scrollable when true the body column scrolls (the video-filters sheet
 *   packs a dozen sliders); picker lists leave it off and scroll in their own
 *   [androidx.compose.foundation.lazy.LazyColumn].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickerSheetScaffold(
    title: String,
    icon: ImageVector,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    headerTrailing: (@Composable RowScope.() -> Unit)? = null,
    preContent: (@Composable ColumnScope.() -> Unit)? = null,
    focusEffectKeys: List<Any?> = emptyList(),
    canGrabFocus: () -> Boolean = { true },
    contentTopGap: Dp = 20.dp,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.(isTv: Boolean, focusRequester: FocusRequester) -> Unit,
) {
    val isTv = LocalTvMode.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isTv, *focusEffectKeys.toTypedArray()) {
        if (isTv && canGrabFocus()) {
            focusRequester.tryRequestFocus("sheet")
        }
    }

    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .ifElse(scrollable, Modifier.verticalScroll(rememberScrollState()))
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(
                title = title,
                icon = icon,
                subtitle = subtitle,
                trailing = headerTrailing,
            )
            preContent?.invoke(this)
            Spacer(Modifier.height(contentTopGap))
            content(isTv, focusRequester)
        }
    }
}

/**
 * One TV-focusable option row — the selected/focused chrome the player picker
 * sheets used to carry as a dozen near-identical copies:
 * 16/2 outer padding, `smooth8` clip ([shape] overrides for grouped lists),
 * `primary@0.1` background when [selected] else `onSurface@0.04`, the TV
 * focus indicator/glow, a 20/14 content padding, and — when [trailingCheck] —
 * the trailing 18dp check mark that marks the selected row.
 *
 * Owns only the chrome. Leading content (label, badges, description line) is
 * [content]; bespoke trailing affordances (the chapter row's play badge, the
 * track row's circled check, the subtitle status slot) render inside
 * [content] with [trailingCheck] left off.
 *
 * @param focusRequesterModifier attach the sheet's focus-grab target here —
 *   `Modifier.ifElse(isFirstOrSelected, Modifier.focusRequester(…))` — so
 *   exactly one row of the recycled list carries the requester.
 * @param enabled false disables the click (subtitle rows gate re-download);
 *   the focus chrome is unaffected, matching the previous
 *   `clickable(enabled = …)` per-row behavior.
 */
@Composable
internal fun TvFocusableOptionRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = ShapeCache.smooth8,
    focusRequesterModifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingCheck: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    // focusedScale is pinned at the modal 1.02f every sheet copy already used.
    // The value is ceremonial — core/ui's TvFocusState never scales (scale is
    // fixed at 1f) — which is why the 1.04/1.05 stragglers in some sheets were
    // silently identical; the chassis carries the one documented value.
    val focusState = rememberTvFocusState(focusedScale = 1.02f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
            )
            .then(focusState.focusModifier)
            .then(focusRequesterModifier)
            .tvFocusIndicator(focusState, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
        if (trailingCheck && selected) {
            Icon(
                Tabler.Outline.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * The standard option-row label: `bodyLarge`, `primary` +
 * [FontWeight.SemiBold] when [selected], `onSurface`/Normal otherwise.
 * [selectedFontWeight] exists for the sleep-timer rows, which emphasize with
 * Bold; everything else keeps the SemiBold default.
 */
@Composable
internal fun TvOptionRowLabel(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    selectedFontWeight: FontWeight = FontWeight.SemiBold,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontWeight = if (selected) selectedFontWeight else FontWeight.Normal,
        ),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/**
 * Grouped-list row shape shared by the chapter/track/subtitle lists: a lone
 * row is fully rounded `smooth16`, the last row of [itemCount] gets the
 * expressive outer-corner treatment, rows in between stay `smooth8`.
 */
internal fun pickerRowShape(itemCount: Int, isLast: Boolean): Shape = when {
    itemCount == 1 -> ShapeCache.smooth16
    isLast -> expressiveListShape(itemCount - 1, itemCount)
    else -> ShapeCache.smooth8
}
