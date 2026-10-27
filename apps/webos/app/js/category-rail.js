(function () {
  'use strict';

  function clamp(value, min, max) { return Math.max(min, Math.min(max, value)); }

  function escapeHtml(value) {
    return String(value == null ? '' : value).replace(/[&<>'"]/g, function (char) {
      return ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', "'":'&#039;', '"':'&quot;' })[char];
    });
  }

  function CategoryRail(options) {
    this.container = options.container;
    this.items = Array.isArray(options.items) ? options.items : [];
    this.activeId = String(options.activeId || '');
    this.onActivate = options.onActivate || function () {};
    this.maxVisible = Math.max(1, Number(options.maxVisible || 13));
    this.start = 0;
    this.focusIndexValue = this.findIndex(this.activeId);
    if (this.focusIndexValue < 0) this.focusIndexValue = 0;
    this.start = this.windowStart(this.focusIndexValue);
    this.boundKeyDown = this.handleKeyDown.bind(this);
    this.boundClick = this.handleClick.bind(this);
    this.container.addEventListener('keydown', this.boundKeyDown, true);
    this.container.addEventListener('click', this.boundClick);
    this.render(false);
  }

  CategoryRail.prototype.findIndex = function (id) {
    id = String(id || '');
    for (var i = 0; i < this.items.length; i += 1) {
      if (String(this.items[i].id) === id) return i;
    }
    return -1;
  };

  CategoryRail.prototype.windowStart = function (index) {
    var maxStart = Math.max(0, this.items.length - this.maxVisible);
    if (index < this.start) return clamp(index, 0, maxStart);
    if (index >= this.start + this.maxVisible) return clamp(index - this.maxVisible + 1, 0, maxStart);
    return clamp(this.start, 0, maxStart);
  };

  CategoryRail.prototype.render = function (focusAfter) {
    /* Render the full category list and let the rail own its scrolling. The
       previous fixed-size window hid every category beyond the visible rows,
       and the D-pad could never move a window that the document-level
       navigation handler had already claimed. A scroll container keeps the
       pointer (Magic Remote wheel) and the D-pad on the same list. */
    var html = '';
    for (var i = 0; i < this.items.length; i += 1) {
      var item = this.items[i];
      var isActive = String(item.id) === this.activeId;
      html += '<button class="category-button focusable' + (isActive ? ' active' : '') + '" data-category-index="' + i + '" data-catalog-category="' + escapeHtml(item.id) + '"' + (isActive ? ' aria-current="true"' : '') + '>' +
        '<span class="category-label">' + escapeHtml(item.label) + '</span>' +
        (item.secondary ? '<small>' + escapeHtml(item.secondary) + '</small>' : '') +
        '</button>';
    }
    this.container.innerHTML = html;
    this.start = 0;
    this.container.setAttribute('data-window-start', '0');
    this.container.setAttribute('data-window-count', String(this.items.length));
    if (window.OvelaNavigation && window.OvelaNavigation.invalidate) window.OvelaNavigation.invalidate();
    if (focusAfter) this.focusDomIndex(this.focusIndexValue);
  };

  CategoryRail.prototype.scrollIntoView = function (button) {
    if (!button) return;
    var container = this.container;
    var itemRect = button.getBoundingClientRect();
    var containerRect = container.getBoundingClientRect();
    var guard = 12;
    if (itemRect.top < containerRect.top + guard) {
      container.scrollTop -= (containerRect.top + guard - itemRect.top);
    } else if (itemRect.bottom > containerRect.bottom - guard) {
      container.scrollTop += (itemRect.bottom - (containerRect.bottom - guard));
    }
  };

  CategoryRail.prototype.focusDomIndex = function (index) {
    var button = this.container.querySelector('[data-category-index="' + index + '"]');
    if (!button) return;
    try { button.focus({ preventScroll:true }); } catch (error) { button.focus(); }
    this.scrollIntoView(button);
  };

  CategoryRail.prototype.focusIndex = function (index) {
    if (!this.items.length) return;
    index = clamp(index, 0, this.items.length - 1);
    this.focusIndexValue = index;
    this.focusDomIndex(index);
  };

  CategoryRail.prototype.handleKeyDown = function (event) {
    var target = event.target && event.target.closest ? event.target.closest('[data-category-index]') : null;
    if (!target || !this.container.contains(target)) return;
    if (event.keyCode !== 38 && event.keyCode !== 40) return;
    var index = Number(target.dataset.categoryIndex);
    if (!isFinite(index)) return;
    var next = clamp(index + (event.keyCode === 38 ? -1 : 1), 0, Math.max(0, this.items.length - 1));
    event.preventDefault();
    event.stopPropagation();
    this.focusIndex(next);
  };

  CategoryRail.prototype.handleClick = function (event) {
    var target = event.target && event.target.closest ? event.target.closest('[data-category-index]') : null;
    if (!target || !this.container.contains(target)) return;
    var index = Number(target.dataset.categoryIndex);
    if (!isFinite(index) || !this.items[index]) return;
    this.focusIndexValue = index;
    this.onActivate(this.items[index], index, target);
  };

  CategoryRail.prototype.focusActive = function () {
    var index = this.findIndex(this.activeId);
    if (index < 0) index = 0;
    this.focusIndex(index);
  };

  CategoryRail.prototype.destroy = function () {
    this.container.removeEventListener('keydown', this.boundKeyDown, true);
    this.container.removeEventListener('click', this.boundClick);
  };

  window.OvelaCategoryRail = CategoryRail;
}());
