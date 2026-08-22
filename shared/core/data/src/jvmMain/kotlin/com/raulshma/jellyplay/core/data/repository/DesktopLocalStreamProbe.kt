package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.MediaStream

/**
 * Desktop implementation of the [LocalStreamProbe] seam: probing the
 * container's track inventory requires a media framework (MediaExtractor /
 * ffprobe) that the desktop target does not wire up yet, so the probe is
 * unsupported and always reports "no badges" — the documented graceful
 * degradation of the seam. A desktop probe (ffprobe-shaped) lands with the
 * Phase V2 player work.
 */
class DesktopLocalStreamProbe : LocalStreamProbe {
    override suspend fun probe(videoFilePath: String): List<MediaStream> = emptyList()
}
