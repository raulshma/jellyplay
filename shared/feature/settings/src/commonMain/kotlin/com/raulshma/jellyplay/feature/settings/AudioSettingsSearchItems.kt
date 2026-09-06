package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_audio_player
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
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
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_preload_buffer_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_preload_buffer_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_prefetch_backfill_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_prefetch_backfill_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_prefetch_lookahead_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_prefetch_lookahead_title
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
 * Settings-search items for the "Audio Player Settings" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to AudioSettingsScreen. Aggregated in [SettingsSearchCatalog].
 */
internal val AudioSettingsSearchItems = listOf(
    SettingsSearchItem(
        id = "audio_default_speed",
        titleRes = Res.string.ss_audio_default_speed_title,
        subtitleRes = Res.string.ss_audio_default_speed_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("audio speed", "pitch", "podcast speed", "music rate"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Gauge
    ),
    SettingsSearchItem(
        id = "audio_visualizer",
        titleRes = Res.string.ss_audio_visualizer_title,
        subtitleRes = Res.string.ss_audio_visualizer_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("visualizer", "fft", "spectrum", "music wave", "effects"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Eye
    ),
    SettingsSearchItem(
        id = "sleep_timer",
        titleRes = Res.string.ss_sleep_timer_title,
        subtitleRes = Res.string.ss_sleep_timer_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("sleep", "timer", "pause", "bedtime"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Clock
    ),
    SettingsSearchItem(
        id = "audio_description",
        titleRes = Res.string.ss_audio_description_title,
        subtitleRes = Res.string.ss_audio_description_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("audio description", "narrated", "accessibility", "visually impaired"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Speakerphone
    ),
    SettingsSearchItem(
        id = "gapless_playback",
        titleRes = Res.string.ss_gapless_playback_title,
        subtitleRes = Res.string.ss_gapless_playback_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("gapless", "seamless", "transition", "silence"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.PlaylistAdd,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "crossfade",
        titleRes = Res.string.ss_crossfade_title,
        subtitleRes = Res.string.ss_crossfade_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("crossfade", "fade", "transition", "overlap"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Music,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "volume_normalization",
        titleRes = Res.string.ss_volume_normalization_title,
        subtitleRes = Res.string.ss_volume_normalization_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("normalization", "volume", "replaygain", "compression", "gain"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Adjustments,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "equalizer",
        titleRes = Res.string.ss_equalizer_title,
        subtitleRes = Res.string.ss_equalizer_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("equalizer", "eq", "bands", "bass", "treble", "audio profile"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Adjustments,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "bass_boost",
        titleRes = Res.string.ss_bass_boost_title,
        subtitleRes = Res.string.ss_bass_boost_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("bass", "boost", "low end", "subwoofer", "amplify"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.WaveSine,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "virtualizer",
        titleRes = Res.string.ss_virtualizer_title,
        subtitleRes = Res.string.ss_virtualizer_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("virtualizer", "spatial", "3d", "surround", "headphones"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Speakerphone,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "volume_boost",
        titleRes = Res.string.ss_volume_boost_title,
        subtitleRes = Res.string.ss_volume_boost_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("volume boost", "boost", "loudness", "gain", "preamp"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Speakerphone,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "reverb",
        titleRes = Res.string.ss_reverb_title,
        subtitleRes = Res.string.ss_reverb_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("reverb", "acoustic", "environment", "room", "hall"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.WaveSine,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "channel_mixing",
        titleRes = Res.string.ss_channel_mixing_title,
        subtitleRes = Res.string.ss_channel_mixing_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("mixing", "channel", "mono", "stereo", "surround"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Speakerphone,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "lr_balance",
        titleRes = Res.string.ss_lr_balance_title,
        subtitleRes = Res.string.ss_lr_balance_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("balance", "left", "right", "stereo balance"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Adjustments,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "audio_autoplay_next",
        titleRes = Res.string.ss_audio_autoplay_next_title,
        subtitleRes = Res.string.ss_audio_autoplay_next_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("audio", "autoplay", "next", "track", "music", "continuous"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.PlaylistAdd
    ),
    SettingsSearchItem(
        id = "night_mode_volume",
        titleRes = Res.string.ss_night_mode_volume_title,
        subtitleRes = Res.string.ss_night_mode_volume_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("night mode", "volume", "max", "limit", "quiet"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Music,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "night_mode_gain",
        titleRes = Res.string.ss_night_mode_gain_title,
        subtitleRes = Res.string.ss_night_mode_gain_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("night mode", "gain", "loudness", "compensation", "boost"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Adjustments,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "audio_skip_prev_threshold",
        titleRes = Res.string.ss_audio_skip_prev_threshold_title,
        subtitleRes = Res.string.ss_audio_skip_prev_threshold_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("skip", "previous", "threshold", "restart", "song", "rewind"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.PlayerSkipForward,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "audio_preload_buffer",
        titleRes = Res.string.ss_audio_preload_buffer_title,
        subtitleRes = Res.string.ss_audio_preload_buffer_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("audio", "preload", "buffer", "cache", "ahead"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Refresh,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "audio_caching_enabled",
        titleRes = Res.string.ss_audio_caching_enabled_title,
        subtitleRes = Res.string.ss_audio_caching_enabled_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("audio", "cache", "caching", "prefetch", "buffer", "plexamp", "music"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Database,
        platforms = ANDROID_ONLY_PLATFORMS,
    ),
    SettingsSearchItem(
        id = "audio_cache_size",
        titleRes = Res.string.ss_audio_cache_size_title,
        subtitleRes = Res.string.ss_audio_cache_size_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("audio", "cache", "size", "disk", "storage"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.DeviceFloppy,
        platforms = ANDROID_ONLY_PLATFORMS,
    ),
    SettingsSearchItem(
        id = "audio_prefetch_lookahead",
        titleRes = Res.string.ss_audio_prefetch_lookahead_title,
        subtitleRes = Res.string.ss_audio_prefetch_lookahead_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("audio", "prefetch", "lookahead", "buffering", "music", "queue"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Music,
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
    ),
    SettingsSearchItem(
        id = "audio_prefetch_backfill",
        titleRes = Res.string.ss_audio_prefetch_backfill_title,
        subtitleRes = Res.string.ss_audio_prefetch_backfill_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("audio", "prefetch", "backfill", "buffering", "music", "previous"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Music,
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
    ),
    SettingsSearchItem(
        id = "audio_cache_clear",
        titleRes = Res.string.ss_audio_cache_clear_title,
        subtitleRes = Res.string.ss_audio_cache_clear_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("audio", "cache", "clear", "music", "storage", "wipe"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Trash,
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
    ),
    SettingsSearchItem(
        id = "audio_cache_network_policy",
        titleRes = Res.string.ss_audio_cache_network_policy_title,
        subtitleRes = Res.string.ss_audio_cache_network_policy_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("audio", "cache", "network", "wifi", "cellular", "metered"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Wifi,
        platforms = ANDROID_ONLY_PLATFORMS,
    ),
    SettingsSearchItem(
        id = "replaygain_preamp",
        titleRes = Res.string.ss_replaygain_preamp_title,
        subtitleRes = Res.string.ss_replaygain_preamp_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("replaygain", "preamp", "pre-amp", "loudness", "gain", "target"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Adjustments,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "equalizer_preset",
        titleRes = Res.string.ss_equalizer_preset_title,
        subtitleRes = Res.string.ss_equalizer_preset_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("equalizer", "preset", "eq", "profile", "bass", "treble"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Adjustments,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "night_mode",
        titleRes = Res.string.ss_night_mode_title,
        subtitleRes = Res.string.ss_night_mode_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("night mode", "audio", "evening", "quiet", "soft"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Gauge,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "night_mode_strength",
        titleRes = Res.string.ss_night_mode_strength_title,
        subtitleRes = Res.string.ss_night_mode_strength_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("night mode", "strength", "intensity", "audio", "level"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Moon,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "bass_boost_strength",
        titleRes = Res.string.ss_bass_boost_strength_title,
        subtitleRes = Res.string.ss_bass_boost_strength_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("bass", "boost", "strength", "intensity", "low end", "subwoofer"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.WaveSine,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "virtualizer_strength",
        titleRes = Res.string.ss_virtualizer_strength_title,
        subtitleRes = Res.string.ss_virtualizer_strength_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("virtualizer", "strength", "spatial", "3d", "surround"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Speakerphone,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "volume_boost_gain",
        titleRes = Res.string.ss_volume_boost_gain_title,
        subtitleRes = Res.string.ss_volume_boost_gain_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("volume boost", "gain", "loudness", "preamp", "level"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Speakerphone,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "auto_eq_by_genre",
        titleRes = Res.string.ss_auto_eq_by_genre_title,
        subtitleRes = Res.string.ss_auto_eq_by_genre_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("auto eq", "genre", "automatic", "equalizer", "preset", "music"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Wand,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "channel_mix_mode",
        titleRes = Res.string.ss_channel_mix_mode_title,
        subtitleRes = Res.string.ss_channel_mix_mode_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("channel", "mix", "mode", "surround", "stereo", "downmix"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Speakerphone,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "pitch_shift",
        titleRes = Res.string.ss_pitch_shift_title,
        subtitleRes = Res.string.ss_pitch_shift_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("pitch", "shift", "semitone", "tone", "key", "audio"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.WaveSine,
        isAdvanced = true
    ),
)
