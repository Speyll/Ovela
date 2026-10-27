"""Playback lifecycle and local-media regressions for the webOS shell.

These tests run in desktop Chromium with the production inline-able webOS
scripts. They do not claim to validate TV firmware, remote-control behavior,
or device codecs.
"""
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import os
from pathlib import Path
import threading

from playwright.sync_api import sync_playwright

APP_ROOT = Path(__file__).resolve().parents[1] / "app"
MEDIA_ROOT = Path(__file__).resolve().parents[3] / ".dev" / "testing" / "fixtures" / "media"


class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, _format, *_args):
        pass


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


def start_media_server():
    handler = partial(QuietHandler, directory=str(MEDIA_ROOT))
    server = ThreadingHTTPServer(("127.0.0.1", 0), handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server


def main() -> None:
    assert (MEDIA_ROOT / "clip.mp4").is_file(), "run generate-media-fixtures.sh first"
    server = start_media_server()
    base = f"http://127.0.0.1:{server.server_address[1]}"
    try:
        with sync_playwright() as playwright:
            options = {
                "headless": True,
                "args": ["--autoplay-policy=no-user-gesture-required"],
            }
            if os.environ.get("CHROMIUM_PATH"):
                options["executable_path"] = os.environ["CHROMIUM_PATH"]
            browser = playwright.chromium.launch(**options)
            page = browser.new_page(viewport={"width": 1920, "height": 1080})
            errors = []
            page.on("pageerror", lambda error: errors.append(str(error)))
            page.set_content(inline_app(), wait_until="domcontentloaded")

            # A stream that never emits `playing` or `canplay` must still be
            # reported after the first-frame deadline. The watchdog is started
            # at load time, not only from the `playing` event.
            no_start = page.evaluate("""async () => {
              const app = OvelaApp;
              const realGet = document.getElementById.bind(document);
              const attempts = [];
              const video = {
                paused: true, ended: false, error: null, currentTime: 0,
                videoWidth: 0, videoHeight: 0, firstChild: null,
                appendChild: node => { video.firstChild = node; },
                removeAttribute: () => {},
                removeChild: () => { video.firstChild = null; },
                load: () => {},
                pause: () => {},
                play: () => new Promise(() => {}),
                addEventListener: () => {}
              };
              document.getElementById = id => id === 'video' ? video : realGet(id);
              app.playerOpen = true;
              app.playerType = 'live';
              app.playerMedia = {stream_id: 'fixture-no-start'};
              app.playbackCandidates = [{label: 'HLS', url: 'https://fixture.invalid/no-start.m3u8'}];
              app.playbackCandidateIndex = 0;
              app.playbackCandidateRetries = {};
              app.playbackFailureLock = false;
              app.playerHasPlayed = false;
              app.showPlayerLoading = () => {};
              app.clearPlayerBuffering = () => {};
              app.showPlayerError = () => {};
              app.tryNextPlaybackCandidate = reason => attempts.push(reason);
              app.loadPlaybackCandidate(0, 'Opening stream…');
              app.playbackStartedAt = Date.now() - 21000;
              await new Promise(resolve => setTimeout(resolve, 2800));
              clearTimeout(app.playbackWatchdog);
              clearTimeout(app.playbackStartTimer);
              app.playbackWatchdog = null;
              app.playbackStartTimer = null;
              app.playerOpen = false;
              document.getElementById = realGet;
              return attempts;
            }""")
            assert no_start == ["The stream did not start."], no_start

            # A rejected play promise belongs to the candidate that created it.
            # Loading the next channel must invalidate its delayed failure path.
            stale = page.evaluate("""async () => {
              const app = OvelaApp;
              const realGet = document.getElementById.bind(document);
              let playCalls = 0;
              const attempts = [];
              const video = {
                paused: true, ended: false, error: null, currentTime: 0,
                videoWidth: 0, videoHeight: 0, firstChild: null,
                appendChild: node => { video.firstChild = node; },
                removeAttribute: () => {},
                removeChild: () => { video.firstChild = null; },
                load: () => {},
                pause: () => {},
                play: () => {
                  playCalls += 1;
                  return playCalls === 1 ? Promise.reject(new Error('autoplay blocked')) : new Promise(() => {});
                },
                addEventListener: () => {}
              };
              document.getElementById = id => id === 'video' ? video : realGet(id);
              app.playerOpen = true;
              app.playerType = 'live';
              app.playerMedia = {stream_id: 'fixture-a'};
              app.playbackCandidates = [{label: 'HLS', url: 'https://fixture.invalid/a.m3u8'}];
              app.playbackCandidateIndex = 0;
              app.playbackCandidateRetries = {};
              app.playbackFailureLock = false;
              app.playerHasPlayed = false;
              app.showPlayerLoading = () => {};
              app.clearPlayerBuffering = () => {};
              app.showPlayerError = () => {};
              app.tryNextPlaybackCandidate = reason => attempts.push(reason);
              app.loadPlaybackCandidate(0, 'Opening A…');
              app.playerMedia = {stream_id: 'fixture-b'};
              app.playbackCandidates = [{label: 'HLS', url: 'https://fixture.invalid/b.m3u8'}];
              app.loadPlaybackCandidate(0, 'Changing channel…');
              await new Promise(resolve => setTimeout(resolve, 450));
              clearTimeout(app.playbackWatchdog);
              clearTimeout(app.playbackStartTimer);
              app.playbackWatchdog = null;
              app.playbackStartTimer = null;
              app.playerOpen = false;
              document.getElementById = realGet;
              return {attempts, playCalls};
            }""")
            assert stale == {"attempts": [], "playCalls": 2}, stale

            # Drive the production player with a real local MP4. This validates
            # URL wiring, media element lifecycle, and Chromium decoding only.
            app_playback = page.evaluate("""async (url) => {
              const app = OvelaApp;
              app.startDemo();
              app.demo = false;
              app.api.getMediaCandidates = () => [{format: 'mp4', label: 'MP4', url, mime: 'video/mp4'}];
              const item = OvelaMock.vodStreams[0];
              app.playMedia(item, 'movie', [item]);
              const video = document.getElementById('video');
              if (video) video.muted = true;
              return {title: document.getElementById('playerTitle')?.textContent || ''};
            }""", f"{base}/clip.mp4")
            assert app_playback["title"], app_playback
            page.wait_for_function(
                """() => {
                  const video = document.getElementById('video');
                  return Boolean(video && (video.readyState >= 2 || video.error));
                }""",
                timeout=10000,
            )
            page.evaluate("() => { const v = document.getElementById('video'); if (v) { v.muted = true; v.play().catch(() => {}); } }")
            page.wait_for_function(
                """() => {
                  const video = document.getElementById('video');
                  return Boolean(video && !video.paused && video.currentTime > 0.1);
                }""",
                timeout=10000,
            )
            media = page.evaluate("""() => {
              const video = document.getElementById('video');
              return {
                duration: video.duration,
                width: video.videoWidth,
                height: video.videoHeight,
                currentTime: video.currentTime,
                paused: video.paused,
                error: video.error && video.error.code,
              };
            }""")
            assert media["error"] is None, media
            assert abs(media["duration"] - 3) < 0.2, media
            assert media["width"] == 320 and media["height"] == 180, media
            assert media["paused"] is False and media["currentTime"] > 0.1, media
            assert errors == [], errors
            browser.close()
    finally:
        server.shutdown()
        server.server_close()
    print("PASS: first-frame watchdog, stale play-rejection guard, and local MP4 playback.")


if __name__ == "__main__":
    main()
