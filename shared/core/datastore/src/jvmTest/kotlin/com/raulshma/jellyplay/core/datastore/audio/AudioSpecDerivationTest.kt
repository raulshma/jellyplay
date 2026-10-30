package com.raulshma.jellyplay.core.datastore.audio

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
 * Guards the derivation integrity for the audio-player domain: the
 * [AudioPreferenceSpecs] rows are the single declaration of every key's
 * wire name / storage type / reset category, and `AudioStore` derives its
 * `Keys` members, reset lists (and its read/restore machinery) from them —
 * so a wire-name, storage-type or reset-category drift would silently change
 * what is read from disk or wiped on reset. These tests pin the derivation to
 * the exact legacy wire strings; the behavioral safety net (defaults,
 * round-trips, the `REPLAYGAIN` alias) stays in [AudioStoreTest].
 */
class AudioSpecDerivationTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /**
     * The legacy wire name → stored value type each slot must keep. DataStore
     * dispatches its file serializer on the stored VALUE type (its Key is one
     * generic class), so this table is what makes a stored slot readable
     * through the typed [AudioStore.Keys] members — the exact expectations
     * the hand-written pre-spec store encoded at every read/write site.
     */
    private val expectedStoredType: Map<String, KClass<*>> = mapOf(
        "audio_default_speed" to Float::class,
        "audio_night_mode_volume" to Float::class,
        "audio_night_mode_gain" to Int::class,
        "audio_skip_previous_threshold_ms" to Long::class,
        "audio_autoplay_next" to Boolean::class,
        "audio_preload_buffer_size" to String::class,
        "audio_normalization_mode" to String::class,
        "audio_normalization_enabled" to Boolean::class,
        "replaygain_pre_amp_db" to Float::class,
        "channel_mix_mode" to String::class,
        "channel_mix_enabled" to Boolean::class,
        "audio_gapless_enabled" to Boolean::class,
        "audio_crossfade_duration_ms" to Long::class,
        "audio_delay_ms" to Long::class,
        "audio_lyrics_visible" to Boolean::class,
        "audio_visualizer_enabled" to Boolean::class,
        "sleep_timer_duration_ms" to Long::class,
        "sleep_timer_end_of_episode" to Boolean::class,
    )

    @Test
    fun `every spec row's derived key matches the store's declared Keys`() {
        val declared = reflectKeys(AudioStore.Keys).associateBy { it.name }
        val rows = AudioPreferenceSpecs.all.associateBy { it.keyName }

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
        AudioPreferenceSpecs.all.forEach { spec -> spec.writeDefault(prefs) }
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
    fun `resetKeysFor scopes the categories to their rows`() {
        val store = AudioStore(TestDataStoreProvider.get(), scope)
        // The exact keys the hand-written pre-spec reset lists carried: the
        // audio-delay knob sits in PLAYBACK in the legacy mapping, everything
        // else in AUDIO.
        assertEquals(
            listOf("audio_delay_ms"),
            store.resetKeysFor(PreferenceResetCategory.PLAYBACK).map { it.name },
        )
        assertEquals(
            listOf(
                "audio_default_speed",
                "audio_night_mode_volume",
                "audio_night_mode_gain",
                "audio_skip_previous_threshold_ms",
                "audio_autoplay_next",
                "audio_preload_buffer_size",
                "audio_normalization_mode",
                "audio_normalization_enabled",
                "replaygain_pre_amp_db",
                "channel_mix_mode",
                "channel_mix_enabled",
                "audio_gapless_enabled",
                "audio_crossfade_duration_ms",
                "audio_lyrics_visible",
                "audio_visualizer_enabled",
                "sleep_timer_duration_ms",
                "sleep_timer_end_of_episode",
            ).sorted(),
            store.resetKeysFor(PreferenceResetCategory.AUDIO).map { it.name }.sorted(),
        )
        PreferenceResetCategory.entries
            .filter { it != PreferenceResetCategory.PLAYBACK && it != PreferenceResetCategory.AUDIO }
            .forEach { category ->
                assertEquals(
                    emptyList<Preferences.Key<*>>(),
                    store.resetKeysFor(category),
                    "$category unexpectedly non-empty",
                )
            }
    }

    @Test
    fun `searchEntries is empty - the search faces stay the feature-side residuals`() {
        // No row declares a search face (the audio screen's row groups own
        // the domain's catalog rows), so the derived list is empty by
        // construction — and no entry id is declared twice (trivially true,
        // pinned so a future search-bearing row cannot land without this test
        // noticing the shape change).
        val entries = AudioPreferenceSpecs.searchEntries
        assertEquals(AudioPreferenceSpecs.all.mapNotNull { it.search }, entries)
        assertEquals(entries.size, entries.map { it.id }.toSet().size, "a search id is declared twice")
    }

    @Test
    fun `every slice default equals its spec default`() {
        // The single-default guard: each [AudioSlice] property's default
        // reads its row's `.default`, so the value below IS the row's — if a
        // future edit restates a literal here (or on the slice) and the two
        // drift, this fails.
        val slice = AudioSlice()
        assertEquals(AudioPreferenceSpecs.AUDIO_DEFAULT_SPEED.default, slice.audioDefaultSpeed)
        assertEquals(AudioPreferenceSpecs.AUDIO_NIGHT_MODE_VOLUME.default, slice.audioNightModeVolume)
        assertEquals(AudioPreferenceSpecs.AUDIO_NIGHT_MODE_GAIN.default, slice.audioNightModeGain)
        assertEquals(AudioPreferenceSpecs.AUDIO_SKIP_PREVIOUS_THRESHOLD_MS.default, slice.audioSkipPreviousThresholdMs)
        assertEquals(AudioPreferenceSpecs.AUDIO_AUTOPLAY_NEXT.default, slice.audioAutoplayNext)
        assertEquals(AudioPreferenceSpecs.AUDIO_PRELOAD_BUFFER_SIZE.default, slice.audioPreloadBufferSize)
        assertEquals(AudioPreferenceSpecs.AUDIO_NORMALIZATION_MODE.default, slice.audioNormalizationMode)
        assertEquals(AudioPreferenceSpecs.AUDIO_NORMALIZATION_ENABLED.default, slice.audioNormalizationEnabled)
        assertEquals(AudioPreferenceSpecs.REPLAYGAIN_PRE_AMP_DB.default, slice.replayGainPreAmpDb)
        assertEquals(AudioPreferenceSpecs.CHANNEL_MIX_MODE.default, slice.channelMixMode)
        assertEquals(AudioPreferenceSpecs.CHANNEL_MIX_ENABLED.default, slice.channelMixEnabled)
        assertEquals(AudioPreferenceSpecs.AUDIO_GAPLESS_ENABLED.default, slice.audioGaplessEnabled)
        assertEquals(AudioPreferenceSpecs.AUDIO_CROSSFADE_DURATION_MS.default, slice.audioCrossfadeDurationMs)
        assertEquals(AudioPreferenceSpecs.AUDIO_DELAY_MS.default, slice.audioDelayMs)
        assertEquals(AudioPreferenceSpecs.AUDIO_LYRICS_VISIBLE.default, slice.audioLyricsVisible)
        assertEquals(AudioPreferenceSpecs.AUDIO_VISUALIZER_ENABLED.default, slice.audioVisualizerEnabled)
        assertEquals(AudioPreferenceSpecs.SLEEP_TIMER_DURATION_MS.default, slice.sleepTimerDurationMs)
        assertEquals(AudioPreferenceSpecs.SLEEP_TIMER_END_OF_EPISODE.default, slice.sleepTimerEndOfEpisode)
        // Totality: the 18 pinned properties are ALL the slice's fields (18
        // rows, every row slice-bound). A field added without a spec-default
        // pin above (and without bumping this count) fails here. Statics (the
        // @Serializable Companion, Compose's $stable) don't count.
        val instanceFields = AudioSlice()::class.java.declaredFields
            .count { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertEquals(18, instanceFields)
    }

    /** Writes [spec]'s default through its derived write (the default IS the row's T). */
    @Suppress("UNCHECKED_CAST")
    private fun PreferenceSpec<*>.writeDefault(prefs: MutablePreferences) {
        (this as PreferenceSpec<Any>).writeTo(prefs, default)
    }
}
