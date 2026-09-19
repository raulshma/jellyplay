package com.raulshma.jellyplay.core.datastore

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [PreferenceCodec.cachedJson] — the promoted per-key "compare-raw →
 * try/decode → update-cache" read — against the exact behaviors the
 * hand-copied sites had, so a conversion cannot silently drift:
 *
 *  - both null-input policies ([CachedJsonNullPolicy.MemoizeNull] /
 *    [CachedJsonNullPolicy.NoMemoOnNull]);
 *  - a decode failure yields the default AND caches that failure result
 *    (a corrupt blob degrades exactly once, never re-parsed per emission);
 *  - an unchanged raw is served from the cache with no re-decode (asserted
 *    via counting parse closures).
 *
 * Pure reads — no DataStore needed, unlike [PreferenceCodecTest].
 */
class CachedJsonTest {

    // ------------------------------------------------------------------
    // MemoizeNull — the HomeDiscovery/PlayerEngine/Widget-class policy
    // ------------------------------------------------------------------

    @Test
    fun `MemoizeNull skips the decode when the raw is unchanged`() {
        var cache = ParsedCache<String?>(null, null)
        var parses = 0
        fun read(raw: String?): String? = PreferenceCodec.cachedJson(
            raw = raw,
            cache = cache,
            default = null,
            parse = { parses++; it.uppercase() },
            cacheRef = { cache = it },
            nullPolicy = CachedJsonNullPolicy.MemoizeNull,
        )

        assertEquals("A", read("a"))
        assertEquals(1, parses)
        // Same raw on the next emission: served from the cache, no re-decode.
        assertEquals("A", read("a"))
        assertEquals(1, parses)
        // A changed raw re-decodes.
        assertEquals("B", read("b"))
        assertEquals(2, parses)
    }

    @Test
    fun `MemoizeNull yields the default for a null raw when no onNull is given`() {
        var cache = ParsedCache<Int>(null, -1)
        var parses = 0
        fun read(raw: String?): Int = PreferenceCodec.cachedJson(
            raw = raw,
            cache = cache,
            default = 0,
            parse = { parses++; it.length },
            cacheRef = { cache = it },
            nullPolicy = CachedJsonNullPolicy.MemoizeNull,
        )

        // Prime the cache with a non-null key first — a fresh
        // ParsedCache(null, …) is ALREADY a null-key memo hit (every store
        // initialises exactly that way, so a first absent-key read is a hit).
        assertEquals(3, read("abc"))
        assertEquals(1, parses)
        assertEquals(0, read(null))
        assertEquals(1, parses) // the parse closure never sees a null raw
        assertEquals(0, read(null))
        assertEquals(1, parses) // the null result is memoised, not re-derived
    }

    @Test
    fun `MemoizeNull memoises the null raw value like any other input`() {
        var cache = ParsedCache<String>(null, "seed")
        var nullReads = 0
        fun read(raw: String?): String = PreferenceCodec.cachedJson(
            raw = raw,
            cache = cache,
            default = "default",
            parse = { "blob:$it" },
            onNull = { nullReads++; "absent" },
            cacheRef = { cache = it },
            nullPolicy = CachedJsonNullPolicy.MemoizeNull,
        )

        // Prime with a non-null raw so the null path actually derives once.
        assertEquals("blob:x", read("x"))
        assertEquals("absent", read(null))
        assertEquals(1, nullReads)
        // The null result was published through cacheRef, so the second null
        // read is a memo hit — the null path is NOT re-derived.
        assertEquals("absent", read(null))
        assertEquals(1, nullReads)
        assertEquals(null, cache.key)
    }

    @Test
    fun `MemoizeNull caches the failure fallback so a corrupt blob degrades exactly once`() {
        var cache = ParsedCache<Int>(null, -1)
        var parses = 0
        fun read(raw: String?): Int = PreferenceCodec.cachedJson(
            raw = raw,
            cache = cache,
            default = 0,
            parse = { parses++; error("corrupt blob") },
            cacheRef = { cache = it },
            nullPolicy = CachedJsonNullPolicy.MemoizeNull,
        )

        // Decode failure → default...
        assertEquals(0, read("corrupt"))
        assertEquals(1, parses)
        // ...AND the failure result is cached: the same corrupt raw is a memo
        // hit, never re-parsed (this is what every hand-copied site's
        // `try/catch { default }.also { cache = … }` did).
        assertEquals(0, read("corrupt"))
        assertEquals(1, parses)
        assertEquals("corrupt", cache.key)
        assertEquals(0, cache.value)
    }

    @Test
    fun `MemoizeNull keys on the composite cacheKey, not the raw alone`() {
        // Mirrors HomeDiscoveryStore's version-stamped enabled-section set:
        // the reader's value depends on the raw AND a schema-version stamp,
        // both folded into cacheKey. The same raw under a new stamp must
        // re-derive, or a stamped write would serve a stale unioned value.
        var cache = ParsedCache<Set<String>>(null, emptySet())
        var parses = 0
        fun read(raw: String?, stamp: Int): Set<String> = PreferenceCodec.cachedJson(
            raw = raw,
            cache = cache,
            default = emptySet(),
            parse = { parses++; setOf(it) },
            cacheRef = { cache = it },
            nullPolicy = CachedJsonNullPolicy.MemoizeNull,
            cacheKey = "v$stamp:$raw",
        )

        assertEquals(setOf("a"), read("a", stamp = 0))
        assertEquals(1, parses)
        assertEquals(setOf("a"), read("a", stamp = 0))
        assertEquals(1, parses) // same raw, same stamp → memo hit
        assertEquals(setOf("a"), read("a", stamp = 1))
        assertEquals(2, parses) // same raw, NEW stamp → re-derive
    }

    // ------------------------------------------------------------------
    // NoMemoOnNull — the VideoPlayerStore legacy-fallback policy
    // ------------------------------------------------------------------

    @Test
    fun `NoMemoOnNull re-derives the null-raw value on every read so legacy inputs stay live`() {
        // The VideoPlayerStore shape: when the JSON blob is absent, the value
        // comes from the legacy boolean keys, which the raw string does not
        // reflect — memoising the null would freeze the legacy fallback out.
        var cache = ParsedCache<String>(null, "stale")
        var legacyValue = "legacy-1"
        var nullReads = 0
        var parses = 0
        fun read(raw: String?): String = PreferenceCodec.cachedJson(
            raw = raw,
            cache = cache,
            default = "default",
            parse = { parses++; "blob:$it" },
            onNull = { nullReads++; legacyValue },
            cacheRef = { cache = it },
            nullPolicy = CachedJsonNullPolicy.NoMemoOnNull,
        )

        assertEquals("legacy-1", read(null))
        assertEquals(1, nullReads)
        assertEquals(0, parses) // the blob decode is never attempted on null
        // Second null read: NOT a memo hit — the null path re-derived.
        legacyValue = "legacy-2"
        assertEquals("legacy-2", read(null))
        assertEquals(2, nullReads)
    }

    @Test
    fun `NoMemoOnNull memoises a non-null raw and a null-keyed entry is never served`() {
        var cache = ParsedCache<String>(null, "seed")
        var parses = 0
        var nullReads = 0
        fun read(raw: String?): String = PreferenceCodec.cachedJson(
            raw = raw,
            cache = cache,
            default = "default",
            parse = { parses++; "blob:$it" },
            onNull = { nullReads++; "legacy" },
            cacheRef = { cache = it },
            nullPolicy = CachedJsonNullPolicy.NoMemoOnNull,
        )

        assertEquals("blob:a", read("a"))
        assertEquals(1, parses)
        // Unchanged non-null raw: normal memo hit.
        assertEquals("blob:a", read("a"))
        assertEquals(1, parses)
        // A null read stores a null-keyed entry — never SERVED under this
        // policy (a hit requires a non-null raw), so a stale null value can
        // never leak into a blob read. It does, however, replace the memoised
        // "a" entry (exactly the pre-promotion VideoPlayerStore write), so the
        // blob decode re-runs after it.
        assertEquals("legacy", read(null))
        assertEquals(1, nullReads)
        assertEquals("blob:a", read("a"))
        assertEquals(2, parses)
        // Re-memoised: the same raw is a hit again.
        assertEquals("blob:a", read("a"))
        assertEquals(2, parses)
        // A different non-null raw re-decodes.
        assertEquals("blob:b", read("b"))
        assertEquals(3, parses)
    }

    @Test
    fun `NoMemoOnNull caches the failure fallback for a non-null raw`() {
        var cache = ParsedCache<Int>(null, -1)
        var parses = 0
        fun read(raw: String?): Int = PreferenceCodec.cachedJson(
            raw = raw,
            cache = cache,
            default = 7,
            parse = { parses++; error("corrupt blob") },
            cacheRef = { cache = it },
            nullPolicy = CachedJsonNullPolicy.NoMemoOnNull,
        )

        assertEquals(7, read("corrupt"))
        assertEquals(7, read("corrupt"))
        assertEquals(1, parses)
        assertEquals("corrupt", cache.key)
        assertEquals(7, cache.value)
    }
}
