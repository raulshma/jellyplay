# Book reader

JellyPlay isn't just for video — your Jellyfin **books library** reads
in-app: comic archives, PDFs, and EPUBs, with your reading position synced
back to the server so you can pick up on any device.

> This document covers what JellyPlay reads and how resume, offline, and
> sync behave. The design history lives in
> [docs/spikes/book-reading.md](./spikes/book-reading.md).

## Feature overview

- 📖 **Read in-app** — CBZ, CBR, PDF, and EPUB books open in the built-in
  reader straight from a book's detail screen (**Read** / **Continue**).
- ⏸️ **Download-only formats** — `azw`, `azw3`, `mobi`, `cb7`, and `cbt`
  items have no in-app reader today; the detail screen offers the download
  action instead of Read.
- 📊 **Resume that survives devices** — position is stored in Jellyfin's
  standard `UserData` playback-position field and encoded exactly like
  jellyfin-web's players, so a book part-read in jellyfin-web resumes at the
  same spot in JellyPlay and vice versa:
  - Comics (CBZ/CBR) and PDF use **page** positions
    (`pageIndex × 10,000` ticks, 0-based pages).
  - EPUB uses a **percent** position (`percent × 10,000,000` ticks), since
    reflowable text has no fixed pages.
- 🎛️ **Reading direction toggle** — comic pages can be flipped
  left-to-right or right-to-left (manga) per book.
- 🎨 **EPUB themes & font size** — reflowable books offer light/sepia/dark
  themes and adjustable type size, with a table of contents.
- 📚 **Offline reading** — books download like any other media and open
  from the offline library with no network; positions made offline are
  queued and synced to the server on reconnect.
- 🖥️ **TV & desktop** — the reader ships on Android/Android TV and desktop
  (see [Limitations](#limitations) for web). TV remotes page with the D-pad
  arrows; D-pad center (or Enter/Menu) toggles the reader chrome, whose
  settings gear opens the direction toggle, EPUB themes, and TOC.

## How resume works

Jellyfin has no book-specific progress endpoints: readers report plain
`PlaybackPositionTicks` via the standard UserData progress API, and each
client agrees on what the ticks mean. JellyPlay uses the same encodings as
jellyfin-web:

| Format | Ticks mean | Example |
|---|---|---|
| CBZ / CBR / PDF | `pageIndex × 10,000` (0-based) | 12th page (index 11) → 110,000 |
| EPUB | `percent × 10,000,000` | 34% (0.34) → 3,400,000 |

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
- **No CB7/CBT/mobi/azw/azw3 rendering** — those containers are
  download-only.
- **CBR is extraction-only** — RAR decoding uses junrar, which is licensed
  under the UnRAR freeware license (declared in its POM); it can never
  *create* RAR archives. No code from RAR source is used.
- **Vendored web assets** — the EPUB reader embeds epub.js **0.3.93**
  (BSD-2-Clause) and JSZip **3.10.1** (MIT); both licenses are permissive
  and credited here.
