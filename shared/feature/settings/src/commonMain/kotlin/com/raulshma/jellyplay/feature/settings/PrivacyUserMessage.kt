package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Immutable

/**
 * One-shot destructive-action confirmation emitted by
 * [PrivacyDataViewModel] and rendered by [PrivacyDataScreen] through the
 * SettingsMessenger seam — the commonMain-safe replacement for the legacy
 * `uiTextOf(R.string.…)` values the ViewModel used to post through the
 * Android-only UserMessageBus (LiveTvUserMessage screen-forward pattern). The
 * screen resolves the resource text (compose-resources), so no R class or
 * UiText machinery leaks into shared code.
 */
@Immutable
sealed interface PrivacyUserMessage {
    /** The cache (minus the image cache) was wiped — `settings_cache_cleared`. */
    data object CacheCleared : PrivacyUserMessage
    /** The Coil image cache was wiped — `settings_image_cache_cleared`. */
    data object ImageCacheCleared : PrivacyUserMessage
    /** The current user's search history was cleared — `settings_search_history_cleared`. */
    data object SearchHistoryCleared : PrivacyUserMessage
    /** All preferences were reset to defaults — `settings_factory_reset_all_done`. */
    data object FactoryResetDone : PrivacyUserMessage
    /** Raw fallback text (exception message). */
    data class Raw(val text: String) : PrivacyUserMessage
}
