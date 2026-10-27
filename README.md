# Ovela

Ovela is an open-source, remote-first IPTV client for living-room televisions.
It connects to user-supplied Xtream-compatible providers or M3U/M3U+ playlists
and presents Live TV, Movies, and Series in a fast, category-scoped interface
built for very large catalogs.

> Ovela does not provide channels, subscriptions, playlists, or copyrighted
> media. Use it only with services and content you are authorized to access.

## Platform status

| Platform | Version | Package | Playback |
|---|---|---|---|
| LG webOS TV | **1.0.0** | `.ipk` | Native webOS media surface |
| Android TV / Google TV | **1.0.0** | Signed `.apk` for sideloading | AndroidX Media3 ExoPlayer |
| Local browser demo | Development only | Static files | Browser media support |

Download packages from
[GitHub Releases](https://github.com/Speyll/Ovela/releases/latest).

The Android APK is a release build signed for sideloading. It is not published
through Google Play. An existing installation signed with a different key must
be uninstalled first, which removes app data. The webOS package is installed in
developer mode and is not signed for the LG Content Store.

## Features

- Xtream account authentication
- M3U/M3U+ playlist import from a URL on both platforms, plus local playlist
  import on Android TV
- Explicit alternate-host failover for Xtream and M3U streams
- Category-scoped Live TV, VOD, and Series browsing
- Bounded large-catalog navigation with readable category lists, flat Live TV
  channels, and poster rails
- Favorites virtual categories, custom collections, default A-Z sorting, and
  editable group management
- Movie and episode resume positions on webOS and Android TV
- Live HLS/MPEG-TS fallback on both TV platforms
- Native Media3 playback on Android TV
- Remote-first navigation and visible focus states
- A shared graphite/ivory Ovela theme, with saved legacy skin preferences mapped
  onto it
- Editable provider settings, catalog cache controls, local history, and privacy
  controls
- Optional LAN API bridge for providers that block browser CORS, including an
  allowlisted M3U playlist endpoint
- No advertising, analytics, or tracking

## Monorepo layout

```text
apps/
  webos/          LG webOS packaged app
  android-tv/     Native Android TV / Google TV app
  api-bridge/     Optional local metadata/CORS bridge
packages/
  core-web/       Shared browser/webOS Xtream, M3U, recovery, and persistence
  contracts/      Platform-neutral JSON schemas and fixtures
  design-tokens/  Shared brand, spacing, color, and motion tokens
  brand/          Canonical project artwork
scripts/          Build, install, synchronization, and release helpers
docs/             Architecture, development, testing, and release guides
.github/          CI, release workflows, templates, and dependency updates
```

The repository shares provider contracts, brand assets, design tokens, fixtures,
and web-compatible domain primitives. Playback and UI shells remain
platform-native so each television platform can use its best-supported media
engine.

## Quick start

### Prerequisites

- Git
- Node.js 20 or newer
- Python 3.11+ for browser smoke tests
- LG webOS CLI for webOS builds
- Android Studio or Android SDK/JDK 17 for Android TV builds

Prepare generated platform files and run the fast checks:

```bash
npm run prepare:apps
npm run check:versions
npm run check:webos
npm run test:core
```

### Build webOS

```bash
npm install -g @webos-tools/cli
npm run build:webos
```

Output:

```text
dist/webos/io.github.speyll.ovela_1.0.0_all.ipk
```

### Build Android TV

Set up signing once as described in
[Releasing](docs/releasing.md#android-signing), then:

```bash
cd apps/android-tv
./gradlew :app:assembleRelease
```

Windows:

```powershell
cd apps\android-tv
.\gradlew.bat :app:assembleRelease
```

Output:

```text
apps/android-tv/app/build/outputs/apk/release/app-release.apk
```

## Documentation

- [Getting started](docs/getting-started.md)
- [Architecture](docs/architecture.md)
- [Build and test on LG webOS](docs/development/webos.md)
- [Build and test on Android TV / Google TV](docs/development/android-tv.md)
- [Debugging and diagnostics](docs/development/debugging.md)
- [Releasing and signing](docs/releasing.md)
- [Ovela product and parity notes](docs/design/ovela.md)
- [Ovela asset family](docs/design/ovela-assets.md)
- [Contributing](CONTRIBUTING.md)
- [Security policy](SECURITY.md)
- [Privacy](PRIVACY.md)
- [Changelog](CHANGELOG.md)

## Category classification and hidden categories

Category names are classified by the shared rule table in
`packages/category-classification.json`, generated into both clients. Matching
uses normalized whole tokens, so `EN|`, `ENGLISH` and `English Movies` are
recognized while `Entertainment` is not. Prefixes are preserved as their own
group; language, quality/format and provider/region metadata are shown on each
row and exposed through independent Group, Language and Quality filters. Names
with several language markers or no reliable match remain under Other /
Unclassified rather than being guessed.

Hiding a category removes it from browse, Home and Favourites and drops that
category's loaded list and derived item references. Favorites, Continue
Watching, history, progress and the active playback item are kept separately.
Re-showing it loads the category again on demand. This is category-scoped
cleanup, not a full-playlist download or a claim of measured RAM, CPU, network
or storage savings. Artwork already decoded by the platform and provider-side
data are outside the app's control.

## Development principles

1. Never fetch a provider's entire catalog unless the API offers no
   category-scoped alternative.
2. Keep remote navigation deterministic and visibly focused.
3. Use platform-native playback where practical.
4. Never ship provider credentials, playlists, or copyrighted content.
5. Treat provider responses as untrusted and inconsistent.
6. Keep logs free of passwords and authenticated stream URLs.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
Third-party attributions are listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
