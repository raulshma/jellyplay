package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.raulshma.jellyplay.core.model.OfflinePersonInfo
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

/**
 * Cast/crew row item for offline detail screens. Mirrors the online
 * [com.raulshma.jellyplay.feature.details.PersonItem] skeleton (circular image
 * + name + role) but consumes an [OfflinePersonInfo] and a pre-built image URL.
 *
 * The image URL is supplied by the caller because the offline store only keeps
 * a primary-image tag — the URL is reconstructed from the cached Coil entry at
 * download time. The [blurHash] provides a placeholder if the cache was evicted.
 */
@Composable
fun OfflinePersonItem(
    person: OfflinePersonInfo,
    imageUrl: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "offlinePersonScale",
    )
    val focusState = rememberTvFocusState(focusedScale = 1.08f)

    val base = modifier
        .width(80.dp)
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .then(focusState.focusModifier)
        .then(Modifier.tvFocusIndicator(focusState, CircleShape))

    val clickableModifier = if (onClick != null) {
        base.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
    } else {
        base
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = clickableModifier,
    ) {
        MediaImage(
            url = imageUrl,
            contentDescription = person.name,
            blurHash = person.blurHash,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = person.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        person.role?.takeIf { it.isNotBlank() }?.let { role ->
            Text(
                text = role,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
