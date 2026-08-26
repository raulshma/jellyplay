# Playback progress sync

JellyPlay reports every play, pause, and stop back to the Jellyfin server so
resume positions, "watched" state, and Continue Watching stay accurate across
devices. This doc explains how that flow works **online**, what happens
**offline**, and how the two are reconciled when connectivity returns.

## Feature overview

- 🔁 **Single chokepoint** — every play path (video, audio, external player)
  funnels through `PlaybackRepository`, so offline handling lives in one place.
- 📥 **Offline outbox** — playback events captured while offline are staged in
  a Room table and drained automatically on reconnect.
- ⚖️ **Latest-wins reconciliation** — on reconnect, local cached progress is
  compared against fresh server state and the newer side wins.
- 🔄 **Cascade sync** — marking a season/series played or unwatched online
  mirrors the server's child-cascade into the local offline store.
- 🔔 **Visible status** — the home header shows a pending-sync badge; a
  low-priority notification appears while the drain runs.

## Architecture

```
 Player (video / audio / external)
        │  reportPlaybackStart / Progress / Stopped
        ▼
 PlaybackRepositoryImpl  ◄────────── PlaybackOutboxRepository
        │                                │
        │ online + success → apiClient   │ enqueue on offline / failure
        │ online + failure → enqueue     │ coalesce PROGRESS per item
        │ offline        → enqueue       │
        ▼                                ▼
 JellyfinApiClient            PlaybackSyncWorker  (drain + reconcile)
                                       ▲
                                       │  enqueue on reconnect + periodic
                            PlaybackSyncReconnectListener
                                       ▲
                                       │  + UserDataSyncScheduler.enqueueNow
                              NetworkMonitor.networkStatus
```

## Online reporting

Three event types are reported to the Jellyfin `/Sessions/Playing` endpoints:

| Event | Trigger | Endpoint |
|---|---|---|
| **Start** | Playback begins | `reportPlaybackStart` |
| **Progress** | Every 10 s while playing (and on pause/resume) | `reportPlaybackProgress` |
| **Stop** | Playback ends, ViewModel cleared, or 95% watched | `reportPlaybackStopped` |

Each reporter (`PlaybackProgressReporter` for video,
`AudioProgressReporter` for audio, `MainViewModel` for external players) calls
the matching `PlaybackRepository` method, which delegates to
`JellyfinApiClient`. The result is fire-and-forget from the reporter's
perspective — the repository absorbs failures.

### Incognito mode

When incognito mode is enabled, `PlaybackProgressReporter` skips reporting
entirely (`getIncognitoModeEnabled()` guard). No outbox entries are created.
This is the correct behavior — incognito watches should never reach the server.

## Offline handling

`PlaybackRepositoryImpl` consults `OfflineModeManager.isOffline` before each
report. The decision matrix:

| State | Action | Returns |
|---|---|---|
| Online + API success | Delegate to `apiClient` | `Result.success` |
| Online + API failure | Enqueue to outbox | `Result.success` (so callers don't double-enqueue) |
| Offline | Enqueue to outbox, skip API | `Result.success` |

Returning `Result.success` even on failure is deliberate: the reporters don't
inspect the result, and enqueuing means the event will reach the server
eventually. Reporters stay simple.

### The outbox

`playback_outbox` table (Room, migrated via `MIGRATION_35_36`). Each row is one
event: `START`, `PROGRESS`, `STOP`, `PLAYED`, or `UNPLAYED`. The first three
carry the full playback payload (sessionId, positionTicks, isPaused, playMethod,
mediaSourceId); `PLAYED`/`UNPLAYED` carry only the target state (a user-driven
watched flip). All rows carry `recordedAt` (capture time, for reconciliation)
and `createdAt` (drain ordering).

**Coalescence** (`PlaybackOutboxRepositoryImpl`):
- Multiple `PROGRESS` entries for the same item collapse to one — the latest
  position/session/paused state wins. Only the most recent position matters.
- `START` and `STOP` never coalesce — each is a distinct session boundary.
- A `STOP` does not delete earlier `PROGRESS` for the item; the worker handles
  that after a successful drain.
- `PLAYED`/`UNPLAYED` use a deterministic id (`played_state:$itemId`) so a
  re-flip REPLACE-lands in place — latest user intent wins, never more than one
  row per item. They do not touch the START/PROGRESS/STOP rows: a final position
  and a watched state are orthogonal and can coexist.

### Local resume cache

Separately from the outbox, `VideoPlayerViewModel.persistPlaybackPosition`
writes the current position to `offline_media` on every position tick (throttled).
This is what `resolveOfflineResumeTicks` reads when resuming a downloaded item
offline. The outbox is the *upward* sync path; `offline_media` is the *local*
resume cache.

## Drain on reconnect

`PlaybackSyncReconnectListener` watches both `NetworkMonitor.networkStatus` and
the app's `OfflineMode`, firing `PlaybackSyncScheduler.enqueueNow()` whenever
the combined state becomes ready (validated Online + Offline Mode disabled).
That includes an Offline/Local → Online transition and turning **manual Offline
Mode** off while connectivity is already online. (`Local` is treated as offline
— captive portals can't reach the server.) It also fires once at app start so
progress captured while the process was killed flushes shortly after launch.

`PlaybackSyncWorker.doWork()`:

1. **Bail** if Offline Mode is still enabled → `Result.success()` without
   draining. This releases the unique one-shot work slot; the ready-state
   listener immediately schedules a fresh drain when Offline Mode is disabled.
2. **Drain** the outbox (oldest-first). For each entry, replay directly through
   `JellyfinApiClient` (bypassing the repository — a retry must not recurse into
   the outbox). On success, `outbox.delete(id)`. On failure, leave it and mark
   the run as failed.
3. **Reconcile** each successfully-pushed item (see below).
4. **Post foreground notification** while draining; dismiss on completion.
5. **Trigger `UserDataSyncScheduler.enqueueNow()`** so Continue Watching, Next
   Up, and detail caches re-fetch fresh server state instead of waiting for
   their 60s/2min TTLs or the 12h periodic tick.
6. **Return** `Result.success` (all drained), `Result.retry` (some failed,
   under MAX_RETRIES), or `Result.failure` (retries exhausted).

A 4-hour periodic backstop (`PlaybackSyncScheduler.enqueuePeriodic`) catches
any drain the reconnect signal misses.

## Reconciliation (latest-wins)

After pushing local progress up, `PlaybackSyncWorker.reconcileOfflineRow(itemId)`
compares the local `offline_media` row against fresh server state. Three
branches:

1. **Server says played** → reset local to played (position 0, percentage 100).
   Covers: watched half offline, finished online, back offline.
2. **Local played but server unplayed** → `applyPlayedState(itemId, false)` with
   hierarchy cascade. Covers: marked season unwatched online, reconnect races
   the offline cascade.
3. **Neither played** → timestamp comparison. If server `lastPlayedDate` is
   newer than local `lastPlayedDate`, overwrite local position from server.
   Otherwise leave local (it's authoritative).

**Timestamp caveat**: `lastPlayedDate` from the Jellyfin SDK mapper is a bare
`LocalDateTime` string with no offset (see `JellyfinDtoMappers`); local rows are
stamped via `OffsetDateTime.now().toString()`. Both are parsed in the system
zone for comparison — the same zone the SDK used to produce the bare form.

## Cascade sync for seasons/series

Jellyfin's `markPlayedItem` / `markUnplayedItem` endpoints recurse into a
season's or series's children server-side. The client mirrors that cascade
locally so the offline screens stay consistent.

`MediaRepositoryImpl.markPlayed` / `markUnplayed`:
1. **Offline** (or transient online failure): optimistically call
   `offlineRepository.applyPlayedState(itemId, isPlayed)` so the UI flips
   immediately, then `outbox.enqueuePlayedState(itemId, isPlayed)` for the
   worker to deliver on reconnect. Returns `Result.success` so the ViewModel's
   optimistic flip runs.
2. **Online + success**: call `apiClient.markPlayed/markUnplayed(itemId)`, then
   `offlineRepository.applyPlayedState(itemId, isPlayed)` as the mirror.
3. **Online + failure**: same as the offline path — apply locally + enqueue,
   swallow the failure.
4. `OfflineMediaDao.applyPlayedStateToHierarchy` runs one batch `UPDATE`
   matching `id == itemId OR parentId == itemId OR seasonId == itemId OR
   seriesId == itemId` — one query covers episode/season/series uniformly. It
   clears every matching resume position in both directions, matching
   Jellyfin's explicit watched/unwatched endpoints and preventing stale local
   progress from resurfacing in offline UI.

The offline write is best-effort (`runCatching`); the server mutation has
already succeeded (or is queued), and `PlaybackSyncWorker` reconciliation will
correct any drift on the next drain.

## Conflict resolution summary

| Scenario | Winner | How |
|---|---|---|
| Watched offline, reconnect | Server (gets the pushed position) | Drain + reconcile no-op |
| Watched offline to completion | Server (marks played) | Drain pushes STOP; reconcile confirms |
| Half offline → finish online → back offline | Server | Reconcile branch 1: server played wins |
| Marked unwatched online → offline | Server | Immediate cascade via `applyPlayedState` |
| Marked watched/unwatched **offline** | Outbox | `enqueuePlayedState` + local mirror; drained on reconnect |
| Race: marked unwatched, reconnect before cascade lands | Server | Reconcile branch 2: server unplayed wins |
| Multi-device: another device marks played | Server | Reconcile by timestamp (server newer) |

## User-visible indicators

### Home header

`CollapsedDockContent` in `HomeAppBar.kt`:
- **Offline + pending count > 0** → badge on the offline toggle showing the
  count (capped at 99). User sees "N playback events queued".
- **Online + pending count > 0** (drain in progress) → small spinner next to
  the dock.
- **Online + 0** → nothing.

State is reactive: `PlaybackOutboxDao.countFlow(): Flow<Int>` →
`PlaybackOutboxRepository.countFlow()` → `HomeViewModel.pendingSyncCount:
StateFlow<Int>` → collected in `MainHomeContent` → threaded to
`CollapsedDockContent`.

### Device notification

`PlaybackSyncNotificationHelper` (channel `"playback_sync"`, `IMPORTANCE_LOW`):
- Foreground notification "Syncing watch progress" / "N items queued" while the
  drain runs.
- Count ticks down as entries drain.
- Cancelled the moment the drain ends — not persistent.
- `FOREGROUND_SERVICE_TYPE_DATA_SYNC` on Android 14+. No new permissions
  required (`FOREGROUND_SERVICE_DATA_SYNC` + `POST_NOTIFICATIONS` already in
  manifest).

## Testing

| Test file | Covers |
|---|---|
| `PlaybackRepositoryImplTest` | Chokepoint: online success/failure, offline enqueue, field propagation, STOP clears stale PROGRESS |
| `PlaybackOutboxRepositoryImplTest` | Coalescence (session/position/paused), no-coalesce for START/STOP, drain order, countFlow reactivity |
| `PlaybackSyncWorkerTest` | Empty outbox, offline retry, drain+delete, partial failure, reconcile branches, enqueueNow trigger |
| `PlaybackSyncSchedulerTest` | Periodic + enqueueNow work, KEEP policy idempotency |
| `PlaybackSyncReconnectListenerTest` | Offline→Online transition fires drain; steady-state doesn't |
| `UserDataSyncSchedulerTest` | Periodic + enqueueNow (post-drain cache refresh) |
| `OfflineMediaDaoTest` | `applyPlayedStateToHierarchy` cascade: series/season/episode, no-op for unknown |
| `MigrationTest` | `playback_outbox` table created via `MIGRATION_35_36`, contiguity |

## Key files

| File | Role |
|---|---|
| `shared/core/data/.../repository/PlaybackRepositoryImpl.kt` | Chokepoint — online/offline decision per event |
| `shared/core/data/.../repository/PlaybackOutboxRepository.kt` | Outbox interface + coalescence |
| `core/data/.../worker/PlaybackSyncWorker.kt` (Android remainder) | Drain + reconcile |
| `shared/core/data/.../worker/PlaybackSyncScheduler.kt` | Periodic + reconnect enqueue (interface; Android impl in `core/data`) |
| `core/data/.../worker/PlaybackSyncReconnectListener.kt` (Android remainder) | Offline→Online trigger |
| `core/data/.../worker/PlaybackSyncNotificationHelper.kt` (Android remainder) | Drain notification |
| `shared/core/data/.../repository/MediaRepositoryImpl.kt` | `markPlayed`/`markUnplayed` cascade |
| `shared/core/data/.../repository/OfflineRepositoryImpl.kt` | `applyPlayedState` wrapper |
| `shared/core/database/.../dao/PlaybackOutboxDao.kt` | Outbox queries + `countFlow` |
| `shared/core/database/.../dao/OfflineMediaDao.kt` | `applyPlayedStateToHierarchy` batch UPDATE |
| `shared/core/database/.../migration/Migrations.kt` | `MIGRATION_35_36` (outbox table) |

## Known limitations

- **Multi-device clock skew**: if another device's `lastPlayedDate` is
  future-dated relative to this device, the sanity guard in `reconcileOfflineRow`
  skips the overwrite. Rare in practice.
- **Audio resume**: the outbox fix covers audio reporting (same chokepoint),
  but audio resume doesn't read `offline_media` the same way video does. If you
  hit audio-specific offline resume issues, file a bug.
- **No "pending sync" UI on non-home screens**: the badge lives on the home
  header only. A settings row showing pending count could be added if needed.
