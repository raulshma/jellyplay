package com.raulshma.jellyplay.core.datastore.videoplayer

import androidx.datastore.preferences.core.MutablePreferences
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
import kotlin.test.assertTrue

/**
 * Guards the derivation integrity for the in-player video domain: the
 * [VideoPlayerPreferenceSpecs] row groups are the single registration of every
 * key's wire name / storage type / default / reset category / search entry,
 * and `VideoPlayerStore` derives its `Keys` members, reset lists (and its
 * read/restore machinery) from them, while [VideoPlayerSlice] derives its
 * defaults and [VideoPlayerPreferenceSpecs.searchEntries] derives from the
 * rows' declared search specs — so a wire-name, storage-type, reset-category,
 * slice-default or search-registration drift would silently change what is
 * read from disk, wiped on reset, served to a fresh slice, or surfaced in
 * settings search. These tests pin the derivation to the exact legacy strings
 * the pre-spec hand-written store carried; the behavioral safety net
 * (defaults, round-trips, legacy migrations, the coercions) stays in
 * [VideoPlayerStoreTest].
 */
class VideoPlayerSpecDerivationTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /**
     * The legacy wire name → stored value type each slot must keep. DataStore
     * dispatches its file serializer on the stored VALUE type (its Key is one
     * generic class), so this table is what makes a stored slot readable
     * through the typed [VideoPlayerStore.Keys] members — the exact
     * expectations the hand-written pre-spec store encoded at every read/write
     * site.
     */
    private val expectedStoredType: Map<String, KClass<*>> = mapOf(
        "video_seek_duration_ms" to Long::class,
        "video_controls_timeout_ms" to Long::class,
        "video_hide_osd_on_pause" to Boolean::class,
        "video_resume_on_headset_plug" to Boolean::class,
        "video_default_orientation" to String::class,
        "video_default_aspect_ratio" to String::class,
        "video_gesture_mode" to String::class,
        "video_gestures_enabled" to Boolean::class,
        "video_gesture_indicator_side" to String::class,
        "video_double_tap_hold_seek_enabled" to Boolean::class,
        "input_bindings" to String::class,
        "video_pass_out_protection_hours" to Int::class,
        "video_skip_back_on_resume_ms" to Long::class,
        "video_hold_speed_enabled" to Boolean::class,
        "video_hold_speed_multiplier" to Float::class,
        "video_default_speed" to Float::class,
        "video_autoplay_next" to Boolean::class,
        "still_watching_mode" to String::class,
        "still_watching_episode_threshold" to Int::class,
        "trailer_autoplay" to Boolean::class,
        "cinema_mode_enabled" to Boolean::class,
        "video_swipe_seek_max_ms" to Long::class,
        "video_remember_brightness" to Boolean::class,
        "video_brightness_level" to Float::class,
        "video_remember_muted" to Boolean::class,
        "video_muted" to Boolean::class,
        "video_auto_skip_intro" to Boolean::class,
        "video_auto_skip_outro" to Boolean::class,
        "trickplay_enabled" to Boolean::class,
        "trickplay_on_seek_gesture" to Boolean::class,
        "video_episode_browser_enabled" to Boolean::class,
        "video_show_playback_metadata" to Boolean::class,
        "video_preload_buffer_size" to String::class,
        "video_cache_size_mb" to Int::class,
        "show_clock_in_player" to Boolean::class,
        "show_time_remaining" to Boolean::class,
        "tv_zoom_mode_percent" to Float::class,
        "incognito_mode_enabled" to Boolean::class,
        "segment_behaviors" to String::class,
        "segment_skip_delays_ms" to String::class,
        "skip_intro_enabled" to String::class,
        "skip_outro_enabled" to String::class,
        "auto_skip_intro" to String::class,
        "auto_skip_outro" to String::class,
        "skip_segments_on_seek" to Boolean::class,
    )

    /**
     * The four legacy string-keyed skip booleans are Stage A declarations
     * (no derived write — their reads route through the `segment_behaviors`
     * row's lambda), so they are the only rows excluded from the write-through
     * storage table below.
     */
    private val legacyStringRows = setOf(
        "skip_intro_enabled",
        "skip_outro_enabled",
        "auto_skip_intro",
        "auto_skip_outro",
    )

    @Test
    fun `every spec row's derived key matches the store's declared Keys`() {
        val declared = reflectKeys(VideoPlayerStore.Keys).associateBy { it.name }
        val rows = VideoPlayerPreferenceSpecs.all.associateBy { it.keyName }

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
        val writeable = VideoPlayerPreferenceSpecs.all.filter { it.encoding != null }
        assertEquals(
            expectedStoredType.keys - legacyStringRows,
            writeable.map { it.keyName }.toSet(),
            "the rows excluded from the write-through table must be exactly the legacy string rows",
        )

        // Write each row's default through its derived write and assert the
        // stored value's runtime type matches the pinned legacy storage table.
        val prefs = emptyPreferences().toMutablePreferences()
        writeable.forEach { spec -> spec.writeDefault(prefs) }
        val storedByName = prefs.asMap().entries.associateBy { it.key.name }

        assertEquals(expectedStoredType.keys - legacyStringRows, storedByName.keys)
        (expectedStoredType.keys - legacyStringRows).forEach { name ->
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
        val names = VideoPlayerPreferenceSpecs.all.map { it.keyName }
        assertEquals(names.size, names.toSet().size, "a row is declared twice across the groups")
        assertEquals(expectedStoredType.keys, names.toSet())
    }

    @Test
    fun `searchEntries is exactly the searchable rows' entries plus the standalone segment entries`() {
        // 1:1 totality: the catalog is the rows' declared search specs (in `all`
        // order) plus the six hand-only per-segment-type catalog facts — nothing
        // re-listed, nothing dropped, nothing hand-appended beyond the
        // standalones. Order is pinned as declaration order (the settings
        // feature matches entries to rows BY ID and owns the catalog order).
        val standaloneIds = listOf(
            "media_segment_intro",
            "media_segment_outro",
            "media_segment_preview",
            "media_segment_recap",
            "media_segment_commercial",
            "media_segment_unknown",
        )
        val entries = VideoPlayerPreferenceSpecs.searchEntries
        assertEquals(
            VideoPlayerPreferenceSpecs.all.mapNotNull { it.search }.map { it.id } + standaloneIds,
            entries.map { it.id },
        )
        // …and no entry id is declared twice.
        assertEquals(entries.size, entries.map { it.id }.toSet().size, "a search id is declared twice")
    }

    @Test
    fun `every slice default equals its spec default`() {
        // The single-default ratchet: each [VideoPlayerSlice] property's
        // default reads its row's `.default`, so the value below IS the row's —
        // if a future edit restates a literal here (or on the slice) and the
        // two drift, this fails.
        val slice = VideoPlayerSlice()
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_SEEK_DURATION_MS.default, slice.videoSeekDurationMs)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_CONTROLS_TIMEOUT_MS.default, slice.videoControlsTimeoutMs)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_HIDE_OSD_ON_PAUSE.default, slice.videoHideOsdOnPause)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_RESUME_ON_HEADSET_PLUG.default, slice.videoResumeOnHeadsetPlug)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_DEFAULT_ORIENTATION.default, slice.videoDefaultOrientation)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_DEFAULT_ASPECT_RATIO.default, slice.videoDefaultAspectRatio)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_GESTURE_MODE.default, slice.videoGestureMode)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_DOUBLE_TAP_HOLD_SEEK_ENABLED.default, slice.videoDoubleTapHoldSeekEnabled)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_INPUT_BINDINGS.default, slice.videoInputBindings)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_PASS_OUT_PROTECTION_HOURS.default, slice.videoPassOutProtectionHours)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_SKIP_BACK_ON_RESUME_MS.default, slice.videoSkipBackOnResumeMs)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_HOLD_SPEED_ENABLED.default, slice.videoHoldSpeedEnabled)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_HOLD_SPEED_MULTIPLIER.default, slice.videoHoldSpeedMultiplier)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_DEFAULT_SPEED.default, slice.videoDefaultSpeed)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_AUTOPLAY_NEXT.default, slice.videoAutoplayNext)
        assertEquals(VideoPlayerPreferenceSpecs.STILL_WATCHING_MODE.default, slice.stillWatchingMode)
        assertEquals(VideoPlayerPreferenceSpecs.STILL_WATCHING_EPISODE_THRESHOLD.default, slice.stillWatchingEpisodeThreshold)
        assertEquals(VideoPlayerPreferenceSpecs.TRAILER_AUTOPLAY.default, slice.trailerAutoplay)
        assertEquals(VideoPlayerPreferenceSpecs.CINEMA_MODE_ENABLED.default, slice.cinemaModeEnabled)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_SWIPE_SEEK_MAX_MS.default, slice.videoSwipeSeekMaxMs)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_REMEMBER_BRIGHTNESS.default, slice.videoRememberBrightness)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_BRIGHTNESS_LEVEL.default, slice.videoBrightnessLevel)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_AUTO_SKIP_INTRO.default, slice.videoAutoSkipIntro)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_AUTO_SKIP_OUTRO.default, slice.videoAutoSkipOutro)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_REMEMBER_MUTED.default, slice.videoRememberMuted)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_MUTED.default, slice.videoMuted)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_GESTURE_INDICATOR_SIDE.default, slice.videoGestureIndicatorSide)
        assertEquals(VideoPlayerPreferenceSpecs.TRICKPLAY_ENABLED.default, slice.trickplayEnabled)
        assertEquals(VideoPlayerPreferenceSpecs.TRICKPLAY_ON_SEEK_GESTURE.default, slice.trickplayOnSeekGesture)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_EPISODE_BROWSER_ENABLED.default, slice.videoEpisodeBrowserEnabled)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_SHOW_PLAYBACK_METADATA.default, slice.videoShowPlaybackMetadata)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_PRELOAD_BUFFER_SIZE.default, slice.videoPreloadBufferSize)
        assertEquals(VideoPlayerPreferenceSpecs.VIDEO_CACHE_SIZE_MB.default, slice.videoCacheSizeMb)
        assertEquals(VideoPlayerPreferenceSpecs.SHOW_CLOCK_IN_PLAYER.default, slice.showClockInPlayer)
        assertEquals(VideoPlayerPreferenceSpecs.SHOW_TIME_REMAINING.default, slice.showTimeRemaining)
        assertEquals(VideoPlayerPreferenceSpecs.TV_ZOOM_MODE_PERCENT.default, slice.tvZoomModePercent)
        assertEquals(VideoPlayerPreferenceSpecs.INCOGNITO_MODE_ENABLED.default, slice.incognitoModeEnabled)
        assertEquals(VideoPlayerPreferenceSpecs.SEGMENT_BEHAVIORS.default, slice.segmentBehaviors)
        assertEquals(VideoPlayerPreferenceSpecs.SEGMENT_SKIP_DELAYS_MS.default, slice.segmentSkipDelaysMs)
        assertEquals(VideoPlayerPreferenceSpecs.SKIP_SEGMENTS_ON_SEEK.default, slice.skipSegmentsOnSeek)
        // Totality: every slice field is covered above — the 40 pinned
        // properties are ALL the slice's fields (45 rows - 5 legacy-only rows
        // with no slice property). A field added without a spec-default pin
        // above (and without bumping this count) fails here. Statics
        // (the @Serializable Companion, Compose's $stable) don't count.
        val instanceFields = VideoPlayerSlice()::class.java.declaredFields
            .count { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertEquals(40, instanceFields)
    }

    @Test
    fun `an empty snapshot reads exactly the default slice`() {
        // The two faces of the single default agree: what an absent key reads
        // (the row encodings, including the gesture-mode legacy fallback and
        // the input-bindings legacy-config derivation on their empty-snapshot
        // paths) is exactly what a fresh in-memory slice serves.
        val store = VideoPlayerStore(TestDataStoreProvider.get(), scope)
        assertEquals(VideoPlayerSlice(), store.read(emptyPreferences()))
    }

    @Test
    fun `every in-player key resets under PLAYBACK and no other category`() {
        val store = VideoPlayerStore(TestDataStoreProvider.get(), scope)
        val playbackKeys = store.resetKeysFor(PreferenceResetCategory.PLAYBACK).map { it.name }
        assertEquals(expectedStoredType.keys, playbackKeys.toSet())
        PreferenceResetCategory.entries
            .filter { it != PreferenceResetCategory.PLAYBACK }
            .forEach { category ->
                assertTrue(store.resetKeysFor(category).isEmpty(), "$category unexpectedly non-empty")
            }
    }

    /** Writes [spec]'s default through its derived write (the default IS the row's T). */
    @Suppress("UNCHECKED_CAST")
    private fun PreferenceSpec<*>.writeDefault(prefs: MutablePreferences) {
        (this as PreferenceSpec<Any>).writeTo(prefs, default)
    }
}
