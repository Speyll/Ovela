# Getting started

## Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| Git | current | All work |
| Node.js | 20 or newer | Shared code generation, checks, webOS builds |
| Python | 3.11 or newer | Browser and webOS smoke tests |
| LG webOS CLI (`@webos-tools/cli`) | current | Packaging and installing the webOS app |
| JDK | 17 | Android builds |
| Android SDK | Platform 36, Build Tools 36.0.0 | Android builds |
| `adb` | current | Installing on Android TV |

Android Studio is optional. The repository includes a Gradle wrapper.

## Clone and prepare

```bash
git clone https://github.com/Speyll/Ovela.git
cd Ovela
npm run prepare:apps
```

`prepare:apps` regenerates category classification, the shared web core copies in
`apps/webos/app/js`, and the design-token output. Do not edit generated files by
hand.

## Verify the checkout

```bash
npm run check:versions
npm run check:android-toolchain
npm run check:webos
npm run test:core
```

The bridge checks require only Node.js:

```bash
npm run check:bridge
npm run test:bridge
```

Browser and webOS smoke tests need Playwright:

```bash
python3 -m pip install -r apps/webos/tests/requirements.txt
python3 -m playwright install chromium
npm run test:webos
```

## Run the browser demo

The webOS app is a static HTML application. It can be served directly for UI
work, without packaging:

```bash
cd apps/webos/app
python3 -m http.server 8080
```

Open `http://localhost:8080`. The browser build is a development surface; final
playback, focus, and remote behavior must be checked on a television.

## Build the webOS package

```bash
npm install -g @webos-tools/cli
npm run build:webos
```

The package is written to `dist/webos/io.github.speyll.ovela_<version>_all.ipk`.
See [webOS development](development/webos.md) for pairing, installing, and
inspecting the app on a TV.

## Build the Android app

Debug build:

```bash
cd apps/android-tv
./gradlew :app:assembleDebug
```

Release build requires a signing configuration; see
[Releasing](releasing.md#android-signing).

```bash
cd apps/android-tv
./gradlew :app:assembleRelease
```

Outputs:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

Install with `adb install -r <apk>`. The `-r` flag preserves existing app data
when the signing key is unchanged.

## Project layout

```text
apps/webos/          Static webOS application and Playwright smoke tests
apps/android-tv/     Kotlin/Compose Android TV application
apps/api-bridge/     Optional LAN CORS/metadata bridge
packages/core-web/   Shared browser/webOS provider, URL, recovery, store code
packages/contracts/  JSON schemas and provider fixtures
packages/design-tokens/  Shared design tokens
packages/brand/      Canonical artwork and brand notes
scripts/             Build, sync, install, and release helpers
docs/                Architecture, development, and release documentation
```

## Next steps

- [Architecture](architecture.md)
- [webOS development](development/webos.md)
- [Android TV development](development/android-tv.md)
- [Debugging](development/debugging.md)
- [Contributing](../CONTRIBUTING.md)
