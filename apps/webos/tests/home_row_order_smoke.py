"""Regression: Home D-pad rows follow the rendered (visual) order.

The Home screen now shows: Continue Watching, Favourites, Recent Live TV.
No Browse shortcuts. Row indices increase top-to-bottom in the DOM.
"""
from pathlib import Path
import os

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1] / "app"


def inline_app() -> str:
    css = "\n".join(
        (ROOT / "css" / name).read_text(encoding="utf-8")
        for name in ("tokens.css", "app.css", "v0.7-ui.css", "legacy-layout.css", "ovela.css")
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
        source = (ROOT / "js" / name).read_text(encoding="utf-8").replace("</script>", "<\\/script>")
        scripts.append("<script>" + source + "</script>")
    return (
        "<!doctype html><html><head><meta charset='utf-8'><style>"
        + css
        + "</style></head><body><div id='app'></div><div id='playerHost'></div>"
        + "<div id='toast'></div>"
        + "".join(scripts)
        + "</body></html>"
    )


def active_row(page) -> int:
    return page.evaluate("Number(document.activeElement.dataset.homeRow)")


def main() -> None:
    errors: list[str] = []
    with sync_playwright() as playwright:
        options: dict[str, object] = {"headless": True}
        if os.environ.get("CHROMIUM_PATH"):
            options["executable_path"] = os.environ["CHROMIUM_PATH"]
        browser = playwright.chromium.launch(**options)
        page = browser.new_page(viewport={"width": 1920, "height": 1080})
        page.on("pageerror", lambda error: errors.append(str(error)))
        page.set_content(inline_app(), wait_until="domcontentloaded")
        page.evaluate("OvelaApp.startDemo()")
        page.evaluate(
            """() => {
                const store = OvelaStore;
                OvelaMock.liveStreams.slice(0, 3).forEach(item => store.addRecent(item, 'live'));
                OvelaMock.vodStreams.slice(0, 3).forEach(item => store.addRecent(item, 'movie'));
                OvelaMock.series.slice(0, 3).forEach(item => store.addRecent(item, 'series'));
                store.toggleFavorite(OvelaMock.vodStreams[3], 'movie');
                OvelaApp.state = store.getState();
                OvelaApp.currentView = 'home';
                OvelaApp.renderShell('home');
            }"""
        )
        page.wait_for_selector(".ovela-home .v07-home-row-section")

        rows = page.evaluate(
            """() => Array.from(document.querySelectorAll('.ovela-home .v07-home-row-section')).map((section, index) => {
                const first = section.querySelector('[data-home-row]');
                return {
                    index,
                    row: first ? Number(first.dataset.homeRow) : null,
                    heading: (section.querySelector('h2,h3') || {}).textContent || '',
                };
            })"""
        )
        assert len(rows) >= 2, rows
        assert all(row["row"] == position for position, row in enumerate(rows)), rows

        # Down through every visual row must advance exactly one row.
        first_card = page.locator(".ovela-home .v07-home-row-section").first.locator(".focusable").first
        first_card.focus()
        assert active_row(page) == 0
        for expected in range(1, len(rows)):
            page.keyboard.press("ArrowDown")
            page.wait_for_timeout(40)
            assert active_row(page) == expected, (expected, rows)

        # Only the top-most row leaves Home for the top navigation.
        first_card.focus()
        page.keyboard.press("ArrowUp")
        page.wait_for_timeout(40)
        assert page.evaluate("document.activeElement.closest('.shell')") is not None

        assert not errors, errors
        browser.close()


if __name__ == "__main__":
    main()
