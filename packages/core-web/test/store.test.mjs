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
