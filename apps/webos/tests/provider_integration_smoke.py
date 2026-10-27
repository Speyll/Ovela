"""Exercise the production webOS API code against the loopback fixture provider."""
from pathlib import Path
import json
import os
import socket
import subprocess
import sys
import time
from urllib.request import urlopen

from playwright.sync_api import sync_playwright

APP_ROOT = Path(__file__).resolve().parents[1] / "app"
REPO_ROOT = Path(__file__).resolve().parents[3]
PROVIDER = REPO_ROOT / ".dev" / "testing" / "scripts" / "mock_provider.py"


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


def free_port() -> int:
    probe = socket.socket()
    probe.bind(("127.0.0.1", 0))
    port = probe.getsockname()[1]
    probe.close()
    return port


def main() -> None:
    assert PROVIDER.is_file(), "mock_provider.py is missing"
    port = free_port()
    process = subprocess.Popen(
        [sys.executable, str(PROVIDER), "--port", str(port)],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    base = f"http://127.0.0.1:{port}"
    try:
        deadline = time.time() + 8
        while True:
            try:
                with urlopen(base + "/health", timeout=1) as response:
                    assert json.load(response)["ok"] is True
                break
            except Exception:
                if process.poll() is not None or time.time() > deadline:
                    stdout, stderr = process.communicate(timeout=2)
                    raise AssertionError(f"fixture provider failed to start\n{stdout}\n{stderr}")
                time.sleep(0.1)

        with sync_playwright() as playwright:
            options = {"headless": True}
            if os.environ.get("CHROMIUM_PATH"):
                options["executable_path"] = os.environ["CHROMIUM_PATH"]
            browser = playwright.chromium.launch(**options)
            page = browser.new_page(viewport={"width": 1920, "height": 1080})
            errors = []
            page.on("pageerror", lambda error: errors.append(str(error)))
            page.set_content(inline_app(), wait_until="domcontentloaded")
            result = page.evaluate("""async (base) => {
              const xtream = new OvelaApi({
                server: base,
                username: 'demo',
                password: 'demo-pass'
              }, {streamFormat: 'auto'});
              const auth = await xtream.authenticate();
              const liveCategories = await xtream.getLiveCategories();
              const live = await xtream.getLiveStreams('1');
              const movies = await xtream.getVodStreams('10');
              const series = await xtream.getSeries('20');
              const epg = await xtream.getShortEpg('1001');
              let rejected = '';
              try {
                await new OvelaApi({
                  server: base, username: 'demo', password: 'wrong'
                }, {}).authenticate();
              } catch (error) { rejected = error.message; }

              const m3u = new OvelaM3uApi({
                playlistUrl: base + '/playlist.m3u',
                alternateServers: ['https://backup.example:8443']
              }, {});
              const playlist = await m3u.authenticate();
              const m3uLive = await m3u.getLiveStreams('all');
              const m3uMovies = await m3u.getVodStreams('all');
              const candidates = m3u.getLiveCandidates(m3uLive[0]);
              return {
                auth,
                liveCategories,
                live,
                movies,
                series,
                epg,
                rejected,
                playlist,
                m3uLive,
                m3uMovies,
                candidates
              };
            }""", base)
            browser.close()
            assert errors == [], errors

        assert result["auth"]["user_info"]["username"] == "mock-user", result["auth"]
        assert len(result["liveCategories"]) == 2, result["liveCategories"]
        assert result["live"][0]["name"] == "Fixture News One", result["live"]
        assert result["live"][0]["stream_icon"].startswith(base), result["live"]
        assert result["movies"][0]["name"] == "Fixture Movie", result["movies"]
        assert result["series"][0]["name"] == "Fixture Series", result["series"]
        assert result["epg"][0]["title"] == "Fixture News Hour", result["epg"]
        assert "Provider rejected" in result["rejected"], result["rejected"]
        assert result["playlist"]["status"] == "3 entries", result["playlist"]
        assert len(result["m3uLive"]) == 1, result["m3uLive"]
        assert len(result["m3uMovies"]) == 2, result["m3uMovies"]
        candidate_urls = [entry["url"] for entry in result["candidates"]]
        assert any(url.startswith("https://backup.example:8443/") for url in candidate_urls), candidate_urls
        assert any("token=fixture-token" in url for url in candidate_urls), candidate_urls
    finally:
        process.terminate()
        try:
            process.wait(timeout=3)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=3)
    print("PASS: webOS Xtream/M3U API calls against the loopback fixture provider.")


if __name__ == "__main__":
    main()
