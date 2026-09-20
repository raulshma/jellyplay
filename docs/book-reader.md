# Book reader

JellyPlay isn't just for video — your Jellyfin **books library** reads
in-app: comic archives, PDFs, and EPUBs, with your reading position synced
back to the server so you can pick up on any device.

> This document covers what JellyPlay reads and how resume, offline, and
> sync behave. The reading-experience 2.0 waves (navigation,
> study tools, comfort, listening) and their wave log were
> retired with completion; the marks-storage decision is
> [ADR 0003](./adr/0003-local-first-reader-marks.md).

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
- 📍 **Chapter tick rail** — an opt-in stack of side ticks on the reader's
  edge shows where the current chapter sits in the table of contents (two
  neighbors out on each side); press-drag scrubs chapter titles and
  release jumps. Off by default; enable it in the reader settings'
  behavior section.
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
  original); zoomed PDF pages re-raster sharply (up to 3×). Comic pages
  decode under a longest-edge cap (4000 px) to bound memory, so deep zoom
  into very large scans softens slightly instead of re-rastering.
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
- 🖼️ **Book-aware detail screen** — a book's detail page shows its cover
  as a blurred hero, the author, the file format (EPUB / PDF / Comic), a
  reading-progress card (percent or page N of M, plus local bookmark and
  highlight counts), a **Contents** row that deep-links into the reader at
  a chapter, and a "Mark as finished" action. Video-only sections (cast,
  studios, skip chips) stay hidden for books.
- 📚 **Offline reading** — books download like any other media and open
  from the offline library with no network; positions made offline are
  queued and synced to the server on reconnect.
- 🖥️ **TV & desktop** — the reader ships on Android/Android TV and desktop.
  TV remotes page with the D-pad
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

## The detail screen's Contents

The detail screen never parses a book file itself. The reader write-throughs
what each open makes known — EPUB table of contents, PDF outline, paged page
count — into a local `book_toc_cache` table (schema v56, per install like the
marks), and the detail screen reads that. A book that has never been opened
but **is downloaded** gets one fallback parse of the local file (same parsers
as the reader: PDFBox outline, container NCX/nav, comic page count — except
CBR, whose RAR count only arrives via the cache on first read); a remote
never-downloaded book simply hides the Contents section rather than fetching
the whole file for a speculative parse.

## The chapter tick rail

While a book with a table of contents is open **and the rail is enabled**
(off by default — the settings sheet's behavior section has a "Chapter
tick rail" switch), the reader keeps a compact stack of ticks on the start
edge: two TOC entries before the current chapter, the current one (widest,
accent-tinted), and two after. It is an orientation and jump affordance,
not a second TOC sheet:

- **Tap a tick** to jump to that chapter; the title bubble flashes what you
  tapped.
- **Press-drag** scrubs like the library screen's alphabet rail — the title
  bubble tracks the finger showing only the chapter title, and dragging past
  either end of the stack keeps stepping one chapter per row through the
  whole TOC. The jump commits on release (an EPUB chapter jump rebuilds the
  WebView section, so it does not live-jump mid-drag).

TOC jumps (rail, TOC sheet, and the detail screen's Contents row) resolve
nav-relative hrefs to spine hrefs inside reader.js — epub.js keys its spine
map by the raw OPF hrefs while TOC hrefs are relative to the nav document,
so a nav doc in a subdirectory (`../Text/ch1.xhtml`) would otherwise be a
silent no-op.

The rail never appears for books without a TOC — comics, TOC-less EPUBs,
PDFs without an outline. The current entry comes from the relocation
event's spine href (EPUB) or the outline page (PDF); the tick window, drag
math, and fisheye lens live in the same compose-free geometry pattern as
the library rail (`TocRailGeometry` in the book feature).

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

If that viewer setup fails (no network mid-download, blocked install), the
reader shows its "cannot open" error instead of loading forever — closing
and reopening the book retries the setup.

Desktop EPUB chrome lays out around the page (top bar / content / bottom
bar) instead of floating over it: the Chromium view paints above
app-drawn overlays, so floating controls would hide behind it. Sheets and
dialogs are no exception — while one is open the Chromium view is hidden
so the sheet shows cleanly, and it comes back on dismiss. Everything
else works the same — auto-hide collapses the bars for fullscreen reading,
the chapter rail docks beside the page. The
brightness slider is hidden there (there is no dim layer that can cover
the native view — use the system brightness).

## Limitations

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
