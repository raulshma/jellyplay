package com.raulshma.jellyplay.feature.newsletter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Localizable one-shot send-result emitted by [NewsletterViewModel] (music
 * conveyor's MixErrorMessage pattern — the commonMain VM has no Context, so
 * the message stays unresolved until render time). Exactly the three
 * outcomes the legacy code built [com.raulshma.jellyplay.core.ui.feedback
 * .UiText.Resource] values for, each carrying its 1:1 same-name string
 * resource: [SendSuccess] → newsletter_send_success, [TestSent] →
 * newsletter_test_sent, [SendFailed] → newsletter_send_failed. The screen
 * collapses it with [asText] where it feeds the snackbar.
 */
@Immutable
sealed interface NewsletterMessage {
    data class SendSuccess(val res: StringResource) : NewsletterMessage
    data class TestSent(val res: StringResource) : NewsletterMessage
    data class SendFailed(val res: StringResource) : NewsletterMessage
}

@Composable
fun NewsletterMessage.asText(): String = when (this) {
    is NewsletterMessage.SendSuccess -> stringResource(res)
    is NewsletterMessage.TestSent -> stringResource(res)
    is NewsletterMessage.SendFailed -> stringResource(res)
}
