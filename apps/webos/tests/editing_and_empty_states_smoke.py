"""Regression: text editing, empty Home, series entry focus, provider Back.

Four small but user-visible defects are covered here:

1. Backspace was treated as the remote Back key everywhere. In a text field it
   threw focus out of the input (catalog search) or closed the dialog (modal
   search) instead of deleting a character.
2. Home with no history, favourites or recents rendered only a paragraph, so
   the view had no focusable content and the D-pad started on <body>.
3. Opening a series focused the Back button and kept restoring it on every
   re-render, so the episode list was never the entry point.
4. Back inside the provider editor left Settings for Home instead of
   cancelling the edit.
"""
from pathlib import Path
import os

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1] / "app"

PRESS_KEY_CODE = """
(code) => {
  const event = new KeyboardEvent('keydown', {bubbles: true, cancelable: true});
  Object.defineProperty(event, 'keyCode', {get: () => code});
  Object.defineProperty(event, 'which', {get: () => code});
  (document.activeElement || document.body).dispatchEvent(event);
}
"""


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


def focused(page) -> dict:
    return page.evaluate(
        """() => {
          const active = document.activeElement;
          return {
            id: active.id || '',
            className: (active.className || '').toString(),
            tag: active.tagName,
            value: active.value !== undefined ? active.value : null,
            visible: active.getBoundingClientRect().width > 0
          };
        }"""
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
        page.evaluate("OvelaApp.startDemo()")

        # ---------- 1a. Backspace edits the catalog search field ----------
        page.evaluate(
            "OvelaApp.activeCategory.movies='102'; OvelaApp.renderShell('movies')"
        )
        page.wait_for_selector("#catalogGrid .poster-card")
        page.wait_for_timeout(300)
        page.evaluate("document.getElementById('catalogSearch').focus()")
        page.keyboard.type("ab")
        page.wait_for_timeout(300)
        page.keyboard.press("Backspace")
        page.wait_for_timeout(300)
        state = focused(page)
        assert state["id"] == "catalogSearch", state
        assert state["value"] == "a", state
        assert page.evaluate("OvelaApp.searchText.movies") == "a"

        # ---------- 1b. Backspace edits the modal search field ----------
        page.evaluate(
            "OvelaApp.searchText.movies=''; OvelaApp.renderShell('movies')"
        )
        page.wait_for_selector("#catalogGrid .poster-card")
        page.wait_for_timeout(300)
        page.evaluate("document.getElementById('v07SearchNav').click()")
        page.wait_for_function("Boolean(document.getElementById('v07SearchOverlay'))")
        page.wait_for_timeout(200)
        page.keyboard.type("ab")
        page.wait_for_timeout(200)
        page.keyboard.press("Backspace")
        page.wait_for_timeout(300)
        assert page.evaluate("Boolean(document.getElementById('v07SearchOverlay'))") is True
        assert page.evaluate("document.getElementById('v07SearchInput').value") == "a"

        # ---------- 1c. Backspace still acts as Back outside text fields ----
        page.keyboard.press("Escape")
        page.wait_for_timeout(300)
        page.evaluate("document.querySelector('#catalogGrid .poster-card').focus()")
        page.wait_for_timeout(100)
        page.keyboard.press("Backspace")
        page.wait_for_timeout(300)
        assert "category-button" in focused(page)["className"], focused(page)

        # ---------- 2. Empty Home still gives the D-pad a starting point ----
        page.evaluate(
            """() => {
              OvelaApp.state.recent = [];
              OvelaApp.state.favorites = [];
              OvelaApp.renderShell('home');
            }"""
        )
        page.wait_for_timeout(500)
        # Home stays content-first: no promotional shortcut row.
        assert page.evaluate("document.querySelectorAll('.ovela-home .shortcut-card').length") == 0
        state = focused(page)
        assert "nav-item" in state["className"], state
        assert state["visible"] is True

        page.keyboard.press("ArrowRight")
        page.wait_for_timeout(120)
        assert "nav-item" in focused(page)["className"], focused(page)

        # ---------- 3. Series detail opens on the episode content ----------
        page.evaluate(
            "OvelaApp.activeCategory.series='201'; OvelaApp.searchText.series='';"
            "OvelaApp.renderShell('series')"
        )
        page.wait_for_selector("#catalogGrid .poster-card")
        page.wait_for_timeout(300)
        page.evaluate("document.querySelector('#catalogGrid .poster-card').click()")
        page.wait_for_timeout(1500)
        state = focused(page)
        assert "v07-series-season-button" in state["className"], state
        assert page.evaluate("OvelaApp.detail.type") == "series"

        # ---------- 4. Back cancels the provider editor ----------
        page.evaluate(PRESS_KEY_CODE, 461)
        page.wait_for_timeout(600)
        page.evaluate("OvelaApp.renderShell('settings')")
        page.wait_for_timeout(500)
        page.evaluate("document.getElementById('editProvider').click()")
        page.wait_for_timeout(400)
        assert page.evaluate("document.getElementById('providerEditor').hidden") is False
        page.evaluate(PRESS_KEY_CODE, 461)
        page.wait_for_timeout(500)
        assert page.evaluate("OvelaApp.currentView") == "settings"
        assert page.evaluate("document.getElementById('providerEditor').hidden") is True
        assert focused(page)["id"] == "editProvider", focused(page)

        # A second Back from the closed editor still leaves Settings.
        page.evaluate(PRESS_KEY_CODE, 461)
        page.wait_for_timeout(600)
        assert page.evaluate("OvelaApp.currentView") == "home"

        assert errors == [], errors
        browser.close()
    print("PASS: Backspace editing, empty Home, series entry focus, provider Back.")


if __name__ == "__main__":
    main()
