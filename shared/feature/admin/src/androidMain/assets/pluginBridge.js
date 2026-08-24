/*
 * pluginBridge.js — injected into the PluginConfigScreen WebView before the
 * plugin's own scripts run. It provides the JavaScript globals that Jellyfin
 * plugin configuration pages expect to find on `window`, mirroring the surface
 * jellyfin-web exposes via its bundled ApiClient/Dashboard.
 *
 * Auth strategy: the shim's fetch()/ajax() calls attach the `X-Emby-Token`
 * header directly so that write requests (config save = POST) carry their body
 * to the server intact. WebViewClient.shouldInterceptRequest cannot forward a
 * POST body (Android's WebResourceRequest exposes none), so intercepting writes
 * is impossible; instead the host intercepts only same-origin GETs (images,
 * CSS) to authenticate resource loads the page itself initiates. UI feedback
 * (saved toast, loading overlay, dialogs) is bridged back to native through
 * NativeInterface.
 *
 * Trust model: a plugin config page runs arbitrary JS, and Jellyfin plugins are
 * documented to have server-equivalent access. Exposing the token to that JS
 * grants no capability the plugin did not already have via authed fetch. The
 * host additionally restricts its native GET interception to exact same-origin
 * URLs (scheme+host+port) so the token is never attached to third-party hosts.
 *
 * Placeholder tokens below (e.g. __SERVER_ADDRESS__) are substituted by
 * PluginConfigViewModel/PluginWebViewAuth.buildBridgeScript() before injection.
 */
(function () {
    'use strict';

    var SERVER_ADDRESS = "__SERVER_ADDRESS__";
    var USER_ID = "__USER_ID__";
    var ACCESS_TOKEN = "__ACCESS_TOKEN__";

    // --- helpers -------------------------------------------------------------

    function serializeQuery(params) {
        if (!params) return '';
        var parts = [];
        for (var key in params) {
            if (!Object.prototype.hasOwnProperty.call(params, key)) continue;
            var value = params[key];
            if (value !== undefined && value !== null && value !== '') {
                parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(value));
            }
        }
        return parts.join('&');
    }

    // Resolve a path (optionally with params) into an absolute server URL.
    // Mirrors jellyfin-apiclient ApiClient.getUrl().
    function getUrl(name, params, baseUrl) {
        if (!name) throw new Error('Url name cannot be empty');
        var base = baseUrl || SERVER_ADDRESS;
        if (!base) throw new Error('serverAddress is not set');
        if (name.charAt(0) !== '/') base += '/';
        base += name;
        var query = serializeQuery(params);
        if (query) base += '?' + query;
        return base;
    }

    // Core HTTP wrapper. Rejects on HTTP >= 400 (jellyfin-apiclient parity).
    function doFetch(request, includeAuthorization) {
        if (!request) return Promise.reject('Request cannot be null');
        request.headers = request.headers || {};
        if (includeAuthorization !== false && ACCESS_TOKEN) {
            // Write requests (POST) must carry their own token — the host cannot
            // intercept and re-issue a POST while preserving its body.
            request.headers['X-Emby-Token'] = ACCESS_TOKEN;
        }
        var fetchOptions = {
            method: request.type || 'GET',
            headers: request.headers,
            credentials: 'same-origin',
        };
        var contentType = request.contentType;
        if (request.data !== undefined && request.data !== null) {
            if (typeof request.data === 'string') {
                fetchOptions.body = request.data;
            } else {
                fetchOptions.body = serializeQuery(request.data);
                contentType = contentType || 'application/x-www-form-urlencoded; charset=UTF-8';
            }
        }
        if (contentType) fetchOptions.headers['Content-Type'] = contentType;
        if (request.dataType === 'json') fetchOptions.headers['accept'] = 'application/json';

        return fetch(request.url, fetchOptions).then(function (response) {
            if (!response.ok) {
                // Resolve body for error context, then reject (status >= 400).
                return response.text().then(function (text) {
                    var err = new Error('Request failed: ' + response.status);
                    err.status = response.status;
                    err.responseText = text;
                    throw err;
                });
            }
            if (request.dataType === 'json' || (request.headers && request.headers.accept === 'application/json')) {
                return response.json();
            }
            return response.text();
        });
    }

    // --- ApiClient -----------------------------------------------------------
    //
    // Only the subset of ApiClient that real plugin config pages call. See
    // scratch/jellyfin-web/src/apiclient.d.ts and the jellyfin-apiclient bundle.
    function ApiClient() {}

    ApiClient.prototype.getUrl = function (name, params, baseUrl) {
        return getUrl(name, params, baseUrl);
    };
    ApiClient.prototype.serverAddress = function () { return SERVER_ADDRESS; };
    ApiClient.prototype.getCurrentUserId = function () { return USER_ID; };
    ApiClient.prototype.accessToken = function () { return ACCESS_TOKEN; };

    ApiClient.prototype.ajax = function (request, includeAuth) {
        if (!request) return Promise.reject('Request cannot be null');
        return this.fetch(request, includeAuth);
    };
    ApiClient.prototype.fetch = function (request, includeAuth) {
        return doFetch(request, includeAuth);
    };
    ApiClient.prototype.get = function (url) {
        return this.ajax({ type: 'GET', url: url });
    };
    ApiClient.prototype.getJSON = function (url, includeAuth) {
        return this.fetch(
            { url: url, type: 'GET', dataType: 'json', headers: { accept: 'application/json' } },
            includeAuth
        );
    };

    // Plugin / system configuration endpoints.
    ApiClient.prototype.getPluginConfiguration = function (id) {
        if (!id) throw new Error('null Id');
        return this.getJSON(this.getUrl('Plugins/' + id + '/Configuration'));
    };
    ApiClient.prototype.updatePluginConfiguration = function (id, configuration) {
        if (!id) throw new Error('null Id');
        if (!configuration) throw new Error('null configuration');
        var url = this.getUrl('Plugins/' + id + '/Configuration');
        return this.ajax({
            type: 'POST',
            url: url,
            data: JSON.stringify(configuration),
            contentType: 'application/json',
        });
    };
    ApiClient.prototype.getNamedConfiguration = function (name) {
        return this.getJSON(this.getUrl('System/Configuration/' + name));
    };
    ApiClient.prototype.updateNamedConfiguration = function (name, configuration) {
        if (!name) throw new Error('null name');
        if (!configuration) throw new Error('null configuration');
        var url = this.getUrl('System/Configuration/' + name);
        return this.ajax({
            type: 'POST',
            url: url,
            data: JSON.stringify(configuration),
            contentType: 'application/json',
        });
    };

    // Generic helpers some plugins call (users, JSON GET by URL).
    ApiClient.prototype.getUsers = function () {
        return this.getJSON(this.getUrl('Users'));
    };
    ApiClient.prototype.getCurrentUser = function () {
        return this.getJSON(this.getUrl('Users/' + USER_ID));
    };

    window.ApiClient = new ApiClient();

    // --- Dashboard -----------------------------------------------------------
    //
    // The helper object jellyfin-web exposes as window.Dashboard (see
    // scratch/jellyfin-web/src/utils/dashboard.js). We bridge the UI feedback
    // methods to native; data methods delegate to window.ApiClient.
    var Dashboard = {
        getPluginUrl: function (name) {
            return 'configurationpage?name=' + encodeURIComponent(name);
        },
        serverAddress: function () { return SERVER_ADDRESS; },
        getCurrentUserId: function () { return USER_ID; },
        getCurrentUser: function () { return window.ApiClient.getCurrentUser(); },

        // Called by plugins after a successful updatePluginConfiguration POST.
        // Hides the loading spinner and shows a native "saved" confirmation.
        processPluginConfigurationUpdateResult: function () {
            try { window.NativeInterface.onConfigSaved(); } catch (e) {}
        },
        processServerConfigurationUpdateResult: function () {
            try { window.NativeInterface.onConfigSaved(); } catch (e) {}
        },
        processErrorResponse: function (response) {
            var msg = (response && (response.statusText || response.responseText)) || 'Request failed';
            try { window.NativeInterface.onConfigError(String(msg)); } catch (e) {}
        },

        showLoadingMsg: function () { try { window.NativeInterface.onLoading(true); } catch (e) {} },
        hideLoadingMsg: function () { try { window.NativeInterface.onLoading(false); } catch (e) {} },

        alert: function (options) {
            var msg = typeof options === 'string' ? options : (options && options.message) || '';
            try { window.NativeInterface.onAlert(String(msg)); } catch (e) {}
        },
        confirm: function (message, title, callback) {
            try {
                window.NativeInterface.onConfirm(String(message || ''), String(title || ''));
            } catch (e) {}
            // jellyfin-web invokes callback(true). We can't do a real native
            // modal synchronously from JS, so default to proceeding (true).
            if (typeof callback === 'function') { try { callback(true); } catch (e) {} }
        },

        navigate: function (url) {
            try { window.NativeInterface.onNavigate(String(url)); } catch (e) {}
        },
        logout: function () {},
    };
    window.Dashboard = Dashboard;

    // --- emby-* custom element stubs -----------------------------------------
    //
    // Modern jellyfin-web ignores the `data-require="emby-input,..."` attribute
    // and instead registers these as global custom elements at startup. Most
    // plugin config pages use plain <input>/<select>/<button>, but a few write
    // <emby-select>/<emby-button>. Register lightweight stubs that simply render
    // their light-DOM children so those pages don't collapse. This is registered
    // defensively: customElements.define throws if called twice for the same name.
    function defineStub(tagName) {
        try {
            if (customElements.get(tagName)) return;
            customElements.define(tagName, class extends HTMLElement {
                connectedCallback() { /* children render naturally */ }
            });
        } catch (e) {}
    }
    ['emby-input', 'emby-button', 'emby-select', 'emby-checkbox', 'emby-linkbutton',
     'emby-textarea', 'emby-collapse', 'emby-radio'].forEach(defineStub);

    // Dispatch the `pageshow` event on the document so Pattern-A plugin pages
    // (inline <script> that registers addEventListener('pageshow', loadConfig))
    // trigger their form population. The host also calls onPageFinished bootstrap
    // as a backstop; this runs once the globals above are established.
    function firePageShow() {
        try {
            var event = new Event('pageshow', { bubbles: true, cancelable: true });
            document.dispatchEvent(event);
            // Also fire on the plugin page container (data-role="page"), which is
            // what legacy pages actually bind to.
            var page = document.querySelector('[data-role="page"]');
            if (page) page.dispatchEvent(event);
        } catch (e) {}
    }

    // Expose for the host to call after the page finishes loading.
    window.__jellyplayFirePageShow = firePageShow;
    // Fire immediately in case the page's DOM is already parsed.
    if (document.readyState === 'complete' || document.readyState === 'interactive') {
        // Defer slightly so the page's own listeners are attached first.
        setTimeout(firePageShow, 0);
    } else {
        document.addEventListener('DOMContentLoaded', function () { setTimeout(firePageShow, 0); });
    }
})();
