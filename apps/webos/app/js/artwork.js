/* Failed provider logos remain retryable; never remove their saved URL. */
(function () {
  'use strict';
  var failed = [];
  var retryTimer = null;
  function scheduleRetry() {
    if (retryTimer || !failed.length) return;
    retryTimer = setInterval(function () { retry(false); }, 5000);
  }
  function stopRetry() {
    if (!retryTimer) return;
    clearInterval(retryTimer);
    retryTimer = null;
  }
  function channel(image) { return image && image.tagName === 'IMG' && image.hasAttribute('data-channel-art'); }
  document.addEventListener('error', function (event) {
    var image = event.target;
    if (!channel(image)) return;
    image.__artUrl = image.__artUrl || image.getAttribute('src');
    image.__artAttempts = (image.__artAttempts || 0) + 1;
    image.__artRetryAt = Date.now() + Math.min(300000, 30000 * image.__artAttempts);
    if (failed.indexOf(image) < 0) failed.push(image);
    // Only poll while something is actually waiting for a retry.
    scheduleRetry();
  }, true);
  document.addEventListener('load', function (event) {
    var image = event.target;
    if (!channel(image)) return;
    image.style.display = '';
    image.__artAttempts = 0;
    if (image.nextElementSibling) image.nextElementSibling.style.display = 'none';
    if (image.parentNode) image.parentNode.classList.remove('image-failed');
    failed = failed.filter(function (entry) { return entry !== image; });
  }, true);
  function retry(force) {
    var budget = 4;
    failed = failed.filter(function (image) { return document.documentElement.contains(image); });
    failed.forEach(function (image) {
      if (!budget || (!force && Date.now() < image.__artRetryAt)) return;
      if (!image.parentNode.getClientRects().length) return;
      budget -= 1;
      image.__artRetryAt = Date.now() + 300000;
      // Avoid a browser-cached HTTP error; keep the original URL in memory/state.
      var url = image.__artUrl;
      var hash = '';
      var index = url.indexOf('#');
      if (index >= 0) { hash = url.slice(index); url = url.slice(0, index); }
      image.src = url + (url.indexOf('?') >= 0 ? '&' : '?') + '_xtv_retry=' + Date.now() + hash;
    });
    if (!failed.length) stopRetry();
  }
  // The retry loop is armed on demand (see scheduleRetry) instead of waking the
  // TV's JS thread every five seconds for the whole session.
  window.addEventListener('online', function () { retry(true); });
  document.addEventListener('visibilitychange', function () { if (!document.hidden) retry(true); });
}());
