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
// DIAG_OVERALL: OK, zero console errors / uncaught exceptions, screenshot.
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

  // 12. Zero console errors.
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
  };
  const jsonPath = join(OUT_DIR, 'result.json');
  writeFileSync(jsonPath, JSON.stringify(result, null, 2));
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
  process.stdout.write(`RESULT_JSON=${jsonPath}\n`);
  process.exit(verdict === 'PASS' ? 0 : 1);
}
