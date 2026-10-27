/* Generated from packages/core-web/src/store.js. Do not edit this copy. */
(function () {
  'use strict';

  var Core = window.OvelaCore;
  if (!Core) throw new Error('OvelaCore must load before store.js');

  var KEY = 'io.github.speyll.ovela.state.v1';
  var defaults = {
    credentials: null,
    activeAccount: '',
    accounts: {},
    settings: {
      apiProxy: '',
      streamFormat: 'auto',
      autoplay: true,
      theme: 'blue',
      favoriteAutoSort: true,
      maxCachedCategories: 3
    },
    favorites: [],
    favoriteGroups: [],
    favoriteGroupOrder: ['all', 'live', 'movie', 'series'],
    favoriteItemOrders: {},
    recent: [],
    progress: {}
  };

  function clone(value) { return JSON.parse(JSON.stringify(value)); }

  function normalizedItem(item, type) {
    var output = Object.assign({}, item || {});
    output.content_type = type || Core.inferType(output);
    return output;
  }

  function migrateItems(items) {
    return (Array.isArray(items) ? items : []).map(function (item) {
      return normalizedItem(item, Core.inferType(item));
    });
  }

  function favoriteKey(type, id) {
    return String(type || 'live') + ':' + String(id || '');
  }

  function normalizeFavoriteGroup(group) {
    group = group && typeof group === 'object' ? group : {};
    var allowedIcons = ['heart', 'tv', 'popcorn', 'play', 'smile', 'trophy', 'folder', 'star'];
    var allowedColors = ['purple', 'blue', 'teal', 'orange', 'rose', 'lime', 'slate'];
    var icon = allowedIcons.indexOf(group.icon) >= 0 ? group.icon : 'folder';
    var color = allowedColors.indexOf(group.color) >= 0 ? group.color : 'purple';
    var seen = {};
    var itemKeys = (Array.isArray(group.itemKeys) ? group.itemKeys : []).map(String).filter(function (key) {
      if (!key || seen[key]) return false;
      seen[key] = true;
      return /^(live|movie|series):.+/.test(key);
    });
    return {
      id: String(group.id || ''),
      name: String(group.name || 'Untitled group').trim().slice(0, 36) || 'Untitled group',
      icon: icon,
      color: color,
      itemKeys: itemKeys.slice(0, 250),
      created_at: Number(group.created_at || Date.now()),
      updated_at: Number(group.updated_at || Date.now())
    };
  }

  function migrateFavoriteGroups(groups) {
    var seen = {};
    return (Array.isArray(groups) ? groups : []).map(normalizeFavoriteGroup).filter(function (group) {
      if (!group.id || seen[group.id]) return false;
      seen[group.id] = true;
      return true;
    }).slice(0, 24);
  }

  function normalizeGroupOrder(order, groups) {
    var valid = ['all', 'live', 'movie', 'series'].concat((groups || []).map(function (group) { return group.id; }));
    var validMap = {};
    valid.forEach(function (id) { validMap[id] = true; });
    var seen = {};
    return (Array.isArray(order) ? order : []).map(String).filter(function (id) {
      if (!validMap[id] || seen[id]) return false;
      seen[id] = true;
      return true;
    }).concat(valid.filter(function (id) {
      if (seen[id]) return false;
      seen[id] = true;
      return true;
    }));
  }

  function normalizeFavoriteItemOrders(orders) {
    var output = {};
    if (!orders || typeof orders !== 'object' || Array.isArray(orders)) return output;
    Object.keys(orders).slice(0, 28).forEach(function (groupId) {
      var seen = {};
      var keys = (Array.isArray(orders[groupId]) ? orders[groupId] : []).map(String).filter(function (key) {
        if (!/^(live|movie|series):.+/.test(key) || seen[key]) return false;
        seen[key] = true;
        return true;
      }).slice(0, 250);
      if (keys.length) output[String(groupId)] = keys;
    });
    return output;
  }

  function load() {
    try {
      var raw = localStorage.getItem(KEY);
      if (!raw) return clone(defaults);
      var parsed = JSON.parse(raw);
      var groups = migrateFavoriteGroups(parsed.favoriteGroups);
      return {
        credentials: parsed.credentials || null,
        activeAccount: parsed.activeAccount || Core.providerKey(parsed.credentials),
        accounts: parsed.accounts && typeof parsed.accounts === 'object' ? parsed.accounts : {},
        settings: Object.assign({}, defaults.settings, parsed.settings || {}),
        favorites: migrateItems(parsed.favorites),
        favoriteGroups: groups,
        favoriteGroupOrder: normalizeGroupOrder(parsed.favoriteGroupOrder, groups),
        favoriteItemOrders: normalizeFavoriteItemOrders(parsed.favoriteItemOrders),
        recent: migrateItems(parsed.recent),
        progress: parsed.progress && typeof parsed.progress === 'object' ? parsed.progress : {}
      };
    } catch (error) { return clone(defaults); }
  }

  var state = load();
  var PROGRESS_PERSIST_DELAY_MS = 1000;
  var persistenceTimer = null;
  var persistenceRevision = 0;
  var persistedRevision = 0;

  function markChanged() { persistenceRevision += 1; }

  function clearPersistenceTimer() {
    if (persistenceTimer !== null && typeof clearTimeout === 'function') clearTimeout(persistenceTimer);
    persistenceTimer = null;
  }

  function persistState() {
    clearPersistenceTimer();
    if (persistenceRevision === persistedRevision) return true;
    var progressKeys = Object.keys(state.progress);
    if (progressKeys.length > 500) {
      progressKeys.sort(function (a, b) { return Number(state.progress[b].updated_at || 0) - Number(state.progress[a].updated_at || 0); });
      progressKeys.slice(500).forEach(function (key) { delete state.progress[key]; });
    }
    try {
      localStorage.setItem(KEY, JSON.stringify(state));
      persistedRevision = persistenceRevision;
      return true;
    } catch (error) {
      if (window.dispatchEvent && typeof CustomEvent !== 'undefined') window.dispatchEvent(new CustomEvent('io.github.speyll.ovela-storage-error'));
      return false;
    }
  }

  function scheduleProgressPersist() {
    markChanged();
    if (persistenceTimer !== null) return;
    if (typeof setTimeout !== 'function') {
      persistState();
      return;
    }
    persistenceTimer = setTimeout(function () {
      persistenceTimer = null;
      persistState();
    }, PROGRESS_PERSIST_DELAY_MS);
  }

  function flushPersistence() { return persistState(); }

  var accountFields = ['favorites', 'favoriteGroups', 'favoriteGroupOrder', 'favoriteItemOrders', 'recent', 'progress'];
  var accountSettings = ['hiddenCatalogCategories', 'hiddenFavoriteGroupIds', 'favoriteSystemGroupOverrides'];
  function accountSnapshot() {
    var value = {};
    accountFields.forEach(function (key) { value[key] = clone(state[key]); });
    value.settings = {};
    accountSettings.forEach(function (key) { if (state.settings[key] !== undefined) value.settings[key] = clone(state.settings[key]); });
    return value;
  }
  function selectAccount(key) {
    if (state.activeAccount === key) return false;
    if (state.activeAccount) state.accounts[state.activeAccount] = accountSnapshot();
    var saved = state.accounts[key] || defaults;
    accountFields.forEach(function (field) { state[field] = clone(saved[field] || defaults[field]); });
    accountSettings.forEach(function (field) { delete state.settings[field]; if (saved.settings && saved.settings[field] !== undefined) state.settings[field] = clone(saved.settings[field]); });
    state.activeAccount = key;
    return true;
  }
  function progressKey(type, id) { return String(type || 'movie') + ':' + String(id || ''); }

  window.OvelaStore = window.TVeeStore = {
    getState: function () { return clone(state); },
    saveCredentials: function (credentials) {
      var changed = selectAccount(Core.providerKey(credentials));
      if (JSON.stringify(credentials) !== JSON.stringify(state.credentials)) changed = true;
      state.credentials = credentials;
      if (changed) {
        markChanged();
        this.persist();
      }
    },
    selectDemo: function () {
      if (!selectAccount('demo')) return;
      markChanged();
      this.persist();
    },
    clearCredentials: function () {
      if (!state.credentials) return;
      if (state.activeAccount) state.accounts[state.activeAccount] = accountSnapshot();
      state.credentials = null;
      markChanged();
      this.persist();
    },
    updateSettings: function (settings) {
      var changed = Object.keys(settings || {}).some(function (key) { return state.settings[key] !== settings[key]; });
      if (!changed) return;
      state.settings = Object.assign({}, state.settings, settings);
      markChanged();
      this.persist();
    },
    isFavorite: function (type, id) {
      if (arguments.length === 1) { id = type; type = 'live'; }
      return state.favorites.some(function (item) {
        return Core.inferType(item) === type && Core.itemId(item, type) === String(id);
      });
    },
    toggleFavorite: function (item, type) {
      type = type || Core.inferType(item);
      var id = Core.itemId(item, type);
      var index = state.favorites.findIndex(function (favorite) {
        return Core.inferType(favorite) === type && Core.itemId(favorite, type) === id;
      });
      if (index >= 0) {
        state.favorites.splice(index, 1);
        var removedKey = favoriteKey(type, id);
        state.favoriteGroups.forEach(function (group) {
          group.itemKeys = group.itemKeys.filter(function (key) { return key !== removedKey; });
          group.updated_at = Date.now();
        });
        Object.keys(state.favoriteItemOrders || {}).forEach(function (groupId) {
          state.favoriteItemOrders[groupId] = state.favoriteItemOrders[groupId].filter(function (key) { return key !== removedKey; });
        });
      } else state.favorites.unshift(normalizedItem(item, type));
      state.favorites = state.favorites.slice(0, 250);
      markChanged();
      this.persist();
      return index < 0;
    },
    favoriteKey: favoriteKey,
    saveFavoriteGroup: function (group) {
      var value = normalizeFavoriteGroup(group);
      if (!value.id) value.id = 'group-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 7);
      value.updated_at = Date.now();
      var index = state.favoriteGroups.findIndex(function (entry) { return entry.id === value.id; });
      if (index >= 0) {
        value.created_at = state.favoriteGroups[index].created_at || value.created_at;
        state.favoriteGroups.splice(index, 1, value);
      } else {
        value.created_at = Date.now();
        state.favoriteGroups.push(value);
        state.favoriteGroups = state.favoriteGroups.slice(0, 24);
        state.favoriteGroupOrder = normalizeGroupOrder((state.favoriteGroupOrder || []).concat(value.id), state.favoriteGroups);
      }
      markChanged();
      this.persist();
      return clone(value);
    },
    deleteFavoriteGroup: function (id) {
      id = String(id);
      var before = state.favoriteGroups.length;
      state.favoriteGroups = state.favoriteGroups.filter(function (group) { return group.id !== id; });
      state.favoriteGroupOrder = normalizeGroupOrder((state.favoriteGroupOrder || []).filter(function (groupId) { return groupId !== id; }), state.favoriteGroups);
      if (state.favoriteItemOrders) delete state.favoriteItemOrders[id];
      var changed = state.favoriteGroups.length !== before;
      if (changed) {
        markChanged();
        this.persist();
      }
      return changed;
    },
    saveFavoriteGroupOrder: function (order) {
      var next = normalizeGroupOrder(order, state.favoriteGroups);
      var changed = JSON.stringify(next) !== JSON.stringify(state.favoriteGroupOrder);
      state.favoriteGroupOrder = next;
      if (changed) {
        markChanged();
        this.persist();
      }
      return clone(state.favoriteGroupOrder);
    },
    saveFavoriteItemOrder: function (groupId, order) {
      groupId = String(groupId || 'all');
      var valid = {};
      state.favorites.forEach(function (item) {
        var type = Core.inferType(item);
        valid[favoriteKey(type, Core.itemId(item, type))] = true;
      });
      var seen = {};
      var normalized = (Array.isArray(order) ? order : []).map(String).filter(function (key) {
        if (!valid[key] || seen[key]) return false;
        seen[key] = true;
        return true;
      }).slice(0, 250);
      state.favoriteItemOrders = state.favoriteItemOrders || {};
      var changed = JSON.stringify(normalized) !== JSON.stringify(state.favoriteItemOrders[groupId] || []);
      state.favoriteItemOrders[groupId] = normalized;
      if (changed) {
        markChanged();
        this.persist();
      }
      return clone(normalized);
    },
    addRecent: function (item, type) {
      type = type || Core.inferType(item);
      var id = Core.itemId(item, type);
      state.recent = state.recent.filter(function (recent) {
        return !(Core.inferType(recent) === type && Core.itemId(recent, type) === id);
      });
      state.recent.unshift(Object.assign(normalizedItem(item, type), { watched_at: Date.now() }));
      state.recent = state.recent.slice(0, 40);
      markChanged();
      this.persist();
    },
    getProgress: function (type, id) { return clone(state.progress[progressKey(type, id)] || null); },
    saveProgress: function (type, id, seconds, duration) {
      if (!id || !isFinite(seconds) || seconds < 0) return;
      var key = progressKey(type, id);
      if (duration && seconds >= duration - 30) {
        if (!Object.prototype.hasOwnProperty.call(state.progress, key)) return;
        delete state.progress[key];
        markChanged();
        this.persist();
        return;
      }
      var nextSeconds = Math.floor(seconds);
      var nextDuration = isFinite(duration) ? Math.floor(duration) : 0;
      var previous = state.progress[key];
      if (previous && previous.seconds === nextSeconds && previous.duration === nextDuration) return;
      state.progress[key] = {
        seconds: nextSeconds,
        duration: nextDuration,
        updated_at: Date.now()
      };
      scheduleProgressPersist();
    },
    clearProgress: function (type, id) {
      var key = progressKey(type, id);
      if (!Object.prototype.hasOwnProperty.call(state.progress, key)) return;
      delete state.progress[key];
      markChanged();
      this.persist();
    },
    clearHistory: function () {
      if (!state.recent.length && !Object.keys(state.progress).length) return;
      state.recent = [];
      state.progress = {};
      markChanged();
      this.persist();
    },
    refreshArtwork: function (items, type) {
      // Only favorites and recents can hold artwork from an earlier catalog
      // load. Index those few saved items instead of every item in the
      // category: a large provider returns tens of thousands of titles, and
      // building an entry per title made loading a category noticeably slower
      // on TV hardware even when there was nothing to refresh.
      var saved = {};
      var wanted = false;
      [state.favorites, state.recent].forEach(function (list) {
        list.forEach(function (item) {
          if (Core.inferType(item) !== type) return;
          var id = Core.itemId(item, type);
          if (!id) return;
          var copies = saved[id];
          if (!copies) { copies = saved[id] = []; }
          copies.push(item);
          wanted = true;
        });
      });
      if (!wanted) return;
      var changed = false;
      (items || []).forEach(function (fresh) {
        var copies = saved[Core.itemId(fresh, type)];
        if (!copies) return;
        copies.forEach(function (item) {
          ['stream_icon', 'cover', 'cover_big', 'movie_image'].forEach(function (key) {
            if (fresh[key] && item[key] !== fresh[key]) { item[key] = fresh[key]; changed = true; }
          });
        });
      });
      if (changed) {
        markChanged();
        this.persist();
      }
    },
    persist: persistState,
    flush: flushPersistence,
    reset: function () { state = clone(defaults); markChanged(); this.persist(); },
    inferType: Core.inferType,
    itemId: Core.itemId
  };

  if (typeof window.addEventListener === 'function') {
    window.addEventListener('pagehide', flushPersistence);
    window.addEventListener('beforeunload', flushPersistence);
    if (typeof document !== 'undefined' && typeof document.addEventListener === 'function') {
      document.addEventListener('visibilitychange', function () {
        if (document.visibilityState === 'hidden') flushPersistence();
      });
    }
  }
}());
