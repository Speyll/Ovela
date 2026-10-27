"""Regression: Details opened from Home return to Home, with focus restored.

Movie and Series Details used to hard-code their return view to the matching
catalog. Back then dropped the viewer into Movies/Series after opening a card
from Home. The origin view is now preserved, and the focused card is restored.
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


def render_home_with_recents(page) -> None:
    page.evaluate(
        """() => {
            const store = OvelaStore;
            OvelaMock.vodStreams.slice(0, 2).forEach(item => store.addRecent(item, 'movie'));
            OvelaMock.series.slice(0, 2).forEach(item => store.addRecent(item, 'series'));
            OvelaMock.liveStreams.slice(0, 2).forEach(item => store.addRecent(item, 'live'));
            OvelaApp.state = store.getState();
            OvelaApp.currentView = 'home';
            OvelaApp.renderShell('home');
        }"""
    )
    page.wait_for_selector(".ovela-home")


def assert_back_to_home(page, detail_button: str, card_selector: str) -> None:
    card = page.locator(card_selector).first
    content_id = card.get_attribute("data-content-id")
    card.focus()
    card.click()
    page.wait_for_selector(detail_button)
    assert page.evaluate("OvelaApp.detail.returnView") == "home"
    page.locator(detail_button).click()
    page.wait_for_timeout(700)
    assert page.evaluate("OvelaApp.currentView") == "home"
    assert page.locator(".ovela-home").count() == 1
    assert page.evaluate("OvelaApp.detail") is None
    restored = page.evaluate("document.activeElement && document.activeElement.dataset.contentId")
    assert restored == content_id, (restored, content_id)


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
        render_home_with_recents(page)

        assert_back_to_home(page, "#closeDetail", '.ovela-home [data-content-type="movie"]')
        render_home_with_recents(page)
        assert_back_to_home(page, "#closeSeries", '.ovela-home [data-content-type="series"]')

        assert errors == [], errors
        browser.close()


if __name__ == "__main__":
    main()
