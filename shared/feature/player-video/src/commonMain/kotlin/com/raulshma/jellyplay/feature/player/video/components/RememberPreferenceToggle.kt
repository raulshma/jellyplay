package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

/**
 * A focusable "remember this for the series" toggle rendered as the footer of
 * a [TrackPickerSheet]. Mirrors the styling of the existing "Reset to Auto"
 * affordance so it reads consistently on phone and TV (D-pad focusable, whole
 * row tappable).
 *
 * @param label the action description, e.g. "Remember audio language for this series".
 * @param checked whether the preference is currently saved.
 * @param onToggle invoked with the new desired state; the caller maps this to a
 *  save/delete of the relevant language.
 */
@Composable
internal fun RememberPreferenceToggle(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTv = LocalTvMode.current
    val focusState = rememberTvFocusState(focusedScale = 1.02f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (checked) 0.10f else 0.04f))
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, CircleShape)
            .clickable { onToggle(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (checked) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            // On TV, the whole row is the click target; the Switch is display-only
            // (its own touch handler is disabled) so D-pad activation comes via the row.
            onCheckedChange = if (isTv) null else onToggle,
        )
    }
}
