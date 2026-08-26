package com.raulshma.jellyplay.feature.player.audio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

@Composable
internal fun TrackInfoSection(
    title: String,
    artist: String,
    artistId: String? = null,
    onArtistClick: (String) -> Unit = {},
) {
    val artistFocusState = rememberTvFocusState()
    Text(
        title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(4.dp))
    val artistClickable = !artistId.isNullOrBlank() && artist.isNotBlank()
    Text(
        artist,
        style = MaterialTheme.typography.bodyLarge,
        color = if (artistClickable) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (artistClickable) {
                    Modifier
                        .then(artistFocusState.focusModifier)
                        .tvFocusIndicator(artistFocusState, ShapeCache.smooth8)
                        .clip(ShapeCache.smooth8)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { onArtistClick(artistId!!) }
                        .padding(vertical = 4.dp)
                } else Modifier
            ),
        textAlign = TextAlign.Center,
    )
}
