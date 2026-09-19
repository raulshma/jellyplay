/*
 * JellyPlay EPUB reader (reflowable books).
 *
 * Built on top of the vendored epub.js 0.3.93 (BSD-2-Clause) and JSZip 3.10.1
 * (MIT) — both used strictly through their public APIs. No code is taken from
 * other readers; this file is original.
 *
 * JS → native: window.hostBridge.post(jsonString) when the host injected the
 * bridge (Android JavascriptInterface); otherwise events queue here and the
 * host drains them by polling consumeEvents() (desktop/CEF).
 */
(function () {
    'use strict';

    var QUEUE_LIMIT = 256;
    var MAX_SEARCH_RESULTS = 500;
    var DRAG_SLOP_PX = 8;
    var SWIPE_MIN_PX = 60;
    var TAP_MAX_MS = 500;
    var TOUCH_ECHO_MS = 700;
    var BLOCK_SELECTOR = 'p, h1, h2, h3, h4, h5, h6, li, blockquote, dd, dt, td, pre';
    var SPEECH_BLOCK_SELECTOR = 'p, h1, h2, h3, h4, h5, h6, li, blockquote';
    var EXCERPT_RADIUS = 40;

    var queue = [];
    var book = null;
    var rendition = null;
    var locationsReady = false;
    var locationsGenerating = false;
    var readyShown = false;
    var userNavigated = false;
    var initialRelocatedFired = false;

    /*
     * The scripts are inlined in <head> (see EpubReaderHtml), so #viewer does
     * not exist yet while this IIFE runs — the element resolves lazily on
     * first use. Every call site runs after the host sent the book (both
     * hosts gate commands on page-finished), when the DOM is complete.
     */
    var viewerEl = null;

    function ensureViewerEl() {
        if (!viewerEl) {
            viewerEl = document.getElementById('viewer');
            if (viewerEl) {
                viewerEl.addEventListener('touchstart', function () { stopAutoScroll(); }, { passive: true, capture: true });
                viewerEl.addEventListener('wheel', function () { userNavigated = true; stopAutoScroll(); }, { passive: true, capture: true });
                viewerEl.addEventListener('scroll', function () { userNavigated = true; }, { passive: true, capture: true });
            }
        }
        return viewerEl;
    }

    // Pending appearance: mutated by the set* commands and re-applied on every
    // rendition build, so every knob survives a flow rebuild. Fields the host
    // leaves out keep the old defaults (back-compat with the pre-bundle
    // theme/fontSize-only protocol).
    var pending = {
        resume: 0,
        theme: 'dark',
        fontSize: 17,
        fontFamily: null,
        lineHeight: null,
        margins: null,
        justify: null,
        flow: 'paginated'
    };
    var assembling = null;

    var THEMES = {
        dark: { color: '#d8dadc', background: '#000000' },
        sepia: { color: '#5f4b32', background: '#f4ecd8' },
        light: { color: '#000000', background: '#ffffff' }
    };

    var ANNOTATION_COLORS = {
        YELLOW: '#ffee58',
        GREEN: '#66bb6a',
        BLUE: '#42a5f5',
        RED: '#ef5350'
    };

    // Applied annotations. epub.js keys its annotation map by
    // encodeURI(cfi + type), so the type must be remembered per cfi to remove
    // a mark again; the list re-paints everything after a flow rebuild.
    var appliedAnnotations = [];
    var annotationTypes = {};

    var searchToken = null;
    var tocLabels = {};
    var hasSelection = false;
    var pointerDown = null;
    var touchStart = null;
    var lastTouchAt = -TOUCH_ECHO_MS;
    var autoScrollRaf = null;
    var autoScrollLastTs = 0;
    var autoScrollPxPerSec = 60;

    function post(event) {
        var json = JSON.stringify(event);
        if (window.hostBridge && typeof window.hostBridge.post === 'function') {
            try {
                window.hostBridge.post(json);
                return;
            } catch (ignored) {
                // Bridge broke mid-session — fall through to the poll queue.
            }
        }
        if (queue.length < QUEUE_LIMIT) {
            queue.push(json);
        }
    }

    function status(name) {
        post({ type: 'status', value: name });
    }

    function currentLocation() {
        if (!rendition) return null;
        try {
            return rendition.currentLocation();
        } catch (ignored) {
            return null;
        }
    }

    function currentPercent() {
        if (!rendition) return null;
        try {
            var loc = currentLocation();
            if (!loc || !loc.start || !loc.start.cfi) return null;
            if (book && locationsReady && book.locations) {
                var percent = book.locations.percentageFromCfi(loc.start.cfi);
                if (typeof percent === 'number' && !isNaN(percent)) return percent;
            }
            // Locations not generated (yet): fall back to the spine fraction
            // so the percent readout and progress reports leave 0 immediately
            // — generating locations on a large book can take minutes, and
            // the relocated event used to report null percent the whole time.
            if (book && book.spine) {
                var item = book.spine.get(loc.start.cfi) || (loc.start.href ? book.spine.get(loc.start.href) : null);
                var count = book.spine.items ? book.spine.items.length : 0;
                if (item && typeof item.index === 'number' && count > 0) {
                    // Spine indexes are 0-based in this epub.js build
                    // (assigned in a 0-based forEach): chapter one reads
                    // 0%, the last (n-1)/n — 100% still waits for
                    // generated locations.
                    return Math.min(Math.max(item.index / count, 0), 1);
                }
            }
        } catch (ignored) {}
        return null;
    }

    function reportPercent() {
        var percent = currentPercent();
        if (percent === null) return;
        post({ type: 'percent', value: percent });
    }

    /*
     * TOC labels are author-controlled markup (NCX navLabel is XML, nav docs
     * are HTML). Regex tag-stripping is bypassable (`<scr<script>ipt>` or an
     * unterminated `<script` survive `/<[^>]*>/g`), so parse the label as a
     * real document instead and take its text — textContent cannot contain
     * markup, and DOMParser never executes or loads anything it parses.
     */
    function plainLabel(value) {
        var raw = String(value || '');
        try {
            var doc = new DOMParser().parseFromString(raw, 'text/html');
            return String(doc.body ? doc.body.textContent : '').trim();
        } catch (ignored) {
            // No DOMParser (not expected in any shipping host): removing both
            // angle brackets wholesale still cannot leave a tag behind.
            return raw.replace(/[<>]/g, '').trim();
        }
    }

    function flattenToc(items) {
        var out = [];
        (items || []).forEach(function (item) {
            var label = plainLabel(item.label);
            out.push({ label: label, href: String(item.href || '') });
            if (item.subitems && item.subitems.length) {
                out = out.concat(flattenToc(item.subitems));
            }
        });
        return out;
    }

    function cleanHref(href) {
        return String(href || '').split('#')[0];
    }

    function endsWith(haystack, needle) {
        return haystack.length >= needle.length &&
            haystack.substring(haystack.length - needle.length) === needle;
    }

    // Spine href → TOC label. Exact (hash-stripped) first, then a path-suffix
    // match for books whose TOC hrefs resolve from a different base.
    function chapterLabelFor(href) {
        var clean = cleanHref(href);
        if (!clean) return '';
        if (tocLabels[clean]) return tocLabels[clean];
        var keys = Object.keys(tocLabels);
        for (var i = 0; i < keys.length; i++) {
            var key = keys[i];
            if (endsWith(clean, '/' + key) || endsWith(key, '/' + clean)) {
                return tocLabels[key];
            }
        }
        return '';
    }

    /*
     * TOC href → spine-resolvable href. epub.js keys its spine map by the RAW
     * OPF manifest hrefs while TOC hrefs are raw relative to the nav/NCX
     * document, so a nav doc in a subdirectory emits "../Text/ch1.xhtml" and
     * rendition.display() silently rejects ("No Section Found") — the jump
     * dies without even a displayError. Same fallback ladder as the label
     * matcher (chapterLabelFor): fragment-stripped exact, lexical ../
     * folding, then a "/"-boundary suffix pass over the spine. Always
     * returns a fragment-free href; the caller re-attaches the fragment.
     */
    function resolveSpineHref(href) {
        var clean = cleanHref(href);
        if (!clean) return clean;
        if (!book || !book.spine) return clean;
        if (book.spine.get(clean)) return clean;
        // Lexical ../ fold (leading "../"s collapse at the root, exactly like
        // URL resolution against the package base).
        var parts = clean.split('/');
        var stack = [];
        for (var i = 0; i < parts.length; i++) {
            if (parts[i] === '..') {
                if (stack.length) stack.pop();
            } else if (parts[i] && parts[i] !== '.') {
                stack.push(parts[i]);
            }
        }
        var folded = stack.join('/');
        if (folded && folded !== clean && book.spine.get(folded)) return folded;
        // Boundary-suffix pass (nav hrefs missing their directory prefix).
        var items = book.spine.spineItems || [];
        for (var j = 0; j < items.length; j++) {
            var spineHref = cleanHref(items[j].href);
            if (spineHref && (endsWith(spineHref, '/' + clean) || endsWith(clean, '/' + spineHref))) {
                return spineHref;
            }
        }
        return clean;
    }

    /*
     * Appearance plumbing. epub.js themes keep overrides in a map re-injected
     * into every newly rendered iframe, so values set here survive page turns;
     * the flow rebuild additionally re-runs applyAppearance from `pending`.
     */

    function applyTheme() {
        var palette = THEMES[pending.theme] || THEMES.dark;
        if (document.body) document.body.style.backgroundColor = palette.background;
        if (!rendition || !rendition.themes) return;
        rendition.themes.override('color', palette.color);
        rendition.themes.override('background', palette.background);
    }

    function applyFontSize() {
        if (!rendition || !rendition.themes) return;
        rendition.themes.fontSize(pending.fontSize + 'px');
    }

    function applyFontFamily() {
        if (!rendition || !rendition.themes) return;
        if (pending.fontFamily) {
            // themes.font() folds into the persisted overrides map.
            rendition.themes.font(pending.fontFamily);
        } else {
            rendition.themes.removeOverride('font-family');
        }
    }

    function applyLineHeight() {
        if (!rendition || !rendition.themes) return;
        if (pending.lineHeight !== null && pending.lineHeight !== undefined) {
            rendition.themes.override('line-height', String(pending.lineHeight));
        } else {
            rendition.themes.removeOverride('line-height');
        }
    }

    function applyJustify() {
        if (!rendition || !rendition.themes) return;
        if (pending.justify === true) {
            rendition.themes.override('text-align', 'justify');
            rendition.themes.override('hyphens', 'auto');
        } else if (pending.justify === false) {
            rendition.themes.removeOverride('text-align');
            rendition.themes.removeOverride('hyphens');
        }
    }

    function applyMargins() {
        var el = ensureViewerEl();
        if (!el) return;
        var px = pending.margins;
        if (px === null || px === undefined || isNaN(px) || px < 0) px = 0;
        el.style.padding = px + 'px';
        // The epub-container is sized from the padded viewer; make epub.js
        // re-measure its stage so the column layout adapts immediately.
        if (rendition) {
            try {
                rendition.resize();
            } catch (ignored) {
                // Manager not measurable yet — the next render sizes itself.
            }
        }
    }

    function applyAppearance() {
        applyTheme();
        applyFontSize();
        applyFontFamily();
        applyLineHeight();
        applyJustify();
        applyMargins();
    }

    /*
     * Rendition wiring: events shared by every build (the flow rebuild throws
     * the old rendition away, so wiring must be a function, not top-level).
     */

    function wireRendition() {
        rendition.on('relocated', onRelocated);
        rendition.on('displayError', function () {
            // Only a failed boot flips to the error veil; a failed jump after
            // pages are showing (stale bookmark CFI, flow switch) leaves the
            // reader on its current page — native hears the displayError event.
            if (!readyShown) status('error');
        });

        // New section iframe rendered → attach per-document listeners (the
        // documents are recreated per chapter; they die with their views).
        rendition.on('rendered', function () {
            try {
                (rendition.getContents() || []).forEach(wireContents);
            } catch (ignored) {
                // Contents not ready — the next 'rendered' retries.
            }
        });

        // NOTE: epub.js 0.3.x emits the raw forwarded content events
        // (click / mousedown / touchstart / touchend / …) on the per-chapter
        // Contents emitter ONLY — they never bubble to the rendition, so a
        // rendition-level click listener would silently never fire (that
        // was exactly why the tap zones were dead). Those inputs are wired
        // per document in wireContents. 'selected' is different: epub.js
        // explicitly forwards selection to the rendition, so it stays here.
        rendition.on('selected', onSelected);
    }

    function wireContents(contents) {
        if (!contents || !contents.document || contents.__jellyPlayWired) return;
        contents.__jellyPlayWired = true;
        try {
            // Same double-tap-zoom kill as the host page: book chapters carry
            // their own markup, so without this the WebView delays every tap
            // inside the iframe ~300 ms and swallows quick second taps.
            contents.document.documentElement.style.touchAction = 'manipulation';
        } catch (ignored) {}
        try {
            // The input listeners (see the wireRendition note): taps, swipe
            // tracking and swipe navigation ride the Contents emitter — the
            // only surface epub.js 0.3.x relays raw content events on.
            contents.on('mousedown', onPointerDown);
            contents.on('click', onContentClick);
            contents.on('touchstart', onContentsTouchStart);
            contents.on('touchend', onContentsTouchEnd);
        } catch (ignored) {
            // Contents emitter unavailable — keyboard paging still works.
        }
        try {
            if (contents.window) {
                contents.window.addEventListener('wheel', function () {
                    userNavigated = true;
                    stopAutoScroll();
                }, { passive: true, capture: true });
                contents.window.addEventListener('scroll', function () {
                    userNavigated = true;
                }, { passive: true, capture: true });
            }
        } catch (ignored) {
            // Contents window unavailable — viewerEl listeners still work.
        }
        try {
            contents.document.addEventListener('selectionchange', function () {
                var selection = contents.window && contents.window.getSelection
                    ? contents.window.getSelection()
                    : null;
                var empty = !selection || selection.isCollapsed || String(selection).length === 0;
                if (empty && hasSelection) {
                    hasSelection = false;
                    post({ type: 'selectionCleared' });
                }
            });
        } catch (ignored) {
            // Selection watching is best-effort; taps and paging still work.
        }
    }

    /*
     * A live selection blocks content taps (selecting, not tapping). The
     * per-document selectionchange watchers die with their chapter iframes,
     * so a selection made in a chapter that is then turned away from would
     * keep `hasSelection` latched forever — native stays in the selection
     * bar and every tap is dropped. Swept on each relocation: when no live
     * frame still holds a non-collapsed selection, clear the latch.
     */
    function sweepStaleSelection() {
        if (!hasSelection) return;
        var live = (rendition.getContents() || []).some(function (c) {
            try {
                var selection = c.window && c.window.getSelection ? c.window.getSelection() : null;
                return selection && !selection.isCollapsed && String(selection).length > 0;
            } catch (ignored) {
                return false;
            }
        });
        if (!live) {
            hasSelection = false;
            post({ type: 'selectionCleared' });
        }
    }

    function onPointerDown(e) {
        pointerDown = e && typeof e.clientX === 'number'
            ? { x: e.clientX, y: e.clientY }
            : null;
    }

    /** A live non-collapsed selection under the event's document, if any. */
    function activeSelection(e) {
        try {
            var doc = e.target && e.target.ownerDocument;
            var win = doc && doc.defaultView;
            var selection = win && win.getSelection ? win.getSelection() : null;
            return selection && !selection.isCollapsed;
        } catch (ignored) {
            return false;
        }
    }

    function onContentsTouchStart(e) {
        stopAutoScroll();
        if (e && typeof e.timeStamp === 'number') lastTouchAt = e.timeStamp;
        // Track single-finger starts only — a second finger is a pinch,
        // never a swipe.
        if (e && e.touches && e.touches.length === 1 && typeof e.touches[0].clientX === 'number') {
            touchStart = {
                x: e.touches[0].clientX,
                y: e.touches[0].clientY,
                t: typeof e.timeStamp === 'number' ? e.timeStamp : 0,
            };
        } else {
            touchStart = null;
        }
    }

    /*
     * Swipe → tap-zone event: a horizontal drag beyond [SWIPE_MIN_PX], and
     * flatter than it is tall (so scrolled-flow scrolling never trips it),
     * posts the zone a tap on the trailing/leading third would produce.
     * Physical mapping: dx < 0 (swipe left) posts 'right' — FORWARD under
     * LTR, BACKWARD under RTL — which is exactly how native maps a tap in
     * that third, and it reuses the native direction logic (book metadata
     * OR the user's per-book override) instead of re-deriving it here.
     * Selection guards match onContentClick: selecting text is not swiping.
     */
    function onContentsTouchEnd(e) {
        var start = touchStart;
        touchStart = null;
        if (e && typeof e.timeStamp === 'number') lastTouchAt = e.timeStamp;
        if (!start || !e || !e.changedTouches || !e.changedTouches.length) return;
        var endX = e.changedTouches[0].clientX;
        var endY = e.changedTouches[0].clientY;
        var dx = endX - start.x;
        var dy = endY - start.y;
        // Horizontal swipe → physical direction only (native maps it through
        // the same direction-aware zone logic as taps). Must be flatter than
        // it is tall, or scrolled-flow scrolling would turn pages.
        if (Math.abs(dx) >= SWIPE_MIN_PX && Math.abs(dx) > Math.abs(dy)) {
            if (hasSelection || activeSelection(e)) return;
            post({ type: 'swipe', dir: dx < 0 ? 'left' : 'right' });
            return;
        }
        // Tap: minimal movement, short press. Detected here — off the touch
        // pair that provably fires — rather than the synthesized click, which
        // the WebView may never produce (or produce late, after the renderer
        // fell behind) inside the content iframe. Raw x + width go over the
        // wire; native derives the zone.
        if (Math.abs(dx) < DRAG_SLOP_PX && Math.abs(dy) < DRAG_SLOP_PX &&
            (!start.t || !e.timeStamp || e.timeStamp - start.t < TAP_MAX_MS)) {
            try {
                var target = e.target;
                if (target && target.closest && target.closest('a[href]')) return;
            } catch (ignored) {}
            if (hasSelection || activeSelection(e)) return;
            reportTap(endX);
        }
    }

    /*
     * Raw gesture reporter — NO zone judgment here. Taps report the raw
     * x + host viewport width and native derives the third (unit-testable,
     * and a broken width degrades to the harmless center toggle instead of
     * paging); swipes report only the physical direction. Every earlier
     * attempt at classifying zones inside this file broke subtly once
     * epub.js started turning pages (drifting iframe widths, late echo
     * clicks) — native owns the mapping now.
     */
    function reportTap(x) {
        var width = window.innerWidth || 0;
        // A missing coordinate is a broken report, not a left-edge tap —
        // default to the horizontal center so native's zone mapping sees
        // the harmless center toggle, never a page turn.
        post({
            type: 'tap',
            x: Math.round(typeof x === 'number' && isFinite(x) ? x : width / 2),
            width: width,
        });
    }

    function onContentClick(e) {
        if (!e) return;
        // A click arriving shortly after a touch is the WebView's
        // synthesized echo of a gesture the touch pair already handled.
        // Echoes can land LATE once the renderer falls behind during page
        // turns — timestamp-gated here, so the gate can never be re-armed
        // by a newer gesture before the stale echo lands.
        if (e.timeStamp && e.timeStamp - lastTouchAt < TOUCH_ECHO_MS) return;
        var target = e.target;
        try {
            // The book's own links (footnotes, cross-refs) belong to the book.
            if (target && target.closest && target.closest('a[href]')) return;
        } catch (ignored) {}
        var doc = target && target.ownerDocument;
        var win = doc && doc.defaultView;
        var selection = win && win.getSelection ? win.getSelection() : null;
        if (selection && !selection.isCollapsed) return; // selecting text, not tapping
        if (pointerDown && typeof e.clientX === 'number') {
            var dx = e.clientX - pointerDown.x;
            var dy = e.clientY - pointerDown.y;
            if (dx * dx + dy * dy > DRAG_SLOP_PX * DRAG_SLOP_PX) return; // swipe, not tap
        }
        reportTap(e.clientX);
    }

    function onSelected(cfiRange, contents) {
        userNavigated = true;
        hasSelection = true;
        var text = '';
        try {
            if (contents && contents.window && contents.window.getSelection) {
                text = String(contents.window.getSelection());
            }
        } catch (ignored) {}
        post({ type: 'selected', cfi: String(cfiRange || ''), text: text });
    }

    function onRelocated(loc) {
        if (readyShown) {
            if (initialRelocatedFired) {
                userNavigated = true;
            } else {
                initialRelocatedFired = true;
            }
        }
        sweepStaleSelection();
        reportPercent();
        var label = '';
        var remaining = null;
        var bookRemaining = null;
        var cfi = null;
        var href = null;
        try {
            if (loc && loc.start) {
                label = chapterLabelFor(loc.start.href);
                // Raw spine href so native can identify the current TOC entry
                // (the chapter label alone collides on duplicate titles).
                if (loc.start.href) href = String(loc.start.href);
                if (loc.start.cfi) cfi = String(loc.start.cfi);
                var shown = loc.start.displayed;
                if (shown && typeof shown.page === 'number' &&
                    typeof shown.total === 'number' && shown.total > 0) {
                    remaining = Math.max(0, shown.total - shown.page);
                }
            }
        } catch (ignored) {}
        try {
            // Book-scope remaining: epub.js locations span the whole book, so
            // total − current approximates the whole-book time left (native
            // turns it into minutes at the user's words-per-minute).
            if (book && locationsReady && book.locations && cfi) {
                var total = book.locations.length();
                var current = book.locations.locationFromCfi(cfi);
                if (typeof total === 'number' && total > 0 &&
                    typeof current === 'number' && current >= 0) {
                    bookRemaining = Math.max(0, total - current);
                }
            }
        } catch (ignored) {}
        post({
            type: 'relocated',
            percent: currentPercent(),
            chapterLabel: label,
            href: href,
            remainingPages: remaining,
            remainingLocations: bookRemaining,
            // The page-start CFI native needs for bookmarks + exact resume;
            // null before locations exist or on books epub.js cannot anchor.
            cfi: cfi
        });
    }

    function createRendition() {
        var options = { width: '100%', height: '100%', flow: pending.flow };
        if (pending.flow === 'scrolled') options.spread = 'none';
        rendition = book.renderTo(ensureViewerEl(), options);
        wireRendition();
    }

    function generateLocations() {
        if (!book || locationsGenerating || locationsReady) return;
        locationsGenerating = true;
        if (book.locations) {
            book.locations.pause = 1;
        }
        book.locations.generate(1024).then(function () {
            locationsGenerating = false;
            locationsReady = true;
            status('locationsReady');
            if (!userNavigated && pending.resume > 0 && book.locations.length() > 0) {
                var preciseTarget = book.locations.cfiFromPercentage(pending.resume);
                pending.resume = 0;
                if (preciseTarget && preciseTarget !== -1) {
                    rendition.display(preciseTarget).then(function () {
                        reportPercent();
                    }).catch(function () {});
                }
            }
            reportPercent();
        }).catch(function () {
            locationsGenerating = false;
            locationsReady = false;
        });
    }

    function openBook(arrayBuffer) {
        userNavigated = false;
        initialRelocatedFired = false;
        locationsReady = false;
        locationsGenerating = false;
        readyShown = false;
        book = ePub(arrayBuffer);

        book.ready.then(function () {
            var direction = 'ltr';
            try {
                direction = String(book.package.metadata.direction || 'ltr');
            } catch (ignored) {
                // Metadata missing — keep the ltr default.
            }
            post({ type: 'direction', value: direction });

            var initialTarget;
            if (pending.resume > 0 && book.spine && book.spine.items && book.spine.items.length > 0) {
                var idx = Math.min(Math.floor(pending.resume * book.spine.items.length), book.spine.items.length - 1);
                var sec = book.spine.get(idx);
                if (sec && sec.href) {
                    initialTarget = sec.href;
                }
            }

            rendition.display(initialTarget).then(function () {
                readyShown = true;
                status('ready');
                reportPercent();
                generateLocations();
            }).catch(function () {
                if (initialTarget !== undefined) {
                    rendition.display().then(function () {
                        readyShown = true;
                        status('ready');
                        reportPercent();
                        generateLocations();
                    }).catch(function () {
                        status('error');
                    });
                } else {
                    status('error');
                }
            });
        });
        book.ready.catch(function () {
            status('error');
        });

        book.loaded.navigation.then(function (navigation) {
            tocLabels = {};
            var flat = flattenToc(navigation.toc);
            flat.forEach(function (item) {
                var clean = cleanHref(item.href);
                if (clean && item.label && !tocLabels[clean]) {
                    tocLabels[clean] = item.label;
                }
            });
            post({ type: 'toc', value: flat });
        }).catch(function (ignored) {
            // Books without a nav document simply have no TOC entry.
        });

        createRendition();
        applyAppearance();
        status('locations');
    }

    function applyLoadPrefs(appearanceJson) {
        try {
            var parsed = typeof appearanceJson === 'string'
                ? JSON.parse(appearanceJson)
                : appearanceJson;
            if (!parsed || typeof parsed !== 'object') return;
            if (parsed.theme) pending.theme = String(parsed.theme);
            if (parsed.fontSize) pending.fontSize = Number(parsed.fontSize) || pending.fontSize;
            if (parsed.fontFamily) pending.fontFamily = String(parsed.fontFamily);
            if (parsed.lineHeight !== undefined && parsed.lineHeight !== null) {
                var lineHeight = Number(parsed.lineHeight);
                if (lineHeight > 0) pending.lineHeight = lineHeight;
            }
            if (parsed.margins !== undefined && parsed.margins !== null) {
                var margins = Number(parsed.margins);
                if (margins >= 0) pending.margins = margins;
            }
            if (parsed.justify !== undefined && parsed.justify !== null) {
                pending.justify = !!parsed.justify;
            }
            if (parsed.flow === 'scrolled' || parsed.flow === 'paginated') {
                pending.flow = parsed.flow;
            }
        } catch (ignored) {
            // Bad JSON — keep the defaults already in `pending`.
        }
    }

    function decodeAndOpen(base64) {
        status('loading');
        var binary = atob(base64);
        var bytes = new Uint8Array(binary.length);
        for (var i = 0; i < binary.length; i++) {
            bytes[i] = binary.charCodeAt(i);
        }
        openBook(bytes.buffer);
    }

    /*
     * Search: sequential chunked spine scan. `book.load` parses each section
     * (epub.js caches it for the eventual render), a TreeWalker over each
     * block's text nodes finds case-insensitive matches — matches may span
     * inline elements, so ranges are computed against the block's concatenated
     * text — and section.cfiFromRange anchors them. A newer token invalidates
     * the in-flight scan; only the newest search posts its final event.
     */

    function leafBlocks(root, selector) {
        var pick = selector || BLOCK_SELECTOR;
        var blocks = root.querySelectorAll(pick);
        var out = [];
        for (var i = 0; i < blocks.length; i++) {
            // Containers with nested blocks are covered by their inner blocks
            // (li > p, blockquote > p) — reporting both would double-count.
            if (!blocks[i].querySelector(pick)) out.push(blocks[i]);
        }
        return out;
    }

    function searchSection(section, needle, results) {
        var root = section.contents || section.document;
        if (!root || !root.querySelectorAll) return;
        var doc = root.ownerDocument;
        if (!doc || !doc.createTreeWalker || !doc.createRange) return;
        var chapter = chapterLabelFor(section.href);
        var blocks = leafBlocks(root);
        for (var i = 0; i < blocks.length; i++) {
            var block = blocks[i];
            var texts = [];
            var walker = doc.createTreeWalker(block, 4 /* NodeFilter.SHOW_TEXT */, null, false);
            var node;
            while ((node = walker.nextNode())) {
                if (node.nodeValue && node.nodeValue.trim()) texts.push(node);
            }
            if (!texts.length) continue;
            var full = '';
            for (var j = 0; j < texts.length; j++) full += texts[j].nodeValue;
            var lower = full.toLowerCase();
            var from = 0;
            var idx;
            while ((idx = lower.indexOf(needle, from)) !== -1) {
                var end = idx + needle.length;
                var range = doc.createRange();
                var startNode = null;
                var startOffset = 0;
                var endNode = null;
                var endOffset = 0;
                var pos = 0;
                for (var k = 0; k < texts.length; k++) {
                    var len = texts[k].nodeValue.length;
                    if (!startNode && idx < pos + len) {
                        startNode = texts[k];
                        startOffset = idx - pos;
                    }
                    if (!endNode && end <= pos + len) {
                        endNode = texts[k];
                        endOffset = end - pos;
                        break;
                    }
                    pos += len;
                }
                if (startNode && endNode) {
                    try {
                        range.setStart(startNode, startOffset);
                        range.setEnd(endNode, endOffset);
                        var excerptStart = Math.max(0, idx - EXCERPT_RADIUS);
                        var excerptEnd = Math.min(full.length, end + EXCERPT_RADIUS);
                        var excerpt = (excerptStart > 0 ? '…' : '') +
                            full.substring(excerptStart, excerptEnd) +
                            (excerptEnd < full.length ? '…' : '');
                        results.push({
                            cfi: section.cfiFromRange(range),
                            excerpt: excerpt,
                            chapter: chapter
                        });
                    } catch (ignored) {
                        // Un-anchorable match — skip it, keep the chapter.
                    }
                }
                from = end;
                if (results.length >= MAX_SEARCH_RESULTS) return;
            }
            if (results.length >= MAX_SEARCH_RESULTS) return;
        }
    }

    /*
     * Auto-scroll (scrolled flow only): rAF loop over the rendition's scroll
     * container. Wheel/touch anywhere (outer document or, via the relayed
     * touchstart, inside the content iframes) stops it, as does the end of the
     * book or a flow switch; every stop posts one autoScrollStopped event.
     */

    function findScroller() {
        var candidates = [];
        try {
            if (rendition && rendition.manager) {
                // Continuous flow scrolls the epub-container; older paths keep
                // a dedicated scroller element.
                if (rendition.manager.container) candidates.push(rendition.manager.container);
                if (rendition.manager.scroller) candidates.push(rendition.manager.scroller);
            }
        } catch (ignored) {}
        if (viewerEl) candidates.push(viewerEl);
        for (var i = 0; i < candidates.length; i++) {
            var el = candidates[i];
            if (el && el !== window && el.scrollHeight > el.clientHeight) return el;
        }
        return viewerEl;
    }

    function atScrollEnd(scroller) {
        if (!scroller) return false;
        if (scroller === window) {
            return window.scrollY + window.innerHeight >=
                document.documentElement.scrollHeight - 1;
        }
        return scroller.scrollTop + scroller.clientHeight >= scroller.scrollHeight - 1;
    }

    function autoScrollFrame(ts) {
        if (autoScrollRaf === null) return;
        var dt = autoScrollLastTs ? (ts - autoScrollLastTs) / 1000 : 0;
        autoScrollLastTs = ts;
        if (dt > 0) {
            var scroller = findScroller();
            var delta = autoScrollPxPerSec * dt;
            if (scroller === window) {
                window.scrollBy(0, delta);
            } else if (scroller) {
                scroller.scrollTop += delta;
            }
            reportPercent();
            if (atScrollEnd(scroller)) {
                stopAutoScroll();
                return;
            }
        }
        autoScrollRaf = requestAnimationFrame(autoScrollFrame);
    }

    function stopAutoScroll() {
        if (autoScrollRaf === null) return;
        cancelAnimationFrame(autoScrollRaf);
        autoScrollRaf = null;
        autoScrollLastTs = 0;
        post({ type: 'autoScrollStopped' });
    }

    window.addEventListener('wheel', function () { stopAutoScroll(); }, { passive: true, capture: true });
    window.addEventListener('touchstart', function () { stopAutoScroll(); }, { passive: true, capture: true });

    /*
     * Annotations: epub.js paints highlight/underline marks into an SVG overlay
     * keyed by cfi+type. Colors map to translucent fills (highlight) or an
     * under-stroke (underline); per-item failures are logged and skipped so
     * one bad CFI never aborts a batch.
     */

    function addAnnotationEntry(entry) {
        if (!rendition || !rendition.annotations) return false;
        var cfi = entry && entry.cfi;
        if (!cfi) return false;
        var color = ANNOTATION_COLORS[entry.color] || ANNOTATION_COLORS.YELLOW;
        var type = entry.style === 'UNDERLINE' ? 'underline' : 'highlight';
        var styles = type === 'underline'
            ? { stroke: color, 'stroke-opacity': '1', 'stroke-width': '2', 'mix-blend-mode': 'multiply' }
            : { fill: color, 'fill-opacity': '0.35', 'mix-blend-mode': 'multiply' };
        try {
            if (type === 'underline') {
                rendition.annotations.underline(cfi, {}, undefined, 'jellyplay-annotation', styles);
            } else {
                rendition.annotations.highlight(cfi, {}, undefined, 'jellyplay-annotation', styles);
            }
            annotationTypes[cfi] = type;
            return true;
        } catch (err) {
            try { console.warn('jellyplay: annotation failed', cfi, err); } catch (ignored) {}
            return false;
        }
    }

    function removeAnnotationCfi(cfi) {
        if (!cfi) return;
        var type = annotationTypes[cfi];
        try {
            if (rendition && rendition.annotations) {
                // The map is keyed by cfi+type — cover both kinds when the
                // remembered type is missing.
                rendition.annotations.remove(cfi, type);
                if (type !== 'highlight') rendition.annotations.remove(cfi, 'highlight');
                if (type !== 'underline') rendition.annotations.remove(cfi, 'underline');
            }
        } catch (ignored) {}
        delete annotationTypes[cfi];
        appliedAnnotations = appliedAnnotations.filter(function (e) { return e.cfi !== cfi; });
    }

    window.jellyPlayReader = {
        // Single-shot entry (small books / tests). Chunked hosts should use
        // loadBookBegin/loadBookChunk/loadBookEnd — evaluateJavascript calls
        // carrying a whole book as one string fail on large EPUBs.
        loadBook: function (base64, resumePercent, appearanceJson) {
            try {
                pending.resume = Number(resumePercent) || 0;
                applyLoadPrefs(appearanceJson);
                decodeAndOpen(base64);
            } catch (ignored) {
                status('error');
            }
        },

        loadBookBegin: function (chunkCount, resumePercent, appearanceJson) {
            try {
                assembling = { parts: new Array(Number(chunkCount) || 0) };
                pending.resume = Number(resumePercent) || 0;
                applyLoadPrefs(appearanceJson);
            } catch (ignored) {
                status('error');
            }
        },

        loadBookChunk: function (index, chunk) {
            if (assembling && index >= 0 && index < assembling.parts.length) {
                assembling.parts[index] = String(chunk);
            }
        },

        loadBookEnd: function () {
            if (!assembling) return;
            var parts = assembling.parts;
            assembling = null;
            try {
                if (parts.some(function (p) { return typeof p !== 'string'; })) {
                    status('error');
                    return;
                }
                decodeAndOpen(parts.join(''));
            } catch (ignored) {
                status('error');
            }
        },

        setTheme: function (name) {
            pending.theme = String(name || 'dark');
            applyTheme();
        },

        setFontSize: function (px) {
            pending.fontSize = Number(px) || 17;
            applyFontSize();
        },

        setFontFamily: function (cssStack) {
            var stack = String(cssStack || '').trim();
            pending.fontFamily = stack || null;
            applyFontFamily();
        },

        setLineHeight: function (value) {
            var lineHeight = Number(value);
            pending.lineHeight = lineHeight > 0 ? lineHeight : null;
            applyLineHeight();
        },

        setMargins: function (px) {
            var margins = Number(px);
            pending.margins = !isNaN(margins) && margins >= 0 ? margins : 0;
            applyMargins();
        },

        setJustify: function (on) {
            pending.justify = !!on;
            applyJustify();
        },

        setFlow: function (mode) {
            userNavigated = true;
            var flow = mode === 'scrolled' ? 'scrolled' : 'paginated';
            if (flow === pending.flow) return;
            pending.flow = flow;
            if (!rendition || !book) return;
            // Remember where the reader is (CFI) so the rebuilt rendition can
            // re-display the same position.
            var keep = null;
            try {
                var loc = rendition.currentLocation();
                if (loc && loc.start && loc.start.cfi) keep = loc.start.cfi;
            } catch (ignored) {}
            stopAutoScroll();
            try {
                rendition.destroy();
            } catch (ignored) {}
            rendition = null;
            createRendition();
            applyAppearance();
            var entries = appliedAnnotations.slice();
            appliedAnnotations = [];
            entries.forEach(function (entry) {
                if (addAnnotationEntry(entry)) appliedAnnotations.push(entry);
            });
            try {
                rendition.display(keep || undefined).catch(function () {
                    post({ type: 'displayError', cfi: keep || '' });
                });
            } catch (ignored) {
                status('error');
            }
        },

        next: function () {
            userNavigated = true;
            if (rendition) rendition.next();
        },

        prev: function () {
            userNavigated = true;
            if (rendition) rendition.prev();
        },

        goTo: function (href) {
            userNavigated = true;
            if (rendition && href) {
                var raw = String(href);
                var fragment = raw.indexOf('#') >= 0 ? raw.substring(raw.indexOf('#')) : '';
                rendition.display(resolveSpineHref(raw) + fragment);
            }
        },

        goToCfi: function (cfi) {
            userNavigated = true;
            if (!rendition || !cfi) return;
            var target = String(cfi);
            try {
                rendition.display(target).catch(function () {
                    // Native falls back to the last known percent.
                    post({ type: 'displayError', cfi: target });
                });
            } catch (ignored) {
                post({ type: 'displayError', cfi: target });
            }
        },

        applyAnnotations: function (list) {
            var entries = [];
            try {
                entries = Array.isArray(list) ? list : JSON.parse(String(list || '[]'));
            } catch (ignored) {
                entries = [];
            }
            // Full replace: clear whatever is painted, then add each entry.
            var previous = appliedAnnotations.slice();
            previous.forEach(function (entry) { removeAnnotationCfi(entry.cfi); });
            entries.forEach(function (entry) {
                if (entry && addAnnotationEntry(entry)) {
                    appliedAnnotations.push({
                        cfi: entry.cfi,
                        style: entry.style,
                        color: entry.color
                    });
                }
            });
        },

        addAnnotation: function (entry) {
            var parsed = entry;
            if (typeof entry === 'string') {
                try { parsed = JSON.parse(entry); } catch (ignored) { return; }
            }
            if (!parsed || !parsed.cfi) return;
            removeAnnotationCfi(parsed.cfi); // re-adding replaces the mark
            if (addAnnotationEntry(parsed)) {
                appliedAnnotations.push({
                    cfi: parsed.cfi,
                    style: parsed.style,
                    color: parsed.color
                });
            }
        },

        removeAnnotation: function (cfi) {
            removeAnnotationCfi(String(cfi || ''));
        },

        // Drops the live DOM selection in every rendered content document and
        // resets the tracking flag, so the native selection action row can
        // dismiss itself after creating an annotation (selectionchange fires
        // for the emptied selection, closing the loop).
        clearSelection: function () {
            hasSelection = false;
            try {
                (rendition.getContents() || []).forEach(function (contents) {
                    var win = contents.window ||
                        (contents.document && contents.document.defaultView);
                    var sel = win && win.getSelection ? win.getSelection() : null;
                    if (sel) sel.removeAllRanges();
                });
            } catch (ignored) {}
        },

        search: function (query, token) {
            var myToken = Number(token);
            searchToken = myToken; // a newer token invalidates any in-flight scan
            var needle = String(query || '').toLowerCase();
            var results = [];
            var finish = function () {
                post({ type: 'searchResults', token: myToken, results: results });
            };
            if (!book || !book.spine || !book.spine.spineItems || !needle) {
                finish();
                return;
            }
            var items = book.spine.spineItems;
            var displayedIndex = -1;
            try {
                var loc = currentLocation();
                if (loc && loc.start && typeof loc.start.index === 'number') {
                    displayedIndex = loc.start.index;
                }
            } catch (ignored) {}
            var i = 0;
            var step = function () {
                if (searchToken !== myToken) return; // superseded — stay silent
                if (i >= items.length || results.length >= MAX_SEARCH_RESULTS) {
                    finish();
                    return;
                }
                var section = items[i++];
                section.load(book.load.bind(book)).then(function () {
                    if (searchToken !== myToken) return;
                    try {
                        searchSection(section, needle, results);
                    } catch (ignored) {}
                    // Free chapters the rendition is not showing (the current
                    // chapter's DOM stays cached for the live view).
                    if (section.index !== displayedIndex) {
                        try { section.unload(); } catch (ignored) {}
                    }
                    setTimeout(step, 0); // chunked — keep the WebView responsive
                }).catch(function () {
                    if (searchToken !== myToken) return;
                    setTimeout(step, 0);
                });
            };
            step();
        },

        getSpeechContext: function (cfi) {
            var paragraphs = [];
            var reply = function () {
                post({ type: 'speechContext', paragraphs: paragraphs });
            };
            if (!book || !book.spine) {
                reply();
                return;
            }
            var index = null;
            if (cfi) {
                try {
                    index = new ePub.CFI(String(cfi)).spinePos;
                } catch (ignored) {}
            }
            if (typeof index !== 'number' || index < 0) {
                try {
                    var loc = currentLocation();
                    if (loc && loc.start && typeof loc.start.index === 'number') {
                        index = loc.start.index;
                    }
                } catch (ignored) {}
            }
            var section = typeof index === 'number' && index >= 0
                ? book.spine.get(index)
                : null;
            if (!section) {
                reply();
                return;
            }
            section.load(book.load.bind(book)).then(function () {
                try {
                    var root = section.contents || section.document;
                    var blocks = leafBlocks(root, SPEECH_BLOCK_SELECTOR);
                    for (var i = 0; i < blocks.length; i++) {
                        var text = String(blocks[i].textContent || '')
                            .replace(/\s+/g, ' ')
                            .trim();
                        if (!text) continue;
                        var paragraphCfi = null;
                        try {
                            paragraphCfi = section.cfiFromElement(blocks[i]);
                        } catch (ignored) {}
                        if (paragraphCfi) {
                            paragraphs.push({ cfi: paragraphCfi, text: text });
                        }
                    }
                } catch (ignored) {}
                reply();
            }).catch(function () {
                reply();
            });
        },

        setAutoScroll: function (on, pxPerSec) {
            if (!on) {
                stopAutoScroll();
                return;
            }
            userNavigated = true;
            if (pending.flow !== 'scrolled') return; // only meaningful scrolled
            autoScrollPxPerSec = Math.max(1, Number(pxPerSec) || 60);
            if (autoScrollRaf === null) {
                autoScrollLastTs = 0;
                autoScrollRaf = requestAnimationFrame(autoScrollFrame);
            }
        },

        consumeEvents: function () {
            var drained = '[' + queue.join(',') + ']';
            queue = [];
            return drained;
        }
    };
})();
