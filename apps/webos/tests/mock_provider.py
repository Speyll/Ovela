#!/usr/bin/env python3
"""Loopback-only synthetic Xtream + M3U provider for the webOS smoke tests.

No real provider is contacted. Every credential, token, and URL is a fixture.
Run from the repository root:
  python3 apps/webos/tests/mock_provider.py --port 18080
"""
from __future__ import annotations

import argparse
import json
import mimetypes
import re
import sys
import time
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlparse

TEST_ROOT = Path(__file__).resolve().parent
FIXTURES = TEST_ROOT / "fixtures" / "xtream"
MEDIA = TEST_ROOT / "fixtures" / "media"
VALID_USER = "demo"
VALID_PASS = "demo-pass"
EXPIRED_USER = "expired-user"
EXPIRED_PASS = "expired-pass"
EMPTY_USER = "empty-user"
EMPTY_PASS = "empty-pass"
STREAM_PATH = re.compile(r"^/(live|movie|series)/([^/]+)/([^/]+)/([^/]+)$")


def fixture(name: str) -> bytes:
    return (FIXTURES / name).read_bytes()


def host_header(handler: BaseHTTPRequestHandler, port: int) -> str:
    value = handler.headers.get("Host", "")
    if re.fullmatch(r"[A-Za-z0-9.\-:\[\]]+", value):
        return value
    return f"127.0.0.1:{port}"


def playlist_document(host: str, large: bool = False) -> bytes:
    base = f"http://{host}"
    lines = [
        '#EXTM3U url-tvg="' + base + '/epg.xml"',
        '#EXTINF:-1 tvg-id="fixture-news" tvg-name="Fixture News One" '
        'tvg-logo="' + base + '/assets/news.png" group-title="News",Fixture News One',
        base + "/media/hls/index.m3u8?token=fixture-token",
        "#EXTINF:-1 group-title=\"Movies\",Fixture Movie",
        base + "/media/clip.mp4?token=fixture-token",
        '#EXTINF:-1 group-title="News, Weather",Fixture Title, With Comma',
        base + "/media/clip.webm?token=fixture-token",
    ]
    if large:
        for index in range(1, 20001):
            lines.extend([
                f'#EXTINF:-1 group-title="Generated {index % 20}",Generated Channel {index}',
                f"{base}/media/clip.mp4?token=fixture-token&channel={index}",
            ])
    return ("\n".join(lines) + "\n").encode("utf-8")


def desktop_playlist_document(host: str) -> bytes:
    """M3U entries whose URLs are directly playable in desktop Chromium."""
    base = f"http://{host}"
    lines = [
        '#EXTM3U url-tvg="' + base + '/epg.xml"',
        '#EXTINF:-1 tvg-id="fixture-news" tvg-logo="' + base + '/assets/poster.png" '
        'group-title="Desktop Live",Fixture Live Direct',
        base + "/media/live-direct?token=fixture-token",
        '#EXTINF:-1 tvg-logo="' + base + '/assets/poster.png" '
        'group-title="Desktop Live",Fixture WebM Direct',
        base + "/media/clip.webm",
        '#EXTINF:-1 tvg-logo="' + base + '/assets/poster.png" '
        'group-title="Desktop Movies",Fixture Movie Direct',
        base + "/media/clip.mp4?token=fixture-token",
    ]
    return ("\n".join(lines) + "\n").encode("utf-8")


class Handler(BaseHTTPRequestHandler):
    server_version = "OvelaFixture/1.0"
    protocol_version = "HTTP/1.1"
    large_playlist: bytes = b""

    def log_message(self, fmt: str, *args: object) -> None:
        print(f"mock-provider {self.command} {urlparse(self.path).path}", file=sys.stderr, flush=True)

    def cors(self) -> None:
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Authorization, Content-Type")
        self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")

    def send_bytes(self, status: int, body: bytes, content_type: str, extra: dict | None = None) -> None:
        self.send_response(status)
        self.cors()
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        for key, value in (extra or {}).items():
            self.send_header(key, value)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def send_json(self, status: int, value: object) -> None:
        self.send_bytes(status, json.dumps(value).encode("utf-8"), "application/json; charset=utf-8")

    def do_OPTIONS(self) -> None:  # noqa: N802
        self.send_response(HTTPStatus.NO_CONTENT)
        self.cors()
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_HEAD(self) -> None:  # noqa: N802
        self.do_GET()

    def do_GET(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        path = parsed.path
        query = parse_qs(parsed.query)
        port = self.server.server_address[1]

        if path == "/health":
            return self.send_json(200, {"ok": True, "service": "ovela-fixture-provider"})
        if path == "/playlist.m3u":
            return self.send_bytes(200, playlist_document(host_header(self, port)), "audio/x-mpegurl; charset=utf-8")
        if path == "/desktop/playlist.m3u":
            return self.send_bytes(200, desktop_playlist_document(host_header(self, port)), "audio/x-mpegurl; charset=utf-8")
        if path == "/playlist-large.m3u":
            return self.send_bytes(200, self.large_playlist, "audio/x-mpegurl; charset=utf-8")
        if path == "/playlist-malformed.m3u":
            return self.send_bytes(200, b'#EXTM3U\n#EXTINF:-1,Broken without URL\n#EXTINF:-1\n', "audio/x-mpegurl; charset=utf-8")
        if path == "/playlist-empty.m3u":
            return self.send_bytes(200, b"#EXTM3U\n", "audio/x-mpegurl; charset=utf-8")
        if path == "/playlist-redirect.m3u":
            self.send_response(HTTPStatus.FOUND)
            self.cors()
            self.send_header("Location", "/playlist.m3u")
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        if path == "/epg.xml":
            body = '<?xml version="1.0"?><tv><channel id="fixture-news"/></tv>'.encode()
            return self.send_bytes(200, body, "application/xml")
        if path.startswith("/assets/") and path.endswith(".png"):
            return self.send_bytes(200, (MEDIA / "poster.png").read_bytes(), "image/png")
        if path.startswith("/media/"):
            return self.serve_media(path.removeprefix("/media/"))
        stream = STREAM_PATH.fullmatch(path)
        if stream:
            return self.serve_stream(stream)
        if path == "/player_api.php":
            return self.serve_api(query)
        return self.send_json(404, {"error": "Not found in fixture provider"})

    def serve_api(self, query: dict[str, list[str]]) -> None:
        username = (query.get("username") or [""])[0]
        password = (query.get("password") or [""])[0]
        action = (query.get("action") or [""])[0]
        if username == "server-error":
            return self.send_json(503, {"error": "Synthetic upstream outage"})
        if username == "bad-json":
            return self.send_bytes(200, b'{"user_info": ', "application/json")
        if username == "slow-user":
            time.sleep(2.5)
        if username == EMPTY_USER and password == EMPTY_PASS:
            if action == "":
                return self.send_json(200, json.loads(fixture("user_info_valid.json")))
            if action in ("get_short_epg",):
                return self.send_json(200, {"epg_listings": []})
            if action in ("get_vod_info",):
                return self.send_json(200, {})
            if action in ("get_series_info",):
                return self.send_json(200, {"info": {}, "episodes": {}})
            if action in (
                "get_live_categories", "get_vod_categories", "get_series_categories",
                "get_live_streams", "get_vod_streams", "get_series", "get_series_streams",
            ):
                return self.send_json(200, [])
            return self.send_json(400, {"error": f"Unsupported fixture action: {action}"})
        if username == EXPIRED_USER and password == EXPIRED_PASS:
            return self.send_json(200, json.loads(fixture("user_info_expired.json")))
        if username != VALID_USER or password != VALID_PASS:
            return self.send_json(200, json.loads(fixture("user_info_invalid.json")))
        responses = {
            "": "user_info_valid.json",
            "get_live_categories": "categories_live.json",
            "get_vod_categories": "categories_vod.json",
            "get_series_categories": "categories_series.json",
            "get_live_streams": "streams_live.json",
            "get_vod_streams": "streams_vod.json",
            "get_series": "series.json",
            "get_series_streams": "series.json",
            "get_vod_info": "vod_info.json",
            "get_series_info": "series_info.json",
            "get_short_epg": "epg.json",
        }
        name = responses.get(action)
        if not name:
            return self.send_json(400, {"error": f"Unsupported fixture action: {action}"})
        host = host_header(self, self.server.server_address[1])
        body = fixture(name).replace(b"127.0.0.1:18080", host.encode("ascii"))
        return self.send_bytes(200, body, "application/json; charset=utf-8")

    def serve_file(self, target: Path) -> None:
        img = "image/png" if target.suffix == ".png" else ""
        body = target.read_bytes()
        content_type = img or mimetypes.guess_type(target.name)[0] or "application/octet-stream"
        if target.suffix == ".ts":
            content_type = "video/mp2t"
        range_header = self.headers.get("Range", "")
        match = re.fullmatch(r"bytes=(\d*)-(\d*)", range_header)
        if match:
            start = int(match.group(1) or 0)
            end = int(match.group(2) or len(body) - 1)
            end = min(end, len(body) - 1)
            if start > end:
                return self.send_bytes(416, b"", content_type, {"Content-Range": f"bytes */{len(body)}"})
            chunk = body[start:end + 1]
            return self.send_bytes(
                206,
                chunk,
                content_type,
                {"Content-Range": f"bytes {start}-{end}/{len(body)}", "Accept-Ranges": "bytes"},
            )
        return self.send_bytes(200, body, content_type, {"Accept-Ranges": "bytes"})

    def serve_media(self, relative: str) -> None:
        if relative == "live-direct":
            return self.serve_file(MEDIA / "clip.mp4")
        if relative in ("corrupt.mp4", "corrupt-a.mp4", "corrupt-b.mp4"):
            return self.send_bytes(200, b"fixture: not a media container\n", "video/mp4")
        if relative == "slow-start.mp4":
            time.sleep(25)
            try:
                return self.send_json(504, {"error": "Synthetic first-frame timeout"})
            except (BrokenPipeError, ConnectionResetError):
                return
        if relative == "stall.mp4":
            target = MEDIA / "clip-long.mp4"
            if not target.is_file():
                return self.send_json(404, {"error": "Generate media fixtures first"})
            body = target.read_bytes()
            start = body[:65536]
            self.send_response(200)
            self.cors()
            self.send_header("Content-Type", "video/mp4")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.close_connection = True
            try:
                self.wfile.write(start)
                self.wfile.flush()
                time.sleep(35)
            except (BrokenPipeError, ConnectionResetError):
                pass
            return
        target = (MEDIA / relative).resolve()
        if MEDIA.resolve() not in target.parents or not target.is_file():
            return self.send_bytes(404, b"", "video/mp4")
        return self.serve_file(target)

    def serve_stream(self, match: re.Match[str]) -> None:
        kind, username, password, filename = match.groups()
        username = unquote(username)
        password = unquote(password)
        if (username, password) not in ((VALID_USER, VALID_PASS), (EMPTY_USER, EMPTY_PASS)):
            return self.send_bytes(403, b"", "video/mp4")
        stem, dot, extension = filename.rpartition(".")
        if not dot:
            return self.send_bytes(404, b"", "video/mp4")
        stem = unquote(stem)
        extension = extension.lower()
        if kind == "live":
            if extension == "m3u8":
                return self.send_bytes(
                    200,
                    (MEDIA / "hls" / "index.m3u8").read_bytes(),
                    "application/vnd.apple.mpegurl",
                )
            if extension == "ts":
                return self.serve_file(MEDIA / "hls" / "segment-000.ts")
            return self.send_bytes(404, b"", "video/mp4")
        if kind == "movie" and stem == "2001" and extension == "mp4":
            return self.serve_file(MEDIA / "clip.mp4")
        if kind == "series" and stem in ("3001", "3001-1-1") and extension == "mp4":
            return self.serve_file(MEDIA / "clip.mp4")
        return self.send_bytes(404, b"", "video/mp4")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=18080)
    args = parser.parse_args()
    if not 1 <= args.port <= 65535:
        parser.error("port must be between 1 and 65535")
    Handler.large_playlist = playlist_document(f"127.0.0.1:{args.port}", large=True)
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    print(f"Fixture Xtream/M3U provider: http://127.0.0.1:{args.port}", flush=True)
    print("Credentials: demo / demo-pass (expired-user / expired-pass)", flush=True)
    print("Loopback only. Press Ctrl-C to stop.", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
