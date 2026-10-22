/* Generated from packages/core-web/src/core.js. Do not edit this copy. */
(function (root, factory) {
  'use strict';
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.XtreamlyCore = api;
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  function trimSlash(value) {
    return String(value || '').trim().replace(/\/+$/, '');
  }

  function decodeMaybeBase64(value) {
    if (!value || typeof value !== 'string') return value || '';
    try {
      var decoded = atob(value);
      if (/^[\x09\x0A\x0D\x20-\x7E\u00A0-\uFFFF]*$/.test(decoded)) {
        try { return decodeURIComponent(escape(decoded)); } catch (error) { return decoded; }
      }
    } catch (error) { /* plain text */ }
    return value;
  }

  function safeExtension(value, fallback) {
    var extension = String(value || fallback || 'mp4').toLowerCase().replace(/[^a-z0-9]/g, '');
    return extension || fallback || 'mp4';
  }

  function objectValues(value) {
    return Object.keys(value || {}).map(function (key) { return value[key]; });
  }

  function normalizeCollection(data, preferredKeys) {
    if (Array.isArray(data)) return data;
    if (!data || typeof data !== 'object') return [];
    preferredKeys = preferredKeys || [];
    var keys = preferredKeys.concat(['data', 'results', 'items', 'streams', 'channels', 'movies', 'series', 'categories']);
    for (var index = 0; index < keys.length; index += 1) {
      if (Array.isArray(data[keys[index]])) return data[keys[index]];
    }
    return objectValues(data).filter(function (item) {
      return item && typeof item === 'object' && !Array.isArray(item);
    });
  }

  function deriveCategories(items, fallbackLabel) {
    var seen = {};
    var output = [];
    (items || []).forEach(function (item) {
      var id = item && (item.category_id !== undefined ? item.category_id : item.categoryId);
      if (id === undefined || id === null || id === '') return;
      var key = String(id);
      if (seen[key]) return;
      seen[key] = true;
      output.push({
        category_id: key,
        category_name: String(item.category_name || item.category || item.genre || (fallbackLabel + ' ' + key))
      });
    });
    return output;
  }

  function inferType(item) {
    if (!item) return 'live';
    if (item.content_type) return item.content_type;
    if (item.series_id !== undefined) return 'series';
    if (item.episode_num !== undefined || item.episode_id !== undefined) return 'episode';
    if (item.container_extension && item.stream_type !== 'live') return 'movie';
    return 'live';
  }

  function itemId(item, type) {
    type = type || inferType(item);
    if (!item) return '';
    if (type === 'series') return String(item.series_id !== undefined ? item.series_id : item.id || '');
    if (type === 'episode') return String(item.id !== undefined ? item.id : item.episode_id || item.stream_id || '');
    return String(item.stream_id !== undefined ? item.stream_id : item.id || '');
  }

  function hash32(value) {
    var hash = 2166136261;
    var text = String(value || '');
    for (var index = 0; index < text.length; index += 1) {
      hash ^= text.charCodeAt(index);
      hash = Math.imul ? Math.imul(hash, 16777619) : (hash * 16777619);
    }
    return (hash >>> 0).toString(16);
  }

  function parseAttributes(value) {
    var attributes = {};
    var text = String(value || '');
    var pattern = /([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"|([A-Za-z0-9_-]+)\s*=\s*'([^']*)'|([A-Za-z0-9_-]+)=([^\s]+)/g;
    var match;
    while ((match = pattern.exec(text))) {
      var key = match[1] || match[3] || match[5];
      var item = match[2] !== undefined ? match[2] : match[4] !== undefined ? match[4] : match[6];
      if (key) attributes[key.toLowerCase()] = item || '';
    }
    return attributes;
  }

  function mediaExtension(url) {
    try {
      var parsed = new URL(String(url));
      var match = parsed.pathname.toLowerCase().match(/\.([a-z0-9]{2,5})$/);
      return match ? match[1] : '';
    } catch (_) {
      var clean = String(url || '').split('?')[0].split('#')[0];
      var fallback = clean.toLowerCase().match(/\.([a-z0-9]{2,5})$/);
      return fallback ? fallback[1] : '';
    }
  }

  function m3uContentType(url, group) {
    var extension = mediaExtension(url);
    if (['mp4', 'mkv', 'avi', 'mov', 'wmv', 'flv', 'm4v', 'webm', 'mpg', 'mpeg'].indexOf(extension) >= 0) return 'movie';
    if (/\b(vod|movies?|films?|cinema)\b/i.test(String(group || ''))) return 'movie';
    return 'live';
  }

  function m3uCategory(group) {
    var name = String(group || '').trim() || 'Ungrouped';
    return { id: 'm3u-group-' + hash32(name.toLowerCase()), name: name };
  }

  function m3uTitleSeparator(value) {
    var quote = '';
    for (var index = 0; index < value.length; index += 1) {
      var character = value.charAt(index);
      if (quote) {
        if (character === quote) quote = '';
      } else if (character === '"' || character === "'") {
        quote = character;
      } else if (character === ',') {
        return index;
      }
    }
    return -1;
  }

  function parseM3u(text) {
    var source = String(text || '').replace(/^\uFEFF/, '');
    var lines = source.split(/\r?\n/);
    var entries = [];
    var warnings = [];
    var epgUrls = [];
    var pending = null;
    var pendingGroup = '';
    var sawHeader = false;
    var seen = {};

    function addEntry(pendingEntry, url) {
      url = String(url || '').trim();
      if (!url || /^#/.test(url)) return;
      var name = String(pendingEntry && (pendingEntry.name || pendingEntry.attributes['tvg-name']) || '').trim();
      if (!name) {
        try { name = decodeURIComponent((new URL(url)).pathname.split('/').filter(Boolean).pop() || 'Untitled channel'); }
        catch (_) { name = 'Untitled channel'; }
      }
      var attributes = pendingEntry && pendingEntry.attributes || {};
      var group = String(attributes['group-title'] || (pendingEntry && pendingEntry.group) || pendingGroup || '').trim();
      var category = m3uCategory(group);
      var type = m3uContentType(url, group);
      var identity = url + '\n' + name + '\n' + group;
      var id = 'm3u-' + hash32(identity);
      if (seen[id]) {
        warnings.push('Duplicate playlist entry skipped: ' + name);
        return;
      }
      seen[id] = true;
      var extension = mediaExtension(url);
      entries.push({
        stream_id: id,
        id: id,
        name: name,
        title: name,
        content_type: type,
        category_id: category.id,
        category_name: category.name,
        group_title: group,
        stream_url: url,
        stream_icon: String(attributes['tvg-logo'] || '').trim(),
        tvg_id: String(attributes['tvg-id'] || '').trim(),
        tvg_name: String(attributes['tvg-name'] || '').trim(),
        channel_number: String(attributes['tvg-chno'] || attributes['channel-number'] || '').trim(),
        container_extension: type === 'movie' && extension ? extension : null
      });
    }

    for (var index = 0; index < lines.length; index += 1) {
      var line = lines[index].trim();
      if (!line) continue;
      if (/^#EXTM3U(?:\s|$)/i.test(line)) {
        sawHeader = true;
        var header = parseAttributes(line.replace(/^#EXTM3U/i, ''));
        var epg = header['url-tvg'] || header['x-tvg-url'] || '';
        epg.split(',').map(function (value) { return value.trim(); }).filter(Boolean).forEach(function (value) {
          if (epgUrls.indexOf(value) < 0) epgUrls.push(value);
        });
        continue;
      }
      if (/^#EXTINF:/i.test(line)) {
        if (pending) warnings.push('Playlist entry without a URL skipped: ' + (pending.name || 'unknown'));
        var body = line.replace(/^#EXTINF:/i, '');
        var comma = m3uTitleSeparator(body);
        var metadata = comma >= 0 ? body.slice(0, comma) : body;
        var displayName = comma >= 0 ? body.slice(comma + 1).trim() : '';
        var durationText = metadata.match(/^\s*(-?\d+(?:\.\d+)?)/);
        pending = {
          name: displayName,
          duration: durationText ? Number(durationText[1]) : -1,
          attributes: parseAttributes(metadata),
          group: ''
        };
        continue;
      }
      if (/^#EXTGRP:/i.test(line)) {
        pendingGroup = line.slice(8).trim();
        if (pending) pending.group = pendingGroup;
        continue;
      }
      if (/^#EXT-X-|^#EXTM3U|^#PLAYLIST:|^#EXTVLCOPT:|^#KODIPROP:/i.test(line)) continue;
      if (/^#/.test(line)) continue;
      if (pending) {
        var completed = pending;
        completed.group = completed.group || pendingGroup;
        addEntry(completed, line);
        pending = null;
        pendingGroup = '';
      } else {
        addEntry(null, line);
      }
    }
    if (pending) warnings.push('Playlist entry without a URL skipped: ' + (pending.name || 'unknown'));
    if (!sawHeader && lines.length) warnings.push('Playlist is missing the #EXTM3U header.');
    return { entries: entries, categories: m3uCategories(entries), epgUrls: epgUrls, warnings: warnings };
  }

  function m3uCategories(entries, type) {
    var seen = {};
    var result = [];
    (entries || []).forEach(function (entry) {
      if (!entry || (type && entry.content_type !== type)) return;
      var id = String(entry.category_id || '');
      if (!id || seen[id]) return;
      seen[id] = true;
      result.push({ category_id: id, category_name: String(entry.category_name || 'Ungrouped') });
    });
    return result;
  }

  function normalizeAlternateHost(value) {
    var host = String(value || '').trim().replace(/\/+$/, '');
    if (!host) return '';
    if (!/^[a-z][a-z0-9+.-]*:\/\//i.test(host)) host = 'https://' + host;
    try {
      var parsed = new URL(host);
      return parsed.origin;
    } catch (_) { return ''; }
  }

  function rewriteM3uHost(url, host) {
    var alternative = normalizeAlternateHost(host);
    if (!alternative) return '';
    try {
      var original = new URL(String(url));
      var replacement = new URL(alternative);
      return replacement.protocol + '//' + replacement.host + original.pathname + original.search + original.hash;
    } catch (_) { return ''; }
  }

  function mimeForUrl(url) {
    var extension = mediaExtension(url);
    if (extension === 'm3u8') return 'application/x-mpegURL';
    if (extension === 'ts') return 'video/mp2t';
    if (extension === 'mp4' || extension === 'm4v') return 'video/mp4';
    if (extension === 'webm') return 'video/webm';
    return '';
  }

  function m3uCandidates(item, alternateHosts) {
    if (!item) return [];
    var url = String(item.stream_url || item.streamUrl || item.url || '').trim();
    if (!url) return [];
    var candidates = [{ url: url, label: item.content_type === 'movie' ? 'Direct file' : 'Playlist stream', format: mediaExtension(url) || 'direct', mime: mimeForUrl(url) }];
    var seen = {};
    seen[url] = true;
    (alternateHosts || []).forEach(function (host, index) {
      var rewritten = rewriteM3uHost(url, host);
      if (!rewritten || seen[rewritten]) return;
      seen[rewritten] = true;
      candidates.push({ url: rewritten, label: 'Alternate host ' + (index + 1), format: mediaExtension(rewritten) || 'direct', mime: mimeForUrl(rewritten) });
    });
    return candidates;
  }

  function recoveryDecision(state, failure) {
    state = state || {};
    failure = failure || {};
    var kind = String(failure.kind || 'transient');
    var permanent = ['auth', 'forbidden', 'not_found', 'unsupported'].indexOf(kind) >= 0;
    var candidateIndex = Number(state.candidateIndex || 0);
    var candidateCount = Number(state.candidateCount || 0);
    var attempts = Number(state.attempts || 0);
    var maxAttempts = Math.max(1, Number(state.maxAttempts || 2));
    if (candidateIndex + 1 < candidateCount) {
      return { action: 'next', reason: 'candidate', delayMs: permanent ? 0 : Math.min(250 * Math.pow(2, attempts), 2000) };
    }
    if (!permanent && attempts < maxAttempts) {
      return { action: 'retry', reason: 'transient', delayMs: Math.min(250 * Math.pow(2, attempts), 2000) };
    }
    return { action: 'fail', reason: permanent ? 'permanent' : 'exhausted' };
  }

  function buildStreamUrl(credentials, type, streamId, extension) {
    var server = trimSlash(credentials && credentials.server);
    var username = encodeURIComponent(String(credentials && credentials.username || '').trim());
    var password = encodeURIComponent(String(credentials && credentials.password || ''));
    var segment = type === 'episode' ? 'series' : type === 'movie' ? 'movie' : 'live';
    return server + '/' + segment + '/' + username + '/' + password + '/' + streamId + '.' + safeExtension(extension, type === 'live' ? 'm3u8' : 'mp4');
  }

  return {
    providerKey: function (credentials) {
      if (!credentials) return '';
      var playlistUrl = String(credentials.playlistUrl || credentials.playlist_url || '').trim();
      if (credentials.sourceType === 'm3u' || credentials.source_type === 'm3u' || playlistUrl) {
        try { var playlist = new URL(playlistUrl); playlist.hash = ''; playlistUrl = playlist.toString(); } catch (_) {}
        return JSON.stringify(['m3u', playlistUrl]);
      }
      var server = trimSlash(credentials.server);
      try { var url = new URL(server); url.hostname = url.hostname.toLowerCase(); server = trimSlash(url.toString()); } catch (_) {}
      return JSON.stringify([server, String(credentials.username || '').trim()]);
    },
    trimSlash: trimSlash,
    decodeMaybeBase64: decodeMaybeBase64,
    safeExtension: safeExtension,
    normalizeCollection: normalizeCollection,
    deriveCategories: deriveCategories,
    inferType: inferType,
    itemId: itemId,
    buildStreamUrl: buildStreamUrl,
    hash32: hash32,
    parseAttributes: parseAttributes,
    mediaExtension: mediaExtension,
    m3uContentType: m3uContentType,
    parseM3u: parseM3u,
    m3uCategories: m3uCategories,
    rewriteM3uHost: rewriteM3uHost,
    m3uCandidates: m3uCandidates,
    recoveryDecision: recoveryDecision
  };
}));
