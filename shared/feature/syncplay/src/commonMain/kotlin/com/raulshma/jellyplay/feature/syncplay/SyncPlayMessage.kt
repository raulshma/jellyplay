package com.raulshma.jellyplay.feature.syncplay

import com.raulshma.jellyplay.core.ui.message.UiMessage

/**
 * Error/notification message seam for the SyncPlay screen (music conveyor's
 * MixErrorMessage shape): the message stays unresolved until render time (the
 * commonMain VM seam has no Context) — [UiMessage.Resource] carries the
 * localized [StringResource] and [UiMessage.Raw] an already-final string
 * (failure cause). The screen collapses it with `asText` where it renders.
 *
 * M3 conveyor unification: the seal was exactly the shared two-variant
 * [UiMessage] shape, so it is now a typealias. Kotlin forbids reaching a
 * nested classifier or companion through a typealias (KEEP-40), so
 * construction and `when` branches name the canonical container directly —
 * `UiMessage.Resource` / `UiMessage.Raw` / `UiMessage.of`, the same
 * container-qualified convention this package's `UiText.Resource` uses —
 * while every TYPE position (state fields, the notifications flow) keeps
 * the feature alias.
 */
typealias SyncPlayMessage = UiMessage
