'use strict';

const http = require('http');
const { URL } = require('url');

const PORT = Number(process.env.PORT || 8787);
const BRIDGE_TOKEN = String(process.env.BRIDGE_TOKEN || '');
const ALLOWED_SERVERS = String(process.env.ALLOWED_SERVERS || '')
  .split(',').map(value => value.trim()).filter(Boolean).map(value => validServer(value));
const ALLOWED_ORIGINS = String(process.env.ALLOWED_ORIGINS || '')
  .split(',').map(value => value.trim()).filter(Boolean);
const ALLOWED_PLAYLISTS = String(process.env.ALLOWED_PLAYLISTS || '')
  .split(',').map(value => value.trim()).filter(Boolean).map(value => validPlaylist(value)).filter(Boolean);
const MAX_RESPONSE_BYTES = Number(process.env.MAX_RESPONSE_BYTES || 8 * 1024 * 1024);
const MAX_PLAYLIST_BYTES = Number(process.env.MAX_PLAYLIST_BYTES || 4 * 1024 * 1024);
const ALLOWED_ACTIONS = new Set([
  '', 'get_live_categories', 'get_live_streams', 'get_short_epg',
  'get_vod_categories', 'get_vod_streams', 'get_vod_info',
  'get_series_categories', 'get_series', 'get_series_streams', 'get_series_info'
]);

function cors(req, res) {
  const origin = String(req.headers.origin || '');
  if (origin && ALLOWED_ORIGINS.includes(origin)) {
    res.setHeader('Access-Control-Allow-Origin', origin);
    res.setHeader('Vary', 'Origin');
  }
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS, GET');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  res.setHeader('Cache-Control', 'no-store');
}

function send(req, res, status, body) {
  cors(req, res);
  res.statusCode = status;
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.end(JSON.stringify(body));
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', chunk => {
      body += chunk;
      if (body.length > 256 * 1024) req.destroy(new Error('Request too large'));
    });
    req.on('end', () => {
      try { resolve(JSON.parse(body || '{}')); } catch (error) { reject(new Error('Invalid JSON')); }
    });
    req.on('error', reject);
  });
}

function validPlaylist(value) {
  try {
    const url = new URL(String(value));
    if (!['http:', 'https:'].includes(url.protocol)) return null;
    url.hash = '';
    return url.toString();
  } catch (_) { return null; }
}

function validServer(value) {
  try {
    const url = new URL(String(value));
    if (!['http:', 'https:'].includes(url.protocol)) return null;
    url.username = ''; url.password = ''; url.search = ''; url.hash = '';
    return url.toString().replace(/\/$/, '');
  } catch (_) { return null; }
}

const server = http.createServer(async (req, res) => {
  cors(req, res);
  if (req.method === 'OPTIONS') { res.statusCode = 204; return res.end(); }
  if (req.method === 'GET' && req.url === '/health') return send(req, res, 200, { ok: true, service: 'ovela-api-bridge' });
  if (req.method !== 'POST' || !['/api/xtream', '/api/m3u'].includes(req.url)) return send(req, res, 404, { error: 'Not found' });
  if (!BRIDGE_TOKEN || req.headers.authorization !== `Bearer ${BRIDGE_TOKEN}`) {
    return send(req, res, 401, { error: 'Bridge authentication required' });
  }
  const origin = String(req.headers.origin || '');
  if (origin && !ALLOWED_ORIGINS.includes(origin)) return send(req, res, 403, { error: 'Origin not allowed' });

  try {
    const body = await readJson(req);
    if (req.url === '/api/m3u') {
      const playlist = validPlaylist(body.url);
      if (!playlist) return send(req, res, 400, { error: 'A valid playlist URL is required' });
      if (!ALLOWED_PLAYLISTS.includes(playlist)) return send(req, res, 403, { error: 'Playlist URL not allowed' });
      let target = playlist;
      for (let hop = 0; hop <= 3; hop += 1) {
        const upstream = await fetch(target, {
          headers: { 'User-Agent': 'Ovela-API-Bridge/0.7' },
          redirect: 'manual',
          signal: AbortSignal.timeout(45000)
        });
        if ([301, 302, 303, 307, 308].includes(upstream.status)) {
          const location = upstream.headers.get('location');
          if (!location) return send(req, res, 502, { error: 'Playlist redirect was missing a location' });
          const next = validPlaylist(new URL(location, target).toString());
          if (!next || !ALLOWED_PLAYLISTS.includes(next)) return send(req, res, 403, { error: 'Playlist redirect not allowed' });
          target = next;
          continue;
        }
        const declaredLength = Number(upstream.headers.get('content-length') || 0);
        if (declaredLength > MAX_PLAYLIST_BYTES) return send(req, res, 502, { error: 'Playlist response too large' });
        const buffer = Buffer.from(await upstream.arrayBuffer());
        if (buffer.length > MAX_PLAYLIST_BYTES) return send(req, res, 502, { error: 'Playlist response too large' });
        cors(req, res);
        res.statusCode = upstream.status;
        res.setHeader('Content-Type', upstream.headers.get('content-type') || 'text/plain; charset=utf-8');
        return res.end(buffer);
      }
      return send(req, res, 502, { error: 'Too many playlist redirects' });
    }
    const base = validServer(body.server);
    if (!base || !body.username || !body.password) return send(req, res, 400, { error: 'server, username, and password are required' });
    if (!ALLOWED_SERVERS.includes(base)) return send(req, res, 403, { error: 'Provider server not allowed' });
    const params = body.params && typeof body.params === 'object' ? body.params : {};
    const action = String(params.action || '');
    if (!ALLOWED_ACTIONS.has(action)) return send(req, res, 400, { error: 'Unsupported action' });

    const target = new URL(base + '/player_api.php');
    target.searchParams.set('username', String(body.username));
    target.searchParams.set('password', String(body.password));
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== null && ['action','category_id','stream_id','vod_id','series_id','limit'].includes(key)) {
        target.searchParams.set(key, String(value));
      }
    }

    const upstream = await fetch(target, {
      headers: { 'User-Agent': 'Ovela-API-Bridge/0.7' },
      redirect: 'error',
      signal: AbortSignal.timeout(18000)
    });
    const declaredLength = Number(upstream.headers.get('content-length') || 0);
    if (declaredLength > MAX_RESPONSE_BYTES) return send(req, res, 502, { error: 'Upstream response too large' });
    const buffer = Buffer.from(await upstream.arrayBuffer());
    if (buffer.length > MAX_RESPONSE_BYTES) return send(req, res, 502, { error: 'Upstream response too large' });
    cors(req, res);
    res.statusCode = upstream.status;
    res.setHeader('Content-Type', upstream.headers.get('content-type') || 'application/json; charset=utf-8');
    res.end(buffer);
  } catch (error) {
    send(req, res, 502, { error: error.message || 'Upstream request failed' });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Ovela API Bridge listening on port ${PORT}`);
});
