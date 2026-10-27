# Architecture

Ovela is a small monorepo with two native application shells and a shared layer
of provider, persistence, and design primitives. Playback and UI rendering stay
platform-native; only code that is genuinely platform-neutral is shared.

## Layers

```text
packages/contracts          JSON schemas and fixtures for provider data
packages/design-tokens      Shared colors, spacing, typography, motion
packages/core-web           Browser/webOS domain code (ES5)
        |
        +--> apps/webos/app/js/core.js, api.js, store.js   (generated copies)
        |
apps/webos                  Static HTML/CSS/JS application
apps/android-tv             Kotlin + Jetpack Compose application
apps/api-bridge             Optional Node.js LAN bridge for CORS-limited providers
```

### `packages/core-web`

Canonical source for shared web behavior. `scripts/sync-web-core.mjs` copies
`api.js`, `core.js`, and `store.js` into `apps/webos/app/js/` with a generated
header. Edit the package, then run `npm run sync:core` (or
`npm run prepare:apps`).

- `api.js` — Xtream and M3U request construction, redirect and failover helpers.
- `core.js` — normalization of provider responses, category rules, item keys.
- `store.js` — persisted settings, favorites, history, progress, and artwork
  cache-refresh logic.

The webOS runtime targets older TV browsers, so these files use ES5 syntax and
avoid framework runtimes.

### `apps/webos`

A packaged static application.

| File | Responsibility |
|---|---|
| `appinfo.json` | Package identity and version (`io.github.speyll.ovela`) |
| `index.html` | Shell markup and stylesheet/script order |
| `js/app.js` | Screens, rendering, input routing, playback |
| `js/navigation.js` | Spatial focus and remote navigation |
| `js/virtual-grid.js` | Windowed rendering for very large catalogs |
| `js/v0.7-ui.js` | Production browsing shell and category rail |
| `js/category-rail.js` | Category wheel/rail behavior |
| `js/startup.js` | Branded boot splash and theme bootstrap |
| `js/artwork.js` | Artwork resolution and fallbacks |
| `js/mock-data.js` | Demo catalog used by tests and the browser demo |

State is stored in `localStorage` under `io.github.speyll.ovela.state.v1`.
The splash is CSS-driven and dismissed once the app shell renders meaningful
content, with a bounded safety timeout.

The optional API bridge is used only when a provider blocks browser-origin
requests. Streams continue to load directly from the provider.

### `apps/android-tv`

A native Kotlin application using Jetpack Compose and AndroidX Media3.

| Package | Responsibility |
|---|---|
| `model` | Immutable UI/data models (`CatalogItem`, `AppSettings`, …) |
| `data` | Xtream/M3U clients, URL building, encrypted credentials, local state, media cache, playback policy |
| `ui` | Compose screens, `AppViewModel`, focus helpers, theme |
| `MainActivity` | Activity, splash handoff, composition root |

Key properties:

- `AppViewModel` owns a single `AppUiState` stream; screens render from it.
- Provider credentials are stored with `EncryptedSharedPreferences`.
- Favorites, history, progress, settings, and the metadata cache are partitioned
  by provider account.
- Media3 ExoPlayer handles playback, with HLS support and explicit failover.
- Release builds are minified with R8 and signed from an external keystore.

The Android views deliberately reimplement the product behavior natively rather
than embedding the webOS UI.

### `apps/api-bridge`

A small Node.js service for webOS users whose provider does not send CORS
headers. It accepts only configured provider servers, playlist URLs, origins, and
a shared token. It is not a general-purpose proxy and should stay on a trusted
LAN.

## Provider data flow

```text
User credentials
      |
      v
Provider client (webOS JS or Android Kotlin)
      |
      +--> category list
      +--> category-scoped item list
      +--> EPG / detail metadata
      |
      v
Normalized models -> UI state -> screens
      |
      v
Playback URL candidates -> platform media engine
```

Rules shared by both platforms:

- Provider responses are untrusted and may be arrays, keyed objects, or
  malformed.
- Catalog requests are category-scoped. Ovela does not download an entire
  provider catalog unless the API offers no alternative.
- Alternate hosts rewrite only scheme, host, and port; path and query tokens are
  preserved.
- Playback tries the next candidate before retrying the same one and uses a
  finite retry budget.
- Credentials and authenticated stream URLs never appear in logs or public issue
  reports.

## Storage and upgrade compatibility

Stored data is intentionally stable across the XtreamlyTV-to-Ovela rename.

| Platform | Data | Key |
|---|---|---|
| webOS | App state | `io.github.speyll.ovela.state.v1` |
| Android | Encrypted credentials | `xtreamlytv.credentials` |
| Android | Local state | `xtreamlytv.state.v1` |
| Android | Metadata cache | `xtreamlytv.media-cache.v1` |

The Android preference names retain their historical spelling on purpose.
Renaming them would orphan existing installations' credentials, favorites,
progress, and settings. Comments at each declaration document this constraint;
treat the strings as frozen.

## Testing strategy

| Layer | Location | Tool |
|---|---|---|
| Shared web domain | `packages/core-web/test` | `node --test` |
| webOS UI and playback | `apps/webos/tests` | Playwright smoke tests |
| Bridge behavior | `apps/api-bridge/test` | `node --test` |
| Android data/policy | `apps/android-tv/app/src/test` | JUnit |
| Build/version consistency | `scripts` | Node checks |

Playwright tests exercise the packaged webOS UI in Chromium, including a
synthetic 55,000-item catalog. They are a regression net, not a substitute for
physical-TV validation of codecs, focus, and media surfaces.
