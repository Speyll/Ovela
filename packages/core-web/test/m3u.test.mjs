import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import test from 'node:test';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const core = require('../src/core.js');

const fixture = name => readFileSync(resolve('packages/contracts/fixtures/m3u', name), 'utf8');
const expected = JSON.parse(fixture('expected-basic.json'));

test('parses EXTINF metadata, groups, movies, and EPG URLs', () => {
  const playlist = core.parseM3u(fixture('basic.m3u'));
  assert.deepEqual(playlist.epgUrls, expected.epgUrls);
  assert.deepEqual(playlist.categories.map(category => ({ id: category.category_id, name: category.category_name })), expected.categories);
  assert.deepEqual(playlist.entries.map(entry => ({ name: entry.name, type: entry.content_type, url: entry.stream_url, group: entry.group_title })), expected.entries);
  assert.equal(playlist.entries[0].stream_icon, 'https://img.example/news.png');
  assert.equal(playlist.entries[1].container_extension, 'mp4');
});

test('keeps commas in titles and quoted attributes', () => {
  const playlist = core.parseM3u([
    '#EXTM3U',
    '#EXTINF:-1 tvg-id="news,us" group-title="News, Weather",World Report, Evening',
    'https://provider.example/live/news.m3u8',
  ].join('\n'));
  assert.equal(playlist.entries.length, 1);
  assert.equal(playlist.entries[0].name, 'World Report, Evening');
  assert.equal(playlist.entries[0].group_title, 'News, Weather');
  assert.equal(playlist.entries[0].tvg_id, 'news,us');
});

test('preserves valid entries around malformed metadata and bare URLs', () => {
  const playlist = core.parseM3u(fixture('malformed.m3u'));
  assert.equal(playlist.entries.length, 3);
  assert.deepEqual(playlist.entries.map(entry => entry.name), ['Direct channel', 'Missing name metadata', 'bare-entry.ts']);
  assert.ok(playlist.warnings.some(warning => warning.includes('Broken without URL')));
  assert.equal(playlist.warnings.some(warning => warning.includes('missing the #EXTM3U')), false);
});

test('keeps query strings and rewrites only the alternate origin', () => {
  const playlist = core.parseM3u(fixture('query-urls.m3u'));
  const candidates = core.m3uCandidates(playlist.entries[0], ['https://backup.example:8443/', 'https://backup.example:8443']);
  assert.equal(candidates[0].url, 'https://origin.example/live/channel.m3u8?token=abc&expires=123');
  assert.equal(candidates[1].url, 'https://backup.example:8443/live/channel.m3u8?token=abc&expires=123');
  assert.equal(candidates.length, 2);
});

test('uses M3U playlist identity for provider-scoped state', () => {
  assert.equal(core.providerKey({ sourceType: 'm3u', playlistUrl: 'https://provider.example/list.m3u' }), '["m3u","https://provider.example/list.m3u"]');
  assert.equal(core.providerKey({ server: 'https://provider.example/', username: 'user' }), '["https://provider.example","user"]');
});

test('recovery is bounded and prefers another candidate before repeating', () => {
  assert.equal(core.recoveryDecision({ candidateIndex: 0, candidateCount: 2, attempts: 0 }, { kind: 'not_found' }).action, 'next');
  assert.equal(core.recoveryDecision({ candidateIndex: 1, candidateCount: 2, attempts: 0 }, { kind: 'auth' }).action, 'fail');
  assert.equal(core.recoveryDecision({ candidateIndex: 0, candidateCount: 1, attempts: 0 }, { kind: 'transient' }).action, 'retry');
  assert.equal(core.recoveryDecision({ candidateIndex: 0, candidateCount: 1, attempts: 2, maxAttempts: 2 }, { kind: 'transient' }).action, 'fail');
});

test('recovery can retry the current candidate before advancing', () => {
  const state = { candidateIndex: 0, candidateCount: 2, attempts: 0, maxAttempts: 1, retryBeforeNext: true };
  assert.equal(core.recoveryDecision(state, { kind: 'transient' }).action, 'retry');
  assert.equal(core.recoveryDecision({ ...state, attempts: 1 }, { kind: 'transient' }).action, 'next');
});
