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
    var queue = [];
    var book = null;
    var rendition = null;
    var locationsReady = false;
    var pending = { resume: 0, theme: 'dark', fontSize: 17 };
    var assembling = null;

    var THEMES = {
        dark: { color: '#d8dadc', background: '#000000' },
        sepia: { color: '#d8a262', background: '#000000' },
        light: { color: '#000000', background: '#ffffff' }
    };

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
        if (!book || !locationsReady || !book.locations) return null;
        var loc = currentLocation();
        if (!loc || !loc.start || !loc.start.cfi) return null;
        var percent = book.locations.percentageFromCfi(loc.start.cfi);
        return typeof percent === 'number' && !isNaN(percent) ? percent : null;
    }

    function reportPercent() {
        var percent = currentPercent();
        if (percent === null) return;
        post({ type: 'percent', value: percent });
    }

    function flattenToc(items) {
        var out = [];
        (items || []).forEach(function (item) {
            var label = String(item.label || '').replace(/<[^>]*>/g, '').trim();
            out.push({ label: label, href: String(item.href || '') });
            if (item.subitems && item.subitems.length) {
                out = out.concat(flattenToc(item.subitems));
            }
        });
        return out;
    }

    function applyTheme() {
        if (!rendition) return;
        var palette = THEMES[pending.theme] || THEMES.dark;
        rendition.themes.override('color', palette.color);
        rendition.themes.override('background', palette.background);
    }

    function applyFontSize() {
        if (!rendition) return;
        rendition.themes.fontSize(pending.fontSize + 'px');
    }

    function openBook(arrayBuffer) {
        book = ePub(arrayBuffer);

        book.ready.then(function () {
            var direction = 'ltr';
            try {
                direction = String(book.package.metadata.direction || 'ltr');
            } catch (ignored) {
                // Metadata missing — keep the ltr default.
            }
            post({ type: 'direction', value: direction });
        });
        book.ready.catch(function () {
            status('error');
        });

        book.loaded.navigation.then(function (navigation) {
            post({ type: 'toc', value: flattenToc(navigation.toc) });
        }).catch(function (ignored) {
            // Books without a nav document simply have no TOC entry.
        });

        rendition = book.renderTo(document.getElementById('viewer'), {
            width: '100%',
            height: '100%',
            flow: 'paginated'
        });
        applyTheme();
        applyFontSize();
        rendition.on('relocated', reportPercent);
        rendition.on('displayError', function () {
            status('error');
        });

        status('locations');
        book.locations.generate(1024).then(function () {
            locationsReady = true;
            status('locationsReady');
            var target;
            if (pending.resume > 0) {
                target = book.locations.cfiFromPercentage(pending.resume);
            }
            rendition.display(target).then(function () {
                status('ready');
                reportPercent();
            }).catch(function () {
                status('error');
            });
        }).catch(function () {
            // Locations failed — display without percent precision rather
            // than leaving the reader stuck on the veil.
            locationsReady = false;
            rendition.display().then(function () {
                status('ready');
            }).catch(function () {
                status('error');
            });
        });
    }

    function applyLoadPrefs(theme, fontSize) {
        if (theme) pending.theme = String(theme);
        if (fontSize) pending.fontSize = Number(fontSize) || pending.fontSize;
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

    window.jellyPlayReader = {
        // Single-shot entry (small books / tests). Chunked hosts should use
        // loadBookBegin/loadBookChunk/loadBookEnd — evaluateJavascript calls
        // carrying a whole book as one string fail on large EPUBs.
        loadBook: function (base64, resumePercent, theme, fontSize) {
            try {
                pending.resume = Number(resumePercent) || 0;
                applyLoadPrefs(theme, fontSize);
                decodeAndOpen(base64);
            } catch (ignored) {
                status('error');
            }
        },

        loadBookBegin: function (chunkCount, resumePercent, theme, fontSize) {
            try {
                assembling = { parts: new Array(Number(chunkCount) || 0) };
                pending.resume = Number(resumePercent) || 0;
                applyLoadPrefs(theme, fontSize);
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

        next: function () {
            if (rendition) rendition.next();
        },

        prev: function () {
            if (rendition) rendition.prev();
        },

        goTo: function (href) {
            if (rendition && href) rendition.display(String(href));
        },

        consumeEvents: function () {
            var drained = '[' + queue.join(',') + ']';
            queue = [];
            return drained;
        }
    };
})();
