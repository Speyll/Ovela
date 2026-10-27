"""Regression: category manager groups provider prefixes and scopes actions.

Prefixes are whatever providers put before a `|` (language codes, `4K`, region
names). Bulk actions must be explicit about their scope: the filtered view, the
entire section, or one selected group. Hidden states outside that scope must not
change, every edit saves immediately, and Undo restores the previous set.
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


CATEGORIES = [
    {"category_id": "1", "category_name": "AR| News"},
    {"category_id": "2", "category_name": "AR| Sport"},
    {"category_id": "3", "category_name": "FR| Cinema"},
    {"category_id": "4", "category_name": "4K| UHD"},
    {"category_id": "5", "category_name": "Plain Channel"},
    {"category_id": "6", "category_name": "Plain News"},
    {"category_id": "7", "category_name": "AR| Docs"},
    {"category_id": "8", "category_name": "AR| Kids"},
    {"category_id": "9", "category_name": "AR| Music"},
    {"category_id": "10", "category_name": "AR| Local"},
]

LIVE_CLASSIFIED = [
    {"category_id": "1", "category_name": "ENGLISH | News"},
    {"category_id": "2", "category_name": "FRANCE| Cinéma"},
    {"category_id": "3", "category_name": "ESPAÑA | Deportes"},
    {"category_id": "4", "category_name": "4K| UHD"},
    {"category_id": "5", "category_name": "SPORTS HD"},
    {"category_id": "6", "category_name": "Entertainment"},
    {"category_id": "7", "category_name": "AR EN| Mixed"},
    {"category_id": "8", "category_name": "HDR| Movies"},
    {"category_id": "9", "category_name": "NETFLIX| Originals"},
    {"category_id": "10", "category_name": "PLAIN"},
]

MOVIE_CLASSIFIED = [
    {"category_id": "11", "category_name": "ENGLISH MOVIES"},
    {"category_id": "12", "category_name": "FRENCH 4K MOVIES"},
    {"category_id": "13", "category_name": "SPANISH FHD"},
    {"category_id": "14", "category_name": "Comedy"},
]

SERIES_CLASSIFIED = [
    {"category_id": "21", "category_name": "DEUTSCH| Serien"},
    {"category_id": "22", "category_name": "TURKISH DRAMA"},
    {"category_id": "23", "category_name": "Documentary SD"},
    {"category_id": "24", "category_name": "KIDS"},
]


def group_labels(page) -> list[str]:
    return page.eval_on_selector_all(
        "#v07CategoryGroups .v07-manager-group",
        "els => els.map(el => el.textContent.replace(/\\s+/g, ' ').trim())",
    )


def facet_labels(page, host: str) -> list[str]:
    return page.eval_on_selector_all(
        f"{host} .v07-manager-group",
        "els => els.map(el => el.textContent.replace(/\\s+/g, ' ').trim())",
    )


def row_names(page) -> list[str]:
    return page.eval_on_selector_all(
        "#v07CategoryRows .v07-category-title",
        "els => els.map(el => el.textContent.trim())",
    )


def row_meta(page) -> list[str]:
    return page.eval_on_selector_all(
        "#v07CategoryRows .v07-category-meta",
        "els => els.map(el => el.textContent.trim())",
    )


def hidden_categories(page, kind: str):
    return page.evaluate(
        "kind => OvelaStore.getState().settings.hiddenCatalogCategories[kind]",
        kind,
    )


def hidden_live(page):
    return hidden_categories(page, "live")


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
            """(categories) => {
                OvelaStore.updateSettings({ hiddenCatalogCategories: { live: ['2'], movies: [], series: [] } });
                OvelaApp.state = OvelaStore.getState();
                OvelaApp.liveCategories = categories;
                OvelaApp.renderSettings();
            }""",
            CATEGORIES,
        )
        page.wait_for_selector(".v07-manager-group")

        assert group_labels(page) == ["All 10", "4K 1", "AR 6", "FR 1", "Other 2"], group_labels(page)

        # Other/unrecognized categories stay in their own group.
        page.click('[data-v07-manager-group="__other__"]')
        page.wait_for_timeout(30)
        assert row_names(page) == ["Plain Channel", "Plain News"], row_names(page)
        assert page.locator("#v07CategoryGroups .active").inner_text().startswith("Other")

        # Group scope works on the whole group, composed with mixed hidden state.
        page.click('[data-v07-manager-group="AR"]')
        page.wait_for_timeout(30)
        assert row_names(page) == ["AR| News", "AR| Sport", "AR| Docs", "AR| Kids", "AR| Music", "AR| Local"], row_names(page)
        page.click("#v07CategoryHideGroup")
        page.wait_for_timeout(40)
        assert set(hidden_live(page)) == {"1", "2", "7", "8", "9", "10"}, hidden_live(page)
        assert "Saved automatically" in page.locator("#v07CategoryFooter").inner_text()
        page.click("#v07CategoryUndo")
        page.wait_for_timeout(40)
        assert hidden_live(page) == ["2"], hidden_live(page)

        page.click("#v07CategoryShowGroup")
        page.wait_for_timeout(40)
        assert hidden_live(page) == [], hidden_live(page)
        page.click("#v07CategoryUndo")
        page.wait_for_timeout(40)
        assert hidden_live(page) == ["2"], hidden_live(page)

        # Filtered scope: search + "Hide shown" saves at once and must not touch
        # categories outside the current results.
        page.click('[data-v07-manager-group="__all__"]')
        page.fill("#v07CategorySearch", "News")
        page.wait_for_timeout(30)
        assert row_names(page) == ["AR| News", "Plain News"], row_names(page)
        page.click("#v07CategorySelectAll")
        page.wait_for_timeout(40)
        assert set(hidden_live(page)) == {"1", "2", "6"}, hidden_live(page)
        page.click("#v07CategoryUndo")
        page.wait_for_timeout(40)
        assert hidden_live(page) == ["2"], hidden_live(page)

        # Bulk actions cover the whole section, not just the filtered results.
        page.fill("#v07CategorySearch", "")
        page.wait_for_timeout(30)
        page.click("#v07CategoryHideAll")
        page.wait_for_timeout(40)
        assert len(hidden_live(page)) == 10, hidden_live(page)
        page.click("#v07CategoryUndo")
        page.wait_for_timeout(40)
        assert hidden_live(page) == ["2"], hidden_live(page)

        # Search and the selected prefix filter combine.
        page.click('[data-v07-manager-group="AR"]')
        page.fill("#v07CategorySearch", "Sport")
        page.wait_for_timeout(30)
        assert row_names(page) == ["AR| Sport"], row_names(page)

        # Scope buttons advertise the whole section rather than filtered results.
        assert "Hide all 10" in page.locator("#v07CategoryHideAll").inner_text()
        assert "Show all 10" in page.locator("#v07CategoryShowAll").inner_text()


        # ---- Shared classification and independent facets -----------------
        page.evaluate(
            """(data) => {
                OvelaStore.updateSettings({ hiddenCatalogCategories: { live: [], movies: [], series: [] } });
                OvelaApp.state = OvelaStore.getState();
                OvelaApp.liveCategories = data.live;
                OvelaApp.vodCategories = data.movies;
                OvelaApp.seriesCategories = data.series;
                OvelaApp.renderSettings();
            }""",
            {"live": LIVE_CLASSIFIED, "movies": MOVIE_CLASSIFIED, "series": SERIES_CLASSIFIED},
        )
        page.wait_for_timeout(50)
        page.fill("#v07CategorySearch", "")
        page.click('[data-v07-manager-group="__all__"]')
        page.click('[data-v07-manager-language="__all__"]')
        page.click('[data-v07-manager-quality="__all__"]')
        page.wait_for_timeout(40)

        assert group_labels(page) == [
            "All 10", "4K 1", "AR EN 1", "ENGLISH 1", "ESPAÑA 1",
            "FRANCE 1", "HDR 1", "NETFLIX 1", "Other 3",
        ], group_labels(page)
        assert facet_labels(page, "#v07CategoryLanguages") == [
            "Any", "English 1", "French 1", "Spanish 1", "Unclassified 7",
        ], facet_labels(page, "#v07CategoryLanguages")
        assert facet_labels(page, "#v07CategoryQualities") == [
            "Any", "4K 1", "HD 1", "Unclassified 8",
        ], facet_labels(page, "#v07CategoryQualities")

        meta = dict(zip(row_names(page), row_meta(page)))
        assert meta["ENGLISH | News"].startswith("English"), meta
        assert "French" in meta["FRANCE| Cinéma"] and "France" in meta["FRANCE| Cinéma"], meta
        assert "Spanish" in meta["ESPAÑA | Deportes"] and "Spain" in meta["ESPAÑA | Deportes"], meta
        assert meta["Entertainment"] == "Unclassified", meta
        assert meta["SPORTS HD"] == "HD", meta
        assert meta["AR EN| Mixed"].startswith("Multiple languages"), meta
        assert "Netflix" in meta["NETFLIX| Originals"], meta
        assert "HEVC" not in meta["HDR| Movies"] and meta["HDR| Movies"].startswith("HDR"), meta

        # Language and quality compose without disturbing the group scope.
        page.click('[data-v07-manager-language="fr"]')
        page.wait_for_timeout(40)
        assert row_names(page) == ["FRANCE| Cinéma"], row_names(page)
        assert page.evaluate("document.activeElement.getAttribute('data-v07-manager-language')") == "fr"
        page.click('[data-v07-manager-quality="4k"]')
        page.wait_for_timeout(40)
        assert row_names(page) == [], row_names(page)

        page.click('[data-v07-manager-quality="__all__"]')
        page.click('[data-v07-manager-language="__all__"]')
        page.click('[data-v07-manager-group="__other__"]')
        page.wait_for_timeout(40)
        assert row_names(page) == ["SPORTS HD", "Entertainment", "PLAIN"], row_names(page)
        page.click('[data-v07-manager-language="en"]')
        page.wait_for_timeout(40)
        assert row_names(page) == [], row_names(page)
        page.click('[data-v07-manager-language="__all__"]')
        page.click('[data-v07-manager-group="__all__"]')
        page.click('[data-v07-manager-language="es"]')
        page.wait_for_timeout(40)
        assert row_names(page) == ["ESPAÑA | Deportes"], row_names(page)

        page.click('[data-v07-manager-language="__all__"]')
        page.click('[data-v07-manager-group="AR EN"]')
        page.wait_for_timeout(40)
        assert row_names(page) == ["AR EN| Mixed"], row_names(page)
        assert "Multiple languages" in row_meta(page)[0], row_meta(page)
        assert "Unclassified" in page.locator('[data-v07-manager-language="__none__"]').inner_text()

        # Row rerenders keep scroll; facet rerenders restore focus to the chip.
        page.click('[data-v07-manager-group="__all__"]')
        page.wait_for_timeout(30)
        page.eval_on_selector("#v07CategoryRows", "el => { el.scrollTop = 80; return el.scrollTop; }")
        page.eval_on_selector('[data-v07-manager-category="4"]', "el => el.click()")
        page.wait_for_timeout(40)
        assert page.eval_on_selector("#v07CategoryRows", "el => el.scrollTop") == 80
        assert page.evaluate("document.activeElement.getAttribute('data-v07-manager-category')") == "4"
        # A row tap saves immediately.
        assert hidden_live(page) == ["4"], hidden_live(page)
        page.click("#v07CategoryUndo")
        page.wait_for_timeout(40)
        assert hidden_live(page) == [], hidden_live(page)

        # Hide one English category, then restore it and undo both directions.
        page.click('[data-v07-manager-language="en"]')
        page.wait_for_timeout(30)
        assert row_names(page) == ["ENGLISH | News"], row_names(page)
        page.click("#v07CategorySelectAll")
        page.wait_for_timeout(40)
        assert hidden_live(page) == ["1"], hidden_live(page)
        page.click("#v07CategoryUndo")
        page.wait_for_timeout(40)
        assert hidden_live(page) == [], hidden_live(page)
        page.click("#v07CategorySelectAll")
        page.wait_for_timeout(40)
        assert hidden_live(page) == ["1"], hidden_live(page)
        page.click("#v07CategoryDeselectAll")
        page.wait_for_timeout(40)
        assert hidden_live(page) == [], hidden_live(page)

        # Movies: leading full-word aliases, independent quality facets, and a
        # saved state that survives a tab switch.
        page.click('[data-v07-manager-kind="movies"]')
        page.wait_for_timeout(50)
        assert group_labels(page) == ["All 4", "ENGLISH 1", "FRENCH 1", "SPANISH 1", "Other 1"], group_labels(page)
        assert facet_labels(page, "#v07CategoryLanguages") == [
            "Any", "English 1", "French 1", "Spanish 1", "Unclassified 1",
        ], facet_labels(page, "#v07CategoryLanguages")
        assert facet_labels(page, "#v07CategoryQualities") == [
            "Any", "4K 1", "FHD 1", "Unclassified 2",
        ], facet_labels(page, "#v07CategoryQualities")
        page.click('[data-v07-manager-language="es"]')
        page.wait_for_timeout(30)
        assert row_names(page) == ["SPANISH FHD"], row_names(page)
        page.click("#v07CategorySelectAll")
        page.wait_for_timeout(40)
        assert hidden_categories(page, "movies") == ["13"], hidden_categories(page, "movies")
        page.click('[data-v07-manager-kind="live"]')
        page.wait_for_timeout(30)
        page.click('[data-v07-manager-kind="movies"]')
        page.wait_for_timeout(30)
        assert hidden_categories(page, "movies") == ["13"], hidden_categories(page, "movies")
        assert page.locator("#v07CategoryUndo").count() == 1
        page.click("#v07CategoryUndo")
        page.wait_for_timeout(40)
        assert hidden_categories(page, "movies") == [], hidden_categories(page, "movies")

        # Series: no leaked live/movie filters, and independent saving.
        page.click('[data-v07-manager-kind="series"]')
        page.wait_for_timeout(50)
        assert group_labels(page) == ["All 4", "DEUTSCH 1", "TURKISH 1", "Other 2"], group_labels(page)
        assert facet_labels(page, "#v07CategoryLanguages") == [
            "Any", "German 1", "Turkish 1", "Unclassified 2",
        ], facet_labels(page, "#v07CategoryLanguages")
        assert facet_labels(page, "#v07CategoryQualities") == [
            "Any", "SD 1", "Unclassified 3",
        ], facet_labels(page, "#v07CategoryQualities")
        page.click('[data-v07-manager-language="de"]')
        page.wait_for_timeout(30)
        assert row_names(page) == ["DEUTSCH| Serien"], row_names(page)
        page.click("#v07CategorySelectAll")
        page.wait_for_timeout(40)
        assert hidden_categories(page, "series") == ["21"], hidden_categories(page, "series")
        page.click("#v07CategoryUndo")
        page.wait_for_timeout(40)
        assert hidden_categories(page, "series") == [], hidden_categories(page, "series")

        # Hidden categories release cached category lists and item references,
        # but never remove favorites or stop the current playback item.
        page.evaluate(
            """() => {
                OvelaStore.updateSettings({ hiddenCatalogCategories: { live: [], movies: [], series: [] } });
                OvelaApp.state = OvelaStore.getState();
                const item = OvelaApp.normalizeCatalogItems(
                    'live',
                    OvelaMock.liveStreams.filter(stream => String(stream.category_id) === '2')
                )[0];
                OvelaApp.playerMedia = item;
                OvelaStore.toggleFavorite(item, 'live');
                OvelaApp.state = OvelaStore.getState();
                OvelaApp.storeCache('live', '2', [item]);
                window.__hiddenCategoryItem = item;
            }"""
        )
        page.click('[data-v07-manager-kind="live"]')
        page.wait_for_timeout(50)
        assert page.evaluate("!!OvelaApp.catalogCache.live['2']")
        assert page.evaluate("!!OvelaApp.itemLookup['live:1001']")
        page.click('[data-v07-manager-category="2"]')
        page.wait_for_timeout(50)
        assert hidden_live(page) == ["2"], hidden_live(page)
        assert page.evaluate("OvelaApp.catalogCache.live['2'] === undefined")
        assert page.evaluate("OvelaApp.itemLookup['live:1001'] === undefined")
        assert page.evaluate("OvelaStore.getState().favorites.length") == 1
        assert page.evaluate(
            "OvelaApp.playerMedia && String(OvelaApp.playerMedia.stream_id) === String(window.__hiddenCategoryItem.stream_id)"
        )
        assert row_names(page)  # hidden categories remain manageable

        # Showing it again removes the persisted block and reloads normally.
        page.click('[data-v07-manager-category="2"]')
        page.wait_for_timeout(50)
        assert hidden_live(page) == [], hidden_live(page)
        page.evaluate("OvelaApp.loadCategory('live', '2')")
        page.wait_for_timeout(120)
        assert page.evaluate("(OvelaApp.catalogCache.live['2'] || []).length") >= 1
        assert page.evaluate("!!OvelaApp.itemLookup['live:1001']")

        assert not errors, errors
        browser.close()


if __name__ == "__main__":
    main()
