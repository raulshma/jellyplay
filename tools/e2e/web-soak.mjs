// Long-session Coil cache/heap verification driver for the apps/web shell
// (wave 18A). Forked/trimmed from web-verify.mjs — same staging, same Edge
// spawn, same Cdp class, same AX→DOM.getBoxModel click technique; the
// per-step assertion ladder of the verification lane is replaced by a CYCLE
// loop that measures stability over time:
//
//   phase a) boot + sign-in + Diagnostics pane + IMAGE_STATE: OK baseline
//            (this first entry is the COLD load: memory-cache misses, real
//            network fetches of the artwork).
//   phase b) cycles: Back → Diagnostics → wait IMAGE_STATE: OK → record
//            ms-to-OK, the per-cycle delta of Network.responseReceived for
//            /Items/<id>/Images/ URLs, and a Performance.getMetrics sample
//            (JSHeapUsedSize/JSHeapTotalSize/Nodes/JSEventListeners/
//            Documents). HeapProfiler.collectGarbage runs before every 5th
//            sample (that sample is the GC'd reading); a screenshot is kept
//            for every 10th cycle. Each pane entry also recreates the
//            WebDiagnostics video host div (VideoCheck's DisposableEffect),
//            so Nodes/Listeners double as the no-leak vector for it.
//   phase c) reload sub-phase ×3: fresh Page.navigate → re-sign-in →
//            Diagnostics → the artwork responses' fromDiskCache flags are
//            recorded (informational: the wasm singleton has NO Coil disk
//            cache — cross-reload persistence can only come from the browser
//            HTTP cache honoring Jellyfin's Cache-Control headers).
//   phase d) results + per-criterion verdict JSON in the out-dir.
//
// The Coil-side counters come from the app itself: the Diagnostics pane's
// COIL_STATS / COIL_CACHE AX lines (Main.kt CoilStats, wave 18A).
//
// Usage:
//   node web-soak.mjs --cycles 50 \
//     --server-url http://localhost:8096 \
//     --username harness --password harness-e2e-pass \
//     [--duration-ms <ms>] (alternative to --cycles: run until elapsed)
//     [--sample-ms <ms>] (AX poll interval inside waits, default 400)
//     [--dist-dir <webpack output>] [--out-dir <results dir>] [--keep]
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

const SERVER_URL = arg('server-url', 'http://localhost:8096');
const USERNAME = arg('username', 'harness');
const PASSWORD = arg('password', 'harness-e2e-pass');
const CYCLES = Number(arg('cycles', '50'));
const DURATION_MS = Number(arg('duration-ms', '0')); // >0 overrides CYCLES
const SAMPLE_MS = Number(arg('sample-ms', '400'));
const DIST_DIR = resolve(
  arg('dist-dir', join(REPO_ROOT, 'apps', 'web', 'build', 'kotlin-webpack', 'wasmJs', 'developmentExecutable')),
);
const OUT_DIR = resolve(arg('out-dir', join(tmpdir(), `jellyplay-web-soak-${Date.now()}`)));
const SERVE_PORT = Number(arg('serve-port', '8901'));
const CDP_PORT = Number(arg('cdp-port', '9333'));
const KEEP = process.argv.includes('--keep');
const EDGE = arg('edge', 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe');

// The fixture's single artwork URL shape (Jellyfin item Primary image). The
// id arrives from the API in compact 32-hex form but buildItemImageUrl
// NORMALIZES to the dashed GUID (ImageUrlBuilder), so both segment shapes are
// accepted (first run's lesson: the compact-only regex matched nothing).
const IMAGES_URL_RE = /\/Items\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\/Images\/|\/Items\/[0-9a-f]{32}\/Images\//i;

// ── CDP client (verbatim fork from web-verify.mjs) ────────────────────────
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
function log(msg) { process.stdout.write(`[soak] ${msg}\n`); }

// ── AX helpers (fork from web-verify.mjs) ─────────────────────────────────
function nodeName(n) { return (n.name && n.name.value) || ''; }
function nodeRole(n) { return (n.role && n.role.value) || ''; }
function nodeValue(n) { return (n.value && n.value.value) || ''; }

async function axTree(cdp) {
  const { nodes } = await cdp.send('Accessibility.getFullAXTree');
  return nodes.filter((n) => !n.ignored);
}

async function snapshotTexts(cdp) {
  return (await axTree(cdp))
    .filter((n) => nodeRole(n) === 'StaticText')
    .map((n) => nodeName(n))
    .filter((s) => s.length > 0)
    .slice(0, 40);
}

async function waitForNode(cdp, label, pred, timeoutMs, pollMs = SAMPLE_MS) {
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

async function centerOf(cdp, node) {
  if (!node.backendDOMNodeId) throw new Error('no backendDOMNode');
  const box = await cdp.send('DOM.getBoxModel', { backendNodeId: node.backendDOMNodeId });
  const [x1, y1, , , x2, y2] = box.model.border;
  return { x: (x1 + x2) / 2, y: (y1 + y2) / 2 };
}

async function clickNode(cdp, node, label) {
  const { x, y } = await centerOf(cdp, node);
  await cdp.send('Input.dispatchMouseEvent', { type: 'mousePressed', x, y, button: 'left', clickCount: 1 });
  await cdp.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x, y, button: 'left', clickCount: 1 });
}

// Positional textbox pick (Compose exposes no field names — web-verify lesson).
async function textboxAt(cdp, index, label, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const tbs = (await axTree(cdp)).filter((n) => nodeRole(n).toLowerCase() === 'textbox');
    if (tbs.length > index) return tbs[index];
    await sleep(300);
  }
  throw new Error(`timeout waiting for textbox #${index} (${label})`);
}

async function typeIntoField(cdp, node, label, text, { requireContains = true } = {}) {
  await clickNode(cdp, node, label);
  await sleep(250);
  await cdp.send('Input.insertText', { text });
  const accepted = async () => {
    const fresh = (await axTree(cdp)).find((n) => n.backendDOMNodeId === node.backendDOMNodeId);
    if (!fresh) return false;
    const v = nodeValue(fresh);
    return requireContains ? v.includes(text) : v.length > 0;
  };
  for (let i = 0; i < 8; i++) {
    await sleep(200);
    if (await accepted()) return 'insertText';
  }
  for (const ch of text) {
    await cdp.send('Input.dispatchKeyEvent', {
      type: 'keyDown', text: ch, key: ch, windowsVirtualKeyCode: ch.charCodeAt(0),
    });
    await cdp.send('Input.dispatchKeyEvent', {
      type: 'keyUp', key: ch, windowsVirtualKeyCode: ch.charCodeAt(0),
    });
    await sleep(20);
  }
  for (let i = 0; i < 8; i++) {
    await sleep(200);
    if (await accepted()) return 'keyEvents';
  }
  throw new Error(`could not type into ${label}`);
}

async function buttonEnabled(cdp, buttonName) {
  const btn = (await axTree(cdp)).find(
    (n) => nodeRole(n) === 'button' && nodeName(n) === buttonName,
  );
  if (!btn) return null;
  const dis = (btn.properties || []).find((p) => p.name === 'disabled');
  if (!dis) return true;
  const v = dis.value && typeof dis.value === 'object' ? dis.value.value : dis.value;
  return v !== true;
}

// ── Composed flows ────────────────────────────────────────────────────────

// Connect + sign-in (trimmed web-verify steps 5–6). Handles the reload-phase
// wrinkle that the server URL field arrives PRE-FILLED from the persisted
// last-server-url DataStore: if it already holds the target URL it is left
// alone (typing would concatenate; web-verify's lane starts from a blank
// field so it never needed this).
async function connectAndSignIn(cdp) {
  await waitForNode(cdp, 'connect form', (n) => nodeName(n).includes('Connect to your Jellyfin server'), 90000);
  const field = await textboxAt(cdp, 0, 'Server URL field');
  const current = nodeValue((await axTree(cdp)).find((n) => n.backendDOMNodeId === field.backendDOMNodeId) || field);
  let how = 'prefilled';
  if (current !== SERVER_URL) {
    if (current.length > 0) {
      // Select-all + Delete via CDP keys (web-verify clearField).
      await clickNode(cdp, field, 'Server URL field');
      await sleep(200);
      const ctrlA = { modifiers: 2, key: 'a', code: 'KeyA', windowsVirtualKeyCode: 65 };
      await cdp.send('Input.dispatchKeyEvent', { type: 'keyDown', ...ctrlA });
      await cdp.send('Input.dispatchKeyEvent', { type: 'keyUp', ...ctrlA });
      await cdp.send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Delete', code: 'Delete', windowsVirtualKeyCode: 46 });
      await cdp.send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Delete', code: 'Delete', windowsVirtualKeyCode: 46 });
      await sleep(200);
    }
    how = await typeIntoField(cdp, field, 'Server URL field', SERVER_URL);
  }
  const connect = await waitForNode(cdp, 'Connect button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Connect', 10000);
  await clickNode(cdp, connect, 'Connect button');

  await waitForNode(cdp, 'sign-in section', (n) => nodeName(n).startsWith('Sign in to'), 30000);
  const deadline = Date.now() + 15000;
  let tbs;
  for (;;) {
    tbs = (await axTree(cdp)).filter((n) => nodeRole(n).toLowerCase() === 'textbox');
    if (tbs.length >= 3) break;
    if (Date.now() > deadline) throw new Error(`expected 3 textboxes, got ${tbs.length}`);
    await sleep(300);
  }
  const positioned = await Promise.all(
    tbs.slice(1).map(async (n) => ({ node: n, y: (await centerOf(cdp, n)).y })),
  );
  positioned.sort((a, b) => a.y - b.y);
  await typeIntoField(cdp, positioned[0].node, 'Username field', USERNAME);
  await typeIntoField(cdp, positioned[1].node, 'Password field', PASSWORD, { requireContains: false });
  const signIn = await waitForNode(cdp, 'Sign in button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Sign in', 10000);
  for (let i = 0; i < 25; i++) {
    if (await buttonEnabled(cdp, 'Sign in')) break;
    await sleep(400);
    if (i === 24) throw new Error('Sign in never became enabled (typing did not land)');
  }
  await clickNode(cdp, signIn, 'Sign in button');
  await waitForNode(cdp, 'ConnectedCard', (n) => nodeName(n) === 'Connected', 60000);
  return `server URL via ${how}`;
}

// Open Diagnostics and wait for the artwork to decode. Returns the ms-to-OK
// split into pane-open and image-decode legs. The AX tree updates
// asynchronously behind the Compose canvas: right after the pane title
// appears, the PREVIOUS pane's IMAGE_STATE: OK line can still be served
// (first run's lesson: msToOk=3ms on a cold entry that really fetched). The
// settle beat lets the stale line drain before the OK poll starts.
async function openDiagnosticsAndWaitOk(cdp) {
  const t0 = Date.now();
  const diag = await waitForNode(cdp, 'Diagnostics button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Diagnostics', 10000);
  await clickNode(cdp, diag, 'Diagnostics button');
  await waitForNode(cdp, 'pane title', (n) => nodeName(n) === 'Web diagnostics', 15000);
  await sleep(250);
  const t1 = Date.now();
  let imageLine = '';
  try {
    const okNode = await waitForNode(cdp, 'IMAGE_STATE OK line', (n) => nodeName(n).startsWith('IMAGE_STATE: OK'), 60000, 250);
    imageLine = nodeName(okNode);
  } catch (e) {
    throw new Error(`${e.message}; texts=${JSON.stringify(await snapshotTexts(cdp))}`);
  }
  return { msToOk: Date.now() - t1, msToOpen: t1 - t0, imageLine };
}

// ── Metrics plumbing ──────────────────────────────────────────────────────
async function performanceSample(cdp) {
  const { metrics } = await cdp.send('Performance.getMetrics');
  const pick = {};
  for (const m of metrics) {
    if (['JSHeapUsedSize', 'JSHeapTotalSize', 'Nodes', 'JSEventListeners', 'Documents'].includes(m.name)) {
      pick[m.name] = m.value;
    }
  }
  return pick;
}

function parseCoilStats(texts) {
  const stats = texts.find((t) => t.startsWith('COIL_STATS:'));
  if (!stats) return null;
  const m = /COIL_STATS: hits=(\d+) misses=(\d+) net=(\d+) fail=(\d+)/.exec(stats);
  if (!m) return { raw: stats, parseError: true };
  return { hits: +m[1], misses: +m[2], net: +m[3], fail: +m[4], raw: stats };
}

function parseCoilCache(texts) {
  const line = texts.find((t) => t.startsWith('COIL_CACHE:'));
  if (!line) return null;
  const m = /COIL_CACHE: (?:none|size=(\d+) maxSize=(\d+))/.exec(line);
  if (!m) return { raw: line, parseError: true };
  return m[1] === undefined ? { size: null, maxSize: null, raw: line } : { size: +m[1], maxSize: +m[2], raw: line };
}

// Least-squares slope of y over x = 0..n-1.
function slope(series) {
  const n = series.length;
  if (n < 2) return 0;
  const mx = (n - 1) / 2;
  const my = series.reduce((a, b) => a + b, 0) / n;
  let num = 0;
  let den = 0;
  for (let i = 0; i < n; i++) {
    num += (i - mx) * (series[i] - my);
    den += (i - mx) * (i - mx);
  }
  return den === 0 ? 0 : num / den;
}

// ── Main ──────────────────────────────────────────────────────────────────
mkdirSync(OUT_DIR, { recursive: true });
let serverProc = null;
let edgeProc = null;
let cdp = null;
let verdict = 'FAIL';
let failure = null;

const consoleErrors = [];
const exceptions = [];
const logErrors = [];
const consoleLogs = []; // last 100 console.log lines (Coil DebugLogger probe)
const imageResponses = []; // rolling log of /Items/*/Images/ responses
let networkEnabledAt = 0;

const cycles = [];
const reloads = [];

async function main() {
  // Stage the bundle (web-verify step 0, same layout requirements).
  if (!existsSync(join(DIST_DIR, 'webapp.js'))) {
    throw new Error(`webapp.js missing under ${DIST_DIR} — run :apps:web:wasmJsBrowserDevelopmentWebpack`);
  }
  const resourcesDir = join(REPO_ROOT, 'apps', 'web', 'build', 'processedResources', 'wasmJs', 'main');
  cpSync(DIST_DIR, OUT_DIR, { recursive: true });
  cpSync(resourcesDir, OUT_DIR, { recursive: true });
  if (!existsSync(join(OUT_DIR, 'index.html'))) throw new Error('no index.html in staged bundle');

  // Static server (web-verify step 1).
  serverProc = spawn(process.execPath, [join(REPO_ROOT, 'tools', 'e2e', 'serve.mjs'), '--root', OUT_DIR, '--port', String(SERVE_PORT)], { stdio: ['ignore', 'pipe', 'pipe'] });
  await new Promise((res, rej) => {
    const t = setTimeout(() => rej(new Error('serve.mjs did not report ready')), 10000);
    serverProc.stdout.on('data', (d) => { if (d.toString().includes('SERVE_READY')) { clearTimeout(t); res(); } });
    serverProc.on('exit', (c) => rej(new Error(`serve.mjs exited ${c}`)));
  });

  // Headless Edge (web-verify step 2).
  const profile = mkdtempSync(join(tmpdir(), 'edge-soak-'));
  edgeProc = spawn(EDGE, [
    '--headless=new',
    `--remote-debugging-port=${CDP_PORT}`,
    '--window-size=1400,900',
    `--user-data-dir=${profile}`,
    '--no-first-run',
    '--no-default-browser-check',
    '--autoplay-policy=no-user-gesture-required',
    'about:blank',
  ], { stdio: 'ignore' });
  edgeProc.on('error', (e) => { throw new Error(`failed to spawn Edge at ${EDGE}: ${e.message}`); });
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

  // CDP session + error/network/performance tracking (web-verify step 3,
  // plus the three domains the soak measures with).
  const res2 = await fetch(`http://127.0.0.1:${CDP_PORT}/json/list`);
  const page = (await res2.json()).find((t) => t.type === 'page');
  cdp = new Cdp(page.webSocketDebuggerUrl);
  await cdp.opened;
  cdp.on((msg) => {
    if (msg.method === 'Runtime.consoleAPICalled') {
      if (msg.params.type === 'error') {
        consoleErrors.push(msg.params.args.map((a) => a.value ?? a.description ?? a.type).join(' ').slice(0, 400));
      } else if (msg.params.type === 'log' || msg.params.type === 'warning') {
        const line = msg.params.args.map((a) => a.value ?? a.description ?? a.type).join(' ').slice(0, 300);
        if (consoleLogs.length < 100) consoleLogs.push(line);
      }
    }
    if (msg.method === 'Runtime.exceptionThrown') {
      exceptions.push((msg.params.exceptionDetails.exception?.description || msg.params.exceptionDetails.text || '').slice(0, 400));
    }
    if (msg.method === 'Log.entryAdded' && msg.params.entry.level === 'error') {
      logErrors.push(`${msg.params.entry.source}: ${msg.params.entry.text}`.slice(0, 300));
    }
    if (msg.method === 'Network.responseReceived') {
      const r = msg.params.response;
      if (IMAGES_URL_RE.test(r.url)) {
        imageResponses.push({
          t: Date.now(),
          url: r.url.replace(/^https?:\/\/[^/]+/, ''),
          status: r.status,
          fromDiskCache: r.fromDiskCache === true,
        });
      }
    }
  });
  await cdp.send('Runtime.enable');
  await cdp.send('Page.enable');
  await cdp.send('Log.enable');
  await cdp.send('DOM.enable');
  await cdp.send('Network.enable');
  await cdp.send('Performance.enable');
  await cdp.send('HeapProfiler.enable');
  networkEnabledAt = Date.now();
  await cdp.send('Runtime.evaluate', {
    expression: `window.addEventListener('unhandledrejection', function (e) { window.__jpErr = 'REJECTION: ' + String((e.reason && e.reason.message) || e.reason).slice(0, 300); });
      window.addEventListener('error', function (e) { window.__jpErr = 'ERROR: ' + String(e.message).slice(0, 300); });`,
    returnByValue: true,
  });

  // Phase a) boot + sign-in + Diagnostics baseline (COLD load).
  await cdp.send('Page.navigate', { url: `http://127.0.0.1:${SERVE_PORT}/` });
  log(`boot sign-in (${await connectAndSignIn(cdp)})`);
  const base = await openDiagnosticsAndWaitOk(cdp);
  const baselineTexts = await snapshotTexts(cdp);
  const baselineStats = parseCoilStats(baselineTexts) ?? { raw: '(missing)' };
  const baselineCache = parseCoilCache(baselineTexts) ?? { raw: '(missing)' };
  // Wait a beat so both concurrent artwork requests (raw painter + MediaImage)
  // have settled their counters, then re-read once.
  await sleep(1500);
  const settledStats = parseCoilStats(await snapshotTexts(cdp)) ?? baselineStats;
  const cycle1Fetches = imageResponses.filter((r) => r.t >= networkEnabledAt).length;
  const cycle0 = {
    phase: 'baseline-cold',
    ...base,
    coilStats: settledStats,
    coilCache: parseCoilCache(await snapshotTexts(cdp)) ?? baselineCache,
    imageFetchCount: cycle1Fetches,
    metrics: await performanceSample(cdp),
  };
  cycles.push(cycle0);
  log(`baseline cold: fetches=${cycle1Fetches} stats=${settledStats.raw} heap=${Math.round(cycle0.metrics.JSHeapUsedSize / 1048576)}MB`);

  // Phase b) the cycle loop. Cycle numbering here matches the mission's
  // PASS-criteria language: cycle-1 = the cold baseline above, cycle-2 =
  // first warm re-entry (heap baseline), and so on.
  const tStart = Date.now();
  const totalCycles = DURATION_MS > 0 ? Number.MAX_SAFE_INTEGER : CYCLES;
  let prevNet = settledStats.net ?? 0;
  for (let cycle = 2; cycle <= totalCycles; cycle++) {
    if (DURATION_MS > 0 && Date.now() - tStart >= DURATION_MS) break;
    const back = await waitForNode(cdp, 'diagnostics Back button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Back', 10000);
    await clickNode(cdp, back, 'diagnostics Back button');
    await waitForNode(cdp, 'ConnectedCard', (n) => nodeName(n) === 'Connected', 15000);
    const windowStart = Date.now();
    const { msToOk, msToOpen } = await openDiagnosticsAndWaitOk(cdp);

    // Settle both artwork loads' counters before reading (same beat as
    // baseline; the AX poll interval already ran during msToOk).
    await sleep(300);
    const texts = await snapshotTexts(cdp);
    const stats = parseCoilStats(texts);
    const cacheLine = parseCoilCache(texts);

    // GC before every 5th sample; that sample reads post-GC heap.
    let gc = false;
    if (cycle % 5 === 0) {
      await cdp.send('HeapProfiler.collectGarbage');
      gc = true;
      await sleep(150);
    }
    const metrics = await performanceSample(cdp);
    const fetchesThisCycle = imageResponses.filter((r) => r.t >= windowStart).length;
    // Cross-check: the app-side Coil net counter must move in lockstep with
    // the network events (a Coil fetch without a network event or vice versa
    // would mean the two observability layers disagree).
    const netDelta = stats ? stats.net - prevNet : null;
    if (stats) prevNet = stats.net;

    const record = {
      cycle,
      msToOpen,
      msToOk,
      coilStats: stats,
      coilCache: cacheLine,
      imageFetchCount: fetchesThisCycle,
      coilNetDelta: netDelta,
      gc,
      metrics,
    };
    cycles.push(record);

    if (cycle % 10 === 0) {
      const { data } = await cdp.send('Page.captureScreenshot', { format: 'png' });
      writeFileSync(join(OUT_DIR, `soak-cycle-${cycle}.png`), Buffer.from(data, 'base64'));
      record.screenshot = `soak-cycle-${cycle}.png`;
    }
    log(`cycle ${cycle}: ok=${msToOk}ms fetches=${fetchesThisCycle} gc=${gc} heap=${Math.round(metrics.JSHeapUsedSize / 1048576)}MB nodes=${metrics.Nodes} listeners=${metrics.JSEventListeners} ${stats ? stats.raw : '(no COIL_STATS)'}`);

    // Hard stop on console damage so a wedged page does not burn the clock.
    if (exceptions.length > 0) throw new Error(`uncaught exception at cycle ${cycle}: ${exceptions[exceptions.length - 1]}`);
  }

  // Phase c) reload sub-phase ×3: fresh page context (the wasm singleton —
  // and its memory cache — die with the page), re-sign-in, Diagnostics, and
  // the fromDiskCache answer for the artwork responses.
  for (let reload = 1; reload <= 3; reload++) {
    await cdp.send('Page.navigate', { url: `http://127.0.0.1:${SERVE_PORT}/` });
    await connectAndSignIn(cdp);
    const windowStart = Date.now();
    const { msToOk } = await openDiagnosticsAndWaitOk(cdp);
    await sleep(500);
    const responses = imageResponses
      .filter((r) => r.t >= windowStart)
      .map((r) => ({ url: r.url, status: r.status, fromDiskCache: r.fromDiskCache }));
    reloads.push({ reload, msToOk, imageResponses: responses });
    log(`reload ${reload}: ok=${msToOk}ms imageResponses=${JSON.stringify(responses)}`);
  }

  // Phase d) criteria evaluation (the PASS-CRITERIA contract of this driver).
  const warm = cycles.filter((c) => c.cycle >= 2);
  const cycle2 = cycles.find((c) => c.cycle === 2);
  const final = cycles[cycles.length - 1];
  const gcSamples = warm.filter((c) => c.gc);
  const gcBaseline = gcSamples[0]; // first GC'd warm sample (cycle 5)
  const gcFinal = gcSamples[gcSamples.length - 1];
  const last40 = warm.slice(-40);
  const last40Gc = last40.filter((c) => c.gc).map((c) => c.metrics.JSHeapUsedSize);

  const okTimes = cycles.map((c) => c.msToOk);
  const sorted = [...okTimes].sort((a, b) => a - b);
  const p50 = sorted[Math.floor(sorted.length / 2)];

  // DOM stability MUST be judged on GC'd samples: raw Nodes/Listeners grow a
  // constant amount per pane entry (first run measured +243 nodes/+43
  // listeners per entry) and collapse on the next GC — the detached pane
  // trees are garbage, not leaks. Post-GC steady state is the no-leak signal
  // (VideoCheck's video-host div create/remove included).
  const nodeBaseGc = gcSamples[0]?.metrics.Nodes ?? null;
  const listenerBaseGc = gcSamples[0]?.metrics.JSEventListeners ?? null;
  const nodeFinalGc = gcFinal?.metrics.Nodes ?? null;
  const listenerFinalGc = gcFinal?.metrics.JSEventListeners ?? null;
  const nodeDrift = nodeBaseGc !== null && nodeFinalGc !== null ? nodeFinalGc - nodeBaseGc : null;
  const listenerDrift = listenerBaseGc !== null && listenerFinalGc !== null ? listenerFinalGc - listenerBaseGc : null;
  const driftTol = (baseValue) => (baseValue === null ? 0 : Math.max(10, Math.round(baseValue * 0.05)));

  const heapRatio = gcBaseline && gcFinal ? gcFinal.metrics.JSHeapUsedSize / gcBaseline.metrics.JSHeapUsedSize : null;
  const heapSlopePerCycle = last40Gc.length >= 4 ? slope(last40Gc) : null;
  const slopeAllowed = gcBaseline ? gcBaseline.metrics.JSHeapUsedSize * 0.001 : null; // 0.1%/cycle

  const laterFetchCycles = warm.filter((c) => c.imageFetchCount > 0 || c.coilNetDelta > 0);

  const criteria = {
    cycleReachOkUnder60s: {
      pass: okTimes.every((ms) => ms <= 60000),
      maxMsToOk: Math.max(...okTimes),
      p50MsToOk: p50,
      perCycle: okTimes,
    },
    zeroRefetchAfterCold: {
      pass: laterFetchCycles.length === 0,
      cycle1FetchCount: cycle0.imageFetchCount,
      cycle1CoilNet: settledStats.net ?? null,
      laterFetchCycles: laterFetchCycles.map((c) => ({ cycle: c.cycle, count: c.imageFetchCount, coilNetDelta: c.coilNetDelta })),
      note: 'cycle-1 count documents the measured coalescing of the two concurrent loads (raw painter + MediaImage); gate covers BOTH the Network.responseReceived delta and the app-side Coil net counter delta',
    },
    heapStable: {
      pass: heapRatio !== null && heapRatio <= 1.3
        && (heapSlopePerCycle === null || slopeAllowed === null ? true : Math.abs(heapSlopePerCycle) <= slopeAllowed)
        && gcSamples.length >= 2,
      gcSampleCount: gcSamples.length,
      gcBaselineCycle: gcBaseline?.cycle ?? null,
      gcBaselineBytes: gcBaseline?.metrics.JSHeapUsedSize ?? null,
      gcFinalCycle: gcFinal?.cycle ?? null,
      gcFinalBytes: gcFinal?.metrics.JSHeapUsedSize ?? null,
      gcRatioFinalVsBaseline: heapRatio,
      last40GcSlopeBytesPerCycle: heapSlopePerCycle,
      last40GcSlopeAllowedBytesPerCycle: slopeAllowed,
      rawCycle2Bytes: cycle2?.metrics.JSHeapUsedSize ?? null,
      rawFinalBytes: final.metrics.JSHeapUsedSize,
      note: 'ratio gate on GC\'d samples (first warm GC vs final GC); slope over GC\'d samples of the last 40 cycles must stay within 0.1% of baseline per cycle; needs >=2 GC samples (first GC lands at cycle 5)',
    },
    domStable: {
      pass: nodeDrift !== null && listenerDrift !== null
        && Math.abs(nodeDrift) <= driftTol(nodeBaseGc) && Math.abs(listenerDrift) <= driftTol(listenerBaseGc),
      nodesFirstWarmGc: nodeBaseGc,
      nodesFinalGc: nodeFinalGc,
      nodeDriftGc: nodeDrift,
      listenersFirstWarmGc: listenerBaseGc,
      listenersFinalGc: listenerFinalGc,
      listenerDriftGc: listenerDrift,
      toleranceRule: 'max(10, 5% of first GC\'d warm value)',
      rawSeriesNote: 'raw per-cycle metrics are in cycles[]; raw values grow between GCs and collapse at each GC by design',
      note: 'covers VideoCheck video-host div create/remove per pane entry',
    },
    zeroConsoleErrors: {
      pass: consoleErrors.length === 0 && exceptions.length === 0,
      consoleErrors,
      exceptions,
    },
    reloadFromDiskCacheRecorded: {
      pass: reloads.every((r) => r.imageResponses !== null),
      reloads,
      note: 'informational: either fromDiskCache answer documents the no-disk-cache + Jellyfin-header reality',
    },
  };

  verdict = Object.values(criteria).every((c) => c.pass) ? 'PASS' : 'FAIL';
  if (verdict === 'FAIL') failure = Object.entries(criteria).filter(([, c]) => !c.pass).map(([k]) => k).join(', ');

  const result = {
    verdict,
    failure,
    startedAt: new Date(tStart).toISOString(),
    totalMs: Date.now() - tStart,
    config: { cyclesRequested: CYCLES, durationMs: DURATION_MS, sampleMs: SAMPLE_MS, cyclesRun: cycles.length },
    serverUrl: SERVER_URL,
    jellyfinVersion: await (async () => {
      try { return (await (await fetch(`${SERVER_URL}/System/Info/Public`)).json()).Version; } catch { return null; }
    })(),
    criteria,
    cycles,
    consoleErrorEntries: consoleErrors,
    consoleLogEntries: consoleLogs,
    exceptionEntries: exceptions,
    logErrorEntries: logErrors,
    outDir: OUT_DIR,
  };
  const jsonPath = join(OUT_DIR, 'soak-result.json');
  writeFileSync(jsonPath, JSON.stringify(result, null, 2));
  log(`verdict ${verdict}${failure ? ` (${failure})` : ''} — ${jsonPath}`);
  if (verdict !== 'PASS') process.exitCode = 1;
}

try {
  await main();
} catch (e) {
  failure = e.message;
  verdict = 'FAIL';
  log(`FATAL: ${e.message}`);
  process.exitCode = 1;
  writeFileSync(join(OUT_DIR, 'soak-result.json'), JSON.stringify({
    verdict, failure, cycles, reloads, consoleErrors, exceptions, logErrors, outDir: OUT_DIR,
  }, null, 2));
} finally {
  if (cdp) {
    try { await cdp.send('Browser.close'); } catch { /* ws may already be gone */ }
    cdp.close();
  }
  if (edgeProc && !KEEP) spawnSync('taskkill', ['/PID', String(edgeProc.pid), '/T', '/F']);
  if (serverProc && !KEEP) spawnSync('taskkill', ['/PID', String(serverProc.pid), '/T', '/F']);
}
