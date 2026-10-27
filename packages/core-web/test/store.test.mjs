import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import test from 'node:test';
import vm from 'node:vm';

function createHarness(initialState, options = {}) {
  const values = new Map();
  if (options.rawState !== undefined) values.set('io.github.speyll.ovela.state.v1', options.rawState);
  else if (initialState) values.set('io.github.speyll.ovela.state.v1', JSON.stringify(initialState));
  const writes = [];
  const storageEvents = [];
  const listeners = {};
  const timers = new Map();
  const harness = { failWrites: false, writeAttempts: 0 };
  let nextTimerId = 1;

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
    setTimeout: (callback, delay) => {
      const id = nextTimerId++;
      timers.set(id, { callback, delay: Number(delay) || 0 });
      return id;
    },
    clearTimeout: id => timers.delete(id),
    addEventListener: (name, listener) => {
      (listeners[name] ||= []).push(listener);
    },
    dispatchEvent: event => storageEvents.push(event.type),
    CustomEvent: class CustomEvent {
      constructor(type) { this.type = type; }
    },
    localStorage: {
      getItem: key => values.has(key) ? values.get(key) : null,
      setItem: (key, value) => {
        harness.writeAttempts += 1;
        if (harness.failWrites) throw new Error('storage quota exceeded');
        writes.push({ key, value: String(value) });
        values.set(key, String(value));
      },
      removeItem: key => values.delete(key),
    },
  };
  context.window = context;
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(readFileSync(resolve('packages/core-web/src/core.js'), 'utf8'), context);
  vm.runInContext(readFileSync(resolve('packages/core-web/src/store.js'), 'utf8'), context);

  return {
    store: context.OvelaStore,
    values,
    writes,
    storageEvents,
    listeners,
    timers,
    harness,
    runTimers() {
      for (const [id, timer] of Array.from(timers)) {
        timers.delete(id);
        timer.callback();
      }
    },
    fire(name) {
      for (const listener of listeners[name] || []) listener({ type: name });
    },
    lastSavedState() {
      return writes.length ? JSON.parse(writes[writes.length - 1].value) : null;
    },
  };
}

function createStore(initialState) {
  return createHarness(initialState).store;
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

test('restores persisted favorites, progress, settings, and account state', () => {
  const harness = createHarness({
    credentials: { server: 'http://provider.test', username: 'user', password: 'pass' },
    activeAccount: '["http://provider.test","user"]',
    accounts: {
      '["http://provider.test","user"]': {
        favorites: [{ stream_id: 9, name: 'Saved channel', content_type: 'live' }],
        favoriteGroups: [],
        favoriteGroupOrder: ['all', 'live', 'movie', 'series'],
        favoriteItemOrders: {},
        recent: [],
        progress: {},
        settings: {},
      },
    },
    settings: { theme: 'teal' },
    favorites: [{ stream_id: 7, name: 'Saved movie', content_type: 'movie' }],
    favoriteGroups: [],
    favoriteGroupOrder: ['all', 'live', 'movie', 'series'],
    favoriteItemOrders: {},
    recent: [{ stream_id: 8, name: 'Recent series', content_type: 'series' }],
    progress: { 'movie:7': { seconds: 120, duration: 600, updated_at: 10 } },
  });

  const state = harness.store.getState();
  assert.equal(state.settings.theme, 'teal');
  assert.equal(state.favorites[0].name, 'Saved movie');
  assert.equal(state.recent[0].name, 'Recent series');
  assert.equal(harness.store.getProgress('movie', '7').seconds, 120);
  assert.equal(state.accounts['["http://provider.test","user"]'].favorites[0].name, 'Saved channel');
});

test('keeps favorite changes immediately durable and does not schedule a timer', () => {
  const harness = createHarness();
  harness.store.toggleFavorite({ stream_id: 11, name: 'News', content_type: 'live' }, 'live');

  assert.equal(harness.writes.length, 1);
  assert.equal(harness.timers.size, 0);
  const saved = harness.lastSavedState();
  assert.equal(saved.favorites.length, 1);
  assert.equal(saved.favorites[0].name, 'News');
});

test('coalesces playback progress into one bounded persistence write', () => {
  const harness = createHarness();
  harness.store.saveProgress('movie', '42', 10.9, 600);
  harness.store.saveProgress('movie', '42', 20.1, 600);
  harness.store.saveProgress('movie', '42', 30.2, 600);

  assert.equal(harness.writes.length, 0);
  assert.equal(harness.timers.size, 1);
  harness.runTimers();

  assert.equal(harness.writes.length, 1);
  assert.equal(harness.timers.size, 0);
  assert.equal(harness.lastSavedState().progress['movie:42'].seconds, 30);
});

test('flush writes pending progress and cancels the scheduled write', () => {
  const harness = createHarness();
  harness.store.saveProgress('episode', '5', 44, 1200);

  assert.equal(harness.store.flush(), true);
  assert.equal(harness.writes.length, 1);
  assert.equal(harness.timers.size, 0);

  harness.runTimers();
  assert.equal(harness.writes.length, 1);
  assert.equal(harness.lastSavedState().progress['episode:5'].seconds, 44);
});

test('an immediate favorite write includes progress and cancels its timer', () => {
  const harness = createHarness();
  harness.store.saveProgress('movie', '42', 90, 600);
  harness.store.toggleFavorite({ stream_id: 42, name: 'Saved movie', content_type: 'movie' }, 'movie');

  assert.equal(harness.writes.length, 1);
  assert.equal(harness.timers.size, 0);
  const saved = harness.lastSavedState();
  assert.equal(saved.favorites[0].name, 'Saved movie');
  assert.equal(saved.progress['movie:42'].seconds, 90);
});

test('skips progress and settings writes when the durable values did not change', () => {
  const harness = createHarness();
  harness.store.saveProgress('movie', '42', 10.9, 600);
  harness.store.flush();
  const writesAfterFirst = harness.writes.length;

  harness.store.saveProgress('movie', '42', 10.2, 600);
  harness.store.updateSettings({ theme: 'blue' });

  assert.equal(harness.writes.length, writesAfterFirst);
  assert.equal(harness.timers.size, 0);
});

test('uses lifecycle flush to persist pending progress', () => {
  const harness = createHarness();
  harness.store.saveProgress('movie', '42', 55, 600);
  assert.equal(harness.timers.size, 1);

  harness.fire('pagehide');

  assert.equal(harness.writes.length, 1);
  assert.equal(harness.timers.size, 0);
  assert.equal(harness.lastSavedState().progress['movie:42'].seconds, 55);
});

test('keeps in-memory progress after a storage failure and can retry', () => {
  const harness = createHarness();
  harness.harness.failWrites = true;

  harness.store.saveProgress('movie', '42', 77, 600);
  harness.runTimers();

  assert.equal(harness.writes.length, 0);
  assert.equal(harness.storageEvents[0], 'io.github.speyll.ovela-storage-error');
  assert.equal(harness.store.getProgress('movie', '42').seconds, 77);

  harness.harness.failWrites = false;
  assert.equal(harness.store.flush(), true);
  assert.equal(harness.lastSavedState().progress['movie:42'].seconds, 77);
});

test('restores safely from malformed saved state', () => {
  const harness = createHarness(undefined, { rawState: '{"favorites":[' });
  const state = harness.store.getState();

  assert.equal(state.favorites.length, 0);
  assert.equal(state.settings.theme, 'blue');
  assert.equal(harness.writes.length, 0);
});

test('skips redundant credential switches and duplicate demo selection', () => {
  const harness = createHarness();
  const provider = { server: 'http://provider.test', username: 'user', password: 'pass' };

  harness.store.saveCredentials(provider);
  const writesAfterLogin = harness.writes.length;
  harness.store.saveCredentials({ server: 'http://provider.test', username: 'user', password: 'pass' });
  assert.equal(harness.writes.length, writesAfterLogin);

  harness.store.selectDemo();
  const writesAfterDemo = harness.writes.length;
  harness.store.selectDemo();
  assert.equal(harness.writes.length, writesAfterDemo);
});

test('removes completed progress immediately instead of scheduling it', () => {
  const harness = createHarness();
  harness.store.saveProgress('movie', '42', 100, 600);
  harness.runTimers();
  assert.equal(harness.writes.length, 1);

  harness.store.saveProgress('movie', '42', 590, 600);
  assert.equal(harness.timers.size, 0);
  assert.equal(harness.writes.length, 2);
  assert.equal(Object.hasOwn(harness.lastSavedState().progress, 'movie:42'), false);
});
