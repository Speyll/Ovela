/* Generated from packages/core-web/src/api.js. Do not edit this copy. */
(function () {
  'use strict';

  var Core = window.XtreamlyCore;
  if (!Core) throw new Error('XtreamlyCore must load before api.js');

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

  function XtreamApi(credentials, settings) {
    this.server = Core.trimSlash(credentials.server);
    this.username = String(credentials.username || '').trim();
    this.password = String(credentials.password || '');
    this.proxyToken = String(settings && settings.apiProxyToken || '');
    this.proxy = Core.trimSlash(settings && settings.apiProxy);
    this.streamFormat = (settings && settings.streamFormat) || 'auto';
  }

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
          error.message = 'The provider blocked the TV app request. Configure the optional XtreamlyTV API Bridge in Settings.';
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
    return Core.buildStreamUrl(this, 'live', streamId, format);
  };

  XtreamApi.prototype.getLiveCandidates = function (streamId, allowedFormats) {
    var preferred = this.streamFormat || 'auto';
    var formats = preferred === 'auto' ? ['m3u8', 'ts'] : [preferred, preferred === 'm3u8' ? 'ts' : 'm3u8'];
    if (Array.isArray(allowedFormats) && allowedFormats.length) {
      var allowed = allowedFormats.map(function (item) { return String(item).toLowerCase(); });
      formats = formats.filter(function (format) { return allowed.indexOf(format) >= 0; });
      if (!formats.length) formats = allowed.filter(function (format) { return format === 'm3u8' || format === 'ts'; });
    }
    var seen = {};
    return formats.filter(function (format) {
      if (seen[format]) return false;
      seen[format] = true;
      return true;
    }).map(function (format) {
      return {
        url: this.getStreamUrl(streamId, format),
        label: format === 'm3u8' ? 'HLS' : 'MPEG-TS',
        format: format,
        mime: format === 'm3u8' ? 'application/x-mpegURL' : 'video/mp2t'
      };
    }, this);
  };

  XtreamApi.prototype.getAlternateStreamUrl = function (streamId) {
    return this.getStreamUrl(streamId, this.streamFormat === 'm3u8' ? 'ts' : 'm3u8');
  };

  XtreamApi.prototype.getMovieUrl = function (streamId, extension) {
    return Core.buildStreamUrl(this, 'movie', streamId, Core.safeExtension(extension, 'mp4'));
  };

  XtreamApi.prototype.getSeriesStreamUrl = function (streamId, extension) {
    return Core.buildStreamUrl(this, 'episode', streamId, Core.safeExtension(extension, 'mp4'));
  };

  XtreamApi.prototype.getMediaCandidates = function (type, streamId, extension) {
    var primary = Core.safeExtension(extension, 'mp4');
    var alternates = primary === 'mp4' ? ['mkv'] : ['mp4'];
    return [primary].concat(alternates).map(function (ext) {
      return {
        url: type === 'episode' ? this.getSeriesStreamUrl(streamId, ext) : this.getMovieUrl(streamId, ext),
        label: ext.toUpperCase(),
        format: ext,
        mime: ext === 'mp4' ? 'video/mp4' : ''
      };
    }, this);
  };

  XtreamApi.prototype.getAlternateMediaUrl = function (type, streamId, extension) {
    var candidates = this.getMediaCandidates(type, streamId, extension);
    return candidates.length > 1 ? candidates[1].url : candidates[0].url;
  };

  XtreamApi.normalizeCollection = Core.normalizeCollection;
  XtreamApi.deriveCategories = Core.deriveCategories;
  window.XtreamlyTVApi = window.TVeeApi = XtreamApi;
}());
