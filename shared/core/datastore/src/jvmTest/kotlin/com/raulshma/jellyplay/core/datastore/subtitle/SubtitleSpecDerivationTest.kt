package com.raulshma.jellyplay.core.datastore.subtitle

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.datastore.reflectKeys
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.LanguageRuleSet
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.SubtitleStylePreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the derivation integrity for the subtitle &amp; language
 * domain: the [SubtitlePreferenceSpecs] rows are the single declaration of
 * every key's wire name / storage type / reset category, and
 * `SubtitleLanguageStore` derives its `Keys` members, reset lists (and its
 * read/restore machinery) from them, while [SubtitleSlice] derives its defaults
 * and [SubtitlePreferenceSpecs.searchEntries] derives from the rows' declared
 * search specs — so a wire-name, storage-type or reset-category drift would
 * silently change what is read from disk or wiped on reset. These tests pin the
 * derivation to the exact legacy wire strings; the
 * behavioral safety net (defaults, round-trips, the LRU and
 * identity-by-absence invariants) stays in [SubtitleLanguageStoreTest].
 */
class SubtitleSpecDerivationTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /**
     * The legacy wire name → stored value type each slot must keep. DataStore
     * dispatches its file serializer on the stored VALUE type (its Key is one
     * generic class), so this table is what makes a stored slot readable
     * through the typed [SubtitleLanguageStore.Keys] members — the exact
     * expectations the hand-written pre-spec store encoded at every read/write
     * site.
     */
    private val expectedStoredType: Map<String, KClass<*>> = mapOf(
        "preferred_subtitle_lang" to String::class,
        "subtitles_forced_only" to Boolean::class,
        "preferred_audio_lang" to String::class,
        "subtitle_delay_by_item" to String::class,
        "subtitle_style" to String::class,
        "subtitle_preview_in_settings" to Boolean::class,
        "prefer_audio_description" to Boolean::class,
        "high_contrast_subtitles" to Boolean::class,
        "app_language" to String::class,
        "hdr_subtitle_style_enabled" to Boolean::class,
        "hdr_subtitle_style" to String::class,
        "track_selection_rules" to String::class,
        "subtitle_style_presets" to String::class,
    )

    /**
     * The opted-out rows — the documented [PreferenceSpec.derived]-without-
     * encode residuals and the plain [PreferenceSpec.string] declarations:
     * the three nullable language overrides (their setters are the
     * set-or-remove dance) and the three identity/param-dependent blobs (the
     * LRU entry removal, the identity key removals). They have no derived
     * write (a null encoding, or a null [PreferenceEncoding.write] on their
     * [Custom] encoding), so they are the only rows excluded from the
     * write-through storage table below.
     */
    private val noDerivedWriteRows = setOf(
        "preferred_subtitle_lang",
        "preferred_audio_lang",
        "app_language",
        "subtitle_delay_by_item",
        "track_selection_rules",
        "subtitle_style_presets",
    )

    @Test
    fun `every spec row's derived key matches the store's declared Keys`() {
        val declared = reflectKeys(SubtitleLanguageStore.Keys).associateBy { it.name }
        val rows = SubtitlePreferenceSpecs.all.associateBy { it.keyName }

        // Same key set: a row without a Keys member (or vice versa) means the
        // store and its declaration have drifted. This also pins every named
        // accessor: each Keys member is rebuilt THROUGH its accessor, so a
        // misnamed accessor lookup fails fast here instead of at runtime.
        assertEquals(expectedStoredType.keys, rows.keys)
        assertEquals(rows.keys, declared.keys)

        // Each Keys member IS the row's key rebuilt from the single-declared
        // wire name (Preferences.Key equality is name-based) — the migration
        // fallback consults keyName itself, so this equality is what keeps a
        // stored value readable across the derivation.
        rows.forEach { (name, spec) ->
            assertEquals(spec.typedKey(), declared.getValue(name), "key drift on $name")
        }
    }

    @Test
    fun `every row's derived write stores its declared storage's value type`() {
        val writeable = SubtitlePreferenceSpecs.all.filter { it.encoding?.write != null }
        assertEquals(
            expectedStoredType.keys - noDerivedWriteRows,
            writeable.map { it.keyName }.toSet(),
            "the rows excluded from the write-through table must be exactly the opted-out rows",
        )

        // Write each row's default through its derived write and assert the
        // stored value's runtime type matches the pinned legacy storage table.
        val prefs = emptyPreferences().toMutablePreferences()
        writeable.forEach { spec -> spec.writeDefault(prefs) }
        val storedByName = prefs.asMap().entries.associateBy { it.key.name }

        assertEquals(expectedStoredType.keys - noDerivedWriteRows, storedByName.keys)
        (expectedStoredType.keys - noDerivedWriteRows).forEach { name ->
            assertEquals(
                expectedStoredType.getValue(name),
                storedByName.getValue(name).value::class,
                "storage drift on $name",
            )
        }
    }

    @Test
    fun `all declares each wire name exactly once`() {
        // The groups are the ONE registration per row; `all` is their
        // concatenation, so a duplicated wire name means a row was declared in
        // two groups (and reset lists / search derivation would double it).
        val names = SubtitlePreferenceSpecs.all.map { it.keyName }
        assertEquals(names.size, names.toSet().size, "a row is declared twice across the groups")
        assertEquals(expectedStoredType.keys, names.toSet())
    }

    @Test
    fun `resetKeysFor returns exactly the SUBTITLES_LANGUAGE-category keys`() {
        val store = SubtitleLanguageStore(TestDataStoreProvider.get(), scope)
        val subtitleKeys = store.resetKeysFor(PreferenceResetCategory.SUBTITLES_LANGUAGE).map { it.name }
        // The exact 11 keys the hand-written pre-spec reset list carried (the
        // app-wide pair below is the rest).
        assertEquals(
            listOf(
                "preferred_subtitle_lang",
                "preferred_audio_lang",
                "subtitles_forced_only",
                "subtitle_preview_in_settings",
                "subtitle_style",
                "high_contrast_subtitles",
                "hdr_subtitle_style_enabled",
                "hdr_subtitle_style",
                "subtitle_delay_by_item",
                "track_selection_rules",
                "subtitle_style_presets",
            ).sorted(),
            subtitleKeys.sorted(),
        )
    }

    @Test
    fun `resetKeysFor scopes the non-SUBTITLES categories to their own rows`() {
        val store = SubtitleLanguageStore(TestDataStoreProvider.get(), scope)
        assertEquals(
            listOf("app_language", "prefer_audio_description").sorted(),
            store.resetKeysFor(PreferenceResetCategory.MISC_APP).map { it.name }.sorted(),
        )
        PreferenceResetCategory.entries
            .filter { it != PreferenceResetCategory.SUBTITLES_LANGUAGE && it != PreferenceResetCategory.MISC_APP }
            .forEach { category ->
                assertTrue(store.resetKeysFor(category).isEmpty(), "$category unexpectedly non-empty")
            }
    }

    @Test
    fun `searchEntries is exactly the searchable rows' entries plus the standalone style-face entries`() {
        // 1:1 totality: the catalog is the rows' declared search specs (in `all`
        // order) plus the seven hand-only style-face catalog facts — nothing
        // re-listed, nothing dropped, nothing hand-appended beyond the
        // standalones. Order is pinned as declaration order (the settings
        // feature matches entries to rows BY ID and owns the catalog order).
        val standaloneIds = listOf(
            "subtitle_font_size",
            "subtitle_color",
            "subtitle_background",
            "subtitle_edge_style",
            "subtitle_sync_offset",
            "subtitle_vertical_position",
            "hdr_subtitle_font_size",
        )
        val entries = SubtitlePreferenceSpecs.searchEntries
        assertEquals(
            SubtitlePreferenceSpecs.all.mapNotNull { it.search }.map { it.id } + standaloneIds,
            entries.map { it.id },
        )
        // …and no entry id is declared twice.
        assertEquals(entries.size, entries.map { it.id }.toSet().size, "a search id is declared twice")
    }

    @Test
    fun `every slice default equals its spec default`() {
        // The single-default guard: each [SubtitleSlice] property's default
        // reads its row's `.default`, so the value below IS the row's — if a
        // future edit restates a literal here (or on the slice) and the two
        // drift, this fails. The HDR style default is the row's
        // DEFAULT_HDR_SUBTITLE_STYLE.
        val slice = SubtitleSlice()
        assertEquals(SubtitlePreferenceSpecs.PREFERRED_SUBTITLE_LANG.default, slice.preferredSubtitleLanguage)
        assertEquals(SubtitlePreferenceSpecs.SUBTITLES_FORCED_ONLY.default, slice.subtitlesForcedOnly)
        assertEquals(SubtitlePreferenceSpecs.PREFERRED_AUDIO_LANG.default, slice.preferredAudioLanguage)
        assertEquals(SubtitlePreferenceSpecs.SUBTITLE_DELAY_BY_ITEM.default, slice.subtitleDelayByItem)
        assertEquals(SubtitlePreferenceSpecs.TRACK_SELECTION_RULES.default, slice.languageRules)
        assertEquals(SubtitlePreferenceSpecs.SUBTITLE_STYLE.default, slice.subtitleStyle)
        assertEquals(SubtitlePreferenceSpecs.SUBTITLE_PREVIEW_IN_SETTINGS.default, slice.subtitlePreviewInSettings)
        assertEquals(SubtitlePreferenceSpecs.PREFER_AUDIO_DESCRIPTION.default, slice.preferAudioDescription)
        assertEquals(SubtitlePreferenceSpecs.HIGH_CONTRAST_SUBTITLES.default, slice.highContrastSubtitles)
        assertEquals(SubtitlePreferenceSpecs.APP_LANGUAGE.default, slice.appLanguage)
        assertEquals(SubtitlePreferenceSpecs.HDR_SUBTITLE_STYLE_ENABLED.default, slice.hdrSubtitleStyleEnabled)
        assertEquals(SubtitlePreferenceSpecs.HDR_SUBTITLE_STYLE.default, slice.hdrSubtitleStyle)
        assertEquals(SubtitlePreferenceSpecs.SUBTITLE_STYLE_PRESETS.default, slice.userStylePresets)
        // Totality: the 13 pinned properties are ALL the slice's fields (13
        // rows, one slice property each). A field added without a spec-default
        // pin above (and without bumping this count) fails here. Statics (the
        // @Serializable Companion, Compose's $stable) don't count.
        val instanceFields = SubtitleSlice()::class.java.declaredFields
            .count { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertEquals(13, instanceFields)
    }

    @Test
    fun `an empty snapshot reads exactly the default slice`() {
        // The two faces of the single default agree: what an absent key reads
        // (the row encodings, including the style-blob absence defaults and
        // the presets' absent-key fast path) is exactly what a fresh
        // in-memory slice serves.
        val store = SubtitleLanguageStore(TestDataStoreProvider.get(), scope)
        assertEquals(SubtitleSlice(), store.read(emptyPreferences()))
    }

    @Test
    fun `a corrupt blob serves its row default on every read`() {
        // The blob decode policy (MemoizeNull at every site — this store's
        // pre-promotion choice, kept): a decode failure caches its fallback,
        // so the same corrupt snapshot serves the row default on every read —
        // never a crash, never a partial decode. Pinned per blob: the SDR and
        // HDR style defaults, the empty delay map, the identity rule set and
        // the empty preset list.
        val corrupt: MutablePreferences = emptyPreferences().toMutablePreferences().apply {
            listOf(
                SubtitlePreferenceSpecs.SUBTITLE_STYLE,
                SubtitlePreferenceSpecs.HDR_SUBTITLE_STYLE,
                SubtitlePreferenceSpecs.SUBTITLE_DELAY_BY_ITEM,
                SubtitlePreferenceSpecs.TRACK_SELECTION_RULES,
                SubtitlePreferenceSpecs.SUBTITLE_STYLE_PRESETS,
            ).forEach { spec -> this[stringPreferencesKey(spec.keyName)] = "not json" }
        }
        val store = SubtitleLanguageStore(TestDataStoreProvider.get(), scope)
        repeat(2) { // the second read rides the memo path the first populated
            val slice = store.read(corrupt)
            assertEquals(SubtitleStyle(), slice.subtitleStyle)
            assertEquals(
                SubtitleStyle(fontSize = 28, backgroundOpacity = 0.5f, edgeType = SubtitleEdgeType.OUTLINE),
                slice.hdrSubtitleStyle,
            )
            assertEquals(emptyMap(), slice.subtitleDelayByItem)
            assertEquals(LanguageRuleSet(), slice.languageRules)
            assertEquals(emptyList<SubtitleStylePreset>(), slice.userStylePresets)
        }
    }

    /** Writes [spec]'s default through its derived write (the default IS the row's T). */
    @Suppress("UNCHECKED_CAST")
    private fun PreferenceSpec<*>.writeDefault(prefs: MutablePreferences) {
        (this as PreferenceSpec<Any>).writeTo(prefs, default)
    }
}
