"""Focused smoke test for final webOS Home scrolling and focus polish."""
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
        page.evaluate("XtreamlyTVApp.startDemo()")
        page.evaluate("""() => {
            XtreamlyTVStore.clearHistory();
            XtreamlyTVMock.liveStreams.slice(0, 4).forEach(item => XtreamlyTVStore.addRecent(item, 'live'));
            XtreamlyTVMock.vodStreams.slice(0, 4).forEach(item => XtreamlyTVStore.addRecent(item, 'movie'));
            XtreamlyTVMock.series.slice(0, 4).forEach(item => XtreamlyTVStore.addRecent(item, 'series'));
            XtreamlyTVApp.state = XtreamlyTVStore.getState();
            XtreamlyTVApp.currentView = 'home';
            XtreamlyTVApp.renderView();
        }""")
        page.wait_for_selector(".hero")

        headings = page.locator(".scroll-view .section-head h2").all_inner_texts()
        for heading in (
            "Continue watching Live TV",
            "Continue watching Movies",
            "Continue watching Series",
        ):
            assert heading in headings, headings

        # D-pad movement must scroll the outer Home viewport, not just the nested row.
        page.locator(".hero .focusable").first.focus()
        initial_scroll = page.locator(".scroll-view").evaluate("el => el.scrollTop")
        for _ in range(7):
            page.keyboard.press("ArrowDown")
            page.wait_for_timeout(30)
        home_visibility = page.evaluate(
            """() => {
                const active = document.activeElement.getBoundingClientRect();
                const viewport = document.querySelector('.scroll-view').getBoundingClientRect();
                return {
                    scrollTop: document.querySelector('.scroll-view').scrollTop,
                    fullyVisible: active.top >= viewport.top + 16 && active.bottom <= viewport.bottom - 16,
                    heading: document.activeElement.closest('.section')?.querySelector('h2')?.textContent || ''
                };
            }"""
        )
        assert home_visibility["scrollTop"] > initial_scroll
        assert home_visibility["fullyVisible"] is True, home_visibility

        # Manager rows do not carry a second persistent selection highlight.
        page.click('[data-view="favorites"]')
        page.click("#editFavoriteGroups")
        assert page.locator(".favorite-manager-row.selected").count() == 0
        page.locator('[data-manager-visibility="live"]').focus()
        assert page.locator(".favorite-manager-row.selected").count() == 0

        # Poster focus is painted above the full card without changing geometry.
        page.click('[data-view="movies"]')
        page.wait_for_selector("#catalogGrid .poster-card")
        card = page.locator("#catalogGrid .poster-card").first
        before = card.evaluate("el => ({width:el.getBoundingClientRect().width,height:el.getBoundingClientRect().height})")
        card.focus()
        after = card.evaluate(
            """el => ({
                width:el.getBoundingClientRect().width,
                height:el.getBoundingClientRect().height,
                opacity:getComputedStyle(el.querySelector('.poster-focus-ring')).opacity,
                border:getComputedStyle(el.querySelector('.poster-focus-ring')).borderTopWidth
            })"""
        )
        assert abs(after["width"] - before["width"]) < 0.1
        assert abs(after["height"] - before["height"]) < 0.1
        assert after["opacity"] == "1"
        assert after["border"] == "4px"

        assert not errors, errors
        browser.close()

    print("PASS: Home section scrolling, manager focus, and poster outline polish.")


if __name__ == "__main__":
    main()
