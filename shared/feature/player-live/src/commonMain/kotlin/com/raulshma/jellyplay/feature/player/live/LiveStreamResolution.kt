package com.raulshma.jellyplay.feature.player.live

import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.PlayMethod

/**
 * The live play-method policies that stay CLIENT-SIDE in the live feature:
 * the DIRECT_STREAM probe-override ([shouldIgnoreServerTranscodeVerdict]) —
 * the pre-request decision whether `resolvePlayable`'s verdict is
 * trustworthy at all under the user's [LiveStreamOption]. The fallback
 * ladder itself (fetchPlaybackInfo → first source → liveStreamId URL) and
 * its capability arms moved into the repository's
 * [com.raulshma.jellyplay.core.data.repository.PlaybackRepository.resolvePlayable]
 * seam (core:data's LiveStreamResolution), where they are the ONE
 * implementation the VOD ladder folds share too.
 */

/**
 * The DIRECT_STREAM probe-override (verbatim from the ViewModel's former
 * inline policy): `option == DIRECT_STREAM && resolved.playMethod ==
 * TRANSCODE` → ignore the server's verdict and re-request with
 * `forceLiveStreamFallback` so the repository runs the liveStreamId ladder
 * instead.
 *
 * When the user asked for Direct Stream but the server still resolved a
 * transcode, it's because the server's live-source probe failed
 * (TranscodeReasons=DirectPlayError) even though the tuner is opened and
 * readable. The tuner session is live, so build a direct-stream URL from
 * the liveStreamId instead; if the player genuinely can't decode it, the
 * caller's onPlayerError -> transcode fallback catches that. AUTO and
 * TRANSCODE options accept whatever the server picks, as does any
 * non-transcode verdict under DIRECT_STREAM.
 */
internal fun shouldIgnoreServerTranscodeVerdict(
    option: LiveStreamOption,
    playMethod: PlayMethod,
): Boolean = option == LiveStreamOption.DIRECT_STREAM && playMethod == PlayMethod.TRANSCODE
