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
import com.raulshma.jellyplay.core.model.HdrFormatPreference
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.MaxAudioChannelsEnum
import com.raulshma.jellyplay.core.model.OfflinePlaybackPreference
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.RefreshRateMode
import com.raulshma.jellyplay.core.model.SessionRestoreMode
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.TranscodeAudioCodecPreference
import com.raulshma.jellyplay.core.model.TranscodeVideoCodecPreference
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
 * The media-delivery domain's single preference declaration: one
 * [PreferenceSpec] row per key `PlaybackStore` owns — the
 * canonical persisted key name (which is ALSO the legacy string wire name,
 * declared exactly once), the default, the reset category, and the
 * read/write encoding ([PreferenceSpec.plainBoolean] / [plainInt] /
 * [plainFloat] / [enumRow] / [derived]).
 *
 * **The row groups below are the ONE registration of this domain's rows.**
 * Each row is constructed exactly once, inside its per-domain group list
 * (`engineDelivery` … `subtitleSyncToggles`) — nothing re-lists them: [all]
 * derives by concatenation of the groups, `resetKeysFor` derives by filtering
 * [all] on the declared [PreferenceSpec.resetCategory], and [searchEntries]
 * derives from the rows' declared [PreferenceSpec.search] (plus the five
 * hand-only standalone per-codec passthrough catalog entries, appended and
 * clearly separated). Adding a preference is therefore one group entry, one
 * named accessor beside the others below, and nothing else on this object — a
 * row cannot be declared and then forgotten by a second list, because there
 * is no second list.
 *
 * The named `val`s are plain accessors over [row]: the public lookup face of
 * the rows, each naming its row's wire name. No reflection enumerates them —
 * property enumeration is impossible without reflection, and the spec tier is
 * R8-safe by stance ([PreferenceSpec]'s KDoc) — so an accessor that names no
 * declared row fails fast at [row] instead, and the accessor↔row binding is
 * pinned by `PlaybackSpecDerivationTest` (which rebuilds every
 * [PlaybackStore.Keys] member through these accessors and compares it to the
 * group-declared rows).
 *
 * `PlaybackStore` is derived from these rows: the `Keys` members rebuild each
 * row's typed key from its wire name, the `read` projection delegates each
 * slice field to its row encoding, the single-key setters and `restore`
 * delegate to the rows' derived writes, and `resetKeysFor` filters the rows by
 * reset category — so a row cannot drift from the machinery that reads or
 * writes it. The [PlaybackSlice] property defaults read the rows too
 * (`.default`) — a preference's default is stated once, in its spec, instead
 * of being restated on the slice. The four rows whose read is migration or
 * platform logic carry it as [PreferenceSpec.derived] lambdas, documented in
 * place below.
 *
 * Not derivable by design (the accepted residuals — see the [PreferenceSpec]
 * KDoc's no-reflection rule):
 *  - the `Keys` aliases stay hand-built (they are the reflection anchor for
 *    the JVM reset-coverage guard), as do the store's `read`/`restore` rows,
 *    its setters and its clamps;
 *  - the cross-key invariant setters `setFrameRateMatching` /
 *    `setRefreshRateMode` — two keys in one atomic edit cannot be a row
 *    encoding — stay hand-written in the store;
 *  - the projection-bundle copy and the per-field `SliceCategoryMergers` copy
 *    stay hand-written by the no-reflection rule.
 *
 * Adding a preference: one entry in the row group below (its key name,
 * default, reset category, encoding, search), one named accessor beside the
 * others, one [PlaybackSlice] property whose default reads the row, one
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
object PlaybackPreferenceSpecs {

    /**
     * Route kind shared by every settings-search row that deep-links into the
     * playback settings screen (this domain's and the in-player video
     * domain's catalog rows alike) — the plain id the feature layer maps to
     * `Route.PlaybackSettings()`.
     */
    const val ROUTE_PLAYBACK_SETTINGS = "playback_settings"

    // ------------------------------------------------------------------
    // The row groups — the ONE registration of this domain's rows, in
    // [PlaybackSlice] property order (with [FORCE_DIRECT_PLAY] beside its
    // [PLAYBACK_MODE] legacy pair). A row is constructed exactly once, here;
    // [all], the reset lists and [searchEntries] all derive from the groups.
    // ------------------------------------------------------------------

    /** Engine + delivery quality. */
    private val engineDelivery: List<PreferenceSpec<*>> = listOf(
        /*
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
        PreferenceSpec.derived(
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
        ),
        PreferenceSpec.enumRow(
            keyName = "preferred_external_player",
            default = ExternalPlayerApp.SYSTEM_CHOOSER,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "external_player_app",
                // One-key family: a single screen-face key.
                titleKey = "settings_external_player_app",
                subtitleKey = "ss_external_player_app_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("external", "player", "app", "mpv", "mx player", "vlc", "mpvkt", "handoff"),
                routeKind = ROUTE_PLAYBACK_SETTINGS,
            ),
        ),
        PreferenceSpec.enumRow(
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
        ),
        PreferenceSpec.enumRow(
            keyName = "cellular_streaming_quality",
            default = StreamingQuality.AUTO,
            resetCategory = PreferenceResetCategory.PLAYBACK,
        ),
        /*
         * The HDR delivery posture for direct play: which HDR family may reach
         * the display and which wins when more than one decodes. AUTO keeps the
         * engine's default negotiation. Sits beside the streaming-quality rows
         * — a delivery-quality knob like its neighbors.
         */
        PreferenceSpec.enumRow(
            keyName = "hdr_format_preference",
            default = HdrFormatPreference.AUTO,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "hdr_format",
                titleKey = "ss_hdr_format_title",
                subtitleKey = "ss_hdr_format_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("hdr", "hdr10", "dolby", "vision", "sdr", "tone mapping", "format", "dv"),
                routeKind = ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        /*
         * Force transcoding for sources taller than this height (0 = off) —
         * the resolution cap beside the streaming-quality rows: a "don't even
         * try to direct-play 4K on this device" lever.
         */
        PreferenceSpec.plainInt(
            keyName = "force_transcode_above_height",
            default = 0,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            min = 0,
            search = PreferenceSearchSpec(
                id = "force_transcode_above_height",
                titleKey = "ss_force_transcode_above_height_title",
                subtitleKey = "ss_force_transcode_above_height_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("transcode", "resolution", "height", "4k", "1080p", "limit", "cap", "above"),
                routeKind = ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        /*
         * The preferred video codec when the server transcodes; AUTO defers to
         * the server/device profile.
         */
        PreferenceSpec.enumRow(
            keyName = "preferred_transcode_video_codec",
            default = TranscodeVideoCodecPreference.AUTO,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "preferred_transcode_video_codec",
                titleKey = "ss_preferred_transcode_video_codec_title",
                subtitleKey = "ss_preferred_transcode_video_codec_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("transcode", "video", "codec", "h264", "hevc", "h.265", "av1", "encoder"),
                routeKind = ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        /*
         * The preferred audio codec when the server transcodes; AUTO defers to
         * the server/device profile.
         */
        PreferenceSpec.enumRow(
            keyName = "preferred_transcode_audio_codec",
            default = TranscodeAudioCodecPreference.AUTO,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "preferred_transcode_audio_codec",
                titleKey = "ss_preferred_transcode_audio_codec_title",
                subtitleKey = "ss_preferred_transcode_audio_codec_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("transcode", "audio", "codec", "aac", "ac3", "eac3", "opus"),
                routeKind = ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
    )

    /** Playback method, with the legacy `force_direct_play` pair. */
    private val playbackMethod: List<PreferenceSpec<*>> = listOf(
        /*
         * Reads [PlaybackSlice.playbackMode]. Migrates the legacy boolean
         * `force_direct_play` key when the new enum key is absent: a legacy value
         * of `true` (the historical default) maps to
         * [PlaybackMode.FORCE_DIRECT_PLAY] to preserve the prior behaviour of
         * always requesting a static stream; `false` maps to [PlaybackMode.AUTO]
         * so the server negotiates the best method.
         */
        PreferenceSpec.derived(
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
        ),
        /*
         * The legacy `force_direct_play` boolean surface. Not a [PlaybackSlice]
         * field — since the [PlaybackMode] enum owns the projection, this key is
         * read only by the `playback_mode` migration (and reset with the rest of
         * the playback keys); it is declared here so its wire name, default and
         * reset participation live with the rows instead of as magic literals.
         */
        PreferenceSpec.plainBoolean(
            keyName = "force_direct_play",
            default = true,
            resetCategory = PreferenceResetCategory.PLAYBACK,
        ),
    )

    /** Live / offline delivery. */
    private val liveOfflineDelivery: List<PreferenceSpec<*>> = listOf(
        // Live-stream delivery choice; reset under `SYNCPLAY_CASTING`, not `PLAYBACK`.
        PreferenceSpec.enumRow(
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
        ),
        PreferenceSpec.enumRow(
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
        ),
    )

    /** Decoder + audio passthrough. */
    private val decoderPassthrough: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.enumRow(
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
        ),
        PreferenceSpec.plainBoolean(
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
        ),
        /*
         * Reads [PlaybackSlice.audioPassthroughCodecs]. **Legacy migration:** the
         * pre-codec-set surface was the single `audio_passthrough` boolean whose
         * "on" always bitstreamed the full historical codec list, so an ABSENT
         * codec key (an install that has never touched the per-codec rows) reads
         * all codecs enabled — flipping the master toggle on later keeps the
         * pre-feature behaviour. A PRESENT key (even the empty set — the user
         * explicitly unchecked everything) wins verbatim, mirroring the
         * `playback_mode` row's absent-key-migrates rule. The decode is memoised
         * on the raw string — see [cachedAudioPassthroughCodecs].
         */
        PreferenceSpec.derived(
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
        ),
        PreferenceSpec.enumRow(
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
        ),
        /*
         * The stereo-downmix loudness compensation in dB; the read clamps to
         * 0–12 (a pre-clamp stored value degrades into range), while the derived
         * write stores the raw value — [PlaybackStore.setDownmixBoostDb] stays the
         * clamp owner for user-driven writes.
         */
        PreferenceSpec.plainFloat(
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
        ),
        /*
         * The ADVISORY "allow spatial audio" toggle (Android-only) — gates what
         * the ENGINE does, not the spatialization itself; see
         * AudioEffectsConfig.spatialAudioEnabled (MediaEngine.kt) for the full
         * rationale. The dependent `spatial_audio_only_headphones` row below is
         * the knob's qualification, mirroring the `audio_passthrough` ↔
         * per-codec pair above.
         */
        PreferenceSpec.plainBoolean(
            keyName = "spatial_audio_enabled",
            default = false,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                // The search hit restates the row's screen title (the fold): the
                // catalog row declares no ss_*_title twin.
                titleKey = "settings_spatial_audio",
                id = "spatial_audio",
                subtitleKey = "ss_spatial_audio_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("spatial", "spatializer", "atmos", "surround", "3d audio", "headphones"),
                routeKind = ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
                platformRule = PreferencePlatformRule.ANDROID_ONLY,
            ),
        ),
        PreferenceSpec.plainBoolean(
            keyName = "spatial_audio_only_headphones",
            default = true,
            resetCategory = PreferenceResetCategory.PLAYBACK,
        ),
    )

    /** Frame-rate ↔ refresh-rate matching (legacy bool + enum pair). */
    private val frameRatePair: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainBoolean(
            keyName = "frame_rate_matching",
            default = false,
            resetCategory = PreferenceResetCategory.PLAYBACK,
        ),
        /*
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
        PreferenceSpec.derived(
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
        ),
    )

    /** Playback-lifecycle behaviour toggles. */
    private val lifecycleToggles: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainBoolean(
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
        ),
        PreferenceSpec.plainBoolean(
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
        ),
        PreferenceSpec.plainBoolean(
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
        ),
        PreferenceSpec.plainInt(
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
        ),
        PreferenceSpec.plainBoolean(
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
        ),
        /*
         * Whether leaving the player during playback auto-enters
         * picture-in-picture. Default `true` preserves the historical behaviour;
         * turning it off makes Home/recents background the app normally instead.
         * The manual PiP button in the controls is unaffected.
         */
        PreferenceSpec.plainBoolean(
            keyName = "auto_enter_pip",
            default = true,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "auto_enter_pip",
                // One-key family: a single screen-face key.
                titleKey = "settings_auto_pip",
                subtitleKey = "ss_auto_pip_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("pip", "picture in picture", "home", "minimize", "background", "floating window"),
                routeKind = ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
                platformRule = PreferencePlatformRule.ANDROID_ONLY,
            ),
        ),
        /*
         * What happens to an interrupted session on cold start: OFF discards,
         * ASK offers the restore, AUTO resumes silently. Default ASK.
         */
        PreferenceSpec.enumRow(
            keyName = "session_restore_mode",
            default = SessionRestoreMode.ASK,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "session_restore",
                titleKey = "ss_session_restore_title",
                subtitleKey = "ss_session_restore_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("session", "restore", "resume", "crash", "reopen", "continue", "interrupted"),
                routeKind = ROUTE_PLAYBACK_SETTINGS,
            ),
        ),
        /*
         * Offer to delete the watched movie file after playback finishes
         * (downloads hygiene). Default off.
         */
        PreferenceSpec.plainBoolean(
            keyName = "offer_delete_after_playback_movies",
            default = false,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "offer_delete_movies",
                titleKey = "ss_offer_delete_movies_title",
                subtitleKey = "ss_offer_delete_movies_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("delete", "offer", "movie", "after playback", "finished", "cleanup", "prompt"),
                routeKind = ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        /*
         * Offer to delete the watched episode file after playback finishes.
         * Default off.
         */
        PreferenceSpec.plainBoolean(
            keyName = "offer_delete_after_playback_episodes",
            default = false,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "offer_delete_episodes",
                titleKey = "ss_offer_delete_episodes_title",
                subtitleKey = "ss_offer_delete_episodes_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("delete", "offer", "episode", "after playback", "finished", "cleanup", "prompt"),
                routeKind = ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        /*
         * The user-authored device-profile JSON merged over the server's
         * transcode decision (the power-user escape hatch, the
         * `video_default_aspect_ratio` raw-string precedent): a derived row
         * with the identity encode, so the derived write/restore machinery
         * stays total. An empty string (the default) merges nothing.
         */
        PreferenceSpec.derived(
            keyName = "custom_device_profile_json",
            default = "",
            resetCategory = PreferenceResetCategory.PLAYBACK,
            read = { _, raw -> raw ?: "" },
            encode = { it },
            search = PreferenceSearchSpec(
                id = "custom_device_profile",
                titleKey = "ss_custom_device_profile_title",
                subtitleKey = "ss_custom_device_profile_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("device profile", "custom", "json", "transcode", "codec", "advanced"),
                routeKind = ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
    )

    /** Subtitle + user-data-sync toggles (other reset categories). */
    private val subtitleSyncToggles: List<PreferenceSpec<*>> = listOf(
        // Reset under `SUBTITLES_LANGUAGE`, not `PLAYBACK`.
        PreferenceSpec.plainBoolean(
            keyName = "pgs_subtitle_direct_play",
            default = false,
            resetCategory = PreferenceResetCategory.SUBTITLES_LANGUAGE,
        ),
        // Both reset under `MISC_APP`, not `PLAYBACK`.
        PreferenceSpec.plainBoolean(
            keyName = "user_data_sync_enabled",
            default = true,
            resetCategory = PreferenceResetCategory.MISC_APP,
        ),
        PreferenceSpec.plainBoolean(
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
        ),
    )

    /**
     * Every row this domain declares — the concatenation of the group lists
     * above, in [PlaybackSlice] property order. Nothing re-lists the rows:
     * a row is registered exactly once, in its group, and appears here (and in
     * the reset derivation and [searchEntries]) automatically. The
     * derivation-integrity test iterates this — a group entry cannot escape it.
     */
    val all: List<PreferenceSpec<*>> =
        engineDelivery +
            playbackMethod +
            liveOfflineDelivery +
            decoderPassthrough +
            frameRatePair +
            lifecycleToggles +
            subtitleSyncToggles

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

    // ------------------------------------------------------------------
    // Settings-search standalone entries: the five per-codec passthrough
    // catalog rows. Five catalog facts over the ONE `audio_passthrough_codecs`
    // knob (the experimental feature-set precedent: the knob is a set, the
    // catalog rows are its members), declared here in the advanced-video
    // group's catalog order. These are the domain's ONLY hand-only search
    // entries — every other search entry is declared on its row and reaches
    // [searchEntries] through the derivation below.
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

    /** The five standalone per-codec passthrough catalog entries, in catalog order. */
    private val codecStandaloneSearchEntries: List<PreferenceSearchSpec> = listOf(
        searchPassthroughCodecAc3,
        searchPassthroughCodecEac3,
        searchPassthroughCodecDts,
        searchPassthroughCodecDtshd,
        searchPassthroughCodecTruehd,
    )

    /**
     * The domain's settings-search declarations, DERIVED: every row above that
     * carries a [PreferenceSearchSpec] (in [all] order), plus the five
     * hand-only standalone per-codec passthrough entries appended. Nothing
     * re-lists the searchable rows — a row that declares a search entry is in
     * the catalog by construction, and one without one is out by construction
     * (the former hand list could forget either). The settings feature derives
     * its spec-backed catalog rows from this list (and
     * [com.raulshma.jellyplay.core.datastore.videoplayer
     * .VideoPlayerPreferenceSpecs.searchEntries]) over its ordered record
     * spine, binding the declared resource keys to real resources — it matches
     * entries to rows BY ID, so this list's order is declaration order, not
     * catalog order: the row lists on the feature side own the catalog order.
     */
    val searchEntries: List<PreferenceSearchSpec> =
        all.mapNotNull { it.search } + codecStandaloneSearchEntries

    // ------------------------------------------------------------------
    // Named accessors — the public lookup face of the rows, one per group
    // entry, each naming its row's wire name. Plain map lookups, no
    // reflection (the spec tier's R8 stance); a name that no group declares
    // fails fast in [row], and the JVM derivation test pins every accessor
    // against the store's Keys.
    // ------------------------------------------------------------------

    val PREFERRED_PLAYER: PreferenceSpec<PlayerType> get() = row("preferred_player")

    val PREFERRED_EXTERNAL_PLAYER: PreferenceSpec<ExternalPlayerApp> get() = row("preferred_external_player")

    val STREAMING_QUALITY: PreferenceSpec<StreamingQuality> get() = row("streaming_quality")

    val CELLULAR_STREAMING_QUALITY: PreferenceSpec<StreamingQuality> get() = row("cellular_streaming_quality")

    val HDR_FORMAT_PREFERENCE: PreferenceSpec<HdrFormatPreference> get() = row("hdr_format_preference")

    val FORCE_TRANSCODE_ABOVE_HEIGHT: PreferenceSpec<Int> get() = row("force_transcode_above_height")

    val PREFERRED_TRANSCODE_VIDEO_CODEC: PreferenceSpec<TranscodeVideoCodecPreference> get() =
        row("preferred_transcode_video_codec")

    val PREFERRED_TRANSCODE_AUDIO_CODEC: PreferenceSpec<TranscodeAudioCodecPreference> get() =
        row("preferred_transcode_audio_codec")

    val SESSION_RESTORE_MODE: PreferenceSpec<SessionRestoreMode> get() = row("session_restore_mode")

    val OFFER_DELETE_AFTER_PLAYBACK_MOVIES: PreferenceSpec<Boolean> get() = row("offer_delete_after_playback_movies")

    val OFFER_DELETE_AFTER_PLAYBACK_EPISODES: PreferenceSpec<Boolean> get() = row("offer_delete_after_playback_episodes")

    val CUSTOM_DEVICE_PROFILE_JSON: PreferenceSpec<String> get() = row("custom_device_profile_json")

    val PLAYBACK_MODE: PreferenceSpec<PlaybackMode> get() = row("playback_mode")

    val FORCE_DIRECT_PLAY: PreferenceSpec<Boolean> get() = row("force_direct_play")

    val LIVE_STREAM_OPTION: PreferenceSpec<LiveStreamOption> get() = row("live_stream_option")

    val OFFLINE_PLAYBACK_PREFERENCE: PreferenceSpec<OfflinePlaybackPreference> get() = row("offline_playback_preference")

    val DECODER_MODE: PreferenceSpec<DecoderMode> get() = row("decoder_mode")

    val AUDIO_PASSTHROUGH: PreferenceSpec<Boolean> get() = row("audio_passthrough")

    val AUDIO_PASSTHROUGH_CODECS: PreferenceSpec<Set<AudioPassthroughCodec>> get() = row("audio_passthrough_codecs")

    val MAX_AUDIO_CHANNELS: PreferenceSpec<MaxAudioChannelsEnum> get() = row("max_audio_channels")

    val DOWNMIX_BOOST_DB: PreferenceSpec<Float> get() = row("downmix_boost_db")

    val SPATIAL_AUDIO_ENABLED: PreferenceSpec<Boolean> get() = row("spatial_audio_enabled")

    val SPATIAL_AUDIO_ONLY_HEADPHONES: PreferenceSpec<Boolean> get() = row("spatial_audio_only_headphones")

    val FRAME_RATE_MATCHING: PreferenceSpec<Boolean> get() = row("frame_rate_matching")

    val REFRESH_RATE_MODE: PreferenceSpec<RefreshRateMode> get() = row("refresh_rate_mode")

    val KEEP_SCREEN_ON_DURING_VIDEO: PreferenceSpec<Boolean> get() = row("keep_screen_on_during_video")

    val PAUSE_ON_AUDIO_FOCUS_LOSS: PreferenceSpec<Boolean> get() = row("pause_on_audio_focus_loss")

    val DUCK_ON_TRANSIENT_FOCUS_LOSS: PreferenceSpec<Boolean> get() = row("duck_on_transient_focus_loss")

    val AUTO_PLAY_COUNTDOWN_SEC: PreferenceSpec<Int> get() = row("auto_play_countdown_sec")

    val BACKGROUND_VIDEO_AUDIO_ENABLED: PreferenceSpec<Boolean> get() = row("background_video_audio_enabled")

    val AUTO_ENTER_PIP: PreferenceSpec<Boolean> get() = row("auto_enter_pip")

    val PGS_SUBTITLE_DIRECT_PLAY: PreferenceSpec<Boolean> get() = row("pgs_subtitle_direct_play")

    val USER_DATA_SYNC_ENABLED: PreferenceSpec<Boolean> get() = row("user_data_sync_enabled")

    val ANDROID_TV_WATCH_NEXT_ENABLED: PreferenceSpec<Boolean> get() = row("android_tv_watch_next_enabled")

    /** The rows keyed by wire name — the accessor lookup's memo (built once). */
    private val rowsByName: Map<String, PreferenceSpec<*>> by lazy { all.associateBy { it.keyName } }

    /**
     * The lookup behind every named accessor: the group-declared row whose
     * wire name is [keyName], typed to the accessor's declared spec type.
     * Fails fast on a name no group declares — the no-reflection substitute
     * for a misnamed-reference compile error, and the derivation test pins
     * every accessor against the store's [PlaybackStore.Keys].
     */
    private fun <T> row(keyName: String): PreferenceSpec<T> {
        val found = requireNotNull(rowsByName[keyName]) {
            "PlaybackPreferenceSpecs has no row '$keyName' — an accessor names a wire name no group declares"
        }
        @Suppress("UNCHECKED_CAST")
        return found as PreferenceSpec<T>
    }
}
