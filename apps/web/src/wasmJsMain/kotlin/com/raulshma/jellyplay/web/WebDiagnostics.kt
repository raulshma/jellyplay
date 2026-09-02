package com.raulshma.jellyplay.web

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.isVideoType
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.network.api.PlaybackApiClient
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import com.raulshma.jellyplay.web.player.HtmlVideoEngine
import kotlinx.browser.document
import kotlinx.coroutines.delay
import org.koin.core.context.GlobalContext
import org.w3c.dom.Element

/**
 * E2E verification surface (wave 13C), gated behind manual navigation — the
 * ConnectedCard's "Diagnostics" button is the ONLY entry point, so the shell's
 * behavior is unchanged unless a human (or the CDP driver,
 * tools/e2e/web-verify.mjs) deliberately opens it.
 *
 * Purpose: close the two "compile-only, never rendered in a browser" gaps the
 * plan tracked for web —
 *  a) IMAGE: Coil artwork loading through the app-wide ImageLoader
 *     (Main.kt's KtorNetworkFetcherFactory singleton) against a REAL
 *     Jellyfin server. Rendered twice by design: the shared
 *     [MediaImage] composable (what feature screens will actually call) and
 *     a raw [rememberAsyncImagePainter]-driven [Image] whose painter STATE
 *     feeds the machine-readable `IMAGE_STATE:` line. The state source is a
 *     REAL RENDERED image — an undrawn painter never receives layout
 *     constraints, so Coil would never resolve its request.
 *  b) VIDEO: [HtmlVideoEngine] attached to a fixed-rect overlay host div and
 *     driven through the real MediaEngine surface (load/play/pause/seekTo)
 *     against `PlaybackApiClient.getStreamUrl` output, with an
 *     `ENGINE_STATE:` line + transport buttons the CDP driver can click via
 *     the accessibility tree.
 *
 * `DIAG_OVERALL:` flips PENDING → OK only when the image DECODED (painter
 * Success) AND the engine reported playing with position > 0; the CDP driver
 * asserts exactly that string (plus zero console errors) for a green run.
 *
 * Wave 18A adds the Coil observability lines `COIL_STATS: hits=<n>
 * misses=<n> net=<n> fail=<n>` and `COIL_CACHE: size=<n> maxSize=<n>`
 * (process-lifetime totals from Main.kt's [CoilStats]; refreshed on a 500ms
 * poll, the same idiom as VideoCheck's position mirror) so the long-session
 * soak lane can watch cache hits, network fetches, and failures across
 * cycles. `COIL_CACHE: none` renders before the image loader singleton has
 * resolved.
 *
 * Wave 20B appends the eviction/foreign-host cards (bottom of the pane —
 * AFTER the Back button, so no existing element moves and the older lanes'
 * click geometry is untouched):
 *  c) CACHE PROBE: enumerates the library's video items and loads each
 *     item's Primary poster SEQUENTIALLY at full decode size through the
 *     app-wide loader. Per settled item it appends the audit line
 *     `CACHE_PROBE: idx=<i>/<n> item=<name> state=<OK|ERR>`; the status line
 *     ends `CACHE_PROBE: done ok=<k> err=<m>`. `CACHE_REVISIT: state=OK|ERR`
 *     re-requests item[0]'s poster — after a probe pass that evicted it, the
 *     revisit's hit/miss outcome is read from COIL_STATS deltas by the lane
 *     (a MISS delta proves LRU eviction; the pane deliberately does NOT
 *     duplicate that signal).
 *  d) FOREIGN HOST: loads an image from a NON-Jellyfin origin passed as the
 *     gated `?foreignImage=<url>` boot param (absent → `FOREIGN_HOST:
 *     skipped (no param)`; no human surface ever sets it) — both the raw
 *     painter (state source) and the shared [MediaImage] pipeline, proving
 *     Coil's ktor3 fetcher handles cross-origin fetches when the origin
 *     sends CORS headers.
 *
 * Deliberately NOT a feature screen: no error taxonomy, no i18n, no design
 * polish. The ALL-CAPS text lines are load-bearing strings for the drivers —
 * do not reword them without updating tools/e2e/web-verify.mjs (asserts
 * IMAGE_STATE/DIAG_OVERALL and, since 18A, the presence + shape of
 * COIL_STATS) and tools/e2e/web-soak.mjs (parses COIL_STATS/COIL_CACHE).
 */
@Composable
internal fun WebDiagnosticsPane(
    onBack: () -> Unit,
    onOpenSeerrDetailDemo: () -> Unit = {},
) {
    val library = remember { GlobalContext.get().get<LibraryApiClient>() }
    val playback = remember { GlobalContext.get().get<PlaybackApiClient>() }

    var item by remember { mutableStateOf<MediaItem?>(null) }
    var loadLine by remember { mutableStateOf("LOADING: querying library…") }

    // Aggregated verdict inputs (hoisted here so one line can see both).
    var imageOk by remember { mutableStateOf(false) }
    var imageFailed by remember { mutableStateOf(false) }
    var engineLive by remember { mutableStateOf(false) }
    var engineFailed by remember { mutableStateOf(false) }

    // One library read: the first top-level video item (the harness server
    // carries a single ~12s movie; real servers just hand back whichever
    // item sorts first — any playable item proves the pipeline).
    LaunchedEffect(library) {
        val result = library.getMediaItems(parentId = null, limit = 10)
        result
            .onSuccess { search ->
                val first = search.items.firstOrNull { it.mediaType.isVideoType }
                    ?: search.items.firstOrNull()
                if (first == null) {
                    loadLine = "LOAD_ERR: library returned no items"
                } else {
                    item = first
                    loadLine = "ITEM: ${first.name}"
                }
            }
            .onFailure { failure ->
                loadLine = "LOAD_ERR: ${failure.message ?: failure::class.simpleName}"
            }
    }

    val overall = when {
        imageFailed -> "DIAG_OVERALL: FAIL (image error)"
        engineFailed -> "DIAG_OVERALL: FAIL (engine error)"
        item == null && loadLine.startsWith("LOAD_ERR") -> "DIAG_OVERALL: FAIL (library)"
        imageOk && engineLive -> "DIAG_OVERALL: OK"
        else -> "DIAG_OVERALL: PENDING"
    }

    // Wave 18A: Coil counters are plain process-lifetime Ints outside
    // Compose state, so the lines refresh on a 500ms poll rather than
    // waiting for an unrelated recomposition (same idiom as VideoCheck's
    // position mirror). Drivers gate on IMAGE_STATE: OK first, which orders
    // every counter mutation of a pane visit before their reads.
    var coilStatsLine by remember { mutableStateOf(CoilStats.axStatsLine()) }
    var coilCacheLine by remember { mutableStateOf(CoilStats.axCacheLine()) }
    LaunchedEffect(Unit) {
        while (true) {
            coilStatsLine = CoilStats.axStatsLine()
            coilCacheLine = CoilStats.axCacheLine()
            delay(500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Web diagnostics",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(loadLine)
        item?.let { loaded ->
            ImageCheck(
                item = loaded,
                library = library,
                onState = { ok, failed ->
                    imageOk = ok
                    imageFailed = failed
                },
            )
            VideoCheck(
                item = loaded,
                playback = playback,
                onLive = { live, failed ->
                    engineLive = live
                    engineFailed = failed
                },
            )
        }
        Text(
            text = overall,
            style = MaterialTheme.typography.titleLarge,
            color = if (overall.endsWith("OK")) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        // Wave 18A Coil observability (load-bearing strings — see the
        // strings-contract note in this file's KDoc).
        Text(
            text = coilStatsLine,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = coilCacheLine,
            style = MaterialTheme.typography.bodySmall,
        )
        // Wave 16C E2E surface: pushes Route.SeerrDetail(550, "movie") so a
        // HUMAN can drive the real shared SeerrDetailScreen from the shell
        // (details' wasmJs target; the SeerrDetailViewModel DI graph resolves
        // on web — narrow MediaRepository included) without a Seerr server:
        // the fixture's requests list is empty ("Seerr not configured"), so
        // nothing there is clickable. The CDP lane does NOT use this button —
        // not because clicks fail (wave 17A's clean-room probe measured
        // synthetic delivery working to the viewport bottom; the wave-16
        // "dead region" was that wave's SeerrDetailViewModel construction
        // crash freezing the UI after the click landed — see
        // docs/e2e/web-input-dead-region.md) but because a boot param
        // decouples the lane from click geometry entirely: it boots into the
        // route via the gated ?e2eRoute= param instead (WebAppRoot's
        // backStack note). Gated like everything else in this pane.
        Button(onClick = onOpenSeerrDetailDemo) {
            Text("SeerrDetail (demo)")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
            Text("Back")
        }
        // Wave 20B cards render BELOW the Back button on purpose: a Column
        // sibling appended after an existing element cannot move anything
        // above it, so the older lanes' (web-verify/web-soak) click geometry
        // on the buttons above is provably unchanged. The eviction lane runs
        // in a taller window (and can wheel-scroll) to reach these.
        CacheProbeCheck(library = library)
        ForeignHostCheck()
    }
}

// ── a) Image pipeline check ────────────────────────────────────────────────

@Composable
private fun ImageCheck(
    item: MediaItem,
    library: LibraryApiClient,
    onState: (ok: Boolean, failed: Boolean) -> Unit,
) {
    val url = remember(item.id) { library.getImageUrl(item.id, "Primary", maxWidth = 300) }
    val context = LocalPlatformContext.current
    val request = remember(url) { ImageRequest.Builder(context).data(url).build() }
    // Rendered Image (NOT just a painter) so Coil actually resolves + draws:
    // an undrawn painter never receives layout constraints and stays Empty.
    val painter = rememberAsyncImagePainter(model = request)
    // coil 3.4.0: AsyncImagePainter.state is a StateFlow<State>, not a
    // plain property — collect it so recomposition follows decode progress.
    val painterState by painter.state.collectAsState()

    LaunchedEffect(painterState) { onState(painterState is AsyncImagePainter.State.Success, painterState is AsyncImagePainter.State.Error) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("IMAGE (Coil → Jellyfin Primary artwork)", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(
                    painter = painter,
                    contentDescription = "diagnostics artwork painter",
                    modifier = Modifier.size(width = 300.dp, height = 170.dp),
                    contentScale = ContentScale.Crop,
                )
                MediaImage(
                    url = url,
                    contentDescription = "diagnostics artwork MediaImage",
                    modifier = Modifier.size(width = 120.dp, height = 68.dp),
                )
            }
            Text(
                text = "IMAGE_URL: ${url.ifBlank { "<blank>" }}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "IMAGE_STATE: ${painterState.axLabel()}",
                style = MaterialTheme.typography.bodyLarge,
                color = when (painterState) {
                    is AsyncImagePainter.State.Error -> MaterialTheme.colorScheme.error
                    is AsyncImagePainter.State.Success -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private fun AsyncImagePainter.State.axLabel(): String = when (this) {
    is AsyncImagePainter.State.Success -> "OK"
    is AsyncImagePainter.State.Error -> "ERR"
    else -> "LOADING"
}

// ── b) Video engine check ──────────────────────────────────────────────────

@Composable
private fun VideoCheck(
    item: MediaItem,
    playback: PlaybackApiClient,
    onLive: (live: Boolean, failed: Boolean) -> Unit,
) {
    val engine = remember(item.id) { HtmlVideoEngine() }
    var streamLine by remember { mutableStateOf<String?>(null) }
    var positionMs by remember { mutableStateOf(0L) }
    val playbackState by engine.playbackState.collectAsState()
    val isPlaying by engine.isPlaying.collectAsState()

    DisposableEffect(item.id) {
        // Fixed-rect overlay host (HtmlVideoEngine KDoc: a player UI must
        // supply a DOM host layer via attachTo). pointer-events none so the
        // overlay never swallows canvas input; zIndex above the canvas so
        // the video is visible in screenshots.
        val host = document.createElement("div")
        styleVideoHost(host)
        document.body!!.appendChild(host)
        engine.attachTo(host)
        // Muted for autoplay reliability (no user gesture precedes load()).
        engine.setMuted(true)

        val streamUrl = playback.getStreamUrl(itemId = item.id, mediaSourceId = item.id)
        if (streamUrl.isNotBlank()) {
            engine.load(
                PlaybackRequest(
                    uri = streamUrl,
                    title = item.name,
                    // runTimeTicks → ms; 0 when the server reported none.
                    serverDurationMs = (item.runTimeTicks ?: 0L) / 10_000L,
                ),
            )
            engine.play()
        } else {
            streamLine = "ENGINE_ERR: stream URL blank (no connected session?)"
        }
        onDispose {
            engine.release()
            host.parentNode?.removeChild(host)
        }
    }

    // Position mirror for the state line: poll currentPositionMs (the
    // synchronous scalar) every 500ms — deliberately independent of
    // positionFlow so the line also exercises the ticker-free getter path.
    LaunchedEffect(engine) {
        while (true) {
            positionMs = engine.currentPositionMs
            delay(500)
        }
    }

    LaunchedEffect(isPlaying, positionMs, playbackState) {
        onLive(isPlaying && positionMs > 0L, playbackState == EnginePlaybackState.ERROR)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("VIDEO (HtmlVideoEngine <video>)", style = MaterialTheme.typography.titleMedium)
            Text("ENGINE_STATE: $playbackState playing=$isPlaying pos=${positionMs / 1000.0}s")
            streamLine?.let { line ->
                Text(
                    text = line,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { engine.play() }) { Text("Play") }
                Button(onClick = { engine.pause() }) { Text("Pause") }
                Button(onClick = { engine.seekTo(positionMs + 5_000L) }) { Text("Seek +5s") }
            }
        }
    }
}

// ── c) Wave 20B: memory-cache eviction probe ───────────────────────────────

/**
 * The wave-20B cache probe. "Probe all" loads every enumerated video item's
 * Primary poster SEQUENTIALLY through the app-wide loader at FULL decode
 * size ([Size.ORIGINAL] — the fixture's probe posters are sized so 8 decoded
 * entries exceed the measured 80,530,636-byte memory-cache cap; a
 * constraint-sampled thumbnail would dodge the eviction the card exists to
 * prove). One request at a time: the next [ProbeImage] only composes after
 * the previous settled (plus a settle beat so the pane's COIL_STATS poll
 * observes each item's counters before the next starts).
 *
 * Load-bearing strings (the eviction lane, tools/e2e/web-cache-eviction.mjs):
 *  - per settled item: `CACHE_PROBE: idx=<i>/<n> item=<name> state=<OK|ERR>`
 *    (the log RENDERS persistently, so the driver reads the full audit trail
 *    at the end — no polling race can lose an intermediate line);
 *  - status: `CACHE_PROBE: idle n=<count>` / `running idx=<i>/<n>` /
 *    `done ok=<k> err=<m>` (terminal condition: `done`);
 *  - revisit: `CACHE_REVISIT: state=<OK|ERR>` after clicking "Revisit #1".
 *
 * The pane deliberately does NOT report hit/miss for the revisit itself —
 * that signal is a COIL_STATS misses-delta the lane computes (counting it
 * here too would duplicate Main.kt's [CoilStats] source of truth).
 */
@Composable
private fun CacheProbeCheck(library: LibraryApiClient) {
    // Enumerate once per pane entry (poster inventory; nothing loads until a
    // button is clicked — zero behavior change for the older lanes).
    var items by remember { mutableStateOf<List<MediaItem>?>(null) }
    var enumErr by remember { mutableStateOf<String?>(null) }

    // -1 idle; 0..n-1 loading item i; n pass finished.
    var probeIdx by remember { mutableStateOf(-1) }
    var pendingAdvance by remember { mutableStateOf(false) }
    var probeLog by remember { mutableStateOf<List<String>>(emptyList()) }

    var revisitPhase by remember { mutableStateOf("idle") }
    var revisitAttempt by remember { mutableStateOf(0) }

    LaunchedEffect(library) {
        library.getMediaItems(parentId = null, limit = 20)
            .onSuccess { search ->
                items = search.items.filter { it.mediaType.isVideoType }.ifEmpty { search.items }
            }
            .onFailure { failure ->
                enumErr = failure.message ?: failure::class.simpleName
            }
    }

    // Settle beat between sequential loads: the AX log line for item i is
    // committed, the pane's 500ms COIL_STATS poll picks up its counters, THEN
    // item i+1 starts — keeping the lane's per-item miss attribution clean.
    LaunchedEffect(pendingAdvance) {
        if (pendingAdvance) {
            delay(250)
            pendingAdvance = false
            probeIdx += 1
        }
    }

    val list = items.orEmpty()
    val okCount = probeLog.count { it.endsWith("state=OK") }
    val errCount = probeLog.count { it.endsWith("state=ERR") }
    val statusLine = when {
        enumErr != null -> "CACHE_PROBE: enum-err $enumErr"
        items == null -> "CACHE_PROBE: enumerating…"
        probeIdx == -1 -> "CACHE_PROBE: idle n=${items!!.size}"
        probeIdx < items!!.size -> "CACHE_PROBE: running idx=${probeIdx + 1}/${items!!.size}"
        else -> "CACHE_PROBE: done ok=$okCount err=$errCount"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("CACHE PROBE (Coil memory-cache LRU)", style = MaterialTheme.typography.titleMedium)
            Text(statusLine, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        probeLog = emptyList()
                        pendingAdvance = false
                        probeIdx = 0
                    },
                    enabled = items != null && (probeIdx == -1 || probeIdx >= list.size),
                ) {
                    Text("Probe all")
                }
                OutlinedButton(
                    onClick = {
                        revisitAttempt += 1
                        revisitPhase = "loading"
                    },
                    enabled = list.isNotEmpty() && revisitPhase != "loading",
                ) {
                    Text("Revisit #1")
                }
            }
            // One load at a time; the drawn [Image] is what resolves the
            // painter (an undrawn painter never receives constraints).
            if (probeIdx in list.indices) {
                val current = list[probeIdx]
                key(current.id) {
                    ProbeImage(
                        url = library.getImageUrl(current.id, "Primary", maxWidth = null),
                        contentDescription = "cache probe artwork ${current.name}",
                    ) { ok ->
                        probeLog = probeLog +
                            "CACHE_PROBE: idx=${probeIdx + 1}/${list.size} item=${current.name} state=${if (ok) "OK" else "ERR"}"
                        pendingAdvance = true
                    }
                }
            }
            // Revisit #1: re-request item[0]'s poster. Re-keyed per attempt so
            // a repeat click builds a FRESH painter (a remembered painter for
            // the same URL keeps its settled state and never re-requests).
            if (revisitPhase == "loading" && list.isNotEmpty()) {
                val first = list[0]
                key("revisit-$revisitAttempt") {
                    ProbeImage(
                        url = library.getImageUrl(first.id, "Primary", maxWidth = null),
                        contentDescription = "cache revisit artwork ${first.name}",
                    ) { ok -> revisitPhase = if (ok) "OK" else "ERR" }
                }
            }
            probeLog.takeLast(20).forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = when (revisitPhase) {
                    "idle" -> "CACHE_REVISIT: idle"
                    "loading" -> "CACHE_REVISIT: loading"
                    "OK" -> "CACHE_REVISIT: state=OK"
                    else -> "CACHE_REVISIT: state=ERR"
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/**
 * One full-decode image load whose settle state feeds a machine-readable
 * line. Same shape as [ImageCheck]'s painter (rendered [Image], collected
 * painter [coil3.compose.AsyncImagePainter.State]) plus TWO probe-specific
 * behaviors: [Size.ORIGINAL] (the memory-cache entry must be the fixture's
 * full-size poster, not a layout-sampled thumbnail) and settle-exactly-once
 * reporting via a per-URL remembered flag.
 */
@Composable
private fun ProbeImage(
    url: String,
    contentDescription: String,
    onSettled: (ok: Boolean) -> Unit,
) {
    val context = LocalPlatformContext.current
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            // Full decode size is the point (see CacheProbeCheck KDoc): the
            // fixture's 2560x1440 posters must land in the memory cache as
            // 14,745,600-byte entries so 8 of them exceed the measured cap.
            .size(Size.ORIGINAL)
            .build()
    }
    val painter = rememberAsyncImagePainter(model = request)
    val painterState by painter.state.collectAsState()
    var settled by remember(url) { mutableStateOf(false) }

    LaunchedEffect(painterState, url) {
        if (settled) return@LaunchedEffect
        when (painterState) {
            is AsyncImagePainter.State.Success -> {
                settled = true
                onSettled(true)
            }
            is AsyncImagePainter.State.Error -> {
                settled = true
                onSettled(false)
            }
            else -> Unit
        }
    }

    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = Modifier.size(width = 300.dp, height = 170.dp),
        contentScale = ContentScale.Crop,
    )
}

// ── d) Wave 20B: non-Jellyfin-origin artwork check ─────────────────────────

/**
 * Loads one image from a NON-Jellyfin origin whose URL arrives via the gated
 * `?foreignImage=` boot param (read once per composition; absent → skipped).
 * Rendering mirrors ImageCheck: the raw painter (whose state feeds the
 * `FOREIGN_HOST: OK|ERR` line) plus the shared [MediaImage] — the pipeline
 * feature screens actually call. Both resolve the app-wide loader
 * (KtorNetworkFetcherFactory over the browser fetch engine), so a pass proves
 * cross-origin artwork works when the origin sends CORS headers (the lane's
 * fixture origin answers `Access-Control-Allow-Origin: *`).
 */
@Composable
private fun ForeignHostCheck() {
    val foreignUrl = remember { foreignImageParam() }
    var phase by remember { mutableStateOf(if (foreignUrl == null) "skipped" else "loading") }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("FOREIGN HOST (Coil → non-Jellyfin origin)", style = MaterialTheme.typography.titleMedium)
            if (foreignUrl == null) {
                Text(
                    text = "FOREIGN_HOST: skipped (no param)",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    key(foreignUrl) {
                        ProbeImage(
                            url = foreignUrl,
                            contentDescription = "foreign-host artwork painter",
                        ) { ok -> phase = if (ok) "OK" else "ERR" }
                    }
                    MediaImage(
                        url = foreignUrl,
                        contentDescription = "foreign-host artwork MediaImage",
                        modifier = Modifier.size(width = 120.dp, height = 68.dp),
                    )
                }
                Text(
                    text = "FOREIGN_HOST: $phase",
                    style = MaterialTheme.typography.bodyLarge,
                    color = when (phase) {
                        "OK" -> MaterialTheme.colorScheme.primary
                        "ERR" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = "FOREIGN_URL: ${foreignUrl.ifBlank { "<blank>" }}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * `?foreignImage=` boot param — wave-20B lane hook with the same rules as
 * Main.kt's `?e2eRoute=`/`?variant=` (parsed from the boot URL only; no
 * user-facing surface ever sets it, so every human load gets null). Single-
 * expression `js()` body (the WasmClock rule: wasm `js()` may only be a
 * function's whole body).
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun foreignImageParam(): String? =
    js("new URLSearchParams(window.location.search).get('foreignImage')")

/**
 * Fixed bottom-left host rect for the diagnostics video overlay. `js()`
 * (single-expression top-level function, same convention as
 * HtmlVideoEngine.playIgnoringAutoplayRejection) because kotlinx-browser's
 * CSSStyleDeclaration binding misses several of these longhand properties.
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun styleVideoHost(div: Element) {
    js(
        """
        {
            const s = div.style;
            s.position = 'fixed';
            s.left = '24px';
            s.bottom = '24px';
            s.width = '480px';
            s.height = '270px';
            s.zIndex = '1';
            s.pointerEvents = 'none';
            s.background = 'black';
        }
        """
    )
}
