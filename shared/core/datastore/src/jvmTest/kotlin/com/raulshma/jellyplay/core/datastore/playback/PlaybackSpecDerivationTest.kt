package com.raulshma.jellyplay.core.datastore.playback

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
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

/**
 * Guards the Stage B derivation integrity for the playback domain: the
 * [PlaybackPreferenceSpecs] rows are the single declaration of every key's
 * wire name / storage type / reset category, and `PlaybackStore` derives its
 * `Keys` members, reset lists (and its read/restore machinery) from them — so
 * a wire-name, storage-type or reset-category drift would silently change
 * what is read from disk or wiped on reset. These tests pin the derivation to
 * the exact legacy strings the pre-spec hand-written store carried; the
 * behavioral safety net (defaults, round-trips, legacy migrations) stays in
 * [PlaybackStoreTest].
 */
class PlaybackSpecDerivationTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /**
     * The legacy wire name → stored value type each slot must keep. DataStore
     * dispatches its file serializer on the stored VALUE type (its Key is one
     * generic class), so this table is what makes a stored slot readable
     * through the typed [PlaybackStore.Keys] members — the exact expectations
     * the hand-written pre-spec store encoded at every read/write site.
     */
    private val expectedStoredType: Map<String, KClass<*>> = mapOf(
        "preferred_player" to String::class,
        "preferred_external_player" to String::class,
        "streaming_quality" to String::class,
        "cellular_streaming_quality" to String::class,
        "force_direct_play" to Boolean::class,
        "playback_mode" to String::class,
        "decoder_mode" to String::class,
        "audio_passthrough" to Boolean::class,
        "audio_passthrough_codecs" to String::class,
        "max_audio_channels" to String::class,
        "downmix_boost_db" to Float::class,
        "frame_rate_matching" to Boolean::class,
        "refresh_rate_mode" to String::class,
        "live_stream_option" to String::class,
        "offline_playback_preference" to String::class,
        "keep_screen_on_during_video" to Boolean::class,
        "pause_on_audio_focus_loss" to Boolean::class,
        "duck_on_transient_focus_loss" to Boolean::class,
        "auto_play_countdown_sec" to Int::class,
        "background_video_audio_enabled" to Boolean::class,
        "auto_enter_pip" to Boolean::class,
        "pgs_subtitle_direct_play" to Boolean::class,
        "user_data_sync_enabled" to Boolean::class,
        "android_tv_watch_next_enabled" to Boolean::class,
    )

    @Test
    fun `every spec row's derived key matches the store's declared Keys`() {
        val declared = reflectKeys(PlaybackStore.Keys).associateBy { it.name }
        val rows = PlaybackPreferenceSpecs.all.associateBy { it.keyName }

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
    fun `every row's derived write stores its declared storage's value type`() {
        // Write each row's default through its derived write and assert the
        // stored value's runtime type matches the pinned legacy storage table.
        val prefs = emptyPreferences().toMutablePreferences()
        PlaybackPreferenceSpecs.all.forEach { spec -> spec.writeDefault(prefs) }
        val storedByName = prefs.asMap().entries.associateBy { it.key.name }

        assertEquals(expectedStoredType.keys, storedByName.keys)
        expectedStoredType.forEach { (name, expectedType) ->
            assertEquals(
                expectedType,
                storedByName.getValue(name).value::class,
                "storage drift on $name",
            )
        }
    }

    @Test
    fun `resetKeysFor returns exactly the PLAYBACK-category keys`() {
        val store = PlaybackStore(TestDataStoreProvider.get(), scope)
        val playbackKeys = store.resetKeysFor(PreferenceResetCategory.PLAYBACK).map { it.name }
        // The exact 20 keys the hand-written pre-spec reset list carried
        // (force_direct_play included — the legacy surface still resets with
        // its category; live_stream_option and the subtitle/sync toggles are
        // other categories').
        assertEquals(
            listOf(
                "preferred_player",
                "preferred_external_player",
                "streaming_quality",
                "cellular_streaming_quality",
                "force_direct_play",
                "playback_mode",
                "offline_playback_preference",
                "decoder_mode",
                "audio_passthrough",
                "audio_passthrough_codecs",
                "max_audio_channels",
                "downmix_boost_db",
                "frame_rate_matching",
                "refresh_rate_mode",
                "keep_screen_on_during_video",
                "pause_on_audio_focus_loss",
                "duck_on_transient_focus_loss",
                "auto_play_countdown_sec",
                "background_video_audio_enabled",
                "auto_enter_pip",
            ).sorted(),
            playbackKeys.sorted(),
        )
    }

    @Test
    fun `resetKeysFor scopes the non-PLAYBACK categories to their own rows`() {
        val store = PlaybackStore(TestDataStoreProvider.get(), scope)
        assertEquals(
            listOf("pgs_subtitle_direct_play"),
            store.resetKeysFor(PreferenceResetCategory.SUBTITLES_LANGUAGE).map { it.name },
        )
        assertEquals(
            listOf("live_stream_option"),
            store.resetKeysFor(PreferenceResetCategory.SYNCPLAY_CASTING).map { it.name },
        )
        assertEquals(
            listOf("android_tv_watch_next_enabled", "user_data_sync_enabled").sorted(),
            store.resetKeysFor(PreferenceResetCategory.MISC_APP).map { it.name }.sorted(),
        )
        assertEquals(
            emptyList<Preferences.Key<*>>(),
            store.resetKeysFor(PreferenceResetCategory.APPEARANCE),
        )
    }

    /** Writes [spec]'s default through its derived write (the default IS the row's T). */
    @Suppress("UNCHECKED_CAST")
    private fun PreferenceSpec<*>.writeDefault(prefs: MutablePreferences) {
        (this as PreferenceSpec<Any>).writeTo(prefs, default)
    }
}
