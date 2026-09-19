# 0003 — Reader bookmarks and annotations are local-first, CFI-anchored

- **Status:** implemented (accepted 2026-09-13, shipped on `feat/reader-advanced`)
- **Date:** 2026-09-13
- **Scope:** `shared/core/database`, `shared/core/data`, `shared/feature/player-book`

## Context

The v0.10.9 book reader has no bookmarks, highlights, or notes — the reading
position is the only user mark, and it lives server-side as Jellyfin
`UserData.PlaybackPositionTicks` (`BookProgressPolicy`: page × 10,000 ticks for
paged books, percent × 10,000,000 for EPUB, round-tripping with jellyfin-web and
Fladder). Readers at this feature tier (Moon+, Readium-based apps, Play Books)
treat marks as first-class: position bookmarks, text-anchored highlights in
colors, attached notes, export.

Jellyfin offers nothing here: no bookmark, highlight, or annotation fields exist
on items or user data, and the progress endpoint carries a single number. Any
marks feature must therefore decide where the data lives and how EPUB anchors
survive.

## Decision

1. **Marks are local-first.** Bookmarks and annotations live in the local Room
   database (`JellyPlayDatabase`, schema 55+), per item, per install. Portability
   is provided by **Markdown and JSON export** from the annotations sheet rather
   than by a sync channel. Import may follow later; export is the contract.
2. **Position model reuses the existing encodings.** A bookmark stores
   `positionTicks` with the same `BookProgressPolicy` math as reading progress,
   plus a nullable `cfi`. Paged books (CBZ/CBR/PDF) are page-anchored; EPUB
   bookmarks are CFI-anchored with the percent fallback already used everywhere.
3. **EPUB text anchors are epub.js CFIs** (`epubcfi(...)` from the vendored
   epub.js 0.3.93). The native side stores opaque CFI strings and never parses
   them beyond equality; anchoring, rendering (highlights/underlines), and CFI
   arithmetic stay in `reader.js`. A CFI that fails to display (book replaced on
   the server, chapter reflowed) degrades to the stored percent.
4. **Exact EPUB resume is local.** The last CFI per item is kept in
   `ReaderStore` (the reader preference domain) and preferred over the server
   percent when the same install reopens the book; the server keeps receiving the
   ticks protocol unchanged, so other clients still see a sane position.
5. **The ticks protocol is untouched.** No mark data is smuggled through
   Jellyfin endpoints. If Jellyfin grows real annotation support, migration to a
   synced model is a new decision.

## Considered options

- **Sync marks via Jellyfin anyway** (e.g. encoded into unused user-data fields):
  rejected — fragile, invisible to other clients, and breaks on server cleanup.
- **CFI-free anchoring** (store only chapter href + percent): rejected — exact
   highlight anchors and exact resume both need CFI precision; percent rounding
   visibly drifts on long chapters.
- **Parse/validate CFIs natively**: rejected — the CFI grammar belongs to the
   rendering engine; native equality checks keep the seam thin and the reader
   engine swappable (relevant if epub.js is ever replaced).
- **A separate marks database**: rejected — one Room schema already covers the
   app's persistence; a second store doubles migration and backup surface.

## Consequences

- Marks do not follow the user to another device; export/import is the escape
   hatch until a sync home exists (roadmap "future").
- Factory reset clears marks with the rest of the database; the last-CFI map
   clears with reader preferences.
- Schema changes for marks ride the existing migration chain and its
   contiguity guard.
- `ReaderAnnotationsRepository` stays in `shared/core/data` commonMain, so a
   future wasmJs reader gets marks for free once the reader itself ships there.
