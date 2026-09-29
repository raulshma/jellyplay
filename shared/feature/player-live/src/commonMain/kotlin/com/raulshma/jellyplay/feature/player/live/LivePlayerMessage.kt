package com.raulshma.jellyplay.feature.player.live

import com.raulshma.jellyplay.core.ui.message.UiMessage

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
 *    screen with `asText` before [components.LiveErrorBanner] renders), and
 *  - the one-shot record/cancel feedback the ViewModel emits wrapped in
 *    [LivePlayerEvent.Message] on [LiveTvPlayerViewModel.events] (the
 *    screen-forward replacement for the legacy Android-only `UserMessageBus`
 *    + `UiText.Resource` posts; the collector resolves `Resource` values
 *    with the suspend `org.jetbrains.compose.resources.getString`, so the
 *    locale of the composition that collects wins — livetv's
 *    LiveTvUserMessage seam shape).
 *
 * M3 conveyor unification: the seal was exactly the shared two-variant
 * [UiMessage] shape, so it is now a typealias. Kotlin forbids reaching a
 * nested classifier or companion through a typealias (KEEP-40), so
 * construction and `when` branches name the canonical container directly —
 * `UiMessage.Resource` / `UiMessage.Raw` / `UiMessage.of`, the same
 * container-qualified convention core.ui.message's `UiText.Resource` uses —
 * while every TYPE position (the UI state field, the event payload) keeps
 * the feature alias. The live format args were `List<String>`;
 * [UiMessage.Resource.args] widens the read type to `List<Any>`, which every
 * `stringResource`/`getString` spread accepts unchanged, and the
 * engine-error fold routes through [UiMessage.of] instead of hand-copying
 * `?.let(Raw) ?: Resource(...)`.
 */
typealias LivePlayerMessage = UiMessage
