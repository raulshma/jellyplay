package com.raulshma.jellyplay.core.datastore.audioeffects

import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.CachedJsonNullPolicy
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.playback.PlaybackPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.ReverbPreset
import kotlinx.serialization.encodeToString

/**
 * Memoisation holder for the [AudioEffectsPreferenceSpecs.EQUALIZER_SETTINGS]
 * row's JSON decode, keyed on the raw string so the decode is skipped when the
 * key has not changed on a given `dataStore.data` emission. Moved here from a
 * per-`AudioEffectsStore` field with the row it belongs to (the playback
 * domain's passthrough-codec cache precedent): the decode is a pure function of
 * the raw string, and a null raw memoises its result exactly once
 * ([CachedJsonNullPolicy.MemoizeNull] — this store's pre-promotion policy; the
 * nullable decode result maps to the [EqualizerSettings] default at the read
 * site, exactly as the pre-spec store's slice mapping did). See
 * [PreferenceCodec.cachedJson].
 */
private var cachedEqualizerSettings: ParsedCache<EqualizerSettings?> = ParsedCache(null, null)

/**
 * The audio-effects domain's single preference declaration: one
 * [PreferenceSpec] row per key `AudioEffectsStore` owns — the canonical
 * persisted key name (which is ALSO the legacy pre-typed-era wire name,
 * declared exactly once), the default, the reset category, and the read/write
 * encoding ([PreferenceSpec.plainBoolean] / [plainInt] / [plainFloat] /
 * [enumRow] / [derived]).
 *
 * `AudioEffectsStore` is derived from these rows: the `Keys` members rebuild
 * each row's typed key from its wire name, the `read` projection delegates
 * each slice field to its row encoding, the single-key setters and `restore`
 * delegate to the rows' derived writes, and `resetKeysFor` filters the rows by
 * reset category — so a row cannot drift from the machinery that reads or
 * writes it. Every key this store owns resets under the single `AUDIO`
 * category.
 *
 * **Read policy:** every boolean / int / float row's pre-spec hand read went
 * through the shared `PreferenceCodec.readBool/readInt/readFloat` fallback
 * (the legacy-string dance), so the rows use the encoded [plainBoolean] /
 * [plainInt] / [plainFloat] declarations — including the `volume_boost_*`
 * pair, whose keys sit deliberately OUTSIDE `PreferenceCodec`'s migration
 * lists: they were born in the typed-key era (a legacy string slot cannot
 * exist under their names), so the fallback those rows carry can never match
 * and the reads are behaviorally identical to a plain typed read.
 *
 * Not derivable by design (the accepted residuals, the videoplayer/playback
 * precedent): the [EQUALIZER_SETTINGS] JSON blob's restore encoding — the
 * pre-spec `restore` wrote it with the defaults-encoding Json while the
 * setter used the lenient one; the row's derived write is the lenient encode
 * (value-equivalent — the omitted defaults decode back to their Kotlin
 * defaults — and it keeps the row's ONE encode beside its ONE read).
 *
 * Adding a preference: one group entry here, one named accessor beside the
 * others, one [AudioEffectsSlice] property whose default reads the row, one
 * `read()` row, one `restore()` row (and its setter) — plus one write-through
 * test line; the derivation covers key identity, encodings, reset lists and
 * slice defaults, not the slice plumbing or its coverage.
 *
 * Public (the [com.raulshma.jellyplay.core.datastore.experimental
 * .ExperimentalPreferenceSpecs] precedent): the settings feature derives its
 * spec-backed catalog rows from [searchEntries], so the object must cross the
 * module boundary even though the store itself stays internal.
 */
object AudioEffectsPreferenceSpecs {

    /**
     * Route kind shared by every settings-search row that deep-links into the
     * audio settings screen — the plain id the feature layer maps to
     * `Route.AudioSettings()`.
     */
    const val ROUTE_AUDIO_SETTINGS = "audio_settings"

    // ------------------------------------------------------------------
    // The row groups — the ONE registration of this domain's rows, in
    // [AudioEffectsSlice] property order. A row is constructed exactly once,
    // here; [all], the reset derivation and [searchEntries] all derive from
    // the groups.
    // ------------------------------------------------------------------

    /** Dialogue boost (enabled + strength). */
    private val dialogueBoost: List<PreferenceSpec<*>> = listOf(
        /*
         * The boost toggle's screen face lives on the playback settings'
         * advanced-video group (the pre-spec fused rows render the pair
         * there), so the search entry deep-links into the playback route —
         * the reset review's AUDIO category carries the row.
         */
        PreferenceSpec.plainBoolean(
            keyName = "dialogue_boost_enabled",
            default = false,
            resetCategory = PreferenceResetCategory.AUDIO,
            search = PreferenceSearchSpec(
                id = "dialogue_boost",
                titleKey = "ss_dialogue_boost_title",
                subtitleKey = "ss_dialogue_boost_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("dialogue", "boost", "speech", "vocal", "enhance"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        // One-key family: the strength row's face restates the toggle's
        // screen title (no ss_*_title twin).
        PreferenceSpec.enumRow(
            keyName = "dialogue_boost_strength",
            default = EffectStrength.MODERATE,
            resetCategory = PreferenceResetCategory.AUDIO,
            search = PreferenceSearchSpec(
                id = "dialogue_boost_strength",
                titleKey = "settings_dialogue_boost_strength",
                subtitleKey = "ss_dialogue_boost_strength_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("dialogue", "boost", "strength", "level", "speech", "amplify"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
    )

    /** Equalizer (enabled + JSON settings blob + preset). */
    private val equalizer: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainBoolean(
            keyName = "equalizer_enabled",
            default = false,
            resetCategory = PreferenceResetCategory.AUDIO,
            search = PreferenceSearchSpec(
                id = "equalizer",
                titleKey = "ss_equalizer_title",
                subtitleKey = "ss_equalizer_subtitle",
                categoryKey = "ss_cat_audio_player",
                keywords = listOf("equalizer", "eq", "bands", "bass", "treble", "audio profile"),
                routeKind = ROUTE_AUDIO_SETTINGS,
                isAdvanced = true,
            ),
        ),
        /*
         * Reads [AudioEffectsSlice.equalizerSettings]. The JSON blob is an
         * opaque writer-produced string: the decode is lenient and memoised
         * on the raw string — see [cachedEqualizerSettings]. Not searchable —
         * the blob's user face IS the equalizer row above (the editor sheet
         * the toggle opens).
         */
        PreferenceSpec.derived(
            keyName = "equalizer_settings",
            default = EqualizerSettings(),
            resetCategory = PreferenceResetCategory.AUDIO,
            read = { _, raw ->
                PreferenceCodec.cachedJson(
                    raw = raw,
                    cache = cachedEqualizerSettings,
                    default = null,
                    parse = { PreferenceCodec.json.decodeFromString<EqualizerSettings>(it) },
                    cacheRef = { cachedEqualizerSettings = it },
                    nullPolicy = CachedJsonNullPolicy.MemoizeNull,
                ) ?: EqualizerSettings()
            },
            encode = { PreferenceCodec.json.encodeToString(it) },
        ),
        PreferenceSpec.enumRow(
            keyName = "equalizer_preset",
            default = EqualizerPreset.FLAT,
            resetCategory = PreferenceResetCategory.AUDIO,
            search = PreferenceSearchSpec(
                id = "equalizer_preset",
                titleKey = "ss_equalizer_preset_title",
                subtitleKey = "ss_equalizer_preset_subtitle",
                categoryKey = "ss_cat_audio_player",
                keywords = listOf("equalizer", "preset", "eq", "profile", "bass", "treble"),
                routeKind = ROUTE_AUDIO_SETTINGS,
                isAdvanced = true,
            ),
        ),
    )

    /** Night-mode effect (enabled + strength). */
    private val nightMode: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainBoolean(
            keyName = "night_mode_enabled",
            default = false,
            resetCategory = PreferenceResetCategory.AUDIO,
            search = PreferenceSearchSpec(
                id = "night_mode",
                titleKey = "ss_night_mode_title",
                subtitleKey = "ss_night_mode_subtitle",
                categoryKey = "ss_cat_audio_player",
                keywords = listOf("night mode", "audio", "evening", "quiet", "soft"),
                routeKind = ROUTE_AUDIO_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.enumRow(
            keyName = "night_mode_strength",
            default = EffectStrength.MODERATE,
            resetCategory = PreferenceResetCategory.AUDIO,
            search = PreferenceSearchSpec(
                id = "night_mode_strength",
                titleKey = "ss_night_mode_strength_title",
                subtitleKey = "ss_night_mode_strength_subtitle",
                categoryKey = "ss_cat_audio_player",
                keywords = listOf("night mode", "strength", "intensity", "audio", "level"),
                routeKind = ROUTE_AUDIO_SETTINGS,
                isAdvanced = true,
            ),
        ),
    )

    /** Bass boost (enabled + strength). */
    private val bassBoost: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainBoolean(
            keyName = "bass_boost_enabled",
            default = false,
            resetCategory = PreferenceResetCategory.AUDIO,
            search = PreferenceSearchSpec(
                id = "bass_boost",
                titleKey = "ss_bass_boost_title",
                subtitleKey = "ss_bass_boost_subtitle",
                categoryKey = "ss_cat_audio_player",
                keywords = listOf("bass", "boost", "low end", "subwoofer", "amplify"),
                routeKind = ROUTE_AUDIO_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.enumRow(
            keyName = "bass_boost_strength",
            default = EffectStrength.MODERATE,
            resetCategory = PreferenceResetCategory.AUDIO,
            search = PreferenceSearchSpec(
                id = "bass_boost_strength",
                titleKey = "ss_bass_boost_strength_title",
                subtitleKey = "ss_bass_boost_strength_subtitle",
                categoryKey = "ss_cat_audio_player",
                keywords = listOf("bass", "boost", "strength", "intensity", "low end", "subwoofer"),
                routeKind = ROUTE_AUDIO_SETTINGS,
                isAdvanced = true,
            ),
        ),
    )

    /** Virtualizer (enabled + strength). */
    private val virtualizer: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainBoolean(
            keyName = "virtualizer_enabled",
            default = false,
            resetCategory = PreferenceResetCategory.AUDIO,
            search = PreferenceSearchSpec(
                id = "virtualizer",
                titleKey = "ss_virtualizer_title",
                subtitleKey = "ss_virtualizer_subtitle",
                categoryKey = "ss_cat_audio_player",
                keywords = listOf("virtualizer", "spatial", "3d", "surround", "headphones"),
                routeKind = ROUTE_AUDIO_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainInt(
            keyName = "virtualizer_strength",
            default = 500,
            resetCategory = PreferenceResetCategory.AUDIO,
            search = PreferenceSearchSpec(
                id = "virtualizer_strength",
                titleKey = "ss_virtualizer_strength_title",
                subtitleKey = "ss_virtualizer_strength_subtitle",
                categoryKey = "ss_cat_audio_player",
                keywords = listOf("virtualizer", "strength", "spatial", "3d", "surround"),
                routeKind = ROUTE_AUDIO_SETTINGS,
                isAdvanced = true,
            ),
        ),
    )

    /** Reverb, balance and the auto-EQ / pitch knobs. */
    private val reverbAndKnobs: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.enumRow(
            keyName = "reverb_preset",
            default = ReverbPreset.NONE,
            resetCategory = PreferenceResetCategory.AUDIO,
            search = PreferenceSearchSpec(
                id = "reverb",
                titleKey = "ss_reverb_title",
                subtitleKey = "ss_reverb_subtitle",
                categoryKey = "ss_cat_audio_player",
                keywords = listOf("reverb", "acoustic", "environment", "room", "hall"),
                routeKind = ROUTE_AUDIO_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainFloat(
            keyName = "lr_balance",
            default = 0f,
            resetCategory = PreferenceResetCategory.AUDIO,
            search = PreferenceSearchSpec(
                id = "lr_balance",
                titleKey = "ss_lr_balance_title",
                subtitleKey = "ss_lr_balance_subtitle",
                categoryKey = "ss_cat_audio_player",
                keywords = listOf("balance", "left", "right", "stereo balance"),
                routeKind = ROUTE_AUDIO_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainBoolean(
            keyName = "auto_eq_by_genre",
            default = false,
            resetCategory = PreferenceResetCategory.AUDIO,
            search = PreferenceSearchSpec(
                id = "auto_eq_by_genre",
                titleKey = "ss_auto_eq_by_genre_title",
                subtitleKey = "ss_auto_eq_by_genre_subtitle",
                categoryKey = "ss_cat_audio_player",
                keywords = listOf("auto eq", "genre", "automatic", "equalizer", "preset", "music"),
                routeKind = ROUTE_AUDIO_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainFloat(
            keyName = "pitch_semitones",
            default = 0f,
            resetCategory = PreferenceResetCategory.AUDIO,
            search = PreferenceSearchSpec(
                id = "pitch_shift",
                titleKey = "ss_pitch_shift_title",
                subtitleKey = "ss_pitch_shift_subtitle",
                categoryKey = "ss_cat_audio_player",
                keywords = listOf("pitch", "shift", "semitone", "tone", "key", "audio"),
                routeKind = ROUTE_AUDIO_SETTINGS,
                isAdvanced = true,
            ),
        ),
    )

    /** Volume boost (enabled + gain). */
    private val volumeBoost: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainBoolean(
            keyName = "volume_boost_enabled",
            default = false,
            resetCategory = PreferenceResetCategory.AUDIO,
            search = PreferenceSearchSpec(
                id = "volume_boost",
                titleKey = "ss_volume_boost_title",
                subtitleKey = "ss_volume_boost_subtitle",
                categoryKey = "ss_cat_audio_player",
                keywords = listOf("volume boost", "boost", "loudness", "gain", "preamp"),
                routeKind = ROUTE_AUDIO_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainInt(
            keyName = "volume_boost_gain",
            default = 0,
            resetCategory = PreferenceResetCategory.AUDIO,
            search = PreferenceSearchSpec(
                id = "volume_boost_gain",
                titleKey = "ss_volume_boost_gain_title",
                subtitleKey = "ss_volume_boost_gain_subtitle",
                categoryKey = "ss_cat_audio_player",
                keywords = listOf("volume boost", "gain", "loudness", "preamp", "level"),
                routeKind = ROUTE_AUDIO_SETTINGS,
                isAdvanced = true,
            ),
        ),
    )

    /**
     * Every row this domain declares — the concatenation of the group lists
     * above, in [AudioEffectsSlice] property order. Nothing re-lists the rows:
     * a row is registered exactly once, in its group, and appears here (and in
     * the reset derivation and [searchEntries]) automatically. The
     * derivation-integrity test iterates this — a group entry cannot escape it.
     */
    val all: List<PreferenceSpec<*>> =
        dialogueBoost +
            equalizer +
            nightMode +
            bassBoost +
            virtualizer +
            reverbAndKnobs +
            volumeBoost

    /**
     * Category reset participation, derived as the rows whose declared
     * [PreferenceSpec.resetCategory] is [category] — every key this store owns
     * sits in the single `AUDIO` bucket, so all other categories return an
     * empty list. The facade aggregates these lists instead of a central
     * `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        all.filter { it.resetCategory == category }.map { it.typedKey() }

    /**
     * The domain's settings-search declarations, DERIVED: every row above that
     * carries a [PreferenceSearchSpec] (in [all] order). Nothing re-lists the
     * searchable rows — a row that declares a search entry is in the catalog
     * by construction, and one without one is out by construction. The only
     * search-less row is the [EQUALIZER_SETTINGS] JSON blob (its user face is
     * the equalizer row beside it). The settings feature matches entries to
     * rows BY ID, so this list's order is declaration order, not catalog
     * order: the row lists on the feature side own the catalog order.
     */
    val searchEntries: List<PreferenceSearchSpec> =
        all.mapNotNull { it.search }

    // ------------------------------------------------------------------
    // Named accessors — the public lookup face of the rows, one per group
    // entry, each naming its row's wire name. Plain map lookups, no
    // reflection (the spec tier's R8 stance); a name that no group declares
    // fails fast in [row], and the JVM derivation test pins every accessor
    // against the store's Keys.
    // ------------------------------------------------------------------

    val DIALOGUE_BOOST_ENABLED: PreferenceSpec<Boolean> get() = row("dialogue_boost_enabled")

    val DIALOGUE_BOOST_STRENGTH: PreferenceSpec<EffectStrength> get() = row("dialogue_boost_strength")

    val EQUALIZER_ENABLED: PreferenceSpec<Boolean> get() = row("equalizer_enabled")

    val EQUALIZER_SETTINGS: PreferenceSpec<EqualizerSettings> get() = row("equalizer_settings")

    val EQUALIZER_PRESET: PreferenceSpec<EqualizerPreset> get() = row("equalizer_preset")

    val NIGHT_MODE_ENABLED: PreferenceSpec<Boolean> get() = row("night_mode_enabled")

    val NIGHT_MODE_STRENGTH: PreferenceSpec<EffectStrength> get() = row("night_mode_strength")

    val BASS_BOOST_ENABLED: PreferenceSpec<Boolean> get() = row("bass_boost_enabled")

    val BASS_BOOST_STRENGTH: PreferenceSpec<EffectStrength> get() = row("bass_boost_strength")

    val VIRTUALIZER_ENABLED: PreferenceSpec<Boolean> get() = row("virtualizer_enabled")

    val VIRTUALIZER_STRENGTH: PreferenceSpec<Int> get() = row("virtualizer_strength")

    val REVERB_PRESET: PreferenceSpec<ReverbPreset> get() = row("reverb_preset")

    val LR_BALANCE: PreferenceSpec<Float> get() = row("lr_balance")

    val AUTO_EQ_BY_GENRE: PreferenceSpec<Boolean> get() = row("auto_eq_by_genre")

    val PITCH_SEMITONES: PreferenceSpec<Float> get() = row("pitch_semitones")

    val VOLUME_BOOST_ENABLED: PreferenceSpec<Boolean> get() = row("volume_boost_enabled")

    val VOLUME_BOOST_GAIN: PreferenceSpec<Int> get() = row("volume_boost_gain")

    /** The rows keyed by wire name — the accessor lookup's memo (built once). */
    private val rowsByName: Map<String, PreferenceSpec<*>> by lazy { all.associateBy { it.keyName } }

    /**
     * The lookup behind every named accessor: the group-declared row whose
     * wire name is [keyName], typed to the accessor's declared spec type.
     * Fails fast on a name no group declares — the no-reflection substitute
     * for a misnamed-reference compile error, and the derivation test pins
     * every accessor against the store's [AudioEffectsStore.Keys].
     */
    private fun <T> row(keyName: String): PreferenceSpec<T> {
        val found = requireNotNull(rowsByName[keyName]) {
            "AudioEffectsPreferenceSpecs has no row '$keyName' — an accessor names a wire name no group declares"
        }
        @Suppress("UNCHECKED_CAST")
        return found as PreferenceSpec<T>
    }
}
