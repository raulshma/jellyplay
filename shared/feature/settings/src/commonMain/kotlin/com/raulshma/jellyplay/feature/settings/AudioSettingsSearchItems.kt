package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.outline.*
import com.composables.icons.tabler.Tabler
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_audio_player
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_auto_play_next
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_cache_clear
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_cache_network_policy
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_cache_size
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_caching_enable
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_default_speed
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_description
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_prefetch_backfill
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_prefetch_lookahead
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_visualizer
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_eq_genre
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_bass_boost
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_bass_boost_strength
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_channel_mix_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_channel_mixing
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_crossfade_duration
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_equalizer
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_equalizer_preset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_gapless_playback
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_lr_balance
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_night_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_night_mode_gain
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_night_mode_strength
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_night_mode_volume
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pitch_shift
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_preload_buffer
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_replaygain_preamp
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reverb
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_prev_threshold
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sleep_timer
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_virtualizer
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_virtualizer_strength
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_volume_boost
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_volume_boost_gain
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_volume_normalization
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_autoplay_next_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_autoplay_next_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_cache_clear_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_cache_clear_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_cache_network_policy_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_cache_network_policy_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_cache_size_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_cache_size_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_caching_enabled_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_caching_enabled_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_default_speed_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_default_speed_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_description_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_description_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_prefetch_backfill_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_prefetch_backfill_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_prefetch_lookahead_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_prefetch_lookahead_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_preload_buffer_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_preload_buffer_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_skip_prev_threshold_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_skip_prev_threshold_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_visualizer_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_visualizer_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_eq_by_genre_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_eq_by_genre_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_bass_boost_strength_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_bass_boost_strength_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_bass_boost_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_bass_boost_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_channel_mix_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_channel_mix_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_channel_mixing_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_channel_mixing_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_crossfade_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_crossfade_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_equalizer_preset_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_equalizer_preset_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_equalizer_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_equalizer_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_gapless_playback_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_gapless_playback_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_lr_balance_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_lr_balance_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_night_mode_gain_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_night_mode_gain_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_night_mode_strength_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_night_mode_strength_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_night_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_night_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_night_mode_volume_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_night_mode_volume_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_pitch_shift_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_pitch_shift_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_replaygain_preamp_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_replaygain_preamp_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_reverb_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_reverb_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_sleep_timer_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_sleep_timer_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_virtualizer_strength_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_virtualizer_strength_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_virtualizer_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_virtualizer_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_volume_boost_gain_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_volume_boost_gain_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_volume_boost_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_volume_boost_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_volume_normalization_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_volume_normalization_title

/**
 * The single-source row ids of this file's settings-search declarations.
 * Every consumer — the `SettingsSearchItem` declarations below, the screen
 * rows' `highlighted` comparisons, the admissions keys and the row-total
 * derivations — references these constants, so each id literal exists
 * exactly once. The values are the persisted deep-link/recents contract:
 * they change only deliberately, here.
 */
internal object AudioSettingsIds {
    const val AUDIO_DEFAULT_SPEED = "audio_default_speed"
    const val AUDIO_VISUALIZER = "audio_visualizer"
    const val SLEEP_TIMER = "sleep_timer"
    const val AUDIO_DESCRIPTION = "audio_description"
    const val GAPLESS_PLAYBACK = "gapless_playback"
    const val CROSSFADE = "crossfade"
    const val VOLUME_NORMALIZATION = "volume_normalization"
    const val EQUALIZER = "equalizer"
    const val BASS_BOOST = "bass_boost"
    const val VIRTUALIZER = "virtualizer"
    const val VOLUME_BOOST = "volume_boost"
    const val REVERB = "reverb"
    const val CHANNEL_MIXING = "channel_mixing"
    const val LR_BALANCE = "lr_balance"
    const val AUDIO_AUTOPLAY_NEXT = "audio_autoplay_next"
    const val NIGHT_MODE_VOLUME = "night_mode_volume"
    const val NIGHT_MODE_GAIN = "night_mode_gain"
    const val AUDIO_SKIP_PREV_THRESHOLD = "audio_skip_prev_threshold"
    const val AUDIO_PRELOAD_BUFFER = "audio_preload_buffer"
    const val REPLAYGAIN_PREAMP = "replaygain_preamp"
    const val EQUALIZER_PRESET = "equalizer_preset"
    const val NIGHT_MODE = "night_mode"
    const val NIGHT_MODE_STRENGTH = "night_mode_strength"
    const val BASS_BOOST_STRENGTH = "bass_boost_strength"
    const val VIRTUALIZER_STRENGTH = "virtualizer_strength"
    const val VOLUME_BOOST_GAIN = "volume_boost_gain"
    const val AUTO_EQ_BY_GENRE = "auto_eq_by_genre"
    const val CHANNEL_MIX_MODE = "channel_mix_mode"
    const val PITCH_SHIFT = "pitch_shift"
    const val AUDIO_CACHING_ENABLED = "audio_caching_enabled"
    const val AUDIO_CACHE_SIZE = "audio_cache_size"
    const val AUDIO_PREFETCH_LOOKAHEAD = "audio_prefetch_lookahead"
    const val AUDIO_PREFETCH_BACKFILL = "audio_prefetch_backfill"
    const val AUDIO_CACHE_CLEAR = "audio_cache_clear"
    const val AUDIO_CACHE_NETWORK_POLICY = "audio_cache_network_policy"
}

/**
 * Settings-search items for the "Audio Player" group of AudioSettingsScreen
 * (playback defaults, night mode, equalizer and audio effects). The list is
 * the group declaration: SettingsScreenGroups.audio decorates it. Aggregated
 * in [SettingsSearchCatalog].
 */
internal val AudioSettingsRowRecords = listOf(
    SettingsRowRecord(
        id = AudioSettingsIds.AUDIO_DEFAULT_SPEED,
        titleRes = Res.string.settings_audio_default_speed,
        searchTitleRes = Res.string.ss_audio_default_speed_title,
        searchSubtitleRes = Res.string.ss_audio_default_speed_subtitle,
        keywords = listOf("audio speed", "pitch", "podcast speed", "music rate"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Gauge
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.AUDIO_VISUALIZER,
        titleRes = Res.string.settings_audio_visualizer,
        searchTitleRes = Res.string.ss_audio_visualizer_title,
        searchSubtitleRes = Res.string.ss_audio_visualizer_subtitle,
        keywords = listOf("visualizer", "fft", "spectrum", "music wave", "effects"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Eye
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.SLEEP_TIMER,
        titleRes = Res.string.settings_sleep_timer,
        searchTitleRes = Res.string.ss_sleep_timer_title,
        searchSubtitleRes = Res.string.ss_sleep_timer_subtitle,
        keywords = listOf("sleep", "timer", "pause", "bedtime"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Clock
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.AUDIO_DESCRIPTION,
        titleRes = Res.string.settings_audio_description,
        searchTitleRes = Res.string.ss_audio_description_title,
        searchSubtitleRes = Res.string.ss_audio_description_subtitle,
        keywords = listOf("audio description", "narrated", "accessibility", "visually impaired"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Speakerphone
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.GAPLESS_PLAYBACK,
        titleRes = Res.string.settings_gapless_playback,
        searchTitleRes = Res.string.ss_gapless_playback_title,
        searchSubtitleRes = Res.string.ss_gapless_playback_subtitle,
        keywords = listOf("gapless", "seamless", "transition", "silence"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.PlaylistAdd,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.CROSSFADE,
        titleRes = Res.string.settings_crossfade_duration,
        searchTitleRes = Res.string.ss_crossfade_title,
        searchSubtitleRes = Res.string.ss_crossfade_subtitle,
        keywords = listOf("crossfade", "fade", "transition", "overlap"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Music,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.VOLUME_NORMALIZATION,
        titleRes = Res.string.settings_volume_normalization,
        searchTitleRes = Res.string.ss_volume_normalization_title,
        searchSubtitleRes = Res.string.ss_volume_normalization_subtitle,
        keywords = listOf("normalization", "volume", "replaygain", "compression", "gain"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Adjustments,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.EQUALIZER,
        titleRes = Res.string.settings_equalizer,
        searchTitleRes = Res.string.ss_equalizer_title,
        searchSubtitleRes = Res.string.ss_equalizer_subtitle,
        keywords = listOf("equalizer", "eq", "bands", "bass", "treble", "audio profile"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Adjustments,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.BASS_BOOST,
        titleRes = Res.string.settings_bass_boost,
        searchTitleRes = Res.string.ss_bass_boost_title,
        searchSubtitleRes = Res.string.ss_bass_boost_subtitle,
        keywords = listOf("bass", "boost", "low end", "subwoofer", "amplify"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.WaveSine,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.VIRTUALIZER,
        titleRes = Res.string.settings_virtualizer,
        searchTitleRes = Res.string.ss_virtualizer_title,
        searchSubtitleRes = Res.string.ss_virtualizer_subtitle,
        keywords = listOf("virtualizer", "spatial", "3d", "surround", "headphones"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Speakerphone,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.VOLUME_BOOST,
        titleRes = Res.string.settings_volume_boost,
        searchTitleRes = Res.string.ss_volume_boost_title,
        searchSubtitleRes = Res.string.ss_volume_boost_subtitle,
        keywords = listOf("volume boost", "boost", "loudness", "gain", "preamp"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Speakerphone,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.REVERB,
        titleRes = Res.string.settings_reverb,
        searchTitleRes = Res.string.ss_reverb_title,
        searchSubtitleRes = Res.string.ss_reverb_subtitle,
        keywords = listOf("reverb", "acoustic", "environment", "room", "hall"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.WaveSine,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.CHANNEL_MIXING,
        titleRes = Res.string.settings_channel_mixing,
        searchTitleRes = Res.string.ss_channel_mixing_title,
        searchSubtitleRes = Res.string.ss_channel_mixing_subtitle,
        keywords = listOf("mixing", "channel", "mono", "stereo", "surround"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Speakerphone,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.LR_BALANCE,
        titleRes = Res.string.settings_lr_balance,
        searchTitleRes = Res.string.ss_lr_balance_title,
        searchSubtitleRes = Res.string.ss_lr_balance_subtitle,
        keywords = listOf("balance", "left", "right", "stereo balance"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Adjustments,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.AUDIO_AUTOPLAY_NEXT,
        titleRes = Res.string.settings_audio_auto_play_next,
        searchTitleRes = Res.string.ss_audio_autoplay_next_title,
        searchSubtitleRes = Res.string.ss_audio_autoplay_next_subtitle,
        keywords = listOf("audio", "autoplay", "next", "track", "music", "continuous"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.PlaylistAdd
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.NIGHT_MODE_VOLUME,
        titleRes = Res.string.settings_night_mode_volume,
        searchTitleRes = Res.string.ss_night_mode_volume_title,
        searchSubtitleRes = Res.string.ss_night_mode_volume_subtitle,
        keywords = listOf("night mode", "volume", "max", "limit", "quiet"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Music,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.NIGHT_MODE_GAIN,
        titleRes = Res.string.settings_night_mode_gain,
        searchTitleRes = Res.string.ss_night_mode_gain_title,
        searchSubtitleRes = Res.string.ss_night_mode_gain_subtitle,
        keywords = listOf("night mode", "gain", "loudness", "compensation", "boost"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Adjustments,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.AUDIO_SKIP_PREV_THRESHOLD,
        titleRes = Res.string.settings_skip_prev_threshold,
        searchTitleRes = Res.string.ss_audio_skip_prev_threshold_title,
        searchSubtitleRes = Res.string.ss_audio_skip_prev_threshold_subtitle,
        keywords = listOf("skip", "previous", "threshold", "restart", "song", "rewind"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.PlayerSkipForward,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.AUDIO_PRELOAD_BUFFER,
        titleRes = Res.string.settings_preload_buffer,
        searchTitleRes = Res.string.ss_audio_preload_buffer_title,
        searchSubtitleRes = Res.string.ss_audio_preload_buffer_subtitle,
        keywords = listOf("audio", "preload", "buffer", "cache", "ahead"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Refresh,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.REPLAYGAIN_PREAMP,
        titleRes = Res.string.settings_replaygain_preamp,
        searchTitleRes = Res.string.ss_replaygain_preamp_title,
        searchSubtitleRes = Res.string.ss_replaygain_preamp_subtitle,
        keywords = listOf("replaygain", "preamp", "pre-amp", "loudness", "gain", "target"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Adjustments,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.EQUALIZER_PRESET,
        titleRes = Res.string.settings_equalizer_preset,
        searchTitleRes = Res.string.ss_equalizer_preset_title,
        searchSubtitleRes = Res.string.ss_equalizer_preset_subtitle,
        keywords = listOf("equalizer", "preset", "eq", "profile", "bass", "treble"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Adjustments,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.NIGHT_MODE,
        titleRes = Res.string.settings_night_mode,
        searchTitleRes = Res.string.ss_night_mode_title,
        searchSubtitleRes = Res.string.ss_night_mode_subtitle,
        keywords = listOf("night mode", "audio", "evening", "quiet", "soft"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Gauge,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.NIGHT_MODE_STRENGTH,
        titleRes = Res.string.settings_night_mode_strength,
        searchTitleRes = Res.string.ss_night_mode_strength_title,
        searchSubtitleRes = Res.string.ss_night_mode_strength_subtitle,
        keywords = listOf("night mode", "strength", "intensity", "audio", "level"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Moon,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.BASS_BOOST_STRENGTH,
        titleRes = Res.string.settings_bass_boost_strength,
        searchTitleRes = Res.string.ss_bass_boost_strength_title,
        searchSubtitleRes = Res.string.ss_bass_boost_strength_subtitle,
        keywords = listOf("bass", "boost", "strength", "intensity", "low end", "subwoofer"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.WaveSine,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.VIRTUALIZER_STRENGTH,
        titleRes = Res.string.settings_virtualizer_strength,
        searchTitleRes = Res.string.ss_virtualizer_strength_title,
        searchSubtitleRes = Res.string.ss_virtualizer_strength_subtitle,
        keywords = listOf("virtualizer", "strength", "spatial", "3d", "surround"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Speakerphone,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.VOLUME_BOOST_GAIN,
        titleRes = Res.string.settings_volume_boost_gain,
        searchTitleRes = Res.string.ss_volume_boost_gain_title,
        searchSubtitleRes = Res.string.ss_volume_boost_gain_subtitle,
        keywords = listOf("volume boost", "gain", "loudness", "preamp", "level"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Speakerphone,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.AUTO_EQ_BY_GENRE,
        titleRes = Res.string.settings_auto_eq_genre,
        searchTitleRes = Res.string.ss_auto_eq_by_genre_title,
        searchSubtitleRes = Res.string.ss_auto_eq_by_genre_subtitle,
        keywords = listOf("auto eq", "genre", "automatic", "equalizer", "preset", "music"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Wand,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.CHANNEL_MIX_MODE,
        titleRes = Res.string.settings_channel_mix_mode,
        searchTitleRes = Res.string.ss_channel_mix_mode_title,
        searchSubtitleRes = Res.string.ss_channel_mix_mode_subtitle,
        keywords = listOf("channel", "mix", "mode", "surround", "stereo", "downmix"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Speakerphone,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.PITCH_SHIFT,
        titleRes = Res.string.settings_pitch_shift,
        searchTitleRes = Res.string.ss_pitch_shift_title,
        searchSubtitleRes = Res.string.ss_pitch_shift_subtitle,
        keywords = listOf("pitch", "shift", "semitone", "tone", "key", "audio"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.WaveSine,
        isAdvanced = true
    ))

/** The catalog projection of `AudioSettingsRowRecords`: the search faces + the shared category. */
internal val AudioSettingsSearchItems: List<SettingsSearchItem> = AudioSettingsRowRecords.toSearchItems(CoreUiRes.string.ss_cat_audio_player)


/**
 * The audio group's per-id declared row admissions — full coverage, so
 * `rowTotalFor` and AudioSettingsScreen's emission `if`s read one gate per
 * id. The base gate is each record's own `isAdvanced` flag (the predicate the
 * retired hand count fell back to); the overrides are the effect-dependent
 * rows that additionally ride their parent effect — every one an isAdvanced
 * row gated `All(Advanced, WhenOn(parent))`. Parent ids are this group's
 * toggle rows; `volume_normalization` counts as "on" while normalization is
 * in the TRACK/ALBUM modes — the `audioRowAdmissionFlags` builder translates.
 */
internal val AudioRowAdmissions: Map<String, RowAdmission> =
    AudioSettingsRowRecords.admissionsByAdvancedFlag() + mapOf(
        AudioSettingsIds.REPLAYGAIN_PREAMP to RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(AudioSettingsIds.VOLUME_NORMALIZATION)),
        AudioSettingsIds.EQUALIZER_PRESET to RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(AudioSettingsIds.EQUALIZER)),
        AudioSettingsIds.NIGHT_MODE_STRENGTH to RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(AudioSettingsIds.NIGHT_MODE)),
        AudioSettingsIds.BASS_BOOST_STRENGTH to RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(AudioSettingsIds.BASS_BOOST)),
        AudioSettingsIds.VIRTUALIZER_STRENGTH to RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(AudioSettingsIds.VIRTUALIZER)),
        AudioSettingsIds.VOLUME_BOOST_GAIN to RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(AudioSettingsIds.VOLUME_BOOST)),
        AudioSettingsIds.CHANNEL_MIX_MODE to RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(AudioSettingsIds.CHANNEL_MIXING)),
    )

/**
 * Settings-search items for the nested "Audio Caching" group of
 * AudioSettingsScreen. Split out of [AudioSettingsSearchItems] along the
 * screen-group line: these rows render in their own group, gated by
 * `settingsCapabilities.supportsAudioCache` — the same flag their platform
 * tags derive from. Aggregated in [SettingsSearchCatalog].
 */
internal val AudioCacheRowRecords = listOf(
    SettingsRowRecord(
        id = AudioSettingsIds.AUDIO_CACHING_ENABLED,
        titleRes = Res.string.settings_audio_caching_enable,
        searchTitleRes = Res.string.ss_audio_caching_enabled_title,
        searchSubtitleRes = Res.string.ss_audio_caching_enabled_subtitle,
        keywords = listOf("audio", "cache", "caching", "prefetch", "buffer", "plexamp", "music"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Database,
        platforms = platformsForCapability(settingsCapabilities.supportsAudioCache),
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.AUDIO_CACHE_SIZE,
        titleRes = Res.string.settings_audio_cache_size,
        searchTitleRes = Res.string.ss_audio_cache_size_title,
        searchSubtitleRes = Res.string.ss_audio_cache_size_subtitle,
        keywords = listOf("audio", "cache", "size", "disk", "storage"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.DeviceFloppy,
        platforms = platformsForCapability(settingsCapabilities.supportsAudioCache),
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.AUDIO_PREFETCH_LOOKAHEAD,
        titleRes = Res.string.settings_audio_prefetch_lookahead,
        searchTitleRes = Res.string.ss_audio_prefetch_lookahead_title,
        searchSubtitleRes = Res.string.ss_audio_prefetch_lookahead_subtitle,
        keywords = listOf("audio", "prefetch", "lookahead", "buffering", "music", "queue"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Music,
        isAdvanced = true,
        platforms = platformsForCapability(settingsCapabilities.supportsAudioCache),
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.AUDIO_PREFETCH_BACKFILL,
        titleRes = Res.string.settings_audio_prefetch_backfill,
        searchTitleRes = Res.string.ss_audio_prefetch_backfill_title,
        searchSubtitleRes = Res.string.ss_audio_prefetch_backfill_subtitle,
        keywords = listOf("audio", "prefetch", "backfill", "buffering", "music", "previous"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Music,
        isAdvanced = true,
        platforms = platformsForCapability(settingsCapabilities.supportsAudioCache),
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.AUDIO_CACHE_CLEAR,
        titleRes = Res.string.settings_audio_cache_clear,
        searchTitleRes = Res.string.ss_audio_cache_clear_title,
        searchSubtitleRes = Res.string.ss_audio_cache_clear_subtitle,
        keywords = listOf("audio", "cache", "clear", "music", "storage", "wipe"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Trash,
        isAdvanced = true,
        platforms = platformsForCapability(settingsCapabilities.supportsAudioCache),
    )
,
    SettingsRowRecord(
        id = AudioSettingsIds.AUDIO_CACHE_NETWORK_POLICY,
        titleRes = Res.string.settings_audio_cache_network_policy,
        searchTitleRes = Res.string.ss_audio_cache_network_policy_title,
        searchSubtitleRes = Res.string.ss_audio_cache_network_policy_subtitle,
        keywords = listOf("audio", "cache", "network", "wifi", "cellular", "metered"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Wifi,
        platforms = platformsForCapability(settingsCapabilities.supportsAudioCache),
    ))

/** The catalog projection of `AudioCacheRowRecords`: the search faces + the shared category. */
internal val AudioCacheSearchItems: List<SettingsSearchItem> = AudioCacheRowRecords.toSearchItems(CoreUiRes.string.ss_cat_audio_player)

