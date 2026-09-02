package com.raulshma.jellyplay.feature.player.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Localizable player message (admin conveyor's AdminUserMessage / music
 * conveyor's MixErrorMessage pattern — the commonMain ViewModel seam has no
 * Context, so the message stays unresolved until render time).
 * [Resource] carries the localized [StringResource] plus optional format
 * args, and [Raw] an already-final string (engine error text / exception
 * message / fixed fallback wording).
 *
 * Serves two flows:
 *  - the persistent [LiveTvPlayerUiState.errorMessage] (resolved by the
 *    screen with [asText] before [components.LiveErrorBanner] renders), and
 *  - the one-shot record/cancel feedback the ViewModel emits on
 *    [LiveTvPlayerViewModel.messages] (the screen-forward replacement for the
 *    legacy Android-only `UserMessageBus` + `UiText.Resource` posts; the
 *    collector resolves `Resource` values with the suspend
 *    `org.jetbrains.compose.resources.getString`, so the locale of the
 *    composition that collects wins — livetv's LiveTvUserMessage seam shape).
 */
@Immutable
sealed interface LivePlayerMessage {
    data class Resource(
        val res: StringResource,
        val args: List<String> = emptyList(),
    ) : LivePlayerMessage

    data class Raw(val text: String) : LivePlayerMessage
}

@Composable
fun LivePlayerMessage.asText(): String = when (this) {
    is LivePlayerMessage.Resource -> stringResource(res, *args.toTypedArray())
    is LivePlayerMessage.Raw -> text
}
