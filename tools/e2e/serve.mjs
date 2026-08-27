// Zero-dependency static file server for the web E2E lane (wave 13C).
// Serves the staged webpack bundle (webapp.js + .wasm pair + index.html) for
// headless-Edge CDP verification. Correct MIME for .wasm is load-bearing:
// WebAssembly.instantiateStreaming rejects a non-"application/wasm" response.
//
// Usage: node serve.mjs --root <dir> --port <n>
import http from 'node:http';
import { createReadStream, existsSync, statSync } from 'node:fs';
import { extname, join, normalize } from 'node:path';

function arg(name, fallback) {
  const i = process.argv.indexOf(`--${name}`);
  if (i === -1) return fallback;
  const v = process.argv[i + 1];
  return v && !v.startsWith('--') ? v : fallback;
}

const root = normalize(arg('root', process.cwd()));
const port = Number(arg('port', 8901));

const MIME = {
  '.wasm': 'application/wasm',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.htm': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.map': 'application/json; charset=utf-8',
};

const server = http.createServer((req, res) => {
  const urlPath = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  let file = join(root, urlPath);
  if (urlPath === '/' || urlPath === '') file = join(root, 'index.html');
  // Path traversal guard: keep every response inside the staged root.
  if (!normalize(file).startsWith(root)) {
    res.writeHead(403).end('forbidden');
    return;
  }
  if (!existsSync(file) || !statSync(file).isFile()) {
    res.writeHead(404).end('not found');
    return;
  }
  res.writeHead(200, {
    'content-type': MIME[extname(file).toLowerCase()] ?? 'application/octet-stream',
    'content-length': statSync(file).size,
    'cache-control': 'no-store',
  });
  createReadStream(file).pipe(res);
});

server.listen(port, '127.0.0.1', () => {
  process.stdout.write(`SERVE_READY ${port} ${root}\n`);
});

// Parent (web-verify.mjs) kills us by PID on exit; still handle SIGTERM
// gracefully in case of standalone use.
process.on('SIGTERM', () => server.close(() => process.exit(0)));
