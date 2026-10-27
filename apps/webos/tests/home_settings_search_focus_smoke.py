"""Regression: Home rails, Settings entry and the modal search field.

Home rails are annotated with data-home-row / data-home-column by
annotateHomeNavigation(). Left/Right must walk that annotation instead of
looking for a wrapper class that no longer exists (the rail used to look up
`.v07-home-row`, find nothing, and either swallow Right or jump to the top
navigation on Left).

Settings must open at the top of the page, and its D-pad must prefer the card
next to the focused control over a same-column control far down the page.

The catalog search popover is modal: arrows must stay inside it instead of
focusing cards behind the backdrop.
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


def focused(page):
    return page.evaluate("""() => {
      const active = document.activeElement;
      return {
        className: active.className || '',
        id: active.id || '',
        row: active.dataset ? active.dataset.homeRow : undefined,
        column: active.dataset ? active.dataset.homeColumn : undefined,
        text: (active.innerText || '').replace(/\\n/g, ' | ').slice(0, 60)
      };
    }""")


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
        page.evaluate("OvelaApp.startDemo()")

        # ---------- Home rails: Left/Right walk the annotated row ----------
        page.evaluate("""() => {
          const store = OvelaStore;
          [['Metro News','2'],['Stadium One','3'],['Cinema One','6']].forEach(([name, category], index) => {
            store.addRecent({stream_id: 1100 + index, name: name, category_id: category, content_type: 'live'}, 'live');
          });
          for (let index = 0; index < 4; index += 1) {
            store.addRecent({stream_id: 2100 + index, name: 'Movie ' + index, category_id: '102', content_type: 'movie'}, 'movie');
          }
          OvelaApp.state = store.getState();
          OvelaApp.renderShell('home');
        }""")
        page.wait_for_timeout(400)

        live_cards = page.locator(".ovela-home-live-section .channel-tile")
        assert live_cards.count() >= 3, live_cards.count()
        live_cards.first.focus()
        page.wait_for_timeout(80)
        assert focused(page)["column"] == "0"

        page.keyboard.press("ArrowRight")
        page.wait_for_timeout(80)
        assert "channel-tile" in focused(page)["className"], focused(page)
        assert focused(page)["column"] == "1", focused(page)

        page.keyboard.press("ArrowRight")
        page.wait_for_timeout(80)
        assert focused(page)["column"] == "2", focused(page)

        # Right at the end of the rail stays put instead of leaving Home.
        page.keyboard.press("ArrowRight")
        page.wait_for_timeout(80)
        assert focused(page)["column"] == "2", focused(page)

        page.keyboard.press("ArrowLeft")
        page.wait_for_timeout(80)
        assert focused(page)["column"] == "1", focused(page)

        # Left at the first card must not leap up into the top navigation.
        page.keyboard.press("ArrowLeft")
        page.wait_for_timeout(80)
        assert focused(page)["column"] == "0", focused(page)
        assert "channel-tile" in focused(page)["className"], focused(page)

        # Up reaches the poster rail, Down comes back to the live rail.
        page.keyboard.press("ArrowUp")
        page.wait_for_timeout(120)
        assert focused(page)["row"] == "0", focused(page)
        assert "poster-card" in focused(page)["className"], focused(page)
        page.keyboard.press("ArrowDown")
        page.wait_for_timeout(120)
        assert focused(page)["row"] == "1", focused(page)
        assert "channel-tile" in focused(page)["className"], focused(page)

        # ---------- Settings opens at the top and moves card by card ----------
        page.evaluate("OvelaApp.renderShell('settings')")
        page.wait_for_timeout(500)
        assert focused(page)["id"] == "v07ManageFavorites", focused(page)

        page.keyboard.press("ArrowDown")
        page.wait_for_timeout(150)
        assert focused(page)["id"] == "editProvider", focused(page)

        page.keyboard.press("ArrowDown")
        page.wait_for_timeout(150)
        assert focused(page)["id"] == "formatSetting", focused(page)

        page.keyboard.press("ArrowDown")
        page.wait_for_timeout(150)
        assert focused(page)["id"] == "savePlayback", focused(page)

        # A <select> keeps Left/Right for changing its value; Down then Right
        # moves to the Save button of the neighbouring card.
        page.keyboard.press("ArrowRight")
        page.wait_for_timeout(150)
        assert focused(page)["id"] == "savePerformance", focused(page)

        page.keyboard.press("ArrowUp")
        page.wait_for_timeout(150)
        assert "nav-item" not in focused(page)["className"], focused(page)

        # ---------- Modal catalog search keeps focus inside ----------
        page.evaluate("OvelaApp.activeCategory.movies='102'; OvelaApp.searchText.movies=''; OvelaApp.renderShell('movies')")
        page.wait_for_selector("#catalogGrid .poster-card")
        page.evaluate("document.getElementById('v07SearchNav').click()")
        page.wait_for_function("Boolean(document.getElementById('v07SearchOverlay'))")
        page.wait_for_timeout(120)
        assert focused(page)["id"] == "v07SearchInput", focused(page)

        page.keyboard.press("ArrowUp")
        page.wait_for_timeout(100)
        assert focused(page)["id"] == "v07SearchClose", focused(page)

        page.keyboard.press("ArrowDown")
        page.wait_for_timeout(100)
        assert focused(page)["id"] == "v07SearchInput", focused(page)

        # Down must not focus a card behind the backdrop.
        page.keyboard.press("ArrowDown")
        page.wait_for_timeout(100)
        assert focused(page)["id"] == "v07SearchInput", focused(page)

        page.keyboard.press("Escape")
        page.wait_for_timeout(200)
        assert page.evaluate("Boolean(document.getElementById('v07SearchOverlay'))") is False
        assert focused(page)["id"] == "v07SearchNav", focused(page)

        assert errors == [], errors
        browser.close()
    print("PASS: Home rails, Settings entry and modal search focus.")


if __name__ == "__main__":
    main()
