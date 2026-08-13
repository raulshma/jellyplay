# Offline downloads

Going on a flight? Commuting on a subway? JellyPlay's offline download
manager lets you take your Jellyfin library on the go — movies, TV
series, music, and more — and plays it back with zero network.

> This document describes both the **user-facing feature** (how to
> download, configure, and play offline) and the **architecture under
> the hood** (engine, storage layout, and the watch-progress sync
> outbox). Jump to [Under the hood](#under-the-hood) for internals.

## Feature overview

- 📥 **WorkManager-backed queue** — downloads survive app kills, device
  reboots, and OS-imposed background limits. Each download is a unique
  `OneTimeWorkRequest`, so re-enqueueing is idempotent.
- ⏸️ **Pause / resume / retry** — long-press a download in the queue to
  pause, resume, restart, move to front, or cancel.
- 🔁 **HTTP Range resumption** — single-connection downloads resume
  exactly where they left off via `Range:` requests, no wasted bandwidth.
- ⚡ **Multi-connection transfers** — large files (>2 MiB) are split into
  parallel byte-range chunks for faster throughput on fast links.
- 📊 **Foreground notification** — a persistent notification shows live
  progress, download speed, and ETA from the Android notification shade.
- 📚 **Offline library browser** — downloaded content is mirrored into a
  dedicated **Offline Library**, fully browsable without your server.
- 📺 **Series & episode selection** — download single episodes, full
  seasons, or entire series; JellyPlay pre-calculates storage needs.
- 🔁 **Auto-download** — optionally fetch new episodes of series you
  already download, on a periodic schedule.
- 🔄 **Watch-progress sync outbox** — playback position, start/stop, and
  played/unplayed state are captured offline and drained back to your
  server the next time you're online.
- 🔁 **Freshness resync** — detect when a download's metadata, artwork,
  subtitles, trickplay, or intro/outro segments have changed server-side and
  refresh just those artifacts (no full re-download). A force-resync lets you
  pick exactly which items and which data categories to refresh. See
  [Keeping downloads fresh](#keeping-downloads-fresh-resync).
- 🔒 **App-private storage** — media is kept in Android's per-app sandbox
  and is wiped on uninstall. See [Security note](#security-note) for
  what is and isn't encrypted.

## How to download a movie

1. Open any movie in JellyPlay
2. Tap the **Download** icon (or kebab menu → **Download**)
3. Choose a quality (see [Download qualities](#download-qualities))
4. Confirm — JellyPlay checks free storage and warns on metered networks
   if the file exceeds your cellular-download threshold

The movie appears in **Downloads** with a progress badge, and in
**Offline Library** once complete.

## How to download a TV series

1. Open any series detail page
2. Tap **Download**
3. Pick **Entire series**, **Specific seasons**, or **Specific episodes**
   (tri-state season checkboxes + per-episode checkboxes, with
   select-all / deselect-all)
4. JellyPlay shows an estimated storage size before you confirm
5. Tap confirm to queue

Each episode is queued as a separate download (up to 4 resolve
concurrently), so you can watch the first one while the rest continue
downloading.

### Auto-download new episodes

Enable **Auto-download new episodes** in **Settings → Downloads** to
have JellyPlay periodically (every ~6 hours) check series you've already
downloaded for new episodes and queue them automatically — subject to
your Wi-Fi-only and schedule settings.

## Download qualities

Quality maps to a server-side max-bitrate cap. **Original** requests the
file as-is (direct stream, no transcoding); the others ask Jellyfin to
transcode down to the cap.

| Quality | Max bitrate | Notes |
| ------- | ----------- | ----- |
| **Original** | (none) | Best quality, largest file, no transcoding |
| **1080p** (High) | 8 Mbps | High quality, transcoded if needed |
| **720p** (Medium) | 3 Mbps | Good for tablets |
| **480p** (Low) | 1.5 Mbps | Saves space, ideal for phones on a long flight |

Your chosen quality is a single global preference (**Settings →
Downloads → Download quality**, default **Original**).

## Configuring downloads

Open **Settings → Storage** to tune downloads. Defaults are shown below.

### Downloads

| Setting | Default | Notes |
| ------- | ------- | ----- |
| **Download quality** | Original | Global quality cap (Original / 1080p / 720p / 480p) |
| **Smart downloads** | Off | Auto-deletes episodes you've watched ≥95% |
| **Auto-download new episodes** | Off | Periodically fetches new episodes of downloaded series |
| **Download schedule** | Off | Restrict downloads to a start–end time window (optionally Wi-Fi-only during the window) |
| **Max download storage** | Unlimited (0) | Cap the downloads directory at 5/10/20/50 GB |
| **Storage location** | Internal | Internal storage vs External (SD card) |

### Storage

| Setting | Default | Notes |
| ------- | ------- | ----- |
| **Wi-Fi only** | On | Block downloads on metered connections |
| **Connections per download** | 4 | Parallel byte-range streams per file (1/2/4/8/12/16; **capped at 8 effective** at runtime) |
| **Max concurrent downloads** | 3 | How many files transfer at once (1–6) |
| **Max cache size** | Unlimited (0) | Auto-evict old downloads at 250/500/1000/2000/5000 MB |

### Network & offline

| Setting | Default | Notes |
| ------- | ------- | ----- |
| **Manual offline mode** | Off | Force JellyPlay offline until you toggle back |
| **Auto offline** | On | Switch to offline automatically when the network drops or is metered/unvalidated |
| **Cellular download warning** | Off (0) | Warn before downloading files larger than N MB on a metered network |

## Managing the queue

Open **Downloads** to see every download and its live state.

**Download states:** `Pending` → `Queued` → `Downloading` → `Completed`
(or `Paused` / `Failed` / `Cancelled`).

- `Downloading` — active transfer; shows `{downloaded} / {total} · {speed} · {ETA}`
- `Queued` — waiting for a concurrency slot (see Max concurrent downloads)
- `Pending` — waiting to start
- `Paused` — manually paused; resumes from where it stopped
- `Failed` — shows the failure reason (network, storage, server error); retry available
- `Cancelled` — stopped; can be restarted
- `Completed` — finished; play or delete

Per-row actions depend on state: **Pause / Cancel / Lower priority**
(downloading), **Move to front / Cancel** (pending/queued), **Resume /
Cancel** (paused), **Retry** (failed), **Play / Delete** (completed).

## Offline playback

Once a download is complete:

- The item appears in **Offline Library** with full metadata (overview,
  ratings, cast, posters, genres) mirrored from your server
- Tap to play — JellyPlay uses the local copy, no network needed
- All player features work: subtitles, audio tracks, trickplay seeking,
  sync, speed control, chapters
- Resume position and played state are tracked locally and synced back
  to your server later (see [Watch-progress sync](#watch-progress-sync))

JellyPlay sniffs the real container of the saved file (so an MKV saved
with a misleading name still picks the right extractor) and attaches any
bundled offline subtitles and local trickplay sprites automatically.

You can also toggle **Offline mode** manually from the home screen at any
time — useful to force offline playback even when a flaky connection is
available.

## Keeping downloads fresh (resync)

A download captures a server snapshot at the moment it finished. Servers
keep changing — artwork gets replaced, metadata is corrected, new subtitle
tracks are added, intro/outro markers get refined. JellyPlay's **resync**
brings a download back in step with the server **without re-downloading the
media file**.

### What gets checked

When you open a downloaded item (or tap **Check all for updates** from the
Downloads screen), JellyPlay compares the server's current state against the
baseline captured at download time, across these axes:

- **Metadata** — overview, cast, ratings, genres, studios, runtime (watched /
  favorite flips are deliberately excluded so they don't trigger a resync)
- **Artwork** — poster and backdrop (Jellyfin issues a new image tag whenever
  an image is replaced)
- **Subtitles** — the set of deliverable external subtitle tracks
- **Trickplay** — the seek-preview thumbnail grid
- **Segments** — intro / outro / recap skip points *(checked only during a
  resync, not on the proactive open-item check — they change rarely and the
  check avoids an extra round-trip)*

If anything differs, an **update available** badge appears on the item.

### Refreshing an item

- **Sync now** (detail screen) refreshes every changed axis at once.
- **Force resync** (Downloads → resync icon → **Force resync**) lets you pick
  exactly which items and which data categories to refresh, regardless of
  whether a change was detected. Skipped categories keep their existing
  baseline, so a partial sync only clears the badge for what it actually
  refreshed.

### When the media file itself changed

If the server's underlying file changed (different media-source id or size),
a metadata resync can't fix that — JellyPlay surfaces it separately and
offers **Re-download media**, which deletes the stale file and re-downloads
the new one.

### How often it checks

The proactive check is **TTL-gated to once per hour per item**, so opening a
download repeatedly doesn't spam the server. It's also a no-op while offline.
There's no background freshness worker; only items you actually open (or a
manual **Check all for updates**) get re-checked.

## Watch-progress sync

When you watch offline, JellyPlay doesn't just remember your position
locally — it records a stream of playback events to a durable **outbox**
and replays them to your Jellyfin server when connectivity returns.

### What gets captured

Each offline playback session emits events to a `playback_outbox` table:

- `START` / `PROGRESS` / `STOP` — the standard Jellyfin playback-report
  lifecycle (session, position, play method)
- `PLAYED` / `UNPLAYED` — watched-state flips, which mirror Jellyfin's
  server-side "mark played" cascade across a series/season hierarchy

Progress events are **coalesced per item** (the latest position wins) so
a long watch session doesn't bloat the outbox. Watched-state flips use a
deterministic id so re-toggling updates the same row in place.

### How it drains

- **On reconnect** — a listener watches both the network status *and*
  the offline-mode flag, and triggers an immediate drain the moment you
  go `Offline → Online` (including when you flip manual offline back on).
- **Background backstop** — a periodic worker (every ~4 hours, when
  connected and not low-battery) drains anything still pending.
- **Manual sync** — the home-screen sync icon lets you tap **Sync now**.
- **Manual offline blocks draining** — while you've forced offline mode,
  the drain worker intentionally no-ops (that's a deliberate choice, not
  a transient failure), so the sync indicator correctly shows
  "will sync when online".

Each event is replayed straight to the Jellyfin API. After **3 failed
attempts** a persistently undeliverable event is dead-lettered (deleted
+ logged) so the sync indicator can return to "up to date" instead of
spinning forever.

### Reconciliation

After pushing your offline progress, JellyPlay fetches a fresh copy of
the item from the server and reconciles the local row using a
"latest-wins" rule:

- If the server says **played**, the local row is marked played.
- If you marked something **played offline** but the server says
  unplayed, the unplayed state cascades back down.
- Otherwise the most recent activity (by timestamp) wins — fixing the
  classic "watched half offline, finished online, resume got stuck at
  50%" case.

A successfully delivered STOP also clears redundant telemetry for that
item while preserving any pending played/unplayed flip.

### Where you see it

A **sync status icon** appears on the home top bar whenever there are
pending events: a count badge with a refresh icon that spins while a
drain is in progress. Tapping it opens the **Sync details sheet**, which
lists each pending item (with poster, title, event type, position, and
age) and offers a **Sync now** button (disabled while offline).

## Storage

### Where downloads live

Storage location is set in **Settings → Downloads → Storage location**.

| Location | Video | Audio |
| -------- | ----- | ----- |
| **Internal** (default) | `<app internal>/downloads/` | `<app internal>/downloads/music/` |
| **External** (SD card) | `<app external>/Movies/` | `<app external>/Music/` |

In both cases the directory is **app-private** (not visible to other
apps, excluded from the media scanner, wiped on uninstall). On internal
storage this is under `/data/data/raulshma.jellyplay/files/`; on
external it's under `/Android/data/raulshma.jellyplay/files/`. JellyPlay
requires at least 100 MB free before starting a download.

File names follow `${name}_${itemId-prefix}.${ext}`, where the extension
is the **real container** reported by Jellyfin (defaulting to `mp4` /
`mp3`) — this avoids the historical bug where an MKV stream saved as
`.mp4` silently confused the player's extractor.

### Bundled artifacts

Each download brings more than the media file. Sibling artifacts are
written alongside it so offline playback matches online:

- `trickplay_<itemId>/` — sprite sheets (`trickplay_*.jpg` + `meta.json`) for
  thumbnail seeking
- `subtitles_<itemId>/` — external subtitle files + a `manifest.json` describing
  each track (language, codec, default flag)
- `<itemId>_segments.json` — intro / outro / recap markers
- `${itemId}_poster.jpg` / `${itemId}_backdrop.jpg` — artwork, keyed by
  itemId because all downloads share one flat directory

Artifacts are **scoped per item** (the `_<itemId>` suffix) so downloads
sharing the flat directory never overwrite each other's siblings. A resync
that refreshes an axis overwrites exactly that item's artifact in place; the
writers are idempotent and `downloadExternalSubtitles` clears its dir when no
deliverable tracks remain (mirroring a server-side subtitle removal).

Deleting a download removes the media file and all of its artifacts.

### Security note

Downloaded **media files are stored as plaintext** in app-private
storage — they are **not** encrypted on disk. They rely on Android's
per-app sandbox for isolation. What *is* encrypted is your **Jellyfin
access token**: it's stored encrypted at rest in the app database
(AES-256-GCM, Android Keystore-backed) and only decrypted in memory to
attach the `X-Emby-Token` header while downloading. If you need
file-level encryption, choose a device with encrypted storage (standard
on modern Android) and rely on the app sandbox.

## Background restrictions

On some manufacturers (Xiaomi, Huawei, OnePlus) Android aggressively
kills background processes. To ensure downloads complete:

- Add JellyPlay to the **protected apps** list in your device's battery
  settings
- Disable **Adaptive battery** for JellyPlay
- Allow **Unrestricted background activity** in **App info → Battery**

The download manager runs as a foreground service, so its notification
should remain visible throughout an active transfer.

## Limitations

| Item | Support | Why |
| ---- | ------- | --- |
| **Movies / TV / music videos** | ✅ | Core use case |
| **Audio-only music** | ✅ | See the [music player guide](./lyrics-music-player.md) |
| **HLS / DASH streams** | ✅ | Direct play, no re-encoding |
| **Live TV** | ❌ | Streaming-only by design |
| **DRM-protected content** | ❌ | Jellyfin doesn't host DRM content |

## Under the hood

A reference for contributors. The feature spans `core/database`,
`core/model`, `core/data`, and the `feature/downloads` / `feature/home`
/ `feature/details` / `feature/settings` modules, wired by Hilt.

### Engine: custom WorkManager + OkHttp (not ExoPlayer)

JellyPlay does **not** use ExoPlayer's `DownloadService`. It uses a
custom `DownloadWorker` (`CoroutineWorker`) with OkHttp for the byte
transfer, which gives finer control over Range resumption,
multi-connection chunking, and concurrency than the ExoPlayer helper.

- **Per-download unique work** — each download enqueues as
  `OneTimeWorkRequest` under the unique name `"download_$id"` (KEEP
  policy), with a `NetworkType` constraint from the Wi-Fi-only setting
  and an optional schedule-window delay.
- **Concurrency limiter** — a shared `Semaphore`
  (`DownloadConcurrencyLimiter`) sized from **Max concurrent downloads**
  (default 3, clamped 1–6). A worker enters `Queued` while waiting for a
  permit, then promotes to `Downloading`.
- **Single-connection path** — OkHttp `GET` with `X-Emby-Token` and a
  custom `User-Agent`. If resuming (`existingBytes > 0`) it sends
  `Range: bytes=N-` and appends; handles HTTP 416 (stale range → restart
  from 0) and 206 partial content. It polls the DB every ~2 s for
  pause/cancel and updates progress + the foreground notification.
- **Multi-connection path** — when `totalSize > 2 MiB` **and** the
  connections setting is `> 1`, `MultiConnectionDownloadStrategy` splits
  the file into N equal byte ranges and transfers them concurrently into
  one `RandomAccessFile` (seek per chunk). Because scattered-offset
  partials can't be appended to, **any cancel/failure deletes the
  partial and resets bytes to 0** (a single-connection resume would
  otherwise corrupt a gapped file).
- **Connection cap** — the per-file connections setting is clamped to
  `1..8` at runtime, so selecting 12/16 in the UI is effectively 8.
- **Shared HTTP client** — a pre-tuned `@Named("download")`
  `OkHttpClient` (connect 30 s, read 60 s, write 30 s) is shared rather
  than cloned per call.

### Orchestration layer

- **`DownloadIntake`** — the single feature-facing entry point.
  `start(detail, maxBitrate)` handles a single item; `startSeries(...)`
  handles a batch. It owns the full "artifact bundle recipe" so no call
  site re-implements it.
- **`DownloadDelegate`** — builds a `DownloadRequest` (resolves the
  stream URL, applies the quality bitrate cap), starts the transfer, and
  bundles all offline artifacts (images, trickplay, subtitles, segments,
  metadata).
- **`DownloadRepository`** — the low-level lifecycle + status API
  (`startDownload`, `cancel/pause/resume/retry/delete`,
  `setDownloadPriority`, `enqueueDownload`) and artifact writers. Series
  downloads resolve per-episode URLs under a `Semaphore(4)` and fan out.
- **`OfflineRepository`** — the read side of the offline library,
  joining `offline_media` with live `downloads` rows so each item carries
  current path/status/progress. Also owns offline-side progress and
  played-state writes.
- **`OfflineModeManager`** — a lifecycle observer that derives
  `OfflineMode` (`ONLINE` / `OFFLINE_MANUAL` / `OFFLINE_AUTO`) by
  combining the manual toggle, the auto toggle, and `NetworkMonitor`
  (treating captive-portal / unvalidated networks as offline).

### Persistence (Room)

A single `JellyPlayDatabase` (v46) holds three relevant tables:

- **`downloads`** — live transfer state (path, url, sizes, status,
  speed, priority, error, container, series/season linkage). Indexed
  `(status, priority, createdAt)` for queue ordering. Includes a
  cold-start recovery projection and a fast auto-download episode query.
- **`offline_media`** — browsable offline metadata (overview, ratings,
  cast JSON, genres, studios, posters, blurHash) plus playback-progress
  columns (`playbackPositionTicks`, `playedPercentage`, `isPlayed`,
  `lastPlayedDate`) and the **freshness-resync baseline + result flags**
  (`syncedPosterTag`, `syncedBackdropTag`, `syncedMetadataSignature`,
  `syncedSubtitleSignature`, `syncedTrickplaySignature`,
  `syncedSegmentsSignature`, `syncedMediaSourceId`, `syncedMediaSizeBytes`,
  `lastSyncedAt`, `syncUpdateAvailable`, `syncMediaChanged`, `syncChecking`,
  `syncError`). `applyPlayedStateToHierarchy` cascades a played /
  unplayed flip across an item and its whole series/season hierarchy in
  one UPDATE, mirroring Jellyfin's server-side behavior.
- **`playback_outbox`** — the offline telemetry queue drained by
  `PlaybackSyncWorker`.

### Sync outbox internals

- **Capture** — `PlaybackRepositoryImpl` enqueues to the outbox whenever
  it's offline (or hits a transient HTTP failure) while reporting
  start/progress/stop, and when marking played/unplayed.
- **Drain** — `PlaybackSyncWorker` replays entries oldest-first
  directly through the API client (bypassing the repository to avoid
  re-enqueuing). Dead-letters after 3 attempts.
- **Reconciliation** — `reconcileOfflineRow` pulls a fresh server item
  after a push and applies latest-wins by ISO timestamp.
- **Triggers** — `PlaybackSyncReconnectListener` enqueues an immediate
  drain on `Offline → Online` transitions (watching both network and
  offline-mode signals); `PlaybackSyncScheduler` provides the periodic
  backstop and a manual `enqueueNow()`.

### Freshness & resync

A two-layer split keeps the freshness rules testable and the I/O isolated:

- **`OfflineSyncComparator`** (`core/data/.../sync`) — the pure, side-effect-free
  decision layer. It computes deterministic **content-hash signatures** for each
  resync axis (metadata, subtitles, trickplay, segments) from a `MediaDetail`,
  captures a `SyncBaseline`, and diffs a fresh fetch against it. No I/O here —
  network, DB, and disk live one layer up.
- **`OfflineSyncManager`** (`core/data/.../sync`) — the orchestrator that moves
  data between the network (`MediaRepository`, `PlaybackRepository`), the DAO
  (`OfflineMediaDao`), and the artifact writers (`OfflineDownloadWriter`). Owns
  the TTL gate, the offline short-circuit, batch progress, and the partial-sync
  baseline merge.

**Signatures, not version tags.** Jellyfin exposes no etag/version/count for
these artifacts, so every axis is a content hash: metadata is a SHA-256 over
the user-facing fields (excluding `UserData` so a watched flip elsewhere can't
trigger a resync); subtitles hash the deliverable SUBTITLE streams; trickplay
folds the tile-grid fields (bandwidth-insensitive, since a quality-only change
doesn't invalidate the tiles); segments hash the typed `(start, end)` list.

**TTL gate.** `checkForUpdates` is a no-op network-wise when `lastSyncedAt` is
within `SYNC_TTL_MS` (1 hour) or the device is offline, so the on-entry check
an offline detail screen fires is effectively free most of the time.

**Baseline seeding + first-contact guard.** Subtitles and trickplay signatures
are seeded at download time (derived free from `MediaDetail`); the segments
signature seeds on the first segments resync (segments aren't part of
`MediaDetail`). An empty signature means "never recorded" and never flags a
spurious change — so a pre-feature row or a not-yet-synced axis degrades to
first-contact seeding rather than a false "update available".

**Composite badge.** The DB stores one coarse `syncUpdateAvailable` flag for the
metadata/images/subtitles/trickplay/segments axes (the per-axis split lives
only in the in-memory `ResyncCheckResult`), so the badge renders from the DB
with no network. `mediaFileChanged` is a separate flag because a resync can't
fix it — it routes through the delete + re-download path.

**Partial sync.** `ResyncOptions` selects which categories to refresh. A
partial sync blends synced-fresh values with retained (skipped-category) values
via `mergePartial`, then re-runs the comparator against that effective baseline
so the update-available flag clears only for the synced categories.

**Segments cache.** A force-resync busts the 5-minute in-memory `segmentsCache`
(`PlaybackRepositoryImpl`) before refreshing, so the axis sees current server
state instead of a recently cached snapshot. The writer fetches fresh; the
manager re-reads (cache hit) for the signature — one network call total.

**Process-death recovery.** The manager clears stale `syncChecking` markers on
construction so a check interrupted by process death doesn't render as a stuck
"checking…" badge forever.

### UI layer

- **`feature/details`** — **one** `MediaDetailScreen` renders detail for
  online, remote-with-attached-download, and local/offline/fallback items.
  `MediaDetailProvider` (in `core/data`) owns the remote/local source decision,
  the source-dependent read graph (detail, seasons/episodes via the shared
  `EpisodeCatalogue`, album children, local subtitles, local artwork), the
  reactive download/sync attachment, and a compact capability set. `DetailViewModel`
  consumes the provider, drives remote-only discovery (Seerr, ARR, theme music,
  similar/collection, playlists), and owns all write actions (watched/favorite,
  download lifecycle: delete/resync/re-download, per-episode and whole-season
  batch delete). Download/sync presentation (`DownloadInfoCard`,
  `WatchProgressSection`, `SyncUpdateBanner`, `ResyncSheet`,
  `DeleteDownloadedEpisodesSheet`) and a manifest-backed local subtitle selector
  live here, gated by capability/attachment so they also serve a remote item with
  a completed download. The `DownloadConfirmationDialog` and `SeriesDownloadSheet`
  initiate downloads; `DetailViewModel` performs the cellular-warning check and
  calls `DownloadIntake`.
- **`feature/downloads`** — reserved for download **queue** and **offline-library**
  management: `DownloadsScreen` (queue + per-row actions, the freshness-resync
  appbar action with a batch check + `DownloadsResyncSheet`, and the granular
  per-item / per-category `ForceResyncSheet`) and `OfflineLibraryScreen`
  (grid with search/sort/filter + storage header). Both drill into
  `Route.MediaDetail(id)` — there is no longer a separate offline detail or series
  screen.
- **`feature/home`** — the `SyncStatusIcon` + `SyncDetailsSheet` that
  surface pending playback-sync events.
- **`feature/settings`** — `StorageSettingsScreen` exposes every
  download, storage, and offline preference.

## Next steps

- 📺 [Set up JellyPlay on your TV →](./android-tv-setup.md)
- 🎵 [Download music for offline listening →](./lyrics-music-player.md)
- ⚖️ [See how JellyPlay compares to other Jellyfin clients →](./why-jellyplay-vs-plex-emby.md)
