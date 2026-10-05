package com.raulshma.jellyplay.feature.music

import com.raulshma.jellyplay.core.data.playback.InstantMixError
import com.raulshma.jellyplay.core.ui.message.UiMessage
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_mix_unavailable

/**
 * Shared instant-mix outcome → message mapping for the music detail screens
 * (album / artist). Both screens resolve the holder error identically — the
 * localized empty-mix string and the cause-message fallback — so the mapping
 * lives here once. Returns null for a null holder error (implicit: playback
 * started, or the guard veto suppressed silently) — the caller treats null as
 * "no error".
 *
 * Vocabulary note (the instant-mix collapse): the outcome chain is
 * `AudioQueueOutcome` (core:data commonMain) →
 * [com.raulshma.jellyplay.core.data.playback.InstantMixOutcome] (the holder's
 * pure commonMain input via core:data's `toInstantMixOutcome`) → THIS fold.
 * The surviving message fold is THIS one over [InstantMixError]; the former
 * duplicate `MusicQueueOutcome.toMixErrorMessage` fold had zero production
 * callers and is deleted.
 *
 * The message stays unresolved until render time (the commonMain VM seam has
 * no Context): [UiMessage.Resource] carries the localized
 * [StringResource] and [UiMessage.Raw] an already-final string (failure
 * cause). Screens collapse it with `asText` where it renders.
 *
 * M3 conveyor unification: this seal was the ORIGIN of the two-variant
 * conveyor shape every feature seal copied (and the reason the fold lives in
 * [UiMessage.of] now), so it typealiases to [UiMessage] like the rest.
 * Kotlin forbids reaching a nested classifier or companion through a
 * typealias (KEEP-40), so construction names the canonical container
 * directly (`UiMessage.Resource` / `UiMessage.Raw`) while every TYPE
 * position (the ViewModels' `error` fields) keeps the feature alias. The
 * one mechanical rename: [UiMessage.Raw]'s field is `text` — this seal's
 * `Raw.message` (construction sites passed positionally, so only test-side
 * field reads moved).
 */
typealias MixErrorMessage = UiMessage

/** Consumer-side fallback for failure causes that carry no message. */
private const val FAILED_TO_START_MIX = "Failed to start Instant Mix"

/**
 * The ONE mix-error message fold: maps the shared
 * [com.raulshma.jellyplay.core.data.playback.InstantMixStateHolder]'s error
 * surface (the album/artist VMs fold holder state into their one `error`
 * field). Null stays null so a Started/Suppressed mix never touches the
 * error channel.
 */
fun InstantMixError?.toMixErrorMessage(): MixErrorMessage? = when (this) {
    InstantMixError.EmptyMix -> UiMessage.Resource(Res.string.music_mix_unavailable)
    is InstantMixError.Failed -> UiMessage.Raw(message ?: FAILED_TO_START_MIX)
    null -> null
}
