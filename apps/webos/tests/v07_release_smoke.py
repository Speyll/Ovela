"""Ovela webOS release regressions using deterministic demo data.

The shell deliberately has no featured hero or media wheel: Home is a set of
genuine rails and the catalog is a readable category rail plus either a poster
grid or one flat channel list. These checks cover those product decisions as
well as the provider/playback guards that must survive the redesign.
"""
from pathlib import Path
import base64
import os
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1] / "app"


def inline_app() -> str:
    css = "\n".join((ROOT / "css" / name).read_text(encoding="utf-8") for name in (
        "tokens.css", "app.css", "v0.7-ui.css", "legacy-layout.css", "ovela.css",
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
        page.evaluate("OvelaApp.startDemo()")
        assert page.evaluate("OvelaApp.__v07UiRefreshInstalled") is True

        # Home is content-first: no featured hero, no promotional backdrop, no Browse shortcuts.
        page.evaluate("OvelaApp.renderShell('home')")
        page.wait_for_selector(".ovela-home")
        assert page.locator(".ovela-home .v07-home-hero, .ovela-home .hero").count() == 0
        assert page.locator(".ovela-home .shortcut-card").count() == 0
        assert "Browse" not in page.locator(".ovela-home").inner_text()

        # Recent activity becomes real rails with readable titles.
        page.evaluate("""() => {
          const store = OvelaStore;
          store.addRecent({stream_id:1001, name:'Metro News', category_id:'2', content_type:'live'}, 'live');
          store.addRecent({stream_id:2001, name:'Midnight Circuit', category_id:'102', content_type:'movie'}, 'movie');
          OvelaApp.state = store.getState();
          OvelaApp.renderShell('home');
        }""")
        page.wait_for_selector(".ovela-home .v07-home-row-section")
        home_text = page.locator(".ovela-home").inner_text()
        assert "Recent Live TV" in home_text
        assert "Continue watching" in home_text
        assert "Metro News" in home_text
        assert "Midnight Circuit" in home_text

        # Live TV is a flat, single-column list, not an EPG grid or media wheel.
        page.evaluate("OvelaApp.activeCategory.live='2'; OvelaApp.searchText.live=''; OvelaApp.renderShell('live')")
        page.wait_for_selector("#catalogGrid.channel-list .channel-tile")
        assert page.locator(".v07-guide, .v07-category-wheel, .v07-media-hero").count() == 0
        assert page.locator("#catalogGrid.channel-list .channel-tile").count() == 2
        assert page.evaluate("getComputedStyle(document.querySelector('#catalogGrid .channel-tile')).display") == "flex"
        assert page.locator(".ovela-category-list .category-button").count() >= 6

        # Activating a category moves focus into the flat channel list.
        page.locator(".ovela-category-list .category-button", has_text="Sports").click()
        page.wait_for_selector("#catalogGrid.channel-list .channel-tile")
        assert page.evaluate("document.activeElement.classList.contains('channel-tile')") is True
        page.locator("#catalogSearch").fill("Stadium")
        page.wait_for_timeout(180)
        assert page.locator("#catalogGrid.channel-list .channel-tile").count() == 1
        page.locator("#catalogSearch").fill("")
        page.wait_for_timeout(180)

        # Category management is inline, staged, and supports select all,
        # apply, and undo across Live TV, Movies and Series.
        page.evaluate("OvelaApp.renderShell('settings')")
        page.wait_for_selector("#v07BlockedCategories")
        assert page.locator("[data-v07-manager-kind]").count() == 3
        assert page.locator("#v07CategoryRows .v07-category-row").count() >= 6
        rows = page.locator("#v07CategoryRows .v07-category-row")
        assert rows.first.inner_text().lower().startswith("featured")
        page.locator("#v07CategoryRows .v07-category-row", has_text="News").click()
        assert "1 unsaved change" in page.locator("#v07CategoryFooter").inner_text()
        page.locator("#v07CategorySelectAll").click()
        assert "6 unsaved changes" in page.locator("#v07CategoryFooter").inner_text()
        page.locator("#v07CategoryDeselectAll").click()
        assert "0 unsaved changes" not in page.locator("#v07CategoryFooter").inner_text()
        page.locator("#v07CategoryRows .v07-category-row", has_text="News").click()
        page.locator("#v07CategoryApply").click()
        hidden = page.evaluate("OvelaStore.getState().settings.hiddenCatalogCategories")
        assert hidden["live"] == ["2"], hidden
        assert page.locator("#v07CategoryUndo").count() == 1
        page.locator("#v07CategoryUndo").click()
        hidden = page.evaluate("OvelaStore.getState().settings.hiddenCatalogCategories")
        assert hidden["live"] == [], hidden

        # Movies keep a poster grid and a search field, without a hero/wheel.
        page.evaluate("OvelaApp.activeCategory.movies='102'; OvelaApp.searchText.movies=''; OvelaApp.renderShell('movies')")
        page.wait_for_selector("#catalogGrid .poster-card")
        assert page.locator(".v07-media-hero, .v07-category-wheel, .hero").count() == 0
        assert page.locator(".ovela-catalog-head h2").inner_text() == "Action"
        page.locator("#catalogSearch").fill("Midnight")
        page.wait_for_timeout(180)
        assert page.locator("#catalogGrid .poster-card").count() == 1
        page.locator("#catalogSearch").fill("")
        page.wait_for_timeout(180)

        # Live OK must reveal controls rather than toggle playback.
        live_ok = page.evaluate("""() => {
          const app = OvelaApp;
          const original = app.showOverlay;
          const calls = [];
          app.playerType = 'live';
          app.showOverlay = () => calls.push('overlay');
          app.togglePlayback();
          app.showOverlay = original;
          return calls;
        }""")
        assert live_ok == ["overlay"], live_ok

        xtream = page.evaluate("""() => {
          const api = new OvelaApi({
            server: 'https://primary.example:443/',
            username: 'u name',
            password: 'p@ss',
            alternateServers: ['backup.example:8443']
          }, {streamFormat: 'auto'});
          return {
            server: api.server,
            alternates: api.alternateServers,
            live: api.getLiveCandidates(7, ['m3u8', 'ts']).map(candidate => candidate.url),
            movie: api.getMediaCandidates('movie', 9, 'mp4').map(candidate => candidate.url)
          };
        }""")
        assert xtream["server"] == "https://primary.example", xtream
        assert xtream["alternates"] == ["https://backup.example:8443"], xtream
        assert len(xtream["live"]) == 4, xtream
        assert xtream["live"][0] == "https://primary.example/live/u%20name/p%40ss/7.m3u8", xtream
        assert xtream["live"][2] == "https://backup.example:8443/live/u%20name/p%40ss/7.m3u8", xtream
        assert xtream["movie"][0] == "https://primary.example/movie/u%20name/p%40ss/9.mp4", xtream
        assert xtream["movie"][2] == "https://backup.example:8443/movie/u%20name/p%40ss/9.mp4", xtream

        playback = page.evaluate("""() => {
          const app = OvelaApp;
          const previousApi = app.api;
          const previousDemo = app.demo;
          app.demo = false;
          app.api = new OvelaApi({
            server: 'https://primary.example:443',
            username: 'demo',
            password: 'demo-pass'
          }, {streamFormat: 'auto'});
          const live = {stream_id: 1001, name: 'Fixture Live', content_type: 'live'};
          const movie = {stream_id: 2001, name: 'Fixture Movie', content_type: 'movie', container_extension: 'mp4'};
          const episode = {id: '3001-1-1', stream_id: '3001-1-1', name: 'Fixture Episode', content_type: 'episode', container_extension: 'mp4'};
          const result = {
            live: app.buildPlaybackCandidates(live, 'live').map(candidate => candidate.url),
            movie: app.buildPlaybackCandidates(movie, 'movie').map(candidate => candidate.url),
            episode: app.buildPlaybackCandidates(episode, 'episode').map(candidate => candidate.url)
          };
          app.api = previousApi;
          app.demo = previousDemo;
          return result;
        }""")
        assert playback["live"][0] == "https://primary.example/live/demo/demo-pass/1001.m3u8", playback
        assert playback["live"][1] == "https://primary.example/live/demo/demo-pass/1001.ts", playback
        assert playback["movie"][0] == "https://primary.example/movie/demo/demo-pass/2001.mp4", playback
        assert playback["episode"][0] == "https://primary.example/series/demo/demo-pass/3001-1-1.mp4", playback
        assert not any("[object" in url for values in playback.values() for url in values), playback

        stale = page.evaluate("""async () => {
          const app = OvelaApp;
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
          const app = OvelaApp; app.resetCatalog();
          for (let category=0; category<8; category++) {
            app.storeCache('movies', String(category), Array.from({length:1000}, (_, index) => ({
              stream_id:category*1000+index+1, name:'Synthetic '+index, content_type:'movie'
            })));
          }
          return {categories:Object.keys(app.catalogCache.movies).length, items:Object.keys(app.itemLookup).length};
        }""")
        assert retained == {"categories": 3, "items": 3000}

        loaded = page.evaluate("""() => {
          const app = OvelaApp; app.resetCatalog();
          app.storeCache('movies', 'one', [
            {stream_id:'1', name:'First', content_type:'movie'},
            {stream_id:'2', name:'Second', content_type:'movie'}
          ]);
          app.storeCache('movies', 'two', [
            {stream_id:'2', name:'Duplicate', content_type:'movie'},
            {stream_id:'3', name:'Third', content_type:'movie'},
            {name:'Missing id', content_type:'movie'}
          ]);
          app.storeCache('movies', 'three', [{stream_id:'4', name:'Fourth', content_type:'movie'}]);
          const beforeEviction = {
            ids:app.loadedItems('movies').map(item => item.stream_id),
            count:app.loadedCount('movies'),
            first:app.firstLoadedItem('movies').name
          };
          app.storeCache('movies', 'four', [{stream_id:'5', name:'Fifth', content_type:'movie'}]);
          return {
            beforeEviction,
            afterEviction:app.loadedItems('movies').map(item => item.stream_id),
            countAfterEviction:app.loadedCount('movies'),
            firstAfterEviction:app.firstLoadedItem('movies').stream_id
          };
        }""")
        assert loaded == {
            "beforeEviction": {"ids": ["1", "2", "3", "4"], "count": 4, "first": "First"},
            "afterEviction": ["2", "3", "4", "5"],
            "countAfterEviction": 4,
            "firstAfterEviction": "2",
        }, loaded

        # Provider artwork is still requested at the painted poster width.
        page.route("https://image.tmdb.org/**", lambda route: route.fulfill(
            status=200,
            content_type="image/png",
            body=base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9Zl9sAAAAASUVORK5CYII="),
        ))
        page.evaluate("""() => {
          const app = OvelaApp;
          app.resetCatalog();
          app.api.getVodInfo = () => new Promise(() => {});
          const item = {
            stream_id:'tmdb-1', name:'TMDB Fixture', content_type:'movie', category_id:'tmdb',
            cover:'https://image.tmdb.org/t/p/original/poster.jpg',
            backdrop_path:'https://image.tmdb.org/t/p/original/backdrop.jpg'
          };
          app.vodCategories = [{category_id:'tmdb', category_name:'TMDB'}];
          app.searchText.movies = '';
          app.activeCategory.movies = 'tmdb';
          app.catalogCache.movies.tmdb = [item];
          app.currentItems.movies = [item];
          app.renderShell('movies');
        }""")
        page.wait_for_selector("#catalogGrid .poster-card img", state="attached")
        poster = page.locator("#catalogGrid .poster-card img").first.get_attribute("src")
        assert poster == "https://image.tmdb.org/t/p/w500/poster.jpg", poster

        # Category rail: every provider category is reachable by D-pad, the
        # rail is a real scroll container, and the pointer can commit an
        # off-screen category.
        page.evaluate("""() => {
          const app = OvelaApp;
          app.startDemo();
          app.liveCategories.push(...Array.from({length: 28}, (_, index) => ({
            category_id: 'rail-' + index,
            category_name: 'Rail Category ' + String(index + 1).padStart(2, '0')
          })));
          app.activeCategory.live = '1';
          app.searchText.live = '';
          app.renderShell('live');
        }""")
        page.wait_for_selector("#categoryList .category-button")
        rail = page.evaluate("""() => {
          const element = document.querySelector('#categoryList');
          return {
            count: element.querySelectorAll('.category-button').length,
            overflow: getComputedStyle(element).overflowY,
            scrollHeight: element.scrollHeight,
            clientHeight: element.clientHeight
          };
        }""")
        assert rail["count"] >= 30, rail
        assert rail["overflow"] == "auto", rail
        assert rail["scrollHeight"] > rail["clientHeight"], rail

        page.evaluate("OvelaApp.categoryRail.focusIndex(0)")
        for _ in range(rail["count"] - 1):
            page.keyboard.press("ArrowDown")
        page.wait_for_timeout(120)
        last = page.evaluate("""() => {
          const element = document.querySelector('#categoryList');
          const focused = document.activeElement.getBoundingClientRect();
          const box = element.getBoundingClientRect();
          return {
            index: document.activeElement.dataset.categoryIndex,
            scrollTop: element.scrollTop,
            visible: focused.top >= box.top - 1 && focused.bottom <= box.bottom + 1
          };
        }""")
        assert last["index"] == str(rail["count"] - 1), last
        assert last["scrollTop"] > 0, last
        assert last["visible"] is True, last

        page.evaluate("document.querySelector('#categoryList').scrollTop = 0")
        pointer_category = page.evaluate("""() => {
          const button = document.querySelector('#categoryList [data-category-index="28"]');
          const id = button.dataset.catalogCategory;
          button.click();
          return id;
        }""")
        page.wait_for_timeout(300)
        assert page.evaluate("OvelaApp.activeCategory.live") == pointer_category

        # Back from a movie detail restores the poster selection and the
        # category rail instead of resetting the catalog.
        page.evaluate("""() => {
          const base = OvelaMock.vodStreams[0];
          for (let index = 0; index < 30; index += 1) {
            OvelaMock.vodStreams.push(Object.assign({}, base, {
              stream_id: 8000 + index,
              name: 'Bulk Movie ' + index,
              category_id: '102'
            }));
          }
          const app = OvelaApp;
          app.startDemo();
          app.searchText.movies = '';
          app.renderShell('movies');
        }""")
        page.wait_for_selector("#categoryList .category-button")
        page.evaluate("""() => {
          const action = Array.from(document.querySelectorAll('#categoryList .category-button'))
            .find(button => button.innerText === 'Action');
          action.click();
        }""")
        page.wait_for_selector("#catalogGrid .poster-card")
        page.wait_for_function("document.activeElement.classList.contains('poster-card')")
        page.wait_for_timeout(300)
        for _ in range(3):
            page.keyboard.press("ArrowDown")
        page.wait_for_timeout(120)
        selected = page.evaluate("document.activeElement.dataset.virtualIndex")
        assert selected is not None
        page.keyboard.press("Enter")
        page.wait_for_selector("#playMovie")
        assert page.evaluate("Boolean(OvelaApp.detail)") is True
        page.evaluate("""() => {
          document.dispatchEvent(new KeyboardEvent('keydown', {
            key: 'GoBack', code: 'GoBack', keyCode: 461, which: 461,
            bubbles: true, cancelable: true
          }));
        }""")
        page.wait_for_timeout(400)
        restored = page.evaluate("""() => ({
          detail: Boolean(OvelaApp.detail),
          focused: document.activeElement.dataset.virtualIndex,
          category: OvelaApp.activeCategory.movies
        })""")
        assert restored["detail"] is False, restored
        assert restored["focused"] == selected, (selected, restored)
        assert restored["category"] == "102", restored

        assert errors == []
        browser.close()
    print("PASS: Ovela Home/catalog, staged category management, Live OK policy, provider failover, stale guards and bounded cache.")


if __name__ == "__main__":
    main()
