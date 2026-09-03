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
event: `START`, `PROGRESS`, `STOP`, `PLAYED`, `UNPLAYED`, `FAVORITE`, or
`UNFAVORITE`. The first three carry the full playback payload (sessionId,
positionTicks, isPaused, playMethod, mediaSourceId); `PLAYED`/`UNPLAYED` carry
only the target state (a user-driven watched flip); `FAVORITE`/`UNFAVORITE`
carry only the target favorite state. All rows carry `recordedAt` (capture
time, for reconciliation) and `createdAt` (drain ordering).

**Coalescence** (`PlaybackOutboxRepositoryImpl`):
- Multiple `PROGRESS` entries for the same item collapse to one — the latest
  position/session/paused state wins. Only the most recent position matters.
- `START` and `STOP` never coalesce — each is a distinct session boundary.
- Enqueueing a `STOP` deletes earlier pending `PROGRESS` for the item under the
  coalescence mutex: the STOP carries the final position, and a surviving
  mid-position PROGRESS could otherwise drain after a dead-lettered STOP and
  leave the server at a stale mid position.
- `PLAYED`/`UNPLAYED` use a deterministic id (`played_state:$itemId`) so a
  re-flip REPLACE-lands in place — latest user intent wins, never more than one
  row per item. They do not touch the START/PROGRESS/STOP rows: a final position
  and a watched state are orthogonal and can coexist. `FAVORITE`/`UNFAVORITE`
  mirror this with `favorite_state:$itemId`.

### Local resume cache

Separately from the outbox, `VideoPlayerViewModel.persistPlaybackPosition`
writes the current position to `offline_media` on every position tick (throttled).
This is what `resolveOfflineResumeTicks` reads when resuming a downloaded item
offline. The outbox is the *upward* sync path; `offline_media` is the *local*
resume cache.

Two #153 hardening rules on this write path:
- Each progress write stamps `isPlayed = isWatchedPercentage(percentage)` —
  once playback crosses the watched threshold (95%), the local row itself reads
  as watched, even if the explicit threshold flip was lost to process death.
- `playback_state`'s upsert keeps `isPlayed` sticky on conflict
  (`isPlayed = playback_state.isPlayed OR excluded.isPlayed`): a sub-threshold
  tick racing the threshold flip must never downgrade the row back to unwatched.
  Only the explicit unwatch path clears the flag.

The session's stop report also falls back to `lastPersistedPositionMs` when the
engine reports position 0 right after `STATE_ENDED`, so the stop telemetry (and
the server's chance to resolve final resume position) is not silently dropped.

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
2. **Drop superseded telemetry**: once a `PLAYED` flip is staged for an item,
   its `START`/`PROGRESS`/`STOP` rows are deleted without replay. A trailing
   STOP replayed after the flip can leave the server with a near-end position
   and `Played=false` (the #153 "watched offline, online home shows mostly
   completed" bug), and `markPlayedItem` already records a full-runtime
   position, so the telemetry carries nothing the flip needs.
3. **Drain** the outbox (oldest-first). For each entry, replay directly through
   `JellyfinApiClient` (bypassing the repository — a retry must not recurse into
   the outbox). On success, `outbox.delete(id)`. On failure, leave it and mark
   the run as failed.
4. **Push derived watched flips**: items with no undelivered `PLAYED`/`UNPLAYED`
   intent row (pending or dead-lettered — the intent row is the authority) that
   the drain snapshot still surfaces via a telemetry row, and whose local
   offline row already reads as watched (`isPlayed`, or ≥ the watched threshold
   via `isFinishedOffline`), get a `markPlayed` derived at drain time — the
   second net for a flip lost to process death at the threshold or an older
   build's missing row (#153). (An item whose entire outbox was lost is not
   surfaced here; reconcile's downloaded-items batch is its net.) The flip
   routes through `MediaRepository.markPlayed` (not raw `PlayedStateSync.flip`)
   so the repository's detail / home-sections / catalogue caches drop in the
   same pass. `flip()` reports success even when the server call failed — it
   stages a `PLAYED` outbox row instead — so delivery is detected from the
   outbox: a row surviving the call means the flip did not land and the drain
   marks itself failed for retry.
5. **Reconcile** each successfully-pushed item (see below).
6. **Post foreground notification** while draining; dismiss on completion.
7. **Invalidate caches + emit a synthetic user-data change** for the drained
   items — open detail sessions and the home refresher listen on the same
   flow as WebSocket pushes and refresh, because the drain's `markPlayedItem`
   calls may never arrive as a `UserDataChanged` echo on this socket. Then
   trigger `UserDataSyncScheduler.enqueueNow()` so Continue Watching, Next
   Up, and detail caches re-fetch fresh server state instead of waiting for
   their 60s/2min TTLs or the 12h periodic tick.
8. **Return** `Result.success` (all drained) or `Result.retry` (some failed,
   under the retry budget). Budget-exhausted entries are already dead-lettered
   by then, so the drain always converges to success and the pending count
   reaches 0.

**Retry budgets** differ by event type: telemetry (`START`/`PROGRESS`/`STOP`)
dead-letters after `MAX_RETRIES` (3) attempts — a stale position is harmless.
User-intent events (`PLAYED`/`UNPLAYED`/`FAVORITE`/`UNFAVORITE`) get a much
larger budget (`MAX_INTENT_RETRIES`, 10) before dead-lettering — a
dead-lettered watched flip is a silently lost user action, which is precisely
the #153 report. Dead-lettered rows are skipped by every later drain and
excluded from the pending count; the only resurrection path is reconcile's
delete-before-push (below), which re-pushes the intent with a fresh budget.

`reconcileOfflineRow` reports a sealed `ReconcileOutcome`: `Changed(result)` /
`NoChange` / `UndeliveredIntent`. The last one — `flip()` failed server-side
and re-staged the outbox row — marks the drain failed for retry: returning
success there would strand the freshly enqueued row until the 4-hour periodic
backstop. A reconcile that merely *throws* stays best-effort/ignored, as
before.

A 4-hour periodic backstop (`PlaybackSyncScheduler.enqueuePeriodic`) catches
any drain the reconnect signal misses.

## Reconciliation (latest-wins)

After pushing local progress up, `PlaybackSyncWorker.reconcileOfflineRow(itemId)`
compares the local `offline_media` row against fresh server state. Three
branches, with an undelivered-intent exception on the first two (#153):

1. **Server says played** → reset local to played (position 0, percentage 100).
   Covers: watched half offline, finished online, back offline.
   *Exception*: if an undelivered `UNPLAYED` intent exists for the item
   (pending or dead-lettered), the user marked it unwatched offline and the
   server never heard it — the server's watched state is not newer knowledge,
   so the unwatched flip is re-pushed instead.
2. **Local played but server unplayed** → `applyPlayedState(itemId, false)` with
   hierarchy cascade. Covers: marked season unwatched online, reconnect races
   the offline cascade.
   *Exception*: if an undelivered `PLAYED` intent exists (pending or
   dead-lettered, or an offline watch whose flip was lost), the watch never
   reached the server — `markPlayed` is pushed instead of clearing the local
   state.
3. **Neither played** → timestamp comparison. If server `lastPlayedDate` is
   newer than local `lastPlayedDate`, overwrite local position from server.
   Otherwise leave local (it's authoritative).

In both intent branches the intent row is deleted **before** the push:
`flip()` re-enqueues it when the server call fails, so the row survives
exactly when the intent is still undelivered — and a formerly dead-lettered
row returns with a fresh retry budget instead of lingering as a zombie that
every later reconcile re-pushes over newer state set on another device.

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
| Watched offline, flip row lost (process death) | Local | Drain-time derived `markPlayed` (#153) |
| Undelivered intent vs. server's opposite state | Local intent | Reconcile pushes the intent instead of mirroring the server (#153) |

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
