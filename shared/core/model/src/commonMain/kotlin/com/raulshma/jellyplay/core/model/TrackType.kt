package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Discriminator for the two categories of tracks a [com.raulshma.jellyplay.core.data.remote.RemotePlayableEngine]
 * can select. Mirrored from `feature:player:video` so the `core:data` remote
 * "Play To" receiver can reference it without depending on the player module.
 */
@Immutable
@Serializable
enum class TrackType { AUDIO, SUBTITLE }
