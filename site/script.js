(function () {
  'use strict';

  var API = 'https://api.github.com/repos/Speyll/Ovela/releases/latest';
  var RELEASES = 'https://github.com/Speyll/Ovela/releases';

  function setText(selector, value) {
    document.querySelectorAll(selector).forEach(function (node) {
      node.textContent = value;
    });
  }

  function formatSize(bytes) {
    if (typeof bytes !== 'number' || !isFinite(bytes) || bytes <= 0) return null;
    return (bytes / 1000000).toFixed(1) + ' MB';
  }

  function findAsset(assets, extension) {
    return assets.find(function (asset) {
      return asset &&
        typeof asset.name === 'string' &&
        asset.name.toLowerCase().endsWith(extension) &&
        typeof asset.browser_download_url === 'string';
    });
  }

  function applyDownload(kind, asset) {
    document.querySelectorAll('[data-download="' + kind + '"]').forEach(function (link) {
      link.href = asset.browser_download_url;
    });
    setText('[data-download-name="' + kind + '"]', asset.name);
    var size = formatSize(asset.size);
    if (size) setText('[data-download-size="' + kind + '"]', size);
  }

  function applyRelease(release) {
    if (!release || typeof release.tag_name !== 'string' || !Array.isArray(release.assets)) return;

    var version = release.tag_name.replace(/^v/i, '');
    var releaseUrl = typeof release.html_url === 'string'
      ? release.html_url
      : RELEASES + '/tag/' + release.tag_name;

    if (version) setText('[data-release-version]', version);
    document.querySelectorAll('[data-release-link]').forEach(function (link) {
      link.href = releaseUrl;
    });

    var apk = findAsset(release.assets, '.apk');
    var ipk = findAsset(release.assets, '.ipk');
    var checksums = findAsset(release.assets, 'sha256sums.txt');
    if (apk) applyDownload('android', apk);
    if (ipk) applyDownload('webos', ipk);
    if (checksums) {
      document.querySelectorAll('[data-checksums-link]').forEach(function (link) {
        link.href = checksums.browser_download_url;
      });
    }

    var status = document.querySelector('[data-release-status]');
    if (status) status.textContent = 'Latest release from GitHub';
  }

  function loadLatestRelease() {
    var controller = new AbortController();
    var timer = window.setTimeout(function () { controller.abort(); }, 6000);

    fetch(API, {
      headers: { Accept: 'application/vnd.github+json' },
      signal: controller.signal,
      cache: 'no-store',
    })
      .then(function (response) {
        if (!response.ok) throw new Error('Latest release request failed');
        return response.json();
      })
      .then(applyRelease)
      .catch(function () {
        // Keep the static, verified 1.0.1 asset links and the releases-page fallback.
      })
      .finally(function () {
        window.clearTimeout(timer);
      });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', loadLatestRelease, { once: true });
  } else {
    loadLatestRelease();
  }
}());
