package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass

@Composable
fun rememberDiscoverRows(
    allDiscoverItems: List<SeerrSearchItem>,
): List<List<SeerrSearchItem>> {
    val adaptiveInfo = LocalAdaptiveInfo.current
    return remember(allDiscoverItems, adaptiveInfo.windowSizeClass) {
        val result = mutableListOf<List<SeerrSearchItem>>()
        var i = 0
        val pattern = if (adaptiveInfo.windowSizeClass == WindowSizeClass.Compact) {
            listOf(3, 2, 3)
        } else {
            listOf(5, 4, 6, 5)
        }
        var patternIdx = 0
        while (i < allDiscoverItems.size) {
            val targetSize = pattern[patternIdx % pattern.size]
            val rowSize = targetSize.coerceAtMost(allDiscoverItems.size - i)
            result.add(allDiscoverItems.subList(i, i + rowSize))
            i += rowSize
            patternIdx++
        }
        result
    }
}
