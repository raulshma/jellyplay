// Input-delivery probe driver (wave 17A) for the CMP-wasm "dead region
// below y≈600" follow-up ticket: drives the GATED ?e2eRoute=inputprobe pane
// (WebInputProbePane — an 18-button click-target lattice, no server/sign-in/
// video/Coil) through the SAME headless-Edge CDP machinery as web-verify.mjs
// (Cdp class, Edge spawn, serve.mjs staging copied from that lane), then
// answers one question empirically: do synthetic clicks reach Compose at the
// bottom of the viewport, and through WHICH coordinate pipe?
//
// Per config (window height × --force-device-scale-factor × plain/scroll
// variant) the script measures:
//  A. ENV   — devicePixelRatio, inner/outer size, canvas CSS rect vs bitmap
//             attributes, document.elementFromPoint at six depths, body
//             child inventory (occluder hunt), and — when the browser
//             exposes per-button a11y DOM nodes — their getBoundingClientRect.
//  B. CLICK — every PROBE button clicked through FOUR coordinate modes:
//               quad    — DOM.getBoxModel border-quad center (web-verify's
//                         exact centerOf machinery, replicated verbatim)
//               quaddpr — quad center divided by measured devicePixelRatio
//               rect    — getBoundingClientRect() center via Runtime.evaluate
//                         (definitely CSS px; requires per-button DOM nodes)
//               layout  — first-principles CSS coordinates from the pane's
//                         fixed geometry (row i center = ((W-88)/2, 24+i*50))
//             Each dispatch is asserted through the pane's "P<i>: <count>"
//             AX text with settle-wait + one retry (lane convention); stray
//             increments of OTHER counters are recorded, not discarded — a
//             mis-scaled click that lands on the WRONG row is the exact
//             failure signature this probe exists to catch.
//  C. SCROLL (variant=scroll only) — a CDP mouseWheel into the pane, then a
//             post-scroll click: does a scrolled scroll-container still
//             deliver?
//
// VERDICT POLICY: this is a measurement tool, not a pass/fail gate — exit 0
// means "probe ran to completion" (every config produced its table row);
// findings (dead modes, wrong-row hits) are RESULTS recorded in the JSON and
// the printed table, not script failures. Operational failures (no bundle,
// Edge never up, pane never rendered) do exit non-zero.
//
// Usage:
//   node input-probe.mjs [--dist-dir <webpack output>] [--out-dir <dir>]
//                        [--serve-port 8903] [--cdp-port 9335]
//                        [--edge <msedge path>] [--keep] [--configs a,b,..]
// (no server URL needed — the pane never talks to a server).
import { spawn, spawnSync } from 'node:child_process';
import { cpSync, mkdirSync, mkdtempSync, writeFileSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import WebSocket from 'ws';

const REPO_ROOT = resolve(fileURLToPath(new URL('.', import.meta.url)), '..', '..');

function arg(name, fallback) {
  const i = process.argv.indexOf(`--${name}`);
  if (i === -1) return fallback;
  const v = process.argv[i + 1];
  return v && !v.startsWith('--') ? v : fallback;
}

const DIST_DIR = resolve(
  arg('dist-dir', join(REPO_ROOT, 'apps', 'web', 'build', 'kotlin-webpack', 'wasmJs', 'developmentExecutable')),
);
const OUT_DIR = resolve(arg('out-dir', join(tmpdir(), `jellyplay-input-probe-${Date.now()}`)));
const SERVE_PORT = Number(arg('serve-port', '8903'));
const CDP_PORT = Number(arg('cdp-port', '9335'));
const KEEP = process.argv.includes('--keep');
const EDGE = arg('edge', 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe');

// Matrix (bounded, minutes): the primary config runs the FULL 18-button
// lattice; the rest sample rows chosen to straddle the 600-ish boundary the
// ticket claims (row centers sit at ~24+i*50 CSS px, so "below y≈600" means
// rows 12+). H1800+dsf1.5 is the discriminating experiment for the
// device-pixel hypothesis: if coordinates only die beyond innerHeight*dpr,
// rows 12–17 must die at H900/dsf1.5 (600 = 900/1.5) yet LIVE at
// H1800/dsf1.5 (1200 boundary, far below the lattice).
const MATRIX = [
  { name: 'H900-default', height: 900, buttons: 'all' },
  { name: 'H900-default-scroll', height: 900, variant: 'scroll', buttons: 'all', scroll: true },
  { name: 'H1800-default', height: 1800, buttons: 'sample' },
  { name: 'H900-dsf1', height: 900, dsf: 1, buttons: 'sample' },
  { name: 'H900-dsf15', height: 900, dsf: 1.5, buttons: 'sample' },
  { name: 'H1800-dsf15', height: 1800, dsf: 1.5, buttons: 'sample' },
];
const only = arg('configs', null);
const CONFIGS = only ? MATRIX.filter((c) => only.split(',').includes(c.name)) : MATRIX;
const SAMPLE_ROWS = [0, 5, 11, 12, 15, 17];
const BUTTON_COUNT = 18;

// ── CDP client (verbatim from web-verify.mjs) ─────────────────────────────
class Cdp {
  constructor(wsUrl) {
    this.ws = new WebSocket(wsUrl, { maxPayload: 512 * 1024 * 1024 });
    this.nextId = 1;
    this.pending = new Map();
    this.listeners = [];
    this.ws.on('message', (raw) => {
      const msg = JSON.parse(raw.toString());
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id);
        this.pending.delete(msg.id);
        msg.error ? reject(new Error(`${msg.error.message} (${msg.error.code})`)) : resolve(msg.result);
      } else if (msg.method) {
        for (const l of this.listeners) l(msg);
      }
    });
    this.opened = new Promise((res, rej) => {
      this.ws.on('open', res);
      this.ws.on('error', rej);
    });
  }
  send(method, params = {}) {
    const id = this.nextId++;
    const p = new Promise((resolve, reject) => this.pending.set(id, { resolve, reject }));
    this.ws.send(JSON.stringify({ id, method, params }));
    return p;
  }
  on(fn) { this.listeners.push(fn); }
  close() { this.ws.close(); }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ── AX helpers (from web-verify.mjs) ──────────────────────────────────────
function nodeName(n) { return (n.name && n.name.value) || ''; }
function nodeRole(n) { return (n.role && n.role.value) || ''; }

async function axTree(cdp) {
  const { nodes } = await cdp.send('Accessibility.getFullAXTree');
  return nodes.filter((n) => !n.ignored);
}

async function waitForNode(cdp, label, pred, timeoutMs, pollMs = 300) {
  const deadline = Date.now() + timeoutMs;
  let last = null;
  while (Date.now() < deadline) {
    const nodes = await axTree(cdp);
    const hit = nodes.find(pred);
    if (hit) return hit;
    last = nodes.length;
    await sleep(pollMs);
  }
  throw new Error(`timeout waiting for ${label} (last tree size ${last})`);
}

// The lane's exact coordinate machinery, replicated verbatim for mode `quad`.
async function centerOf(cdp, node) {
  if (!node.backendDOMNodeId) throw new Error('no backendDOMNode');
  const box = await cdp.send('DOM.getBoxModel', { backendNodeId: node.backendDOMNodeId });
  const [x1, y1, , , x2, y2] = box.model.border; // border quad: tl, tr, br, bl
  return { x: (x1 + x2) / 2, y: (y1 + y2) / 2 };
}

// Counter lattice: {"0": 0, "1": 0, ...} from the P<i>: <n> StaticTexts.
function countersFrom(nodes) {
  const out = {};
  for (const n of nodes) {
    if (nodeRole(n) !== 'StaticText') continue;
    const m = /^P(\d+): (\d+)$/.exec(nodeName(n));
    if (m) out[Number(m[1])] = Number(m[2]);
  }
  return out;
}

async function readCounters(cdp) {
  return countersFrom(await axTree(cdp));
}

// ── Phase A: env probes ────────────────────────────────────────────────────
async function envProbe(cdp) {
  const { result } = await cdp.send('Runtime.evaluate', {
    returnByValue: true,
    expression: `(function () {
      const canvasInfo = [...document.querySelectorAll('canvas')].map((c) => {
        const r = c.getBoundingClientRect();
        return { rectX: r.x, rectY: r.y, rectW: r.width, rectH: r.height, attrW: c.width, attrH: c.height };
      });
      const efp = {};
      for (const y of [100, 300, 590, 610, 700, 850]) {
        const e = document.elementFromPoint(700, y);
        efp[y] = e ? e.tagName + (e.id ? '#' + e.id : '') : 'none';
      }
      const children = [...document.body.children].slice(0, 30).map((e) => {
        const r = e.getBoundingClientRect();
        const cls = typeof e.className === 'string' ? e.className : '';
        return { tag: e.tagName, id: e.id || undefined, cls: cls || undefined,
                 x: r.x, y: r.y, w: r.width, h: r.height,
                 pe: getComputedStyle(e).pointerEvents, zi: getComputedStyle(e).zIndex };
      });
      return {
        dpr: window.devicePixelRatio,
        innerW: window.innerWidth, innerH: window.innerHeight,
        outerW: window.outerWidth, outerH: window.outerHeight,
        canvases: canvasInfo, elementFromPoint: efp, bodyChildren: children,
      };
    })()`,
  });
  if (!result.value) throw new Error('env probe returned nothing');
  return result.value;
}

// Per-button DOM rects, when the browser materializes compose semantics as
// real positioned DOM nodes (mode `rect` is unavailable otherwise).
async function domButtonRects(cdp) {
  const { result } = await cdp.send('Runtime.evaluate', {
    returnByValue: true,
    expression: `(function () {
      const out = {};
      for (const e of document.querySelectorAll('[aria-label]')) {
        const m = /^PROBE (\\d+)$/.exec(e.getAttribute('aria-label'));
        if (!m) continue;
        const r = e.getBoundingClientRect();
        out[Number(m[1])] = { x: r.x, y: r.y, w: r.width, h: r.height };
      }
      return out;
    })()`,
  });
  return result.value || {};
}

// ── Phase B: one click through one coordinate mode ─────────────────────────
// Returns { ok, hitOther } — hitOther lists counters that moved when the
// target did not (the mis-scale signature: the click landed on a neighbor).
async function clickOnce(cdp, i, coords, { timeoutMs = 2500, attempts = 2 } = {}) {
  const before = await readCounters(cdp);
  const want = (before[i] ?? -1) + 1;
  for (let a = 0; a < attempts; a++) {
    await cdp.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: coords.x, y: coords.y, button: 'left', clickCount: 1 });
    await cdp.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: coords.x, y: coords.y, button: 'left', clickCount: 1 });
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      await sleep(250);
      const now = await readCounters(cdp);
      if ((now[i] ?? -1) >= want) return { ok: true, hitOther: [] };
      // Even on miss, keep polling to the deadline — compose settles fast but
      // not instantly.
    }
  }
  const after = await readCounters(cdp);
  const hitOther = [];
  for (const k of Object.keys(before)) {
    if (Number(k) !== i && (after[k] ?? 0) > (before[k] ?? 0)) hitOther.push(`P${k}`);
  }
  return { ok: false, hitOther };
}

// ── One config end-to-end ──────────────────────────────────────────────────
async function runConfig(cfg, ctx) {
  const rows = cfg.buttons === 'all' ? [...Array(BUTTON_COUNT).keys()] : SAMPLE_ROWS;
  const url = `http://127.0.0.1:${SERVE_PORT}/?e2eRoute=inputprobe${cfg.variant ? `&variant=${cfg.variant}` : ''}`;
  const out = { name: cfg.name, height: cfg.height, dsf: cfg.dsf ?? null, variant: cfg.variant ?? 'plain', url, clicks: [] };

  // Spawn a FRESH Edge per config (profile + flags differ).
  const profile = mkdtempSync(join(tmpdir(), 'edge-probe-'));
  const flags = [
    '--headless=new',
    `--remote-debugging-port=${CDP_PORT}`,
    `--window-size=1400,${cfg.height}`,
    `--user-data-dir=${profile}`,
    '--no-first-run',
    '--no-default-browser-check',
    '--autoplay-policy=no-user-gesture-required',
  ];
  if (cfg.dsf) flags.push(`--force-device-scale-factor=${cfg.dsf}`);
  flags.push('about:blank');
  const edgeProc = spawn(EDGE, flags, { stdio: 'ignore' });
  edgeProc.on('error', (e) => { throw new Error(`failed to spawn Edge at ${EDGE}: ${e.message}`); });
  ctx.procs.push(edgeProc);
  let cdp = null;
  try {
    let targets = null;
    for (let i = 0; i < 40; i++) {
      await sleep(500);
      try {
        const res = await fetch(`http://127.0.0.1:${CDP_PORT}/json/list`);
        targets = await res.json();
        if (targets.some((t) => t.type === 'page')) break;
      } catch { /* browser not up yet */ }
    }
    if (!targets || !targets.some((t) => t.type === 'page')) throw new Error('Edge CDP endpoint never listed a page target');
    const page = targets.find((t) => t.type === 'page');
    cdp = new Cdp(page.webSocketDebuggerUrl);
    await cdp.opened;
    await cdp.send('Runtime.enable');
    await cdp.send('Page.enable');
    await cdp.send('DOM.enable');

    await cdp.send('Page.navigate', { url });
    await waitForNode(cdp, 'probe pane (PROBE 0 button)', (n) => nodeRole(n) === 'button' && nodeName(n) === 'PROBE 0', 90000);
    await waitForNode(cdp, 'counter P0', (n) => /^P0: \d+$/.test(nodeName(n)), 30000);
    await sleep(500); // let the lattice settle

    // A. ENV.
    out.env = await envProbe(cdp);
    const dpr = out.env.dpr;
    out.domButtonRectsAvailable = null;

    // Per-button rect source (mode `rect`).
    const rects = await domButtonRects(cdp);
    out.domButtonRectsAvailable = Object.keys(rects).length >= rows.length - 1;
    out.rectsSample = rects[0] ? { i: 0, ...rects[0] } : null;

    // Geometry sanity: measured rect step vs the layout model (48px rows +
    // 2px gaps). Warn-level only — recorded, not gating.
    if (rects[0] && rects[1]) {
      out.rectRowStep = +(rects[1].y - rects[0].y).toFixed(2);
      out.rectRowHeight = +rects[0].h.toFixed(2);
    }

    // B. CLICK MATRIX.
    for (const i of rows) {
      const button = await waitForNode(cdp, `PROBE ${i} button`, (n) => nodeRole(n) === 'button' && nodeName(n) === `PROBE ${i}`, 10000);
      const quad = await centerOf(cdp, button).catch((e) => null);
      const modes = {
        quad: quad ? { x: quad.x, y: quad.y } : null,
        quaddpr: quad ? { x: quad.x / dpr, y: quad.y / dpr } : null,
        rect: rects[i] ? { x: rects[i].x + rects[i].w / 2, y: rects[i].y + rects[i].h / 2 } : null,
        layout: { x: (out.env.innerW - 88) / 2, y: 24 + i * 50 },
      };
      for (const [mode, coords] of Object.entries(modes)) {
        if (!coords) {
          out.clicks.push({ mode, i, skipped: 'coordinate source unavailable' });
          continue;
        }
        const res = await clickOnce(cdp, i, coords);
        out.clicks.push({ mode, i, x: +coords.x.toFixed(1), y: +coords.y.toFixed(1), ...res });
      }
    }

    // C. SCROLL phase (scroll variant only): wheel 600px into the pane, then
    // one post-scroll click on the last row through the rect mode (fallback
    // layout) — does a SCROLLED scroll-container still deliver input?
    if (cfg.scroll) {
      const last = rows[rows.length - 1];
      const rectBefore = rects[last] ? { ...rects[last] } : null;
      await cdp.send('Input.dispatchMouseEvent', { type: 'mouseWheel', x: 700, y: 450, deltaX: 0, deltaY: 600 });
      await sleep(800);
      const rectsAfter = await domButtonRects(cdp);
      const target = rectsAfter[last]
        ? { x: rectsAfter[last].x + rectsAfter[last].w / 2, y: rectsAfter[last].y + rectsAfter[last].h / 2 }
        : { x: (out.env.innerW - 88) / 2, y: Math.max(24, 24 + last * 50 - 600) };
      const res = await clickOnce(cdp, last, target);
      out.scrollPhase = {
        wheelDeltaY: 600,
        rowYBefore: rectBefore ? +rectBefore.y.toFixed(1) : null,
        rowYAfter: rectsAfter[last] ? +rectsAfter[last].y.toFixed(1) : null,
        clickRow: last, ...res,
      };
    }
  } finally {
    if (cdp) {
      try { await cdp.send('Browser.close'); } catch { /* ws may already be gone */ }
      cdp.close();
    }
    if (!KEEP) spawnSync('taskkill', ['/PID', String(edgeProc.pid), '/T', '/F']);
  }

  // Compact per-mode verdict for the table.
  out.modes = {};
  for (const mode of ['quad', 'quaddpr', 'rect', 'layout']) {
    const cs = out.clicks.filter((c) => c.mode === mode && !c.skipped);
    if (!cs.length) { out.modes[mode] = 'n/a'; continue; }
    const ok = cs.filter((c) => c.ok).length;
    const wrongRow = cs.filter((c) => !c.ok && c.hitOther && c.hitOther.length).length;
    const lowestOkY = Math.max(...cs.filter((c) => c.ok).map((c) => c.y), -1);
    out.modes[mode] = `${ok}/${cs.length} ok${wrongRow ? `, ${wrongRow} wrong-row` : ''}; lowest ok y=${lowestOkY}`;
  }
  return out;
}

// ── Main ───────────────────────────────────────────────────────────────────
mkdirSync(OUT_DIR, { recursive: true });
const startedAt = Date.now();
let serverProc = null;
const ctx = { procs: [] };
const results = [];
let verdict = 'COMPLETE';
let failure = null;

try {
  // Stage the bundle exactly like web-verify.mjs (webpack output + the
  // resource index.html + processed composeResources — the probe pane needs
  // none of the resources, but staging identically keeps one truth).
  if (!existsSync(join(DIST_DIR, 'webapp.js'))) {
    throw new Error(`webapp.js missing under ${DIST_DIR} — run :apps:web:wasmJsBrowserDevelopmentWebpack`);
  }
  const resourcesDir = join(REPO_ROOT, 'apps', 'web', 'build', 'processedResources', 'wasmJs', 'main');
  cpSync(DIST_DIR, OUT_DIR, { recursive: true });
  cpSync(resourcesDir, OUT_DIR, { recursive: true });
  if (!existsSync(join(OUT_DIR, 'index.html'))) throw new Error(`no index.html under ${resourcesDir} — build first`);

  serverProc = spawn(process.execPath, [join(REPO_ROOT, 'tools', 'e2e', 'serve.mjs'), '--root', OUT_DIR, '--port', String(SERVE_PORT)], { stdio: ['ignore', 'pipe', 'pipe'] });
  await new Promise((res, rej) => {
    const t = setTimeout(() => rej(new Error('serve.mjs did not report ready')), 10000);
    serverProc.stdout.on('data', (d) => { if (d.toString().includes('SERVE_READY')) { clearTimeout(t); res(); } });
    serverProc.on('exit', (c) => rej(new Error(`serve.mjs exited ${c}`)));
  });

  for (const cfg of CONFIGS) {
    process.stdout.write(`\n== config ${cfg.name}\n`);
    const r = await runConfig(cfg, ctx);
    results.push(r);
    const c0 = r.env.canvases[0];
    const canvasLine = c0 ? `canvas ${c0.rectW}x${c0.rectH} css (attr ${c0.attrW}x${c0.attrH})` : 'NO CANVAS';
    process.stdout.write(`   dpr=${r.env.dpr} inner=${r.env.innerW}x${r.env.innerH} ${canvasLine}\n`);
    for (const [m, v] of Object.entries(r.modes)) process.stdout.write(`   ${m.padEnd(7)} ${v}\n`);
    if (r.scrollPhase) {
      process.stdout.write(`   scroll  wheel600: row y ${r.scrollPhase.rowYBefore}→${r.scrollPhase.rowYAfter}, click P${r.scrollPhase.clickRow}: ${r.scrollPhase.ok ? 'ok' : `MISS${r.scrollPhase.hitOther.length ? ' (hit ' + r.scrollPhase.hitOther.join(',') + ')' : ''}`}\n`);
    }
  }
} catch (e) {
  verdict = 'OPERATIONAL-FAIL';
  failure = e.message;
} finally {
  if (serverProc && !KEEP) spawnSync('taskkill', ['/PID', String(serverProc.pid), '/T', '/F']);

  const result = {
    verdict,
    failure,
    startedAt: new Date(startedAt).toISOString(),
    totalMs: Date.now() - startedAt,
    edgeBinary: EDGE,
    distDir: DIST_DIR,
    outDir: OUT_DIR,
    configs: results,
  };
  const jsonPath = join(OUT_DIR, 'input-probe-result.json');
  try {
    writeFileSync(jsonPath, JSON.stringify(result, null, 2));
    process.stdout.write(`\nRESULT_JSON=${jsonPath}\n`);
  } catch (e) {
    process.stdout.write(`\n[warn] could not write ${jsonPath}: ${e.message}\n`);
  }
  process.exit(verdict === 'COMPLETE' ? 0 : 1);
}
