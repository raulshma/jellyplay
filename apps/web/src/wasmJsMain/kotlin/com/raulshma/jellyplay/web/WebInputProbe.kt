package com.raulshma.jellyplay.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey

/**
 * Gated e2e-only route key for [WebInputProbePane] (`?e2eRoute=inputprobe`):
 * unlike the OTHER boot routes this key never enters the NavDisplay back
 * stack — [WebAppRoot] renders the pane INSTEAD of the whole shell when it
 * sees this key, bypassing the session/sign-in gate entirely. Same
 * memory-only/no-deep-link lifetime stance as the other web-only keys; same
 * desktop `jellyplay.harness.*` boot-param precedent as [WebDiag]'s demo
 * button cut — no user-facing surface ever sets the parameter.
 */
internal data object WebInputProbe : NavKey

/** Button count for [WebInputProbePane] — 18 rows cover a 900 CSS px viewport
 *  (18×48dp + 17×2dp spacing = 898dp ≈ 898 CSS px; CMP web maps 1dp = 1 CSS
 *  px via the dpr-backed density, so the rows tile the full height with
 *  centers ≈ 25 + i×50). The driver measures real centers; this constant
 *  only needs to be "enough rows to reach the bottom". */
private const val PROBE_ROW_COUNT = 18

/**
 * E2E INPUT DELIVERY PROBE (wave 17A): a minimal, deterministic click target
 * lattice for tools/e2e/input-probe.mjs — the re-runnable repro for the
 * "CMP-wasm input dead-region below y≈600" ticket (wave 17A verdict: NO
 * dead region exists — the report was crash-contaminated; measured evidence
 * in docs/e2e/web-input-dead-region.md). Reached ONLY via
 * `?e2eRoute=inputprobe` (parsed in Main.kt); [WebAppRoot] short-circuits to
 * this pane before any shell state exists, so the pane runs with NO server,
 * NO sign-in, NO video host and NO Coil — every input variable stripped
 * except Compose-viewport hit-testing itself. That isolation is the point:
 * the Diagnostics pane mixed Coil, a &lt;video&gt; overlay and scroll into the
 * same observation.
 *
 * Surface contract with the driver (do not reword without updating
 * tools/e2e/input-probe.mjs — same rule as WebDiagnosticsPane's lines):
 *  - button i is an AX button named exactly `PROBE i`;
 *  - its click count renders as an AX StaticText `P<i>: <count>` OUTSIDE the
 *    button (buttons merge their text descendants, so the counter must not
 *    live inside the button or it would disappear from the AX tree).
 *
 * `&variant=scroll` wraps the SAME lattice in a verticalScroll Column with a
 * 240dp end spacer (the non-scroll variant clips the spacer harmlessly) —
 * the "scrollable interaction eats events" hypothesis needs a scrollable
 * that actually has scroll range, not just the modifier attached.
 *
 * Not a feature screen, deliberately unstyled beyond defaults; no i18n.
 */
@Composable
internal fun WebInputProbePane(scrollable: Boolean) {
    // SnapshotStateList indexed set triggers recomposition of exactly the
    // rows whose counter text is read — one shared list beats 18 states.
    val counts = remember { mutableStateListOf<Int>().apply { repeat(PROBE_ROW_COUNT) { add(0) } } }

    Column(
        modifier = if (scrollable) {
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        } else {
            Modifier.fillMaxSize()
        },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(PROBE_ROW_COUNT) { i ->
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { counts[i] = counts[i] + 1 },
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 8.dp),
                ) {
                    Text("PROBE $i")
                }
                Text(
                    text = "P$i: ${counts[i]}",
                    modifier = Modifier.width(80.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        // Scroll range for the scrollable variant only (see KDoc); the plain
        // Column simply clips it below the viewport.
        Box(modifier = Modifier.fillMaxWidth().height(240.dp))
    }
}
