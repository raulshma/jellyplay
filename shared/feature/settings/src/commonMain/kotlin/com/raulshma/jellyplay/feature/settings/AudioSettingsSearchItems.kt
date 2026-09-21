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
internal val AudioSettingsSearchItems = listOf(
    SettingsSearchItem(
        id = AudioSettingsIds.AUDIO_DEFAULT_SPEED,
        titleRes = Res.string.ss_audio_default_speed_title,
        subtitleRes = Res.string.ss_audio_default_speed_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("audio speed", "pitch", "podcast speed", "music rate"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Gauge
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.AUDIO_VISUALIZER,
        titleRes = Res.string.ss_audio_visualizer_title,
        subtitleRes = Res.string.ss_audio_visualizer_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("visualizer", "fft", "spectrum", "music wave", "effects"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Eye
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.SLEEP_TIMER,
        titleRes = Res.string.ss_sleep_timer_title,
        subtitleRes = Res.string.ss_sleep_timer_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("sleep", "timer", "pause", "bedtime"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Clock
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.AUDIO_DESCRIPTION,
        titleRes = Res.string.ss_audio_description_title,
        subtitleRes = Res.string.ss_audio_description_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("audio description", "narrated", "accessibility", "visually impaired"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Speakerphone
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.GAPLESS_PLAYBACK,
        titleRes = Res.string.ss_gapless_playback_title,
        subtitleRes = Res.string.ss_gapless_playback_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("gapless", "seamless", "transition", "silence"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.PlaylistAdd,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.CROSSFADE,
        titleRes = Res.string.ss_crossfade_title,
        subtitleRes = Res.string.ss_crossfade_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("crossfade", "fade", "transition", "overlap"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Music,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.VOLUME_NORMALIZATION,
        titleRes = Res.string.ss_volume_normalization_title,
        subtitleRes = Res.string.ss_volume_normalization_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("normalization", "volume", "replaygain", "compression", "gain"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Adjustments,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.EQUALIZER,
        titleRes = Res.string.ss_equalizer_title,
        subtitleRes = Res.string.ss_equalizer_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("equalizer", "eq", "bands", "bass", "treble", "audio profile"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Adjustments,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.BASS_BOOST,
        titleRes = Res.string.ss_bass_boost_title,
        subtitleRes = Res.string.ss_bass_boost_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("bass", "boost", "low end", "subwoofer", "amplify"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.WaveSine,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.VIRTUALIZER,
        titleRes = Res.string.ss_virtualizer_title,
        subtitleRes = Res.string.ss_virtualizer_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("virtualizer", "spatial", "3d", "surround", "headphones"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Speakerphone,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.VOLUME_BOOST,
        titleRes = Res.string.ss_volume_boost_title,
        subtitleRes = Res.string.ss_volume_boost_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("volume boost", "boost", "loudness", "gain", "preamp"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Speakerphone,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.REVERB,
        titleRes = Res.string.ss_reverb_title,
        subtitleRes = Res.string.ss_reverb_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("reverb", "acoustic", "environment", "room", "hall"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.WaveSine,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.CHANNEL_MIXING,
        titleRes = Res.string.ss_channel_mixing_title,
        subtitleRes = Res.string.ss_channel_mixing_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("mixing", "channel", "mono", "stereo", "surround"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Speakerphone,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.LR_BALANCE,
        titleRes = Res.string.ss_lr_balance_title,
        subtitleRes = Res.string.ss_lr_balance_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("balance", "left", "right", "stereo balance"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Adjustments,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.AUDIO_AUTOPLAY_NEXT,
        titleRes = Res.string.ss_audio_autoplay_next_title,
        subtitleRes = Res.string.ss_audio_autoplay_next_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("audio", "autoplay", "next", "track", "music", "continuous"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.PlaylistAdd
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.NIGHT_MODE_VOLUME,
        titleRes = Res.string.ss_night_mode_volume_title,
        subtitleRes = Res.string.ss_night_mode_volume_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("night mode", "volume", "max", "limit", "quiet"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Music,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.NIGHT_MODE_GAIN,
        titleRes = Res.string.ss_night_mode_gain_title,
        subtitleRes = Res.string.ss_night_mode_gain_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("night mode", "gain", "loudness", "compensation", "boost"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Adjustments,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.AUDIO_SKIP_PREV_THRESHOLD,
        titleRes = Res.string.ss_audio_skip_prev_threshold_title,
        subtitleRes = Res.string.ss_audio_skip_prev_threshold_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("skip", "previous", "threshold", "restart", "song", "rewind"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.PlayerSkipForward,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.AUDIO_PRELOAD_BUFFER,
        titleRes = Res.string.ss_audio_preload_buffer_title,
        subtitleRes = Res.string.ss_audio_preload_buffer_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("audio", "preload", "buffer", "cache", "ahead"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Refresh,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.REPLAYGAIN_PREAMP,
        titleRes = Res.string.ss_replaygain_preamp_title,
        subtitleRes = Res.string.ss_replaygain_preamp_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("replaygain", "preamp", "pre-amp", "loudness", "gain", "target"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Adjustments,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.EQUALIZER_PRESET,
        titleRes = Res.string.ss_equalizer_preset_title,
        subtitleRes = Res.string.ss_equalizer_preset_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("equalizer", "preset", "eq", "profile", "bass", "treble"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Adjustments,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.NIGHT_MODE,
        titleRes = Res.string.ss_night_mode_title,
        subtitleRes = Res.string.ss_night_mode_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("night mode", "audio", "evening", "quiet", "soft"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Gauge,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.NIGHT_MODE_STRENGTH,
        titleRes = Res.string.ss_night_mode_strength_title,
        subtitleRes = Res.string.ss_night_mode_strength_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("night mode", "strength", "intensity", "audio", "level"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Moon,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.BASS_BOOST_STRENGTH,
        titleRes = Res.string.ss_bass_boost_strength_title,
        subtitleRes = Res.string.ss_bass_boost_strength_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("bass", "boost", "strength", "intensity", "low end", "subwoofer"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.WaveSine,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.VIRTUALIZER_STRENGTH,
        titleRes = Res.string.ss_virtualizer_strength_title,
        subtitleRes = Res.string.ss_virtualizer_strength_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("virtualizer", "strength", "spatial", "3d", "surround"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Speakerphone,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.VOLUME_BOOST_GAIN,
        titleRes = Res.string.ss_volume_boost_gain_title,
        subtitleRes = Res.string.ss_volume_boost_gain_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("volume boost", "gain", "loudness", "preamp", "level"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Speakerphone,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.AUTO_EQ_BY_GENRE,
        titleRes = Res.string.ss_auto_eq_by_genre_title,
        subtitleRes = Res.string.ss_auto_eq_by_genre_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("auto eq", "genre", "automatic", "equalizer", "preset", "music"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Wand,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.CHANNEL_MIX_MODE,
        titleRes = Res.string.ss_channel_mix_mode_title,
        subtitleRes = Res.string.ss_channel_mix_mode_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("channel", "mix", "mode", "surround", "stereo", "downmix"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Speakerphone,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.PITCH_SHIFT,
        titleRes = Res.string.ss_pitch_shift_title,
        subtitleRes = Res.string.ss_pitch_shift_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("pitch", "shift", "semitone", "tone", "key", "audio"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.WaveSine,
        isAdvanced = true
    ),

)

/**
 * The audio group's per-id declared row admissions — every effect-dependent
 * strength row only renders behind the advanced toggle AND its parent effect
 * (`audioScreenRowTotal` and AudioSettingsScreen's emission `if`s read these
 * one gates). Parent ids are this group's toggle rows; `volume_normalization`
 * counts as "on" while normalization is in the TRACK/ALBUM modes — the
 * `audioRowAdmissionFlags` builder translates.
 */
internal val AudioRowAdmissions: Map<String, RowAdmission> = mapOf(
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
internal val AudioCacheSearchItems = listOf(
    SettingsSearchItem(
        id = AudioSettingsIds.AUDIO_CACHING_ENABLED,
        titleRes = Res.string.ss_audio_caching_enabled_title,
        subtitleRes = Res.string.ss_audio_caching_enabled_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("audio", "cache", "caching", "prefetch", "buffer", "plexamp", "music"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Database,
        platforms = platformsForCapability(settingsCapabilities.supportsAudioCache),
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.AUDIO_CACHE_SIZE,
        titleRes = Res.string.ss_audio_cache_size_title,
        subtitleRes = Res.string.ss_audio_cache_size_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("audio", "cache", "size", "disk", "storage"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.DeviceFloppy,
        platforms = platformsForCapability(settingsCapabilities.supportsAudioCache),
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.AUDIO_PREFETCH_LOOKAHEAD,
        titleRes = Res.string.ss_audio_prefetch_lookahead_title,
        subtitleRes = Res.string.ss_audio_prefetch_lookahead_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("audio", "prefetch", "lookahead", "buffering", "music", "queue"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Music,
        isAdvanced = true,
        platforms = platformsForCapability(settingsCapabilities.supportsAudioCache),
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.AUDIO_PREFETCH_BACKFILL,
        titleRes = Res.string.ss_audio_prefetch_backfill_title,
        subtitleRes = Res.string.ss_audio_prefetch_backfill_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("audio", "prefetch", "backfill", "buffering", "music", "previous"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Music,
        isAdvanced = true,
        platforms = platformsForCapability(settingsCapabilities.supportsAudioCache),
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.AUDIO_CACHE_CLEAR,
        titleRes = Res.string.ss_audio_cache_clear_title,
        subtitleRes = Res.string.ss_audio_cache_clear_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("audio", "cache", "clear", "music", "storage", "wipe"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Trash,
        isAdvanced = true,
        platforms = platformsForCapability(settingsCapabilities.supportsAudioCache),
    )
,
    SettingsSearchItem(
        id = AudioSettingsIds.AUDIO_CACHE_NETWORK_POLICY,
        titleRes = Res.string.ss_audio_cache_network_policy_title,
        subtitleRes = Res.string.ss_audio_cache_network_policy_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_audio_player,
        keywords = listOf("audio", "cache", "network", "wifi", "cellular", "metered"),
        route = Route.AudioSettings(),
        icon = Tabler.Outline.Wifi,
        platforms = platformsForCapability(settingsCapabilities.supportsAudioCache),
    )
)
