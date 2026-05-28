package com.raulshma.jellyplay.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.DreamImageCategory
import com.raulshma.jellyplay.core.model.DreamTransitionStyle
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ACTIVE_SERVER_ID = stringPreferencesKey("active_server_id")
        val ACTIVE_USER_ID = stringPreferencesKey("active_user_id")
        val PREFERRED_PLAYER = stringPreferencesKey("preferred_player")
        val PREFERRED_SUBTITLE_LANG = stringPreferencesKey("preferred_subtitle_lang")
        val PREFERRED_AUDIO_LANG = stringPreferencesKey("preferred_audio_lang")
        val MEDIA_STREAM_SELECTIONS = stringPreferencesKey("media_stream_selections")
        val DYNAMIC_THEMING = stringPreferencesKey("dynamic_theming")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val OLED_MODE = stringPreferencesKey("oled_mode")
        val SUBTITLE_STYLE = stringPreferencesKey("subtitle_style")
        val STREAMING_QUALITY = stringPreferencesKey("streaming_quality")
        val MAX_CACHE_SIZE_MB = stringPreferencesKey("max_cache_size_mb")
        val AUTO_DELETE_CACHE = stringPreferencesKey("auto_delete_cache")
        val PIN_LOCK_ENABLED = stringPreferencesKey("pin_lock_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val CONTINUE_WATCHING = stringPreferencesKey("continue_watching")
        val KIDS_MODE_ENABLED = stringPreferencesKey("kids_mode_enabled")
        val KIDS_MODE_MAX_RATING = stringPreferencesKey("kids_mode_max_rating")
        val DIALOGUE_BOOST_ENABLED = stringPreferencesKey("dialogue_boost_enabled")
        val DIALOGUE_BOOST_STRENGTH = stringPreferencesKey("dialogue_boost_strength")
        val EQUALIZER_ENABLED = stringPreferencesKey("equalizer_enabled")
        val EQUALIZER_SETTINGS = stringPreferencesKey("equalizer_settings")
        val AUDIO_DELAY_MS = stringPreferencesKey("audio_delay_ms")
        val DECODER_MODE = stringPreferencesKey("decoder_mode")
        val AUDIO_PASSTHROUGH = stringPreferencesKey("audio_passthrough")
        val FRAME_RATE_MATCHING = stringPreferencesKey("frame_rate_matching")
        val NIGHT_MODE_ENABLED = stringPreferencesKey("night_mode_enabled")
        val NIGHT_MODE_STRENGTH = stringPreferencesKey("night_mode_strength")
        val HOME_MODE = stringPreferencesKey("home_mode")
        val VIDEO_SEEK_DURATION_MS = stringPreferencesKey("video_seek_duration_ms")
        val VIDEO_DEFAULT_ORIENTATION = stringPreferencesKey("video_default_orientation")
        val VIDEO_CONTROLS_TIMEOUT_MS = stringPreferencesKey("video_controls_timeout_ms")
        val VIDEO_GESTURES_ENABLED = stringPreferencesKey("video_gestures_enabled")
        val VIDEO_DEFAULT_SPEED = stringPreferencesKey("video_default_speed")
        val VIDEO_DEFAULT_ASPECT_RATIO = stringPreferencesKey("video_default_aspect_ratio")
        val VIDEO_AUTOPLAY_NEXT = stringPreferencesKey("video_autoplay_next")
        val VIDEO_SWIPE_SEEK_MAX_MS = stringPreferencesKey("video_swipe_seek_max_ms")
        val VIDEO_REMEMBER_BRIGHTNESS = stringPreferencesKey("video_remember_brightness")
        val VIDEO_BRIGHTNESS_LEVEL = stringPreferencesKey("video_brightness_level")
        val AUDIO_DEFAULT_SPEED = stringPreferencesKey("audio_default_speed")
        val AUDIO_NIGHT_MODE_VOLUME = stringPreferencesKey("audio_night_mode_volume")
        val AUDIO_NIGHT_MODE_GAIN = stringPreferencesKey("audio_night_mode_gain")
        val AUDIO_SKIP_PREVIOUS_THRESHOLD_MS = stringPreferencesKey("audio_skip_previous_threshold_ms")
        val AUDIO_AUTOPLAY_NEXT = stringPreferencesKey("audio_autoplay_next")
        val TRICKPLAY_ENABLED = stringPreferencesKey("trickplay_enabled")
        val TRICKPLAY_ON_SEEK_GESTURE = stringPreferencesKey("trickplay_on_seek_gesture")
        val SKIP_INTRO_ENABLED = stringPreferencesKey("skip_intro_enabled")
        val SKIP_OUTRO_ENABLED = stringPreferencesKey("skip_outro_enabled")
        val AUTO_SKIP_INTRO = stringPreferencesKey("auto_skip_intro")
        val AUTO_SKIP_OUTRO = stringPreferencesKey("auto_skip_outro")
        val SEGMENT_BEHAVIORS = stringPreferencesKey("segment_behaviors")
        val VIDEO_EPISODE_BROWSER_ENABLED = stringPreferencesKey("video_episode_browser_enabled")
        val VIDEO_PRELOAD_BUFFER_SIZE = stringPreferencesKey("video_preload_buffer_size")
        val AUDIO_PRELOAD_BUFFER_SIZE = stringPreferencesKey("audio_preload_buffer_size")
        val AUDIO_NORMALIZATION_MODE = stringPreferencesKey("audio_normalization_mode")
        val AUDIO_NORMALIZATION_ENABLED = stringPreferencesKey("audio_normalization_enabled")
        val REPLAYGAIN_PRE_AMP_DB = stringPreferencesKey("replaygain_pre_amp_db")
        val CHANNEL_MIX_MODE = stringPreferencesKey("channel_mix_mode")
        val CHANNEL_MIX_ENABLED = stringPreferencesKey("channel_mix_enabled")
        val AUDIO_GAPLESS_ENABLED = stringPreferencesKey("audio_gapless_enabled")
        val AUDIO_CROSSFADE_DURATION_MS = stringPreferencesKey("audio_crossfade_duration_ms")
        val SLEEP_TIMER_DURATION_MS = stringPreferencesKey("sleep_timer_duration_ms")
        val SLEEP_TIMER_END_OF_EPISODE = stringPreferencesKey("sleep_timer_end_of_episode")
        val DREAM_IMAGE_CATEGORIES = stringPreferencesKey("dream_image_categories")
        val DREAM_SLIDESHOW_INTERVAL_MS = stringPreferencesKey("dream_slideshow_interval_ms")
        val DREAM_KEN_BURNS_ENABLED = stringPreferencesKey("dream_ken_burns_enabled")
        val DREAM_TRANSITION_STYLE = stringPreferencesKey("dream_transition_style")
        val DREAM_SHOW_TITLE = stringPreferencesKey("dream_show_title")
        val EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset")
        val BASS_BOOST_ENABLED = stringPreferencesKey("bass_boost_enabled")
        val BASS_BOOST_STRENGTH = stringPreferencesKey("bass_boost_strength")
        val VIRTUALIZER_ENABLED = stringPreferencesKey("virtualizer_enabled")
        val VIRTUALIZER_STRENGTH = stringPreferencesKey("virtualizer_strength")
        val REVERB_PRESET = stringPreferencesKey("reverb_preset")
        val LR_BALANCE = stringPreferencesKey("lr_balance")
        val AUTO_EQ_BY_GENRE = stringPreferencesKey("auto_eq_by_genre")
        val PITCH_SEMITONES = stringPreferencesKey("pitch_semitones")
        val DOWNLOAD_CONNECTIONS = stringPreferencesKey("download_connections")
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun readMediaStreamSelections(prefs: Preferences): Map<String, MediaStreamSelection> {
        val raw = prefs[Keys.MEDIA_STREAM_SELECTIONS] ?: return emptyMap()
        return try {
            json.decodeFromString<Map<String, MediaStreamSelection>>(raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun readSegmentBehaviors(prefs: Preferences): Map<MediaSegmentType, SegmentBehavior> {
        val raw = prefs[Keys.SEGMENT_BEHAVIORS]
        if (raw != null) {
            return try {
                val stored = json.decodeFromString<Map<String, String>>(raw)
                stored.mapNotNull { (typeStr, behaviorStr) ->
                    try {
                        MediaSegmentType.valueOf(typeStr) to SegmentBehavior.valueOf(behaviorStr)
                    } catch (_: Exception) { null }
                }.toMap()
            } catch (_: Exception) { emptyMap() }
        }

        val migrated = mutableMapOf<MediaSegmentType, SegmentBehavior>()
        val skipIntro = prefs[Keys.SKIP_INTRO_ENABLED]?.toBoolean() ?: true
        val skipOutro = prefs[Keys.SKIP_OUTRO_ENABLED]?.toBoolean() ?: true
        val autoIntro = prefs[Keys.AUTO_SKIP_INTRO]?.toBoolean() ?: false
        val autoOutro = prefs[Keys.AUTO_SKIP_OUTRO]?.toBoolean() ?: false
        migrated[MediaSegmentType.INTRO] = when {
            autoIntro -> SegmentBehavior.AUTO_SKIP
            skipIntro -> SegmentBehavior.SHOW_BUTTON
            else -> SegmentBehavior.IGNORE
        }
        migrated[MediaSegmentType.OUTRO] = when {
            autoOutro -> SegmentBehavior.AUTO_SKIP
            skipOutro -> SegmentBehavior.SHOW_BUTTON
            else -> SegmentBehavior.IGNORE
        }
        return SegmentBehavior.DEFAULT_BEHAVIORS + migrated
    }

    private suspend fun writeMediaStreamSelections(
        itemId: String,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ) {
        context.dataStore.edit { prefs ->
            val current = readMediaStreamSelections(prefs).toMutableMap()
            current[itemId] = MediaStreamSelection(
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
            )
            if (current.size > 100) {
                val excess = current.size - 100
                current.keys.take(excess).forEach { current.remove(it) }
            }
            prefs[Keys.MEDIA_STREAM_SELECTIONS] = json.encodeToString(current)
        }
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val subtitleStyle = try {
            prefs[Keys.SUBTITLE_STYLE]?.let { json.decodeFromString<SubtitleStyle>(it) }
        } catch (_: Exception) { null }

        val streamingQuality = try {
            StreamingQuality.valueOf(prefs[Keys.STREAMING_QUALITY] ?: StreamingQuality.AUTO.name)
        } catch (_: Exception) { StreamingQuality.AUTO }

        val equalizerSettings = try {
            prefs[Keys.EQUALIZER_SETTINGS]?.let { json.decodeFromString<EqualizerSettings>(it) }
        } catch (_: Exception) { null }

        UserPreferences(
            preferredPlayer = try {
                PlayerType.fromStoredName(prefs[Keys.PREFERRED_PLAYER] ?: PlayerType.EXO_PLAYER.name)
            } catch (_: Exception) { PlayerType.EXO_PLAYER },
            preferredSubtitleLanguage = prefs[Keys.PREFERRED_SUBTITLE_LANG],
            preferredAudioLanguage = prefs[Keys.PREFERRED_AUDIO_LANG],
            mediaStreamSelections = readMediaStreamSelections(prefs),
            dynamicTheming = prefs[Keys.DYNAMIC_THEMING]?.toBoolean() ?: true,
            themeMode = try {
                ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
            } catch (_: Exception) { ThemeMode.SYSTEM },
            oledMode = prefs[Keys.OLED_MODE]?.toBoolean() ?: false,
            subtitleStyle = subtitleStyle ?: SubtitleStyle(),
            streamingQuality = streamingQuality,
            maxCacheSizeMb = prefs[Keys.MAX_CACHE_SIZE_MB]?.toIntOrNull() ?: 0,
            autoDeleteCache = prefs[Keys.AUTO_DELETE_CACHE]?.toBoolean() ?: true,
            pinLockEnabled = prefs[Keys.PIN_LOCK_ENABLED]?.toBoolean() ?: false,
            pinHash = prefs[Keys.PIN_HASH],
            kidsModeEnabled = prefs[Keys.KIDS_MODE_ENABLED]?.toBoolean() ?: false,
            kidsModeMaxRating = prefs[Keys.KIDS_MODE_MAX_RATING] ?: "PG",
            dialogueBoostEnabled = prefs[Keys.DIALOGUE_BOOST_ENABLED]?.toBoolean() ?: false,
            dialogueBoostStrength = try {
                EffectStrength.valueOf(prefs[Keys.DIALOGUE_BOOST_STRENGTH] ?: EffectStrength.MODERATE.name)
            } catch (_: Exception) { EffectStrength.MODERATE },
            equalizerEnabled = prefs[Keys.EQUALIZER_ENABLED]?.toBoolean() ?: false,
            equalizerSettings = equalizerSettings ?: EqualizerSettings(),
            audioDelayMs = prefs[Keys.AUDIO_DELAY_MS]?.toLongOrNull() ?: 0L,
            decoderMode = try {
                DecoderMode.valueOf(prefs[Keys.DECODER_MODE] ?: DecoderMode.HW_PREFERRED.name)
            } catch (_: Exception) { DecoderMode.HW_PREFERRED },
            audioPassthrough = prefs[Keys.AUDIO_PASSTHROUGH]?.toBoolean() ?: false,
            frameRateMatching = prefs[Keys.FRAME_RATE_MATCHING]?.toBoolean() ?: false,
            nightModeEnabled = prefs[Keys.NIGHT_MODE_ENABLED]?.toBoolean() ?: false,
            nightModeStrength = try {
                EffectStrength.valueOf(prefs[Keys.NIGHT_MODE_STRENGTH] ?: EffectStrength.MODERATE.name)
            } catch (_: Exception) { EffectStrength.MODERATE },
            homeMode = try {
                HomeMode.valueOf(prefs[Keys.HOME_MODE] ?: HomeMode.VIDEO.name)
            } catch (_: Exception) { HomeMode.VIDEO },
            videoSeekDurationMs = prefs[Keys.VIDEO_SEEK_DURATION_MS]?.toLongOrNull() ?: 10_000L,
            videoDefaultOrientation = try {
                OrientationMode.valueOf(prefs[Keys.VIDEO_DEFAULT_ORIENTATION] ?: OrientationMode.SENSOR_LANDSCAPE.name)
            } catch (_: Exception) { OrientationMode.SENSOR_LANDSCAPE },
            videoControlsTimeoutMs = prefs[Keys.VIDEO_CONTROLS_TIMEOUT_MS]?.toLongOrNull() ?: 5_000L,
            videoGesturesEnabled = prefs[Keys.VIDEO_GESTURES_ENABLED]?.toBoolean() ?: true,
            videoDefaultSpeed = prefs[Keys.VIDEO_DEFAULT_SPEED]?.toFloatOrNull() ?: 1.0f,
            videoDefaultAspectRatio = prefs[Keys.VIDEO_DEFAULT_ASPECT_RATIO] ?: "AUTO",
            videoAutoplayNext = prefs[Keys.VIDEO_AUTOPLAY_NEXT]?.toBoolean() ?: false,
            videoSwipeSeekMaxMs = prefs[Keys.VIDEO_SWIPE_SEEK_MAX_MS]?.toLongOrNull() ?: 120_000L,
            videoRememberBrightness = prefs[Keys.VIDEO_REMEMBER_BRIGHTNESS]?.toBoolean() ?: false,
            videoBrightnessLevel = prefs[Keys.VIDEO_BRIGHTNESS_LEVEL]?.toFloatOrNull() ?: 0.5f,
            audioDefaultSpeed = prefs[Keys.AUDIO_DEFAULT_SPEED]?.toFloatOrNull() ?: 1.0f,
            audioNightModeVolume = prefs[Keys.AUDIO_NIGHT_MODE_VOLUME]?.toFloatOrNull() ?: 0.4f,
            audioNightModeGain = prefs[Keys.AUDIO_NIGHT_MODE_GAIN]?.toIntOrNull() ?: 1200,
            audioSkipPreviousThresholdMs = prefs[Keys.AUDIO_SKIP_PREVIOUS_THRESHOLD_MS]?.toLongOrNull() ?: 3_000L,
            audioAutoplayNext = prefs[Keys.AUDIO_AUTOPLAY_NEXT]?.toBoolean() ?: true,
            trickplayEnabled = prefs[Keys.TRICKPLAY_ENABLED]?.toBoolean() ?: true,
            trickplayOnSeekGesture = prefs[Keys.TRICKPLAY_ON_SEEK_GESTURE]?.toBoolean() ?: true,
            segmentBehaviors = readSegmentBehaviors(prefs),
            videoEpisodeBrowserEnabled = prefs[Keys.VIDEO_EPISODE_BROWSER_ENABLED]?.toBoolean() ?: true,
            videoPreloadBufferSize = try {
                PreloadBufferSize.valueOf(prefs[Keys.VIDEO_PRELOAD_BUFFER_SIZE] ?: PreloadBufferSize.MEDIUM.name)
            } catch (_: Exception) { PreloadBufferSize.MEDIUM },
            audioPreloadBufferSize = try {
                PreloadBufferSize.valueOf(prefs[Keys.AUDIO_PRELOAD_BUFFER_SIZE] ?: PreloadBufferSize.MEDIUM.name)
            } catch (_: Exception) { PreloadBufferSize.MEDIUM },
            audioNormalizationMode = try {
                when (val stored = prefs[Keys.AUDIO_NORMALIZATION_MODE] ?: AudioNormalizationMode.NONE.name) {
                    "REPLAYGAIN" -> AudioNormalizationMode.TRACK
                    else -> AudioNormalizationMode.valueOf(stored)
                }
            } catch (_: Exception) { AudioNormalizationMode.NONE },
            audioNormalizationEnabled = prefs[Keys.AUDIO_NORMALIZATION_ENABLED]?.toBoolean() ?: false,
            replayGainPreAmpDb = prefs[Keys.REPLAYGAIN_PRE_AMP_DB]?.toFloatOrNull() ?: 0f,
            channelMixMode = try {
                ChannelMixMode.valueOf(prefs[Keys.CHANNEL_MIX_MODE] ?: ChannelMixMode.AUTO.name)
            } catch (_: Exception) { ChannelMixMode.AUTO },
            channelMixEnabled = prefs[Keys.CHANNEL_MIX_ENABLED]?.toBoolean() ?: false,
            audioGaplessEnabled = prefs[Keys.AUDIO_GAPLESS_ENABLED]?.toBoolean() ?: true,
            audioCrossfadeDurationMs = prefs[Keys.AUDIO_CROSSFADE_DURATION_MS]?.toLongOrNull() ?: 0L,
            sleepTimerDurationMs = prefs[Keys.SLEEP_TIMER_DURATION_MS]?.toLongOrNull() ?: 0L,
            sleepTimerEndOfEpisode = prefs[Keys.SLEEP_TIMER_END_OF_EPISODE]?.toBoolean() ?: false,
            dreamImageCategories = try {
                prefs[Keys.DREAM_IMAGE_CATEGORIES]?.let {
                    json.decodeFromString<Set<DreamImageCategory>>(it)
                } ?: setOf(DreamImageCategory.MOVIES, DreamImageCategory.SERIES)
            } catch (_: Exception) { setOf(DreamImageCategory.MOVIES, DreamImageCategory.SERIES) },
            dreamSlideshowIntervalMs = prefs[Keys.DREAM_SLIDESHOW_INTERVAL_MS]?.toLongOrNull() ?: 15_000L,
            dreamKenBurnsEnabled = prefs[Keys.DREAM_KEN_BURNS_ENABLED]?.toBoolean() ?: true,
            dreamTransitionStyle = try {
                DreamTransitionStyle.valueOf(prefs[Keys.DREAM_TRANSITION_STYLE] ?: DreamTransitionStyle.CROSSFADE.name)
            } catch (_: Exception) { DreamTransitionStyle.CROSSFADE },
            dreamShowTitle = prefs[Keys.DREAM_SHOW_TITLE]?.toBoolean() ?: true,
            equalizerPreset = try {
                EqualizerPreset.valueOf(prefs[Keys.EQUALIZER_PRESET] ?: EqualizerPreset.FLAT.name)
            } catch (_: Exception) { EqualizerPreset.FLAT },
            bassBoostEnabled = prefs[Keys.BASS_BOOST_ENABLED]?.toBoolean() ?: false,
            bassBoostStrength = try {
                EffectStrength.valueOf(prefs[Keys.BASS_BOOST_STRENGTH] ?: EffectStrength.MODERATE.name)
            } catch (_: Exception) { EffectStrength.MODERATE },
            virtualizerEnabled = prefs[Keys.VIRTUALIZER_ENABLED]?.toBoolean() ?: false,
            virtualizerStrength = prefs[Keys.VIRTUALIZER_STRENGTH]?.toIntOrNull() ?: 500,
            reverbPreset = try {
                ReverbPreset.valueOf(prefs[Keys.REVERB_PRESET] ?: ReverbPreset.NONE.name)
            } catch (_: Exception) { ReverbPreset.NONE },
            lrBalance = prefs[Keys.LR_BALANCE]?.toFloatOrNull() ?: 0f,
            autoEqByGenre = prefs[Keys.AUTO_EQ_BY_GENRE]?.toBoolean() ?: false,
            pitchSemitones = prefs[Keys.PITCH_SEMITONES]?.toFloatOrNull() ?: 0f,
            downloadConnections = prefs[Keys.DOWNLOAD_CONNECTIONS]?.toIntOrNull() ?: 4,
        )
    }.distinctUntilChanged()

    val activeServerId: Flow<String?> = context.dataStore.data.map { it[Keys.ACTIVE_SERVER_ID] }
    val activeUserId: Flow<String?> = context.dataStore.data.map { it[Keys.ACTIVE_USER_ID] }

    suspend fun setActiveServer(serverId: String) {
        context.dataStore.edit { it[Keys.ACTIVE_SERVER_ID] = serverId }
    }

    suspend fun setActiveUser(userId: String) {
        context.dataStore.edit { it[Keys.ACTIVE_USER_ID] = userId }
    }

    suspend fun setPreferredPlayer(playerType: PlayerType) {
        context.dataStore.edit { it[Keys.PREFERRED_PLAYER] = playerType.name }
    }

    suspend fun setPreferredSubtitleLanguage(language: String?) {
        context.dataStore.edit {
            if (language != null) it[Keys.PREFERRED_SUBTITLE_LANG] = language
            else it.remove(Keys.PREFERRED_SUBTITLE_LANG)
        }
    }

    suspend fun setPreferredAudioLanguage(language: String?) {
        context.dataStore.edit {
            if (language != null) it[Keys.PREFERRED_AUDIO_LANG] = language
            else it.remove(Keys.PREFERRED_AUDIO_LANG)
        }
    }

    suspend fun setMediaStreamSelection(
        itemId: String,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ) {
        writeMediaStreamSelections(
            itemId = itemId,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
        )
    }

    suspend fun setDynamicTheming(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_THEMING] = enabled.toString() }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setOledMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.OLED_MODE] = enabled.toString() }
    }

    suspend fun setSubtitleStyle(style: SubtitleStyle) {
        context.dataStore.edit { it[Keys.SUBTITLE_STYLE] = json.encodeToString(style) }
    }

    suspend fun setStreamingQuality(quality: StreamingQuality) {
        context.dataStore.edit { it[Keys.STREAMING_QUALITY] = quality.name }
    }

    suspend fun setMaxCacheSize(sizeMb: Int) {
        context.dataStore.edit { it[Keys.MAX_CACHE_SIZE_MB] = sizeMb.toString() }
    }

    suspend fun setAutoDeleteCache(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_DELETE_CACHE] = enabled.toString() }
    }

    suspend fun setPinLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PIN_LOCK_ENABLED] = enabled.toString() }
    }

    suspend fun setPinHash(hash: String?) {
        context.dataStore.edit {
            if (hash != null) it[Keys.PIN_HASH] = hash
            else it.remove(Keys.PIN_HASH)
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    fun verifyPin(input: String, storedHash: String?): Boolean {
        if (storedHash == null) return false
        return hashPin(input) == storedHash
    }

    fun hashPin(pin: String): String {
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(pin.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    suspend fun setContinueWatching(items: List<com.raulshma.jellyplay.core.model.MediaItem>) {
        context.dataStore.edit { it[Keys.CONTINUE_WATCHING] = json.encodeToString(items) }
    }

    suspend fun setKidsModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KIDS_MODE_ENABLED] = enabled.toString() }
    }

    suspend fun setKidsModeMaxRating(rating: String) {
        context.dataStore.edit { it[Keys.KIDS_MODE_MAX_RATING] = rating }
    }

    suspend fun setDialogueBoostEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DIALOGUE_BOOST_ENABLED] = enabled.toString() }
    }

    suspend fun setDialogueBoostStrength(strength: EffectStrength) {
        context.dataStore.edit { it[Keys.DIALOGUE_BOOST_STRENGTH] = strength.name }
    }

    suspend fun setEqualizerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EQUALIZER_ENABLED] = enabled.toString() }
    }

    suspend fun setEqualizerSettings(settings: EqualizerSettings) {
        context.dataStore.edit { it[Keys.EQUALIZER_SETTINGS] = json.encodeToString(settings) }
    }

    suspend fun setAudioDelay(ms: Long) {
        context.dataStore.edit { it[Keys.AUDIO_DELAY_MS] = ms.toString() }
    }

    suspend fun setDecoderMode(mode: DecoderMode) {
        context.dataStore.edit { it[Keys.DECODER_MODE] = mode.name }
    }

    suspend fun setAudioPassthrough(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUDIO_PASSTHROUGH] = enabled.toString() }
    }

    suspend fun setFrameRateMatching(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FRAME_RATE_MATCHING] = enabled.toString() }
    }

    suspend fun setNightModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NIGHT_MODE_ENABLED] = enabled.toString() }
    }

    suspend fun setNightModeStrength(strength: EffectStrength) {
        context.dataStore.edit { it[Keys.NIGHT_MODE_STRENGTH] = strength.name }
    }

    suspend fun setHomeMode(mode: HomeMode) {
        context.dataStore.edit { it[Keys.HOME_MODE] = mode.name }
    }

    suspend fun setVideoSeekDurationMs(ms: Long) {
        context.dataStore.edit { it[Keys.VIDEO_SEEK_DURATION_MS] = ms.toString() }
    }

    suspend fun setVideoDefaultOrientation(mode: OrientationMode) {
        context.dataStore.edit { it[Keys.VIDEO_DEFAULT_ORIENTATION] = mode.name }
    }

    suspend fun setVideoControlsTimeoutMs(ms: Long) {
        context.dataStore.edit { it[Keys.VIDEO_CONTROLS_TIMEOUT_MS] = ms.toString() }
    }

    suspend fun setVideoGesturesEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIDEO_GESTURES_ENABLED] = enabled.toString() }
    }

    suspend fun setVideoDefaultSpeed(speed: Float) {
        context.dataStore.edit { it[Keys.VIDEO_DEFAULT_SPEED] = speed.toString() }
    }

    suspend fun setVideoDefaultAspectRatio(ratio: String) {
        context.dataStore.edit { it[Keys.VIDEO_DEFAULT_ASPECT_RATIO] = ratio }
    }

    suspend fun setVideoAutoplayNext(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIDEO_AUTOPLAY_NEXT] = enabled.toString() }
    }

    suspend fun setVideoSwipeSeekMaxMs(ms: Long) {
        context.dataStore.edit { it[Keys.VIDEO_SWIPE_SEEK_MAX_MS] = ms.toString() }
    }

    suspend fun setVideoRememberBrightness(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIDEO_REMEMBER_BRIGHTNESS] = enabled.toString() }
    }

    suspend fun setVideoBrightnessLevel(level: Float) {
        context.dataStore.edit { it[Keys.VIDEO_BRIGHTNESS_LEVEL] = level.toString() }
    }

    suspend fun setAudioDefaultSpeed(speed: Float) {
        context.dataStore.edit { it[Keys.AUDIO_DEFAULT_SPEED] = speed.toString() }
    }

    suspend fun setAudioNightModeVolume(volume: Float) {
        context.dataStore.edit { it[Keys.AUDIO_NIGHT_MODE_VOLUME] = volume.toString() }
    }

    suspend fun setAudioNightModeGain(gain: Int) {
        context.dataStore.edit { it[Keys.AUDIO_NIGHT_MODE_GAIN] = gain.toString() }
    }

    suspend fun setAudioSkipPreviousThresholdMs(ms: Long) {
        context.dataStore.edit { it[Keys.AUDIO_SKIP_PREVIOUS_THRESHOLD_MS] = ms.toString() }
    }

    suspend fun setAudioAutoplayNext(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUDIO_AUTOPLAY_NEXT] = enabled.toString() }
    }

    suspend fun setTrickplayEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TRICKPLAY_ENABLED] = enabled.toString() }
    }

    suspend fun setTrickplayOnSeekGesture(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TRICKPLAY_ON_SEEK_GESTURE] = enabled.toString() }
    }

    suspend fun setSegmentBehavior(type: MediaSegmentType, behavior: SegmentBehavior) {
        context.dataStore.edit { prefs ->
            val current = readSegmentBehaviors(prefs).toMutableMap()
            current[type] = behavior
            prefs[Keys.SEGMENT_BEHAVIORS] = json.encodeToString(
                current.mapKeys { it.key.name }.mapValues { it.value.name }
            )
        }
    }

    suspend fun setVideoEpisodeBrowserEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIDEO_EPISODE_BROWSER_ENABLED] = enabled.toString() }
    }

    suspend fun setVideoPreloadBufferSize(size: PreloadBufferSize) {
        context.dataStore.edit { it[Keys.VIDEO_PRELOAD_BUFFER_SIZE] = size.name }
    }

    suspend fun setAudioPreloadBufferSize(size: PreloadBufferSize) {
        context.dataStore.edit { it[Keys.AUDIO_PRELOAD_BUFFER_SIZE] = size.name }
    }

    suspend fun setAudioNormalizationMode(mode: AudioNormalizationMode) {
        context.dataStore.edit { it[Keys.AUDIO_NORMALIZATION_MODE] = mode.name }
    }

    suspend fun setAudioNormalizationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUDIO_NORMALIZATION_ENABLED] = enabled.toString() }
    }

    suspend fun setReplayGainPreAmpDb(db: Float) {
        context.dataStore.edit { it[Keys.REPLAYGAIN_PRE_AMP_DB] = db.toString() }
    }

    suspend fun setChannelMixMode(mode: ChannelMixMode) {
        context.dataStore.edit { it[Keys.CHANNEL_MIX_MODE] = mode.name }
    }

    suspend fun setChannelMixEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CHANNEL_MIX_ENABLED] = enabled.toString() }
    }

    suspend fun setGaplessEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUDIO_GAPLESS_ENABLED] = enabled.toString() }
    }

    suspend fun setCrossfadeDurationMs(ms: Long) {
        context.dataStore.edit { it[Keys.AUDIO_CROSSFADE_DURATION_MS] = ms.toString() }
    }

    suspend fun setSleepTimerDurationMs(ms: Long) {
        context.dataStore.edit { it[Keys.SLEEP_TIMER_DURATION_MS] = ms.toString() }
    }

    suspend fun setSleepTimerEndOfEpisode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SLEEP_TIMER_END_OF_EPISODE] = enabled.toString() }
    }

    suspend fun setDreamImageCategories(categories: Set<DreamImageCategory>) {
        context.dataStore.edit { it[Keys.DREAM_IMAGE_CATEGORIES] = json.encodeToString(categories) }
    }

    suspend fun setDreamSlideshowIntervalMs(ms: Long) {
        context.dataStore.edit { it[Keys.DREAM_SLIDESHOW_INTERVAL_MS] = ms.toString() }
    }

    suspend fun setDreamKenBurnsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DREAM_KEN_BURNS_ENABLED] = enabled.toString() }
    }

    suspend fun setDreamTransitionStyle(style: DreamTransitionStyle) {
        context.dataStore.edit { it[Keys.DREAM_TRANSITION_STYLE] = style.name }
    }

    suspend fun setDreamShowTitle(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DREAM_SHOW_TITLE] = enabled.toString() }
    }

    suspend fun setEqualizerPreset(preset: EqualizerPreset) {
        context.dataStore.edit { it[Keys.EQUALIZER_PRESET] = preset.name }
    }

    suspend fun setBassBoostEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BASS_BOOST_ENABLED] = enabled.toString() }
    }

    suspend fun setBassBoostStrength(strength: EffectStrength) {
        context.dataStore.edit { it[Keys.BASS_BOOST_STRENGTH] = strength.name }
    }

    suspend fun setVirtualizerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIRTUALIZER_ENABLED] = enabled.toString() }
    }

    suspend fun setVirtualizerStrength(strength: Int) {
        context.dataStore.edit { it[Keys.VIRTUALIZER_STRENGTH] = strength.toString() }
    }

    suspend fun setReverbPreset(preset: ReverbPreset) {
        context.dataStore.edit { it[Keys.REVERB_PRESET] = preset.name }
    }

    suspend fun setLrBalance(balance: Float) {
        context.dataStore.edit { it[Keys.LR_BALANCE] = balance.toString() }
    }

    suspend fun setAutoEqByGenre(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_EQ_BY_GENRE] = enabled.toString() }
    }

    suspend fun setPitchSemitones(semitones: Float) {
        context.dataStore.edit { it[Keys.PITCH_SEMITONES] = semitones.toString() }
    }

    suspend fun setDownloadConnections(count: Int) {
        context.dataStore.edit { it[Keys.DOWNLOAD_CONNECTIONS] = count.toString() }
    }

    val continueWatching: kotlinx.coroutines.flow.Flow<List<com.raulshma.jellyplay.core.model.MediaItem>> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.CONTINUE_WATCHING]?.let {
                try {
                    json.decodeFromString<List<com.raulshma.jellyplay.core.model.MediaItem>>(it)
                } catch (_: Exception) { emptyList() }
            } ?: emptyList()
        }
}
