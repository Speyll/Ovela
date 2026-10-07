/* XtreamlyTV v0.7.0 webOS browsing and interaction layer.
 *
 * Owns the v0.7 living-room navigation shell, category wheel, guide-first
 * Live TV surface, Movies/Series hero + poster rail, scoped search overlay,
 * Favorites presentation, and remote-first focus routing.
 */
(function () {
  'use strict';

  var App = window.XtreamlyTVApp || window.TVeeApp;
  var Store = window.XtreamlyTVStore || window.TVeeStore;
  if (!App || !Store || App.__v07UiRefreshInstalled) return;

  var KEY = { LEFT:37, UP:38, RIGHT:39, DOWN:40, ENTER:13, BACK:461, ESC:27, BACKSPACE:8, CH_UP:427, CH_DOWN:428 };
  var V = {
    version:'0.7.0',
    mediaIndex:{ movies:0, series:0 },
    selectedKey:{ movies:'', series:'', live:'' },
    liveIndex:0,
    heroGeneration:0,
    heroTimer:null,
    backdropGeneration:0,
    backdropTimer:null,
    adjacentBackdropTimer:null,
    backdropActiveLayer:'a',
    backdropUrl:{ movies:'', series:'' },
    artworkPreload:{},
    detailCache:{},
    epgCache:{},
    epgGeneration:0,
    liveWindowSize:5,
    mediaWindowSize:6,
    categoryWheel:null,
    timelineTimer:null,
    searchOpen:false,
    lastLivePageAt:0,
    searchViewportSync:null,
    movieHeroOriginIndex:null,
    mediaRailWindow:{ movies:null, series:null },
    richPrefetchTimer:null,
    richPrefetchPromises:{},
    seriesDetailBrowseSnapshot:null,
    persistentMediaCache:null,
    persistentMediaCacheScope:'',
    persistentMediaCacheWarmed:false,
    posterPrefetchTimers:{ movies:[], series:[] },
    artworkImageCache:{},
    artworkImageOrder:[]
  };

  function boundCache(cache, limit) {
    var keys = Object.keys(cache);
    keys.slice(0, Math.max(0, keys.length - limit)).forEach(function (key) { delete cache[key]; });
  }

  App.resetMetadata = function (clearPersistent) {
    V.heroGeneration += 1;
    V.epgGeneration += 1;
    V.backdropGeneration += 1;
    [V.heroTimer, V.backdropTimer, V.adjacentBackdropTimer, V.richPrefetchTimer].forEach(clearTimeout);
    Object.keys(V.posterPrefetchTimers).forEach(function (kind) { V.posterPrefetchTimers[kind].forEach(clearTimeout); V.posterPrefetchTimers[kind] = []; });
    stopTimeline();
    V.detailCache = {};
    V.epgCache = {};
    V.epgPending = {};
    V.richPrefetchPromises = {};
    V.artworkPreload = {};
    V.artworkImageCache = {};
    V.artworkImageOrder = [];
    V.persistentMediaCache = null;
    V.persistentMediaCacheScope = '';
    V.persistentMediaCacheWarmed = false;
    if (clearPersistent) { try { localStorage.removeItem(MEDIA_CACHE_STORAGE_KEY); } catch (_) {} }
  };

  var legacy = {
    renderCatalog:App.renderCatalog,
    updateCatalogGrid:App.updateCatalogGrid,
    renderHome:App.renderHome,
    renderSettings:App.renderSettings,
    renderSeriesDetail:App.renderSeriesDetail,
    openSeriesDetail:App.openSeriesDetail,
    renderFavorites:App.renderFavorites,
    favoriteItemsForGroup:App.favoriteItemsForGroup,
    beginFavoriteItemReorder:App.beginFavoriteItemReorder,
    toggleFocusedFavorite:App.toggleFocusedFavorite,
    showCatalogLoading:App.showCatalogLoading,
    showCatalogError:App.showCatalogError
  };

  function esc(value) {
    return String(value == null ? '' : value).replace(/[&<>'"]/g, function (char) {
      return ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', "'":'&#039;', '"':'&quot;' })[char];
    });
  }

  function searchIconHtml() {
    /* Inline SVG avoids unsupported Unicode search glyphs rendering as boxes
       on older webOS Chromium builds. */
    return '<svg class="v07-search-svg" viewBox="0 0 24 24" aria-hidden="true" focusable="false">' +
      '<circle cx="10.5" cy="10.5" r="6.5"></circle>' +
      '<path d="M15.5 15.5 L21 21"></path></svg>';
  }

  function heroActionIconHtml(type) {
    if (type === 'series') {
      return '<svg class="v07-action-icon v07-action-icon-tv" viewBox="0 0 24 24" aria-hidden="true" focusable="false">' +
        '<path d="M8 3l4 3 4-3"></path>' +
        '<rect x="3" y="6" width="18" height="14" rx="2.5"></rect>' +
        '<path d="M8 20h8"></path></svg>';
    }
    return '<svg class="v07-action-icon v07-action-icon-play" viewBox="0 0 24 24" aria-hidden="true" focusable="false">' +
      '<path d="M8 5.5v13l10-6.5z"></path></svg>';
  }

  function mod(value, count) {
    if (!count) return 0;
    return ((value % count) + count) % count;
  }

  function stopTimeline() {
    if (V.timelineTimer) {
      clearInterval(V.timelineTimer);
      V.timelineTimer = null;
    }
  }

  function typeForKind(kind) {
    return kind === 'live' ? 'live' : (kind === 'movies' ? 'movie' : 'series');
  }

  function idOf(item, type) {
    if (!item) return '';
    if (Store.itemId) return String(Store.itemId(item, type));
    if (type === 'series') return String(item.series_id || item.id || '');
    if (type === 'movie' || type === 'live') return String(item.stream_id || item.id || '');
    return String(item.id || item.stream_id || '');
  }

  function itemKey(item, type) {
    return type + ':' + idOf(item, type);
  }

  function titleOf(item) {
    return String(item && (item.name || item.title || item.series_name) || 'Untitled');
  }

  function artworkUrl(value) {
    var url = String(value || '').replace(/^\s+|\s+$/g, '');
    if (!url) return '';
    if (/^(?:images\/)?noposter\.jpg(?:\?|$)/i.test(url) || /\/noposter\.jpg(?:\?|$)/i.test(url)) return '';
    if (!/^https?:\/\//i.test(url) && !/^data:image\//i.test(url)) return '';
    return url;
  }

  function firstBackdrop(item) {
    if (!item) return '';
    var paths = item.backdrop_path || item.backdrops || (item.info && item.info.backdrop_path);
    if (typeof paths === 'string') {
      var direct = artworkUrl(paths);
      if (direct) return direct;
      try { paths = JSON.parse(paths); } catch (error) { paths = null; }
    }
    if (paths && Object.prototype.toString.call(paths) === '[object Array]') {
      for (var i = 0; i < paths.length; i += 1) {
        var candidate = artworkUrl(paths[i]);
        if (candidate) return candidate;
      }
    }
    return artworkUrl(item.backdrop || item.fanart || item.fan_art || (item.info && (item.info.backdrop || item.info.fanart || item.info.fan_art)));
  }

  function backdropImageOf(item) {
    return firstBackdrop(item);
  }

  function imageOf(item, type) {
    if (!item) return '';
    var backdrop = firstBackdrop(item);
    if (backdrop) return backdrop;
    if (type === 'live') return artworkUrl(item.stream_icon);
    if (type === 'series') return artworkUrl(item.cover_big) || artworkUrl(item.cover) || artworkUrl(item.stream_icon);
    return artworkUrl(item.cover_big) || artworkUrl(item.movie_image) || artworkUrl(item.stream_icon) || artworkUrl(item.cover);
  }

  function posterCandidatesOf(item, type) {
    if (!item) return [];
    var raw = type === 'live' ?
      [item.stream_icon] :
      (type === 'series' ?
        [item.cover, item.cover_big, item.stream_icon, item.movie_image] :
        [item.stream_icon, item.movie_image, item.cover, item.cover_big]);
    var urls = [];
    var seen = {};
    raw.forEach(function (value) {
      var url = artworkUrl(value);
      if (!url || seen[url]) return;
      seen[url] = true;
      urls.push(url);
    });
    return urls;
  }

  function posterImageOf(item, type) {
    var candidates = posterCandidatesOf(item, type);
    return candidates.length ? candidates[0] : '';
  }

  function descriptionOf(item) {
    return String(item && (item.plot || item.description || item.info && item.info.plot) || '');
  }

  function initials(value) {
    return String(value || 'TV').split(/\s+/).slice(0, 2).map(function (part) {
      return part.charAt(0);
    }).join('').toUpperCase();
  }

  function yearOf(item) {
    var value = item && (item.releaseDate || item.releasedate || item.year || item.release_date);
    return value ? String(value).slice(0, 4) : '';
  }

  function ratingOf(item) {
    var value = item && (item.rating_5based || item.rating);
    if (value === undefined || value === null || value === '') return '';
    var number = Number(value);
    if (isFinite(number) && number <= 5) number *= 2;
    return isFinite(number) ? number.toFixed(1).replace('.0', '') : String(value);
  }

  function durationOf(item) {
    if (!item) return '';
    var minutes = Number(item.runtime || item.episode_run_time || 0);
    if (isFinite(minutes) && minutes > 0) {
      if (minutes >= 60) {
        var hours = Math.floor(minutes / 60);
        var remainder = Math.round(minutes % 60);
        return hours + 'h' + (remainder ? ' ' + remainder + 'm' : '');
      }
      return Math.round(minutes) + 'm';
    }

    var duration = String(item.duration || '');
    var match = duration.match(/^(\d{1,2}):(\d{2})(?::\d{2})?$/);
    if (match) {
      var durationHours = Number(match[1]);
      var durationMinutes = Number(match[2]);
      if (durationHours > 0) return durationHours + 'h' + (durationMinutes ? ' ' + durationMinutes + 'm' : '');
      if (durationMinutes > 0) return durationMinutes + 'm';
    }

    var seconds = Number(item.duration_secs || 0);
    if (isFinite(seconds) && seconds > 0) {
      var totalMinutes = Math.max(1, Math.round(seconds / 60));
      if (totalMinutes >= 60) {
        var secondsHours = Math.floor(totalMinutes / 60);
        var secondsMinutes = totalMinutes % 60;
        return secondsHours + 'h' + (secondsMinutes ? ' ' + secondsMinutes + 'm' : '');
      }
      return totalMinutes + 'm';
    }
    return '';
  }

  function genreOf(item) {
    return String(item && (item.genre || item.genres) || '');
  }

  function posterHtml(item, type, className) {
    var candidates = posterCandidatesOf(item, type);
    var image = candidates.length ? candidates[0] : '';
    var encodedCandidates = image ? encodeURIComponent(JSON.stringify(candidates)) : '';
    className = className || 'v07-poster-art';
    return '<div class="' + className + (image ? '' : ' image-failed') + '">' +
      (image ? '<img src="' + esc(image) + '" alt="" loading="eager" draggable="false" ' +
        'data-v07-art-candidates="' + esc(encodedCandidates) + '" data-v07-art-index="0" data-v07-art-retries="0" ' +
        'onload="window.XtreamlyTVV07PosterLoaded&&window.XtreamlyTVV07PosterLoaded(this)" ' +
        'onerror="window.XtreamlyTVV07PosterFailed&&window.XtreamlyTVV07PosterFailed(this,false)">' : '') +
      '<div class="v07-poster-fallback"><span>' + esc(initials(titleOf(item))) + '</span></div></div>';
  }

  function posterCandidateList(image) {
    if (!image) return [];
    var encoded = image.getAttribute('data-v07-art-candidates') || '';
    if (!encoded) return [];
    try {
      var parsed = JSON.parse(decodeURIComponent(encoded));
      return Object.prototype.toString.call(parsed) === '[object Array]' ? parsed : [];
    } catch (error) {
      return [];
    }
  }

  function finishPosterFailure(image) {
    if (!image || !image.parentNode) return;
    image.style.display = 'none';
    image.parentNode.classList.add('image-failed');
  }

  window.XtreamlyTVV07PosterLoaded = function (image) {
    if (!image) return;
    var url = artworkUrl(image.getAttribute('src'));
    if (url) V.artworkPreload[url] = 'ready';
    image.setAttribute('data-v07-art-retries', '0');
    if (image.parentNode) image.parentNode.classList.remove('image-failed');
  };

  window.XtreamlyTVV07PosterFailed = function (image, watchdog) {
    if (!image || !image.parentNode) return;
    var candidates = posterCandidateList(image);
    var currentIndex = Number(image.getAttribute('data-v07-art-index') || 0);
    var nextIndex = currentIndex + 1;

    if (nextIndex < candidates.length) {
      image.setAttribute('data-v07-art-index', String(nextIndex));
      image.setAttribute('data-v07-art-retries', '0');
      image.style.display = '';
      image.src = candidates[nextIndex];
      return;
    }

    var retries = Number(image.getAttribute('data-v07-art-retries') || 0);
    if (retries < 1 && candidates.length) {
      image.setAttribute('data-v07-art-index', '0');
      image.setAttribute('data-v07-art-retries', String(retries + 1));
      window.setTimeout(function () {
        if (!image.parentNode) return;
        image.style.display = '';
        image.src = candidates[0];
      }, watchdog ? 120 : 650);
      return;
    }

    finishPosterFailure(image);
  };

  function armPosterImageWatchdog(button) {
    if (!button) return;
    var image = button.querySelector('.v07-poster-thumb img');
    if (!image || image.__v07WatchdogArmed) return;
    image.__v07WatchdogArmed = true;
    window.setTimeout(function () {
      if (!image.parentNode || image.style.display === 'none') return;
      if (!image.complete || Number(image.naturalWidth || 0) <= 0) {
        window.XtreamlyTVV07PosterFailed(image, true);
      }
    }, 6500);
  }

  function logoHtml(item, eager) {
    var image = imageOf(item, 'live');
    return '<div class="v07-station-logo">' +
      (image ? '<img data-channel-art="true" src="' + esc(image) + '" alt="" loading="' + (eager ? 'eager' : 'lazy') + '" decoding="' + (eager ? 'sync' : 'async') + '" onerror="this.style.display=\'none\';this.nextSibling.style.display=\'grid\'">' : '') +
      '<span' + (image ? ' style="display:none"' : '') + '>' + esc(initials(titleOf(item))) + '</span></div>';
  }

  function metaChips(item, type) {
    var values = [];
    var year = yearOf(item);
    var rating = ratingOf(item);
    var genre = genreOf(item);
    var duration = durationOf(item);
    if (year) values.push(year);
    if (rating) values.push('★ ' + rating);
    if (genre) values.push(genre.split(/\s*[,\/]\s*/)[0]);
    if (duration) values.push(duration);
    if (type === 'series' && values.length < 3) values.push('Series');
    return values.slice(0, 4).map(function (value) {
      return '<span class="v07-meta-chip">' + esc(value) + '</span>';
    }).join('');
  }


  function movieResolutionOf(item) {
    var video = item && item.video;
    var width = Number(video && video.width || 0);
    var height = Number(video && video.height || 0);
    if (!isFinite(width) || !isFinite(height) || width <= 0 || height <= 0) return '';
    if (height >= 2160) return '4K';
    if (height >= 1440) return '1440p';
    if (height >= 1080) return '1080p';
    if (height >= 720) return '720p';
    return Math.round(width) + '×' + Math.round(height);
  }

  function movieAudioOf(item) {
    var audio = item && item.audio;
    if (!audio) return '';
    var layout = String(audio.channel_layout || '').replace(/^\s+|\s+$/g, '');
    if (layout) return layout === '5.1' ? '5.1 audio' : layout;
    var channels = Number(audio.channels || 0);
    if (channels === 6) return '5.1 audio';
    if (channels === 2) return 'Stereo';
    return channels > 0 ? channels + 'ch audio' : '';
  }

  function movieFactsHtml(item) {
    if (!item) return '';
    var facts = [];
    function add(label, value, wide) {
      value = String(value || '').replace(/^\s+|\s+$/g, '');
      if (!value) return;
      facts.push('<div class="v07-movie-fact' + (wide ? ' v07-movie-fact--wide' : '') + '"><span>' + esc(label) + '</span><strong>' + esc(value) + '</strong></div>');
    }
    add('Release', item.releasedate || item.releaseDate || item.release_date || '');
    add('Genre', genreOf(item), true);
    add('Director', item.director || '');
    add('Cast', item.cast || item.actors || '', true);
    add('Language / Country', item.country || '');
    var quality = [movieResolutionOf(item), movieAudioOf(item)].filter(function (value) { return !!value; }).join(' \u2022 ');
    add('Quality', quality);
    return '<div class="v07-movie-facts">' + facts.join('') + '</div>';
  }

  function favoriteAutoSortEnabled() {
    return !(App.state && App.state.settings && App.state.settings.favoriteAutoSort === false);
  }

  function hiddenCatalogConfig() {
    var settings = App.state && App.state.settings || {};
    var source = settings.hiddenCatalogCategories || {};
    function normalized(value) {
      if (Object.prototype.toString.call(value) !== '[object Array]') return [];
      var seen = {};
      return value.map(function (id) { return String(id); }).filter(function (id) {
        if (!id || seen[id]) return false;
        seen[id] = true;
        return true;
      });
    }
    return {
      movies:normalized(source.movies),
      series:normalized(source.series)
    };
  }

  function hiddenCategoryIds(kind) {
    var config = hiddenCatalogConfig();
    return config[kind] || [];
  }

  function isProviderCategoryHidden(kind, categoryId) {
    categoryId = String(categoryId == null ? '' : categoryId);
    if (!categoryId || (kind !== 'movies' && kind !== 'series')) return false;
    return hiddenCategoryIds(kind).indexOf(categoryId) >= 0;
  }

  function addCategoryIds(value, output) {
    if (value === undefined || value === null || value === '') return;
    if (Object.prototype.toString.call(value) === '[object Array]') {
      value.forEach(function (entry) { addCategoryIds(entry, output); });
      return;
    }
    if (typeof value === 'object') {
      if (value.category_id !== undefined) addCategoryIds(value.category_id, output);
      if (value.id !== undefined) addCategoryIds(value.id, output);
      return;
    }

    var text = String(value).replace(/^\s+|\s+$/g, '');
    if (!text) return;
    if (text.charAt(0) === '[' || text.charAt(0) === '{') {
      try {
        addCategoryIds(JSON.parse(text), output);
        return;
      } catch (error) {}
    }

    text.split(/[,;|]/).forEach(function (entry) {
      entry = String(entry || '').replace(/^\s+|\s+$/g, '');
      if (entry && output.indexOf(entry) < 0) output.push(entry);
    });
  }

  function itemCategoryIds(item) {
    var ids = [];
    if (!item) return ids;
    addCategoryIds(item.category_id, ids);
    addCategoryIds(item.category_ids, ids);
    addCategoryIds(item.categoryId, ids);
    if (item.parent_series) {
      addCategoryIds(item.parent_series.category_id, ids);
      addCategoryIds(item.parent_series.category_ids, ids);
    }
    return ids;
  }

  function itemHiddenByCategory(item, type) {
    var kind = type === 'movie' ? 'movies' : (type === 'series' || type === 'episode' ? 'series' : '');
    if (!kind) return false;
    var hidden = hiddenCategoryIds(kind);
    if (!hidden.length) return false;
    return itemCategoryIds(item).some(function (id) { return hidden.indexOf(String(id)) >= 0; });
  }

  function firstVisibleLoadedItem(kind) {
    var type = typeForKind(kind);
    var items = App.loadedItems(kind) || [];
    for (var i = 0; i < items.length; i += 1) {
      if (!itemHiddenByCategory(items[i], type)) return items[i];
    }
    return null;
  }

  function providerCategoriesForSettings(kind) {
    return (App.categoriesFor(kind) || []).filter(function (category) {
      if (!category || category.category_id === undefined || category.category_id === null) return false;
      return String(category.category_id) !== 'all';
    });
  }

  function categoryNameById(kind, id) {
    id = String(id || '');
    var category = providerCategoriesForSettings(kind).find(function (entry) {
      return String(entry.category_id) === id;
    });
    return category ? String(category.category_name || ('Category ' + id)) : ('Category ' + id);
  }

  function setProviderCategoryHidden(kind, categoryId, hidden) {
    if (kind !== 'movies' && kind !== 'series') return;
    categoryId = String(categoryId || '');
    if (!categoryId) return;

    var config = hiddenCatalogConfig();
    var values = config[kind].slice();
    var index = values.indexOf(categoryId);
    if (hidden && index < 0) values.push(categoryId);
    if (!hidden && index >= 0) values.splice(index, 1);
    config[kind] = values;

    Store.updateSettings({ hiddenCatalogCategories:config });
    App.state = Store.getState();

    if (hidden && String(App.activeCategory[kind] || '') === categoryId) {
      App.activeCategory[kind] = '';
      App.currentItems[kind] = [];
      App.currentFilteredItems[kind] = [];
      V.mediaIndex[kind] = 0;
      V.selectedKey[kind] = '';
    }
  }

  function blockedCategoryGroupHtml(kind, title) {
    var hidden = hiddenCategoryIds(kind);
    var categories = providerCategoriesForSettings(kind);
    var visible = categories.filter(function (category) {
      return hidden.indexOf(String(category.category_id)) < 0;
    });
    var selectId = kind === 'movies' ? 'v07BlockMoviesSelect' : 'v07BlockSeriesSelect';
    var buttonId = kind === 'movies' ? 'v07BlockMoviesButton' : 'v07BlockSeriesButton';
    var options = visible.map(function (category) {
      return '<option value="' + esc(String(category.category_id)) + '">' + esc(String(category.category_name || ('Category ' + category.category_id))) + '</option>';
    }).join('');
    if (!options) options = '<option value="">No additional categories available</option>';

    var blocked = hidden.map(function (id) {
      return '<button class="v07-blocked-category-row focusable" type="button" data-v07-unblock-kind="' + kind + '" data-v07-unblock-id="' + esc(id) + '">' +
        '<span>' + esc(categoryNameById(kind, id)) + '</span><strong>Unblock</strong></button>';
    }).join('');
    if (!blocked) blocked = '<div class="v07-blocked-empty">No blocked ' + esc(title.toLowerCase()) + ' categories.</div>';

    return '<div class="v07-blocked-group">' +
      '<h4>' + esc(title) + '</h4>' +
      '<div class="v07-blocked-add"><select id="' + selectId + '" class="focusable">' + options + '</select>' +
      '<button id="' + buttonId + '" class="secondary-button focusable" type="button"' + (visible.length ? '' : ' disabled') + '>Block category</button></div>' +
      '<div class="v07-blocked-list">' + blocked + '</div></div>';
  }

  function renderBlockedCategorySettings() {
    var grid = document.querySelector('.settings-grid');
    if (!grid) return;
    var old = document.getElementById('v07BlockedCategories');
    if (old && old.parentNode) old.parentNode.removeChild(old);

    var section = document.createElement('section');
    section.id = 'v07BlockedCategories';
    section.className = 'settings-card v07-blocked-categories';
    section.innerHTML =
      '<h3>Blocked Movies / Series Categories</h3>' +
      '<p>Blocked provider categories are removed from the Movies/Series wheels and their items are hidden from Home and Favorites. This is a local visibility filter; it is not PIN-protected.</p>' +
      blockedCategoryGroupHtml('movies', 'Movies') +
      blockedCategoryGroupHtml('series', 'Series');
    grid.appendChild(section);

    function bindBlock(kind, selectId, buttonId) {
      var select = document.getElementById(selectId);
      var button = document.getElementById(buttonId);
      if (!select || !button) return;

      /* LG webOS spatial navigation does not reliably cross from a native
         <select> into a sibling button. Own that horizontal transition so the
         remote can always reach Block category. */
      select.addEventListener('keydown', function (event) {
        if (event.keyCode === KEY.RIGHT) {
          event.preventDefault();
          event.stopImmediatePropagation();
          button.focus();
        }
      }, true);
      button.addEventListener('keydown', function (event) {
        if (event.keyCode === KEY.LEFT) {
          event.preventDefault();
          event.stopImmediatePropagation();
          select.focus();
        }
      }, true);

      button.addEventListener('click', function () {
        var id = String(select.value || '');
        if (!id) return;
        var name = categoryNameById(kind, id);
        setProviderCategoryHidden(kind, id, true);
        App.toast('Blocked ' + name);
        patchSettings();
        setTimeout(function () {
          var next = document.getElementById(selectId);
          if (next) next.focus();
        }, 0);
      });
    }

    bindBlock('movies', 'v07BlockMoviesSelect', 'v07BlockMoviesButton');
    bindBlock('series', 'v07BlockSeriesSelect', 'v07BlockSeriesButton');

    Array.prototype.forEach.call(section.querySelectorAll('[data-v07-unblock-kind]'), function (button) {
      button.addEventListener('click', function () {
        var kind = String(button.getAttribute('data-v07-unblock-kind') || '');
        var id = String(button.getAttribute('data-v07-unblock-id') || '');
        var name = categoryNameById(kind, id);
        setProviderCategoryHidden(kind, id, false);
        App.toast('Unblocked ' + name);
        patchSettings();
        setTimeout(function () {
          var target = document.getElementById(kind === 'movies' ? 'v07BlockMoviesSelect' : 'v07BlockSeriesSelect');
          if (target) target.focus();
        }, 0);
      });
    });
  }

  function alphabeticalItems(items) {
    return (items || []).slice().sort(function (a, b) {
      var left = titleOf(a).toLowerCase();
      var right = titleOf(b).toLowerCase();
      if (left < right) return -1;
      if (left > right) return 1;
      return titleOf(a).localeCompare(titleOf(b));
    });
  }

  function stateFavoritesForType(type) {
    var items = (App.state && App.state.favorites || []).filter(function (item) {
      var itemType = Store.inferType ? Store.inferType(item) : item.content_type;
      return itemType === type && !itemHiddenByCategory(item, type);
    });
    return favoriteAutoSortEnabled() ? alphabeticalItems(items) : items;
  }

  function visibleCustomGroups(type) {
    var groups = [];
    if (typeof App.favoriteGroups === 'function') {
      try {
        groups = App.favoriteGroups().filter(function (group) { return !group.system; });
      } catch (error) { groups = []; }
    }
    if (!groups.length) groups = (App.state && App.state.favoriteGroups || []).slice();

    var favoriteKeys = {};
    stateFavoritesForType(type).forEach(function (item) {
      favoriteKeys[itemKey(item, type)] = true;
    });

    return groups.filter(function (group) {
      return (group.itemKeys || []).some(function (key) { return !!favoriteKeys[String(key)]; });
    });
  }

  function categoriesWithVirtuals(kind) {
    var type = typeForKind(kind);
    var output = [{
      category_id:'__favorites__',
      category_name:'♥ Favorites'
    }];

    visibleCustomGroups(type).forEach(function (group) {
      output.push({
        category_id:'__group__:' + group.id,
        category_name:'♡ ' + String(group.name || 'My List')
      });
    });

    (App.categoriesFor(kind) || []).forEach(function (category) {
      if (!category || category.category_id === undefined || category.category_id === null) return;
      if (String(category.category_id) === 'all') return;
      if (isProviderCategoryHidden(kind, category.category_id)) return;
      output.push(category);
    });
    return output;
  }

  function virtualItems(kind, categoryId) {
    var type = typeForKind(kind);
    if (categoryId === '__favorites__') {
      return stateFavoritesForType(type);
    }
    if (categoryId.indexOf('__group__:') === 0) {
      var groupId = categoryId.slice('__group__:'.length);
      var group = null;
      if (typeof App.favoriteGroupById === 'function') {
        try { group = App.favoriteGroupById(groupId); } catch (error) { group = null; }
      }
      if (!group) {
        group = (App.state && App.state.favoriteGroups || []).find(function (entry) {
          return String(entry.id) === String(groupId);
        });
      }
      if (!group) return [];
      var keys = {};
      (group.itemKeys || []).forEach(function (key) { keys[String(key)] = true; });
      return stateFavoritesForType(type).filter(function (item) {
        return !!keys[itemKey(item, type)];
      });
    }
    return null;
  }

  function ensureActiveCategory(kind, categories) {
    var active = String(App.activeCategory[kind] || '');
    var exists = categories.some(function (category) {
      return String(category.category_id) === active;
    });
    if (!exists) {
      var provider = categories.find(function (category) {
        return String(category.category_id).indexOf('__') !== 0;
      });
      App.activeCategory[kind] = provider ? String(provider.category_id) :
        (categories.length ? String(categories[0].category_id) : '');
    }
    return String(App.activeCategory[kind] || '');
  }

  function bindScopedSearch(kind, update) {
    var search = document.getElementById('catalogSearch');
    if (!search) return;
    search.addEventListener('input', function () {
      App.searchText[kind] = search.value;
      clearTimeout(App.searchTimer);
      App.searchTimer = setTimeout(function () { update(kind); }, 70);
    });
  }

  function alignScopedSearchToTopNav() {
    var searchWrap = document.querySelector('.v07-search-wrap');
    if (!searchWrap) return;
    searchWrap.style.left = '';
    searchWrap.style.transform = '';
  }

  function wheelLabelClass(label) {
    var text = String(label || '');
    if (text.length >= 24) return ' v07-wheel-label-xlong';
    if (text.length >= 18) return ' v07-wheel-label-long';
    return '';
  }

  function fitCategoryWheelLabels() {
    var labels = document.querySelectorAll('#categoryList .v07-wheel-label');
    Array.prototype.forEach.call(labels, function (label) {
      var button = label.parentNode;
      if (!button) return;

      label.classList.remove('v07-wheel-fit-1');
      label.classList.remove('v07-wheel-fit-2');
      label.classList.remove('v07-wheel-fit-3');
      label.classList.remove('v07-wheel-fit-ellipsis');

      /* The r20 wheel gives each row a 268px paint box. Measure the actual
         rendered glyph width rather than guessing from character count, since
         provider names can contain wide capitals, pipes, spaces and symbols. */
      var available = Math.max(160, (button.clientWidth || 268) - 14);
      label.style.maxWidth = available + 'px';

      function stillTooWide() {
        return label.scrollWidth > available + 1;
      }

      if (stillTooWide()) label.classList.add('v07-wheel-fit-1');
      if (stillTooWide()) label.classList.add('v07-wheel-fit-2');
      if (stillTooWide()) label.classList.add('v07-wheel-fit-3');
      if (stillTooWide()) label.classList.add('v07-wheel-fit-ellipsis');
    });
  }

  function settingsCogIconHtml() {
    return '<svg class="v07-settings-svg" viewBox="0 0 24 24" aria-hidden="true" focusable="false">' +
      '<circle cx="12" cy="12" r="3.1"></circle>' +
      '<path d="M19.4 13.2a7.7 7.7 0 0 0 .06-2.4l2.06-1.6-2-3.47-2.52.72a7.92 7.92 0 0 0-2.06-1.2L14.4 2h-4.01l-.54 3.23a7.92 7.92 0 0 0-2.06 1.2l-2.52-.72-2 3.47 2.06 1.6a7.7 7.7 0 0 0 .06 2.4l-2.12 1.66 2 3.47 2.66-.76c.62.5 1.32.9 2.08 1.18L10.39 22h4.01l.54-3.29a8.34 8.34 0 0 0 2.08-1.18l2.66.76 2-3.47-2.28-1.62z"></path></svg>';
  }

  function searchAvailableForView() {
    return App.currentView === 'live' || App.currentView === 'movies' || App.currentView === 'series';
  }

  function currentCategoryLabel(kind) {
    var active = String(App.activeCategory && App.activeCategory[kind] || '');
    var categories = categoriesWithVirtuals(kind);
    var match = categories.find(function (category) { return String(category.category_id) === active; });
    return match ? String(match.category_name || 'Current category') : 'Current category';
  }

  function removeSearchViewportSync() {
    if (!V.searchViewportSync) return;
    window.removeEventListener('scroll', V.searchViewportSync, true);
    window.removeEventListener('resize', V.searchViewportSync, true);
    if (window.visualViewport) {
      try { window.visualViewport.removeEventListener('scroll', V.searchViewportSync); } catch (error) {}
      try { window.visualViewport.removeEventListener('resize', V.searchViewportSync); } catch (error) {}
    }
    V.searchViewportSync = null;
  }

  function syncSearchOverlayToViewport() {
    var overlay = document.getElementById('v07SearchOverlay');
    if (!overlay || !V.searchOpen) return;
    var doc = document.documentElement;
    var body = document.body;
    var scrollY = window.pageYOffset || (doc && doc.scrollTop) || (body && body.scrollTop) || 0;
    var scrollX = window.pageXOffset || (doc && doc.scrollLeft) || (body && body.scrollLeft) || 0;
    var viewport = window.visualViewport;
    var offsetTop = viewport && isFinite(viewport.offsetTop) ? viewport.offsetTop : 0;
    var offsetLeft = viewport && isFinite(viewport.offsetLeft) ? viewport.offsetLeft : 0;
    var height = viewport && viewport.height ? viewport.height : (window.innerHeight || (doc && doc.clientHeight) || 720);
    var width = viewport && viewport.width ? viewport.width : (window.innerWidth || (doc && doc.clientWidth) || 1280);
    overlay.style.top = Math.round(scrollY + offsetTop) + 'px';
    overlay.style.left = Math.round(scrollX + offsetLeft) + 'px';
    overlay.style.width = Math.round(width) + 'px';
    overlay.style.height = Math.round(height) + 'px';
  }

  function installSearchViewportSync() {
    removeSearchViewportSync();
    V.searchViewportSync = function () {
      if (!V.searchOpen) return;
      syncSearchOverlayToViewport();
    };
    window.addEventListener('scroll', V.searchViewportSync, true);
    window.addEventListener('resize', V.searchViewportSync, true);
    if (window.visualViewport) {
      try { window.visualViewport.addEventListener('scroll', V.searchViewportSync); } catch (error) {}
      try { window.visualViewport.addEventListener('resize', V.searchViewportSync); } catch (error) {}
    }
    syncSearchOverlayToViewport();
  }

  function closeSearchPopup(restoreFocus) {
    removeSearchViewportSync();
    var overlay = document.getElementById('v07SearchOverlay');
    if (overlay && overlay.parentNode) overlay.parentNode.removeChild(overlay);
    V.searchOpen = false;
    if (restoreFocus) {
      var button = document.getElementById('v07SearchNav');
      if (button) button.focus();
    }
  }

  function openSearchPopup() {
    if (!searchAvailableForView()) return;
    closeSearchPopup(false);
    var kind = App.currentView;
    var host = document.body;
    var overlay = document.createElement('div');
    overlay.id = 'v07SearchOverlay';
    overlay.className = 'v07-search-overlay';
    overlay.innerHTML = '<div class="v07-search-popover" role="dialog" aria-modal="true" aria-label="Search current category">' +
      '<div class="v07-search-popover-head"><div><strong>Search this category</strong><span>' + esc(currentCategoryLabel(kind)) + '</span></div><button id="v07SearchClose" class="v07-search-close focusable" type="button" aria-label="Close search">×</button></div>' +
      '<div class="v07-search-input-wrap"><span class="v07-search-icon">' + searchIconHtml() + '</span><input id="v07SearchInput" class="search-box focusable" type="text" inputmode="search" spellcheck="false" value="' + esc(App.searchText[kind] || '') + '" placeholder="Search this category" autocomplete="off"></div>' +
      '<div class="v07-search-scope">Results are limited to the selected category.</div>' +
      '</div>';
    host.insertBefore(overlay, host.firstChild || null);
    V.searchOpen = true;
    installSearchViewportSync();

    var input = document.getElementById('v07SearchInput');
    var close = document.getElementById('v07SearchClose');
    if (input) {
      input.addEventListener('input', function () {
        App.searchText[kind] = input.value;
        clearTimeout(App.searchTimer);
        App.searchTimer = setTimeout(function () {
          if (App.currentView === kind) App.updateCatalogGrid(kind);
          patchTopNavDecorations();
        }, 70);
      });
      input.addEventListener('keydown', function (event) {
        if (isBackKey(event.keyCode)) {
          event.preventDefault();
          event.stopImmediatePropagation();
          closeSearchPopup(true);
        }
      }, true);
    }
    if (close) close.addEventListener('click', function () { closeSearchPopup(true); });
    overlay.addEventListener('click', function (event) {
      if (event.target === overlay) closeSearchPopup(true);
    });
    if (window.XtreamlyTVNavigation) XtreamlyTVNavigation.invalidate();
    setTimeout(function () {
      if (input) {
        try { input.focus({ preventScroll:true }); }
        catch (error) { input.focus(); }
        syncSearchOverlayToViewport();
        if (input.select) input.select();
        setTimeout(syncSearchOverlayToViewport, 50);
        setTimeout(syncSearchOverlayToViewport, 180);
      }
    }, 0);
  }

  function patchTopNavDecorations() {
    var nav = document.querySelector('.nav-list');
    var settings = document.querySelector('.nav-item[data-view="settings"]');
    if (settings) {
      settings.setAttribute('aria-label', 'Settings');
      var icon = settings.querySelector('.nav-icon') || settings;
      if (icon) icon.innerHTML = settingsCogIconHtml();
    }

    var search = document.getElementById('v07SearchNav');
    if (!searchAvailableForView()) {
      if (search && search.parentNode) search.parentNode.removeChild(search);
      closeSearchPopup(false);
      return;
    }
    if (!nav) return;
    if (!search) {
      search = document.createElement('button');
      search.id = 'v07SearchNav';
      search.className = 'nav-item v07-search-nav focusable';
      search.type = 'button';
      search.setAttribute('aria-label', 'Search current category');
      search.innerHTML = '<span class="nav-icon">' + searchIconHtml() + '</span>';
      if (settings && settings.parentNode === nav) nav.insertBefore(search, settings);
      else nav.appendChild(search);
      search.addEventListener('click', openSearchPopup);
    }
    search.classList.toggle('has-query', !!(App.searchText[App.currentView] || ''));
  }

  function categoryWheelItems(categories) {
    return (categories || []).map(function (category) {
      return {
        id:String(category.category_id),
        label:String(category.category_name || ('Category ' + category.category_id))
      };
    });
  }

  function categoryWheelCenterButton() {
    return document.querySelector('#categoryList [data-v07-wheel-center="true"]');
  }

  function renderCategoryWheel(direction) {
    var wheel = V.categoryWheel;
    if (!wheel || wheel.destroyed || !wheel.container) return;
    var items = wheel.items;
    if (!items.length) {
      wheel.container.innerHTML = '<div class="v07-wheel-empty">No categories</div>';
      return;
    }

    var html = '<div class="v07-wheel-viewport' +
      (direction ? ' v07-wheel-spin-' + direction : '') + '">';
    for (var offset = -3; offset <= 3; offset += 1) {
      var index = mod(wheel.centerIndex + offset, items.length);
      var item = items[index];
      var center = offset === 0;
      var active = String(item.id) === String(App.activeCategory[wheel.kind] || '');
      html += '<button class="category-button v07-wheel-slot' +
        (center ? ' focusable v07-wheel-center' : '') +
        (active ? ' active v07-wheel-active' : '') +
        '" data-v07-wheel-index="' + index + '"' +
        (center ? ' data-v07-wheel-center="true" tabindex="0"' : ' tabindex="-1" aria-hidden="true"') +
        ' data-wheel-distance="' + Math.abs(offset) + '" data-wheel-offset="' + offset + '">' +
        '<span class="v07-wheel-label' + wheelLabelClass(item.label) + '">' + esc(item.label) + '</span>' +
        (active ? '<span class="v07-wheel-active-dot" aria-hidden="true"></span>' : '') +
        '</button>';
    }
    html += '</div>';
    wheel.container.innerHTML = html;
    fitCategoryWheelLabels();

    var centerButton = categoryWheelCenterButton();
    if (centerButton) {
      centerButton.addEventListener('click', function (event) {
        event.preventDefault();
        event.stopPropagation();
        activateCategoryWheel();
      });
    }
    if (window.XtreamlyTVNavigation) XtreamlyTVNavigation.invalidate();
  }

  function moveCategoryWheel(delta) {
    var wheel = V.categoryWheel;
    if (!wheel || !wheel.items.length) return;
    wheel.centerIndex = mod(wheel.centerIndex + delta, wheel.items.length);
    renderCategoryWheel(delta < 0 ? 'up' : 'down');
    var center = categoryWheelCenterButton();
    if (center) center.focus();
  }

  function focusContentForKind(kind) {
    if (kind === 'live') focusLiveStation();
    else if (kind === 'movies' || kind === 'series') focusMediaPoster(kind);
  }

  function activateCategoryWheel() {
    var wheel = V.categoryWheel;
    if (!wheel || !wheel.items.length) return;
    var item = wheel.items[wheel.centerIndex];
    if (!item) return;
    var category = String(item.id);
    var kind = wheel.kind;

    if (String(App.activeCategory[kind] || '') === category) {
      /* Keep focus locked to the wheel while this category is genuinely
         loading. Otherwise stale currentFilteredItems from the prior category
         could be painted into the new surface by an eager second OK/Right. */
      if (catalogActivityActive()) return;
      focusFirstContentForKind(kind);
      return;
    }

    App.activeCategory[kind] = category;
    App.searchText[kind] = '';
    resetContentSelection(kind);
    if (App.pendingCatalogFirstFocus) App.pendingCatalogFirstFocus[kind] = true;
    App.pendingNavigationRestore = null;
    App.renderCatalog(kind);
  }

  function installCategoryRail(kind, categories, active) {
    var rail = document.getElementById('categoryList');
    if (!rail) return;
    var items = categoryWheelItems(categories);
    var activeIndex = 0;
    for (var i = 0; i < items.length; i += 1) {
      if (String(items[i].id) === String(active)) {
        activeIndex = i;
        break;
      }
    }

    var wheel = {
      kind:kind,
      items:items,
      centerIndex:activeIndex,
      container:rail,
      destroyed:false,
      destroy:function () {
        this.destroyed = true;
        if (V.categoryWheel === this) V.categoryWheel = null;
      },
      focusActive:function () {
        if (this.destroyed || !this.items.length) return;
        var current = String(App.activeCategory[this.kind] || '');
        for (var index = 0; index < this.items.length; index += 1) {
          if (String(this.items[index].id) === current) {
            this.centerIndex = index;
            break;
          }
        }
        renderCategoryWheel('');
        var target = categoryWheelCenterButton();
        if (target) target.focus();
      }
    };

    V.categoryWheel = wheel;
    App.categoryRail = wheel;
    rail.classList.add('v07-category-wheel');
    rail.addEventListener('click', function (event) {
      event.preventDefault();
      event.stopPropagation();
      if (wheel && wheel.focusActive) wheel.focusActive();
    });
    renderCategoryWheel('');
  }

  function setCatalogActivity(message) {
    var activity = document.getElementById('v07CatalogActivity');
    if (!activity) return;
    activity.innerHTML = '<span class="v07-activity-spinner" aria-hidden="true"></span><span>' +
      esc(message || 'Loading category…') + '</span>';
    activity.classList.add('active');
  }

  function clearCatalogActivity() {
    var activity = document.getElementById('v07CatalogActivity');
    if (!activity) return;
    activity.classList.remove('active');
    activity.innerHTML = '';
  }

  function catalogActivityActive() {
    var activity = document.getElementById('v07CatalogActivity');
    return !!(activity && activity.classList.contains('active'));
  }

  function loadActiveCategory(kind, category) {
    category = String(category || '');
    if (!category) return Promise.resolve([]);
    /* A fresh view should never expose the prior category through a fallback
       focus path while this request is in flight. */
    App.currentFilteredItems[kind] = [];
    App.currentItems[kind] = [];
    setCatalogActivity('Loading category…');

    var session = App.sessionGeneration;
    var promise;
    try { promise = App.loadCategory(kind, category); }
    catch (error) { promise = Promise.reject(error); }

    if (!promise || !promise.then) return Promise.resolve([]);
    return promise.then(function (items) {
      if (session !== App.sessionGeneration) return [];
      /* v0.6 deduplicates an already-running request before allocating a new
         generation. If the user leaves and returns to that category while the
         request is still pending, its original generation can be stale and
         the core intentionally declines to render it. Reconcile the resolved
         cache here so the new screen cannot be left in a permanent loading
         state. */
      if (App.currentView === kind &&
          String(App.activeCategory[kind] || '') === category) {
        var resolved = App.catalogCache[kind] && App.catalogCache[kind][category] || items || [];
        if (App.currentItems[kind] !== resolved || catalogActivityActive()) {
          App.currentItems[kind] = resolved;
          App.updateCatalogGrid(kind);
        }
      }
      return items;
    }).catch(function (error) {
      if (session === App.sessionGeneration && App.currentView === kind &&
          String(App.activeCategory[kind] || '') === category) {
        clearCatalogActivity();
      }
      throw error;
    });
  }

  function currentItemByKey(items, type, key) {
    if (!key) return -1;
    for (var i = 0; i < items.length; i += 1) {
      if (itemKey(items[i], type) === key) return i;
    }
    return -1;
  }


function mediaWindow(kind, items, index) {
  if (!items.length) return { start:0, end:0 };
  var size = Math.min(V.mediaWindowSize, items.length);
  var maxStart = Math.max(0, items.length - size);
  var current = V.mediaRailWindow[kind];

  if (current && current.end > current.start && (current.end - current.start) === size) {
    if (index >= current.start && index < current.end) {
      return { start:current.start, end:current.end };
    }
    if (index >= current.end) {
      var rightStart = Math.min(maxStart, current.start + Math.max(1, index - current.end + 1));
      return { start:rightStart, end:rightStart + size };
    }
    if (index < current.start) {
      var leftStart = Math.max(0, current.start - Math.max(1, current.start - index));
      return { start:leftStart, end:leftStart + size };
    }
  }

  var half = Math.floor(size / 2);
  var start = Math.max(0, Math.min(maxStart, index - half));
  return { start:start, end:start + size };
}

  function posterButtonHtml(item, type, index, selected) {
    var id = idOf(item, type);
    var favorite = Store.isFavorite && Store.isFavorite(type, id);
    var label = titleOf(item);
    return '<button class="v07-poster-card poster-card focusable' + (selected ? ' selected' : '') + '" ' +
      'aria-label="' + esc(label) + '" ' +
      'title="' + esc(label) + '" ' +
      'data-v07-media-index="' + index + '" data-content-type="' + esc(type) + '" data-content-id="' + esc(id) + '">' +
      posterHtml(item, type, 'v07-poster-thumb') +
      (favorite ? '<span class="v07-favorite-mark">♥</span>' : '') +
      '</button>';
  }


  var MEDIA_CACHE_STORAGE_KEY = 'xtreamlytv.media-cache.v1';
  var MEDIA_CACHE_MAX_CHARS = 1800000;
  var MEDIA_CACHE_LIMITS = { movie:60, series:8 };
  var MEDIA_CACHE_TTL = { movie:7 * 24 * 60 * 60 * 1000, series:3 * 24 * 60 * 60 * 1000 };

  function mediaCacheHash(value) {
    var hash = 2166136261;
    value = String(value || '');
    for (var i = 0; i < value.length; i += 1) {
      hash ^= value.charCodeAt(i);
      hash += (hash << 1) + (hash << 4) + (hash << 7) + (hash << 8) + (hash << 24);
    }
    return (hash >>> 0).toString(36);
  }

  function mediaCacheScope() {
    if (!App.api || App.demo) return 'demo';
    return window.XtreamlyCore.providerKey(App.api);
  }

  function mediaCacheLoad() {
    var scope = mediaCacheScope();
    if (V.persistentMediaCache && V.persistentMediaCacheScope === scope) return V.persistentMediaCache;
    var doc = { version:1, scope:scope, entries:{} };
    try {
      var raw = localStorage.getItem(MEDIA_CACHE_STORAGE_KEY);
      var parsed = raw ? JSON.parse(raw) : null;
      if (parsed && parsed.version === 1 && parsed.scope === scope && parsed.entries) doc = parsed;
    } catch (error) { /* localStorage unavailable/corrupt; use memory only */ }
    V.persistentMediaCache = doc;
    V.persistentMediaCacheScope = scope;
    V.persistentMediaCacheWarmed = false;
    mediaCachePrune(doc, false);
    return doc;
  }

  function mediaCachePrune(doc, persist) {
    if (!doc || !doc.entries) return;
    var now = Date.now();
    Object.keys(doc.entries).forEach(function (key) {
      var entry = doc.entries[key];
      if (!entry || Number(entry.expires || 0) <= now) delete doc.entries[key];
    });
    Object.keys(MEDIA_CACHE_LIMITS).forEach(function (kind) {
      var keys = Object.keys(doc.entries).filter(function (key) { return doc.entries[key].kind === kind; });
      keys.sort(function (a,b) { return Number(doc.entries[b].at || 0) - Number(doc.entries[a].at || 0); });
      keys.slice(MEDIA_CACHE_LIMITS[kind]).forEach(function (key) { delete doc.entries[key]; });
    });
    var encoded = '';
    try { encoded = JSON.stringify(doc); } catch (error) { encoded = ''; }
    if (encoded && encoded.length > MEDIA_CACHE_MAX_CHARS) {
      var all = Object.keys(doc.entries).sort(function (a,b) { return Number(doc.entries[a].at || 0) - Number(doc.entries[b].at || 0); });
      while (all.length && encoded.length > MEDIA_CACHE_MAX_CHARS) {
        delete doc.entries[all.shift()];
        try { encoded = JSON.stringify(doc); } catch (error2) { break; }
      }
    }
    if (persist !== false) {
      try { localStorage.setItem(MEDIA_CACHE_STORAGE_KEY, JSON.stringify(doc)); } catch (error3) { /* quota/private mode */ }
    }
  }

  function mediaCacheGet(kind, id) {
    id = String(id || '');
    if (!id) return null;
    var doc = mediaCacheLoad();
    var entry = doc.entries[kind + ':' + id];
    if (!entry || Number(entry.expires || 0) <= Date.now()) {
      if (entry) { delete doc.entries[kind + ':' + id]; mediaCachePrune(doc, true); }
      return null;
    }
    entry.at = Date.now();
    return entry.data || null;
  }

  function compactMovieResponse(response) {
    response = response || {};
    var info = response.info || {};
    var data = response.movie_data || {};
    var keepInfo = ['duration_secs','duration','video','audio','bitrate','tmdb_id','name','o_name','cover_big','movie_image','releasedate','releaseDate','youtube_trailer','director','actors','cast','description','plot','age','country','genre','backdrop_path','runtime','rating','status'];
    var keepData = ['stream_id','name','added','category_id','category_ids','container_extension','custom_sid','direct_source'];
    var outInfo = {}, outData = {};
    keepInfo.forEach(function (key) { if (info[key] !== undefined) outInfo[key] = info[key]; });
    keepData.forEach(function (key) { if (data[key] !== undefined) outData[key] = data[key]; });
    return { info:outInfo, movie_data:outData };
  }

  function compactEpisodeForCache(episode) {
    episode = episode || {};
    var info = episode.info || {};
    var out = {};
    ['id','stream_id','episode_num','episode','title','name','season','container_extension','direct_source','added','custom_sid'].forEach(function (key) {
      if (episode[key] !== undefined) out[key] = episode[key];
    });
    var outInfo = {};
    ['movie_image','cover','stream_icon','plot','description','duration','duration_secs','releaseDate','releasedate','rating','tmdb_id','air_date'].forEach(function (key) {
      var value = info[key] !== undefined ? info[key] : episode[key];
      if (value !== undefined) outInfo[key] = value;
    });
    if (Object.keys(outInfo).length) out.info = outInfo;
    return out;
  }

  function compactSeriesResponse(response) {
    response = response || {};
    var info = response.info || {};
    var outInfo = {};
    ['name','series_id','cover','cover_big','stream_icon','plot','description','genre','releaseDate','releasedate','rating','backdrop_path','youtube_trailer','tmdb','tmdb_id','episode_run_time','cast','director'].forEach(function (key) {
      if (info[key] !== undefined) outInfo[key] = info[key];
    });
    var episodes = response.episodes || {};
    var outEpisodes;
    if (Object.prototype.toString.call(episodes) === '[object Array]') {
      outEpisodes = episodes.map(compactEpisodeForCache);
    } else {
      outEpisodes = {};
      Object.keys(episodes).forEach(function (season) {
        outEpisodes[season] = (episodes[season] || []).map(compactEpisodeForCache);
      });
    }
    var seasons = (response.seasons || []).map(function (season) {
      var out = {};
      ['id','season_number','name','cover','cover_big','cover_tmdb','air_date','episode_count'].forEach(function (key) {
        if (season && season[key] !== undefined) out[key] = season[key];
      });
      return out;
    });
    return { info:outInfo, seasons:seasons, episodes:outEpisodes };
  }

  function mediaCacheArtwork(kind, data) {
    var root = data && (data.info || data) || {};
    var type = kind === 'movie' ? 'movie' : 'series';
    var urls = [firstBackdrop(root), posterImageOf(root, type), artworkUrl(root.cover_big), artworkUrl(root.movie_image)];
    var seen = {};
    return urls.filter(function (url) { if (!url || seen[url]) return false; seen[url] = true; return true; }).slice(0,4);
  }

  function mediaCachePut(kind, id, data) {
    id = String(id || '');
    if (!id || !data) return;
    var compact = kind === 'movie' ? compactMovieResponse(data) : compactSeriesResponse(data);
    var doc = mediaCacheLoad();
    doc.entries[kind + ':' + id] = {
      kind:kind,
      at:Date.now(),
      expires:Date.now() + MEDIA_CACHE_TTL[kind],
      data:compact,
      art:mediaCacheArtwork(kind, compact)
    };
    mediaCachePrune(doc, true);
  }

  function warmPersistentArtwork() {
    var doc = mediaCacheLoad();
    if (V.persistentMediaCacheWarmed || !doc || !doc.entries) return;
    V.persistentMediaCacheWarmed = true;
    var entries = Object.keys(doc.entries).map(function (key) { return doc.entries[key]; });
    entries.sort(function (a,b) { return Number(b.at || 0) - Number(a.at || 0); });
    var urls = [], seen = {};
    entries.slice(0,8).forEach(function (entry) {
      (entry.art || []).forEach(function (url) {
        if (url && !seen[url] && urls.length < 8) { seen[url] = true; urls.push(url); }
      });
    });

    /* Older webOS Chromium has a small per-host connection pool. Let the
       visible six-card rail load first instead of competing with a 24-image
       speculative warm-up burst. */
    urls.forEach(function (url, index) {
      window.setTimeout(function () {
        preloadArtwork(url);
      }, 1200 + index * 180);
    });
  }

  function mergeMovieDetail(item, response) {
    return Object.assign({}, item, response && response.movie_data || {}, response && response.info || {});
  }

  function getMovieRich(item) {
    var key = itemKey(item, 'movie');
    if (V.detailCache[key]) return Promise.resolve(V.detailCache[key]);
    var persisted = mediaCacheGet('movie', idOf(item, 'movie'));
    if (persisted) {
      var cachedRich = mergeMovieDetail(item, persisted);
      V.detailCache[key] = cachedRich;
      boundCache(V.detailCache, 60);
      preloadArtwork(firstBackdrop(cachedRich));
      return Promise.resolve(cachedRich);
    }
    if (V.richPrefetchPromises[key]) return V.richPrefetchPromises[key];
    if (!App.api || !App.api.getVodInfo) return Promise.resolve(item);
    var session = App.sessionGeneration;
    var request;
    try { request = App.api.getVodInfo(idOf(item, 'movie')); } catch (error) { return Promise.resolve(item); }
    V.richPrefetchPromises[key] = request.then(function (response) {
      if (session !== App.sessionGeneration) return item;
      mediaCachePut('movie', idOf(item, 'movie'), response);
      var rich = mergeMovieDetail(item, response);
      V.detailCache[key] = rich;
      boundCache(V.detailCache, 60);
      preloadArtwork(firstBackdrop(rich));
      delete V.richPrefetchPromises[key];
      return rich;
    }).catch(function () {
      if (session !== App.sessionGeneration) return item;
      delete V.richPrefetchPromises[key];
      return item;
    });
    return V.richPrefetchPromises[key];
  }

  function preloadArtwork(url) {
    url = artworkUrl(url);
    if (!url || V.artworkPreload[url] === 'loading' || V.artworkPreload[url] === 'ready') return;
    V.artworkPreload[url] = 'loading';
    var image = new Image();
    V.artworkImageCache[url] = image;
    V.artworkImageOrder.push(url);

    while (V.artworkImageOrder.length > 18) {
      var evicted = V.artworkImageOrder.shift();
      if (evicted && evicted !== url) delete V.artworkImageCache[evicted];
    }

    image.onload = function () {
      V.artworkPreload[url] = 'ready';
    };
    image.onerror = function () {
      /* Do not permanently blacklist transient failures for the app session. */
      delete V.artworkPreload[url];
      delete V.artworkImageCache[url];
    };
    image.src = url;
  }



function ensureRootMediaBackdrop(kind) {
  var app = document.getElementById('app');
  if (!app) return null;

  var host = document.getElementById('v07RootMediaBackdrop');
  if (!host) {
    host = document.createElement('div');
    host.id = 'v07RootMediaBackdrop';
    host.className = 'v07-root-media-backdrop';
    host.setAttribute('aria-hidden', 'true');
    host.innerHTML =
      '<div class="v07-root-media-backdrop-layer"></div>' +
      '<div class="v07-root-media-backdrop-scrim"></div>';
    app.insertBefore(host, app.firstChild);
  }

  host.setAttribute('data-media-kind', kind || '');
  host.classList.add('is-active');
  return host;
}

function hideRootMediaBackdrop() {
  var host = document.getElementById('v07RootMediaBackdrop');
  if (!host) return;
  host.classList.remove('is-active');
  host.removeAttribute('data-media-kind');
  host.removeAttribute('data-backdrop-request');
}

function setRootCanvasBackdrop(kind, url) {
  var host = ensureRootMediaBackdrop(kind);
  if (!host) return;
  var primary = host.querySelector('.v07-root-media-backdrop-layer');
  if (!primary) return;

  url = artworkUrl(url);
  if (!url) {
    primary.style.backgroundImage = '';
    primary.classList.remove('is-visible');
    host.classList.remove('has-art');
    host.classList.remove('is-active');
    host.removeAttribute('data-backdrop-url');
    host.removeAttribute('data-backdrop-request');
    return;
  }

  host.setAttribute('data-media-kind', kind || '');
  host.setAttribute('data-backdrop-request', url);

  if (host.getAttribute('data-backdrop-url') === url && host.classList.contains('has-art')) {
    primary.classList.add('is-visible');
    host.classList.add('is-active');
    return;
  }

  var image = new Image();
  image.onload = function () {
    if (host.getAttribute('data-backdrop-request') !== url) return;
    V.artworkPreload[url] = 'ready';
    primary.style.backgroundImage = 'url(' + JSON.stringify(url) + ')';
    primary.classList.add('is-visible');
    host.classList.add('has-art');
    host.classList.add('is-active');
    host.setAttribute('data-backdrop-url', url);
    host.setAttribute('data-media-kind', kind || '');
  };
  image.onerror = function () {
    if (host.getAttribute('data-backdrop-request') !== url) return;
    V.artworkPreload[url] = 'failed';
  };
  image.src = url;
}

function applyMediaBackdrop(kind, url, generation) {
  if (App.currentView !== kind || generation !== V.backdropGeneration) return;

  /* Browse artwork is rendered at #app level so it can fade continuously
     through the navigation boundary instead of being clipped by .main. */
  setRootCanvasBackdrop(kind, url);
  V.backdropUrl[kind] = artworkUrl(url) || '';
}

function queueMediaBackdrop(kind, item, rich) {
    clearTimeout(V.backdropTimer);
    var source = rich || item;
    var url = backdropImageOf(source) || backdropImageOf(item);
    var generation = ++V.backdropGeneration;

    /* Movies often expose their backdrop only from get_vod_info. Keep the
       previous art visible while that lightweight detail request is pending
       instead of flashing a stretched portrait poster into the hero. */
    if (!url && kind === 'movies' && !rich) return;

    V.backdropTimer = setTimeout(function () {
      if (generation !== V.backdropGeneration || App.currentView !== kind) return;
      applyMediaBackdrop(kind, url, generation);
    }, rich ? 35 : 170);
  }

  function preloadAdjacentBackdrops(kind, index) {
    clearTimeout(V.adjacentBackdropTimer);
    V.adjacentBackdropTimer = setTimeout(function () {
      if (App.currentView !== kind || Number(V.mediaIndex[kind] || 0) !== Number(index)) return;
      var items = App.currentFilteredItems[kind] || [];
      if (kind === 'series') {
        [-3, -2, -1, 1, 2, 3].forEach(function (delta) {
          var seriesItem = items[index + delta];
          if (seriesItem) preloadArtwork(backdropImageOf(seriesItem));
        });
        return;
      }
      if (kind === 'movies') {
        [-1, 1].forEach(function (delta) {
          var movie = items[index + delta];
          if (!movie) return;
          getMovieRich(movie).then(function (rich) {
            if (App.currentView === kind) preloadArtwork(firstBackdrop(rich));
          });
        });
      }
    }, kind === 'movies' ? 700 : 420);
  }

  function updateHero(kind, item, rich) {
    var type = typeForKind(kind);
    var hero = document.getElementById('v07MediaHero');
    if (!hero || !item) return;
    var merged = rich || item;
    var favorite = Store.isFavorite && Store.isFavorite(type, idOf(item, type));
    var description = descriptionOf(merged) || descriptionOf(item) || 'No description supplied by this provider.';
    var actionLabel = type === 'series' ? 'View Episodes' : 'Play Now';
    var active = document.activeElement;
    var restoreHeroFocusId = active && hero.contains(active) && active.id ? active.id : '';

    queueMediaBackdrop(kind, item, rich);

    hero.innerHTML =
      '<div class="v07-media-hero-inner v07-media-hero-inner--immersive">' +
        '<div class="v07-hero-primary">' +
          '<h2>' + esc(titleOf(merged) || titleOf(item)) + '</h2>' +
          '<div class="v07-meta-row">' + metaChips(merged, type) + '</div>' +
          '<div class="v07-hero-description"><p>' + esc(description) + '</p></div>' +
          (type === 'movie' ? movieFactsHtml(merged) : '') +
          '<div class="v07-hero-actions">' +
            '<button class="primary-button focusable" id="v07HeroPlay">' + heroActionIconHtml(type) + '<span>' + esc(actionLabel) + '</span></button>' +
            '<button class="secondary-button focusable" id="v07HeroFavorite">' + (favorite ? '♥ Favorited' : '♡ Favorite') + '</button>' +
          '</div>' +
        '</div>' +
      '</div>';

    var play = document.getElementById('v07HeroPlay');
    var favoriteButton = document.getElementById('v07HeroFavorite');
    if (play) play.addEventListener('click', function () {
      if (type === 'series') App.openSeriesDetail(item);
      else App.playMedia(merged, 'movie');
    });
    if (favoriteButton) favoriteButton.addEventListener('click', function () {
      var snapshot = favoriteFocusSnapshot(document.activeElement || favoriteButton);
      var added = Store.toggleFavorite(item, type);
      App.state = Store.getState();
      App.toast(added ? 'Added to favorites' : 'Removed from favorites');
      updateHero(kind, item, merged);
      renderMediaRail(kind, false);
      if (String(App.activeCategory[kind]) === '__favorites__') {
        App.currentItems[kind] = virtualItems(kind, '__favorites__');
        App.updateCatalogGrid(kind);
      }
      restoreFavoriteFocusDeferred(snapshot);
    });

    if (restoreHeroFocusId) {
      var restore = document.getElementById(restoreHeroFocusId);
      if (restore) restore.focus();
    }
  }

  function requestRichHero(kind, item) {
    clearTimeout(V.heroTimer);
    var type = typeForKind(kind);
    var key = itemKey(item, type);
    var generation = ++V.heroGeneration;

    if (type === 'series') return;

    var persisted = V.detailCache[key] || null;
    if (!persisted) {
      var stored = mediaCacheGet('movie', idOf(item, 'movie'));
      if (stored) {
        persisted = mergeMovieDetail(item, stored);
        V.detailCache[key] = persisted;
        boundCache(V.detailCache, 60);
      }
    }
    if (persisted) {
      updateHero(kind, item, persisted);
      preloadArtwork(firstBackdrop(persisted));
      return;
    }

    V.heroTimer = setTimeout(function () {
      getMovieRich(item).then(function (rich) {
        if (generation !== V.heroGeneration || App.currentView !== kind) return;
        updateHero(kind, item, rich);
      });
    }, 140);
  }


function bindMediaPosterButton(kind, rail, button) {
  if (!button || button.__v07MediaBound) return;
  button.__v07MediaBound = true;
  armPosterImageWatchdog(button);

  button.addEventListener('focus', function () {
    var next = Number(button.dataset.v07MediaIndex);
    if (!isFinite(next) || next < 0 || next === V.mediaIndex[kind]) return;
    var items = App.currentFilteredItems[kind] || [];
    var type = typeForKind(kind);
    var item = items[next];
    if (!item) return;

    V.mediaIndex[kind] = next;
    V.selectedKey[kind] = itemKey(item, type);
    Array.prototype.forEach.call(rail.querySelectorAll('.v07-poster-card'), function (card) {
      card.classList.toggle('selected', Number(card.dataset.v07MediaIndex) === next);
    });
    updateHero(kind, item);
    requestRichHero(kind, item);
    preloadAdjacentBackdrops(kind, next);
    preloadAdjacentPosters(kind, next);
  });

  button.addEventListener('click', function () {
    var clickedIndex = Number(button.dataset.v07MediaIndex);
    if (!isFinite(clickedIndex) || clickedIndex < 0) return;
    var items = App.currentFilteredItems[kind] || [];
    var type = typeForKind(kind);
    var item = items[clickedIndex];
    if (!item) return;
    if (type === 'movie') {
      V.movieHeroOriginIndex = clickedIndex;
      var play = document.getElementById('v07HeroPlay');
      if (play) play.focus();
      return;
    }
    App.openContent(item, type);
  });
}

function createMediaPosterButton(kind, rail, item, type, index, selected) {
  var shell = document.createElement('div');
  shell.innerHTML = posterButtonHtml(item, type, index, selected);
  var button = shell.firstElementChild || shell.firstChild;
  bindMediaPosterButton(kind, rail, button);
  return button;
}

function updateMediaPosterDecoration(button, item, type, index, selected) {
  if (!button || !item) return;
  button.classList.toggle('selected', !!selected);
  button.setAttribute('aria-label', titleOf(item));
  button.setAttribute('title', titleOf(item));
  var favorite = Store.isFavorite && Store.isFavorite(type, idOf(item, type));
  var mark = button.querySelector('.v07-favorite-mark');
  if (favorite && !mark) {
    mark = document.createElement('span');
    mark.className = 'v07-favorite-mark';
    mark.textContent = '♥';
    button.appendChild(mark);
  } else if (!favorite && mark && mark.parentNode) {
    mark.parentNode.removeChild(mark);
  }
}

function preloadAdjacentPosters(kind, index) {
  var items = App.currentFilteredItems[kind] || [];
  if (!items.length) return;
  var type = typeForKind(kind);
  var range = V.mediaRailWindow[kind] || mediaWindow(kind, items, index);
  var timers = V.posterPrefetchTimers[kind] || [];

  timers.forEach(function (timer) { clearTimeout(timer); });
  timers.length = 0;

  /* The mounted six posters are already high-priority DOM image requests.
     Prefetch only outside that window, after visible cards get first use of
     the provider connection pool. */
  [range.start - 2, range.start - 1, range.end, range.end + 1].forEach(function (itemIndex, order) {
    if (itemIndex < 0 || itemIndex >= items.length) return;
    var item = items[itemIndex];
    timers.push(window.setTimeout(function () {
      if (App.currentView !== kind) return;
      preloadArtwork(posterImageOf(item, type));
    }, 240 + order * 120));
  });

  V.posterPrefetchTimers[kind] = timers;
}

function rebuildMediaRail(kind, rail, items, type, windowRange, index) {
  var html = '';
  for (var i = windowRange.start; i < windowRange.end; i += 1) {
    html += posterButtonHtml(items[i], type, i, i === index);
  }
  rail.innerHTML = html;
  Array.prototype.forEach.call(rail.querySelectorAll('[data-v07-media-index]'), function (button) {
    bindMediaPosterButton(kind, rail, button);
  });
}

  function selectMediaIndex(kind, index, focus) {
    var items = App.currentFilteredItems[kind] || [];
    if (!items.length) return;
    index = Math.max(0, Math.min(items.length - 1, Number(index) || 0));
    V.mediaIndex[kind] = index;
    if (kind === 'movies') V.movieHeroOriginIndex = null;
    var type = typeForKind(kind);
    var item = items[index];
    V.selectedKey[kind] = itemKey(item, type);

    var rail = document.getElementById('v07PosterRail');
    var target = rail && rail.querySelector('[data-v07-media-index="' + index + '"]');
    if (!target) {
      renderMediaRail(kind, focus);
    } else {
      Array.prototype.forEach.call(rail.querySelectorAll('.v07-poster-card'), function (card) {
        card.classList.toggle('selected', Number(card.dataset.v07MediaIndex) === index);
      });
      if (focus) target.focus();
    }

    updateHero(kind, item);
    requestRichHero(kind, item);
    preloadAdjacentBackdrops(kind, index);
    preloadAdjacentPosters(kind, index);
  }


function renderMediaRail(kind, focus) {
  var items = App.currentFilteredItems[kind] || [];
  var rail = document.getElementById('v07PosterRail');
  if (!rail) return;
  if (!items.length) {
    rail.innerHTML = '<div class="v07-empty">Nothing to show in this category.</div>';
    V.mediaRailWindow[kind] = null;
    return;
  }

  var index = Math.max(0, Math.min(items.length - 1, V.mediaIndex[kind] || 0));
  var type = typeForKind(kind);
  var previous = V.mediaRailWindow[kind];
  var windowRange = mediaWindow(kind, items, index);
  var existing = rail.querySelectorAll('.v07-poster-card');
  var expectedSize = windowRange.end - windowRange.start;
  var shiftedInPlace = false;

  /* When the selected item crosses a six-poster window boundary, keep the
     overlapping poster elements mounted and replace only the one edge item.
     This prevents webOS from blanking/re-decoding the entire rail. */
  if (previous && existing.length === expectedSize && (previous.end - previous.start) === expectedSize) {
    if (windowRange.start === previous.start + 1 && windowRange.end === previous.end + 1) {
      var first = rail.querySelector('.v07-poster-card');
      if (first && first.parentNode === rail) rail.removeChild(first);
      var newRightIndex = windowRange.end - 1;
      var rightButton = createMediaPosterButton(kind, rail, items[newRightIndex], type, newRightIndex, newRightIndex === index);
      rail.appendChild(rightButton);
      shiftedInPlace = true;
    } else if (windowRange.start === previous.start - 1 && windowRange.end === previous.end - 1) {
      var cards = rail.querySelectorAll('.v07-poster-card');
      var last = cards.length ? cards[cards.length - 1] : null;
      if (last && last.parentNode === rail) rail.removeChild(last);
      var newLeftIndex = windowRange.start;
      var leftButton = createMediaPosterButton(kind, rail, items[newLeftIndex], type, newLeftIndex, newLeftIndex === index);
      rail.insertBefore(leftButton, rail.firstChild || null);
      shiftedInPlace = true;
    } else if (windowRange.start === previous.start && windowRange.end === previous.end) {
      shiftedInPlace = true;
    }
  }

  if (!shiftedInPlace) {
    rebuildMediaRail(kind, rail, items, type, windowRange, index);
  }

  Array.prototype.forEach.call(rail.querySelectorAll('.v07-poster-card'), function (button) {
    var buttonIndex = Number(button.dataset.v07MediaIndex);
    var buttonItem = items[buttonIndex];
    if (buttonItem) updateMediaPosterDecoration(button, buttonItem, type, buttonIndex, buttonIndex === index);
  });

  V.mediaRailWindow[kind] = { start:windowRange.start, end:windowRange.end };
  preloadAdjacentPosters(kind, index);

  if (focus) {
    /* Focus before yielding the event loop. When the six-card window shifts,
       delaying this until setTimeout lets webOS paint the previously focused
       card one slot to the left/right for a frame, which looks like a bounce. */
    var focusTarget = rail.querySelector('[data-v07-media-index="' + index + '"]');
    if (focusTarget) focusTarget.focus();
  }
  if (window.XtreamlyTVNavigation) XtreamlyTVNavigation.invalidate();
}

function updateMediaCatalog(kind) {
    var config = App.catalogConfig(kind);
    clearCatalogActivity();
    var status = document.getElementById('catalogStatus');
    if (status) status.innerHTML = '';
    var items = App.filteredCatalogItems(kind);
    App.currentFilteredItems[kind] = items;

    var count = document.querySelector('.result-count');
    if (count) count.textContent = items.length.toLocaleString() + ' ' + config.plural;

    App.destroyVirtualGrid();
    var oldKey = V.selectedKey[kind];
    var type = typeForKind(kind);
    var index = currentItemByKey(items, type, oldKey);
    if (index < 0) index = Math.min(V.mediaIndex[kind] || 0, Math.max(0, items.length - 1));
    V.mediaIndex[kind] = index;

    var rail = document.getElementById('v07PosterRail');
    var hero = document.getElementById('v07MediaHero');
    if (!items.length) {
      clearTimeout(V.backdropTimer);
      V.backdropGeneration += 1;
      applyMediaBackdrop(kind, '', V.backdropGeneration);
      if (hero) hero.innerHTML = '<div class="v07-empty v07-empty-hero"><strong>No results</strong><span>Try another category or change the search.</span></div>';
      if (rail) rail.innerHTML = '';
      V.mediaRailWindow[kind] = null;
      if (App.pendingCatalogFirstFocus && App.pendingCatalogFirstFocus[kind]) {
        App.pendingCatalogFirstFocus[kind] = false;
        setTimeout(focusCategory, 0);
      }
      return;
    }

    V.selectedKey[kind] = itemKey(items[index], type);
    renderMediaRail(kind, false);
    updateHero(kind, items[index]);
    requestRichHero(kind, items[index]);
    preloadAdjacentBackdrops(kind, index);

    if (App.pendingCatalogFirstFocus && App.pendingCatalogFirstFocus[kind]) {
      App.pendingCatalogFirstFocus[kind] = false;
      renderMediaRail(kind, true);
    }
  }

  function renderMediaCatalog(kind) {
    ensureRootMediaBackdrop(kind);
    warmPersistentArtwork();
    V.mediaRailWindow[kind] = null;
    stopTimeline();
    if (kind === 'series' && !App.seriesCategories.length && App.seriesDiscovery === 'idle') {
      App.ensureSeriesDiscovery(false);
    }

    var config = App.catalogConfig(kind);
    var categories = categoriesWithVirtuals(kind);
    var active = ensureActiveCategory(kind, categories);
    var enterContentAfterLoad = !!(App.pendingCatalogFirstFocus && App.pendingCatalogFirstFocus[kind]);
    var view = document.getElementById('view');
    if (!view) return;

    App.destroyCategoryRail();
    App.destroyVirtualGrid();
    view.innerHTML =
      '<div class="v07-library v07-media-browser catalog-' + kind + '">' +
        '<aside id="categoryList" class="category-list v07-category-wheel" aria-label="' + esc(config.plural) + ' categories"></aside>' +
        '<section class="v07-media-stage">' +
          '<div id="v07MediaBackdrop" class="v07-media-backdrop" aria-hidden="true">' +
            '<div class="v07-media-backdrop-layer" data-v07-backdrop-layer="a"></div>' +
            '<div class="v07-media-backdrop-layer" data-v07-backdrop-layer="b"></div>' +
            '<div class="v07-media-backdrop-scrim"></div>' +
          '</div>' +
          '<div class="v07-browser-tools">' +
            '<span class="result-count">0 ' + esc(config.plural) + '</span>' +
            '<div class="v07-tool-left"><div id="v07CatalogActivity" class="v07-catalog-activity"></div></div>' +
          '</div>' +
          '<div id="catalogStatus" class="catalog-status"></div>' +
          '<section id="v07MediaHero" class="v07-media-hero"></section>' +
          '<div class="v07-poster-rail-wrap"><div id="v07PosterRail" class="v07-poster-rail"></div></div>' +
          '<div id="catalogGrid" class="v07-catalog-compat" aria-hidden="true"></div>' +
        '</section>' +
      '</div>';

    installCategoryRail(kind, categories, active);
    patchTopNavDecorations();

    var virtual = virtualItems(kind, active);
    if (virtual !== null) {
      App.currentItems[kind] = App.normalizeCatalogItems(kind, virtual);
      App.indexItems(kind, App.currentItems[kind]);
      App.updateCatalogGrid(kind);
    } else if (App.catalogCache[kind][active]) {
      App.currentItems[kind] = App.catalogCache[kind][active];
      App.updateCatalogGrid(kind);
    } else if (active) {
      loadActiveCategory(kind, active).catch(function () {});
    } else {
      App.showCatalogError(kind, 'No categories were returned by this provider.');
    }

    if (!enterContentAfterLoad && App.categoryRail && active && App.categoryRail.focusActive) App.categoryRail.focusActive();
  }

  function alignHalfHour(timestamp) {
    var d = new Date(timestamp);
    d.setSeconds(0, 0);
    d.setMinutes(d.getMinutes() < 30 ? 0 : 30);
    return d.getTime();
  }

  function formatClock(timestamp) {
    return new Date(timestamp).toLocaleTimeString([], { hour:'numeric', minute:'2-digit' });
  }

  function epgEntryStart(entry) {
    var value = Number(entry && (entry.start_timestamp || entry.start || entry.start_time));
    if (!isFinite(value)) return 0;
    return value < 100000000000 ? value * 1000 : value;
  }

  function epgEntryStop(entry) {
    var value = Number(entry && (entry.stop_timestamp || entry.end_timestamp || entry.stop || entry.end));
    if (!isFinite(value)) return 0;
    return value < 100000000000 ? value * 1000 : value;
  }

  function epgEntryTitle(entry) {
    return String(entry && (entry.title || entry.name) || 'Programming');
  }

  function programBlocks(entries, start, end) {
    var candidates = [];
    var seen = Object.create(null);
    var span = Math.max(1, end - start);

    (entries || []).forEach(function (entry) {
      var a = epgEntryStart(entry);
      var b = epgEntryStop(entry);
      if (!a || !b || b <= a || b <= start || a >= end) return;

      var title = epgEntryTitle(entry);
      var visibleStart = Math.max(start, a);
      var visibleEnd = Math.min(end, b);
      if (visibleEnd <= visibleStart) return;

      /* Xtream providers occasionally return duplicate or overlapping short-EPG
         records for the same channel. Absolute-positioning both records paints
         their titles on top of one another. De-duplicate exact records here,
         then resolve any remaining overlap before producing the timeline. */
      var key = String(a) + '|' + String(b) + '|' + title;
      if (seen[key]) return;
      seen[key] = true;

      candidates.push({
        entry:entry,
        title:title,
        start:a,
        stop:b,
        visibleStart:visibleStart,
        visibleEnd:visibleEnd
      });
    });

    candidates.sort(function (left, right) {
      return left.visibleStart - right.visibleStart ||
        right.visibleEnd - left.visibleEnd ||
        left.title.localeCompare(right.title);
    });

    var blocks = [];
    var occupiedUntil = start;

    candidates.forEach(function (item) {
      /* Keep the first schedule record for an occupied interval. If a later
         provider record extends past it, expose only the non-overlapping tail.
         This prevents malformed provider data from stacking program cards. */
      var visibleStart = Math.max(item.visibleStart, occupiedUntil);
      var visibleEnd = item.visibleEnd;
      if (visibleEnd <= visibleStart) return;

      var left = ((visibleStart - start) / span) * 100;
      var width = ((visibleEnd - visibleStart) / span) * 100;
      if (width <= 0) return;

      blocks.push('<div class="v07-program" style="left:' + left.toFixed(3) + '%;width:' + width.toFixed(3) + '%" title="' + esc(item.title) + '">' +
        '<strong>' + esc(item.title) + '</strong><span>' + esc(formatClock(item.start)) + ' – ' + esc(formatClock(item.stop)) + '</span></div>');

      occupiedUntil = Math.max(occupiedUntil, visibleEnd);
    });

    return blocks.join('');
  }

  function fillEpgTrack(channel, generation, start, end) {
    var id = idOf(channel, 'live');
    var session = App.sessionGeneration;
    var cached = V.epgCache[id];
    var fresh = cached && Date.now() - cached.at < 300000;

    function paint(entries) {
      if (session !== App.sessionGeneration || generation !== V.epgGeneration || App.currentView !== 'live') return;
      var selector = '[data-v07-epg-id="' + String(id).replace(/"/g, '\\"') + '"]';
      var current = document.querySelector(selector);
      if (!current) return;
      var html = programBlocks(entries, start, end);
      current.innerHTML = html || '<div class="v07-no-guide">No guide data</div>';
    }

    if (fresh) {
      paint(cached.entries);
      return;
    }
    if (!App.api || !App.api.getShortEpg) {
      paint([]);
      return;
    }
    try {
      V.epgPending = V.epgPending || {};
      if (!V.epgPending[id]) {
        V.epgPending[id] = App.api.getShortEpg(channel.stream_id).then(function (entries) {
          if (session === App.sessionGeneration) delete V.epgPending[id];
          return entries;
        }, function (error) { if (session === App.sessionGeneration) delete V.epgPending[id]; throw error; });
      }
      V.epgPending[id].then(function (entries) {
        if (session !== App.sessionGeneration) return;
        V.epgCache[id] = { at:Date.now(), entries:entries || [] };
        boundCache(V.epgCache, 80);
        paint(entries || []);
      }).catch(function () { paint([]); });
    } catch (error) { paint([]); }
  }

  function liveWindow(items, index) {
    if (!items.length) return { start:0, end:0 };
    var size = Math.min(V.liveWindowSize, items.length);
    var half = Math.floor(size / 2);
    var start = Math.max(0, index - half);
    var end = Math.min(items.length, start + size);
    start = Math.max(0, end - size);
    return { start:start, end:end };
  }

  function stationRowHtml(item, index) {
    var id = idOf(item, 'live');
    var favorite = Store.isFavorite && Store.isFavorite('live', id);
    return '<div class="v07-guide-row">' +
      '<button class="v07-station focusable" data-v07-live-index="' + index + '" data-content-type="live" data-content-id="' + esc(id) + '">' +
        logoHtml(item, true) +
        '<div class="v07-station-copy"><strong><span>' + esc(titleOf(item)) + '</span></strong><span>' + (favorite ? '♥ Favorite' : 'CH ' + esc(item.num || '—')) + '</span></div>' +
      '</button>' +
      '<div class="v07-program-track" data-v07-epg-id="' + esc(id) + '"><div class="v07-guide-loading"></div></div>' +
    '</div>';
  }

  function updateStationMarquee(button, enabled) {
    var label = button && button.querySelector('.v07-station-copy strong');
    var text = label && label.querySelector('span');
    if (!label || !text) return;
    label.classList.remove('is-overflowing');
    label.style.removeProperty('--v07-marquee-distance');
    label.style.removeProperty('--v07-marquee-duration');
    if (!enabled) return;
    requestAnimationFrame(function () {
      if (!button.matches(':focus')) return;
      var distance = Math.max(0, text.scrollHeight - label.clientHeight);
      if (distance > 1) {
        label.style.setProperty('--v07-marquee-distance', distance + 'px');
        label.style.setProperty('--v07-marquee-duration', Math.max(4, distance / 28) + 's');
        label.classList.add('is-overflowing');
      }
    });
  }

  function renderLiveWindow(focus) {
    var items = App.currentFilteredItems.live || [];
    var host = document.getElementById('v07GuideRows');
    if (!host) return;
    var activeElement = document.activeElement;
    var restoreIndex = activeElement && host.contains(activeElement) && activeElement.hasAttribute('data-v07-live-index')
      ? Number(activeElement.dataset.v07LiveIndex) : null;
    if (!items.length) {
      host.innerHTML = '<div class="v07-empty"><strong>No channels</strong><span>Try another category or change the search.</span></div>';
      return;
    }

    V.liveIndex = Math.max(0, Math.min(items.length - 1, V.liveIndex || 0));
    var windowRange = liveWindow(items, V.liveIndex);
    var html = '';
    for (var i = windowRange.start; i < windowRange.end; i += 1) html += stationRowHtml(items[i], i);
    host.innerHTML = html;

    var generation = ++V.epgGeneration;
    var start = alignHalfHour(Date.now());
    var end = start + 2 * 60 * 60 * 1000;
    items.slice(windowRange.start, windowRange.end).forEach(function (channel) {
      fillEpgTrack(channel, generation, start, end);
    });

    Array.prototype.forEach.call(host.querySelectorAll('[data-v07-live-index]'), function (button) {
      button.addEventListener('focus', function () {
        var index = Number(button.dataset.v07LiveIndex);
        V.liveIndex = index;
        V.selectedKey.live = itemKey(items[index], 'live');
        updateStationMarquee(button, true);
      });
      button.addEventListener('blur', function () { updateStationMarquee(button, false); });
      button.addEventListener('click', function () {
        var channel = items[Number(button.dataset.v07LiveIndex)];
        if (channel) App.playMedia(channel, 'live', items);
      });
    });

    if (window.XtreamlyTVNavigation) XtreamlyTVNavigation.invalidate();
    if (focus || restoreIndex !== null) {
      var targetIndex = focus ? V.liveIndex : restoreIndex;
      var target = host.querySelector('[data-v07-live-index="' + targetIndex + '"]');
      if (target) target.focus();
    }
  }

  function selectLiveIndex(index, focus) {
    var items = App.currentFilteredItems.live || [];
    if (!items.length) return;
    index = Math.max(0, Math.min(items.length - 1, Number(index) || 0));
    V.liveIndex = index;
    V.selectedKey.live = itemKey(items[index], 'live');

    var host = document.getElementById('v07GuideRows');
    var target = host && host.querySelector('[data-v07-live-index="' + index + '"]');
    if (!target) {
      renderLiveWindow(focus);
    } else if (focus) {
      target.focus();
    }
  }

  function updateLiveGuide() {
    clearCatalogActivity();
    var items = App.filteredCatalogItems('live');
    App.currentFilteredItems.live = items;
    var count = document.querySelector('.result-count');
    if (count) count.textContent = items.length.toLocaleString() + ' channels';
    var liveCount = document.getElementById('v07LiveChannelCount');
    if (liveCount) liveCount.textContent = items.length.toLocaleString() + (items.length === 1 ? ' CHANNEL' : ' CHANNELS');

    var index = currentItemByKey(items, 'live', V.selectedKey.live);
    if (index < 0) index = Math.min(V.liveIndex || 0, Math.max(0, items.length - 1));
    V.liveIndex = index;
    if (items.length) V.selectedKey.live = itemKey(items[index], 'live');

    if (!items.length) {
      renderLiveWindow(false);
      if (App.pendingCatalogFirstFocus && App.pendingCatalogFirstFocus.live) {
        App.pendingCatalogFirstFocus.live = false;
        setTimeout(focusCategory, 0);
      }
      return;
    }

    renderLiveWindow(!!(App.pendingCatalogFirstFocus && App.pendingCatalogFirstFocus.live));
    if (App.pendingCatalogFirstFocus) App.pendingCatalogFirstFocus.live = false;
  }

  function refreshTimeline() {
    var host = document.getElementById('v07GuideTimes');
    var guide = document.querySelector('.v07-guide');
    var nowLine = document.getElementById('v07NowLine');
    if (!host || !guide || !nowLine) return;

    var start = alignHalfHour(Date.now());
    var html = '';
    for (var i = 0; i < 4; i += 1) {
      html += '<div class="v07-guide-time">' + esc(formatClock(start + i * 30 * 60 * 1000)) + '</div>';
    }
    host.innerHTML = html;

    var ratio = Math.max(0, Math.min(1, (Date.now() - start) / (2 * 60 * 60 * 1000)));
    var stationWidth = 260;
    var header = guide.querySelector('.v07-station-header');
    if (header && header.offsetWidth) stationWidth = header.offsetWidth;
    var timelineWidth = Math.max(0, guide.clientWidth - stationWidth);
    nowLine.style.left = Math.round(stationWidth + timelineWidth * ratio) + 'px';
  }

  function renderTimeline() {
    stopTimeline();
    refreshTimeline();
    V.timelineTimer = setInterval(function () {
      if (App.currentView !== 'live') {
        stopTimeline();
        return;
      }
      refreshTimeline();
      renderLiveWindow(false);
    }, 30000);
  }

  function renderLiveCatalog() {
    var kind = 'live';
    var config = App.catalogConfig(kind);
    var categories = categoriesWithVirtuals(kind);
    var active = ensureActiveCategory(kind, categories);
    var enterContentAfterLoad = !!(App.pendingCatalogFirstFocus && App.pendingCatalogFirstFocus.live);
    var view = document.getElementById('view');
    if (!view) return;

    App.destroyCategoryRail();
    App.destroyVirtualGrid();
    view.innerHTML =
      '<div class="v07-library v07-live-browser catalog-live">' +
        '<aside id="categoryList" class="category-list v07-category-wheel" aria-label="Live TV categories"></aside>' +
        '<section class="v07-guide-stage">' +
          '<div id="catalogStatus" class="catalog-status"></div>' +
          '<div class="v07-guide">' +
            '<div class="v07-guide-header"><div id="v07LiveChannelCount" class="v07-station-header">0 CHANNELS</div><div class="v07-guide-time-area"><div id="v07GuideTimes" class="v07-guide-times"></div></div></div>' +
            '<div id="v07GuideRows" class="v07-guide-rows"></div>' +
            '<div id="v07NowLine" class="v07-now-line" aria-hidden="true"></div>' +
          '</div>' +
          '<div id="catalogGrid" class="v07-catalog-compat" aria-hidden="true"></div>' +
        '</section>' +
      '</div>';

    installCategoryRail(kind, categories, active);
    patchTopNavDecorations();
    renderTimeline();

    var virtual = virtualItems(kind, active);
    if (virtual !== null) {
      App.currentItems.live = App.normalizeCatalogItems('live', virtual);
      App.indexItems('live', App.currentItems.live);
      App.updateCatalogGrid('live');
    } else if (App.catalogCache.live[active]) {
      App.currentItems.live = App.catalogCache.live[active];
      App.updateCatalogGrid('live');
    } else if (active) {
      loadActiveCategory('live', active).catch(function () {});
    } else {
      App.showCatalogError('live', 'No Live TV categories were returned by this provider.');
    }

    if (!enterContentAfterLoad && App.categoryRail && active && App.categoryRail.focusActive) App.categoryRail.focusActive();
  }

  var HOME_LIVE_BACKDROP = 'assets/home-live-backdrop.jpg';

  function homeVisualSource(item, type) {
    if (!item) return { item:null, type:type || 'live' };
    var visualType = type === 'episode' ? 'series' : type;
    var visualItem = type === 'episode' && item.parent_series ? item.parent_series : item;

    if (visualType === 'movie') {
      var movieId = idOf(visualItem, 'movie');
      var movieResolved = App.resolveItem && App.resolveItem('movie', movieId);
      if (movieResolved) visualItem = Object.assign({}, movieResolved, visualItem);
      var movieCached = mediaCacheGet('movie', movieId);
      if (movieCached) visualItem = mergeMovieDetail(visualItem, movieCached);
    } else if (visualType === 'series') {
      var seriesId = idOf(visualItem, 'series');
      var seriesResolved = App.resolveItem && App.resolveItem('series', seriesId);
      if (seriesResolved) visualItem = Object.assign({}, seriesResolved, visualItem);
      var seriesCached = mediaCacheGet('series', seriesId);
      if (seriesCached && seriesCached.info) visualItem = Object.assign({}, visualItem, seriesCached.info);
    }

    return { item:visualItem, type:visualType };
  }

  function homeBackdropFor(item, type) {
    if (type === 'live') return HOME_LIVE_BACKDROP;
    return backdropImageOf(item);
  }

  function homeBackdropHtml(backdrop) {
    if (!backdrop) return '<div class="v07-home-backdrop" id="v07HomeBackdrop"></div>';
    return '<div class="v07-home-backdrop has-art" id="v07HomeBackdrop">' +
      '<img class="v07-home-backdrop-art" id="v07HomeBackdropArt" src="' + esc(backdrop) + '" alt="" draggable="false" ' +
      'onerror="this.parentNode.classList.remove(\'has-art\');this.style.display=\'none\';">' +
      '</div>';
  }

  function homeHeroTypeLabel(type) {
    if (type === 'movie') return 'MOVIE';
    if (type === 'series') return 'SERIES';
    if (type === 'episode') return 'SERIES';
    return 'LIVE TV';
  }

  function homeHeroArtHtml(item, type) {
    var visual = homeVisualSource(item, type);
    if (!visual.item) return '<div class="v07-home-hero-art v07-home-hero-art--empty"><span>TV</span></div>';
    if (visual.type === 'live') {
      return '<div class="v07-home-hero-art v07-home-hero-art--live">' + logoHtml(visual.item) + '</div>';
    }
    return '<div class="v07-home-hero-art v07-home-hero-art--poster">' +
      posterHtml(visual.item, visual.type, 'v07-home-hero-poster') + '</div>';
  }

  function homeCard(item, type, row) {
    var id = idOf(item, type);
    var label = titleOf(item);
    if (type === 'live') {
      return '<button class="v07-home-channel focusable" aria-label="' + esc(label) + '" title="' + esc(label) + '" ' +
        'data-home-row="' + row + '" data-content-type="live" data-content-id="' + esc(id) + '">' +
        logoHtml(item) + '<span>' + esc(label) + '</span></button>';
    }
    return '<button class="v07-home-poster focusable" aria-label="' + esc(label) + '" title="' + esc(label) + '" ' +
      'data-home-row="' + row + '" data-content-type="' + esc(type) + '" data-content-id="' + esc(id) + '">' +
      posterHtml(item, type, 'v07-home-poster-art') + '</button>';
  }

  function homeRow(title, items, type, row) {
    if (!items.length) return '';
    var posterRow = type === 'movie' || type === 'series';
    var limit = posterRow ? 7 : 6;
    return '<section class="v07-home-row-section' + (posterRow ? ' v07-home-row-section--poster' : ' v07-home-row-section--live') + '">' +
      '<h3>' + esc(title) + '</h3><div class="v07-home-row' + (posterRow ? ' v07-home-row--poster' : ' v07-home-row--live') + '">' +
      items.slice(0, limit).map(function (item) { return homeCard(item, type, row); }).join('') +
      '</div></section>';
  }

  function resolveRecentType(item) {
    var type = Store.inferType ? Store.inferType(item) : item.content_type;
    return type || 'live';
  }

  function playHomeFeatured(item, type) {
    if (!item) return;
    if (type === 'live') App.playMedia(item, 'live', [item]);
    else if (type === 'movie') App.playMedia(item, 'movie');
    else if (type === 'episode') App.playMedia(item, 'episode', [], item.parent_series || null);
    else if (type === 'series') App.openSeriesDetail(item);
  }

  function applyHomeHeroArtwork(featured, featuredType, richVisual) {
    if (App.currentView !== 'home') return;
    var visual = richVisual ? { item:richVisual, type:featuredType === 'episode' ? 'series' : featuredType } : homeVisualSource(featured, featuredType);
    var backdrop = homeBackdropFor(visual.item, visual.type);
    var backdropEl = document.getElementById('v07HomeBackdrop');
    var backdropArt = document.getElementById('v07HomeBackdropArt');
    var artEl = document.getElementById('v07HomeHeroArt');
    if (backdropEl) {
      if (backdrop) {
        backdropEl.classList.add('has-art');
        if (!backdropArt) {
          backdropArt = document.createElement('img');
          backdropArt.id = 'v07HomeBackdropArt';
          backdropArt.className = 'v07-home-backdrop-art';
          backdropArt.alt = '';
          backdropArt.draggable = false;
          backdropArt.onerror = function () {
            backdropEl.classList.remove('has-art');
            this.style.display = 'none';
          };
          backdropEl.appendChild(backdropArt);
        }
        backdropArt.style.display = '';
        if (backdropArt.getAttribute('src') !== String(backdrop)) backdropArt.setAttribute('src', String(backdrop));
      } else {
        backdropEl.classList.remove('has-art');
        if (backdropArt) {
          backdropArt.removeAttribute('src');
          backdropArt.style.display = 'none';
        }
      }
    }
    if (artEl && visual.item) {
      artEl.innerHTML = homeHeroArtHtml(visual.item, visual.type);
    }
    if (backdrop && /^https?:\/\//i.test(backdrop)) preloadArtwork(backdrop);
    var poster = posterImageOf(visual.item, visual.type);
    if (poster) preloadArtwork(poster);
  }

  function hydrateHomeFeatured(featured, featuredType) {
    if (!featured) return;
    if (featuredType === 'movie') {
      getMovieRich(featured).then(function (rich) {
        if (App.currentView !== 'home') return;
        applyHomeHeroArtwork(featured, featuredType, rich);
      });
      return;
    }
    applyHomeHeroArtwork(featured, featuredType);
  }



function scrollHomeFocusIntoView(target, forceTop) {
  var home = document.querySelector('.v07-home');
  var scroller = document.getElementById('v07HomeScroll');
  if (!home || !scroller || !target || !home.contains(target)) return;

  if (forceTop || target.id === 'v07HomeWatch') {
    if (scroller.scrollTop !== 0) scroller.scrollTop = 0;
    return;
  }

  var section = target.closest ? target.closest('.v07-home-row-section') : null;
  if (!section || !scroller.contains(section)) return;

  var scrollerRect = scroller.getBoundingClientRect();
  var sectionRect = section.getBoundingClientRect();
  var sectionTop = scroller.scrollTop + (sectionRect.top - scrollerRect.top);
  var max = Math.max(0, scroller.scrollHeight - scroller.clientHeight);
  var topPad = 18;
  var bottomPad = 24;
  var next = scroller.scrollTop;

  if (sectionRect.height + topPad + bottomPad <= scroller.clientHeight) {
    if (sectionRect.top < scrollerRect.top + topPad) {
      next = sectionTop - topPad;
    } else if (sectionRect.bottom > scrollerRect.bottom - bottomPad) {
      next = sectionTop - Math.max(topPad, scroller.clientHeight - sectionRect.height - bottomPad);
    }
  } else {
    next = sectionTop - topPad;
  }

  next = Math.max(0, Math.min(max, Math.round(next)));
  if (Math.abs(next - scroller.scrollTop) > 1) scroller.scrollTop = next;
}

function bindHomeFocusScrolling(home) {
  if (!home) return;
  Array.prototype.forEach.call(home.querySelectorAll('.focusable'), function (button) {
    button.addEventListener('focus', function () {
      var target = button;
      if (window.requestAnimationFrame) {
        window.requestAnimationFrame(function () { scrollHomeFocusIntoView(target, false); });
      } else {
        setTimeout(function () { scrollHomeFocusIntoView(target, false); }, 0);
      }
    });
  });
}

  function renderHome() {
    stopTimeline();
    var view = document.getElementById('view');
    if (!view) return;
    warmPersistentArtwork();

    var recent = (App.state && App.state.recent || []).filter(function (item) {
      var type = resolveRecentType(item);
      if (type === 'movie') return !itemHiddenByCategory(item, 'movie');
      if (type === 'series') return !itemHiddenByCategory(item, 'series');
      if (type === 'episode') return !itemHiddenByCategory(item.parent_series || item, 'series');
      return true;
    });
    var featured = recent[0] || (firstVisibleLoadedItem('movies') || firstVisibleLoadedItem('live') || firstVisibleLoadedItem('series'));
    var featuredType = featured ? resolveRecentType(featured) : 'live';
    var featuredVisual = homeVisualSource(featured, featuredType);
    var heroBackdrop = featured ? homeBackdropFor(featuredVisual.item, featuredVisual.type) : '';
    setRootCanvasBackdrop('home', heroBackdrop);
    var heroDescription = featured ? (descriptionOf(featuredVisual.item) || descriptionOf(featured) || 'Continue where you left off.') : 'Choose Live TV, Movies, or Series from the navigation above.';
    var heroTitle = featured ? titleOf(featured) : 'Your TV, one remote away';
    var heroMeta = '';
    if (featured && featuredVisual.type !== 'live') heroMeta = metaChips(featuredVisual.item, featuredVisual.type);

    var recentLive = recent.filter(function (item) { return resolveRecentType(item) === 'live'; });
    var recentMovies = recent.filter(function (item) { return resolveRecentType(item) === 'movie'; });
    var recentSeries = [];
    var seenSeries = {};
    recent.forEach(function (item) {
      var type = resolveRecentType(item);
      var series = type === 'episode' && item.parent_series ? item.parent_series : (type === 'series' ? item : null);
      if (!series) return;
      var key = idOf(series, 'series');
      if (!key || seenSeries[key]) return;
      seenSeries[key] = true;
      recentSeries.push(homeVisualSource(series, 'series').item);
    });

    /* Parent-series objects embedded in episode recents are not guaranteed to
       have been loaded into a catalog cache yet. Register the Home objects so
       the existing resolveItem/openContent path remains deterministic. */
    if (App.itemLookup) {
      recentLive.forEach(function (item) { App.itemLookup['live:' + idOf(item, 'live')] = item; });
      recentMovies.forEach(function (item) { App.itemLookup['movie:' + idOf(item, 'movie')] = item; });
      recentSeries.forEach(function (item) { App.itemLookup['series:' + idOf(item, 'series')] = item; });
    }

    recentMovies.slice(0, 7).forEach(function (item) { preloadArtwork(posterImageOf(item, 'movie')); });
    recentSeries.slice(0, 7).forEach(function (item) { preloadArtwork(posterImageOf(item, 'series')); });
    recentLive.slice(0, 6).forEach(function (item) { preloadArtwork(posterImageOf(item, 'live')); });

    var row = 1;
    var rowsHtml = '';
    if (recentLive.length) rowsHtml += homeRow('Continue Watching Live TV', recentLive, 'live', row++);
    if (recentMovies.length) rowsHtml += homeRow('Continue Watching Movies', recentMovies, 'movie', row++);
    if (recentSeries.length) rowsHtml += homeRow('Continue Watching Series', recentSeries, 'series', row++);

    view.innerHTML =
      '<div class="v07-home">' +
        '<section class="v07-home-hero">' +
          homeBackdropHtml(heroBackdrop) +
          '<div class="v07-home-scrim"></div>' +
          '<div class="v07-home-hero-inner">' +
            '<div id="v07HomeHeroArt">' + homeHeroArtHtml(featuredVisual.item, featuredVisual.type) + '</div>' +
            '<div class="v07-home-copy">' +
              '<div class="v07-home-kicker">LAST PLAYED' + (featured ? ' &#8226; ' + homeHeroTypeLabel(featuredType) : '') + '</div>' +
              '<h2>' + esc(heroTitle) + '</h2>' +
              (heroMeta ? '<div class="v07-home-meta v07-meta-row">' + heroMeta + '</div>' : '') +
              '<p>' + esc(heroDescription) + '</p>' +
              (featured ? '<button class="primary-button focusable" data-home-row="0" id="v07HomeWatch">▶ ' +
                esc(featuredType === 'series' ? 'View Episodes' : (featuredType === 'live' ? 'Resume Live TV' : 'Resume')) + '</button>' : '') +
            '</div>' +
          '</div>' +
        '</section>' +
        '<div class="v07-home-scroll" id="v07HomeScroll">' + rowsHtml + '</div>' +
      '</div>';

    var watch = document.getElementById('v07HomeWatch');
    if (watch) watch.addEventListener('click', function () { playHomeFeatured(featured, featuredType); });

    Array.prototype.forEach.call(view.querySelectorAll('[data-content-type][data-content-id]'), function (button) {
      button.addEventListener('click', function () {
        var type = button.dataset.contentType;
        var item = App.resolveItem(type, button.dataset.contentId);
        if (item) App.openContent(item, type);
      });
    });

    bindHomeFocusScrolling(view.querySelector('.v07-home'));
    hydrateHomeFeatured(featured, featuredType);

    if (window.XtreamlyTVNavigation) {
      XtreamlyTVNavigation.invalidate();
      if (watch) setTimeout(function () { watch.focus(); }, 0);
      else XtreamlyTVNavigation.focusFirst('.v07-home .focusable');
    }
  }

  function patchFavoriteAutoSortUi() {
    if (App.currentView !== 'favorites') return;
    var enabled = favoriteAutoSortEnabled();
    var reorder = document.getElementById('reorderFavoriteItems');
    if (reorder) {
      reorder.disabled = enabled;
      reorder.setAttribute('aria-disabled', enabled ? 'true' : 'false');
      reorder.title = enabled ? 'Turn off alphabetical auto-sort to reorder items manually.' : '';
    }

    var header = document.querySelector('.favorite-manager-header');
    if (!header || document.getElementById('v07FavoriteAutoSort')) return;
    var actions = header.lastElementChild;
    if (!actions) return;
    var label = document.createElement('label');
    label.className = 'v07-favorite-sort-option';
    label.innerHTML = '<input id="v07FavoriteAutoSort" class="focusable" type="checkbox"' + (enabled ? ' checked' : '') + '><span><strong>Auto-sort A–Z</strong><small>Sort favorite items alphabetically</small></span>';
    actions.insertBefore(label, actions.firstChild);
    var checkbox = document.getElementById('v07FavoriteAutoSort');
    checkbox.addEventListener('change', function () {
      Store.updateSettings({ favoriteAutoSort:checkbox.checked });
      App.state = Store.getState();
      App.toast(checkbox.checked ? 'Favorites will sort alphabetically' : 'Manual favorite order enabled');
      patchFavoriteAutoSortUi();
    });
    if (window.XtreamlyTVNavigation) XtreamlyTVNavigation.invalidate();
  }

  function patchSettings() {
    stopTimeline();
    if (!legacy.renderSettings) return;
    legacy.renderSettings.call(App);
    var grid = document.querySelector('.settings-grid');
    if (!grid) return;

    var section = document.createElement('section');
    section.className = 'settings-card v07-favorite-settings';
    section.innerHTML = '<h3>Favorites & Lists</h3><p>Favorites now live inside each media category wheel. Your v0.6 custom lists are preserved and appear there too.</p><button id="v07ManageFavorites" class="secondary-button focusable">Manage favorite lists</button>';
    grid.insertBefore(section, grid.firstChild);
    document.getElementById('v07ManageFavorites').addEventListener('click', function () {
      App.favoriteMode = 'home';
      App.favoriteGroupId = 'all';
      App.favoriteEditor = null;
      App.favoriteReorder = null;
      App.renderShell('favorites');
    });

    renderBlockedCategorySettings();
    if (window.XtreamlyTVNavigation) XtreamlyTVNavigation.invalidate();
  }

  function focusCurrentTopNav() {
    var selector = '.nav-item[data-view="' + App.currentView + '"]';
    var target = document.querySelector(selector);
    if (!target || target.offsetWidth <= 0) target = document.querySelector('.nav-item[data-view="home"]');
    if (target) target.focus();
  }


  function focusFirstHomeItem() {
    var home = document.querySelector('.v07-home .focusable');
    if (home) home.focus();
  }

  function resetContentSelection(kind) {
    if (kind === 'live') {
      V.liveIndex = 0;
      V.selectedKey.live = '';
    } else if (kind === 'movies' || kind === 'series') {
      V.mediaIndex[kind] = 0;
      V.selectedKey[kind] = '';
    }
  }

  function focusFirstContentForKind(kind) {
    resetContentSelection(kind);
    if (kind === 'live') {
      selectLiveIndex(0, true);
      return;
    }
    if (kind === 'movies' || kind === 'series') {
      selectMediaIndex(kind, 0, true);
    }
  }

  function patchTopNavFocusBehavior() {
    Array.prototype.forEach.call(document.querySelectorAll('.nav-item[data-view]'), function (item) {
      if (item.__v07FocusPatched) return;
      item.__v07FocusPatched = true;
      item.addEventListener('click', function () {
        var view = item.dataset.view;
        setTimeout(function () {
          if (view === 'live' || view === 'movies' || view === 'series') focusCategory();
          else if (view === 'home') focusFirstHomeItem();
        }, 0);
      });
    });
  }

  function focusCategory() {
    if (App.categoryRail && App.categoryRail.focusActive) {
      App.categoryRail.focusActive();
      return;
    }
    var active = document.querySelector('#categoryList .category-button.active') || document.querySelector('#categoryList .category-button');
    if (active) active.focus();
  }

  function focusMediaPoster(kind) {
    var rail = document.getElementById('v07PosterRail');
    if (!rail) return;
    var target = rail.querySelector('[data-v07-media-index="' + (V.mediaIndex[kind] || 0) + '"]') || rail.querySelector('[data-v07-media-index]');
    if (target) target.focus();
    else renderMediaRail(kind, true);
  }

  function focusLiveStation() {
    var rows = document.getElementById('v07GuideRows');
    if (!rows) return;
    var target = rows.querySelector('[data-v07-live-index="' + (V.liveIndex || 0) + '"]') || rows.querySelector('[data-v07-live-index]');
    if (target) target.focus();
    else renderLiveWindow(true);
  }

  function visibleNavItems() {
    return Array.prototype.filter.call(document.querySelectorAll('.nav-item'), function (element) {
      return element.offsetWidth > 0 && element.offsetHeight > 0;
    });
  }

  function moveNav(active, delta) {
    var items = visibleNavItems();
    var index = items.indexOf(active);
    if (index < 0) return;
    var next = index + delta;
    if (next < 0 || next >= items.length) return;
    items[next].focus();
  }


  function isBackKey(code) {
    return code === KEY.BACK || code === KEY.ESC || code === KEY.BACKSPACE;
  }

  function favoriteFocusSnapshot(active) {
    return {
      view: App.currentView,
      category: !!(active && active.classList && active.classList.contains('category-button')),
      search: !!(active && active.id === 'catalogSearch'),
      heroActionId: (active && active.id && /^v07Hero/.test(active.id)) ? active.id : '',
      mediaIndex: (active && active.dataset && active.dataset.v07MediaIndex !== undefined) ? Number(active.dataset.v07MediaIndex) : null,
      liveIndex: (active && active.dataset && active.dataset.v07LiveIndex !== undefined) ? Number(active.dataset.v07LiveIndex) : null
    };
  }

  function restoreFavoriteFocus(snapshot) {
    if (!snapshot) return;
    if (snapshot.category) {
      focusCategory();
      return;
    }
    if (snapshot.search) {
      var search = document.getElementById('catalogSearch');
      if (search) {
        search.focus();
        return;
      }
    }
    if (snapshot.view === 'live' && snapshot.liveIndex != null) {
      selectLiveIndex(snapshot.liveIndex, true);
      return;
    }
    if ((snapshot.view === 'movies' || snapshot.view === 'series')) {
      if (snapshot.heroActionId) {
        var heroButton = document.getElementById(snapshot.heroActionId);
        if (heroButton) {
          heroButton.focus();
          return;
        }
      }
      if (snapshot.mediaIndex != null) {
        selectMediaIndex(snapshot.view, snapshot.mediaIndex, true);
        return;
      }
    }
  }

  function restoreFavoriteFocusDeferred(snapshot) {
    [0, 40, 120].forEach(function (delay) {
      setTimeout(function () { restoreFavoriteFocus(snapshot); }, delay);
    });
  }

  function pageLiveGuide(direction, force) {
    var pageItems = App.currentFilteredItems.live || [];
    if (!pageItems.length) return false;
    var now = Date.now();
    if (!force && now - Number(V.lastLivePageAt || 0) < 180) return true;
    V.lastLivePageAt = now;
    var active = document.activeElement;
    var pageBase = active && active.dataset && active.dataset.v07LiveIndex !== undefined ? Number(active.dataset.v07LiveIndex) : Number(V.liveIndex || 0);
    var pageTarget = Math.max(0, Math.min(pageItems.length - 1, pageBase + (direction < 0 ? -V.liveWindowSize : V.liveWindowSize)));
    selectLiveIndex(pageTarget, true);
    return true;
  }


  function seriesDetailScrollHost(active) {
    return active && active.closest ? active.closest('.v07-series-detail-page') : document.querySelector('.v07-series-detail-page');
  }

  function seriesDetailFocus(target, forceTop) {
    if (!target) return false;
    try { target.focus({ preventScroll:true }); } catch (error) { target.focus(); }
    var host = seriesDetailScrollHost(target);
    if (!host) return true;
    if (forceTop) {
      host.scrollTop = 0;
      return true;
    }
    var itemRect = target.getBoundingClientRect();
    var hostRect = host.getBoundingClientRect();
    var guard = 22;
    if (itemRect.top < hostRect.top + guard) host.scrollTop -= (hostRect.top + guard - itemRect.top);
    else if (itemRect.bottom > hostRect.bottom - guard) host.scrollTop += (itemRect.bottom - (hostRect.bottom - guard));
    return true;
  }

  function nearestSeriesDetailByX(elements, active) {
    if (!elements || !elements.length) return null;
    var rect = active && active.getBoundingClientRect ? active.getBoundingClientRect() : null;
    var x = rect ? rect.left + rect.width / 2 : 0;
    var best = elements[0];
    var distance = Infinity;
    elements.forEach(function (element) {
      var candidate = element.getBoundingClientRect();
      var next = Math.abs((candidate.left + candidate.width / 2) - x);
      if (next < distance) { distance = next; best = element; }
    });
    return best;
  }

  function handleSeriesDetailNavigation(event, active) {
    if (!App.detail || App.detail.type !== 'series' || !active) return false;
    if (event.keyCode !== KEY.LEFT && event.keyCode !== KEY.RIGHT && event.keyCode !== KEY.UP && event.keyCode !== KEY.DOWN) return false;

    var actions = Array.prototype.slice.call(document.querySelectorAll('.v07-series-detail-actions .focusable:not([disabled])'));
    var seasons = Array.prototype.slice.call(document.querySelectorAll('.v07-series-season-button:not([disabled])'));
    var episodes = Array.prototype.slice.call(document.querySelectorAll('.v07-series-episode-card:not([disabled])'));
    var selectedSeason = document.querySelector('.v07-series-season-button.active') || seasons[0];
    var target = null;

    if (active.closest && active.closest('.v07-series-detail-actions')) {
      var actionIndex = actions.indexOf(active);
      if (event.keyCode === KEY.LEFT && actionIndex > 0) target = actions[actionIndex - 1];
      else if (event.keyCode === KEY.RIGHT && actionIndex >= 0 && actionIndex < actions.length - 1) target = actions[actionIndex + 1];
      else if (event.keyCode === KEY.DOWN) target = selectedSeason;
      else if (event.keyCode === KEY.UP) {
        var topHost = seriesDetailScrollHost(active);
        if (topHost) topHost.scrollTop = 0;
        return true;
      }
      if (target) seriesDetailFocus(target, false);
      return true;
    }

    if (active.classList && active.classList.contains('v07-series-season-button')) {
      var seasonIndex = seasons.indexOf(active);
      if (event.keyCode === KEY.LEFT && seasonIndex > 0) target = seasons[seasonIndex - 1];
      else if (event.keyCode === KEY.RIGHT && seasonIndex >= 0 && seasonIndex < seasons.length - 1) target = seasons[seasonIndex + 1];
      else if (event.keyCode === KEY.UP) {
        target = nearestSeriesDetailByX(actions, active) || actions[0];
        if (target) seriesDetailFocus(target, true);
        else {
          var seasonHost = seriesDetailScrollHost(active);
          if (seasonHost) seasonHost.scrollTop = 0;
        }
        return true;
      } else if (event.keyCode === KEY.DOWN) {
        target = nearestSeriesDetailByX(episodes.slice(0, 2), active) || episodes[0];
      }
      if (target) seriesDetailFocus(target, false);
      return true;
    }

    if (active.classList && active.classList.contains('v07-series-episode-card')) {
      var episodeIndex = episodes.indexOf(active);
      if (episodeIndex < 0) return true;
      var columns = 2;
      if (event.keyCode === KEY.LEFT) {
        if (episodeIndex % columns !== 0) target = episodes[episodeIndex - 1];
      } else if (event.keyCode === KEY.RIGHT) {
        if (episodeIndex % columns !== columns - 1 && episodeIndex + 1 < episodes.length) target = episodes[episodeIndex + 1];
      } else if (event.keyCode === KEY.UP) {
        if (episodeIndex - columns >= 0) target = episodes[episodeIndex - columns];
        else target = selectedSeason;
      } else if (event.keyCode === KEY.DOWN) {
        if (episodeIndex + columns < episodes.length) target = episodes[episodeIndex + columns];
      }
      if (target) seriesDetailFocus(target, false);
      return true;
    }

    return false;
  }

  function captureNavigation(event) {
    if (App.playerOpen || event.defaultPrevented) return;
    var active = document.activeElement;
    if (!active) return;

    /* Detail pages use the core return-state machinery. Do not let the v0.7
       catalog BACK routing turn BACK into a jump to the category wheel. */
    if (App.detail && isBackKey(event.keyCode)) return;
    if (App.detail && App.detail.type === 'series' && handleSeriesDetailNavigation(event, active)) {
      event.preventDefault();
      event.stopImmediatePropagation();
      return;
    }

    if (V.searchOpen) {
      if (isBackKey(event.keyCode)) {
        event.preventDefault();
        event.stopImmediatePropagation();
        closeSearchPopup(true);
      }
      return;
    }

    /* Some external/conventional remotes may expose channel/page key codes,
       though LG Magic Remote Channel +/- is not delivered to web apps. */
    if (App.currentView === 'live' && (event.keyCode === KEY.CH_UP || event.keyCode === KEY.CH_DOWN || event.keyCode === 33 || event.keyCode === 34)) {
      event.preventDefault();
      event.stopImmediatePropagation();
      pageLiveGuide((event.keyCode === KEY.CH_UP || event.keyCode === 33) ? -1 : 1, true);
      return;
    }

    if (isBackKey(event.keyCode)) {
      if (App.currentView === 'movies' && active.closest && active.closest('.v07-hero-actions')) {
        event.preventDefault();
        event.stopImmediatePropagation();
        var movieReturnIndex = V.movieHeroOriginIndex != null ? Number(V.movieHeroOriginIndex) : Number(V.mediaIndex.movies || 0);
        V.movieHeroOriginIndex = null;
        selectMediaIndex('movies', movieReturnIndex, true);
        return;
      }
      if (active.classList && active.classList.contains('category-button')) {
        event.preventDefault();
        event.stopImmediatePropagation();
        focusCurrentTopNav();
        return;
      }
      if (App.currentView === 'live' || App.currentView === 'movies' || App.currentView === 'series') {
        if (!(active.classList && active.classList.contains('nav-item'))) {
          event.preventDefault();
          event.stopImmediatePropagation();
          focusCategory();
          return;
        }
      }
    }

    if (active.classList && active.classList.contains('nav-item')) {
      if (event.keyCode === KEY.LEFT || event.keyCode === KEY.RIGHT) {
        event.preventDefault();
        event.stopImmediatePropagation();
        moveNav(active, event.keyCode === KEY.LEFT ? -1 : 1);
        return;
      }
      if (event.keyCode === KEY.DOWN) {
        event.preventDefault();
        event.stopImmediatePropagation();
        if (App.currentView === 'home') {
          focusFirstHomeItem();
        } else if (App.currentView === 'live' || App.currentView === 'movies' || App.currentView === 'series') {
          focusCategory();
        } else {
          var first = document.querySelector('#view .focusable');
          if (first) first.focus();
        }
        return;
      }
      if (event.keyCode === KEY.ENTER && active.dataset && active.dataset.view === App.currentView) {
        event.preventDefault();
        event.stopImmediatePropagation();
        if (App.currentView === 'home') focusFirstHomeItem();
        else if (App.currentView === 'live' || App.currentView === 'movies' || App.currentView === 'series') focusCategory();
        return;
      }
      if (event.keyCode === KEY.UP) {
        event.preventDefault();
        event.stopImmediatePropagation();
        return;
      }
    }

    if (active.classList && active.classList.contains('category-button')) {
      if (event.keyCode === KEY.UP || event.keyCode === KEY.DOWN) {
        event.preventDefault();
        event.stopImmediatePropagation();
        moveCategoryWheel(event.keyCode === KEY.UP ? -1 : 1);
        return;
      }
      if (event.keyCode === KEY.RIGHT || event.keyCode === KEY.ENTER) {
        event.preventDefault();
        event.stopImmediatePropagation();
        activateCategoryWheel();
        return;
      }
      if (event.keyCode === KEY.LEFT) {
        event.preventDefault();
        event.stopImmediatePropagation();
        focusCurrentTopNav();
        return;
      }
    }

    if (active.dataset && active.dataset.v07MediaIndex !== undefined) {
      var kind = App.currentView;
      var index = Number(active.dataset.v07MediaIndex);
      if (event.keyCode === KEY.LEFT || event.keyCode === KEY.RIGHT) {
        event.preventDefault();
        event.stopImmediatePropagation();
        if (event.keyCode === KEY.LEFT && index <= 0) {
          focusCategory();
        } else {
          selectMediaIndex(kind, index + (event.keyCode === KEY.LEFT ? -1 : 1), true);
        }
        return;
      }
      if (event.keyCode === KEY.UP) {
        event.preventDefault();
        event.stopImmediatePropagation();
        var heroAction = document.getElementById('v07HeroPlay');
        if (heroAction) heroAction.focus();
        else focusCurrentTopNav();
        return;
      }
      if (event.keyCode === KEY.DOWN) {
        event.preventDefault();
        event.stopImmediatePropagation();
        return;
      }
    }

    if (active.closest && active.closest('.v07-hero-actions')) {
      var actionButtons = Array.prototype.filter.call(active.closest('.v07-hero-actions').querySelectorAll('.focusable'), function (button) {
        return button.offsetWidth > 0 && button.offsetHeight > 0;
      });
      var actionIndex = actionButtons.indexOf(active);
      if (event.keyCode === KEY.LEFT) {
        event.preventDefault();
        event.stopImmediatePropagation();
        if (actionIndex <= 0) focusCategory();
        else actionButtons[actionIndex - 1].focus();
        return;
      }
      if (event.keyCode === KEY.RIGHT) {
        if (actionIndex >= 0 && actionIndex < actionButtons.length - 1) {
          event.preventDefault();
          event.stopImmediatePropagation();
          actionButtons[actionIndex + 1].focus();
          return;
        }
      }
      if (event.keyCode === KEY.DOWN) {
        event.preventDefault();
        event.stopImmediatePropagation();
        focusMediaPoster(App.currentView);
        return;
      }
      if (event.keyCode === KEY.UP) {
        event.preventDefault();
        event.stopImmediatePropagation();
        focusCurrentTopNav();
        return;
      }
    }


    if (active.id === 'v07HomeWatch') {
      if (event.keyCode === KEY.UP || event.keyCode === KEY.LEFT) {
        event.preventDefault();
        event.stopImmediatePropagation();
        focusCurrentTopNav();
        return;
      }
    }

    if (active.dataset && active.dataset.homeRow !== undefined) {
      var activeHomeRow = active.closest ? active.closest('.v07-home-row') : null;
      var rowItems = activeHomeRow ? Array.prototype.filter.call(activeHomeRow.querySelectorAll('.focusable'), function (button) {
        return button.offsetWidth > 0 && button.offsetHeight > 0;
      }) : [];
      var rowItemIndex = rowItems.indexOf(active);

      if (event.keyCode === KEY.LEFT || event.keyCode === KEY.RIGHT) {
        event.preventDefault();
        event.stopImmediatePropagation();
        if (event.keyCode === KEY.LEFT) {
          if (rowItemIndex > 0) rowItems[rowItemIndex - 1].focus();
          else focusCurrentTopNav();
        } else if (rowItemIndex >= 0 && rowItemIndex < rowItems.length - 1) {
          rowItems[rowItemIndex + 1].focus();
        }
        return;
      }
      if (event.keyCode === KEY.UP) {
        var homeRow = Number(active.dataset.homeRow || 0);
        var hasHeroWatch = !!document.getElementById('v07HomeWatch');
        if ((hasHeroWatch && homeRow <= 1) || (!hasHeroWatch && homeRow <= 1)) {
          event.preventDefault();
          event.stopImmediatePropagation();
          if (hasHeroWatch) {
            var watchButton = document.getElementById('v07HomeWatch');
            if (watchButton) watchButton.focus();
            else focusCurrentTopNav();
          } else {
            focusCurrentTopNav();
          }
          return;
        }
      }
    }

    if (active.dataset && active.dataset.v07LiveIndex !== undefined) {
      var liveIndex = Number(active.dataset.v07LiveIndex);
      if (event.keyCode === KEY.UP || event.keyCode === KEY.DOWN) {
        event.preventDefault();
        event.stopImmediatePropagation();
        if (event.repeat) {
          pageLiveGuide(event.keyCode === KEY.UP ? -1 : 1, false);
          return;
        }
        var nextLive = liveIndex + (event.keyCode === KEY.UP ? -1 : 1);
        var items = App.currentFilteredItems.live || [];
        if (nextLive < 0) {
          focusCurrentTopNav();
          return;
        }
        if (nextLive >= items.length) return;
        selectLiveIndex(nextLive, true);
        return;
      }
      if (event.keyCode === KEY.LEFT) {
        event.preventDefault();
        event.stopImmediatePropagation();
        focusCategory();
        return;
      }
      if (event.keyCode === KEY.RIGHT) {
        event.preventDefault();
        event.stopImmediatePropagation();
        return;
      }
    }

    if (active.id === 'catalogSearch') {
      if (event.keyCode === KEY.DOWN) {
        event.preventDefault();
        event.stopImmediatePropagation();
        if (App.currentView === 'live') focusLiveStation();
        else focusMediaPoster(App.currentView);
        return;
      }
      if (event.keyCode === KEY.UP || event.keyCode === KEY.LEFT) {
        event.preventDefault();
        event.stopImmediatePropagation();
        focusCurrentTopNav();
      }
    }
  }


  function seriesDetailModel(detail) {
    detail = detail || {};
    var base = detail.item || {};
    var snapshot = V.seriesDetailBrowseSnapshot;
    if (!snapshot || String(idOf(snapshot, 'series')) !== String(idOf(base, 'series'))) snapshot = {};
    var info = detail.info && detail.info.info || {};
    /* The catalog object is deliberately applied last. get_series already
       contains the rich browse metadata/artwork and this keeps the detail hero
       visually identical to the item the user just selected. */
    return Object.assign({}, info, base, snapshot, {
      series_id: base.series_id || info.series_id || '',
      content_type:'series'
    });
  }

  function seriesDetailBackdrop(detail, series) {
    var captured = artworkUrl(detail && detail.heroBackdrop);
    if (captured) return captured;
    var snapshot = V.seriesDetailBrowseSnapshot;
    if (snapshot && detail && detail.item && String(idOf(snapshot, 'series')) !== String(idOf(detail.item, 'series'))) snapshot = null;
    return firstBackdrop(snapshot) || firstBackdrop(detail && detail.item) || firstBackdrop(series) ||
      firstBackdrop(detail && detail.info && detail.info.info);
  }

  function seriesEpisodeCardHtml(episode) {
    var id = idOf(episode, 'episode');
    var progress = Store.getProgress ? Store.getProgress('episode', id) : null;
    var percent = progress && progress.duration ? Math.min(100, progress.seconds / progress.duration * 100) : 0;
    var image = artworkUrl(episode && (episode.movie_image || episode.cover || episode.stream_icon));
    var number = String(episode && (episode.episode_num || episode.episode || '') || '\u2022');
    var runtime = durationOf(episode);
    var description = descriptionOf(episode) || ('Episode ' + number);

    return '<button class="episode-card v07-series-episode-card focusable" data-episode-id="' + esc(id) + '">' +
      '<div class="v07-series-episode-art' + (image ? '' : ' image-failed') + '">' +
        (image ? '<img src="' + esc(image) + '" alt="" decoding="async" draggable="false" onerror="this.style.display=\'none\';this.parentNode.classList.add(\'image-failed\')">' : '') +
        '<span class="v07-series-episode-number">' + esc(number) + '</span>' +
      '</div>' +
      '<div class="v07-series-episode-copy">' +
        '<div class="v07-series-episode-meta">Episode ' + esc(number) + (runtime ? '<span>&#8226;</span>' + esc(runtime) : '') + '</div>' +
        '<strong>' + esc(titleOf(episode)) + '</strong>' +
        '<p>' + esc(description) + '</p>' +
      '</div>' +
      '<span class="episode-play">▶</span>' +
      (percent ? '<div class="episode-progress"><i style="width:' + percent + '%"></i></div>' : '') +
      '</button>';
  }


  function openV07SeriesDetail(series) {
    var self = App;
    var returnView = self.currentView === 'favorites' ? 'favorites' : 'series';
    var returnState = self.captureNavigationState();
    self.rememberNavigationState(returnState);
    V.seriesDetailBrowseSnapshot = Object.assign({}, series || {});
    /* Carry the exact fanart already resolved and painted in the browse hero
       into Series Details. This avoids relying on the much larger
       get_series_info payload to rediscover artwork and lets webOS reuse the
       image from its HTTP/decoder cache immediately. */
    var browseBackdrop = artworkUrl(V.backdropUrl.series) || firstBackdrop(series);
    var browsePoster = posterImageOf(series, 'series');
    preloadArtwork(browseBackdrop);
    preloadArtwork(browsePoster);

    var cached = mediaCacheGet('series', idOf(series, 'series'));
    self.detail = {
      type:'series', item:series, info:cached, loading:!cached, error:'', season:null,
      heroBackdrop:browseBackdrop, heroPoster:browsePoster,
      returnView:returnView, returnState:returnState
    };
    if (cached) {
      var cachedSeasons = self.seriesSeasonKeys(cached);
      self.detail.season = cachedSeasons.length ? cachedSeasons[0] : null;
    }
    self.renderShell(returnView);
    if (cached) return;

    var pendingDetail = self.detail, session = App.sessionGeneration;
    self.api.getSeriesInfo(series.series_id).then(function (response) {
      if (session !== App.sessionGeneration || self.detail !== pendingDetail) return;
      if (!self.detail || self.detail.type !== 'series' || String(self.detail.item.series_id) !== String(series.series_id)) return;
      var compact = compactSeriesResponse(response || {});
      mediaCachePut('series', idOf(series, 'series'), compact);
      self.detail.info = compact;
      self.detail.loading = false;
      var seasons = self.seriesSeasonKeys(compact);
      self.detail.season = seasons.length ? seasons[0] : null;
      self.renderView();
    }).catch(function (error) {
      if (session !== App.sessionGeneration || self.detail !== pendingDetail) return;
      self.detail.loading = false;
      self.detail.info = { info:series, episodes:{} };
      self.detail.error = error.message || 'Series details are unavailable.';
      self.renderView();
    });
  }

  function renderV07SeriesDetail() {
    var detail = App.detail;
    if (!detail || detail.type !== 'series') {
      if (legacy.renderSeriesDetail) return legacy.renderSeriesDetail.apply(App, arguments);
      return;
    }

    var view = document.getElementById('view');
    if (!view) return;

    var series = seriesDetailModel(detail);
    var response = detail.info || {};
    var seasons = detail.loading ? [] : App.seriesSeasonKeys(response);
    var activeSeason = detail.season || (seasons.length ? seasons[0] : null);
    var episodes = activeSeason == null ? [] : App.episodesForSeason(response, activeSeason).map(function (episode) {
      return App.normalizeEpisode(episode, series, activeSeason);
    });

    App.episodeLookup = {};
    episodes.forEach(function (episode) {
      App.episodeLookup[idOf(episode, 'episode')] = episode;
    });

    var favorite = Store.isFavorite && Store.isFavorite('series', idOf(series, 'series'));
    var backdrop = seriesDetailBackdrop(detail, series);
    setRootCanvasBackdrop('series-detail', backdrop);
    if (backdrop) preloadArtwork(backdrop);
    preloadArtwork(posterImageOf(series, 'series'));
    episodes.slice(0, 6).forEach(function (episode) { preloadArtwork(artworkUrl(episode && (episode.movie_image || episode.cover || episode.stream_icon))); });
    var active = document.activeElement;
    var restoreId = active && active.id ? active.id : '';
    var restoreSeason = active && active.dataset && active.dataset.season !== undefined ? String(active.dataset.season) : '';

    var backdropHtml = backdrop ?
      '<div class="v07-series-detail-backdrop" aria-hidden="true">' +
        '<img class="v07-series-detail-backdrop-art" src="' + esc(backdrop) + '" alt="" decoding="async" draggable="false" ' +
          'onerror="this.style.display=\'none\';this.parentNode.classList.add(\'image-failed\')">' +
      '</div>' : '';

    var seasonHtml = seasons.map(function (season) {
      var selected = String(activeSeason) === String(season);
      return '<button class="season-button v07-series-season-button focusable' + (selected ? ' active' : '') + '" data-season="' + esc(season) + '">Season ' + esc(season) + '</button>';
    }).join('');

    var episodeBody = '';
    if (detail.loading) {
      episodeBody = '<div class="v07-series-detail-loading"><div class="spinner"></div><span>Loading seasons and episodes…</span></div>';
    } else if (!seasons.length) {
      episodeBody = '<div class="empty-state grid-empty">No seasons or episodes were returned for this series.</div>';
    } else {
      episodeBody = '<div class="season-row v07-series-season-row">' + seasonHtml + '</div>' +
        (detail.error ? '<p class="detail-warning">' + esc(detail.error) + '</p>' : '') +
        '<div class="episode-grid v07-series-episode-grid">' +
          (episodes.length ? episodes.map(seriesEpisodeCardHtml).join('') : '<div class="empty-state grid-empty">No episodes were returned for this season.</div>') +
        '</div>';
    }

    view.innerHTML =
      '<div class="scroll-view detail-scroll v07-series-detail-page">' +
        '<section class="v07-series-detail-hero' + (backdrop ? ' has-art' : '') + '">' +
          backdropHtml +
          '<div class="v07-series-detail-summary">' +
            posterHtml(series, 'series', 'v07-series-detail-poster') +
            '<div class="v07-series-detail-copy">' +
              '<h2>' + esc(titleOf(series)) + '</h2>' +
              '<div class="v07-meta-row">' + metaChips(series, 'series') + '</div>' +
              '<p class="v07-series-detail-plot">' + esc(descriptionOf(series) || 'No description supplied by this provider.') + '</p>' +
              '<div class="v07-series-detail-actions">' +
                '<button class="secondary-button focusable" id="favoriteSeries">' + (favorite ? '♥ Favorited' : '♡ Favorite') + '</button>' +
                '<button class="secondary-button focusable" id="closeSeries">Back</button>' +
              '</div>' +
            '</div>' +
          '</div>' +
        '</section>' +
        '<section class="episode-section v07-series-episodes">' + episodeBody + '</section>' +
      '</div>';

    var favoriteButton = document.getElementById('favoriteSeries');
    var backButton = document.getElementById('closeSeries');
    if (favoriteButton) favoriteButton.addEventListener('click', function () {
      var added = Store.toggleFavorite(detail.item, 'series');
      App.state = Store.getState();
      App.toast(added ? 'Added to favorites' : 'Removed from favorites');
      renderV07SeriesDetail();
      var restored = document.getElementById('favoriteSeries');
      if (restored) restored.focus();
    });
    if (backButton) backButton.addEventListener('click', function () { App.closeDetail(); });

    Array.prototype.forEach.call(document.querySelectorAll('.v07-series-season-button[data-season]'), function (button) {
      button.addEventListener('click', function () {
        App.detail.season = button.dataset.season;
        renderV07SeriesDetail();
        var target = document.querySelector('.v07-series-season-button[data-season="' + button.dataset.season.replace(/"/g, '\\"') + '"]');
        if (target) target.focus();
      });
    });

    App.bindEpisodeCards();
    if (window.XtreamlyTVNavigation && XtreamlyTVNavigation.invalidate) XtreamlyTVNavigation.invalidate();

    if (restoreId) {
      var restore = document.getElementById(restoreId);
      if (restore) {
        restore.focus();
        return;
      }
    }
    if (restoreSeason) {
      var seasonRestore = document.querySelector('.v07-series-season-button[data-season="' + restoreSeason.replace(/"/g, '\\"') + '"]');
      if (seasonRestore) {
        seasonRestore.focus();
        return;
      }
    }
    if (!detail.loading) {
      var selectedSeason = document.querySelector('.v07-series-season-button.active');
      if (selectedSeason) {
        selectedSeason.focus();
        return;
      }
      var firstEpisode = document.querySelector('.v07-series-episode-card');
      if (firstEpisode) {
        firstEpisode.focus();
        return;
      }
    }
    if (backButton) backButton.focus();
  }

  function afterFavoriteToggle(snapshot) {
    if (App.currentView === 'movies' || App.currentView === 'series') {
      var kind = App.currentView;
      var items = App.currentFilteredItems[kind] || [];
      var item = items[V.mediaIndex[kind] || 0];
      if (item) updateHero(kind, item, V.detailCache[itemKey(item, typeForKind(kind))]);
      renderMediaRail(kind, false);
    } else if (App.currentView === 'live') {
      renderLiveWindow(false);
    }
    restoreFavoriteFocusDeferred(snapshot);
  }

  if (legacy.favoriteItemsForGroup) {
    App.favoriteItemsForGroup = function (groupId) {
      var items = legacy.favoriteItemsForGroup.call(App, groupId) || [];
      return favoriteAutoSortEnabled() ? alphabeticalItems(items) : items;
    };
  }

  if (legacy.beginFavoriteItemReorder) {
    App.beginFavoriteItemReorder = function () {
      if (favoriteAutoSortEnabled()) {
        App.toast('Turn off Auto-sort A–Z to reorder favorites manually.');
        return;
      }
      return legacy.beginFavoriteItemReorder.apply(App, arguments);
    };
  }

  if (legacy.renderFavorites) {
    App.renderFavorites = function () {
      var result = legacy.renderFavorites.apply(App, arguments);
      setTimeout(patchFavoriteAutoSortUi, 0);
      return result;
    };
  }

  App.showCatalogLoading = function (kind, message) {
    setCatalogActivity(message || 'Loading category…');
    var count = document.querySelector('.result-count');
    if (count && !count.textContent) count.textContent = 'Loading…';
    if (kind === 'live') {
      var liveCount = document.getElementById('v07LiveChannelCount');
      if (liveCount) liveCount.textContent = 'LOADING…';
    }
  };

  App.showCatalogError = function (kind, message) {
    clearCatalogActivity();
    return legacy.showCatalogError.call(App, kind, message);
  };

  App.renderCatalog = function (kind) {
    if (kind === 'live') return renderLiveCatalog();
    if (kind === 'movies' || kind === 'series') return renderMediaCatalog(kind);
    return legacy.renderCatalog.call(App, kind);
  };

  App.updateCatalogGrid = function (kind) {
    if (kind === 'live') return updateLiveGuide();
    if (kind === 'movies' || kind === 'series') return updateMediaCatalog(kind);
    return legacy.updateCatalogGrid.call(App, kind);
  };

  App.renderHome = renderHome;
  App.renderSettings = patchSettings;
  App.openSeriesDetail = openV07SeriesDetail;
  App.renderSeriesDetail = renderV07SeriesDetail;

  var renderShellBase = App.renderShell;
  if (renderShellBase) {
    App.renderShell = function () {
      closeSearchPopup(false);
      var result = renderShellBase.apply(App, arguments);
      setTimeout(function () {
        if (App.currentView !== 'home' && App.currentView !== 'movies' && App.currentView !== 'series') hideRootMediaBackdrop();
        patchTopNavDecorations();
        patchTopNavFocusBehavior();
        patchFavoriteAutoSortUi();
      }, 0);
      return result;
    };
  }

  if (window && window.addEventListener) {
    window.addEventListener('resize', function () {
      patchTopNavDecorations();
    });
  }

  if (legacy.toggleFocusedFavorite) {
    App.toggleFocusedFavorite = function () {
      var snapshot = favoriteFocusSnapshot(document.activeElement);
      var result = legacy.toggleFocusedFavorite.call(App);
      if (result) setTimeout(function () { afterFavoriteToggle(snapshot); }, 0);
      return result;
    };
  }

  document.addEventListener('keydown', captureNavigation, true);

  /* The v0.6 shell still owns the robust player, settings, details and state
     machinery. CSS turns its side navigation into the v0.7 top wheel. */
  App.__v07UiRefreshInstalled = true;
  App.__v07UiRefresh = V;
}());
