package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A bare colored dot beside a label — the status affordance for rows that
 * carry NO tinted surface (unlike [StatusPill], whose 15%-tinted background is
 * the point there). This is the deliberate third shape in the pill family,
 * not a drifted copy: the requests list row's status badge and auth's server
 * health badge both draw dot + text, and they previously hand-copied the same
 * 8.dp dot / 6.dp gap / labelSmall pair.
 *
 * The default label color is neutral (`onSurfaceVariant`, the health badge's
 * choice); the requests row passes the status color through [labelColor] +
 * [fontWeight] instead of forking the shell. Only draws — the
 * color-per-status decision tables stay feature-local.
 */
@Composable
fun DotLabel(
    text: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
    dotSize: Dp = 8.dp,
    spacing: Dp = 6.dp,
    style: TextStyle = MaterialTheme.typography.labelSmall,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(spacing))
        Text(
            text = text,
            style = if (fontWeight != null) style.copy(fontWeight = fontWeight) else style,
            color = labelColor,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
