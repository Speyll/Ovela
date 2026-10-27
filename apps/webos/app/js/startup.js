(function () {
  'use strict';

  var STATE_KEY = 'io.github.speyll.ovela.state.v1';
  var DEFAULT_THEME = 'blue';
  var ALLOWED = ['teal','gray','purple','pink','blue'];
  // Ovela ships one dark appearance; a stored legacy theme still selects an
  // accent, never a different wordmark.
  var THEME_META = { teal:'#0B0B0D', gray:'#0E0E10', purple:'#0B0B0D', pink:'#0B0B0D', blue:'#0B0B0D' };
  var currentTheme = DEFAULT_THEME;
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

  function assetFor() {
    return '';
  }

  // The Ovela lockup is CSS, not an image, so there is nothing to swap when the
  // accent changes. Kept as a no-op because older callers still invoke it.
  function updateWordmarks() {}

  function applyBrand(theme) {
    currentTheme = ALLOWED.indexOf(theme) >= 0 ? theme : DEFAULT_THEME;
    updateWordmarks(document);
    return currentTheme;
  }

  window.OvelaBrand = {
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
      splash.className = 'ovela-startup';
      document.body.appendChild(splash);
    }
    splash.className = 'ovela-startup';
    splash.setAttribute('aria-hidden','true');
    splash.innerHTML =
      '<div class="ovela-startup-frame">' +
        '<div class="ovela-startup-lockup">' +
          '<span class="ovela-mark" aria-hidden="true"></span>' +
          '<img class="ovela-word" src="assets/ovela-wordmark.png" alt="Ovela" aria-hidden="true">' +
        '</div>' +
        '<div class="ovela-startup-progress" aria-hidden="true"><i></i></div>' +
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
    }, 260);
  }

  function markReady() {
    if (finished) return;
    actuallyFinish();
  }

  function observeApp() {
    var app = document.getElementById('app');
    if (!app || typeof MutationObserver === 'undefined') {
      setTimeout(markReady, 0);
      return;
    }
    appObserver = new MutationObserver(function () {
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
  window.addEventListener('resize', scaleSplash);

  // Safety net only: if a provider or the app shell never renders a screen,
  // show the shell rather than trapping the viewer on a black splash forever.
  setTimeout(markReady, 8000);

  window.OvelaStartup = { finish:markReady, theme:storedTheme };
})();
