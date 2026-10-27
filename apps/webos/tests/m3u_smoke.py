"""Deterministic smoke test for the M3U source path without network access."""
from pathlib import Path
import os
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1] / "app"

PLAYLIST = """#EXTM3U url-tvg="https://epg.example/guide.xml"
#EXTINF:-1 tvg-id="news" tvg-logo="https://img.example/news.png" group-title="News",News One
https://origin.example/live/news.m3u8?token=abc
#EXTINF:-1 group-title="Movies",Example Movie
https://origin.example/movies/example.mp4?token=abc
"""


def inline_app() -> str:
    css = "\n".join((ROOT / "css" / name).read_text(encoding="utf-8") for name in (
        "tokens.css", "app.css", "v0.7-ui.css", "legacy-layout.css",
    ))
    scripts = []
    for name in (
        "core.js", "store.js", "api.js", "mock-data.js", "navigation.js",
        "virtual-grid.js", "category-rail.js", "artwork.js", "app.js", "v0.7-ui.js",
    ):
        source = (ROOT / "js" / name).read_text(encoding="utf-8").replace("</script>", "<\\/script>")
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

        page.evaluate("OvelaApp.renderLogin()")
        assert page.locator("#loginSourceType").count() == 1
        assert page.locator("#m3uFields").is_hidden()
        page.select_option("#loginSourceType", "m3u")
        assert page.locator("#m3uFields").is_visible()
        assert page.locator("#xtreamFields").is_hidden()
        page.locator("#loginPlaylist").fill("https://provider.example/playlist.m3u")
        page.locator("#loginAlternates").fill("https://backup.example:8443")

        page.evaluate(
            """async (playlist) => {
                window.fetch = async () => new Response(playlist, {
                    status: 200,
                    headers: { 'Content-Type': 'audio/x-mpegurl' }
                });
                OvelaApp.connect({
                    sourceType: 'm3u',
                    playlistUrl: 'https://provider.example/playlist.m3u?token=secret',
                    alternateServers: ['https://backup.example:8443']
                }, { apiProxy: '', streamFormat: 'auto' });
                await new Promise(resolve => {
                    const timer = setInterval(() => {
                        if (OvelaApp.api && OvelaApp.liveCategories.length) {
                            clearInterval(timer);
                            resolve();
                        }
                    }, 10);
                });
            }""",
            PLAYLIST,
        )
        assert page.locator("#app .screen").count() >= 1

        state = page.evaluate("""() => {
            const live = OvelaApp.api.entries.find(entry => entry.content_type === 'live');
            const movie = OvelaApp.api.entries.find(entry => entry.content_type === 'movie');
            return {
                isM3u: OvelaApp.api instanceof OvelaM3uApi,
                playlist: OvelaApp.api.playlistUrl,
                liveCategories: OvelaApp.liveCategories.length,
                live: live.name,
                movie: movie.name,
                liveCandidates: OvelaApp.buildPlaybackCandidates(live, 'live').map(candidate => candidate.url),
                movieCandidates: OvelaApp.buildPlaybackCandidates(movie, 'movie').map(candidate => candidate.url),
                epg: OvelaApp.profile.epgUrls
            };
        }""")
        assert state["isM3u"] is True
        assert state["playlist"] == "https://provider.example/playlist.m3u?token=secret"
        assert state["liveCategories"] == 1
        assert state["live"] == "News One"
        assert state["movie"] == "Example Movie"
        assert state["liveCandidates"] == [
            "https://origin.example/live/news.m3u8?token=abc",
            "https://backup.example:8443/live/news.m3u8?token=abc",
        ]
        assert state["movieCandidates"] == [
            "https://origin.example/movies/example.mp4?token=abc",
            "https://backup.example:8443/movies/example.mp4?token=abc",
        ]
        assert state["epg"] == ["https://epg.example/guide.xml"]

        page.evaluate("OvelaApp.renderSettings()")
        summary = page.locator(".provider-summary").inner_text()
        assert "token=secret" not in summary
        assert "provider.example" in summary
        assert not errors, errors
        browser.close()


if __name__ == "__main__":
    main()
    print("PASS: M3U login, catalog, EPG metadata, and alternate-host candidates.")
