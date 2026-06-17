package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.model.ActivityLogSeverity
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.DeviceCapabilities
import com.raulshma.jellyplay.core.model.DeviceInfo
import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.model.DvrTimer
import com.raulshma.jellyplay.core.model.DvrTimerStatus
import com.raulshma.jellyplay.core.model.ImageBlurHashes
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.model.LogFile
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.SessionNowPlayingItem
import com.raulshma.jellyplay.core.model.SessionPlayState
import com.raulshma.jellyplay.core.model.TaskExecutionInfo
import com.raulshma.jellyplay.core.model.TaskState
import com.raulshma.jellyplay.core.model.TaskTriggerInfo
import com.raulshma.jellyplay.core.model.TrickplayInfo
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.RecordingStatus
import org.jellyfin.sdk.model.api.TrickplayInfoDto
import org.jellyfin.sdk.model.serializer.toUUID

internal fun BaseItemDto.toMediaItem() = MediaItem(
    id = id.toString(),
    name = name ?: "",
    originalTitle = originalTitle,
    overview = overview,
    mediaType = type?.toMediaType() ?: MediaType.UNKNOWN,
    year = productionYear,
    communityRating = communityRating?.toFloat(),
    officialRating = officialRating,
    runTimeTicks = runTimeTicks,
    playbackPositionTicks = userData?.playbackPositionTicks,
    isPlayed = userData?.played == true,
    isFavorite = userData?.isFavorite == true,
    premiereDate = premiereDate?.toString(),
    genres = genres ?: emptyList(),
    studios = studios?.mapNotNull { it.name } ?: emptyList(),
    tags = tags ?: emptyList(),
    parentId = parentId?.toString(),
    seriesId = seriesId?.toString(),
    seasonId = seasonId?.toString(),
    seriesName = seriesName,
    seasonNumber = parentIndexNumber,
    episodeNumber = indexNumber,
    indexNumber = indexNumber,
    childCount = childCount,
    albumArtist = albumArtist,
    album = album,
    blurHashes = ImageBlurHashes(
        primary = imageBlurHashes?.get(ImageType.PRIMARY)?.values?.firstOrNull(),
        backdrop = imageBlurHashes?.get(ImageType.BACKDROP)?.values?.firstOrNull(),
    ),
    normalizationGain = normalizationGain,
    playCount = userData?.playCount ?: 0,
    lastPlayedDate = userData?.lastPlayedDate?.toString(),
)

internal fun BaseItemKind.toMediaType(): MediaType = when (this) {
    BaseItemKind.MOVIE -> MediaType.MOVIE
    BaseItemKind.SERIES -> MediaType.SERIES
    BaseItemKind.SEASON -> MediaType.SEASON
    BaseItemKind.EPISODE -> MediaType.EPISODE
    BaseItemKind.MUSIC_ALBUM -> MediaType.ALBUM
    BaseItemKind.AUDIO -> MediaType.AUDIO
    BaseItemKind.MUSIC_ARTIST -> MediaType.ARTIST
    BaseItemKind.MUSIC_VIDEO -> MediaType.MUSIC_VIDEO
    BaseItemKind.BOX_SET -> MediaType.COLLECTION
    BaseItemKind.PHOTO -> MediaType.PHOTO
    BaseItemKind.PHOTO_ALBUM -> MediaType.PHOTO_FOLDER
    BaseItemKind.LIVE_TV_CHANNEL -> MediaType.CHANNEL
    BaseItemKind.LIVE_TV_PROGRAM -> MediaType.LIVE_TV
    else -> MediaType.UNKNOWN
}

internal fun MediaType.toBaseItemKind(): BaseItemKind? = when (this) {
    MediaType.MOVIE -> BaseItemKind.MOVIE
    MediaType.SERIES -> BaseItemKind.SERIES
    MediaType.SEASON -> BaseItemKind.SEASON
    MediaType.EPISODE -> BaseItemKind.EPISODE
    MediaType.ALBUM -> BaseItemKind.MUSIC_ALBUM
    MediaType.AUDIO -> BaseItemKind.AUDIO
    MediaType.ARTIST -> BaseItemKind.MUSIC_ARTIST
    MediaType.MUSIC_VIDEO -> BaseItemKind.MUSIC_VIDEO
    MediaType.COLLECTION -> BaseItemKind.BOX_SET
    MediaType.PHOTO -> BaseItemKind.PHOTO
    MediaType.PHOTO_FOLDER -> BaseItemKind.PHOTO_ALBUM
    MediaType.CHANNEL -> BaseItemKind.LIVE_TV_CHANNEL
    MediaType.LIVE_TV -> BaseItemKind.LIVE_TV_PROGRAM
    MediaType.MUSIC -> BaseItemKind.AUDIO
    MediaType.UNKNOWN -> null
}

internal fun TrickplayInfoDto.toTrickplayInfo() = TrickplayInfo(
    width = width ?: 320,
    height = height ?: 180,
    tileWidth = tileWidth ?: 10,
    tileHeight = tileHeight ?: 1,
    thumbnailCount = thumbnailCount ?: 0,
    interval = interval ?: 10_000,
    bandwidth = bandwidth ?: 0,
)

internal fun BaseItemDto.toLiveTvChannel() = LiveTvChannel(
    id = id.toString(),
    name = name ?: "",
    number = channelNumber,
    imageTag = imageTags?.get(ImageType.PRIMARY)?.toString(),
    currentProgram = currentProgram?.toLiveTvProgram(),
    mediaType = MediaType.CHANNEL,
    primaryBlurHash = imageBlurHashes?.get(ImageType.PRIMARY)?.values?.firstOrNull(),
)

internal fun BaseItemDto.toLiveTvProgram() = LiveTvProgram(
    id = id.toString(),
    name = name ?: "",
    overview = overview,
    channelId = channelId?.toString() ?: "",
    startDate = startDate?.toString(),
    endDate = endDate?.toString(),
    durationTicks = runTimeTicks,
    episodeTitle = episodeTitle,
    officialRating = officialRating,
    isMovie = isMovie ?: false,
    isNews = isNews ?: false,
    isSports = isSports ?: false,
    isKids = isKids ?: false,
    isLive = isLive ?: false,
    isPremiere = isPremiere ?: false,
    isSeries = isSeries ?: false,
)

internal fun org.jellyfin.sdk.model.api.TimerInfoDto.toDvrTimer() = DvrTimer(
    id = id?.toString() ?: java.util.UUID.randomUUID().toString(),
    programId = programId?.toString() ?: "",
    programName = name ?: "",
    channelId = channelId?.toString() ?: "",
    channelName = channelName ?: "",
    startDate = startDate?.toString(),
    endDate = endDate?.toString(),
    status = when (status) {
        RecordingStatus.NEW -> DvrTimerStatus.NEW
        RecordingStatus.IN_PROGRESS -> DvrTimerStatus.RECORDING
        RecordingStatus.COMPLETED -> DvrTimerStatus.COMPLETED
        RecordingStatus.CANCELLED -> DvrTimerStatus.CANCELLED
        else -> {
            val name = status?.serialName
            when (name) {
                "SCHEDULED" -> DvrTimerStatus.SCHEDULED
                "RECORDING" -> DvrTimerStatus.RECORDING
                "CONFLICT_OK" -> DvrTimerStatus.CONFLICT_OK
                "CONFLICT_NOT_OK" -> DvrTimerStatus.CONFLICT_NOT_OK
                else -> DvrTimerStatus.NEW
            }
        }
    },
    isPrePaddingRequired = isPrePaddingRequired ?: false,
    isPostPaddingRequired = isPostPaddingRequired ?: false,
    prePaddingSeconds = prePaddingSeconds ?: 0,
    postPaddingSeconds = postPaddingSeconds ?: 0,
    priority = priority ?: 0,
    seriesTimerId = seriesTimerId?.toString(),
)

internal fun org.jellyfin.sdk.model.api.SeriesTimerInfoDto.toDvrSeriesTimer() = DvrSeriesTimer(
    id = id?.toString() ?: java.util.UUID.randomUUID().toString(),
    name = name ?: "",
    channelId = channelId?.toString(),
    channelName = channelName,
    days = days?.map { it.serialName } ?: emptyList(),
    priority = priority ?: 0,
    recordAnyTime = recordAnyTime ?: true,
    recordAnyChannel = recordAnyChannel ?: true,
    keepUpTo = keepUpTo ?: 0,
)

internal fun org.jellyfin.sdk.model.api.TaskInfo.toTaskModel() = ScheduledTaskInfo(
    id = id?.toString() ?: "",
    name = name ?: "",
    state = when (state) {
        org.jellyfin.sdk.model.api.TaskState.RUNNING -> TaskState.RUNNING
        org.jellyfin.sdk.model.api.TaskState.CANCELLING -> TaskState.CANCELLING
        else -> TaskState.IDLE
    },
    isHidden = isHidden,
    isEnabled = true,
    triggers = triggers?.map { it.toTriggerModel() } ?: emptyList(),
    lastExecutionResult = lastExecutionResult?.toExecutionModel(),
    currentProgressPercentage = currentProgressPercentage,
    description = description,
    category = category,
)

internal fun org.jellyfin.sdk.model.api.TaskTriggerInfo.toTriggerModel() = TaskTriggerInfo(
    type = type.serialName,
    timeOfDayTicks = timeOfDayTicks,
    intervalTicks = intervalTicks,
    dayOfWeek = dayOfWeek?.serialName,
    maxRuntimeTicks = maxRuntimeTicks,
)

internal fun org.jellyfin.sdk.model.api.TaskResult.toExecutionModel() = TaskExecutionInfo(
    name = name ?: "",
    key = key ?: "",
    startTimeUtc = startTimeUtc.toString(),
    endTimeUtc = endTimeUtc.toString(),
    status = status.serialName,
    errorMessage = errorMessage,
)

internal fun org.jellyfin.sdk.model.api.DeviceInfoDto.toDeviceModel() = DeviceInfo(
    id = id?.toString() ?: "",
    name = name ?: "",
    customName = customName,
    appName = appName ?: "",
    appVersion = appVersion ?: "",
    lastUserName = lastUserName ?: "",
    lastUserId = lastUserId?.toString() ?: "",
    dateLastActivity = dateLastActivity?.toString() ?: "",
    iconUrl = iconUrl,
    capabilities = capabilities.let { it.toCapabilitiesModel() },
)

internal fun org.jellyfin.sdk.model.api.ClientCapabilitiesDto.toCapabilitiesModel() = DeviceCapabilities(
    playableMediaTypes = playableMediaTypes.map { it.serialName },
    supportedCommands = supportedCommands.map { it.serialName },
    supportsMediaControl = supportsMediaControl,
    supportsContentUploading = false,
)

internal fun org.jellyfin.sdk.model.api.LogFile.toLogFileModel() = LogFile(
    name = name,
    dateModified = dateModified.toString(),
    size = size,
    contentType = "text/plain",
)

internal fun org.jellyfin.sdk.model.api.ActivityLogEntry.toActivityModel() = ActivityLogEntry(
    id = id,
    name = name,
    type = type,
    userId = userId.toString(),
    overview = overview,
    shortOverview = shortOverview,
    itemId = itemId,
    date = date.toString(),
    severity = when (severity) {
        org.jellyfin.sdk.model.api.LogLevel.TRACE -> ActivityLogSeverity.TRACE
        org.jellyfin.sdk.model.api.LogLevel.DEBUG -> ActivityLogSeverity.DEBUG
        org.jellyfin.sdk.model.api.LogLevel.WARNING -> ActivityLogSeverity.WARNING
        org.jellyfin.sdk.model.api.LogLevel.ERROR -> ActivityLogSeverity.ERROR
        org.jellyfin.sdk.model.api.LogLevel.CRITICAL -> ActivityLogSeverity.FATAL
        else -> ActivityLogSeverity.INFORMATION
    },
)

internal fun org.jellyfin.sdk.model.api.SessionInfoDto.toSessionModel() = SessionInfo(
    id = id?.toString() ?: "",
    deviceId = deviceId ?: "",
    userId = userId.toString(),
    userName = userName ?: "",
    client = client ?: "",
    lastActivityDate = lastActivityDate.toString(),
    lastPlaybackCheckIn = lastPlaybackCheckIn?.toString(),
    deviceName = deviceName ?: "",
    deviceType = deviceType ?: "",
    nowPlayingItem = nowPlayingItem?.toSessionItemModel(),
    playState = playState?.toSessionPlayStateModel(),
    isActive = isActive,
    supportsRemoteControl = supportsRemoteControl,
)

internal fun BaseItemDto.toSessionItemModel() = SessionNowPlayingItem(
    id = id?.toString() ?: "",
    name = name ?: "",
    type = type.serialName,
    mediaType = mediaType?.serialName,
    runTimeTicks = runTimeTicks,
    primaryImageTag = imageTags?.entries?.firstOrNull { it.key == ImageType.PRIMARY }?.value ?: imageTags?.entries?.firstOrNull()?.value,
    seriesName = seriesName,
    backdropImageTag = backdropImageTags?.firstOrNull() ?: imageTags?.entries?.firstOrNull { it.key == ImageType.BACKDROP }?.value,
)

internal fun org.jellyfin.sdk.model.api.PlayerStateInfo.toSessionPlayStateModel() = SessionPlayState(
    positionTicks = positionTicks,
    isPaused = isPaused,
    isMuted = isMuted,
    volumeLevel = volumeLevel,
    repeatMode = repeatMode.serialName,
    playMethod = playMethod?.serialName,
)

internal fun parseItemKind(type: String): BaseItemKind? = when (type) {
    "Movie" -> BaseItemKind.MOVIE
    "Series" -> BaseItemKind.SERIES
    "Episode" -> BaseItemKind.EPISODE
    "Audio" -> BaseItemKind.AUDIO
    "MusicVideo" -> BaseItemKind.MUSIC_VIDEO
    "Book" -> BaseItemKind.BOOK
    "BoxSet" -> BaseItemKind.BOX_SET
    else -> null
}

internal fun parseItemSortBy(sortBy: String): org.jellyfin.sdk.model.api.ItemSortBy? = when (sortBy) {
    "SortName" -> org.jellyfin.sdk.model.api.ItemSortBy.SORT_NAME
    "DatePlayed" -> org.jellyfin.sdk.model.api.ItemSortBy.DATE_PLAYED
    "DateCreated" -> org.jellyfin.sdk.model.api.ItemSortBy.DATE_CREATED
    "PlayCount" -> org.jellyfin.sdk.model.api.ItemSortBy.PLAY_COUNT
    "Random" -> org.jellyfin.sdk.model.api.ItemSortBy.RANDOM
    "PremiereDate" -> org.jellyfin.sdk.model.api.ItemSortBy.PREMIERE_DATE
    "ProductionYear" -> org.jellyfin.sdk.model.api.ItemSortBy.PRODUCTION_YEAR
    else -> null
}
