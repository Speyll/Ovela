import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import test from 'node:test';
import vm from 'node:vm';

function createApi(fetch) {
  const context = { AbortController, URL, URLSearchParams, clearTimeout, fetch, setTimeout };
  context.window = context;
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(readFileSync(resolve('packages/core-web/src/core.js'), 'utf8'), context);
  vm.runInContext(readFileSync(resolve('packages/core-web/src/api.js'), 'utf8'), context);
  return new context.OvelaApi({ server: 'http://provider.test', username: 'u', password: 'p' }, {});
}

test('request timeout covers reading the response body', async () => {
  const api = createApi(async () => ({
    ok: true,
    json: () => new Promise(resolve => setTimeout(() => resolve({ ok: true }), 80)),
  }));
  await assert.rejects(api.request('get_vod_info', {}, { timeout: 10 }), /too long/i);
});

test('series fallback is limited to unsupported-action responses', async () => {
  const api = createApi(async url => {
    const action = new URL(url).searchParams.get('action');
    if (action === 'get_series') return { ok: false, status: 404, json: async () => ({}) };
    return { ok: true, json: async () => [{ series_id: 9 }] };
  });
  const items = await api.getSeries('3');
  assert.equal(items.length, 1);

  api.request = async () => { throw new Error('network outage'); };
  await assert.rejects(api.getSeries('3'), /network outage/);
});
