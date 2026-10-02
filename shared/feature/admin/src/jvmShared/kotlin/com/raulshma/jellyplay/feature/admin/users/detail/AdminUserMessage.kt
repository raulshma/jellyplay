package com.raulshma.jellyplay.feature.admin.users.detail

import com.raulshma.jellyplay.core.ui.message.UiMessage

/**
 * Localizable one-shot feedback emitted by [UserDetailViewModel] (music
 * conveyor's MixErrorMessage pattern — the commonMain VM seam has no Context,
 * so the message stays unresolved until render time). [Resource] carries the
 * localized [StringResource] and [Raw] an already-final string (exception
 * message / fixed wording). Screens collapse it with `asText` where it
 * renders (core.ui.message).
 *
 * M3 conveyor unification: the seal was exactly the shared two-variant
 * [UiMessage] shape, so it is now a typealias. Kotlin forbids reaching a
 * nested classifier or companion through a typealias (KEEP-40), so the
 * Raw-only error writes name the canonical container's constructor directly
 * (`UiMessage::Raw`) while every TYPE position keeps the feature alias.
 * Those writes are deliberately NOT the [UiMessage.of] fold — `of` would put
 * a fallback resource where the current contract leaves null.
 */
typealias AdminUserMessage = UiMessage
