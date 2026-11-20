package com.raulshma.jellyplay.feature.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Radar
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.AutoDownloadMode
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.download_sheet_policy_ask
import com.raulshma.jellyplay.core.ui.generated.resources.download_sheet_policy_instant
import com.raulshma.jellyplay.core.ui.generated.resources.download_sheet_policy_never
import com.raulshma.jellyplay.feature.downloads.generated.resources.Res
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_armed_series_mode_follow_global
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_armed_series_rearm
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_armed_series_subtitle
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_armed_series_title
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_armed_series_unarm
import org.jetbrains.compose.resources.stringResource

/**
 * The Downloads screen's armed-series card (ADR-0015): one row per
 * `series_download_policies` arm — including series with zero downloaded
 * episodes, which is why the list exists (the normal downloads list cannot
 * show them). Each row shows the series name and its EFFECTIVE mode (the
 * follows-global arm reads the global preference's label; explicit overrides
 * — NEVER included, the check visits-and-skips those — read their own), plus
 * the row action: an armed row shows the un-arm verb (the disarm verb's only
 * UI home — it writes the NEVER tombstone), a tombstoned row
 * ([ArmedSeriesRow.reArmable]) shows the re-arm verb instead — which is why
 * the "stopped" series stay listed at all. The host gates the card on the
 * rows being non-empty, so it never renders hollow.
 */
@Composable
internal fun ArmedSeriesCard(
    rows: List<ArmedSeriesRow>,
    onUnArm: (seriesId: String) -> Unit,
    onReArm: (seriesId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // The CARD is the bounded surface (the picker sheet's 320 dp cap,
            // header included) and the rows claim the card's leftover space
            // (weight, fill = false) — together they make the card fit ANY
            // finite budget, so a 240 dp-high desktop window shrinks the
            // scroll region instead of the card pushing everything below it
            // off-screen. The section rides OUTSIDE the download list (pinned
            // above it, both placements) in a non-scrolling column, where an
            // unbounded card would overflow the viewport entirely.
            .heightIn(max = 320.dp)
            .clip(ShapeCache.smooth4)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Tabler.Outline.Radar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.downloads_armed_series_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(Res.string.downloads_armed_series_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // The rows region is the card's scroll owner: it takes what the
        // header leaves of the card's bounded height and never more (weight,
        // fill = false — a short arm list keeps the card compact), so only
        // the rows scroll and the header stays put.
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        ) {
            rows.forEach { row ->
                ArmedSeriesRowView(
                    row = row,
                    onUnArm = { onUnArm(row.seriesId) },
                    onReArm = { onReArm(row.seriesId) },
                )
            }
        }
    }
}

@Composable
private fun ArmedSeriesRowView(
    row: ArmedSeriesRow,
    onUnArm: () -> Unit,
    onReArm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = modeLabel(row),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(
            // The row action flips on the RAW tombstone flag, never the
            // resolved mode: a NEVER row is the disarm tombstone (re-arm
            // back to FOLLOW_GLOBAL), everything else — a FOLLOW_GLOBAL row
            // under a global NEVER included — is armed and offers the stop.
            onClick = if (row.reArmable) onReArm else onUnArm,
            shape = ShapeCache.smoothPill,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                stringResource(
                    if (row.reArmable) Res.string.downloads_armed_series_rearm
                    else Res.string.downloads_armed_series_unarm,
                ),
            )
        }
    }
}

/**
 * The row's mode label: a follows-global arm names the preference it rides
 * AND the value it resolves to ("Follows global · Instant" — what the user
 * will actually get); an explicit override names itself. The resolved enum →
 * label mapping reuses the policy sheet's labels (core:ui's
 * `download_sheet_policy_*` — the same modes, the same copy, one home), not
 * the projection fold — labels are presentation; the fold only resolves
 * modes. The "·" join is code, not a template (the suggestion card's
 * label-join idiom).
 */
@Composable
private fun modeLabel(row: ArmedSeriesRow): String {
    val resolved = when (row.resolvedMode) {
        AutoDownloadMode.INSTANT -> stringResource(CoreUiRes.string.download_sheet_policy_instant)
        AutoDownloadMode.ASK -> stringResource(CoreUiRes.string.download_sheet_policy_ask)
        AutoDownloadMode.NEVER -> stringResource(CoreUiRes.string.download_sheet_policy_never)
        // Defensive only: armedSeriesRows resolves the sentinel away and the
        // global mode is clamped against it store-side, so this arm is
        // unreachable through the fold — show the follows-global copy rather
        // than the raw enum name.
        AutoDownloadMode.FOLLOW_GLOBAL -> stringResource(Res.string.downloads_armed_series_mode_follow_global)
    }
    return if (row.followsGlobal) {
        stringResource(Res.string.downloads_armed_series_mode_follow_global) + " · " + resolved
    } else {
        resolved
    }
}
