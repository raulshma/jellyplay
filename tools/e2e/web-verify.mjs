// Headless-Edge CDP verification driver for the apps/web shell (wave 13C).
// Drives the CANVAS app through the accessibility tree (wave-12 technique):
// Accessibility.getFullAXTree → find node by role/name → backendDOMNode →
// DOM.getBoxModel → center → Input.dispatchMouseEvent. Text entry into
// Compose fields: Input.insertText after focusing the field, with a per-char
// Input.dispatchKeyEvent fallback if Compose ignores the IME path (verified
// empirically per run — recorded in the result JSON either way).
//
// Flow: connect/sign-in against a REAL Jellyfin server → ConnectedCard →
// Diagnostics pane → assert IMAGE_STATE: OK + ENGINE_STATE pos>0 +
// DIAG_OVERALL: OK → wave 15C: pop back, open the FIRST feature screen
// (Route.Requests → shared RequestsScreen), assert title + filter bar +
// the honest "Seerr not configured" error state (Seerr is NOT part of the
// fixture — see apps/web Main.kt's SEERR-ON-WEB HONESTY note) → wave 16A:
// the SECOND feature screen (Route.UpcomingCalendar → shared
// UpcomingCalendarScreen), asserting the honest feature-disabled pane
// (DIRECT_ARR_INTEGRATION boots off) → wave 16B: the Seerr credentials pane
// (fill/save/honest test-fail/localStorage persistence proof) → wave 16C:
// boot into Route.SeerrDetail via the gated e2eRoute param, asserting the
// honest error state → wave 18A: the Diagnostics REVISIT gate (pop the demo,
// re-enter Diagnostics cold, re-enter AGAIN, assert the COIL_STATS line:
// hits>0 / net>0 / fail=0 — the memory cache must serve the second entry) →
// zero console errors / uncaught exceptions, screenshots.
//
// Usage:
//   node web-verify.mjs --server-url http://localhost:8096 \
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
const OUT_DIR = resolve(arg('out-dir', join(tmpdir(), `jellyplay-web-verify-${Date.now()}`)));
const SERVE_PORT = Number(arg('serve-port', '8901'));
const CDP_PORT = Number(arg('cdp-port', '9333'));
const KEEP = process.argv.includes('--keep');
const EDGE = arg('edge', 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe');

// ── CDP client ────────────────────────────────────────────────────────────
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

// ── Result plumbing ───────────────────────────────────────────────────────
const startedAt = Date.now();
const steps = [];
const consoleErrors = [];
const exceptions = [];
const logErrors = [];
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

// ── AX helpers ────────────────────────────────────────────────────────────
function nodeName(n) { return (n.name && n.name.value) || ''; }
function nodeRole(n) { return (n.role && n.role.value) || ''; }
function nodeValue(n) { return (n.value && n.value.value) || ''; }

async function axTree(cdp) {
  const { nodes } = await cdp.send('Accessibility.getFullAXTree');
  // Ignored nodes (presentational/stale) only add ghost textboxes — the
  // driver matches on live nodes exclusively.
  return nodes.filter((n) => !n.ignored);
}

// Diagnostic snapshot for failure messages: every visible text line.
async function snapshotTexts(cdp) {
  return (await axTree(cdp))
    .filter((n) => nodeRole(n) === 'StaticText')
    .map((n) => nodeName(n))
    .filter((s) => s.length > 0)
    .slice(0, 40);
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

// Compose text fields expose role "textbox" with NO accessible name (the
// OutlinedTextField label does not land in the AX name) — so fields are
// located POSITIONALLY: they appear in the tree in layout order.
async function textboxAt(cdp, index, label, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const tbs = (await axTree(cdp)).filter((n) => nodeRole(n).toLowerCase() === 'textbox');
    if (tbs.length > index) return tbs[index];
    await sleep(300);
  }
  throw new Error(`timeout waiting for textbox #${index} (${label})`);
}

async function clickNode(cdp, node, label) {
  const { x, y } = await centerOf(cdp, node);
  await cdp.send('Input.dispatchMouseEvent', { type: 'mousePressed', x, y, button: 'left', clickCount: 1 });
  await cdp.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x, y, button: 'left', clickCount: 1 });
}

async function centerOf(cdp, node) {
  if (!node.backendDOMNodeId) throw new Error('no backendDOMNode');
  const box = await cdp.send('DOM.getBoxModel', { backendNodeId: node.backendDOMNodeId });
  const [x1, y1, , , x2, y2] = box.model.border; // border quad: tl, tr, br, bl
  return { x: (x1 + x2) / 2, y: (y1 + y2) / 2 };
}

// PasswordVisualTransformation hides the field's AX value, so password
// typing is verified by "value left empty" rather than text containment;
// the true gate is the Sign in button flipping to enabled (below).
// Select-all + Delete through CDP key events so a field can be cleared
// before retyping (Edge autofill can overwrite a typed field AFTER the
// insertText acceptance check passed — observed on the wave 13C lane where
// the Windows account name replaced a typed username).
async function clearField(cdp, node) {
  await clickNode(cdp, node, 'field');
  await sleep(200);
  const ctrlA = { modifiers: 2, key: 'a', code: 'KeyA', windowsVirtualKeyCode: 65 };
  await cdp.send('Input.dispatchKeyEvent', { type: 'keyDown', ...ctrlA });
  await cdp.send('Input.dispatchKeyEvent', { type: 'keyUp', ...ctrlA });
  await cdp.send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Delete', code: 'Delete', windowsVirtualKeyCode: 46 });
  await cdp.send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Delete', code: 'Delete', windowsVirtualKeyCode: 46 });
  await sleep(200);
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
  throw new Error(`could not type into ${label} (insertText AND key-event fallback both failed)`);
}

async function buttonEnabled(cdp, buttonName) {
  const btn = (await axTree(cdp)).find(
    (n) => nodeRole(n) === 'button' && nodeName(n) === buttonName,
  );
  if (!btn) return null;
  const dis = (btn.properties || []).find((p) => p.name === 'disabled');
  if (!dis) return true;
  // CDP serializes AX property values as {type, value}; a boolean shows up
  // as {type:'boolean', value:false} — unwrap both shapes before comparing.
  const v = dis.value && typeof dis.value === 'object' ? dis.value.value : dis.value;
  return v !== true;
}

// ── Main ──────────────────────────────────────────────────────────────────
mkdirSync(OUT_DIR, { recursive: true });
let serverProc = null;
let edgeProc = null;
let cdp = null;
let verdict = 'FAIL';
let failure = null;

async function main() {
  // 0. Stage the bundle: webpack output + the resource index.html + the
  // processed wasmJs resources (composeResources — MediaImage's placeholder
  // path loads strings.commonMain.cvr at runtime via ./composeResources/...
  // — without these the pane throws MissingResourceException). Source is
  // processedResources/wasmJs/main: the same merge KGP's browser run task
  // serves next to the webpack output.
  await step('stage bundle', async () => {
    if (!existsSync(join(DIST_DIR, 'webapp.js'))) {
      throw new Error(`webapp.js missing under ${DIST_DIR} — run :apps:web:wasmJsBrowserDevelopmentWebpack`);
    }
    // STALENESS TRAP (wave 19 coordinator, 3 lane runs lost): this lane
    // stages build/dist AS-IS — compileKotlinWasmJs alone leaves the old
    // bundle in place, so newly-added AX strings (e.g. COIL_STATS) are
    // silently absent and only their steps fail. After ANY apps/web source
    // change, run the full webpack task before this lane.
    const resourcesDir = join(REPO_ROOT, 'apps', 'web', 'build', 'processedResources', 'wasmJs', 'main');
    cpSync(DIST_DIR, OUT_DIR, { recursive: true });
    cpSync(resourcesDir, OUT_DIR, { recursive: true });
    if (!existsSync(join(OUT_DIR, 'index.html'))) {
      throw new Error(`no index.html under ${resourcesDir} — build first`);
    }
    return `staged in ${OUT_DIR}`;
  });

  // 1. Static server.
  await step('start static server', async () => {
    serverProc = spawn(process.execPath, [join(REPO_ROOT, 'tools', 'e2e', 'serve.mjs'), '--root', OUT_DIR, '--port', String(SERVE_PORT)], { stdio: ['ignore', 'pipe', 'pipe'] });
    await new Promise((res, rej) => {
      const t = setTimeout(() => rej(new Error('serve.mjs did not report ready')), 10000);
      serverProc.stdout.on('data', (d) => { if (d.toString().includes('SERVE_READY')) { clearTimeout(t); res(); } });
      serverProc.on('exit', (c) => rej(new Error(`serve.mjs exited ${c}`)));
    });
    return `http://127.0.0.1:${SERVE_PORT}/`;
  });

  // 2. Headless Edge.
  await step('start headless Edge', async () => {
    const profile = mkdtempSync(join(tmpdir(), 'edge-e2e-'));
    edgeProc = spawn(EDGE, [
      '--headless=new',
      `--remote-debugging-port=${CDP_PORT}`,
      '--window-size=1400,900',
      `--user-data-dir=${profile}`,
      '--no-first-run',
      '--no-default-browser-check',
      // Video must start without a gesture for the autoplay engine check.
      '--autoplay-policy=no-user-gesture-required',
      'about:blank',
    ], { stdio: 'ignore' });
    // An invalid EDGE path would otherwise raise an unhandled 'error' event
    // that crashes before finally and leaks serverProc.
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
    return `cdp on :${CDP_PORT}`;
  });

  // 3. CDP session + error tracking.
  await step('open CDP session', async () => {
    const res = await fetch(`http://127.0.0.1:${CDP_PORT}/json/list`);
    const targets = await res.json();
    const page = targets.find((t) => t.type === 'page');
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
        // Counted, not gated: resource-load failures land here (they are not
        // console.error calls) — e.g. the browser's automatic /favicon.ico
        // request 404s against the bare-bones static server. Content is
        // recorded so a run's Log errors are auditable in result.json.
        logErrors.push(`${msg.params.entry.source}: ${msg.params.entry.text}`.slice(0, 300));
      }
    });
    await cdp.send('Runtime.enable');
    await cdp.send('Page.enable');
    await cdp.send('Log.enable');
    await cdp.send('DOM.enable');
    // Probe hooks: capture window-level unhandled rejections/errors that the
    // CDP exception listener has empirically NOT seen fire for wasm compose
    // failures (the SeerrDetail demo investigation).
    await cdp.send('Runtime.evaluate', {
      expression: `window.addEventListener('unhandledrejection', function (e) { window.__jpErr = 'REJECTION: ' + String((e.reason && e.reason.message) || e.reason).slice(0, 300); });
        window.addEventListener('error', function (e) { window.__jpErr = 'ERROR: ' + String(e.message).slice(0, 300); });`,
      returnByValue: true,
    });
    return 'Runtime/Page/Log/DOM enabled';
  });

  // 4. Navigate + wait for the connect form.
  await step('load app', async () => {
    await cdp.send('Page.navigate', { url: `http://127.0.0.1:${SERVE_PORT}/` });
    await waitForNode(cdp, 'connect form', (n) => nodeName(n).includes('Connect to your Jellyfin server'), 90000);
    return 'SignInCard rendered';
  });

  // 5. Probe the server.
  await step('fill server URL + Connect', async () => {
    const field = await textboxAt(cdp, 0, 'Server URL field');
    const how = await typeIntoField(cdp, field, 'Server URL field', SERVER_URL);
    const connect = await waitForNode(cdp, 'Connect button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Connect', 10000);
    await clickNode(cdp, connect, 'Connect button');
    return `typed via ${how}; Connect clicked`;
  });

  // 6. Sign in. Field identity lesson (empirical, see result JSON): once
  // text lands, Compose adds a GHOST textbox node that shifts positional
  // picks, so both fresh fields are resolved ONCE here (positions are stable
  // pre-typing) and disambiguated by GEOMETRY — Username sits above
  // Password. #0 is the persistent Server URL field.
  await step('sign in', async () => {
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
    const userField = positioned[0].node;
    const passField = positioned[1].node;
    let howUser = await typeIntoField(cdp, userField, 'Username field', USERNAME);
    let howPass = await typeIntoField(cdp, passField, 'Password field', PASSWORD, { requireContains: false });
    // The authoritative gate that BOTH fields really took text: Compose
    // enables Sign in only when username+password are non-blank. THEN
    // re-verify CONTENT: Edge autofill can clobber a typed field AFTER the
    // acceptance check inside typeIntoField (observed: the Windows account
    // name replaced the typed username while Sign in sat enabled). The
    // password field renders bullets, so its check is bullet-count == length.
    const signIn = await waitForNode(cdp, 'Sign in button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Sign in', 10000);
    for (let i = 0; i < 25; i++) {
      if (await buttonEnabled(cdp, 'Sign in')) break;
      await sleep(400);
      if (i === 24) throw new Error('Sign in never became enabled (typing did not land)');
    }
    const fieldsOk = async () => {
      const fresh = (await axTree(cdp)).filter((n) => nodeRole(n).toLowerCase() === 'textbox');
      const uv = fresh.find((n) => n.backendDOMNodeId === userField.backendDOMNodeId);
      const pv = fresh.find((n) => n.backendDOMNodeId === passField.backendDOMNodeId);
      if (!uv || !pv) return { ok: false, why: 'field nodes vanished' };
      const u = nodeValue(uv);
      const p = nodeValue(pv);
      if (u !== USERNAME) return { ok: false, why: `username is "${u}"` };
      if (p.length !== PASSWORD.length) return { ok: false, why: `password length ${p.length} != ${PASSWORD.length}` };
      return { ok: true };
    };
    let autofillFixes = 0;
    for (let i = 0; i < 5; i++) {
      const check = await fieldsOk();
      if (check.ok) break;
      // Clobbered — clear and retype both suspect fields.
      autofillFixes++;
      process.stdout.write(`[warn] autofill clobber (${check.why}) — clearing + retyping\n`);
      await clearField(cdp, userField);
      howUser = await typeIntoField(cdp, userField, 'Username field', USERNAME);
      await clearField(cdp, passField);
      howPass = await typeIntoField(cdp, passField, 'Password field', PASSWORD, { requireContains: false });
      await sleep(400); // give a late autofill pass time to strike again
      if (i === 4) throw new Error(`fields kept getting clobbered: ${check.why}`);
    }
    await clickNode(cdp, signIn, 'Sign in button');
    try {
      await waitForNode(cdp, 'ConnectedCard', (n) => nodeName(n) === 'Connected', 60000);
    } catch (e) {
      throw new Error(`${e.message}; texts=${JSON.stringify(await snapshotTexts(cdp))}`);
    }
    return `user via ${howUser}, pass via ${howPass}${autofillFixes ? `, autofill fixes: ${autofillFixes}` : ''}; Connected rendered`;
  });

  // 7. Open the diagnostics pane.
  await step('open Diagnostics pane', async () => {
    const diag = await waitForNode(cdp, 'Diagnostics button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Diagnostics', 10000);
    await clickNode(cdp, diag, 'Diagnostics button');
    await waitForNode(cdp, 'pane title', (n) => nodeName(n) === 'Web diagnostics', 15000);
    return 'pane rendered';
  });

  // 8. Image check.
  await step('IMAGE_STATE: OK', async () => {
    try {
      await waitForNode(cdp, 'IMAGE_STATE OK line', (n) => nodeName(n).startsWith('IMAGE_STATE: OK'), 60000);
    } catch (e) {
      throw new Error(`${e.message}; texts=${JSON.stringify(await snapshotTexts(cdp))}`);
    }
    return 'Coil decoded the Primary artwork';
  });

  // 9. Engine check: playing with pos > 0. The harness clip is ~12s — if it
  // already ENDED before this poll, click Play (engine replay: seek(0)+play)
  // and keep polling.
  await step('ENGINE_STATE playing pos>0', async () => {
    const line = () => (async () =>
      (await axTree(cdp)).find((n) => nodeName(n).startsWith('ENGINE_STATE:')))();
    await waitForNode(cdp, 'ENGINE_STATE line', (n) => nodeName(n).startsWith('ENGINE_STATE:'), 90000);
    const deadline = Date.now() + 90000;
    let lastLine = '';
    while (Date.now() < deadline) {
      const fresh = await line();
      lastLine = nodeName(fresh) || lastLine;
      const m = /pos=([0-9.]+)s/.exec(lastLine);
      if (m && parseFloat(m[1]) > 0 && /playing=true/.test(lastLine)) return lastLine;
      if (/ENDED/.test(lastLine)) {
        const playBtn = (await axTree(cdp)).find((n) => nodeRole(n) === 'button' && nodeName(n) === 'Play');
        if (playBtn) await clickNode(cdp, playBtn, 'Play button');
      }
      await sleep(500);
    }
    throw new Error(`engine never reported playing=true with pos>0 (last: ${lastLine}); texts=${JSON.stringify(await snapshotTexts(cdp))}`);
  });

  // 10. Overall verdict line.
  await step('DIAG_OVERALL: OK', async () => {
    await waitForNode(cdp, 'DIAG_OVERALL OK', (n) => nodeName(n) === 'DIAG_OVERALL: OK', 60000);
    return 'image OK + engine live';
  });

  // 11. Screenshot evidence.
  await step('screenshot', async () => {
    await sleep(1500); // let the video frame settle
    const { data } = await cdp.send('Page.captureScreenshot', { format: 'png' });
    const path = join(OUT_DIR, 'diagnostics.png');
    writeFileSync(path, Buffer.from(data, 'base64'));
    return path;
  });

  // 12. Wave 15C: pop the diagnostics pane and open the FIRST shared feature
  // screen — the ConnectedCard's "Requests" button pushes Route.Requests and
  // the shell renders the shared RequestsScreen (koinViewModel() against the
  // shell-provided ViewModelStoreOwner + requestsModule/dataWasmModule DI).
  await step('open Requests pane', async () => {
    const back = await waitForNode(cdp, 'diagnostics Back button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Back', 10000);
    await clickNode(cdp, back, 'diagnostics Back button');
    const requestsBtn = await waitForNode(cdp, 'Requests button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Requests', 15000);
    await clickNode(cdp, requestsBtn, 'Requests button');
    return 'popped Diagnostics, clicked landing Requests button';
  });

  // 13. The screen IS the shared RequestsScreen: title + filter bar + search
  // field all AX-visible. Strings come from the feature's composeResources
  // (requests_title / filter chips / media chips / sort chips) — exact
  // StaticText matches; "All" and "Pending" both exist (filter row AND media
  // row each have an "All").
  await step('REQUESTS screen renders (title + filter bar)', async () => {
    try {
      await waitForNode(cdp, 'Requests title', (n) => nodeRole(n) === 'StaticText' && nodeName(n) === 'Requests', 30000);
      await waitForNode(cdp, 'filter chip Pending', (n) => nodeRole(n) === 'StaticText' && nodeName(n) === 'Pending', 20000);
      await waitForNode(cdp, 'filter chip Failed', (n) => nodeRole(n) === 'StaticText' && nodeName(n) === 'Failed', 20000);
      await waitForNode(cdp, 'media chip Movies', (n) => nodeRole(n) === 'StaticText' && nodeName(n) === 'Movies', 20000);
      await waitForNode(cdp, 'media chip TV', (n) => nodeRole(n) === 'StaticText' && nodeName(n) === 'TV', 20000);
      await waitForNode(cdp, 'sort chip Recent', (n) => nodeRole(n) === 'StaticText' && nodeName(n) === 'Recent', 20000);
      await waitForNode(cdp, 'sort chip Modified', (n) => nodeRole(n) === 'StaticText' && nodeName(n) === 'Modified', 20000);
      // Search field: the driver header's field lesson applies to wasm
      // placeholders too — Compose renders the "Search requests" placeholder
      // but does NOT expose it (nor a label) in the AX tree, so the field is
      // asserted by its textbox ROLE (the pane's only one).
      const boxes = (await axTree(cdp)).filter((n) => nodeRole(n).toLowerCase() === 'textbox');
      if (boxes.length < 1) throw new Error('no search-field textbox in AX tree');
    } catch (e) {
      throw new Error(`${e.message}; texts=${JSON.stringify(await snapshotTexts(cdp))}`);
    }
    return 'Requests title + filter/media/sort chips + search field AX-visible';
  });

  // 14. The honest v1 data state AT THIS POINT IN THE LANE: Seerr is NOT
  // part of the fixture and no credentials have been saved yet (the 16B pane
  // saves dead-host credentials only LATER, step 22+ — this assertion is
  // positionally correct because it runs before that save; session-cookie
  // auth additionally is browser-impossible — Main.kt's SEERR-ON-WEB HONESTY
  // note), so SeerrRepositoryImpl fails every read with "Seerr not
  // configured" and the screen must render its error pane + Retry affordance
  // (NOT a spinner, NOT a crash, NOT an empty list).
  await step('REQUESTS not-configured state', async () => {
    try {
      await waitForNode(cdp, '"Seerr not configured" error line', (n) => nodeRole(n) === 'StaticText' && nodeName(n) === 'Seerr not configured', 60000);
    } catch (e) {
      throw new Error(`${e.message}; texts=${JSON.stringify(await snapshotTexts(cdp))}`);
    }
    await waitForNode(cdp, 'Retry button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Retry', 15000);
    return 'error pane + Retry visible (honest not-connected assertion)';
  });

  // 15. Screenshot evidence for the requests pane.
  await step('requests screenshot', async () => {
    await sleep(1000);
    const { data } = await cdp.send('Page.captureScreenshot', { format: 'png' });
    const path = join(OUT_DIR, 'requests.png');
    writeFileSync(path, Buffer.from(data, 'base64'));
    return path;
  });

  // 16. Wave 16A: pop the requests pane and open the SECOND shared feature
  // screen — the ConnectedCard's "Calendar" button pushes Route.UpcomingCalendar
  // and the shell renders the shared UpcomingCalendarScreen (koinViewModel()
  // against calendarModule, registered in Main.kt this wave). The pop rides
  // history.back() — browser-initiated back is WebAppRoot's reconciled pop
  // path (the correction from the review round: RequestsScreen's scaffold
  // back DOES carry a contentDescription ("Back"); history.back() is chosen
  // for robustness across pane re-layouts, not because the name is missing).
  await step('open Calendar pane', async () => {
    await cdp.send('Runtime.evaluate', { expression: 'history.back()' });
    const calendarBtn = await waitForNode(cdp, 'Calendar button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Calendar', 15000);
    await clickNode(cdp, calendarBtn, 'Calendar button');
    return 'popped Requests, clicked landing Calendar button';
  });

  // 17. The honest v1 state: the fixture has NO *arr servers and the
  // DIRECT_ARR_INTEGRATION experimental flag boots OFF with no web settings
  // UI to flip it, so the screen must render its feature-disabled pane —
  // title "Upcoming" + the "Direct *arr Integration is off" headline + the
  // "Open *arr Settings" button (inert on web: settings has no wasm target,
  // WebAppRoot's cut note). NOT a spinner, NOT a crash, NOT a fake list.
  await step('CALENDAR disabled state', async () => {
    try {
      await waitForNode(cdp, 'calendar title "Upcoming"', (n) => nodeRole(n) === 'StaticText' && nodeName(n) === 'Upcoming', 30000);
      await waitForNode(cdp, '"Direct *arr Integration is off" line', (n) => nodeRole(n) === 'StaticText' && nodeName(n) === 'Direct *arr Integration is off', 20000);
    } catch (e) {
      throw new Error(`${e.message}; texts=${JSON.stringify(await snapshotTexts(cdp))}`);
    }
    await waitForNode(cdp, '"Open *arr Settings" button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Open *arr Settings', 20000);
    return 'title + disabled pane + settings affordance visible (honest flag-off assertion)';
  });

  // 18. Screenshot evidence for the calendar pane.
  await step('calendar screenshot', async () => {
    await sleep(1000);
    const { data } = await cdp.send('Page.captureScreenshot', { format: 'png' });
    const path = join(OUT_DIR, 'calendar.png');
    writeFileSync(path, Buffer.from(data, 'base64'));
    return path;
  });

  // ── Wave 16B: Seerr credentials pane (coordinator merge: follows the
  // calendar block; steps renumbered 19–25) ──────────────────────────────
  // Self-contained block (open Seerr → fill → Save → honest test failure →
  // localStorage persistence proof → screenshot), directly before the
  // zero-console-errors gate.

  // 19. Open the Seerr credentials pane from the landing connected card —
  // after a history.back() from the CALENDAR pane (the browser-initiated
  // back path WebAppRoot.onPopState reconciles), the same reconcile path
  // the requests pop used above.
  await step('open Seerr pane', async () => {
    await cdp.send('Runtime.evaluate', { expression: 'history.back()' });
    const seerrBtn = await waitForNode(cdp, 'Seerr button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Seerr', 15000);
    await clickNode(cdp, seerrBtn, 'Seerr button');
    try {
      await waitForNode(cdp, 'Seerr pane title', (n) => nodeRole(n) === 'StaticText' && nodeName(n) === 'Seerr settings', 15000);
    } catch (e) {
      throw new Error(`${e.message}; texts=${JSON.stringify(await snapshotTexts(cdp))}`);
    }
    return 'clicked landing Seerr button';
  });

  // 20. Both fields present. Same field lesson as the connect flow: Compose
  // does not expose OutlinedTextField labels/placeholders in the AX tree, so
  // the pane renders plain "Server URL"/"API Key" StaticText headers; the
  // fields themselves are asserted by textbox ROLE (exactly two on this
  // pane) and disambiguated by GEOMETRY — Server URL sits above API Key.
  let seerrUrlField = null;
  let seerApiKeyField = null;
  await step('Seerr pane fields present', async () => {
    try {
      await waitForNode(cdp, '"Server URL" header', (n) => nodeRole(n) === 'StaticText' && nodeName(n) === 'Server URL', 15000);
      await waitForNode(cdp, '"API Key" header', (n) => nodeRole(n) === 'StaticText' && nodeName(n) === 'API Key', 15000);
      const deadline = Date.now() + 15000;
      let tbs;
      for (;;) {
        tbs = (await axTree(cdp)).filter((n) => nodeRole(n).toLowerCase() === 'textbox');
        if (tbs.length === 2) break;
        if (Date.now() > deadline) throw new Error(`expected exactly 2 textboxes, got ${tbs.length}`);
        await sleep(300);
      }
      const positioned = await Promise.all(
        tbs.map(async (n) => ({ node: n, y: (await centerOf(cdp, n)).y })),
      );
      positioned.sort((a, b) => a.y - b.y);
      seerrUrlField = positioned[0].node;
      seerApiKeyField = positioned[1].node;
    } catch (e) {
      throw new Error(`${e.message}; texts=${JSON.stringify(await snapshotTexts(cdp))}`);
    }
    return 'Server URL + API Key headers and both textbox fields AX-visible';
  });

  // 21. Fill both fields (click + Input.insertText with the driver's
  // key-event fallback) and verify content landed.
  const SEERR_URL = 'http://localhost:5055';
  const SEERR_API_KEY = 'e2e-seerr-api-key-0123456789abcdef';
  await step('fill Seerr credentials', async () => {
    const howUrl = await typeIntoField(cdp, seerrUrlField, 'Seerr Server URL field', SEERR_URL);
    const howKey = await typeIntoField(cdp, seerApiKeyField, 'Seerr API Key field', SEERR_API_KEY);
    // Re-read fresh nodes by backendDOMNodeId (ghost textbox lesson): the
    // pane's Save/Test buttons gate on nothing, so VALUE CONTAINMENT here is
    // the authoritative typing check.
    const fresh = (await axTree(cdp)).filter((n) => nodeRole(n).toLowerCase() === 'textbox');
    const u = fresh.find((n) => n.backendDOMNodeId === seerrUrlField.backendDOMNodeId);
    const k = fresh.find((n) => n.backendDOMNodeId === seerApiKeyField.backendDOMNodeId);
    if (!u || !k) throw new Error('Seerr field nodes vanished after typing');
    if (nodeValue(u) !== SEERR_URL) throw new Error(`Seerr server URL is "${nodeValue(u)}"`);
    if (nodeValue(k) !== SEERR_API_KEY) throw new Error(`Seerr API key is "${nodeValue(k)}"`);
    return `url via ${howUrl}, key via ${howKey}`;
  });

  // 22. Save → the pane confirms with a "Saved" status line.
  await step('Save → "Saved"', async () => {
    try {
      const save = await waitForNode(cdp, 'Save button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Save', 10000);
      await clickNode(cdp, save, 'Save button');
      await waitForNode(cdp, '"Saved" status line', (n) => nodeRole(n) === 'StaticText' && nodeName(n) === 'Saved', 15000);
    } catch (e) {
      throw new Error(`${e.message}; texts=${JSON.stringify(await snapshotTexts(cdp))}`);
    }
    return 'Save clicked; "Saved" confirmation rendered';
  });

  // 23. Test connection with NO Seerr server in the fixture: the honest
  // assertion is an ERROR status line (never "Connected"). The write happens
  // before the call (persist-then-test, mirroring SeerrSettingsViewModel),
  // so by the time this step ends the credentials are persisted regardless
  // of the fetch outcome.
  await step('Test connection fails honestly (no Seerr server)', async () => {
    try {
      const test = await waitForNode(cdp, 'Test connection button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Test connection', 10000);
      await clickNode(cdp, test, 'Test connection button');
      // Fetch to the dead host + RetryPolicy backoff (retryable transport
      // failures retry 3x, ~1+2+4s) — 90s deadline is generous.
      await waitForNode(cdp, '"Test failed:" status line', (n) => nodeRole(n) === 'StaticText' && nodeName(n).startsWith('Test failed:'), 90000);
      const stillNoConnected = (await axTree(cdp)).every(
        (n) => !(nodeRole(n) === 'StaticText' && nodeName(n).startsWith('Connected')),
      );
      if (!stillNoConnected) throw new Error('"Connected" line rendered without a Seerr server');
    } catch (e) {
      throw new Error(`${e.message}; texts=${JSON.stringify(await snapshotTexts(cdp))}`);
    }
    return 'error status line rendered; no "Connected" claim (honest failure)';
  });

  // 24. PERSISTENCE PROOF (wave 16B's whole point): the save survived to
  // REAL localStorage. Exact keys (see LocalStorageSecureKeyValueStorage +
  // WebDatastoreModule): the secure store under
  // `jellyplay/secure/seerr/api_key` (Base64-of-UTF8 — asserted to DECODE to
  // the typed key), and the Seerr preferences DataStore under
  // `jellyplay/datastore/seerr_prefs.preferences_pb`.
  await step('localStorage persistence proof', async () => {
    const SEERR_SECURE_KEY = 'jellyplay/secure/seerr/api_key';
    const SEERR_PREFS_KEY = 'jellyplay/datastore/seerr_prefs.preferences_pb';
    const readExpr = `(function () {
      return JSON.stringify({
        secure: window.localStorage.getItem('${SEERR_SECURE_KEY}'),
        prefs: window.localStorage.getItem('${SEERR_PREFS_KEY}'),
        seerrKeys: Object.keys(window.localStorage).filter((k) => k.indexOf('seerr') !== -1),
      });
    })()`;
    let snapshot = null;
    const deadline = Date.now() + 20000;
    while (Date.now() < deadline) {
      const { result } = await cdp.send('Runtime.evaluate', { expression: readExpr, returnByValue: true });
      snapshot = JSON.parse(result.value);
      if (snapshot.secure && snapshot.prefs) break;
      // Save is fire-and-forget on a controller scope; give the writes a beat.
      await sleep(500);
    }
    if (!snapshot.secure) throw new Error(`${SEERR_SECURE_KEY} missing from localStorage (keys: ${JSON.stringify(snapshot.seerrKeys)})`);
    if (!snapshot.prefs) throw new Error(`${SEERR_PREFS_KEY} missing from localStorage (keys: ${JSON.stringify(snapshot.seerrKeys)})`);
    const decoded = Buffer.from(snapshot.secure, 'base64').toString('utf8');
    if (decoded !== SEERR_API_KEY) throw new Error(`stored api_key decodes to "${decoded}", expected "${SEERR_API_KEY}"`);
    return `secure=${SEERR_SECURE_KEY} (Base64→"${decoded}"), prefs=${SEERR_PREFS_KEY} present`;
  });

  // 25. Screenshot evidence for the Seerr pane.
  await step('seerr screenshot', async () => {
    await sleep(500);
    const { data } = await cdp.send('Page.captureScreenshot', { format: 'png' });
    const path = join(OUT_DIR, 'seerr.png');
    writeFileSync(path, Buffer.from(data, 'base64'));
    return path;
  });

  // ── End wave 16A+16B blocks ────────────────────────────────────────────

  // ── Wave 16C: SeerrDetail demo surface ─────────────────────────────────
  // Coordinator merge placement: runs LAST (after the 16B credentials block,
  // whose persisted dead-host credentials shape the honest failure below).

  // 26. Boot straight into Route.SeerrDetail via the GATED e2eRoute param
  // (WebAppRoot's backStack note; desktop jellyplay.harness.* precedent).
  // HISTORY: the lane originally clicked the Diagnostics pane's "SeerrDetail
  // (demo)" button — and the wave-16 run read the resulting silence as a
  // "CMP-wasm input dead region below y≈600". Wave 17A's clean-room probe
  // (tools/e2e/input-probe.mjs; docs/e2e/web-input-dead-region.md) measured
  // NO dead region: synthetic CDP clicks deliver everywhere inside the
  // viewport (to y=803.5 of an 805px viewport, at device scale 1 and 1.5;
  // getBoxModel centers are CSS px and stay correct at dpr 1.5 — dividing
  // by dpr is what MISCALCULATES). The wave-16 silence is attributed to
  // that wave's SeerrDetailViewModel construction crash (fixed in
  // e44eb5c46): the demo click landed, navigated, and the crash froze
  // composition, so every later click — Back included — hit a dead UI. The
  // boot param STAYS as lane hygiene regardless of the retraction: reload
  // the page with ?e2eRoute=seerrdetail/550/movie and the shell seeds its
  // back stack with the REAL shared route, decoupling verification from
  // mouse mechanics — the same koinViewModel() graph resolves
  // (detailsModule + dataWasmModule's SeerrRepository/SeerrRequestDelegate +
  // webDetailsPlatformModule's narrow MediaRepository). The demo button
  // remains for human navigation.
  await step('boot into SeerrDetail (gated e2eRoute param)', async () => {
    await cdp.send('Page.navigate', { url: `http://127.0.0.1:${SERVE_PORT}/?e2eRoute=seerrdetail/550/movie` });
    return 'navigated to /?e2eRoute=seerrdetail/550/movie';
  });

  // 27. The screen IS the shared SeerrDetailScreen rendering its honest
  // failure. Ordering note (coordinator merge): the 16B block above already
  // persisted DEAD-host credentials into the REAL stores (localStorage
  // survives the reload), so the VM's SeerrRepository call here fails with a
  // CONNECTION error, not the credential-less "Seerr not configured" literal
  // — the error literal is environment-dependent, so the assertion is the
  // ERROR-STATE AFFORDANCES (the Retry button only exists in the error
  // branch) + the shell's Back scaffold; the error line itself is captured
  // for the step record. A spinner is tolerated transiently; the error state
  // MUST arrive. Crash-on-render (DI graph unresolved, LocalUriHandler
  // missing, wasm klib absent from the bundle) would fire exceptionThrown
  // and fail the console gate below.
  await step('SEERRDETAIL renders honest error state', async () => {
    try {
      await waitForNode(cdp, 'Retry button (error-state affordance)', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Retry', 60000);
      await waitForNode(cdp, 'shell Back button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Back', 15000);
    } catch (e) {
      throw new Error(`${e.message}; texts=${JSON.stringify(await snapshotTexts(cdp))}`);
    }
    const texts = (await axTree(cdp)).filter((n) => nodeRole(n) === 'StaticText').map((n) => nodeName(n));
    const errLine = texts.find((t) => t && t !== 'Retry' && t !== 'Back' && t !== 'Web diagnostics');
    return `error state affordances visible (captured line: ${JSON.stringify(errLine)})`;
  });

  // 28. Screenshot evidence for the SeerrDetail pane.
  await step('seerrdetail screenshot', async () => {
    await sleep(1000);
    const { data } = await cdp.send('Page.captureScreenshot', { format: 'png' });
    const path = join(OUT_DIR, 'seerrdetail.png');
    writeFileSync(path, Buffer.from(data, 'base64'));
    return path;
  });

  // 28b. Wave 18A: the Diagnostics REVISIT + Coil observability gate. The
  // lane's earlier pane visit (steps 7-11) ran on a cold ImageLoader — every
  // artwork request was a memory-cache MISS, so COIL_STATS' hits counter was
  // necessarily 0 there and could only be asserted as "present". But this
  // step also has to contend with the lane's own step 26: the boot-param
  // reload DISCARDED the in-memory session (WasmSecureKeyValueStorage is
  // session-memory only — see Main.kt's SEERR-ON-WEB HONESTY note), so
  // popping the SeerrDetail demo reveals the CONNECT/sign-in card, not the
  // Connected landing (first run's lesson: "timeout waiting for
  // ConnectedCard"). So this step re-signs-in from the revealed card (same
  // flow as steps 5-6, trimmed), enters Diagnostics once (cold entry for
  // this page context: misses + network fetches), then BACK and in AGAIN —
  // the second entry re-runs the exact same two artwork requests, which MUST
  // resolve from the memory cache (hits >= 1) with zero failures and at
  // least one network fetch on the books. The line itself is
  // WebDiagnostics' COIL_STATS (Main.kt CoilStats); the driver polls it
  // (500ms app-side refresh) rather than assuming immediacy.
  await step('COIL_STATS after Diagnostics revisit (hits>0)', async () => {
    // Pop the SeerrDetail demo → WebLanding (signed out after the reload).
    const backFromDetail = await waitForNode(cdp, 'shell Back button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Back', 15000);
    await clickNode(cdp, backFromDetail, 'shell Back button');
    // Re-sign-in (trimmed steps 5-6): the connect form is either still in
    // its first phase or the last-server-url DataStore prefilled the field
    // and the probe still needs the Connect click.
    await waitForNode(cdp, 'connect form', (n) => nodeName(n).includes('Connect to your Jellyfin server'), 30000);
    const urlField = await textboxAt(cdp, 0, 'Server URL field');
    const currentUrl = nodeValue((await axTree(cdp)).find((n) => n.backendDOMNodeId === urlField.backendDOMNodeId) || urlField);
    if (currentUrl !== SERVER_URL) {
      if (currentUrl.length > 0) await clearField(cdp, urlField);
      await typeIntoField(cdp, urlField, 'Server URL field', SERVER_URL);
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
    // Cold entry (this page context's first): misses + fetches.
    let diag = await waitForNode(cdp, 'Diagnostics button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Diagnostics', 15000);
    await clickNode(cdp, diag, 'Diagnostics button');
    await waitForNode(cdp, 'pane title', (n) => nodeName(n) === 'Web diagnostics', 15000);
    await waitForNode(cdp, 'IMAGE_STATE OK line', (n) => nodeName(n).startsWith('IMAGE_STATE: OK'), 60000);
    // Revisit: the same two requests must hit the memory cache now.
    const back = await waitForNode(cdp, 'diagnostics Back button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Back', 10000);
    await clickNode(cdp, back, 'diagnostics Back button');
    await waitForNode(cdp, 'ConnectedCard', (n) => nodeName(n) === 'Connected', 20000);
    diag = await waitForNode(cdp, 'Diagnostics button', (n) => nodeRole(n) === 'button' && nodeName(n) === 'Diagnostics', 15000);
    await clickNode(cdp, diag, 'Diagnostics button');
    await waitForNode(cdp, 'pane title', (n) => nodeName(n) === 'Web diagnostics', 15000);
    await waitForNode(cdp, 'IMAGE_STATE OK line', (n) => nodeName(n).startsWith('IMAGE_STATE: OK'), 60000);
    // Poll the line itself (counters refresh on the pane's 500ms poll; the
    // MediaImage hit may land one beat after the painter's).
    const lineDeadline = Date.now() + 15000;
    let line = null;
    let parsed = null;
    while (Date.now() < lineDeadline) {
      line = (await axTree(cdp)).find((n) => nodeRole(n) === 'StaticText' && nodeName(n).startsWith('COIL_STATS:'));
      parsed = line && /COIL_STATS: hits=(\d+) misses=(\d+) net=(\d+) fail=(\d+)/.exec(nodeName(line));
      if (parsed && +parsed[1] > 0) break;
      await sleep(500);
    }
    if (!parsed) {
      throw new Error(`COIL_STATS line never parsed; texts=${JSON.stringify(await snapshotTexts(cdp))}`);
    }
    const [, hits, misses, net, fail] = parsed;
    if (+hits < 1) throw new Error(`hits=${hits} after revisit — memory cache never hit`);
    if (+net < 1) throw new Error(`net=${net} — cold entry recorded no fetch at all`);
    if (+fail !== 0) throw new Error(`fail=${fail} — a request errored during the lane`);
    const cacheLine = (await axTree(cdp)).find((n) => nodeRole(n) === 'StaticText' && nodeName(n).startsWith('COIL_CACHE:'));
    return `COIL_STATS hits=${hits} misses=${misses} net=${net} fail=${fail}; ${cacheLine ? nodeName(cacheLine) : 'COIL_CACHE line missing'}`;
  });

  // 29. Zero console errors.
  await step('zero console errors', async () => {
    if (consoleErrors.length > 0) throw new Error(`console errors: ${JSON.stringify(consoleErrors.slice(0, 5))}`);
    if (exceptions.length > 0) throw new Error(`uncaught exceptions: ${JSON.stringify(exceptions.slice(0, 5))}`);
    return `consoleAPICalled(error)=0 exceptionThrown=0 (Log.error entries: ${JSON.stringify(logErrors)})`;
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
    consoleErrors,
    exceptions,
    logErrorEntries: logErrors,
    outDir: OUT_DIR,
    screenshot: join(OUT_DIR, 'diagnostics.png'),
    requestsScreenshot: join(OUT_DIR, 'requests.png'),
    calendarScreenshot: join(OUT_DIR, 'calendar.png'),
    seerrScreenshot: join(OUT_DIR, 'seerr.png'),
    seerrDetailScreenshot: join(OUT_DIR, 'seerrdetail.png'),
    seerrPersistenceKeys: {
      secureApiKey: 'jellyplay/secure/seerr/api_key',
      preferencesDataStore: 'jellyplay/datastore/seerr_prefs.preferences_pb',
    },
  };
  const jsonPath = join(OUT_DIR, 'result.json');
  writeFileSync(jsonPath, JSON.stringify(result, null, 2));
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
  process.stdout.write(`RESULT_JSON=${jsonPath}\n`);
  process.exit(verdict === 'PASS' ? 0 : 1);
}
