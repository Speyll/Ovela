"""Generate the documentation screenshots from the production webOS UI.

The capture uses XtreamlyTV's fictional, artwork-free demo catalog. This keeps
the published images reproducible and free of provider logos or media artwork.
"""

from pathlib import Path
import base64
import os

from playwright.sync_api import sync_playwright


ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "apps" / "webos" / "app"
OUTPUT = ROOT / "docs" / "screenshots"


def inline_app() -> str:
    wordmark = base64.b64encode((APP / "assets" / "xtreamlytv-wordmark.svg").read_bytes()).decode("ascii")
    wordmark_uri = "data:image/svg+xml;base64," + wordmark
    css = "\n".join(
        (APP / "css" / name).read_text(encoding="utf-8")
        for name in ("tokens.css", "app.css", "v0.7-ui.css")
    )
    scripts = []
    for name in (
        "core.js",
        "store.js",
        "api.js",
        "mock-data.js",
        "navigation.js",
        "virtual-grid.js",
        "category-rail.js",
        "artwork.js",
        "app.js",
        "v0.7-ui.js",
    ):
        source = (APP / "js" / name).read_text(encoding="utf-8")
        source = source.replace("assets/xtreamlytv-wordmark.svg", wordmark_uri)
        scripts.append("<script>" + source.replace("</script>", "<\\/script>") + "</script>")
    return (
        "<!doctype html><html><head><meta charset='utf-8'><base href='"
        + APP.as_uri()
        + "/'><style>"
        + css
        + "</style></head><body><div id='app'></div><div id='playerHost'></div>"
        + "<div id='toast'></div>"
        + "".join(scripts)
        + "</body></html>"
    )


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    with sync_playwright() as playwright:
        options = {"headless": True}
        if os.environ.get("CHROMIUM_PATH"):
            options["executable_path"] = os.environ["CHROMIUM_PATH"]
        browser = playwright.chromium.launch(**options)
        page = browser.new_page(viewport={"width": 1920, "height": 1080}, device_scale_factor=1)
        # Keep documentation captures free of photographic media. Brand SVGs
        # remain available through the local file base URL.
        page.route("**/home-live-backdrop.jpg", lambda route: route.abort())
        page.route("http://**", lambda route: route.abort())
        page.route("https://**", lambda route: route.abort())
        page.set_content(inline_app(), wait_until="domcontentloaded")
        page.evaluate("XtreamlyTVApp.startDemo()")
        page.evaluate(
            """() => {
                const app = XtreamlyTVApp;
                const store = XtreamlyTVStore;
                const mock = XtreamlyTVMock;
                store.addRecent(mock.liveStreams[0], 'live');
                store.addRecent(mock.vodStreams[0], 'movie');
                store.addRecent(mock.series[0], 'series');
                [mock.liveStreams[0], mock.liveStreams[2]].forEach(item => {
                    if (!store.isFavorite('live', item.stream_id)) store.toggleFavorite(item, 'live');
                });
                [mock.vodStreams[0], mock.vodStreams[5]].forEach(item => {
                    if (!store.isFavorite('movie', item.stream_id)) store.toggleFavorite(item, 'movie');
                });
                [mock.series[0], mock.series[4]].forEach(item => {
                    if (!store.isFavorite('series', item.series_id)) store.toggleFavorite(item, 'series');
                });
                const allKeys = [
                    store.favoriteKey('live', mock.liveStreams[0].stream_id),
                    store.favoriteKey('movie', mock.vodStreams[0].stream_id),
                    store.favoriteKey('series', mock.series[0].series_id),
                    store.favoriteKey('movie', mock.vodStreams[5].stream_id)
                ];
                store.saveFavoriteGroup({
                    name: 'Weekend Picks', icon: 'star', color: 'rose', itemKeys: allKeys.slice(0, 4)
                });
                app.state = store.getState();
                app.renderShell('home');
            }"""
        )

        captures = (
            ("xtreamlytv-home.png", "XtreamlyTVApp.renderShell('home')", ".v07-home"),
            (
                "xtreamlytv-live-virtualized.png",
                "XtreamlyTVApp.activeCategory.live='2'; XtreamlyTVApp.renderShell('live')",
                ".v07-guide",
            ),
            (
                "xtreamlytv-movies.png",
                "XtreamlyTVApp.activeCategory.movies='102'; XtreamlyTVApp.renderShell('movies')",
                ".v07-media-stage",
            ),
            (
                "xtreamlytv-favorites.png",
                "XtreamlyTVApp.favoriteMode='home'; XtreamlyTVApp.favoriteGroupId='all'; "
                "XtreamlyTVApp.renderShell('favorites')",
                ".favorites-hub",
            ),
            (
                "xtreamlytv-favorite-group.png",
                """(() => {
                    const group = XtreamlyTVStore.getState().favoriteGroups.find(item => item.name === 'Weekend Picks');
                    XtreamlyTVApp.favoriteMode='group'; XtreamlyTVApp.favoriteGroupId=group.id;
                    XtreamlyTVApp.renderShell('favorites');
                })()""",
                ".view",
            ),
            ("xtreamlytv-skins.png", "XtreamlyTVApp.renderShell('settings')", ".view"),
        )

        for filename, action, selector in captures:
            page.evaluate(action)
            page.wait_for_selector(selector)
            page.wait_for_timeout(250)
            page.screenshot(path=str(OUTPUT / filename), animations="disabled")

        browser.close()


if __name__ == "__main__":
    main()
