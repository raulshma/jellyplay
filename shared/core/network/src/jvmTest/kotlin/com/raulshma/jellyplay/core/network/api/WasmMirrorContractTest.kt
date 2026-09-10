package com.raulshma.jellyplay.core.network.api

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The wasm↔JVM mirror contract (the drift ratchet): the wasmJs Ktor clients
 * ([KtorWasmLibraryApiClient], [KtorWasmUserApiClient]) hand-mirror the
 * jvmShared SDK clients ([LibraryApiClientImpl], [UserApiClientImpl])
 * request-for-request because the wasm target has no Jellyfin SDK — and the
 * wasmJsTest lane never executes in CI, so a per-endpoint drift (wrong path,
 * wrong query param name, an endpoint missing on one side) is invisible until
 * a wasm user hits it. This test reads both source files at runtime and pins
 * the mirror: the same endpoint method set, and per method the same wire
 * calls — verb, path template and query-parameter NAME set.
 *
 * Source-scanning like `SettingsCatalogScreenContractTest`
 * (feature/settings): walk up from `user.dir` to this module's root, then
 * assert against the sources, so the drift fails the JVM build instead of
 * shipping.
 *
 * The JVM side speaks through the Jellyfin SDK's typed setters, which hide
 * two wire facts the wasm side must hand-replicate; both live in
 * [sdkEndpoints] below, verified against jellyfin-api 1.8.12 sources
 * (org.jellyfin.sdk.api.operations.*; the SDK's UrlBuilder skips only null
 * values, so a NON-NULL default such as getItems' `enableImages = true` is
 * always on the wire even when the caller omits it):
 *  - which named args are {path} segments rather than query params
 *    (e.g. markPlayedItem's itemId);
 *  - which query params the SDK implicitly sends (e.g. getItems always adds
 *    enableTotalRecordCount/enableImages — the wasm `itemsEndpointDefaults`).
 *
 * Follow-ups (same machinery, not yet mirrored): the auth and playback wasm
 * client pairs. The ARR/Seerr/Tmdb wasm mirrors are OUT of scope for this
 * extraction — they use a different URL-builder idiom on both sides.
 */
class WasmMirrorContractTest {

    // ── Source access (the settings-contract precedent) ──────────────────

    /** This module's root (the nearest ancestor holding our source sets). */
    private val moduleRoot: File by lazy {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null && !File(dir, "src/jvmShared").isDirectory) dir = dir.parentFile
        assertTrue(
            File(dir, "src/jvmShared").isDirectory,
            "could not locate src/jvmShared from ${System.getProperty("user.dir")}",
        )
        dir
    }

    private fun source(relativePath: String): String =
        File(moduleRoot, relativePath).let { file ->
            assertTrue(file.isFile, "missing source file ${file.absolutePath}")
            stripComments(file.readText(Charsets.UTF_8))
        }

    // ── The wire model ───────────────────────────────────────────────────

    /** One HTTP call as the mirror policy pins it: verb, path, query names. */
    private data class WireCall(val verb: String, val path: String, val params: Set<String>) {
        override fun toString(): String = "$verb $path ${params.sorted().joinToString(",", "{", "}")}"
    }

    /**
     * A typed Jellyfin SDK call, resolved to its wire shape. [pathArgs] are
     * named args baked into the path (never query params); [implicitQuery]
     * are the params the SDK always sends because their defaults are
     * non-null (null or emptyList() defaults never reach the wire).
     */
    private data class SdkEndpoint(
        val verb: String,
        val path: String,
        val pathArgs: Set<String> = emptySet(),
        val implicitQuery: Set<String> = emptySet(),
    )

    /**
     * The SDK's typed-setter → wire mapping for every call the mirrored JVM
     * clients make. Named-arg names ARE the wire query names for every arg
     * these two clients pass (the SDK's put() keys equal its parameter
     * names); the one shape that differs elsewhere in the SDK (getItems
     * `is4k` → wire `is4K`) does not occur here — if a future call needs a
     * non-identity arg→wire rename, express it in this table rather than
     * weakening the comparison.
     */
    private val sdkEndpoints: Map<String, SdkEndpoint> = mapOf(
        // ── used by LibraryApiClientImpl ──────────────────────────────────
        "userLibraryApi.getLatestMedia" to
            SdkEndpoint("GET", "/Items/Latest", implicitQuery = setOf("limit", "groupItems")),
        "userLibraryApi.getItem" to
            SdkEndpoint("GET", "/Items/{itemId}", pathArgs = setOf("itemId")),
        "userLibraryApi.getIntros" to
            SdkEndpoint("GET", "/Items/{itemId}/Intros", pathArgs = setOf("itemId")),
        "userLibraryApi.getSpecialFeatures" to
            SdkEndpoint("GET", "/Items/{itemId}/SpecialFeatures", pathArgs = setOf("itemId")),
        "userLibraryApi.markFavoriteItem" to
            SdkEndpoint("POST", "/UserFavoriteItems/{itemId}", pathArgs = setOf("itemId")),
        "userLibraryApi.unmarkFavoriteItem" to
            SdkEndpoint("DELETE", "/UserFavoriteItems/{itemId}", pathArgs = setOf("itemId")),
        "tvShowsApi.getNextUp" to
            SdkEndpoint("GET", "/Shows/NextUp", implicitQuery = setOf("enableTotalRecordCount", "enableResumable", "enableRewatching")),
        "tvShowsApi.getSeasons" to
            SdkEndpoint("GET", "/Shows/{seriesId}/Seasons", pathArgs = setOf("seriesId")),
        "tvShowsApi.getEpisodes" to
            SdkEndpoint("GET", "/Shows/{seriesId}/Episodes", pathArgs = setOf("seriesId")),
        "itemsApi.getItems" to
            SdkEndpoint("GET", "/Items", implicitQuery = setOf("enableTotalRecordCount", "enableImages")),
        "itemsApi.getResumeItems" to
            SdkEndpoint("GET", "/UserItems/Resume", implicitQuery = setOf("enableTotalRecordCount", "enableImages", "excludeActiveSessions")),
        "userViewsApi.getUserViews" to
            SdkEndpoint("GET", "/UserViews", implicitQuery = setOf("includeHidden")),
        "genresApi.getGenres" to
            SdkEndpoint("GET", "/Genres", implicitQuery = setOf("enableImages", "enableTotalRecordCount")),
        "studiosApi.getStudios" to
            SdkEndpoint("GET", "/Studios", implicitQuery = setOf("enableImages", "enableTotalRecordCount")),
        "libraryApi.getSimilarItems" to
            SdkEndpoint("GET", "/Items/{itemId}/Similar", pathArgs = setOf("itemId")),
        "libraryApi.getThemeSongs" to
            SdkEndpoint("GET", "/Items/{itemId}/ThemeSongs", pathArgs = setOf("itemId"), implicitQuery = setOf("inheritFromParent")),
        "instantMixApi.getInstantMixFromItem" to
            SdkEndpoint("GET", "/Items/{itemId}/InstantMix", pathArgs = setOf("itemId")),
        "collectionApi.createCollection" to
            SdkEndpoint("POST", "/Collections", implicitQuery = setOf("isLocked")),
        "collectionApi.addToCollection" to
            SdkEndpoint("POST", "/Collections/{collectionId}/Items", pathArgs = setOf("collectionId")),
        "playlistsApi.createPlaylist" to
            SdkEndpoint("POST", "/Playlists"),
        "playlistsApi.updatePlaylist" to
            SdkEndpoint("POST", "/Playlists/{playlistId}", pathArgs = setOf("playlistId")),
        "playlistsApi.addItemToPlaylist" to
            SdkEndpoint("POST", "/Playlists/{playlistId}/Items", pathArgs = setOf("playlistId")),
        "playlistsApi.removeItemFromPlaylist" to
            SdkEndpoint("DELETE", "/Playlists/{playlistId}/Items", pathArgs = setOf("playlistId")),
        "playlistsApi.moveItem" to
            SdkEndpoint("POST", "/Playlists/{playlistId}/Items/{itemId}/Move/{newIndex}", pathArgs = setOf("playlistId", "itemId", "newIndex")),
        "playStateApi.markPlayedItem" to
            SdkEndpoint("POST", "/UserPlayedItems/{itemId}", pathArgs = setOf("itemId")),
        "playStateApi.markUnplayedItem" to
            SdkEndpoint("DELETE", "/UserPlayedItems/{itemId}", pathArgs = setOf("itemId")),
        // The local jvmShared LyricsApi wrapper (fetchLyrics) delegates to SDK
        // lyricsApi.getLyrics — keyed by the token that appears in the source.
        "lyricsApi.fetchLyrics" to
            SdkEndpoint("GET", "/Audio/{itemId}/Lyrics", pathArgs = setOf("itemId")),
        // ── used by UserApiClientImpl ─────────────────────────────────────
        "userApi.getUsers" to
            SdkEndpoint("GET", "/Users"),
        "userApi.getUserById" to
            SdkEndpoint("GET", "/Users/{userId}", pathArgs = setOf("userId")),
        "userApi.getCurrentUser" to
            SdkEndpoint("GET", "/Users/Me"),
        "userApi.createUserByName" to
            SdkEndpoint("POST", "/Users/New"),
        "userApi.updateUser" to
            SdkEndpoint("POST", "/Users"),
        "userApi.updateUserPolicy" to
            SdkEndpoint("POST", "/Users/{userId}/Policy", pathArgs = setOf("userId")),
        "userApi.updateUserPassword" to
            SdkEndpoint("POST", "/Users/Password"),
        "userApi.deleteUser" to
            SdkEndpoint("DELETE", "/Users/{userId}", pathArgs = setOf("userId")),
        "libraryApi.getMediaFolders" to
            SdkEndpoint("GET", "/Library/MediaFolders"),
        "localizationApi.getParentalRatings" to
            SdkEndpoint("GET", "/Localization/ParentalRatings"),
    )

    /** SDK calls that build a URL string but issue no wire request. */
    private val sdkUrlBuilders = setOf("imageApi.getItemImageUrl")

    /**
     * wasm path-template placeholder → SDK path-arg name, for the spots where
     * the wasm local variable is named for the DOMAIN concept while the SDK
     * names the same wire segment differently. Justified per entry; anything
     * not listed compares by exact placeholder name.
     */
    private val pathPlaceholderAliases: Map<String, String> = mapOf(
        // movePlaylistItem: SDK moveItem calls the moved segment {itemId}; the
        // wasm client (and the client interface) call the same id "entryId".
        "entryId" to "itemId",
    )

    /** Named args that are request bodies, never query params. */
    private val bodyArgs = setOf("data")

    /**
     * Accepted mirror divergences ("method" → justification), the settings
     * contract's exception-list pattern. An entry exempts the whole method
     * from the wire comparison; adding one needs a row-shaped justification
     * sentence, and a companion assertion below fails when an entry stops
     * describing a real method, so the list cannot rot into a place to hide
     * new drift. Currently EMPTY — the ratchet holds with no exceptions.
     */
    private val mirrorExceptions: Map<String, String> = emptyMap()

    // ── The mirrored pairs ───────────────────────────────────────────────

    private class MirrorPair(
        val label: String,
        val jvmRelativePath: String,
        val wasmRelativePath: String,
    )

    private val pairs = listOf(
        MirrorPair(
            label = "KtorWasmLibraryApiClient ↔ LibraryApiClientImpl",
            jvmRelativePath = "src/jvmShared/kotlin/com/raulshma/jellyplay/core/network/api/LibraryApiClientImpl.kt",
            wasmRelativePath = "src/wasmJsMain/kotlin/com/raulshma/jellyplay/core/network/api/KtorWasmLibraryApiClient.kt",
        ),
        MirrorPair(
            label = "KtorWasmUserApiClient ↔ UserApiClientImpl",
            jvmRelativePath = "src/jvmShared/kotlin/com/raulshma/jellyplay/core/network/api/UserApiClientImpl.kt",
            wasmRelativePath = "src/wasmJsMain/kotlin/com/raulshma/jellyplay/core/network/api/KtorWasmUserApiClient.kt",
        ),
    )

    private class MirrorAnalysis(
        val label: String,
        val jvmMembers: Map<String, List<WireCall>>,
        val wasmMembers: Map<String, List<WireCall>>,
    )

    /**
     * Both sides' per-member wire calls, computed once. Members are the
     * endpoint methods (`override fun`) plus the property initializers that
     * carry transport (e.g. the `emptyLibraryFallback` ladder's
     * getLatestMedia fetch, named `val:emptyLibraryFallback`) — non-
     * transporting overrides stay in with an empty call list, pinning "no
     * transport on either side"; non-transporting private vals (plain
     * caches/helpers) drop out.
     */
    private val analyses: List<MirrorAnalysis> by lazy {
        pairs.map { pair ->
            val jvmText = source(pair.jvmRelativePath)
            val wasmText = source(pair.wasmRelativePath)
            val itemsDefaults = wasmItemsEndpointDefaults(wasmText)
            MirrorAnalysis(
                label = pair.label,
                jvmMembers = memberBlocks(jvmText)
                    .filterValues { block -> !block.startsWith("    private val") || jvmHasTransport(block) }
                    .mapValues { (_, block) -> jvmWireCalls(block) },
                wasmMembers = memberBlocks(wasmText)
                    .filterValues { block -> !block.startsWith("    private val") || "apiUrl(" in block }
                    .mapValues { (_, block) -> wasmWireCalls(block, itemsDefaults) },
            )
        }
    }

    /** Every typed SDK call seen in the mirrored JVM sources. */
    private val usedSdkKeys: Set<String> by lazy {
        val seen = mutableSetOf<String>()
        for (pair in pairs) {
            Regex("\\b(\\w+Api)\\.(\\w+)\\s*\\(").findAll(source(pair.jvmRelativePath)).forEach { m ->
                seen.add("${m.groupValues[1]}.${m.groupValues[2]}")
            }
        }
        seen
    }

    // ── Extraction: members ──────────────────────────────────────────────

    /**
     * Class-body member name → member source block. Members are the 4-space
     * indented `override fun` / `private val|fun` declarations; a block runs
     * to the next member start. Transport-carrying properties are named
     * `val:<name>` so the pseudo-endpoint is compared like a method.
     */
    private fun memberBlocks(text: String): Map<String, String> {
        val starts = Regex("(?m)^    (?:override|private|internal|protected)\\b")
            .findAll(text).map { it.range.first }.toList()
        val blocks = mutableMapOf<String, String>()
        for ((i, start) in starts.withIndex()) {
            val end = starts.getOrNull(i + 1) ?: text.length
            val block = text.substring(start, end)
            val name = Regex("override(?: suspend)? fun (\\w+)\\(").find(block)?.groupValues?.get(1)
                ?: Regex("private val (\\w+)").find(block)?.groupValues?.get(1)?.let { "val:$it" }
                ?: continue
            blocks[name] = block
        }
        return blocks
    }

    /**
     * The wasm `itemsEndpointDefaults` list — the SDK's non-null
     * enableTotalRecordCount/enableImages getItems defaults, replicated by
     * the wasm client; parsed from its declaration so the test tracks the
     * client's own definition.
     */
    private fun wasmItemsEndpointDefaults(wasmText: String): Set<String> {
        val decl = Regex("private val itemsEndpointDefaults = listOf\\((.*?)\\)", RegexOption.DOT_MATCHES_ALL)
            .find(wasmText)?.groupValues?.get(1) ?: return emptySet()
        return queryKeys(decl)
    }

    // ── Extraction: wasm wire calls ──────────────────────────────────────

    private val wasmVerbs = mapOf(
        "getJson" to "GET",
        "getBytes" to "GET",
        "getBodyTextWithEmbyToken" to "GET",
        "postForJson" to "POST",
        "postStatusOnly" to "POST",
        "deleteStatusOnly" to "DELETE",
    )

    /**
     * Wire calls of one wasm member block. Query attribution: with a single
     * transport call in the block, every `"key" to …` pair in the block
     * belongs to it (the block-level `mutableListOf`/`.add` builders feed
     * exactly that call); with several calls, each call gets only the pairs
     * inside its own parens (the multi-call members all build their queries
     * inline). The `+ itemsEndpointDefaults` append is attributed per call
     * via its own parens (it is always written inline in `query = …`).
     */
    private fun wasmWireCalls(block: String, itemsDefaults: Set<String>): List<WireCall> {
        val transport = Regex(
            // one nesting level of type args: getJson<List<BaseItemDtoWire>>(
            "\\b(getJson|getBytes|getBodyTextWithEmbyToken|postForJson|postStatusOnly|deleteStatusOnly)\\s*(?:<(?:[^<>]|<[^<>]*>)+>)?\\s*\\(",
        )
        val sites = transport.findAll(block).toList()
        val singleCall = sites.size == 1
        return sites.mapNotNull { match ->
            val openParen = block.indexOf('(', match.range.endInclusive - 1)
            val args = balancedArgs(block, openParen) ?: return@mapNotNull null
            val path = Regex("apiUrl\\([^,]+,\\s*\"([^\"]*)\"\\)").find(args)?.groupValues?.get(1)
                ?: return@mapNotNull null
            // The SDK's non-null getItems defaults, replicated where the
            // client appends `+ itemsEndpointDefaults` to the query — either
            // inline in the call's parens or (getMediaItems style) on the
            // `val baseQuery` expression that feeds the block's single call.
            val sdkDefaults = if (
                "itemsEndpointDefaults" in args ||
                (singleCall && "itemsEndpointDefaults" in block)
            ) {
                itemsDefaults
            } else {
                emptySet()
            }
            WireCall(
                verb = wasmVerbs.getValue(match.groupValues[1]),
                path = normalizePath(path, wasmPlaceholders = true),
                params = queryKeys(if (singleCall) block else args) + sdkDefaults,
            )
        }
    }

    /** Query keys: every `"key" to …` pair (the only to-pair usage in these clients). */
    private fun queryKeys(text: String): Set<String> =
        Regex("\"([A-Za-z][A-Za-z0-9]*)\"\\s+to\\b").findAll(text).map { it.groupValues[1] }.toSet()

    // ── Extraction: JVM wire calls ───────────────────────────────────────

    private fun jvmHasTransport(block: String): Boolean =
        Regex("\\b\\w+Api\\.\\w+\\s*\\(").containsMatchIn(block) ||
            Regex("\\.get<").containsMatchIn(block) ||
            Regex("\\.request\\s*\\(").containsMatchIn(block)

    private fun jvmWireCalls(block: String): List<WireCall> {
        val calls = mutableListOf<WireCall>()

        // Typed SDK calls: receiver.apiName.method(named args…)
        for (match in Regex("\\b(\\w+Api)\\.(\\w+)\\s*\\(").findAll(block)) {
            val key = "${match.groupValues[1]}.${match.groupValues[2]}"
            if (key in sdkUrlBuilders) continue // URL builder — no wire request
            val endpoint = sdkEndpoints[key] ?: continue
            // The table-completeness test fails loudly on unknown calls;
            // skipping here keeps the per-method diff focused on divergence.
            val openParen = block.indexOf('(', match.range.endInclusive - 1)
            val args = balancedArgs(block, openParen).orEmpty()
            val queryNames = topLevelNamedArgs(args)
                .minus(endpoint.pathArgs)
                .minus(bodyArgs)
                .toSet() + endpoint.implicitQuery
            calls.add(WireCall(endpoint.verb, endpoint.path, queryNames))
        }

        // Raw SDK calls: get<T>(pathTemplate = …, queryParameters = mapOf(… to …))
        for (match in Regex("\\.get<[^>]+>\\s*\\(").findAll(block)) {
            val openParen = block.indexOf('(', match.range.endInclusive - 1)
            val args = balancedArgs(block, openParen) ?: continue
            val path = Regex("pathTemplate\\s*=\\s*\"([^\"]*)\"").find(args)?.groupValues?.get(1) ?: continue
            calls.add(WireCall("GET", normalizePath(path), queryKeys(args)))
        }

        // Raw SDK calls: request(method = HttpMethod.X, pathTemplate = …)
        for (match in Regex("\\.request\\s*\\(").findAll(block)) {
            val openParen = block.indexOf('(', match.range.endInclusive - 1)
            val args = balancedArgs(block, openParen) ?: continue
            val verb = Regex("method\\s*=\\s*HttpMethod\\.(\\w+)").find(args)?.groupValues?.get(1) ?: continue
            val path = Regex("pathTemplate\\s*=\\s*\"([^\"]*)\"").find(args)?.groupValues?.get(1) ?: continue
            calls.add(WireCall(verb, normalizePath(path), emptySet()))
        }

        return calls
    }

    // ── Text helpers ─────────────────────────────────────────────────────

    /** Removes // and /* */ comments while preserving string literals. */
    private fun stripComments(source: String): String = buildString {
        var i = 0
        var inString = false
        while (i < source.length) {
            val c = source[i]
            when {
                inString -> {
                    append(c)
                    if (c == '\\' && i + 1 < source.length) { append(source[i + 1]); i++ }
                    else if (c == '"') inString = false
                }
                c == '"' -> { inString = true; append(c) }
                c == '\'' -> { // char literal — copy through the closing quote
                    append(c)
                    var j = i + 1
                    while (j < source.length && source[j] != '\'') {
                        append(source[j])
                        if (source[j] == '\\') { j++; if (j < source.length) append(source[j]) }
                        j++
                    }
                    if (j < source.length) append(source[j])
                    i = j
                }
                c == '/' && i + 1 < source.length && source[i + 1] == '/' ->
                    while (i < source.length && source[i] != '\n') i++
                c == '/' && i + 1 < source.length && source[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < source.length && !(source[i] == '*' && source[i + 1] == '/')) i++
                    i++
                }
                else -> append(c)
            }
            i++
        }
    }

    /** The text between the balanced parens starting at [openParenIndex], or null. */
    private fun balancedArgs(text: String, openParenIndex: Int): String? {
        var depth = 0
        var i = openParenIndex
        var inString = false
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                if (c == '\\') i++
                else if (c == '"') inString = false
            } else when (c) {
                '"' -> inString = true
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return text.substring(openParenIndex + 1, i)
                }
            }
            i++
        }
        return null
    }

    /**
     * Named args at the TOP level of a call's argument list (depth 0,
     * comma-separated segments) — nested DTO constructors such as
     * UpdateUserPassword(currentPw = …) inside updateUserPassword(…) must
     * not leak in as phantom query names.
     */
    private fun topLevelNamedArgs(args: String): Set<String> {
        val names = mutableSetOf<String>()
        val segment = StringBuilder()
        var depth = 0
        var inString = false
        var i = 0
        fun flush() {
            Regex("^[\\s]*([a-zA-Z]\\w*)\\s+=\\s").find(segment)?.let { names.add(it.groupValues[1]) }
            segment.setLength(0)
        }
        while (i < args.length) {
            val c = args[i]
            when {
                inString -> {
                    segment.append(c)
                    if (c == '\\' && i + 1 < args.length) { segment.append(args[i + 1]); i++ }
                    else if (c == '"') inString = false
                }
                c == '"' -> { inString = true; segment.append(c) }
                c == '(' || c == '{' -> { depth++; segment.append(c) }
                c == ')' || c == '}' -> { depth--; segment.append(c) }
                c == ',' && depth == 0 -> flush()
                else -> segment.append(c)
            }
            i++
        }
        flush()
        return names
    }

    /** `/Items/$itemId` → `/Items/{itemId}`; ensures the leading slash. */
    private fun normalizePath(raw: String, wasmPlaceholders: Boolean = false): String {
        val slashPrefixed = if (raw.startsWith("/")) raw else "/$raw"
        return Regex("\\$\\{?([A-Za-z][A-Za-z0-9]*)\\}?").replace(slashPrefixed) { match ->
            val name = match.groupValues[1]
            // wasm local placeholder names fold onto the SDK path-arg names
            // via the documented alias table; unknown names stay as written.
            val canonical = if (wasmPlaceholders) pathPlaceholderAliases[name] ?: name else name
            "{$canonical}"
        }
    }

    // ── The contract ─────────────────────────────────────────────────────

    @Test
    fun `the source scans found the mirrored endpoints (guard against a broken scan)`() {
        for (analysis in analyses) {
            val jvmCallCount = analysis.jvmMembers.values.sumOf { it.size }
            val wasmCallCount = analysis.wasmMembers.values.sumOf { it.size }
            assertTrue(
                analysis.jvmMembers.size >= 10 && analysis.wasmMembers.size >= 10 &&
                    jvmCallCount >= 12 && wasmCallCount >= 12,
                "${analysis.label}: scan is broken — found ${analysis.jvmMembers.size}/${analysis.wasmMembers.size} " +
                    "members and $jvmCallCount/$wasmCallCount wire calls (an empty scan would vacuously pass " +
                    "every parity assertion above).",
            )
        }
    }

    @Test
    fun `each wasm mirror implements exactly the jvm client's endpoint method set`() {
        for (analysis in analyses) {
            val jvmOnly = analysis.jvmMembers.keys - analysis.wasmMembers.keys
            val wasmOnly = analysis.wasmMembers.keys - analysis.jvmMembers.keys
            assertTrue(
                jvmOnly.isEmpty() && wasmOnly.isEmpty(),
                "${analysis.label}: endpoint method sets diverged — JVM-only=$jvmOnly WASM-only=$wasmOnly. " +
                    "Every endpoint must exist on both sides (an override with no transport on either side is " +
                    "fine; a method present on only one side is a missing endpoint).",
            )
        }
    }

    @Test
    fun `each wasm mirror matches the jvm wire calls per method`() {
        val failures = mutableListOf<String>()
        for (analysis in analyses) {
            for (method in analysis.jvmMembers.keys.sorted()) {
                if (method in mirrorExceptions) continue
                val expected = analysis.jvmMembers.getValue(method).sortedBy { it.toString() }
                val actual = analysis.wasmMembers.getValue(method).sortedBy { it.toString() }
                if (expected != actual) failures.add(describeDivergence(analysis.label, method, expected, actual))
            }
        }
        assertEquals(
            emptyList(),
            failures,
            "wasm mirror diverged from the JVM client (wrong path/verb, wrong query param names, " +
                "or a missing/extra call):",
        )
    }

    /** Names the method and the diverging artifact (paths vs param sets), not a bare set inequality. */
    private fun describeDivergence(
        label: String,
        method: String,
        expected: List<WireCall>,
        actual: List<WireCall>,
    ): String {
        val header = "\n      $label :: $method"
        val expSignatures = expected.map { "${it.verb} ${it.path}" }.sorted()
        val actSignatures = actual.map { "${it.verb} ${it.path}" }.sorted()
        if (expSignatures != actSignatures) {
            return "$header — endpoint path/verb divergence:\n" +
                "        JVM:  $expSignatures\n" +
                "        WASM: $actSignatures"
        }
        val paramDiffs = expected.map { jvmCall ->
            val wasmParams = actual
                .filter { it.verb == jvmCall.verb && it.path == jvmCall.path }
                .flatMap { it.params }
                .toSet()
            val missingOnWasm = jvmCall.params - wasmParams
            val extraOnWasm = wasmParams - jvmCall.params
            if (missingOnWasm.isEmpty() && extraOnWasm.isEmpty()) {
                ""
            } else {
                "\n        ${jvmCall.verb} ${jvmCall.path}: missingOnWasm=$missingOnWasm extraOnWasm=$extraOnWasm"
            }
        }.filter { it.isNotEmpty() }
        return "$header — query parameter divergence (names the JVM wire carries vs the wasm hand-build):" +
            paramDiffs.joinToString("")
    }

    @Test
    fun `the sdk endpoint table covers every typed call the mirrored jvm clients make`() {
        val unknown = usedSdkKeys - sdkEndpoints.keys - sdkUrlBuilders
        assertTrue(
            unknown.isEmpty(),
            "SDK calls missing from sdkEndpoints (path/implicit-default mapping unverified): $unknown — " +
                "add each entry (verified against the org.jellyfin.sdk.api.operations sources) or the " +
                "mirror is silently under-pinned for those endpoints.",
        )
        val stale = sdkEndpoints.keys + sdkUrlBuilders - usedSdkKeys
        assertTrue(
            stale.isEmpty(),
            "stale sdkEndpoints/urlBuilders entries no longer used by the mirrored JVM clients: $stale — " +
                "remove them so the table cannot rot.",
        )
    }

    @Test
    fun `no stale mirror exception entries`() {
        val allMethods = analyses.flatMap { it.jvmMembers.keys }.toSet()
        val stale = mirrorExceptions.keys - allMethods
        assertTrue(stale.isEmpty(), "mirrorExceptions names methods that no longer exist: $stale")
    }
}
