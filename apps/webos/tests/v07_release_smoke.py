"""Production v0.7 shell regressions using deterministic demo data."""
from pathlib import Path
import os
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1] / "app"


def inline_app() -> str:
    css = "\n".join((ROOT / "css" / name).read_text(encoding="utf-8") for name in (
        "tokens.css", "app.css", "v0.7-ui.css",
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
        page.evaluate("XtreamlyTVApp.startDemo()")
        assert page.evaluate("XtreamlyTVApp.__v07UiRefreshInstalled") is True

        page.evaluate("XtreamlyTVApp.activeCategory.live='2'; XtreamlyTVApp.renderShell('live')")
        page.wait_for_selector("[data-v07-live-index]")
        assert page.locator("[data-v07-live-index]").count() <= 5

        # Channel names keep a two-line viewport and marquee only when focused.
        marquee = page.evaluate("""async () => {
          const button = document.querySelector('[data-v07-live-index]');
          const label = button.querySelector('.v07-station-copy strong');
          label.querySelector('span').textContent =
            'Metro News International Regional Network Extended Channel Name ' +
            'Metro News International Regional Network Extended Channel Name';
          button.blur();
          button.focus();
          await new Promise(resolve => setTimeout(resolve, 100));
          const style = getComputedStyle(label);
          return {
            height: label.clientHeight,
            lineHeight: parseFloat(style.lineHeight),
            overflowing: label.classList.contains('is-overflowing'),
            animation: getComputedStyle(label.querySelector('span')).animationName
          };
        }""")
        assert abs(marquee["height"] - marquee["lineHeight"] * 2) <= 1
        assert marquee["overflowing"] is True
        assert marquee["animation"] == "v07-channel-name-marquee"

        page.evaluate("XtreamlyTVApp.activeCategory.movies='102'; XtreamlyTVApp.renderShell('movies')")
        page.wait_for_selector("[data-v07-media-index]")
        assert page.locator("[data-v07-media-index]").count() <= 6
        page.locator("#v07SearchNav").click()
        page.locator("#v07SearchInput").fill("no-such-title")
        page.wait_for_timeout(150)
        assert "No results" in page.locator("#v07MediaHero").inner_text()

        stale = page.evaluate("""async () => {
          const app = XtreamlyTVApp;
          app.resetCatalog();
          let resolve;
          app.api = {getVodStreams: () => new Promise(done => { resolve = done; })};
          const pending = app.loadCategory('movies', 'old', {render:false});
          app.resetCatalog();
          resolve([{stream_id:'old', name:'Old provider item', category_id:'old'}]);
          await pending;
          return Boolean(app.catalogCache.movies.old);
        }""")
        assert stale is False

        retained = page.evaluate("""() => {
          const app = XtreamlyTVApp; app.resetCatalog();
          for (let category=0; category<8; category++) {
            app.storeCache('movies', String(category), Array.from({length:1000}, (_, index) => ({
              stream_id:category*1000+index+1, name:'Synthetic '+index, content_type:'movie'
            })));
          }
          return {categories:Object.keys(app.catalogCache.movies).length, items:Object.keys(app.itemLookup).length};
        }""")
        assert retained == {"categories": 3, "items": 3000}
        assert errors == []
        browser.close()
    print("PASS: production v0.7 shell, stale-response guards, and bounded lookup cache.")


if __name__ == "__main__":
    main()
