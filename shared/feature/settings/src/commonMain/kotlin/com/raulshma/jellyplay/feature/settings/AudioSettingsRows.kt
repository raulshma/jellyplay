package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_player
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
 * The audio domain's fused row declarations — the feature-side single home of
 * every audio row's presentation, ordering, and capability (the
 * [AppearanceRows] template). Each [SettingsRow] replaces the trio the domain
 * used to declare per row: the `SettingsSearchBinding` entry, the
 * `SettingsRowRecord` entry, and the `AudioSettingsIds` holder constant (all
 * retired).
 *
 * Every row is a HAND-MAINTAINED residual (the audio knobs live in spec-less
 * stores) and carries its full hand search faces; the effect-dependent rows
 * declare their [RowAdmission.All] gates — advanced AND the parent effect
 * toggle — the one declaration both the derived totals and the screen
 * emission `if`s read. `volume_normalization` counts as "on" while
 * normalization is in the TRACK/ALBUM modes — the screen's
 * `audioRowAdmissionFlags` builder translates. The projection
 * ([List.toSearchItems]/[List.asRowGroup]) fails fast at catalog init on any
 * drift; search results, catalog order, group membership and per-gate
 * visibility are byte-identical to the retired declarations.
 */
internal object AudioRows {

    // -- The "Audio Player" group's 30 rows — HAND-MAINTAINED residuals (the knobs live in the spec-less audio/effects stores), in catalog order. --

    val AudioDefaultSpeed = SettingsRow(
        id = "audio_default_speed",
        icon = Tabler.Outline.Gauge,
        titleRes = Res.string.settings_audio_default_speed,
        searchTitleRes = Res.string.ss_audio_default_speed_title,
        searchSubtitleRes = Res.string.ss_audio_default_speed_subtitle,
        keywords = listOf("audio speed", "pitch", "podcast speed", "music rate"),
        route = Route.AudioSettings(),
    )

    val AudioVisualizer = SettingsRow(
        id = "audio_visualizer",
        icon = Tabler.Outline.Eye,
        titleRes = Res.string.settings_audio_visualizer,
        searchTitleRes = Res.string.ss_audio_visualizer_title,
        searchSubtitleRes = Res.string.ss_audio_visualizer_subtitle,
        keywords = listOf("visualizer", "fft", "spectrum", "music wave", "effects"),
        route = Route.AudioSettings(),
    )

    val SleepTimer = SettingsRow(
        id = "sleep_timer",
        icon = Tabler.Outline.Clock,
        titleRes = Res.string.settings_sleep_timer,
        searchTitleRes = Res.string.ss_sleep_timer_title,
        searchSubtitleRes = Res.string.ss_sleep_timer_subtitle,
        keywords = listOf("sleep", "timer", "pause", "bedtime"),
        route = Route.AudioSettings(),
    )

    val AudioDescription = SettingsRow(
        id = "audio_description",
        icon = Tabler.Outline.Speakerphone,
        titleRes = Res.string.settings_audio_description,
        searchTitleRes = Res.string.ss_audio_description_title,
        searchSubtitleRes = Res.string.ss_audio_description_subtitle,
        keywords = listOf("audio description", "narrated", "accessibility", "visually impaired"),
        route = Route.AudioSettings(),
    )

    val GaplessPlayback = SettingsRow(
        id = "gapless_playback",
        icon = Tabler.Outline.PlaylistAdd,
        titleRes = Res.string.settings_gapless_playback,
        searchTitleRes = Res.string.ss_gapless_playback_title,
        searchSubtitleRes = Res.string.ss_gapless_playback_subtitle,
        keywords = listOf("gapless", "seamless", "transition", "silence"),
        route = Route.AudioSettings(),
        isAdvanced = true,
    )

    val Crossfade = SettingsRow(
        id = "crossfade",
        icon = Tabler.Outline.Music,
        titleRes = Res.string.settings_crossfade_duration,
        searchTitleRes = Res.string.ss_crossfade_title,
        searchSubtitleRes = Res.string.ss_crossfade_subtitle,
        keywords = listOf("crossfade", "fade", "transition", "overlap"),
        route = Route.AudioSettings(),
        isAdvanced = true,
    )

    val VolumeNormalization = SettingsRow(
        id = "volume_normalization",
        icon = Tabler.Outline.Adjustments,
        titleRes = Res.string.settings_volume_normalization,
        searchTitleRes = Res.string.ss_volume_normalization_title,
        searchSubtitleRes = Res.string.ss_volume_normalization_subtitle,
        keywords = listOf("normalization", "volume", "replaygain", "compression", "gain"),
        route = Route.AudioSettings(),
        isAdvanced = true,
    )

    val Equalizer = SettingsRow(
        id = "equalizer",
        icon = Tabler.Outline.Adjustments,
        titleRes = Res.string.settings_equalizer,
        searchTitleRes = Res.string.ss_equalizer_title,
        searchSubtitleRes = Res.string.ss_equalizer_subtitle,
        keywords = listOf("equalizer", "eq", "bands", "bass", "treble", "audio profile"),
        route = Route.AudioSettings(),
        isAdvanced = true,
    )

    val BassBoost = SettingsRow(
        id = "bass_boost",
        icon = Tabler.Outline.WaveSine,
        titleRes = Res.string.settings_bass_boost,
        searchTitleRes = Res.string.ss_bass_boost_title,
        searchSubtitleRes = Res.string.ss_bass_boost_subtitle,
        keywords = listOf("bass", "boost", "low end", "subwoofer", "amplify"),
        route = Route.AudioSettings(),
        isAdvanced = true,
    )

    val Virtualizer = SettingsRow(
        id = "virtualizer",
        icon = Tabler.Outline.Speakerphone,
        titleRes = Res.string.settings_virtualizer,
        searchTitleRes = Res.string.ss_virtualizer_title,
        searchSubtitleRes = Res.string.ss_virtualizer_subtitle,
        keywords = listOf("virtualizer", "spatial", "3d", "surround", "headphones"),
        route = Route.AudioSettings(),
        isAdvanced = true,
    )

    val VolumeBoost = SettingsRow(
        id = "volume_boost",
        icon = Tabler.Outline.Speakerphone,
        titleRes = Res.string.settings_volume_boost,
        searchTitleRes = Res.string.ss_volume_boost_title,
        searchSubtitleRes = Res.string.ss_volume_boost_subtitle,
        keywords = listOf("volume boost", "boost", "loudness", "gain", "preamp"),
        route = Route.AudioSettings(),
        isAdvanced = true,
    )

    val Reverb = SettingsRow(
        id = "reverb",
        icon = Tabler.Outline.WaveSine,
        titleRes = Res.string.settings_reverb,
        searchTitleRes = Res.string.ss_reverb_title,
        searchSubtitleRes = Res.string.ss_reverb_subtitle,
        keywords = listOf("reverb", "acoustic", "environment", "room", "hall"),
        route = Route.AudioSettings(),
        isAdvanced = true,
    )

    val ChannelMixing = SettingsRow(
        id = "channel_mixing",
        icon = Tabler.Outline.Speakerphone,
        titleRes = Res.string.settings_channel_mixing,
        searchTitleRes = Res.string.ss_channel_mixing_title,
        searchSubtitleRes = Res.string.ss_channel_mixing_subtitle,
        keywords = listOf("mixing", "channel", "mono", "stereo", "surround"),
        route = Route.AudioSettings(),
        isAdvanced = true,
    )

    val LrBalance = SettingsRow(
        id = "lr_balance",
        icon = Tabler.Outline.Adjustments,
        titleRes = Res.string.settings_lr_balance,
        searchTitleRes = Res.string.ss_lr_balance_title,
        searchSubtitleRes = Res.string.ss_lr_balance_subtitle,
        keywords = listOf("balance", "left", "right", "stereo balance"),
        route = Route.AudioSettings(),
        isAdvanced = true,
    )

    val AudioAutoplayNext = SettingsRow(
        id = "audio_autoplay_next",
        icon = Tabler.Outline.PlaylistAdd,
        titleRes = Res.string.settings_audio_auto_play_next,
        searchTitleRes = Res.string.ss_audio_autoplay_next_title,
        searchSubtitleRes = Res.string.ss_audio_autoplay_next_subtitle,
        keywords = listOf("audio", "autoplay", "next", "track", "music", "continuous"),
        route = Route.AudioSettings(),
    )

    val NightModeVolume = SettingsRow(
        id = "night_mode_volume",
        icon = Tabler.Outline.Music,
        titleRes = Res.string.settings_night_mode_volume,
        searchTitleRes = Res.string.ss_night_mode_volume_title,
        searchSubtitleRes = Res.string.ss_night_mode_volume_subtitle,
        keywords = listOf("night mode", "volume", "max", "limit", "quiet"),
        route = Route.AudioSettings(),
        isAdvanced = true,
    )

    val NightModeGain = SettingsRow(
        id = "night_mode_gain",
        icon = Tabler.Outline.Adjustments,
        titleRes = Res.string.settings_night_mode_gain,
        searchTitleRes = Res.string.ss_night_mode_gain_title,
        searchSubtitleRes = Res.string.ss_night_mode_gain_subtitle,
        keywords = listOf("night mode", "gain", "loudness", "compensation", "boost"),
        route = Route.AudioSettings(),
        isAdvanced = true,
    )

    val AudioSkipPrevThreshold = SettingsRow(
        id = "audio_skip_prev_threshold",
        icon = Tabler.Outline.PlayerSkipForward,
        titleRes = Res.string.settings_skip_prev_threshold,
        searchTitleRes = Res.string.ss_audio_skip_prev_threshold_title,
        searchSubtitleRes = Res.string.ss_audio_skip_prev_threshold_subtitle,
        keywords = listOf("skip", "previous", "threshold", "restart", "song", "rewind"),
        route = Route.AudioSettings(),
        isAdvanced = true,
    )

    val AudioPreloadBuffer = SettingsRow(
        id = "audio_preload_buffer",
        icon = Tabler.Outline.Refresh,
        titleRes = Res.string.settings_preload_buffer,
        searchTitleRes = Res.string.ss_audio_preload_buffer_title,
        searchSubtitleRes = Res.string.ss_audio_preload_buffer_subtitle,
        keywords = listOf("audio", "preload", "buffer", "cache", "ahead"),
        route = Route.AudioSettings(),
        isAdvanced = true,
    )

    val ReplaygainPreamp = SettingsRow(
        id = "replaygain_preamp",
        icon = Tabler.Outline.Adjustments,
        titleRes = Res.string.settings_replaygain_preamp,
        searchTitleRes = Res.string.ss_replaygain_preamp_title,
        searchSubtitleRes = Res.string.ss_replaygain_preamp_subtitle,
        keywords = listOf("replaygain", "preamp", "pre-amp", "loudness", "gain", "target"),
        route = Route.AudioSettings(),
        isAdvanced = true,
        gate = RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(AudioRows.VolumeNormalization.id)),
    )

    val EqualizerPreset = SettingsRow(
        id = "equalizer_preset",
        icon = Tabler.Outline.Adjustments,
        titleRes = Res.string.settings_equalizer_preset,
        searchTitleRes = Res.string.ss_equalizer_preset_title,
        searchSubtitleRes = Res.string.ss_equalizer_preset_subtitle,
        keywords = listOf("equalizer", "preset", "eq", "profile", "bass", "treble"),
        route = Route.AudioSettings(),
        isAdvanced = true,
        gate = RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(AudioRows.Equalizer.id)),
    )

    val NightMode = SettingsRow(
        id = "night_mode",
        icon = Tabler.Outline.Gauge,
        titleRes = Res.string.settings_night_mode,
        searchTitleRes = Res.string.ss_night_mode_title,
        searchSubtitleRes = Res.string.ss_night_mode_subtitle,
        keywords = listOf("night mode", "audio", "evening", "quiet", "soft"),
        route = Route.AudioSettings(),
        isAdvanced = true,
    )

    val NightModeStrength = SettingsRow(
        id = "night_mode_strength",
        icon = Tabler.Outline.Moon,
        titleRes = Res.string.settings_night_mode_strength,
        searchTitleRes = Res.string.ss_night_mode_strength_title,
        searchSubtitleRes = Res.string.ss_night_mode_strength_subtitle,
        keywords = listOf("night mode", "strength", "intensity", "audio", "level"),
        route = Route.AudioSettings(),
        isAdvanced = true,
        gate = RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(AudioRows.NightMode.id)),
    )

    val BassBoostStrength = SettingsRow(
        id = "bass_boost_strength",
        icon = Tabler.Outline.WaveSine,
        titleRes = Res.string.settings_bass_boost_strength,
        searchTitleRes = Res.string.ss_bass_boost_strength_title,
        searchSubtitleRes = Res.string.ss_bass_boost_strength_subtitle,
        keywords = listOf("bass", "boost", "strength", "intensity", "low end", "subwoofer"),
        route = Route.AudioSettings(),
        isAdvanced = true,
        gate = RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(AudioRows.BassBoost.id)),
    )

    val VirtualizerStrength = SettingsRow(
        id = "virtualizer_strength",
        icon = Tabler.Outline.Speakerphone,
        titleRes = Res.string.settings_virtualizer_strength,
        searchTitleRes = Res.string.ss_virtualizer_strength_title,
        searchSubtitleRes = Res.string.ss_virtualizer_strength_subtitle,
        keywords = listOf("virtualizer", "strength", "spatial", "3d", "surround"),
        route = Route.AudioSettings(),
        isAdvanced = true,
        gate = RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(AudioRows.Virtualizer.id)),
    )

    val VolumeBoostGain = SettingsRow(
        id = "volume_boost_gain",
        icon = Tabler.Outline.Speakerphone,
        titleRes = Res.string.settings_volume_boost_gain,
        searchTitleRes = Res.string.ss_volume_boost_gain_title,
        searchSubtitleRes = Res.string.ss_volume_boost_gain_subtitle,
        keywords = listOf("volume boost", "gain", "loudness", "preamp", "level"),
        route = Route.AudioSettings(),
        isAdvanced = true,
        gate = RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(AudioRows.VolumeBoost.id)),
    )

    val AutoEqByGenre = SettingsRow(
        id = "auto_eq_by_genre",
        icon = Tabler.Outline.Wand,
        titleRes = Res.string.settings_auto_eq_genre,
        searchTitleRes = Res.string.ss_auto_eq_by_genre_title,
        searchSubtitleRes = Res.string.ss_auto_eq_by_genre_subtitle,
        keywords = listOf("auto eq", "genre", "automatic", "equalizer", "preset", "music"),
        route = Route.AudioSettings(),
        isAdvanced = true,
    )

    val ChannelMixMode = SettingsRow(
        id = "channel_mix_mode",
        icon = Tabler.Outline.Speakerphone,
        titleRes = Res.string.settings_channel_mix_mode,
        searchTitleRes = Res.string.ss_channel_mix_mode_title,
        searchSubtitleRes = Res.string.ss_channel_mix_mode_subtitle,
        keywords = listOf("channel", "mix", "mode", "surround", "stereo", "downmix"),
        route = Route.AudioSettings(),
        isAdvanced = true,
        gate = RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(AudioRows.ChannelMixing.id)),
    )

    val PitchShift = SettingsRow(
        id = "pitch_shift",
        icon = Tabler.Outline.WaveSine,
        titleRes = Res.string.settings_pitch_shift,
        searchTitleRes = Res.string.ss_pitch_shift_title,
        searchSubtitleRes = Res.string.ss_pitch_shift_subtitle,
        keywords = listOf("pitch", "shift", "semitone", "tone", "key", "audio"),
        route = Route.AudioSettings(),
        isAdvanced = true,
    )

    // -- The nested "Audio Caching" group's six rows — the whole group only exists where `settingsCapabilities.supportsAudioCache` holds, and every row derives its platform tag from that same flag. --

    val AudioCachingEnabled = SettingsRow(
        id = "audio_caching_enabled",
        icon = Tabler.Outline.Database,
        titleRes = Res.string.settings_audio_caching_enable,
        searchTitleRes = Res.string.ss_audio_caching_enabled_title,
        searchSubtitleRes = Res.string.ss_audio_caching_enabled_subtitle,
        keywords = listOf("audio", "cache", "caching", "prefetch", "buffer", "plexamp", "music"),
        route = Route.AudioSettings(),
        platforms = platformsForCapability(settingsCapabilities.supportsAudioCache),
    )

    val AudioCacheSize = SettingsRow(
        id = "audio_cache_size",
        icon = Tabler.Outline.DeviceFloppy,
        titleRes = Res.string.settings_audio_cache_size,
        searchTitleRes = Res.string.ss_audio_cache_size_title,
        searchSubtitleRes = Res.string.ss_audio_cache_size_subtitle,
        keywords = listOf("audio", "cache", "size", "disk", "storage"),
        route = Route.AudioSettings(),
        platforms = platformsForCapability(settingsCapabilities.supportsAudioCache),
    )

    val AudioPrefetchLookahead = SettingsRow(
        id = "audio_prefetch_lookahead",
        icon = Tabler.Outline.Music,
        titleRes = Res.string.settings_audio_prefetch_lookahead,
        searchTitleRes = Res.string.ss_audio_prefetch_lookahead_title,
        searchSubtitleRes = Res.string.ss_audio_prefetch_lookahead_subtitle,
        keywords = listOf("audio", "prefetch", "lookahead", "buffering", "music", "queue"),
        route = Route.AudioSettings(),
        isAdvanced = true,
        platforms = platformsForCapability(settingsCapabilities.supportsAudioCache),
    )

    val AudioPrefetchBackfill = SettingsRow(
        id = "audio_prefetch_backfill",
        icon = Tabler.Outline.Music,
        titleRes = Res.string.settings_audio_prefetch_backfill,
        searchSubtitleRes = Res.string.ss_audio_prefetch_backfill_subtitle,
        keywords = listOf("audio", "prefetch", "backfill", "buffering", "music", "previous"),
        route = Route.AudioSettings(),
        isAdvanced = true,
        platforms = platformsForCapability(settingsCapabilities.supportsAudioCache),
    )

    val AudioCacheClear = SettingsRow(
        id = "audio_cache_clear",
        icon = Tabler.Outline.Trash,
        titleRes = Res.string.settings_audio_cache_clear,
        searchSubtitleRes = Res.string.ss_audio_cache_clear_subtitle,
        keywords = listOf("audio", "cache", "clear", "music", "storage", "wipe"),
        route = Route.AudioSettings(),
        isAdvanced = true,
        platforms = platformsForCapability(settingsCapabilities.supportsAudioCache),
    )

    val AudioCacheNetworkPolicy = SettingsRow(
        id = "audio_cache_network_policy",
        icon = Tabler.Outline.Wifi,
        titleRes = Res.string.settings_audio_cache_network_policy,
        searchTitleRes = Res.string.ss_audio_cache_network_policy_title,
        searchSubtitleRes = Res.string.ss_audio_cache_network_policy_subtitle,
        keywords = listOf("audio", "cache", "network", "wifi", "cellular", "metered"),
        route = Route.AudioSettings(),
        platforms = platformsForCapability(settingsCapabilities.supportsAudioCache),
    )

    /**
     * Every fused audio row — the ratchet's vocabulary. A computed accessor
     * (not an initializer): the group row lists are top-level vals declared
     * later in this file, and an eager field would turn the
     * object-to-file-facade initialization order into a cycle.
     */
    val all: List<SettingsRow>
        get() = AudioPlayerRows + AudioCacheRows
}

// ---------------------------------------------------------------------
// The spec-derived derivation inputs: the searchable semantics live on the
// datastore-side spec declarations where they exist; the ordered row lists
// below are the spine — presentation faces, catalog order, gates.
// ---------------------------------------------------------------------

private val searchRoutes: Map<String, Route> = emptyMap()

private val audioCategory = CoreUiRes.string.ss_cat_audio_player

internal val AudioPlayerRows: List<SettingsRow> = listOf(
    AudioRows.AudioDefaultSpeed,
    AudioRows.AudioVisualizer,
    AudioRows.SleepTimer,
    AudioRows.AudioDescription,
    AudioRows.GaplessPlayback,
    AudioRows.Crossfade,
    AudioRows.VolumeNormalization,
    AudioRows.Equalizer,
    AudioRows.BassBoost,
    AudioRows.Virtualizer,
    AudioRows.VolumeBoost,
    AudioRows.Reverb,
    AudioRows.ChannelMixing,
    AudioRows.LrBalance,
    AudioRows.AudioAutoplayNext,
    AudioRows.NightModeVolume,
    AudioRows.NightModeGain,
    AudioRows.AudioSkipPrevThreshold,
    AudioRows.AudioPreloadBuffer,
    AudioRows.ReplaygainPreamp,
    AudioRows.EqualizerPreset,
    AudioRows.NightMode,
    AudioRows.NightModeStrength,
    AudioRows.BassBoostStrength,
    AudioRows.VirtualizerStrength,
    AudioRows.VolumeBoostGain,
    AudioRows.AutoEqByGenre,
    AudioRows.ChannelMixMode,
    AudioRows.PitchShift,
)

internal val AudioCacheRows: List<SettingsRow> = listOf(
    AudioRows.AudioCachingEnabled,
    AudioRows.AudioCacheSize,
    AudioRows.AudioPrefetchLookahead,
    AudioRows.AudioPrefetchBackfill,
    AudioRows.AudioCacheClear,
    AudioRows.AudioCacheNetworkPolicy,
)

/**
 * The screen groups — items AND per-row admissions derive from the row
 * lists above in one act ([List.asRowGroup]), so the declaration is the
 * single home of the groups' order, faces and gates.
 */
internal val AudioGroup =
    AudioPlayerRows.asRowGroup("audio", emptyList(), searchRoutes, audioCategory)

internal val AudioCacheGroup =
    AudioCacheRows.asRowGroup("audio.cache", emptyList(), searchRoutes, audioCategory)

// The catalog projections, kept as named vals — the search/catalog-order
// pins (SpecDerivedSearchItemsTest, SettingsSearchCatalogTest) read these
// lists.

internal val AudioSettingsSearchItems: List<SettingsSearchItem> = AudioGroup.items

internal val AudioCacheSearchItems: List<SettingsSearchItem> = AudioCacheGroup.items

// -- The domain's root-screen entrance declaration --

/** The audio domain's root-screen entrance — the ONE ordered declaration that drives both the settings root's `item_audio` section emission (icon/title/route id) and its entrance-step index (spliced into [SETTINGS_ENTRANCE_SECTIONS] at this render position). */
internal val AudioEntrance = SettingsEntranceSectionRow(
    key = "item_audio",
    rowId = "audio",
    icon = Tabler.Outline.Music,
    titleRes = Res.string.settings_audio_player,
)
