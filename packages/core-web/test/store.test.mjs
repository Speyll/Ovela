import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import test from 'node:test';
import vm from 'node:vm';

function createStore(initialState) {
  const values = new Map();
  if (initialState) values.set('xtreamlytv.state.v1', JSON.stringify(initialState));
  const context = {
    console,
    Date,
    JSON,
    Math,
    Object,
    Array,
    String,
    Number,
    RegExp,
    isFinite,
    localStorage: {
      getItem: key => values.has(key) ? values.get(key) : null,
      setItem: (key, value) => values.set(key, String(value)),
      removeItem: key => values.delete(key),
    },
  };
  context.window = context;
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(readFileSync(resolve('packages/core-web/src/core.js'), 'utf8'), context);
  vm.runInContext(readFileSync(resolve('packages/core-web/src/store.js'), 'utf8'), context);
  return context.XtreamlyTVStore;
}

test('persists favorite group and item order and cleans removed entries', () => {
  const store = createStore();
  const live = { stream_id: 11, name: 'Live Eleven', content_type: 'live' };
  const movie = { stream_id: 22, name: 'Movie Twenty Two', content_type: 'movie', stream_type: 'movie' };

  store.toggleFavorite(live, 'live');
  store.toggleFavorite(movie, 'movie');
  const group = store.saveFavoriteGroup({
    name: 'Weekend Picks',
    icon: 'star',
    color: 'rose',
    itemKeys: ['live:11', 'movie:22'],
  });

  store.saveFavoriteGroupOrder([group.id, 'all', 'live', 'movie', 'series']);
  store.saveFavoriteItemOrder(group.id, ['live:11', 'movie:22']);

  let state = store.getState();
  assert.deepEqual(Array.from(state.favoriteGroupOrder), [group.id, 'all', 'live', 'movie', 'series']);
  assert.deepEqual(Array.from(state.favoriteItemOrders[group.id]), ['live:11', 'movie:22']);

  store.toggleFavorite(live, 'live');
  state = store.getState();
  assert.equal(state.favoriteGroups[0].itemKeys.includes('live:11'), false);
  assert.equal(state.favoriteItemOrders[group.id].includes('live:11'), false);

  store.deleteFavoriteGroup(group.id);
  state = store.getState();
  assert.equal(state.favoriteGroupOrder.includes(group.id), false);
  assert.equal(Object.hasOwn(state.favoriteItemOrders, group.id), false);
});

test('migrates missing order fields without losing existing groups', () => {
  const store = createStore({
    settings: {},
    favorites: [],
    favoriteGroups: [{
      id: 'legacy',
      name: 'Legacy Group',
      icon: 'folder',
      color: 'blue',
      itemKeys: [],
    }],
    recent: [],
    progress: {},
  });

  const state = store.getState();
  assert.deepEqual(Array.from(state.favoriteGroupOrder), ['all', 'live', 'movie', 'series', 'legacy']);
  assert.deepEqual(Object.keys(state.favoriteItemOrders), []);
});


test('favoriteAutoSort defaults to enabled and can be disabled', () => {
  const store = createStore();
  assert.equal(store.getState().settings.favoriteAutoSort, true);
  store.updateSettings({ favoriteAutoSort: false });
  assert.equal(store.getState().settings.favoriteAutoSort, false);
});

test('keeps favorites, history, and progress isolated by provider account', () => {
  const store = createStore();
  const providerA = { server: 'http://provider-a.test/', username: 'alice', password: 'one' };
  const providerB = { server: 'http://provider-b.test', username: 'bob', password: 'two' };

  store.saveCredentials(providerA);
  store.toggleFavorite({ stream_id: 42, name: 'Provider A', content_type: 'live' }, 'live');
  store.addRecent({ stream_id: 42, name: 'Provider A', content_type: 'live' }, 'live');
  store.saveProgress('movie', '42', 120, 600);

  store.saveCredentials(providerB);
  assert.equal(store.getState().favorites.length, 0);
  assert.equal(store.getState().recent.length, 0);
  assert.equal(store.getProgress('movie', '42'), null);
  store.toggleFavorite({ stream_id: 42, name: 'Provider B', content_type: 'live' }, 'live');

  store.saveCredentials(providerA);
  assert.equal(store.getState().favorites[0].name, 'Provider A');
  assert.equal(store.getState().recent[0].name, 'Provider A');
  assert.equal(store.getProgress('movie', '42').seconds, 120);
});

test('refreshes saved channel artwork when a provider recovers or changes its icon URL', () => {
  const store = createStore();
  store.saveCredentials({ server: 'http://provider.test', username: 'user', password: 'pass' });
  store.toggleFavorite({ stream_id: 7, name: 'News', stream_icon: 'http://old.test/7.png', content_type: 'live' }, 'live');
  store.addRecent({ stream_id: 7, name: 'News', stream_icon: 'http://old.test/7.png', content_type: 'live' }, 'live');

  store.refreshArtwork([{ stream_id: 7, stream_icon: 'http://restored.test/7.png' }], 'live');
  const state = store.getState();
  assert.equal(state.favorites[0].stream_icon, 'http://restored.test/7.png');
  assert.equal(state.recent[0].stream_icon, 'http://restored.test/7.png');
});
