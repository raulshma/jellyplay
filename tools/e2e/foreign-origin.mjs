// Zero-dependency CORS-enabled second-origin image server (wave 20B).
//
// The eviction lane (web-cache-eviction.mjs) proves Coil loads artwork from a
// NON-Jellyfin origin: the app is served from http://127.0.0.1:8901 (serve.mjs)
// and this process serves the same machine on a DIFFERENT PORT — a different
// port is a different origin, so the browser's fetch() inside Coil's
// KtorNetworkFetcherFactory runs in CORS mode and the response MUST carry
// Access-Control-Allow-Origin for the image to be readable. Every response
// here sends `Access-Control-Allow-Origin: *` (plus Allow-Methods/Headers so
// an OPTIONS preflight would also pass; a plain image GET never sends one).
//
// Fork of serve.mjs (same MIME table + traversal guard) minus the dist-staging
// assumptions, plus the CORS headers. Usage:
//   node foreign-origin.mjs --root <dir> --port 8599
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
const port = Number(arg('port', 8599));

const MIME = {
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.png': 'image/png',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.svg': 'image/svg+xml',
};

const server = http.createServer((req, res) => {
  const cors = {
    'access-control-allow-origin': '*',
    'access-control-allow-methods': 'GET, HEAD, OPTIONS',
    'access-control-allow-headers': '*',
  };
  if (req.method === 'OPTIONS') {
    res.writeHead(204, cors).end();
    return;
  }
  const urlPath = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  let file = join(root, urlPath);
  if (urlPath === '/' || urlPath === '') file = join(root, 'index.html');
  // Path traversal guard: keep every response inside the served root.
  if (!normalize(file).startsWith(root)) {
    res.writeHead(403, { ...cors }).end('forbidden');
    return;
  }
  if (!existsSync(file) || !statSync(file).isFile()) {
    res.writeHead(404, { ...cors }).end('not found');
    return;
  }
  res.writeHead(200, {
    ...cors,
    'content-type': MIME[extname(file).toLowerCase()] ?? 'application/octet-stream',
    'content-length': statSync(file).size,
    'cache-control': 'no-store',
  });
  createReadStream(file).pipe(res);
});

server.listen(port, '127.0.0.1', () => {
  process.stdout.write(`FOREIGN_ORIGIN_READY ${port} ${root}\n`);
});

// Parent lane kills us by PID on exit; still handle SIGTERM gracefully in
// case of standalone use.
process.on('SIGTERM', () => server.close(() => process.exit(0)));
