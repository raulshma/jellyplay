package com.raulshma.jellyplay.feature.newsletter

sealed interface NewsletterUiEvent {
    data object Refresh : NewsletterUiEvent
    data object PullToRefresh : NewsletterUiEvent
    data object Dismiss : NewsletterUiEvent
}
