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
 * Guards the derivation integrity for the playback domain: the
 * [PlaybackPreferenceSpecs] rows are the single declaration of every key's
 * wire name / storage type / reset category, and `PlaybackStore` derives its
 * `Keys` members, reset lists (and its read/restore machinery) from them — so
 * a wire-name, storage-type or reset-category drift would silently change
 * what is read from disk or wiped on reset. These tests pin the derivation to
 * the exact legacy wire strings; the
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
        "hdr_format_preference" to String::class,
        "force_transcode_above_height" to Int::class,
        "preferred_transcode_video_codec" to String::class,
        "preferred_transcode_audio_codec" to String::class,
        "force_direct_play" to Boolean::class,
        "playback_mode" to String::class,
        "decoder_mode" to String::class,
        "audio_passthrough" to Boolean::class,
        "audio_passthrough_codecs" to String::class,
        "max_audio_channels" to String::class,
        "downmix_boost_db" to Float::class,
        "spatial_audio_enabled" to Boolean::class,
        "spatial_audio_only_headphones" to Boolean::class,
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
        "session_restore_mode" to String::class,
        "session_restore_dismissed" to String::class,
        "offer_delete_after_playback_movies" to Boolean::class,
        "offer_delete_after_playback_episodes" to Boolean::class,
        "custom_device_profile_json" to String::class,
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
        // The exact keys the hand-written pre-spec reset list carried plus the
        // new rows (force_direct_play included — the legacy surface still
        // resets with its category; live_stream_option and the subtitle/sync
        // toggles are other categories').
        assertEquals(
            listOf(
                "preferred_player",
                "preferred_external_player",
                "streaming_quality",
                "cellular_streaming_quality",
                "hdr_format_preference",
                "force_transcode_above_height",
                "preferred_transcode_video_codec",
                "preferred_transcode_audio_codec",
                "force_direct_play",
                "playback_mode",
                "offline_playback_preference",
                "decoder_mode",
                "audio_passthrough",
                "audio_passthrough_codecs",
                "max_audio_channels",
                "downmix_boost_db",
                "spatial_audio_enabled",
                "spatial_audio_only_headphones",
                "frame_rate_matching",
                "refresh_rate_mode",
                "keep_screen_on_during_video",
                "pause_on_audio_focus_loss",
                "duck_on_transient_focus_loss",
                "auto_play_countdown_sec",
                "background_video_audio_enabled",
                "auto_enter_pip",
                "session_restore_mode",
                "session_restore_dismissed",
                "offer_delete_after_playback_movies",
                "offer_delete_after_playback_episodes",
                "custom_device_profile_json",
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

    @Test
    fun `searchEntries is exactly the searchable rows' entries plus the standalone codec entries`() {
        // 1:1 totality: the catalog is the rows' declared search specs (in `all`
        // order) plus the five hand-only per-codec passthrough catalog facts —
        // nothing re-listed, nothing dropped, nothing hand-appended beyond the
        // standalones. Order is pinned as declaration order (the settings
        // feature matches entries to rows BY ID and owns the catalog order).
        val standaloneIds = listOf(
            "passthrough_codec_ac3",
            "passthrough_codec_eac3",
            "passthrough_codec_dts",
            "passthrough_codec_dtshd",
            "passthrough_codec_truehd",
        )
        val entries = PlaybackPreferenceSpecs.searchEntries
        assertEquals(
            PlaybackPreferenceSpecs.all.mapNotNull { it.search }.map { it.id } + standaloneIds,
            entries.map { it.id },
        )
        // …and no entry id is declared twice.
        assertEquals(entries.size, entries.map { it.id }.toSet().size, "a search id is declared twice")
    }

    @Test
    fun `every slice default equals its spec default`() {
        // The single-default guard: each [PlaybackSlice] property's default
        // reads its row's `.default`, so the value below IS the row's — if a
        // future edit restates a literal here (or on the slice) and the two
        // drift, this fails. PREFERRED_PLAYER's row default is deliberately the
        // pre-clamp value (the row's read degrades to the platform default on
        // absence; the declared default stays platform-neutral), which is what
        // the slice always served.
        val slice = PlaybackSlice()
        assertEquals(PlaybackPreferenceSpecs.PREFERRED_PLAYER.default, slice.preferredPlayer)
        assertEquals(PlaybackPreferenceSpecs.PREFERRED_EXTERNAL_PLAYER.default, slice.preferredExternalPlayer)
        assertEquals(PlaybackPreferenceSpecs.STREAMING_QUALITY.default, slice.streamingQuality)
        assertEquals(PlaybackPreferenceSpecs.CELLULAR_STREAMING_QUALITY.default, slice.cellularStreamingQuality)
        assertEquals(PlaybackPreferenceSpecs.HDR_FORMAT_PREFERENCE.default, slice.hdrFormatPreference)
        assertEquals(PlaybackPreferenceSpecs.FORCE_TRANSCODE_ABOVE_HEIGHT.default, slice.forceTranscodeAboveHeight)
        assertEquals(PlaybackPreferenceSpecs.PREFERRED_TRANSCODE_VIDEO_CODEC.default, slice.preferredTranscodeVideoCodec)
        assertEquals(PlaybackPreferenceSpecs.PREFERRED_TRANSCODE_AUDIO_CODEC.default, slice.preferredTranscodeAudioCodec)
        assertEquals(PlaybackPreferenceSpecs.PLAYBACK_MODE.default, slice.playbackMode)
        assertEquals(PlaybackPreferenceSpecs.LIVE_STREAM_OPTION.default, slice.liveStreamOption)
        assertEquals(PlaybackPreferenceSpecs.OFFLINE_PLAYBACK_PREFERENCE.default, slice.offlinePlaybackPreference)
        assertEquals(PlaybackPreferenceSpecs.DECODER_MODE.default, slice.decoderMode)
        assertEquals(PlaybackPreferenceSpecs.AUDIO_PASSTHROUGH.default, slice.audioPassthrough)
        assertEquals(PlaybackPreferenceSpecs.AUDIO_PASSTHROUGH_CODECS.default, slice.audioPassthroughCodecs)
        assertEquals(PlaybackPreferenceSpecs.MAX_AUDIO_CHANNELS.default, slice.maxAudioChannels)
        assertEquals(PlaybackPreferenceSpecs.DOWNMIX_BOOST_DB.default, slice.downmixBoostDb)
        assertEquals(PlaybackPreferenceSpecs.SPATIAL_AUDIO_ENABLED.default, slice.spatialAudioEnabled)
        assertEquals(PlaybackPreferenceSpecs.SPATIAL_AUDIO_ONLY_HEADPHONES.default, slice.spatialAudioOnlyHeadphones)
        assertEquals(PlaybackPreferenceSpecs.FRAME_RATE_MATCHING.default, slice.frameRateMatching)
        assertEquals(PlaybackPreferenceSpecs.REFRESH_RATE_MODE.default, slice.refreshRateMode)
        assertEquals(PlaybackPreferenceSpecs.KEEP_SCREEN_ON_DURING_VIDEO.default, slice.keepScreenOnDuringVideo)
        assertEquals(PlaybackPreferenceSpecs.PAUSE_ON_AUDIO_FOCUS_LOSS.default, slice.pauseOnAudioFocusLoss)
        assertEquals(PlaybackPreferenceSpecs.DUCK_ON_TRANSIENT_FOCUS_LOSS.default, slice.duckOnTransientFocusLoss)
        assertEquals(PlaybackPreferenceSpecs.AUTO_PLAY_COUNTDOWN_SEC.default, slice.autoPlayCountdownSec)
        assertEquals(PlaybackPreferenceSpecs.BACKGROUND_VIDEO_AUDIO_ENABLED.default, slice.backgroundVideoAudioEnabled)
        assertEquals(PlaybackPreferenceSpecs.AUTO_ENTER_PIP.default, slice.autoEnterPip)
        assertEquals(PlaybackPreferenceSpecs.SESSION_RESTORE_MODE.default, slice.sessionRestoreMode)
        assertEquals(PlaybackPreferenceSpecs.OFFER_DELETE_AFTER_PLAYBACK_MOVIES.default, slice.offerDeleteAfterPlaybackMovies)
        assertEquals(PlaybackPreferenceSpecs.OFFER_DELETE_AFTER_PLAYBACK_EPISODES.default, slice.offerDeleteAfterPlaybackEpisodes)
        assertEquals(PlaybackPreferenceSpecs.CUSTOM_DEVICE_PROFILE_JSON.default, slice.customDeviceProfileJson)
        assertEquals(PlaybackPreferenceSpecs.PGS_SUBTITLE_DIRECT_PLAY.default, slice.pgsSubtitleDirectPlay)
        assertEquals(PlaybackPreferenceSpecs.USER_DATA_SYNC_ENABLED.default, slice.userDataSyncEnabled)
        assertEquals(PlaybackPreferenceSpecs.ANDROID_TV_WATCH_NEXT_ENABLED.default, slice.androidTvWatchNextEnabled)
        // Totality: the 33 pinned properties are ALL the slice's fields (34
        // rows - the legacy-only force_direct_play with no slice property). A
        // field added without a spec-default pin above (and without bumping
        // this count) fails here. Statics (the @Serializable Companion,
        // Compose's $stable) don't count.
        val instanceFields = PlaybackSlice()::class.java.declaredFields
            .count { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertEquals(33, instanceFields)
    }

    /** Writes [spec]'s default through its derived write (the default IS the row's T). */
    @Suppress("UNCHECKED_CAST")
    private fun PreferenceSpec<*>.writeDefault(prefs: MutablePreferences) {
        (this as PreferenceSpec<Any>).writeTo(prefs, default)
    }
}
