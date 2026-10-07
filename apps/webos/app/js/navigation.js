(function () {
  'use strict';

  var KEY = { LEFT:37, UP:38, RIGHT:39, DOWN:40, ENTER:13 };
  var current = null;
  var cachedFocusables = null;
  var enterHold = null;

  function invalidate() { cachedFocusables = null; }

  function isVisible(el) {
    if (!el || el.disabled || !document.documentElement.contains(el)) return false;
    if (el.offsetWidth <= 0 || el.offsetHeight <= 0) return false;
    var style = window.getComputedStyle(el);
    return style.visibility !== 'hidden' && style.display !== 'none';
  }

  function focusables(force) {
    if (force) invalidate();
    if (cachedFocusables) return cachedFocusables;
    cachedFocusables = Array.prototype.slice.call(document.querySelectorAll('.focusable:not([disabled])')).filter(isVisible);
    return cachedFocusables;
  }

  function center(rect) { return { x:rect.left + rect.width / 2, y:rect.top + rect.height / 2 }; }

  function nearestByX(elements, active) {
    if (!elements || !elements.length) return null;
    var activeX = center(active.getBoundingClientRect()).x;
    var best = elements[0];
    var bestDistance = Infinity;
    elements.forEach(function (element) {
      var distance = Math.abs(center(element.getBoundingClientRect()).x - activeX);
      if (distance < bestDistance) { bestDistance = distance; best = element; }
    });
    return best;
  }

  function focusElement(el, preventScroll) {
    if (!el || !isVisible(el)) return false;
    try { el.focus({ preventScroll:!!preventScroll }); } catch (error) { el.focus(); }
    current = el;
    return true;
  }

  function ensureAxisVisible(viewport, candidate, horizontal, vertical) {
    if (!viewport || !candidate) return;
    var itemRect = candidate.getBoundingClientRect();
    var viewportRect = viewport.getBoundingClientRect();
    var guardX = 16;
    var guardY = 18;
    if (horizontal) {
      if (itemRect.left < viewportRect.left + guardX) viewport.scrollLeft -= (viewportRect.left + guardX - itemRect.left);
      else if (itemRect.right > viewportRect.right - guardX) viewport.scrollLeft += (itemRect.right - (viewportRect.right - guardX));
    }
    if (vertical) {
      if (itemRect.top < viewportRect.top + guardY) viewport.scrollTop -= (viewportRect.top + guardY - itemRect.top);
      else if (itemRect.bottom > viewportRect.bottom - guardY) viewport.scrollTop += (itemRect.bottom - (viewportRect.bottom - guardY));
    }
  }

  function ensureVisible(candidate) {
    if (!candidate || !candidate.closest) return;
    var horizontalViewport = candidate.closest('.favorite-groups-row,.channel-row,.poster-row,.mixed-row');
    var verticalViewport = candidate.closest('.virtual-grid-host,.favorite-manager-list,.favorite-item-manager-list,.favorite-editor-settings,.favorite-editor-grid,.scroll-view,.category-list');

    /* Home cards live inside a horizontal row nested in the page's vertical
       scroll view. Keep both axes in sync instead of scrolling only the row. */
    if (horizontalViewport) ensureAxisVisible(horizontalViewport, candidate, true, horizontalViewport === verticalViewport);
    if (verticalViewport && verticalViewport !== horizontalViewport) ensureAxisVisible(verticalViewport, candidate, false, true);
  }

  function moveSibling(active, selector, delta, trapAtEnd) {
    if (!active || !active.parentNode) return false;
    var candidate = active;
    do {
      candidate = delta < 0 ? candidate.previousElementSibling : candidate.nextElementSibling;
    } while (candidate && (!candidate.matches(selector) || !isVisible(candidate)));
    if (!candidate) return !!trapAtEnd;
    focusElement(candidate, true);
    ensureVisible(candidate);
    return true;
  }

  function moveActionRow(active, rowSelector, direction) {
    if (!active || !active.closest) return false;
    var row = active.closest(rowSelector);
    if (!row) return false;
    var actions = Array.prototype.slice.call(row.querySelectorAll('.focusable:not([disabled])')).filter(isVisible);
    if (direction === 'left' || direction === 'right') {
      var index = actions.indexOf(active);
      var targetIndex = index + (direction === 'left' ? -1 : 1);
      if (index < 0 || targetIndex < 0 || targetIndex >= actions.length) return true;
      focusElement(actions[targetIndex], true);
      ensureVisible(actions[targetIndex]);
      return true;
    }
    var sibling = direction === 'up' ? row.previousElementSibling : row.nextElementSibling;
    while (sibling && !sibling.matches(rowSelector)) sibling = direction === 'up' ? sibling.previousElementSibling : sibling.nextElementSibling;
    if (!sibling) {
      if (direction === 'up') {
        var surface = active.closest('[data-favorite-surface]');
        var headerSelector = rowSelector === '.favorite-manager-row' ? '.favorite-manager-header' : '.favorite-item-manager-header';
        var header = surface && surface.querySelector(headerSelector);
        var headerActions = header ? Array.prototype.slice.call(header.querySelectorAll('.focusable:not([disabled])')).filter(isVisible) : [];
        var headerTarget = nearestByX(headerActions, active);
        if (headerTarget) {
          focusElement(headerTarget, true);
          ensureVisible(headerTarget);
        }
      }
      return true;
    }
    var selector = '';
    if (active.hasAttribute('data-item-manager-move')) selector = '[data-item-manager-move="' + active.dataset.itemManagerMove + '"]:not([disabled])';
    else if (active.hasAttribute('data-manager-move')) selector = '[data-manager-move="' + active.dataset.managerMove + '"]:not([disabled])';
    else if (active.hasAttribute('data-manager-items')) selector = '[data-manager-items]:not([disabled])';
    else if (active.hasAttribute('data-manager-edit')) selector = '[data-manager-edit]:not([disabled])';
    else if (active.hasAttribute('data-manager-visibility')) selector = '[data-manager-visibility]:not([disabled])';
    var target = selector ? sibling.querySelector(selector) : null;
    if (!target) {
      var siblingActions = Array.prototype.slice.call(sibling.querySelectorAll('.focusable:not([disabled])')).filter(isVisible);
      target = siblingActions.length ? siblingActions[Math.min(actions.indexOf(active), siblingActions.length - 1)] : null;
    }
    if (!target) return true;
    focusElement(target, true);
    ensureVisible(target);
    return true;
  }

  function moveHeaderToFirstRow(active, rowSelector) {
    if (!active || !active.closest) return false;
    var surface = active.closest('[data-favorite-surface]');
    var row = surface && surface.querySelector(rowSelector);
    if (!row) return true;
    var actions = Array.prototype.slice.call(row.querySelectorAll('.focusable:not([disabled])')).filter(isVisible);
    var target = nearestByX(actions, active);
    if (!target) return true;
    focusElement(target, true);
    ensureVisible(target);
    return true;
  }

  function moveHomeRow(active, delta) {
    if (!active || !active.dataset || active.dataset.homeRow === undefined) return false;
    var currentRow = Number(active.dataset.homeRow);
    if (!isFinite(currentRow)) return false;
    var homeViewport = active.closest ? active.closest('.scroll-view') : document.querySelector('.scroll-view');
    if (delta < 0 && currentRow === 0) {
      if (homeViewport) homeViewport.scrollTop = 0;
      return true;
    }
    var rows = Array.prototype.slice.call(document.querySelectorAll('[data-home-row]'));
    var maxRow = rows.reduce(function (max, element) {
      var value = Number(element.dataset.homeRow);
      return isFinite(value) ? Math.max(max, value) : max;
    }, currentRow);
    var targetRow = currentRow + delta;
    while (targetRow >= 0 && targetRow <= maxRow) {
      var candidates = rows.filter(function (element) { return Number(element.dataset.homeRow) === targetRow && isVisible(element); });
      if (candidates.length) {
        var activeCenter = center(active.getBoundingClientRect()).x;
        var best = candidates[0];
        var bestDistance = Infinity;
        candidates.forEach(function (candidate) {
          var distance = Math.abs(center(candidate.getBoundingClientRect()).x - activeCenter);
          if (distance < bestDistance) { bestDistance = distance; best = candidate; }
        });
        focusElement(best, true);
        ensureVisible(best);
        if (targetRow === 0 && homeViewport) homeViewport.scrollTop = 0;
        return true;
      }
      targetRow += delta;
    }
    return true;
  }

  function seriesDetailViewport(active) {
    return active && active.closest ? active.closest('.detail-scroll') : document.querySelector('.detail-scroll');
  }

  function seriesNearestByX(elements, active) {
    if (!elements || !elements.length) return null;
    var activeRect = active && active.getBoundingClientRect ? active.getBoundingClientRect() : null;
    var activeX = activeRect ? activeRect.left + activeRect.width / 2 : 0;
    var best = elements[0];
    var bestDistance = Infinity;

    elements.forEach(function (element) {
      var rect = element.getBoundingClientRect();
      var distance = Math.abs((rect.left + rect.width / 2) - activeX);
      if (distance < bestDistance) {
        bestDistance = distance;
        best = element;
      }
    });

    return best;
  }

  function focusSeriesTarget(target, scrollToTop) {
    if (!target) return true;

    focusElement(target, true);

    if (typeof ensureVisible === 'function') {
      ensureVisible(target);
    } else if (target.scrollIntoView) {
      target.scrollIntoView({ block:'nearest', inline:'nearest' });
    }

    var viewport = seriesDetailViewport(target);
    if (scrollToTop && viewport) viewport.scrollTop = 0;
    return true;
  }

  function moveSeriesAction(active, direction) {
    if (!active || !active.closest || !active.closest('.series-summary .detail-actions')) return false;

    var viewport = seriesDetailViewport(active);

    if (direction === 'left' || direction === 'right') {
      moveSibling(active, '.focusable', direction === 'left' ? -1 : 1, true);
      return true;
    }

    if (direction === 'up') {
      if (viewport) viewport.scrollTop = 0;
      return true;
    }

    var season = document.querySelector('.season-button.active') ||
      document.querySelector('.season-button');

    return focusSeriesTarget(season, false);
  }

  function moveSeriesSeason(active, direction) {
    if (!active || !active.classList || !active.classList.contains('season-button')) return false;

    if (direction === 'left' || direction === 'right') {
      moveSibling(active, '.season-button', direction === 'left' ? -1 : 1, true);
      return true;
    }

    if (direction === 'up') {
      var actions = Array.prototype.slice.call(
        document.querySelectorAll('.series-summary .detail-actions .focusable:not([disabled])')
      ).filter(isVisible);

      return focusSeriesTarget(seriesNearestByX(actions, active), true);
    }

    var episodes = Array.prototype.slice.call(
      document.querySelectorAll('.episode-grid .episode-card:not([disabled])')
    ).filter(isVisible);

    return focusSeriesTarget(seriesNearestByX(episodes, active), false);
  }

  function moveSeriesEpisode(active, direction) {
    if (!active || !active.classList || !active.classList.contains('episode-card')) return false;

    var episodes = Array.prototype.slice.call(
      document.querySelectorAll('.episode-grid .episode-card:not([disabled])')
    ).filter(isVisible);

    var index = episodes.indexOf(active);
    if (index < 0) return true;

    /* Series Details uses a fixed two-column episode grid. */
    var columns = 2;
    var targetIndex = -1;

    if (direction === 'left') {
      if (index % columns === 0) return true;
      targetIndex = index - 1;
    } else if (direction === 'right') {
      if (index % columns === columns - 1 || index + 1 >= episodes.length) return true;
      targetIndex = index + 1;
    } else if (direction === 'up') {
      if (index - columns >= 0) {
        targetIndex = index - columns;
      } else {
        var activeSeason = document.querySelector('.season-button.active') ||
          document.querySelector('.season-button');
        return focusSeriesTarget(activeSeason, false);
      }
    } else if (direction === 'down') {
      if (index + columns >= episodes.length) return true;
      targetIndex = index + columns;
    }

    return focusSeriesTarget(episodes[targetIndex], false);
  }
  function bestCandidate(list, active, direction) {
    var a = center(active.getBoundingClientRect());
    var best = null;
    var bestScore = Infinity;
    list.forEach(function (candidate) {
      if (candidate === active || !isVisible(candidate)) return;
      var b = center(candidate.getBoundingClientRect());
      var dx = b.x - a.x;
      var dy = b.y - a.y;
      var primary;
      var secondary;
      if (direction === 'left' && dx < -4) { primary = -dx; secondary = Math.abs(dy); }
      else if (direction === 'right' && dx > 4) { primary = dx; secondary = Math.abs(dy); }
      else if (direction === 'up' && dy < -4) { primary = -dy; secondary = Math.abs(dx); }
      else if (direction === 'down' && dy > 4) { primary = dy; secondary = Math.abs(dx); }
      else return;
      var score = primary + secondary * 2.35;
      if (score < bestScore) { bestScore = score; best = candidate; }
    });
    return best;
  }

  function favoriteScope(active) {
    var app = window.XtreamlyTVApp || window.TVeeApp;
    if (!app || app.currentView !== 'favorites' || !active || !active.closest) return null;
    return active.closest('.view');
  }

  function move(direction) {
    var list = focusables();
    var active = document.activeElement;
    if (active && document.documentElement.contains(active) && list.indexOf(active) < 0) list = focusables(true);
    if (!active || list.indexOf(active) < 0) {
      var scope = document.querySelector('.view [data-favorite-surface]');
      var fallback = current && list.indexOf(current) >= 0 ? current : (scope ? list.find(function (element) { return scope.contains(element); }) : list[0]);
      focusElement(fallback, false);
      return;
    }

    var scopeElement = favoriteScope(active);
    var scopedList = scopeElement ? list.filter(function (candidate) { return scopeElement.contains(candidate); }) : list;
    var best = bestCandidate(scopedList, active, direction);

    /* Keep Right/Up/Down inside Favorites. Left may intentionally return to the sidebar. */
    if (!best && scopeElement && direction === 'left') best = bestCandidate(list, active, direction);
    if (!best && scopeElement) return;
    if (!best) return;
    focusElement(best, true);
    ensureVisible(best);
  }

  function clearEnterHold() {
    if (!enterHold) return;
    clearTimeout(enterHold.timer);
    enterHold = null;
  }

  function beginEnterHold(element) {
    clearEnterHold();
    enterHold = { element:element, fired:false, timer:0 };
    enterHold.timer = setTimeout(function () {
      if (!enterHold || enterHold.element !== element) return;
      enterHold.fired = true;
      var event;
      try { event = new CustomEvent('xtreamlongpress', { bubbles:true, cancelable:true }); }
      catch (error) {
        event = document.createEvent('CustomEvent');
        event.initCustomEvent('xtreamlongpress', true, true, null);
      }
      element.dispatchEvent(event);
    }, 560);
  }

  document.addEventListener('focusin', function (event) {
    if (event.target.classList && event.target.classList.contains('focusable')) {
      if (enterHold && enterHold.element !== event.target) clearEnterHold();
      current = event.target;
    }
  });

  document.addEventListener('keydown', function (event) {
    if (event.defaultPrevented) return;
    if ((window.XtreamlyTVApp && window.XtreamlyTVApp.playerOpen) || (window.TVeeApp && window.TVeeApp.playerOpen)) return;
    var active = document.activeElement;
    var input = /INPUT|TEXTAREA|SELECT/.test(active && active.tagName);
    if (input && (event.keyCode === KEY.LEFT || event.keyCode === KEY.RIGHT)) return;

    if ((window.XtreamlyTVApp && window.XtreamlyTVApp.currentView === 'home') && active && active.dataset && active.dataset.homeRow !== undefined && (event.keyCode === KEY.UP || event.keyCode === KEY.DOWN)) {
      if (moveHomeRow(active, event.keyCode === KEY.UP ? -1 : 1)) { event.preventDefault(); return; }
    }
    if (active && active.closest &&
        active.closest('.series-summary .detail-actions') &&
        (event.keyCode === KEY.LEFT || event.keyCode === KEY.RIGHT ||
         event.keyCode === KEY.UP || event.keyCode === KEY.DOWN)) {
      if (moveSeriesAction(
        active,
        event.keyCode === KEY.LEFT ? 'left' :
        event.keyCode === KEY.RIGHT ? 'right' :
        event.keyCode === KEY.UP ? 'up' : 'down'
      )) {
        event.preventDefault();
        return;
      }
    }

    if (active && active.classList &&
        active.classList.contains('season-button') &&
        (event.keyCode === KEY.LEFT || event.keyCode === KEY.RIGHT ||
         event.keyCode === KEY.UP || event.keyCode === KEY.DOWN)) {
      if (moveSeriesSeason(
        active,
        event.keyCode === KEY.LEFT ? 'left' :
        event.keyCode === KEY.RIGHT ? 'right' :
        event.keyCode === KEY.UP ? 'up' : 'down'
      )) {
        event.preventDefault();
        return;
      }
    }

    if (active && active.classList &&
        active.classList.contains('episode-card') &&
        (event.keyCode === KEY.LEFT || event.keyCode === KEY.RIGHT ||
         event.keyCode === KEY.UP || event.keyCode === KEY.DOWN)) {
      if (moveSeriesEpisode(
        active,
        event.keyCode === KEY.LEFT ? 'left' :
        event.keyCode === KEY.RIGHT ? 'right' :
        event.keyCode === KEY.UP ? 'up' : 'down'
      )) {
        event.preventDefault();
        return;
      }
    }
    if (active && active.classList && active.classList.contains('category-button') && (event.keyCode === KEY.UP || event.keyCode === KEY.DOWN)) {
      if (moveSibling(active, '.category-button', event.keyCode === KEY.UP ? -1 : 1, true)) { event.preventDefault(); return; }
    }
    if (active && active.classList && active.classList.contains('nav-item') && (event.keyCode === KEY.UP || event.keyCode === KEY.DOWN)) {
      if (moveSibling(active, '.nav-item', event.keyCode === KEY.UP ? -1 : 1, true)) { event.preventDefault(); return; }
    }
    if (active && active.classList && active.classList.contains('favorite-group-card') && (event.keyCode === KEY.LEFT || event.keyCode === KEY.RIGHT)) {
      var trap = event.keyCode === KEY.RIGHT;
      if (moveSibling(active, '.favorite-group-card', event.keyCode === KEY.LEFT ? -1 : 1, trap)) { event.preventDefault(); return; }
    }
    if (active && active.closest && active.closest('.favorite-groups-heading-actions') && (event.keyCode === KEY.LEFT || event.keyCode === KEY.RIGHT)) {
      if (moveSibling(active, '.focusable', event.keyCode === KEY.LEFT ? -1 : 1, true)) { event.preventDefault(); return; }
    }

    if (active && active.closest && active.closest('.favorite-manager-header') && event.keyCode === KEY.DOWN) {
      if (moveHeaderToFirstRow(active, '.favorite-manager-row')) { event.preventDefault(); return; }
    }
    if (active && active.closest && active.closest('.favorite-item-manager-header') && event.keyCode === KEY.DOWN) {
      if (moveHeaderToFirstRow(active, '.favorite-item-manager-row')) { event.preventDefault(); return; }
    }

    if (active && active.id === 'cancelFavoriteEditorTop' && event.keyCode === KEY.DOWN) {
      var groupName = document.getElementById('favoriteGroupName');
      if (focusElement(groupName, true)) { ensureVisible(groupName); event.preventDefault(); return; }
    }
    if (active && active.id === 'favoriteGroupName' && event.keyCode === KEY.UP) {
      var editorBack = document.getElementById('cancelFavoriteEditorTop');
      if (focusElement(editorBack, true)) { ensureVisible(editorBack); event.preventDefault(); return; }
    }
    if (active && active.id === 'favoriteGroupName' && event.keyCode === KEY.DOWN) {
      var activeIcon = document.querySelector('.favorite-icon-choice.active') || document.querySelector('.favorite-icon-choice');
      if (focusElement(activeIcon, true)) { ensureVisible(activeIcon); event.preventDefault(); return; }
    }
    if (active && active.classList && active.classList.contains('favorite-icon-choice') && event.keyCode === KEY.UP) {
      var editorName = document.getElementById('favoriteGroupName');
      if (focusElement(editorName, true)) { ensureVisible(editorName); event.preventDefault(); return; }
    }

    if (active && active.closest && active.closest('.favorite-item-manager-row') && (event.keyCode === KEY.LEFT || event.keyCode === KEY.RIGHT || event.keyCode === KEY.UP || event.keyCode === KEY.DOWN)) {
      if (moveActionRow(active, '.favorite-item-manager-row', event.keyCode === KEY.LEFT ? 'left' : event.keyCode === KEY.RIGHT ? 'right' : event.keyCode === KEY.UP ? 'up' : 'down')) { event.preventDefault(); return; }
    }
    if (active && active.closest && active.closest('.favorite-manager-row') && (event.keyCode === KEY.LEFT || event.keyCode === KEY.RIGHT || event.keyCode === KEY.UP || event.keyCode === KEY.DOWN)) {
      if (moveActionRow(active, '.favorite-manager-row', event.keyCode === KEY.LEFT ? 'left' : event.keyCode === KEY.RIGHT ? 'right' : event.keyCode === KEY.UP ? 'up' : 'down')) { event.preventDefault(); return; }
    }

    if (event.keyCode === KEY.LEFT) { event.preventDefault(); move('left'); }
    else if (event.keyCode === KEY.RIGHT) { event.preventDefault(); move('right'); }
    else if (event.keyCode === KEY.UP) { event.preventDefault(); move('up'); }
    else if (event.keyCode === KEY.DOWN) { event.preventDefault(); move('down'); }
    else if (event.keyCode === KEY.ENTER && document.activeElement && !input) {
      event.preventDefault();
      var target = document.activeElement;
      if (target.hasAttribute && target.hasAttribute('data-long-press')) {
        if (!event.repeat && (!enterHold || enterHold.element !== target)) beginEnterHold(target);
      } else target.click();
    }
  });

  document.addEventListener('keyup', function (event) {
    if (event.keyCode !== KEY.ENTER || !enterHold) return;
    event.preventDefault();
    var hold = enterHold;
    clearEnterHold();
    if (!hold.fired && hold.element && document.documentElement.contains(hold.element)) hold.element.click();
  });

  window.addEventListener('blur', clearEnterHold);

  if (window.MutationObserver) {
    new MutationObserver(invalidate).observe(document.documentElement, { childList:true, subtree:true, attributes:true, attributeFilter:['disabled','hidden'] });
  }
  window.addEventListener('resize', invalidate);

  window.XtreamlyTVNavigation = window.TVeeNavigation = {
    focusFirst:function (selector) {
      var list = focusables(true);
      var el = selector ? document.querySelector(selector) : list[0];
      if (el) focusElement(el, true);
    },
    invalidate:invalidate,
    reset:function () { current = null; invalidate(); }
  };
}());
