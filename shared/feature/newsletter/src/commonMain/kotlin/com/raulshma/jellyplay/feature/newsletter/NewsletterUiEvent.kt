package com.raulshma.jellyplay.feature.newsletter

sealed interface NewsletterUiEvent {
    data object Refresh : NewsletterUiEvent
    data object PullToRefresh : NewsletterUiEvent
    data object Dismiss : NewsletterUiEvent
    data object SendNow : NewsletterUiEvent
    data object SendTest : NewsletterUiEvent
    data object ConfirmSend : NewsletterUiEvent
    data object DismissSendDialog : NewsletterUiEvent

    // The former DismissSendResult consume event died with the sendResult
    // field it cleared: the send result now rides the VM's one-shot
    // [NewsletterViewModel.sendResults] Channel (StateManagementConventions
    // §2), which needs no consume dance — a channel never replays.
}
