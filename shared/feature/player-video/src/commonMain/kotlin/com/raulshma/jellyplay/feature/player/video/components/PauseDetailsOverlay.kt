package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.pause_details_starring
import org.jetbrains.compose.resources.stringResource

/** How many top-billed cast names the ribbon carries (only data already in state). */
private const val PAUSE_DETAILS_CAST_LIMIT = 5

/**
 * The pause-details overlay (#13): a quiet bottom-start panel over the paused
 * frame — the item's title, its metadata line (the same derivation the
 * control chrome renders: "Series · S1E5" for episodes, a trimmed synopsis
 * for movies), the synopsis, and a top-billed cast ribbon. All data rides
 * the uiState projection the player already holds — nothing is fetched.
 *
 * DECLARED data divergence: the player's uiState carries no production year
 * (the item model is not projected into `MediaContentState`), so the panel
 * renders the metadata line in the year's place; the policy signature keeps
 * the details gate independent of that.
 *
 * Visibility rides the call site's AnimatedVisibility (the
 * [com.raulshma.jellyplay.feature.player.video.shouldShowPauseDetails] fold);
 * this composable is the render half only. Over the video the panel uses the
 * player scrim tokens (always-dark chrome over arbitrary content, like the
 * trickplay bubble), with M3 typography — visually quiet by design.
 */
@Composable
internal fun PauseDetailsOverlay(
    title: String,
    metadataLine: String,
    synopsis: String,
    cast: List<PersonInfo>,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Surface scrim: bottom-heavy, soft — the paused frame stays visible.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.62f),
                    ),
                ),
        )
        val castNames = cast.take(PAUSE_DETAILS_CAST_LIMIT).map { it.name }.filter { it.isNotBlank() }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.72f)
                .padding(start = 48.dp, bottom = 148.dp, end = 24.dp),
        ) {
            if (metadataLine.isNotBlank()) {
                Text(
                    text = metadataLine,
                    style = MaterialTheme.typography.labelLarge,
                    color = playerOnScrim().copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = playerOnScrim(),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (synopsis.isNotBlank()) {
                Text(
                    text = synopsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = playerOnScrim().copy(alpha = 0.8f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (castNames.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.pause_details_starring) + " " +
                        castNames.joinToString(separator = " · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = playerOnScrim().copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}
