(function () {
  'use strict';

  var STATE_KEY = 'xtreamlytv.state.v1';
  var DEFAULT_THEME = 'blue';
  var ALLOWED = ['teal','gray','purple','pink','blue'];
  var THEME_META = { teal:'#071014', gray:'#101113', purple:'#10091b', pink:'#170a13', blue:'#07101d' };
  var ASSETS = {
    blue:'assets/xtreamlytv-wordmark-blue.svg',
    teal:'assets/xtreamlytv-wordmark-teal.svg',
    gray:'assets/xtreamlytv-wordmark-gray.svg',
    purple:'assets/xtreamlytv-wordmark-purple.svg',
    pink:'assets/xtreamlytv-wordmark-pink.svg'
  };
  var currentTheme = DEFAULT_THEME;
  var startedAt = Date.now();
  var minimumDuration = 2050;
  var maximumDuration = 5000;
  var ready = false;
  var finished = false;
  var appObserver = null;
  var brandObserver = null;

  function storedTheme() {
    try {
      var raw = window.localStorage && window.localStorage.getItem(STATE_KEY);
      var state = raw ? JSON.parse(raw) : null;
      var theme = state && state.settings && state.settings.theme;
      return ALLOWED.indexOf(theme) >= 0 ? theme : DEFAULT_THEME;
    } catch (error) { return DEFAULT_THEME; }
  }

  function assetFor(theme) {
    return ASSETS[ALLOWED.indexOf(theme) >= 0 ? theme : DEFAULT_THEME] || ASSETS.blue;
  }

  function normalizeLegacyBrand() {
    var logos = document.querySelectorAll('.theme-brand-logo');
    Array.prototype.forEach.call(logos, function (logo) {
      logo.classList.remove('theme-brand-logo');
      logo.innerHTML = '<img class="brand-wordmark" src="' + assetFor(currentTheme) + '" alt="XtreamlyTV">';
    });
  }

  function updateWordmarks(root) {
    root = root || document;
    normalizeLegacyBrand();
    var images = root.querySelectorAll ? root.querySelectorAll('img.brand-wordmark') : [];
    Array.prototype.forEach.call(images, function (image) {
      var desired = assetFor(currentTheme);
      if (image.getAttribute('src') !== desired) image.setAttribute('src', desired);
    });
    var startup = document.getElementById('xtStartupWordmark');
    if (startup) startup.setAttribute('src', assetFor(currentTheme));
  }

  function applyBrand(theme) {
    currentTheme = ALLOWED.indexOf(theme) >= 0 ? theme : DEFAULT_THEME;
    updateWordmarks(document);
    return currentTheme;
  }

  window.XtreamlyTVBrand = {
    apply:applyBrand,
    assetFor:assetFor,
    currentTheme:function () { return currentTheme; }
  };

  function applyEarlyTheme() {
    currentTheme = storedTheme();
    if (document.body) document.body.setAttribute('data-theme', currentTheme);
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', THEME_META[currentTheme] || THEME_META.blue);
  }

  function buildSplash() {
    var splash = document.getElementById('startupSplash');
    if (!splash) {
      splash = document.createElement('div');
      splash.id = 'startupSplash';
      splash.className = 'xt-startup';
      document.body.appendChild(splash);
    }
    splash.className = 'xt-startup';
    splash.setAttribute('aria-hidden','true');
    splash.innerHTML =
      '<div class="xt-startup-frame">' +
        '<div class="xt-startup-lockup">' +
          '<img id="xtStartupWordmark" class="xt-startup-wordmark" src="' + assetFor(currentTheme) + '" alt="XtreamlyTV">' +
          '<span class="xt-startup-flare"></span>' +
        '</div>' +
        '<div class="xt-startup-status">Live<i></i>Movies<i></i>Series</div>' +
      '</div>';
    scaleSplash();
    return splash;
  }

  function scaleSplash() {
    var splash = document.getElementById('startupSplash');
    if (!splash) return;
    var scale = Math.min(window.innerWidth / 1920, window.innerHeight / 1080);
    if (!isFinite(scale) || scale <= 0) scale = 1;
    var left = Math.max(0, (window.innerWidth - 1920 * scale) / 2);
    var top = Math.max(0, (window.innerHeight - 1080 * scale) / 2);
    splash.style.left = left + 'px';
    splash.style.top = top + 'px';
    splash.style.transform = Math.abs(scale - 1) < 0.001 ? 'none' : 'scale(' + scale + ')';
  }

  function playSound() {
    try {
      var audio = new Audio('assets/startup-chime.mp3');
      audio.preload = 'auto';
      audio.volume = 0.46;
      var result = audio.play();
      if (result && typeof result.catch === 'function') result.catch(function () {});
      window.__xtreamlyStartupAudio = audio;
    } catch (error) {}
  }

  function appHasMeaningfulContent() {
    var app = document.getElementById('app');
    return !!(app && app.firstElementChild && app.querySelector('.shell,.login-screen,.screen'));
  }

  function actuallyFinish() {
    if (finished) return;
    finished = true;
    var splash = document.getElementById('startupSplash');
    if (!splash) return;
    splash.classList.add('is-leaving');
    setTimeout(function () {
      if (splash && splash.parentNode) splash.parentNode.removeChild(splash);
      window.__xtreamlyStartupAudio = null;
    }, 520);
  }

  function finishWhenAllowed() {
    if (finished || !ready) return;
    var remaining = minimumDuration - (Date.now() - startedAt);
    if (remaining > 0) setTimeout(actuallyFinish, remaining);
    else actuallyFinish();
  }

  function markReady() { ready = true; finishWhenAllowed(); }

  function observeApp() {
    var app = document.getElementById('app');
    if (!app || typeof MutationObserver === 'undefined') {
      setTimeout(markReady, minimumDuration);
      return;
    }
    appObserver = new MutationObserver(function () {
      updateWordmarks(document);
      if (appHasMeaningfulContent()) {
        appObserver.disconnect();
        appObserver = null;
        markReady();
      }
    });
    appObserver.observe(app,{childList:true,subtree:true});
    if (appHasMeaningfulContent()) markReady();
  }

  function observeBrandNodes() {
    if (typeof MutationObserver === 'undefined') return;
    brandObserver = new MutationObserver(function (records) {
      for (var i=0;i<records.length;i+=1) {
        if (records[i].addedNodes && records[i].addedNodes.length) {
          updateWordmarks(document);
          return;
        }
      }
    });
    brandObserver.observe(document.body,{childList:true,subtree:true});
  }

  applyEarlyTheme();
  buildSplash();
  updateWordmarks(document);
  observeBrandNodes();
  observeApp();
  playSound();
  window.addEventListener('resize', scaleSplash);

  setTimeout(function () { ready = true; actuallyFinish(); }, maximumDuration);

  window.XtreamlyTVStartup = { finish:markReady, theme:storedTheme };
})();
