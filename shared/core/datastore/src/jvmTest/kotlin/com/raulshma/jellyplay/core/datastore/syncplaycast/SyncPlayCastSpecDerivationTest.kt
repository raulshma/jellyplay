package com.raulshma.jellyplay.core.datastore.syncplaycast

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.datastore.reflectKeys
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the derivation integrity for the SyncPlay + casting + DVR domain: the
 * [SyncPlayCastPreferenceSpecs] rows are the single declaration of every key's
 * wire name / storage type / reset category, and `SyncPlayCastStore` derives
 * its `Keys` members, reset lists (and its read/restore machinery) from them —
 * so a wire-name, storage-type or reset-category drift would silently change
 * what is read from disk or wiped on reset. These tests pin the derivation to
 * the exact legacy wire strings; the behavioral safety net (defaults,
 * round-trips, the nullable-renderer guard) stays in [SyncPlayCastStoreTest].
 */
class SyncPlayCastSpecDerivationTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /**
     * The legacy wire name → stored value type each slot must keep. DataStore
     * dispatches its file serializer on the stored VALUE type (its Key is one
     * generic class), so this table is what makes a stored slot readable
     * through the typed [SyncPlayCastStore.Keys] members — the exact
     * expectations the hand-written pre-spec store encoded at every read/write
     * site.
     */
    private val expectedStoredType: Map<String, KClass<*>> = mapOf(
        "sync_play_join_behavior" to String::class,
        "sync_play_tolerance_ms" to Long::class,
        "sync_play_auto_accept_invites" to Boolean::class,
        "default_casting_strategy" to String::class,
        "background_casting_enabled" to Boolean::class,
        "preferred_renderer" to String::class,
        "dvr_pre_padding_minutes" to Int::class,
        "dvr_post_padding_minutes" to Int::class,
        "dvr_recording_quality" to String::class,
    )

    /** The declaration-only string rows — no derived write to pin (the storage pin above covers them). */
    private val declarationOnlyRows = setOf("preferred_renderer", "dvr_recording_quality")

    private fun expectedKey(name: String, type: KClass<*>): Preferences.Key<*> = when (type) {
        Boolean::class -> booleanPreferencesKey(name)
        Int::class -> intPreferencesKey(name)
        Long::class -> longPreferencesKey(name)
        String::class -> stringPreferencesKey(name)
        else -> error("unexpected storage type for $name")
    }

    @Test
    fun `every spec row's derived key matches the store's declared Keys`() {
        val declared = reflectKeys(SyncPlayCastStore.Keys).associateBy { it.name }
        val rows = SyncPlayCastPreferenceSpecs.all.associateBy { it.keyName }

        // Same key set: a row without a Keys member (or vice versa) means the
        // store and its declaration have drifted.
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
    fun `every row rebuilds its legacy storage type`() {
        // The declaration-only string rows have no derived write, so the
        // STORAGE pin is the typed key each row rebuilds from its wire name
        // (Preferences.Key equality is class-sensitive, so this pins the slot
        // type per row — the library domain's storage-pin shape).
        SyncPlayCastPreferenceSpecs.all.forEach { spec ->
            assertEquals(
                expectedKey(spec.keyName, expectedStoredType.getValue(spec.keyName)),
                spec.typedKey(),
                "storage drift on ${spec.keyName}",
            )
        }
    }

    @Test
    fun `every row's derived write stores its declared storage's value type`() {
        // Write each derived-write row's default through its derived write and
        // assert the stored value's runtime type matches the pinned legacy
        // storage table (the playback pin, scoped to the rows that carry an
        // encoding — the declaration-only renderer/quality rows are pinned by
        // the storage-type test above).
        val prefs = emptyPreferences().toMutablePreferences()
        SyncPlayCastPreferenceSpecs.all
            .filter { it.encoding?.write != null }
            .forEach { spec -> spec.writeDefault(prefs) }
        val storedByName = prefs.asMap().entries.associateBy { it.key.name }

        assertEquals(
            expectedStoredType.keys - declarationOnlyRows,
            storedByName.keys,
        )
        expectedStoredType
            .filterKeys { it !in declarationOnlyRows }
            .forEach { (name, expectedType) ->
                assertEquals(
                    expectedType,
                    storedByName.getValue(name).value::class,
                    "storage drift on $name",
                )
            }
    }

    @Test
    fun `resetKeysFor returns exactly the SYNCPLAY_CASTING-category keys`() {
        val store = SyncPlayCastStore(TestDataStoreProvider.get(), scope)
        val syncplayKeys = store.resetKeysFor(PreferenceResetCategory.SYNCPLAY_CASTING).map { it.name }
        // The exact keys the hand-written pre-spec reset list carried — every
        // key this store owns sits in the SYNCPLAY_CASTING bucket (the
        // LIVE_STREAM_OPTION reset-mate is PlaybackStore's row).
        assertEquals(expectedStoredType.keys.sorted(), syncplayKeys.sorted())
        PreferenceResetCategory.entries
            .filter { it != PreferenceResetCategory.SYNCPLAY_CASTING }
            .forEach { category ->
                assertTrue(store.resetKeysFor(category).isEmpty(), "$category unexpectedly non-empty")
            }
    }

    @Test
    fun `searchEntries is exactly the searchable rows' entries`() {
        // Every row declares its search face (reusing the hand fused rows'
        // ss_* keys and ids): 1:1 totality, nothing re-listed, nothing
        // dropped. Order is pinned as declaration order (the settings feature
        // matches entries to rows BY ID and owns the catalog order).
        val entries = SyncPlayCastPreferenceSpecs.searchEntries
        assertEquals(
            SyncPlayCastPreferenceSpecs.all.mapNotNull { it.search }.map { it.id },
            entries.map { it.id },
        )
        // …and no entry id is declared twice.
        assertEquals(entries.size, entries.map { it.id }.toSet().size, "a search id is declared twice")
    }

    @Test
    fun `every slice default equals its spec default`() {
        // The single-default guard: each [SyncPlayCastSlice] property's
        // default reads its row's `.default`, so the value below IS the row's
        // — if a future edit restates a literal here (or on the slice) and the
        // two drift, this fails.
        val slice = SyncPlayCastSlice()
        assertEquals(SyncPlayCastPreferenceSpecs.SYNC_PLAY_JOIN_BEHAVIOR.default, slice.syncPlayJoinBehavior)
        assertEquals(SyncPlayCastPreferenceSpecs.SYNC_PLAY_TOLERANCE_MS.default, slice.syncPlayToleranceMs)
        assertEquals(SyncPlayCastPreferenceSpecs.SYNC_PLAY_AUTO_ACCEPT_INVITES.default, slice.syncPlayAutoAcceptInvites)
        assertEquals(SyncPlayCastPreferenceSpecs.DEFAULT_CASTING_STRATEGY.default, slice.defaultCastingStrategy)
        assertEquals(SyncPlayCastPreferenceSpecs.BACKGROUND_CASTING_ENABLED.default, slice.backgroundCastingEnabled)
        assertEquals(SyncPlayCastPreferenceSpecs.PREFERRED_RENDERER.default, slice.preferredRenderer)
        assertEquals(SyncPlayCastPreferenceSpecs.DVR_PRE_PADDING_MINUTES.default, slice.dvrPrePaddingMinutes)
        assertEquals(SyncPlayCastPreferenceSpecs.DVR_POST_PADDING_MINUTES.default, slice.dvrPostPaddingMinutes)
        assertEquals(SyncPlayCastPreferenceSpecs.DVR_RECORDING_QUALITY.default, slice.dvrRecordingQuality)
        // Totality: the 9 pinned properties are ALL the slice's fields (9
        // rows, every row slice-bound). A field added without a spec-default
        // pin above (and without bumping this count) fails here. Statics (the
        // @Serializable Companion, Compose's $stable) don't count.
        val instanceFields = SyncPlayCastSlice()::class.java.declaredFields
            .count { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertEquals(9, instanceFields)
    }

    /** Writes [spec]'s default through its derived write (the default IS the row's T). */
    @Suppress("UNCHECKED_CAST")
    private fun PreferenceSpec<*>.writeDefault(prefs: MutablePreferences) {
        (this as PreferenceSpec<Any>).writeTo(prefs, default)
    }
}
