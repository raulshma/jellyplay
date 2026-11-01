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
import com.raulshma.jellyplay.core.model.DetailDensity
import com.raulshma.jellyplay.core.model.OfflinePersonInfo
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import androidx.compose.ui.unit.Dp

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

/**
 * The cast rail's item geometry per [DetailDensity] (the
 * `detail_cast_item_size` pref): the item column's width and the circular
 * portrait's diameter. The shipped DEFAULT row is the historical fixed
 * 80dp / 64dp pair; the pure mapping lives here so the rail (media-detail
 * header) and any other PersonItem consumer agree on one source of truth.
 */
internal data class CastRailMetrics(
    val itemWidth: Dp,
    val imageSize: Dp,
)

/** The [DetailDensity] → cast-rail geometry fold (see [CastRailMetrics]). */
internal fun DetailDensity.castRailMetrics(): CastRailMetrics = when (this) {
    DetailDensity.COMPACT -> CastRailMetrics(itemWidth = 64.dp, imageSize = 52.dp)
    DetailDensity.DEFAULT -> CastRailMetrics(itemWidth = 80.dp, imageSize = 64.dp)
    DetailDensity.LARGE -> CastRailMetrics(itemWidth = 96.dp, imageSize = 80.dp)
}

/**
 * The Cast & Crew grid's column-minimum per [DetailDensity] (the
 * `detail_people_grid_density` pref): a ±40dp delta applied to the adaptive
 * environment's base minimum (140–200dp by window class / TV — see
 * `AdaptiveGridMinSize`), clamped so a cell always fits the 80dp person card.
 * DEFAULT is the untouched environment value.
 */
internal fun DetailDensity.peopleGridMinSize(base: Dp): Dp = when (this) {
    DetailDensity.COMPACT -> (base - 40.dp).coerceAtLeast(84.dp)
    DetailDensity.DEFAULT -> base
    DetailDensity.LARGE -> base + 40.dp
}

@Composable
internal fun PersonItem(
    person: PersonInfo,
    imageUrl: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** The cast-rail density knob; DEFAULT is the historical fixed geometry. */
    density: DetailDensity = DetailDensity.DEFAULT,
) {
    val metrics = density.castRailMetrics()
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
            .width(metrics.itemWidth)
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
                .size(metrics.imageSize)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            // The decode target tracks the rendered circle (~2× the dp at the
            // historical default: 64.dp → 128 px, the shipped size).
            size = CoilSize((metrics.imageSize.value * 2).toInt(), (metrics.imageSize.value * 2).toInt()),
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
