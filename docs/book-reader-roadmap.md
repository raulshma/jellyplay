# Book Reader Roadmap — Reading Experience 2.0

Status: **active** (started 2026-09-13, on `feat/reader-advanced`)

The v0.10.9 reader (`docs/book-reader.md`) established the core: EPUB (epub.js 0.3.93
in a WebView/KCEF host), CBZ/CBR and PDF paged reading, reading-direction control,
three fixed themes, font size, and Jellyfin-compatible progress sync. This roadmap
takes the reader to feature parity with dedicated reading apps across four areas:
navigation, comfort/personalization, study tools, and listening/accessibility.

Ground rules that hold across every wave:

- The server progress protocol is untouched. Bookmarks and annotations are
  **local-first** (see ADR 0003) — Jellyfin has no fields for them, so user marks
  live in the local Room database with Markdown/JSON export for portability.
- Position encodings stay compatible with jellyfin-web/Fladder
  (`BookProgressPolicy`): EPUB additions ride epub.js CFIs *locally* and always
  carry a percent fallback.
- Every wave keeps all targets compiling (Android, desktop, wasmJs with honest
  degradation) and ships strings in all 9 locales with jvmTest coverage.

## Waves (this cycle)

### Wave 0 — Groundwork

Branch `feat/reader-advanced`, this roadmap, and ADR 0003.

### Wave 1 — Data & preferences foundation (`core/*`)

- Room schema 54 → 55: `BookBookmarkEntity` (position ticks + optional CFI +
  chapter label) and `BookAnnotationEntity` (CFI, style highlight/underline, color,
  anchor text, note, chapter label), with DAOs and a migration.
- `ReaderAnnotationsRepository` in `shared/core/data`: CRUD, per-item observe
  flows, Markdown/JSON export.
- `ReaderStore` grows the full reader-preference surface: font family
  (system/serif/sans/mono), line height, page margins, justify, scroll mode,
  brightness, volume-key paging, animated page turns, speech rate/pitch, reading
  speed (WPM), per-book appearance overrides, and the last-CFI map for exact EPUB
  resume.

### Wave 2 — EPUB engine + host protocol (`feature/player-book`)

All original code on top of the vendored epub.js public APIs.

- `reader.js`: true sepia paper palette; typography commands (font family, line
  height, margins, justify, flow switch paginated/scrolled); `goToCfi`;
  annotation add/remove/apply; chunked cancellable spine search; speech context
  (current chapter's paragraphs with CFIs); auto-scroll.
- New JS→native events: JS-side tap zones (frees the WebView from the Compose
  pointer overlay — this is what unblocks text selection), text selection with
  CFI, search results, richer relocated payloads (chapter label, remaining pages).
- The appearance bundle (theme, font, typography, flow) rides the chunked
  `loadBookBegin` protocol so a book opens already styled.

### Wave 3 — Navigation & study UI

- `BookReaderScreen` decomposed (chrome/sheets files) before it doubles in size.
- Bookmarks: top-bar toggle at the current position, bookmarks sheet with jump.
- TOC upgrades: PDF outline (desktop PDFBox; Android via pdfbox-android — accepted
  ~3 MB cost, outline-only lazy use), shown alongside the EPUB TOC sheet.
- Search-in-book for EPUB: query field, chapter + excerpt results, jump with an
  ephemeral highlight.
- Highlights & notes: selection action row (four colors, underline, note, copy),
  persistent rendering via epub.js annotations, annotations sheet (jump/edit/
  recolor/delete), Markdown/JSON export.
- Exact EPUB resume: local last-CFI wins, server percent is the fallback (CFIs
  survive re-downloads but not library edits; the fallback covers that).

### Wave 4 — Comfort & personalization

- Typography section in the settings sheet, pushed live to the open book.
- Brightness dim overlay with a slider in the bottom chrome (both content kinds).
- Paged zoom & fit: pinch-zoom/pan, double-tap toggle, fit-width/fit-page/100%
  selector; settled zoom re-rasters pages at the scaled width (capped 3×).
- Per-book appearance overrides ("this book only").
- Time-remaining label: EPUB remaining percent + estimated minutes from the
  reading-speed preference; paged books show pages left.

### Wave 5 — Listening & accessibility

- TTS read-aloud (Android voices first; desktop shows an honest "unavailable"
  entry): paragraph-by-paragraph speech with live paragraph highlight, automatic
  page-follow and chapter advance, skip-sentence controls.
- Sleep timer in the reader (5/15/30/60 min + end-of-chapter) pausing TTS and
  auto-scroll.
- Auto-scroll for EPUB scroll mode with a speed preference and tap-to-pause.

### Wave 6 — Polish & closure

Full verification (module jvmTests, Android assembleDebug, desktop compile),
`docs/book-reader.md` + `CONTEXT.md` updates, ADR status flips to implemented.

## Future (deliberately out of this cycle)

- **Dictionary lookup** on selected words (Wiktionary or similar; needs offline
  story decisions).
- **Web (wasmJs) reading** — the reader module compiles but is runtime-disabled on
  web; enabling it rides the detail-cluster web roadmap.
- **More formats**: MOBI/AZW3 (conversion vs native parse decision), TXT, CB7/CBT.
- **Custom fonts & theme import** (user font files, custom palettes).
- **Reading statistics** in the insights feature (time-in-book, pages/hour).
- **Cross-device annotation sync** — export/import first; a sync channel needs a
  server-side home Jellyfin does not offer today.
- **MediaSession integration** for TTS (play/pause from headset controls).

## Accepted trade-offs

- `pdfbox-android` joins the Android build for PDF outlines (~3 MB, lazy init,
  outline-only use).
- The sepia palette changes from dark-with-tan-text to a true paper sepia — a
  visible change for existing users, kept because the old palette was a known
  complaint (unreadable on OLED).
- EPUB pointer input moves into the WebView (JS tap zones) so text selection can
  work; keyboard/DPAD handling stays native for TV.
