"""Focused regression test for webOS Home/category navigation cleanup."""
from pathlib import Path
import os

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1] / "app"


def inline_app() -> str:
    css = (
        (ROOT / "css" / "tokens.css").read_text(encoding="utf-8")
        + "\n"
        + (ROOT / "css" / "app.css").read_text(encoding="utf-8")
    )
    scripts: list[str] = []
    for filename in (
        "core.js",
        "store.js",
        "api.js",
        "mock-data.js",
        "navigation.js",
        "virtual-grid.js",
        "category-rail.js",
        "app.js",
    ):
        content = (ROOT / "js" / filename).read_text(encoding="utf-8")
        escaped = content.replace("</script>", "<\\/script>")
        scripts.append("<script>" + escaped + "</script>")
    return (
        "<!doctype html><html><head><meta charset='utf-8'><style>"
        + css
        + "</style></head><body><div id='app'></div>"
        + "<div id='playerHost' aria-hidden='true'></div>"
        + "<div id='toast' class='toast'></div>"
        + "".join(scripts)
        + "</body></html>"
    )


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

        # Populate each history type and Favorites.
        page.evaluate("""() => {
            OvelaStore.clearHistory();
            OvelaMock.liveStreams.slice(0, 5).forEach(item => OvelaStore.addRecent(item, 'live'));
            OvelaMock.vodStreams.slice(0, 5).forEach(item => OvelaStore.addRecent(item, 'movie'));
            OvelaMock.series.slice(0, 5).forEach(item => OvelaStore.addRecent(item, 'series'));
            OvelaMock.liveStreams.slice(0, 3).forEach(item => {
                if (!OvelaStore.isFavorite('live', item.stream_id)) OvelaStore.toggleFavorite(item, 'live');
            });
            OvelaMock.vodStreams.slice(0, 2).forEach(item => {
                if (!OvelaStore.isFavorite('movie', item.stream_id)) OvelaStore.toggleFavorite(item, 'movie');
            });
            OvelaApp.state = OvelaStore.getState();
            OvelaApp.currentView = 'home';
            OvelaApp.renderView();
        }""")
        page.wait_for_selector(".hero")

        headings = page.locator(".scroll-view .section-head h2").all_inner_texts()
        assert "Continue watching" not in headings, headings
        assert "Continue watching Live TV" in headings, headings
        assert "Continue watching Movies" in headings, headings
        assert "Continue watching Series" in headings, headings
        assert page.locator(".continue-watching-row").count() == 0

        # Moving back to Home row 0 must restore the outer page to scrollTop 0.
        page.locator(".hero .focusable").first.focus()
        for _ in range(5):
            page.keyboard.press("ArrowDown")
            page.wait_for_timeout(25)
        assert page.locator(".scroll-view").evaluate("el => el.scrollTop") > 0
        for _ in range(5):
            page.keyboard.press("ArrowUp")
            page.wait_for_timeout(25)
        assert page.evaluate("document.activeElement.closest('.hero') !== null") is True
        assert page.locator(".scroll-view").evaluate("el => el.scrollTop") == 0

        # The custom poster overlay focus implementation must be absent.
        page.click('[data-view="movies"]')
        page.wait_for_selector("#catalogGrid .poster-card")
        first = page.locator("#catalogGrid .poster-card").first
        first.focus()
        overlay = first.evaluate(
            """el => ({
                content:getComputedStyle(el, '::after').content,
                opacity:getComputedStyle(el, '::after').opacity,
                isolation:getComputedStyle(el).isolation
            })"""
        )
        assert overlay["content"] in ("none", "normal", '""'), overlay
        assert overlay["isolation"] != "isolate", overlay

        # Selecting a catalog category focuses the first content card, never search.
        for view in ("live", "movies", "series"):
            page.click(f'[data-view="{view}"]')
            page.wait_for_selector(".category-button")
            category_buttons = page.locator(".category-button")
            index = 1 if category_buttons.count() > 1 else 0
            category_buttons.nth(index).click()
            page.wait_for_function(
                """() => document.activeElement &&
                    document.activeElement.hasAttribute('data-virtual-index') &&
                    document.activeElement.dataset.virtualIndex === '0'"""
            )
            assert page.evaluate("document.activeElement.id") != "catalogSearch"

        # Selecting a Favorites group also focuses its first card.
        page.click('[data-view="favorites"]')
        page.wait_for_selector('[data-favorite-group="live"]')
        page.click('[data-favorite-group="live"]')
        page.wait_for_function(
            """() => document.activeElement &&
                document.activeElement.hasAttribute('data-virtual-index') &&
                document.activeElement.dataset.virtualIndex === '0'"""
        )
        assert page.evaluate("document.activeElement.closest('#favoriteGrid') !== null") is True

        assert not errors, errors
        browser.close()

    print("PASS: Home sections, top-scroll return, poster rollback, and first-card focus.")


if __name__ == "__main__":
    main()
