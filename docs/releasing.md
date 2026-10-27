# Releasing and signing

Ovela releases are built from a tagged commit on `main`. The release workflow
builds the webOS package and an Android APK, writes checksums, and attaches them
to a draft GitHub release.

## Version bump

The version is defined once in `VERSION` and mirrored everywhere else. Update:

| File | Field |
|---|---|
| `VERSION` | plain version, e.g. `1.0.0` |
| `package.json` | `version` |
| `packages/core-web/package.json` | `version` |
| `apps/api-bridge/package.json` | `version` |
| `apps/webos/app/appinfo.json` | `version` |
| `apps/webos/app/js/app.js` | `APP_VERSION` |
| `apps/webos/app/js/v0.7-ui.js` | `version` |
| `apps/android-tv/app/build.gradle.kts` | `versionCode` and `versionName` |

`versionCode` must increase monotonically. The 1.0.0 release uses `1000`.

Verify:

```bash
npm run check:versions
```

Add the release notes to `docs/release-notes/<version>.md` and update
`CHANGELOG.md`. If no versioned notes file exists, the release workflow generates
notes from the changelog with `npm run release:notes`.

## Pre-release checks

```bash
npm run prepare:apps
npm run check
npm run test:webos
```

`npm run check` covers version consistency, the pinned Android toolchain, webOS
syntax and assets, bridge configuration and tests, and shared web unit tests.
`test:webos` runs the Playwright smoke suite and requires a Playwright browser
install.

Android unit tests and a build:

```bash
cd apps/android-tv
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

## Android signing

Release builds are signed from a keystore that is never committed. The Gradle
configuration reads signing values from `apps/android-tv/keystore.properties` or,
as a fallback, from environment variables:

| Property | Environment variable |
|---|---|
| `storeFile` | `OVELA_KEYSTORE_FILE` |
| `storePassword` | `OVELA_KEYSTORE_PASSWORD` |
| `keyAlias` | `OVELA_KEYSTORE_ALIAS` |
| `keyPassword` | `OVELA_KEYSTORE_KEY_PASSWORD` |

`keystore.properties` and `*.jks`/`*.keystore` are gitignored. If no signing
values are present, `assembleRelease` produces an unsigned APK. That builds, but
it cannot be installed as an update over a signed install.

### Create the keystore once

Store it outside the repository, for example at
`~/.config/ovela/ovela-release.jks`:

```bash
mkdir -p ~/.config/ovela
keytool -genkeypair \
  -keystore ~/.config/ovela/ovela-release.jks \
  -alias ovela \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Ovela, O=Ovela, C=US"
```

Keep an offline backup. Losing this key makes it impossible to update an
existing sideloaded installation without uninstalling it first.

### Configure the local build

Create `apps/android-tv/keystore.properties` (gitignored):

```properties
storeFile=/home/you/.config/ovela/ovela-release.jks
storePassword=...
keyAlias=ovela
keyPassword=...
```

Then build:

```bash
cd apps/android-tv
./gradlew :app:assembleRelease
```

Verify the signature:

```bash
apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

### Configure CI signing

The release workflow signs Android builds when these repository secrets exist:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | base64-encoded keystore file |
| `ANDROID_STORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | key alias |
| `ANDROID_KEY_PASSWORD` | key password |

Create the base64 value locally:

```bash
base64 -w0 ~/.config/ovela/ovela-release.jks
```

Without these secrets the workflow falls back to a debug APK so that a release
can still be built and inspected. Do not attach that fallback to a public
release as if it were the signed production build.

## webOS packaging

webOS packages are installed through Developer Mode and do not need manufacturer
signing for that flow. Ovela does not ship Seller Lounge signing keys or
production-signed webOS artifacts.

```bash
npm run build:webos
```

Output:

```text
dist/webos/io.github.speyll.ovela_<version>_all.ipk
```

Install on a paired TV with `ares-install`, or use
`scripts/install-webos.ps1` / `scripts/install-webos.sh`. See
[webOS development](development/webos.md).

## Release artefacts

| File | Content |
|---|---|
| `io.github.speyll.ovela_<version>_all.ipk` | webOS package |
| `Ovela-AndroidTV.apk` | Signed Android release APK (CI) |
| `SHA256SUMS.txt` | Checksums for the artefacts |

For a local release, build both platforms, verify the Android signature, and
generate checksums:

```bash
sha256sum dist/webos/*.ipk app/build/outputs/apk/release/app-release.apk
```

## Tag and publish

```bash
git tag -s v1.0.0 -m "Ovela 1.0.0"
git push origin main
git push origin v1.0.0
```

The tag triggers `.github/workflows/release.yml`, which creates a **draft**
release. Review the attached files and notes before publishing.

## Data compatibility

Do not rename the Android preference stores or the webOS state key during a
release. They are documented in [Architecture](architecture.md#storage-and-upgrade-compatibility)
and must remain stable so updates preserve credentials, favorites, progress, and
settings. A key change is a breaking migration, not a cleanup.
