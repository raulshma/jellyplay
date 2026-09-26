package com.raulshma.jellyplay.whatsnew

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.WhatsNewRelease

/**
 * The post-update What's New sheet's state. [Idle] keeps the sheet hidden;
 * [Show] presents [release] once — any close path (dismiss, deep link, TV
 * back) stamps the version seen, so the same release never re-presents.
 */
@Immutable
sealed interface WhatsNewState {
    data object Idle : WhatsNewState
    data class Show(val release: WhatsNewRelease) : WhatsNewState
}
