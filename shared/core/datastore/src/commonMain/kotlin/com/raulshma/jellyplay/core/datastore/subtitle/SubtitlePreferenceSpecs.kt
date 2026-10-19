package com.raulshma.jellyplay.core.datastore.subtitle

import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.CachedJsonNullPolicy
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.LanguageRuleSet
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.SubtitleStylePreset
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer

/**
 * The HDR subtitle style the HDR pair serves when the blob is absent or
 * corrupt — the larger font + half-opaque backdrop. Stated once, here; the
 * row's default, the slice's default and every absence read derive from it.
 */
internal val DEFAULT_HDR_SUBTITLE_STYLE: SubtitleStyle = SubtitleStyle(
    fontSize = 28,
    backgroundOpacity = 0.5f,
    edgeType = SubtitleEdgeType.OUTLINE,
)

/**
 * The subtitle &amp; language domain's single preference declaration: one
 * [PreferenceSpec] row per
 * key `SubtitleLanguageStore` owns — the canonical persisted key name (which is
 * ALSO the legacy string wire name, declared exactly once), the default,
 * the reset category, and the read/write encoding ([PreferenceSpec.plainBoolean]
 * / [string] / [derived]).
 *
 * **The row groups below are the ONE registration of this domain's rows.**
 * Each row is constructed exactly once, inside its per-domain group list
 * (`generalLanguages` … `stylePresets`) — nothing re-lists them: [all] derives
 * by concatenation of the groups, `resetKeysFor` derives by filtering [all] on
 * the declared [PreferenceSpec.resetCategory], and [searchEntries] derives from
 * the rows' declared [PreferenceSpec.search] (plus the seven hand-only
 * standalone catalog entries over the two style blobs, appended and clearly
 * separated). Adding a preference is therefore one group entry, one named
 * accessor beside the others below, and nothing else on this object — a row
 * cannot be declared and then forgotten by a second list, because there is no
 * second list.
 *
 * The named `val`s are plain accessors over [row]: the public lookup face of
 * the rows, each naming its row's wire name. No reflection enumerates them —
 * property enumeration is impossible without reflection, and the spec tier is
 * R8-safe by stance ([PreferenceSpec]'s KDoc) — so an accessor that names no
 * declared row fails fast at [row] instead, and the accessor↔row binding is
 * pinned by the JVM `SubtitleSpecDerivationTest` (which rebuilds every
 * [SubtitleLanguageStore.Keys] member through these accessors and compares it
 * to the group-declared rows, the same guard the sibling domains run).
 *
 * `SubtitleLanguageStore` is derived from these rows: the `Keys` members
 * rebuild each row's typed key from its wire name, the `read` projection
 * delegates each slice field to its row encoding, the plain-typed setters and
 * the style-blob rows of `restore` delegate to the rows' derived writes, and
 * `resetKeysFor` filters the rows by reset category — so a row cannot drift
 * from the machinery that reads or writes it. The [SubtitleSlice] property
 * defaults read the rows too (`.default`) — a preference's default is stated
 * once, in its spec, not restated on the slice (the HDR style default
 * collapses onto [DEFAULT_HDR_SUBTITLE_STYLE]). Every JSON blob decode keeps
 * the store's memoisation policy ([CachedJsonNullPolicy.MemoizeNull] at
 * every site — a null raw is a cacheable input here; the playback domain's
 * NoMemoOnNull choice belongs to its legacy-migration reads, which the
 * subtitle blobs don't have).
 *
 * Not derivable by design (the no-reflection rule — see the [PreferenceSpec]
 * KDoc):
 *  - the `Keys` aliases stay hand-built (they are the reflection anchor for
 *    the JVM reset-coverage guard), as do the store's `read`/`restore` rows
 *    for the opted-out rows, its setters and its invariants;
 *  - the three nullable language overrides (`preferred_subtitle_lang` /
 *    `preferred_audio_lang` / `app_language`) are plain [string] rows whose
 *    setters are the set-or-remove dance — a row encoding writes a slot, it
 *    cannot remove one — so the store keeps those hand lines;
 *  - the three identity-by-absence / param-dependent blobs keep hand setters
 *    and restore lines via the documented [derived]-without-encode opt-out:
 *    the per-item-delay LRU map (a `delayMs == 0` write REMOVES its entry),
 *    the track-rule set and the style preset list (the identity/empty value
 *    removes the key so storage carries no no-op blob);
 *  - the projection-bundle copy and the per-field `SliceCategoryMergers` copy
 *    stay hand-written by the no-reflection rule.
 *
 * Adding a preference: one entry in the row group below (its key name,
 * default, reset category, encoding, search), one named accessor beside the
 * others, one [SubtitleSlice] property whose default reads the row, one
 * `read()` row, one `restore()` row (and its setter) — plus one write-through
 * test line; the derivation covers key identity, encodings, reset lists,
 * search registration and slice defaults, not the slice plumbing or its
 * coverage.
 *
 * Public (the [com.raulshma.jellyplay.core.datastore.experimental
 * .ExperimentalPreferenceSpecs] precedent): the settings feature derives its
 * spec-backed catalog rows from [searchEntries], so the object must cross the
 * module boundary even though the store itself stays internal.
 */
object SubtitlePreferenceSpecs {

    /**
     * Route kind shared by every settings-search row that deep-links into the
     * language settings screen (this domain's catalog rows) — the plain id the
     * feature layer maps to `Route.LanguageSettings()`.
     */
    const val ROUTE_LANGUAGE_SETTINGS = "language_settings"

    /**
     * The domain-wide settings-search category — every row this domain
     * catalogues rides the language &amp; subtitles chip.
     */
    private const val CATEGORY_LANGUAGE_SUBTITLES = "ss_cat_language_subtitles"

    // ------------------------------------------------------------------
    // The row groups — the ONE registration of this domain's rows, in
    // [SubtitleSlice] property order (the HDR toggle beside its style-blob
    // pair). A row is constructed exactly once, here; [all], the reset lists
    // and [searchEntries] all derive from the groups.
    // ------------------------------------------------------------------

    /**
     * The screen's leading language knobs: the two nullable track-language
     * overrides (set-or-remove — see the residuals note above), the
     * forced-subtitles toggle, and the app-wide display-language override
     * (reset under `MISC_APP`, not `SUBTITLES_LANGUAGE`).
     */
    private val generalLanguages: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.string(
            keyName = "preferred_subtitle_lang",
            resetCategory = PreferenceResetCategory.SUBTITLES_LANGUAGE,
            search = PreferenceSearchSpec(
                id = "subtitle_language",
                titleKey = "ss_subtitle_language_title",
                subtitleKey = "ss_subtitle_language_subtitle",
                categoryKey = CATEGORY_LANGUAGE_SUBTITLES,
                keywords = listOf("subtitles", "language", "cc", "captions"),
                routeKind = ROUTE_LANGUAGE_SETTINGS,
            ),
        ),
        PreferenceSpec.plainBoolean(
            keyName = "subtitles_forced_only",
            default = false,
            resetCategory = PreferenceResetCategory.SUBTITLES_LANGUAGE,
            search = PreferenceSearchSpec(
                id = "subtitle_forced_only",
                titleKey = "ss_subtitle_forced_only_title",
                subtitleKey = "ss_subtitle_forced_only_subtitle",
                categoryKey = CATEGORY_LANGUAGE_SUBTITLES,
                keywords = listOf("forced", "subtitles", "foreign", "parts", "native"),
                routeKind = ROUTE_LANGUAGE_SETTINGS,
            ),
        ),
        PreferenceSpec.string(
            keyName = "preferred_audio_lang",
            resetCategory = PreferenceResetCategory.SUBTITLES_LANGUAGE,
            search = PreferenceSearchSpec(
                id = "audio_language",
                titleKey = "ss_audio_language_title",
                subtitleKey = "ss_audio_language_subtitle",
                categoryKey = CATEGORY_LANGUAGE_SUBTITLES,
                keywords = listOf("language", "audio track", "speech", "default language"),
                routeKind = ROUTE_LANGUAGE_SETTINGS,
            ),
        ),
        PreferenceSpec.string(
            keyName = "app_language",
            resetCategory = PreferenceResetCategory.MISC_APP,
            search = PreferenceSearchSpec(
                id = "app_language",
                titleKey = "ss_app_language_title",
                subtitleKey = "ss_app_language_subtitle",
                categoryKey = CATEGORY_LANGUAGE_SUBTITLES,
                keywords = listOf("language", "display", "interface", "locale", "ui language", "app language"),
                routeKind = ROUTE_LANGUAGE_SETTINGS,
            ),
        ),
    )

    /** The subtitle behaviour toggles, including the HDR style toggle (the blob is the next group). */
    private val toggles: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainBoolean(
            keyName = "subtitle_preview_in_settings",
            default = true,
            resetCategory = PreferenceResetCategory.SUBTITLES_LANGUAGE,
        ),
        // App-wide (`MISC_APP`), like `app_language` — the accessibility
        // toggle follows the facade's reset-category split, not the screen's.
        PreferenceSpec.plainBoolean(
            keyName = "prefer_audio_description",
            default = false,
            resetCategory = PreferenceResetCategory.MISC_APP,
        ),
        // Declared advanced in the catalog yet always shown — the shipped
        // quirk; the row's admission gate owns the exception (the spec only
        // carries the advanced tag).
        PreferenceSpec.plainBoolean(
            keyName = "high_contrast_subtitles",
            default = false,
            resetCategory = PreferenceResetCategory.SUBTITLES_LANGUAGE,
            search = PreferenceSearchSpec(
                id = "high_contrast_subtitles",
                titleKey = "ss_high_contrast_subtitles_title",
                subtitleKey = "ss_high_contrast_subtitles_subtitle",
                categoryKey = CATEGORY_LANGUAGE_SUBTITLES,
                keywords = listOf("subtitle", "high", "contrast", "accessibility", "visibility"),
                routeKind = ROUTE_LANGUAGE_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainBoolean(
            keyName = "hdr_subtitle_style_enabled",
            default = false,
            resetCategory = PreferenceResetCategory.SUBTITLES_LANGUAGE,
            search = PreferenceSearchSpec(
                id = "hdr_subtitle_style",
                titleKey = "ss_hdr_subtitle_style_title",
                subtitleKey = "ss_hdr_subtitle_style_subtitle",
                categoryKey = CATEGORY_LANGUAGE_SUBTITLES,
                keywords = listOf("hdr", "subtitle", "style", "dolby vision", "hdr10", "brightness"),
                routeKind = ROUTE_LANGUAGE_SETTINGS,
                isAdvanced = true,
            ),
        ),
    )

    /*
     * Every blob read below is the SAME memoised decode — the raw slot
     * decoded as JSON into the row's type, the absent/corrupt slot serving
     * the row's default, the decode memoised into the per-row holder
     * ([PreferenceCodec.cachedJson] with this domain's MemoizeNull policy).
     * The helper states that shape once; each row names only its type,
     * default and holder.
     */
    private inline fun <reified T> memoizedJsonRead(
        raw: String?,
        cache: ParsedCache<T>,
        default: T,
        noinline cacheRef: (ParsedCache<T>) -> Unit,
    ): T = PreferenceCodec.cachedJson(
        raw = raw,
        cache = cache,
        default = default,
        parse = { PreferenceCodec.json.decodeFromString<T>(it) },
        cacheRef = cacheRef,
        nullPolicy = CachedJsonNullPolicy.MemoizeNull,
    )

    /*
     * The SDR + HDR subtitle style blobs. **Memoized decode beside the row:**
     * the decode is a pure function of the raw string, so its holder sits
     * beside the row it belongs to. The absent/corrupt slot serves the row's
     * declared default — the HDR row's being [DEFAULT_HDR_SUBTITLE_STYLE].
     * The derived write is the whole-object JSON encode
     * ([PreferenceCodec.encodeDefaultsJson]) — the single-key setters ride it
     * too, so their bytes omit serializer defaults; no decoded value changes
     * (the read side fills serializer defaults either way).
     */

    /** Memoisation holder for the [SUBTITLE_STYLE] row's JSON decode. */
    private var cachedSubtitleStyle: ParsedCache<SubtitleStyle> =
        ParsedCache(null, SubtitleStyle())

    /** Memoisation holder for the [HDR_SUBTITLE_STYLE] row's JSON decode. */
    private var cachedHdrSubtitleStyle: ParsedCache<SubtitleStyle> =
        ParsedCache(null, DEFAULT_HDR_SUBTITLE_STYLE)

    /** The SDR subtitle style blob; a corrupt or absent blob serves [SubtitleStyle]. */
    private val sdrStyleBlob: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.derived(
            keyName = "subtitle_style",
            default = SubtitleStyle(),
            resetCategory = PreferenceResetCategory.SUBTITLES_LANGUAGE,
            read = { _, raw ->
                memoizedJsonRead(raw, cachedSubtitleStyle, SubtitleStyle()) { cachedSubtitleStyle = it }
            },
            encode = { PreferenceCodec.encodeDefaultsJson.encodeToString(serializer<SubtitleStyle>(), it) },
        ),
    )

    /** The HDR subtitle style blob; a corrupt or absent blob serves [DEFAULT_HDR_SUBTITLE_STYLE]. */
    private val hdrStyleBlob: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.derived(
            keyName = "hdr_subtitle_style",
            default = DEFAULT_HDR_SUBTITLE_STYLE,
            resetCategory = PreferenceResetCategory.SUBTITLES_LANGUAGE,
            read = { _, raw ->
                memoizedJsonRead(raw, cachedHdrSubtitleStyle, DEFAULT_HDR_SUBTITLE_STYLE) {
                    cachedHdrSubtitleStyle = it
                }
            },
            encode = { PreferenceCodec.encodeDefaultsJson.encodeToString(serializer<SubtitleStyle>(), it) },
        ),
    )

    /*
     * The three opted-out blobs — [PreferenceSpec.derived] rows WITHOUT an
     * `encode`, the documented opt-out: the store keeps its hand-written
     * setter and restore lines because each write carries extra semantics a
     * row encoding cannot express (the LRU entry removal, the
     * identity-by-absence key removals).
     */

    /** Memoisation holder for the [SUBTITLE_DELAY_BY_ITEM] row's JSON decode. */
    private var cachedSubtitleDelayByItem: ParsedCache<Map<String, Long>> =
        ParsedCache(null, emptyMap())

    /** Memoisation holder for the [TRACK_SELECTION_RULES] row's JSON decode. */
    private var cachedTrackSelectionRules: ParsedCache<LanguageRuleSet> =
        ParsedCache(null, LanguageRuleSet())

    /** Memoisation holder for the [SUBTITLE_STYLE_PRESETS] row's JSON decode. */
    private var cachedSubtitleStylePresets: ParsedCache<List<SubtitleStylePreset>> =
        ParsedCache(null, emptyList())

    /**
     * The per-item subtitle-sync delay map. The read is memoised like every
     * blob here; the WRITE is the store's read-modify-write LRU invariant (a
     * `delayMs == 0` removes its entry) — no [PreferenceSpec.derived] encode,
     * the store keeps its hand lines.
     */
    private val perItemDelayBlob: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.derived(
            keyName = "subtitle_delay_by_item",
            default = emptyMap(),
            resetCategory = PreferenceResetCategory.SUBTITLES_LANGUAGE,
            read = { _, raw ->
                memoizedJsonRead(raw, cachedSubtitleDelayByItem, emptyMap()) {
                    cachedSubtitleDelayByItem = it
                }
            },
        ),
    )

    /**
     * The track-language rule set. The identity rule set removes the key on
     * write (identity-by-absence, matching this read's default) — the store
     * keeps its hand setter/restore lines, no [PreferenceSpec.derived] encode.
     */
    private val trackRulesBlob: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.derived(
            keyName = "track_selection_rules",
            default = LanguageRuleSet(),
            resetCategory = PreferenceResetCategory.SUBTITLES_LANGUAGE,
            read = { _, raw ->
                memoizedJsonRead(raw, cachedTrackSelectionRules, LanguageRuleSet()) {
                    cachedTrackSelectionRules = it
                }
            },
        ),
    )

    /**
     * The user-named subtitle style presets. Absent-by-default (the identity
     * write removes the key); the read keeps the store's absent-key fast
     * path — the decode machinery is skipped entirely when no blob exists.
     */
    private val stylePresetsBlob: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.derived(
            keyName = "subtitle_style_presets",
            default = emptyList(),
            resetCategory = PreferenceResetCategory.SUBTITLES_LANGUAGE,
            read = { _, raw ->
                if (raw == null) {
                    emptyList()
                } else {
                    memoizedJsonRead(raw, cachedSubtitleStylePresets, emptyList()) {
                        cachedSubtitleStylePresets = it
                    }
                }
            },
        ),
    )

    /**
     * Every row this domain declares — the concatenation of the group lists
     * above, in [SubtitleSlice] property order. Nothing re-lists the rows:
     * a row is registered exactly once, in its group, and appears here (and in
     * the reset derivation and [searchEntries]) automatically. The
     * derivation-integrity test iterates this — a group entry cannot escape it.
     */
    val all: List<PreferenceSpec<*>> =
        generalLanguages +
            toggles +
            sdrStyleBlob +
            hdrStyleBlob +
            perItemDelayBlob +
            trackRulesBlob +
            stylePresetsBlob

    /**
     * Category reset participation, derived as the rows whose declared
     * [PreferenceSpec.resetCategory] is [category] — every key resets under
     * `SUBTITLES_LANGUAGE` except the app-wide pair (`app_language`,
     * `prefer_audio_description`) under `MISC_APP`, matching the facade's
     * `resetCategoryKeys`. The facade aggregates these lists instead of a
     * central `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        all.filter { it.resetCategory == category }.map { it.typedKey() }

    // ------------------------------------------------------------------
    // Settings-search standalone entries: the style-blob catalog faces.
    // Seven catalog facts over the TWO style blobs (the playback domain's
    // per-codec passthrough precedent: the knob is one object, the catalog
    // rows are its editor faces), declared here in the language screen's
    // catalog order — the six SDR style editor rows, then the HDR one.
    // These are the domain's ONLY hand-only search entries — every other
    // search entry is declared on its row and reaches [searchEntries]
    // through the derivation below.
    // ------------------------------------------------------------------

    private val searchSubtitleFontSize = PreferenceSearchSpec(
        id = "subtitle_font_size",
        titleKey = "ss_subtitle_font_size_title",
        subtitleKey = "ss_subtitle_font_size_subtitle",
        categoryKey = CATEGORY_LANGUAGE_SUBTITLES,
        keywords = listOf("subtitle size", "font size", "text size", "bigger"),
        routeKind = ROUTE_LANGUAGE_SETTINGS,
    )

    private val searchSubtitleColor = PreferenceSearchSpec(
        id = "subtitle_color",
        titleKey = "ss_subtitle_color_title",
        subtitleKey = "ss_subtitle_color_subtitle",
        categoryKey = CATEGORY_LANGUAGE_SUBTITLES,
        keywords = listOf("subtitle color", "text color", "yellow subtitles", "white"),
        routeKind = ROUTE_LANGUAGE_SETTINGS,
        isAdvanced = true,
    )

    private val searchSubtitleBackground = PreferenceSearchSpec(
        id = "subtitle_background",
        titleKey = "ss_subtitle_background_title",
        subtitleKey = "ss_subtitle_background_subtitle",
        categoryKey = CATEGORY_LANGUAGE_SUBTITLES,
        keywords = listOf("subtitle background", "opacity", "transparency", "box"),
        routeKind = ROUTE_LANGUAGE_SETTINGS,
        isAdvanced = true,
    )

    private val searchSubtitleEdgeStyle = PreferenceSearchSpec(
        id = "subtitle_edge_style",
        titleKey = "ss_subtitle_edge_style_title",
        subtitleKey = "ss_subtitle_edge_style_subtitle",
        categoryKey = CATEGORY_LANGUAGE_SUBTITLES,
        keywords = listOf("edge style", "shadow", "outline", "border"),
        routeKind = ROUTE_LANGUAGE_SETTINGS,
        isAdvanced = true,
    )

    private val searchSubtitleSyncOffset = PreferenceSearchSpec(
        id = "subtitle_sync_offset",
        titleKey = "ss_subtitle_sync_offset_title",
        subtitleKey = "ss_subtitle_sync_offset_subtitle",
        categoryKey = CATEGORY_LANGUAGE_SUBTITLES,
        keywords = listOf("sync", "offset", "delay", "lagging subtitles"),
        routeKind = ROUTE_LANGUAGE_SETTINGS,
        isAdvanced = true,
    )

    private val searchSubtitleVerticalPosition = PreferenceSearchSpec(
        id = "subtitle_vertical_position",
        titleKey = "ss_subtitle_vertical_position_title",
        subtitleKey = "ss_subtitle_vertical_position_subtitle",
        categoryKey = CATEGORY_LANGUAGE_SUBTITLES,
        keywords = listOf("position", "height", "vertical", "bottom", "margin"),
        routeKind = ROUTE_LANGUAGE_SETTINGS,
        isAdvanced = true,
    )

    private val searchHdrSubtitleFontSize = PreferenceSearchSpec(
        id = "hdr_subtitle_font_size",
        titleKey = "ss_hdr_subtitle_font_size_title",
        subtitleKey = "ss_hdr_subtitle_font_size_subtitle",
        categoryKey = CATEGORY_LANGUAGE_SUBTITLES,
        keywords = listOf("hdr", "subtitle", "font size", "text", "dolby vision"),
        routeKind = ROUTE_LANGUAGE_SETTINGS,
        isAdvanced = true,
    )

    /** The seven standalone style-face catalog entries, in catalog order. */
    private val styleFaceStandaloneSearchEntries: List<PreferenceSearchSpec> = listOf(
        searchSubtitleFontSize,
        searchSubtitleColor,
        searchSubtitleBackground,
        searchSubtitleEdgeStyle,
        searchSubtitleSyncOffset,
        searchSubtitleVerticalPosition,
        searchHdrSubtitleFontSize,
    )

    /**
     * The domain's settings-search declarations, DERIVED: every row above that
     * carries a [PreferenceSearchSpec] (in [all] order), plus the seven
     * hand-only standalone style-face entries appended. Nothing re-lists the
     * searchable rows — a row that declares a search entry is in the catalog
     * by construction, and one without one is out by construction (the former
     * hand list could forget either). The settings feature derives its
     * spec-backed catalog rows from this list over its ordered row spine,
     * binding the declared resource keys to real resources — it matches
     * entries to rows BY ID, so this list's order is declaration order, not
     * catalog order: the row lists on the feature side own the catalog order.
     */
    val searchEntries: List<PreferenceSearchSpec> =
        all.mapNotNull { it.search } + styleFaceStandaloneSearchEntries

    // ------------------------------------------------------------------
    // Named accessors — the public lookup face of the rows, one per group
    // entry, each naming its row's wire name. Plain map lookups, no
    // reflection (the spec tier's R8 stance); a name that no group declares
    // fails fast in [row], and the JVM derivation test pins every accessor
    // against the store's Keys.
    // ------------------------------------------------------------------

    val PREFERRED_SUBTITLE_LANG: PreferenceSpec<String?> get() = row("preferred_subtitle_lang")

    val SUBTITLES_FORCED_ONLY: PreferenceSpec<Boolean> get() = row("subtitles_forced_only")

    val PREFERRED_AUDIO_LANG: PreferenceSpec<String?> get() = row("preferred_audio_lang")

    val APP_LANGUAGE: PreferenceSpec<String?> get() = row("app_language")

    val SUBTITLE_PREVIEW_IN_SETTINGS: PreferenceSpec<Boolean> get() = row("subtitle_preview_in_settings")

    val PREFER_AUDIO_DESCRIPTION: PreferenceSpec<Boolean> get() = row("prefer_audio_description")

    val HIGH_CONTRAST_SUBTITLES: PreferenceSpec<Boolean> get() = row("high_contrast_subtitles")

    val HDR_SUBTITLE_STYLE_ENABLED: PreferenceSpec<Boolean> get() = row("hdr_subtitle_style_enabled")

    val SUBTITLE_STYLE: PreferenceSpec<SubtitleStyle> get() = row("subtitle_style")

    val HDR_SUBTITLE_STYLE: PreferenceSpec<SubtitleStyle> get() = row("hdr_subtitle_style")

    val SUBTITLE_DELAY_BY_ITEM: PreferenceSpec<Map<String, Long>> get() = row("subtitle_delay_by_item")

    val TRACK_SELECTION_RULES: PreferenceSpec<LanguageRuleSet> get() = row("track_selection_rules")

    val SUBTITLE_STYLE_PRESETS: PreferenceSpec<List<SubtitleStylePreset>> get() = row("subtitle_style_presets")

    /** The rows keyed by wire name — the accessor lookup's memo (built once). */
    private val rowsByName: Map<String, PreferenceSpec<*>> by lazy { all.associateBy { it.keyName } }

    /**
     * The lookup behind every named accessor: the group-declared row whose
     * wire name is [keyName], typed to the accessor's declared spec type.
     * Fails fast on a name no group declares — the no-reflection substitute
     * for a misnamed-reference compile error, and the derivation test pins
     * every accessor against the store's [SubtitleLanguageStore.Keys].
     */
    private fun <T> row(keyName: String): PreferenceSpec<T> {
        val found = requireNotNull(rowsByName[keyName]) {
            "SubtitlePreferenceSpecs has no row '$keyName' — an accessor names a wire name no group declares"
        }
        @Suppress("UNCHECKED_CAST")
        return found as PreferenceSpec<T>
    }
}
