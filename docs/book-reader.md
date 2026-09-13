# Book reader

JellyPlay isn't just for video — your Jellyfin **books library** reads
in-app: comic archives, PDFs, and EPUBs, with your reading position synced
back to the server so you can pick up on any device.

> This document covers what JellyPlay reads and how resume, offline, and
> sync behave. The enhancement roadmap lives in
> [docs/book-reader-roadmap.md](./book-reader-roadmap.md); the marks-storage
> decision is [ADR 0003](./adr/0003-local-first-reader-marks.md).

## Feature overview

- 📖 **Read in-app** — CBZ, CBR, PDF, and EPUB books open in the built-in
  reader straight from a book's detail screen (**Read** / **Continue**).
- ⏸️ **Download-only formats** — `azw`, `azw3`, `mobi`, `cb7`, and `cbt`
  items have no in-app reader today; the detail screen offers the download
  action instead of Read.
- 🔖 **Bookmarks** — save any position (page or exact EPUB spot) from the
  top bar; the bookmarks sheet lists and jumps to them. Stored locally,
  per install (see ADR 0003).
- 🖍️ **Highlights & notes** — select text in an EPUB to highlight in four
  colors (or underline) and attach a note; the annotations sheet lists,
  edits, and jumps, and exports everything to Markdown or JSON via the
  clipboard.
- 🔎 **Search in book** — EPUBs are full-text searchable with jump-to-hit
  and a flash highlight; PDFs expose their outline (table of contents)
  with page jumps.
- 🎛️ **Reading direction toggle** — comic pages can be flipped
  left-to-right or right-to-left (manga) per book.
- 🎨 **EPUB typography** — reflowable books offer light/sepia/dark themes
  (sepia is a true paper tone), adjustable type size, font family
  (system/serif/sans/mono), line height, page margins, justification, and
  a continuous-scroll mode, all applied live. Theme and type size can be
  set per book ("use for this book only"); the other typography axes are
  global.
- ⏱️ **Time remaining** — the reflowable bottom chrome estimates minutes
  left (whole book and current chapter) from your reading-speed
  preference; paged books show pages left.
- 🔍 **Paged zoom & fit** — PDF and comic pages pinch-zoom with pan,
  double-tap to toggle zoom, and a fit mode (fit width / fit page /
  original); zoomed pages re-raster sharply (up to 3×).
- ☀️ **Brightness** — an in-reader dim slider lives in the bottom chrome of
  both readers.
- 🔊 **Read aloud (Android)** — EPUBs can be spoken with the system TTS
  voice: the current paragraph is highlighted, pages follow along, and
  chapters advance automatically, with sentence-level skip forward/back.
  Speed and pitch are adjustable. Desktop reports the feature as
  unavailable (roadmap: future).
- 🌙 **Sleep timer & auto-scroll** — stop read-aloud/auto-scroll after
  5–60 minutes or at the end of the chapter; scroll-mode EPUBs gain
  auto-scroll with a persisted speed slider and tap-to-pause.
- 📚 **Offline reading** — books download like any other media and open
  from the offline library with no network; positions made offline are
  queued and synced to the server on reconnect.
- 🖥️ **TV & desktop** — the reader ships on Android/Android TV and desktop
  (see [Limitations](#limitations) for web). TV remotes page with the D-pad
  arrows; D-pad center (or Enter/Menu) toggles the reader chrome, whose
  settings gear opens direction, typography, behavior, and TOC.

## How resume works

Jellyfin has no book-specific progress endpoints: readers report plain
`PlaybackPositionTicks` via the standard UserData progress API, and each
client agrees on what the ticks mean. JellyPlay uses the same encodings as
jellyfin-web:

| Format | Ticks mean | Example |
|---|---|---|
| CBZ / CBR / PDF | `pageIndex × 10,000` (0-based) | 12th page (index 11) → 110,000 |
| EPUB | `percent × 10,000,000` | 34% (0.34) → 3,400,000 |

On the same install, EPUB resume is exact: the reader also stores the
last position's CFI (character-fragment identifier) locally and prefers it
over the percent; the percent still syncs to the server so other clients
stay compatible. Bookmarks use the same encoding plus the CFI.

Marking a book as read is always a user action — the server never
auto-completes a book from progress.

## Server versions

- **Jellyfin 12.0** servers probe book files: page counts for PDF and comic
  archives land in the server's own metadata, and comic series/volumes are
  parsed from filenames, so series grouping works.
- **Jellyfin 10.x** servers expose neither — books render as a flat list.

Reading and resume behave identically on both server lines: JellyPlay does
not rely on server metadata for reading — it always opens the book file and
discovers the page count locally when the book opens. Only library-level
series grouping differs.

## Desktop EPUB first run

On desktop, EPUB renders inside a Chromium runtime (KCEF). The first time
you open an EPUB, JellyPlay downloads that runtime (~200 MB) and caches it
under the app data dir; later EPUBs open instantly. Comic and PDF reading
does not need it.

## Limitations

- **No web reading yet** — JellyPlay's detail screens (and therefore the
  reader entry points) have no wasm target today; book reading on web rides
  the detail-cluster web roadmap.
- **Read aloud is Android-only** — desktop has no bundled TTS engine; the
  controls report the feature as unavailable there instead of failing
  mid-book.
- **Marks stay on the device** — bookmarks, highlights, and notes are
  local-only (Jellyfin has no fields for them); use the Markdown/JSON
  export from the annotations sheet to move them.
- **No CB7/CBT/mobi/azw/azw3 rendering** — those containers are
  download-only.
- **CBR is extraction-only** — RAR decoding uses junrar, which is licensed
  under the UnRAR freeware license (declared in its POM); it can never
  *create* RAR archives. No code from RAR source is used.
- **Vendored web assets** — the EPUB reader embeds epub.js **0.3.93**
  (BSD-2-Clause) and JSZip **3.10.1** (MIT); both licenses are permissive
  and credited here. The reader engine script (`reader.js`) is original
  JellyPlay code driving epub.js through its public APIs.
