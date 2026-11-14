package com.raulshma.jellyplay.desktop.player.mpv

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import kotlin.test.Test
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Pins [MpvLib.create]'s LC_NUMERIC contract — the fix for the "Non-C locale
 * detected" outage: mpv refuses to create a core when libc's numeric locale
 * is anything but `C`/`C.UTF-8`, and on Linux/macOS the JVM adopts the
 * environment locale at startup (`setlocale(LC_ALL, "")`), so any machine
 * whose locale is not literally `C` got a NULL from every create — engine,
 * sw-render probe and audio-device enumerator alike. Invisible to CI, which
 * carries no libmpv and skips every live-mpv suite.
 *
 * The reset is reproduced with libc directly (the same `setlocale` call the
 * JVM startup and a GTK init make). The hazard is proven live before
 * asserting: the RAW create must return NULL under the non-C locale, or this
 * machine cannot exercise the contract and the case skips — so the test can
 * never pass vacuously, and a future "simplify to one set at load time"
 * regression goes red here instead of on a user's machine.
 */
class MpvLibNumericLocaleTest {

    private interface CLocale : Library {
        fun setlocale(category: Int, locale: String?): String?
    }

    @Test
    fun `create succeeds after the process numeric locale goes non-C, on every create`() {
        assumeTrue(!Platform.isWindows()) { "the JVM leaves the CRT locale at C on Windows" }
        assumeTrue(try { MpvLib.mpv; true } catch (_: Throwable) { false }) {
            "libmpv not available on this machine"
        }

        val libc = Native.load(Platform.C_LIBRARY_NAME, CLocale::class.java)
        val lcNumeric = if (Platform.isLinux()) 1 else 4 // macOS/BSD

        repeat(2) { round ->
            // What the JVM startup and a GTK init both do to this category:
            // adopt the environment locale, or a common desktop one. The
            // C-family rejection lives inside the search, and every
            // non-adoptable answer — the just-adopted C, or NULL (glibc
            // reports an env locale that is unavailable or invalid as
            // failure) — advances to the next candidate instead of ending
            // the search.
            val adopted = listOf("", "en_US.UTF-8", "de_DE.UTF-8", "fr_FR.UTF-8")
                .firstNotNullOfOrNull { name ->
                    libc.setlocale(lcNumeric, name)
                        ?.takeIf { it != "C" && it != "C.UTF-8" && it != "POSIX" }
                }
            assumeTrue(adopted != null) {
                "no non-C locale installable on this machine (cannot exercise the contract)"
            }

            // Hazard proof: the raw call must fail under this locale, or the
            // machine proves nothing about the contract.
            val raw = MpvLib.mpv.mpv_create()
            if (raw != null) {
                MpvLib.mpv.mpv_terminate_destroy(raw)
                assumeTrue(false) {
                    "mpv accepted LC_NUMERIC=$adopted (raw create non-null) — cannot exercise the contract"
                }
            }

            // The contract: create() re-establishes the C numeric locale and
            // returns a live core — EVERY time, not just the first call.
            val core = assertNotNull(MpvLib.create(), "round $round: create must survive LC_NUMERIC=$adopted")
            MpvLib.mpv.mpv_terminate_destroy(core)
        }
    }
}
