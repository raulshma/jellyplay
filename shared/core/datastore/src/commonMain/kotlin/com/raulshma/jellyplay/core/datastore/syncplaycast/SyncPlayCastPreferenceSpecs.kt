package com.raulshma.jellyplay.core.datastore.syncplaycast

import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.datastore.playback.PlaybackPreferenceSpecs
import com.raulshma.jellyplay.core.model.CastingStrategy
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.SyncPlayJoinBehavior

/**
 * The SyncPlay + casting + DVR domain's single preference declaration: one
 * [PreferenceSpec] row per key `SyncPlayCastStore` owns — the canonical
 * persisted key name (which is ALSO the legacy pre-typed-era wire name,
 * declared exactly once), the default, the reset category, and the read/write
 * encoding ([PreferenceSpec.plainBoolean] / [plainInt] / [plainLong] /
 * [enumRow] / [derived] / the declaration-only [PreferenceSpec.string]).
 *
 * **Read policy:** the booleans (`sync_play_auto_accept_invites`,
 * `background_casting_enabled`) and ints (`dvr_pre/post_padding_minutes`) are
 * deliberately NOT in `PreferenceCodec`'s legacy-string → typed-key migration
 * lists — they were born in the typed-key era (a legacy string slot cannot
 * exist under their names), so the fallback the [plainBoolean] / [plainInt]
 * rows carry can never match and the reads are behaviorally identical to a
 * plain typed read (the audioeffects `volume_boost_*` precedent). The hand
 * read of `sync_play_tolerance_ms` was the plain `prefs[key] ?: 100L`, but the
 * spec tier has no declaration-only long constructor (custom's STRING storage
 * would misstate a LONG slot), so it rides [plainLong] for the same
 * born-typed reason. The two enums (`sync_play_join_behavior`,
 * `default_casting_strategy`) are enum-by-name string slots whose hand reads
 * were already the `toEnumOrNull() ?: default` parse — the [enumRow] encoding
 * verbatim. The two raw strings (`preferred_renderer` — a nullable override —
 * and `dvr_recording_quality`) keep the declaration-only [PreferenceSpec.string]
 * rows: the hand read is the plain `prefs[key] ?: default`, exactly
 * [PreferenceSpec.readStored].
 *
 * `SyncPlayCastStore` is derived from these rows: the `Keys` members rebuild
 * each row's typed key from its wire name, the `read` projection delegates
 * each slice field to its row encoding (or [PreferenceSpec.readStored]), the
 * single-key setters and [restore]'s encoded rows delegate to the rows'
 * derived writes, and [resetKeysFor] filters the rows by reset category —
 * every key this store owns sits in the single `SYNCPLAY_CASTING` bucket.
 *
 * **The documented opt-outs (hand-written residuals, declaration-only rows
 * have no derived write):** [SyncPlayCastStore.setPreferredRenderer] (writes
 * the renderer, or REMOVES the key for null — a removal cannot be a row
 * encoding) and [SyncPlayCastStore.setDvrRecordingQuality]; [restore]'s
 * `preferred_renderer` line keeps the same nullable guard beside them.
 *
 * Adding a preference: one group entry here, one named accessor beside the
 * others, one [SyncPlayCastSlice] property whose default reads the row, one
 * `read()` row, one `restore()` row (and its setter) — plus one write-through
 * test line; the derivation covers key identity, encodings, reset lists and
 * search registration, not the slice plumbing or its coverage.
 *
 * Public (the [com.raulshma.jellyplay.core.datastore.experimental
 * .ExperimentalPreferenceSpecs] precedent): the settings feature derives its
 * spec-backed catalog rows from [searchEntries], so the object must cross the
 * module boundary even though the store itself stays internal.
 */
object SyncPlayCastPreferenceSpecs {

    // ------------------------------------------------------------------
    // The row groups — the ONE registration of this domain's rows, in
    // [SyncPlayCastSlice] property order (SyncPlay, casting, DVR — the
    // screen-group order the hand rows always rendered). A row is
    // constructed exactly once, here; [all], the reset derivation and
    // [searchEntries] all derive from the groups.
    // ------------------------------------------------------------------

    /** The SyncPlay watch-party knobs. */
    private val syncplay: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.enumRow(
            keyName = "sync_play_join_behavior",
            default = SyncPlayJoinBehavior.ASK,
            resetCategory = PreferenceResetCategory.SYNCPLAY_CASTING,
            search = PreferenceSearchSpec(
                id = "syncplay_join_behavior",
                titleKey = "ss_syncplay_join_behavior_title",
                subtitleKey = "ss_syncplay_join_behavior_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("syncplay", "join", "behavior", "group", "watch party"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            ),
        ),
        /*
         * Born-typed (never in `PreferenceCodec`'s longs migration list — the
         * hand read was the plain `prefs[key] ?: 100L`), so the legacy-string
         * fallback this row carries can never match; see the object KDoc's
         * read policy. The search faces reuse the hand rows' ss_* keys.
         */
        PreferenceSpec.plainLong(
            keyName = "sync_play_tolerance_ms",
            default = 100L,
            resetCategory = PreferenceResetCategory.SYNCPLAY_CASTING,
            search = PreferenceSearchSpec(
                id = "syncplay_tolerance",
                titleKey = "ss_syncplay_tolerance_title",
                subtitleKey = "ss_syncplay_tolerance_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("syncplay", "tolerance", "drift", "sync", "correction"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            ),
        ),
        PreferenceSpec.plainBoolean(
            keyName = "sync_play_auto_accept_invites",
            default = false,
            resetCategory = PreferenceResetCategory.SYNCPLAY_CASTING,
            search = PreferenceSearchSpec(
                id = "syncplay_auto_accept_invites",
                titleKey = "ss_syncplay_auto_accept_invites_title",
                subtitleKey = "ss_syncplay_auto_accept_invites_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("syncplay", "auto", "accept", "invites", "friends"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            ),
        ),
    )

    /** The casting + DLNA knobs. */
    private val casting: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.enumRow(
            keyName = "default_casting_strategy",
            default = CastingStrategy.ASK,
            resetCategory = PreferenceResetCategory.SYNCPLAY_CASTING,
            search = PreferenceSearchSpec(
                id = "casting_strategy",
                titleKey = "ss_casting_strategy_title",
                subtitleKey = "ss_casting_strategy_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("casting", "strategy", "dlna", "cast", "chromecast", "tv"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            ),
        ),
        PreferenceSpec.plainBoolean(
            keyName = "background_casting_enabled",
            default = true,
            resetCategory = PreferenceResetCategory.SYNCPLAY_CASTING,
            search = PreferenceSearchSpec(
                id = "background_casting",
                titleKey = "ss_background_casting_title",
                subtitleKey = "ss_background_casting_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("casting", "background", "keep alive", "dlna", "cast"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            ),
        ),
        /*
         * The nullable preferred-renderer override: an absent key means "no
         * preference" (the hand setter removes the key for null), so the row
         * is the declaration-only [PreferenceSpec.string] — the read is
         * [PreferenceSpec.readStored] verbatim, and the write stays the
         * store's hand line (see the object KDoc's opt-outs).
         */
        PreferenceSpec.string(
            keyName = "preferred_renderer",
            default = null,
            resetCategory = PreferenceResetCategory.SYNCPLAY_CASTING,
            search = PreferenceSearchSpec(
                id = "preferred_renderer",
                titleKey = "ss_preferred_renderer_title",
                subtitleKey = "ss_preferred_renderer_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("renderer", "preferred", "cast", "device", "target", "tv"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            ),
        ),
    )

    /** The DVR padding + recording-quality knobs. */
    private val dvr: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainInt(
            keyName = "dvr_pre_padding_minutes",
            default = 0,
            resetCategory = PreferenceResetCategory.SYNCPLAY_CASTING,
            search = PreferenceSearchSpec(
                id = "dvr_pre_padding",
                titleKey = "ss_dvr_pre_padding_title",
                subtitleKey = "ss_dvr_pre_padding_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("dvr", "pre padding", "recording", "live tv", "start", "early"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            ),
        ),
        PreferenceSpec.plainInt(
            keyName = "dvr_post_padding_minutes",
            default = 0,
            resetCategory = PreferenceResetCategory.SYNCPLAY_CASTING,
            search = PreferenceSearchSpec(
                id = "dvr_post_padding",
                titleKey = "ss_dvr_post_padding_title",
                subtitleKey = "ss_dvr_post_padding_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("dvr", "post padding", "recording", "live tv", "end", "extend"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            ),
        ),
        PreferenceSpec.string(
            keyName = "dvr_recording_quality",
            default = "AUTO",
            resetCategory = PreferenceResetCategory.SYNCPLAY_CASTING,
            search = PreferenceSearchSpec(
                id = "dvr_recording_quality",
                titleKey = "ss_dvr_recording_quality_title",
                subtitleKey = "ss_dvr_recording_quality_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("dvr", "recording", "quality", "live tv", "resolution"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
    )

    /**
     * Every row this domain declares — the concatenation of the group lists
     * above, in [SyncPlayCastSlice] property order. Nothing re-lists the rows:
     * a row is registered exactly once, in its group, and appears here (and in
     * the reset derivation and [searchEntries]) automatically. The
     * derivation-integrity test iterates this — a group entry cannot escape it.
     */
    val all: List<PreferenceSpec<*>> =
        syncplay +
            casting +
            dvr

    /**
     * Category reset participation, derived as the rows whose declared
     * [PreferenceSpec.resetCategory] is [category] — every key this store owns
     * sits in the single `SYNCPLAY_CASTING` bucket (the
     * `LIVE_STREAM_OPTION` reset-mate is `PlaybackStore`'s row), so all other
     * categories return an empty list. The facade aggregates these lists
     * instead of a central `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        all.filter { it.resetCategory == category }.map { it.typedKey() }

    /**
     * The domain's settings-search declarations, DERIVED: every row above
     * carries a [PreferenceSearchSpec] (in [all] order), reusing the hand
     * fused rows' ss_* keys and ids. Nothing re-lists the searchable rows — a
     * row that declares a search entry is in the catalog by construction. The
     * settings feature matches entries to rows BY ID, so this list's order is
     * declaration order, not catalog order: the row lists on the feature side
     * own the catalog order.
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

    val SYNC_PLAY_JOIN_BEHAVIOR: PreferenceSpec<SyncPlayJoinBehavior> get() = row("sync_play_join_behavior")

    val SYNC_PLAY_TOLERANCE_MS: PreferenceSpec<Long> get() = row("sync_play_tolerance_ms")

    val SYNC_PLAY_AUTO_ACCEPT_INVITES: PreferenceSpec<Boolean> get() = row("sync_play_auto_accept_invites")

    val DEFAULT_CASTING_STRATEGY: PreferenceSpec<CastingStrategy> get() = row("default_casting_strategy")

    val BACKGROUND_CASTING_ENABLED: PreferenceSpec<Boolean> get() = row("background_casting_enabled")

    val PREFERRED_RENDERER: PreferenceSpec<String?> get() = row("preferred_renderer")

    val DVR_PRE_PADDING_MINUTES: PreferenceSpec<Int> get() = row("dvr_pre_padding_minutes")

    val DVR_POST_PADDING_MINUTES: PreferenceSpec<Int> get() = row("dvr_post_padding_minutes")

    val DVR_RECORDING_QUALITY: PreferenceSpec<String> get() = row("dvr_recording_quality")

    /** The rows keyed by wire name — the accessor lookup's memo (built once). */
    private val rowsByName: Map<String, PreferenceSpec<*>> by lazy { all.associateBy { it.keyName } }

    /**
     * The lookup behind every named accessor: the group-declared row whose
     * wire name is [keyName], typed to the accessor's declared spec type.
     * Fails fast on a name no group declares — the no-reflection substitute
     * for a misnamed-reference compile error, and the derivation test pins
     * every accessor against the store's [SyncPlayCastStore.Keys].
     */
    private fun <T> row(keyName: String): PreferenceSpec<T> {
        val found = requireNotNull(rowsByName[keyName]) {
            "SyncPlayCastPreferenceSpecs has no row '$keyName' — an accessor names a wire name no group declares"
        }
        @Suppress("UNCHECKED_CAST")
        return found as PreferenceSpec<T>
    }
}
