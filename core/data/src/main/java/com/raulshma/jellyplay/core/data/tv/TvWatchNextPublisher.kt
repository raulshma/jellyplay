package com.raulshma.jellyplay.core.data.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.deeplink.DeepLinkGrammar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Publishes Continue Watching and Next Up items to the Android TV home screen's
 * "Watch Next" OS row via the TvContractCompat provider.
 *
 * Behaviour:
 * - Skip publishing entirely on devices without the leanback feature.
 * - Diff the latest items against what's already in the Watch Next row:
 *   delete removed items, keep ones the user explicitly dismissed
 *   (`isBrowsable == false`), and insert new ones.
 * - Persist dismissed item IDs to SharedPreferences so we don't republish them.
 *
 * This class is TV-only by design. Calling it on a phone is a no-op because
 * the [Context.featureEnabled] check below will short-circuit early.
 */
class TvWatchNextPublisher(
    private val context: Context,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    suspend fun publish(): Result<Unit> = runCatching {
        if (!isTv()) return@runCatching

        val sections = mediaRepository.getHomeSections(
            HomeSectionQuery(
                enabledSections = setOf(HomeSectionType.CONTINUE_WATCHING, HomeSectionType.NEXT_UP),
            ),
        ).getOrThrow().sections

        val continueWatching = sections.firstOrNull { it.type == HomeSectionType.CONTINUE_WATCHING }?.items.orEmpty()
        val nextUp = sections.firstOrNull { it.type == HomeSectionType.NEXT_UP }?.items.orEmpty()

        // Build a unique, ordered candidate list — Continue Watching first,
        // then Next Up items not already present.
        val seen = mutableSetOf<String>()
        val candidates = (continueWatching + nextUp)
            .filter { seen.add(it.id) }
            .take(MAX_ITEMS)

        val existing = queryExistingPrograms()
        val existingIds = existing.map { it.internalProviderId }.toSet()

        // Remember items the user removed from the Watch Next row so we don't
        // re-add them on every refresh.
        val savedRemovedIds = prefs.getStringSet(KEY_USER_REMOVED, emptySet())?.toMutableSet() ?: mutableSetOf()
        val userRemovedIds = existing.filterNot { it.isBrowsable }
            .mapNotNull { it.internalProviderId }

        var prefsChanged = false
        for (id in userRemovedIds) {
            if (savedRemovedIds.add(id)) {
                prefsChanged = true
            }
        }
        if (prefsChanged) {
            prefs.edit(false) {
                putStringSet(KEY_USER_REMOVED, savedRemovedIds)
            }
        }

        // Remove programs no longer in candidates (or revived by the user).
        val candidateIds = candidates.map { it.id }.toSet()
        existing
            .filter { prog ->
                val id = prog.internalProviderId ?: return@filter false
                id !in candidateIds || id in savedRemovedIds
            }
            .forEach { prog ->
                context.contentResolver.delete(
                    TvContractCompat.buildWatchNextProgramUri(prog.id),
                    null,
                    null,
                )
            }

        // Update existing candidates that are already in the row and weren't dismissed.
        val existingMap = existing.associateBy { it.internalProviderId }
        val toUpdate = candidates.filter { it.id in existingIds && it.id !in savedRemovedIds }
        toUpdate.forEach { candidate ->
            val existingProg = existingMap[candidate.id]
            if (existingProg != null) {
                val updatedProgram = convert(candidate)
                context.contentResolver.update(
                    TvContractCompat.buildWatchNextProgramUri(existingProg.id),
                    updatedProgram.toContentValues(),
                    null,
                    null,
                )
            }
        }

        // Insert new candidates that aren't already in the row and weren't dismissed.
        val toAdd = candidates.filter { it.id !in existingIds && it.id !in savedRemovedIds }
        if (toAdd.isNotEmpty()) {
            val values = toAdd.map { convert(it).toContentValues() }.toTypedArray()
            context.contentResolver.bulkInsert(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                values,
            )
        }
    }

    suspend fun clear(): Result<Unit> = runCatching {
        if (!isTv()) return@runCatching
        val existing = queryExistingPrograms()
        existing.forEach { prog ->
            context.contentResolver.delete(
                TvContractCompat.buildWatchNextProgramUri(prog.id),
                null,
                null,
            )
        }
        prefs.edit(false) {
            remove(KEY_USER_REMOVED)
        }
    }

    private suspend fun queryExistingPrograms(): List<WatchNextProgram> = withContext(Dispatchers.IO) {
        context.contentResolver
            .query(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                WatchNextProgram.PROJECTION,
                null,
                null,
                null,
            )?.use { cursor ->
                buildList {
                    if (cursor.moveToFirst()) {
                        do {
                            add(WatchNextProgram.fromCursor(cursor))
                        } while (cursor.moveToNext())
                    }
                }
            } ?: emptyList()
    }

    private fun convert(item: MediaItem): WatchNextProgram = WatchNextProgram.Builder().apply {
        setInternalProviderId(item.id)

        val type = when (item.mediaType) {
            MediaType.EPISODE -> TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE
            MediaType.MOVIE -> TvContractCompat.WatchNextPrograms.TYPE_MOVIE
            else -> TvContractCompat.WatchNextPrograms.TYPE_CLIP
        }
        setType(type)

        val resumeTicks = item.playbackPositionTicks ?: 0L
        if (resumeTicks >= MIN_RESUME_TICKS) {
            setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            setLastPlaybackPositionMillis((resumeTicks / TICKS_PER_MS).toInt())
        } else {
            setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT)
        }

        item.runTimeTicks?.let {
            setDurationMillis((it / TICKS_PER_MS).toInt())
        }

        setLastEngagementTimeUtcMillis(System.currentTimeMillis())
        setTitle(item.name)
        setDescription(item.overview)
        if (item.mediaType == MediaType.EPISODE) {
            item.episodeNumber?.let(::setEpisodeNumber)
            item.seasonNumber?.let(::setSeasonNumber)
        }
        setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9)

        // Use the authenticated image URL so the system can fetch the artwork.
        val artworkUri = playbackRepository.getBackdropUrl(item.id, BACKDROP_WIDTH).toUri()
        setPosterArtUri(artworkUri)

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DeepLinkGrammar.mediaLink(item.id)))
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        setIntent(intent)
    }.build()

    private fun isTv(): Boolean = try {
        context.packageManager.hasSystemFeature("android.software.leanback")
    } catch (_: Exception) {
        false
    }

    companion object {
        private const val PREFS_NAME = "tv_watch_next"
        private const val KEY_USER_REMOVED = "user_removed_ids"
        private const val MAX_ITEMS = 16
        private const val MIN_RESUME_TICKS = 20_000_000L // 2 seconds
        private const val TICKS_PER_MS = 10_000L
        private const val BACKDROP_WIDTH = 1280
    }
}
