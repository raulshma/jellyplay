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
 * Deliberately NOT a feature screen: no error taxonomy, no i18n, no design
 * polish. The ALL-CAPS text lines are load-bearing strings for the driver —
 * do not reword them without updating tools/e2e/web-verify.mjs.
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
