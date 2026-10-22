import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import test from 'node:test';
import vm from 'node:vm';

const playlist = readFileSync(resolve('packages/contracts/fixtures/m3u/basic.m3u'), 'utf8');

function createApi(fetch) {
  const context = { AbortController, URL, URLSearchParams, clearTimeout, fetch, setTimeout };
  context.window = context;
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(readFileSync(resolve('packages/core-web/src/core.js'), 'utf8'), context);
  vm.runInContext(readFileSync(resolve('packages/core-web/src/api.js'), 'utf8'), context);
  return new context.XtreamlyTVM3uApi({ sourceType: 'm3u', playlistUrl: 'https://provider.test/list.m3u' }, {});
}

test('loads and normalizes M3U catalogs without Xtream actions', async () => {
  const api = createApi(async () => ({ ok: true, text: async () => playlist }));
  const profile = await api.authenticate();
  assert.match(profile.status, /2 entries/);
  assert.equal((await api.getLiveCategories()).length, 1);
  assert.equal((await api.getVodCategories()).length, 1);
  assert.equal((await api.getLiveStreams('m3u-group-4bc7bc46'))[0].name, 'News One');
  assert.equal((await api.getVodStreams('m3u-group-257fbbf4'))[0].name, 'Example Film');
  assert.equal((await api.getLiveCandidates(api.entries[0]))[0].url, 'https://stream.example/live/news-one.m3u8');
});

test('rejects empty playlists with a useful error', async () => {
  const api = createApi(async () => ({ ok: true, text: async () => '#EXTM3U\n' }));
  await assert.rejects(api.authenticate(), /did not contain any playable entries/);
});
