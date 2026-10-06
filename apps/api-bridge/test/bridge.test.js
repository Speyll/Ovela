'use strict';

const assert = require('node:assert/strict');
const { spawn } = require('node:child_process');
const http = require('node:http');
const path = require('node:path');
const test = require('node:test');

const bridgePort = 18787;
const upstreamPort = 18788;
let bridge;
let upstream;

test.before(async () => {
  upstream = http.createServer((req, res) => {
    res.setHeader('Content-Type', 'application/json');
    res.end(JSON.stringify({ action: new URL(req.url, `http://127.0.0.1:${upstreamPort}`).searchParams.get('action') }));
  });
  await new Promise(resolve => upstream.listen(upstreamPort, '127.0.0.1', resolve));
  bridge = spawn(process.execPath, [path.resolve(__dirname, '../index.js')], {
    env: {
      ...process.env,
      PORT: String(bridgePort),
      BRIDGE_TOKEN: 'test-token',
      ALLOWED_SERVERS: `http://127.0.0.1:${upstreamPort}`,
      ALLOWED_ORIGINS: 'http://tv.test',
    },
    stdio: ['ignore', 'pipe', 'inherit'],
  });
  await new Promise((resolve, reject) => {
    bridge.stdout.once('data', resolve);
    bridge.once('error', reject);
    bridge.once('exit', code => reject(new Error(`bridge exited ${code}`)));
  });
});

test.after(() => {
  bridge.kill();
  upstream.close();
});

async function request({ token = 'test-token', server = `http://127.0.0.1:${upstreamPort}`, action = 'get_series_streams' } = {}) {
  return fetch(`http://127.0.0.1:${bridgePort}/api/xtream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Origin': 'http://tv.test',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ server, username: 'user', password: 'pass', params: { action } }),
  });
}

test('requires a bridge token', async () => {
  assert.equal((await request({ token: '' })).status, 401);
});

test('blocks providers outside the explicit allowlist', async () => {
  assert.equal((await request({ server: 'http://127.0.0.1:1' })).status, 403);
});

test('allows the client series fallback action', async () => {
  const response = await request();
  assert.equal(response.status, 200);
  assert.equal((await response.json()).action, 'get_series_streams');
  assert.equal(response.headers.get('access-control-allow-origin'), 'http://tv.test');
});
