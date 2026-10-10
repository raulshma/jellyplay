package com.raulshma.jellyplay.core.model

import kotlinx.serialization.Serializable

/**
 * The settings-sync wire payload for ONE per-item/per-series playback
 * preference row (the `itemprefs` namespace, key `"{scope}/{key}"`): the full
 * row, flattened — enum columns ride their raw names ([scope] mirrors
 * [PlaybackPrefScope], [dialogueBoostStrength] mirrors [EffectStrength]) and
 * the remembered-track triples stay flat instead of nested [RememberedTrack]s
 * so the payload mirrors the persisted row one-to-one and survives domain
 * refactors without a wire migration.
 *
 * Deliberately NOT the domain [ItemPlaybackPreference]: that model nests
 * typed objects ([RememberedTrack], [MpvRenderOverrides]) whose shapes are
 * free to evolve; this payload is the stable wire form. `renderProfile` rides
 * as the raw serialized [MpvRenderOverrides] string — an opaque blob the wire
 * carries verbatim. Install-local columns (the Room autogen `id`) are absent.
 *
 * [updatedAt] rides along INFORMATIONALLY (it keeps a receiving device's LWW
 * order coherent for the next local edit) — arbitration itself is the sync
 * engine's server-side stamp on the wire row, never this field. Fields
 * default so an older payload (missing a column added later) still decodes.
 *
 * MIXED-VERSION DOWNGRADE STANCE (accepted, not solved here): decoding runs
 * with `ignoreUnknownKeys`, so a field a NEWER app writes is silently dropped
 * by an OLDER app's decode — and that device's re-encoded row then reads
 * dirty against its mirror and gets re-pushed STRIPPED (with a fresh LWW
 * stamp), overwriting the newer device's full row. The contract: adding a
 * field requires all of a user's devices to run a version that knows it, or
 * the user accepts the strip-on-adopt behavior. Unknown-field preservation
 * is deliberately NOT implemented (no raw-passthrough envelope).
 */
@Serializable
data class ItemPlaybackPreferencePayload(
    val scope: String,
    val key: String,
    val audioLanguage: String? = null,
    val subtitleLanguage: String? = null,
    val subtitleDisabled: Boolean? = null,
    val subtitleForced: Boolean? = null,
    val subtitleHearingImpaired: Boolean? = null,
    val dialogueBoostStrength: String? = null,
    val rememberedAudioLabel: String? = null,
    val rememberedAudioLanguage: String? = null,
    val rememberedAudioIndex: Int? = null,
    val rememberedAudioCodec: String? = null,
    val rememberedSubtitleLabel: String? = null,
    val rememberedSubtitleLanguage: String? = null,
    val rememberedSubtitleIndex: Int? = null,
    val rememberedSubtitleCodec: String? = null,
    val renderProfile: String? = null,
    val preferredMediaSourceId: String? = null,
    val updatedAt: Long = 0L,
)
