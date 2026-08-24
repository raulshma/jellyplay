package com.raulshma.jellyplay.feature.admin.users.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Localizable one-shot feedback emitted by [UserDetailViewModel] (music
 * conveyor's MixErrorMessage pattern — the commonMain VM has no Context, so
 * the message stays unresolved until render time). [Resource] carries the
 * localized [StringResource] and [Raw] an already-final string (exception
 * message / fixed wording). Screens collapse it with [asText] where it
 * renders.
 */
@Immutable
sealed interface AdminUserMessage {
    data class Resource(val res: StringResource) : AdminUserMessage
    data class Raw(val text: String) : AdminUserMessage
}

@Composable
fun AdminUserMessage.asText(): String = when (this) {
    is AdminUserMessage.Resource -> stringResource(res)
    is AdminUserMessage.Raw -> text
}
