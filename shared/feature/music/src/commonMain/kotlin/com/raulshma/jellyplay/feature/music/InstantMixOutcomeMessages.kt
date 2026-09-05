package com.raulshma.jellyplay.feature.music

import androidx.compose.runtime.Composable
import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome
import com.raulshma.jellyplay.core.data.playback.InstantMixError
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_mix_unavailable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Shared instant-mix outcome → message mapping for the music detail screens
 * (album / artist). Both screens resolve the outcome identically — the
 * localized empty-mix string and the cause-message fallback — so the mapping
 * lives here once. Returns null for [AudioQueueOutcome.Started] (implicit:
 * playback started) and [AudioQueueOutcome.Suppressed] (guard veto, silent by
 * design) — the caller treats null as "no error".
 *
 * The message stays unresolved until render time (the commonMain VM seam has
 * no Context): [MixErrorMessage.Resource] carries the localized
 * [StringResource] and [MixErrorMessage.Raw] an already-final string (failure
 * cause). Screens collapse it with [MixErrorMessage.asText] where it renders.
 */
sealed interface MixErrorMessage {
    data class Resource(val res: StringResource) : MixErrorMessage
    data class Raw(val message: String) : MixErrorMessage
}

fun AudioQueueOutcome.toMixErrorMessage(): MixErrorMessage? = when (this) {
    AudioQueueOutcome.Empty -> MixErrorMessage.Resource(Res.string.music_mix_unavailable)
    is AudioQueueOutcome.Failed -> MixErrorMessage.Raw(cause.message ?: "Failed to start Instant Mix")
    else -> null
}

/**
 * Same mapping for the shared [InstantMixStateHolder]'s error surface (the
 * album/artist VMs fold holder state into their one `error` field): null
 * stays null so a Started/Suppressed mix never touches the error channel.
 */
fun InstantMixError?.toMixErrorMessage(): MixErrorMessage? = when (this) {
    InstantMixError.EmptyMix -> MixErrorMessage.Resource(Res.string.music_mix_unavailable)
    is InstantMixError.Failed -> MixErrorMessage.Raw(message)
    null -> null
}

@Composable
fun MixErrorMessage.asText(): String = when (this) {
    is MixErrorMessage.Resource -> stringResource(res)
    is MixErrorMessage.Raw -> message
}
