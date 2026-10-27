"""Playback recovery regressions for the webOS production shell."""
from pathlib import Path
import os

from playwright.sync_api import sync_playwright

APP_ROOT = Path(__file__).resolve().parents[1] / "app"


def inline_app() -> str:
    css = "\n".join((APP_ROOT / "css" / name).read_text(encoding="utf-8") for name in (
        "tokens.css", "app.css", "v0.7-ui.css", "legacy-layout.css",
    ))
    scripts = []
    for name in (
        "core.js", "store.js", "api.js", "mock-data.js", "navigation.js",
        "virtual-grid.js", "category-rail.js", "artwork.js", "app.js", "v0.7-ui.js",
    ):
        source = (APP_ROOT / "js" / name).read_text(encoding="utf-8").replace("</script>", "<\\/script>")
        scripts.append("<script>" + source + "</script>")
    return (
        "<!doctype html><html><head><meta charset='utf-8'><style>" + css +
        "</style></head><body><div id='app'></div><div id='playerHost'></div>"
        "<div id='toast'></div>" + "".join(scripts) + "</body></html>"
    )


def main() -> None:
    with sync_playwright() as playwright:
        options = {"headless": True}
        if os.environ.get("CHROMIUM_PATH"):
            options["executable_path"] = os.environ["CHROMIUM_PATH"]
        browser = playwright.chromium.launch(**options)
        page = browser.new_page(viewport={"width": 1920, "height": 1080})
        errors = []
        page.on("pageerror", lambda error: errors.append(str(error)))
        page.set_content(inline_app(), wait_until="domcontentloaded")

        retry = page.evaluate("""() => {
          const app = OvelaApp;
          const host = document.getElementById('playerHost');
          host.innerHTML = '<section id="player" class="player"><video id="video"></video>' +
            '<div id="playerLoading" class="player-loading"><strong id="playerLoadingText"></strong></div></section>';
          app.playerOpen = true;
          app.playerMedia = {stream_id: 901};
          app.playerType = 'live';
          app.playbackCandidates = [
            {format: 'm3u8', label: 'HLS', url: 'https://fixture.invalid/live.m3u8'},
            {format: 'ts', label: 'MPEG-TS', url: 'https://fixture.invalid/live.ts'}
          ];
          app.playbackCandidateIndex = 1;
          app.playbackCandidateRetries = {0: 1, 1: 1};
          app.playbackFailureLock = true;
          app.playbackSwitching = false;
          app.playerHasPlayed = true;
          app.showPlayerError('Unable to display this stream', 'Fixture failure.');
          const button = document.getElementById('retryPlayback');
          const focused = document.activeElement && document.activeElement.id;
          const loaded = [];
          app.loadPlaybackCandidate = (index, message) => {
            loaded.push({index, message});
            app.playbackCandidateIndex = index;
            app.playbackFailureLock = false;
          };
          if (button) button.click();
          return {
            button: Boolean(button),
            focused,
            loaded,
            index: app.playbackCandidateIndex,
            retries: app.playbackCandidateRetries,
            failureLock: app.playbackFailureLock,
            errorOpen: app.playerErrorOpen,
            hasErrorOverlay: Boolean(document.querySelector('.player-error'))
          };
        }""")
        assert retry["button"] is True, retry
        assert retry["focused"] == "retryPlayback", retry
        assert retry["loaded"] == [{"index": 0, "message": "Reconnecting stream…"}], retry
        assert retry["index"] == 0 and retry["retries"] == {}, retry
        assert retry["failureLock"] is False and retry["errorOpen"] is False, retry
        assert retry["hasErrorOverlay"] is False, retry

        key_retry = page.evaluate("""() => {
          const app = OvelaApp;
          const host = document.getElementById('playerHost');
          host.innerHTML = '<section id="player" class="player"><video id="video"></video>' +
            '<div id="playerLoading" class="player-loading"><strong id="playerLoadingText"></strong></div></section>';
          app.playerOpen = true;
          app.playerMedia = {stream_id: 902};
          app.playerType = 'live';
          app.playbackCandidates = [{format: 'm3u8', label: 'HLS', url: 'https://fixture.invalid/retry.m3u8'}];
          app.playbackCandidateIndex = 0;
          app.playbackCandidateRetries = {0: 1};
          app.playbackFailureLock = true;
          app.showPlayerError('Unable to display this stream', 'Fixture failure.');
          const loaded = [];
          app.loadPlaybackCandidate = (index, message) => {
            loaded.push({index, message});
            app.playbackCandidateIndex = index;
            app.playbackFailureLock = false;
          };
          let prevented = false;
          app.onGlobalKey({keyCode: 13, preventDefault: () => { prevented = true; }});
          return {loaded, prevented};
        }""")
        assert key_retry == {
            "loaded": [{"index": 0, "message": "Reconnecting stream…"}],
            "prevented": True,
        }, key_retry

        decision = page.evaluate("""async () => {
          const app = OvelaApp;
          const originalDecision = window.OvelaCore.recoveryDecision;
          const originalLoad = app.loadPlaybackCandidate;
          const originalToast = app.toast;
          const originalLoading = app.showPlayerLoading;
          const originalError = app.showPlayerError;
          const calls = [];
          const loaded = [];
          window.OvelaCore.recoveryDecision = (state, failure) => {
            calls.push({state, failure});
            return originalDecision(state, failure);
          };
          app.playerOpen = true;
          app.playerType = 'live';
          app.playerMedia = {stream_id: 903};
          app.playbackCandidates = [
            {format: 'm3u8', label: 'HLS', url: 'https://fixture.invalid/a.m3u8'},
            {format: 'ts', label: 'MPEG-TS', url: 'https://fixture.invalid/a.ts'}
          ];
          app.playbackCandidateIndex = 0;
          app.playbackCandidateRetries = {};
          app.playbackFailureLock = false;
          app.loadPlaybackCandidate = (index, message) => {
            loaded.push(index);
            app.playbackCandidateIndex = index;
            app.playbackFailureLock = false;
          };
          app.toast = () => {};
          app.showPlayerLoading = () => {};
          app.showPlayerError = () => {};
          app.tryNextPlaybackCandidate('Fixture failure.');
          await new Promise(resolve => setTimeout(resolve, 700));
          const first = {
            calls: calls.slice(),
            loaded: loaded.slice(),
            retries: Object.assign({}, app.playbackCandidateRetries)
          };
          clearTimeout(app.playbackRetryTimer);
          app.playbackRetryTimer = null;
          app.playbackFailureLock = false;
          app.tryNextPlaybackCandidate('Fixture failure.');
          const second = {calls: calls.slice(), loaded: loaded.slice()};
          window.OvelaCore.recoveryDecision = originalDecision;
          app.loadPlaybackCandidate = originalLoad;
          app.toast = originalToast;
          app.showPlayerLoading = originalLoading;
          app.showPlayerError = originalError;
          return {first, second};
        }""")
        assert len(decision["first"]["calls"]) == 1, decision
        assert decision["first"]["calls"][0]["state"]["attempts"] == 0, decision
        assert decision["first"]["calls"][0]["state"]["maxAttempts"] == 1, decision
        assert decision["first"]["loaded"] == [0], decision
        assert decision["first"]["retries"] == {"0": 1}, decision
        assert len(decision["second"]["calls"]) == 2, decision
        assert decision["second"]["loaded"][-1] == 1, decision

        vod_retry = page.evaluate("""() => {
          const app = OvelaApp;
          const store = OvelaStore;
          const realGet = document.getElementById.bind(document);
          const originalLoad = app.loadPlaybackCandidate;
          const loaded = [];
          let currentVideo = null;
          document.getElementById = id => id === 'video' ? currentVideo : realGet(id);
          app.loadPlaybackCandidate = (index, message) => {
            loaded.push({
              index,
              message,
              target: app.playbackResumeTarget,
              restored: app.playbackProgressRestored
            });
          };
          function reset(type, media, resumeTarget, restored, video) {
            currentVideo = video;
            app.playerOpen = true;
            app.playerType = type;
            app.playerMedia = media;
            app.playerErrorOpen = true;
            app.playerErrorRetryAvailable = true;
            app.playbackCandidates = [{label: 'MP4', url: 'https://fixture.invalid/retry.mp4'}];
            app.playbackCandidateIndex = 1;
            app.playbackCandidateRetries = {1: 1};
            app.playbackFailureLock = true;
            app.playbackResumeTarget = resumeTarget;
            app.playbackProgressRestored = restored;
            app.playbackResumeAttempts = 2;
            app.playerHasPlayed = true;
            loaded.length = 0;
          }

          // Known VOD position wins over older persisted progress, and the
          // zeroed media element during reload must not save over it.
          store.clearProgress('movie', 'retry-current');
          store.saveProgress('movie', 'retry-current', 700, 3600);
          reset('movie', {stream_id: 'retry-current'}, null, true, {currentTime: 900, duration: 3600});
          app.retryPlayback();
          const current = Object.assign({}, loaded[0]);
          currentVideo.currentTime = 0;
          app.maybeSaveProgress(currentVideo, true);
          const currentSaved = store.getProgress('movie', 'retry-current');

          // An unreadable player position falls back to the latest saved value.
          store.clearProgress('movie', 'retry-saved');
          store.saveProgress('movie', 'retry-saved', 610, 3600);
          store.saveProgress('movie', 'retry-saved', 640, 3600);
          reset('movie', {stream_id: 'retry-saved'}, null, true, {currentTime: NaN, duration: 3600});
          app.retryPlayback();
          const savedFallback = Object.assign({}, loaded[0]);

          // With no player or saved position, retain the safe start-from-zero path.
          store.clearProgress('movie', 'retry-empty');
          reset('movie', {stream_id: 'retry-empty'}, null, true, null);
          app.retryPlayback();
          const empty = Object.assign({}, loaded[0]);

          // Live retry must not acquire VOD resume state.
          reset('live', {stream_id: 'retry-live'}, null, true, {currentTime: 900, duration: 0});
          app.retryPlayback();
          const live = Object.assign({}, loaded[0]);

          app.loadPlaybackCandidate = originalLoad;
          document.getElementById = realGet;
          app.playerOpen = false;
          app.playerMedia = null;
          app.playerType = null;
          return {current, currentSaved, savedFallback, empty, live};
        }""")
        assert vod_retry["current"]["index"] == 0, vod_retry
        assert vod_retry["current"]["target"] == 900, vod_retry
        assert vod_retry["current"]["restored"] is False, vod_retry
        assert vod_retry["currentSaved"]["seconds"] == 700, vod_retry
        assert vod_retry["savedFallback"]["target"] == 640, vod_retry
        assert vod_retry["savedFallback"]["restored"] is False, vod_retry
        assert vod_retry["empty"]["target"] is None, vod_retry
        assert vod_retry["empty"]["restored"] is True, vod_retry
        assert vod_retry["empty"]["index"] == 0, vod_retry
        assert vod_retry["live"]["target"] is None, vod_retry
        assert vod_retry["live"]["restored"] is True, vod_retry
        assert vod_retry["live"]["index"] == 0, vod_retry

        assert errors == [], errors
        browser.close()
    print("PASS: terminal/live retry routing, shared recovery decisions, and VOD retry resume.")


if __name__ == "__main__":
    main()
