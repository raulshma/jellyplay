package com.raulshma.jellyplay.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.adaptive.rowCardWidth
import com.raulshma.jellyplay.core.ui.components.OfflineMediaCard
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode

/**
 * Inline "Downloaded" row shown on the online home. Upgraded from the old
 * bespoke poster to the shared [OfflineMediaCard] so it matches the rest of
 * the offline surfaces; title and TV/touch scroller ride the same shared
 * modules as every other home row ([HomeRowTitle] / [HomeItemRow]) instead of
 * a hand-rolled Text + LazyRow.
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

    Column(modifier = modifier.fillMaxWidth().background(backgroundColor)) {
        HomeRowTitle(
            title = stringResource(R.string.home_downloaded),
            contentPad = contentPad,
            topPadding = 24.dp,
        )
        HomeItemRow(
            items = offlineLibrary,
            key = { "offline_${it.id}" },
            cardWidth = cardWidth,
            spacing = spacing,
            contentPad = contentPad,
            clippingEnabled = false,
            modifier = Modifier.padding(vertical = 4.dp),
        ) { offlineItem, mod ->
            OfflineMediaCard(
                item = offlineItem,
                onClick = onOfflineLibraryClick,
                modifier = mod.width(cardWidth),
            )
        }
    }
}
