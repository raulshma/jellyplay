package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The dumb status-pill shell shared by every status / service / badge chip:
 * a 15 %-tinted [color] surface with the label set in the same [color]. This
 * shell only draws — the label+color DECISION tables (which color means what)
 * stay feature-local (ArrQueuePresentation, the admin plugin badgeTint).
 *
 * Defaults are the arr-queue chip pair (`RoundedCornerShape(6.dp)`,
 * `labelSmall`, h6-v2 padding); admin's plugin badge passes its own knobs
 * (smooth8, `labelMedium`, h8-v3) through the parameters instead of forking
 * the shell. The label is single-line and ellipsized — admin's long-label
 * guard, a visual no-op for the short queue/release labels.
 *
 * Not adopted by the dot+label badges (the requests list row, auth's server
 * health): those draw no tinted surface at all — a bare colored dot beside a
 * label — and the health badge's label is neutral (`onSurfaceVariant`), not
 * tinted. They are a different shape, not a drifted copy of this one.
 */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
    style: TextStyle = MaterialTheme.typography.labelSmall,
    fontWeight: FontWeight? = null,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = text,
            style = if (fontWeight != null) style.copy(fontWeight = fontWeight) else style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(contentPadding),
        )
    }
}
