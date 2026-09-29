package com.raulshma.jellyplay.feature.newsletter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.image.MediaImage

/**
 * The shared newsletter media-card stack — the Column → Box(clip +
 * focusIndicator + clickable) → MediaImage → caption scaffold every
 * newsletter card had hand-copied. Each card keeps ONLY its distinctive
 * [overlay] (scrims, badges, in-box captions) and [caption] (the metadata
 * block under the poster).
 *
 * Values that DRIFTED between the copied cards are parameters, not
 * unifications — every card keeps its CURRENT rendering exactly:
 *  - [shape]: the poster cards clip at smooth12; CuratedFeaturedCard clips
 *    its full-bleed backdrop at smooth20.
 *  - [focusShape]: ContinueWatching/NextUp/CuratedFeatured draw the focus
 *    indicator at smooth16; CuratedPickCard at smooth12 (kept, not unified).
 *  - [aspectRatio]: 16:9 (ContinueWatching + CuratedFeatured's backdrop) vs
 *    2:3 posters (NextUp, CuratedPickCard).
 *
 * [caption] is nullable: the featured card renders no under-poster block, so
 * no (zero-height, padded) caption Column is composed for it at all.
 */
@Composable
internal fun NewsletterCardScaffold(
    aspectRatio: Float,
    shape: Shape,
    focusShape: Shape,
    onClick: () -> Unit,
    imageUrl: String,
    contentDescription: String?,
    blurHash: String?,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
    caption: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(shape)
                .focusIndicator(focusShape)
                .clickable(onClick = onClick),
        ) {
            MediaImage(
                url = imageUrl,
                contentDescription = contentDescription,
                blurHash = blurHash,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            overlay()
        }
        caption?.let { caption ->
            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                caption()
            }
        }
    }
}
