package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

/**
 * The shared visual kit every sheet in the app builds on, so a `ModalBottomSheet`
 * body looks like the Library / MediaDetail / More-menu surfaces regardless of
 * which feature module owns it. Everything here consumes only
 * [MaterialTheme] color/typography/motion + [ShapeCache] — no new design tokens.
 *
 *  - [SheetHeader]      — title row with optional tinted icon, subtitle, close.
 *  - [SheetTabRow]      — a [PrimaryTabRow] tuned to sit flush on a sheet body.
 *  - [SheetSection]     — the non-collapsible sibling of `SettingsGroup`: a
 *                         `smooth24` container on `surfaceContainerLow`.
 *  - [SheetDragHandle]  — the single drag handle both sheet wrappers use.
 */

/** 40×4dp pill drag handle, identical on player and standard sheets. */
@Composable
fun SheetDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 40.dp, height = 4.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
    }
}

/**
 * Canonical sheet title row.
 *
 * Layout: optional 36dp tinted icon container → title `titleLarge` SemiBold
 * (+ heading semantics) with optional `bodySmall` subtitle → weight(1f) →
 * optional trailing slot → optional close button. Matches the MediaDetail /
 * Library header weight and the SettingsGroup tinted-icon treatment.
 *
 * @param icon optional leading icon; rendered in a `primary@0.15f` `smooth12`
 *   container so the header reads like a [com.raulshma.jellyplay.feature.settings.SettingsGroup].
 * @param onClose optional close affordance; when non-null renders a 40dp
 *   `IconToggleButton` (Tabler `X`) on a `surfaceVariant@0.3f` background.
 */
@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    onClose: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(ShapeCache.smooth12)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            trailing()
            Spacer(Modifier.width(8.dp))
        }
        if (onClose != null) {
            IconToggleButton(
                checked = false,
                onCheckedChange = { onClose() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(ShapeCache.smooth12)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .tvFocusIndicator(rememberTvFocusState()),
            ) {
                Icon(
                    imageVector = Tabler.Outline.X,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * A [PrimaryTabRow] tuned to sit flush on a sheet body.
 *
 * The default [PrimaryTabRow] container is [TabRowDefaults.primaryContainerColor],
 * which resolves to `colorScheme.surface` — so on a sheet painted at
 * `surfaceContainer` the tab strip renders as a disjoint band (in OLED the tabs
 * are pure #000 over the sheet's #111). Since sheets now paint at `surface` too,
 * passing [Color.Transparent] keeps the tab strip perfectly flush with the body
 * and lets the [MaterialTheme.colorScheme.primary] indicator + selected text
 * carry the active state — the same active/inactive idiom the expressive nav bar
 * uses (`primary`/`onPrimaryContainer` active, `onSurfaceVariant` inactive).
 *
 * The caller builds the `Tab`s in [tabs] exactly as it would for [PrimaryTabRow].
 *
 * @param selectedTabIndex the current selected tab index.
 * @param tabs the row of [androidx.compose.material3.Tab]s, as for [PrimaryTabRow].
 */
@Composable
fun SheetTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    tabs: @Composable () -> Unit,
) {
    PrimaryTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier,
        containerColor = Color.Transparent,
        // The default indicator is already a primary 3dp pill — keep it; only the
        // divider is dropped so the flush tab strip doesn't draw a separator line
        // against the sheet body.
        divider = {},
        tabs = tabs,
    )
}

/**
 * A non-collapsible grouped-content container — the `SettingsGroup` look without
 * the expand affordance. Use inside a sheet body to cluster related controls
 * (e.g. an AV-sync sheet's delay rows, a filter sheet's chip groups) so the
 * sheet reads like the settings/library grouped surfaces instead of a flat list.
 *
 * Container: `clip(smooth24).background(surfaceContainerLow)` with
 * `horizontal=16 / vertical=14` padding, content spaced `4.dp` apart — the same
 * recipe [com.raulshma.jellyplay.feature.settings.SettingsGroup] uses.
 *
 * On a sheet painted at `colorScheme.surface` this `surfaceContainerLow` tier
 * reads as a correctly-elevated grouped container (e.g. #0A0A0A over the OLED
 * #000 body) — the inverse of the old `surfaceContainer` sheet where sections
 * sank below the body.
 *
 * @param title optional section header; when present renders a compact row with
 *   the optional [icon] in a tinted `smooth12` container and a `titleMedium`
 *   SemiBold label, mirroring the [SheetHeader] treatment at smaller scale.
 */
@Composable
fun SheetSection(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth24)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(ShapeCache.smooth12)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(Modifier.size(8.dp))
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}
