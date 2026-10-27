"""Xtream API/media host separation and redirect regression for the webOS shell."""
from functools import partial
from http.server import BaseHTTPRequestHandler, SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import threading
from urllib.parse import parse_qs, urlparse

from playwright.sync_api import sync_playwright

APP_ROOT = Path(__file__).resolve().parents[1] / "app"
MEDIA_ROOT = Path(__file__).resolve().parents[3] / ".dev" / "testing" / "fixtures" / "media"
LIVE_PATH = "/live/demo/demo-pass/1001.m3u8"
MOVIE_PATH = "/movie/demo/demo-pass/2001.mp4"


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


class CorsMediaHandler(SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        super().end_headers()

    def guess_type(self, path):
        if path.endswith(".m3u8"):
            return "application/vnd.apple.mpegurl"
        if path.endswith(".ts"):
            return "video/mp2t"
        return super().guess_type(path)

    def log_message(self, _format, *_args):
        pass


class FixtureApiHandler(BaseHTTPRequestHandler):
    media_base = ""

    def log_message(self, _format, *_args):
        pass

    def do_HEAD(self):
        self.respond(head=True)

    def do_GET(self):
        self.respond(head=False)

    def respond(self, head):
        parsed = urlparse(self.path)
        if parsed.path == "/player_api.php":
            self.player_api(parsed, head)
        elif parsed.path == LIVE_PATH:
            self.redirect(self.media_base + "/hls/index.m3u8", head)
        elif parsed.path == MOVIE_PATH:
            self.redirect(self.media_base + "/clip.mp4", head)
        else:
            self.payload(404, "application/json", '{"error":"not found"}', head)

    def player_api(self, parsed, head):
        query = parse_qs(parsed.query, keep_blank_values=True)
        username = (query.get("username") or [""])[0]
        password = (query.get("password") or [""])[0]
        action = (query.get("action") or [""])[0]
        if username != "demo" or password != "demo-pass":
            body = {"user_info": {"auth": 0, "message": "Synthetic credentials rejected"}}
        elif action == "":
            body = {"user_info": {"auth": 1, "username": "mock-user", "status": "Active"}}
        elif action == "get_live_categories":
            body = [{"category_id": "1", "category_name": "Fixture News"}]
        elif action == "get_live_streams":
            body = [{"stream_id": 1001, "name": "Fixture News One", "category_id": "1", "num": "1"}]
        else:
            body = []
        self.payload(200, "application/json", json.dumps(body), head)

    def redirect(self, location, head):
        self.send_response(302)
        self.send_header("Location", location)
        self.send_header("Content-Length", "0")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()

    def payload(self, status, content_type, body, head):
        encoded = body.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(encoded)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        if not head:
            self.wfile.write(encoded)


def start_media_server(host):
    handler = partial(CorsMediaHandler, directory=str(MEDIA_ROOT))
    server = ThreadingHTTPServer((host, 0), handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server


def start_api_server(host, media_base):
    FixtureApiHandler.media_base = media_base
    server = ThreadingHTTPServer((host, 0), FixtureApiHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server


def main() -> None:
    assert (MEDIA_ROOT / "hls" / "index.m3u8").is_file(), "HLS fixture is missing"
    assert (MEDIA_ROOT / "clip.mp4").is_file(), "MP4 fixture is missing"
    media_server = start_media_server("127.0.0.2")
    media_base = f"http://127.0.0.2:{media_server.server_address[1]}"
    api_server = start_api_server("127.0.0.1", media_base)
    api_base = f"http://127.0.0.1:{api_server.server_address[1]}"
    try:
        with sync_playwright() as playwright:
            options = {
                "headless": True,
                "args": [
                    "--autoplay-policy=no-user-gesture-required",
                    "--no-proxy-server",
                ],
            }
            if os.environ.get("CHROMIUM_PATH"):
                options["executable_path"] = os.environ["CHROMIUM_PATH"]
            browser = playwright.chromium.launch(**options)
            page = browser.new_page(viewport={"width": 1920, "height": 1080})
            errors = []
            responses = []
            page.on("pageerror", lambda error: errors.append(str(error)))
            page.on(
                "response",
                lambda response: responses.append({
                    "url": response.url,
                    "status": response.status,
                    "contentType": response.headers.get("content-type", ""),
                }),
            )
            page.set_content(inline_app(), wait_until="domcontentloaded")
            result = page.evaluate("""async (apiBase) => {
              const api = new OvelaApi({
                server: apiBase,
                username: 'demo',
                password: 'demo-pass'
              }, {streamFormat: 'auto'});
              const auth = await api.authenticate();
              const live = await api.getLiveStreams('1');
              const liveCandidates = api.getLiveCandidates(live[0].stream_id, ['m3u8', 'ts']);
              const liveResponse = await fetch(liveCandidates[0].url, {redirect: 'follow'});
              const playlist = await liveResponse.text();
              const segment = playlist.split(/\\r?\\n/).find(line => line && !line.startsWith('#'));
              const segmentUrl = new URL(segment, liveResponse.url).toString();

              const app = OvelaApp;
              app.startDemo();
              app.demo = false;
              app.api = new OvelaApi({
                server: apiBase,
                username: 'demo',
                password: 'demo-pass'
              }, {streamFormat: 'auto'});
              const item = {
                stream_id: 2001,
                name: 'Fixture Movie',
                content_type: 'movie',
                container_extension: 'mp4'
              };
              app.playMedia(item, 'movie', [item]);
              const video = document.getElementById('video');
              if (video) video.muted = true;
              const player = await new Promise(resolve => {
                let settled = false;
                const finish = value => {
                  if (settled) return;
                  settled = true;
                  resolve(value);
                };
                const check = () => {
                  if (!video) return finish({error: 'missing video'});
                  if (video.error) return finish({
                    error: video.error.code,
                    readyState: video.readyState
                  });
                  if (video.readyState >= 2) return finish({
                    error: null,
                    readyState: video.readyState,
                    duration: video.duration
                  });
                  setTimeout(check, 100);
                };
                check();
                setTimeout(() => finish({
                  error: 'timeout',
                  readyState: video && video.readyState
                }), 10000);
              });
              return {
                auth,
                live,
                liveCandidates,
                liveStatus: liveResponse.status,
                playlist,
                segmentUrl,
                player
              };
            }""", api_base)
            browser.close()
            assert errors == [], errors

        live_url = api_base + LIVE_PATH
        movie_url = api_base + MOVIE_PATH
        media_hls = media_base + "/hls/index.m3u8"
        media_mp4 = media_base + "/clip.mp4"
        statuses = {(entry["url"], entry["status"]) for entry in responses}
        assert (live_url, 302) in statuses, responses
        assert (media_hls, 200) in statuses, responses
        assert (movie_url, 302) in statuses, responses
        assert any(entry["url"] == media_mp4 and entry["status"] in (200, 206) for entry in responses), responses
        assert result["auth"]["user_info"]["username"] == "mock-user", result["auth"]
        assert result["live"][0]["name"] == "Fixture News One", result["live"]
        assert result["liveCandidates"][0]["url"] == live_url, result["liveCandidates"]
        assert media_base not in result["liveCandidates"][0]["url"], result["liveCandidates"]
        assert result["liveStatus"] == 200, result["liveStatus"]
        assert result["playlist"].startswith("#EXTM3U"), result["playlist"]
        assert result["segmentUrl"] == media_base + "/hls/segment-000.ts", result["segmentUrl"]
        assert result["player"]["error"] is None, result["player"]
        assert result["player"]["readyState"] >= 2, result["player"]
    finally:
        api_server.shutdown()
        media_server.shutdown()
        api_server.server_close()
        media_server.server_close()
    print("PASS: webOS follows Xtream live and VOD redirects to a separate media host.")


if __name__ == "__main__":
    main()
