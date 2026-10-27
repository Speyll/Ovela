# Ovela

Ovela is a small, open-source IPTV client for the living room. It plays Xtream
Codes and M3U playlists on LG webOS TVs and Android TV / Google TV, and it can
also be installed on an Android phone.

Ovela ships with no channels, playlists, or subscriptions. You connect it to a
provider you already have.

## Features

- Live TV, movies, and series with a remote-friendly interface
- Xtream Codes and M3U sources, including alternate-server failover
- Favourites, watch progress, and a continue-watching row
- Built-in player controls, subtitles, and stream-format selection

## Build

You need Node.js 20 or newer.

```sh
npm run prepare:apps
```

`prepare:apps` regenerates the shared web core, the category classification
rules, and the design tokens. Generated files are committed, so run it after
editing `packages/core-web/src` or any file under `packages/`.

### LG webOS

The webOS build needs `ares-package` from `@webos-tools/cli`. Prefer the
project-local copy so builds stay reproducible and independent of a global
install:

```sh
npm install --prefix .dev/tooling/webos @webos-tools/cli
```

`npm run build:webos` uses that local CLI automatically. It falls back to a
global `npm install -g @webos-tools/cli` if the local copy is absent. Put the
local `bin` directory on `PATH` to use the other `ares-*` commands:

```sh
export PATH="$PWD/.dev/tooling/webos/node_modules/.bin:$PATH"
npm run build:webos
ares-install --device tv1 dist/webos/io.github.speyll.ovela_1.0.1_all.ipk
```

`ares-setup-device` and developer mode on the TV are required the first time.
The IPK is intentionally unsigned; install it through Developer Mode.

### Android TV / phone

You need JDK 17 and Android SDK 36. Point Gradle at the SDK with `ANDROID_HOME`
or `apps/android-tv/local.properties`.

```sh
npm run build:android
adb install -r apps/android-tv/app/build/outputs/apk/release/app-release.apk
```

`build:android` writes the APK to
`dist/release/Ovela-AndroidTV-<version>.apk` as well as the Gradle output path.

Release builds are signed so they can be installed directly. By default the
Android **debug key is used, and that key is generated per machine**: an APK
built on another machine (or a fresh clone) gets a different key and cannot
update an APK already installed. The build prints a warning when this fallback
is used. For consistent updates, create `apps/android-tv/keystore.properties`:

```properties
storeFile=/path/to/ovela-release.jks
storePassword=...
keyAlias=ovela
keyPassword=...
```

The same values can be supplied as `OVELA_KEYSTORE_FILE`,
`OVELA_KEYSTORE_PASSWORD`, `OVELA_KEYSTORE_ALIAS`, and
`OVELA_KEYSTORE_KEY_PASSWORD`. Keep the keystore and its passwords out of Git.

### Optional API bridge

`apps/api-bridge/` is a small dependency-free Node server that proxies Xtream
and M3U requests for providers that block browser CORS requests from the TV. It
is only needed when the webOS app reports a CORS or "provider blocked" error.

```sh
BRIDGE_TOKEN=... ALLOWED_SERVERS=https://provider.example node apps/api-bridge/index.js
```

A `Dockerfile` and `docker-compose.yml` are included if you prefer to run it on
a small home server. Then enter the bridge URL and token in the webOS app under
Settings → API bridge. The bridge forwards only the supported catalogue and
playlist actions to the servers and playlists listed in `ALLOWED_SERVERS` and
`ALLOWED_PLAYLISTS`, and requires `BRIDGE_TOKEN` as a bearer token.

## Test

```sh
npm run check
npm run test:webos
```

`npm run check` is read-only: it fails if generated files drift from their
sources, instead of rewriting them. It covers version consistency, the Android
toolchain pins, the webOS app and assets, the API bridge, packaged licence
copies, and the bridge and core unit tests. Regenerate with
`npm run prepare:apps` when it reports drift.

The Android unit tests run with the existing Gradle wrapper:

```sh
cd apps/android-tv && ./gradlew :app:testDebugUnitTest
```

The webOS browser tests need Python 3.12 and Playwright:

```sh
python3 -m pip install -r apps/webos/tests/requirements.txt
python3 -m playwright install chromium
```

They run in desktop Chromium against the production webOS code. They are not a
claim about TV firmware behaviour.

## Release

Releases are built and published manually:

```sh
npm run prepare:apps && npm run check
(cd apps/android-tv && ./gradlew :app:testDebugUnitTest)
npm run build:webos
npm run build:android
mkdir -p dist/release-<version>
cp dist/webos/io.github.speyll.ovela_<version>_all.ipk dist/release-<version>/
cp dist/release/Ovela-AndroidTV-<version>.apk dist/release-<version>/
cd dist/release-<version>
sha256sum io.github.speyll.ovela_<version>_all.ipk Ovela-AndroidTV-<version>.apk > SHA256SUMS.txt
sha256sum -c SHA256SUMS.txt
```

`build:webos` writes the IPK to `dist/webos/` and `build:android` writes the APK
to `dist/release/`. The dedicated `dist/release-<version>/` directory holds only
the two versioned artifacts and `SHA256SUMS.txt`; upload those three files to a
GitHub release. Local Android builds use `apps/android-tv/keystore.properties`.

`.github/workflows/release.yml` is manual-only (`workflow_dispatch`); pushing a
tag never starts it. It requires the repository secrets
`ANDROID_KEYSTORE_BASE64`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS` and
`ANDROID_KEY_PASSWORD`, and fails before building if any is missing. It is
optional: the manual procedure above needs no Actions secrets.

## Licence

Apache License 2.0. Ovela is Copyright 2026 Ovela Contributors; see
[LICENSE](LICENSE).

The Android APK and the webOS IPK both bundle the notices from `LICENSE` and
`licenses/XtreamlyTV-MIT.txt` as `assets/licenses/Apache-2.0.txt` and
`assets/licenses/XtreamlyTV-MIT.txt`, so they travel with the binaries.
`npm run check:licenses` fails if a packaged copy drifts from its source.

Ovela is derived from [XtreamlyTV](https://github.com/joshcoburn/XtreamlyTV),
Copyright (c) 2026 XtreamlyTV Contributors, which was distributed under the MIT
License:

> Permission is hereby granted, free of charge, to any person obtaining a copy
> of this software and associated documentation files (the "Software"), to deal
> in the Software without restriction, including without limitation the rights
> to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
> copies of the Software, and to permit persons to whom the Software is
> furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all
> copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
> IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
> FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
> AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
> LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
> OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
> SOFTWARE.

The bundled Inter and Arimo fonts are licensed under the SIL Open Font License;
their licence files ship in `assets/licenses/` inside both apps.
