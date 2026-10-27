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

### LG webOS

Install the webOS CLI once:

```sh
npm install -g @webos-tools/cli
```

Then build and install:

```sh
npm run build:webos
ares-install --device tv1 dist/webos/io.github.speyll.ovela_1.0.0_all.ipk
```

`ares-setup-device` and developer mode on the TV are required the first time.

### Android TV / phone

You need JDK 17 and Android SDK 36.

```sh
npm run build:android
adb install -r apps/android-tv/app/build/outputs/apk/release/app-release.apk
```

Release builds are signed so they can be installed directly. By default the
Android debug key is used. To sign with your own key, create
`apps/android-tv/keystore.properties`:

```properties
storeFile=/path/to/ovela-release.jks
storePassword=...
keyAlias=ovela
keyPassword=...
```

The same values can be supplied as `OVELA_KEYSTORE_FILE`,
`OVELA_KEYSTORE_PASSWORD`, `OVELA_KEYSTORE_ALIAS`, and
`OVELA_KEYSTORE_KEY_PASSWORD`. Keep the keystore and its passwords out of Git; an
existing install can only be updated by a build signed with the same key.

## Test

```sh
npm run check
npm run test:webos
```

The webOS browser tests need Python 3.12 and Playwright:

```sh
python3 -m pip install -r apps/webos/tests/requirements.txt
python3 -m playwright install chromium
```

They run in desktop Chromium against the production webOS code. They are not a
claim about TV firmware behaviour.

## Licence

Apache License 2.0. See [LICENSE](LICENSE).

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
