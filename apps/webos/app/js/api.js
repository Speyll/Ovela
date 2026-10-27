/* Generated from packages/core-web/src/api.js. Do not edit this copy. */
(function () {
  'use strict';

  var Core = window.OvelaCore;
  if (!Core) throw new Error('OvelaCore must load before api.js');

  function readResponse(response) {
    if (!response.ok) {
      var error = new Error('Provider returned HTTP ' + response.status + '.');
      error.status = response.status;
      throw error;
    }
    return response.json().catch(function (error) {
      if (error.name === 'AbortError') throw error;
      throw new Error('Provider returned an invalid response.');
    }).then(function (data) {
      if (data && !Array.isArray(data) && (data.error || (data.user_info && String(data.user_info.auth) === '0'))) {
        throw new Error('Provider rejected the request. Check your connection and account.');
      }
      return data;
    });
  }

  function withTimeout(url, options, timeoutMs) {
    var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    if (controller) options = Object.assign({}, options, { signal: controller.signal });
    var timer;
    var deadline = new Promise(function (_, reject) {
      timer = setTimeout(function () {
        var error = new Error('The provider took too long to respond.');
        error.name = 'AbortError';
        reject(error);
        if (controller) controller.abort();
      }, timeoutMs || 25000);
    });
    return Promise.race([fetch(url, options).then(readResponse), deadline]).then(function (data) {
      clearTimeout(timer); return data;
    }, function (error) { clearTimeout(timer); throw error; });
  }

  function withTextTimeout(url, options, timeoutMs) {
    var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    if (controller) options = Object.assign({}, options, { signal: controller.signal });
    var timer;
    var deadline = new Promise(function (_, reject) {
      timer = setTimeout(function () {
        var error = new Error('The provider took too long to respond.');
        error.name = 'AbortError';
        reject(error);
        if (controller) controller.abort();
      }, timeoutMs || 25000);
    });
    var read = function (response) {
      if (!response.ok) {
        var error = new Error('Provider returned HTTP ' + response.status + '.');
        error.status = response.status;
        throw error;
      }
      var declaredLength = Number(response.headers && response.headers.get('content-length') || 0);
      if (declaredLength > 32 * 1024 * 1024) throw new Error('Playlist is too large to load safely.');
      return response.text().then(function (text) {
        if (text.length > 32 * 1024 * 1024) throw new Error('Playlist is too large to load safely.');
        return text;
      });
    };
    return Promise.race([fetch(url, options).then(read), deadline]).then(function (data) {
      clearTimeout(timer); return data;
    }, function (error) { clearTimeout(timer); throw error; });
  }

  function normalizeServer(value, fallback) {
    var raw = String(value || '').trim();
    if (!raw) return '';
    if (!/^[a-z][a-z0-9+.-]*:\/\//i.test(raw)) {
      var protocol = 'http:';
      try { protocol = new URL(String(fallback || '')).protocol; } catch (_) {}
      raw = protocol + '//' + raw;
    }
    try {
      var parsed = new URL(raw);
      if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return '';
      parsed.hash = '';
      parsed.search = '';
      return Core.trimSlash(parsed.toString());
    } catch (_) { return ''; }
  }

  function XtreamApi(credentials, settings) {
    this.server = normalizeServer(credentials.server, '') || Core.trimSlash(credentials.server);
    this.username = String(credentials.username || '').trim();
    this.password = String(credentials.password || '');
    var seen = {};
    seen[this.server] = true;
    this.alternateServers = (Array.isArray(credentials.alternateServers) ? credentials.alternateServers : [])
      .map(function (value) { return normalizeServer(value, this.server); }, this)
      .filter(function (value) { if (!value || seen[value]) return false; seen[value] = true; return true; });
    this.servers = [this.server].concat(this.alternateServers);
    this.proxyToken = String(settings && settings.apiProxyToken || '');
    this.proxy = Core.trimSlash(settings && settings.apiProxy);
    this.streamFormat = (settings && settings.streamFormat) || 'auto';
  }

  XtreamApi.prototype.streamUrlFor = function (server, type, streamId, extension) {
    return Core.buildStreamUrl({
      server: server,
      username: this.username,
      password: this.password
    }, type, streamId, extension);
  };

  XtreamApi.prototype.request = function (action, extras, requestOptions) {
    var params = Object.assign({}, extras || {});
    requestOptions = requestOptions || {};
    if (action) params.action = action;

    if (this.proxy) {
      var headers = { 'Content-Type': 'application/json' };
      if (this.proxyToken) headers.Authorization = 'Bearer ' + this.proxyToken;
      return withTimeout(this.proxy + '/api/xtream', {
        method: 'POST',
        headers: headers,
        body: JSON.stringify({
          server: this.server,
          username: this.username,
          password: this.password,
          params: params
        })
      }, requestOptions.timeout || 45000);
    }

    var query = new URLSearchParams();
    query.set('username', this.username);
    query.set('password', this.password);
    Object.keys(params).forEach(function (key) {
      if (params[key] !== undefined && params[key] !== null) query.set(key, params[key]);
    });
    return withTimeout(this.server + '/player_api.php?' + query.toString(), { method: 'GET' }, requestOptions.timeout || 45000)
      .catch(function (error) {
        if (error && error.name === 'AbortError') error.message = 'The provider took too long to respond.';
        else if (String(error && error.message).toLowerCase().indexOf('fetch') >= 0) {
          error.message = 'The provider blocked the TV app request. Configure the optional Ovela API Bridge in Settings.';
        }
        throw error;
      });
  };

  XtreamApi.prototype.handleResponse = readResponse;

  XtreamApi.prototype.authenticate = function () {
    return this.request(null, null, { timeout: 25000 }).then(function (data) {
      if (!data || !data.user_info || String(data.user_info.auth) !== '1') {
        throw new Error((data && data.user_info && data.user_info.message) || 'Login was rejected by the provider.');
      }
      return data;
    });
  };

  XtreamApi.prototype.getLiveCategories = function () {
    return this.request('get_live_categories').then(function (data) { return Core.normalizeCollection(data, ['live_categories']); });
  };

  XtreamApi.prototype.getLiveStreams = function (categoryId) {
    return this.request('get_live_streams', { category_id: categoryId }, { timeout: 60000 }).then(function (data) {
      return Core.normalizeCollection(data, ['live_streams']);
    });
  };

  XtreamApi.prototype.getShortEpg = function (streamId) {
    return this.request('get_short_epg', { stream_id: streamId, limit: 4 }, { timeout: 18000 }).then(function (data) {
      var listings = data && Array.isArray(data.epg_listings) ? data.epg_listings : [];
      return listings.map(function (item) {
        return Object.assign({}, item, {
          title: Core.decodeMaybeBase64(item.title),
          description: Core.decodeMaybeBase64(item.description)
        });
      });
    });
  };

  XtreamApi.prototype.getVodCategories = function () {
    return this.request('get_vod_categories').then(function (data) { return Core.normalizeCollection(data, ['vod_categories']); });
  };

  XtreamApi.prototype.getVodStreams = function (categoryId) {
    return this.request('get_vod_streams', { category_id: categoryId }, { timeout: 60000 }).then(function (data) {
      return Core.normalizeCollection(data, ['vod_streams']);
    });
  };

  XtreamApi.prototype.getVodInfo = function (vodId) {
    return this.request('get_vod_info', { vod_id: vodId }, { timeout: 30000 }).then(function (data) { return data || {}; });
  };

  XtreamApi.prototype.getSeriesCategories = function () {
    return this.request('get_series_categories').then(function (data) { return Core.normalizeCollection(data, ['series_categories']); });
  };

  XtreamApi.prototype.getSeries = function (categoryId) {
    var self = this;
    var extras = categoryId && categoryId !== 'all' ? { category_id: categoryId } : {};
    return self.request('get_series', extras, { timeout: 60000 }).catch(function (error) {
      if ([400, 404, 405, 501].indexOf(error.status) < 0) throw error;
      return self.request('get_series_streams', extras, { timeout: 60000 });
    }).then(function (data) { return Core.normalizeCollection(data, ['series']); });
  };

  XtreamApi.prototype.discoverSeries = function (knownCategories) {
    var self = this;
    knownCategories = Array.isArray(knownCategories) ? knownCategories : [];
    if (knownCategories.length) return Promise.resolve({ categories: knownCategories, items: null });
    return this.getSeries('all').then(function (items) {
      return { categories: Core.deriveCategories(items, 'Series category'), items: items };
    });
  };

  XtreamApi.prototype.getSeriesInfo = function (seriesId) {
    return this.request('get_series_info', { series_id: seriesId }, { timeout: 45000 }).then(function (data) { return data || {}; });
  };

  XtreamApi.prototype.getStreamUrl = function (streamId, format) {
    format = format || (this.streamFormat === 'auto' ? 'm3u8' : this.streamFormat) || 'm3u8';
    return this.streamUrlFor(this.server, 'live', streamId, format);
  };

  XtreamApi.prototype.getLiveCandidates = function (streamId, allowedFormats) {
    var preferred = this.streamFormat || 'auto';
    var formats = preferred === 'auto' ? ['m3u8', 'ts'] : [preferred, preferred === 'm3u8' ? 'ts' : 'm3u8'];
    if (Array.isArray(allowedFormats) && allowedFormats.length) {
      var allowed = allowedFormats.map(function (item) { return String(item).toLowerCase(); });
      formats = formats.filter(function (format) { return allowed.indexOf(format) >= 0; });
      if (!formats.length) formats = allowed.filter(function (format) { return format === 'm3u8' || format === 'ts'; });
    }
    var seenFormats = {};
    formats = formats.filter(function (format) {
      if (seenFormats[format]) return false;
      seenFormats[format] = true;
      return true;
    });
    var seenUrls = {};
    var candidates = [];
    this.servers.forEach(function (server, serverIndex) {
      formats.forEach(function (format) {
        var url = this.streamUrlFor(server, 'live', streamId, format);
        if (seenUrls[url]) return;
        seenUrls[url] = true;
        candidates.push({
          url: url,
          label: (format === 'm3u8' ? 'HLS' : 'MPEG-TS') + (serverIndex ? ' backup' : ''),
          format: format,
          mime: format === 'm3u8' ? 'application/x-mpegURL' : 'video/mp2t'
        });
      }, this);
    }, this);
    return candidates;
  };

  XtreamApi.prototype.getAlternateStreamUrl = function (streamId) {
    return this.getStreamUrl(streamId, this.streamFormat === 'm3u8' ? 'ts' : 'm3u8');
  };

  XtreamApi.prototype.getMovieUrl = function (streamId, extension) {
    return this.streamUrlFor(this.server, 'movie', streamId, Core.safeExtension(extension, 'mp4'));
  };

  XtreamApi.prototype.getSeriesStreamUrl = function (streamId, extension) {
    return this.streamUrlFor(this.server, 'episode', streamId, Core.safeExtension(extension, 'mp4'));
  };

  XtreamApi.prototype.getMediaCandidates = function (type, streamId, extension) {
    var primary = Core.safeExtension(extension, 'mp4');
    var extensions = [primary].concat(primary === 'mp4' ? ['mkv'] : ['mp4']).filter(function (ext, index, list) {
      return list.indexOf(ext) === index;
    });
    var seen = {};
    var candidates = [];
    this.servers.forEach(function (server, serverIndex) {
      extensions.forEach(function (ext) {
        var url = this.streamUrlFor(server, type, streamId, ext);
        if (seen[url]) return;
        seen[url] = true;
        candidates.push({
          url: url,
          label: ext.toUpperCase() + (serverIndex ? ' backup' : ''),
          format: ext,
          mime: ext === 'mp4' ? 'video/mp4' : ''
        });
      }, this);
    }, this);
    return candidates;
  };

  XtreamApi.prototype.getAlternateMediaUrl = function (type, streamId, extension) {
    var candidates = this.getMediaCandidates(type, streamId, extension);
    return candidates.length > 1 ? candidates[1].url : candidates[0].url;
  };

  function M3uApi(credentials, settings) {
    this.playlistUrl = String(credentials && (credentials.playlistUrl || credentials.playlist_url) || '').trim();
    this.alternateServers = credentials && Array.isArray(credentials.alternateServers) ? credentials.alternateServers : [];
    this.proxy = Core.trimSlash(settings && settings.apiProxy);
    this.proxyToken = String(settings && settings.apiProxyToken || '');
    this.entries = [];
    this.categories = [];
    this.parsed = null;
  }

  M3uApi.prototype.fetchPlaylist = function () {
    if (this.parsed) return Promise.resolve(this.parsed);
    if (!this.playlistUrl) return Promise.reject(new Error('Enter an M3U playlist URL.'));
    var request;
    if (this.proxy) {
      var headers = { 'Content-Type': 'application/json' };
      if (this.proxyToken) headers.Authorization = 'Bearer ' + this.proxyToken;
      request = withTextTimeout(this.proxy + '/api/m3u', {
        method: 'POST',
        headers: headers,
        body: JSON.stringify({ url: this.playlistUrl })
      }, 45000);
    } else {
      request = withTextTimeout(this.playlistUrl, { method: 'GET' }, 45000).catch(function (error) {
        if (error && error.name === 'AbortError') error.message = 'The playlist took too long to respond.';
        else if (String(error && error.message).toLowerCase().indexOf('fetch') >= 0) {
          error.message = 'The playlist could not be loaded from this TV. Check CORS access or configure the optional API Bridge.';
        }
        throw error;
      });
    }
    var self = this;
    return request.then(function (text) {
      var parsed = Core.parseM3u(text);
      if (!parsed.entries.length) {
        var error = new Error('The playlist did not contain any playable entries.');
        error.playlistWarnings = parsed.warnings;
        throw error;
      }
      self.parsed = parsed;
      self.entries = parsed.entries;
      self.categories = parsed.categories;
      return parsed;
    });
  };

  M3uApi.prototype.authenticate = function () {
    return this.fetchPlaylist().then(function (parsed) {
      return {
        username: 'M3U playlist',
        status: parsed.entries.length + ' entries',
        expiration: null,
        epgUrls: parsed.epgUrls,
        warnings: parsed.warnings
      };
    });
  };

  M3uApi.prototype.categoriesFor = function (type) {
    return Core.m3uCategories(this.entries, type);
  };

  M3uApi.prototype.itemsFor = function (type, categoryId) {
    return this.entries.filter(function (entry) {
      return entry.content_type === type && (!categoryId || categoryId === 'all' || entry.category_id === categoryId);
    }).map(function (entry) { return Object.assign({}, entry); });
  };

  M3uApi.prototype.findItem = function (value) {
    if (value && typeof value === 'object') return value;
    var id = String(value || '');
    return this.entries.find(function (entry) {
      return String(entry.stream_id) === id || String(entry.id) === id;
    }) || null;
  };

  M3uApi.prototype.getLiveCategories = function () { return Promise.resolve(this.categoriesFor('live')); };
  M3uApi.prototype.getVodCategories = function () { return Promise.resolve(this.categoriesFor('movie')); };
  M3uApi.prototype.getSeriesCategories = function () { return Promise.resolve([]); };
  M3uApi.prototype.getLiveStreams = function (categoryId) { return Promise.resolve(this.itemsFor('live', categoryId)); };
  M3uApi.prototype.getVodStreams = function (categoryId) { return Promise.resolve(this.itemsFor('movie', categoryId)); };
  M3uApi.prototype.getSeries = function () { return Promise.resolve([]); };
  M3uApi.prototype.discoverSeries = function () { return Promise.resolve({ categories: [], items: [] }); };
  M3uApi.prototype.getShortEpg = function () { return Promise.resolve([]); };
  M3uApi.prototype.getVodInfo = function (id) { return Promise.resolve(this.findItem(id) || {}); };
  M3uApi.prototype.getSeriesInfo = function () { return Promise.resolve({}); };
  M3uApi.prototype.getLiveCandidates = function (item) {
    return Core.m3uCandidates(this.findItem(item), this.alternateServers);
  };
  M3uApi.prototype.getMediaCandidates = function (type, id) {
    return Core.m3uCandidates(this.findItem(id), this.alternateServers);
  };

  XtreamApi.M3uApi = M3uApi;
  window.OvelaM3uApi = M3uApi;
  XtreamApi.normalizeCollection = Core.normalizeCollection;
  XtreamApi.deriveCategories = Core.deriveCategories;
  window.OvelaApi = XtreamApi;
}());
