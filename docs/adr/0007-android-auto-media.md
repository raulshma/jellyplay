# 0007 — Android Auto: media-app path over the existing MediaLibraryService

- **Status:** implemented
- **Scope:** `shared/core/data/androidMain` (playback stack), `app` (manifest)
- **Supersedes:** nothing (car support previously did not exist as code)

## Context

JellyPlay already ships everything an Android Auto **media app** needs at the
transport layer: `JellyPlayPlaybackService` is a media3 `MediaLibraryService`
(exported, `foregroundServiceType=mediaPlayback`, both the media3 and legacy
`MediaBrowserService` intent filters), `AudioLibraryBrowser` implements a paged
browsable tree, and the manifest carried the `automotive_app_desc.xml`
`<uses name="media"/>` declaration. But no car client could actually use it:

- `onGetSession` returned `sessionManager.currentSession`, which is **null
  until the first phone-side play** — the ExoPlayer + `MediaLibrarySession`
  are built lazily by `getOrCreatePlayer()` on the play path. A head unit
  binding the service cold got a null session and showed the app as
  unavailable.
- Controller-initiated `setMediaItems` (the Auto "play" tap) wrote the
  resolved list straight onto the ExoPlayer, bypassing the queue chassis —
  `AudioQueueStateCore.queue` (the single source of truth for the phone queue
  UI, persistence, and the `QueuePlaylistMirror` prefix invariant) would
  desync from whatever the car started.
- No `onSearch`/`onGetSearchResult` (no voice search), a thin root
  (Artists/Albums/Playlists/Favorites/Downloads), and no content-style hints.
- The automotive declaration sat in `app/src/main`, so the **TV flavor**
  advertised car capability too.

## Decision

1. **Media-app path only, no CarAppService.** Android Auto treats a
   `MediaLibraryService` + `<uses name="media"/>` as a first-class media app:
   browse, playback controls, voice. The `androidx.car.app` template API
   would only matter for custom non-media UI (and is disallowed for
   distraction-sensitive playback UIs anyway). No car dependency is added.
2. **Cold-connect session guarantee.** `AudioPlaybackManager
   .ensureAudioSession()` (idempotent `getOrCreatePlayer()` — idle player, no
   autoplay) is called from `onGetSession` before the session is handed out.
   Manager construction is auth-free; an unauthenticated cold connect gets a
   live session whose browse calls fail to empty lists, rather than a
   rejected connection. (Reject-when-unauthenticated is a possible follow-up;
   it was traded away for "browse works the moment the phone app is ever
   logged in".)
3. **Car playback routes through the queue chassis.** `onSetMediaItems` is
   overridden in `AudioLibraryBrowser`: the incoming mediaIds expand to
   DOMAIN tracks (`expandToDomainTracks` — the one prefix ladder, any node
   type including `GENRE_|`, shared with `onAddMediaItems`, stopping at the
   domain model), which go to `AudioQueueFacade.playTracks`; the playable
   list returned to media3 converges with the `QueuePlaylistMirror` rebuild
   (same ids, same order, so whichever write lands last leaves the playlist
   correct) — except per-item degradation: a playable that fails to resolve
   (no detail, offline, resolve throw) drops out of the controller-side list
   only, so the chassis queue can be a superset; that keeps playback alive
   when the head unit's list would otherwise empty out. The facade is
   injected as a call-time `() -> AudioQueueFacade` provider — a direct
   constructor dependency would be circular (browser ← manager ← facade).
   A facade failure (e.g. the queue write itself) degrades to the plain
   direct-write path: playback still works, only queue-chassis sync is lost.
   Queue rows request `MUSIC_MAX_WIDTH` artwork — the same width the
   facade's phone-side dense-list callers use (image-URL building returns
   an empty URL without a server session, never throws).
4. **Rich root, typed folders, content styles.** Root gains Recently Played
   (`DATE_PLAYED` audio), Recently Added (`DATE_ADDED` albums), and Genres
   (`GENRE_|<id>` prefix; genre children filter by genre **name** because
   the Jellyfin genres param is name-keyed — the id maps back through the
   same `getGenres` read that produced the node). Folder mediaTypes use the
   real `MEDIA_TYPE_FOLDER_*` constants, and per-folder children responses
   carry the platform `android.media.browse.CONTENT_STYLE_*_HINT` extras
   (grids for container folders, lists for track folders; playlists are the
   one container exception — a list, matching Auto's playlist convention
   and the sparse artwork Jellyfin playlists carry) — declared as
   string literals because the media3-side constants ride `@UnstableApi`
   and the wire keys are frozen by the platform; clients that don't
   understand them ignore them.
5. **Voice search.** `onSearch` validates (blank → `RESULT_ERROR_BAD_VALUE`),
   `onGetSearchResult` pages `MediaRepository.search` over
   artist/album/audio/music; artists and albums return as browsable nodes so
   "play <artist>" resolves through the same expansion ladder.
6. **Phone-only declaration.** `automotive_app_desc.xml` + both manifest
   meta-data tags moved to the `phone` flavor source set; the TV build no
   longer advertises Auto to Play.
7. **Session activity.** `buildMediaSession` sets a `setSessionActivity`
   PendingIntent to MainActivity (by class name, the video controller's
   convention) — the car's "open on phone" affordance. The notification
   keeps its own content intent (`JellyPlayNotificationProvider`).

## Known limitations (deliberate, v1)

- The car's player-level **shuffle button** toggles ExoPlayer shuffle only,
  not the chassis `shuffleMode` flag — the phone queue sheet shows the
  pre-car shuffle state until the next phone-side toggle.
- `addMediaItems` from a controller (Auto "play next"/"add to queue") still
  writes through the default direct-write path; only `setMediaItems` is
  chassis-routed. `MediaSession.Callback` cannot distinguish resolve-for-set
  from resolve-for-add inside `onAddMediaItems`.
- The playable resolution for a car-initiated play re-fetches detail per
  track (the facade's queue build and the playable list resolve
  independently); bounded by the same `Semaphore(4)` as every other resolve.
- Playlists and artist-albums children are served unoffset: the repository
  seams (`PlaylistRepository.getPlaylists(limit)`,
  `MediaRepository.getArtistAlbums(artistId, limit)`) take no offset param,
  so a head-unit scroll past the first page re-serves page 0 (duplicate
  rows). Pre-existing seams surfaced by Auto — offset support is a follow-up
  on those repositories, not on the browser.

## Testing

Per ADR 0006: all new pins live in `androidHostTest` (the subjects are
`androidMain` actuals) — `AudioLibraryBrowserTest` (root folders, the
DatePlayed/DateAdded/genre-name query pins, content-style extras, search
mapping, facade routing + start-index mapping) and `JellyPlayPlaybackServiceTest`
(cold-connect `ensureAudioSession` pin).
