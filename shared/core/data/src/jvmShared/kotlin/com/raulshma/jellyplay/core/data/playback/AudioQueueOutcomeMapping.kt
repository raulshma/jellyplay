package com.raulshma.jellyplay.core.data.playback

/**
 * Normalizes the facade outcome to the [InstantMixStateHolder]'s pure outcome
 * shape — the one fold the album, artist, and media-detail mix starts used to
 * carry as three private copies. [AudioQueueOutcome.Started] keeps the queue
 * head for the one-shot navigation (null on an empty queue).
 */
fun AudioQueueOutcome.toInstantMixOutcome(): InstantMixOutcome = when (this) {
    is AudioQueueOutcome.Started -> InstantMixOutcome.Started(queue.firstOrNull()?.id)
    AudioQueueOutcome.Empty -> InstantMixOutcome.EmptyMix
    AudioQueueOutcome.Suppressed -> InstantMixOutcome.Suppressed
    is AudioQueueOutcome.Failed -> InstantMixOutcome.Failed(cause)
}
