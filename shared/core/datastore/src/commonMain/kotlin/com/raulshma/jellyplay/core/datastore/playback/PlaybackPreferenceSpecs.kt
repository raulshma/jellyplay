package com.raulshma.jellyplay.core.datastore.playback

import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.CachedJsonNullPolicy
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.datastore.spec.PreferencePlatformRule
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.datastore.toEnumOrNull
import com.raulshma.jellyplay.core.model.AudioPassthroughCodec
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.ExternalPlayerApp
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.MaxAudioChannelsEnum
import com.raulshma.jellyplay.core.model.OfflinePlaybackPreference
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.RefreshRateMode
import com.raulshma.jellyplay.core.model.StreamingQuality
import kotlinx.serialization.encodeToString

/** The stereo-downmix loudness compensation bounds ([PlaybackSlice.downmixBoostDb] clamps to these). */
internal const val MIN_DOWNMIX_BOOST_DB = 0f
internal const val MAX_DOWNMIX_BOOST_DB = 12f
internal const val DEFAULT_DOWNMIX_BOOST_DB = 0f

/** The passthrough codec set the legacy single-boolean surface always bitstreamed. */
internal val DEFAULT_AUDIO_PASSTHROUGH_CODECS: Set<AudioPassthroughCodec> = AudioPassthroughCodec.ALL

/**
 * Memoisation holder for the [PlaybackPreferenceSpecs.AUDIO_PASSTHROUGH_CODECS]
 * row's JSON decode, keyed on the raw string so the decode is skipped when the
 * key has not changed on a given `dataStore.data` emission. Moved here from a
 * per-`PlaybackStore` field with the row it belongs to: the decode is a pure
 * function of the raw string (a corrupt blob caches its fallback exactly
 * once), so a single holder is semantically identical — and the null-raw path
 * never consults it ([CachedJsonNullPolicy.NoMemoOnNull], because the null
 * value is the legacy-boolean migration re-derived on every read). See
 * [PreferenceCodec.cachedJson].
 */
private var cachedAudioPassthroughCodecs: ParsedCache<Set<AudioPassthroughCodec>> =
    ParsedCache(null, DEFAULT_AUDIO_PASSTHROUGH_CODECS)

/**
 * The media-delivery domain's single preference declaration (Stage B of the
 * spec machinery): one [PreferenceSpec] row per key `PlaybackStore` owns — the
 * canonical persisted key name (which is ALSO the legacy pre-typed-era wire
 * name, declared exactly once), the default, the reset category, and the
 * read/write encoding ([PreferenceSpec.plainBoolean] / [plainInt] /
 * [plainFloat] / [enumRow] / [derived]).
 *
 * `PlaybackStore` is derived from these rows: the `Keys` members rebuild each
 * row's typed key from its wire name, the `read` projection delegates each
 * slice field to its row encoding, the single-key setters and `restore`
 * delegate to the rows' derived writes, and `resetKeysFor` filters the rows by
 * reset category — so a row cannot drift from the machinery that reads or
 * writes it. The four rows whose read is migration or platform logic carry it
 * as [PreferenceSpec.derived] lambdas, documented in place below.
 *
 * Not derivable by design (the accepted residuals):
 *  - the cross-key invariant setters `setFrameRateMatching` /
 *    `setRefreshRateMode` — two keys in one atomic edit cannot be a row
 *    encoding — stay hand-written in the store;
 *  - the projection-bundle copy and the per-field `SliceCategoryMergers` copy
 *    stay hand-written by the no-reflection rule.
 *
 * Adding a preference: one row here, one [PlaybackSlice] property, one
 * `read()` row, one `restore()` row (and its setter) — plus one write-through
 * test line; the derivation covers key identity, encodings and reset lists,
 * not the slice plumbing or its coverage.
 *
 * Public (the [com.raulshma.jellyplay.core.datastore.experimental
 * .ExperimentalPreferenceSpecs] precedent): the settings feature derives its
 * spec-backed catalog rows from [searchEntries], so the object must cross the
 * module boundary even though the store itself stays internal.
 */
object PlaybackPreferenceSpecs {

    /**
     * Route kind shared by every settings-search row that deep-links into the
     * playback settings screen (this domain's and the in-player video
     * domain's catalog rows alike) — the plain id the feature layer maps to
     * `Route.PlaybackSettings()`.
     */
    const val ROUTE_PLAYBACK_SETTINGS = "playback_settings"

    // ------------------------------------------------------------------
    // Engine + delivery quality
    // ------------------------------------------------------------------

    /**
     * The engine selection, parsed + clamped against the engines the running
     * binary actually ships ([platformEngineSupport]) — [normalizePreferredPlayer]
     * is the single choke point every preferred-engine read flows through, so
     * a choice restored from another platform's backup degrades to the
     * platform default instead of reaching an unregistered factory.
     * Non-destructive by design: the derived write stores the constant name,
     * so the raw value on disk stays platform-agnostic. The declared default
     * is the pre-clamp slice default; the value actually served on absence is
     * `platformEngineSupport.default`.
     */
    val PREFERRED_PLAYER: PreferenceSpec<PlayerType> = PreferenceSpec.derived(
        keyName = "preferred_player",
        default = PlayerType.EXO_PLAYER,
        resetCategory = PreferenceResetCategory.PLAYBACK,
        read = { _, raw -> normalizePreferredPlayer(raw) },
        encode = { it.name },
        search = PreferenceSearchSpec(
            id = "player_engine",
            titleKey = "ss_player_engine_title",
            subtitleKey = "ss_player_engine_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("player", "engine", "mpv", "exoplayer", "vlc", "playback"),
            routeKind = ROUTE_PLAYBACK_SETTINGS,
        ),
    )

    val PREFERRED_EXTERNAL_PLAYER: PreferenceSpec<ExternalPlayerApp> = PreferenceSpec.enumRow(
        keyName = "preferred_external_player",
        default = ExternalPlayerApp.SYSTEM_CHOOSER,
        resetCategory = PreferenceResetCategory.PLAYBACK,
        search = PreferenceSearchSpec(
            id = "external_player_app",
            titleKey = "ss_external_player_app_title",
            subtitleKey = "ss_external_player_app_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("external", "player", "app", "mpv", "mx player", "vlc", "mpvkt", "handoff"),
            routeKind = ROUTE_PLAYBACK_SETTINGS,
        ),
    )

    val STREAMING_QUALITY: PreferenceSpec<StreamingQuality> = PreferenceSpec.enumRow(
        keyName = "streaming_quality",
        default = StreamingQuality.AUTO,
        resetCategory = PreferenceResetCategory.PLAYBACK,
        search = PreferenceSearchSpec(
            id = "streaming_quality",
            titleKey = "ss_streaming_quality_title",
            subtitleKey = "ss_streaming_quality_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("quality", "streaming", "resolution", "4k", "1080p", "sd"),
            routeKind = ROUTE_PLAYBACK_SETTINGS,
            isAdvanced = true,
        ),
    )

    val CELLULAR_STREAMING_QUALITY: PreferenceSpec<StreamingQuality> = PreferenceSpec.enumRow(
        keyName = "cellular_streaming_quality",
        default = StreamingQuality.AUTO,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    // ------------------------------------------------------------------
    // Playback method (with the legacy force_direct_play pair)
    // ------------------------------------------------------------------

    /**
     * Reads [PlaybackSlice.playbackMode]. Migrates the legacy boolean
     * `force_direct_play` key when the new enum key is absent: a legacy value
     * of `true` (the historical default) maps to
     * [PlaybackMode.FORCE_DIRECT_PLAY] to preserve the prior behaviour of
     * always requesting a static stream; `false` maps to [PlaybackMode.AUTO]
     * so the server negotiates the best method.
     */
    val PLAYBACK_MODE: PreferenceSpec<PlaybackMode> = PreferenceSpec.derived(
        keyName = "playback_mode",
        default = PlaybackMode.AUTO,
        resetCategory = PreferenceResetCategory.PLAYBACK,
        read = { prefs, raw ->
            raw.toEnumOrNull<PlaybackMode>() ?: run {
                val legacyForce = PreferenceCodec.readBool(
                    prefs,
                    FORCE_DIRECT_PLAY.typedKey(),
                    FORCE_DIRECT_PLAY.keyName,
                    default = true,
                )
                if (legacyForce) PlaybackMode.FORCE_DIRECT_PLAY else PlaybackMode.AUTO
            }
        },
        encode = { it.name },
    )

    /**
     * The legacy `force_direct_play` boolean surface. Not a [PlaybackSlice]
     * field — since the [PlaybackMode] enum owns the projection, this key is
     * read only by the [PLAYBACK_MODE] migration (and reset with the rest of
     * the playback keys); it is declared here so its wire name, default and
     * reset participation live with the rows instead of as magic literals.
     */
    val FORCE_DIRECT_PLAY: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "force_direct_play",
        default = true,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    // ------------------------------------------------------------------
    // Live / offline delivery
    // ------------------------------------------------------------------

    /** Live-stream delivery choice; reset under `SYNCPLAY_CASTING`, not `PLAYBACK`. */
    val LIVE_STREAM_OPTION: PreferenceSpec<LiveStreamOption> = PreferenceSpec.enumRow(
        keyName = "live_stream_option",
        default = LiveStreamOption.AUTO,
        resetCategory = PreferenceResetCategory.SYNCPLAY_CASTING,
        search = PreferenceSearchSpec(
            id = "live_stream_option",
            titleKey = "ss_live_stream_option_title",
            subtitleKey = "ss_live_stream_option_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("live tv", "direct stream", "transcode", "tuner", "htsp", "tvheadend", "channel", "mpeg-ts", "mpeg ts", "broadcast"),
            routeKind = ROUTE_PLAYBACK_SETTINGS,
            isAdvanced = true,
        ),
    )

    val OFFLINE_PLAYBACK_PREFERENCE: PreferenceSpec<OfflinePlaybackPreference> = PreferenceSpec.enumRow(
        keyName = "offline_playback_preference",
        default = OfflinePlaybackPreference.PREFER_DOWNLOADED,
        resetCategory = PreferenceResetCategory.PLAYBACK,
        search = PreferenceSearchSpec(
            // The search hit restates the row's screen title (the fold): the
            // catalog row declares no ss_*_title twin.
            titleKey = "settings_offline_playback",
            id = "offline_playback",
            subtitleKey = "ss_offline_playback_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("offline", "download", "downloaded", "streaming", "prefer", "direct play", "local", "copy"),
            routeKind = ROUTE_PLAYBACK_SETTINGS,
            isAdvanced = true,
        ),
    )

    // ------------------------------------------------------------------
    // Decoder + audio passthrough
    // ------------------------------------------------------------------

    val DECODER_MODE: PreferenceSpec<DecoderMode> = PreferenceSpec.enumRow(
        keyName = "decoder_mode",
        default = DecoderMode.HW_PREFERRED,
        resetCategory = PreferenceResetCategory.PLAYBACK,
        search = PreferenceSearchSpec(
            id = "decoder",
            titleKey = "ss_decoder_title",
            subtitleKey = "ss_decoder_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("decoder", "hardware", "software", "decoding", "codec"),
            routeKind = ROUTE_PLAYBACK_SETTINGS,
            isAdvanced = true,
        ),
    )

    val AUDIO_PASSTHROUGH: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "audio_passthrough",
        default = false,
        resetCategory = PreferenceResetCategory.PLAYBACK,
        search = PreferenceSearchSpec(
            id = "audio_passthrough",
            titleKey = "ss_audio_passthrough_title",
            subtitleKey = "ss_audio_passthrough_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("passthrough", "surround", "hdmi", "receiver", "raw"),
            routeKind = ROUTE_PLAYBACK_SETTINGS,
            isAdvanced = true,
        ),
    )

    /**
     * Reads [PlaybackSlice.audioPassthroughCodecs]. **Legacy migration:** the
     * pre-codec-set surface was the single `audio_passthrough` boolean whose
     * "on" always bitstreamed the full historical codec list, so an ABSENT
     * codec key (an install that has never touched the per-codec rows) reads
     * all codecs enabled — flipping the master toggle on later keeps the
     * pre-feature behaviour. A PRESENT key (even the empty set — the user
     * explicitly unchecked everything) wins verbatim, mirroring the
     * [PLAYBACK_MODE] row's absent-key-migrates rule. The decode is memoised
     * on the raw string — see [cachedAudioPassthroughCodecs].
     */
    val AUDIO_PASSTHROUGH_CODECS: PreferenceSpec<Set<AudioPassthroughCodec>> = PreferenceSpec.derived(
        keyName = "audio_passthrough_codecs",
        default = DEFAULT_AUDIO_PASSTHROUGH_CODECS,
        resetCategory = PreferenceResetCategory.PLAYBACK,
        read = { _, raw ->
            PreferenceCodec.cachedJson(
                raw = raw,
                cache = cachedAudioPassthroughCodecs,
                default = DEFAULT_AUDIO_PASSTHROUGH_CODECS,
                parse = { PreferenceCodec.json.decodeFromString<Set<AudioPassthroughCodec>>(it) },
                cacheRef = { cachedAudioPassthroughCodecs = it },
                nullPolicy = CachedJsonNullPolicy.NoMemoOnNull,
                onNull = { DEFAULT_AUDIO_PASSTHROUGH_CODECS },
            )
        },
        encode = { PreferenceCodec.json.encodeToString(it) },
    )

    val MAX_AUDIO_CHANNELS: PreferenceSpec<MaxAudioChannelsEnum> = PreferenceSpec.enumRow(
        keyName = "max_audio_channels",
        default = MaxAudioChannelsEnum.AUTO,
        resetCategory = PreferenceResetCategory.PLAYBACK,
        search = PreferenceSearchSpec(
            // The search hit restates the row's screen title (the fold): the
            // catalog row declares no ss_*_title twin.
            titleKey = "settings_max_audio_channels",
            id = "max_audio_channels",
            subtitleKey = "ss_max_audio_channels_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("channels", "speaker", "layout", "5.1", "7.1", "stereo", "surround", "downmix"),
            routeKind = ROUTE_PLAYBACK_SETTINGS,
            isAdvanced = true,
        ),
    )

    /**
     * The stereo-downmix loudness compensation in dB; the read clamps to
     * 0–12 (a pre-clamp stored value degrades into range), while the derived
     * write stores the raw value — [PlaybackStore.setDownmixBoostDb] stays the
     * clamp owner for user-driven writes.
     */
    val DOWNMIX_BOOST_DB: PreferenceSpec<Float> = PreferenceSpec.plainFloat(
        keyName = "downmix_boost_db",
        default = DEFAULT_DOWNMIX_BOOST_DB,
        resetCategory = PreferenceResetCategory.PLAYBACK,
        transform = { it.coerceIn(MIN_DOWNMIX_BOOST_DB, MAX_DOWNMIX_BOOST_DB) },
        min = MIN_DOWNMIX_BOOST_DB,
        max = MAX_DOWNMIX_BOOST_DB,
        search = PreferenceSearchSpec(
            // The search hit restates the row's screen title (the fold): the
            // catalog row declares no ss_*_title twin.
            titleKey = "settings_downmix_boost",
            id = "downmix_boost",
            subtitleKey = "ss_downmix_boost_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("downmix", "boost", "stereo", "loudness", "volume", "night", "gain", "db"),
            routeKind = ROUTE_PLAYBACK_SETTINGS,
            isAdvanced = true,
        ),
    )

    // ------------------------------------------------------------------
    // Frame-rate ↔ refresh-rate matching (legacy bool + enum pair)
    // ------------------------------------------------------------------

    val FRAME_RATE_MATCHING: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "frame_rate_matching",
        default = false,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    /**
     * Reads [PlaybackSlice.refreshRateMode]. An absent key reads the OFF
     * default directly — the legacy migration below only rescues a corrupt
     * stored value, not a fresh install: a user with the old
     * `frame_rate_matching` boolean on but no mode stored maps to
     * [RefreshRateMode.FRAME_RATE_ONLY] (the old single-resolution
     * behaviour). The boolean itself stays in sync via the hand-written
     * cross-key invariant setters, not via this row.
     *
     * The settings-search row (`frame_rate_matching`) is cataloged here: this
     * is the canonical projection of the legacy boolean + mode pair — the
     * picker's knob — while [FRAME_RATE_MATCHING] stays the wire-compat
     * surface.
     */
    val REFRESH_RATE_MODE: PreferenceSpec<RefreshRateMode> = PreferenceSpec.derived(
        keyName = "refresh_rate_mode",
        default = RefreshRateMode.OFF,
        resetCategory = PreferenceResetCategory.PLAYBACK,
        read = { prefs, raw ->
            if (raw == null) {
                RefreshRateMode.OFF
            } else {
                raw.toEnumOrNull<RefreshRateMode>() ?: run {
                    if (PreferenceCodec.readBool(
                            prefs,
                            FRAME_RATE_MATCHING.typedKey(),
                            FRAME_RATE_MATCHING.keyName,
                            default = false,
                        )
                    ) {
                        RefreshRateMode.FRAME_RATE_ONLY
                    } else {
                        RefreshRateMode.OFF
                    }
                }
            }
        },
        encode = { it.name },
        search = PreferenceSearchSpec(
            id = "frame_rate_matching",
            titleKey = "ss_frame_rate_matching_title",
            subtitleKey = "ss_frame_rate_matching_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("refresh rate", "frame rate", "hz", "judder", "tv"),
            routeKind = ROUTE_PLAYBACK_SETTINGS,
            isAdvanced = true,
        ),
    )

    // ------------------------------------------------------------------
    // Playback-lifecycle behaviour toggles
    // ------------------------------------------------------------------

    val KEEP_SCREEN_ON_DURING_VIDEO: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "keep_screen_on_during_video",
        default = true,
        resetCategory = PreferenceResetCategory.PLAYBACK,
        search = PreferenceSearchSpec(
            id = "keep_screen_on",
            titleKey = "ss_keep_screen_on_title",
            subtitleKey = "ss_keep_screen_on_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("screen", "awake", "lock", "stay on", "timeout"),
            routeKind = ROUTE_PLAYBACK_SETTINGS,
            isAdvanced = true,
        ),
    )

    val PAUSE_ON_AUDIO_FOCUS_LOSS: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "pause_on_audio_focus_loss",
        default = true,
        resetCategory = PreferenceResetCategory.PLAYBACK,
        search = PreferenceSearchSpec(
            id = "pause_on_focus_loss",
            titleKey = "ss_pause_on_focus_loss_title",
            subtitleKey = "ss_pause_on_focus_loss_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("pause", "focus", "loss", "audio focus", "interruption"),
            routeKind = ROUTE_PLAYBACK_SETTINGS,
            isAdvanced = true,
        ),
    )

    val DUCK_ON_TRANSIENT_FOCUS_LOSS: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "duck_on_transient_focus_loss",
        default = false,
        resetCategory = PreferenceResetCategory.PLAYBACK,
        search = PreferenceSearchSpec(
            id = "duck_on_transient_focus_loss",
            titleKey = "ss_duck_on_transient_focus_loss_title",
            subtitleKey = "ss_duck_on_transient_focus_loss_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("duck", "phone", "call", "focus", "transient", "volume", "rewind"),
            routeKind = ROUTE_PLAYBACK_SETTINGS,
            isAdvanced = true,
        ),
    )

    val AUTO_PLAY_COUNTDOWN_SEC: PreferenceSpec<Int> = PreferenceSpec.plainInt(
        keyName = "auto_play_countdown_sec",
        default = 10,
        resetCategory = PreferenceResetCategory.PLAYBACK,
        search = PreferenceSearchSpec(
            id = "autoplay_countdown",
            titleKey = "ss_autoplay_countdown_title",
            subtitleKey = "ss_autoplay_countdown_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("countdown", "timer", "autoplay", "next"),
            routeKind = ROUTE_PLAYBACK_SETTINGS,
        ),
    )

    val BACKGROUND_VIDEO_AUDIO_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "background_video_audio_enabled",
        default = false,
        resetCategory = PreferenceResetCategory.PLAYBACK,
        search = PreferenceSearchSpec(
            id = "background_audio",
            titleKey = "ss_background_audio_title",
            subtitleKey = "ss_background_audio_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("background", "audio", "video background", "pip"),
            routeKind = ROUTE_PLAYBACK_SETTINGS,
            isAdvanced = true,
        ),
    )

    /**
     * Whether leaving the player during playback auto-enters
     * picture-in-picture. Default `true` preserves the historical behaviour;
     * turning it off makes Home/recents background the app normally instead
     * (issue #167). The manual PiP button in the controls is unaffected.
     */
    val AUTO_ENTER_PIP: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "auto_enter_pip",
        default = true,
        resetCategory = PreferenceResetCategory.PLAYBACK,
        search = PreferenceSearchSpec(
            id = "auto_enter_pip",
            titleKey = "ss_auto_pip_title",
            subtitleKey = "ss_auto_pip_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("pip", "picture in picture", "home", "minimize", "background", "floating window"),
            routeKind = ROUTE_PLAYBACK_SETTINGS,
            isAdvanced = true,
            platformRule = PreferencePlatformRule.ANDROID_ONLY,
        ),
    )

    // ------------------------------------------------------------------
    // Subtitle + user-data-sync toggles (other reset categories)
    // ------------------------------------------------------------------

    /** Reset under `SUBTITLES_LANGUAGE`, not `PLAYBACK`. */
    val PGS_SUBTITLE_DIRECT_PLAY: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "pgs_subtitle_direct_play",
        default = false,
        resetCategory = PreferenceResetCategory.SUBTITLES_LANGUAGE,
    )

    /** Both reset under `MISC_APP`, not `PLAYBACK`. */
    val USER_DATA_SYNC_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "user_data_sync_enabled",
        default = true,
        resetCategory = PreferenceResetCategory.MISC_APP,
    )

    val ANDROID_TV_WATCH_NEXT_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "android_tv_watch_next_enabled",
        default = true,
        resetCategory = PreferenceResetCategory.MISC_APP,
        search = PreferenceSearchSpec(
            id = "android_tv_watch_next",
            titleKey = "ss_android_tv_watch_next_title",
            subtitleKey = "ss_android_tv_watch_next_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("android tv", "watch next", "home", "tv", "continue"),
            routeKind = ROUTE_PLAYBACK_SETTINGS,
            isAdvanced = true,
            platformRule = PreferencePlatformRule.ANDROID_ONLY,
        ),
    )

    // ------------------------------------------------------------------
    // Settings-search standalone entries: the five per-codec passthrough
    // catalog rows. Five catalog facts over the ONE [AUDIO_PASSTHROUGH_CODECS]
    // knob (the experimental feature-set precedent: the knob is a set, the
    // catalog rows are its members), declared here in the advanced-video
    // group's catalog order.
    // ------------------------------------------------------------------

    private val searchPassthroughCodecAc3 = PreferenceSearchSpec(
        // The search hit restates the row's screen title (the fold): the
        // catalog row declares no ss_*_title twin.
        titleKey = "settings_passthrough_codec_ac3",
        id = "passthrough_codec_ac3",
        subtitleKey = "ss_passthrough_codec_subtitle",
        categoryKey = "ss_cat_playback",
        keywords = listOf("passthrough", "codec", "ac3", "dolby digital", "bitstream", "receiver"),
        routeKind = ROUTE_PLAYBACK_SETTINGS,
        isAdvanced = true,
    )

    private val searchPassthroughCodecEac3 = PreferenceSearchSpec(
        titleKey = "settings_passthrough_codec_eac3",
        id = "passthrough_codec_eac3",
        subtitleKey = "ss_passthrough_codec_subtitle",
        categoryKey = "ss_cat_playback",
        keywords = listOf("passthrough", "codec", "eac3", "dolby digital plus", "dd+", "bitstream", "receiver"),
        routeKind = ROUTE_PLAYBACK_SETTINGS,
        isAdvanced = true,
    )

    private val searchPassthroughCodecDts = PreferenceSearchSpec(
        titleKey = "settings_passthrough_codec_dts",
        id = "passthrough_codec_dts",
        subtitleKey = "ss_passthrough_codec_subtitle",
        categoryKey = "ss_cat_playback",
        keywords = listOf("passthrough", "codec", "dts", "bitstream", "receiver"),
        routeKind = ROUTE_PLAYBACK_SETTINGS,
        isAdvanced = true,
    )

    private val searchPassthroughCodecDtshd = PreferenceSearchSpec(
        titleKey = "settings_passthrough_codec_dtshd",
        id = "passthrough_codec_dtshd",
        subtitleKey = "ss_passthrough_codec_subtitle",
        categoryKey = "ss_cat_playback",
        keywords = listOf("passthrough", "codec", "dts-hd", "dtshd", "dts hd", "bitstream", "receiver"),
        routeKind = ROUTE_PLAYBACK_SETTINGS,
        isAdvanced = true,
    )

    private val searchPassthroughCodecTruehd = PreferenceSearchSpec(
        titleKey = "settings_passthrough_codec_truehd",
        id = "passthrough_codec_truehd",
        subtitleKey = "ss_passthrough_codec_subtitle",
        categoryKey = "ss_cat_playback",
        keywords = listOf("passthrough", "codec", "truehd", "dolby atmos", "mlp", "bitstream", "receiver"),
        routeKind = ROUTE_PLAYBACK_SETTINGS,
        isAdvanced = true,
    )

    /**
     * Every row this store declares, in [PlaybackSlice] property order (with
     * [FORCE_DIRECT_PLAY] beside its [PLAYBACK_MODE] legacy pair). The reset
     * derivation below and the derivation-integrity test both iterate this —
     * a row declared but forgotten here falls out of reset coverage and is
     * caught by the JVM reset-coverage guard.
     */
    val all: List<PreferenceSpec<*>> = listOf(
        PREFERRED_PLAYER,
        PREFERRED_EXTERNAL_PLAYER,
        STREAMING_QUALITY,
        CELLULAR_STREAMING_QUALITY,
        PLAYBACK_MODE,
        FORCE_DIRECT_PLAY,
        LIVE_STREAM_OPTION,
        OFFLINE_PLAYBACK_PREFERENCE,
        DECODER_MODE,
        AUDIO_PASSTHROUGH,
        AUDIO_PASSTHROUGH_CODECS,
        MAX_AUDIO_CHANNELS,
        DOWNMIX_BOOST_DB,
        FRAME_RATE_MATCHING,
        REFRESH_RATE_MODE,
        KEEP_SCREEN_ON_DURING_VIDEO,
        PAUSE_ON_AUDIO_FOCUS_LOSS,
        DUCK_ON_TRANSIENT_FOCUS_LOSS,
        AUTO_PLAY_COUNTDOWN_SEC,
        BACKGROUND_VIDEO_AUDIO_ENABLED,
        AUTO_ENTER_PIP,
        PGS_SUBTITLE_DIRECT_PLAY,
        USER_DATA_SYNC_ENABLED,
        ANDROID_TV_WATCH_NEXT_ENABLED,
    )

    /**
     * Category reset participation, derived as the rows whose declared
     * [PreferenceSpec.resetCategory] is [category] — a single store owns keys
     * across several categories ([LIVE_STREAM_OPTION] sits in
     * `SYNCPLAY_CASTING`, the subtitle + user-data-sync toggles in their own
     * categories, the rest in `PLAYBACK`). The facade aggregates these lists
     * instead of a central `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        all.filter { it.resetCategory == category }.map { it.typedKey() }

    /**
     * The domain's settings-search declarations, in the settings feature's
     * catalog order (the order the retired hand records carried): every row
     * above that carries a [PreferenceSearchSpec], plus the five standalone
     * per-codec passthrough entries. The settings feature derives its
     * spec-backed catalog rows from this list (and
     * [com.raulshma.jellyplay.core.datastore.videoplayer
     * .VideoPlayerPreferenceSpecs.searchEntries]) over its ordered record
     * spine, binding the declared resource keys to real resources.
     */
    val searchEntries: List<PreferenceSearchSpec> = listOf(
        PREFERRED_PLAYER.searchEntry(),
        AUTO_PLAY_COUNTDOWN_SEC.searchEntry(),
        DUCK_ON_TRANSIENT_FOCUS_LOSS.searchEntry(),
        BACKGROUND_VIDEO_AUDIO_ENABLED.searchEntry(),
        AUTO_ENTER_PIP.searchEntry(),
        KEEP_SCREEN_ON_DURING_VIDEO.searchEntry(),
        PAUSE_ON_AUDIO_FOCUS_LOSS.searchEntry(),
        ANDROID_TV_WATCH_NEXT_ENABLED.searchEntry(),
        DECODER_MODE.searchEntry(),
        AUDIO_PASSTHROUGH.searchEntry(),
        searchPassthroughCodecAc3,
        searchPassthroughCodecEac3,
        searchPassthroughCodecDts,
        searchPassthroughCodecDtshd,
        searchPassthroughCodecTruehd,
        MAX_AUDIO_CHANNELS.searchEntry(),
        DOWNMIX_BOOST_DB.searchEntry(),
        REFRESH_RATE_MODE.searchEntry(),
        OFFLINE_PLAYBACK_PREFERENCE.searchEntry(),
        STREAMING_QUALITY.searchEntry(),
        LIVE_STREAM_OPTION.searchEntry(),
        PREFERRED_EXTERNAL_PLAYER.searchEntry(),
    )

    /** The projection hook for [searchEntries]: a row's declared search entry. */
    private fun PreferenceSpec<*>.searchEntry(): PreferenceSearchSpec =
        requireNotNull(search) { "row '$keyName' declares no search entry" }
}
