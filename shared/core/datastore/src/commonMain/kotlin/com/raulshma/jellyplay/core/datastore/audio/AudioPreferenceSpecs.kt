package com.raulshma.jellyplay.core.datastore.audio

import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.datastore.toEnumOrNull
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.model.PreferenceResetCategory

/**
 * The audio-player domain's single preference declaration: one
 * [PreferenceSpec] row per key `AudioStore` owns — the canonical persisted
 * key name (which is ALSO the legacy pre-typed-era wire name, declared
 * exactly once), the default, the reset category, and the read/write encoding
 * ([PreferenceSpec.plainBoolean] / [plainFloat] / [plainInt] / [plainLong] /
 * [enumRow] / [derived]).
 *
 * **Read policy:** every boolean / int / float / long row's pre-spec hand
 * read went through the shared `PreferenceCodec.readBool/readInt/readFloat/
 * readLong` fallback (the legacy-string dance), so the rows use the encoded
 * [plainBoolean] / [plainInt] / [plainFloat] / [plainLong] declarations. The
 * enum-by-name rows ([AUDIO_PRELOAD_BUFFER_SIZE], [CHANNEL_MIX_MODE]) are
 * [enumRow] — the plain string-slot parse the hand reads always were. The
 * [AUDIO_NORMALIZATION_MODE] row carries the legacy `"REPLAYGAIN"` →
 * `TRACK` alias as its [PreferenceSpec.derived] read (see
 * [normalizeAudioNormalizationMode]) — non-destructive: the legacy value
 * stays on disk, the derived write persists the modern name.
 *
 * `AudioStore` is derived from these rows: the `Keys` members rebuild each
 * row's typed key from its wire name, the `read` projection delegates each
 * slice field to its row encoding, the single-key setters and `restore`
 * delegate to the rows' derived writes, and `resetKeysFor` filters the rows
 * by reset category. No row carries a search face: the domain's
 * settings-search entries stay the feature-side residual rows they always
 * were (the audio screen's groups own them), so this domain declares none.
 *
 * Adding a preference: one group entry here, one named accessor beside the
 * others, one [AudioSlice] property whose default reads the row, one
 * `read()` row, one `restore()` row (and its setter) — plus one write-through
 * test line; the derivation covers key identity, encodings, reset lists and
 * slice defaults, not the slice plumbing or its coverage.
 *
 * Public (the [com.raulshma.jellyplay.core.datastore.experimental
 * .ExperimentalPreferenceSpecs] precedent): the settings feature derives its
 * spec-backed catalog rows from [searchEntries], so the object must cross the
 * module boundary even though the store itself stays internal.
 */
object AudioPreferenceSpecs {

    // ------------------------------------------------------------------
    // The row groups — the ONE registration of this domain's rows, in
    // [AudioSlice] property order. A row is constructed exactly once, here;
    // [all], the reset derivation and [searchEntries] all derive from the
    // groups.
    // ------------------------------------------------------------------

    /** Playback speed + the night-mode volume/gain pair + autoplay. */
    private val speedAndVolume: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainFloat(
            keyName = "audio_default_speed",
            default = 1.0f,
            resetCategory = PreferenceResetCategory.AUDIO,
        ),
        PreferenceSpec.plainFloat(
            keyName = "audio_night_mode_volume",
            default = 0.4f,
            resetCategory = PreferenceResetCategory.AUDIO,
        ),
        PreferenceSpec.plainInt(
            keyName = "audio_night_mode_gain",
            default = 1200,
            resetCategory = PreferenceResetCategory.AUDIO,
        ),
        PreferenceSpec.plainLong(
            keyName = "audio_skip_previous_threshold_ms",
            default = 3_000L,
            resetCategory = PreferenceResetCategory.AUDIO,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "audio_autoplay_next",
            default = true,
            resetCategory = PreferenceResetCategory.AUDIO,
        ),
    )

    /** Preload buffer + replaygain/normalization (mode + enabled). */
    private val preloadAndNormalization: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.enumRow(
            keyName = "audio_preload_buffer_size",
            default = PreloadBufferSize.MEDIUM,
            resetCategory = PreferenceResetCategory.AUDIO,
        ),
        /*
         * Reads [AudioSlice.audioNormalizationMode]. Maps the legacy
         * `"REPLAYGAIN"` stored value to [AudioNormalizationMode.TRACK] (the
         * closest modern equivalent) before the enum parse — see
         * [normalizeAudioNormalizationMode]. Non-destructive: the derived
         * write persists the modern constant name, the raw value on disk
         * keeps degrading the same way until next written.
         */
        PreferenceSpec.derived(
            keyName = "audio_normalization_mode",
            default = AudioNormalizationMode.NONE,
            resetCategory = PreferenceResetCategory.AUDIO,
            read = { _, raw -> normalizeAudioNormalizationMode(raw) },
            encode = { it.name },
        ),
        PreferenceSpec.plainBoolean(
            keyName = "audio_normalization_enabled",
            default = false,
            resetCategory = PreferenceResetCategory.AUDIO,
        ),
        PreferenceSpec.plainFloat(
            keyName = "replaygain_pre_amp_db",
            default = 0f,
            resetCategory = PreferenceResetCategory.AUDIO,
        ),
    )

    /** Channel-mix (mode + enabled). */
    private val channelMix: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.enumRow(
            keyName = "channel_mix_mode",
            default = ChannelMixMode.AUTO,
            resetCategory = PreferenceResetCategory.AUDIO,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "channel_mix_enabled",
            default = false,
            resetCategory = PreferenceResetCategory.AUDIO,
        ),
    )

    /** Playback-lifecycle behaviour toggles (gapless / crossfade / delay / UI). */
    private val playbackBehavior: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainBoolean(
            keyName = "audio_gapless_enabled",
            default = true,
            resetCategory = PreferenceResetCategory.AUDIO,
        ),
        PreferenceSpec.plainLong(
            keyName = "audio_crossfade_duration_ms",
            default = 0L,
            resetCategory = PreferenceResetCategory.AUDIO,
        ),
        /*
         * The audio-delay knob resets under `PLAYBACK`, not `AUDIO` — the
         * legacy mapping this domain inherited (the delay pairs with the
         * video player's offset knobs, and the factory-reset category split
         * kept them together).
         */
        PreferenceSpec.plainLong(
            keyName = "audio_delay_ms",
            default = 0L,
            resetCategory = PreferenceResetCategory.PLAYBACK,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "audio_lyrics_visible",
            default = false,
            resetCategory = PreferenceResetCategory.AUDIO,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "audio_visualizer_enabled",
            default = false,
            resetCategory = PreferenceResetCategory.AUDIO,
        ),
    )

    /** Sleep timer (duration + end-of-episode stop). */
    private val sleepTimer: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainLong(
            keyName = "sleep_timer_duration_ms",
            default = 0L,
            resetCategory = PreferenceResetCategory.AUDIO,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "sleep_timer_end_of_episode",
            default = false,
            resetCategory = PreferenceResetCategory.AUDIO,
        ),
    )

    /**
     * Every row this domain declares — the concatenation of the group lists
     * above, in [AudioSlice] property order. Nothing re-lists the rows: a row
     * is registered exactly once, in its group, and appears here (and in the
     * reset derivation and [searchEntries]) automatically. The
     * derivation-integrity test iterates this — a group entry cannot escape
     * it.
     */
    val all: List<PreferenceSpec<*>> =
        speedAndVolume +
            preloadAndNormalization +
            channelMix +
            playbackBehavior +
            sleepTimer

    /**
     * Category reset participation, derived as the rows whose declared
     * [PreferenceSpec.resetCategory] is [category] — a single store owns keys
     * across two categories ([AUDIO_DELAY_MS] sits in `PLAYBACK` in the
     * legacy mapping while the rest are `AUDIO`), so each store scopes its
     * own keys per category. The facade aggregates these lists instead of a
     * central `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        all.filter { it.resetCategory == category }.map { it.typedKey() }

    /**
     * The domain's settings-search declarations, DERIVED: every row above
     * that carries a [PreferenceSearchSpec] (in [all] order) — which today is
     * none. The audio-player knobs' search faces stay the feature-side
     * residual rows they always were (the audio screen's groups own them),
     * the library domain's stance.
     */
    val searchEntries: List<PreferenceSearchSpec> = emptyList()

    // ------------------------------------------------------------------
    // Named accessors — the public lookup face of the rows, one per group
    // entry, each naming its row's wire name. Plain map lookups, no
    // reflection (the spec tier's R8 stance); a name that no group declares
    // fails fast in [row], and the JVM derivation test pins every accessor
    // against the store's [AudioStore.Keys].
    // ------------------------------------------------------------------

    val AUDIO_DEFAULT_SPEED: PreferenceSpec<Float> get() = row("audio_default_speed")

    val AUDIO_NIGHT_MODE_VOLUME: PreferenceSpec<Float> get() = row("audio_night_mode_volume")

    val AUDIO_NIGHT_MODE_GAIN: PreferenceSpec<Int> get() = row("audio_night_mode_gain")

    val AUDIO_SKIP_PREVIOUS_THRESHOLD_MS: PreferenceSpec<Long> get() = row("audio_skip_previous_threshold_ms")

    val AUDIO_AUTOPLAY_NEXT: PreferenceSpec<Boolean> get() = row("audio_autoplay_next")

    val AUDIO_PRELOAD_BUFFER_SIZE: PreferenceSpec<PreloadBufferSize> get() = row("audio_preload_buffer_size")

    val AUDIO_NORMALIZATION_MODE: PreferenceSpec<AudioNormalizationMode> get() = row("audio_normalization_mode")

    val AUDIO_NORMALIZATION_ENABLED: PreferenceSpec<Boolean> get() = row("audio_normalization_enabled")

    val REPLAYGAIN_PRE_AMP_DB: PreferenceSpec<Float> get() = row("replaygain_pre_amp_db")

    val CHANNEL_MIX_MODE: PreferenceSpec<ChannelMixMode> get() = row("channel_mix_mode")

    val CHANNEL_MIX_ENABLED: PreferenceSpec<Boolean> get() = row("channel_mix_enabled")

    val AUDIO_GAPLESS_ENABLED: PreferenceSpec<Boolean> get() = row("audio_gapless_enabled")

    val AUDIO_CROSSFADE_DURATION_MS: PreferenceSpec<Long> get() = row("audio_crossfade_duration_ms")

    val AUDIO_DELAY_MS: PreferenceSpec<Long> get() = row("audio_delay_ms")

    val AUDIO_LYRICS_VISIBLE: PreferenceSpec<Boolean> get() = row("audio_lyrics_visible")

    val AUDIO_VISUALIZER_ENABLED: PreferenceSpec<Boolean> get() = row("audio_visualizer_enabled")

    val SLEEP_TIMER_DURATION_MS: PreferenceSpec<Long> get() = row("sleep_timer_duration_ms")

    val SLEEP_TIMER_END_OF_EPISODE: PreferenceSpec<Boolean> get() = row("sleep_timer_end_of_episode")

    /** The rows keyed by wire name — the accessor lookup's memo (built once). */
    private val rowsByName: Map<String, PreferenceSpec<*>> by lazy { all.associateBy { it.keyName } }

    /**
     * The lookup behind every named accessor: the group-declared row whose
     * wire name is [keyName], typed to the accessor's declared spec type.
     * Fails fast on a name no group declares — the no-reflection substitute
     * for a misnamed-reference compile error, and the derivation test pins
     * every accessor against the store's [AudioStore.Keys].
     */
    private fun <T> row(keyName: String): PreferenceSpec<T> {
        val found = requireNotNull(rowsByName[keyName]) {
            "AudioPreferenceSpecs has no row '$keyName' — an accessor names a wire name no group declares"
        }
        @Suppress("UNCHECKED_CAST")
        return found as PreferenceSpec<T>
    }
}

/**
 * Parses a stored `audio_normalization_mode` value. Maps the legacy
 * `"REPLAYGAIN"` stored name to [AudioNormalizationMode.TRACK] (the closest
 * modern equivalent) before the enum parse — the single choke point every
 * normalization-mode read flows through ([AudioStore.read] via the
 * AUDIO_NORMALIZATION_MODE spec row, and therefore every projection fold and
 * backup import). An absent or corrupt value degrades to
 * [AudioNormalizationMode.NONE].
 */
private fun normalizeAudioNormalizationMode(raw: String?): AudioNormalizationMode =
    when (raw) {
        "REPLAYGAIN" -> AudioNormalizationMode.TRACK
        else -> raw.toEnumOrNull() ?: AudioNormalizationMode.NONE
    }
