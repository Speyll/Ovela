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
    if (req.url === '/playlist.m3u') {
      res.setHeader('Content-Type', 'audio/x-mpegurl');
      res.end('#EXTM3U\n#EXTINF:-1 group-title="News",News One\nhttps://media.example/news.m3u8\n');
      return;
    }
    if (req.url === '/redirect.m3u') {
      res.statusCode = 302;
      res.setHeader('Location', '/playlist.m3u');
      res.end();
      return;
    }
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
      ALLOWED_PLAYLISTS: `http://127.0.0.1:${upstreamPort}/playlist.m3u,http://127.0.0.1:${upstreamPort}/redirect.m3u`,
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

async function playlistRequest({ token = 'test-token', url = `http://127.0.0.1:${upstreamPort}/playlist.m3u` } = {}) {
  return fetch(`http://127.0.0.1:${bridgePort}/api/m3u`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Origin': 'http://tv.test',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ url }),
  });
}

test('proxies an explicitly allowlisted M3U playlist', async () => {
  const response = await playlistRequest();
  assert.equal(response.status, 200);
  assert.match(await response.text(), /#EXTM3U/);
  assert.equal(response.headers.get('access-control-allow-origin'), 'http://tv.test');
});

test('follows only allowlisted playlist redirects', async () => {
  const response = await playlistRequest({ url: `http://127.0.0.1:${upstreamPort}/redirect.m3u` });
  assert.equal(response.status, 200);
  assert.match(await response.text(), /News One/);
});

test('rejects playlists outside the explicit allowlist', async () => {
  const response = await playlistRequest({ url: `http://127.0.0.1:${upstreamPort}/other.m3u` });
  assert.equal(response.status, 403);
});

test('requires authentication for playlist proxying', async () => {
  const response = await playlistRequest({ token: '' });
  assert.equal(response.status, 401);
});
