package com.raulshma.jellyplay.feature.player.video.engine.mpv

/**
 * The ONE home for the mpv property / option / command name literals the
 * [MpvCore] choreography and both engine adapters write and read.
 *
 * Before this object the same strings were hand-typed at every write site of
 * `MpvPlayerEngine` (Android) and `MpvDesktopEngine` (desktop) — 115 literals
 * in the Android engine alone, with no compiler help when a name drifted.
 * The shared intake table ([com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvPropertyIntake],
 * player-contract) keeps its own literals — that module is upstream of this
 * one and its table IS the decision record; these constants are the
 * engine-side single spelling for everything the CORE and the adapters touch.
 *
 * Grouped by mpv's own namespaces. Values are byte-identical to the shipped
 * literals (the extraction is textual, not editorial).
 */
public object MpvProperties {
    // ── Playback state properties (the observed set; see MpvPropertyIntake) ──
    public const val PAUSE: String = "pause"
    public const val SPEED: String = "speed"
    public const val PAUSED_FOR_CACHE: String = "paused-for-cache"
    public const val EOF_REACHED: String = "eof-reached"
    public const val TIME_POS: String = "time-pos"
    public const val DURATION: String = "duration"
    public const val DEMUXER_CACHE_DURATION: String = "demuxer-cache-duration"
    public const val DEMUXER_CACHE_TIME: String = "demuxer-cache-time"
    public const val DEMUXER_CACHE_STATE: String = "demuxer-cache-state"
    public const val SID: String = "sid"
    public const val AID: String = "aid"
    public const val SECONDARY_SID: String = "secondary-sid"
    public const val TRACK_LIST: String = "track-list"
    public const val SUB_VISIBILITY: String = "sub-visibility"
    public const val SUB_TEXT: String = "sub-text"
    public const val SUB_START: String = "sub-start"
    public const val AUDIO_PARAMS_CHANNEL_COUNT: String = "audio-params/channel-count"

    // ── Aspect / video-outputs ──────────────────────────────────────────────
    public const val VIDEO_ASPECT_OVERRIDE: String = "video-aspect-override"
    public const val PANSCAN: String = "panscan"
    public const val SUB_USE_MARGINS: String = "sub-use-margins"
    public const val SUB_ASS_FORCE_MARGINS: String = "sub-ass-force-margins"
    public const val VIDEO_ROTATE: String = "video-rotate"
    public const val VF: String = "vf"

    // ── Audio ───────────────────────────────────────────────────────────────
    public const val AF: String = "af"
    public const val AUDIO_CHANNELS: String = "audio-channels"
    public const val PITCH: String = "pitch"
    public const val AO: String = "ao"
    public const val VOLUME: String = "volume"
    public const val MUTE: String = "mute"

    // ── Config / decode (runtime writes from the shared config applier) ─────
    public const val AUDIO_DELAY: String = "audio-delay"
    public const val SUB_DELAY: String = "sub-delay"
    public const val HWDEC: String = "hwdec"

    // ── Per-request (load) options ──────────────────────────────────────────
    public const val START: String = "start"
    public const val ALANG: String = "alang"
    public const val SLANG: String = "slang"
    public const val HTTP_HEADER_FIELDS: String = "http-header-fields"
    public const val HTTP_HEADER_FIELDS_APPEND: String = "http-header-fields-append"
    public const val USER_AGENT: String = "user-agent"
    public const val DEMUXER_READAHEAD_SECS: String = "demuxer-readahead-secs"

    // ── Init-only options (the platform shells' pre-init setup) ─────────────
    public const val CONFIG: String = "config"
    public const val CONFIG_DIR: String = "config-dir"
    public const val IDLE: String = "idle"
    public const val KEEP_OPEN: String = "keep-open"
    public const val WID: String = "wid"
    public const val INPUT_DEFAULT_BINDINGS: String = "input-default-bindings"
    public const val INPUT_VO_KEYBOARD: String = "input-vo-keyboard"
    public const val OSC: String = "osc"
    public const val HWDEC_CODECS: String = "hwdec-codecs"
    public const val SUB_FONTS_DIR: String = "sub-fonts-dir"
    public const val SUB_FONT_PROVIDER: String = "sub-font-provider"
    public const val SUB_FONT: String = "sub-font"
    public const val SUB_SCALE_WITH_WINDOW: String = "sub-scale-with-window"
    public const val SUB_AUTO: String = "sub-auto"
    public const val VD_LAVC_FILM_GRAIN: String = "vd-lavc-film-grain"
    public const val MSG_LEVEL: String = "msg-level"
    public const val PROFILE: String = "profile"
    public const val AUDIO_SET_MEDIA_ROLE: String = "audio-set-media-role"
    public const val AUDIOTRACK_SESSION_ID: String = "audiotrack-session-id"
    public const val AAUDIO_SESSION_ID: String = "aaudio-session-id"
    public const val TARGET_COLORSPACE_HINT: String = "target-colorspace-hint"

    // ── Diagnostics-only reads (the Android debug render-state log) ─────────
    public const val SUB_FONT_SIZE: String = "sub-font-size"
    public const val SUB_MARGIN_Y: String = "sub-margin-y"
    public const val SUB_POS: String = "sub-pos"

    // ── Selection spellings (mpv choice values) ─────────────────────────────
    public const val SELECT_AUTO: String = "auto"
    public const val SELECT_NO: String = "no"
    public const val SELECT_YES: String = "yes"

    // ── Command names ───────────────────────────────────────────────────────
    public const val CMD_SEEK: String = "seek"
    public const val CMD_STOP: String = "stop"
    public const val CMD_LOADFILE: String = "loadfile"
    public const val CMD_SUB_ADD: String = "sub-add"
    public const val CMD_SUB_RELOAD: String = "sub-reload"
    public const val CMD_AF_CLR: String = "af"
    public const val CMD_VF_CLR: String = "vf"
    public const val CMD_SCREENSHOT_TO_FILE: String = "screenshot-to-file"

    /** The `seek` command's absolute mode argument. */
    public const val SEEK_ABSOLUTE: String = "absolute"
}
