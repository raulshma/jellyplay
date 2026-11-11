package com.raulshma.jellyplay.feature.newsletter

import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * Localizable one-shot send-result emitted by [NewsletterViewModel] — the
 * commonMain VM has no Context, so the message stays unresolved until render
 * time. Each variant carries its 1:1 same-name string resource:
 * [SendSuccess] → newsletter_send_success, [TestSent] → newsletter_test_sent,
 * [SendFailed] → newsletter_send_failed. The screen collapses it with
 * [asText] inside its send-results collector, where it feeds the snackbar.
 *
 * It rides the VM's one-shot [NewsletterViewModel.sendResults] Channel (the
 * former `sendResult` parked uiState field replayed on state restoration —
 * the migration this type's transport records). The suspend [asText] is the
 * flow-escape pattern the detail screen uses: compose-resources' suspend
 * resolver runs inside collect, where no [androidx.compose.runtime.Composable]
 * snapshot context exists.
 */
@Immutable
sealed interface NewsletterMessage {
    data class SendSuccess(val res: StringResource) : NewsletterMessage
    data class TestSent(val res: StringResource) : NewsletterMessage
    data class SendFailed(val res: StringResource) : NewsletterMessage
}

suspend fun NewsletterMessage.asText(): String = when (this) {
    is NewsletterMessage.SendSuccess -> getString(res)
    is NewsletterMessage.TestSent -> getString(res)
    is NewsletterMessage.SendFailed -> getString(res)
}
