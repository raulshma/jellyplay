package com.raulshma.jellyplay.feature.player.live.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayMethod
import com.raulshma.jellyplay.feature.player.live.generated.resources.Res
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_badge
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_direct
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_transcode

/**
 * Red "LIVE" pill badge shown in the live player's top bar to indicate the
 * stream is a live broadcast.
 */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.live_badge),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Red)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/**
 * Delivery-method pill badge for the live player chrome. Shows "DIRECT" when
 * streaming the tuner verbatim and "TRANSCODE" when the server re-encodes, so
 * the user can tell at a glance which mode is actually in use (the "Direct
 * Stream" setting can be overridden by the server's playability verdict or the
 * on-error transcode fallback). Omitted when [method] is null (pre-resolve).
 */
@Composable
fun LivePlayMethodBadge(
    method: LivePlayMethod?,
    modifier: Modifier = Modifier,
) {
    if (method == null) return
    val (label, color) = when (method) {
        LivePlayMethod.DIRECT_PLAY, LivePlayMethod.DIRECT_STREAM ->
            stringResource(Res.string.live_direct) to Color(0xFF2E7D32)
        LivePlayMethod.TRANSCODE -> stringResource(Res.string.live_transcode) to Color(0xFFEF6C00)
    }
    Text(
        text = label,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
