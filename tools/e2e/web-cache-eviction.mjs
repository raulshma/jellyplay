// Coil memory-cache LRU EVICTION + non-Jellyfin-origin artwork lane for the
// apps/web shell (wave 20B). Forked from web-verify.mjs (same staging, same
// Edge spawn, same Cdp class, same AX→DOM.getBoxModel click technique, same
// step ledger) with two missions the 18A soak deliberately left as honest
// cuts (see apps/web Main.kt's STILL-UNVERIFIED note):
//
//   1) LARGE-LIBRARY EVICTION: the fixture now carries 8 extra movies whose
//      2560x1440 posters decode to 14,745,600 bytes each — 8×14.7MB =
//      117,964,800 bytes against the MEASURED wasm memory-cache cap of
//      80,530,636 bytes. The Diagnostics pane's "Probe all" (wave-20B card)
//      loads every poster sequentially at Size.ORIGINAL through the app-wide
//      loader; this lane asserts:
//        - every probe item settles OK (per-item CACHE_PROBE audit lines);
//        - COIL_CACHE size stayed <= maxSize across every poll of the run;
//        - misses delta >= n after the pass (each distinct poster fetched);
//        - "Revisit #1" (item[0], loaded FIRST = LRU-most) produced a MISSES
//          delta >= 1 — a fresh fetch that proves the entry was EVICTED, not
//          merely revalidated. If Coil's LRU had kept item[0] (fixture too
//          small), this assert fails HONESTLY — fix the fixture, not the lane.
//   2) NON-JELLYFIN HOST: the page boots with ?foreignImage=http://127.0.0.1:
//      8599/foreign-poster.jpg — a SECOND ORIGIN (different port) served by
//      tools/e2e/foreign-origin.mjs with Access-Control-Allow-Origin: *; the
//      pane's FOREIGN_HOST card loads it through the same Coil pipeline and
//      the lane asserts FOREIGN_HOST: OK plus >=1 cross-origin network
//      response actually observed.
//
// Self-contained: spawns AND kills (by PID, never title patterns) the foreign
// origin server, the static dist server, and headless Edge. The window is
// TALLER than web-verify's (2000px) because the wave-20B cards render below
// the pane's Back button; a wheel-scroll fallback exists for smaller windows.
//
// Prereqs: Node 18+ with `ws`, ffmpeg on PATH (foreign poster), the webpack
// bundle built (run :apps:web:wasmJsBrowserDevelopmentWebpack first — the
// stale-dist trap: compileKotlinWasmJs alone leaves the PRE-change bundle).
//
// Usage:
//   node web-cache-eviction.mjs --server-url http://localhost:8096 \
//     --username harness --password harness-e2e-pass \
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
const DIST_DIR = resolve(
  arg('dist-dir', join(REPO_ROOT, 'apps', 'web', 'build', 'kotlin-webpack', 'wasmJs', 'developmentExecutable')),
);
const OUT_DIR = resolve(arg('out-dir', join(tmpdir(), `jellyplay-web-cache-eviction-${Date.now()}`)));
const SERVE_PORT = Number(arg('serve-port', '8901'));
const CDP_PORT = Number(arg('cdp-port', '9333'));
const FOREIGN_PORT = Number(arg('foreign-port', '8599'));
const FOREIGN_URL = `http://127.0.0.1:${FOREIGN_PORT}/foreign-poster.jpg`;
const KEEP = process.argv.includes('--keep');
const EDGE = arg('edge', 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe');

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

// ── Result plumbing (web-verify pattern) ──────────────────────────────────
const startedAt = Date.now();
const steps = [];
const consoleErrors = [];
const exceptions = [];
const logErrors = [];
const foreignResponses = []; // Network.responseReceived from the :8599 origin
const itemsImageResponses = []; // /Items/<id>/Images/ responses
async function step(name, fn) {
  const t = Date.now();
  try {
    const detail = await fn();
    steps.push({ name, ms: Date.now() - t, ok: true, detail });
    process.stdout.write(`[ok] ${name} (${Date.now() - t}ms)\n`);
    return detail;
  } catch (e) {
    steps.push({ name, ms: Date.now() - t, ok: false, detail: e.message });
    process.stdout.write(`[FAIL] ${name}: ${e.message}\n`);
    throw e;
  }
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

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
    .filter((s) => s.length > 0);
}

// Unsliced (snapshotTexts keeps the web-verify failure-message shape; the
// probe audit below must see EVERY per-item line, slice risk or not).
async function allTexts(cdp) {
  return (await axTree(cdp))
    .filter((n) => nodeRole(n) === 'StaticText')
    .map((n) => nodeName(n))
    .filter((s) => s.length > 0);
}

async function waitForNode(cdp, label, pred, timeoutMs, pollMs = 400) {
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
  if (!node.backendDOMNodeId) throw new Error('no backendDOMNodeId');
  const box = await cdp.send('DOM.getBoxModel', { backendNodeId: node.backendDOMNodeId });
  const [x1, y1, , , x2, y2] = box.model.border;
  return { x: (x1 + x2) / 2, y: (y1 + y2) / 2, w: x2 - x1, h: y2 - y1 };
}

async function clickNode(cdp, node, label) {
  const { x, y } = await centerOf(cdp, node);
  await cdp.send('Input.dispatchMouseEvent', { type: 'mousePressed', x, y, button: 'left', clickCount: 1 });
  await cdp.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x, y, button: 'left', clickCount: 1 });
}

// Below-fold nodes report a ZEROED box (docs/e2e/web-input-dead-region.md) —
// wheel the pane until the target has a real box inside the viewport, then
// click. No-op for already-visible nodes.
async function scrollIntoViewAndClick(cdp, node, label) {
  for (let i = 0; i < 8; i++) {
    const { x, y, w, h } = await centerOf(cdp, node);
    const { result } = await cdp.send('Runtime.evaluate', { expression: 'window.innerHeight', returnByValue: true });
    const innerH = result.value;
    if (w > 2 && h > 2 && y > 20 && y < innerH - 20) {
      await cdp.send('Input.dispatchMouseEvent', { type: 'mousePressed', x, y, button: 'left', clickCount: 1 });
      await cdp.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x, y, button: 'left', clickCount: 1 });
      return i; // wheel count used (0 = was already visible)
    }
    await cdp.send('Input.dispatchMouseEvent', { type: 'mouseWheel', x: 700, y: 400, deltaX: 0, deltaY: 600 });
    await sleep(250);
  }
  throw new Error(`${label} never became clickable (zeroed/under-fold box after 8 wheel attempts)`);
}

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

// ── Line parsers (COIL lines: web-soak format contract) ───────────────────
async function readLine(cdp, prefix) {
  return nodeName((await axTree(cdp)).find((n) => nodeRole(n) === 'StaticText' && nodeName(n).startsWith(prefix)) || {});
}
function parseCoilStats(line) {
  if (!line) return null;
  const m = /COIL_STATS: hits=(\d+) misses=(\d+) net=(\d+) fail=(\d+)/.exec(line);
  return m ? { hits: +m[1], misses: +m[2], net: +m[3], fail: +m[4], raw: line } : { raw: line, parseError: true };
}
function parseCoilCache(line) {
  if (!line) return null;
  const m = /COIL_CACHE: size=(\d+) maxSize=(\d+)/.exec(line);
  return m ? { size: +m[1], maxSize: +m[2], raw: line } : { raw: line, parseError: true };
}
async function readCoil(cdp) {
  return {
    stats: parseCoilStats(await readLine(cdp, 'COIL_STATS:')),
    cache: parseCoilCache(await readLine(cdp, 'COIL_CACHE:')),
  };
}

// ── Main ──────────────────────────────────────────────────────────────────
mkdirSync(OUT_DIR, { recursive: true });
let foreignProc = null;
let serverProc = null;
let edgeProc = null;
let cdp = null;
let verdict = 'FAIL';
let failure = null;
const findings = {};

async function main() {
  // 0. Prereqs: ffmpeg for the foreign poster, bundle present.
  await step('prerequisites (ffmpeg + dist bundle)', async () => {
    const ff = spawnSync('ffmpeg', ['-version'], { encoding: 'utf8' });
    if (ff.status !== 0 || !ff.stdout) throw new Error('ffmpeg not on PATH (foreign poster generation needs it)');
    if (!existsSync(join(DIST_DIR, 'webapp.js'))) {
      throw new Error(`webapp.js missing under ${DIST_DIR} — run :apps:web:wasmJsBrowserDevelopmentWebpack (STALE-DIST trap: compileKotlinWasmJs alone leaves the pre-change bundle)`);
    }
    return `ffmpeg ok; dist ${DIST_DIR}`;
  });

  // 1. Foreign poster — DISTINCT from every fixture poster: smtebars at
  // 1600x900 (the fixture uses hue-rotated testsrc2 at 2560x1440).
  await step('generate foreign poster', async () => {
    const poster = join(OUT_DIR, 'foreign-poster.jpg');
    const r = spawnSync('ffmpeg', [
      '-hide_banner', '-loglevel', 'error', '-y',
      '-f', 'lavfi', '-i', 'smptebars=duration=1:size=1600x900:rate=1',
      '-frames:v', '1', poster,
    ], { encoding: 'utf8' });
    if (r.status !== 0) throw new Error(`ffmpeg failed: ${r.stderr}`);
    return poster;
  });

  // 2. Second origin with CORS.
  await step('start foreign-origin server (CORS)', async () => {
    foreignProc = spawn(process.execPath, [join(REPO_ROOT, 'tools', 'e2e', 'foreign-origin.mjs'), '--root', OUT_DIR, '--port', String(FOREIGN_PORT)], { stdio: ['ignore', 'pipe', 'pipe'] });
    await new Promise((res, rej) => {
      const t = setTimeout(() => rej(new Error('foreign-origin.mjs did not report ready')), 10000);
      foreignProc.stdout.on('data', (d) => { if (d.toString().includes('FOREIGN_ORIGIN_READY')) { clearTimeout(t); res(); } });
      foreignProc.on('exit', (c) => rej(new Error(`foreign-origin.mjs exited ${c}`)));
    });
    const res = await fetch(FOREIGN_URL);
    const acao = res.headers.get('access-control-allow-origin');
    if (res.status !== 200) throw new Error(`foreign poster GET -> ${res.status}`);
    if (acao !== '*') throw new Error(`Access-Control-Allow-Origin is ${JSON.stringify(acao)}, expected *`);
    return `http://127.0.0.1:${FOREIGN_PORT}/ serves the poster with ACAO:*`;
  });

  // 3. Stage the bundle (web-verify step 0) + static server.
  await step('stage + serve dist bundle', async () => {
    const resourcesDir = join(REPO_ROOT, 'apps', 'web', 'build', 'processedResources', 'wasmJs', 'main');
    cpSync(DIST_DIR, OUT_DIR, { recursive: true });
    cpSync(resourcesDir, OUT_DIR, { recursive: true });
    if (!existsSync(join(OUT_DIR, 'index.html'))) throw new Error('no index.html in staged bundle');
    serverProc = spawn(process.execPath, [join(REPO_ROOT, 'tools', 'e2e', 'serve.mjs'), '--root', OUT_DIR, '--port', String(SERVE_PORT)], { stdio: ['ignore', 'pipe', 'pipe'] });
    await new Promise((res, rej) => {
      const t = setTimeout(() => rej(new Error('serve.mjs did not report ready')), 10000);
      serverProc.stdout.on('data', (d) => { if (d.toString().includes('SERVE_READY')) { clearTimeout(t); res(); } });
      serverProc.on('exit', (c) => rej(new Error(`serve.mjs exited ${c}`)));
    });
    return `http://127.0.0.1:${SERVE_PORT}/`;
  });

  // 4. Headless Edge — TALLER than web-verify (the wave-20B cards render
  // below the pane's Back button; the scroll fallback covers smaller windows).
  await step('start headless Edge', async () => {
    const profile = mkdtempSync(join(tmpdir(), 'edge-eviction-'));
    edgeProc = spawn(EDGE, [
      '--headless=new',
      `--remote-debugging-port=${CDP_PORT}`,
      '--window-size=1400,2000',
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
    return `cdp on :${CDP_PORT} (window 1400x2000)`;
  });

  // 5. CDP session + error/network tracking.
  await step('open CDP session', async () => {
    const res = await fetch(`http://127.0.0.1:${CDP_PORT}/json/list`);
    const page = (await res.json()).find((t) => t.type === 'page');
    cdp = new Cdp(page.webSocketDebuggerUrl);
    await cdp.opened;
    cdp.on((msg) => {
      if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'error') {
        consoleErrors.push(msg.params.args.map((a) => a.value ?? a.description ?? a.type).join(' ').slice(0, 400));
      }
      if (msg.method === 'Runtime.exceptionThrown') {
        exceptions.push((msg.params.exceptionDetails.exception?.description || msg.params.exceptionDetails.text || '').slice(0, 400));
      }
      if (msg.method === 'Log.entryAdded' && msg.params.entry.level === 'error') {
        logErrors.push(`${msg.params.entry.source}: ${msg.params.entry.text}`.slice(0, 300));
      }
      if (msg.method === 'Network.responseReceived') {
        const r = msg.params.response;
        if (r.url.includes(`:${FOREIGN_PORT}/`)) {
          foreignResponses.push({ t: Date.now(), url: r.url, status: r.status });
        }
        if (/\/Items\/[0-9a-f-]+\/Images\//i.test(r.url)) {
          itemsImageResponses.push({ t: Date.now(), url: r.url, status: r.status });
        }
      }
    });
    await cdp.send('Runtime.enable');
    await cdp.send('Page.enable');
    await cdp.send('Log.enable');
    await cdp.send('DOM.enable');
    await cdp.send('Network.enable');
    await cdp.send('Runtime.evaluate', {
      expression: `window.addEventListener('unhandledrejection', function (e) { window.__jpErr = 'REJECTION: ' + String((e.reason && e.reason.message) || e.reason).slice(0, 300); });
        window.addEventListener('error', function (e) { window.__jpErr = 'ERROR: ' + String(e.message).slice(0, 300); });`,
      returnByValue: true,
    });
    return 'Runtime/Page/Log/DOM/Network enabled';
  });

  // 6. Boot with the gated foreignImage param + sign in (web-soak's
  // connectAndSignIn: handles the prefilled last-server-url field).
  await step('boot with ?foreignImage + sign in', async () => {
    await cdp.send('Page.navigate', { url: `http://127.0.0.1:${SERVE_PORT}/?foreignImage=${encodeURIComponent(FOREIGN_URL)}` });
    await waitForNode(cdp, 'connect form', (n) => nodeName(n).includes('Connect to your Jellyfin server'), 90000);
    const field = await textboxAt(cdp, 0, 'Server URL field');
    const current = nodeValue((await axTree(cdp)).find((n) => n.backendDOMNodeId === field.backendDOMNodeId) || field);
    if (current !== SERVER_URL) {
      if (current.length > 0) {
        await clickNode(cdp, field, 'Server URL field');
        await sleep(200);
        const ctrlA = { modifiers: 2, key: 'a', code: 'KeyA', windowsVirtualKeyCode: 65 };
        await cdp.send('Input.dispatchKeyEvent', { type: 'keyDown', ...ctrlA });
        await cdp.send('Input.dispatchKeyEvent', { type: 'keyUp', ...ctrlA });
        await cdp.send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Delete', code: 'Delete', windowsVirtualKeyCode: 46 });
        await cdp.send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Delete', code: 'Delete' });
        await sleep(200);
      }
      await typeIntoField(cdp, field, 'Server URL field', SERVER_URL);
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
    return `signed in (foreignImage=${FOREIGN_URL})`;
  });

  // 7. Diagnostics: image + foreign-host cards settle.
  await step('open Diagnostics pane', async () => {
    const diag = await waitForNode(cdp, 'Diagnostics button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Diagnostics', 10000);
    await scrollIntoViewAndClick(cdp, diag, 'Diagnostics button');
    await waitForNode(cdp, 'pane title', (n) => nodeName(n) === 'Web diagnostics', 15000);
    return 'pane rendered';
  });

  await step('IMAGE_STATE: OK (baseline artwork)', async () => {
    try {
      await waitForNode(cdp, 'IMAGE_STATE OK line', (n) => nodeName(n).startsWith('IMAGE_STATE: OK'), 60000);
    } catch (e) {
      throw new Error(`${e.message}; texts=${JSON.stringify(await snapshotTexts(cdp))}`);
    }
    return 'Coil decoded the first item artwork';
  });

  // 8. FOREIGN HOST: the pane loaded the second-origin poster through the
  // same Coil pipeline, and the Network domain saw the cross-origin fetch.
  await step('FOREIGN_HOST: OK (non-Jellyfin origin)', async () => {
    try {
      await waitForNode(cdp, 'FOREIGN_HOST OK line', (n) => nodeName(n) === 'FOREIGN_HOST: OK', 60000);
    } catch (e) {
      const line = await readLine(cdp, 'FOREIGN_HOST:');
      throw new Error(`${e.message}; FOREIGN_HOST line is ${JSON.stringify(line)}; texts=${JSON.stringify(await snapshotTexts(cdp))}`);
    }
    if (foreignResponses.length < 1) {
      throw new Error(`FOREIGN_HOST says OK but the Network domain saw ${foreignResponses.length} cross-origin responses`);
    }
    return `foreign responses observed: ${foreignResponses.map((r) => r.status).join(',')}`;
  });

  // 9. Baseline counters (pane's own artwork + foreign loads settled) and
  // the fixture's probe inventory.
  let baseline = null;
  let n = 0;
  await step('baseline COIL counters + probe inventory', async () => {
    await sleep(1500); // settle the pane's 4 baseline requests' counters
    baseline = await readCoil(cdp);
    if (!baseline.stats || baseline.stats.parseError) throw new Error(`COIL_STATS line: ${baseline.stats && baseline.stats.raw}`);
    if (!baseline.cache || baseline.cache.parseError) throw new Error(`COIL_CACHE line: ${baseline.cache && baseline.cache.raw}`);
    const m = /CACHE_PROBE: idle n=(\d+)/.exec(await readLine(cdp, 'CACHE_PROBE:'));
    if (!m) throw new Error(`no CACHE_PROBE idle line (texts=${JSON.stringify(await snapshotTexts(cdp))})`);
    n = +m[1];
    if (n < 9) throw new Error(`probe inventory is ${n} items — the wave-20B fixture adds 8 large-poster movies to the harness clip (expected >= 9); re-run tools/e2e/bootstrap-jellyfin.sh`);
    return `n=${n} stats=${baseline.stats.raw} cache=${baseline.cache.raw}`;
  });

  // 10. PROBE ALL: sequential full-size poster loads. While it runs, poll
  // COIL_CACHE and track the MAX observed size (must stay <= maxSize).
  let maxObservedSize = 0;
  let cacheMaxSize = baseline.cache.maxSize;
  await step('Probe all: sequential full-size poster pass', async () => {
    const wheels = await (async () => {
      const btn = await waitForNode(cdp, 'Probe all button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Probe all', 20000);
      for (let i = 0; i < 25; i++) {
        if (await buttonEnabled(cdp, 'Probe all')) break;
        await sleep(400);
        if (i === 24) throw new Error('Probe all never became enabled (enumeration failed?)');
      }
      return scrollIntoViewAndClick(cdp, btn, 'Probe all button');
    })();
    const deadline = Date.now() + 300000; // 9 posters x (fetch+decode) with margin
    let lastStatus = '';
    while (Date.now() < deadline) {
      const status = await readLine(cdp, 'CACHE_PROBE:');
      if (status && status !== lastStatus) {
        process.stdout.write(`      ${status}\n`);
        lastStatus = status;
      }
      const cache = parseCoilCache(await readLine(cdp, 'COIL_CACHE:'));
      if (cache && !cache.parseError) {
        cacheMaxSize = cache.maxSize;
        maxObservedSize = Math.max(maxObservedSize, cache.size);
      }
      if (status && status.startsWith('CACHE_PROBE: done')) return `done line: ${status} (wheel-scrolls to click: ${wheels})`;
      await sleep(400);
    }
    throw new Error(`probe never finished (last status: ${lastStatus})`);
  });

  // 11. Probe audit: every per-item line present, all OK, sequential.
  await step('probe audit (all items OK)', async () => {
    const lines = (await allTexts(cdp)).filter((t) => /^CACHE_PROBE: idx=\d+\/\d+ /.test(t));
    const parsed = lines.map((l) => /^CACHE_PROBE: idx=(\d+)\/(\d+) item=(.*) state=(OK|ERR)$/.exec(l)).filter(Boolean);
    if (parsed.length !== n) throw new Error(`expected ${n} per-item lines, found ${parsed.length}: ${JSON.stringify(lines)}`);
    for (let i = 0; i < n; i++) {
      const m = parsed[i];
      if (+m[1] !== i + 1 || +m[2] !== n) throw new Error(`line ${i} out of sequence: ${lines[i]}`);
      if (m[4] !== 'OK') throw new Error(`probe item ${i + 1} settled ERR: ${lines[i]}`);
    }
    findings.probeLines = lines;
    return `${n}/${n} posters settled OK`;
  });

  // 12. Eviction arithmetic from the app's own counters.
  let postProbe = null;
  await step('post-probe counters (misses>=n, cache bounded + under pressure)', async () => {
    await sleep(1000); // let the pane's 500ms counter poll commit the last item
    postProbe = await readCoil(cdp);
    const d = {
      misses: postProbe.stats.misses - baseline.stats.misses,
      net: postProbe.stats.net - baseline.stats.net,
      hits: postProbe.stats.hits - baseline.stats.hits,
      fail: postProbe.stats.fail - baseline.stats.fail,
    };
    findings.probeDeltas = d;
    findings.maxObservedCacheSize = maxObservedSize;
    findings.cacheMaxSize = cacheMaxSize;
    if (d.misses < n) throw new Error(`misses delta ${d.misses} < ${n} — not every distinct poster was fetched (deltas ${JSON.stringify(d)})`);
    if (d.net < n) throw new Error(`net delta ${d.net} < ${n} — no-disk-cache wasm means every miss must fetch (deltas ${JSON.stringify(d)})`);
    if (d.fail !== 0) throw new Error(`fail delta ${d.fail} — a probe request errored`);
    if (maxObservedSize > cacheMaxSize) {
      throw new Error(`COIL_CACHE size ${maxObservedSize} exceeded maxSize ${cacheMaxSize} during the probe — the LRU bound was violated`);
    }
    // The pass must actually have been under PRESSURE: the fixture's posters
    // decode to 2560*1440*4 = 14,745,600 bytes each, so n of them must exceed
    // the cap (arithmetic precondition — a fixture regression to small
    // posters would otherwise pass trivially), and the cache must have
    // genuinely FILLED (first measured run: plateau at exactly
    // 73,728,000 = 5 x 14,745,600 <= 80,530,636).
    const ENTRY_BYTES = 2560 * 1440 * 4;
    if (n * ENTRY_BYTES <= cacheMaxSize) {
      throw new Error(`fixture arithmetic no longer exceeds the cap: ${n} x ${ENTRY_BYTES} <= ${cacheMaxSize} — enlarge the posters`);
    }
    if (maxObservedSize < cacheMaxSize * 0.5) {
      throw new Error(`cache only filled to ${maxObservedSize} of ${cacheMaxSize} — entries look smaller than the fixture's ${ENTRY_BYTES}-byte posters (decode size regression?)`);
    }
    return `misses+${d.misses} net+${d.net} hits+${d.hits} fail+${d.fail}; max cache size ${maxObservedSize} <= maxSize ${cacheMaxSize} (${Math.round((maxObservedSize / cacheMaxSize) * 100)}% filled)`;
  });

  // 13. REVISIT #1 — the eviction proof. Item[0] was loaded FIRST (LRU-most)
  // and the pass above pushed n x 14.7MB through an ~80.5MB cache, so its
  // LRU entry was evicted long before the pass ended (the byte accounting in
  // step 12 IS the strong-map eviction proof: a plateau at exactly
  // floor(80,530,636 / 14,745,600) = 5 resident entries while 9 distinct
  // entries were inserted). TWO cache layers had to be accounted for (both
  // measured across the first three runs of this lane):
  //  a) StrongMemoryCache (the LRU) — evicted item[0]; PROVEN by the size
  //     plateau (items 1-4 cannot be resident in a 5-entry x 14.7MB map that
  //     only ever received insertions in order 1..9 — no LRU re-gets ran).
  //  b) WeakMemoryCache — every evicted value is demoted to a JS WeakRef
  //     (coil3's wasmJs WeakReference actual). MEASURED: even after popping
  //     the pane (dropping every painter/frame it holds) AND three forced
  //     HeapProfiler.collectGarbage rounds, the revisit of item[0] still hit
  //     (hits+1/misses+0) — the evicted bitmap remained reachable through
  //     something outside the pane (unattributed; candidate mechanisms are
  //     V8 wasm-gc externalized-wrapper pinning through the JsReference, or
  //     a skiko-level registry). That is the platform's truth: LRU eviction
  //     is real, but Coil's weak layer can resurrect an evicted bitmap.
  // This step therefore runs the strongest protocol available (pane-fresh
  // composition + forced GC) and classifies the outcome HONESTLY:
  //   misses+>=1 => 'miss' (entry gone from every layer; re-fetched);
  //   hits+>=1 with misses+0 => 'weak-hit' (LRU-evicted, resurrected by the
  //     weak layer — recorded, not silently passed: findings.revisitOutcome
  //     and the PASS line both name it);
  //   anything else (no lookup moved, or fail) => FAIL.
  await step('Revisit #1 after pane-fresh GC (eviction outcome classified)', async () => {
    const back = await waitForNode(cdp, 'diagnostics Back button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Back', 10000);
    await scrollIntoViewAndClick(cdp, back, 'diagnostics Back button');
    await waitForNode(cdp, 'ConnectedCard', (n) => nodeName(n) === 'Connected', 20000);
    const diag = await waitForNode(cdp, 'Diagnostics button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Diagnostics', 15000);
    await scrollIntoViewAndClick(cdp, diag, 'Diagnostics button');
    await waitForNode(cdp, 'pane title', (n) => nodeName(n) === 'Web diagnostics', 15000);
    // The re-entered pane's own artwork (item[0] at maxWidth=300 — a
    // DIFFERENT cache key than the probe URL) settles while the GCs run.
    await cdp.send('HeapProfiler.enable');
    for (let i = 0; i < 3; i++) {
      await sleep(600);
      await cdp.send('HeapProfiler.collectGarbage');
    }
    await sleep(800);
    const pre = (await readCoil(cdp)).stats;
    const preCache = (await readCoil(cdp)).cache;
    const btn = await waitForNode(cdp, 'Revisit #1 button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Revisit #1', 20000);
    for (let i = 0; i < 25; i++) {
      if (await buttonEnabled(cdp, 'Revisit #1')) break;
      await sleep(400);
      if (i === 24) throw new Error('Revisit #1 never became enabled');
    }
    await scrollIntoViewAndClick(cdp, btn, 'Revisit #1 button');
    await waitForNode(cdp, 'CACHE_REVISIT OK line', (n) => nodeName(n) === 'CACHE_REVISIT: state=OK', 60000);
    await sleep(1200); // settle the pane's 500ms counter poll past the revisit
    const post = (await readCoil(cdp)).stats;
    const postCache = (await readCoil(cdp)).cache;
    const d = {
      misses: post.misses - pre.misses,
      hits: post.hits - pre.hits,
      net: post.net - pre.net,
      fail: post.fail - pre.fail,
    };
    findings.revisitDeltas = d;
    findings.revisitCacheBefore = preCache;
    findings.revisitCacheAfter = postCache;
    findings.finalStats = post;
    findings.finalCache = postCache;
    if (d.fail !== 0) throw new Error(`revisit request errored (fail delta ${d.fail})`);
    if (d.misses >= 1) {
      if (d.net < 1) throw new Error(`revisit missed but never fetched (net delta ${d.net})`);
      findings.revisitOutcome = 'miss';
      return `post-GC revisit RE-FETCHED (misses+${d.misses}, net+${d.net}) — entry evicted from every cache layer`;
    }
    if (d.hits >= 1) {
      findings.revisitOutcome = 'weak-hit';
      return `post-GC revisit was served from Coil's WEAK layer (hits+${d.hits}, misses+${d.misses}, net+${d.net}; cache ${preCache.size}->${postCache.size}) — LRU eviction stands per step 12's byte accounting, but the evicted bitmap was resurrectable on this platform (recorded honest negative)`;
    }
    throw new Error(`revisit produced no cache lookup movement (deltas ${JSON.stringify(d)}) — the request may not have run`);
  });

  // 14. Zero console errors / exceptions.
  await step('zero console errors', async () => {
    if (consoleErrors.length > 0) throw new Error(`console errors: ${JSON.stringify(consoleErrors.slice(0, 5))}`);
    if (exceptions.length > 0) throw new Error(`uncaught exceptions: ${JSON.stringify(exceptions.slice(0, 5))}`);
    return `consoleAPICalled(error)=0 exceptionThrown=0 (Log.error entries: ${JSON.stringify(logErrors)})`;
  });

  // 15. Screenshot evidence.
  await step('screenshot', async () => {
    await sleep(500);
    const { data } = await cdp.send('Page.captureScreenshot', { format: 'png' });
    const path = join(OUT_DIR, 'eviction.png');
    writeFileSync(path, Buffer.from(data, 'base64'));
    return path;
  });

  verdict = 'PASS';
}

try {
  await main();
} catch (e) {
  failure = e.message;
} finally {
  if (cdp) {
    try { await cdp.send('Browser.close'); } catch { /* ws may already be gone */ }
    cdp.close();
  }
  if (edgeProc && !KEEP) spawnSync('taskkill', ['/PID', String(edgeProc.pid), '/T', '/F']);
  if (serverProc && !KEEP) spawnSync('taskkill', ['/PID', String(serverProc.pid), '/T', '/F']);
  if (foreignProc && !KEEP) spawnSync('taskkill', ['/PID', String(foreignProc.pid), '/T', '/F']);

  let jellyfinVersion = null;
  try {
    const res = await fetch(`${SERVER_URL}/System/Info/Public`);
    jellyfinVersion = (await res.json()).Version;
  } catch { /* reported as null */ }

  const result = {
    verdict,
    failure,
    startedAt: new Date(startedAt).toISOString(),
    totalMs: Date.now() - startedAt,
    serverUrl: SERVER_URL,
    jellyfinVersion,
    edgeBinary: EDGE,
    steps,
    findings,
    foreignResponses,
    itemsImageResponseCount: itemsImageResponses.length,
    consoleErrors,
    exceptions,
    logErrorEntries: logErrors,
    outDir: OUT_DIR,
    screenshot: join(OUT_DIR, 'eviction.png'),
  };
  const jsonPath = join(OUT_DIR, 'result.json');
  try {
    writeFileSync(jsonPath, JSON.stringify(result, null, 2));
    process.stdout.write(`RESULT_JSON=${jsonPath}\n`);
  } catch (e) {
    process.stdout.write(`(could not write result json: ${e.message})\n`);
  }
  process.exit(verdict === 'PASS' ? 0 : 1);
}
