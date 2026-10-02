package com.raulshma.jellyplay.deeplink

/**
 * What [parseSharedText] decided an ACTION_SEND shared-text payload wants.
 * `Empty` means the payload carries nothing searchable (the shell surfaces a
 * one-shot message); `Search` is the query to run (a bare URL or plain
 * text); `MediaDetail` is a Jellyfin media URL whose item id opens directly.
 */
internal sealed interface SharedTextTarget {
    data object Empty : SharedTextTarget
    data class Search(val query: String) : SharedTextTarget
    data class MediaDetail(val mediaId: String) : SharedTextTarget
}

/**
 * The pure fold over an ACTION_SEND shared-text payload — moved verbatim out
 * of MainViewModel (it was a private fold + sealed class inside the
 * 18-constructor-dep shell ViewModel, untestable without instantiating the
 * graph). Arm order is load-bearing, mirroring the former member: a
 * `jellyfin://media/<uuid>` URL anywhere in the text wins (opens
 * MediaDetail), then any http(s) URL (searched verbatim), then the whole
 * payload as a search query when non-blank, else [SharedTextTarget.Empty].
 *
 * `internal` + top-level (beside [IncomingIntentRequest], the same
 * deeplink-package fold family): MainViewModel delegates to it, and the
 * plain-JVM test beside it exercises the fold without Android or Robolectric
 * machinery.
 */
internal fun parseSharedText(text: String): SharedTextTarget {
    val jellyfinUrlMatch = JELLYFIN_MEDIA_URL_REGEX.find(text)
    if (jellyfinUrlMatch != null) {
        return SharedTextTarget.MediaDetail(jellyfinUrlMatch.groupValues[1])
    }
    val urlMatch = ANY_URL_REGEX.find(text)
    if (urlMatch != null) {
        return SharedTextTarget.Search(urlMatch.value)
    }
    return text.takeIf { it.isNotBlank() }
        ?.let(SharedTextTarget::Search)
        ?: SharedTextTarget.Empty
}

private val JELLYFIN_MEDIA_URL_REGEX = Regex("""jellyfin://media/([a-f0-9-]+)""")
private val ANY_URL_REGEX = Regex("""https?://[^\s]+""")
