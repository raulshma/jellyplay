package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.size.Size as CoilSize
import com.raulshma.jellyplay.core.model.OfflinePersonInfo
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

/**
 * Adapts a remote [PersonInfo] into the [OfflinePersonInfo] shape consumed by
 * the non-navigable cast row ([OfflinePersonItem]) for a LOCAL origin. The
 * local portrait path is NOT carried here — the call site resolves
 * `assets.castImages[id]` and passes it as the image URL (the offline item's
 * own `localImagePath` field stays null at this seam).
 */
internal fun PersonInfo.toOfflinePersonInfo(): OfflinePersonInfo = OfflinePersonInfo(
    id = id,
    name = name,
    role = role,
    type = type,
    imageTag = primaryImageTag,
    blurHash = primaryBlurHash,
)

@Composable
internal fun PersonItem(
    person: PersonInfo,
    imageUrl: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "personScale",
    )

    val personFocusState = rememberTvFocusState(focusedScale = 1.08f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(80.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(personFocusState.focusModifier)
            .then(Modifier.tvFocusIndicator(personFocusState, CircleShape))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        MediaImage(
            url = imageUrl,
            contentDescription = person.name,
            blurHash = person.primaryBlurHash,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            // 64.dp @ ~3× density renders at ~192 px; 128 px covers 2× density with margin.
            size = CoilSize(128, 128),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = person.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        person.role?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
