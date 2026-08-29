package com.raulshma.jellyplay.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.adaptive.rowCardWidth
import com.raulshma.jellyplay.core.ui.animation.lazyItemPlacementSpec
import com.raulshma.jellyplay.core.ui.components.OfflineMediaCard
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer

/**
 * Inline "Downloaded" row shown on the online home. Upgraded from the old
 * bespoke poster to the shared [OfflineMediaCard] so it matches the rest of
 * the offline surfaces.
 *
 * (The former dedicated offline home that lived in this file was removed for
 * issue #147: while offline the home now renders the normal
 * [HomeContentList] with sections derived from the offline library — see
 * [buildOfflineHomeSections] and [OfflineHomeMediaRow].)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadedSection(
    offlineLibrary: List<OfflineMediaItem>,
    onOfflineLibraryClick: () -> Unit,
    contentPad: Dp,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val cardWidth = adaptiveInfo.rowCardWidth(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)

    Text(
        text = stringResource(R.string.home_downloaded),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(start = contentPad, top = 24.dp, bottom = 8.dp),
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup()
            .tvFocusRestorer()
            .background(backgroundColor)
            .padding(horizontal = contentPad, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        items(
            count = offlineLibrary.size,
            key = { index -> "offline_${offlineLibrary[index].id}" },
            contentType = { "offlineItem" },
        ) { index ->
            val offlineItem = offlineLibrary[index]
            val placementSpec = lazyItemPlacementSpec()
            OfflineMediaCard(
                item = offlineItem,
                onClick = onOfflineLibraryClick,
                modifier = Modifier.animateItem(placementSpec = placementSpec).width(cardWidth),
            )
        }
    }
}
