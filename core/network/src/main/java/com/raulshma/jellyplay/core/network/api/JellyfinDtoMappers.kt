package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.model.ActivityLogSeverity
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.DeviceCapabilities
import com.raulshma.jellyplay.core.model.DeviceInfo
import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.model.DvrTimer
import com.raulshma.jellyplay.core.model.DvrTimerStatus
import com.raulshma.jellyplay.core.model.ImageBlurHashes
import com.raulshma.jellyplay.core.model.GuideInfo
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.model.LiveTvRecording
import com.raulshma.jellyplay.core.model.LogFile
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.SessionNowPlayingItem
import com.raulshma.jellyplay.core.model.SessionPlayState
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.TaskExecutionInfo
import com.raulshma.jellyplay.core.model.TaskState
import com.raulshma.jellyplay.core.model.TaskTriggerInfo
import com.raulshma.jellyplay.core.model.TrickplayInfo
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.RecordingStatus
import org.jellyfin.sdk.model.api.TrickplayInfoDto
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.model.DateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    seasonId = seasonId?.toString() ?: (if (type == BaseItemKind.EPISODE) parentId?.toString() else null),
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
    unplayedItemCount = userData?.unplayedItemCount,
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
    BaseItemKind.LIVE_TV_CHANNEL, BaseItemKind.TV_CHANNEL -> MediaType.CHANNEL
    BaseItemKind.LIVE_TV_PROGRAM, BaseItemKind.TV_PROGRAM -> MediaType.LIVE_TV
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

/**
 * Maps a Jellyfin [MediaSourceInfo] to the domain [MediaSource]. Shared by
 * the item-detail fetch ([LibraryApiClientImpl]) and the `PlaybackInfo`
 * fetch ([PlaybackApiClientImpl]) so the playability flags and stream list
 * are parsed identically. [trickplayInfo] is item-scoped so callers that
 * have it (detail) pass it in; [PlaybackInfo] callers pass `null`.
 */
internal fun MediaSourceInfo.toMediaSource(
    trickplayInfo: TrickplayInfo? = null,
) = MediaSource(
    id = id.toString(),
    name = name ?: "",
    container = container,
    size = size,
    bitrate = bitrate?.toLong(),
    runTimeTicks = runTimeTicks,
    supportsTranscoding = supportsTranscoding,
    supportsDirectStream = supportsDirectStream,
    supportsDirectPlay = supportsDirectPlay,
    transcodeUrl = transcodingUrl,
    liveStreamId = liveStreamId,
    requiresOpening = requiresOpening,
    path = path,
    mediaStreams = mediaStreams?.map { it.toMediaStream() } ?: emptyList(),
    trickplayInfo = trickplayInfo,
)

internal fun org.jellyfin.sdk.model.api.MediaStream.toMediaStream() = MediaStream(
    index = index,
    type = when (type) {
        MediaStreamType.VIDEO -> StreamType.VIDEO
        MediaStreamType.AUDIO -> StreamType.AUDIO
        MediaStreamType.SUBTITLE -> StreamType.SUBTITLE
        else -> StreamType.EMBEDDED_IMAGE
    },
    codec = codec,
    language = language,
    title = title,
    displayTitle = displayTitle,
    isDefault = isDefault,
    isForced = isForced,
    isHearingImpaired = isHearingImpaired,
    isExternal = isExternal,
    width = width,
    height = height,
    bitRate = bitRate?.toLong(),
    sampleRate = sampleRate,
    channels = channels,
    deliveryUrl = deliveryUrl,
    videoRange = videoRange?.serialName,
    videoRangeType = videoRangeType?.serialName,
    realFrameRate = realFrameRate,
    videoDoViTitle = videoDoViTitle,
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
    channelName = channelName,
    startDate = startDate?.toIsoInstantString(),
    endDate = endDate?.toIsoInstantString(),
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
    isRepeat = isRepeat ?: false,
    hasAired = endDate?.isBefore(org.jellyfin.sdk.model.DateTime.now()) ?: false,
    indexNumber = indexNumber,
    parentIndexNumber = parentIndexNumber,
    imageTag = imageTags?.get(ImageType.PRIMARY)?.toString(),
    timerId = timerId?.toString(),
    seriesTimerId = seriesTimerId?.toString(),
)

internal fun BaseItemDto.toLiveTvRecording() = LiveTvRecording(
    id = id.toString(),
    name = name ?: "",
    overview = overview,
    channelId = channelId?.toString(),
    channelName = channelName,
    startDate = startDate?.toIsoInstantString(),
    endDate = endDate?.toIsoInstantString(),
    runTimeTicks = runTimeTicks,
    imageTag = imageTags?.get(ImageType.PRIMARY)?.toString(),
    seriesTimerId = seriesTimerId?.toString(),
    status = DvrTimerStatus.COMPLETED,
)

/**
 * Convert a Jellyfin SDK [DateTime] into a zone-carrying ISO-8601 string.
 *
 * The SDK serializes date-times as [ZonedDateTime] but deserializes them into
 * a [DateTime] (a `java.time.LocalDateTime` typealias on JVM) by shifting to
 * the system zone and dropping the offset — see
 * `DateTimeSerializer.deserialize`. A plain `.toString()` therefore emits a
 * bare `LocalDateTime` with no offset, which downstream consumers (e.g. the
 * EPG grid) would misread as UTC, shifting every program by the local offset
 * and dropping most outside the visible window.
 *
 * Re-attach the system zone here so callers get an unambiguous ISO instant
 * (`...Z` / `+HH:MM`) regardless of device timezone.
 */
internal fun DateTime.toIsoInstantString(): String =
    atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

internal fun org.jellyfin.sdk.model.api.TimerInfoDto.toDvrTimer() = DvrTimer(
    id = id?.toString() ?: java.util.UUID.randomUUID().toString(),
    programId = programId?.toString() ?: "",
    programName = name ?: "",
    channelId = channelId?.toString() ?: "",
    channelName = channelName ?: "",
    startDate = startDate?.toIsoInstantString(),
    endDate = endDate?.toIsoInstantString(),
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
    key = key ?: "",
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

/**
 * Parses a PascalCase [org.json.JSONObject] `TaskInfo` from the Jellyfin
 * WebSocket `ScheduledTasksInfo` push payload into the app's
 * [ScheduledTaskInfo] model. The server emits the full task list every push
 * (see ScheduledTasksRealtimeChannel), and the JSON casing differs from the
 * SDK's camelCase DTOs, so a dedicated parser is simpler than coercing the SDK
 * deserializer onto a raw WS string.
 *
 * Reads every field the admin UI displays: identity (Id/Key/Name), runtime
 * state (State/CurrentProgressPercentage), metadata for grouping/display
 * (Category/Description), schedule triggers, and last-execution history.
 * PascalCase field names match the Jellyfin server's `TaskInfo` JSON contract.
 */
internal fun org.json.JSONObject.toScheduledTaskInfo(): ScheduledTaskInfo {
    val stateStr = optString("State")
    val state = when (stateStr) {
        "Running" -> TaskState.RUNNING
        "Cancelling" -> TaskState.CANCELLING
        else -> TaskState.IDLE
    }
    // currentProgressPercentage may be absent or null when the server cannot
    // report a concrete value (most of a library scan).
    val progress = if (has("CurrentProgressPercentage") && !isNull("CurrentProgressPercentage")) {
        optDouble("CurrentProgressPercentage", Double.NaN).takeIf { it.isFinite() }
    } else {
        null
    }
    // Category drives the section grouping on the Scheduled Tasks screen —
    // match jellyfin-web's getCategories(): empty/blank is treated as absent.
    val category = optString("Category").takeIf { it.isNotBlank() }
    val description = optString("Description").takeIf { it.isNotBlank() }
    val triggers = optTriggers()
    val lastExecutionResult = optJSONObject("LastExecutionResult")?.toExecutionModel()
    return ScheduledTaskInfo(
        id = optString("Id"),
        key = optString("Key"),
        name = optString("Name"),
        state = state,
        isHidden = optBoolean("Hidden", false),
        isEnabled = true,
        currentProgressPercentage = progress,
        category = category,
        description = description,
        triggers = triggers,
        lastExecutionResult = lastExecutionResult,
    )
}

private fun org.json.JSONObject.optTriggers(): List<TaskTriggerInfo> {
    val arr = optJSONArray("Triggers") ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            add(
                TaskTriggerInfo(
                    type = obj.optString("Type"),
                    timeOfDayTicks = obj.optLongOrNull("TimeOfDayTicks"),
                    intervalTicks = obj.optLongOrNull("IntervalTicks"),
                    dayOfWeek = obj.optString("DayOfWeek").takeIf { it.isNotBlank() },
                    maxRuntimeTicks = obj.optLongOrNull("MaxRuntimeMs"),
                ),
            )
        }
    }
}

private fun org.json.JSONObject.optLongOrNull(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    // optLong returns 0 on parse failure; use the Number coercion path so we
    // can distinguish a genuine 0 from a missing/garbage value.
    return when (val num = opt(key)) {
        is Number -> num.toLong()
        is String -> num.toLongOrNull()
        else -> null
    }
}

/**
 * Parses a PascalCase `TaskResult` (the `LastExecutionResult` field of a WS
 * TaskInfo) into [TaskExecutionInfo]. The server sends ISO-8601 timestamps
 * directly as strings (unlike the SDK, which strips the offset), so they are
 * passed through verbatim.
 */
private fun org.json.JSONObject.toExecutionModel(): TaskExecutionInfo = TaskExecutionInfo(
    name = optString("Name"),
    key = optString("Key"),
    startTimeUtc = optString("StartTimeUtc").takeIf { it.isNotBlank() },
    endTimeUtc = optString("EndTimeUtc").takeIf { it.isNotBlank() },
    status = optString("Status").ifBlank { "Success" },
    errorMessage = optString("ErrorMessage").takeIf { it.isNotBlank() },
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
    // The SDK strips the offset during deserialization (see toIsoInstantString),
    // so a plain .toString() yields a bare LocalDateTime that can't be parsed as
    // OffsetDateTime — re-attach the zone here.
    startTimeUtc = startTimeUtc.toIsoInstantString(),
    endTimeUtc = endTimeUtc.toIsoInstantString(),
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

internal fun parseItemSortList(sortBy: String): List<org.jellyfin.sdk.model.api.ItemSortBy> {
    if (sortBy.isBlank()) return emptyList()
    return sortBy.split(",").mapNotNull { token ->
        val trimmed = token.trim()
        when (trimmed) {
            "SortName" -> org.jellyfin.sdk.model.api.ItemSortBy.SORT_NAME
            "DatePlayed" -> org.jellyfin.sdk.model.api.ItemSortBy.DATE_PLAYED
            "DateCreated" -> org.jellyfin.sdk.model.api.ItemSortBy.DATE_CREATED
            "DateLastContentAdded" -> org.jellyfin.sdk.model.api.ItemSortBy.DATE_LAST_CONTENT_ADDED
            "PlayCount" -> org.jellyfin.sdk.model.api.ItemSortBy.PLAY_COUNT
            "Random" -> org.jellyfin.sdk.model.api.ItemSortBy.RANDOM
            "PremiereDate" -> org.jellyfin.sdk.model.api.ItemSortBy.PREMIERE_DATE
            "ProductionYear" -> org.jellyfin.sdk.model.api.ItemSortBy.PRODUCTION_YEAR
            "CommunityRating" -> org.jellyfin.sdk.model.api.ItemSortBy.COMMUNITY_RATING
            else -> org.jellyfin.sdk.model.api.ItemSortBy.entries.find {
                it.serialName.equals(trimmed, ignoreCase = true) || it.name.equals(trimmed, ignoreCase = true)
            }
        }
    }
}

internal fun parseItemSortBy(sortBy: String): org.jellyfin.sdk.model.api.ItemSortBy? =
    parseItemSortList(sortBy).firstOrNull()

internal fun org.jellyfin.sdk.model.api.UserDto.toManagedUser() = ManagedUser(
    id = id.toString(),
    name = name ?: "",
    primaryImageTag = primaryImageTag,
    hasPassword = hasPassword,
    hasConfiguredPassword = hasConfiguredPassword,
    lastLoginDate = lastLoginDate?.toString(),
    lastActivityDate = lastActivityDate?.toString(),
    policy = policy?.toManagedPolicy() ?: ManagedUserPolicy(),
)

internal fun org.jellyfin.sdk.model.api.UserPolicy.toManagedPolicy() = ManagedUserPolicy(
    isAdministrator = isAdministrator,
    isHidden = isHidden,
    isDisabled = isDisabled,
    enableUserPreferenceAccess = enableUserPreferenceAccess,
    enableAllFolders = enableAllFolders,
    enabledFolders = (enabledFolders ?: emptyList()).map { it.toString() },
    enableMediaPlayback = enableMediaPlayback,
    enableAudioPlaybackTranscoding = enableAudioPlaybackTranscoding,
    enableVideoPlaybackTranscoding = enableVideoPlaybackTranscoding,
    enablePlaybackRemuxing = enablePlaybackRemuxing,
    enableContentDeletion = enableContentDeletion,
    enableContentDownloading = enableContentDownloading,
    enableLiveTvAccess = enableLiveTvAccess,
    enableLiveTvManagement = enableLiveTvManagement,
    enableRemoteControlOfOtherUsers = enableRemoteControlOfOtherUsers,
    enableRemoteAccess = enableRemoteAccess,
    maxParentalRating = maxParentalRating,
    maxParentalSubRating = maxParentalSubRating,
    maxActiveSessions = maxActiveSessions,
    loginAttemptsBeforeLockout = loginAttemptsBeforeLockout,
    enableCollectionManagement = enableCollectionManagement,
    enableSubtitleManagement = enableSubtitleManagement,
    forceRemoteSourceTranscoding = forceRemoteSourceTranscoding,
    enableSharedDeviceControl = enableSharedDeviceControl,
    remoteClientBitrateLimit = remoteClientBitrateLimit,
    syncPlayAccess = syncPlayAccess.toAppOption(),
    enableAllChannels = enableAllChannels,
    enabledChannels = (enabledChannels ?: emptyList()).map { it.toString() },
    enableAllDevices = enableAllDevices,
    enabledDevices = enabledDevices ?: emptyList(),
    enableContentDeletionFromFolders = enableContentDeletionFromFolders ?: emptyList(),
    blockUnratedItems = (blockUnratedItems ?: emptyList()).map { it.toAppOption() },
    allowedTags = allowedTags ?: emptyList(),
    blockedTags = blockedTags ?: emptyList(),
    accessSchedules = (accessSchedules ?: emptyList()).map { it.toAppSchedule() },
)

/**
 * Copies every editable [ManagedUserPolicy] field onto a full SDK
 * [UserPolicy], preserving bookkeeping fields (auth provider ids,
 * invalid-login count, etc.). [userId] is the target user's UUID,
 * required to reconstruct [AccessSchedule]s. Used by
 * [UserApiClientImpl.updateUserPolicy] so non-edited server state is never reset.
 */
internal fun org.jellyfin.sdk.model.api.UserPolicy.overlayWith(
    edited: ManagedUserPolicy,
    userId: String,
): org.jellyfin.sdk.model.api.UserPolicy = copy(
    isAdministrator = edited.isAdministrator,
    isHidden = edited.isHidden,
    isDisabled = edited.isDisabled,
    enableUserPreferenceAccess = edited.enableUserPreferenceAccess,
    enableAllFolders = edited.enableAllFolders,
    enabledFolders = edited.enabledFolders.map { it.toUUID() },
    enableMediaPlayback = edited.enableMediaPlayback,
    enableAudioPlaybackTranscoding = edited.enableAudioPlaybackTranscoding,
    enableVideoPlaybackTranscoding = edited.enableVideoPlaybackTranscoding,
    enablePlaybackRemuxing = edited.enablePlaybackRemuxing,
    enableContentDeletion = edited.enableContentDeletion,
    enableContentDownloading = edited.enableContentDownloading,
    enableLiveTvAccess = edited.enableLiveTvAccess,
    enableLiveTvManagement = edited.enableLiveTvManagement,
    enableRemoteControlOfOtherUsers = edited.enableRemoteControlOfOtherUsers,
    enableRemoteAccess = edited.enableRemoteAccess,
    maxParentalRating = edited.maxParentalRating,
    maxParentalSubRating = edited.maxParentalSubRating,
    maxActiveSessions = edited.maxActiveSessions,
    loginAttemptsBeforeLockout = edited.loginAttemptsBeforeLockout,
    enableCollectionManagement = edited.enableCollectionManagement,
    enableSubtitleManagement = edited.enableSubtitleManagement,
    forceRemoteSourceTranscoding = edited.forceRemoteSourceTranscoding,
    enableSharedDeviceControl = edited.enableSharedDeviceControl,
    remoteClientBitrateLimit = edited.remoteClientBitrateLimit,
    syncPlayAccess = edited.syncPlayAccess.toSdk(),
    enableAllChannels = edited.enableAllChannels,
    enabledChannels = edited.enabledChannels.map { it.toUUID() },
    enableAllDevices = edited.enableAllDevices,
    enabledDevices = edited.enabledDevices,
    enableContentDeletionFromFolders = edited.enableContentDeletionFromFolders,
    blockUnratedItems = edited.blockUnratedItems.map { it.toSdk() },
    allowedTags = edited.allowedTags,
    blockedTags = edited.blockedTags,
    accessSchedules = edited.accessSchedules.map { it.toSdk(userId.toUUID()) },
)

// --- SDK ↔ app enum/model mappers for the extended policy fields ---

private fun org.jellyfin.sdk.model.api.SyncPlayUserAccessType.toAppOption() = when (this) {
    org.jellyfin.sdk.model.api.SyncPlayUserAccessType.CREATE_AND_JOIN_GROUPS ->
        com.raulshma.jellyplay.core.model.SyncPlayAccessOption.CREATE_AND_JOIN
    org.jellyfin.sdk.model.api.SyncPlayUserAccessType.JOIN_GROUPS ->
        com.raulshma.jellyplay.core.model.SyncPlayAccessOption.JOIN_ONLY
    org.jellyfin.sdk.model.api.SyncPlayUserAccessType.NONE ->
        com.raulshma.jellyplay.core.model.SyncPlayAccessOption.NONE
}

private fun com.raulshma.jellyplay.core.model.SyncPlayAccessOption.toSdk() =
    when (this) {
        com.raulshma.jellyplay.core.model.SyncPlayAccessOption.CREATE_AND_JOIN ->
            org.jellyfin.sdk.model.api.SyncPlayUserAccessType.CREATE_AND_JOIN_GROUPS
        com.raulshma.jellyplay.core.model.SyncPlayAccessOption.JOIN_ONLY ->
            org.jellyfin.sdk.model.api.SyncPlayUserAccessType.JOIN_GROUPS
        com.raulshma.jellyplay.core.model.SyncPlayAccessOption.NONE ->
            org.jellyfin.sdk.model.api.SyncPlayUserAccessType.NONE
    }

private fun org.jellyfin.sdk.model.api.UnratedItem.toAppOption():
    com.raulshma.jellyplay.core.model.UnratedItemOption = when (this) {
    org.jellyfin.sdk.model.api.UnratedItem.BOOK -> com.raulshma.jellyplay.core.model.UnratedItemOption.BOOK
    org.jellyfin.sdk.model.api.UnratedItem.CHANNEL_CONTENT -> com.raulshma.jellyplay.core.model.UnratedItemOption.CHANNEL_CONTENT
    org.jellyfin.sdk.model.api.UnratedItem.LIVE_TV_CHANNEL -> com.raulshma.jellyplay.core.model.UnratedItemOption.LIVE_TV_CHANNEL
    org.jellyfin.sdk.model.api.UnratedItem.MOVIE -> com.raulshma.jellyplay.core.model.UnratedItemOption.MOVIE
    org.jellyfin.sdk.model.api.UnratedItem.MUSIC -> com.raulshma.jellyplay.core.model.UnratedItemOption.MUSIC
    org.jellyfin.sdk.model.api.UnratedItem.TRAILER -> com.raulshma.jellyplay.core.model.UnratedItemOption.TRAILER
    org.jellyfin.sdk.model.api.UnratedItem.SERIES -> com.raulshma.jellyplay.core.model.UnratedItemOption.SERIES
    // LIVE_TV_PROGRAM / OTHER are not exposed in the app UI; map to a safe default.
    else -> com.raulshma.jellyplay.core.model.UnratedItemOption.MOVIE
}

private fun com.raulshma.jellyplay.core.model.UnratedItemOption.toSdk(): org.jellyfin.sdk.model.api.UnratedItem =
    when (this) {
        com.raulshma.jellyplay.core.model.UnratedItemOption.BOOK -> org.jellyfin.sdk.model.api.UnratedItem.BOOK
        com.raulshma.jellyplay.core.model.UnratedItemOption.CHANNEL_CONTENT -> org.jellyfin.sdk.model.api.UnratedItem.CHANNEL_CONTENT
        com.raulshma.jellyplay.core.model.UnratedItemOption.LIVE_TV_CHANNEL -> org.jellyfin.sdk.model.api.UnratedItem.LIVE_TV_CHANNEL
        com.raulshma.jellyplay.core.model.UnratedItemOption.MOVIE -> org.jellyfin.sdk.model.api.UnratedItem.MOVIE
        com.raulshma.jellyplay.core.model.UnratedItemOption.MUSIC -> org.jellyfin.sdk.model.api.UnratedItem.MUSIC
        com.raulshma.jellyplay.core.model.UnratedItemOption.TRAILER -> org.jellyfin.sdk.model.api.UnratedItem.TRAILER
        com.raulshma.jellyplay.core.model.UnratedItemOption.SERIES -> org.jellyfin.sdk.model.api.UnratedItem.SERIES
    }

private fun org.jellyfin.sdk.model.api.AccessSchedule.toAppSchedule() =
    com.raulshma.jellyplay.core.model.UserAccessSchedule(
        id = id,
        dayOfWeek = dayOfWeek.serialName,
        startHour = startHour,
        endHour = endHour,
    )

private fun com.raulshma.jellyplay.core.model.UserAccessSchedule.toSdk(
    userId: org.jellyfin.sdk.model.UUID,
) = org.jellyfin.sdk.model.api.AccessSchedule(
    id = id,
    userId = userId,
    dayOfWeek = org.jellyfin.sdk.model.api.DynamicDayOfWeek.fromName(dayOfWeek),
    startHour = startHour,
    endHour = endHour,
)
