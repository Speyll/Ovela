"""Focused smoke tests for webOS navigation restoration and favorite-group visibility.

Run from the repository root:
  python -m pip install -r apps/webos/tests/requirements.txt
  python -m playwright install chromium
  python apps/webos/tests/navigation_state_smoke.py
"""
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
    scripts = []
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
        escaped_content = content.replace("</script>", "<\\/script>")
        scripts.append("<script>" + escaped_content + "</script>")
    return (
        "<!doctype html><html><head><meta charset='utf-8'><style>"
        + css
        + "</style></head><body>"
        + "<div id='app'></div><div id='playerHost' aria-hidden='true'></div>"
        + "<div id='toast' class='toast'></div>"
        + "".join(scripts)
        + "</body></html>"
    )


def main() -> None:
    errors: list[str] = []
    with sync_playwright() as playwright:
        launch_options: dict[str, object] = {"headless": True}
        if os.environ.get("CHROMIUM_PATH"):
            launch_options["executable_path"] = os.environ["CHROMIUM_PATH"]
        browser = playwright.chromium.launch(**launch_options)
        page = browser.new_page(viewport={"width": 1920, "height": 1080})
        page.on("pageerror", lambda error: errors.append(str(error)))
        page.set_content(inline_app(), wait_until="domcontentloaded")
        page.evaluate("OvelaApp.startDemo()")
        page.wait_for_selector(".hero")

        # Returning from direct playback restores the exact catalog card.
        page.click('[data-view="live"]')
        page.click('[data-catalog-category="2"]')
        page.wait_for_selector("#catalogGrid .channel-tile")
        page.evaluate("OvelaApp.virtualGrid.focusIndex(1)")
        before_player = page.evaluate(
            """() => ({
                id: document.activeElement.dataset.contentId,
                index: document.activeElement.dataset.virtualIndex,
                scroll: document.getElementById('catalogGrid').scrollTop
            })"""
        )
        page.keyboard.press("Enter")
        page.wait_for_selector("#player")
        page.evaluate("OvelaApp.closePlayer()")
        page.wait_for_timeout(100)
        after_player = page.evaluate(
            """() => ({
                id: document.activeElement.dataset.contentId,
                index: document.activeElement.dataset.virtualIndex,
                scroll: document.getElementById('catalogGrid').scrollTop
            })"""
        )
        assert after_player["id"] == before_player["id"]
        assert after_player["index"] == before_player["index"]
        assert abs(after_player["scroll"] - before_player["scroll"]) <= 1

        # Detail Back restores the originating poster, category, and grid position.
        page.click('[data-view="movies"]')
        page.click('[data-catalog-category="102"]')
        page.wait_for_selector("#catalogGrid .poster-card")
        page.evaluate("OvelaApp.virtualGrid.focusIndex(1)")
        movie_id = page.evaluate("document.activeElement.dataset.contentId")
        page.keyboard.press("Enter")
        page.wait_for_selector("#closeDetail")
        page.click("#closeDetail")
        page.wait_for_timeout(150)
        assert page.evaluate("document.activeElement.dataset.contentId") == movie_id
        assert page.evaluate("OvelaApp.activeCategory.movies") == "102"

        # Sidebar round trips restore the last card in each library.
        page.click('[data-view="settings"]')
        page.click('[data-view="movies"]')
        page.wait_for_timeout(150)
        assert page.evaluate("document.activeElement.dataset.contentId") == movie_id

        # Built-in groups can be hidden and restored through the manager.
        page.click('[data-view="favorites"]')
        page.click("#editFavoriteGroups")
        assert page.locator('[data-manager-visibility="live"]').inner_text() == "Hide"
        page.click('[data-manager-visibility="live"]')
        assert page.locator('[data-manager-visibility="live"]').inner_text() == "Show"
        page.click("#favoriteManagerBack")
        assert page.locator('[data-favorite-group="live"]').count() == 0
        page.click("#editFavoriteGroups")
        page.click('[data-manager-visibility="live"]')
        assert page.locator('[data-manager-visibility="live"]').inner_text() == "Hide"
        page.click("#favoriteManagerBack")

        # Larger living-room typography must preserve the five-card shelf.
        geometry = page.evaluate(
            """() => {
                const row = document.getElementById('favoriteGroupsRow');
                const cards = Array.from(row.querySelectorAll('[data-favorite-group]'));
                const rowRect = row.getBoundingClientRect();
                return {
                    navFont: parseFloat(getComputedStyle(document.querySelector('.nav-item')).fontSize),
                    titleFont: parseFloat(getComputedStyle(document.querySelector('.topbar h1')).fontSize),
                    buttonFont: parseFloat(getComputedStyle(document.querySelector('#editFavoriteGroups')).fontSize),
                    firstFiveFit: cards.slice(0, 5).every(card => card.getBoundingClientRect().right <= rowRect.right + 1)
                };
            }"""
        )
        assert geometry["navFont"] >= 23
        assert geometry["titleFont"] >= 42
        assert geometry["buttonFont"] >= 18
        assert geometry["firstFiveFit"] is True
        assert not errors, errors
        browser.close()

    print("PASS: navigation restoration, group visibility, and living-room scale.")


if __name__ == "__main__":
    main()
