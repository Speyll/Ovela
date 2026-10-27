"""Regression: readable category rail and deterministic catalog D-pad edges.

The category rail must stay legible at TV distance, and every catalog surface
must have one obvious exit in each direction:

    category rail  --Right/OK-->  first content item
    first content row  --Up-->  search
    first poster column  --Left-->  category rail

Right on the last live channel or bottom/right edges of a grid must stay put
instead of letting the generic spatial fallback jump somewhere unexpected.
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
        category: active.dataset ? active.dataset.categoryIndex : undefined,
        virtual: active.dataset ? active.dataset.virtualIndex : undefined,
        text: (active.innerText || '').replace(/\\n/g, ' | ').slice(0, 80)
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

        page.evaluate("OvelaApp.activeCategory.live='2'; OvelaApp.searchText.live=''; OvelaApp.renderShell('live')")
        page.wait_for_selector("#catalogGrid.channel-list .channel-tile")
        page.evaluate("OvelaApp.categoryRail.focusActive()")
        page.wait_for_timeout(150)

        metrics = page.evaluate("""() => {
          const rail = document.querySelector('#categoryList');
          const button = rail.querySelector('.category-button');
          return {
            font: parseFloat(getComputedStyle(button).fontSize),
            railWidth: rail.getBoundingClientRect().width,
            overflow: getComputedStyle(rail).overflowY,
            current: (rail.querySelector('.category-button.active') || {}).getAttribute
              ? rail.querySelector('.category-button.active').getAttribute('aria-current') : null
          };
        }""")
        assert metrics["font"] >= 26, metrics
        assert metrics["railWidth"] >= 310, metrics
        assert metrics["overflow"] == "auto", metrics
        assert metrics["current"] == "true", metrics

        # Selecting a different category with Right also lands in content
        # (the old render pulled focus back to the category rail here).
        page.evaluate("OvelaApp.categoryRail.focusIndex(2)")
        page.keyboard.press("ArrowRight")
        page.wait_for_function("document.activeElement.classList.contains('channel-tile')")
        assert page.evaluate("OvelaApp.activeCategory.live") != "2", focused(page)
        page.evaluate("OvelaApp.categoryRail.focusActive()")
        page.wait_for_timeout(120)

        # Right on the already-selected category enters the content pane too.
        category = page.evaluate("OvelaApp.activeCategory.live")
        page.keyboard.press("ArrowRight")
        page.wait_for_function("document.activeElement.classList.contains('channel-tile')")
        state = focused(page)
        assert "channel-tile" in state["className"], state
        assert page.evaluate("OvelaApp.activeCategory.live") == category

        # Left returns to the rail; Right restores the same content position.
        page.keyboard.press("ArrowDown")
        page.wait_for_timeout(120)
        assert focused(page)["virtual"] == "1", focused(page)
        page.keyboard.press("ArrowLeft")
        page.wait_for_timeout(120)
        assert "category-button" in focused(page)["className"]
        page.keyboard.press("ArrowRight")
        page.wait_for_function("document.activeElement.classList.contains('channel-tile')")
        assert focused(page)["virtual"] == "1", focused(page)

        # Up from the first row reaches search; Down returns to content.
        page.keyboard.press("ArrowUp")
        page.wait_for_timeout(120)
        assert focused(page)["virtual"] == "0", focused(page)
        page.keyboard.press("ArrowUp")
        page.wait_for_timeout(120)
        assert focused(page)["id"] == "catalogSearch", focused(page)
        # Left/Right stay in the query field so the caret can be edited.
        page.keyboard.press("ArrowLeft")
        page.wait_for_timeout(120)
        assert focused(page)["id"] == "catalogSearch", focused(page)
        page.keyboard.press("ArrowDown")
        page.wait_for_timeout(120)
        first_index = focused(page)["virtual"]
        assert first_index == "0", focused(page)

        # The single-column live list does not use Right to jump elsewhere.
        page.keyboard.press("ArrowRight")
        page.wait_for_timeout(120)
        assert focused(page)["virtual"] == first_index, focused(page)

        # Down/Up inside the list remains stable.
        page.keyboard.press("ArrowDown")
        page.wait_for_timeout(120)
        assert focused(page)["virtual"] == "1", focused(page)
        page.keyboard.press("ArrowUp")
        page.wait_for_timeout(120)
        assert focused(page)["virtual"] == first_index, focused(page)

        # Movies use the same rail/content contract, plus the poster grid's
        # first-column and first-row exits.
        page.evaluate("OvelaApp.activeCategory.movies='102'; OvelaApp.searchText.movies=''; OvelaApp.renderShell('movies')")
        page.wait_for_selector("#catalogGrid .poster-card")
        page.evaluate("OvelaApp.categoryRail.focusActive()")
        page.wait_for_timeout(120)
        page.keyboard.press("ArrowRight")
        page.wait_for_function("document.activeElement.classList.contains('poster-card')")
        page.keyboard.press("ArrowLeft")
        page.wait_for_timeout(120)
        assert "category-button" in focused(page)["className"], focused(page)
        page.keyboard.press("ArrowRight")
        page.wait_for_function("document.activeElement.classList.contains('poster-card')")
        page.keyboard.press("ArrowUp")
        page.wait_for_timeout(120)
        assert focused(page)["id"] == "catalogSearch", focused(page)
        page.keyboard.press("ArrowDown")
        page.wait_for_timeout(120)
        assert "poster-card" in focused(page)["className"], focused(page)

        assert errors == [], errors
        browser.close()
    print("PASS: readable category rail and deterministic catalog D-pad edges.")


if __name__ == "__main__":
    main()
