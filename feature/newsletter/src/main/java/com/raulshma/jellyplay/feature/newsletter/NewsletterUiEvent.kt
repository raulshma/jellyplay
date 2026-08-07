package com.raulshma.jellyplay.feature.newsletter

sealed interface NewsletterUiEvent {
    data object Refresh : NewsletterUiEvent
    data object PullToRefresh : NewsletterUiEvent
    data object Dismiss : NewsletterUiEvent
    data object SendNow : NewsletterUiEvent
    data object SendTest : NewsletterUiEvent
    data object ConfirmSend : NewsletterUiEvent
    data object DismissSendDialog : NewsletterUiEvent
    data object DismissSendResult : NewsletterUiEvent
}
