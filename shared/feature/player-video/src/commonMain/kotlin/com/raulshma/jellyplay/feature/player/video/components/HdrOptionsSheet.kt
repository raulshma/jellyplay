package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertCircle
import com.composables.icons.tabler.outline.CircleCheck
import com.composables.icons.tabler.outline.Contrast2
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.SunHigh
import com.composables.icons.tabler.outline.Video
import com.raulshma.jellyplay.core.designsystem.theme.HdrColors
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.ExoHdrToneMappingMode
import com.raulshma.jellyplay.core.model.HdrFormatPreference
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_hdr_format_auto
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_hdr_format_dv_only
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_hdr_format_hdr10_only
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_hdr_format_prefer_dv
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_hdr_format_preference_caption
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_hdr_format_preference_section
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_hdr_format_sdr_only
import com.raulshma.jellyplay.feature.player.video.engine.HdrPlaybackMode
import com.raulshma.jellyplay.feature.player.video.engine.HdrPlaybackModeInfo
import com.raulshma.jellyplay.feature.player.video.engine.hdrLabelFor
import com.raulshma.jellyplay.feature.player.video.engine.resolveDetectedDisplayLabel
import com.raulshma.jellyplay.feature.player.video.engine.resolveHdrPlaybackMode
import org.jetbrains.compose.resources.stringResource

/**
 * The "HDR" sheet (opened from the HDR chip in the playback-metadata row),
 * in three glanceable blocks:
 *
 *  1. **Status card** — the current HDR decision at a glance: the ACTIVE
 *     playback mode (native HDR output / tone mapped to SDR / unhandled /
 *     SDR playback) with a one-line explanation, plus the two diagnosis
 *     facts the grade rests on — the item's content family and the DETECTED
 *     display type.
 *  2. **Engine config** — the HDR-specific settings that already exist in
 *     the settings system: mpv's global `hdrPassthrough` toggle (the write
 *     choreography lives in RenderControls — the session lens arms
 *     `target-colorspace-hint` on the live config rebuild, then persists the
 *     global slice) or ExoPlayer's global `hdrToneMapping` mode picker
 *     (AUTO / OFF — the GL tone-mapping path is a construction-time player
 *     input, so the pick applies on the next playback).
 *  3. **Routes** — mpv only: the tone-mapping PRESET row stays in the
 *     Rendering sheet; the affordance routes there instead of duplicating
 *     the picker.
 *
 * The status card's facts come from the engine's stats projection
 * ([hdrOutputActive] / [hdrOutputNotice] / [observedHdrType] /
 * [displayHdrTypes]) and the metadata stream's claim ([contentHdrType]);
 * which config block shows gates on the active player type, so an
 * unconfigured engine (VLC / external) shows the read-only status only.
 *
 * English literals match the sibling sheets (RenderSheet, QualityPickerSheet
 * et al.) — sheet bodies are the established hard-coded-string surface.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HdrOptionsSheet(
    /** The item's HDR family from the media streams (the metadata claim). */
    contentHdrType: String?,
    /** The engine's OBSERVED content family from the decoding stream (stats). */
    observedHdrType: String?,
    playerType: PlayerType,
    /** The engine's HDR output-policy verdict: the display switched to an HDR target. */
    hdrOutputActive: Boolean,
    /** The engine's one-line fallback diagnostic (null = nothing to report). */
    hdrOutputNotice: String?,
    /** The display probe's HDR family list ("HDR10, Dolby Vision"), or null when no probe answered. */
    displayHdrTypes: String?,
    /** mpv only: the current passthrough state; null (or no callback) hides the mpv section. */
    mpvHdrPassthrough: Boolean? = null,
    onMpvHdrPassthroughChange: ((Boolean) -> Unit)? = null,
    exoHdrToneMapping: ExoHdrToneMappingMode? = null,
    onExoHdrToneMappingSelect: ((ExoHdrToneMappingMode) -> Unit)? = null,
    /**
     * The HDR delivery posture (#5) — the persisted playback pref every
     * engine consumes (output posture via the HDR output policy; the Exo
     * decoder half via its engine config). `null` (or no callback) hides the
     * row — the unconfigured-engine posture (VLC / external, which cannot
     * honor it) matches the sheet's other config blocks.
     */
    hdrFormatPreference: HdrFormatPreference? = null,
    onHdrFormatPreferenceSelect: ((HdrFormatPreference) -> Unit)? = null,
    /** mpv only: routes to the Rendering sheet, which owns the tone-mapping preset row. */
    onOpenRender: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    // The Content row describes the ITEM — metadata-first, the same
    // precedence the playback-metadata row's HDR chip uses; the OBSERVED
    // family (the decode, Exo only) is the fallback when the claim is
    // silent (mpv hosts never publish it). The MODE card bases itself on
    // the engine's own verdict instead — see resolveHdrPlaybackMode.
    val contentLabel = hdrLabelFor(contentHdrType) ?: observedHdrType ?: "SDR"
    val modeInfo = resolveHdrPlaybackMode(
        playerType = playerType,
        contentHdrType = contentHdrType,
        observedHdrType = observedHdrType,
        hdrOutputActive = hdrOutputActive,
        hdrOutputNotice = hdrOutputNotice,
    )
    val displayLabel = resolveDetectedDisplayLabel(displayHdrTypes, modeInfo.mode)
    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The sheet keeps a 95% max height (PlayerModalBottomSheet);
                // small screens + the status card + the config block overflow
                // it — scroll like the sibling sheets (SubtitleHub, AVSync).
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(
                title = "HDR",
                icon = Tabler.Outline.SunHigh,
                subtitle = playerType.displayName,
            )
            Text(
                "How this item's HDR picture is rendered on your display.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(16.dp))

            HdrStatusCard(
                modeInfo = modeInfo,
                contentLabel = contentLabel,
                displayLabel = displayLabel,
            )

            // The HDR delivery posture (#5): every engine-consuming pick —
            // shown whenever the caller supplies the pref + write path (the
            // engines that cannot honor it pass null, like the other config
            // blocks).
            if (hdrFormatPreference != null && onHdrFormatPreferenceSelect != null) {
                Spacer(Modifier.height(16.dp))
                HdrSectionTitle(stringResource(Res.string.player_video_hdr_format_preference_section))
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HdrFormatPreference.entries.forEach { preference ->
                        HdrChip(
                            label = hdrFormatPreferenceLabel(preference),
                            selected = preference == hdrFormatPreference,
                            onClick = { onHdrFormatPreferenceSelect(preference) },
                        )
                    }
                }
                Text(
                    stringResource(Res.string.player_video_hdr_format_preference_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
            }

            if (playerType == PlayerType.MPV &&
                onMpvHdrPassthroughChange != null &&
                mpvHdrPassthrough != null
            ) {
                Spacer(Modifier.height(16.dp))
                HdrSectionTitle("Passthrough")
                HdrPassthroughToggleRow(
                    enabled = mpvHdrPassthrough,
                    onChange = onMpvHdrPassthroughChange,
                )
                Text(
                    "Send the HDR picture straight to an HDR-capable display (requires the " +
                        "gpu-next output); SDR displays tone map it instead. Applies on the " +
                        "next playback.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
                if (onOpenRender != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "The HDR → SDR tone-mapping presets (BT.2390, Hable, …) live in " +
                            "the Rendering sheet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    TextButton(onClick = onOpenRender) {
                        Text("Open Rendering…")
                    }
                }
            }

            if (playerType == PlayerType.EXO_PLAYER &&
                onExoHdrToneMappingSelect != null &&
                exoHdrToneMapping != null
            ) {
                Spacer(Modifier.height(16.dp))
                HdrSectionTitle("Tone Mapping (HDR → SDR)")
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExoHdrToneMappingMode.entries.forEach { mode ->
                        HdrChip(
                            label = mode.displayName,
                            selected = mode == exoHdrToneMapping,
                            onClick = { onExoHdrToneMappingSelect(mode) },
                        )
                    }
                }
                Text(
                    "Auto engages the tone-mapping path only when the display can't show this " +
                        "item's HDR family; Off keeps native output. Applies on the next playback.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
            }
        }
    }
}

// ── Status card (trimmed: one mode row, then the facts side by side) ─────

@Composable
private fun HdrStatusCard(
    modeInfo: HdrPlaybackModeInfo,
    contentLabel: String,
    displayLabel: String,
) {
    val accent = when (modeInfo.mode) {
        HdrPlaybackMode.HdrOutput -> HdrColors.hdr10Gold
        HdrPlaybackMode.ToneMapped -> MaterialTheme.colorScheme.primary
        // The codebase's warning color family (PlaybackMetadataRow's same pick).
        HdrPlaybackMode.NativeUnhandled -> MaterialTheme.colorScheme.tertiary
        HdrPlaybackMode.SdrPlayback -> MaterialTheme.colorScheme.onSurface
        HdrPlaybackMode.NotReported -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(ShapeCache.smooth12)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), ShapeCache.smooth12)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HdrModeIcon(accent = accent, icon = modeIcon(modeInfo.mode))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = modeInfo.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = accent,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = modeInfo.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            HdrFactColumn("Content", contentLabel, Modifier.weight(1f))
            HdrFactColumn("Display", displayLabel, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HdrModeIcon(accent: Color, icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp),
        )
    }
}

private fun modeIcon(mode: HdrPlaybackMode): ImageVector = when (mode) {
    HdrPlaybackMode.HdrOutput -> Tabler.Outline.CircleCheck
    HdrPlaybackMode.ToneMapped -> Tabler.Outline.Contrast2
    HdrPlaybackMode.NativeUnhandled -> Tabler.Outline.AlertCircle
    HdrPlaybackMode.SdrPlayback -> Tabler.Outline.Video
    HdrPlaybackMode.NotReported -> Tabler.Outline.InfoCircle
}

/**
 * The [HdrFormatPreference] label map — one `when` arm per enum entry
 * (compiler-exhaustive, so a new arm cannot silently render its raw name),
 * over the sheet's picker strings. Kept beside the sheet so the settings
 * screen's own label map and this one share the string keys' shape.
 */
@Composable
private fun hdrFormatPreferenceLabel(preference: HdrFormatPreference): String = stringResource(
    when (preference) {
        HdrFormatPreference.AUTO -> Res.string.player_video_hdr_format_auto
        HdrFormatPreference.PREFER_DV_OVER_HDR10 -> Res.string.player_video_hdr_format_prefer_dv
        HdrFormatPreference.SDR_ONLY -> Res.string.player_video_hdr_format_sdr_only
        HdrFormatPreference.HDR10_ONLY -> Res.string.player_video_hdr_format_hdr10_only
        HdrFormatPreference.DOLBY_VISION_ONLY -> Res.string.player_video_hdr_format_dv_only
    },
)

/** One status-card fact cell: the dim caption, then the value. */
@Composable
private fun HdrFactColumn(caption: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun HdrSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
    Spacer(Modifier.height(8.dp))
}

/** The mpv passthrough row (settings-style title/caption/toggle shape). */
@Composable
private fun HdrPassthroughToggleRow(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(ShapeCache.smooth8)
            .clickable { onChange(!enabled) }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.padding(end = 16.dp)) {
            Text(
                text = "HDR passthrough",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (enabled) {
                    "On — HDR-capable displays switch to HDR output"
                } else {
                    "Off — HDR content is tone mapped to SDR"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onChange,
        )
    }
}

/** Same FilterChip styling as RenderSheet's picker chips (the file-private twin). */
@Composable
private fun HdrChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        },
        shape = ShapeCache.smoothPill,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            selectedLabelColor = MaterialTheme.colorScheme.primary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = Color.Transparent,
            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            enabled = true,
            selected = selected,
        ),
    )
}
