package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_hdr_label

import com.raulshma.jellyplay.core.designsystem.theme.HdrColors
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache

@Composable
fun HdrBadge(
    hdrType: String?,
    modifier: Modifier = Modifier,
) {
    if (hdrType == null) return

    val label = when (hdrType.lowercase()) {
        "hdr10" -> "HDR10"
        "hdr" -> "HDR"
        "hlg" -> "HLG"
        "dolbyvision", "dolby_vision", "dovi" -> "Dolby Vision"
        "hdr10plus", "hdr10_plus" -> "HDR10+"
        else -> hdrType.uppercase()
    }

    val isDolby = hdrType.lowercase() in listOf("dolbyvision", "dolby_vision", "dovi")

    val description = stringResource(Res.string.player_video_hdr_label, label)

    val bgColor = if (isDolby) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    }

    val borderColor = if (isDolby) {
        HdrColors.hdr10Gold.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    }

    Row(
        modifier = modifier
            .clip(ShapeCache.smoothPill)
            .background(bgColor)
            .border(1.dp, borderColor, ShapeCache.smoothPill)
            .semantics { contentDescription = description }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            color = if (isDolby) HdrColors.dolbyVisionGold else MaterialTheme.colorScheme.primary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}
